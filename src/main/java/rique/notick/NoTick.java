package rique.notick;

import com.google.common.base.Predicates;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ChunkPos;
import rique.notick.api.Tickable;
import rique.notick.integration.FTBChunkClaimProvider;
import rique.notick.integration.IChunkClaimProvider;
import rique.notick.integration.OPACChunkClaimProvider;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

#if FABRIC
    import net.fabricmc.api.ModInitializer;
    import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
    import net.fabricmc.loader.api.FabricLoader;

    #if after_21_1
    import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
    import net.neoforged.fml.config.ModConfig;
    import net.neoforged.neoforge.common.ModConfigSpec;
    import net.neoforged.neoforge.common.ModConfigSpec.*;
    #endif

    #if current_20_1
    import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
    import net.minecraftforge.common.ForgeConfigSpec;
    import net.minecraftforge.common.ForgeConfigSpec.*;
    import net.minecraftforge.fml.config.ModConfig;
    #endif
#endif

#if FORGE
    import net.minecraftforge.common.MinecraftForge;
    import net.minecraftforge.event.RegisterCommandsEvent;
    import net.minecraftforge.event.entity.player.PlayerEvent;
    import net.minecraftforge.fml.ModLoadingContext;
    import net.minecraftforge.fml.common.Mod;
    import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
    import net.minecraftforge.fml.config.ModConfig;
    import net.minecraftforge.fml.event.config.ModConfigEvent;
    import net.minecraftforge.fml.loading.FMLLoader;
    #if current_20_1
    import net.minecraftforge.common.ForgeConfigSpec;
    import net.minecraftforge.common.ForgeConfigSpec.*;
    #endif
#endif


#if NEO
    import net.neoforged.fml.common.Mod;
    import net.neoforged.bus.api.IEventBus;
    import net.neoforged.fml.ModContainer;
    import net.neoforged.fml.config.ModConfig;
    import net.neoforged.fml.event.config.ModConfigEvent;
    import net.neoforged.neoforge.common.NeoForge;
    import net.neoforged.neoforge.common.ModConfigSpec;
    import net.neoforged.neoforge.common.ModConfigSpec.*;
    import net.neoforged.neoforge.event.RegisterCommandsEvent;
    import net.neoforged.neoforge.event.entity.player.PlayerEvent;
    import net.neoforged.fml.loading.FMLLoader;
#endif


