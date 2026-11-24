package com.onlyk.justepicportals;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(JustEpicPortals.ID)
public final class JustEpicPortals {
    public static final String ID = "justepicportals";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,ID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, ID);

    public static final Supplier<Block> STONE_ANCHOR = anchor("stone_portal_anchor", PortalMaterial.STONE);
    public static final Supplier<Block> NETHERRACK_ANCHOR = anchor("netherrack_portal_anchor", PortalMaterial.NETHERRACK);
    public static final Supplier<Block> END_STONE_ANCHOR = anchor("end_stone_portal_anchor", PortalMaterial.END_STONE);
    public static final Supplier<Block> NETHER_BRICKS_ANCHOR = anchor("nether_bricks_portal_anchor", PortalMaterial.NETHER_BRICKS);
    public static final Supplier<Block> RED_NETHER_BRICKS_ANCHOR = anchor("red_nether_bricks_portal_anchor", PortalMaterial.RED_NETHER_BRICKS);
    public static final Supplier<Block> SMOOTH_STONE_ANCHOR = anchor("smooth_stone_portal_anchor", PortalMaterial.SMOOTH_STONE);
    public static final Supplier<Block> BRICKS_ANCHOR = anchor("bricks_portal_anchor", PortalMaterial.BRICKS);
    public static final Supplier<Block> BEDROCK_ANCHOR = anchor("bedrock_portal_anchor", PortalMaterial.BEDROCK);
    public static final Supplier<Block> CUT_SANDSTONE_ANCHOR = anchor("cut_sandstone_portal_anchor", PortalMaterial.CUT_SANDSTONE);
    public static final Supplier<Block> GILDED_BLACKSTONE_ANCHOR = anchor("gilded_blackstone_portal_anchor", PortalMaterial.GILDED_BLACKSTONE);
    public static final Supplier<Block> AMETHYST_BLOCK_ANCHOR = anchor("amethyst_portal_anchor", PortalMaterial.AMETHYST_BLOCK);
    public static final Supplier<Block> WAXED_CUT_COPPER_ANCHOR = anchor("waxed_cut_copper_portal_anchor", PortalMaterial.WAXED_CUT_COPPER);
    public static final Supplier<Block> WAXED_OXIDIZED_CUT_COPPER_ANCHOR = anchor("waxed_oxidized_cut_copper_portal_anchor", PortalMaterial.WAXED_OXIDIZED_CUT_COPPER);
    public static final Supplier<Block> EMERALD_BLOCK_ANCHOR = anchor("emerald_portal_anchor", PortalMaterial.EMERALD_BLOCK);
    public static final Supplier<Block> DIAMOND_BLOCK_ANCHOR = anchor("diamond_portal_anchor", PortalMaterial.DIAMOND_BLOCK);
    public static final Supplier<Block> HONEYCOMB_BLOCK_ANCHOR = anchor("honeycomb_portal_anchor", PortalMaterial.HONEYCOMB_BLOCK);
    public static final Supplier<Block> RED_MUSHROOM_BLOCK_ANCHOR = anchor("red_mushroom_portal_anchor", PortalMaterial.RED_MUSHROOM_BLOCK);
    public static final Supplier<Block> BROWN_MUSHROOM_BLOCK_ANCHOR = anchor("brown_mushroom_portal_anchor", PortalMaterial.BROWN_MUSHROOM_BLOCK);
    public static final Supplier<Block> GLOWSTONE_ANCHOR = anchor("glowstone_portal_anchor", PortalMaterial.GLOWSTONE);
    public static final Supplier<Block> OBSIDIAN_ANCHOR = anchor("obsidian_portal_anchor", PortalMaterial.OBSIDIAN);
    public static final Supplier<Block> CRYING_OBSIDIAN_ANCHOR = anchor("crying_obsidian_portal_anchor", PortalMaterial.CRYING_OBSIDIAN);
    public static final Supplier<Block> PURPUR_PILLAR_ANCHOR = anchor("purpur_pillar_portal_anchor", PortalMaterial.PURPUR_PILLAR);
    public static final Supplier<Block> PURPUR_BLOCK_ANCHOR = anchor("purpur_portal_anchor", PortalMaterial.PURPUR_BLOCK);
    public static final Supplier<Block> PRISMARINE_BRICKS_ANCHOR = anchor("prismarine_bricks_portal_anchor", PortalMaterial.PRISMARINE_BRICKS);
    public static final Supplier<Block> DARK_PRISMARINE_ANCHOR = anchor("dark_prismarine_portal_anchor", PortalMaterial.DARK_PRISMARINE);
    public static final Supplier<Block> CHISELED_QUARTZ_BLOCK_ANCHOR = anchor("chiseled_quartz_portal_anchor", PortalMaterial.CHISELED_QUARTZ_BLOCK);
    public static final Supplier<Block> QUARTZ_PILLAR_ANCHOR = anchor("quartz_pillar_portal_anchor", PortalMaterial.QUARTZ_PILLAR);
    public static final Supplier<Block> SEA_LANTERN_ANCHOR = anchor("sea_lantern_portal_anchor", PortalMaterial.SEA_LANTERN);
    public static final Supplier<Block> SURFACE = BLOCKS.register("portal_surface", () -> new PortalSurfaceBlock(
            BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().strength(-1).noLootTable().lightLevel(state -> 9)));
    public static final Supplier<BlockEntityType<PortalAnchorBlockEntity>> ANCHOR_ENTITY = BLOCK_ENTITIES.register("portal_anchor",
            () -> BlockEntityType.Builder.of(PortalAnchorBlockEntity::new,
                    STONE_ANCHOR.get(),
                    NETHERRACK_ANCHOR.get(),
                    END_STONE_ANCHOR.get(),
                    NETHER_BRICKS_ANCHOR.get(),
                    RED_NETHER_BRICKS_ANCHOR.get(),
                    SMOOTH_STONE_ANCHOR.get(),
                    BRICKS_ANCHOR.get(),
                    BEDROCK_ANCHOR.get(),
                    CUT_SANDSTONE_ANCHOR.get(),
                    GILDED_BLACKSTONE_ANCHOR.get(),
                    AMETHYST_BLOCK_ANCHOR.get(),
                    WAXED_CUT_COPPER_ANCHOR.get(),
                    WAXED_OXIDIZED_CUT_COPPER_ANCHOR.get(),
                    EMERALD_BLOCK_ANCHOR.get(),
                    DIAMOND_BLOCK_ANCHOR.get(),
                    HONEYCOMB_BLOCK_ANCHOR.get(),
                    RED_MUSHROOM_BLOCK_ANCHOR.get(),
                    BROWN_MUSHROOM_BLOCK_ANCHOR.get(),
                    GLOWSTONE_ANCHOR.get(),
                    OBSIDIAN_ANCHOR.get(),
                    CRYING_OBSIDIAN_ANCHOR.get(),
                    PURPUR_PILLAR_ANCHOR.get(),
                    PURPUR_BLOCK_ANCHOR.get(),
                    PRISMARINE_BRICKS_ANCHOR.get(),
                    DARK_PRISMARINE_ANCHOR.get(),
                    CHISELED_QUARTZ_BLOCK_ANCHOR.get(),
                    QUARTZ_PILLAR_ANCHOR.get(),
                    SEA_LANTERN_ANCHOR.get()).build(null));
    public static final Supplier<BlockEntityType<PortalSurfaceBlockEntity>> SURFACE_ENTITY = BLOCK_ENTITIES.register("portal_surface",
            () -> BlockEntityType.Builder.of(PortalSurfaceBlockEntity::new, SURFACE.get()).build(null));
    public static final Supplier<Item> CORE_CRUX = ITEMS.register("core_crux", () -> new Item(new Item.Properties()));
    public static final Supplier<net.minecraft.world.inventory.MenuType<PortalAnchorMenu>> ANCHOR_MENU = MENUS.register("portal_anchor", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(PortalAnchorMenu::new));
    public static final Supplier<Item> PORTAL_ANCHOR_CORE = ITEMS.register("portal_anchor_core", () -> new PortalAnchorCoreItem(new Item.Properties()));
    public static final Supplier<SoundEvent> ASTRAL_HUM = SOUNDS.register("astral_hum", () -> SoundEvent.createVariableRangeEvent(id("astral_hum")));
    public static final Supplier<SoundEvent> ASTRAL_TRAVEL = SOUNDS.register("astral_travel", () -> SoundEvent.createVariableRangeEvent(id("astral_travel")));

    static { CONDITIONS.register("config", () -> RecipeConfigCondition.CODEC); }

    public JustEpicPortals(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        CONDITIONS.register(modBus);
        modBus.addListener(PortalConfig::loaded);
        modBus.addListener(PortalConfig::reloaded);
        modBus.addListener(JustEpicPortals::creativeContents);
        modBus.addListener(PortalNetwork::register);
        modBus.addListener(PortalNetwork::registerTasks);
        PortalService.registerEvents();
        container.registerConfig(ModConfig.Type.COMMON, PortalConfig.SPEC, "justepicportals-common.toml");
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public static Block anchorBlock(PortalMaterial material) {
        return switch(material) {
            case STONE -> STONE_ANCHOR.get();
            case NETHERRACK -> NETHERRACK_ANCHOR.get();
            case END_STONE -> END_STONE_ANCHOR.get();
            case NETHER_BRICKS -> NETHER_BRICKS_ANCHOR.get();
            case RED_NETHER_BRICKS -> RED_NETHER_BRICKS_ANCHOR.get();
            case SMOOTH_STONE -> SMOOTH_STONE_ANCHOR.get();
            case BRICKS -> BRICKS_ANCHOR.get();
            case BEDROCK -> BEDROCK_ANCHOR.get();
            case CUT_SANDSTONE -> CUT_SANDSTONE_ANCHOR.get();
            case GILDED_BLACKSTONE -> GILDED_BLACKSTONE_ANCHOR.get();
            case AMETHYST_BLOCK -> AMETHYST_BLOCK_ANCHOR.get();
            case WAXED_CUT_COPPER -> WAXED_CUT_COPPER_ANCHOR.get();
            case WAXED_OXIDIZED_CUT_COPPER -> WAXED_OXIDIZED_CUT_COPPER_ANCHOR.get();
            case EMERALD_BLOCK -> EMERALD_BLOCK_ANCHOR.get();
            case DIAMOND_BLOCK -> DIAMOND_BLOCK_ANCHOR.get();
            case HONEYCOMB_BLOCK -> HONEYCOMB_BLOCK_ANCHOR.get();
            case RED_MUSHROOM_BLOCK -> RED_MUSHROOM_BLOCK_ANCHOR.get();
            case BROWN_MUSHROOM_BLOCK -> BROWN_MUSHROOM_BLOCK_ANCHOR.get();
            case GLOWSTONE -> GLOWSTONE_ANCHOR.get();
            case OBSIDIAN -> OBSIDIAN_ANCHOR.get();
            case CRYING_OBSIDIAN -> CRYING_OBSIDIAN_ANCHOR.get();
            case PURPUR_PILLAR -> PURPUR_PILLAR_ANCHOR.get();
            case PURPUR_BLOCK -> PURPUR_BLOCK_ANCHOR.get();
            case PRISMARINE_BRICKS -> PRISMARINE_BRICKS_ANCHOR.get();
            case DARK_PRISMARINE -> DARK_PRISMARINE_ANCHOR.get();
            case CHISELED_QUARTZ_BLOCK -> CHISELED_QUARTZ_BLOCK_ANCHOR.get();
            case QUARTZ_PILLAR -> QUARTZ_PILLAR_ANCHOR.get();
            case SEA_LANTERN -> SEA_LANTERN_ANCHOR.get();
        };
    }

    private static Supplier<Block> anchor(String name, PortalMaterial material) {
        Supplier<Block> block = BLOCKS.register(name, () -> {
            var properties = BlockBehaviour.Properties.ofFullCopy(material.frameBlock()).requiresCorrectToolForDrops();
            return material == PortalMaterial.PURPUR_PILLAR || material == PortalMaterial.QUARTZ_PILLAR
                    ? new PillarPortalAnchorBlock(material, properties) : new PortalAnchorBlock(material, properties);
        });
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(CORE_CRUX.get()); event.accept(PORTAL_ANCHOR_CORE.get());
        }
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            var rules = PortalConfig.creativeRules();
            for(var material:PortalMaterial.values()) if(!rules.disabled(material)) event.accept(anchorBlock(material).asItem());
        }
    }
}
