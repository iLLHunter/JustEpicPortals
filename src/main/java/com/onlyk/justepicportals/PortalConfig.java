package com.onlyk.justepicportals;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;

/** Keeps disable rules fixed until server restart; the PAC recipe can change on a data reload. */
public final class PortalConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue CHEAP;
    private static final ModConfigSpec.BooleanValue STONE;
    private static final ModConfigSpec.BooleanValue NETHERRACK;
    private static final ModConfigSpec.BooleanValue END_STONE;
    private static final ModConfigSpec.BooleanValue DIMENSIONAL;
    private static final ModConfigSpec.BooleanValue NETHER_BRICKS;
    private static final ModConfigSpec.BooleanValue RED_NETHER_BRICKS;
    private static final ModConfigSpec.BooleanValue SMOOTH_STONE;
    private static final ModConfigSpec.BooleanValue BRICKS;
    private static final ModConfigSpec.BooleanValue BEDROCK;
    private static final ModConfigSpec.BooleanValue CUT_SANDSTONE;
    private static final ModConfigSpec.BooleanValue GILDED_BLACKSTONE;
    private static final ModConfigSpec.BooleanValue AMETHYST_BLOCK;
    private static final ModConfigSpec.BooleanValue WAXED_CUT_COPPER;
    private static final ModConfigSpec.BooleanValue WAXED_OXIDIZED_CUT_COPPER;
    private static final ModConfigSpec.BooleanValue EMERALD_BLOCK;
    private static final ModConfigSpec.BooleanValue DIAMOND_BLOCK;
    private static final ModConfigSpec.BooleanValue HONEYCOMB_BLOCK;
    private static final ModConfigSpec.BooleanValue RED_MUSHROOM_BLOCK;
    private static final ModConfigSpec.BooleanValue BROWN_MUSHROOM_BLOCK;

    private static final ModConfigSpec.BooleanValue GLOWSTONE;

    private static final ModConfigSpec.BooleanValue OBSIDIAN;
    private static final ModConfigSpec.BooleanValue CRYING_OBSIDIAN;
    private static final ModConfigSpec.BooleanValue PURPUR_PILLAR;
    private static final ModConfigSpec.BooleanValue PURPUR_BLOCK;
    private static final ModConfigSpec.BooleanValue PRISMARINE_BRICKS;
    private static final ModConfigSpec.BooleanValue DARK_PRISMARINE;
    private static final ModConfigSpec.BooleanValue CHISELED_QUARTZ_BLOCK;
    private static final ModConfigSpec.BooleanValue QUARTZ_PILLAR;
    private static final ModConfigSpec.BooleanValue SEA_LANTERN;

    private static final ModConfigSpec.IntValue MINIMUM_OPERATOR_LEVEL;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MATERIAL_REQUIREMENTS;
    private static volatile int minimumOperatorLevel=2;

    public record Rules(boolean cheapRecipe,
                        boolean disableStone,
                        boolean disableNetherrack,
                        boolean disableEndStone,
                        boolean disableDimensional,
                        boolean disableNetherBricks,
                        boolean disableRedNetherBricks,
                        boolean disableSmoothStone,
                        boolean disableBricks,
                        boolean disableBedrock,
                        boolean disableCutSandstone,
                        boolean disableGildedBlackstone,
                        boolean disableAmethystBlock,
                        boolean disableWaxedCutCopper,
                        boolean disableWaxedOxidizedCutCopper,
                        boolean disableEmeraldBlock,
                        boolean disableDiamondBlock,
                        boolean disableHoneycombBlock,
                        boolean disableRedMushroomBlock,
                        boolean disableBrownMushroomBlock,
                        boolean disableGlowstone,
                        boolean disableObsidian,
                        boolean disableCryingObsidian,
                        boolean disablePurpurPillar,
                        boolean disablePurpurBlock,
                        boolean disablePrismarineBricks,
                        boolean disableDarkPrismarine,
                        boolean disableChiseledQuartzBlock,
                        boolean disableQuartzPillar,
                        boolean disableSeaLantern) {
        public boolean disabled(PortalMaterial material) {
            return switch(material) {
                case STONE -> disableStone;
                case NETHERRACK -> disableNetherrack;
                case END_STONE -> disableEndStone;
                case NETHER_BRICKS -> disableNetherBricks;
                case RED_NETHER_BRICKS -> disableRedNetherBricks;
                case SMOOTH_STONE -> disableSmoothStone;
                case BRICKS -> disableBricks;
                case BEDROCK -> disableBedrock;
                case CUT_SANDSTONE -> disableCutSandstone;
                case GILDED_BLACKSTONE -> disableGildedBlackstone;
                case AMETHYST_BLOCK -> disableAmethystBlock;
                case WAXED_CUT_COPPER -> disableWaxedCutCopper;
                case WAXED_OXIDIZED_CUT_COPPER -> disableWaxedOxidizedCutCopper;
                case EMERALD_BLOCK -> disableEmeraldBlock;
                case DIAMOND_BLOCK -> disableDiamondBlock;
                case HONEYCOMB_BLOCK -> disableHoneycombBlock;
                case RED_MUSHROOM_BLOCK -> disableRedMushroomBlock;
                case BROWN_MUSHROOM_BLOCK -> disableBrownMushroomBlock;
                case GLOWSTONE -> disableGlowstone;
                case OBSIDIAN -> disableObsidian;
                case CRYING_OBSIDIAN -> disableCryingObsidian;
                case PURPUR_PILLAR -> disablePurpurPillar;
                case PURPUR_BLOCK -> disablePurpurBlock;
                case PRISMARINE_BRICKS -> disablePrismarineBricks;
                case DARK_PRISMARINE -> disableDarkPrismarine;
                case CHISELED_QUARTZ_BLOCK -> disableChiseledQuartzBlock;
                case QUARTZ_PILLAR -> disableQuartzPillar;
                case SEA_LANTERN -> disableSeaLantern;
            };
        }
    }

    private static volatile Rules startup = new Rules(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
    private static volatile Rules synced;

    static {
        var builder = new ModConfigSpec.Builder();
        CHEAP = builder.comment("Use four pearls/powders in the corners instead of eight around the Core Crux.",
                "Run /reload after changing this option.").define("PacRecipeCheap", false);
        STONE = builder.comment("Disable stone portals. Requires a server restart.")
                .worldRestart().define("disableStonePortal", false);
        NETHERRACK = builder.comment("Disable netherrack portals. Requires a server restart.")
                .worldRestart().define("disableNetherrackPortal", false);
        END_STONE = builder.comment("Disable end stone portals. Requires a server restart.")
                .worldRestart().define("disableEndStonePortal", false);
        DIMENSIONAL = builder.comment("Allow travel only within the same dimension. Requires a server restart.")
                .worldRestart().define("disableDimensionalTeleportation", false);
        NETHER_BRICKS = builder.comment("Disable nether bricks portals. Requires a server restart.")
                .worldRestart().define("disableNetherBricks", false);
        RED_NETHER_BRICKS = builder.comment("Disable red nether bricks portals. Requires a server restart.")
                .worldRestart().define("disableRedNetherBricks", false);
        SMOOTH_STONE = builder.comment("Disable smooth stone portals. Requires a server restart.")
                .worldRestart().define("disableSmoothStone", false);
        BRICKS = builder.comment("Disable bricks portals. Requires a server restart.")
                .worldRestart().define("disableBricks", false);
        BEDROCK = builder.comment("Disable bedrock portals. Requires a server restart.")
                .worldRestart().define("disableBedrock", false);
        CUT_SANDSTONE = builder.comment("Disable cut sandstone portals. Requires a server restart.")
                .worldRestart().define("disableCutSandstone", false);
        GILDED_BLACKSTONE = builder.comment("Disable gilded blackstone portals. Requires a server restart.")
                .worldRestart().define("disableGildedBlackstone", false);
        AMETHYST_BLOCK = builder.comment("Disable amethyst block portals. Requires a server restart.")
                .worldRestart().define("disableAmethystBlock", false);
        WAXED_CUT_COPPER = builder.comment("Disable waxed cut copper portals. Requires a server restart.")
                .worldRestart().define("disableWaxedCutCopper", false);
        WAXED_OXIDIZED_CUT_COPPER = builder.comment("Disable waxed oxidized cut copper portals. Requires a server restart.")
                .worldRestart().define("disableWaxedOxidizedCutCopper", false);
        EMERALD_BLOCK = builder.comment("Disable emerald block portals. Requires a server restart.")
                .worldRestart().define("disableEmeraldBlock", false);
        DIAMOND_BLOCK = builder.comment("Disable diamond block portals. Requires a server restart.")
                .worldRestart().define("disableDiamondBlock", false);
        HONEYCOMB_BLOCK = builder.comment("Disable honeycomb block portals. Requires a server restart.")
                .worldRestart().define("disableHoneycombBlock", false);
        RED_MUSHROOM_BLOCK = builder.comment("Disable red mushroom block portals. Requires a server restart.")
                .worldRestart().define("disableRedMushroomBlock", false);
        BROWN_MUSHROOM_BLOCK = builder.comment("Disable brown mushroom block portals. Requires a server restart.")
                .worldRestart().define("disableBrownMushroomBlock", false);
        GLOWSTONE = builder.comment("Disable glowstone portals. Requires a server restart.")
                .worldRestart().define("disableGlowstone", false);
        OBSIDIAN = builder.comment("Disable obsidian portals. Requires a server restart.")
                .worldRestart().define("disableObsidian", false);
        CRYING_OBSIDIAN = builder.comment("Disable crying obsidian portals. Requires a server restart.")
                .worldRestart().define("disableCryingObsidian", false);
        PURPUR_PILLAR = builder.comment("Disable purpur pillar portals. Requires a server restart.")
                .worldRestart().define("disablePurpurPillar", false);
        PURPUR_BLOCK = builder.comment("Disable purpur block portals. Requires a server restart.")
                .worldRestart().define("disablePurpurBlock", false);
        PRISMARINE_BRICKS = builder.comment("Disable prismarine bricks portals. Requires a server restart.")
                .worldRestart().define("disablePrismarineBricks", false);
        DARK_PRISMARINE = builder.comment("Disable dark prismarine portals. Requires a server restart.")
                .worldRestart().define("disableDarkPrismarine", false);
        CHISELED_QUARTZ_BLOCK = builder.comment("Disable chiseled quartz block portals. Requires a server restart.")
                .worldRestart().define("disableChiseledQuartzBlock", false);
        QUARTZ_PILLAR = builder.comment("Disable quartz pillar portals. Requires a server restart.")
                .worldRestart().define("disableQuartzPillar", false);
        SEA_LANTERN = builder.comment("Disable sea lantern portals. Requires a server restart.")
                .worldRestart().define("disableSeaLantern", false);
        MINIMUM_OPERATOR_LEVEL = builder.comment("Operator level required to administer anchors (1-4). Requires a server restart.",
                "Allows settings, PAC actions and admin command removal. Normal mining remains owner-only.")
                .translation("config.justepicportals.minimum_operator_level").worldRestart().defineInRange("minimumOperatorLevel",2,1,4);
        MATERIAL_REQUIREMENTS = builder.comment("Advancements every anchor of a material requires before players can travel through it.",
                "Each entry is \"<material>: [all|any] <entry> <entry>...\". An entry is an advancement id, group=<id> for a",
                "Just Epic Portals requirement group, or jed_rule=<id> for a Just Epic Dimensions access rule. all is the default.",
                "Example: [\"bedrock: minecraft:end/kill_dragon\", \"red_nether_bricks: any minecraft:story/enter_the_nether create:andesite_alloy\"]",
                "Applies on /reload or a config file change. Operators who may administer anchors, and Creative or Spectator players, are exempt.")
                .translation("config.justepicportals.material_requirements")
                .defineListAllowEmpty("materialRequirements", java.util.List.of(), () -> "", value -> value instanceof String);
        SPEC = builder.build();
    }

    public static Rules rules() { return startup; }
    public static Rules creativeRules() { return synced == null ? startup : synced; }
    public static void sync(Rules rules) { synced = rules; }
    public static void disconnected() { synced = null; }

    public static void loaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {startup = readForServerStart();minimumOperatorLevel=readMinimumOperatorLevel();PortalRequirements.configure(MATERIAL_REQUIREMENTS.get());}
    }
    public static void reloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {recipeReloading();PortalRequirements.configure(MATERIAL_REQUIREMENTS.get());}
    }
    /** Read straight from the file, so /reload picks up an edit even before the config watcher reports it. */
    public static java.util.List<? extends String> readMaterialRequirements() {
        var path=FMLPaths.CONFIGDIR.get().resolve("justepicportals-common.toml");
        if(java.nio.file.Files.isRegularFile(path))try(var file=CommentedFileConfig.builder(path).sync().build()) {
            file.load();Object value=file.get("materialRequirements");
            if(value instanceof java.util.List<?> list&&list.stream().allMatch(String.class::isInstance))return list.stream().map(String.class::cast).toList();
        } catch (RuntimeException invalidFile) {
            // NeoForge reports the file problem; keep its corrected value.
        }
        if(!SPEC.isLoaded())return java.util.List.of();
        MATERIAL_REQUIREMENTS.clearCache();
        return MATERIAL_REQUIREMENTS.get();
    }
    public static void recipeReloading() {
        var previous = startup;
        startup = new Rules(readForServerStart().cheapRecipe(),
                previous.disableStone(),
                previous.disableNetherrack(),
                previous.disableEndStone(),
                previous.disableDimensional(),
                previous.disableNetherBricks(),
                previous.disableRedNetherBricks(),
                previous.disableSmoothStone(),
                previous.disableBricks(),
                previous.disableBedrock(),
                previous.disableCutSandstone(),
                previous.disableGildedBlackstone(),
                previous.disableAmethystBlock(),
                previous.disableWaxedCutCopper(),
                previous.disableWaxedOxidizedCutCopper(),
                previous.disableEmeraldBlock(),
                previous.disableDiamondBlock(),
                previous.disableHoneycombBlock(),
                previous.disableRedMushroomBlock(),
                previous.disableBrownMushroomBlock(),
                previous.disableGlowstone(),
                previous.disableObsidian(),
                previous.disableCryingObsidian(),
                previous.disablePurpurPillar(),
                previous.disablePurpurBlock(),
                previous.disablePrismarineBricks(),
                previous.disableDarkPrismarine(),
                previous.disableChiseledQuartzBlock(),
                previous.disableQuartzPillar(),
                previous.disableSeaLantern());
    }

    /** Also runs when a singleplayer server starts again without restarting the client process. */
    public static boolean serverStarted() {
        minimumOperatorLevel=readMinimumOperatorLevel();
        Rules next = readForServerStart();
        boolean changed = !next.equals(startup);
        startup = next;
        return changed;
    }

    public static int minimumOperatorLevel() { return minimumOperatorLevel; }
    private static int readMinimumOperatorLevel() {
        var path=FMLPaths.CONFIGDIR.get().resolve("justepicportals-common.toml");
        if(java.nio.file.Files.isRegularFile(path))try(var file=CommentedFileConfig.builder(path).sync().build()) {
            file.load();Object value=file.get("minimumOperatorLevel");
            if(value instanceof Number number)return Math.clamp(number.intValue(),1,4);
        }catch(RuntimeException invalidFile) {}
        MINIMUM_OPERATOR_LEVEL.clearCache();return MINIMUM_OPERATOR_LEVEL.get();
    }

    private static Rules readForServerStart() {
        // Read at the operation boundary rather than waiting for the asynchronous file watcher.
        // Only serverStarted applies disable flags; recipeReloading takes the cheap flag alone.
        var path=FMLPaths.CONFIGDIR.get().resolve("justepicportals-common.toml");
        if (java.nio.file.Files.isRegularFile(path)) {
            try(var file=CommentedFileConfig.builder(path).sync().build()) {
                file.load();
                return new Rules(file.getOrElse("PacRecipeCheap",false),
                        file.getOrElse("disableStonePortal",false),
                        file.getOrElse("disableNetherrackPortal",false),
                        file.getOrElse("disableEndStonePortal",false),
                        file.getOrElse("disableDimensionalTeleportation",false),
                        file.getOrElse("disableNetherBricks",false),
                        file.getOrElse("disableRedNetherBricks",false),
                        file.getOrElse("disableSmoothStone",false),
                        file.getOrElse("disableBricks",false),
                        file.getOrElse("disableBedrock",false),
                        file.getOrElse("disableCutSandstone",false),
                        file.getOrElse("disableGildedBlackstone",false),
                        file.getOrElse("disableAmethystBlock",false),
                        file.getOrElse("disableWaxedCutCopper",false),
                        file.getOrElse("disableWaxedOxidizedCutCopper",false),
                        file.getOrElse("disableEmeraldBlock",false),
                        file.getOrElse("disableDiamondBlock",false),
                        file.getOrElse("disableHoneycombBlock",false),
                        file.getOrElse("disableRedMushroomBlock",false),
                        file.getOrElse("disableBrownMushroomBlock",false),
                        file.getOrElse("disableGlowstone",false),
                        file.getOrElse("disableObsidian",false),
                        file.getOrElse("disableCryingObsidian",false),
                        file.getOrElse("disablePurpurPillar",false),
                        file.getOrElse("disablePurpurBlock",false),
                        file.getOrElse("disablePrismarineBricks",false),
                        file.getOrElse("disableDarkPrismarine",false),
                        file.getOrElse("disableChiseledQuartzBlock",false),
                        file.getOrElse("disableQuartzPillar",false),
                        file.getOrElse("disableSeaLantern",false));
            } catch (RuntimeException invalidFile) {
                // NeoForge owns validation/error reporting; retain its corrected values as fallback.
            }
        }
        CHEAP.clearCache();
        STONE.clearCache();
        NETHERRACK.clearCache();
        END_STONE.clearCache();
        DIMENSIONAL.clearCache();
        NETHER_BRICKS.clearCache();
        RED_NETHER_BRICKS.clearCache();
        SMOOTH_STONE.clearCache();
        BRICKS.clearCache();
        BEDROCK.clearCache();
        CUT_SANDSTONE.clearCache();
        GILDED_BLACKSTONE.clearCache();
        AMETHYST_BLOCK.clearCache();
        WAXED_CUT_COPPER.clearCache();
        WAXED_OXIDIZED_CUT_COPPER.clearCache();
        EMERALD_BLOCK.clearCache();
        DIAMOND_BLOCK.clearCache();
        HONEYCOMB_BLOCK.clearCache();
        RED_MUSHROOM_BLOCK.clearCache();
        BROWN_MUSHROOM_BLOCK.clearCache();
        GLOWSTONE.clearCache();
        OBSIDIAN.clearCache();
        CRYING_OBSIDIAN.clearCache();
        PURPUR_PILLAR.clearCache();
        PURPUR_BLOCK.clearCache();
        PRISMARINE_BRICKS.clearCache();
        DARK_PRISMARINE.clearCache();
        CHISELED_QUARTZ_BLOCK.clearCache();
        QUARTZ_PILLAR.clearCache();
        SEA_LANTERN.clearCache();
        return new Rules(CHEAP.get(),
                STONE.get(),
                NETHERRACK.get(),
                END_STONE.get(),
                DIMENSIONAL.get(),
                NETHER_BRICKS.get(),
                RED_NETHER_BRICKS.get(),
                SMOOTH_STONE.get(),
                BRICKS.get(),
                BEDROCK.get(),
                CUT_SANDSTONE.get(),
                GILDED_BLACKSTONE.get(),
                AMETHYST_BLOCK.get(),
                WAXED_CUT_COPPER.get(),
                WAXED_OXIDIZED_CUT_COPPER.get(),
                EMERALD_BLOCK.get(),
                DIAMOND_BLOCK.get(),
                HONEYCOMB_BLOCK.get(),
                RED_MUSHROOM_BLOCK.get(),
                BROWN_MUSHROOM_BLOCK.get(),
                GLOWSTONE.get(),
                OBSIDIAN.get(),
                CRYING_OBSIDIAN.get(),
                PURPUR_PILLAR.get(),
                PURPUR_BLOCK.get(),
                PRISMARINE_BRICKS.get(),
                DARK_PRISMARINE.get(),
                CHISELED_QUARTZ_BLOCK.get(),
                QUARTZ_PILLAR.get(),
                SEA_LANTERN.get());
    }

    private PortalConfig() {}
}
