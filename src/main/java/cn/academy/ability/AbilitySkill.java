package cn.academy.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable description of one skill from the original four skill trees. */
public record AbilitySkill(
        String category,
        String name,
        int level,
        float cpCost,
        float overload,
        int cooldownTicks,
        boolean controllable,
        List<Requirement> requirements
) {
    public AbilitySkill {
        requirements = List.copyOf(requirements);
    }

    public String id() {
        return category + "." + name;
    }

    private String localizationCategory() {
        return name.equals("brain_course") || name.equals("brain_course_advanced") || name.equals("mind_course")
                ? "generic" : category;
    }

    public Component displayName() {
        return Component.translatable("ac.ability." + localizationCategory() + "." + name + ".name");
    }

    public Component description() {
        return Component.translatable("ac.ability." + localizationCategory() + "." + name + ".desc");
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/abilities/" + localizationCategory() + "/skills/" + name + ".png");
    }

    public record Requirement(String skillId, float experience) {}
}
