package dev.mawonna;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.PassiveEvolution;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@Mod("mawonna")
public final class MawonnaCompanion {
    public static final String DEFEATS = "mawonna_verified_dark_defeats";
    public static final String DEFEATS_1000 = "mawonna_verified_dark_defeats_1000";
    public static final String MEALS = "mawonna_distinct_meals";
    public static final String MARRIED = "mawonna_married";
    private static final String MEAL_IDS = "mawonna_verified_dishes_v1";
    private static final String SPOUSE = "mawonna_spouse";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mawonna");
    public static final DeferredItem<Item> RING = ITEMS.registerSimpleItem("mawilite_vow_ring", new Item.Properties().stacksTo(1));
    private static final Map<String,String> DISHES = loadDishes();
    // Weak battle keys expire after the battle registry releases the battle.
    private static final Map<Object,Set<String>> SEEN_FAINTS = Collections.synchronizedMap(new WeakHashMap<>());

    public MawonnaCompanion(IEventBus modBus) {
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::onInteract);
        NeoForge.EVENT_BUS.addListener(this::commands);
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, (Consumer<BattleFaintedEvent>) MawonnaCompanion::onFainted);
    }

    private static Map<String,String> loadDishes() {
        var stream = MawonnaCompanion.class.getResourceAsStream("/mawonna/meals.json");
        if (stream == null) throw new IllegalStateException("Missing audited meal list");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String,String> result = new Gson().fromJson(reader, new TypeToken<Map<String,String>>(){}.getType());
            if (new HashSet<>(result.values()).size() < 49) throw new IllegalStateException("Fewer than 49 distinct approved dishes");
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    static String species(Pokemon pokemon) {
        return pokemon.getSpecies().getName().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }
    static String counterKey(Pokemon pokemon) {
        return switch(species(pokemon)) {
            case "mawile" -> DEFEATS;
            case "corvisquire", "sliggoo", "sylveon" -> DEFEATS_1000;
            default -> null;
        };
    }
    public static int value(Pokemon pokemon, String key) {
        var feature = pokemon.getFeature(key);
        return feature instanceof IntSpeciesFeature number ? number.getValue() : 0;
    }
    static void set(Pokemon pokemon, String key, int value) {
        // Native synchronized species features persist with the Pokemon and supply the summary sidebar.
        new IntSpeciesFeature(key, value).apply(pokemon);
    }
    static boolean eligible(boolean pvp, ActorType attacker, ActorType victim,
                            boolean opposingSide, boolean dark, boolean playerOwned, boolean knownAttacker) {
        return !pvp && knownAttacker && attacker == ActorType.PLAYER && playerOwned && opposingSide && dark
                && (victim == ActorType.WILD || victim == ActorType.NPC);
    }
    private static void onFainted(BattleFaintedEvent event) {
        BattlePokemon killed = event.getKilled();
        BattlePokemon killer = event.getContext().getOrigin();
        if (killer == null) return; // Unknown or environmental damage cannot identify a personal defeat.
        BattleActor actor = killer.getActor();
        BattleActor victim = killed.getActor();
        boolean dark = false;
        for (var type : killed.getEffectedPokemon().getTypes()) if (type == ElementalTypes.DARK) dark = true;
        boolean pvp = event.getBattle().isPvP();
        for (var opponent : actor.getSide().getOppositeSide().getActors()) if (opponent.getType() == ActorType.PLAYER) pvp = true;
        Pokemon original = killer.getOriginalPokemon();
        if (!eligible(pvp, actor.getType(), victim.getType(), actor.getSide() != victim.getSide(), dark,
                      original.getOwnerUUID() != null, true)) return;
        String key = counterKey(original);
        if (key == null) return;
        String faint = killed.getUuid() + ":" + killed.getFaintedAt();
        if (!firstFaint(event.getBattle(), faint)) return;
        int next = creditDefeat(original);
        if (killer.getEffectedPokemon() != original) set(killer.getEffectedPokemon(), key, next);
        killer.getPostBattlePokemonOperations().add(pokemon -> {
            // Retain the credit if the battle implementation restores a battle copy at its end.
            set(pokemon.getOriginalPokemon(), key, Math.max(next, value(pokemon.getOriginalPokemon(), key)));
            offerEvolution(pokemon.getOriginalPokemon());
            return kotlin.Unit.INSTANCE;
        });
    }
    static boolean firstFaint(Object battle, String faint) {
        synchronized(SEEN_FAINTS) {
            return SEEN_FAINTS.computeIfAbsent(battle, unused -> new HashSet<>()).add(faint);
        }
    }
    static int creditDefeat(Pokemon pokemon) {
        String key = Objects.requireNonNull(counterKey(pokemon));
        int maximum = DEFEATS.equals(key) ? 666 : 1000;
        int next = Math.min(maximum, value(pokemon, key) + 1);
        set(pokemon, key, next);
        return next;
    }
    private static void offerEvolution(Pokemon pokemon) {
        Set<String> ids = Set.of("mawile_mawonna", "corvisquire_covislight", "sliggoo_fairy_sliggoo", "sylveon_inkyeon");
        for (var evolution : pokemon.getForm().getEvolutions()) {
            if (ids.contains(evolution.getId()) && evolution instanceof PassiveEvolution passive) passive.attemptEvolution(pokemon);
        }
    }
    static Set<String> mealIds(Pokemon pokemon) {
        Set<String> ids = new LinkedHashSet<>();
        ListTag tags = pokemon.getPersistentData().getList(MEAL_IDS, Tag.TAG_STRING);
        for (Tag tag : tags) ids.add(tag.getAsString());
        return ids;
    }
    static boolean rememberMeal(Pokemon pokemon, String canonical) {
        Set<String> ids = mealIds(pokemon);
        if (!ids.add(canonical)) return false;
        ListTag saved = new ListTag();
        ids.forEach(id -> saved.add(StringTag.valueOf(id)));
        pokemon.getPersistentData().put(MEAL_IDS, saved);
        set(pokemon, MEALS, Math.min(49, ids.size()));
        return true;
    }
    static boolean readyToMarry(Pokemon pokemon) {
        return species(pokemon).equals("mawile") && mealIds(pokemon).size() >= 49 && value(pokemon, DEFEATS) >= 666
                && value(pokemon, MARRIED) == 0 && !pokemon.getPersistentData().hasUUID(SPOUSE);
    }
    private static void message(ServerPlayer player, String key, Object... values) {
        player.sendSystemMessage(Component.translatable("mawonna.message." + key, values));
    }
    private void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof PokemonEntity entity)) return;
        Pokemon pokemon = entity.getPokemon();
        if (!species(pokemon).equals("mawile")) return;
        ItemStack stack = event.getItemStack();
        boolean ring = stack.is(RING.get());
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (!ring && food == null) return;
        // Own these interactions on both sides to prevent Cobblemon's ordinary feeding consuming the same item twice.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals(pokemon.getOwnerUUID())) { message(player,"owner"); return; }
        if (entity.isBattling()) { message(player,"battle"); return; }
        if (ring) {
            if (value(pokemon, MARRIED) != 0 || pokemon.getPersistentData().hasUUID(SPOUSE)) { message(player,"already_married"); return; }
            if (!readyToMarry(pokemon)) { message(player,"requirements",mealIds(pokemon).size(),value(pokemon,DEFEATS)); return; }
            if (!player.isShiftKeyDown()) { message(player,"confirm"); return; }
            pokemon.getPersistentData().putUUID(SPOUSE, player.getUUID());
            pokemon.getPersistentData().putString("mawonna_spouse_name", player.getGameProfile().getName());
            set(pokemon, MARRIED, 1);
            stack.shrink(1);
            var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("mawonna","married_mawile"));
            if (advancement != null) player.getAdvancements().award(advancement,"vow");
            offerEvolution(pokemon);
            message(player,"married");
            return;
        }
        String canonical = DISHES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (canonical == null || food.nutrition() < 6 || food.saturation() < 7.199F) { message(player,"ineligible"); return; }
        if (!rememberMeal(pokemon, canonical)) { message(player,"repeat",mealIds(pokemon).size()); return; }
        ItemStack remainder = stack.getCraftingRemainingItem();
        stack.shrink(1);
        if (!remainder.isEmpty() && !player.addItem(remainder)) player.drop(remainder,false);
        message(player,"meal",Math.min(49,mealIds(pokemon).size()),value(pokemon,DEFEATS));
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mawonnatest").requires(source -> source.hasPermission(4)).executes(context -> {
            try {
                selfTest(context.getSource().registryAccess());
                auditAvailableMeals(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("MAWONNA_SELF_TEST_OK"), false);
                return 1;
            } catch (Throwable failure) {
                failure.printStackTrace();
                context.getSource().sendFailure(Component.literal("MAWONNA_SELF_TEST_FAILED: " + failure));
                return 0;
            }
        }));
    }
    private static void auditAvailableMeals(net.minecraft.server.MinecraftServer server) {
        Set<String> obtainable = new HashSet<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            var output = recipe.getResultItem(server.registryAccess());
            String id = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
            String canonical = DISHES.get(id);
            if (canonical == null) continue;
            var food = output.get(DataComponents.FOOD);
            if (food == null || food.nutrition() < 6 || food.saturation() < 7.199F) continue;
            boolean ingredientsExist = !recipe.getIngredients().isEmpty();
            for (var ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty() && ingredient.getItems().length == 0) ingredientsExist = false;
            }
            if (ingredientsExist) obtainable.add(canonical);
        }
        require(obtainable.size() >= 49, "Only " + obtainable.size() + " distinct nutritious dishes have obtainable recipes; need at least 49");
        System.out.println("MAWONNA_MEAL_AUDIT_OK distinct=" + obtainable.size());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static boolean evolutionReady(Pokemon pokemon, String id) {
        return pokemon.getForm().getEvolutions().stream().filter(e -> e.getId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing evolution " + id)).test(pokemon);
    }
    static void selfTest(net.minecraft.core.RegistryAccess registry) {
        Object battle = new Object();
        require(firstFaint(battle,"victim:1"),"First faint ignored");
        require(!firstFaint(battle,"victim:1"),"Duplicate faint counted");
        require(firstFaint(battle,"victim:2"),"Revived target later defeat ignored");
        require(eligible(false,ActorType.PLAYER,ActorType.WILD,true,true,true,true),"Wild knockout rejected");
        require(eligible(false,ActorType.PLAYER,ActorType.NPC,true,true,true,true),"NPC knockout rejected");
        require(!eligible(true,ActorType.PLAYER,ActorType.WILD,true,true,true,true),"Mixed PvP accepted");
        require(!eligible(false,ActorType.PLAYER,ActorType.PLAYER,true,true,true,true),"Player victim accepted");
        require(!eligible(false,ActorType.PLAYER,ActorType.WILD,false,true,true,true),"Ally victim accepted");
        require(!eligible(false,ActorType.PLAYER,ActorType.WILD,true,false,true,true),"Non-Dark victim accepted");
        require(!eligible(false,ActorType.PLAYER,ActorType.WILD,true,true,true,false),"Unknown attacker accepted");
        require(!eligible(false,ActorType.NPC,ActorType.WILD,true,true,true,true),"NPC attacker accepted");
        for (String name : List.of("mawile","corvisquire","sliggoo","sylveon")) {
            Pokemon pokemon = new Pokemon();
            pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName(name)));
            String key = counterKey(pokemon);
            require(pokemon.getFeature(key) instanceof IntSpeciesFeature,"Missing feature assignment " + name);
            require(value(pokemon,key)==0,"Counter imports old progress");
            require(creditDefeat(pokemon)==1,"Verified credit not applied");
            int maximum = DEFEATS.equals(key) ? 666 : 1000;
            set(pokemon,key,maximum);
            require(creditDefeat(pokemon)==maximum,"Counter overflow");
            set(pokemon,key,3);
            require(value(pokemon,key)==3,"Missing species feature assignment for " + name);
            Pokemon restored = new Pokemon().loadFromNBT(registry,pokemon.saveToNBT(registry,new CompoundTag()));
            require(value(restored,key)==3,"Defeat counter not persisted for " + name);
        }
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName("mawile")));
        List<String> canonical = new ArrayList<>(new LinkedHashSet<>(DISHES.values()));
        for (int i=0;i<48;i++) require(rememberMeal(pokemon,canonical.get(i)),"Unique meal ignored");
        require(!rememberMeal(pokemon,canonical.get(0)),"Duplicate meal credited");
        set(pokemon,DEFEATS,666);
        require(!readyToMarry(pokemon),"Marriage allowed before 49 meals");
        rememberMeal(pokemon,canonical.get(48));
        set(pokemon,DEFEATS,665);
        require(!readyToMarry(pokemon),"Marriage allowed before 666 defeats");
        set(pokemon,DEFEATS,666);
        require(readyToMarry(pokemon),"Marriage threshold rejected");
        require(!evolutionReady(pokemon,"mawile_mawonna"),"Mawonna evolution bypasses ring marriage");
        UUID owner = UUID.randomUUID();
        pokemon.getPersistentData().putUUID(SPOUSE,owner);
        set(pokemon,MARRIED,1);
        require(!readyToMarry(pokemon),"Marriage can be repeated");
        require(evolutionReady(pokemon,"mawile_mawonna"),"Mawonna full requirements rejected");
        Pokemon restored = new Pokemon().loadFromNBT(registry,pokemon.saveToNBT(registry,new CompoundTag()));
        require(mealIds(restored).size()==49 && value(restored,MEALS)==49,"Meal persistence failed");
        require(value(restored,MARRIED)==1 && restored.getPersistentData().getUUID(SPOUSE).equals(owner),"Marriage persistence failed");
        Pokemon sylveon = new Pokemon();
        sylveon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName("sylveon")));
        set(sylveon,DEFEATS_1000,1000);
        sylveon.setFriendship(1,true);
        require(!evolutionReady(sylveon,"sylveon_inkyeon"),"Inkyeon accepts positive friendship");
        sylveon.setFriendship(0,true);
        require(evolutionReady(sylveon,"sylveon_inkyeon"),"Inkyeon rejects exact zero friendship");
        set(sylveon,DEFEATS_1000,999);
        require(!evolutionReady(sylveon,"sylveon_inkyeon"),"Inkyeon bypasses defeat counter");
        require(BuiltInRegistries.ITEM.getKey(RING.get()).toString().equals("mawonna:mawilite_vow_ring"),"Ring missing");
    }
}
