package com.onlyk.justepicportals;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Optional Just Epic Dimensions access checks through its public API ({@code justepicdimensions.api.access.JEDAccess}).
 * JEP has no compile-time dependency on JED. With an older JED that lacks the API, the pre-check is skipped (JED still
 * enforces its rules during the move) and JED rule references deny.
 */
public final class PortalJed {
    private static final String API = "justepicdimensions.api.access.JEDAccess";
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Methods methods;
    private record Methods(Method denial, Method evaluateRule, Method ruleIds, Method allowed, Method reason) {}

    public static boolean loaded() { return ModList.get() != null && ModList.get().isLoaded("justepicdimensions"); }

    private static Methods methods() {
        if (!loaded()) return null;
        var current = methods;
        if (current != null) return current;
        try {
            var api = Class.forName(API);
            var result = Class.forName(API + "$Result");
            current = new Methods(api.getMethod("denial", Entity.class, ResourceLocation.class),
                    api.getMethod("evaluateRule", ServerPlayer.class, ResourceLocation.class),
                    api.getMethod("ruleIds", MinecraftServer.class),
                    result.getMethod("allowed"), result.getMethod("reason"));
            methods = current;
            return current;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    /** JED's reason why {@code root} and its passengers may not enter {@code destination}, or empty when they may (or JED
     * is absent). */
    public static Optional<Component> denial(Entity root, ResourceKey<Level> destination) {
        var api = methods();
        if (api == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked") var result = (Optional<Component>) api.denial().invoke(null, root, destination.location());
            return result;
        } catch (ReflectiveOperationException | LinkageError error) { warn(error); return Optional.empty(); }
    }

    /** Empty when the rule is unknown or JED (or its API) is missing; otherwise empty-inside when allowed, or the reason. */
    public static Optional<Optional<Component>> evaluateRule(ServerPlayer player, ResourceLocation rule) {
        var api = methods();
        if (api == null) return Optional.empty();
        try {
            var result = (Optional<?>) api.evaluateRule().invoke(null, player, rule);
            if (result.isEmpty()) return Optional.empty();
            var value = result.get();
            return Optional.of((boolean) api.allowed().invoke(value) ? Optional.empty() : Optional.of((Component) api.reason().invoke(value)));
        } catch (ReflectiveOperationException | LinkageError error) { warn(error); return Optional.empty(); }
    }

    @SuppressWarnings("unchecked")
    public static Set<ResourceLocation> ruleIds(MinecraftServer server) {
        var api = methods();
        if (api == null) return Set.of();
        try { return (Set<ResourceLocation>) api.ruleIds().invoke(null, server); }
        catch (ReflectiveOperationException | LinkageError error) { warn(error); return Set.of(); }
    }

    private static void warn(Throwable error) {
        if (WARNED.compareAndSet(false, true))
            LogUtils.getLogger().warn("Just Epic Dimensions is installed without the access API Just Epic Portals uses; portals cannot check JED access before travel or use JED rules in requirements", error);
    }
    private PortalJed() {}
}
