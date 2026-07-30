package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Original OBJ renderer for magnetic hooks and Silbarn projectiles. */
public final class ACThrownItemRenderer extends EntityRenderer<ACThrownItemEntity> {
    private final ItemRenderer itemRenderer;

    public ACThrownItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void render(ACThrownItemEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (entity.getItem().is(ACItems.MAG_HOOK.get())) {
            pose.pushPose();
            if (entity.isHooked()) {
                switch (entity.hookFace()) {
                    case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                    case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
                    case NORTH -> { }
                    case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                    case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                    case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                }
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() + 90));
                pose.mulPose(Axis.ZP.rotationDegrees(entity.getXRot() - 90));
            }
            pose.scale(.0054f, .0054f, .0054f);
            ACObjModel.get(entity.isHooked() ? "maghook_open" : "maghook").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucent(texture("maghook"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        } else if (entity.getItem().is(ACItems.SILBARN.get())) {
            pose.pushPose();
            float spin = (entity.tickCount + partialTick) * 6;
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.XP.rotationDegrees(spin));
            pose.mulPose(Axis.ZP.rotationDegrees(spin * .63f));
            pose.scale(.05f, .05f, .05f);
            ACObjModel.get("silbarn").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucent(texture("silbarn"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        } else {
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            if (entity.getItem().is(ACItems.COIN.get())) pose.mulPose(Axis.YP.rotationDegrees(
                    (entity.tickCount + partialTick) * 32));
            itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ACThrownItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
