package cn.academy.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Recreates the original four-second MisakaCloud terminal installation overlay. */
public final class TerminalInstallingScreen extends Screen {
    private static final int INSTALL_TICKS = 80;
    private static final int WAIT_TICKS = 14;
    private int elapsed;

    public TerminalInstallingScreen() {
        super(Component.translatable("ac.gui.terminal.installing"));
    }

    @Override
    public void tick() {
        if (++elapsed >= INSTALL_TICKS + WAIT_TICKS && minecraft != null) {
            minecraft.setScreen(new TerminalScreen());
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        float progress = Mth.clamp((elapsed + partialTick) / INSTALL_TICKS, 0, 1);
        float alpha = Mth.clamp((elapsed + partialTick) / 4f, 0, 1);
        if (elapsed > INSTALL_TICKS) alpha *= Mth.clamp(1 - (elapsed - INSTALL_TICKS) / (float) WAIT_TICKS, 0, 1);
        int a = Math.round(alpha * 255) << 24;
        int x = width / 2 - 75;
        int y = height / 2;
        gui.fill(x, y, x + 150, y + 9, (Math.round(alpha * 120) << 24) | 0x003C3C3C);
        gui.fill(x + 1, y + 1, x + 149, y + 8, a | 0x00E5F8FF);
        gui.fill(x + 2, y + 2, x + 148, y + 7, (Math.round(alpha * 220) << 24) | 0x001E1E1E);
        gui.fill(x + 2, y + 2, x + 2 + Math.round(146 * progress), y + 7, a | 0x0066DDF5);
        gui.drawString(font, title, x, y - 12, a | 0x00FFFFFF, false);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
