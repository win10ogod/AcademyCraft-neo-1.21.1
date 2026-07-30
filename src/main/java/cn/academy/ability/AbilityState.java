package cn.academy.ability;

import cn.academy.config.ACConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-authoritative replacement for LambdaLib2's AbilityData, CPData and PresetData. */
public final class AbilityState {
    public static final String ROOT_KEY = "academy:ability";
    private static final float[] BASE_CP = {1800, 1800, 2800, 4000, 5800, 8000};
    private static final float[] BASE_OVERLOAD = {100, 100, 150, 240, 350, 500};
    private static final float[] MAX_BONUS_CP = {0, 900, 1000, 1500, 1700, 12000};
    private static final float[] MAX_BONUS_OVERLOAD = {0, 40, 70, 80, 100, 500};

    private String category = "";
    private int level;
    private float levelProgress;
    private float cp;
    private float overload;
    private float bonusCp;
    private float bonusOverload;
    private boolean active;
    private boolean overloadLocked;
    private boolean terminalInstalled;
    private boolean tutorialGiven;
    private int misakaId;
    private boolean interfered;
    private boolean attackPlayers = true;
    private boolean destroyBlocks = true;
    private boolean coinFlip = true;
    private boolean mouseWheelTeleport = true;
    private int cpRecoveryDelay;
    private int overloadRecoveryDelay;
    private int teleportCount;
    private final Set<String> learned = new LinkedHashSet<>();
    private final Map<String, Float> experience = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final String[][] presets = new String[4][4];
    private int currentPreset;
    private final Set<String> apps = new HashSet<>();
    private final Set<String> tutorials = new HashSet<>();
    private BlockPos teleportMark;
    private String teleportMarkDimension = "";
    private final List<TeleportLocation> teleportLocations = new ArrayList<>();

    public static AbilityState load(Player player) {
        return fromTag(player.getPersistentData().getCompound(ROOT_KEY));
    }

    public void save(Player player) {
        player.getPersistentData().put(ROOT_KEY, toTag());
    }

