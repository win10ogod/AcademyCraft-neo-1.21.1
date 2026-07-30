package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.BufferedReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Native recreation of the 1.12.2 about.xml credits/donation application. */
public final class AboutScreen extends Screen {
    private static final int VIRTUAL_WIDTH = 742;
    private static final int VIRTUAL_HEIGHT = 923;
    private static final int AREA_X = 53;
    private static final int AREA_Y = 266;
    private static final int SCROLL_TOP = AREA_Y + 58;
    private static final int SCROLL_WIDTH = 620;
    private static final int SCROLL_HEIGHT = 540;
    private static final ResourceLocation BACKGROUND = texture("guis/about/bg.png");
    private static final ResourceLocation BUTTON_GLOW = texture("guis/about/button_glow.png");

    private final List<TextItem> credits = new ArrayList<>();
    private final List<TextItem> donation = new ArrayList<>();
    private boolean donateTab;
    private float scrollProgress;
    private float creditsMaxY;
    private boolean draggingScroll;
    private String hoveringUrl;

    public AboutScreen() {
        super(Component.translatable("ac.app.about.name"));
        buildContent();
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/" + path);
    }

    private float scale() {
        return Math.min(.25f, Math.min((width - 8f) / VIRTUAL_WIDTH, (height - 8f) / VIRTUAL_HEIGHT));
    }

    private float left() { return (width - VIRTUAL_WIDTH * scale()) * .5f; }
    private float top() { return (height - VIRTUAL_HEIGHT * scale()) * .5f; }

    private void buildContent() {
        credits.clear();
        donation.clear();
        float y = 60;
        addCredit(0, y, "Presented by Lambda Innovation", Align.CENTER, true, 30); y += 30;
        addCredit(0, y, "ac.li-dev.cn", Align.CENTER, true, 30); y += 90;

        String[][] staff = {
                {"Project Direction", "WeAthFolD"},
                {"Game Design", "Eing", "WeAthFolD"},
                {"Programming", "Paindar", "ColdHikari", "KSkun", "EAirPeter", "WeAthFolD", "冥狼", "acaly"},
                {"Art", "Nolife_M", "H控"},
                {"QA", "xiao6", "BlueFeather"},
                {"Website", "LeLe", "NAT", "yinfb", "KSkun", "Cafe"},
                {"Localization", "mkpoli (Japanese)", "柳荫理乃 (Japanese)", "dtraitor (Russian)"},
                {"GitHub Contributors", "3TUSK", "l89669", "MrBenjaminBowman", "berry64"}
        };
        for (String[] group : staff) {
            addCredit(-30, y, group[0], Align.RIGHT, true, 30);
            for (int i = 1; i < group.length; i++) {
                addCredit(30, y, group[i], Align.LEFT, false, 30);
                y += 30;
            }
            y += 15;
        }
        y += 30;
        addCredit(0, y, "Donators", Align.CENTER, true, 30); y += 33;
        String[] hints = Component.translatable("ac.about.donators_info").getString().split("\\\\n|\\n");
        for (String hint : hints) {
            addCredit(0, y, hint, Align.CENTER, false, 21); y += 21;
        }
        y += 45;
        List<String> donors = readBundledDonators();
        for (int i = 0; i < donors.size(); i++) {
            float x = 30 + (i % 3) * (620 - 60 - 150) / 2f - 310;
            addCredit(x, y, donors.get(i), Align.LEFT, false, 24);
            if (i % 3 == 2) y += 24;
        }
        if (donors.size() % 3 != 0) y += 24;
        y += 60;
        addCredit(0, y, "Thank you for playing!", Align.CENTER, true, 30); y += 30;
        creditsMaxY = y + 30;

        // The original about.conf has no zh_tw donation block, so 1.12.2 falls back to en_us.
        float donationY = 100;
        float donationX = -280;
        addDonation(donationX, donationY, "Thank you for playing AcademyCraft!!", Align.LEFT, false, 30, null); donationY += 30;
        addDonation(donationX, donationY, "If you enjoyed the mod, you can support us via:", Align.LEFT, false, 30, null); donationY += 40;
        addDonation(donationX, donationY, "Patreon", Align.LEFT, false, 40,
                "https://www.patreon.com/WeAthFolD"); donationY += 50;
        addDonation(donationX, donationY, "We will make this mod more intriguing! >3<", Align.LEFT, false, 30, null);
        donationY += 150;
        addDonation(-donationX, donationY, "Lambda Innovation", Align.RIGHT, false, 30, null);
    }

