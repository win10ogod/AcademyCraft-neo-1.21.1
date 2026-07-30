package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.entity.ACElectronBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Camera-facing animated renderer using the five original mdball frames. */
public final class ACElectronBallRenderer extends EntityRenderer<ACElectronBallEntity> {
    public ACElectronBallRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(ACElectronBallEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        int frame = Math.floorMod((entity.tickCount * 3 + entity.getId()), 5);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "textures/effects/mdball/" + frame + ".png");
        pose.pushPose();
        if (entity.owner() != null) pose.translate(0, entity.owner().getEyeHeight(), 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        float age = entity.tickCount + partialTick;
        float end = Math.max(0, entity.life() - age);
        float scale = end < 2 ? end / 2f : end < 6 ? 1 + (6 - end) * .12f : 1;
        float alpha = age < 6 ? age / 6f * .6f : end < 3 ? end / 3f : .6f;
        pose.scale(scale, scale, scale);
        ResourceLocation glow = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "textures/effects/mdball/glow.png");
        quad(buffers.getBuffer(RenderType.entityTranslucent(glow)), pose, .7f, alpha * .8f, light);
        quad(buffers.getBuffer(RenderType.entityTranslucent(texture)), pose, .5f, alpha, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(VertexConsumer out, PoseStack pose, float size, float alpha, int light) {
        float half = size / 2;
        vertex(out, pose, -half, -half, 0, 0, 1, alpha, light);
        vertex(out, pose, half, -half, 0, 1, 1, alpha, light);
        vertex(out, pose, half, half, 0, 1, 0, alpha, light);
        vertex(out, pose, -half, half, 0, 0, 0, alpha, light);
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                               float u, float v, float alpha, int light) {
        out.addVertex(pose.last(), x, y, z).setColor(1, 1, 1, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
    }

    @Override public ResourceLocation getTextureLocation(ACElectronBallEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/effects/mdball/0.png");
    }
}
