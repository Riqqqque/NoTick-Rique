package rique.notick;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

#if FABRIC
    import net.fabricmc.api.ModInitializer;
    import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
    import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
    import net.fabricmc.loader.api.FabricLoader;

    #if after_21_1
    import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
    import net.neoforged.fml.config.ConfigTracker;
    import net.neoforged.fml.config.ModConfig;
    import net.neoforged.fml.config.ModConfigs;
    import net.neoforged.neoforge.common.ModConfigSpec;
    import net.neoforged.neoforge.common.ModConfigSpec.*;
    #endif

    #if current_20_1
    import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
    import net.minecraftforge.common.ForgeConfigSpec;
    import net.minecraftforge.common.ForgeConfigSpec.*;
    import net.minecraftforge.fml.config.ConfigTracker;
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
    import net.minecraftforge.fml.config.ConfigTracker;
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
    import net.neoforged.fml.config.ConfigTracker;
    import net.neoforged.fml.config.ModConfig;
    import net.neoforged.fml.config.ModConfigs;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(NoTick.class);
    private static final byte UNKNOWN = -1;
    private static final byte FALSE = 0;
    private static final byte TRUE = 1;
    private static volatile int whitelistRevision;

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
    private static final Map<Level, PlayerSpatialCache> PLAYER_SPATIAL_CACHE = new WeakHashMap<>();

    static {
        List<? extends String> itemList = ObjectArrayList.wrap(new String[]{"minecraft:cobblestone"});
        List<? extends String> entityModIdList = ObjectArrayList.wrap(new String[]{"create", "witherstormmod"});
        List<? extends String> entityWhiteList = ObjectArrayList.wrap(new String[]{
                "minecraft:ender_dragon", "minecraft:ghast", "minecraft:wither", "minecraft:player",
                "minecraft:tnt", "minecraft:end_crystal", "minecraft:area_effect_cloud", "minecraft:evoker_fangs",
                "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident", "minecraft:firework_rocket",
                "minecraft:egg", "minecraft:snowball", "minecraft:llama_spit", "minecraft:eye_of_ender",
                "minecraft:ender_pearl", "minecraft:potion", "minecraft:experience_bottle",
                "minecraft:lightning_bolt", "minecraft:tnt_minecart",
                "minecraft:experience_orb", "minecraft:ominous_item_spawner",
                "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball", "minecraft:wither_skull",
                "minecraft:shulker_bullet", "minecraft:wind_charge", "minecraft:breeze_wind_charge",
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
        OPTIMIZE_ENTITIES_TICKING = builder.comment("When enabled, distant entities can stop ticking when no safety rule requires them to remain active.").define("OptimizeEntitiesTicking", true);
        LIVING_HORIZONTAL_TICK_DIST = builder.defineInRange("LivingEntitiesMaxHorizontalTickDistance", 64, 1, Integer.MAX_VALUE);
        LIVING_VERTICAL_TICK_DIST = builder.defineInRange("LivingEntitiesMaxVerticalTickDistance", 32, 1, Integer.MAX_VALUE);
        ENTITIES_WHITELIST = builder.comment("Entity IDs that must always tick. Leave empty to disable this whitelist.")
                #if AFTER_21_1
                .defineListAllowEmpty("EntitiesWhitelist", entityWhiteList, () -> "minecraft:pig", NoTick::isStringConfigValue);
                #else
                .defineList("EntitiesWhitelist", entityWhiteList, NoTick::isStringConfigValue);
                #endif
        ENTITIES_MOD_ID_WHITELIST = builder.comment("Mod IDs whose entities must always tick. Leave empty to disable this whitelist.")
                #if AFTER_21_1
                .defineListAllowEmpty("EntitiesModIDWhiteList", entityModIdList, () -> "minecraft", NoTick::isStringConfigValue);
                #else
                .defineList("EntitiesModIDWhiteList", entityModIdList, NoTick::isStringConfigValue);
                #endif
        TICKING_RAIDER_ENTITIES_IN_RAID = builder.comment("Keep raiders ticking while they are inside an active raid.").define("TickRaidersInRaid", true);
        List<? extends String> raidEntityWhitelist = ObjectArrayList.wrap(new String[]{"minecraft:witch", "minecraft:vex"});
        RAID_ENTITIES_WHITELIST = builder.comment("Additional entity IDs that must tick while inside an active raid.")
                #if AFTER_21_1
                .defineListAllowEmpty("RaidEntitiesWhiteList", raidEntityWhitelist, () -> "minecraft:witch", NoTick::isStringConfigValue);
                #else
                .defineList("RaidEntitiesWhiteList", raidEntityWhitelist, NoTick::isStringConfigValue);
                #endif
        RAID_ENTITIES_MOD_ID_LIST = builder.comment("Mod IDs whose entities must tick while inside an active raid.")
                #if AFTER_21_1
                .defineListAllowEmpty("RaidEntitiesModIDWhiteList", new ObjectArrayList<>(), () -> "minecraft", NoTick::isStringConfigValue);
                #else
                .defineList("RaidEntitiesModIDWhiteList", new ObjectArrayList<>(), NoTick::isStringConfigValue);
                #endif
        DIMENSION_WHITELIST = builder.comment("Dimensions where optimization is allowed. Leave empty to allow every dimension.")
                #if AFTER_21_1
                .defineListAllowEmpty("DimensionWhitelist", new ObjectArrayList<>(), () -> "minecraft:overworld", NoTick::isStringConfigValue);
                #else
                .defineList("DimensionWhitelist", new ObjectArrayList<>(), NoTick::isStringConfigValue);
                #endif
        IGNORE_DEAD_ENTITIES = builder.comment("Allow dead entities outside protected areas to be skipped. This can delay their removal until the area becomes active again.").define("IgnoreDeadEntities", false);
        IGNORE_HOSTILE_ENTITIES = builder.comment("If this is enabled, this mod will only work on passive entities.").define("IgnoreHostileEntities", false);
        IGNORE_PASSIVE_ENTITIES = builder.comment("If this is enabled, this mod will only work on hostile entities.").define("IgnorePassiveEntities", false);
        ACTIVE_CHUNK_RADIUS = builder.comment("Radius in chunks used for active chunk protection checks. 2 means a 5x5 area around each entity chunk.").defineInRange("ActiveChunkRadius", 2, 0, 16);
        ACTIVE_CHUNK_SECONDS_THRESHOLD = builder.comment("Chunk activity threshold in seconds. Chunks with activity above this value are protected from entity tick skipping.").defineInRange("ActiveChunkSecondsThreshold", 15, 1, Integer.MAX_VALUE);
        builder.pop();
        builder.push("Item Entities Tick Settings");
        OPTIMIZE_ITEM_MOVEMENT = builder.comment("Apply probabilistic ticking to non-whitelisted item entities outside player range.").define("OptimizeItemMovement", false);
        ITEM_TICK_CHANCE_PERCENT = builder.comment("Tick chance for non-whitelisted item entities when item optimization is enabled. 75 means items tick on 75% of game ticks.").defineInRange("ItemTickChancePercent", 75, 1, 100);
        ITEMS_WHITELIST = builder.comment("Item IDs that must always tick when item optimization is enabled. Leave empty to disable this whitelist.")
                #if AFTER_21_1
                .defineListAllowEmpty("ItemWhiteList", itemList, () -> "minecraft:cobblestone", NoTick::isStringConfigValue);
                #else
                .defineList("ItemWhiteList", itemList, NoTick::isStringConfigValue);
                #endif
        builder.pop();
        builder.push("Misc");
        DISABLE_ON_CLIENT = builder.define("DisableOnClient", true);
        SEND_MESSAGE = builder.define("SendWarningMessageWhenPlayerLogIn", true);
        DISABLE_IN_ACTIVE_CHUNKS = builder.comment("If you disable this, entities near player bases may be affected.").define("DisableInActiveChunks", true);
        builder.pop();
        COMMON_CONFIG = builder.build();
    }

    private static boolean isStringConfigValue(Object value) {
        return value instanceof String;
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendLoginWarning(handler.player));
        #endif
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("notick")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("help").executes(context -> executeHelpCommand(context.getSource())))
                .then(Commands.literal("here").executes(context -> executeHereCommand(context.getSource())))
                .then(Commands.literal("reload").executes(context -> executeReloadCommand(context.getSource())))
                .then(Commands.literal("status").executes(context -> executeStatusCommand(context.getSource())))
                .executes(context -> executeStatusCommand(context.getSource())));
    }

    private static int executeStatusCommand(CommandSourceStack source) {
        boolean entityOptimizationEnabled = OPTIMIZE_ENTITIES_TICKING.get();
        boolean itemOptimizationConfigured = OPTIMIZE_ITEM_MOVEMENT.get();
        boolean itemOptimizationEnabled = entityOptimizationEnabled && itemOptimizationConfigured;
        MutableComponent itemOptimizationState = enabledDisabledComponent(itemOptimizationEnabled);
        if (itemOptimizationEnabled) {
            itemOptimizationState.append(Component.literal(" (" + ITEM_TICK_CHANCE_PERCENT.get() + "% chance)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        sendCommandLine(source, commandHeader("Status", "Server tick optimization"));
        sendCommandLine(source, stateLine("Entity optimization",
                enabledDisabledComponent(entityOptimizationEnabled),
                entityOptimizationEnabled
                        ? "distant, unprotected entities can be skipped"
                        : "all entities tick normally"));
        sendCommandLine(source, stateLine("Distant item optimization",
                itemOptimizationState,
                itemOptimizationEnabled
                        ? "only applies outside player/protected ranges"
                        : itemOptimizationConfigured
                                ? "waiting for entity optimization to be enabled"
                                : "dropped items tick normally"));
        sendCommandLine(source, stateLine("Player safe range",
                Component.literal(LIVING_HORIZONTAL_TICK_DIST.get() + " blocks horizontal / " + LIVING_VERTICAL_TICK_DIST.get() + " blocks vertical")
                        .withStyle(ChatFormatting.AQUA),
                "entities inside this range always tick"));
        sendCommandLine(source, stateLine("Active chunk protection",
                enabledDisabledComponent(DISABLE_IN_ACTIVE_CHUNKS.get())
                        .append(Component.literal(" (radius " + ACTIVE_CHUNK_RADIUS.get() + ", " + ACTIVE_CHUNK_SECONDS_THRESHOLD.get() + "s)").withStyle(ChatFormatting.DARK_GRAY)),
                DISABLE_IN_ACTIVE_CHUNKS.get()
                        ? "recently used chunks stay active"
                        : "recent activity is ignored"));
        sendCommandLine(source, stateLine(
                "Claim integrations",
                integrationLine(),
                "claimed chunks stay active when supported"));
        sendCommandLine(source, infoLine(
                "Next checks",
                "/notick here for this chunk, /notick reload after config edits"));

        if (source.getEntity() instanceof Player) {
            sendCommandLine(source, infoLine("Tip", "stand near a farm and run /notick here to see why it is protected"));
        } else {
            sendCommandLine(source, infoLine("Tip", "run /notick here in-game for local chunk protection checks"));
        }

        return 1;
    }

    private static int executeHereCommand(CommandSourceStack source) {
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof Player player)) {
            sendCommandLine(source, commandHeader("Here", "Local chunk diagnostics"));
            sendCommandLine(source, infoLine("Unavailable", "This command must be run by a player"));
            return 0;
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        boolean entityOptimizationEnabled = OPTIMIZE_ENTITIES_TICKING.get();
        boolean optimizableDimension = isOptimizableDim(level);
        boolean claimedChunk = isInClaimedChunk(level, pos);
        boolean activeChunkProtectionEnabled = DISABLE_IN_ACTIVE_CHUNKS.get();
        boolean activeChunk = activeChunkProtectionEnabled && isInOrNearActiveChunk(level, chunk);

        sendCommandLine(source, commandHeader("Here", "Current chunk diagnostics"));
        sendCommandLine(source, stateLine("Location",
                Component.literal(level.dimension().location() + " / chunk " + chunk.x + ", " + chunk.z).withStyle(ChatFormatting.AQUA),
                "your current server position"));
        sendCommandLine(source, stateLine(
                "Entity optimization",
                enabledDisabledComponent(entityOptimizationEnabled),
                entityOptimizationEnabled ? "distant entities can be evaluated" : "all entities tick normally"));
        sendCommandLine(source, stateLine(
                "Dimension allowed",
                yesNoComponent(optimizableDimension),
                dimensionStatusText(optimizableDimension)));
        sendCommandLine(source, stateLine(
                "Claim protection",
                yesNoComponent(claimedChunk),
                protectionText(claimedChunk, "claimed chunks force normal ticking")));
        sendCommandLine(source, stateLine(
                "Active chunk protection",
                yesNoComponent(activeChunk),
                activeChunkProtectionEnabled
                        ? protectionText(activeChunk, "recent player activity keeps this area active")
                        : "disabled in config"));
        sendCommandLine(source, stateLine("Nearby player range",
                Component.literal(LIVING_HORIZONTAL_TICK_DIST.get() + " horizontal / " + LIVING_VERTICAL_TICK_DIST.get() + " vertical")
                        .withStyle(ChatFormatting.AQUA),
                "entities near players always tick"));
        sendCommandLine(source, infoLine("Result", hereResultText(entityOptimizationEnabled, optimizableDimension, claimedChunk, activeChunk)));

        return 1;
    }

    private static int executeHelpCommand(CommandSourceStack source) {
        sendCommandLine(source, commandHeader("Help", "Available admin commands"));
        sendCommandLine(source, infoLine("/notick", "show current optimization status"));
        sendCommandLine(source, infoLine("/notick status", "show current optimization status"));
        sendCommandLine(source, infoLine("/notick here", "show current chunk diagnostics"));
        sendCommandLine(source, infoLine("/notick reload", "reload the config from disk"));
        sendCommandLine(source, infoLine("/notick help", "show this help page"));

        return 1;
    }

    private static int executeReloadCommand(CommandSourceStack source) {
        sendCommandLine(source, commandHeader("Reload", "Refreshing config from disk"));

        try {
            int reloadedConfigs = reloadCommonConfigs();
            if (reloadedConfigs <= 0) {
                sendCommandLine(source, infoLine("Unavailable", "No loaded NoTick common config was found"));
                return 0;
            }

            clearCaches();
            sendCommandLine(source, stateLine("Reloaded config files",
                    Component.literal(String.valueOf(reloadedConfigs)).withStyle(ChatFormatting.GREEN),
                    "disk config values were re-read"));
            sendCommandLine(source, infoLine("Result", "runtime caches were cleared and rebuilt on the next tick check"));
            return 1;
        } catch (Exception exception) {
            LOGGER.error("Failed to reload NoTick config from disk", exception);

            String errorMessage = exception.getMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = exception.getClass().getSimpleName();
            }

            sendCommandLine(source, labeledLine("Reload failed", Component.literal(errorMessage).withStyle(ChatFormatting.RED)));
            return 0;
        }
    }

    private static void sendCommandLine(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    private static MutableComponent commandHeader(String title, String subtitle) {
        return Component.literal("NoTick ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" - " + subtitle).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent labeledLine(String label, Component value) {
        return Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }

    private static MutableComponent stateLine(String label, Component value, String explanation) {
        return labeledLine(label, value)
                .append(Component.literal(" - " + explanation).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent infoLine(String commandOrLabel, String description) {
        return Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(commandOrLabel).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent enabledDisabledComponent(boolean value) {
        return Component.literal(value ? "Enabled" : "Disabled")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent yesNoComponent(boolean value) {
        return Component.literal(value ? "Yes" : "No")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent integrationLine() {
        return Component.empty()
                .append(Component.literal("FTB Chunks ").withStyle(ChatFormatting.GRAY))
                .append(integrationStatusComponent(FTB_CLAIM_PROVIDER))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("OPAC ").withStyle(ChatFormatting.GRAY))
                .append(integrationStatusComponent(OPAC_CLAIM_PROVIDER))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("External CAT ").withStyle(ChatFormatting.GRAY))
                .append(yesNoComponent(ChunkActivityTrackerCompat.isExternalAvailable()));
    }

    private static MutableComponent integrationStatusComponent(@Nullable IChunkClaimProvider provider) {
        if (provider == null) {
            return Component.literal("Not installed").withStyle(ChatFormatting.GRAY);
        }
        if (provider.isOperational()) {
            return Component.literal("Ready").withStyle(ChatFormatting.GREEN);
        }
        return Component.literal("Fail-safe").withStyle(ChatFormatting.YELLOW);
    }

    private static String dimensionStatusText(boolean optimizableDimension) {
        return optimizableDimension
                ? "this dimension allows optimization"
                : "this dimension is excluded by config";
    }

    private static String protectionText(boolean protectedHere, String protectedMessage) {
        return protectedHere ? protectedMessage : "not protecting this chunk right now";
    }

    private static String hereResultText(boolean entityOptimizationEnabled, boolean optimizableDimension, boolean claimedChunk, boolean activeChunk) {
        if (!entityOptimizationEnabled) {
            return "entity optimization is disabled; all entities tick normally";
        }
        if (!optimizableDimension) {
            return "entities in this dimension tick normally";
        }
        if (claimedChunk || activeChunk) {
            return "this chunk is protected from distant tick skipping";
        }
        return "when no player is nearby, distant non-whitelisted entities here can be skipped";
    }

    private static int reloadCommonConfigs() throws Exception {
        int reloadedConfigs = 0;

        #if current_20_1
        ModConfig commonConfig = findLegacyCommonConfig();
        if (commonConfig == null) return 0;

        reloadLegacyCommonConfig(commonConfig);
        reloadedConfigs++;
        #else
        for (ModConfig modConfig : ModConfigs.getModConfigs(MOD_ID)) {
            if (modConfig.getType() != ModConfig.Type.COMMON) continue;

            reloadModernCommonConfig(modConfig);
            reloadedConfigs++;
        }
        #endif

        return reloadedConfigs;
    }

    #if current_20_1
    private static void reloadLegacyCommonConfig(ModConfig config) throws Exception {
        var configData = config.getConfigData();
        if (configData == null) {
            throw new IllegalStateException("NoTick common config is not loaded");
        }

        Method load = Class.forName("com.electronwill.nightconfig.core.file.FileConfig").getMethod("load");
        invokeConfigTracker(load, configData);

        var spec = config.getSpec();
        if (!spec.isCorrect(configData)) {
            LOGGER.warn("[NoTick] Correcting invalid values in {}", config.getFullPath());
            spec.correct(configData);
            config.save();
        }
        spec.afterReload();
    }
    #else
    private static void reloadModernCommonConfig(ModConfig config) throws Exception {
        Path configBasePath = config.getFullPath().getParent();
        Method closeConfig = findModernCloseConfigMethod();
        Method openConfig = ConfigTracker.class.getDeclaredMethod("openConfig", ModConfig.class, Path.class, Path.class);
        openConfig.setAccessible(true);
        invokeConfigTracker(closeConfig, null, config);
        invokeConfigTracker(openConfig, null, config, configBasePath, null);
    }

    private static Method findModernCloseConfigMethod() throws NoSuchMethodException {
        try {
            Method method = ConfigTracker.class.getDeclaredMethod("unloadConfig", ModConfig.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = ConfigTracker.class.getDeclaredMethod("closeConfig", ModConfig.class);
            method.setAccessible(true);
            return method;
        }
    }
    #endif

    private static void invokeConfigTracker(Method method, Object target, Object... args) throws Exception {
        try {
            method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    #if current_20_1
    private static @Nullable ModConfig findLegacyCommonConfig() {
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get(MOD_ID + "-common.toml");
        if (config != null && config.getType() == ModConfig.Type.COMMON && MOD_ID.equals(config.getModId())) {
            return config;
        }

        for (ModConfig trackedConfig : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON)) {
            if (MOD_ID.equals(trackedConfig.getModId())) {
                return trackedConfig;
            }
        }

        return null;
    }
    #endif

    public static boolean isTickable(@NotNull Entity entity) {
        if (entity instanceof Player player) {
            if (OPTIMIZE_ENTITIES_TICKING.get() && DISABLE_IN_ACTIVE_CHUNKS.get()) {
                ChunkActivityTrackerCompat.recordPlayerActivity(player);
            }
            return true;
        }

        if (!OPTIMIZE_ENTITIES_TICKING.get())
            return true;

        Level level = entity.level();
        if (DISABLE_ON_CLIENT.get() && level.isClientSide)
            return true;

        if (!isOptimizableDim(level))
            return true;

        if (entity instanceof FallingBlockEntity)
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

        EntityType<?> entityType = entity.getType();
        if (((Tickable.EntityType) entityType).notick$shouldAlwaysTick())
            return true;

        if (hasAlwaysTickingPassenger(entity))
            return true;

        boolean optimizeItemEntity = false;
        if (entity instanceof ItemEntity itemEntity) {
            optimizeItemEntity = shouldOptimizeItemEntity(itemEntity);
            if (!optimizeItemEntity)
                return true;
        }

        BlockPos entityPos = entity.blockPosition();
        boolean nearPlayer = isNearPlayer(level, entityPos);
        if (nearPlayer)
            return true;

        if (DISABLE_IN_ACTIVE_CHUNKS.get() && isInOrNearActiveChunk(level, entity.chunkPosition()))
            return true;

        if (isInClaimedChunk(level, entityPos))
            return true;

        if (optimizeItemEntity) {
            int tickChance = ITEM_TICK_CHANCE_PERCENT.get();
            return tickChance >= 100 || ThreadLocalRandom.current().nextInt(100) < tickChance;
        }

        if (shouldTickInRaid(level, entityPos, entityType, entity))
            return true;

        return false;
    }

    private static boolean shouldTickInRaid(Level level, BlockPos blockPos, EntityType<?> entityType, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        boolean affectedByRaidRules = (entity instanceof Raider && TICKING_RAIDER_ENTITIES_IN_RAID.get())
                || ((Tickable.EntityType) entityType).notick$shouldAlwaysTickInRaid();
        return affectedByRaidRules && serverLevel.isRaided(blockPos);
    }

    private static boolean hasAlwaysTickingPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player
                    || ((Tickable.EntityType) passenger.getType()).notick$shouldAlwaysTick()
                    || hasAlwaysTickingPassenger(passenger)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldOptimizeItemEntity(ItemEntity entity) {
        if (!OPTIMIZE_ITEM_MOVEMENT.get()) return false;

        Item item = entity.getItem().getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;
        return !itemWhitelist().contains(itemId.toString());
    }

    private static boolean isInClaimedChunk(Level level, BlockPos pos) {
        if (FTB_CLAIM_PROVIDER == null && OPAC_CLAIM_PROVIDER == null) return false;

        ChunkBoolCache cache = getChunkCache(CLAIMED_CHUNK_CACHE, level);
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
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
        int maxDistance = LIVING_HORIZONTAL_TICK_DIST.get();
        long maxDistSquared = (long) maxDistance * maxDistance;
        PlayerSpatialCache playerSpatialCache = getPlayerSpatialCache(level, maxDistance);
        return playerSpatialCache.isNear(posX, posY, posZ, maxHeight, maxDistSquared);
    }

    public static boolean isEntityTypeWhitelisted(@NotNull ResourceLocation id) {
        return entityWhitelist().contains(id.toString()) || entityModWhitelist().contains(id.getNamespace());
    }

    public static boolean isRaidEntityTypeWhitelisted(@NotNull ResourceLocation id) {
        return raidEntityWhitelist().contains(id.toString()) || raidEntityModWhitelist().contains(id.getNamespace());
    }

    public static int getWhitelistRevision() {
        return whitelistRevision;
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
                    long secondsInChunk = ChunkActivityTrackerCompat.getTotalTimeInChunk(level, chunkX, chunkZ);
                    state = secondsInChunk >= thresholdSeconds ? TRUE : FALSE;
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

    private static PlayerSpatialCache getPlayerSpatialCache(Level level, int horizontalDistanceBlocks) {
        synchronized (PLAYER_SPATIAL_CACHE) {
            PlayerSpatialCache cache = PLAYER_SPATIAL_CACHE.get(level);
            if (cache == null) {
                cache = new PlayerSpatialCache();
                PLAYER_SPATIAL_CACHE.put(level, cache);
            }
            cache.refresh(level, horizontalDistanceBlocks);
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
        whitelistRevision++;
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
        synchronized (PLAYER_SPATIAL_CACHE) {
            PLAYER_SPATIAL_CACHE.clear();
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
        sendLoginWarning(event.getEntity());
    }
    #endif

    #if NEO
    private void onNeoPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendLoginWarning(event.getEntity());
    }
    #endif

    private static void sendLoginWarning(Player player) {
        if (!SEND_MESSAGE.get()) return;
        player.sendSystemMessage(Component.literal(getLoginWarningText()));
    }

    private static String getLoginWarningText() {
        if (IS_FTB_CHUNKS_PRESENT || IS_OPAC_PRESENT) {
            return "NoTick is installed on this server. If your mob farm stops working from far away, claim its chunks with your installed chunk-claim mod. You can disable this message in NoTick config.";
        }
        return "NoTick is installed but no supported chunk-claim mod is present. If your mob farm stops working from far away, install FTB Chunks / OPAC and claim its chunks. You can disable this message in NoTick config.";
    }

    private static final class StringSetCache {
        private boolean initialized;
        private Set<String> cached = Set.of();

        private synchronized Set<String> get(List<? extends String> source) {
            if (initialized) return cached;
            initialized = true;
            HashSet<String> rebuilt = new HashSet<>(source.size());
            for (String entry : source) {
                if (entry != null) {
                    String normalized = entry.trim();
                    if (!normalized.isEmpty()) {
                        rebuilt.add(normalized.toLowerCase(Locale.ROOT));
                    }
                }
            }
            cached = rebuilt;
            return cached;
        }

        private synchronized void clear() {
            initialized = false;
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

    private static final class PlayerSpatialCache {
        private static final long BUCKET_CLEANUP_INTERVAL_TICKS = 20L * 60L;

        private long gameTime = Long.MIN_VALUE;
        private long lastBucketCleanupTime = Long.MIN_VALUE;
        private int chunkRadius = -1;
        private int playerCount;
        private final ObjectArrayList<PlayerSnapshot> players = new ObjectArrayList<>();
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<PlayerBucket> playersByChunk = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

        private void refresh(Level level, int horizontalDistanceBlocks) {
            long now = level.getGameTime();
            int nextChunkRadius = horizontalDistanceBlocks > 256
                    ? 17
                    : (horizontalDistanceBlocks + 15) >> 4;
            if (now == gameTime && nextChunkRadius == chunkRadius) return;

            if (now < gameTime) {
                playersByChunk.clear();
                lastBucketCleanupTime = Long.MIN_VALUE;
            }
            gameTime = now;
            chunkRadius = nextChunkRadius;
            playerCount = 0;
            for (Player player : level.players()) {
                PlayerSnapshot snapshot;
                if (playerCount < players.size()) {
                    snapshot = players.get(playerCount);
                } else {
                    snapshot = new PlayerSnapshot();
                    players.add(snapshot);
                }
                snapshot.update(player);
                playerCount++;
            }

            if (playerCount <= 4 || nextChunkRadius > 16) {
                cleanupBuckets(now);
                return;
            }

            for (int index = 0; index < playerCount; index++) {
                PlayerSnapshot player = players.get(index);
                long key = ChunkPos.asLong(player.chunkX, player.chunkZ);
                PlayerBucket bucket = playersByChunk.get(key);
                if (bucket == null) {
                    bucket = new PlayerBucket();
                    playersByChunk.put(key, bucket);
                }
                bucket.add(now, player);
            }
            cleanupBuckets(now);
        }

        private boolean isNear(int posX, int posY, int posZ, int maxHeight, long maxDistSquared) {
            if (playerCount == 0) return false;

            if (playerCount <= 4 || chunkRadius > 16) {
                for (int index = 0; index < playerCount; index++) {
                    if (isNearPlayer(players.get(index), posX, posY, posZ, maxHeight, maxDistSquared)) return true;
                }
                return false;
            }

            int chunkX = posX >> 4;
            int chunkZ = posZ >> 4;
            for (int x = -chunkRadius; x <= chunkRadius; x++) {
                for (int z = -chunkRadius; z <= chunkRadius; z++) {
                    long key = ChunkPos.asLong(chunkX + x, chunkZ + z);
                    PlayerBucket bucket = playersByChunk.get(key);
                    if (bucket == null || bucket.gameTime != gameTime) continue;
                    for (PlayerSnapshot player : bucket.players) {
                        if (isNearPlayer(player, posX, posY, posZ, maxHeight, maxDistSquared)) return true;
                    }
                }
            }
            return false;
        }

        private void cleanupBuckets(long now) {
            if (lastBucketCleanupTime != Long.MIN_VALUE
                    && now - lastBucketCleanupTime < BUCKET_CLEANUP_INTERVAL_TICKS) {
                return;
            }

            var iterator = playersByChunk.long2ObjectEntrySet().fastIterator();
            while (iterator.hasNext()) {
                PlayerBucket bucket = iterator.next().getValue();
                if (bucket.gameTime != now) {
                    iterator.remove();
                }
            }
            lastBucketCleanupTime = now;
        }

        private static boolean isNearPlayer(PlayerSnapshot player, int posX, int posY, int posZ, int maxHeight, long maxDistSquared) {
            if (Math.abs(player.y - posY) > maxHeight) return false;
            double x = player.x - posX;
            double z = player.z - posZ;
            return (x * x + z * z) <= maxDistSquared;
        }
    }

    private static final class PlayerSnapshot {
        private double x;
        private double y;
        private double z;
        private int chunkX;
        private int chunkZ;

        private void update(Player player) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            chunkX = ((int) Math.floor(x)) >> 4;
            chunkZ = ((int) Math.floor(z)) >> 4;
        }
    }

    private static final class PlayerBucket {
        private long gameTime = Long.MIN_VALUE;
        private final ObjectArrayList<PlayerSnapshot> players = new ObjectArrayList<>();

        private void add(long now, PlayerSnapshot player) {
            if (gameTime != now) {
                gameTime = now;
                players.clear();
            }
            players.add(player);
        }
    }

    private static final class ChunkActivityTrackerCompat {
        private static final MethodHandle GET_TOTAL_TIME_IN_CHUNK = resolve();
        private static boolean warnedExternalTrackerFailure;

        private static long getTotalTimeInChunk(Level level, int chunkX, int chunkZ) {
            MethodHandle handle = GET_TOTAL_TIME_IN_CHUNK;
            if (handle == null) {
                return InternalChunkActivityTracker.getTotalTimeInChunk(level, ChunkPos.asLong(chunkX, chunkZ));
            }

            try {
                return (long) handle.invoke(level, new ChunkPos(chunkX, chunkZ));
            } catch (Throwable throwable) {
                if (!warnedExternalTrackerFailure) {
                    warnedExternalTrackerFailure = true;
                    LOGGER.warn("[NoTick] External Chunk Activity Tracker call failed; falling back to internal tracker.", throwable);
                }
                return InternalChunkActivityTracker.getTotalTimeInChunk(level, ChunkPos.asLong(chunkX, chunkZ));
            }
        }

        private static void clear() {
            InternalChunkActivityTracker.clear();
        }

        private static boolean isExternalAvailable() {
            return GET_TOTAL_TIME_IN_CHUNK != null;
        }

        private static void recordPlayerActivity(Player player) {
            InternalChunkActivityTracker.recordPlayerActivity(player);
        }

        private static MethodHandle resolve() {
            try {
                Class<?> clazz = Class.forName("toni.chunkactivitytracker.ChunkActivityTracker");
                MethodType type = MethodType.methodType(long.class, Level.class, ChunkPos.class);
                return MethodHandles.publicLookup().findStatic(clazz, "getTotalTimeInChunk", type);
            } catch (ClassNotFoundException ignored) {
                return null;
            } catch (Throwable throwable) {
                LOGGER.warn("[NoTick] External Chunk Activity Tracker is present but incompatible; internal tracker will be used.", throwable);
                return null;
            }
        }
    }

    private static final class InternalChunkActivityTracker {
        private static final long TICKS_PER_SECOND = 20L;
        private static final long CLEANUP_INTERVAL_TICKS = 20L * 10L;
        private static final long FORGET_AFTER_TICKS = 20L * 60L * 30L;
        private static final Map<Level, LevelState> STATES = new WeakHashMap<>();

        private static long getTotalTimeInChunk(Level level, long chunkKey) {
            if (level.isClientSide) return 0L;
            LevelState state = getState(level);
            state.observeTime(level.getGameTime());
            return state.getSeconds(chunkKey);
        }

        private static void recordPlayerActivity(Player player) {
            Level level = player.level();
            if (level.isClientSide) return;
            getState(level).recordPlayer(player, level.getGameTime());
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
            private long lastObservedTick = Long.MIN_VALUE;
            private long lastCleanupTick = Long.MIN_VALUE;
            private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap secondsByChunk = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap lastSeenTickByChunk = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

            private LevelState() {
                secondsByChunk.defaultReturnValue(0);
                lastSeenTickByChunk.defaultReturnValue(Long.MIN_VALUE);
            }

            private void observeTime(long now) {
                if (now < lastObservedTick) {
                    secondsByChunk.clear();
                    lastSeenTickByChunk.clear();
                    lastCleanupTick = Long.MIN_VALUE;
                }
                lastObservedTick = now;

                if (lastCleanupTick == Long.MIN_VALUE || now - lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
                    cleanup(now);
                    lastCleanupTick = now;
                }
            }

            private void recordPlayer(Player player, long now) {
                observeTime(now);
                if (now % TICKS_PER_SECOND != 0L) return;

                long key = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
                if (lastSeenTickByChunk.get(key) == now) return;

                int seconds = secondsByChunk.get(key);
                if (seconds < Integer.MAX_VALUE) {
                    secondsByChunk.put(key, seconds + 1);
                }
                lastSeenTickByChunk.put(key, now);
            }

            private long getSeconds(long chunkKey) {
                return secondsByChunk.get(chunkKey);
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
