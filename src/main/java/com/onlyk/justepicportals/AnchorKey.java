package com.onlyk.justepicportals;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** The identity prevents an old PAC or link from targeting a replacement anchor at the same coordinates. */
public record AnchorKey(ResourceKey<Level> dimension, BlockPos position, UUID identity) {
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("position", position.asLong());
        tag.putUUID("identity", identity);
        return tag;
    }
    public static Optional<AnchorKey> load(CompoundTag tag) {
        var location = ResourceLocation.tryParse(tag.getString("dimension"));
        if (location == null || !tag.contains("position") || !tag.hasUUID("identity")) return Optional.empty();
        return Optional.of(new AnchorKey(ResourceKey.create(Registries.DIMENSION, location),
                BlockPos.of(tag.getLong("position")), tag.getUUID("identity")));
    }
}
