package cn.academy.client.render;

import cn.academy.AcademyCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original-style terminal/app installation notification banner. */
public final class ACNotifications {
    private static final ResourceLocation BACK = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
            "textures/guis/notification/back.png");
    private static String app = "";
    private static int remaining;

    public static void showAppInstalled(String appId) {
        app = appId;
        remaining = 100;
    }

    public static void tick() {
        if (remaining > 0) remaining--;
    }

    public static void render(GuiGraphics gui, Minecraft minecraft, float partialTick) {
        if (remaining <= 0 || app.isBlank()) return;
        float enter = Mth.clamp((100 - remaining + partialTick) / 10f, 0, 1);
        float leave = Mth.clamp((remaining + partialTick) / 12f, 0, 1);
        float alpha = Math.min(enter, leave);
        int width = 207, height = 68;
        int x = (gui.guiWidth() - width) / 2;
        int y = Math.round(-height + (height + 8) * ease(alpha));
        gui.setColor(.75f, .94f, 1f, alpha);
        gui.blit(BACK, x, y, width, height, 0, 0, 517, 170, 517, 170);
        gui.setColor(1, 1, 1, 1);
        Component title = Component.translatable("ac.notification.app_installed");
        Component name = Component.translatable("ac.app." + app + ".name");
        gui.drawCenteredString(minecraft.font, title, gui.guiWidth() / 2, y + 18, 0xFFD8F7FF);
        gui.drawCenteredString(minecraft.font, name, gui.guiWidth() / 2, y + 35, 0xFF6EE4FF);
    }

    private static float ease(float value) {
        return 1 - (1 - value) * (1 - value) * (1 - value);
    }

    private ACNotifications() {}
}