    public static AbilityState fromTag(CompoundTag tag) {
        AbilityState state = new AbilityState();
        state.category = tag.getString("Category");
        state.level = tag.getInt("Level");
        state.levelProgress = tag.getFloat("LevelProgress");
        state.cp = tag.getFloat("CP");
        state.overload = tag.getFloat("Overload");
        state.bonusCp = tag.getFloat("BonusCP");
        state.bonusOverload = tag.getFloat("BonusOverload");
        state.active = tag.getBoolean("Active");
        state.overloadLocked = tag.getBoolean("OverloadLocked");
        state.terminalInstalled = tag.getBoolean("TerminalInstalled");
        state.tutorialGiven = tag.getBoolean("TutorialGiven");
        state.misakaId = tag.getInt("MisakaId");
        state.interfered = tag.getBoolean("Interfered");
        state.attackPlayers = !tag.contains("AttackPlayers") || tag.getBoolean("AttackPlayers");
        state.destroyBlocks = !tag.contains("DestroyBlocks") || tag.getBoolean("DestroyBlocks");
        state.coinFlip = !tag.contains("CoinFlip") || tag.getBoolean("CoinFlip");
        state.mouseWheelTeleport = !tag.contains("MouseWheelTeleport") || tag.getBoolean("MouseWheelTeleport");
        state.cpRecoveryDelay = tag.getInt("CPRecoveryDelay");
        state.overloadRecoveryDelay = tag.getInt("OverloadRecoveryDelay");
        state.teleportCount = Math.max(0, tag.getInt("TeleportCount"));

        ListTag learnedTag = tag.getList("Learned", Tag.TAG_STRING);
        for (Tag value : learnedTag) state.learned.add(value.getAsString());

        CompoundTag expTag = tag.getCompound("Experience");
        for (String key : expTag.getAllKeys()) state.experience.put(key, expTag.getFloat(key));

        CompoundTag cooldownTag = tag.getCompound("Cooldowns");
        for (String key : cooldownTag.getAllKeys()) state.cooldowns.put(key, cooldownTag.getInt(key));

        state.currentPreset = Math.max(0, Math.min(3, tag.getInt("CurrentPreset")));
        if (tag.contains("PresetSets", Tag.TAG_COMPOUND)) {
            CompoundTag sets = tag.getCompound("PresetSets");
            for (int set = 0; set < state.presets.length; set++) {
                ListTag values = sets.getList(Integer.toString(set), Tag.TAG_STRING);
                for (int slot = 0; slot < Math.min(4, values.size()); slot++) state.presets[set][slot] = values.getString(slot);
            }
        } else {
            // Backward compatibility with early Neo alpha saves containing one four-slot preset.
            ListTag presetTag = tag.getList("Presets", Tag.TAG_STRING);
            for (int slot = 0; slot < Math.min(4, presetTag.size()); slot++) state.presets[0][slot] = presetTag.getString(slot);
        }

        ListTag appsTag = tag.getList("Apps", Tag.TAG_STRING);
        for (Tag value : appsTag) state.apps.add(value.getAsString());
        ListTag tutorialsTag = tag.getList("Tutorials", Tag.TAG_STRING);
        for (Tag value : tutorialsTag) state.tutorials.add(value.getAsString());

        if (tag.contains("TeleportMark", Tag.TAG_COMPOUND)) {
            CompoundTag mark = tag.getCompound("TeleportMark");
            state.teleportMark = new BlockPos(mark.getInt("X"), mark.getInt("Y"), mark.getInt("Z"));
            state.teleportMarkDimension = mark.getString("Dimension");
        }
        ListTag locations = tag.getList("TeleportLocations", Tag.TAG_COMPOUND);
        for (Tag entry : locations) {
            CompoundTag location = (CompoundTag) entry;
            state.teleportLocations.add(new TeleportLocation(location.getString("Name"), location.getString("Dimension"),
                    location.getDouble("X"), location.getDouble("Y"), location.getDouble("Z")));
        }
        state.normalize();
        return state;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Category", category);
        tag.putInt("Level", level);
        tag.putFloat("LevelProgress", levelProgress);
        tag.putFloat("CP", cp);
        tag.putFloat("Overload", overload);
        tag.putFloat("BonusCP", bonusCp);
        tag.putFloat("BonusOverload", bonusOverload);
        tag.putBoolean("Active", active);
        tag.putBoolean("OverloadLocked", overloadLocked);
        tag.putBoolean("TerminalInstalled", terminalInstalled);
        tag.putBoolean("TutorialGiven", tutorialGiven);
        tag.putInt("MisakaId", misakaId);
        tag.putBoolean("Interfered", interfered);
        tag.putBoolean("AttackPlayers", attackPlayers);
        tag.putBoolean("DestroyBlocks", destroyBlocks);
        tag.putBoolean("CoinFlip", coinFlip);
        tag.putBoolean("MouseWheelTeleport", mouseWheelTeleport);
        tag.putInt("CPRecoveryDelay", cpRecoveryDelay);
        tag.putInt("OverloadRecoveryDelay", overloadRecoveryDelay);
        tag.putInt("TeleportCount", teleportCount);

        ListTag learnedTag = new ListTag();
        learned.forEach(value -> learnedTag.add(StringTag.valueOf(value)));
        tag.put("Learned", learnedTag);

        CompoundTag expTag = new CompoundTag();
        experience.forEach(expTag::putFloat);
        tag.put("Experience", expTag);

        CompoundTag cooldownTag = new CompoundTag();
        cooldowns.forEach(cooldownTag::putInt);
        tag.put("Cooldowns", cooldownTag);

        tag.putInt("CurrentPreset", currentPreset);
        CompoundTag presetSets = new CompoundTag();
        for (int set = 0; set < presets.length; set++) {
            ListTag values = new ListTag();
            for (String value : presets[set]) values.add(StringTag.valueOf(value == null ? "" : value));
            presetSets.put(Integer.toString(set), values);
        }
        tag.put("PresetSets", presetSets);
        ListTag presetTag = new ListTag();
        for (String value : presets[currentPreset]) presetTag.add(StringTag.valueOf(value == null ? "" : value));
        tag.put("Presets", presetTag);

        ListTag appsTag = new ListTag();
        apps.forEach(value -> appsTag.add(StringTag.valueOf(value)));
        tag.put("Apps", appsTag);
        ListTag tutorialsTag = new ListTag();
        tutorials.forEach(value -> tutorialsTag.add(StringTag.valueOf(value)));
        tag.put("Tutorials", tutorialsTag);

        if (teleportMark != null) {
            CompoundTag mark = new CompoundTag();
            mark.putInt("X", teleportMark.getX());
            mark.putInt("Y", teleportMark.getY());
            mark.putInt("Z", teleportMark.getZ());
            mark.putString("Dimension", teleportMarkDimension);
            tag.put("TeleportMark", mark);
        }
        ListTag locations = new ListTag();
        for (TeleportLocation value : teleportLocations) {
            CompoundTag location = new CompoundTag();
            location.putString("Name", value.name());
            location.putString("Dimension", value.dimension());
            location.putDouble("X", value.x());
            location.putDouble("Y", value.y());
            location.putDouble("Z", value.z());
            locations.add(location);
        }
        tag.put("TeleportLocations", locations);
        return tag;
    }

