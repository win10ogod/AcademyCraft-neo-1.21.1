package cn.academy.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Translucent, linearly filtered GUI quad path for smoothly scaled legacy textures. */
public final class ACGuiTextures {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();

    public static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                            float u, float v, float regionWidth, float regionHeight,
                            float textureWidth, float textureHeight) {
        blit(gui, texture, x, y, width, height, u, v, regionWidth, regionHeight,
                textureWidth, textureHeight, 1, 1, 1, 1);
    }

    public static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                            float u, float v, float regionWidth, float regionHeight,
                            float textureWidth, float textureHeight,
                            float red, float green, float blue, float alpha) {
        gui.flush();
        VertexConsumer output = gui.bufferSource().getBuffer(type(texture));
        float u0 = u / textureWidth, v0 = v / textureHeight;
        float u1 = (u + regionWidth) / textureWidth, v1 = (v + regionHeight) / textureHeight;
        var pose = gui.pose().last();
        output.addVertex(pose, x, y + height, 0).setUv(u0, v1).setColor(red, green, blue, alpha);
        output.addVertex(pose, x + width, y + height, 0).setUv(u1, v1).setColor(red, green, blue, alpha);
        output.addVertex(pose, x + width, y, 0).setUv(u1, v0).setColor(red, green, blue, alpha);
        output.addVertex(pose, x, y, 0).setUv(u0, v0).setColor(red, green, blue, alpha);
        gui.flush();
    }

    /** Selects bilinear sampling for a regular GuiGraphics blit without changing its blend path. */
    public static void setLinearFilter(ResourceLocation texture) {
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
    }

    public static synchronized void clear() { TYPES.clear(); }

    private static synchronized RenderType type(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, location -> RenderType.create("academy_gui_translucent_" + location,
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(location, true, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)));
    }

    private ACGuiTextures() {}
}
