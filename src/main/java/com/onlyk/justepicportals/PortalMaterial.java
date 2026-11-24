package com.onlyk.justepicportals;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum PortalMaterial {
    STONE(Blocks.STONE, "disableStonePortal"),
    NETHERRACK(Blocks.NETHERRACK, "disableNetherrackPortal"),
    END_STONE(Blocks.END_STONE, "disableEndStonePortal"),
    NETHER_BRICKS(Blocks.NETHER_BRICKS, "disableNetherBricks"),
    RED_NETHER_BRICKS(Blocks.RED_NETHER_BRICKS, "disableRedNetherBricks"),
    SMOOTH_STONE(Blocks.SMOOTH_STONE, "disableSmoothStone"),
    BRICKS(Blocks.BRICKS, "disableBricks"),
    BEDROCK(Blocks.BEDROCK, "disableBedrock"),
    CUT_SANDSTONE(Blocks.CUT_SANDSTONE, "disableCutSandstone"),
    GILDED_BLACKSTONE(Blocks.GILDED_BLACKSTONE, "disableGildedBlackstone"),
    AMETHYST_BLOCK(Blocks.AMETHYST_BLOCK, "disableAmethystBlock"),
    WAXED_CUT_COPPER(Blocks.WAXED_CUT_COPPER, "disableWaxedCutCopper"),
    WAXED_OXIDIZED_CUT_COPPER(Blocks.WAXED_OXIDIZED_CUT_COPPER, "disableWaxedOxidizedCutCopper"),
    EMERALD_BLOCK(Blocks.EMERALD_BLOCK, "disableEmeraldBlock"),
    DIAMOND_BLOCK(Blocks.DIAMOND_BLOCK, "disableDiamondBlock"),
    HONEYCOMB_BLOCK(Blocks.HONEYCOMB_BLOCK, "disableHoneycombBlock"),
    RED_MUSHROOM_BLOCK(Blocks.RED_MUSHROOM_BLOCK, "disableRedMushroomBlock"),
    BROWN_MUSHROOM_BLOCK(Blocks.BROWN_MUSHROOM_BLOCK, "disableBrownMushroomBlock"),
    GLOWSTONE(Blocks.GLOWSTONE, "disableGlowstone"),
    OBSIDIAN(Blocks.OBSIDIAN, "disableObsidian"),
    CRYING_OBSIDIAN(Blocks.CRYING_OBSIDIAN, "disableCryingObsidian"),
    PURPUR_PILLAR(Blocks.PURPUR_PILLAR, "disablePurpurPillar"),
    PURPUR_BLOCK(Blocks.PURPUR_BLOCK, "disablePurpurBlock"),
    PRISMARINE_BRICKS(Blocks.PRISMARINE_BRICKS, "disablePrismarineBricks"),
    DARK_PRISMARINE(Blocks.DARK_PRISMARINE, "disableDarkPrismarine"),
    CHISELED_QUARTZ_BLOCK(Blocks.CHISELED_QUARTZ_BLOCK, "disableChiseledQuartzBlock"),
    QUARTZ_PILLAR(Blocks.QUARTZ_PILLAR, "disableQuartzPillar"),
    SEA_LANTERN(Blocks.SEA_LANTERN, "disableSeaLantern");

    private final Block frameBlock;
    private final String disableOption;
    PortalMaterial(Block frameBlock, String disableOption) { this.frameBlock = frameBlock; this.disableOption = disableOption; }
    public Block frameBlock() { return frameBlock; }
    public String disableOption() { return disableOption; }
    public String id() { return switch (this) {
        case AMETHYST_BLOCK -> "amethyst";
        case EMERALD_BLOCK -> "emerald";
        case DIAMOND_BLOCK -> "diamond";
        case HONEYCOMB_BLOCK -> "honeycomb";
        case RED_MUSHROOM_BLOCK -> "red_mushroom";
        case BROWN_MUSHROOM_BLOCK -> "brown_mushroom";
        case PURPUR_BLOCK -> "purpur";
        case CHISELED_QUARTZ_BLOCK -> "chiseled_quartz";
        default -> name().toLowerCase(java.util.Locale.ROOT);
    }; }
    public boolean disabled() { return PortalConfig.rules().disabled(this); }
}
