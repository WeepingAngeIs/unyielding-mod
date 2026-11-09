package test;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Captures actual Cobblemon GUI renderers, without starting a local world. */
public final class VisualSmokeScreen extends Screen {
    private static final List<String> SPECIES = List.of("lunavee", "buzzeon", "creepyeon", "ghost_jirachi");
    private static final List<String> SHOTS = List.of("friends-profiles-portraits.png", "buzzeon-wing-cycle.png", "ghost-jirachi-angles.png");
    private final Map<String,Object> results;
    private int page, frames;
    private boolean done;

    public VisualSmokeScreen(Map<String,Object> results) {
        super(Component.literal("Friend Pokemon rendering verification"));
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
            else renderGhost(graphics);
            graphics.flush();
            if (++frames < 4) return;
            Path directory = Path.of("visual-smoke");
            Files.createDirectories(directory);
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(directory.resolve(SHOTS.get(page)));
            }
            if (page == 0) results.put("pose_transition_scale_samples", validateTransitions());
            frames = 0;
            if (++page < SHOTS.size()) return;
            results.put("actual_gui_screenshots", SHOTS);
            results.put("gui_rendered_variants", 8);
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
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
            String species = SPECIES.get(column), sex = row < 2 ? "male" : "female";
            boolean portrait = (row & 1) == 1;
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, species + " " + sex + (portrait ? " portrait" : " profile"));
            graphics.enableScissor(x + 3, y + 20, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + (portrait ? cellHeight * .65 : 34), 100);
            FloatingState state = state(sex, 0);
            if (portrait) GuiUtilsKt.drawPosablePortrait(id(species), graphics.pose(), 40f, 1f, false, state, 0f, 0f, 0f, 0f, 0f, 0f, false, 1f, 1f, 1f, 1f);
            else profile(graphics, species, state, PoseType.PROFILE, 64f, 28f);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private void renderWings(GuiGraphics graphics) {
        PoseType[] poses = {PoseType.STAND, PoseType.FLY, PoseType.FLY, PoseType.FLY, PoseType.HOVER};
        float[] seconds = {0f, 0f, .175f, .35f, .3f};
        int cellWidth = width / 5, cellHeight = (height - 36) / 2;
        for (int row = 0; row < 2; row++) for (int column = 0; column < 5; column++) {
            String sex = row == 0 ? "male" : "female";
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, sex + " " + poses[column] + " t=" + seconds[column]);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 105, 100);
            profile(graphics, "buzzeon", state(sex, seconds[column]), poses[column], 94f, 12f);
            graphics.pose().popPose();
        }
    }

    private void renderGhost(GuiGraphics graphics) {
        int cellWidth = width / 3, cellHeight = (height - 36) / 2;
        float[] angles = {-45f, 0f, 45f};
        for (int row = 0; row < 2; row++) for (int column = 0; column < 3; column++) {
            String sex = row == 0 ? "male" : "female";
            int x = column * cellWidth, y = 32 + row * cellHeight;
            panel(graphics, x, y, cellWidth, cellHeight, "Ghost Jirachi " + sex + " yaw=" + angles[column]);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellWidth / 2.0, y + 92, 100);
            profile(graphics, "ghost_jirachi", state(sex, .5f), PoseType.PROFILE, 110f, angles[column]);
            graphics.pose().popPose();
        }
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
