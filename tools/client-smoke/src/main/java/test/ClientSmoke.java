package test;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

@Mod("clientsmoke")
public class ClientSmoke {
    private boolean started;
    public ClientSmoke() {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))) throw new IllegalStateException("Cloud only");
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (started || !(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null) return;
        started=true;
        mc.reloadResourcePacks().whenComplete((unused,error)->mc.execute(()->{
            try {
                if(error!=null) throw new RuntimeException(error);
                boolean bundled = "true".equals(System.getenv("UNYIELDING_BUNDLED_PACK"));
                if(!bundled && !mc.getResourcePackRepository().getSelectedIds().contains("file/mawonna-test")) throw new AssertionError("Test pack was removed");
                Class<?> repoClass=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository");
                Object repo=repoClass.getField("INSTANCE").get(null);
                Method modelsMethod=repoClass.getDeclaredMethod("getTexturedModels");
                modelsMethod.setAccessible(true);
                Map<?,?> models=(Map<?,?>)modelsMethod.invoke(repo);
                for(String key:List.of("lunavee:lunavee.geo","buzzeon:buzzeon_male.geo","buzzeon:buzzeon_female.geo","creepyeon:creepyeon_male.geo","creepyeon:creepyeon_female.geo","uwu:ghost_jirachi_male.geo","uwu:ghost_jirachi_female.geo","mawonna:inkyeon.geo","mawonna:fairy_sliggoo.geo","mawonna:pink_corvisquire.geo","mawile_custom:mawile_third_evolution.geo")) {
                    if(!models.containsKey(net.minecraft.resources.ResourceLocation.parse(key))) throw new AssertionError("Model not baked: "+key);
                }
                Class<?> stateClass=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.PosableState");
                Class<?> floating=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.FloatingState");
                Method getPoser=repoClass.getMethod("getPoser",net.minecraft.resources.ResourceLocation.class,stateClass);
                Method getTexture=repoClass.getMethod("getTextureNoSubstitute",net.minecraft.resources.ResourceLocation.class,stateClass);
                Method setAspects=stateClass.getMethod("setCurrentAspects",Set.class);
                Map<?,?> variations=(Map<?,?>)repoClass.getMethod("getVariations").invoke(repo);
                Map<String,List<String>> textures=new LinkedHashMap<>();
                for(String id:List.of("fairy_sliggoo","inkyeon","covislight","mawonna","mawonna_flower")) {
                    var location=net.minecraft.resources.ResourceLocation.parse("cobblemon:"+id);
                    if(!variations.containsKey(location)) throw new AssertionError("Missing species resolver: "+id);
                    Object state=floating.getConstructor().newInstance();
                    Object poser=getPoser.invoke(repo,location,state);
                    if(poser==null) throw new AssertionError("Missing poser: "+id);
                    Object normal=getTexture.invoke(repo,location,state);
                    setAspects.invoke(state,Set.of("shiny"));
                    Object shiny=getTexture.invoke(repo,location,state);
                    if(normal==null || shiny==null || normal.equals(shiny)) throw new AssertionError("Missing distinct normal/shiny texture: "+id);
                    textures.put(id,List.of(normal.toString(),shiny.toString()));
                }
                for(String id:List.of("lunavee","buzzeon","ghost_jirachi","creepyeon")) {
                    var location=net.minecraft.resources.ResourceLocation.parse("cobblemon:"+id);
                    if(!variations.containsKey(location)) throw new AssertionError("Missing imported resolver: "+id);
                    for(String sex:List.of("male","female")) {
                        Object state=floating.getConstructor().newInstance();
                        setAspects.invoke(state,Set.of(sex));
                        if(getPoser.invoke(repo,location,state)==null || getTexture.invoke(repo,location,state)==null)
                            throw new AssertionError("Missing imported appearance: "+id+" "+sex);
                    }
                }
                var ringId=net.minecraft.resources.ResourceLocation.parse("mawonna:mawilite_vow_ring");
                if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ringId)) throw new AssertionError("Vow ring is not registered");
                var ring=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ringId);
                var ringModel=mc.getItemRenderer().getItemModelShaper().getItemModel(new net.minecraft.world.item.ItemStack(ring));
                if(ringModel==null || ringModel==mc.getModelManager().getMissingModel()) throw new AssertionError("Vow ring model is missing");
                if(mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.parse("mawonna:textures/item/mawilite_vow_ring.png")).isEmpty()) throw new AssertionError("Vow ring texture is missing");
                // Exercise the actual shipped Cube class: the original malformed UV must fail.
                Class<?> cube=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.Cube");
                boolean rejected=false;
                try {new Gson().fromJson("{\"uv\":{\"north\":{\"uv\":[0,0]}}}",cube);} catch(com.google.gson.JsonParseException expected){rejected=true;}
                if(!rejected) throw new AssertionError("Regression fixture did not reproduce parser rejection");
                var results = new LinkedHashMap<String,Object>();
                results.putAll(Map.of("status","passed","resource_reload",true,"pack_retained",true,"original_uv_rejected",true,"vow_ring_baked",true,"species_textures",textures));
                results.put("pack_source", bundled ? "bundled_mod_jar" : "external_resource_pack");
                results.put("title_screen_render_fixture", Map.of("species", prepareRenderSpecies(bundled), "fields", List.of("name", "baseScale"), "purpose", "GUI form-scale lookup only; full species parsing and gameplay are verified by server runtime smoke"));
                // Launch defaults override options.txt dimensions; resize the actual
                // GLFW window and allow its normal framebuffer callback to run.
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
                mc.setScreen(new VisualSmokeScreen(results));
            } catch(Throwable t) {
                try {Files.writeString(Path.of("client-smoke-failed.txt"),t.toString());}catch(Exception ignored){}
                t.printStackTrace();
                mc.stop();
            }
        }));
    }

    // A title screen has neither server registry sync nor the ability registry.
    // The GUI only needs name and baseScale; use those exact shipped values in a
    // render-only fixture. Models, textures, poses and animations remain real.
    // Full species parsing/gameplay remains independently tested by runtime smoke.
    private static List<String> prepareRenderSpecies(boolean bundled) throws Exception {
        var registry = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE;
        Map<net.minecraft.resources.ResourceLocation, com.cobblemon.mod.common.pokemon.Species> species = new LinkedHashMap<>();
        for (var existing : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getSpecies())
            species.put(existing.getResourceIdentifier(), existing);
        List<String> loaded = new ArrayList<>();
        for (String name : List.of("lunavee", "buzzeon", "creepyeon", "ghost_jirachi")) {
            var id = net.minecraft.resources.ResourceLocation.parse("cobblemon:" + name);
            if (species.containsKey(id)) continue;
            String resource = "data/cobblemon/species/custom/" + name + ".json";
            String json;
            if (bundled) {
                try (var jar = new java.util.zip.ZipFile(Path.of("../libs/Unyielding-Mawonna.jar").toFile())) {
                    var entry = jar.getEntry(resource);
                    if (entry == null) throw new AssertionError("Bundled species data missing: " + resource);
                    try (var stream = jar.getInputStream(entry)) { json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
                }
            } else json = Files.readString(Path.of("resourcepacks/mawonna-test").resolve(resource));
            var definition = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (definition.has("forms") && !definition.getAsJsonArray("forms").isEmpty())
                throw new AssertionError("Render fixture requires explicit form handling for " + name);
            var fixture = new com.cobblemon.mod.common.pokemon.Species();
            fixture.setName(definition.get("name").getAsString());
            fixture.setBaseScale(definition.get("baseScale").getAsFloat());
            species.put(id, fixture);
            loaded.add(name);
        }
        if (!loaded.isEmpty()) registry.reload(species);
        return loaded;
    }
}
