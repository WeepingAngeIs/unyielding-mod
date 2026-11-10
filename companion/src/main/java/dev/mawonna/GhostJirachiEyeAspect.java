package dev.mawonna;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Set;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Exact server HP state, carried through Cobblemon's existing aspect synchronization. */
final class GhostJirachiEyeAspect implements AspectProvider {
    static final String EXACT = "mawonna-ghost-eye-exact";
    static final String OPEN = "mawonna-ghost-eye-open";
    private static final Set<String> CLOSED_ASPECTS = Set.of(EXACT);
    private static final Set<String> OPEN_ASPECTS = Set.of(EXACT, OPEN);

    static void install() {
        new GhostJirachiEyeAspect().register();
        NeoForge.EVENT_BUS.addListener(GhostJirachiEyeAspect::afterEntityTick);
        NeoForge.EVENT_BUS.addListener(GhostJirachiEyeAspect::afterPlayerTick);
    }

    private static boolean isGhostJirachi(Pokemon pokemon) {
        return MawonnaCompanion.species(pokemon).equals("ghostjirachi");
    }

    static boolean belowHalf(int currentHp, int maxHp) {
        // Integer comparison keeps exactly 50% closed, including odd maximum HP.
        return maxHp > 0 && 2L * currentHp < maxHp;
    }

    @Override
    public Set<String> provide(Pokemon pokemon) {
        if (!isGhostJirachi(pokemon)) return Set.of();
        return belowHalf(pokemon.getCurrentHealth(), pokemon.getMaxHealth())
                ? OPEN_ASPECTS : CLOSED_ASPECTS;
    }

    @Override
    public Set<String> provide(PokemonProperties properties) {
        // Properties without a live Pokemon do not have authoritative health.
        return Set.of();
    }

    private static void afterEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity) || entity.level().isClientSide) return;
        refresh(entity.getPokemon());
    }

    private static void afterPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        // A recalled Pokemon has no entity tick. Refresh its party representation
        // after healing as well, so the team portrait does not retain an old eye.
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) refresh(pokemon);
    }

    static void refresh(Pokemon pokemon) {
        if (!isGhostJirachi(pokemon)) return;
        boolean open = belowHalf(pokemon.getCurrentHealth(), pokemon.getMaxHealth());
        Set<String> aspects = pokemon.getAspects();
        if (!aspects.contains(EXACT) || aspects.contains(OPEN) != open) {
            // Recompute only on a boundary change; never overwrite other aspects.
            // The native server delegate publishes ASPECTS on its next tick.
            pokemon.updateAspects();
        }
    }

    static void selfTest() {
        int[][] cases = {{100,100,0},{51,100,0},{50,100,0},{49,100,1},
                {7,13,0},{6,13,1},{7,14,0},{6,14,1},{0,100,1},{0,0,0},
                {Integer.MAX_VALUE,Integer.MAX_VALUE,0},
                {1073741823,Integer.MAX_VALUE,1},{1073741824,Integer.MAX_VALUE,0}};
        for (int[] c : cases) {
            if (belowHalf(c[0], c[1]) != (c[2] == 1))
                throw new IllegalStateException("Ghost Jirachi eye threshold " + c[0] + "/" + c[1]);
        }
    }

    static void selfTestPokemon() {
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByIdentifier(
                ResourceLocation.fromNamespaceAndPath("cobblemon", "ghost_jirachi")), "Ghost Jirachi missing"));
        pokemon.setLevel(50);
        pokemon.setShiny(true);
        pokemon.setForcedAspects(Set.of("mawonna-eye-test-preserved"));
        int max = pokemon.getMaxHealth();
        require(max > 2, "invalid max HP");
        pokemon.setCurrentHealth(max);
        refresh(pokemon);
        require(pokemon.getAspects().contains(EXACT) && !pokemon.getAspects().contains(OPEN), "full HP not closed");
        pokemon.setCurrentHealth((max - 1) / 2);
        refresh(pokemon);
        require(pokemon.getAspects().contains(OPEN), "below-half HP not open");
        // The same refresh path is used for recalled party members, without any entity.
        pokemon.setCurrentHealth(max);
        refresh(pokemon);
        require(!pokemon.getAspects().contains(OPEN), "recalled healing did not close eye");
        require(pokemon.getAspects().contains("mawonna-eye-test-preserved")
                && pokemon.getAspects().contains("shiny"), "unrelated aspects changed");
        pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName("eevee")));
        pokemon.updateAspects();
        require(!pokemon.getAspects().contains(EXACT) && !pokemon.getAspects().contains(OPEN), "other species affected");
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException("Ghost Jirachi eye: " + failure);
    }
}
