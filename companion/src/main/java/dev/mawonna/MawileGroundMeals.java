package dev.mawonna;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/** Short-range meal collection for a trainer's sent-out Mawile. */
final class MawileGroundMeals {
    private record Bite(ItemStack food, int finishTick) {}
    private static final Map<PokemonEntity, Bite> bites = new WeakHashMap<>();

    static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity) || !(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isAlive() || entity.isBusy() || entity.isBattling() || entity.isPassenger() || entity.isVehicle()) return;
        Bite bite = bites.get(entity);
        if (bite != null) {
            if (entity.tickCount >= bite.finishTick()) {
                bites.remove(entity);
            } else if (entity.tickCount % 6 == 0) {
                // Cobblemon's EatHeldItemTask uses this entity event, eating sound and item particles.
                level.broadcastEntityEvent(entity, (byte)45);
                entity.playSound(entity.getEatingSound(bite.food()), 0.8f, 1f);
                var mouth = entity.getEyePosition().add(entity.getLookAngle().scale(0.25));
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bite.food()), mouth.x, mouth.y, mouth.z, 4, .08, .06, .08, .015);
            }
            return;
        }
        if (entity.tickCount % 10 != 0) return;
        Pokemon pokemon = entity.getPokemon();
        if (!MawonnaCompanion.species(pokemon).equals("mawile")) return;
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (owner == null || owner.level() != level) return;
        var eaten = MawonnaCompanion.mealIds(pokemon);
        var drops = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(8,3,8), drop -> {
            if (!drop.isAlive() || drop.getOwner() != owner) return false;
            String meal = MawonnaCompanion.approvedMeal(drop.getItem());
            return meal != null && !eaten.contains(meal);
        });
        drops.sort(Comparator.comparingDouble(drop -> entity.distanceToSqr(drop)));
        for (ItemEntity drop : drops) {
            if (entity.distanceToSqr(drop) > 2.25 || !entity.hasLineOfSight(drop)) {
                if (entity.getNavigation().moveTo(drop, 1.15)) return;
                continue;
            }
            ItemStack food = drop.getItem().copyWithCount(1);
            if (!MawonnaCompanion.rememberMeal(pokemon, MawonnaCompanion.approvedMeal(food))) return;
            entity.getNavigation().stop();
            entity.getLookControl().setLookAt(drop);
            entity.take(drop, 1);
            ItemStack remaining = drop.getItem().copy();
            remaining.shrink(1);
            if (remaining.isEmpty()) drop.discard();
            else drop.setItem(remaining);
            ItemStack container = food.getCraftingRemainingItem();
            if (!container.isEmpty()) entity.spawnAtLocation(container);
            bites.put(entity, new Bite(food, entity.tickCount + 30));
            MawonnaCompanion.reportMeal(owner, pokemon);
            return;
        }
    }
}