#if FORGELIKE
@Mod("no_ticks")
#endif
public class NoTick #if FABRIC implements ModInitializer #endif{
    public static final String MOD_ID = "no_ticks";
    private static final byte UNKNOWN = -1;
    private static final byte FALSE = 0;
    private static final byte TRUE = 1;

    private static final boolean IS_FTB_CHUNKS_PRESENT =
            #if fabric
            FabricLoader.getInstance().isModLoaded("ftbchunks")
            #else
            FMLLoader.getLoadingModList().getModFileById("ftbchunks") != null
            #endif;

    private static final boolean IS_OPAC_PRESENT =
            #if fabric
            FabricLoader.getInstance().isModLoaded("openpartiesandclaims")
            #else
            FMLLoader.getLoadingModList().getModFileById("openpartiesandclaims") != null
            #endif;

    public static final @Nullable IChunkClaimProvider FTB_CLAIM_PROVIDER = IS_FTB_CHUNKS_PRESENT ? new FTBChunkClaimProvider() : null;
    public static final @Nullable IChunkClaimProvider OPAC_CLAIM_PROVIDER = IS_OPAC_PRESENT ? new OPACChunkClaimProvider() : null;

    public static final #if current_20_1 ForgeConfigSpec #else ModConfigSpec #endif COMMON_CONFIG;
    public static final IntValue LIVING_HORIZONTAL_TICK_DIST, LIVING_VERTICAL_TICK_DIST, ACTIVE_CHUNK_RADIUS, ACTIVE_CHUNK_SECONDS_THRESHOLD, ITEM_TICK_CHANCE_PERCENT;
    public static final BooleanValue DISABLE_ON_CLIENT, DISABLE_IN_ACTIVE_CHUNKS, OPTIMIZE_ITEM_MOVEMENT, IGNORE_DEAD_ENTITIES, IGNORE_HOSTILE_ENTITIES, IGNORE_PASSIVE_ENTITIES, TICKING_RAIDER_ENTITIES_IN_RAID, OPTIMIZE_ENTITIES_TICKING, SEND_MESSAGE;
    public static final ConfigValue<List<? extends String>> ENTITIES_WHITELIST, ITEMS_WHITELIST, ENTITIES_MOD_ID_WHITELIST, RAID_ENTITIES_WHITELIST, RAID_ENTITIES_MOD_ID_LIST, DIMENSION_WHITELIST;

    private static final StringSetCache ENTITIES_WHITELIST_CACHE = new StringSetCache();
    private static final StringSetCache ENTITIES_MOD_WHITELIST_CACHE = new StringSetCache();
    private static final StringSetCache RAID_ENTITIES_WHITELIST_CACHE = new StringSetCache();
    private static final StringSetCache RAID_ENTITIES_MOD_WHITELIST_CACHE = new StringSetCache();
    private static final StringSetCache ITEMS_WHITELIST_CACHE = new StringSetCache();
    private static final StringSetCache DIMENSION_WHITELIST_CACHE = new StringSetCache();

    private static final Map<Level, ChunkBoolCache> ACTIVE_CHUNK_CACHE = new WeakHashMap<>();
    private static final Map<Level, ChunkBoolCache> CLAIMED_CHUNK_CACHE = new WeakHashMap<>();

    static {
        List<? extends String> itemList = ObjectArrayList.wrap(new String[]{"minecraft:cobblestone"});
        List<? extends String> entityModIdList = ObjectArrayList.wrap(new String[]{"create", "witherstormmod"});
        List<? extends String> entityWhiteList = ObjectArrayList.wrap(new String[]{
                "minecraft:ender_dragon", "minecraft:ghast", "minecraft:wither", "minecraft:player",
                "alexsmobs:void_worm", "alexsmobs:void_worm_part", "alexsmobs:spectre",
                "twilightforest:naga", "twilightforest:lich", "twilightforest:yeti", "twilightforest:snow_queen", "twilightforest:minoshroom", "twilightforest:hydra", "twilightforest:knight_phantom", "twilightforest:ur_ghast",
                "atum:pharaoh",
                "mowziesmobs:barako", "mowziesmobs:ferrous_wroughtnaut", "mowziesmobs:frostmaw", "mowziesmobs:naga",
                "aoa3:skeletron", "aoa3:smash", "aoa3:baroness", "aoa3:clunkhead", "aoa3:corallus", "aoa3:cotton_candor", "aoa3:craexxeus", "aoa3:xxeus", "aoa3:creep", "aoa3:crystocore", "aoa3:dracyon", "aoa3:graw", "aoa3:gyro", "aoa3:hive_king", "aoa3:kajaros", "aoa3:miskel", "aoa3:harkos", "aoa3:raxxan", "aoa3:okazor", "aoa3:king_bambambam", "aoa3:king_shroomus", "aoa3:kror", "aoa3:mechbot", "aoa3:nethengeic_wither", "aoa3:red_guardian", "aoa3:blue_guardian", "aoa3:green_guardian", "aoa3:yellow_guardian", "aoa3:rock_rider", "aoa3:shadowlord", "aoa3:tyrosaur", "aoa3:vinecorne", "aoa3:visualent", "aoa3:voxxulon", "aoa3:bane", "aoa3:elusive",
                "gaiadimension:malachite_drone", "gaiadimension:malachite_guard",
                "blue_skies:alchemist", "blue_skies:arachnarch", "blue_skies:starlit_crusher", "blue_skies:summoner",
                "stalwart_dungeons:awful_ghast", "stalwart_dungeons:nether_keeper", "stalwart_dungeons:shelterer_without_armor",
                "dungeonsmod:extrapart", "dungeonsmod:king", "dungeonsmod:deserted", "dungeonsmod:crawler", "dungeonsmod:ironslime", "dungeonsmod:kraken", "dungeonsmod:voidmaster", "dungeonsmod:lordskeleton", "dungeonsmod:winterhunter", "dungeonsmod:sun",
                "forestcraft:beequeen", "forestcraft:iguana_king", "forestcraft:cosmic_fiend", "forestcraft:nether_scourge",
                "cataclysm:ender_golem", "cataclysm:ender_guardian", "cataclysm:ignis", "cataclysm:ignited_revenant", "cataclysm:netherite_monstrosity",
                "iceandfire:fire_dragon", "iceandfire:ice_dragon", "iceandfire:lightning_dragon", "iceandfire:dragon_multipart"
        });

        Builder builder = new Builder();
        builder.comment("NoTick").push("Living Entities Tick Settings");
        OPTIMIZE_ENTITIES_TICKING = builder.comment("If you disable this, entities will not stop ticking when they'are far from you, this mod may be useless for you too").define("OptimizeEntitiesTicking", true);
        LIVING_HORIZONTAL_TICK_DIST = builder.defineInRange("LivingEntitiesMaxHorizontalTickDistance", 64, 1, Integer.MAX_VALUE);
        LIVING_VERTICAL_TICK_DIST = builder.defineInRange("LivingEntitiesMaxVerticalTickDistance", 32, 1, Integer.MAX_VALUE);
        ENTITIES_WHITELIST = builder.comment("If you don't want an entity to be affected by the optimization, you can write its registry name down here.").defineList("EntitiesWhitelist", entityWhiteList, Predicates.alwaysTrue());
        ENTITIES_MOD_ID_WHITELIST = builder.comment("If you don't want entities of a mod to be affected by the optimization, you can write its modid down here").defineList("EntitiesModIDWhiteList", entityModIdList, Predicates.alwaysTrue());
        TICKING_RAIDER_ENTITIES_IN_RAID = builder.comment("With this turned on, all the raider won't stop ticking in raid chunks even if they are far from players (well this is not perfect as the raiders may walk out of the raid range)").define("TickRaidersInRaid", true);
        RAID_ENTITIES_WHITELIST = builder.comment("Similar with entity whitelist, but only take effect in raid.").defineList("RaidEntitiesWhiteList", ObjectArrayList.wrap(new String[]{"minecraft:witch", "minecraft:vex"}), Predicates.alwaysTrue());
        RAID_ENTITIES_MOD_ID_LIST = builder.comment("Similar with entity modID whitelist, but only take effect in raid").defineList("RaidEntitiesModIDWhiteList", new ObjectArrayList<>(), Predicates.alwaysTrue());
        DIMENSION_WHITELIST = builder.comment("Leave this empty for applying to all the dimensions", "Entities in these dimensions will be affected by the optimization").defineList("DimensionWhitelist", new ObjectArrayList<>(), Predicates.alwaysTrue());
        IGNORE_DEAD_ENTITIES = builder.comment("If this is enabled, tickable check will run a lot faster, but the entity will not die out of range").define("IgnoreDeadEntities", false);
        IGNORE_HOSTILE_ENTITIES = builder.comment("If this is enabled, this mod will only work on passive entities.").define("IgnoreHostileEntities", false);
        IGNORE_PASSIVE_ENTITIES = builder.comment("If this is enabled, this mod will only work on hostile entities.").define("IgnorePassiveEntities", false);
        ACTIVE_CHUNK_RADIUS = builder.comment("Radius in chunks used for active chunk protection checks. 2 means a 5x5 area around each entity chunk.").defineInRange("ActiveChunkRadius", 2, 0, 16);
        ACTIVE_CHUNK_SECONDS_THRESHOLD = builder.comment("Chunk activity threshold in seconds. Chunks with activity above this value are protected from entity tick skipping.").defineInRange("ActiveChunkSecondsThreshold", 15, 1, Integer.MAX_VALUE);
        builder.pop();
        builder.push("Item Entities Tick Settings");
        OPTIMIZE_ITEM_MOVEMENT = builder.comment("Apply probabilistic ticking to non-whitelisted item entities.").define("OptimizeItemMovement", false);
        ITEM_TICK_CHANCE_PERCENT = builder.comment("Tick chance for non-whitelisted item entities when item optimization is enabled. 75 means items tick on 75% of game ticks.").defineInRange("ItemTickChancePercent", 75, 1, 100);
        ITEMS_WHITELIST = builder.comment("If you don't want to let a specific item entity in the world to be effected by the optimization, you can write its registry name down here.", "Require 'OptimizeItemMovement' to be true").defineList("ItemWhiteList", itemList, Predicates.alwaysTrue());
        builder.pop();
        builder.push("Misc");
        DISABLE_ON_CLIENT = builder.define("DisableOnClient", true);
        SEND_MESSAGE = builder.define("SendWarningMessageWhenPlayerLogIn", true);
        DISABLE_IN_ACTIVE_CHUNKS = builder.comment("If you disable this, entities near player bases may be affected.").define("DisableInActiveChunks", true);
        builder.pop();
        COMMON_CONFIG = builder.build();
    }

    public NoTick(#if NEO IEventBus modEventBus, ModContainer modContainer #endif) {

        #if FORGE
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onForgeConfigChange);
        MinecraftForge.EVENT_BUS.addListener(this::onForgePlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onForgeRegisterCommands);
        #elif NEO
        modContainer.registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
        modEventBus.addListener(this::onNeoConfigChange);
        NeoForge.EVENT_BUS.addListener(this::onNeoPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onNeoRegisterCommands);
        #elif FABRIC
            #if AFTER_21_1
            NeoForgeConfigRegistry.INSTANCE.register(NoTick.MOD_ID, ModConfig.Type.COMMON, COMMON_CONFIG);
            #else
            ForgeConfigRegistry.INSTANCE.register(NoTick.MOD_ID, ModConfig.Type.COMMON, COMMON_CONFIG);
            #endif
        #endif
    }

    #if FABRIC @Override #endif
    public void onInitialize() {
        #if FABRIC
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        #endif
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("notick")
                .then(Commands.literal("status").executes(context -> executeStatusCommand(context.getSource())))
                .executes(context -> executeStatusCommand(context.getSource())));
    }

    private static int executeStatusCommand(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[NoTick] EntityTicking=" + OPTIMIZE_ENTITIES_TICKING.get()
                + ", ItemTicking=" + OPTIMIZE_ITEM_MOVEMENT.get()
                + " (" + ITEM_TICK_CHANCE_PERCENT.get() + "% chance)"), false);
        source.sendSuccess(() -> Component.literal("[NoTick] ActiveChunkProtection=" + DISABLE_IN_ACTIVE_CHUNKS.get()
                + " (radius=" + ACTIVE_CHUNK_RADIUS.get() + ", threshold=" + ACTIVE_CHUNK_SECONDS_THRESHOLD.get() + "s)"), false);
        source.sendSuccess(() -> Component.literal("[NoTick] Integrations: FTBChunks="
                + (FTB_CLAIM_PROVIDER != null) + ", OPAC=" + (OPAC_CLAIM_PROVIDER != null)
                + ", ExternalCAT=" + ChunkActivityTrackerCompat.isExternalAvailable()), false);

        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player player) {
            Level level = player.level();
            BlockPos pos = player.blockPosition();
            ChunkPos chunk = player.chunkPosition();
            boolean optimizableDimension = isOptimizableDim(level);
            boolean claimedChunk = isInClaimedChunk(level, pos);
            boolean activeChunk = isInOrNearActiveChunk(level, chunk);
            source.sendSuccess(() -> Component.literal("[NoTick] Here: dim=" + level.dimension().location()
                    + ", chunk=" + chunk.x + "," + chunk.z
                    + ", dimOptimized=" + optimizableDimension
                    + ", claimed=" + claimedChunk
                    + ", activeProtected=" + activeChunk), false);
        } else {
            source.sendSuccess(() -> Component.literal("[NoTick] Run as a player to view current chunk diagnostics."), false);
        }

        return 1;
    }

    public static boolean isTickable(@NotNull Entity entity) {
        if (entity instanceof Player)
            return true;

        if (!OPTIMIZE_ENTITIES_TICKING.get())
            return true;

        Level level = entity.level();
        if (!isOptimizableDim(level))
            return true;

        if (DISABLE_ON_CLIENT.get() && level.isClientSide)
            return true;

        if (entity instanceof FallingBlockEntity)
            return true;

        if (DISABLE_IN_ACTIVE_CHUNKS.get() && isInOrNearActiveChunk(level, entity.chunkPosition()))
            return true;

        if (entity instanceof LivingEntity) {
            if (!IGNORE_DEAD_ENTITIES.get() && ((LivingEntity) entity).isDeadOrDying())
                return true;

            var isMonster = entity instanceof Monster || entity instanceof Slime;
            if (IGNORE_HOSTILE_ENTITIES.get() && isMonster)
                return true;

            if (IGNORE_PASSIVE_ENTITIES.get() && !isMonster)
                return true;
        }

        if (isOptimizableItemEntity(entity))
            return ThreadLocalRandom.current().nextInt(100) < ITEM_TICK_CHANCE_PERCENT.get();

        BlockPos entityPos = entity.blockPosition();
        if (isInClaimedChunk(level, entityPos))
            return true;

        EntityType<?> entityType = entity.getType();
        if (((Tickable.EntityType) entityType).doespotatotick$shouldAlwaysTick())
            return true;

        if (shouldTickInRaid(level, entityPos, entityType, entity))
            return true;

        return isNearPlayer(level, entityPos);
    }

    private static boolean shouldTickInRaid(Level level, BlockPos blockPos, EntityType<?> entityType, Entity entity) {
        if (level instanceof ServerLevel && ((ServerLevel) level).isRaided(blockPos)) {
            if (entity instanceof Raider) return TICKING_RAIDER_ENTITIES_IN_RAID.get();
            return ((Tickable.EntityType)entityType).doespotatotick$shouldAlwaysTickInRaid();
        }
        return false;
    }

    private static boolean isOptimizableItemEntity(Entity entity) {
        if (!OPTIMIZE_ITEM_MOVEMENT.get()) return false;
        if (entity instanceof ItemEntity) {
            Item item = ((ItemEntity) entity).getItem().getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) return true;
            return !itemWhitelist().contains(itemId.toString());
        }
        return false;
    }

    private static boolean isInClaimedChunk(Level level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkBoolCache cache = getChunkCache(CLAIMED_CHUNK_CACHE, level);
        long key = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        byte state = cache.get(key);
        if (state != UNKNOWN) return state == TRUE;

        boolean flag = false;
        if (FTB_CLAIM_PROVIDER != null)
            flag = FTB_CLAIM_PROVIDER.isInClaimedChunk(level, pos);

        if (OPAC_CLAIM_PROVIDER != null)
            flag = flag || OPAC_CLAIM_PROVIDER.isInClaimedChunk(level, pos);

        cache.put(key, flag);
        return flag;
    }

    private static boolean isOptimizableDim(Level level) {
        Set<String> whitelist = dimensionWhitelist();
        if (whitelist.isEmpty()) return true;
        return whitelist.contains(level.dimension().location().toString());
    }

    private static boolean isNearPlayer(@NotNull Level level, @NotNull BlockPos pos) {
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();
        int maxHeight = LIVING_VERTICAL_TICK_DIST.get();
        int maxDistSquared = LIVING_HORIZONTAL_TICK_DIST.get();
        maxDistSquared *= maxDistSquared;
        for (Player player : level.players()) {
            if (Math.abs(player.getY() - posY) < maxHeight) {
                double x = player.getX() - posX;
                double z = player.getZ() - posZ;
                if ((x * x + z * z) < maxDistSquared) return true;
            }
        }
        return false;
    }

    public static boolean isEntityTypeWhitelisted(@NotNull ResourceLocation id) {
        return entityWhitelist().contains(id.toString()) || entityModWhitelist().contains(id.getNamespace());
    }

    public static boolean isRaidEntityTypeWhitelisted(@NotNull ResourceLocation id) {
        return raidEntityWhitelist().contains(id.toString()) || raidEntityModWhitelist().contains(id.getNamespace());
    }

    private static boolean isInOrNearActiveChunk(Level level, ChunkPos center) {
        ChunkBoolCache cache = getChunkCache(ACTIVE_CHUNK_CACHE, level);
        int radius = ACTIVE_CHUNK_RADIUS.get();
        long thresholdSeconds = ACTIVE_CHUNK_SECONDS_THRESHOLD.get();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = center.x + x;
                int chunkZ = center.z + z;
                long key = ChunkPos.asLong(chunkX, chunkZ);
                byte state = cache.get(key);

                if (state == UNKNOWN) {
                    long secondsInChunk = ChunkActivityTrackerCompat.getTotalTimeInChunk(level, new ChunkPos(chunkX, chunkZ));
                    state = secondsInChunk > thresholdSeconds ? TRUE : FALSE;
                    cache.put(key, state == TRUE);
                }

                if (state == TRUE) return true;
            }
        }

        return false;
    }

    private static ChunkBoolCache getChunkCache(Map<Level, ChunkBoolCache> cacheByLevel, Level level) {
        synchronized (cacheByLevel) {
            ChunkBoolCache cache = cacheByLevel.get(level);
            if (cache == null) {
                cache = new ChunkBoolCache();
                cacheByLevel.put(level, cache);
            }

            long gameTime = level.getGameTime();
            if (cache.gameTime != gameTime) {
                cache.gameTime = gameTime;
                cache.cache.clear();
            }

            return cache;
        }
    }

    private static Set<String> entityWhitelist() {
        return ENTITIES_WHITELIST_CACHE.get(ENTITIES_WHITELIST.get());
    }

    private static Set<String> entityModWhitelist() {
        return ENTITIES_MOD_WHITELIST_CACHE.get(ENTITIES_MOD_ID_WHITELIST.get());
    }

    private static Set<String> raidEntityWhitelist() {
        return RAID_ENTITIES_WHITELIST_CACHE.get(RAID_ENTITIES_WHITELIST.get());
    }

    private static Set<String> raidEntityModWhitelist() {
        return RAID_ENTITIES_MOD_WHITELIST_CACHE.get(RAID_ENTITIES_MOD_ID_LIST.get());
    }

    private static Set<String> itemWhitelist() {
        return ITEMS_WHITELIST_CACHE.get(ITEMS_WHITELIST.get());
    }

    private static Set<String> dimensionWhitelist() {
        return DIMENSION_WHITELIST_CACHE.get(DIMENSION_WHITELIST.get());
    }

    private static void clearCaches() {
        ENTITIES_WHITELIST_CACHE.clear();
        ENTITIES_MOD_WHITELIST_CACHE.clear();
        RAID_ENTITIES_WHITELIST_CACHE.clear();
        RAID_ENTITIES_MOD_WHITELIST_CACHE.clear();
        ITEMS_WHITELIST_CACHE.clear();
        DIMENSION_WHITELIST_CACHE.clear();

        synchronized (ACTIVE_CHUNK_CACHE) {
            ACTIVE_CHUNK_CACHE.clear();
        }
        synchronized (CLAIMED_CHUNK_CACHE) {
            CLAIMED_CHUNK_CACHE.clear();
        }
        ChunkActivityTrackerCompat.clear();
    }

    #if FORGE
    private void onForgeConfigChange(ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MOD_ID) && event.getConfig().getType() == ModConfig.Type.COMMON) {
            clearCaches();
        }
    }
    #endif

    #if NEO
    private void onNeoConfigChange(ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MOD_ID) && event.getConfig().getType() == ModConfig.Type.COMMON) {
            clearCaches();
        }
    }
    #endif

    #if FORGE
    private void onForgeRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }
    #endif

    #if NEO
    private void onNeoRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }
    #endif

    #if FORGE
    private void onForgePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SEND_MESSAGE.get()) return;
        event.getEntity().displayClientMessage(Component.literal(getLoginWarningText()), false);
    }
    #endif

    #if NEO
    private void onNeoPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SEND_MESSAGE.get()) return;
        event.getEntity().displayClientMessage(Component.literal(getLoginWarningText()), false);
    }
    #endif

    private static String getLoginWarningText() {
        if (IS_FTB_CHUNKS_PRESENT) {
            return "NoTick is installed on this server. If your mob farm stops working from far away, claim its chunks with FTB Chunks / OPAC. You can disable this message in NoTick config.";
        }
        return "NoTick is installed but FTB Chunks is not. If your mob farm stops working from far away, install FTB Chunks / OPAC and claim its chunks. You can disable this message in NoTick config.";
    }

    private static final class StringSetCache {
        private List<? extends String> lastSource;
        private Set<String> cached = Set.of();

        private synchronized Set<String> get(List<? extends String> source) {
            if (source == lastSource) return cached;
            lastSource = source;
            HashSet<String> rebuilt = new HashSet<>(source.size());
            for (String entry : source) {
                if (entry != null && !entry.isBlank()) {
                    rebuilt.add(entry);
                }
            }
            cached = rebuilt;
            return cached;
        }

        private synchronized void clear() {
            lastSource = null;
            cached = Set.of();
        }
    }

    private static final class ChunkBoolCache {
        private long gameTime = Long.MIN_VALUE;
        private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap cache = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();

        private ChunkBoolCache() {
            cache.defaultReturnValue(UNKNOWN);
        }

        private byte get(long key) {
            return cache.get(key);
        }

        private void put(long key, boolean value) {
            cache.put(key, value ? TRUE : FALSE);
        }
    }

    private static final class ChunkActivityTrackerCompat {
        private static final MethodHandle GET_TOTAL_TIME_IN_CHUNK = resolve();

        private static long getTotalTimeInChunk(Level level, ChunkPos chunkPos) {
            MethodHandle handle = GET_TOTAL_TIME_IN_CHUNK;
            if (handle == null) {
                return InternalChunkActivityTracker.getTotalTimeInChunk(level, chunkPos);
            }

            try {
                return (long) handle.invoke(level, chunkPos);
            } catch (Throwable ignored) {
                return InternalChunkActivityTracker.getTotalTimeInChunk(level, chunkPos);
            }
        }

        private static void clear() {
            InternalChunkActivityTracker.clear();
        }

        private static boolean isExternalAvailable() {
            return GET_TOTAL_TIME_IN_CHUNK != null;
        }

        private static MethodHandle resolve() {
            try {
                Class<?> clazz = Class.forName("toni.chunkactivitytracker.ChunkActivityTracker");
                MethodType type = MethodType.methodType(long.class, Level.class, ChunkPos.class);
                return MethodHandles.publicLookup().findStatic(clazz, "getTotalTimeInChunk", type);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final class InternalChunkActivityTracker {
        private static final long TICKS_PER_SECOND = 20L;
        private static final long CLEANUP_INTERVAL_TICKS = 20L * 10L;
        private static final long FORGET_AFTER_TICKS = 20L * 60L * 30L;
        private static final Map<Level, LevelState> STATES = new WeakHashMap<>();

        private static long getTotalTimeInChunk(Level level, ChunkPos chunkPos) {
            if (level.isClientSide) return 0L;
            LevelState state = getState(level);
            state.refresh(level);
            return state.getSeconds(chunkPos);
        }

        private static void clear() {
            synchronized (STATES) {
                STATES.clear();
            }
        }

        private static LevelState getState(Level level) {
            synchronized (STATES) {
                LevelState state = STATES.get(level);
                if (state == null) {
                    state = new LevelState();
                    STATES.put(level, state);
                }
                return state;
            }
        }

        private static final class LevelState {
            private long lastProcessedTick = Long.MIN_VALUE;
            private long lastCleanupTick = Long.MIN_VALUE;
            private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap secondsByChunk = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap lastSeenTickByChunk = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

            private LevelState() {
                secondsByChunk.defaultReturnValue(0);
                lastSeenTickByChunk.defaultReturnValue(Long.MIN_VALUE);
            }

            private void refresh(Level level) {
                long now = level.getGameTime();
                if (now == lastProcessedTick) return;
                if (now < lastProcessedTick) {
                    secondsByChunk.clear();
                    lastSeenTickByChunk.clear();
                }
                lastProcessedTick = now;

                if (now % TICKS_PER_SECOND == 0L) {
                    for (Player player : level.players()) {
                        long key = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
                        secondsByChunk.put(key, secondsByChunk.get(key) + 1);
                        lastSeenTickByChunk.put(key, now);
                    }
                }

                if (lastCleanupTick == Long.MIN_VALUE || now - lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
                    cleanup(now);
                    lastCleanupTick = now;
                }
            }

            private long getSeconds(ChunkPos chunkPos) {
                return secondsByChunk.get(ChunkPos.asLong(chunkPos.x, chunkPos.z));
            }

            private void cleanup(long now) {
                var iterator = lastSeenTickByChunk.long2LongEntrySet().fastIterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (now - entry.getLongValue() > FORGET_AFTER_TICKS) {
                        long key = entry.getLongKey();
                        iterator.remove();
                        secondsByChunk.remove(key);
                    }
                }
            }
        }
    }
}
