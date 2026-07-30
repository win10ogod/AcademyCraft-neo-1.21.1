package cn.academy.client.render;

import cn.academy.AcademyCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** CPU-baked modern equivalent of the 1.12.2 skill_progbar two-sampler shader. */
public final class ACSkillProgressTextures {
    private static final ResourceLocation MASK = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "textures/guis/developer/skill_radial_mask.png");
    private static final Map<Key, ResourceLocation> CACHE = new HashMap<>();
    private static long serial;

    private record Key(String circle, int progress) {}

    public static synchronized ResourceLocation get(String circle, float progress) {
        int step = Mth.clamp(Math.round(progress * 100), 0, 100);
        Key key = new Key(circle, step);
        return CACHE.computeIfAbsent(key, ACSkillProgressTextures::bake);
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        CACHE.values().forEach(minecraft.getTextureManager()::release);
        CACHE.clear();
    }

    private static ResourceLocation bake(Key key) {
        ResourceLocation sourceLocation = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "textures/guis/developer/" + key.circle + ".png");
        try (var sourceStream = Minecraft.getInstance().getResourceManager().getResource(sourceLocation)
                     .orElseThrow().open();
             var maskStream = Minecraft.getInstance().getResourceManager().getResource(MASK)
                     .orElseThrow().open();
             NativeImage source = NativeImage.read(sourceStream);
             NativeImage mask = NativeImage.read(maskStream)) {
            int width = source.getWidth(), height = source.getHeight();
            NativeImage output = new NativeImage(width, height, true);
            float progress = key.progress / 100f;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int mx = x * mask.getWidth() / width;
                int my = y * mask.getHeight() / height;
                float threshold = FastColor.ABGR32.red(mask.getPixelRGBA(mx, my)) / 255f;
                output.setPixelRGBA(x, y, progress > threshold ? source.getPixelRGBA(x, y) : 0);
            }
            DynamicTexture texture = new DynamicTexture(output);
            texture.setFilter(true, false);
            return Minecraft.getInstance().getTextureManager().register(
                    "academy_skill_progress/" + key.circle + "_" + key.progress + "_" + serial++, texture);
        } catch (IOException exception) {
            AcademyCraft.LOGGER.error("Unable to bake developer progress texture {}", sourceLocation, exception);
            return sourceLocation;
        }
    }

    private ACSkillProgressTextures() {}
}
