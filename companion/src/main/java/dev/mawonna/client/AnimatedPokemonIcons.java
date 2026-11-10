package dev.mawonna.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;

/** Client-only custom portraits and GIF map playback, without changing Pokemon models. */
public final class AnimatedPokemonIcons {
    private static final long START = System.nanoTime();
    private static final Map<String, Clip> CLIPS = loadClips();
    private static final Map<String, Integer> STATIC_MAP_CELLS = Map.of(
            "lunavee",0, "buzzeon",1, "creepyeon",2, "ghost_jirachi",3);
    private static final Map<String, Long> STATIC_MAP_DRAWS = new LinkedHashMap<>();
    public static long portraitSelections;
    public static long mapDraws;

    private record Clip(int[] durations, int total, String portraitFile) {
        int frame(long ms) {
            long phase = Math.floorMod(ms, total);
            for (int i=0;i<durations.length;i++) {
                if (phase < durations[i]) return i;
                phase -= durations[i];
            }
            return 0;
        }
    }

    private static Map<String, Clip> loadClips() {
        try (var stream = AnimatedPokemonIcons.class.getResourceAsStream("/assets/mawonna/animated_icons.json")) {
            if (stream == null) throw new IllegalStateException("Missing animated icon manifest");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Clip> result = new LinkedHashMap<>();
            for (var entry : json.getAsJsonObject("animations").entrySet()) {
                JsonObject data = entry.getValue().getAsJsonObject();
                int[] times = new int[data.getAsJsonArray("durationsMs").size()];
                int total = 0;
                for (int i=0;i<times.length;i++) {
                    times[i]=data.getAsJsonArray("durationsMs").get(i).getAsInt();
                    if (times[i]<=0) throw new IllegalStateException("Invalid frame duration");
                    total += times[i];
                }
                if (times.length != data.get("frames").getAsInt() || total != data.get("totalMs").getAsInt())
                    throw new IllegalStateException("Invalid animated icon sequence");
                String portraitFile = data.has("portraitFile") ? data.get("portraitFile").getAsString() : null;
                result.put(entry.getKey(), new Clip(times,total,portraitFile));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    public static String key(ResourceLocation species) {
        if (species == null || !species.getNamespace().equals("cobblemon")) return null;
        return switch(species.getPath()) {
            case "mawonna", "mawonna_flower", "mawonnaflower" -> "mawonna";
            case "inkyeon" -> "inkyeon";
            default -> null;
        };
    }
    /** Map-only selection also covers the four friends' static artwork.
     *  External icon packs may replace Xaero's Pokemon definition JSON, so the
     *  final draw selects these species directly without depending on that JSON.
     */
    public static String mapKey(ResourceLocation species) {
        String animated = key(species);
        if (animated != null) return animated;
        if (species == null || !species.getNamespace().equals("cobblemon")) return null;
        String path = species.getPath().equals("ghostjirachi") ? "ghost_jirachi" : species.getPath();
        return STATIC_MAP_CELLS.containsKey(path) ? path : null;
    }
    public static long elapsedMs() { return (System.nanoTime()-START)/1_000_000L; }
    public static int frameAt(String key, long timeMs) { return CLIPS.get(key).frame(timeMs); }
    public static ResourceLocation portraitAt(String key, long timeMs) {
        String fixedPortrait = CLIPS.get(key).portraitFile;
        if (fixedPortrait != null) return ResourceLocation.fromNamespaceAndPath("mawonna", fixedPortrait);
        return ResourceLocation.fromNamespaceAndPath("mawonna", "textures/animated_icons/"+key+"/portrait_"+String.format(java.util.Locale.ROOT,"%03d",frameAt(key,timeMs))+".png");
    }
    public static ResourceLocation portrait(ResourceLocation species) {
        String key = key(species);
        if (key == null) return null;
        portraitSelections++;
        return portraitAt(key,elapsedMs());
    }
    public static ResourceLocation mapAtlas(String key) {
        if (STATIC_MAP_CELLS.containsKey(key))
            return ResourceLocation.fromNamespaceAndPath("mawonna","textures/animated_icons/friends_map.png");
        return ResourceLocation.fromNamespaceAndPath("mawonna","textures/animated_icons/"+key+"_map.png");
    }
    public static int mapCellAt(String key, long timeMs) {
        Integer cell = STATIC_MAP_CELLS.get(key);
        return cell == null ? frameAt(key,timeMs) : cell;
    }

    /** Replace only the final Xaero quad's texture coordinates; keep its fade and placement. */
    public static void replaceMapQuad(Args args, String key) {
        if (key == null) return;
        int frame=mapCellAt(key,elapsedMs());
        args.set(3,(frame%8)*64+1);
        // Xaero's cached framebuffer atlas is inverted relative to a PNG texture.
        args.set(4,(frame/8)*64+63);
        args.set(7,-62.0F);
        args.set(8,512.0F);
        args.set(9,Minecraft.getInstance().getTextureManager().getTexture(mapAtlas(key)).getId());
        mapDraws++;
        if (STATIC_MAP_CELLS.containsKey(key)) STATIC_MAP_DRAWS.merge(key,1L,Long::sum);
    }

    public static void drawMapPreview(PoseStack pose, String species, int x, int y, int width, int height) {
        drawMapPreviewAt(pose,species,x,y,width,height,elapsedMs());
    }

    /** Draw the same atlas frame with ordinary GUI orientation for hosted visual checks. */
    public static void drawMapPreviewAt(PoseStack pose, String species, int x, int y, int width, int height, long timeMs) {
        String key=mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon",species));
        if (key == null) throw new IllegalArgumentException("No custom map icon: "+species);
        int frame=mapCellAt(key,timeMs);
        float u0=((frame%8)*64+1)/512f, v0=((frame/8)*64+1)/512f;
        float u1=u0+62/512f, v1=v0+62/512f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0,mapAtlas(key));
        var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
        var matrix=pose.last().pose();
        buffer.addVertex(matrix,x,y+height,0).setUv(u0,v1);
        buffer.addVertex(matrix,x+width,y+height,0).setUv(u1,v1);
        buffer.addVertex(matrix,x+width,y,0).setUv(u1,v0);
        buffer.addVertex(matrix,x,y,0).setUv(u0,v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Invoked by the hosted client verification harness. Must run on the render thread. */
    public static Map<String,Object> verifyClient() {
        Map<String,Object> out=new LinkedHashMap<>();
        for (var entry:CLIPS.entrySet()) {
            String key=entry.getKey(); Clip clip=entry.getValue();
            if (frameAt(key,0)!=0 || frameAt(key,clip.durations[0])!=1 || frameAt(key,clip.total)!=0)
                throw new IllegalStateException("Animation frame timing: "+key);
            int portraitFrames = clip.portraitFile == null ? clip.durations.length : 1;
            long portraitTime = 0;
            for (int i=0;i<portraitFrames;i++) {
                var file=portraitAt(key,portraitTime);
                if (Minecraft.getInstance().getResourceManager().getResource(file).isEmpty())
                    throw new IllegalStateException("Missing portrait "+file);
                portraitTime += clip.durations[i];
            }
            boolean portraitChanges = !portraitAt(key,0).equals(portraitAt(key,clip.durations[0]));
            if (portraitChanges != (clip.portraitFile == null))
                throw new IllegalStateException("Incorrect portrait playback mode: "+key);
            if (Minecraft.getInstance().getResourceManager().getResource(mapAtlas(key)).isEmpty())
                throw new IllegalStateException("Missing map atlas "+key);
            out.put(key+"Frames",clip.durations.length);
            out.put(key+"DurationMs",clip.total);
            out.put(key+"PortraitAnimated",clip.portraitFile == null);
            out.put(key+"PortraitFrames",portraitFrames);
            out.put(key+"FirstFrame",portraitAt(key,0).toString());
            out.put(key+"SecondFrame",portraitAt(key,clip.durations[0]).toString());
        }
        Map<String,Object> staticMaps = new LinkedHashMap<>();
        for (var entry:STATIC_MAP_CELLS.entrySet()) {
            String species=entry.getKey();
            var id=ResourceLocation.fromNamespaceAndPath("cobblemon",species);
            if (!species.equals(mapKey(id)) || key(id)!=null || mapCellAt(species,0)!=entry.getValue()
                    || mapCellAt(species,10000)!=entry.getValue())
                throw new IllegalStateException("Static map selection changed native portrait or timing: "+species);
            var texture=mapAtlas(species);
            if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty())
                throw new IllegalStateException("Missing static map artwork: "+texture);
            staticMaps.put(species,Map.of("texture",texture.toString(),"cell",entry.getValue()));
        }
        if (mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon","eevee"))!=null
                || mapKey(ResourceLocation.fromNamespaceAndPath("othermod","lunavee"))!=null
                || !"ghost_jirachi".equals(mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon","ghostjirachi"))))
            throw new IllegalStateException("Custom map selection must preserve unrelated Pokemon");
        out.put("staticMapIcons",staticMaps);
        out.put("staticMapDraws",new LinkedHashMap<>(STATIC_MAP_DRAWS));
        out.put("portraitSelections",portraitSelections);
        out.put("mapDraws",mapDraws);
        return out;
    }
}
