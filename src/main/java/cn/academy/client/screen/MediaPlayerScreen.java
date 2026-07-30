package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.client.sound.ACMediaPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Native recreation of the 1.12.2 media_player.xml screen. */
public final class MediaPlayerScreen extends Screen {
    private static final int VIRTUAL_WIDTH = 650;
    private static final int VIRTUAL_HEIGHT = 504;
    private static final ResourceLocation BACKGROUND = texture("guis/apps/media_player/back.png");
    private static final ResourceLocation PLAY = texture("guis/apps/media_player/play.png");
    private static final ResourceLocation PAUSE = texture("guis/apps/media_player/pause.png");
    private static final ResourceLocation STOP = texture("guis/apps/media_player/stop.png");
    private static final ResourceLocation VOLUME = texture("guis/icons/volume_overlay.png");

    private int scroll;
    private boolean draggingVolume;

    public MediaPlayerScreen() {
        super(Component.translatable("ac.app.media_player.name"));
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/" + path);
    }

    private float scale() {
        return Math.min(.32f, Math.min((width - 8f) / VIRTUAL_WIDTH, (height - 8f) / VIRTUAL_HEIGHT));
    }

    private float left() {
        return (width - VIRTUAL_WIDTH * scale()) * .5f;
    }

    private float top() {
        return (height - VIRTUAL_HEIGHT * scale()) * .5f;
    }

    private List<ACMediaPlayer.Track> installed() {
        List<ACMediaPlayer.Track> result = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) return result;
        AbilityState state = AbilityState.load(minecraft.player);
        for (ACMediaPlayer.Track track : ACMediaPlayer.TRACKS) {
            if (state.apps().contains("media:" + track.id())) result.add(track);
        }
        return result;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        float scale = scale();
        float vx = (mouseX - left()) / scale;
        float vy = (mouseY - top()) / scale;
        List<ACMediaPlayer.Track> tracks = installed();
        scroll = Mth.clamp(scroll, 0, Math.max(0, tracks.size() - 5));

        gui.pose().pushPose();
        gui.pose().translate(left(), top(), 0);
        gui.pose().scale(scale, scale, 1);
        gui.blit(BACKGROUND, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT,
                0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        ACMediaPlayer.Track playing = ACMediaPlayer.current();
        String title = playing == null ? "" : Component.translatable("ac.media." + playing.id() + ".name").getString();
        drawVirtualText(gui, title, 324, 78, 276, 36, 0xFFF3FBFD, Align.RIGHT);
        drawVirtualText(gui, ACMediaPlayer.formatTime(ACMediaPlayer.elapsedSeconds()), 404, 127,
                200, 30, 0xFFF3FBFD, Align.RIGHT);

        int progress = Math.round(554 * ACMediaPlayer.progress());
        gui.fill(52, 120, 606, 126, 0x88405259);
        if (progress > 0) gui.fill(52, 120, 52 + progress, 126, 0xFFE7F7FA);

        boolean popHovered = inside(vx, vy, 52, 72, 50, 42);
        boolean stopHovered = inside(vx, vy, 115, 72, 50, 42);
        gui.setColor(1, 1, 1, popHovered ? 1f : .78f);
        ResourceLocation pop = playing != null && !ACMediaPlayer.paused() ? PAUSE : PLAY;
        gui.blit(pop, 52, 72, 50, 42, 0, 0, 50, 42, 50, 42);
        gui.setColor(1, 1, 1, stopHovered ? 1f : .78f);
        gui.blit(STOP, 115, 72, 50, 42, 0, 0, 50, 42, 50, 42);
        gui.setColor(1, 1, 1, 1);

        gui.blit(VOLUME, 180, 77, 128, 32, 0, 0, 128, 32, 128, 32);
        int volumeX = 186 + Math.round(112 * ACMediaPlayer.volume());
        gui.fill(volumeX, 78, volumeX + 9, 108,
                inside(vx, vy, 180, 73, 132, 40) ? 0xE6F6FCFF : 0xA6E5F2F7);

        int shown = Math.min(5, tracks.size() - scroll);
        for (int row = 0; row < shown; row++) {
            ACMediaPlayer.Track track = tracks.get(scroll + row);
            int y = 169 + row * 60;
            boolean hovered = inside(vx, vy, 51, y, 552, 60);
            boolean selected = playing != null && playing.id().equals(track.id());
            if (hovered || selected) gui.fill(51, y, 603, y + 60,
                    selected ? 0x8055C9E7 : 0x473EAECE);
            ResourceLocation cover = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                    "media/cover/" + track.id() + ".png");
            gui.blit(cover, 55, y + 5, 50, 50, 0, 0, 64, 64, 64, 64);
            drawVirtualText(gui, Component.translatable("ac.media." + track.id() + ".name").getString(),
                    116, y + 1, 300, 35, selected ? 0xFFFFFFFF : 0xFFE9F5F8, Align.LEFT);
            drawVirtualText(gui, Component.translatable("ac.media." + track.id() + ".desc").getString(),
                    117, y + 29, 300, 27, 0xFFB9CCD1, Align.LEFT);
            drawVirtualText(gui, track.displayLength(), 527, y + 15, 70, 28, 0xFFE9F5F8, Align.RIGHT);
        }
        if (tracks.isEmpty()) {
            drawVirtualText(gui, Component.translatable("ac.media.empty").getString(),
                    51, 225, 552, 32, 0xFF9CB2B8, Align.CENTER);
        }

