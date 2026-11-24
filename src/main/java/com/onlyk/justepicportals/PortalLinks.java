package com.onlyk.justepicportals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-owned pair index also updates links whose remote chunks are unloaded. */
public final class PortalLinks extends SavedData {
    private static final Factory<PortalLinks> FACTORY = new Factory<>(PortalLinks::new, PortalLinks::load);
    private final Map<AnchorKey, AnchorKey> links = new HashMap<>();
    /** Destination consent: one token per destination anchor, made when its owner first records it with a PAC. */
    private final Map<AnchorKey, java.util.UUID> consents = new HashMap<>();

    public java.util.UUID authorize(AnchorKey destination) {
        var token = consents.get(destination);
        if (token == null) { token = java.util.UUID.randomUUID(); consents.put(destination, token); setDirty(); }
        return token;
    }
    public boolean authorized(java.util.UUID token, AnchorKey destination) {
        return token != null && token.equals(consents.get(destination));
    }
    public void revokeAuthorizations(AnchorKey destination) {
        if (consents.remove(destination) != null) setDirty();
    }

    public static PortalLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "justepicportals_links");
    }
    public Optional<AnchorKey> destination(AnchorKey source) { return Optional.ofNullable(links.get(source)); }
    public boolean paired(AnchorKey first, AnchorKey second) {
        return second.equals(links.get(first)) && first.equals(links.get(second));
    }
    public void unlink(AnchorKey key) {
        AnchorKey previous = links.remove(key);
        if (previous != null) {
            if (key.equals(links.get(previous))) links.remove(previous);
            setDirty();
        }
    }
    public void link(AnchorKey first, AnchorKey second) {
        unlink(first); unlink(second);
        links.put(first, second); links.put(second, first);
        setDirty();
    }
    public void invalidateDimensionalLinks() {
        if (links.entrySet().removeIf(entry -> !entry.getKey().dimension().equals(entry.getValue().dimension()))) setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        links.forEach((from, to) -> {
            var pair = new CompoundTag(); pair.put("from", from.save()); pair.put("to", to.save()); entries.add(pair);
        });
        tag.put("links", entries);
        var grants = new ListTag();
        consents.forEach((destination,token) -> {
            var grant = new CompoundTag(); grant.putUUID("token",token); grant.put("destination",destination.save()); grants.add(grant);
        });
        tag.put("authorizations",grants);
        return tag;
    }
    public static PortalLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PortalLinks();
        for (Tag element : tag.getList("links", Tag.TAG_COMPOUND)) {
            var pair = (CompoundTag) element;
            var from = AnchorKey.load(pair.getCompound("from"));
            var to = AnchorKey.load(pair.getCompound("to"));
            if (from.isPresent() && to.isPresent() && !from.get().equals(to.get())) data.links.put(from.get(), to.get());
        }
        data.links.entrySet().removeIf(entry -> !entry.getKey().equals(data.links.get(entry.getValue())));
        for (Tag element : tag.getList("authorizations",Tag.TAG_COMPOUND)) {
            var grant = (CompoundTag)element;
            if (grant.hasUUID("token")) AnchorKey.load(grant.getCompound("destination")).ifPresent(destination -> data.consents.put(destination,grant.getUUID("token")));
        }
        return data;
    }
}
