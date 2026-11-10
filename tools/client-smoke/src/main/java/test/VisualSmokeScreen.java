package test;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Captures actual Cobblemon GUI renderers, without starting a local world. */
public final class VisualSmokeScreen extends Screen {
    private static final List<String> SPECIES = List.of("lunavee", "buzzeon", "creepyeon", "ghost_jirachi");
    private static final List<String> SHOTS = List.of("friends-profiles-portraits.png", "buzzeon-wing-cycle.png", "ghost-jirachi-angles.png", "fairy-sliggoo-native-face.png", "inkyeon-walk-normal.png", "inkyeon-walk-shiny.png", "inkyeon-idle-walk-normal.png", "inkyeon-idle-walk-shiny.png", "animated-portraits-and-map-frames.png", "lucky-spin-reel.png");
    private static final float[] WALK_PHASES = {0f, (float)Math.PI / 1.32f, (float)Math.PI / .66f, 3f * (float)Math.PI / 1.32f};
    private static final List<String> REAR_CHAIN = List.of("sylveon", "body", "back_legs", "back_leg_left", "knee_left", "ankle_left", "foot_left", "back_leg_right", "knee_right", "ankle_right", "foot_right");
    private final Map<String,Object> results;
    private int page, frames;
    private boolean done;

    public VisualSmokeScreen(Map<String,Object> results) {
        super(Component.literal("Unyielding Pokemon rendering verification"));
        this.results = results;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (done) return;
        try {
            graphics.fill(0, 0, width, height, 0xFF17202D);
            graphics.drawString(font, "Actual Cobblemon 1.8.4 client: " + SHOTS.get(page), 12, 10, 0xFFFFFFFF, false);
            if (page == 0) renderOverview(graphics);
            else if (page == 1) renderWings(graphics);
            else if (page == 2) renderGhost(graphics);
            else if (page == 3) renderSliggoo(graphics);
            else if (page < 6) renderInkyeonWalk(graphics, page == 5);
            else if (page < 8) renderInkyeonTransitions(graphics, page == 7);
            else if(page==8) renderAnimatedIcons(graphics);
            else renderLuckySpin(graphics);
            graphics.flush();
            if (++frames < 4) return;
            Path directory = Path.of("visual-smoke");
            Files.createDirectories(directory);
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(directory.resolve(SHOTS.get(page)));
            }
            if (page == 0) results.put("pose_transition_scale_samples", validateTransitions());
            if (page == 2) results.put("ghost_jirachi_native", validateGhostJirachi());
            if (page == 3) results.put("fairy_sliggoo_face_bones", validateSliggooFace());
            if (page == 5) results.put("inkyeon_walk_samples", validateInkyeonWalk());
            if (page == 7) results.put("inkyeon_transition_checks", validateInkyeonTransitions());
            frames = 0;
            if (++page < SHOTS.size()) return;
            results.put("actual_gui_screenshots", SHOTS);
            results.put("gui_rendered_variants", 12);
            results.put("screenshot_gui_dimensions", List.of(width, height));
            Files.writeString(Path.of("client-smoke-passed.json"), new Gson().toJson(results));
            done = true;
            Minecraft.getInstance().stop();
        } catch (Throwable failure) {
            done = true;
            try { Files.writeString(Path.of("client-smoke-failed.txt"), failure.toString()); } catch (Exception ignored) {}
            failure.printStackTrace();
            Minecraft.getInstance().stop();
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        int cellWidth = width / 4, cellHeight = (height - 36) / 4;
        int iconScale = Math.max(1, Math.min(3, (cellHeight - 32) / 32));
        int iconSize = 32 * iconScale;
        float profileFactor = Math.min(cellWidth / 320f, cellHeight / 171f);
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
            String species = SPECIES.get(column), sex = row < 2 ? "male" : "female";
            boolean portrait = (row & 1) == 1;
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, species + " " + sex + (portrait ? " portrait 32x32 (" + iconScale + "x)" : " profile"));
            int iconX = x + (cellWidth - iconSize) / 2;
            int iconY = y + 24 + (cellHeight - 28 - iconSize) / 2;
            if (portrait) {
                graphics.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF93A5B8);
                graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF283442);
                graphics.enableScissor(iconX, iconY, iconX + iconSize, iconY + iconSize);
            } else graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            FloatingState state = state(sex, 0);
            if (portrait) {
                graphics.pose().translate(iconX, iconY, 100);
                graphics.pose().scale(iconScale, iconScale, 1f);
                graphics.pose().translate(16, 0, 0);
                GuiUtilsKt.drawPosablePortrait(id(species), graphics.pose(), 13f, 1f, false, state, 0f, 0f, 0f, 0f, 0f, 0f, false, 1f, 1f, 1f, 1f);
            } else {
                graphics.pose().translate(x + cellWidth / 2.0, y + 34 * profileFactor, 100);
                profile(graphics, species, state, PoseType.PROFILE, 64f * profileFactor, 28f);
            }
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderLuckySpin(GuiGraphics graphics) throws Exception {
        Class<?> type=Class.forName("dev.unyielding.quests.client.LuckySpinScreen");
        Screen screen=(Screen)type.getConstructor(Screen.class).newInstance(this);
        screen.init(minecraft,width,height);
        var stateField=type.getDeclaredField("state");stateField.setAccessible(true);
        var state=new net.minecraft.nbt.CompoundTag();state.putLong("count",2);state.putBoolean("ready",true);state.putBoolean("enabled",true);stateField.set(screen,state);
        var waiting=type.getDeclaredField("waiting");waiting.setAccessible(true);waiting.setBoolean(screen,false);
        Class<?> tier=Class.forName("dev.unyielding.quests.spin.LuckySpin$Tier");
        Object basic=tier.getEnumConstants()[0];
        var entry=Class.forName("dev.unyielding.quests.spin.LuckySpin$Entry").getConstructor(tier,net.minecraft.world.item.ItemStack.class);
        var strip=new ArrayList<Object>();
        net.minecraft.world.item.Item[] items={net.minecraft.world.item.Items.DIAMOND,net.minecraft.world.item.Items.GOLD_INGOT,net.minecraft.world.item.Items.BREAD};
        for(int i=0;i<30;i++)strip.add(entry.newInstance(basic,new net.minecraft.world.item.ItemStack(items[i%3])));
        Object result=Class.forName("dev.unyielding.quests.spin.LuckySpin$Spin").getConstructor(tier,boolean.class,String.class,int.class,List.class).newInstance(basic,false,"Diamond",24,strip);
        type.getMethod("receiveSpins",List.class).invoke(screen,List.of(result));
        var started=type.getDeclaredField("started");started.setAccessible(true);started.setLong(screen,net.minecraft.Util.getMillis()-2500);
        screen.tick();screen.render(graphics,0,0,0);
        results.put("lucky_spin_render","Production screen, saved-credit fixture and mid-animation horizontal reel");
    }

    private void renderAnimatedIcons(GuiGraphics graphics) throws Exception {
        String[] species = {"mawonna", "mawonna_flower", "inkyeon"};
        for (int i=0;i<species.length;i++) {
            int x=i*width/3, y=40;
            panel(graphics,x,y,width/3,280,species[i]+" actual team portrait");
            graphics.pose().pushPose();
            graphics.pose().translate(x+width/6.0,y+40,100);
            graphics.pose().scale(6,6,1);
            GuiUtilsKt.drawPosablePortrait(id(species[i]),graphics.pose(),13f,1f,false,state("female",0),0f,0f,0f,0f,0f,0f,false,1f,1f,1f,1f);
            graphics.pose().popPose();
        }
        graphics.flush();
        var draw=Class.forName("dev.mawonna.client.AnimatedPokemonIcons").getMethod("drawMapPreviewAt",PoseStack.class,String.class,int.class,int.class,int.class,int.class,long.class);
        for (int i=0;i<4;i++) {
            String key=i<2 ? "mawonna" : "inkyeon";
            int x=i*width/4, y=330; long time=(i%2)*360L;
            panel(graphics,x,y,width/4,320,key+" map frame t="+time+"ms");
            graphics.flush();
            draw.invoke(null,graphics.pose(),key,x+width/8-100,y+40,200,200,time);
        }
        results.put("map_frame_screenshot_scope","Production atlas selection helper at 0/360ms; live radar requires in-world verification");
    }

    private void renderWings(GuiGraphics graphics) {
        PoseType[] poses = {PoseType.STAND, PoseType.FLY, PoseType.FLY, PoseType.FLY, PoseType.HOVER};
        float[] seconds = {0f, 0f, .175f, .35f, .3f};
        int cellWidth = width / 5, cellHeight = (height - 36) / 2;
        float factor = Math.min(cellWidth / 256f, cellHeight / 342f);
        for (int row = 0; row < 2; row++) for (int column = 0; column < 5; column++) {
            String sex = row == 0 ? "male" : "female";
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, sex + " " + poses[column] + " t=" + seconds[column]);
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 105 * factor, 100);
            profile(graphics, "buzzeon", state(sex, seconds[column]), poses[column], 94f * factor, 12f);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderGhost(GuiGraphics graphics) {
        int cellWidth = width / 3, cellHeight = (height - 36) / 2;
        float factor = Math.min(cellWidth / (1280f / 3f), cellHeight / 342f);
        float[] angles = {-45f, 0f, 45f};
        for (int row = 0; row < 2; row++) for (int column = 0; column < 3; column++) {
            String sex = row == 0 ? "male" : "female";
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, "Ghost Jirachi " + sex + " yaw=" + angles[column]);
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 92 * factor, 100);
            profile(graphics, "ghost_jirachi", state(sex, .5f), PoseType.PROFILE, 110f * factor, angles[column]);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderSliggoo(GuiGraphics graphics) {
        int cellWidth = width / 3, cellHeight = (height - 36) / 2;
        float factor = Math.min(cellWidth / (1280f / 3f), cellHeight / 342f);
        float[] angles = {-40f, 0f, 40f};
        for (int row = 0; row < 2; row++) for (int column = 0; column < 3; column++) {
            boolean shiny = row == 1;
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, "Frilligoo " + (shiny ? "shiny" : "normal") + " STAND yaw=" + angles[column]);
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 36 * factor, 100);
            profile(graphics, "fairy_sliggoo", variantState(shiny, 0), PoseType.STAND, 126f * factor, angles[column]);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderInkyeonWalk(GuiGraphics graphics, boolean shiny) {
        int cellWidth = width / 4, cellHeight = (height - 36) / 3;
        float factor = Math.min(cellWidth / 320f, cellHeight / 228f);
        float[] angles = {0f, 90f, 180f};
        String[] views = {"front", "side", "rear"};
        for (int row = 0; row < 3; row++) for (int column = 0; column < WALK_PHASES.length; column++) {
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, (shiny ? "shiny " : "normal ") + views[row] + " WALK phase " + column + "/4");
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 28 * factor, 100);
            walkingProfile(graphics, variantState(shiny, column / 8f), WALK_PHASES[column], 94f * factor, angles[row]);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderInkyeonTransitions(GuiGraphics graphics, boolean shiny) {
        int[] ticks = {0, 1, 2, 3, 5, 10};
        int cellWidth = width / ticks.length, cellHeight = (height - 36) / 4;
        float factor = Math.min(cellWidth / (1280f / 6), cellHeight / 171f);
        for (int row = 0; row < 4; row++) for (int column = 0; column < ticks.length; column++) {
            String from = row < 2 ? "standing" : "walking", to = row < 2 ? "walking" : "standing";
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, (row < 2 ? "idle>walk " : "walk>idle ") + ((row & 1) == 0 ? "front " : "rear ") + "t=" + ticks[column]);
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 24 * factor, 100);
            inkyeonProfile(graphics, variantState(shiny, 2f), 0f, 64f * factor, (row & 1) == 0 ? 0f : 180f, from, to, ticks[column]);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    // The normal inventory helper hardcodes zero limb swing and zero stride.
    // Use the same loaded Cobblemon renderer with a real moving WALK pose here.
    private static void walkingProfile(GuiGraphics graphics, FloatingState state, float limbSwing, float scale, float yaw) {
        inkyeonProfile(graphics, state, limbSwing, scale, yaw, null, null, 0);
    }

    private static void inkyeonProfile(GuiGraphics graphics, FloatingState state, float limbSwing, float scale, float yaw, String from, String to, int elapsed) {
        var repository = VaryingModelRepository.INSTANCE;
        var species = id("inkyeon");
        var model = repository.getPoser(species, state);
        var texture = repository.getTexture(species, state);
        var context = configureModelContext(model, species, state);
        state.setCurrentModel(model);
        if (from == null) {
            state.setPoseToFirstSuitable(PoseType.WALK);
            model.applyAnimations(null, state, limbSwing, .8f, state.getAnimationSeconds() * 20f, 0f, 0f);
        } else advanceInkyeonTransition(model, state, from, to, 40, elapsed);
        graphics.pose().scale(scale, scale, -scale);
        var translation = model.getProfileTranslation();
        float profileScale = model.getProfileScale();
        graphics.pose().translate(translation.x, translation.y + 1.5 * profileScale, translation.z - 4);
        graphics.pose().scale(profileScale, profileScale, 1f / profileScale);
        graphics.pose().mulPose(new Quaternionf().rotationY((float)Math.toRadians(yaw)));
        Lighting.setupForEntityInInventory();
        RenderSystem.setShaderLights(new Vector3f(-1f, 1f, 1f), new Vector3f(1.3f, -1f, 1f));
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(RenderType.entityCutout(texture));
        model.withLayerContext(buffers, state, repository.getLayers(species, state), () -> {
            model.render(context, graphics.pose(), vertices, 0x00F000F0, OverlayTexture.NO_OVERLAY, -1);
            buffers.endBatch();
            return kotlin.Unit.INSTANCE;
        });
        model.setDefault();
        Lighting.setupFor3DItems();
    }

    private static RenderContext configureModelContext(PosableModel model, ResourceLocation species, FloatingState state) {
        var context = new RenderContext();
        context.put(RenderContext.Companion.getTEXTURE(), VaryingModelRepository.INSTANCE.getTexture(species, state));
        context.put(RenderContext.Companion.getSCALE(), 1f);
        context.put(RenderContext.Companion.getSPECIES(), species);
        context.put(RenderContext.Companion.getASPECTS(), state.getCurrentAspects());
        context.put(RenderContext.Companion.getRENDER_STATE(), RenderContext.RenderState.PROFILE);
        context.put(RenderContext.Companion.getPOSABLE_STATE(), state);
        context.put(RenderContext.Companion.getDO_QUIRKS(), false);
        model.setContext(context);
        return context;
    }

    private static com.google.gson.JsonObject resourceJson(String id) {
        try (var reader = new java.io.InputStreamReader(Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(ResourceLocation.parse(id)).open(), java.nio.charset.StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static Map<String,com.google.gson.JsonObject> geometryBones(com.google.gson.JsonObject json) {
        Map<String,com.google.gson.JsonObject> result = new LinkedHashMap<>();
        for (var bone : json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            var b = bone.getAsJsonObject(); result.put(b.get("name").getAsString(), b);
        }
        return result;
    }

    private static List<String> validateSliggooFace() {
        var model = VaryingModelRepository.INSTANCE.getPoser(id("fairy_sliggoo"), variantState(false, 0));
        var checked = List.of("head", "eye_left", "eye_right", "mouth");
        var actual = geometryBones(resourceJson("mawonna:bedrock/pokemon/models/fairy_sliggoo/fairy_sliggoo.geo.json"));
        var original = geometryBones(resourceJson("cobblemon:bedrock/pokemon/models/0705_sliggoo/sliggoo.geo.json"));
        for (String name : checked) {
            if (model.getPart(name) == null) throw new AssertionError("Missing native Frilligoo face bone: " + name);
            if (!actual.get(name).get("cubes").equals(original.get(name).get("cubes")))
                throw new AssertionError("Frilligoo differs from native face geometry/UV: " + name);
        }
        if (actual.get("mouth").getAsJsonArray("cubes").size() != 1)
            throw new AssertionError("Added smile panel remains on Frilligoo");
        if (!Component.translatable("cobblemon.species.fairysliggoo.name").getString().equals("Frilligoo"))
            throw new AssertionError("Frilligoo display name alias still stale");
        return checked;
    }

    private static Map<String,Object> validateGhostJirachi() {
        var bones = geometryBones(resourceJson("uwu:bedrock/pokemon/models/ghost_jirachi/ghost_jirachi_male.geo.json"));
        if (bones.size() != 49 || !bones.containsKey("jirachi") || bones.containsKey("import_uv_scale") || bones.containsKey("import_uv_root"))
            throw new AssertionError("Ghost Jirachi must retain the native 49-bone skeleton");
        var animations = resourceJson("uwu:bedrock/pokemon/animations/ghost_jirachi/ghost_jirachi.animation.json").getAsJsonObject("animations");
        if (animations.size() != 16) throw new AssertionError("Missing original Jirachi animation clips");
        Map<String,Object> states = new LinkedHashMap<>();
        for (boolean open : new boolean[]{false,true}) {
            var state = variantState(false, .5f);
            state.setCurrentAspects(open ? Set.of("mawonna-ghost-eye-exact", "mawonna-ghost-eye-open") : Set.of("mawonna-ghost-eye-exact"));
            var model = VaryingModelRepository.INSTANCE.getPoser(id("ghost_jirachi"), state);
            configureModelContext(model,id("ghost_jirachi"),state);
            state.setCurrentModel(model); state.setPoseToFirstSuitable(PoseType.PROFILE);
            model.applyAnimations(null,state,0,0,10,0,0);
            String pose = state.getCurrentPose();
            if (pose.contains("low-health") != open) throw new AssertionError("Exact belly-eye aspect chose wrong menu pose: " + pose);
            states.put(open ? "open" : "closed", Map.of("pose",pose,"eye_open_z",model.getPart("eye_open").z,"eye_close_z",model.getPart("eye_close").z));
            model.setDefault();
        }
        return Map.of("native_bones",bones.size(),"native_animation_clips",animations.size(),"exact_aspect_menu_states",states);
    }

    private static List<Map<String,Object>> validateInkyeonWalk() {
        List<Map<String,Object>> samples = new ArrayList<>();
        var actualRig = geometryBones(resourceJson("mawonna:bedrock/pokemon/models/inkyeon/inkyeon.geo.json"));
        var nativeRig = geometryBones(resourceJson("cobblemon:bedrock/pokemon/models/0700_sylveon/sylveon.geo.json"));
        for (String bone : REAR_CHAIN) for (String field : List.of("parent", "pivot", "rotation"))
            if (!Objects.equals(actualRig.get(bone).get(field), nativeRig.get(bone).get(field)))
                throw new AssertionError("Inkyeon rear rig differs from native Sylveon: " + bone + " " + field);
        for (boolean shiny : new boolean[]{false, true}) {
            float minLeft = Float.POSITIVE_INFINITY, maxLeft = Float.NEGATIVE_INFINITY;
            float minFront = Float.POSITIVE_INFINITY, maxFront = Float.NEGATIVE_INFINITY;
            Map<String,List<Float>> restingRear = new LinkedHashMap<>();
            for (int frame = 0; frame < WALK_PHASES.length; frame++) {
                var state = variantState(shiny, frame / 8f);
                var model = VaryingModelRepository.INSTANCE.getPoser(id("inkyeon"), state);
                configureModelContext(model, id("inkyeon"), state);
                state.setCurrentModel(model);
                state.setPoseToFirstSuitable(PoseType.WALK);
                model.applyAnimations(null, state, WALK_PHASES[frame], 0f, state.getAnimationSeconds() * 20f, 0f, 0f);
                float rearNeutral = model.getPart("back_leg_left").xRot, frontNeutral = model.getPart("front_leg_left").xRot;
                model.applyAnimations(null, state, WALK_PHASES[frame], .8f, state.getAnimationSeconds() * 20f, 0f, 0f);
                Map<String,Object> rotations = new LinkedHashMap<>();
                for (String boneName : List.of("front_leg_left", "front_leg_right", "back_leg_left", "back_leg_right", "knee_left", "knee_right", "ankle_left", "ankle_right", "foot_left", "foot_right")) {
                    var bone = model.getPart(boneName);
                    for (float angle : new float[]{bone.xRot, bone.yRot, bone.zRot})
                        if (!Float.isFinite(angle)) throw new AssertionError("Nonfinite Inkyeon walk rotation: " + boneName);
                    rotations.put(boneName, List.of(bone.xRot, bone.yRot, bone.zRot));
                    if (boneName.startsWith("front_leg")) {
                        if (Math.abs(bone.yRot) > .0001f || Math.abs(bone.zRot) > .0001f)
                            throw new AssertionError("Inkyeon front WALK leg swings sideways: " + boneName);
                    } else {
                        var rest = restingRear.computeIfAbsent(boneName, ignored -> List.of(bone.xRot, bone.yRot, bone.zRot));
                        if (!boneName.startsWith("back_leg"))
                            assertNear(bone.xRot, rest.get(0), "no custom rear joint pitch " + boneName);
                        assertNear(bone.yRot, rest.get(1), "no rear yaw oscillation " + boneName);
                        assertNear(bone.zRot, rest.get(2), "no rear roll oscillation " + boneName);
                    }
                }
                float left = model.getPart("back_leg_left").xRot;
                float right = model.getPart("back_leg_right").xRot;
                float frontLeft = model.getPart("front_leg_left").xRot;
                float frontRight = model.getPart("front_leg_right").xRot;
                minLeft = Math.min(minLeft, left);
                maxLeft = Math.max(maxLeft, left);
                minFront = Math.min(minFront, frontLeft);
                maxFront = Math.max(maxFront, frontLeft);
                var nativeState = variantState(shiny, frame / 8f);
                var nativeModel = VaryingModelRepository.INSTANCE.getPoser(id("sylveon"), nativeState);
                configureModelContext(nativeModel, id("sylveon"), nativeState);
                nativeState.setCurrentModel(nativeModel);
                nativeState.setPoseToFirstSuitable(PoseType.WALK);
                nativeModel.applyAnimations(null, nativeState, WALK_PHASES[frame], 0f, nativeState.getAnimationSeconds() * 20f, 0f, 0f);
                nativeModel.applyAnimations(null, nativeState, WALK_PHASES[frame], .8f, nativeState.getAnimationSeconds() * 20f, 0f, 0f);
                assertNear(frontLeft, nativeModel.getPart("front_leg_left").xRot, "native front-left stride");
                assertNear(frontRight, nativeModel.getPart("front_leg_right").xRot, "native front-right stride");
                // Compare actual loaded native transforms, including all parents and
                // static knee/ankle/foot offsets; do not merely mirror our controller.
                float[] actualRear = rearTransform(model), nativeRear = rearTransform(nativeModel);
                for (int i=0;i<actualRear.length;i++)
                    assertNear(actualRear[i], nativeRear[i], "native rear-chain transform " + REAR_CHAIN.get(i/9) + " component=" + i%9);
                for (String side : List.of("left", "right")) for (boolean bodyLocal : new boolean[]{false,true}) {
                    var actualFoot = rearFootPosition(model, side, bodyLocal);
                    var nativeFoot = rearFootPosition(nativeModel, side, bodyLocal);
                    assertNear(actualFoot.x, nativeFoot.x, "native foot lateral " + side);
                    assertNear(actualFoot.y, nativeFoot.y, "native foot height " + side);
                    assertNear(actualFoot.z, nativeFoot.z, "native foot forward/back " + side);
                }
                assertNear(left-rearNeutral, frontRight-frontNeutral, "native diagonal left-rear/right-front phase");
                assertNear(right-rearNeutral, frontLeft-frontNeutral, "native diagonal right-rear/left-front phase");
                samples.add(Map.of("shiny", shiny, "phase", frame, "limb_swing", WALK_PHASES[frame], "stride", .8f, "radians_xyz", rotations, "native_front_and_rear_match", true));
                nativeModel.setDefault();
                model.setDefault();
            }
            assertNear(maxLeft - minLeft, 2.24f, "native rear WALK pitch range at stride 0.8");
            assertNear(maxFront - minFront, 2.24f, "front WALK pitch range at stride 0.8");
        }
        return samples;
    }

    private static void assertNear(float actual, float expected, String label) {
        if (Math.abs(actual - expected) > .0001f) throw new AssertionError(label + ": got " + actual + ", expected " + expected);
    }

    private static void advanceInkyeonTransition(PosableModel model, FloatingState state, String from, String to, int startTick, int elapsed) {
        state.setCurrentModel(model);
        state.updateAge(startTick);
        state.setPose(from);
        model.applyAnimations(null, state, startTick * .55f, .8f, startTick, 0f, 0f);
        model.moveToPose(state, model.getPose(to));
        for (int tick = 0; tick <= elapsed; tick++) {
            state.updateAge(startTick + tick);
            state.preRender();
            model.applyAnimations(null, state, (startTick + tick) * .55f, .8f, startTick + tick, 0f, 0f);
        }
    }

    private static float[] rearTransform(PosableModel model) {
        float[] values = new float[REAR_CHAIN.size() * 9];
        int index = 0;
        for (String name : REAR_CHAIN) {
            var bone = rearPart(model, name);
            for (float value : new float[]{bone.x, bone.y, bone.z, bone.xRot, bone.yRot, bone.zRot, bone.xScale, bone.yScale, bone.zScale}) {
                if (!Float.isFinite(value)) throw new AssertionError("Nonfinite rear-chain transform: " + name);
                values[index++] = value;
            }
        }
        return values;
    }

    private static ModelPart rearPart(PosableModel model, String name) {
        // JSON posers register the chosen root as __root, not its geometry name.
        // Read the actual root object so its transforms stay in the assertions.
        if (name.equals("sylveon")) {
            Object root = model.getRootPart();
            if (root instanceof ModelPart part) return part;
            throw new AssertionError("Expected a baked ModelPart root, got " + root.getClass().getName());
        }
        Object candidate = model.getRelevantPartsByName().get(name);
        if (candidate instanceof ModelPart part) return part;
        throw new AssertionError("Missing rear ancestor or joint: " + name);
    }

    // Pivot position through the actual baked Minecraft ModelPart hierarchy.
    // Both global and body-local values distinguish body sway from a loose leg.
    private static Vector3f rearFootPosition(PosableModel model, String side, boolean bodyLocal) {
        PoseStack transforms = new PoseStack();
        if (!bodyLocal) {
            model.getRootPart().transform(transforms);
            rearPart(model, "body").translateAndRotate(transforms);
        }
        for (String bone : List.of("back_legs", "back_leg_" + side, "knee_" + side, "ankle_" + side, "foot_" + side))
            rearPart(model, bone).translateAndRotate(transforms);
        return new Vector3f().mulPosition(transforms.last().pose());
    }

    private static Map<String,Object> rearSnapshot(PosableModel model) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        float[] values = rearTransform(model);
        for (int i = 0; i < REAR_CHAIN.size(); i++) {
            int n = i * 9;
            snapshot.put(REAR_CHAIN.get(i), Map.of("position", List.of(values[n], values[n+1], values[n+2]), "rotation_xyz", List.of(values[n+3], values[n+4], values[n+5]), "scale", List.of(values[n+6], values[n+7], values[n+8])));
        }
        for (String side : List.of("left", "right")) for (boolean bodyLocal : new boolean[]{false, true}) {
            var point = rearFootPosition(model, side, bodyLocal);
            snapshot.put(side + (bodyLocal ? "_foot_body_local" : "_foot_world"), List.of(point.x, point.y, point.z));
        }
        return snapshot;
    }

    private static Map<String,Object> validateInkyeonTransitions() {
        Map<String,Object> output = new LinkedHashMap<>();
        Map<String,Object> boundsReport = new LinkedHashMap<>();
        float[] minima = new float[REAR_CHAIN.size() * 9], maxima = new float[minima.length];
        Arrays.fill(minima, Float.POSITIVE_INFINITY);
        Arrays.fill(maxima, Float.NEGATIVE_INFINITY);
        float minLateral = Float.POSITIVE_INFINITY, maxLateral = Float.NEGATIVE_INFINITY;
        // Cover a complete eight-second idle loop and multiple gait cycles.
        // The native Sylveon comparison uses its real loaded pose and hierarchy.
        for (String species : List.of("inkyeon", "sylveon")) for (String pose : List.of("standing", "battle_standing", "walking")) {
            var state = variantState(false, 0);
            var model = VaryingModelRepository.INSTANCE.getPoser(id(species), state);
            configureModelContext(model, id(species), state);
            state.setCurrentModel(model);
            if (!model.getPoses().containsKey(pose)) continue;
            state.setPose(pose);
            float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            float[] idleRear = null;
            for (int tick = 0; tick <= 160; tick++) {
                state.updateAge(tick);
                model.applyAnimations(null, state, tick * .55f, .8f, tick, 0f, 0f);
                float[] values = rearTransform(model);
                if (species.equals("inkyeon") && !pose.equals("walking")) {
                    if (idleRear == null) idleRear = values.clone();
                    // Keep the existing body sway, but forbid separate twitching
                    // of the rear parent, hips, knees, ankles or feet while idle.
                    for (int i=18;i<values.length;i++)
                        assertNear(values[i], idleRear[i], "calm idle rear chain " + pose + " " + REAR_CHAIN.get(i/9));
                }
                if (species.equals("inkyeon")) for (int i = 0; i < values.length; i++) {
                    minima[i] = Math.min(minima[i], values[i]);
                    maxima[i] = Math.max(maxima[i], values[i]);
                }
                for (String side : List.of("left", "right")) {
                    var point = rearFootPosition(model, side, true);
                    float[] coordinates = {point.x, point.y, point.z};
                    for (int i = 0; i < 3; i++) { low[i] = Math.min(low[i], coordinates[i]); high[i] = Math.max(high[i], coordinates[i]); }
                    if (species.equals("inkyeon")) { minLateral = Math.min(minLateral, point.x); maxLateral = Math.max(maxLateral, point.x); }
                }
                model.setDefault();
            }
            boundsReport.put(species + ":" + pose, Map.of("frames", 161, "body_local_foot_min_xyz", low, "body_local_foot_max_xyz", high));
        }
        List<Map<String,Object>> samples = new ArrayList<>();
        for (boolean shiny : new boolean[]{false, true}) for (String idle : List.of("standing", "battle_standing")) for (boolean startsWalking : new boolean[]{false, true})
            for (int startTick : new int[]{0, 20, 40, 80, 120}) for (int elapsed : new int[]{0, 1, 2, 3, 5, 10, 20}) {
                String from = startsWalking ? "walking" : idle, to = startsWalking ? idle : "walking";
                var state = variantState(shiny, startTick / 20f);
                var model = VaryingModelRepository.INSTANCE.getPoser(id("inkyeon"), state);
                configureModelContext(model, id("inkyeon"), state);
                advanceInkyeonTransition(model, state, from, to, startTick, elapsed);
                float[] values = rearTransform(model);
                for (int i = 0; i < values.length; i++) {
                    int component = i % 9;
                    float tolerance = component >= 6 ? .0001f : component >= 3 ? .05f : .25f;
                    if (values[i] < minima[i] - tolerance || values[i] > maxima[i] + tolerance)
                        throw new AssertionError("Inkyeon transition escaped steady transform bounds: " + from + "->" + to + " t=" + elapsed + " bone=" + REAR_CHAIN.get(i / 9) + " component=" + component + " value=" + values[i] + " range=" + minima[i] + ".." + maxima[i]);
                }
                for (String side : List.of("left", "right")) {
                    var foot = rearFootPosition(model, side, true);
                    if (foot.x < minLateral - .03125f || foot.x > maxLateral + .03125f)
                        throw new AssertionError("Inkyeon transition foot escaped lateral bounds: " + from + "->" + to + " t=" + elapsed + " side=" + side + " x=" + foot.x);
                }
                samples.add(Map.of("shiny", shiny, "from", from, "to", to, "start_tick", startTick, "transition_tick", elapsed, "current_pose", state.getCurrentPose(), "rear_chain", rearSnapshot(model)));
                model.setDefault();
            }
        output.put("steady_bounds", boundsReport);
        output.put("samples", samples);
        output.put("lateral_tolerance_blocks", .03125f);
        output.put("scope", "Actual loaded pose transitions, native Sylveon stride 0.8, body-local and world foot pivots, all rear ancestors and joints; 8-second idle loops with static rear joints plus native Sylveon comparison");
        return output;
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF354455);
        graphics.drawString(font, label, x + 8, y + 7, 0xFFFFFFFF, false);
    }

    private static ResourceLocation id(String species) { return ResourceLocation.parse("cobblemon:" + species); }
    private static FloatingState state(String sex, float seconds) {
        FloatingState state = new FloatingState();
        state.setCurrentAspects(Set.of(sex));
        state.updateAge((int)(seconds * 20));
        state.updatePartialTicks(seconds * 20 - (int)(seconds * 20));
        return state;
    }
    private static FloatingState variantState(boolean shiny, float seconds) {
        FloatingState state = state("female", seconds);
        state.setCurrentAspects(shiny ? Set.of("female", "shiny") : Set.of("female"));
        return state;
    }
    private static void profile(GuiGraphics graphics, String species, FloatingState state, PoseType pose, float scale, float yaw) {
        PokemonGuiUtilsKt.drawProfilePokemon(id(species), graphics.pose(), new Quaternionf().rotationY((float)Math.toRadians(yaw)), pose, state, 0f, scale, ProfileTransformType.PROFILE, false, false, 1f, 1f, 1f, 1f, 0f, 0f, 15);
    }

    private static int validateTransitions() {
        int samples = 0;
        for (String species : SPECIES) for (String sex : List.of("male", "female")) {
            FloatingState appearance = state(sex, 0);
            var model = VaryingModelRepository.INSTANCE.getPoser(id(species), appearance);
            String scaleBone = species.equals("creepyeon") ? "export_scale" : "import_uv_scale";
            for (var from : model.getPoses().values()) for (var to : model.getPoses().values()) {
                if (from == to) continue;
                FloatingState state = state(sex, 0);
                state.setCurrentModel(model);
                state.setPose(from.getPoseName());
                model.applyAnimations(null, state, 0f, 0f, 0f, 0f, 0f);
                model.moveToPose(state, to);
                for (int tick : new int[]{0, 1, 2, 5, 10}) {
                    state.updateAge(tick);
                    model.applyAnimations(null, state, 0f, 0f, 0f, 0f, 0f);
                    var bone = species.equals("ghost_jirachi") ? (ModelPart) (Object) model.getRootPart() : model.getPart(scaleBone);
                    float expectedScale = species.equals("ghost_jirachi") ? 1f : .0625f;
                    for (float scale : new float[]{bone.xScale, bone.yScale, bone.zScale}) {
                        if (Math.abs(scale - expectedScale) > 0.00001f)
                            throw new AssertionError(species + " " + sex + " " + from.getPoseName() + "->" + to.getPoseName() + " tick=" + tick + " scale=" + scale);
                    }
                    samples++;
                }
                model.setDefault();
            }
        }
        return samples;
    }
}
