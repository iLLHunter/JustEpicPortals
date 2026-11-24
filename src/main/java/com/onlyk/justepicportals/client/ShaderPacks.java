package com.onlyk.justepicportals.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a shader pack is drawing the world. Iris (and forks with its public API) replace vanilla's shader programs
 * with the pack's own but do not run a mod's custom core shader, so layers drawn with one would not appear at all.
 * The API is found by name, so there is no dependency on Iris; without it this always answers false.
 */
final class ShaderPacks {
    private static final MethodHandle IN_USE = find();

    private ShaderPacks() {}

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
            return lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (Throwable absent) {
            return null;
        }
    }

    static boolean inUse() {
        if (IN_USE == null) return false;
        try {
            return (boolean) IN_USE.invokeExact();
        } catch (Throwable error) {
            return false;
        }
    }
}