    private void normalize() {
        if (AbilityRegistry.category(category) == null) {
            category = "";
            level = 0;
            active = false;
            learned.clear();
            experience.clear();
            clearPresets();
        } else {
            level = Math.max(1, Math.min(5, level));
        }
        tutorials.addAll(List.of("welcome", "misc", "ability_basis", "develop_ability", "wireless_network", "energy_bridge"));
        levelProgress = clamp(levelProgress, 0, 1);
        bonusCp = clamp(bonusCp, 0, hasCategory() ? MAX_BONUS_CP[level] : 0);
        bonusOverload = clamp(bonusOverload, 0, hasCategory() ? MAX_BONUS_OVERLOAD[level] : 0);
        cp = clamp(cp, 0, maxCp());
        overload = clamp(overload, 0, maxOverload());
        if (hasCategory() && overload >= maxOverload()) overloadLocked = true;
        currentPreset = Math.max(0, Math.min(3, currentPreset));
        for (String[] preset : presets) {
            for (int slot = 0; slot < preset.length; slot++) {
                AbilitySkill skill = AbilityRegistry.skill(preset[slot]);
                if (skill == null || !skill.category().equals(category) || !learned.contains(skill.id()) || !skill.controllable()) {
                    preset[slot] = "";
                }
            }
        }
    }

    public boolean hasCategory() { return !category.isEmpty(); }
    public String category() { return category; }
    public int level() { return level; }
    public float levelProgress() { return levelProgress; }
    public float cp() { return cp; }
    public float overload() { return overload; }
    public float maxCp() {
        if (!hasCategory()) return 0;
        float passive = learned.contains(category + ".brain_course") ? 1000 : 0;
        if (learned.contains(category + ".brain_course_advanced")) passive += 1500;
        return BASE_CP[level] + bonusCp + passive;
    }
    public float maxOverload() {
        if (!hasCategory()) return 0;
        float passive = learned.contains(category + ".brain_course_advanced") ? 100 : 0;
        return BASE_OVERLOAD[level] + bonusOverload + passive;
    }
    public boolean active() { return active; }
    public boolean overloadLocked() { return overloadLocked; }
    public boolean interfered() { return interfered; }
    public boolean terminalInstalled() { return terminalInstalled; }
    public boolean attackPlayers() { return attackPlayers; }
    public boolean destroyBlocks() { return destroyBlocks; }
    public boolean coinFlip() { return coinFlip; }
    public boolean mouseWheelTeleport() { return mouseWheelTeleport; }
    public Set<String> learned() { return Set.copyOf(learned); }
    public Set<String> apps() { return Set.copyOf(apps); }
    public Set<String> tutorials() { return Set.copyOf(tutorials); }
    public boolean unlockTutorial(String id) { return tutorials.add(id); }
    public boolean tutorialUnlocked(String id) { return tutorials.contains(id); }
    public String preset(int slot) { return slot >= 0 && slot < 4 ? presets[currentPreset][slot] : ""; }
    public List<String> presets() { return List.of(presets[currentPreset].clone()); }
    public int currentPreset() { return currentPreset; }
    public float experience(String skillId) {
        if (skillId.equals("meltdowner.rad_intensify") && learned.contains(skillId))
            return clamp(maxCp() / BASE_CP[5], 0, 1);
        if (skillId.equals("teleporter.shift_tp")
                && (!ACConfig.DESTROY_BLOCKS.get() || !destroyBlocks)) return 1;
        return experience.getOrDefault(skillId, 0f);
    }
    public int cooldown(String skillId) { return cooldowns.getOrDefault(skillId, 0); }
    public BlockPos teleportMark() { return teleportMark; }
    public String teleportMarkDimension() { return teleportMarkDimension; }
    public List<TeleportLocation> teleportLocations() { return List.copyOf(teleportLocations); }
    public int teleportCount() { return teleportCount; }
    public int recordTeleport() { return ++teleportCount; }

