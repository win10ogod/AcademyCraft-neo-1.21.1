package cn.academy.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Modern replacement for LambdaLib's AC_Normal/AC_Bold TrueType renderer.
 *
 * <p>The 1.12.2 UI rasterised a system TrueType font and treated FontOption sizes as actual
 * virtual pixels. Scaling Minecraft's nine-pixel bitmap font independently on X/Y cannot match
 * that output, especially after the terminal's perspective scale. This cache restores the same
 * antialiased, uniformly-scaled text contract without relying on legacy OpenGL.</p>
 */
public final class ACLegacyFont {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    private static final int MAX_CACHE = 768;
    private static final String FAMILY = chooseFamily();
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static long serial;

    private record Key(String text, int size, boolean bold) {}
    private record Entry(ResourceLocation texture, int width, int height) {}

    private static String chooseFamily() {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String preferred : new String[]{"Microsoft YaHei", "微软雅黑", "SimHei", "Noto Sans CJK TC",
                "Noto Sans CJK SC", "Noto Sans", "DejaVu Sans"})
            if (available.contains(preferred)) return preferred;
        return Font.SANS_SERIF;
    }

    public static int width(Component text, float size, boolean bold) {
        return entry(text.getString(), size, bold).width;
    }

    public static float fittedScale(Component text, float size, float maxWidth, boolean bold) {
        Entry entry = entry(text.getString(), size, bold);
        return maxWidth <= 0 ? 1 : Math.min(1, maxWidth / Math.max(1, entry.width));
    }

    public static List<String> wrap(String text, int maxWidth, float requestedSize, boolean bold) {
        int size = Math.max(1, Math.round(requestedSize));
        Font font = new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, size);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measure.createGraphics();
        configure(graphics);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> result = new ArrayList<>();
        for (String source : text.split("\\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < source.length();) {
                int codePoint = source.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && metrics.stringWidth(line + glyph) > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                line.append(glyph);
                offset += Character.charCount(codePoint);
            }
            result.add(line.toString());
        }
        graphics.dispose();
        return result;
    }

    public static void draw(GuiGraphics gui, Component text, float x, float y, float size,
                            int argb, int alignment, float maxWidth, boolean bold) {
        String value = text.getString();
        if (value.isEmpty() || ((argb >>> 24) & 255) == 0) return;
        Entry entry = entry(value, size, bold);
        float fit = maxWidth <= 0 ? 1 : Math.min(1, maxWidth / Math.max(1, entry.width));
        float drawWidth = entry.width * fit;
        float drawHeight = entry.height * fit;
        float drawX = alignment == CENTER ? x - drawWidth / 2 : alignment == RIGHT ? x - drawWidth : x;
        int alpha = argb >>> 24 & 255, red = argb >>> 16 & 255, green = argb >>> 8 & 255, blue = argb & 255;
        gui.setColor(red / 255f, green / 255f, blue / 255f, alpha / 255f);
        gui.pose().pushPose();
        gui.pose().translate(drawX, y, 20);
        gui.pose().scale(fit, fit, 1);
        gui.blit(entry.texture, 0, 0, entry.width, entry.height,
                0, 0, entry.width, entry.height, entry.width, entry.height);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry value : CACHE.values()) minecraft.getTextureManager().release(value.texture);
        CACHE.clear();
    }

    private static synchronized Entry entry(String text, float requestedSize, boolean bold) {
        int size = Math.max(1, Math.round(requestedSize));
        Key key = new Key(text, size, bold);
        Entry cached = CACHE.get(key);
        if (cached != null) return cached;
        Entry created = rasterize(key);
        CACHE.put(key, created);
        while (CACHE.size() > MAX_CACHE) {
            Key eldest = CACHE.keySet().iterator().next();
            Entry removed = CACHE.remove(eldest);
            if (removed != null) Minecraft.getInstance().getTextureManager().release(removed.texture);
        }
        return created;
    }

    private static Entry rasterize(Key key) {
        Font font = new Font(FAMILY, key.bold ? Font.BOLD : Font.PLAIN, key.size);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measure.createGraphics();
        configure(graphics);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int width = Math.max(1, metrics.stringWidth(key.text) + 4);
        int height = Math.max(1, metrics.getHeight() + 2);
        graphics.dispose();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        configure(graphics);
        graphics.setFont(font);
        graphics.setColor(java.awt.Color.WHITE);
        metrics = graphics.getFontMetrics();
        graphics.drawString(key.text, 2, 1 + metrics.getAscent());
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            nativeImage.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(image.getRGB(x, y)));
        DynamicTexture texture = new DynamicTexture(nativeImage);
        texture.setFilter(true, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
                "academy_legacy_font/" + Long.toUnsignedString(serial++, 36).toLowerCase(Locale.ROOT), texture);
        return new Entry(location, width, height);
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private ACLegacyFont() {}
}
