package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.registry.ACSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Modern recreation of the original 1.12.2 MisakaCloud holographic data terminal. */
public final class TerminalScreen extends Screen {
    private static final int VIRTUAL_WIDTH = 640;
    private static final int VIRTUAL_HEIGHT = 785;
    private static final ResourceLocation BACK = texture("data_terminal/back.png");
    private static final ResourceLocation LOGO = texture("data_terminal/logo.png");
    private static final ResourceLocation APP_BACK = texture("data_terminal/app_back.png");
    private static final ResourceLocation APP_HIGHLIGHT = texture("data_terminal/app_back_highlight.png");
    private static final ResourceLocation CURSOR = texture("data_terminal/cursor.png");
    private static final ResourceLocation ARROW_UP = texture("data_terminal/arrow_up.png");
    private static final ResourceLocation ARROW_DOWN = texture("data_terminal/arrow_down.png");

    private record AppEntry(Component name, ResourceLocation icon, Supplier<Screen> screen) {}

    private final List<AppEntry> apps = new ArrayList<>();
    private int scrollRow;
    private int hovered = -1;
    private int previousHovered = -1;
    private int keyboardSelection = -1;
    private int openTicks;
    private int stateHash;
    private float scale = 1;
    private float originX;
    private float originY;

    public TerminalScreen() {
        super(Component.translatable("ac.gui.terminal.title"));
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private static ResourceLocation appIcon(String app) {
        return texture("apps/" + app + "/icon.png");
    }

    @Override
    protected void init() {
        refreshApps();
        updateTransform();
    }

    private void refreshApps() {
        apps.clear();
        if (minecraft == null || minecraft.player == null) return;
        AbilityState state = AbilityState.load(minecraft.player);
        stateHash = java.util.Objects.hash(state.terminalInstalled(), state.apps());

        apps.add(new AppEntry(Component.translatable("ac.app.settings.name"), appIcon("settings"), SettingsScreen::new));
        if (state.apps().contains("skill_tree"))
            apps.add(new AppEntry(Component.translatable("ac.app.skill_tree.name"), appIcon("skill_tree"), SkillTreeScreen::new));
        apps.add(new AppEntry(Component.translatable("ac.app.tutorial.name"), appIcon("tutorial"), TutorialScreen::new));
        if (state.apps().contains("media_player"))
            apps.add(new AppEntry(Component.translatable("ac.app.media_player.name"), appIcon("media_player"), MediaPlayerScreen::new));
        if (state.apps().contains("freq_transmitter"))
            apps.add(new AppEntry(Component.translatable("ac.app.freq_transmitter.name"), appIcon("freq_transmitter"), FrequencyTransmitterScreen::new));
        // AppAbout is preinstalled in the 1.12.2 terminal and its resources are bundled with the port.
        apps.add(new AppEntry(Component.translatable("ac.app.about.name"), appIcon("about"), AboutScreen::new));
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (apps.size() + 2) / 3 - 3);
    }

    private void updateTransform() {
        scale = Math.min((width - 18f) / VIRTUAL_WIDTH, (height - 18f) / VIRTUAL_HEIGHT);
        scale = Math.min(scale, .78f);
        originX = (width - VIRTUAL_WIDTH * scale) * .5f;
        originY = (height - VIRTUAL_HEIGHT * scale) * .5f;
    }

    private double virtualX(double mouseX) { return (mouseX - originX) / scale; }
    private double virtualY(double mouseY) { return (mouseY - originY) / scale; }

    private int appAt(double mouseX, double mouseY) {
        double mx = virtualX(mouseX);
        double my = virtualY(mouseY);
        for (int visible = 0; visible < 9; visible++) {
            int index = scrollRow * 3 + visible;
            if (index >= apps.size()) break;
            int x = 65 + (visible % 3) * 180;
            int y = 155 + (visible / 3) * 180;
            if (mx >= x && mx < x + 151 && my >= y && my < y + 151) return index;
        }
        return -1;
    }

    @Override
    public void tick() {
        openTicks++;
        if (minecraft != null && minecraft.player != null) {
            AbilityState state = AbilityState.load(minecraft.player);
            if (java.util.Objects.hash(state.terminalInstalled(), state.apps()) != stateHash) refreshApps();
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        updateTransform();
        hovered = keyboardSelection >= 0 && keyboardSelection < apps.size()
                ? keyboardSelection : appAt(mouseX, mouseY);
        if (hovered != previousHovered && hovered >= 0 && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ACSounds.TERMINAL_SELECT.get(), 1f, .22f));
        }
        previousHovered = hovered;

