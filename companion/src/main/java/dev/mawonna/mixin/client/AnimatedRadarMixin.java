package dev.mawonna.mixin.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.mawonna.client.AnimatedPokemonIcons;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.icon.XaeroIcon;
import xaero.hud.minimap.radar.icon.RadarIconManager;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Optional integration: this mixin is skipped entirely when Xaero is absent. */
@Pseudo
@Mixin(targets="xaero.hud.minimap.radar.render.element.RadarRenderer",remap=false)
public abstract class AnimatedRadarMixin {
    @Unique private String mawonna$iconKey;
    @Unique private boolean mawonna$customMapSpecies;
    @Shadow private xaero.common.minimap.render.MinimapRendererHelper helper;
    @Shadow private xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer iconsRenderer;
    @Shadow private boolean heightBasedFade;
    @Shadow private int heightLimit;
    @Shadow private int startFadingAt;
    @Shadow private xaero.hud.minimap.radar.RadarSession radarSession;

    // Custom artwork needs no model prerender. A marker takes the normal icon branch;
    // its null atlas is never read because our renderIcon injection consumes it.
    @Redirect(method="renderElement(Lnet/minecraft/world/entity/Entity;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at=@At(value="INVOKE",target="Lxaero/hud/minimap/radar/icon/RadarIconManager;get(Lnet/minecraft/world/entity/Entity;FZZLnet/minecraft/client/gui/GuiGraphics;Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lxaero/common/icon/XaeroIcon;"),require=1)
    private XaeroIcon mawonna$directIcon(RadarIconManager manager, Entity entity, float scale,
            boolean debug, boolean variants, GuiGraphics graphics, RenderTarget target) {
        return mawonna$iconKey == null ? manager.get(entity,scale,debug,variants,graphics,target)
                : new XaeroIcon(null,0,0);
    }

    @Inject(method="renderIcon",at=@At("HEAD"),cancellable=true,require=1)
    private void mawonna$drawDirect(XaeroIcon icon, double scale, double iconScale, float height,
            boolean outlined, PoseStack pose, CallbackInfo ci) {
        if (mawonna$iconKey == null) return;
        float size=(float)Math.max(1,scale*iconScale);
        pose.scale(size,size,1);
        float fade=heightBasedFade ? radarSession.getColorHelper().getEntityHeightFade(height,heightLimit,startFadingAt) : 1f;
        int frame=AnimatedPokemonIcons.mapCellAt(mawonna$iconKey,AnimatedPokemonIcons.elapsedMs());
        var matrix=AnimatedPokemonIcons.scaledMapMatrix(pose.last().pose(),-31,-31,62,62,AnimatedPokemonIcons.configuredMapScale());
        int texture=net.minecraft.client.Minecraft.getInstance().getTextureManager()
                .getTexture(AnimatedPokemonIcons.mapAtlas(mawonna$iconKey)).getId();
        helper.prepareMyTexturedColoredModalRect(matrix,-31,-31,(frame%8)*64+1,(frame/8)*64+63,
                62,62,-62,512,texture,outlined?1:fade,outlined?1:fade,outlined?1:fade,outlined?fade:1,iconsRenderer);
        AnimatedPokemonIcons.mapDraws++;
        ci.cancel();
    }

    @ModifyVariable(method="renderElement(Lnet/minecraft/world/entity/Entity;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
                    at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private Entity mawonna$captureEntity(Entity entity) {
        mawonna$iconKey=entity instanceof PokemonEntity pokemon
                ? AnimatedPokemonIcons.mapKey(pokemon.getPokemon().getSpecies().getResourceIdentifier()) : null;
        mawonna$customMapSpecies=entity instanceof PokemonEntity pokemon
                && AnimatedPokemonIcons.isCustomMapSpecies(pokemon.getPokemon().getSpecies().getResourceIdentifier());
        return entity;
    }

    @ModifyArgs(method="renderIcon",at=@At(value="INVOKE",
            target="Lxaero/common/minimap/render/MinimapRendererHelper;prepareMyTexturedColoredModalRect(Lorg/joml/Matrix4f;FFIIFFFFIFFFFLxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"),require=1)
    private void mawonna$animatedMap(Args args) {
        if (mawonna$customMapSpecies) AnimatedPokemonIcons.scaleMapQuad(args);
        AnimatedPokemonIcons.replaceMapQuad(args,mawonna$iconKey);
    }
}
