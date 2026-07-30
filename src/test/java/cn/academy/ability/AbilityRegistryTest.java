package cn.academy.ability;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbilityRegistryTest {
    @Test
    void containsAllOriginalCategoriesAndSkills() {
        assertEquals(Set.of("electromaster", "meltdowner", "teleporter", "vecmanip"),
                new HashSet<>(AbilityRegistry.categoryIds()));
        assertEquals(12, AbilityRegistry.category("electromaster").skills().size());
        assertEquals(14, AbilityRegistry.category("meltdowner").skills().size());
        assertEquals(12, AbilityRegistry.category("teleporter").skills().size());
        assertEquals(12, AbilityRegistry.category("vecmanip").skills().size());

        Set<String> ids = new HashSet<>();
        AbilityRegistry.categories().forEach(category -> category.skills().forEach(skill -> {
            assertTrue(ids.add(skill.id()), "duplicate skill id " + skill.id());
            assertSame(skill, AbilityRegistry.skill(skill.id()));
            assertTrue(skill.level() >= 1 && skill.level() <= 5);
        }));
    }

    @Test
    void abilityStateRoundTripsThroughNbt() {
        AbilityState original = new AbilityState();
        original.setCategory("electromaster");
        original.setLevel(4);
        original.learnAll();
        assertTrue(original.selectPreset(0, "electromaster.railgun"));
        original.setActive(true);

        AbilityState restored = AbilityState.fromTag(original.toTag());
        assertEquals("electromaster", restored.category());
        assertEquals(4, restored.level());
        assertTrue(restored.active());
        assertTrue(restored.learned().contains("electromaster.railgun"));
        assertEquals("electromaster.railgun", restored.preset(0));
        assertEquals(original.maxCp(), restored.maxCp());
    }

    @Test
    void allFourPresetBanksPersistIndependently() {
        AbilityState state = new AbilityState();
        state.setCategory("electromaster");
        state.setLevel(5);
        state.learnAll();
        assertTrue(state.selectPreset(0, "electromaster.arc_gen"));
        assertTrue(state.switchPreset(1));
        assertTrue(state.selectPreset(0, "electromaster.railgun"));

        AbilityState restored = AbilityState.fromTag(state.toTag());
        assertEquals(1, restored.currentPreset());
        assertEquals("electromaster.railgun", restored.preset(0));
        assertTrue(restored.switchPreset(0));
        assertEquals("electromaster.arc_gen", restored.preset(0));
    }

    @Test
    void experienceRequiresDeveloperActionForLevelUp() {
        AbilityState state = new AbilityState();
        state.setCategory("electromaster");
        AbilitySkill root = AbilityRegistry.skill("electromaster.arc_gen");
        assertNotNull(root);
        assertTrue(state.learn(root));
        state.addExperience(root, 100);
        assertEquals(1, state.level());
        assertEquals(1, state.levelProgress());
        state.setLevel(2);
        assertEquals(2, state.level());
        assertEquals(0, state.levelProgress());
    }

    @Test
    void misakaCloudResearchStatePersists() {
        AbilityState state = new AbilityState();
        state.markTutorialGiven(12345);
        assertTrue(state.unlockTutorial("terminal"));
        AbilityState restored = AbilityState.fromTag(state.toTag());
        assertTrue(restored.tutorialGiven());
        assertEquals(12345, restored.misakaId());
        assertTrue(restored.tutorialUnlocked("welcome"));
        assertTrue(restored.tutorialUnlocked("terminal"));
    }

    @Test
    void malformedCategoryIsSafelyReset() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Category", "not_a_real_category");
        tag.putInt("Level", 99);
        tag.putBoolean("Active", true);
        AbilityState state = AbilityState.fromTag(tag);
        assertFalse(state.hasCategory());
        assertFalse(state.active());
        assertEquals(0, state.level());
    }

}
