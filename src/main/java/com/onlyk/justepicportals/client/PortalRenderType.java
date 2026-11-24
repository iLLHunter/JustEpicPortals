package com.onlyk.justepicportals.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import com.onlyk.justepicportals.JustEpicPortals;

/** Both faces stay visible, and translucent light never writes an opaque depth plane. */
public final class PortalRenderType extends RenderType {
    public static final RenderType SURFACE = create("justepicportals_astral_surface", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType VEIL = create("justepicportals_astral_veil",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,16384,false,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(PortalClient::veilShader))
                    .setTextureState(new TextureStateShard(JustEpicPortals.id("textures/effect/astral_veil.png"),true,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    public static final RenderType LIGHT = light("justepicportals_astral_light");
    /** The same light drawn after the dragon spirits. A batching renderer (Iris) replays each render type once, so the
     *  embers need their own type to stay above the spirits instead of joining the light drawn beneath them. */
    public static final RenderType EMBERS = light("justepicportals_dragonwake_embers");
    public static final RenderType[] SPELLMIST = potionFrames();
    public static final RenderType DRAGON = create("justepicportals_dragonwake",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(PortalClient::veilShader))
                    .setTextureState(new TextureStateShard(JustEpicPortals.id("textures/effect/dragonwake_spirit.png"),true,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static RenderType light(String name) {
        return create(name,DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,16384,false,true,CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }
    private static RenderType[] potionFrames() {
        var result=new RenderType[8];
        for(int i=0;i<result.length;i++)result[i]=create("justepicportals_spellmist_"+i,DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(PortalClient::veilShader))
                        .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/particle/effect_"+i+".png"),false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        return result;
    }
    private PortalRenderType() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true, () -> {}, () -> {}); }
}
