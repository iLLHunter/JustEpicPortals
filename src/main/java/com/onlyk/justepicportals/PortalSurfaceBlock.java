package com.onlyk.justepicportals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public final class PortalSurfaceBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public PortalSurfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(COLOR, DyeColor.LIME));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(PortalSurfaceBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS, COLOR); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PortalSurfaceBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Block.box(7, 0, 0, 9, 16, 16);
            case Y -> Block.box(0, 7, 0, 16, 9, 16);
            case Z -> Block.box(0, 0, 7, 16, 16, 9);
        };
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity.getBoundingBox().intersects(getShape(state, level, pos, CollisionContext.empty()).bounds().move(pos))
                && level.getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface) {
            var anchor = surface.anchor();
            if (anchor != null) PortalService.touch(anchor, entity);
        }
    }
    public static DyeColor dye(ItemStack stack) {
        if (stack.getItem() instanceof DyeItem item) return item.getDyeColor();
        // Check all sixteen common dye tags, including black (omitted by the upstream helper in 21.1.243).
        for (DyeColor color : DyeColor.values()) if (stack.is(color.getTag())) return color;
        return null;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        var color = dye(stack);
        var effect = PortalEffect.fromItem(stack);
        if (color == null && effect == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface) {
            var anchor = surface.anchor();
            if (anchor == null || anchor.material().disabled() || anchor.activeFrame() == null) return ItemInteractionResult.FAIL;
            if (effect != null) {
                if (anchor.effect() != effect) {
                    anchor.effect(effect);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
            } else if (anchor.color() != color) {
                anchor.color(color);
                PortalService.paint(anchor);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState adjacent, LevelAccessor level, BlockPos pos, BlockPos next) {
        level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface)) { level.removeBlock(pos, false); return; }
        var anchor = surface.anchor();
        if (anchor == null || anchor.activeFrame() == null || !anchor.activeFrame().contains(pos) || anchor.material().disabled()
                || anchor.findFrame().isEmpty()) level.removeBlock(pos, false);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        int rgb = state.getValue(COLOR).getTextureDiffuseColor();
        var particle = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), .55f);
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        switch (state.getValue(AXIS)) {
            case X -> x = pos.getX() + .5;
            case Y -> y = pos.getY() + .5;
            case Z -> z = pos.getZ() + .5;
        }
        level.addParticle(particle, x, y, z, 0, .01, 0);
    }
}