    public void setCategory(String id) {
        AbilityCategory newCategory = AbilityRegistry.category(id);
        if (newCategory == null) throw new IllegalArgumentException("Unknown category: " + id);
        category = id;
        level = 1;
        levelProgress = 0;
        active = false;
        overloadLocked = false;
        learned.clear();
        experience.clear();
        cooldowns.clear();
        clearPresets();
        bonusCp = bonusOverload = overload = 0;
        cp = maxCp();

        // Root skills become available here but still require a developer stimulation, as in 1.0.7.
    }

    public void clearCategory() {
        category = "";
        level = 0;
        levelProgress = cp = overload = bonusCp = bonusOverload = 0;
        active = false;
        overloadLocked = false;
        learned.clear();
        experience.clear();
        cooldowns.clear();
        clearPresets();
    }

    public void setLevel(int newLevel) {
        if (!hasCategory()) return;
        level = Math.max(1, Math.min(5, newLevel));
        levelProgress = 0;
        bonusCp = Math.min(bonusCp, MAX_BONUS_CP[level]);
        bonusOverload = Math.min(bonusOverload, MAX_BONUS_OVERLOAD[level]);
        cp = maxCp();
        overload = 0;
        overloadLocked = false;
        cpRecoveryDelay = overloadRecoveryDelay = 0;
    }

    public void setActive(boolean value) {
        active = value && hasCategory();
    }

    public void recoverAll() {
        if (!hasCategory()) return;
        cp = maxCp();
        overload = 0;
        overloadLocked = false;
        cpRecoveryDelay = 0;
        overloadRecoveryDelay = 0;
    }

    public void setInterfered(boolean value) {
        interfered = value;
        if (value) active = false;
    }

    public boolean canUse() {
        return hasCategory() && active && !interfered && !overloadLocked && overload < maxOverload();
    }

    public boolean canStartContext(AbilitySkill skill) {
        return skill != null && canUse() && learned.contains(skill.id()) && level >= skill.level()
                && cooldown(skill.id()) <= 0;
    }

    /** Per-tick/context consumption used by the restored 1.12.2 key context lifecycle. */
    public boolean consumeRaw(AbilitySkill skill, float cpCost, float overloadCost, boolean creative) {
        if (!canStartContext(skill)) return false;
        cpCost = Math.max(0, cpCost);
        overloadCost = Math.max(0, overloadCost);
        if (!creative && cp < cpCost) return false;
        if (!creative) {
            cp = Math.max(0, cp - cpCost);
            overload = Math.min(maxOverload(), overload + overloadCost);
            if (overload >= maxOverload()) overloadLocked = true;
            cpRecoveryDelay = ACConfig.CP_RECOVERY_DELAY.get();
            overloadRecoveryDelay = ACConfig.OVERLOAD_RECOVERY_DELAY.get();
            bonusCp = Math.min(MAX_BONUS_CP[level], bonusCp + cpCost * .0025f);
            bonusOverload = Math.min(MAX_BONUS_OVERLOAD[level], bonusOverload + overloadCost * .0058f);
        }
        return true;
    }

    /** Legacy performWithForce: execute an already-started action even when current CP is below its final cost. */
    public boolean consumeForce(AbilitySkill skill, float cpCost, float overloadCost, boolean creative) {
        if (!canStartContext(skill)) return false;
        cpCost = Math.max(0, cpCost);
        overloadCost = Math.max(0, overloadCost);
        if (!creative) {
            cp = Math.max(0, cp - cpCost);
            overload = Math.min(maxOverload(), overload + overloadCost);
            if (overload >= maxOverload()) overloadLocked = true;
            cpRecoveryDelay = ACConfig.CP_RECOVERY_DELAY.get();
            overloadRecoveryDelay = ACConfig.OVERLOAD_RECOVERY_DELAY.get();
            bonusCp = Math.min(MAX_BONUS_CP[level], bonusCp + cpCost * .0025f);
            bonusOverload = Math.min(MAX_BONUS_OVERLOAD[level], bonusOverload + overloadCost * .0058f);
        }
        return true;
    }

