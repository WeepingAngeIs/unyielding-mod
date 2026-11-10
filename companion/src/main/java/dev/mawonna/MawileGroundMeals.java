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
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.pathfinder.Path;

/** Short-range meal collection for a trainer's sent-out Mawile. */
final class MawileGroundMeals {
    private record Bite(ItemStack food, int finishTick) {}
    private static final Map<PokemonEntity, Bite> bites = new WeakHashMap<>();
    private record HeldMeal(UUID player, String id, int lastCryTick) {}
    private record Lure(ServerPlayer player, String meal) {}
    private static final Map<PokemonEntity, HeldMeal> heldMeals = new WeakHashMap<>();
    private static final Map<PokemonEntity, Path> foodPaths = new WeakHashMap<>();

    private static Lure closestLure(PokemonEntity entity, ServerLevel level, Set<String> eaten) {
        Lure closest = null;
        double best = 64.0;
        for (var player : level.players()) {
            double distance = entity.distanceToSqr(player);
            if (distance > best || player.isSpectator() || !player.isAlive()) continue;
            for (ItemStack hand : player.getHandSlots()) {
                String meal = MawonnaCompanion.approvedMeal(hand, player);
                if (meal != null && !eaten.contains(meal) && entity.hasLineOfSight(player)) {
                    closest = new Lure(player, meal);
                    best = distance;
                    break;
                }
            }
        }
        return closest;
    }

    private static void noticeHeldFood(PokemonEntity entity, Lure lure) {
        HeldMeal previous = heldMeals.get(entity);
        if (lure == null) {
            if (previous != null && previous.id() != null)
                heldMeals.put(entity, new HeldMeal(null, null, previous.lastCryTick()));
        } else if (previous == null || ((!lure.meal().equals(previous.id()) || !lure.player().getUUID().equals(previous.player()))
                && entity.tickCount - previous.lastCryTick() >= 60)) {
            entity.cry();
            heldMeals.put(entity, new HeldMeal(lure.player().getUUID(), lure.meal(), entity.tickCount));
        }
    }

    private static boolean walkTo(PokemonEntity entity, net.minecraft.world.entity.Entity target) {
        if (!entity.getNavigation().moveTo(target, 1.15)) return false;
        foodPaths.put(entity, entity.getNavigation().getPath());
        return true;
    }

    private static void stopFoodPath(PokemonEntity entity) {
        Path path = foodPaths.remove(entity);
        if (path != null && path == entity.getNavigation().getPath()) entity.getNavigation().stop();
    }

    static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity) || !(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isAlive() || entity.isBusy() || entity.isBattling() || entity.isPassenger() || entity.isVehicle()) {
            stopFoodPath(entity);
            heldMeals.remove(entity);
            return;
        }
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
        var eaten = MawonnaCompanion.mealIds(pokemon);
        Lure lure = closestLure(entity, level, eaten);
        noticeHeldFood(entity, lure);
        var drops = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(8,3,8), drop -> {
            if (!drop.isAlive() || !(drop.getOwner() instanceof ServerPlayer feeder)) return false;
            String meal = MawonnaCompanion.approvedMeal(drop.getItem(), feeder);
            return meal != null && !eaten.contains(meal);
        });
        drops.sort(Comparator.comparingDouble(drop -> entity.distanceToSqr(drop)));
        for (ItemEntity drop : drops) {
            if (entity.distanceToSqr(drop) > 2.25 || !entity.hasLineOfSight(drop)) {
                if (walkTo(entity, drop)) return;
                continue;
            }
            if (!(drop.getOwner() instanceof ServerPlayer feeder)) continue;
            ItemStack food = drop.getItem().copyWithCount(1);
            String meal = MawonnaCompanion.approvedMeal(food, feeder);
            if (meal == null || !MawonnaCompanion.rememberMeal(pokemon, meal)) return;
            stopFoodPath(entity);
            entity.getLookControl().setLookAt(drop);
            entity.take(drop, 1);
            ItemStack remaining = drop.getItem().copy();
            remaining.shrink(1);
            if (remaining.isEmpty()) drop.discard();
            else drop.setItem(remaining);
            ItemStack container = food.getCraftingRemainingItem();
            if (!container.isEmpty()) entity.spawnAtLocation(container);
            bites.put(entity, new Bite(food, entity.tickCount + 30));
            MawonnaCompanion.reportMeal(feeder, pokemon);
            return;
        }
        if (lure != null) {
            entity.getLookControl().setLookAt(lure.player());
            if (entity.distanceToSqr(lure.player()) > 4 && walkTo(entity, lure.player())) return;
        }
        stopFoodPath(entity);
    }
}
