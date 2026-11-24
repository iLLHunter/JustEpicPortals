package com.onlyk.justepicportals.client;

import com.onlyk.justepicportals.JustEpicPortals;
import com.onlyk.justepicportals.PortalAnchorBlockEntity;
import com.onlyk.justepicportals.PortalConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = JustEpicPortals.ID, value = Dist.CLIENT)
public final class PortalClient {
    private static final Map<UUID, Hum> HUMS = new HashMap<>();
    private static net.minecraft.client.renderer.ShaderInstance veilShader;
    /** The veil, dragon and spellmist layers' shader. Under a shader pack this mod's core shader would draw nothing,
     *  so they use vanilla's textured-colour shader, which the pack replaces with its own; faint edges below alpha 0.1
     *  are cut there. */
    static net.minecraft.client.renderer.ShaderInstance veilShader() {
        return ShaderPacks.inUse() ? net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader() : veilShader;
    }
    @SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        // The vanilla textured-color shader cuts off faint alpha at 0.1. Wisps need
        // their complete fade to avoid hard, speckled contours as the veil drifts.
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                JustEpicPortals.id("astral_veil"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),
                shader->veilShader=shader);
    }
    @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(JustEpicPortals.ANCHOR_MENU.get(),PortalAnchorScreen::new);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(JustEpicPortals.ANCHOR_ENTITY.get(), PortalRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PortalAnchorBlockEntity.clientTick = PortalClient::tick;
            NeoForge.EVENT_BUS.addListener(PortalClient::logout);
        });
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HUMS.values().forEach(Hum::end);
        HUMS.clear();
        PortalConfig.disconnected();
    }
    private static void tick(PortalAnchorBlockEntity anchor) {
        var client = Minecraft.getInstance();
        if (anchor.activeFrame() == null || client.player == null) return;
        var frame = anchor.activeFrame();
        if (client.player.position().distanceToSqr(frame.point(frame.width() / 2d, frame.height() / 2d, 0)) > 256) return;
        if (!HUMS.containsKey(anchor.identity())) {
            var hum = new Hum(anchor);
            HUMS.put(anchor.identity(), hum);
            client.getSoundManager().play(hum);
        }
    }
    private static final class Hum extends AbstractTickableSoundInstance {
        private final PortalAnchorBlockEntity anchor;
        private Hum(PortalAnchorBlockEntity anchor) {
            super(JustEpicPortals.ASTRAL_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.anchor = anchor;
            looping = true; delay = 0; volume = .16f; pitch = 1;
            var frame = anchor.activeFrame();
            if (frame != null) {
                var pos = frame.point(frame.width() / 2d, frame.height() / 2d, 0);
                x = pos.x; y = pos.y; z = pos.z;
            }
        }
        private void end() { stop(); }
        @Override public void tick() {
            var client = Minecraft.getInstance();
            var frame = anchor.activeFrame();
            if (anchor.isRemoved() || frame == null || client.level != anchor.getLevel() || client.player == null) {
                stop(); HUMS.remove(anchor.identity(), this); return;
            }
            var pos = frame.point(frame.width() / 2d, frame.height() / 2d, 0);
            x = pos.x; y = pos.y; z = pos.z;
            if (client.player.distanceToSqr(x, y, z) > 256) { stop(); HUMS.remove(anchor.identity(), this); }
        }
    }
}