    public void finishContext(AbilitySkill skill, int cooldownTicks, float experienceGain) {
        if (skill == null) return;
        if (cooldownTicks > 0) cooldowns.put(skill.id(), cooldownTicks);
        if (experienceGain > 0) addExperience(skill, experienceGain);
    }

    public boolean consume(AbilitySkill skill, boolean creative) {
        if (!canUse() || !learned.contains(skill.id()) || level < skill.level() || cooldown(skill.id()) > 0) return false;
        float effectiveCp = skill.cpCost();
        float effectiveOverload = skill.overload();
        if (!creative && cp < effectiveCp) return false;
        if (!creative) {
            cp = Math.max(0, cp - effectiveCp);
            overload = Math.min(maxOverload(), overload + effectiveOverload);
            if (overload >= maxOverload()) overloadLocked = true;
            cpRecoveryDelay = ACConfig.CP_RECOVERY_DELAY.get();
            overloadRecoveryDelay = ACConfig.OVERLOAD_RECOVERY_DELAY.get();
            bonusCp = Math.min(MAX_BONUS_CP[level], bonusCp + effectiveCp * .0025f);
            bonusOverload = Math.min(MAX_BONUS_OVERLOAD[level], bonusOverload + effectiveOverload * .0058f);
        }
        cooldowns.put(skill.id(), cooldownFor(skill));
        float experienceGain = switch (skill.name()) {
            case "mine_detect" -> .008f;
            case "body_intensify", "thunder_clap" -> .01f;
            case "mag_movement", "railgun" -> .005f;
            case "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> .0005f;
            default -> .003f;
        };
        addExperience(skill, experienceGain);
        return true;
    }

    public boolean consumeLocationTeleport(AbilitySkill skill, double distance, boolean crossDimension, boolean creative) {
        if (!canUse() || !learned.contains(skill.id()) || cooldown(skill.id()) > 0) return false;
        float proficiency = experience(skill.id());
        float cpCost = (200 - proficiency * 50) * (crossDimension ? 2 : 1)
                * Math.max(8f, (float) Math.sqrt(Math.min(800, distance)));
        float addedOverload = 240;
        if (!creative && cp < cpCost) return false;
        if (!creative) {
            cp = Math.max(0, cp - cpCost);
            overload = Math.min(maxOverload(), overload + addedOverload);
            if (overload >= maxOverload()) overloadLocked = true;
            cpRecoveryDelay = ACConfig.CP_RECOVERY_DELAY.get();
            overloadRecoveryDelay = ACConfig.OVERLOAD_RECOVERY_DELAY.get();
        }
        cooldowns.put(skill.id(), Math.max(20, 30 - Math.round(proficiency * 10)));
        addExperience(skill, distance >= 200 ? .03f : .015f);
        return true;
    }

    public int cooldownFor(AbilitySkill skill) {
        float exp = experience(skill.id());
        int[] range = switch (skill.name()) {
            case "mag_manip" -> new int[]{60, 40};
            case "mine_detect" -> new int[]{900, 400};
            case "body_intensify" -> new int[]{900, 600};
            case "thunder_bolt" -> new int[]{120, 50};
            case "railgun" -> new int[]{300, 160};
            case "electron_bomb" -> new int[]{20, 10};
            case "light_shield" -> new int[]{800, 400};
            case "meltdowner" -> new int[]{300, 140};
            case "mine_ray_basic" -> new int[]{40, 20};
            case "mine_ray_expert", "mine_ray_luck" -> new int[]{60, 30};
            case "ray_barrage" -> new int[]{100, 40};
            case "jet_engine" -> new int[]{60, 30};
            case "electron_missile" -> new int[]{700, 400};
            case "threatening_teleport" -> new int[]{30, 15};
            case "penetrate_teleport" -> new int[]{50, 30};
            case "mark_teleport" -> new int[]{30, 0};
            case "flesh_ripping" -> new int[]{90, 40};
            case "shift_tp" -> new int[]{100, 60};
            case "dir_shock" -> new int[]{60, 20};
            case "ground_shock" -> new int[]{80, 40};
            case "vec_accel" -> new int[]{80, 50};
            case "dir_blast" -> new int[]{80, 50};
            case "storm_wing" -> new int[]{30, 10};
            case "blood_retro" -> new int[]{90, 40};
            case "plasma_cannon" -> new int[]{1000, 600};
            default -> null;
        };
        return range == null ? skill.cooldownTicks() : Math.max(0, Math.round(range[0] + (range[1] - range[0]) * exp));
    }

