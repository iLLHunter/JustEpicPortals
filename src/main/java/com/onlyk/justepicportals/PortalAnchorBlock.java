package com.onlyk.justepicportals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PortalAnchorBlock extends BaseEntityBlock {
    private final PortalMaterial material;
    public PortalAnchorBlock(PortalMaterial material, Properties properties) {
        super(properties);
        this.material = material;
        // Copy bedrock's hardness, but use the anchor's own drops rather than its empty loot table.
        if(material==PortalMaterial.BEDROCK)this.drops=net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,JustEpicPortals.id("blocks/bedrock_portal_anchor"));
    }
    public PortalMaterial material() { return material; }
    /** A sneak-right-click with any item but a PAC is the item's own use (such as placing a block), not the anchor's. */
    static boolean placesItem(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
        return player.isSecondaryUseActive() && !stack.isEmpty() && !stack.is(JustEpicPortals.PORTAL_ANCHOR_CORE.get());
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new PortalAnchorBlock(material, properties));
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (placesItem(player,stack)) return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor)) return net.minecraft.world.ItemInteractionResult.CONSUME;
        if (stack.is(JustEpicPortals.PORTAL_ANCHOR_CORE.get()) && (player.isShiftKeyDown() || !anchor.containsCore())) {
            stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
        } else if (stack.isEmpty() && player.isShiftKeyDown() && anchor.containsCore()) {
            PortalService.removeCore(anchor,player,hand);
        } else {
            PortalService.openMenu(anchor,player);
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        useItemOn(player.getMainHandItem(),state,level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PortalAnchorBlockEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player player
                && level.getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor) anchor.placedBy(player);
    }
    @Override protected float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player,
                                                  net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor && !anchor.ownedBy(player)) return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, JustEpicPortals.ANCHOR_ENTITY.get(), PortalAnchorBlockEntity::tick);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor) {
            PortalService.anchorRemoved(anchor);
            if (!level.isClientSide) Block.popResource(level,pos,anchor.removeCore());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
