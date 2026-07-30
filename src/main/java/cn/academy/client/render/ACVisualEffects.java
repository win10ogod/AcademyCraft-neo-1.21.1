package cn.academy.client.render;

import cn.academy.network.VisualEffectPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Additive modern replacement for the legacy fixed-pipeline GLSL/ray effect stack. */
public final class ACVisualEffects {
    private static final class Effect {
        final String type;
        final Vec3 start, end;
        final float scale;
        final int color, duration, seed;
        int remaining;
        BlockPos scanCenter;
        List<DetectedOre> detectedOres = List.of();

        Effect(VisualEffectPayload payload) {
            type = payload.effect();
            start = new Vec3(payload.startX(), payload.startY(), payload.startZ());
            end = new Vec3(payload.endX(), payload.endY(), payload.endZ());
            scale = payload.scale();
            color = payload.color();
            duration = remaining = Math.max(1, payload.duration());
            seed = java.util.Objects.hash(type, start, end);
        }
    }

    private record DetectedOre(BlockPos pos, int color) { }

    private static final List<Effect> EFFECTS = new ArrayList<>();

    public static void clientTick(Minecraft minecraft) {
        if (minecraft.player == null) {
            EFFECTS.clear();
            return;
        }
        ListTag queue = minecraft.player.getPersistentData().getList("academy:visual_effect_queue", 10);
        for (int index = 0; index < queue.size(); index++) {
            CompoundTag tag = queue.getCompound(index);
            add(new VisualEffectPayload(tag.getString("Effect"),
                    tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ"),
                    tag.getDouble("EX"), tag.getDouble("EY"), tag.getDouble("EZ"),
                    tag.getFloat("Scale"), tag.getInt("Color"), tag.getInt("Duration")));
        }
        if (!queue.isEmpty()) minecraft.player.getPersistentData().remove("academy:visual_effect_queue");
        for (Effect effect : EFFECTS) {
            if (effect.type.equals("mine_detect") && (effect.scanCenter == null
                    || effect.scanCenter.distManhattan(minecraft.player.blockPosition()) > 4
                    || effect.remaining % 20 == 0)) scanDetectedOres(effect, minecraft);
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) if (--iterator.next().remaining <= 0) iterator.remove();
    }

    public static void add(VisualEffectPayload payload) {
        if (EFFECTS.size() >= 128) EFFECTS.removeFirst();
        Effect effect = new Effect(payload);
        EFFECTS.add(effect);
        if (effect.type.equals("mine_detect")) scanDetectedOres(effect, Minecraft.getInstance());
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = ACShaders.ENERGY;
        VertexConsumer out = buffers.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Effect effect : EFFECTS) if (!effect.type.equals("mine_detect"))
            renderEffect(effect, pose, out, camera, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        buffers.endBatch(type);
        VertexConsumer mineOut = buffers.getBuffer(ACShaders.MINE_VIEW);
        for (Effect effect : EFFECTS) if (effect.type.equals("mine_detect")) renderDetectedOres(effect, pose, mineOut);
        buffers.endBatch(ACShaders.MINE_VIEW);
        pose.popPose();
    }

