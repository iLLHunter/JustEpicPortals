package com.onlyk.justepicportals;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PortalSurfaceBlockEntity extends BlockEntity {
    private BlockPos owner;
    private UUID identity;
    public PortalSurfaceBlockEntity(BlockPos pos, BlockState state) { super(JustEpicPortals.SURFACE_ENTITY.get(), pos, state); }
    public void owner(PortalAnchorBlockEntity anchor) { owner = anchor.getBlockPos(); identity = anchor.identity(); setChanged(); }
    public PortalAnchorBlockEntity anchor() {
        if (owner == null || level == null || !level.hasChunkAt(owner)) return null;
        return level.getBlockEntity(owner) instanceof PortalAnchorBlockEntity anchor && anchor.identity().equals(identity) ? anchor : null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putLong("owner", owner.asLong());
        if (identity != null) tag.putUUID("identity", identity);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains("owner") ? BlockPos.of(tag.getLong("owner")) : null;
        identity = tag.hasUUID("identity") ? tag.getUUID("identity") : null;
    }
}
