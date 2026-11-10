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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Client-only custom portraits and GIF map playback, without changing Pokemon models. */
public final class AnimatedPokemonIcons {
    private static final long START = System.nanoTime();
    private static final Map<String, Clip> CLIPS = loadClips();
    private static final Map<String, Integer> STATIC_MAP_CELLS = Map.of(
            "lunavee",0, "buzzeon",1, "creepyeon",2, "ghost_jirachi",3, "fairy_sliggoo",4);
    private static final Map<String, Long> STATIC_MAP_DRAWS = new LinkedHashMap<>();
    public static long portraitSelections;
    public static long mapDraws;
    private static final java.lang.reflect.Field SERVER_MAP_SCALE = mapScaleField();

    private static java.lang.reflect.Field mapScaleField() {
        try {
            return Class.forName("dev.unyielding.quests.client.ClientData").getField("customMapIconScale");
        } catch (ReflectiveOperationException | LinkageError absentOptionalModule) {
            return null;
        }
    }

    public static float clampMapScale(double value) {
        return Double.isFinite(value) ? (float)Math.max(.25,Math.min(4,value)) : 1f;
    }

    public static float configuredMapScale() {
        if (SERVER_MAP_SCALE == null) return 1f;
        try { return clampMapScale(SERVER_MAP_SCALE.getDouble(null)); }
        catch (IllegalAccessException | IllegalArgumentException unavailable) { return 1f; }
    }

    /** Change geometry only: Xaero shares its quad width with horizontal UV span. */
    public static Matrix4f scaledMapMatrix(Matrix4f matrix, float x, float y, float width, float height, double scale) {
        float centerX=x+width/2, centerY=y+height/2, clamped=clampMapScale(scale);
        return new Matrix4f(matrix).translate(centerX,centerY,0).scale(clamped,clamped,1).translate(-centerX,-centerY,0);
    }

    private record Timing(int[] durations, int total) {
        int frame(long ms) {
            long phase = Math.floorMod(ms, total);
            for (int i=0;i<durations.length;i++) {
                if (phase < durations[i]) return i;
                phase -= durations[i];
            }
            return 0;
        }
    }

    private record Clip(Timing map, Timing portrait, String portraitFile) {}

    private static Timing timing(JsonObject data) {
        int[] times = new int[data.getAsJsonArray("durationsMs").size()];
        int total = 0;
        for (int i=0;i<times.length;i++) {
            times[i] = data.getAsJsonArray("durationsMs").get(i).getAsInt();
            if (times[i]<=0) throw new IllegalStateException("Invalid frame duration");
            total = Math.addExact(total,times[i]);
        }
        if (times.length == 0 || times.length > 64 || times.length != data.get("frames").getAsInt()
                || total != data.get("totalMs").getAsInt())
            throw new IllegalStateException("Invalid animated icon sequence");
        return new Timing(times,total);
    }

