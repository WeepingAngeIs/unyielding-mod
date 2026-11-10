package dev.mawonna.mixin.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.mawonna.client.AnimatedPokemonIcons;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
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
