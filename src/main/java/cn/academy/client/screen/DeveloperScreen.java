package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityCategory;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.ACSkillProgressTextures;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.network.DeveloperActionPayload;
import cn.academy.network.DevelopmentSyncPayload;
import cn.academy.registry.ACItems;
import com.mojang.math.Axis;
import net.minecraft.util.StringUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pixel-for-pixel native recreation of the 1.12.2 page_developer.xml and DeveloperUI lifecycle. */
public final class DeveloperScreen extends Screen {
    private static final int MAIN_W = 400, MAIN_H = 187;
    private static final int AREA_X = 128, AREA_Y = 18, AREA_W = 257, AREA_H = 139;

    private static final ResourceLocation AREA_BACK = gui("effect/effect_developer_background.png");
    private static final ResourceLocation PARENT_LEFT = gui("parent/parent_background_developerleft.png");
    private static final ResourceLocation PARENT_RIGHT = gui("parent/parent_background_developerright.png");
    private static final ResourceLocation PARENT_MACHINE = gui("parent/parent_background_developermachine.png");
    private static final ResourceLocation UI_LEFT = gui("ui/ui_developerleft.png");
    private static final ResourceLocation UI_RIGHT = gui("ui/ui_developerright.png");
    private static final ResourceLocation SKILL_BACK = gui("developer/skill_back.png");
    private static final ResourceLocation SKILL_OUTLINE = gui("developer/skill_outline.png");
    private static final ResourceLocation LINE = gui("developer/line.png");
    private static final ResourceLocation SMALL_BUTTON = gui("developer/button.png");
    private static final ResourceLocation LEARN_BUTTON = gui("button/button_learn.png");
    private static final ResourceLocation ELEMENT_BACK = gui("element/element_background300x32.png");
    private static final ResourceLocation NODE_ICON = gui("icons/icon_node.png");
    private static final ResourceLocation NO_CATEGORY = gui("icons/icon_nocategory.png");

    private final BlockPos sourcePos;
    private final boolean portable;
    private final Map<String, Float> hoverBlend = new HashMap<>();
    private int stateHash;
    private int screenTicks;
    private AbilitySkill hovered;
    private AbilitySkill selected;
    private Overlay overlay = Overlay.NONE;
    private int overlayTicks;
    private LegacyConsole console;

    private enum Overlay { NONE, SKILL, LEVEL }

    public DeveloperScreen(BlockPos sourcePos, boolean portable) {
        super(Component.translatable("ac.gui.developer.title"));
        this.sourcePos = sourcePos;
        this.portable = portable;
    }

    private static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private float uiScale() {
        return Math.min(1, Math.min((width - 8f) / MAIN_W, (height - 8f) / MAIN_H));
    }
    private float left() { return (width - MAIN_W * uiScale()) * .5f; }
    private float top() { return (height - MAIN_H * uiScale()) * .5f; }
    private float vx(double mouseX) { return (float) ((mouseX - left()) / uiScale()); }
    private float vy(double mouseY) { return (float) ((mouseY - top()) / uiScale()); }

    @Override
    protected void init() {
        clearWidgets();
        screenTicks = 0;
        hoverBlend.clear();
        if (minecraft != null && minecraft.player != null) {
            AbilityState state = AbilityState.load(minecraft.player);
            stateHash = relevantStateHash(state);
            ensureConsole(state, true);
        }
    }

    private int maximumSkillLevel() {
        if (portable) return 2;
        ACMachineBlockEntity machine = machine();
        if (machine != null && machine.kind() == MachineKind.DEVELOPER_ADVANCED) return 5;
        return 3;
    }

    private boolean advanced() { return !portable && maximumSkillLevel() == 5; }

