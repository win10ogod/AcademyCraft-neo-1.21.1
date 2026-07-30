package cn.academy.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AbilityCategory(String id, int color, List<AbilitySkill> skills) {
    public AbilityCategory {
        skills = List.copyOf(skills);
    }

    public Component displayName() {
        return Component.translatable("ac.ability." + id + ".name");
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/abilities/" + id + "/icon.png");
    }

    public AbilitySkill skill(String name) {
        return skills.stream().filter(skill -> skill.name().equals(name)).findFirst().orElse(null);
    }
}
