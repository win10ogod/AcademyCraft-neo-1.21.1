package cn.academy.client.sound;

import cn.academy.registry.ACSounds;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.Util;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Client-side replacement for the 1.12.2 MediaBackend.  It keeps playback alive
 * while screens change and controls only its own OpenAL channel when pausing.
 */
public final class ACMediaPlayer {
    public record Track(String id, float lengthSeconds) {
        public String displayLength() {
            return formatTime(lengthSeconds);
        }
    }

    public static final Track[] TRACKS = {
            new Track("level5_judgelight", 265.261474f),
            new Track("only_my_railgun", 257.006780f),
            new Track("sisters_noise", 262.544739f)
    };

    private static MediaSound sound;
    private static Track current;
    private static Track lastPlayed;
    private static boolean paused;
    private static long startedAt;
    private static float elapsedBeforeStart;
    private static float volume = 1f;
    private static long stateChangedAt;

    private static Field soundEngineField;
    private static Field channelsField;
    private static boolean reflectionReady;

    public static Track track(String id) {
        for (Track track : TRACKS) if (track.id.equals(id)) return track;
        return null;
    }

    public static void play(Track track) {
        if (track == null) return;
        stop();
        Minecraft minecraft = Minecraft.getInstance();
        sound = new MediaSound(track.id, volume);
        current = track;
        lastPlayed = track;
        paused = false;
        elapsedBeforeStart = 0;
        startedAt = Util.getMillis();
        stateChangedAt = startedAt;
        minecraft.getMusicManager().stopPlaying();
        minecraft.getSoundManager().play(sound);
    }

    public static void playLastOrFirst(Track first) {
        Track target = lastPlayed != null ? lastPlayed : first;
        if (target != null) play(target);
    }

    public static void togglePause(Track first) {
        if (current == null) {
            playLastOrFirst(first);
        } else if (paused) {
            resume();
        } else {
            pause();
        }
    }

    public static void pause() {
        if (current == null || paused) return;
        elapsedBeforeStart = elapsedSeconds();
        paused = true;
        stateChangedAt = Util.getMillis();
        withChannel(Channel::pause);
    }

    public static void resume() {
        if (current == null || !paused) return;
        paused = false;
        startedAt = Util.getMillis();
        stateChangedAt = startedAt;
        withChannel(Channel::unpause);
    }

    public static void stop() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null;
        current = null;
        paused = false;
        elapsedBeforeStart = 0;
        stateChangedAt = Util.getMillis();
    }

    public static void setVolume(float value) {
        volume = Mth.clamp(value, 0f, 1f);
        if (sound != null) sound.setMediaVolume(volume);
        withChannel(channel -> channel.setVolume(volume));
    }

    public static float volume() {
        return volume;
    }

    public static Track current() {
        return current;
    }

    public static boolean paused() {
        return paused;
    }

    public static float elapsedSeconds() {
        if (current == null) return 0;
        float elapsed = paused ? elapsedBeforeStart
                : elapsedBeforeStart + (Util.getMillis() - startedAt) / 1000f;
        return Mth.clamp(elapsed, 0, current.lengthSeconds);
    }

    public static float progress() {
        return current == null || current.lengthSeconds <= 0 ? 0 : elapsedSeconds() / current.lengthSeconds;
    }

    public static void tick() {
        if (current == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getMusicManager().stopPlaying();
        if (elapsedSeconds() >= current.lengthSeconds - .05f) {
            stop();
            return;
        }
        // Sound startup is asynchronous; only treat an inactive source as ended after a grace period.
        if (!paused && sound != null && Util.getMillis() - stateChangedAt > 1000
                && !minecraft.getSoundManager().isActive(sound)) stop();
    }

    public static void renderHud(GuiGraphics gui, Minecraft minecraft) {
        if (current == null) return;
        int width = 145;
        int left = gui.guiWidth() - width - 6;
        // The legacy aux GUI was bottom-right; keep that anchor but reserve the modern
        // hotbar/first-person overlay safe zone so its title is not cut by hand rendering.
        int top = Math.max(6, gui.guiHeight() - 76);
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 500);
        gui.fill(left, top, left + width, top + 32, 0xB007151D);
        gui.fill(left + 10, top + 26, left + 130, top + 28, 0xAA31424A);
        gui.fill(left + 10, top + 26, left + 10 + Math.round(120 * progress()), top + 28, 0xFFE5F7FA);
        String title = net.minecraft.client.resources.language.I18n.get("ac.media." + current.id + ".name");
        if (minecraft.font.width(title) > 101) title = minecraft.font.plainSubstrByWidth(title, 98) + "…";
        gui.drawString(minecraft.font, title, left + 9, top + 14, 0xFFEAF9FC, false);
        String time = formatTime(elapsedSeconds());
        gui.drawString(minecraft.font, time, left + 112, top + 22, 0xFFCADCE1, false);
        if (paused) gui.drawString(minecraft.font, "Ⅱ", left + 130, top + 13, 0xFFFFD37A, false);
        gui.pose().popPose();
    }

    public static String formatTime(float seconds) {
        int value = Math.max(0, (int) seconds);
        return String.format("%02d:%02d", value / 60, value % 60);
    }

    @SuppressWarnings("unchecked")
    private static void withChannel(java.util.function.Consumer<Channel> action) {
        if (sound == null) return;
        try {
            if (!reflectionReady) {
                try {
                    soundEngineField = SoundManager.class.getDeclaredField("soundEngine");
                } catch (NoSuchFieldException mappedRuntime) {
                    for (Field field : SoundManager.class.getDeclaredFields()) {
                        if (SoundEngine.class.isAssignableFrom(field.getType())) { soundEngineField = field; break; }
                    }
                }
                if (soundEngineField == null) return;
                soundEngineField.setAccessible(true);
                try {
                    channelsField = SoundEngine.class.getDeclaredField("instanceToChannel");
                    channelsField.setAccessible(true);
                } catch (NoSuchFieldException ignored) { channelsField = null; }
                reflectionReady = true;
            }
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            SoundEngine engine = (SoundEngine) soundEngineField.get(manager);
            ChannelAccess.ChannelHandle handle = null;
            if (channelsField != null) {
                Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
                        (Map<SoundInstance, ChannelAccess.ChannelHandle>) channelsField.get(engine);
                handle = channels.get(sound);
            } else {
                // Literal field names are not remapped in a production JAR. Locate the
                // instance map by type/key so pause and volume still affect only this track.
                for (Field field : SoundEngine.class.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object candidate = ((Map<?, ?>) field.get(engine)).get(sound);
                    if (candidate instanceof ChannelAccess.ChannelHandle found) { handle = found; break; }
                }
            }
            if (handle != null) handle.execute(action);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Playback remains functional if another mapping set hides the channel;
            // the UI state is still retained, and stop/play continue to use public APIs.
        }
    }

    private static final class MediaSound extends AbstractSoundInstance {
        private MediaSound(String id, float volume) {
            super(ACSounds.get("media." + id), SoundSource.MUSIC, RandomSource.create());
            this.volume = volume;
            this.pitch = 1f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            this.relative = true;
        }

        private void setMediaVolume(float volume) {
            this.volume = volume;
        }
    }

    private ACMediaPlayer() {}
}
