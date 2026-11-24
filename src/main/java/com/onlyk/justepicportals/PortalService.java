package com.onlyk.justepicportals;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class PortalService {
    private record WorldPos(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {}
    private static final class Index {
        final Map<WorldPos, Set<AnchorKey>> edges = new HashMap<>();
        final Map<AnchorKey, PortalFrame> frames = new HashMap<>();
    }
    private static final Map<MinecraftServer, Index> INDICES = new WeakHashMap<>();

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(PortalService::started);
        NeoForge.EVENT_BUS.addListener(PortalService::stopped);
        NeoForge.EVENT_BUS.addListener(PortalService::neighbors);
        NeoForge.EVENT_BUS.addListener(PortalService::breaking);
        NeoForge.EVENT_BUS.addListener(PortalService::serverTick);
        NeoForge.EVENT_BUS.addListener(PortalService::resourcesReloading);
        NeoForge.EVENT_BUS.addListener(PortalRequirements::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(PortalCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(PortalService::rightClick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,PortalService::leftClick);
    }
    private static void resourcesReloading(AddReloadListenerEvent event) { PortalConfig.recipeReloading(); }
    private static void started(ServerStartedEvent event) {
        if (PortalConfig.serverStarted()) {
            // Initial recipe loading precedes this event. Refresh it before accepting players if
            // an integrated server has restarted with different COMMON config values.
            var server = event.getServer();
            var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
            server.managedBlock(reload::isDone);
            reload.join();
        }
        if (PortalConfig.rules().disableDimensional()) PortalLinks.get(event.getServer()).invalidateDimensionalLinks();
    }
    private static void stopped(ServerStoppedEvent event) {
        INDICES.remove(event.getServer()); PortalTransit.stopped(event.getServer());
    }
    private static void serverTick(ServerTickEvent.Post event) { PortalTransit.tick(event.getServer()); }
    private static void rightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        // Sneaking with any item other than a PAC keeps vanilla's behaviour, so players can place blocks against an anchor.
        if (event.getUseBlock().isDefault() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof PortalAnchorBlock
                && !PortalAnchorBlock.placesItem(event.getEntity(),event.getItemStack())) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
    private static void leftClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        // Some tools handle unmineable blocks directly during the interaction event.
        if(event.getLevel().getBlockEntity(event.getPos()) instanceof PortalAnchorBlockEntity anchor
                && !anchor.ownedBy(event.getEntity())) {
            event.setCanceled(true);
            if(!event.getLevel().isClientSide)fail(event.getEntity(),"mining_owner_only");
        }
    }
    private static void breaking(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof PortalAnchorBlockEntity anchor
                && !anchor.ownedBy(event.getPlayer())) {
            event.setCanceled(true);
            fail(event.getPlayer(), "mining_owner_only");
        }
    }
    private static void neighbors(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var index = INDICES.get(level.getServer());
        if (index == null) return;
        var keys = index.edges.get(new WorldPos(level.dimension(), event.getPos()));
        if (keys == null) return;
        for (AnchorKey key : List.copyOf(keys)) {
            var anchor = resolve(level.getServer(), key, false);
            if (anchor == null) continue;
            var state = level.getBlockState(event.getPos());
            if (!(state.is(anchor.material().frameBlock()) || event.getPos().equals(anchor.getBlockPos()) && state.getBlock() instanceof PortalAnchorBlock)) {
                frameBroken(anchor);
            }
        }
    }

    public static PortalAnchorBlockEntity resolve(MinecraftServer server, AnchorKey key, boolean loadChunk) {
        var level = server.getLevel(key.dimension());
        if (level == null) return null;
        if (loadChunk) level.getChunkAt(key.position());
        else if (!level.hasChunkAt(key.position())) return null;
        return level.getBlockEntity(key.position()) instanceof PortalAnchorBlockEntity anchor && anchor.identity().equals(key.identity()) ? anchor : null;
    }

    public static boolean link(PortalAnchorBlockEntity source, AnchorKey saved, Player player, ItemStack core) {
        return link(source,saved,player,core,source.linkedFrame()==null?null:source.linkedFrame().normal().getAxis(),PortalAnchorCoreItem.destinationAxis(core));
    }
    public static boolean link(PortalAnchorBlockEntity source, AnchorKey saved, Player player, ItemStack core,
                               net.minecraft.core.Direction.Axis sourceAxis, net.minecraft.core.Direction.Axis destinationAxis) {
        var level = (ServerLevel) source.getLevel();
        var server = level.getServer();
        if (player != null && !PortalPermissions.canManage(source,player)) return fail(player,"owner_only");
        if (source.containsCore()) return fail(player,"remove_core_first");
        var destination = resolve(server, saved, true);
        if (destination == null) return fail(player, "invalid_destination");
        if (source.key().equals(saved)) return fail(player, "same_anchor");
        if (destination.containsCore()) return fail(player,"destination_core_present");
        var links = PortalLinks.get(server);
        if (player != null && !PortalPermissions.canManage(destination,player) && !links.authorized(PortalAnchorCoreItem.authorization(core),destination.key())) {
            return fail(player,"destination_permission");
        }
        if (source.material().disabled() || destination.material().disabled()) return fail(player, "disabled");
        if (PortalConfig.rules().disableDimensional() && !source.key().dimension().equals(saved.dimension())) return fail(player, "disabled_dimensions");
        var first = source.findFrame(sourceAxis); var second = destination.findFrame(destinationAxis);
        var problem = frameProblem(source, first);
        if (problem != null) return fail(player, problem.message(false));
        problem = frameProblem(destination, second);
        if (problem != null) return fail(player, problem.message(true));
        var oldFirst = links.destination(source.key()); var oldSecond = links.destination(saved);
        links.link(source.key(), saved);
        source.linkedFrame(first.get()); destination.linkedFrame(second.get());
        var installed = core.copyWithCount(1);
        var authorization = PortalAnchorCoreItem.authorization(core);
        PortalAnchorCoreItem.remember(installed,saved,second.get().normal().getAxis());
        if (authorization != null) PortalAnchorCoreItem.authorize(installed,authorization);
        source.insertedCore(installed);
        oldFirst.filter(key -> !key.equals(saved)).ifPresent(key -> deactivateOld(server, key));
        oldSecond.filter(key -> !key.equals(source.key())).ifPresent(key -> deactivateOld(server, key));
        refresh(source); refresh(destination);
        core.shrink(1);
        if (player != null) player.displayClientMessage(Component.translatable("message.justepicportals.linked"), true);
        return true;
    }
    /** Why this end of a link has no usable frame, or null if {@code frame} is usable. */
    private static PortalFrame.Problem frameProblem(PortalAnchorBlockEntity anchor, java.util.Optional<PortalFrame> frame) {
        if (frame.isPresent()) return available(anchor, frame.get()) ? null : PortalFrame.Problem.OVERLAP;
        var problem = PortalFrame.diagnose(anchor.getLevel(), anchor.getBlockPos(), anchor.material());
        return problem == null ? PortalFrame.Problem.AMBIGUOUS : problem;
    }
    private static boolean available(PortalAnchorBlockEntity anchor, PortalFrame frame) {
        boolean[] valid = {true};
        frame.forEach(pos -> {
            if (anchor.getLevel().getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface && surface.anchor() != anchor) valid[0] = false;
        });
        return valid[0];
    }
    private static void deactivateOld(MinecraftServer server, AnchorKey key) {
        var old = resolve(server, key, false);
        if (old != null) { clear(old); old.linkedFrame(null); }
        untrack(server, key);
    }
    public static void refresh(PortalAnchorBlockEntity anchor) {
        if (!(anchor.getLevel() instanceof ServerLevel level) || anchor.isRemoved()) return;
        var links = PortalLinks.get(level.getServer());
        var destination = links.destination(anchor.key());
        if (destination.isEmpty()) { clear(anchor); anchor.linkedFrame(null); untrack(level.getServer(), anchor.key()); return; }
        if (PortalConfig.rules().disableDimensional() && !anchor.key().dimension().equals(destination.get().dimension())) {
            frameBroken(anchor); return;
        }
        var expected = anchor.linkedFrame();
        var actual = anchor.findFrame();
        if (expected == null || actual.isEmpty() || !expected.equals(actual.get()) || !available(anchor, expected)) {
            frameBroken(anchor); return;
        }
        track(level.getServer(), anchor.key(), expected);
        var partner = resolve(level.getServer(), destination.get(), false);
        if (anchor.material().disabled() || partner != null && partner.material().disabled()) { clear(anchor); return; }
        if (anchor.activeFrame() != null && !expected.equals(anchor.activeFrame())) clear(anchor);
        anchor.activeFrame(expected);
        expected.forEach(pos -> {
            var desired = JustEpicPortals.SURFACE.get().defaultBlockState().setValue(PortalSurfaceBlock.AXIS, expected.normal().getAxis()).setValue(PortalSurfaceBlock.COLOR, anchor.color());
            if (!level.getBlockState(pos).equals(desired)) level.setBlock(pos, desired, 2);
            if (level.getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface && surface.anchor() != anchor) surface.owner(anchor);
        });
    }
    private static void track(MinecraftServer server, AnchorKey key, PortalFrame frame) {
        var index = INDICES.computeIfAbsent(server, ignored -> new Index());
        if (frame.equals(index.frames.get(key))) return;
        untrack(server, key); index.frames.put(key, frame);
        frame.forEachEdge(pos -> index.edges.computeIfAbsent(new WorldPos(key.dimension(), pos), ignored -> new HashSet<>()).add(key));
    }
    public static void anchorLoaded(PortalAnchorBlockEntity anchor) {
        if (anchor.getLevel() instanceof ServerLevel level && anchor.linkedFrame() != null) {
            track(level.getServer(),anchor.key(),anchor.linkedFrame());
        }
    }
    private static void untrack(MinecraftServer server, AnchorKey key) {
        var index = INDICES.get(server); if (index == null) return;
        var frame = index.frames.remove(key); if (frame == null) return;
        frame.forEachEdge(pos -> {
            var position = new WorldPos(key.dimension(), pos); var keys = index.edges.get(position);
            if (keys != null) { keys.remove(key); if (keys.isEmpty()) index.edges.remove(position); }
        });
    }
    public static void frameBroken(PortalAnchorBlockEntity anchor) {
        if (!(anchor.getLevel() instanceof ServerLevel level)) return;
        var links = PortalLinks.get(level.getServer());
        var partner = links.destination(anchor.key());
        links.unlink(anchor.key());
        clear(anchor); anchor.linkedFrame(null); untrack(level.getServer(), anchor.key());
        partner.ifPresent(key -> deactivateOld(level.getServer(), key));
    }
    public static void anchorRemoved(PortalAnchorBlockEntity anchor) {
        frameBroken(anchor);
        if (anchor.getLevel() instanceof ServerLevel level) PortalLinks.get(level.getServer()).revokeAuthorizations(anchor.key());
    }
    public static boolean removeCore(PortalAnchorBlockEntity anchor, Player player, net.minecraft.world.InteractionHand hand) {
        if (!PortalPermissions.canManage(anchor,player)) return fail(player, "owner_only");
        if (!player.getItemInHand(hand).isEmpty() || !anchor.containsCore()) return false;
        var core = anchor.removeCore();
        frameBroken(anchor);
        player.setItemInHand(hand, core);
        return true;
    }
    public static void openMenu(PortalAnchorBlockEntity anchor, Player player) {
        if (!PortalPermissions.canManage(anchor,player)) { fail(player,"owner_only"); return; }
        if (player instanceof ServerPlayer serverPlayer) {
            var choices = PortalTeams.choices(serverPlayer.getServer(),anchor.owner(),anchor.ownerName());
            var allChoices=PortalPermissions.canAdminister(serverPlayer)?PortalTeams.allChoices(serverPlayer.getServer()):java.util.List.<PortalTeams.Choice>of();
            var provider = new net.minecraft.world.SimpleMenuProvider(
                    (id,inventory,viewer) -> new PortalAnchorMenu(id,inventory,anchor,choices,allChoices,PortalPermissions.canAdminister(serverPlayer)),
                    anchor.getBlockState().getBlock().getName());
            serverPlayer.openMenu(provider, buffer -> PortalAnchorMenu.writeOpeningData(buffer,anchor,choices,allChoices,PortalPermissions.canAdminister(serverPlayer)));
        }
    }
    public static void clear(PortalAnchorBlockEntity anchor) {
        var frame = anchor.activeFrame();
        anchor.activeFrame(null);
        if (frame == null) return;
        frame.forEach(pos -> {
            if (anchor.getLevel().getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface && surface.anchor() == anchor) {
                anchor.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        });
    }
    public static void paint(PortalAnchorBlockEntity anchor) {
        var frame = anchor.activeFrame(); if (frame == null) return;
        frame.forEach(pos -> {
            if (anchor.getLevel().getBlockEntity(pos) instanceof PortalSurfaceBlockEntity surface && surface.anchor() == anchor) {
                anchor.getLevel().setBlock(pos, anchor.getLevel().getBlockState(pos).setValue(PortalSurfaceBlock.COLOR, anchor.color()), 2);
            }
        });
    }
    public static void touch(PortalAnchorBlockEntity anchor, Entity entity) { PortalTransit.touch(anchor, entity); }
    public static boolean fail(Player player, String key) {
        if (player != null) player.displayClientMessage(Component.translatable("message.justepicportals." + key), true);
        return false;
    }
    public static boolean tell(Player player, Component message) {
        if (player != null) player.displayClientMessage(message, true);
        return false;
    }
    private PortalService() {}
}
