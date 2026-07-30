package cn.academy.advancement;

import cn.academy.AcademyCraft;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ACAdvancements {
    public static void grant(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, path));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void grantLegacyLevels(ServerPlayer player, String category, int level) {
        if (category == null || category.isEmpty()) return;
        for (int current = 1; current <= Math.min(5, level); current++) {
            grant(player, "legacy/" + category + "/lv" + current);
        }
    }

    public static void grantLegacySkillLearn(ServerPlayer player, String category, String skill) {
        String achievement = switch (category + "." + skill) {
            case "meltdowner.rad_intensify" -> "rad_intensify";
            case "meltdowner.light_shield" -> "light_shield";
            case "meltdowner.meltdowner" -> "meltdowner";
            case "meltdowner.mine_ray_basic" -> "mine_ray";
            case "meltdowner.electron_missile" -> "electron_missile";
            default -> "";
        };
        if (!achievement.isEmpty()) grant(player, "legacy/" + category + "/" + achievement);
    }

    public static void grantLegacySkillUse(ServerPlayer player, String category, String skill) {
        String achievement = switch (category + "." + skill) {
            case "electromaster.mag_movement" -> "mag_movement";
            case "electromaster.body_intensify" -> "body_intensify";
            case "electromaster.mine_detect" -> "mine_detect";
            case "electromaster.thunder_bolt" -> "thunder_bolt";
            case "electromaster.railgun" -> "railgun";
            case "electromaster.thunder_clap" -> "thunder_clap";
            case "meltdowner.jet_engine" -> "jet_engine";
            case "teleporter.threatening_teleport" -> "threatening_teleport";
            case "teleporter.penetrate_teleport" -> "ignore_barrier";
            case "teleporter.flashing" -> "flashing";
            case "vecmanip.ground_shock" -> "ground_shock";
            case "vecmanip.dir_blast" -> "dir_blast";
            case "vecmanip.storm_wing" -> "storm_wing";
            case "vecmanip.blood_retro" -> "blood_retro";
            case "vecmanip.vec_reflection" -> "vec_reflection";
            default -> "";
        };
        if (!achievement.isEmpty()) grant(player, "legacy/" + category + "/" + achievement);
    }

    private ACAdvancements() {}
}
