package com.onlyk.justepicportals;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalTransit {
    private record Contact(UUID player, AnchorKey source, int side) {}
    private static final class Runtime {
        final Map<UUID, Contact> contacts = new LinkedHashMap<>();
        final Map<UUID, Integer> arrivals = new HashMap<>();
        final Map<UUID, Integer> messages = new HashMap<>();
    }
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static Runtime runtime(MinecraftServer server) { return RUNTIMES.computeIfAbsent(server, ignored -> new Runtime()); }
    public static void stopped(MinecraftServer server) { RUNTIMES.remove(server); }

    public static void touch(PortalAnchorBlockEntity anchor, Entity touching) {
        if (!(anchor.getLevel() instanceof ServerLevel level) || anchor.material().disabled() || anchor.activeFrame() == null) return;
        var server = level.getServer(); var state = runtime(server); int now = server.getTickCount();
        var players = touching.getRootVehicle().getPassengersAndSelf()
                .filter(entity -> entity instanceof ServerPlayer).map(entity -> (ServerPlayer) entity).toList();
        for (var player : players) {
            if (!player.isAlive()) continue;
            if (state.arrivals.containsKey(player.getUUID())) { state.arrivals.put(player.getUUID(), now); continue; }
            if (!anchor.allows(player)) {
                if (now - state.messages.getOrDefault(player.getUUID(), -100) >= 40) {
                    PortalService.fail(player,"access_denied"); state.messages.put(player.getUUID(),now);
                }
                continue;
            }
            var missing = PortalRequirements.missing(anchor, player);
            if (missing.isPresent()) {
                if (now - state.messages.getOrDefault(player.getUUID(), -100) >= 40) {
                    PortalService.tell(player, Component.translatable("message.justepicportals.requirement.denied", missing.get())); state.messages.put(player.getUUID(), now);
                }
                continue;
            }
            if (PortalLinks.get(server).destination(anchor.key()).isEmpty()) continue;
            state.contacts.putIfAbsent(player.getUUID(), new Contact(player.getUUID(), anchor.key(), entrySide(anchor.activeFrame(), touching)));
        }
    }

    public static void tick(MinecraftServer server) {
        var state = RUNTIMES.get(server); if (state == null) return;
        int now = server.getTickCount();
        state.arrivals.entrySet().removeIf(entry -> now - entry.getValue() > 1);
        state.messages.entrySet().removeIf(entry -> now - entry.getValue() > 80);
        // Collision callbacks can visit several surface blocks and passengers. Transfer once at
        // the end of this tick, outside collision iteration, without requiring continued contact.
        var contacts = List.copyOf(state.contacts.values());
        state.contacts.clear();
        for (var contact : contacts) {
            var player = server.getPlayerList().getPlayer(contact.player);
            var anchor = PortalService.resolve(server, contact.source, false);
            if (player == null || !player.isAlive() || anchor == null || anchor.activeFrame() == null
                    || !player.level().dimension().equals(contact.source.dimension()) || state.arrivals.containsKey(contact.player)
                    || anchor.material().disabled() || !anchor.allows(player) || PortalRequirements.missing(anchor, player).isPresent() || PortalLinks.get(server).destination(contact.source).isEmpty()) continue;
            travel(anchor, player, contact.side);
        }
    }

    private static int entrySide(PortalFrame frame, Entity entity) {
        var normal = vector(frame.normal());
        double velocity = entity.getDeltaMovement().dot(normal);
        if (Math.abs(velocity) > .001) return velocity > 0 ? 1 : -1;
        double position = entity.getBoundingBox().getCenter().subtract(frame.point(frame.width() / 2d, frame.height() / 2d, 0)).dot(normal);
        if (Math.abs(position) > .001) return position > 0 ? -1 : 1;
        return entity.getLookAngle().dot(normal) >= 0 ? 1 : -1;
    }

    /** Uses native dimension transitions to retain vehicle passengers and entity data. No aperture-fit restriction is added. */
    public static boolean travel(PortalAnchorBlockEntity source, ServerPlayer player, int side) {
        var sourceLevel = player.serverLevel(); var server = sourceLevel.getServer();
        if (source.material().disabled()) return PortalService.fail(player, "disabled");
        var links = PortalLinks.get(server);
        var key = links.destination(source.key());
        if (key.isEmpty()) return PortalService.fail(player, "destination_unavailable");
        if (PortalConfig.rules().disableDimensional() && !key.get().dimension().equals(sourceLevel.dimension())) {
            PortalService.frameBroken(source); return PortalService.fail(player, "disabled_dimensions");
        }
        var destination = PortalService.resolve(server, key.get(), true);
        if (destination == null || destination.material().disabled() || !links.paired(source.key(), key.get())) {
            return PortalService.fail(player, "destination_unavailable");
        }
        PortalService.refresh(source); PortalService.refresh(destination);
        if (source.activeFrame() == null || destination.activeFrame() == null) return PortalService.fail(player, "destination_unavailable");
        var sourceFrame = source.activeFrame(); var destinationFrame = destination.activeFrame();
        var targetLevel = (ServerLevel) destination.getLevel();
        var primary = player.getRootVehicle();
        var travelers = primary.getPassengersAndSelf().filter(entity -> entity instanceof ServerPlayer)
                .map(entity -> (ServerPlayer) entity).toList();
        if (travelers.stream().anyMatch(traveler -> !source.allows(traveler) || !destination.allows(traveler))) {
            return PortalService.fail(player,"access_denied_group");
        }
        // Advancement requirements at both ends, then Just Epic Dimensions' rules for the destination dimension, are checked
        // for every player before anyone moves, so a denied group stays together.
        var denials = new LinkedHashMap<ServerPlayer, Component>();
        for (var traveler : travelers) {
            PortalRequirements.missing(source, traveler).map(missing -> (Component) Component.translatable("message.justepicportals.requirement.denied", missing))
                    .or(() -> PortalRequirements.missing(destination, traveler).map(missing -> Component.translatable("message.justepicportals.requirement.destination_denied", missing)))
                    .ifPresent(reason -> denials.put(traveler, reason));
        }
        if (!denials.isEmpty()) {
            for (var traveler : travelers) {
                if (denials.containsKey(traveler)) PortalService.tell(traveler, denials.get(traveler));
                else PortalService.fail(traveler, "requirement.group");
            }
            return false;
        }
        if (!targetLevel.dimension().equals(sourceLevel.dimension())) {
            var refused = PortalJed.denial(primary, targetLevel.dimension());
            if (refused.isPresent()) {
                for (var traveler : travelers) PortalService.tell(traveler, refused.get());
                return false;
            }
        }
        var roots = new LinkedHashMap<UUID, Entity>(); roots.put(primary.getUUID(), primary);
        // Only this area can hold entities to bring: Nearby reaches four blocks, and leashes break beyond LEASH_TOO_FAR_DIST.
        AABB area = travelers.stream().map(Entity::getBoundingBox).reduce(AABB::minmax).orElse(primary.getBoundingBox())
                .inflate(Math.max(4, Leashable.LEASH_TOO_FAR_DIST) + 2);
        for (LivingEntity entity : sourceLevel.getEntitiesOfClass(LivingEntity.class, area, candidate -> !(candidate instanceof Player) && candidate.isAlive())) {
            boolean nearby = source.nearby() && travelers.stream().anyMatch(traveler -> entity.distanceToSqr(traveler) <= 16);
            boolean leashed = entity instanceof Leashable leashable && travelers.contains(leashable.getLeashHolder());
            if (!nearby && !leashed) continue;
            var root = entity.getRootVehicle();
            if (root.getPassengersAndSelf().anyMatch(passenger -> passenger instanceof Player && !travelers.contains(passenger))) continue;
            // Nearby brings loose mobs only: anything in the group tied to a fence post, or on the lead of someone who is not
            // travelling, stays where it is.
            if (root.getPassengersAndSelf().anyMatch(member -> member instanceof Leashable held && held.getLeashHolder() != null
                    && !travelers.contains(held.getLeashHolder()))) continue;
            roots.putIfAbsent(root.getUUID(), root);
        }
        var selected = new LinkedHashMap<UUID, Entity>();
        roots.values().forEach(root -> root.getPassengersAndSelf().forEach(entity -> selected.putIfAbsent(entity.getUUID(), entity)));
        var leashes = new HashMap<UUID, UUID>();
        selected.values().forEach(entity -> {
            if (entity instanceof Leashable leashable && leashable.getLeashHolder() != null && selected.containsKey(leashable.getLeashHolder().getUUID())) {
                leashes.put(entity.getUUID(), leashable.getLeashHolder().getUUID());
            }
        });
        leashes.keySet().forEach(id -> ((Leashable) selected.get(id)).dropLeash(true, false));
        Vec3 initial = primary.position();
        var transferred = new HashMap<UUID, Entity>();
        sourceLevel.playSound(null, player.blockPosition(), JustEpicPortals.ASTRAL_TRAVEL.get(), SoundSource.BLOCKS, .45f, 1);
        boolean primaryMoved = false;
        for (Entity root : roots.values()) {
            Vec3 formation = rotate(root.position().subtract(initial), sourceFrame, destinationFrame);
            var normal = vector(destinationFrame.normal());
            formation = formation.subtract(normal.scale(formation.dot(normal)));
            Vec3 target = arrival(root, destinationFrame, side).add(formation);
            Vec3 speed = rotate(root.getDeltaMovement(), sourceFrame, destinationFrame);
            Vec3 look = rotate(root.getLookAngle(), sourceFrame, destinationFrame);
            float yaw = (float) (Mth.atan2(look.z, look.x) * 180 / Math.PI) - 90;
            float pitch = (float) (-Mth.atan2(look.y, Math.hypot(look.x, look.z)) * 180 / Math.PI);
            Entity moved = root.changeDimension(new DimensionTransition(targetLevel, target, speed, yaw, pitch, DimensionTransition.PLACE_PORTAL_TICKET));
            if (moved != null) {
                moved.getPassengersAndSelf().forEach(entity -> transferred.put(entity.getUUID(), entity));
                if (moved instanceof ServerPlayer movedPlayer) {
                    movedPlayer.setDeltaMovement(speed);
                    movedPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(movedPlayer));
                }
            }
            if (root == primary) {
                primaryMoved = moved != null;
                if (!primaryMoved) break;
            }
        }
        // Another mod can refuse any one entity's dimension change. Reattach every lead whose animal and holder ended up
        // together; a lead that cannot be reattached drops as an item, as a snapped lead does, and is never deleted.
        for (var entry : leashes.entrySet()) {
            Entity entity = transferred.get(entry.getKey()), holder = transferred.get(entry.getValue());
            if (entity == null) entity = sourceLevel.getEntity(entry.getKey());
            if (holder == null) holder = sourceLevel.getEntity(entry.getValue());
            if (!(entity instanceof Leashable leashable) || !entity.isAlive()) continue;
            if (holder != null && holder.isAlive() && entity.level() == holder.level() && entity.distanceTo(holder) <= Leashable.LEASH_TOO_FAR_DIST)
                leashable.setLeashedTo(holder, true);
            else entity.spawnAtLocation(Items.LEAD);
        }
        var state = runtime(server); int now = server.getTickCount();
        for (Entity entity : selected.values()) if (entity instanceof ServerPlayer traveler) state.contacts.remove(traveler.getUUID());
        if (!primaryMoved) return PortalService.fail(player, "travel_cancelled");
        // Only players who actually arrived get arrival protection. A rider whose own move was refused or held while the
        // mount went ahead is told so, and can use the portal again at once.
        // "Arrived" means moved by this trip (a same-dimension pair would make a level check pass for someone left behind).
        for (Entity entity : selected.values()) if (entity instanceof ServerPlayer traveler) {
            if (transferred.containsKey(traveler.getUUID())) state.arrivals.put(traveler.getUUID(), now);
            else if (server.getPlayerList().getPlayer(traveler.getUUID()) instanceof ServerPlayer left) PortalService.fail(left, "travel_cancelled");
        }
        targetLevel.playSound(null, player.blockPosition(), JustEpicPortals.ASTRAL_TRAVEL.get(), SoundSource.BLOCKS, .45f, 1);
        return true;
    }

    public static Vec3 arrival(Entity root, PortalFrame frame, int side) {
        AABB bounds = root.getBoundingBox();
        for (Entity passenger : root.getPassengersAndSelf().toList()) bounds = bounds.minmax(passenger.getBoundingBox());
        double half = switch (frame.normal().getAxis()) { case X -> bounds.getXsize() / 2; case Y -> bounds.getYsize() / 2; case Z -> bounds.getZsize() / 2; };
        Vec3 centerOffset = bounds.getCenter().subtract(root.position());
        return frame.point(frame.width() / 2d, frame.height() / 2d, (side < 0 ? -1 : 1) * (half + .6)).subtract(centerOffset);
    }
    public static Vec3 rotate(Vec3 value, PortalFrame source, PortalFrame destination) {
        return vector(destination.u()).scale(value.dot(vector(source.u())))
                .add(vector(destination.v()).scale(value.dot(vector(source.v()))))
                .add(vector(destination.normal()).scale(value.dot(vector(source.normal()))));
    }
    private static Vec3 vector(Direction direction) { return Vec3.atLowerCornerOf(direction.getNormal()); }
    private PortalTransit() {}
}
