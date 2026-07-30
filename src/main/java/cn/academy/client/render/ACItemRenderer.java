package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACDataComponents;
import cn.academy.item.MatterUnitItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Custom inventory/held renderer for the original 1.12.2 OBJ items and animated block items. */
public final class ACItemRenderer extends BlockEntityWithoutLevelRenderer {
    public ACItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        // The legacy OBJ renderers are intentionally kept for held, ground and frame views.  In a
        // GUI, however, several of those models are edge-on or much smaller than a 16 px slot.  Use
        // the original 1.12.2 inventory artwork so every custom-rendered item has a clear icon.
        if (context == ItemDisplayContext.GUI) {
            ResourceLocation icon = guiIcon(stack);
            if (icon != null) {
                renderFlatItem(pose, buffers, light, overlay, icon);
                return;
            }
        }
        if (stack.is(ACItems.CAT_ENGINE.get())) {
            renderCat(pose, buffers, light, overlay);
            return;
        }
        if (stack.is(ACItems.COIN.get())) {
            renderCoin(pose, buffers, light, overlay);
            return;
        }
        if (stack.is(ACItems.MATTER_UNIT.get())) {            int frame = Minecraft.getInstance().level == null ? 0
                    : (int) ((Minecraft.getInstance().level.getGameTime() / 4) % 4);
            String texture = MatterUnitItem.isFilled(stack) ? "matter_unit_phase_liquid_" + frame : "matter_unit";
            renderFlatItem(pose, buffers, light, overlay, ResourceLocation.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "textures/item/" + texture + ".png"));
            return;
        }
        String model = null, texture = null;
        float scale = 1;
        float yOffset = 0;
        float rotY = 0;
        if (stack.is(ACItems.SOLAR_GEN.get())) { model = texture = "solar"; scale = .014f; rotY = 90; }
        else if (stack.is(ACItems.PHASE_GEN.get())) { model = "ip_gen"; texture = "ip_gen0"; scale = .85f; yOffset = -.45f; }
        else if (stack.is(ACItems.MATRIX.get())) { model = texture = "matrix"; scale = .29f; yOffset = -.43f; }
        else if (stack.is(ACItems.WINDGEN_BASE.get())) { model = texture = "windgen_base"; scale = .42f; yOffset = -.43f; }
        else if (stack.is(ACItems.WINDGEN_PILLAR.get())) { model = texture = "windgen_pillar"; scale = .72f; yOffset = -.36f; }
        else if (stack.is(ACItems.WINDGEN_MAIN.get())) { model = texture = "windgen_main"; scale = .43f; yOffset = -.25f; }
        else if (stack.is(ACItems.DEV_NORMAL.get())) { model = texture = "developer_normal"; scale = .16f; yOffset = -.43f; rotY = 180; }
        else if (stack.is(ACItems.DEV_ADVANCED.get())) { model = texture = "developer_advanced"; scale = .16f; yOffset = -.43f; rotY = 180; }
        else if (stack.is(ACItems.DEVELOPER_PORTABLE.get())) { model = texture = "developer_portable"; scale = .32f; rotY = -10; }
        else if (stack.is(ACItems.TERMINAL_INSTALLER.get())) { model = texture = "terminal_installer"; scale = .42f; rotY = -15; }
        else if (stack.is(ACItems.MAG_HOOK.get())) { model = texture = "maghook"; scale = .0054f; }
        else if (stack.is(ACItems.SILBARN.get())) { model = texture = "silbarn"; scale = .075f; rotY = 30; }
        else if (stack.is(ACItems.WINDGEN_FAN.get())) { model = texture = "windgen_fan"; scale = .065f; }

        if (model == null) {
            super.renderByItem(stack, context, pose, buffers, light, overlay);
            return;
        }
        pose.pushPose();
        pose.translate(.5, .5 + yOffset, .5);
        if (context == ItemDisplayContext.GUI) {
            pose.mulPose(Axis.XP.rotationDegrees(-22));
            pose.mulPose(Axis.YP.rotationDegrees(35));
        }
        if (rotY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotY));
        pose.scale(scale, scale, scale);
        ACObjModel.get(model).render(pose, buffers.getBuffer(RenderType.entityTranslucent(texture(texture))),
                light, overlay, 1, 1, 1, 1);
        pose.popPose();
    }

    private static ResourceLocation guiIcon(ItemStack stack) {
        String name = null;
        if (stack.is(ACItems.COIN.get())) name = "coin_front";
        else if (stack.is(ACItems.DEVELOPER_PORTABLE.get())) {
            int energy = stack.getOrDefault(ACDataComponents.ENERGY.get(), 0);
            name = "developer_portable_" + (energy <= 0 ? "empty" : energy >= 10_000 ? "full" : "half");
        }
        else if (stack.is(ACItems.MATTER_UNIT.get())) {
            int frame = Minecraft.getInstance().level == null ? 0
                    : (int) ((Minecraft.getInstance().level.getGameTime() / 4) % 4);
            name = MatterUnitItem.isFilled(stack) ? "matter_unit_phase_liquid_" + frame : "matter_unit";
        }
        else if (stack.is(ACItems.TERMINAL_INSTALLER.get())) name = "terminal_installer";
        else if (stack.is(ACItems.MAG_HOOK.get())) name = "mag_hook";
        else if (stack.is(ACItems.SILBARN.get())) name = "silbarn";
        else if (stack.is(ACItems.WINDGEN_FAN.get())) name = "windgen_fan";
        return name == null ? null : ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/item/" + name + ".png");
    }

    private static void renderCoin(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation front = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/item/coin_front.png");
        ResourceLocation back = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/item/coin_back.png");
        pose.pushPose();
        pose.translate(0, 0, .51);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(front));
        itemVertex(out, pose, 0, 0, 0, 0, 1, light, overlay);
        itemVertex(out, pose, 1, 0, 0, 1, 1, light, overlay);
        itemVertex(out, pose, 1, 1, 0, 1, 0, light, overlay);
        itemVertex(out, pose, 0, 1, 0, 0, 0, light, overlay);
        pose.translate(0, 0, -.02);
        out = buffers.getBuffer(RenderType.entityCutoutNoCull(back));
        itemVertex(out, pose, 0, 0, 0, 1, 1, light, overlay);
        itemVertex(out, pose, 0, 1, 0, 1, 0, light, overlay);
        itemVertex(out, pose, 1, 1, 0, 0, 0, light, overlay);
        itemVertex(out, pose, 1, 0, 0, 0, 1, light, overlay);
        pose.popPose();
    }

    private static void renderFlatItem(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                       ResourceLocation texture) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        pose.translate(0, 0, .5);
        itemVertex(out, pose, 0, 0, 0, 0, 1, light, overlay);
        itemVertex(out, pose, 1, 0, 0, 1, 1, light, overlay);
        itemVertex(out, pose, 1, 1, 0, 1, 0, light, overlay);
        itemVertex(out, pose, 0, 1, 0, 0, 0, light, overlay);
        pose.popPose();
    }

    private static void renderCat(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0, 0, .5);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/block/cat_engine.png")));
        itemVertex(out, pose, 0, 0, 0, 0, 1, light, overlay);
        itemVertex(out, pose, 1, 0, 0, 1, 1, light, overlay);
        itemVertex(out, pose, 1, 1, 0, 1, 0, light, overlay);
        itemVertex(out, pose, 0, 1, 0, 0, 0, light, overlay);
        pose.popPose();
    }

    private static void itemVertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                                   float u, float v, int light, int overlay) {
        out.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(), 0, 0, 1);
    }

    @Override
    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
        ACObjModel.clearCache();
    }
}