    private static void scanDetectedOres(Effect effect, Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return;
        BlockPos center = minecraft.player.blockPosition();
        int range = Mth.clamp(Mth.ceil(effect.scale), 1, 28);
        int rangeSquared = range * range;
        List<DetectedOre> ores = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(minecraft.level.getMinBuildHeight(), center.getY() - range);
        int maxY = Math.min(minecraft.level.getMaxBuildHeight() - 1, center.getY() + range);
        for (int x = center.getX() - range; x <= center.getX() + range; x++) {
            for (int z = center.getZ() - range; z <= center.getZ() + range; z++) {
                if (!minecraft.level.hasChunkAt(cursor.set(x, center.getY(), z))) continue;
                for (int y = minY; y <= maxY; y++) {
                    int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > rangeSquared) continue;
                    cursor.set(x, y, z);
                    BlockState state = minecraft.level.getBlockState(cursor);
                    if (!isOre(state)) continue;
                    int color = effect.color == 0 ? 0xFF73C8E3 : state.is(BlockTags.NEEDS_DIAMOND_TOOL)
                            ? 0xFFEB6D54 : state.is(BlockTags.NEEDS_IRON_TOOL)
                            ? 0xFF61CC5E : state.is(BlockTags.NEEDS_STONE_TOOL) ? 0xFF57E7F8 : 0xFFA1B5BC;
                    ores.add(new DetectedOre(cursor.immutable(), color));
                    if (ores.size() >= 8400) break;
                }
                if (ores.size() >= 8400) break;
            }
            if (ores.size() >= 8400) break;
        }
        effect.scanCenter = center.immutable();
        effect.detectedOres = List.copyOf(ores);
    }

    private static boolean isOre(BlockState state) {
        String path = state.getBlockHolder().unwrapKey().map(key -> key.location().getPath()).orElse("");
        return path.endsWith("_ore") || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    private static void renderDetectedOres(Effect effect, PoseStack pose, VertexConsumer out) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Vec3 player = minecraft.player.position();
        float life = Mth.clamp(effect.remaining / 12f, 0, 1);
        for (DetectedOre ore : effect.detectedOres) {
            double distance = Math.sqrt(ore.pos.distToCenterSqr(player.x, player.y, player.z));
            float alpha = Mth.clamp((float) (.3 + (1 - distance / Math.max(1, effect.scale) * 2.2) * .7), .08f, .7f) * life;
            renderOreBox(pose, out, ore.pos, ore.color, alpha);
        }
    }

    private static void renderOreBox(PoseStack pose, VertexConsumer out, BlockPos pos, int color, float alpha) {
        double e = .05;
        Vec3 a = new Vec3(pos.getX() + e, pos.getY() + e, pos.getZ() + e);
        Vec3 b = new Vec3(pos.getX() + 1 - e, pos.getY() + 1 - e, pos.getZ() + 1 - e);
        Vec3 p000 = new Vec3(a.x, a.y, a.z), p001 = new Vec3(a.x, a.y, b.z);
        Vec3 p010 = new Vec3(a.x, b.y, a.z), p011 = new Vec3(a.x, b.y, b.z);
        Vec3 p100 = new Vec3(b.x, a.y, a.z), p101 = new Vec3(b.x, a.y, b.z);
        Vec3 p110 = new Vec3(b.x, b.y, a.z), p111 = new Vec3(b.x, b.y, b.z);
        quad(pose, out, p000, p100, p110, p010, color, alpha);
        quad(pose, out, p101, p001, p011, p111, color, alpha);
        quad(pose, out, p001, p000, p010, p011, color, alpha);
        quad(pose, out, p100, p101, p111, p110, color, alpha);
        quad(pose, out, p010, p110, p111, p011, color, alpha);
        quad(pose, out, p001, p101, p100, p000, color, alpha);
    }

    private static void renderEffect(Effect effect, PoseStack pose, VertexConsumer out, Vec3 camera, float partialTick) {
        float life = Mth.clamp((effect.remaining - partialTick) / effect.duration, 0, 1);
        float alpha = Math.min(1, life * 2.5f);
        float pulse = 1 + .12f * Mth.sin((effect.duration - effect.remaining + partialTick) * .7f);
        float width = effect.scale * pulse;
        switch (effect.type) {
            case "arc" -> jaggedBeam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha, effect.seed,
                    effect.duration - effect.remaining);
            case "railgun" -> {
                beam(pose, out, effect.start, effect.end, camera, width * 1.8f, effect.color, alpha);
                beam(pose, out, effect.start, effect.end, camera, width * .55f, 0xFFFFFFFF, alpha);
                ring(pose, out, effect.end, width * 5, effect.color, alpha, 24, 0);
            }
            case "meltdowner" -> {
                beam(pose, out, effect.start, effect.end, camera, width * 2.2f, effect.color, alpha);
                beam(pose, out, effect.start, effect.end, camera, width * .8f, 0xFFE8FFF0, alpha);
                ring(pose, out, effect.end, width * 4, effect.color, alpha, 24, 1);
            }
            case "sphere", "shield", "reflection" -> sphere(pose, out, effect.end,
                    width * (1.2f + (1 - life) * .5f), effect.color, alpha, 28);
            case "teleport" -> {
                float radius = width * (1.2f + (1 - life) * 4);
                ring(pose, out, effect.start, radius, effect.color, alpha, 28, 0);
                ring(pose, out, effect.start.add(0, .7, 0), radius * .75f, effect.color, alpha, 28, 0);
                ring(pose, out, effect.end, radius, effect.color, alpha, 28, 0);
                ring(pose, out, effect.end.add(0, .7, 0), radius * .75f, effect.color, alpha, 28, 0);
            }
            case "wave" -> {
                beam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha);
                ring(pose, out, effect.end, width * (3 + (1 - life) * 6), effect.color, alpha, 32, 0);
            }
            case "plasma" -> {
                jaggedBeam(pose, out, effect.start, effect.end, camera, width * 2.2f, effect.color, alpha,
                        effect.seed, effect.duration - effect.remaining);
                sphere(pose, out, effect.end, width * (4 + (1 - life) * 5), effect.color, alpha, 32);
            }
            case "tornado" -> spiral(pose, out, effect.start, width * 5, effect.color, alpha,
                    effect.duration - effect.remaining + partialTick);
            default -> beam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha);
        }
    }

    private static void jaggedBeam(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end, Vec3 camera,
                                   float width, int color, float alpha, int seed, float time) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length < .01) {
            sphere(pose, out, start, width * 3, color, alpha, 18);
            return;
        }
        Vec3 normal = direction.normalize();
        Vec3 axis = Math.abs(normal.y) < .9 ? normal.cross(new Vec3(0, 1, 0)).normalize()
                : normal.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 last = start;
        int segments = Mth.clamp((int) (length * 1.5), 7, 28);
        for (int i = 1; i <= segments; i++) {
            double progress = i / (double) segments;
            double envelope = Math.sin(Math.PI * progress);
            double noiseA = Math.sin(seed * .013 + i * 2.17 + time * .9) * width * 2.6 * envelope;
            double noiseB = Math.cos(seed * .021 + i * 1.71 - time * .7) * width * 1.9 * envelope;
            Vec3 point = start.add(direction.scale(progress)).add(axis.scale(noiseA))
                    .add(normal.cross(axis).scale(noiseB));
            beam(pose, out, last, point, camera, width, color, alpha);
            last = point;
        }
    }

    private static void beam(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end, Vec3 camera,
                             float width, int color, float alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0e-5) return;
        Vec3 view = camera.subtract(start);
        Vec3 side = direction.cross(view);
        if (side.lengthSqr() < 1.0e-5) side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-5) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        quad(pose, out, start.subtract(side), end.subtract(side), end.add(side), start.add(side), color, alpha);
        Vec3 second = direction.normalize().cross(side.normalize()).scale(width);
        quad(pose, out, start.subtract(second), end.subtract(second), end.add(second), start.add(second), color, alpha * .72f);
    }

    private static void sphere(PoseStack pose, VertexConsumer out, Vec3 center, float radius,
                               int color, float alpha, int segments) {
        ring(pose, out, center, radius, color, alpha, segments, 0);
        ring(pose, out, center, radius, color, alpha, segments, 1);
        ring(pose, out, center, radius, color, alpha, segments, 2);
    }

    private static void ring(PoseStack pose, VertexConsumer out, Vec3 center, float radius, int color,
                             float alpha, int segments, int plane) {
        float thickness = Math.max(.012f, radius * .025f);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 p0 = circlePoint(center, radius, a0, plane);
            Vec3 p1 = circlePoint(center, radius, a1, plane);
            Vec3 q0 = circlePoint(center, radius + thickness, a0, plane);
            Vec3 q1 = circlePoint(center, radius + thickness, a1, plane);
            quad(pose, out, p0, p1, q1, q0, color, alpha);
        }
    }

    private static Vec3 circlePoint(Vec3 center, float radius, double angle, int plane) {
        double a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
        return switch (plane) {
            case 1 -> center.add(a, b, 0);
            case 2 -> center.add(0, a, b);
            default -> center.add(a, 0, b);
        };
    }

    private static void spiral(PoseStack pose, VertexConsumer out, Vec3 center, float radius,
                               int color, float alpha, float time) {
        Vec3 previous = center;
        for (int i = 1; i <= 36; i++) {
            float y = i / 12f;
            float r = radius * (1 - i / 50f);
            double angle = i * .65 + time * .3;
            Vec3 current = center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            beam(pose, out, previous, current, center.add(0, 1, 4), Math.max(.025f, radius * .035f), color, alpha);
            previous = current;
        }
    }

    private static void quad(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int color, float alpha) {
        int ca = Math.round(((color >>> 24) & 255) * alpha);
        if ((color >>> 24) == 0) ca = Math.round(255 * alpha);
        int argb = (Mth.clamp(ca, 0, 255) << 24) | (color & 0xFFFFFF);
        out.addVertex(pose.last(), (float) a.x, (float) a.y, (float) a.z).setColor(argb);
        out.addVertex(pose.last(), (float) b.x, (float) b.y, (float) b.z).setColor(argb);
        out.addVertex(pose.last(), (float) c.x, (float) c.y, (float) c.z).setColor(argb);
        out.addVertex(pose.last(), (float) d.x, (float) d.y, (float) d.z).setColor(argb);
    }

    private ACVisualEffects() {}
}
