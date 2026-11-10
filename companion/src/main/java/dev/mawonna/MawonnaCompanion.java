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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
    public static final DeferredItem<Item> RING = ITEMS.registerSimpleItem("mawilite_vow_ring",
            new Item.Properties().stacksTo(1).component(DataComponents.LORE,
                    new ItemLore(List.of(Component.translatable("mawonna.ring.promise")))));
    private static final Map<String,String> LEGACY_GROUPS = loadLegacyGroups();
    // Weak battle keys expire after the battle registry releases the battle.
    private static final Map<Object,Set<String>> SEEN_FAINTS = Collections.synchronizedMap(new WeakHashMap<>());

    public MawonnaCompanion(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(MawonnaCompanion::addCreativeItems);
        GhostJirachiEyeAspect.install();
        NeoForge.EVENT_BUS.addListener(this::onInteract);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::midnightOffers);
        NeoForge.EVENT_BUS.addListener(MawileGroundMeals::tick);
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe(Priority.NORMAL, (Consumer<com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionAcceptedEvent>) event -> {
            if (event.getEvolution().getId().equals("eevee_lunavee") && !event.getEvolution().test(event.getPokemon())) event.cancel();
        });
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, (Consumer<BattleFaintedEvent>) MawonnaCompanion::onFainted);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(RING.get());
    }

    private static Map<String,String> loadLegacyGroups() {
        var stream = MawonnaCompanion.class.getResourceAsStream("/mawonna/meals.json");
        if (stream == null) throw new IllegalStateException("Missing audited meal list");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String,String> result = new Gson().fromJson(reader, new TypeToken<Map<String,String>>(){}.getType());
            // Older saves tracked several different items as one dish. Keep one credit per old group.
            Map<String, String> groups = new HashMap<>();
            groups.put("mawonna:dish/burger", "farmersdelight:hamburger");
            result.forEach((item, group) -> {
                if (group.startsWith("mawonna:dish/")) groups.putIfAbsent(group, item);
            });
            return Map.copyOf(groups);
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
        Map<String,Integer> goals = new LinkedHashMap<>();
        String darkKey = counterKey(original);
        if (dark && darkKey != null) goals.put(darkKey, defeatGoal(darkKey));
        if (species(original).equals("eevee")) {
            Pokemon opponent = killed.getEffectedPokemon();
            if (killed.getOriginalPokemon().getSpecies().getResourceIdentifier().toString().equals("cobblemon:gastly"))
                goals.put("unyielding_eevee_gastly_defeats", 1000);
            for (var type : opponent.getTypes()) {
                if (type == ElementalTypes.NORMAL) goals.put("unyielding_eevee_normal_defeats", 500);
                if (type == ElementalTypes.BUG) goals.put("unyielding_eevee_bug_defeats", 1000);
            }
        }
        if (!eligible(pvp, actor.getType(), victim.getType(), actor.getSide() != victim.getSide(), !goals.isEmpty(),
                      original.isPlayerOwned(), true)) return;
        if (!firstFaint(event.getBattle(), killed.getUuid() + ":" + killed.getFaintedAt())) return;
        goals.replaceAll((key, limit) -> {
            int next = Math.min(limit, value(original, key) + 1);
            set(original, key, next);
            if (killer.getEffectedPokemon() != original) set(killer.getEffectedPokemon(), key, next);
            if (key.equals(darkKey) && next == limit && original.getOwnerPlayer() != null)
                awardDefeatMilestone(original.getOwnerPlayer(), original);
            return next;
        });
        killer.getPostBattlePokemonOperations().add(pokemon -> {
            Pokemon restored = pokemon.getOriginalPokemon();
            goals.forEach((key,next) -> set(restored,key,Math.max(next,value(restored,key))));
            offerEvolution(restored);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void midnightOffers(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            long time = Math.floorMod(player.level().dayTime(), 24000L);
            if (time < 17000 || time >= 19000) continue;
            var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
            for (Pokemon pokemon : party) {
                if (species(pokemon).equals("eevee") && value(pokemon,"unyielding_eevee_normal_defeats") >= 500)
                    offerEvolution(pokemon);
            }
        }
    }
    static boolean firstFaint(Object battle, String faint) {
        synchronized(SEEN_FAINTS) {
            return SEEN_FAINTS.computeIfAbsent(battle, unused -> new HashSet<>()).add(faint);
        }
    }
    static int creditDefeat(Pokemon pokemon) {
        String key = Objects.requireNonNull(counterKey(pokemon));
        int maximum = defeatGoal(key);
        int next = Math.min(maximum, value(pokemon, key) + 1);
        set(pokemon, key, next);
        return next;
    }
    private static void offerEvolution(Pokemon pokemon) {
        Set<String> ids = Set.of("mawile_mawonna", "corvisquire_covislight", "sliggoo_fairy_sliggoo", "sylveon_inkyeon", "eevee_lunavee", "eevee_creepyeon", "eevee_buzzeon");
        for (var evolution : pokemon.getForm().getEvolutions()) {
            if (ids.contains(evolution.getId()) && evolution instanceof PassiveEvolution passive) passive.attemptEvolution(pokemon);
        }
    }
    static Set<String> mealIds(Pokemon pokemon) {
        Set<String> ids = new LinkedHashSet<>();
        ListTag tags = pokemon.getPersistentData().getList(MEAL_IDS, Tag.TAG_STRING);
        for (Tag tag : tags) ids.add(LEGACY_GROUPS.getOrDefault(tag.getAsString(), tag.getAsString()));
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
    static String approvedMeal(ItemStack stack, net.minecraft.world.entity.LivingEntity owner) {
        if (stack.isEmpty() || !net.neoforged.fml.ModList.get().isLoaded("solonion")
                || stack.getItem().getFoodProperties(stack, owner) == null) return null;
        double diversity = OnionScore.forFood(stack, owner);
        return qualifiesDiversity(diversity) ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : null;
    }

    private static boolean qualifiesDiversity(double score) {
        // The mod calculates in binary floating point, including when adding food effect bonuses.
        return score >= 8.0 - 1.0e-9;
    }

    // The other Unyielding features also run in installations without Spice of Life Onion.
    private static final class OnionScore {
        static double forFood(ItemStack stack, net.minecraft.world.entity.LivingEntity owner) {
            return team.creative.solonion.common.SOLOnion.CONFIG.getDiversity(owner, stack);
        }
    }

    static void reportMeal(ServerPlayer player, Pokemon pokemon) {
        message(player,"meal",Math.min(49,mealIds(pokemon).size()),value(pokemon,DEFEATS));
    }

    private void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PokemonEntity entity)) return;
        Pokemon pokemon = entity.getPokemon();
        if (!species(pokemon).equals("mawile")) return;
        ItemStack stack = event.getItemStack();
        boolean ring = stack.is(RING.get());
        FoodProperties food = stack.getItem().getFoodProperties(stack, event.getEntity());
        if (!ring && food == null) return;
        // Resolve an attempted food interaction here so it cannot become a player bite.
        // Own eligible interactions on both sides to prevent ordinary feeding consuming the same item twice.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ring && !player.getUUID().equals(pokemon.getOwnerUUID())) { message(player,"owner"); return; }
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
        String meal = approvedMeal(stack, player);
        if (meal == null) message(player, "ineligible");
        else if (mealIds(pokemon).contains(meal)) message(player, "repeat", Math.min(49, mealIds(pokemon).size()));
        else message(player, "drop_food");
    }
    private static int defeatGoal(String key) {
        return DEFEATS.equals(key) ? 666 : 1000;
    }

    private static void awardDefeatMilestone(ServerPlayer owner, Pokemon pokemon) {
        var id = ResourceLocation.fromNamespaceAndPath("mawonna", "dark_defeats_" + species(pokemon));
        var advancement = owner.server.getAdvancements().get(id);
        if (advancement != null) owner.getAdvancements().award(advancement, "milestone");
    }

    private static int adjustDefeats(ServerPlayer owner, int slot, int amount) {
        var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(owner);
        Pokemon pokemon = party.get(slot - 1);
        String key = pokemon == null ? null : counterKey(pokemon);
        if (key == null) {
            if (pokemon != null && species(pokemon).equals("eevee")) {
                owner.sendSystemMessage(Component.literal("For Eevee use /eeveedefeats <gastly|normal|bug> " + slot + " <complete|add amount>."));
                return 0;
            }
            owner.sendSystemMessage(Component.literal("Choose a party slot containing Mawile, Corvisquire, Sliggoo or Sylveon."));
            return 0;
        }
        int goal = defeatGoal(key);
        int count = Math.min(goal, value(pokemon, key) + amount);
        set(pokemon, key, count);
        if (count == goal) {
            awardDefeatMilestone(owner, pokemon);
            offerEvolution(pokemon);
        }
        owner.sendSystemMessage(Component.literal("Slot " + slot + " - ").append(pokemon.getDisplayName(false))
                .append(": " + count + " / " + goal + " Dark types defeated."));
        return 1;
    }

    private static int adjustEevee(ServerPlayer player, int slot, String route, int goal, int amount) {
        Pokemon pokemon = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null || !species(pokemon).equals("eevee")) {
            player.sendSystemMessage(Component.literal("Choose a party slot containing Eevee."));
            return 0;
        }
        String key = "unyielding_eevee_" + route + "_defeats";
        int next = Math.min(goal, value(pokemon, key) + amount);
        set(pokemon, key, next);
        offerEvolution(pokemon);
        player.sendSystemMessage(Component.literal("Eevee in slot " + slot + ": " + next + "/" + goal + " " + route + " defeats."));
        return 1;
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cobblemonspawn").requires(source -> source.hasPermission(2))
                .then(Commands.argument("species", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                PokemonSpecies.getImplemented().stream().map(sp -> sp.getResourceIdentifier().getPath()), builder))
                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    var source = ctx.getSource();
                                    var id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx,"species");
                                    if (id.getNamespace().equals("minecraft"))
                                        id = ResourceLocation.fromNamespaceAndPath("cobblemon",id.getPath());
                                    var sp = PokemonSpecies.getByIdentifier(id);
                                    if (sp == null) {
                                        source.sendFailure(Component.literal("Unknown species: " + id));
                                        return 0;
                                    }
                                    int count = ctx.getArgument("count", Integer.class);
                                    int spawned = 0;
                                    for (int i = 0; i < count; i++) {
                                        try {
                                            spawned += event.getDispatcher().execute("spawnpokemon " + sp.getResourceIdentifier().getPath(),source.withSuppressedOutput());
                                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                                            source.sendFailure(Component.literal("Spawn stopped: " + failure.getMessage()));
                                            break;
                                        }
                                    }
                                    int total = spawned;
                                    source.sendSuccess(() -> Component.literal("Spawned " + total + " / " + count + " " + sp.getName()), false);
                                    return total;
                                }))));
        var eeveeCommand = Commands.literal("eeveedefeats").requires(source -> source.hasPermission(2));
        for (String route : List.of("gastly", "normal", "bug")) {
            int goal = route.equals("normal") ? 500 : 1000;
            eeveeCommand.then(Commands.literal(route)
                    .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,6))
                            .then(Commands.literal("complete").executes(ctx -> adjustEevee(ctx.getSource().getPlayerOrException(),
                                    ctx.getArgument("slot", Integer.class), route, goal, goal)))
                            .then(Commands.literal("add").then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,1000))
                                    .executes(ctx -> adjustEevee(ctx.getSource().getPlayerOrException(),ctx.getArgument("slot", Integer.class),
                                            route,goal,ctx.getArgument("amount",Integer.class)))))));
        }
        event.getDispatcher().register(eeveeCommand);
        var slots = com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6);
        var amounts = com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000);
        event.getDispatcher().register(Commands.literal("darkdefeats").requires(source -> source.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("slot", slots)
                        .then(Commands.argument("amount", amounts).executes(ctx -> adjustDefeats(
                                ctx.getSource().getPlayerOrException(), ctx.getArgument("slot", Integer.class),
                                ctx.getArgument("amount", Integer.class))))))
                .then(Commands.literal("complete").then(Commands.argument("slot", slots).executes(ctx ->
                        adjustDefeats(ctx.getSource().getPlayerOrException(), ctx.getArgument("slot", Integer.class), 1000)))));

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
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        Map<String, Double> foods = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            String id = approvedMeal(stack, owner);
            if (id != null) foods.put(id, OnionScore.forFood(stack, owner));
        }
        Set<String> recipeFoods = new TreeSet<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            ItemStack output = recipe.getResultItem(server.registryAccess());
            String id = approvedMeal(output, owner);
            if (id == null || recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().stream().allMatch(ingredient ->
                    ingredient == net.minecraft.world.item.crafting.Ingredient.EMPTY || ingredient.getItems().length > 0))
                recipeFoods.add(id);
        }
        System.out.println("MAWONNA_DIVERSITY_ITEMS " + new Gson().toJson(foods));
        System.out.println("MAWONNA_DIVERSITY_RECIPES " + new Gson().toJson(recipeFoods));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static final class TestActor extends BattleActor {
        private final ActorType type;
        TestActor(ActorType type, List<BattlePokemon> team) { super(UUID.randomUUID(),team); this.type=type; }
        @Override public ActorType getType() { return type; }
        @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.literal("Cloud test actor"); }
        @Override public net.minecraft.network.chat.MutableComponent nameOwned(String name) { return Component.literal(name); }
    }
    private static Pokemon testPokemon(String name) {
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName(name)));
        return pokemon;
    }
    private static void testFaintEvent(ActorType victimType, boolean dark, boolean knownOrigin, int expected) {
        testFaintEvent(victimType,knownOrigin,expected,"mawile",dark ? "umbreon" : "eevee",DEFEATS);
    }
    private static void testFaintEvent(ActorType victimType, boolean knownOrigin, int expected, String speciesName, String victimName, String feature) {
        Pokemon attacker = testPokemon(speciesName);
        Pokemon bench = testPokemon(speciesName);
        var store = new com.cobblemon.mod.common.api.storage.party.PlayerPartyStore(UUID.randomUUID());
        require(store.add(attacker) && store.add(bench), "Test ownership not initialized");
        Pokemon battleCopy = testPokemon(speciesName);
        BattlePokemon killer = new BattlePokemon(attacker,battleCopy,new ArrayList<>(),new ArrayList<>());
        BattlePokemon benched = new BattlePokemon(bench,bench,new ArrayList<>(),new ArrayList<>());
        Pokemon victimPokemon = testPokemon(victimName);
        BattlePokemon victim = new BattlePokemon(victimPokemon,victimPokemon,new ArrayList<>(),new ArrayList<>());
        TestActor player = new TestActor(ActorType.PLAYER,List.of(killer,benched));
        TestActor opponent = new TestActor(victimType,List.of(victim));
        PokemonBattle battle = new PokemonBattle(new com.cobblemon.mod.common.battles.BattleFormat(),
            new com.cobblemon.mod.common.battles.BattleSide(player),new com.cobblemon.mod.common.battles.BattleSide(opponent));
        victim.setFaintedAt(1);
        var context = new com.cobblemon.mod.common.api.battles.interpreter.BasicContext("tackle",1,
            com.cobblemon.mod.common.api.battles.interpreter.BattleContext.Type.values()[0],knownOrigin ? killer : null);
        var event = new BattleFaintedEvent(battle,victim,context,"p2a");
        onFainted(event);
        require(value(attacker,feature)==expected,"Faint event credit wrong for " + victimType + " dark="+victimName+" origin="+knownOrigin);
        require(value(battleCopy,feature)==expected,"Battle copy missing verified counter");
        require(value(bench,feature)==0,"Benched teammate received credit");
        onFainted(event);
        require(value(attacker,feature)==expected,"Duplicate faint event credited twice");
        for (var operation : killer.getPostBattlePokemonOperations()) operation.invoke(killer);
        require(value(attacker,feature)==expected,"Postbattle operation changed valid credit");
    }
    private static boolean evolutionReady(Pokemon pokemon, String id) {
        return pokemon.getForm().getEvolutions().stream().filter(e -> e.getId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing evolution " + id)).test(pokemon);
    }
    static void selfTest(net.minecraft.core.RegistryAccess registry) {
        Pokemon diner = testPokemon("mawile");
        ListTag legacy = new ListTag();
        legacy.add(StringTag.valueOf("mawonna:dish/burger"));
        diner.getPersistentData().put(MEAL_IDS, legacy);
        require(!qualifiesDiversity(7.99) && qualifiesDiversity(8.0) && qualifiesDiversity(8.01),
                "Diversity threshold must include exactly eight");
        require(mealIds(diner).contains("farmersdelight:hamburger"), "Legacy burger credit lost");
        require(approvedMeal(new ItemStack(net.minecraft.world.item.Items.CARROT), null) == null, "Carrot qualified");
        for (String id : List.of("farmersdelight:baked_cod_stew", "farmersdelight:honey_glazed_ham",
                "twilightdelight:plate_of_lily_chicken", "ends_delight:dragon_breath_and_chorus_soup")) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            require(id.equals(approvedMeal(stack, null)), "High-diversity food rejected: " + id);
            require(rememberMeal(diner, id) && !rememberMeal(diner, id), "Dish credited twice: " + id);
        }
        require(approvedMeal(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mynethersdelight:nether_burger"))), null) == null,
                "Lower-diversity Nether Burger qualified");
        GhostJirachiEyeAspect.selfTest();
        GhostJirachiEyeAspect.selfTestPokemon();
        testFaintEvent(ActorType.WILD,true,true,1);
        testFaintEvent(ActorType.NPC,true,true,1);
        testFaintEvent(ActorType.PLAYER,true,true,0);
        testFaintEvent(ActorType.WILD,false,true,0);
        testFaintEvent(ActorType.WILD,true,false,0);
        testFaintEvent(ActorType.WILD,true,1,"eevee","gastly","unyielding_eevee_gastly_defeats");
        testFaintEvent(ActorType.WILD,true,0,"eevee","haunter","unyielding_eevee_gastly_defeats");
        testFaintEvent(ActorType.NPC,true,1,"eevee","rattata","unyielding_eevee_normal_defeats");
        testFaintEvent(ActorType.WILD,true,1,"eevee","caterpie","unyielding_eevee_bug_defeats");
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
            int maximum = defeatGoal(key);
            set(pokemon,key,maximum);
            require(creditDefeat(pokemon)==maximum,"Counter overflow");
            set(pokemon,key,3);
            require(value(pokemon,key)==3,"Missing species feature assignment for " + name);
            Pokemon restored = new Pokemon().loadFromNBT(registry,pokemon.saveToNBT(registry,new CompoundTag()));
            require(value(restored,key)==3,"Defeat counter not persisted for " + name);
        }
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName("mawile")));
        List<String> canonical = java.util.stream.IntStream.range(0, 49).mapToObj(i -> "test:dish_" + i).toList();
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
        Pokemon sliggoo = testPokemon("sliggoo");
        set(sliggoo,DEFEATS_1000,999);
        require(!evolutionReady(sliggoo,"sliggoo_fairy_sliggoo"),"Fairy Sliggoo bypasses defeat counter");
        set(sliggoo,DEFEATS_1000,1000);
        require(evolutionReady(sliggoo,"sliggoo_fairy_sliggoo"),"Fairy Sliggoo threshold rejected");
        Pokemon corvisquire = testPokemon("corvisquire");
        corvisquire.setGender(com.cobblemon.mod.common.pokemon.Gender.FEMALE);
        set(corvisquire,DEFEATS_1000,999);
        require(!evolutionReady(corvisquire,"corvisquire_covislight"),"Corvislight bypasses defeat counter");
        set(corvisquire,DEFEATS_1000,1000);
        require(evolutionReady(corvisquire,"corvisquire_covislight"),"Female Corvislight threshold rejected");
        corvisquire.setGender(com.cobblemon.mod.common.pokemon.Gender.MALE);
        require(!evolutionReady(corvisquire,"corvisquire_covislight"),"Male Corvislight evolution accepted");
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