    private static Map<String, Clip> loadClips() {
        try (var stream = AnimatedPokemonIcons.class.getResourceAsStream("/assets/mawonna/animated_icons.json")) {
            if (stream == null) throw new IllegalStateException("Missing animated icon manifest");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Clip> result = new LinkedHashMap<>();
            for (var entry : json.getAsJsonObject("animations").entrySet()) {
                JsonObject data = entry.getValue().getAsJsonObject();
                Timing map = timing(data);
                Timing portrait = data.has("portraitAnimation") ? timing(data.getAsJsonObject("portraitAnimation")) : map;
                String portraitFile = data.has("portraitFile") ? data.get("portraitFile").getAsString() : null;
                result.put(entry.getKey(), new Clip(map,portrait,portraitFile));
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
    public static boolean isCustomMapSpecies(ResourceLocation species) {
        if (mapKey(species) != null) return true;
        return species != null && species.getNamespace().equals("cobblemon")
                && (species.getPath().equals("covislight") || species.getPath().equals("corvislight"));
    }

    /** Map-only selection also covers the friends' and Frilligoo's static artwork.
     *  External icon packs may replace Xaero's Pokemon definition JSON, so the
     *  final draw selects these species directly without depending on that JSON.
     */
    public static String mapKey(ResourceLocation species) {
        String animated = key(species);
        if (animated != null) return animated;
        if (species == null || !species.getNamespace().equals("cobblemon")) return null;
        String path = switch(species.getPath()) {
            case "ghostjirachi" -> "ghost_jirachi";
            case "fairysliggoo" -> "fairy_sliggoo";
            default -> species.getPath();
        };
        return STATIC_MAP_CELLS.containsKey(path) ? path : null;
    }
    public static long elapsedMs() { return (System.nanoTime()-START)/1_000_000L; }
    public static int frameAt(String key, long timeMs) { return CLIPS.get(key).map.frame(timeMs); }
    public static ResourceLocation portraitAt(String key, long timeMs) {
        String fixedPortrait = CLIPS.get(key).portraitFile;
        if (fixedPortrait != null) return ResourceLocation.fromNamespaceAndPath("mawonna", fixedPortrait);
        return ResourceLocation.fromNamespaceAndPath("mawonna", "textures/animated_icons/"+key+"/portrait_"+String.format(java.util.Locale.ROOT,"%03d",CLIPS.get(key).portrait.frame(timeMs))+".png");
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

    /** Includes Corvislight's native artwork; never modifies pixel/UV spans. */
    public static void scaleMapQuad(Args args) {
        float scale=configuredMapScale();
        if (scale != 1f)
            args.set(0,scaledMapMatrix(args.get(0),args.get(1),args.get(2),args.get(5),args.get(6),scale));
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
        var matrix=scaledMapMatrix(pose.last().pose(),x,y,width,height,configuredMapScale());
        buffer.addVertex(matrix,x,y+height,0).setUv(u0,v1);
        buffer.addVertex(matrix,x+width,y+height,0).setUv(u1,v1);
        buffer.addVertex(matrix,x+width,y,0).setUv(u1,v0);
        buffer.addVertex(matrix,x,y,0).setUv(u0,v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Invoked by the hosted client verification harness. Must run on the render thread. */
    public static Map<String,Object> verifyClient() {
        Map<String,Object> out=new LinkedHashMap<>();
        // Check nonzero centers and translated parents; shared matrices/UV dimensions stay untouched.
        Matrix4f parent=new Matrix4f().translate(7,11,0);
        for (double scale : new double[]{.25,1,2,4,-1,20,Double.NaN,Double.POSITIVE_INFINITY}) {
            Matrix4f scaled=scaledMapMatrix(parent,5,9,62,62,scale);
            Vector3f center=scaled.transformPosition(new Vector3f(36,40,0));
            Vector3f left=scaled.transformPosition(new Vector3f(5,9,0));
            Vector3f right=scaled.transformPosition(new Vector3f(67,9,0));
            if (center.distance(new Vector3f(43,51,0))>.0001f
                    || Math.abs(right.distance(left)-62*clampMapScale(scale))>.0001f
                    || !parent.equals(new Matrix4f().translate(7,11,0)))
                throw new IllegalStateException("Map scaling changed center, span or shared parent");
        }
        out.put("mapScaleCases",8);
        out.put("configuredMapScale",configuredMapScale());
        for (String species : new String[]{"mawonna","mawonna_flower","inkyeon","lunavee","buzzeon","creepyeon","ghost_jirachi","fairy_sliggoo","covislight"})
            if (!isCustomMapSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon",species)))
                throw new IllegalStateException("Missing custom species map scale: "+species);
        if (mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon","covislight")) != null
                || isCustomMapSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon","eevee")))
            throw new IllegalStateException("Map scale changed native artwork or other species");
        out.put("mapScaleSpecies",9);
        for (var entry:CLIPS.entrySet()) {
            String key=entry.getKey(); Clip clip=entry.getValue();
            if (frameAt(key,0)!=0 || frameAt(key,clip.map.durations[0])!=1 || frameAt(key,clip.map.total)!=0)
                throw new IllegalStateException("Animation frame timing: "+key);
            int portraitFrames = clip.portraitFile == null ? clip.portrait.durations.length : 1;
            long portraitTime = 0;
            for (int i=0;i<portraitFrames;i++) {
                var file=portraitAt(key,portraitTime);
                if (Minecraft.getInstance().getResourceManager().getResource(file).isEmpty())
                    throw new IllegalStateException("Missing portrait "+file);
                portraitTime += clip.portrait.durations[i];
            }
            boolean portraitChanges = !portraitAt(key,0).equals(portraitAt(key,clip.portrait.durations[0]));
            if (portraitChanges != (clip.portraitFile == null))
                throw new IllegalStateException("Incorrect portrait playback mode: "+key);
            if (Minecraft.getInstance().getResourceManager().getResource(mapAtlas(key)).isEmpty())
                throw new IllegalStateException("Missing map atlas "+key);
            out.put(key+"Frames",clip.map.durations.length);
            out.put(key+"DurationMs",clip.map.total);
            out.put(key+"PortraitAnimated",clip.portraitFile == null);
            out.put(key+"PortraitFrames",portraitFrames);
            out.put(key+"PortraitDurationMs",clip.portraitFile == null ? clip.portrait.total : 0);
            out.put(key+"FirstFrame",portraitAt(key,0).toString());
            out.put(key+"SecondFrame",portraitAt(key,clip.portrait.durations[0]).toString());
        }
        // The replacement map animation must never alter the approved portrait sequence.
        Clip mawonna = CLIPS.get("mawonna");
        if (mawonna.map.durations.length != 60 || mawonna.map.total != 12000
                || mawonna.portrait.durations.length != 30 || mawonna.portrait.total != 2400
                || !portraitAt("mawonna",2400).equals(portraitAt("mawonna",0))
                || frameAt("mawonna",2400) != 12)
            throw new IllegalStateException("Mawonna map and portrait clocks must remain independent");
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
                || !"ghost_jirachi".equals(mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon","ghostjirachi")))
                || !"fairy_sliggoo".equals(mapKey(ResourceLocation.fromNamespaceAndPath("cobblemon","fairysliggoo"))))
            throw new IllegalStateException("Custom map selection must preserve unrelated Pokemon");
        out.put("staticMapIcons",staticMaps);
        out.put("staticMapDraws",new LinkedHashMap<>(STATIC_MAP_DRAWS));
        out.put("portraitSelections",portraitSelections);
        out.put("mapDraws",mapDraws);
        return out;
    }
}
