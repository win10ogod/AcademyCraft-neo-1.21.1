package cn.academy.client.sound;

import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lifecycle manager replacing LambdaLib's FollowEntitySound for ability contexts. */
public final class ACContextSounds {
    private record Key(UUID entity, String sound) { }
    private static final Map<Key, FollowingLoop> PLAYING = new HashMap<>();

    public static void handle(UUID entity, String sound, boolean start, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        Key key = new Key(entity, sound);
        FollowingLoop previous = PLAYING.remove(key);
        if (previous != null) previous.halt();
        if (!start || minecraft.level == null) return;
        Player player = minecraft.level.getPlayerByUUID(entity);
        if (player == null) return;
        FollowingLoop loop = new FollowingLoop(player, sound, volume, key);
        PLAYING.put(key, loop);
        minecraft.getSoundManager().play(loop);
    }

    public static void stopAll() {
        new java.util.ArrayList<>(PLAYING.values()).forEach(FollowingLoop::halt);
        PLAYING.clear();
    }

    private static final class FollowingLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private final Key key;

        private FollowingLoop(Player player, String sound, float volume, Key key) {
            super(ACSounds.get(sound), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.key = key;
            this.volume = volume;
            this.pitch = 1;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = player.getX(); this.y = player.getY(); this.z = player.getZ();
        }

        @Override public void tick() {
            if (player.isRemoved() || Minecraft.getInstance().level == null) {
                halt();
                return;
            }
            x = player.getX(); y = player.getY(); z = player.getZ();
        }

        private void halt() {
            stop();
            PLAYING.remove(key, this);
        }
    }

    private ACContextSounds() { }
}
