package dev.mawonna.mixin.client;

import com.cobblemon.mod.common.client.render.SpriteType;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.mawonna.client.AnimatedPokemonIcons;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository", remap=false)
public abstract class AnimatedPortraitMixin {
    @Inject(method="getSprite",at=@At("HEAD"),cancellable=true,require=1)
    private void mawonna$animatedPortrait(ResourceLocation species, PosableState state, SpriteType type,
                                          CallbackInfoReturnable<ResourceLocation> cir) {
        if (type != SpriteType.PORTRAIT) return;
        ResourceLocation sprite=AnimatedPokemonIcons.portrait(species);
        if (sprite != null) cir.setReturnValue(sprite);
    }
}