    private List<String> readBundledDonators() {
        List<String> result = new ArrayList<>();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "config/about.conf");
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow();
            try (BufferedReader reader = resource.openAsReader()) {
                boolean inList = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    String clean = line.strip();
                    if (clean.startsWith("donators:")) { inList = true; continue; }
                    if (!inList) continue;
                    if (clean.equals("]") || clean.equals("],")) break;
                    if (!clean.startsWith("\"")) continue;
                    int end = clean.lastIndexOf('"');
                    if (end > 0) result.add(clean.substring(1, end));
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private void addCredit(float x, float y, String text, Align align, boolean bold, float size) {
        credits.add(new TextItem(x, y, text, align, bold, size, null));
    }

    private void addDonation(float x, float y, String text, Align align, boolean bold, float size, String url) {
        donation.add(new TextItem(x, y, text, align, bold, size, url));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        float scale = scale();
        float virtualMouseX = (mouseX - left()) / scale;
        float virtualMouseY = (mouseY - top()) / scale;
        gui.pose().pushPose();
        gui.pose().translate(left(), top(), 0);
        gui.pose().scale(scale, scale, 1);
        gui.blit(BACKGROUND, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT,
                0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        drawTab(gui, 0, "Credits", !donateTab, inside(virtualMouseX, virtualMouseY, AREA_X, AREA_Y, 315, 58));
        drawTab(gui, 315, "Donate", donateTab, inside(virtualMouseX, virtualMouseY, AREA_X + 315, AREA_Y, 315, 58));

        float maxOffset = Math.max(0, creditsMaxY - SCROLL_HEIGHT + 50);
        float yOffset = donateTab ? 0 : scrollProgress * maxOffset;
        hoveringUrl = null;
        int scissorLeft = Math.round(left() + AREA_X * scale);
        int scissorTop = Math.round(top() + SCROLL_TOP * scale);
        int scissorRight = Math.round(left() + (AREA_X + SCROLL_WIDTH) * scale);
        int scissorBottom = Math.round(top() + (SCROLL_TOP + SCROLL_HEIGHT) * scale);
        gui.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);
        for (TextItem item : donateTab ? donation : credits) {
            float itemY = item.y - yOffset;
            if (itemY < -50 || itemY > SCROLL_HEIGHT + 50) continue;
            float centerX = AREA_X + SCROLL_WIDTH * .5f;
            int color = item.url == null ? 0xFFF3F7F8 : 0xFF5BB4FF;
            float textScale = fittedScale(item);
            float textWidth = measuredWidth(item.text, item.bold) * textScale;
            float localX = switch (item.align) {
                case LEFT -> centerX + item.x;
                case CENTER -> centerX + item.x - textWidth * .5f;
                case RIGHT -> centerX + item.x - textWidth;
            };
            boolean hover = item.url != null && virtualMouseX >= localX
                    && virtualMouseX <= localX + textWidth
                    && virtualMouseY >= SCROLL_TOP + itemY
                    && virtualMouseY <= SCROLL_TOP + itemY + item.size;
            if (hover) {
                color = 0xFF8ECBFF;
                hoveringUrl = item.url;
            }
            drawText(gui, item.text, localX, SCROLL_TOP + itemY, item.size, item.maxWidth, color, item.bold);
        }
        gui.disableScissor();

        int handleY = AREA_Y + 58 + Math.round(472 * scrollProgress);
        gui.fill(AREA_X + 620, handleY, AREA_X + 630, handleY + 70,
                draggingScroll ? 0xE6FFFFFF : 0x99FFFFFF);
        gui.pose().popPose();
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawTab(GuiGraphics gui, int relativeX, String label, boolean selected, boolean hovered) {
        int x = AREA_X + relativeX;
        int y = AREA_Y;
        gui.fill(x, y, x + 315, y + 58, selected ? 0x80FFFFFF : hovered ? 0x55FFFFFF : 0x33FFFFFF);
        if (selected) gui.blit(BUTTON_GLOW, x - 8, y - 11, 332, 80,
                0, 0, 332, 80, 332, 80);
        drawCenteredText(gui, label, x, y + 6, 315, 42, selected ? 0xFF3D3F4B : 0xFFFFFFFF);
    }

    private int measuredWidth(String text, boolean bold) {
        Component component = bold ? Component.literal(text).setStyle(Style.EMPTY.withBold(true)) : Component.literal(text);
        return font.width(component);
    }

    private float fittedScale(TextItem item) {
        return Math.min(item.size / Math.max(1f, font.lineHeight),
                item.maxWidth / Math.max(1f, measuredWidth(item.text, item.bold)));
    }

    private void drawText(GuiGraphics gui, String text, float x, float y, float fontSize,
                          float maxWidth, int color, boolean bold) {
        Component component = bold ? Component.literal(text).setStyle(Style.EMPTY.withBold(true)) : Component.literal(text);
        float scale = Math.min(fontSize / Math.max(1f, font.lineHeight),
                maxWidth / Math.max(1f, font.width(component)));
        gui.pose().pushPose();
        gui.pose().translate(x, y, 5);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, component, 0, 0, color, false);
        gui.pose().popPose();
    }

    private void drawCenteredText(GuiGraphics gui, String text, float x, float y, float width, float size, int color) {
        float textScale = size / Math.max(1f, font.lineHeight);
        float textWidth = font.width(text) * textScale;
        drawText(gui, text, x + (width - textWidth) * .5f, y, size, width - 12, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = (float) ((mouseX - left()) / scale());
        float vy = (float) ((mouseY - top()) / scale());
        if (inside(vx, vy, AREA_X, AREA_Y, 315, 58)) {
            donateTab = false; scrollProgress = 0; return true;
        }
        if (inside(vx, vy, AREA_X + 315, AREA_Y, 315, 58)) {
            donateTab = true; scrollProgress = 0; return true;
        }
        if (inside(vx, vy, AREA_X + 612, AREA_Y + 58, 24, 542)) {
            draggingScroll = true;
            updateScroll(vy);
            return true;
        }
        if (hoveringUrl != null && inside(vx, vy, AREA_X, SCROLL_TOP, SCROLL_WIDTH, SCROLL_HEIGHT)) {
            try {
                ConfirmLinkScreen.confirmLinkNow(this, URI.create(hoveringUrl));
            } catch (IllegalArgumentException ignored) { }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScroll) {
            updateScroll((float) ((mouseY - top()) / scale()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!donateTab) scrollProgress = Mth.clamp(scrollProgress - (float) scrollY * .2f, 0, 1);
        return true;
    }

    private void updateScroll(float virtualY) {
        scrollProgress = Mth.clamp((virtualY - (AREA_Y + 58) - 35) / 472f, 0, 1);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Align { LEFT, CENTER, RIGHT }
    private record TextItem(float x, float y, String text, Align align, boolean bold, float size,
                            String url, float maxWidth) {
        private TextItem(float x, float y, String text, Align align, boolean bold, float size, String url) {
            this(x, y, text, align, bold, size, url,
                    align == Align.CENTER ? 590 : size <= 24 ? 175 : Math.abs(x) <= 50 ? 270 : 560);
        }
    }
}