        gui.fill(0, 0, width, height, 0x3800060C);
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);

        float intro = Mth.clamp((openTicks + partialTick) / 12f, 0, 1);
        gui.setColor(.65f, .92f, 1f, .88f * intro);
        gui.blit(BACK, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, 0, 0, 268, 327, 268, 327);
        gui.setColor(1, 1, 1, 1);

        gui.blit(LOGO, 40, 50, 0, 0, 50, 50, 50, 50);
        drawVirtualText(gui, Component.literal("DATA"), 98, 37, 40, 0xAABDEFFF, 0, 100);
        drawVirtualText(gui, Component.literal("TERMINAL"), 98, 69, 40, 0xAABDEFFF, 0, 160);

        if (minecraft != null && minecraft.player != null) {
            drawVirtualText(gui, minecraft.player.getName(), 600, 42, 40,
                    0xCCE9FAFF, 2, 200);
            long dayTime = minecraft.level == null ? 0 : minecraft.level.getDayTime() % 24000;
            int hour = (int) (dayTime / 1000);
            int minute = (int) ((dayTime % 1000) * 60 / 1000);
            Component count = Component.translatable("ac.gui.terminal.appcount", apps.size());
            String status = count.getString() + String.format(", %02d:%02d", hour, minute);
            drawVirtualText(gui, Component.literal(status), 600, 84, 30,
                    0x99E9FAFF, 2, 300);
        }

        long animFrame = (minecraft == null ? 0 : minecraft.level == null ? 0 : minecraft.level.getGameTime() / 8) % 3;
        for (int visible = 0; visible < 9; visible++) {
            int index = scrollRow * 3 + visible;
            if (index >= apps.size()) break;
            AppEntry app = apps.get(index);
            int x = 65 + visible % 3 * 180;
            int y = 155 + visible / 3 * 180;
            float alpha = Mth.clamp((openTicks + partialTick - (visible + 1) * 2f) / 10f, 0, 1);
            gui.setColor(1, 1, 1, alpha);
            gui.blit(index == hovered ? APP_HIGHLIGHT : APP_BACK, x, y, 0, 0, 151, 151, 151, 151);
            ResourceLocation icon = app.icon();
            if (icon.equals(appIcon("tutorial"))) icon = texture("apps/tutorial/icon_" + animFrame + ".png");
            gui.setColor(.8f, .95f, 1f, (index == hovered ? .95f : .62f) * alpha);
            String iconPath = icon.getPath();
            int iconWidth = iconPath.contains("media_player") ? 84 : iconPath.endsWith("icon_2.png") ? 100
                    : iconPath.contains("freq_transmitter") || iconPath.contains("/about/") ? 128 : 110;
            int iconHeight = iconPath.contains("media_player") ? 85 : iconWidth;
            gui.blit(icon, x + 9, y + 32, 110, 110, 0, 0,
                    iconWidth, iconHeight, iconWidth, iconHeight);
            gui.setColor(1, 1, 1, 1);
            int color = index == hovered ? 0xFFE9FAFF : 0x997EB5C8;
            drawVirtualText(gui, app.name(), x + 75, y + 143, 32, color, 1, 151);
        }

        if (scrollRow > 0) gui.blit(ARROW_UP, 280, 133, 80, 20, 0, 0, 64, 17, 64, 17);
        if (scrollRow < maxScroll()) gui.blit(ARROW_DOWN, 280, 725, 80, 20, 0, 0, 64, 17, 64, 17);

        double vmx = Mth.clamp(virtualX(mouseX), 0, VIRTUAL_WIDTH);
        double vmy = Mth.clamp(virtualY(mouseY), 0, VIRTUAL_HEIGHT);
        int cursorSize = hovered >= 0 ? 28 : 22;
        float pulse = (float) (1 + .08 * Math.sin((openTicks + partialTick) * .25));
        cursorSize = Math.round(cursorSize * pulse);
        gui.setColor(.75f, .95f, 1f, .55f);
        gui.blit(CURSOR, (int) vmx - cursorSize / 2, (int) vmy - cursorSize / 2,
                cursorSize, cursorSize, 0, 0, 32, 32, 32, 32);
        gui.setColor(1, 1, 1, 1);

        pose.popPose();
        if (hovered >= 0 && hovered < apps.size()) {
            Component hoverName = apps.get(hovered).name();
            int tooltipWidth = font.width(hoverName) + 12;
            gui.fill(width / 2 - tooltipWidth / 2, height - 20, width / 2 + tooltipWidth / 2,
                    height - 6, 0xD006121A);
            gui.drawCenteredString(font, hoverName, width / 2, height - 17, 0xFFE4FAFF);
        }
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    /** Uses the same antialiased TrueType sizing contract as LambdaLib's AC_Normal font. */
    private void drawVirtualText(GuiGraphics gui, Component text, float x, float y, float virtualSize,
                                 int color, int alignment, float maxVirtualWidth) {
        ACLegacyFont.draw(gui, text, x, y, virtualSize, color, alignment, maxVirtualWidth, false);
    }

    private void openApp(int selected) {
        if (selected < 0 || selected >= apps.size() || minecraft == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ACSounds.TERMINAL_CONFIRM.get(), 1f, .45f));
        minecraft.setScreen(apps.get(selected).screen().get());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboardSelection = -1;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (apps.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            openApp(keyboardSelection >= 0 ? keyboardSelection : Math.max(0, hovered));
            return true;
        }
        int direction = switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, org.lwjgl.glfw.GLFW.GLFW_KEY_A -> -1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_KEY_D -> 1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP, org.lwjgl.glfw.GLFW.GLFW_KEY_W -> -3;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, org.lwjgl.glfw.GLFW.GLFW_KEY_S -> 3;
            default -> 0;
        };
        if (direction != 0) {
            int first = scrollRow * 3;
            if (keyboardSelection < first || keyboardSelection >= first + 9)
                keyboardSelection = Math.min(apps.size() - 1, first);
            else keyboardSelection = Mth.clamp(keyboardSelection + direction, 0, apps.size() - 1);
            if (keyboardSelection < scrollRow * 3) scrollRow = Math.max(0, scrollRow - 1);
            if (keyboardSelection >= scrollRow * 3 + 9) scrollRow = Math.min(maxScroll(), scrollRow + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int selected = appAt(mouseX, mouseY);
            if (selected >= 0 && selected < apps.size() && minecraft != null) {
                openApp(selected);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