        int maxScroll = Math.max(0, tracks.size() - 5);
        int scrollY = 169 + (maxScroll == 0 ? 0 : Math.round(246f * scroll / maxScroll));
        gui.fill(604, scrollY, 609, scrollY + 55, 0x82F0FBFF);
        gui.pose().popPose();

        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawVirtualText(GuiGraphics gui, String text, float x, float y, float maxWidth,
                                 float fontSize, int color, Align align) {
        if (text == null || text.isEmpty()) return;
        float textScale = fontSize / Math.max(1f, font.lineHeight);
        float renderedWidth = font.width(text) * textScale;
        if (renderedWidth > maxWidth) textScale *= maxWidth / renderedWidth;
        float width = font.width(text) * textScale;
        float drawX = align == Align.RIGHT ? x + maxWidth - width
                : align == Align.CENTER ? x + (maxWidth - width) * .5f : x;
        gui.pose().pushPose();
        gui.pose().translate(drawX, y, 4);
        gui.pose().scale(textScale, textScale, 1);
        gui.drawString(font, text, 0, 0, color, false);
        gui.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = (float) ((mouseX - left()) / scale());
        float vy = (float) ((mouseY - top()) / scale());
        List<ACMediaPlayer.Track> tracks = installed();
        if (inside(vx, vy, 52, 72, 50, 42)) {
            ACMediaPlayer.togglePause(tracks.isEmpty() ? null : tracks.getFirst());
            return true;
        }
        if (inside(vx, vy, 115, 72, 50, 42)) {
            ACMediaPlayer.stop();
            return true;
        }
        if (inside(vx, vy, 180, 73, 132, 40)) {
            draggingVolume = true;
            updateVolume(vx);
            return true;
        }
        if (inside(vx, vy, 51, 169, 552, 302)) {
            int row = (int) ((vy - 169) / 60);
            int selected = scroll + row;
            if (row >= 0 && row < 5 && selected < tracks.size()) {
                ACMediaPlayer.play(tracks.get(selected));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingVolume) {
            updateVolume((float) ((mouseX - left()) / scale()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingVolume) {
            draggingVolume = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float vx = (float) ((mouseX - left()) / scale());
        float vy = (float) ((mouseY - top()) / scale());
        if (inside(vx, vy, 45, 160, 570, 320)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, installed().size() - 5));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateVolume(float virtualMouseX) {
        ACMediaPlayer.setVolume((virtualMouseX - 186) / 112f);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Align { LEFT, CENTER, RIGHT }
}