    public void tick() {
        cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));
        cooldowns.values().removeIf(value -> value <= 0);
        if (!hasCategory()) return;

        if (cpRecoveryDelay > 0) cpRecoveryDelay--;
        else {
            float ratio = maxCp() <= 0 ? 0 : cp / maxCp();
            float recovery = (float) (0.0003 * maxCp() * (1 + ratio) * ACConfig.CP_RECOVERY_SCALE.get());
            if (learned.contains(category + ".mind_course")) recovery *= 1.2f;
            cp = Math.min(maxCp(), cp + recovery);
        }

        if (overloadRecoveryDelay > 0) overloadRecoveryDelay--;
        else {
            float recovery = (float) (Math.max(.002 * maxOverload(), .007 * maxOverload() * (1 - overload / maxOverload() / 4))
                    * ACConfig.OVERLOAD_RECOVERY_SCALE.get());
            overload = Math.max(0, overload - recovery);
            if (overload <= 0) overloadLocked = false;
        }
    }

    public boolean canLearn(AbilitySkill skill) {
        if (skill == null || !hasCategory() || !skill.category().equals(category) || learned.contains(skill.id()) || skill.level() > level) return false;
        int requiredLevelSkill = switch (skill.name()) {
            case "brain_course" -> 3;
            case "brain_course_advanced" -> 4;
            case "mind_course" -> 5;
            default -> 0;
        };
        if (requiredLevelSkill > 0 && AbilityRegistry.category(category).skills().stream()
                .noneMatch(candidate -> candidate.controllable() && candidate.level() == requiredLevelSkill
                        && learned.contains(candidate.id()))) return false;
        for (AbilitySkill.Requirement requirement : skill.requirements()) {
            String id = category + "." + requirement.skillId();
            if (!learned.contains(id) || experience(id) + 1.0e-5f < requirement.experience()) return false;
        }
        return true;
    }

    public boolean learn(AbilitySkill skill) {
        if (skill == null || !skill.category().equals(category)) return false;
        if (!learned.add(skill.id())) return false;
        experience.putIfAbsent(skill.id(), 0f);
        if (skill.controllable()) fillFirstEmptyPreset(skill.id());
        return true;
    }

    public boolean unlearn(AbilitySkill skill) {
        if (skill == null || !learned.remove(skill.id())) return false;
        experience.remove(skill.id());
        cooldowns.remove(skill.id());
        for (String[] preset : presets) {
            for (int slot = 0; slot < preset.length; slot++) {
                if (preset[slot].equals(skill.id())) preset[slot] = "";
            }
        }
        return true;
    }

    public boolean setExperience(AbilitySkill skill, float value) {
        if (skill == null || !learned.contains(skill.id())) return false;
        experience.put(skill.id(), clamp(value, 0, 1));
        return true;
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    public void maxOutLevelProgress() {
        if (hasCategory() && level < 5) levelProgress = 1;
    }

    public AbilitySkill learnNext(int maximumLevel) {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return null;
        for (AbilitySkill skill : abilityCategory.skills()) {
            if (skill.level() <= maximumLevel && canLearn(skill)) {
                learn(skill);
                return skill;
            }
        }
        return null;
    }

    public void learnAll() {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return;
        abilityCategory.skills().forEach(this::learn);
    }

    public void addExperience(AbilitySkill skill, float amount) {
        float scaled = (float) (amount * ACConfig.PROGRESSION_SCALE.get());
        experience.compute(skill.id(), (key, old) -> Math.min(1, (old == null ? 0 : old) + scaled));
        if (level < 5) {
            long levelSkills = AbilityRegistry.category(category).skills().stream()
                    .filter(s -> s.controllable() && s.level() == level).count();
            float denominator = Math.max(1, levelSkills) * (level == 4 ? 1.333f : .666f);
            levelProgress = Math.min(1, levelProgress + scaled / denominator);
        }
    }

    private void autoLearnPassives() {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return;
        boolean changed;
        do {
            changed = false;
            for (AbilitySkill skill : abilityCategory.skills()) {
                if (!skill.controllable() && canLearn(skill)) changed |= learn(skill);
            }
        } while (changed);
    }

    public boolean selectPreset(int slot, String skillId) {
        AbilitySkill skill = AbilityRegistry.skill(skillId);
        if (slot < 0 || slot >= 4 || skill == null || !skill.controllable()
                || !skill.category().equals(category) || !learned.contains(skillId)) return false;
        String[] preset = presets[currentPreset];
        for (int i = 0; i < preset.length; i++) {
            if (preset[i].equals(skillId)) preset[i] = "";
        }
        preset[slot] = skillId;
        return true;
    }

    public boolean switchPreset(int id) {
        if (id < 0 || id >= presets.length) return false;
        currentPreset = id;
        return true;
    }

    private void fillFirstEmptyPreset(String skillId) {
        String[] preset = presets[currentPreset];
        for (int i = 0; i < preset.length; i++) {
            if (preset[i].isEmpty()) {
                preset[i] = skillId;
                return;
            }
        }
    }

    private void clearPresets() {
        for (String[] preset : presets) Arrays.fill(preset, "");
        currentPreset = 0;
    }

    public void installTerminal() { terminalInstalled = true; }
    public boolean tutorialGiven() { return tutorialGiven; }
    public int misakaId() { return misakaId; }
    public void markTutorialGiven(int id) {
        tutorialGiven = true;
        misakaId = Math.max(1000, Math.min(18999, id));
    }

    public boolean setting(String name) {
        return switch (name) {
            case "attack_players" -> attackPlayers;
            case "destroy_blocks" -> destroyBlocks;
            case "coin_flip" -> coinFlip;
            case "mouse_wheel_teleport" -> mouseWheelTeleport;
            default -> false;
        };
    }

    public boolean setSetting(String name, boolean value) {
        switch (name) {
            case "attack_players" -> attackPlayers = value;
            case "destroy_blocks" -> destroyBlocks = value;
            case "coin_flip" -> coinFlip = value;
            case "mouse_wheel_teleport" -> mouseWheelTeleport = value;
            default -> { return false; }
        }
        return true;
    }
    public boolean installApp(String id) { return apps.add(id); }

    public void setTeleportMark(Level level, BlockPos pos) {
        teleportMark = pos.immutable();
        teleportMarkDimension = level.dimension().location().toString();
    }

    public void clearTeleportMark() {
        teleportMark = null;
        teleportMarkDimension = "";
    }

    public boolean addTeleportLocation(String name, Level level, double x, double y, double z) {
        if (teleportLocations.size() >= 32) return false;
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) cleanName = "Location " + (teleportLocations.size() + 1);
        if (cleanName.length() > 32) cleanName = cleanName.substring(0, 32);
        teleportLocations.add(new TeleportLocation(cleanName, level.dimension().location().toString(), x, y, z));
        return true;
    }

    public boolean removeTeleportLocation(int index) {
        if (index < 0 || index >= teleportLocations.size()) return false;
        teleportLocations.remove(index);
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(category, level, levelProgress, cp, maxCp(), overload, maxOverload(), active,
                interfered, Set.copyOf(learned), Map.copyOf(experience), List.of(presets[currentPreset].clone()), terminalInstalled, Set.copyOf(apps),
                attackPlayers, destroyBlocks, coinFlip, mouseWheelTeleport);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TeleportLocation(String name, String dimension, double x, double y, double z) {}

    public record Snapshot(String category, int level, float levelProgress, float cp, float maxCp,
                           float overload, float maxOverload, boolean active, boolean interfered,
                           Set<String> learned, Map<String, Float> experience, List<String> presets,
                           boolean terminalInstalled, Set<String> apps, boolean attackPlayers, boolean destroyBlocks,
                           boolean coinFlip, boolean mouseWheelTeleport) {}
}
