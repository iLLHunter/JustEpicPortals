package com.onlyk.justepicportals;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PortalAnchorBlockEntity extends BlockEntity {
    public static java.util.function.Consumer<PortalAnchorBlockEntity> clientTick = anchor -> {};
    private UUID identity = UUID.randomUUID();
    private UUID owner;
    private String ownerName = "";
    private ItemStack insertedCore = ItemStack.EMPTY;
    private PortalAccess access = PortalAccess.EVERYONE;
    private PortalTeams.Key selectedTeam;
    private boolean nearby;
    private PortalRequirements.AnchorRequirement requirement = PortalRequirements.AnchorRequirement.NONE;
    private DyeColor color = DyeColor.LIME;
    private PortalEffect effect = PortalEffect.ASTRAL;
    private PortalFrame activeFrame;
    private PortalFrame linkedFrame;

    public PortalAnchorBlockEntity(BlockPos pos, BlockState state) { super(JustEpicPortals.ANCHOR_ENTITY.get(), pos, state); }
    public PortalMaterial material() { return ((PortalAnchorBlock) getBlockState().getBlock()).material(); }
    public AnchorKey key() { return new AnchorKey(level.dimension(), worldPosition, identity); }
    public UUID identity() { return identity; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public PortalAccess access() { return access; }
    public boolean nearby() { return nearby; }
    public PortalTeams.Key selectedTeam() { return selectedTeam; }
    public void selectedTeam(PortalTeams.Key value) { if (!java.util.Objects.equals(selectedTeam,value)) { selectedTeam = value; setChanged(); } }
    public boolean allows(net.minecraft.server.level.ServerPlayer player) {
        return switch (access) {
            case EVERYONE -> true;
            case SELF -> ownedBy(player);
            case TEAM -> PortalTeams.member(player.getServer(), selectedTeam, player);
        };
    }
    // Access, Nearby, the team and the core reach clients only through the open menu, so they are saved without a block update.
    public void access(PortalAccess value) { if (access != value) { access = value; setChanged(); } }
    public void nearby(boolean value) { if (nearby != value) { nearby = value; setChanged(); } }
    /** The anchor's own advancement requirement, set by operators. Material rules apply on top of it. */
    public PortalRequirements.AnchorRequirement requirement() { return requirement; }
    public void requirement(PortalRequirements.AnchorRequirement value) { if (!requirement.equals(value)) { requirement = value; setChanged(); } }
    public boolean containsCore() { return !insertedCore.isEmpty(); }
    public ItemStack insertedCore() { return insertedCore.copy(); }
    public void insertedCore(ItemStack stack) {
        insertedCore = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }
    public ItemStack removeCore() {
        var removed = insertedCore;
        insertedCore = ItemStack.EMPTY;
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    public boolean ownedBy(net.minecraft.world.entity.player.Player player) {
        return player != null && player.getUUID().equals(owner);
    }
    public void owner(net.minecraft.world.entity.player.Player player) {
        if (owner != null) return;
        assignOwner(player);
    }
    public void placedBy(net.minecraft.world.entity.player.Player player) {
        identity=UUID.randomUUID();
        assignOwner(player);
    }
    private void assignOwner(net.minecraft.world.entity.player.Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        changed();
    }
    public DyeColor color() { return color; }
    public PortalEffect effect() { return effect; }
    public void effect(PortalEffect value) { if (effect != value) { effect = value; changed(); } }
    public PortalFrame activeFrame() { return activeFrame; }
    public PortalFrame linkedFrame() { return linkedFrame; }
    public void linkedFrame(PortalFrame frame) { linkedFrame = frame; setChanged(); }
    public java.util.Optional<PortalFrame> findFrame() {
        return findFrame(linkedFrame == null ? null : linkedFrame.normal().getAxis());
    }
    public java.util.Optional<PortalFrame> findFrame(net.minecraft.core.Direction.Axis preference) {
        var frames = new java.util.ArrayList<PortalFrame>();
        for (var normal : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP, net.minecraft.core.Direction.SOUTH}) {
            var candidate = PortalFrame.find(level, worldPosition, material(), normal);
            candidate.ifPresent(frames::add);
        }
        if (frames.size() == 1) return java.util.Optional.of(frames.getFirst());
        return frames.stream().filter(frame -> frame.normal().getAxis() == preference).findFirst();
    }
    public void color(DyeColor value) { if (color != value) { color = value; changed(); } }
    public void activeFrame(PortalFrame frame) {
        if (!java.util.Objects.equals(activeFrame, frame)) { activeFrame = frame; changed(); }
    }
    /** For fields in {@link #getUpdateTag}: save and send the block update that nearby clients render from. */
    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel && linkedFrame != null) PortalService.anchorLoaded(this);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, PortalAnchorBlockEntity anchor) {
        if (level.isClientSide) { clientTick.accept(anchor); return; }
        if ((level.getGameTime() + pos.asLong()) % 10 == 0) PortalService.refresh(anchor);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("identity", identity);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        if (!insertedCore.isEmpty()) tag.put("inserted_core", insertedCore.save(registries));
        tag.putString("access", access.name());
        if (selectedTeam != null) { tag.putString("team_system", selectedTeam.system()); tag.putString("team_id",selectedTeam.id()); }
        tag.putBoolean("nearby", nearby);
        if (!requirement.isEmpty()) {
            var entries = new net.minecraft.nbt.ListTag();
            requirement.entries().forEach(entry -> entries.add(net.minecraft.nbt.StringTag.valueOf(entry.save())));
            tag.put("requirements", entries);
            tag.putBoolean("requirement_any", requirement.any());
        }
        tag.putInt("color", color.getId());
        tag.putString("effect", effect.id());
        if (activeFrame != null) tag.put("frame", activeFrame.save());
        if (linkedFrame != null) tag.put("linked_frame", linkedFrame.save());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("identity")) identity = tag.getUUID("identity");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        insertedCore = tag.contains("inserted_core") ? ItemStack.parseOptional(registries, tag.getCompound("inserted_core")) : ItemStack.EMPTY;
        access = PortalAccess.load(tag.getString("access"));
        selectedTeam = tag.contains("team_system") && tag.contains("team_id") ? new PortalTeams.Key(tag.getString("team_system"),tag.getString("team_id")) : null;
        nearby = tag.getBoolean("nearby");
        var entries = new java.util.ArrayList<PortalRequirements.Entry>();
        // An entry this version cannot read stays a requirement nobody meets, rather than silently opening the portal.
        for (var entry : tag.getList("requirements", net.minecraft.nbt.Tag.TAG_STRING))
            entries.add(PortalRequirements.Entry.load(entry.getAsString()).orElse(new PortalRequirements.Entry(PortalRequirements.Kind.GROUP, JustEpicPortals.id("unreadable"))));
        requirement = new PortalRequirements.AnchorRequirement(tag.getBoolean("requirement_any"), entries.stream().limit(PortalRequirements.MAX_ANCHOR_ENTRIES).toList());
        color = tag.contains("color") ? DyeColor.byId(tag.getInt("color")) : DyeColor.LIME;
        effect = PortalEffect.load(tag.getString("effect"));
        activeFrame = tag.contains("frame") ? PortalFrame.load(tag.getCompound("frame")).orElse(null) : null;
        linkedFrame = tag.contains("linked_frame") ? PortalFrame.load(tag.getCompound("linked_frame")).orElse(null) : null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag();
        tag.putUUID("identity",identity);
        if(owner!=null)tag.putUUID("owner",owner);
        tag.putString("owner_name",ownerName);
        tag.putInt("color",color.getId());
        tag.putString("effect",effect.id());
        if(activeFrame!=null)tag.put("frame",activeFrame.save());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public AABB renderBounds() {
        if (activeFrame == null) return new AABB(worldPosition);
        return new AABB(activeFrame.origin()).minmax(new AABB(activeFrame.position(activeFrame.width() - 1, activeFrame.height() - 1))).inflate(1);
    }
}
