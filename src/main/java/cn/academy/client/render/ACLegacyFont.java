package cn.academy.client.render;

import cn.academy.AcademyCraft;
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
import java.io.InputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int MAX_FAMILY_CACHE = 512;
    private static final List<String> PREFERRED_FAMILIES = List.of(
            // Keep the original 1.12.2 fallback order first, then add modern Traditional Chinese
            // and freely available Linux families before considering arbitrary installed fonts.
            "Microsoft YaHei", "微软雅黑", "SimHei", "Adobe Heiti Std R",
            "Microsoft JhengHei",
            "PingFang TC", "PingFang SC", "Noto Sans CJK TC", "Noto Sans CJK SC",
            "Source Han Sans TC", "Source Han Sans SC", "WenQuanYi Zen Hei",
            "IPAexGothic", "IPAGothic", "Unifont-JP", "Unifont", "Noto Sans", "DejaVu Sans");
    private static final List<String> AVAILABLE_FAMILIES = availableFamilies();
    private static final Font BUNDLED_TRADITIONAL_CHINESE = loadBundledTraditionalChinese();
    private static final Map<String, String> FAMILY_CACHE = new LinkedHashMap<>(128, .75f, true);
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static long serial;

    private record Key(String text, int size, boolean bold) {}
    private record Entry(ResourceLocation texture, int width, int height) {}

    private static List<String> availableFamilies() {
        String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Map<String, String> byLowerCase = new HashMap<>();
        for (String name : names) byLowerCase.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String preferred : PREFERRED_FAMILIES) {
            String installed = byLowerCase.get(preferred.toLowerCase(Locale.ROOT));
            if (installed != null) ordered.add(installed);
        }
        Arrays.stream(names).sorted(Comparator.naturalOrder()).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    public static int width(Component text, float size, boolean bold) {
        if (!canRasterize(text.getString(), bold)) return minecraftWidth(text, size, bold);
        return entry(text.getString(), size, bold).width;
    }

    public static float fittedScale(Component text, float size, float maxWidth, boolean bold) {
        int width = width(text, size, bold);
        return maxWidth <= 0 ? 1 : Math.min(1, maxWidth / Math.max(1, width));
    }

    public static List<String> wrap(String text, int maxWidth, float requestedSize, boolean bold) {
        if (!canRasterize(text, bold)) return wrapMinecraft(text, maxWidth, requestedSize, bold);
        int size = Math.max(1, Math.round(requestedSize));
        Font font = fontFor(text, size, bold);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measure.createGraphics();
        configure(graphics);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> result = new ArrayList<>();
        int limit = Math.max(1, maxWidth);
        for (String source : text.split("\\n", -1)) {
            if (source.isEmpty()) {
                result.add("");
                continue;
            }
            BreakIterator breaks = BreakIterator.getLineInstance();
            breaks.setText(source);
            StringBuilder line = new StringBuilder();
            int start = breaks.first();
            for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
                String part = source.substring(start, end);
                if (metrics.stringWidth(line + part) <= limit) {
                    line.append(part);
                    continue;
                }
                if (!line.isEmpty()) result.add(line.toString().stripTrailing());
                line.setLength(0);
                part = part.stripLeading();
                for (int offset = 0; offset < part.length();) {
                    int codePoint = part.codePointAt(offset);
                    String glyph = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && metrics.stringWidth(line + glyph) > limit) {
                        result.add(line.toString().stripTrailing());
                        line.setLength(0);
                    }
                    line.append(glyph);
                    offset += Character.charCount(codePoint);
                }
            }
            result.add(line.toString().stripTrailing());
        }
        graphics.dispose();
        return result;
    }

    private static Font fontFor(String text, int size, boolean bold) {
        int style = bold ? Font.BOLD : Font.PLAIN;
        Font system = new Font(familyFor(text), style, size);
        if (system.canDisplayUpTo(text) < 0) return system;
        if (BUNDLED_TRADITIONAL_CHINESE != null) {
            Font bundled = BUNDLED_TRADITIONAL_CHINESE.deriveFont(style, (float) size);
            if (bundled.canDisplayUpTo(text) < 0) return bundled;
        }
        return system;
    }

    private static Font loadBundledTraditionalChinese() {
        String path = "/assets/academy/font/noto_sans_tc_vf.ttf";
        try (InputStream stream = ACLegacyFont.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            // Registration is useful to Java2D's internal fallback machinery, while retaining the
            // actual Font object guarantees that headless and stripped JREs use this exact file.
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception exception) {
            AcademyCraft.LOGGER.error("Unable to load bundled Traditional Chinese UI font", exception);
            return null;
        }
    }

    private static synchronized String familyFor(String text) {
        String cached = FAMILY_CACHE.get(text);
        if (cached != null) return cached;
        for (String family : AVAILABLE_FAMILIES) {
            if (new Font(family, Font.PLAIN, 12).canDisplayUpTo(text) < 0) {
                cacheFamily(text, family);
                return family;
            }
        }
        // fontFor() tries the bundled Traditional Chinese face after this logical-font fallback.
        cacheFamily(text, Font.SANS_SERIF);
        return Font.SANS_SERIF;
    }

    private static void cacheFamily(String text, String family) {
        FAMILY_CACHE.put(text, family);
        while (FAMILY_CACHE.size() > MAX_FAMILY_CACHE)
            FAMILY_CACHE.remove(FAMILY_CACHE.keySet().iterator().next());
    }

    static boolean canRasterize(String text, boolean bold) {
        return fontFor(text, 12, bold).canDisplayUpTo(text) < 0;
    }

    static Font selectedFont(String text, boolean bold) {
        return fontFor(text, 12, bold);
    }

    public static void draw(GuiGraphics gui, Component text, float x, float y, float size,
                            int argb, int alignment, float maxWidth, boolean bold) {
        String value = text.getString();
        if (value.isEmpty() || ((argb >>> 24) & 255) == 0) return;

        if (!canRasterize(value, bold)) {
            drawMinecraft(gui, text, x, y, size, argb, alignment, maxWidth, bold);
            return;
        }

        // Rasterise at the final framebuffer density instead of creating a tiny virtual texture and
        // enlarging it with the screen's pose.  The latter was especially visible in the developer
        // and terminal screens, whose scale is independent of Minecraft's GUI scale.
        float density = framebufferDensity(gui);
        Entry natural = entry(value, size * density, bold);
        float naturalWidth = natural.width / density;
        float fit = maxWidth <= 0 ? 1 : Math.min(1, maxWidth / Math.max(1, naturalWidth));
        Entry rendered = fit < .999f ? entry(value, size * density * fit, bold) : natural;
        float drawWidth = rendered.width / density;
        float drawX = alignment == CENTER ? x - drawWidth / 2 : alignment == RIGHT ? x - drawWidth : x;
        int alpha = argb >>> 24 & 255, red = argb >>> 16 & 255, green = argb >>> 8 & 255, blue = argb & 255;
        gui.setColor(red / 255f, green / 255f, blue / 255f, alpha / 255f);
        gui.pose().pushPose();
        gui.pose().translate(drawX, y, 20);
        gui.pose().scale(1 / density, 1 / density, 1);
        gui.blit(rendered.texture, 0, 0, rendered.width, rendered.height,
                0, 0, rendered.width, rendered.height, rendered.width, rendered.height);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    private static float framebufferDensity(GuiGraphics gui) {
        var matrix = gui.pose().last().pose();
        float scaleX = (float) Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01()
                + matrix.m02() * matrix.m02());
        float scaleY = (float) Math.sqrt(matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11()
                + matrix.m12() * matrix.m12());
        float poseScale = (scaleX + scaleY) * .5f;
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(.125f, Math.min(8, poseScale * guiScale));
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry value : CACHE.values()) minecraft.getTextureManager().release(value.texture);
        CACHE.clear();
    }

    private static int minecraftWidth(Component text, float size, boolean bold) {
        Component rendered = bold ? text.copy().withStyle(net.minecraft.ChatFormatting.BOLD) : text;
        float scale = size / Math.max(1, Minecraft.getInstance().font.lineHeight);
        return Math.max(1, Math.round(Minecraft.getInstance().font.width(rendered) * scale));
    }

    private static List<String> wrapMinecraft(String text, int maxWidth, float requestedSize, boolean bold) {
        List<String> result = new ArrayList<>();
        int limit = Math.max(1, maxWidth);
        for (String source : text.split("\\n", -1)) {
            if (source.isEmpty()) {
                result.add("");
                continue;
            }
            BreakIterator breaks = BreakIterator.getLineInstance();
            breaks.setText(source);
            StringBuilder line = new StringBuilder();
            int start = breaks.first();
            for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
                String part = source.substring(start, end);
                if (minecraftWidth(Component.literal(line + part), requestedSize, bold) <= limit) {
                    line.append(part);
                    continue;
                }
                if (!line.isEmpty()) result.add(line.toString().stripTrailing());
                line.setLength(0);
                part = part.stripLeading();
                for (int offset = 0; offset < part.length();) {
                    int codePoint = part.codePointAt(offset);
                    String glyph = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && minecraftWidth(Component.literal(line + glyph), requestedSize, bold) > limit) {
                        result.add(line.toString().stripTrailing());
                        line.setLength(0);
                    }
                    line.append(glyph);
                    offset += Character.charCount(codePoint);
                }
            }
            result.add(line.toString().stripTrailing());
        }
        return result;
    }

    private static void drawMinecraft(GuiGraphics gui, Component text, float x, float y, float size,
                                      int argb, int alignment, float maxWidth, boolean bold) {
        Component rendered = bold ? text.copy().withStyle(net.minecraft.ChatFormatting.BOLD) : text;
        var font = Minecraft.getInstance().font;
        float naturalScale = size / Math.max(1, font.lineHeight);
        float naturalWidth = font.width(rendered) * naturalScale;
        float fit = maxWidth <= 0 ? 1 : Math.min(1, maxWidth / Math.max(1, naturalWidth));
        float scale = naturalScale * fit;
        float drawWidth = font.width(rendered) * scale;
        float drawX = alignment == CENTER ? x - drawWidth / 2 : alignment == RIGHT ? x - drawWidth : x;
        gui.pose().pushPose();
        gui.pose().translate(drawX, y, 20);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, rendered, 0, 0, argb, false);
        gui.pose().popPose();
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
        Font font = fontFor(key.text, key.size, key.bold);
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
        // Java2D already supplies antialiased edge alpha.  Nearest sampling keeps the one-texel-per-
        // framebuffer-pixel raster sharp; draw() compensates for the current pose and GUI scale.
        texture.setFilter(false, false);
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