    private ACMachineBlockEntity machine() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(sourcePos) instanceof ACMachineBlockEntity machine ? machine : null;
    }

    private static int relevantStateHash(AbilityState state) {
        return java.util.Objects.hash(state.category(), state.level(), state.levelProgress(), state.learned());
    }

    private net.minecraft.nbt.CompoundTag development() {
        return minecraft == null || minecraft.player == null ? new net.minecraft.nbt.CompoundTag()
                : minecraft.player.getPersistentData().getCompound("academy:development");
    }

    private void setDevelopmentIdle() {
        if (minecraft == null || minecraft.player == null) return;
        net.minecraft.nbt.CompoundTag value = development();
        value.putInt("State", DevelopmentSyncPayload.IDLE);
        minecraft.player.getPersistentData().put("academy:development", value);
    }

    @Override
    public void tick() {
        super.tick();
        screenTicks++;
        if (overlay != Overlay.NONE) overlayTicks++;
        if (minecraft == null || minecraft.player == null) return;
        AbilityState state = AbilityState.load(minecraft.player);
        net.minecraft.nbt.CompoundTag dev = development();

        int hash = relevantStateHash(state);
        if (hash != stateHash) stateHash = hash;

        ensureConsole(state, false);
        if (console != null) {
            console.tick(dev);
            if (console.finishedTicks > 10) {
                setDevelopmentIdle();
                console = null;
                ensureConsole(state, true);
            }
        }

        for (AbilitySkill skill : AbilityRegistry.category(state.category()) == null ? List.<AbilitySkill>of()
                : AbilityRegistry.category(state.category()).skills()) {
            float value = hoverBlend.getOrDefault(skill.id(), 0f);
            float target = skill == hovered ? 1 : 0;
            hoverBlend.put(skill.id(), Mth.clamp(value + Math.signum(target - value) * .25f, 0, 1));
        }
    }

    private void ensureConsole(AbilityState state, boolean force) {
        int devState = development().getInt("State");
        if (console != null && devState != DevelopmentSyncPayload.IDLE) return;
        boolean emergency = state.hasCategory() && resetMode();
        boolean needed = !state.hasCategory() || emergency;
        if (!needed) {
            console = null;
            return;
        }
        if (force || console == null || console.emergency != emergency) {
            String playerName = minecraft == null || minecraft.player == null ? "User" : minecraft.player.getName().getString();
            console = new LegacyConsole(emergency, playerName,
                    minecraft == null || minecraft.player == null ? 0 : minecraft.player.getUUID().hashCode());
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        if (minecraft == null || minecraft.player == null) return;
        AbilityState state = AbilityState.load(minecraft.player);
        AbilityCategory category = AbilityRegistry.category(state.category());
        float mx = vx(mouseX), my = vy(mouseY), scale = uiScale();

        gui.pose().pushPose();
        gui.pose().translate(left(), top(), 0);
        gui.pose().scale(scale, scale, 1);
        drawFrame(gui, state, category, mx, my);

        int devState = development().getInt("State");
        if (console != null) {
            drawConsole(gui);
        } else if (overlay != Overlay.NONE) {
            drawCover(gui, state, category, mx, my, devState);
        } else {
            drawTree(gui, state, category, mx, my);
        }
        gui.pose().popPose();
    }

    private void drawFrame(GuiGraphics gui, AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        ACGuiTextures.blit(gui, PARENT_LEFT, 4, 0, 109, 187, 0, 0, 217, 374, 217, 374);
        ACGuiTextures.blit(gui, UI_LEFT, 4, 0, 109, 187, 0, 0, 217, 374, 217, 374);
        ACGuiTextures.blit(gui, PARENT_RIGHT, 118, 0, 278, 187, 0, 0, 556, 374, 556, 374);
        ACGuiTextures.blit(gui, UI_RIGHT, 118, 0, 278, 187, 0, 0, 556, 374, 556, 374);

        float px = Mth.clamp(mouseX / MAIN_W, 0, 1) - .5f;
        float py = Mth.clamp(mouseY / MAIN_H, 0, 1) - .5f;
        int sourceX = Mth.clamp(Math.round((px + .5f) * 5), 0, 5);
        int sourceY = Mth.clamp(Math.round((py + .5f) * 3), 0, 3);
        ACGuiTextures.blit(gui, AREA_BACK, AREA_X, AREA_Y, AREA_W, AREA_H,
                sourceX, sourceY, 507, 276, 512, 279);
        ACGuiTextures.blit(gui, PARENT_MACHINE, 4, 0, 109, 187,
                0, 0, 217, 374, 217, 374);

        ResourceLocation icon = category == null ? NO_CATEGORY : ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/guis/icons/icon_" + category.id() + ".png");
        ACGuiTextures.blit(gui, icon, 6, 68, 32, 32, 0, 0, 64, 64, 64, 64);
        Component categoryName = category == null ? Component.literal("N/A") : category.displayName();
        drawFit(gui, categoryName, 37, 69, 70, 13, 0xFFFFFFFF, false);
        gui.fill(37, 81, 107, 83, 0x4D666666);
        gui.fill(37, 81, 37 + Math.round(70 * state.levelProgress()), 83, 0xFFFFFFFF);
        drawText(gui, Component.literal("EXP " + (int) (state.levelProgress() * 100) + "%"),
                36, 84, 8, 0xFFFFFFFF, false);

        boolean canUpgrade = state.hasCategory() && state.level() < 5 && state.level() < maximumSkillLevel()
                && state.levelProgress() >= 1;
        if (canUpgrade) {
            ACGuiTextures.blit(gui, LEARN_BUTTON, 66, 82, 48, 16,
                    0, 0, 200, 64, 200, 64, 1, 1, 1, .7f);
        } else {
            Component levelText = state.hasCategory() ? Component.translatable("ac.ability.level" + state.level())
                    : Component.literal("Level 0");
            drawFit(gui, levelText, 66, 84, 41, 9, 0xFF1177D6, true);
        }

        if (!portable) drawWirelessPanel(gui);
        int[] energy = developerEnergy();
        drawText(gui, Component.literal("Power:"), 8, 131, 12, 0xFFFFFFFF, false);
        gui.fill(10, 145, 107, 153, 0x66333A3D);
        int amount = energy[1] <= 0 ? 0 : Math.round(97f * energy[0] / energy[1]);
        gui.fill(10, 145, 10 + amount, 153, 0xFFFCC532);
        drawText(gui, Component.literal("Sync Rate:"), 8, 154, 12, 0xFFFFFFFF, false);
        gui.fill(10, 167, 107, 175, 0x66333A3D);
        int sync = portable ? 29 : advanced() ? 97 : 68;
        gui.fill(10, 167, 10 + sync, 175, 0xFF32A4FC);
    }

    private void drawWirelessPanel(GuiGraphics gui) {
        drawText(gui, Component.literal("Current Node:"), 8, 105, 12, 0xFFFFFFFF, false);
        ACGuiTextures.blit(gui, ELEMENT_BACK, 8, 114, 100, 16,
                0, 0, 300, 32, 300, 32, 1, 1, 1, .7f);
        ACGuiTextures.blit(gui, NODE_ICON, 15, 116, 12, 12, 0, 0, 32, 32, 32, 32);
        drawFit(gui, Component.literal(linkedNodeName()), 34, 116, 70, 12, 0xFFFFFFFF, false);
    }

    private String linkedNodeName() {
        ACMachineBlockEntity machine = machine();
        if (machine == null || machine.linkedNodePos() == null || minecraft == null || minecraft.level == null) return "N/A";
        return minecraft.level.getBlockEntity(machine.linkedNodePos()) instanceof ACMachineBlockEntity node
                ? node.nodeName() : machine.networkId().isBlank() ? "N/A" : machine.networkId();
    }

    private int[] developerEnergy() {
        if (minecraft == null || minecraft.player == null) return new int[]{0, 0};
        net.minecraft.nbt.CompoundTag dev = development();
        if (dev.getInt("MaxEnergy") > 0 && dev.getBoolean("Portable") == portable
                && dev.getInt("State") != DevelopmentSyncPayload.IDLE)
            return new int[]{dev.getInt("Energy"), dev.getInt("MaxEnergy")};
        ACMachineBlockEntity machine = machine();
        if (!portable && machine != null)
            return new int[]{machine.energy.getEnergyStored(), machine.energy.getMaxEnergyStored()};
        ItemStack main = minecraft.player.getMainHandItem(), off = minecraft.player.getOffhandItem();
        ItemStack stack = main.is(ACItems.DEVELOPER_PORTABLE.get()) ? main : off;
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? new int[]{0, 10_000} : new int[]{storage.getEnergyStored(), storage.getMaxEnergyStored()};
    }

    private boolean resetMode() {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getMainHandItem().is(ACItems.MAGNETIC_COIL.get());
    }

    private List<AbilitySkill> visibleSkills(AbilityState state, AbilityCategory category) {
        if (category == null) return List.of();
        List<AbilitySkill> result = new ArrayList<>();
        for (AbilitySkill skill : category.skills()) {
            boolean parentLearned = skill.requirements().isEmpty() || skill.requirements().stream()
                    .anyMatch(requirement -> state.learned().contains(category.id() + "." + requirement.skillId()));
            if (state.level() >= skill.level() || state.learned().contains(skill.id()) || parentLearned) result.add(skill);
        }
        return result;
    }

    private int nodeX(AbilitySkill skill, float mouseX) {
        SkillTreeScreen.Point point = SkillTreeScreen.POSITIONS.get(skill.name());
        float dx = (Mth.clamp(mouseX / MAIN_W, 0, 1) - .5f) * 10;
        return point == null ? AREA_X + AREA_W / 2 : Math.round(AREA_X + point.x() + 8 - dx);
    }

    private int nodeY(AbilitySkill skill, float mouseY) {
        SkillTreeScreen.Point point = SkillTreeScreen.POSITIONS.get(skill.name());
        float dy = (Mth.clamp(mouseY / MAIN_H, 0, 1) - .5f) * 10;
        return point == null ? AREA_Y + AREA_H / 2 : Math.round(AREA_Y + point.y() + 8 - dy);
    }

    private void drawTree(GuiGraphics gui, AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        if (category == null) return;
        List<AbilitySkill> skills = visibleSkills(state, category);
        hovered = skillAt(state, category, mouseX, mouseY);

        for (int index = 0; index < skills.size(); index++) {
            AbilitySkill skill = skills.get(index);
            int x2 = nodeX(skill, mouseX), y2 = nodeY(skill, mouseY);
            for (AbilitySkill.Requirement requirement : skill.requirements()) {
                AbilitySkill parent = category.skill(requirement.skillId());
                if (parent == null || !skills.contains(parent)) continue;
                boolean learned = state.learned().contains(skill.id());
                float alpha = appearance(index, 5) * (learned ? 1 : .4f) * potentialAlpha(state, category, skill);
                drawTexturedLine(gui, nodeX(parent, mouseX), nodeY(parent, mouseY), x2, y2, 5.5f, alpha);
            }
        }

        for (int index = 0; index < skills.size(); index++) {
            AbilitySkill skill = skills.get(index);
            int x = nodeX(skill, mouseX), y = nodeY(skill, mouseY);
            boolean learned = state.learned().contains(skill.id());
            float alpha = appearance(index, 2) * potentialAlpha(state, category, skill);
            float scale = 1 + .2f * hoverBlend.getOrDefault(skill.id(), skill == hovered ? 1f : 0f);
            int back = Math.round(23 * scale), outline = Math.round(31 * scale), icon = Math.round(14 * scale);

            ACGuiTextures.blit(gui, SKILL_BACK, x - back / 2f, y - back / 2f, back, back,
                    0, 0, 128, 128, 128, 128, 1, 1, 1, alpha);
            ACGuiTextures.blit(gui, SKILL_OUTLINE, x - outline / 2f, y - outline / 2f, outline, outline,
                    0, 0, 128, 128, 128, 128, .2f, .2f, .2f, alpha * .6f);
            float shade = learned ? 1 : .38f;
            ACGuiTextures.blit(gui, skill.icon(), x - icon / 2f, y - icon / 2f, icon, icon,
                    0, 0, 32, 32, 32, 32, shade, shade, shade, alpha);
            if (learned) ACGuiTextures.blit(gui,
                    ACSkillProgressTextures.get("skill_outline", state.experience(skill.id())),
                    x - outline / 2f, y - outline / 2f, outline, outline,
                    0, 0, 128, 128, 128, 128, 1, 1, 1, alpha);
        }
    }

    private float potentialAlpha(AbilityState state, AbilityCategory category, AbilitySkill skill) {
        if (state.learned().contains(skill.id())) return 1;
        boolean parent = skill.requirements().isEmpty() || skill.requirements().stream()
                .anyMatch(req -> state.learned().contains(category.id() + "." + req.skillId()));
        return parent ? .7f : .25f;
    }

    private float appearance(int index, int duration) {
        float start = 2 + index * 1.6f;
        return Mth.clamp((screenTicks - start) / Math.max(1, duration), 0, 1);
    }

    private void drawTexturedLine(GuiGraphics gui, int x1, int y1, int x2, int y2, float width, float alpha) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1 || alpha <= 0) return;
        double trim = Math.min(12.2, length / 3);
        double nx = dx / length, ny = dy / length;
        double sx = x1 + nx * trim, sy = y1 + ny * trim;
        int drawLength = Math.max(1, (int) Math.round(length - trim * 2));
        gui.pose().pushPose();
        gui.pose().translate(sx, sy, 4);
        gui.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        ACGuiTextures.blit(gui, LINE, 0, -width / 2, drawLength, width,
                0, 0, 16, 16, 16, 16, 1, 1, 1, alpha);
        gui.pose().popPose();
    }

    private AbilitySkill skillAt(AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        for (AbilitySkill skill : visibleSkills(state, category)) {
            float dx = mouseX - nodeX(skill, mouseX), dy = mouseY - nodeY(skill, mouseY);
            if (dx * dx + dy * dy <= 14 * 14) return skill;
        }
        return null;
    }

    private void drawConsole(GuiGraphics gui) {
        if (console == null) return;
        List<String> visual = new ArrayList<>();
        for (String line : console.renderLines()) visual.addAll(wrapLegacy(line, AREA_W - 10, 8, false));
        int from = Math.max(0, visual.size() - 10);
        int y = AREA_Y + 5;
        for (int i = from; i < visual.size(); i++, y += 10)
            drawText(gui, Component.literal(visual.get(i)), AREA_X + 5, y, 8, 0xFFFFFFFF, false);
    }

    private void drawCover(GuiGraphics gui, AbilityState state, AbilityCategory category,
                           float mouseX, float mouseY, int devState) {
        float fade = Mth.clamp(overlayTicks / 4f, 0, 1);
        gui.fill(0, 0, MAIN_W, MAIN_H, Math.round(0xB3 * fade) << 24);
        int cx = MAIN_W / 2, cy = MAIN_H / 2;
        float progress = devState == DevelopmentSyncPayload.DEVELOPING || devState == DevelopmentSyncPayload.DONE
                || devState == DevelopmentSyncPayload.FAILED ? development().getFloat("Progress") : 0;

        if (overlay == Overlay.SKILL && selected != null) {
            boolean learned = state.learned().contains(selected.id());
            float iconProgress = devState == DevelopmentSyncPayload.DONE ? 1
                    : devState == DevelopmentSyncPayload.DEVELOPING || devState == DevelopmentSyncPayload.FAILED
                    ? progress : learned ? state.experience(selected.id()) : 0;
            drawActionIcon(gui, selected.icon(), 32, cx, cy, iconProgress,
                    devState == DevelopmentSyncPayload.DONE || learned && devState == DevelopmentSyncPayload.IDLE);
            Component name = selected.displayName().copy().append(" (LV " + selected.level() + ")");
            drawCenteredFit(gui, name, cx, cy + 28, 250, 12, 0xFFFFFFFF);
            if (learned && devState == DevelopmentSyncPayload.IDLE) {
                drawCenteredFit(gui, Component.translatable("ac.skill_tree.skill_exp").copy()
                        .append((int) (state.experience(selected.id()) * 100) + "%"),
                        cx, cy + 40, 250, 8, 0xFFA1E1FF);
                drawWrappedCentered(gui, selected.description(), cx, cy + 49, 200, 9, 0xFFFFFFFF);
                return;
            }

            drawCenteredFit(gui, Component.translatable("ac.skill_tree.skill_not_learned"),
                    cx, cy + 40, 250, 10, 0xFFFF5555);
            drawConditions(gui, state, category, selected, cx, cy + 50, mouseX, mouseY);
            Component message;
            if (devState == DevelopmentSyncPayload.DEVELOPING)
                message = Component.translatable("ac.skill_tree.progress").copy()
                        .append(" " + Math.round(progress * 100) + "%");
            else if (devState == DevelopmentSyncPayload.DONE)
                message = Component.translatable("ac.skill_tree.dev_successful");
            else if (devState == DevelopmentSyncPayload.FAILED)
                message = Component.translatable("ac.skill_tree.dev_failed");
            else {
                int estimate = skillEnergy(selected);
                message = Component.translatable("ac.skill_tree.learn_question", estimate);
            }
            drawCenteredFit(gui, message, cx, cy + 65, 285, 10,
                    devState == DevelopmentSyncPayload.FAILED ? 0xFFFF7777 : 0xAAFFFFFF);
            if (devState == DevelopmentSyncPayload.IDLE) {
                boolean canLearn = state.canLearn(selected) && selected.level() <= maximumSkillLevel()
                        && hasDevelopmentEnergy(skillEnergy(selected));
                drawSmallButton(gui, cx - 16, cy + 78, canLearn);
            }
        } else {
            int next = Mth.clamp(state.level() + 1, 1, 5);
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                    "textures/abilities/condition/any" + next + ".png");
            drawActionIcon(gui, icon, 32, cx, cy, progress, devState == DevelopmentSyncPayload.DONE);
            Component nextLevel = Component.translatable("ac.ability.level" + next);
            drawCenteredFit(gui, Component.translatable("ac.skill_tree.uplevel", nextLevel),
                    cx, cy + 28, 250, 12, 0xFFFFFFFF);
            int estimate = levelEnergy(state);
            drawCenteredFit(gui, Component.translatable("ac.skill_tree.req").copy().append(" " + estimate),
                    cx, cy + 41, 250, 9, 0xFFFFFFFF);
            Component hint = switch (devState) {
                case DevelopmentSyncPayload.DEVELOPING -> Component.translatable("ac.skill_tree.dev_developing")
                        .copy().append(" " + Math.round(progress * 100) + "%");
                case DevelopmentSyncPayload.DONE -> Component.translatable("ac.skill_tree.dev_successful");
                case DevelopmentSyncPayload.FAILED -> Component.translatable("ac.skill_tree.dev_failed");
                default -> Component.translatable("ac.skill_tree.level_question");
            };
            drawCenteredFit(gui, hint, cx, cy + 52, 270, 9,
                    devState == DevelopmentSyncPayload.FAILED ? 0xFFFF7777 : 0xFFFFFFFF);
            if (devState == DevelopmentSyncPayload.IDLE) {
                boolean allowed = state.hasCategory() && state.level() < 5 && state.level() < maximumSkillLevel()
                        && state.levelProgress() >= 1 && hasDevelopmentEnergy(estimate);
                drawSmallButton(gui, cx - 16, cy + 68, allowed);
            }
        }
    }

    private void drawConditions(GuiGraphics gui, AbilityState state, AbilityCategory category, AbilitySkill skill,
                                int cx, int y, float mouseX, float mouseY) {
        List<ConditionView> conditions = new ArrayList<>();
        boolean levelAccepted = state.level() >= skill.level() && skill.level() <= maximumSkillLevel();
        conditions.add(new ConditionView(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "textures/abilities/condition/any" + Mth.clamp(skill.level(), 1, 5) + ".png"),
                levelAccepted, Component.translatable("ac.skill_tree.level_fail", skill.level())));
        if (category != null) for (AbilitySkill.Requirement requirement : skill.requirements()) {
            AbilitySkill dependency = category.skill(requirement.skillId());
            boolean accepted = dependency != null && state.learned().contains(dependency.id())
                    && state.experience(dependency.id()) + 1.0e-5f >= requirement.experience();
            Component hint = (dependency == null ? Component.literal(requirement.skillId()) : dependency.displayName())
                    .copy().append(" " + Math.round(requirement.experience() * 100) + "%");
            conditions.add(new ConditionView(dependency == null ? NO_CATEGORY : dependency.icon(), accepted, hint));
        }
        int step = 16, len = step * conditions.size();
        drawFit(gui, Component.translatable("ac.skill_tree.req"), cx - len / 2f - 43, y + 2,
                40, 9, 0xAAFFFFFF, true);
        Component hoverHint = null;
        for (int i = 0; i < conditions.size(); i++) {
            int x = cx - len / 2 + i * step;
            ConditionView condition = conditions.get(i);
            float shade = condition.accepted ? 1 : .32f;
            ACGuiTextures.blit(gui, condition.icon, x, y, 14, 14,
                    0, 0, 32, 32, 32, 32, shade, shade, shade, 1);
            if (inside(mouseX, mouseY, x, y, 14, 14)) hoverHint = condition.hint;
        }
        if (hoverHint != null) drawFit(gui, hoverHint, cx + len / 2f + 3, y + 2,
                95, 9, 0xEEFFFFFF, false);
    }

    private record ConditionView(ResourceLocation icon, boolean accepted, Component hint) {}

    private void drawActionIcon(GuiGraphics gui, ResourceLocation icon, int iconTextureSize,
                                int cx, int cy, float progress, boolean glow) {
        ACGuiTextures.blit(gui, SKILL_BACK, cx - 25, cy - 25, 50, 50,
                0, 0, 128, 128, 128, 128);
        ACGuiTextures.blit(gui, icon, cx - 14, cy - 14, 27, 27,
                0, 0, iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
        ACGuiTextures.blit(gui,
                ACSkillProgressTextures.get(glow ? "skill_view_outline_glow" : "skill_view_outline", progress),
                cx - 25, cy - 25, 50, 50, 0, 0, 128, 128, 128, 128);
    }

    private int energyPerStimulation() { return portable ? 750 : advanced() ? 600 : 700; }
    private int skillEnergy(AbilitySkill skill) {
        return (int) (3 + skill.level() * skill.level() * .5f) * energyPerStimulation();
    }
    private int levelEnergy(AbilityState state) { return 5 * (state.level() + 1) * energyPerStimulation(); }
    private boolean hasDevelopmentEnergy(int amount) { return developerEnergy()[0] >= amount; }

    private void drawSmallButton(GuiGraphics gui, int x, int y, boolean active) {
        float shade = active ? 1 : .35f;
        ACGuiTextures.blit(gui, SMALL_BUTTON, x, y, 32, 16,
                0, 0, 64, 32, 64, 32, shade, shade, shade, 1);
    }

    private void beginDevelopment(DeveloperActionPayload payload) {
        if (minecraft != null && minecraft.player != null) {
            int[] energy = developerEnergy();
            net.minecraft.nbt.CompoundTag value = new net.minecraft.nbt.CompoundTag();
            value.putInt("State", DevelopmentSyncPayload.DEVELOPING);
            value.putInt("Action", payload.action());
            value.putString("Skill", payload.skill());
            value.putFloat("Progress", 0);
            value.putBoolean("Portable", portable);
            value.putInt("Energy", energy[0]);
            value.putInt("MaxEnergy", energy[1]);
            minecraft.player.getPersistentData().put("academy:development", value);
        }
        PacketDistributor.sendToServer(payload);
    }

    private void runConsoleCommand() {
        if (console == null || !console.ready || minecraft == null || minecraft.player == null) return;
        String command = console.takeCommand();
        AbilityState state = AbilityState.load(minecraft.player);
        if (!state.hasCategory() && command.equals("learn")) {
            int energy = levelEnergy(state);
            if (!hasDevelopmentEnergy(energy)) console.outputLine(Component.translatable("ac.skill_tree.noenergy").getString());
            else {
                console.startDevelopment(false);
                beginDevelopment(DeveloperActionPayload.level(sourcePos, portable));
            }
        } else if (resetMode() && command.equals("reset")) {
            if (!advanced()) console.outputLine(Component.translatable("ac.skill_tree.console.reset_fail_dev").getString());
            else {
                console.startDevelopment(true);
                beginDevelopment(DeveloperActionPayload.reset(sourcePos));
            }
        } else console.outputLine(Component.translatable("ac.skill_tree.console.invalid_command").getString());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || minecraft == null || minecraft.player == null)
            return super.mouseClicked(mouseX, mouseY, button);
        float x = vx(mouseX), y = vy(mouseY);
        if (!inside(x, y, 0, 0, MAIN_W, MAIN_H)) return false;
        int devState = development().getInt("State");
        if (devState == DevelopmentSyncPayload.DEVELOPING) return true;
        AbilityState state = AbilityState.load(minecraft.player);
        AbilityCategory category = AbilityRegistry.category(state.category());

        if (console != null) return true;
        if (overlay != Overlay.NONE) {
            if (devState == DevelopmentSyncPayload.DONE || devState == DevelopmentSyncPayload.FAILED) {
                setDevelopmentIdle();
                overlay = Overlay.NONE;
                selected = null;
                return true;
            }
            if (overlay == Overlay.SKILL && selected != null && inside(x, y, 184, 168, 32, 19)
                    && !state.learned().contains(selected.id()) && state.canLearn(selected)
                    && selected.level() <= maximumSkillLevel() && hasDevelopmentEnergy(skillEnergy(selected))) {
                beginDevelopment(DeveloperActionPayload.learn(sourcePos, portable, selected.id()));
                return true;
            }
            if (overlay == Overlay.LEVEL && inside(x, y, 184, 157, 32, 20)
                    && state.hasCategory() && state.level() < maximumSkillLevel() && state.levelProgress() >= 1
                    && hasDevelopmentEnergy(levelEnergy(state))) {
                beginDevelopment(DeveloperActionPayload.level(sourcePos, portable));
                return true;
            }
            overlay = Overlay.NONE;
            selected = null;
            return true;
        }
        if (!portable && inside(x, y, 8, 114, 100, 16)) {
            minecraft.setScreen(new FrequencyTransmitterScreen());
            return true;
        }
        if (inside(x, y, 62, 79, 55, 22) && state.level() < 5 && state.level() < maximumSkillLevel()
                && state.levelProgress() >= 1) {
            overlay = Overlay.LEVEL;
            overlayTicks = 0;
            return true;
        }
        AbilitySkill skill = category == null ? null : skillAt(state, category, x, y);
        if (skill != null) {
            selected = skill;
            overlay = Overlay.SKILL;
            overlayTicks = 0;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (console != null && console.ready) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                console.backspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                runConsoleCommand();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && overlay != Overlay.NONE
                && development().getInt("State") != DevelopmentSyncPayload.DEVELOPING) {
            overlay = Overlay.NONE;
            selected = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (console != null && console.ready && StringUtil.isAllowedChatCharacter(codePoint)) {
            console.type(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (development().getInt("State") == DevelopmentSyncPayload.DEVELOPING)
            PacketDistributor.sendToServer(DeveloperActionPayload.abort(sourcePos, portable));
        super.onClose();
    }

    private void drawText(GuiGraphics gui, Component text, float x, float y, float size, int color, boolean centered) {
        ACLegacyFont.draw(gui, text, x, y, size, color,
                centered ? ACLegacyFont.CENTER : ACLegacyFont.LEFT, 0, false);
    }

    private void drawFit(GuiGraphics gui, Component text, float x, float y, float maxWidth,
                         float size, int color, boolean centered) {
        ACLegacyFont.draw(gui, text, centered ? x + maxWidth / 2 : x, y, size, color,
                centered ? ACLegacyFont.CENTER : ACLegacyFont.LEFT, maxWidth, false);
    }

    private void drawCenteredFit(GuiGraphics gui, Component text, int center, int y, int maxWidth, int size, int color) {
        ACLegacyFont.draw(gui, text, center, y, size, color, ACLegacyFont.CENTER, maxWidth, true);
    }

    private void drawWrappedCentered(GuiGraphics gui, Component text, int center, int y, int width, int size, int color) {
        List<String> lines = wrapLegacy(text.getString(), width, size, false);
        for (int i = 0; i < lines.size(); i++) ACLegacyFont.draw(gui, Component.literal(lines.get(i)),
                center, y + i * (size + 1), size, color, ACLegacyFont.CENTER, width, false);
    }

    private static List<String> wrapLegacy(String text, int maxWidth, float size, boolean bold) {
        return ACLegacyFont.wrap(text, maxWidth, size, bold);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Reproduces the timed Academy OS boot console used before category acquisition and during reset. */
    private static final class LegacyConsole {
        private final boolean emergency;
        private final List<String> lines = new ArrayList<>();
        private final String initText;
        private final String startupText;
        private final List<String> bootValues;
        private String input = "";
        private int phase;
        private int phaseTicks;
        private int charIndex;
        private int bootIndex;
        private String bootPrefix = "";
        private boolean ready;
        private boolean developing;
        private boolean resetAction;
        private int lastDevState = DevelopmentSyncPayload.IDLE;
        private int finishedTicks;

        LegacyConsole(boolean emergency, String playerName, int seed) {
            this.emergency = emergency;
            this.initText = Component.translatable("ac.skill_tree.console.init", playerName).getString();
            this.startupText = Component.translatable(emergency ? "ac.skill_tree.console.override"
                    : "ac.skill_tree.console.invalid_cat").getString()
                    + (emergency ? "" : Component.translatable("ac.skill_tree.console.learn_hint").getString());
            java.util.Random random = new java.util.Random(seed);
            bootValues = new ArrayList<>();
            for (int i = 1; i <= 6; i++) bootValues.add((i * 10 + random.nextInt(6) - 3) + "%");
            bootValues.add((64 + random.nextInt(4)) + "%");
            lines.add("");
        }

        void tick(net.minecraft.nbt.CompoundTag development) {
            int state = development.getInt("State");
            if (developing) {
                int progress = Math.round(development.getFloat("Progress") * 100);
                replaceLast(Component.translatable("ac.skill_tree.console.progress", twoDigits(progress)).getString());
                if ((state == DevelopmentSyncPayload.DONE || state == DevelopmentSyncPayload.FAILED)
                        && state != lastDevState) {
                    output("\n" + Component.translatable(state == DevelopmentSyncPayload.DONE
                            ? resetAction ? "ac.skill_tree.console.reset_succ" : "ac.skill_tree.console.dev_succ"
                            : resetAction ? "ac.skill_tree.console.reset_fail" : "ac.skill_tree.console.dev_fail").getString());
                    developing = false;
                    ready = false;
                    finishedTicks = 1;
                }
                lastDevState = state;
                return;
            }
            if (finishedTicks > 0) {
                finishedTicks++;
                return;
            }
            switch (phase) {
                case 0 -> {
                    int end = Math.min(initText.length(), charIndex + 5);
                    output(initText.substring(charIndex, end));
                    charIndex = end;
                    if (charIndex >= initText.length()) { phase = 1; phaseTicks = 0; }
                }
                case 1 -> {
                    if (++phaseTicks >= 8) {
                        phase = 2;
                        phaseTicks = 0;
                        bootPrefix = lines.getLast();
                        replaceLast(bootPrefix + bootValues.getFirst());
                    }
                }
                case 2 -> {
                    if (++phaseTicks >= 6) {
                        phaseTicks = 0;
                        if (++bootIndex < bootValues.size()) replaceLast(bootPrefix + bootValues.get(bootIndex));
                        else {
                            replaceLast(bootPrefix + Component.translatable("ac.skill_tree.console.boot_failed").getString().stripTrailing());
                            output("\n");
                            phase = 3;
                            charIndex = 0;
                        }
                    }
                }
                case 3 -> {
                    int end = Math.min(startupText.length(), charIndex + 5);
                    output(startupText.substring(charIndex, end));
                    charIndex = end;
                    if (charIndex >= startupText.length()) { phase = 4; ready = true; }
                }
                default -> { }
            }
        }

        List<String> renderLines() {
            List<String> result = new ArrayList<>(lines);
            if (ready) result.add("OS >" + input + ((System.currentTimeMillis() % 1000) < 500 ? "_" : ""));
            return result;
        }

        void type(char value) { if (input.length() < 64) input += value; }
        void backspace() { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }

        String takeCommand() {
            String value = input.strip().toLowerCase(java.util.Locale.ROOT);
            output("OS >" + input + "\n");
            input = "";
            return value;
        }

        void outputLine(String value) { output(value.endsWith("\n") ? value : value + "\n"); }

        void startDevelopment(boolean reset) {
            resetAction = reset;
            developing = true;
            ready = false;
            lastDevState = DevelopmentSyncPayload.DEVELOPING;
            output(Component.translatable(reset ? "ac.skill_tree.console.reset_begin"
                    : "ac.skill_tree.console.dev_begin").getString());
            if (!lines.getLast().isEmpty()) output("\n");
            output(Component.translatable("ac.skill_tree.console.progress", "00").getString());
        }

        private void output(String value) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\b') {
                    String last = lines.getLast();
                    if (!last.isEmpty()) lines.set(lines.size() - 1, last.substring(0, last.length() - 1));
                } else if (c == '\n') lines.add("");
                else lines.set(lines.size() - 1, lines.getLast() + c);
            }
            while (lines.size() > 10) lines.removeFirst();
        }

        private void replaceLast(String value) { lines.set(lines.size() - 1, value); }
        private static String twoDigits(int value) { return value < 10 ? "0" + value : Integer.toString(value); }
    }
}
