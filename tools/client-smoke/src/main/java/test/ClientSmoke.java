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
                if(!mc.getResourcePackRepository().getSelectedIds().contains("file/mawonna-test")) throw new AssertionError("Test pack was removed");
                Class<?> repoClass=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository");
                Object repo=repoClass.getField("INSTANCE").get(null);
                Method modelsMethod=repoClass.getDeclaredMethod("getTexturedModels");
                modelsMethod.setAccessible(true);
                Map<?,?> models=(Map<?,?>)modelsMethod.invoke(repo);
                for(String key:List.of("mawonna:pink_corvisquire.geo","mawile_custom:mawile_third_evolution.geo")) {
                    if(!models.containsKey(net.minecraft.resources.ResourceLocation.parse(key))) throw new AssertionError("Model not baked: "+key);
                }
                Class<?> stateClass=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.PosableState");
                Class<?> floating=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.FloatingState");
                Method getPoser=repoClass.getMethod("getPoser",net.minecraft.resources.ResourceLocation.class,stateClass);
                for(String id:List.of("covislight","mawonna","mawonna_flower")) {
                    Object state=floating.getConstructor().newInstance();
                    Object poser=getPoser.invoke(repo,net.minecraft.resources.ResourceLocation.parse("cobblemon:"+id),state);
                    if(poser==null) throw new AssertionError("Missing poser: "+id);
                }
                // Exercise the actual shipped Cube class: the original malformed UV must fail.
                Class<?> cube=Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.Cube");
                boolean rejected=false;
                try {new Gson().fromJson("{\"uv\":{\"north\":{\"uv\":[0,0]}}}",cube);} catch(com.google.gson.JsonParseException expected){rejected=true;}
                if(!rejected) throw new AssertionError("Regression fixture did not reproduce parser rejection");
                Files.writeString(Path.of("client-smoke-passed.json"),"{\"status\":\"passed\",\"resource_reload\":true,\"pack_retained\":true,\"original_uv_rejected\":true}");
            } catch(Throwable t) {
                try {Files.writeString(Path.of("client-smoke-failed.txt"),t.toString());}catch(Exception ignored){}
                t.printStackTrace();
            } finally {mc.stop();}
        }));
    }
}
