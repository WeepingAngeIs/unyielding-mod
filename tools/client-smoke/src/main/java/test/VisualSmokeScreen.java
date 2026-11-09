package test;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    private static final List<String> SHOTS = List.of("friends-profiles-portraits.png", "buzzeon-wing-cycle.png", "ghost-jirachi-angles.png", "fairy-sliggoo-native-face.png", "inkyeon-walk-normal.png", "inkyeon-walk-shiny.png");
    private static final float[] WALK_PHASES = {0f, (float)Math.PI / 1.32f, (float)Math.PI / .66f, 3f * (float)Math.PI / 1.32f};
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
            else renderInkyeonWalk(graphics, page == 5);
            graphics.flush();
            if (++frames < 4) return;
            Path directory = Path.of("visual-smoke");
            Files.createDirectories(directory);
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(directory.resolve(SHOTS.get(page)));
            }
            if (page == 0) results.put("pose_transition_scale_samples", validateTransitions());
            if (page == 3) results.put("fairy_sliggoo_face_bones", validateSliggooFace());
            if (page == 5) results.put("inkyeon_walk_samples", validateInkyeonWalk());
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
            panel(graphics, x, y, cellWidth, cellHeight, "Fairy Sliggoo " + (shiny ? "shiny" : "normal") + " STAND yaw=" + angles[column]);
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

    // The normal inventory helper hardcodes zero limb swing and zero stride.
    // Use the same loaded Cobblemon renderer with a real moving WALK pose here.
    private static void walkingProfile(GuiGraphics graphics, FloatingState state, float limbSwing, float scale, float yaw) {
        var repository = VaryingModelRepository.INSTANCE;
        var species = id("inkyeon");
        var model = repository.getPoser(species, state);
        var texture = repository.getTexture(species, state);
        var context = new RenderContext();
        context.put(RenderContext.Companion.getTEXTURE(), texture);
        context.put(RenderContext.Companion.getSCALE(), 1f);
        context.put(RenderContext.Companion.getSPECIES(), species);
        context.put(RenderContext.Companion.getASPECTS(), state.getCurrentAspects());
        context.put(RenderContext.Companion.getRENDER_STATE(), RenderContext.RenderState.PROFILE);
        context.put(RenderContext.Companion.getPOSABLE_STATE(), state);
        context.put(RenderContext.Companion.getDO_QUIRKS(), false);
        model.setContext(context);
        state.setCurrentModel(model);
        state.setPoseToFirstSuitable(PoseType.WALK);
        model.applyAnimations(null, state, limbSwing, .8f, state.getAnimationSeconds() * 20f, 0f, 0f);
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

    private static List<String> validateSliggooFace() {
        var model = VaryingModelRepository.INSTANCE.getPoser(id("fairy_sliggoo"), variantState(false, 0));
        var checked = List.of("head", "eye_left", "eye_right", "mouth");
        for (String name : checked) if (model.getPart(name) == null)
            throw new AssertionError("Missing native Fairy Sliggoo face bone: " + name);
        return checked;
    }

    private static List<Map<String,Object>> validateInkyeonWalk() {
        List<Map<String,Object>> samples = new ArrayList<>();
        for (boolean shiny : new boolean[]{false, true}) {
            float minLeft = Float.POSITIVE_INFINITY, maxLeft = Float.NEGATIVE_INFINITY;
            for (int frame = 0; frame < WALK_PHASES.length; frame++) {
                var state = variantState(shiny, frame / 8f);
                var model = VaryingModelRepository.INSTANCE.getPoser(id("inkyeon"), state);
                state.setCurrentModel(model);
                state.setPoseToFirstSuitable(PoseType.WALK);
                model.applyAnimations(null, state, WALK_PHASES[frame], .8f, state.getAnimationSeconds() * 20f, 0f, 0f);
                Map<String,Object> rotations = new LinkedHashMap<>();
                for (String boneName : List.of("back_leg_left", "back_leg_right", "knee_left", "knee_right", "ankle_left", "ankle_right", "foot_left", "foot_right")) {
                    var bone = model.getPart(boneName);
                    for (float angle : new float[]{bone.xRot, bone.yRot, bone.zRot})
                        if (!Float.isFinite(angle)) throw new AssertionError("Nonfinite Inkyeon walk rotation: " + boneName);
                    rotations.put(boneName, List.of(bone.xRot, bone.yRot, bone.zRot));
                    if (Math.abs(bone.yRot) > .0001f || Math.abs(bone.zRot) > .0001f)
                        throw new AssertionError("Inkyeon WALK leg swings sideways: " + boneName + " yaw=" + bone.yRot + " roll=" + bone.zRot);
                }
                float left = model.getPart("back_leg_left").xRot;
                minLeft = Math.min(minLeft, left);
                maxLeft = Math.max(maxLeft, left);
                samples.add(Map.of("shiny", shiny, "phase", frame, "limb_swing", WALK_PHASES[frame], "stride", .8f, "radians_xyz", rotations));
                model.setDefault();
            }
            if (maxLeft - minLeft < .2f) throw new AssertionError("Inkyeon WALK test did not exercise rear-leg motion");
        }
        return samples;
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
                    var bone = model.getPart(scaleBone);
                    for (float scale : new float[]{bone.xScale, bone.yScale, bone.zScale}) {
                        if (Math.abs(scale - 0.0625f) > 0.00001f)
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
