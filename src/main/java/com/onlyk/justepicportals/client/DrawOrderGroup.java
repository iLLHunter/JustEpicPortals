package com.onlyk.justepicportals.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;

/**
 * Keeps a renderer's layers in the order it requested them when the buffer source batches render types.
 *
 * <p>Iris (with or without a shader pack) replaces the level's buffer source with one that collects every render type
 * for the whole frame and replays them in an order it works out itself; it ignores per-type flushes. It only keeps the
 * order a renderer asked for while a draw-order "group" is open, and it opens groups for entities and banners but not
 * for other block entities. Without one, the portal's dark base can be replayed over its veil and light, which dims the
 * whole surface. Opening the batcher's own group restores the order vanilla draws in. The methods are found by name, so
 * there is no dependency on Iris (or its forks); any other buffer source is left alone.
 */
final class DrawOrderGroup {
    private record Methods(MethodHandle start, MethodHandle end) {}
    private static final Methods NONE = new Methods(null, null);
    private static final ClassValue<Methods> METHODS = new ClassValue<>() {
        @Override protected Methods computeValue(Class<?> type) {
            // Look through public declarers only: a buffer source may be a non-public wrapper of a public interface.
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                var found = find(c);
                if (found != null) return found;
                for (var i : c.getInterfaces()) {
                    found = find(i);
                    if (found != null) return found;
                }
            }
            return NONE;
        }
    };

    private static Methods find(Class<?> declarer) {
        if (!Modifier.isPublic(declarer.getModifiers())) return null;
        try {
            var lookup = MethodHandles.publicLookup();
            return new Methods(lookup.findVirtual(declarer, "maybeStartGroup", MethodType.methodType(boolean.class)),
                    lookup.findVirtual(declarer, "endGroup", MethodType.methodType(void.class)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private DrawOrderGroup() {}

    /** Opens a group on {@code buffers} if it supports one and none is open; returns whether {@link #end} must be called. */
    static boolean begin(Object buffers) {
        var methods = METHODS.get(buffers.getClass());
        if (methods == NONE) return false;
        try {
            return (boolean) methods.start().invoke(buffers);
        } catch (Throwable e) {
            return false;
        }
    }

    static void end(Object buffers) {
        try {
            METHODS.get(buffers.getClass()).end().invoke(buffers);
        } catch (Throwable ignored) {
            // A group the batcher could not close is its own state; rendering continues either way.
        }
    }
}
