package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.network.AbilityActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native recreation of settings.xml at its original 0.2 scale. */
public final class SettingsScreen extends Screen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "textures/guis/settings.png");
    private static final ResourceLocation CHECK_TRUE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "textures/guis/check_true.png");
    private static final ResourceLocation CHECK_FALSE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "textures/guis/check_false.png");
    private static final String[] SETTINGS = {
            "attack_players", "destroy_blocks", "coin_flip", "mouse_wheel_teleport"
    };
    private static final String[] LABELS = {
            "attackPlayer", "destroyBlocks", "headsOrTails", "useMouseWheel"
    };

    public SettingsScreen() {
        super(Component.translatable("ac.app.settings.name"));
    }

    private int[] panelBounds() {
        float scale = Math.min(.2f, Math.min((width - 20f) / 742f, (height - 14f) / 923f));
        int panelWidth = Math.round(742 * scale);
        int panelHeight = Math.round(923 * scale);
        return new int[]{(width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight};
    }

    private void toggle(String setting, boolean value) {
        if (minecraft == null || minecraft.player == null) return;
        AbilityState state = AbilityState.load(minecraft.player);
        state.setSetting(setting, value);
        state.save(minecraft.player);
        PacketDistributor.sendToServer(AbilityActionPayload.setting(setting, value));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        int[] panel = panelBounds();
        int left = panel[0], top = panel[1], panelWidth = panel[2], panelHeight = panel[3];
        float xmlScale = panelWidth / 742f;
        gui.setColor(.72f, .91f, 1f, .96f);
        gui.blit(BACKGROUND, left, top, panelWidth, panelHeight, 0, 0, 742, 923, 742, 923);
        gui.setColor(1, 1, 1, 1);

        int contentLeft = left + Math.round(58.5f * xmlScale);
        int contentRight = left + Math.round(669.5f * xmlScale);
        int headingY = top + Math.round(120 * xmlScale);
        drawXmlText(gui, Component.translatable("ac.settings.cat.generic").getString(),
                contentLeft + Math.round(15 * xmlScale), headingY, Math.round(300 * xmlScale),
                40 * xmlScale, 0xFF70E5FF);
        int lineY = headingY + Math.max(8, Math.round(43 * xmlScale));
        gui.fill(contentLeft, lineY, contentRight, lineY + Math.max(1, Math.round(4 * xmlScale)), 0xAA5CD7F2);

        AbilityState state = minecraft != null && minecraft.player != null ? AbilityState.load(minecraft.player) : null;
        int rowStep = Math.max(10, Math.round(60 * xmlScale));
        int firstY = headingY + rowStep;
        for (int index = 0; index < SETTINGS.length; index++) {
            int y = firstY + index * rowStep;
            boolean enabled = state != null && state.setting(SETTINGS[index]);
            boolean hovered = mouseX >= contentLeft && mouseX <= contentRight && mouseY >= y && mouseY <= y + rowStep;
            if (hovered) gui.fill(contentLeft, y, contentRight, y + rowStep, 0x5037B8DF);
            drawXmlText(gui, Component.translatable("ac.settings.prop." + LABELS[index]).getString(),
                    contentLeft + Math.round(15 * xmlScale), y, Math.round(300 * xmlScale),
                    40 * xmlScale, enabled ? 0xFFE8FAFF : 0xFF9EAFB5);
            int box = Math.max(7, Math.round(35 * xmlScale));
            int boxX = left + Math.round(608.5f * xmlScale);
            gui.blit(enabled ? CHECK_TRUE : CHECK_FALSE, boxX, y,
                    box, box, 0, 0, 35, 35, 35, 35);
        }
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawXmlText(GuiGraphics gui, String text, int x, int y, int maxWidth, float size, int color) {
        float scale = size / Math.max(1f, font.lineHeight);
        float rendered = font.width(text) * scale;
        if (rendered > maxWidth) scale *= maxWidth / rendered;
        gui.pose().pushPose();
        gui.pose().translate(x, y, 3);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, text, 0, 0, color, false);
        gui.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || minecraft == null || minecraft.player == null) return false;
        int[] panel = panelBounds();
        float xmlScale = panel[2] / 742f;
        int contentLeft = panel[0] + Math.round(58.5f * xmlScale);
        int contentRight = panel[0] + Math.round(669.5f * xmlScale);
        int headingY = panel[1] + Math.round(120 * xmlScale);
        int rowStep = Math.max(10, Math.round(60 * xmlScale));
        int firstY = headingY + rowStep;
        if (mouseX >= contentLeft && mouseX <= contentRight) {
            for (int index = 0; index < SETTINGS.length; index++) {
                int y = firstY + index * rowStep;
                if (mouseY >= y && mouseY <= y + rowStep) {
                    String setting = SETTINGS[index];
                    toggle(setting, !AbilityState.load(minecraft.player).setting(setting));
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }
}
