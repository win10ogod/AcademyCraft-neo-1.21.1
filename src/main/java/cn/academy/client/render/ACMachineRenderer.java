package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.block.ACMachineBlock;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Restores the original machine OBJ geometry and moving matrix/wind/cat-engine parts. */
public final class ACMachineRenderer implements BlockEntityRenderer<ACMachineBlockEntity> {
    public ACMachineRenderer(BlockEntityRendererProvider.Context ignored) {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void render(ACMachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        switch (machine.kind()) {
            case SOLAR_GENERATOR -> renderModel(machine, pose, buffers, packedLight, "solar", "solar", .014f, 90);
            case PHASE_GENERATOR -> {
                int frame = Math.max(0, Math.min(4, Math.round(machine.phaseLiquid() / 2_000f)));
                renderModel(machine, pose, buffers, packedLight, "ip_gen", "ip_gen" + frame, 1, 0);
            }
            case MATRIX -> renderMatrix(machine, partialTick, pose, buffers, packedLight);
            case WIND_BASE -> renderModel(machine, pose, buffers, packedLight, "windgen_base",
                    machine.windStructureCompleteForRender() ? "windgen_base" : "windgen_base_disabled", 1, 0);
            case WIND_PILLAR -> renderModel(machine, pose, buffers, packedLight, "windgen_pillar", "windgen_pillar", 1, 0);
            case WIND_GENERATOR -> renderWindMain(machine, partialTick, pose, buffers, packedLight);
            case DEVELOPER_NORMAL -> renderModel(machine, pose, buffers, packedLight,
                    "developer_normal", "developer_normal", .5f, 0);
            case DEVELOPER_ADVANCED -> renderModel(machine, pose, buffers, packedLight,
                    "developer_advanced", "developer_advanced", .5f, 0);
            case CAT_ENGINE -> renderCatEngine(machine, partialTick, pose, buffers, packedLight);
            default -> { }
        }
    }

    private static void renderModel(ACMachineBlockEntity machine, PoseStack pose, MultiBufferSource buffers, int light,
                                    String modelName, String textureName, float scale, float rotationY) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        rotateForFacing(machine, pose);
        if (rotationY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotationY));
        pose.scale(scale, scale, scale);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture(textureName)));
        ACObjModel.get(modelName).render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }

    private static void renderMatrix(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        ACObjModel model = ACObjModel.get("matrix");
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture("matrix")));
        pose.pushPose();
        pose.translate(1, 0, 1);
        rotateForFacing(machine, pose);
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, "Main", "Core");
        if (machine.isMatrixWorking()) {
            float time = (machine.getLevel() == null ? 0 : machine.getLevel().getGameTime()) + partialTick;
            for (int index = 0; index < 3; index++) {
                pose.pushPose();
                pose.translate(0, .1 * Math.sin(time / 18f + index * .7), 0);
                pose.mulPose(Axis.YP.rotationDegrees(time * 2.5f + index * 120));
                model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, .92f, "Shield");
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static void renderWindMain(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        rotateForFacing(machine, pose);
        ACObjModel.get("windgen_main").render(pose,
                buffers.getBuffer(RenderType.entityTranslucent(texture("windgen_main"))), light,
                OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (machine.items.getStackInSlot(0).is(ACItems.WINDGEN_FAN.get())
                && machine.windStructureCompleteForRender() && machine.windNoObstacleForRender()) {
            float time = (machine.getLevel() == null ? 0 : machine.getLevel().getGameTime()) + partialTick;
            pose.pushPose();
            pose.translate(0, .5, .82);
            pose.mulPose(Axis.ZN.rotationDegrees(time * 3));
            ACObjModel.get("windgen_fan").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucent(texture("windgen_fan"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void rotateForFacing(ACMachineBlockEntity machine, PoseStack pose) {
        if (!machine.getBlockState().hasProperty(ACMachineBlock.FACING)) return;
        float angle = switch (machine.getBlockState().getValue(ACMachineBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        if (angle != 0) pose.mulPose(Axis.YP.rotationDegrees(angle));
    }

    private static void renderCatEngine(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                        MultiBufferSource buffers, int light) {
        float time = (machine.getLevel() == null ? 0 : machine.getLevel().getGameTime()) + partialTick;
        pose.pushPose();
        pose.translate(.5, .5 + .03 * Math.sin(time * .12), .5);
        pose.mulPose(Axis.XP.rotationDegrees(time * 7.5f));
        pose.translate(-.5, -.5, 0);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/block/cat_engine.png")));
        vertex(out, pose, 0, 0, 0, 0, 1, light);
        vertex(out, pose, 1, 0, 0, 1, 1, light);
        vertex(out, pose, 1, 1, 0, 1, 0, light);
        vertex(out, pose, 0, 1, 0, 0, 0, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                               float u, float v, int light) {
        out.addVertex(pose.last(), x, y, z).setColor(1, 1, 1, 1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
    }

    @Override
    public AABB getRenderBoundingBox(ACMachineBlockEntity machine) {
        double radius = machine.kind() == MachineKind.WIND_GENERATOR ? 9
                : machine.kind() == MachineKind.MATRIX || machine.kind() == MachineKind.DEVELOPER_NORMAL
                || machine.kind() == MachineKind.DEVELOPER_ADVANCED ? 3 : 2;
        return new AABB(machine.getBlockPos()).inflate(radius);
    }

    @Override public int getViewDistance() { return 128; }
}
