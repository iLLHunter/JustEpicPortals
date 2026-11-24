package com.onlyk.justepicportals;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The client needs the server's rules before it builds the creative tab and recipe viewers such as JEI. They are sent once
 * per connection in the configuration phase, which finishes before the server sends recipes or places the player. */
public final class PortalNetwork {
    /** Change when the payload's format or phase changes, so mismatched installs are refused with a version message. */
    public static final String VERSION = "7";

    /** Bit i is component i of {@link PortalConfig.Rules}, so a new material or rule gets the next bit with no hand-numbered
     * constant. A long holds 63 rules, and startup fails clearly before that runs out. */
    public record RulesPayload(long bits) implements CustomPacketPayload {
        public static final Type<RulesPayload> TYPE = new Type<>(JustEpicPortals.id("rules"));
        private static final RecordComponent[] RULES = PortalConfig.Rules.class.getRecordComponents();
        private static final Constructor<PortalConfig.Rules> CONSTRUCTOR;
        static {
            if (RULES.length > 63) throw new IllegalStateException("Portal rules have " + RULES.length + " entries; the rules payload holds 63. Change its encoding.");
            var types = new Class<?>[RULES.length];
            for (int i = 0; i < RULES.length; i++) {
                if (RULES[i].getType() != boolean.class) throw new IllegalStateException("Portal rule " + RULES[i].getName() + " is not a boolean; the rules payload cannot send it.");
                types[i] = boolean.class;
            }
            try { CONSTRUCTOR = PortalConfig.Rules.class.getDeclaredConstructor(types); }
            catch (NoSuchMethodException error) { throw new IllegalStateException(error); }
        }
        /** A plain-buffer codec: the configuration phase has no registry access. */
        public static final StreamCodec<ByteBuf, RulesPayload> CODEC = ByteBufCodecs.VAR_LONG.map(bits -> new RulesPayload(bits), RulesPayload::bits);
        /** The number of rules the payload carries. */
        public static int size() { return RULES.length; }
        public RulesPayload(PortalConfig.Rules rules) { this(encode(rules)); }
        private static long encode(PortalConfig.Rules rules) {
            long bits = 0;
            try { for (int i = 0; i < RULES.length; i++) if ((boolean) RULES[i].getAccessor().invoke(rules)) bits |= 1L << i; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            return bits;
        }
        /** Bits beyond the known rules are ignored; the channel version keeps mismatched installs apart. */
        public PortalConfig.Rules rules() {
            var values = new Object[RULES.length];
            for (int i = 0; i < RULES.length; i++) values[i] = (bits & 1L << i) != 0;
            try { return CONSTRUCTOR.newInstance(values); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        @Override public Type<RulesPayload> type() { return TYPE; }
    }

    /** The anchor's requirement after an operator changed it in the open menu; the screen shows it from this. */
    public record RequirementPayload(int containerId, boolean any, java.util.List<PortalAnchorMenu.RequirementRow> entries) implements CustomPacketPayload {
        public static final Type<RequirementPayload> TYPE = new Type<>(JustEpicPortals.id("requirement"));
        public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, RequirementPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.containerId()); buffer.writeBoolean(payload.any()); PortalAnchorMenu.writeRows(buffer, payload.entries()); },
                buffer -> new RequirementPayload(buffer.readVarInt(), buffer.readBoolean(), PortalAnchorMenu.readRows(buffer, PortalRequirements.MAX_ANCHOR_ENTRIES)));
        @Override public Type<RequirementPayload> type() { return TYPE; }
    }

    /** Sends the server's rules, then lets the next configuration task run. */
    public record RulesTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(JustEpicPortals.id("rules"));
        @Override public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new RulesPayload(PortalConfig.rules()));
            listener.finishCurrentTask(TYPE);
        }
        @Override public ConfigurationTask.Type type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // The registrar's default runs the handler on the client's main thread.
        event.registrar(VERSION).configurationToClient(RulesPayload.TYPE, RulesPayload.CODEC,
                (payload, context) -> PortalConfig.sync(payload.rules()))
                .playToClient(RequirementPayload.TYPE, RequirementPayload.CODEC, (payload, context) -> {
                    if (context.player().containerMenu instanceof PortalAnchorMenu menu && menu.containerId == payload.containerId())
                        menu.requirementSynced(payload.any(), payload.entries());
                });
    }
    /** The channel is required, so NeoForge refuses clients without it before tasks are registered. */
    public static void registerTasks(RegisterConfigurationTasksEvent event) {
        if (event.getListener().hasChannel(RulesPayload.TYPE)) event.register(new RulesTask(event.getListener()));
    }
    private PortalNetwork() {}
}
