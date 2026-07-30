package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityCategory;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.AbilityActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Graphical recreation of the 1.12.2 MisakaCloud skill-tree application. */
public final class SkillTreeScreen extends Screen {
    static record Point(int x, int y) {}
    static final Map<String, Point> POSITIONS = positions();
    private static final ResourceLocation AREA_BACK = guiTexture("effect/effect_developer_background.png");
    private static final ResourceLocation SKILL_BACK = guiTexture("developer/skill_back.png");
    private static final ResourceLocation SKILL_OUTLINE = guiTexture("developer/skill_outline.png");

    private int selectedSlot;
    private int lastLearnedCount = -1;
    private int lastPreset = -1;
    private int lastPresetHash;
    private int graphLeft, graphTop, graphWidth, graphHeight;
    private AbilitySkill hoveredSkill;

    public SkillTreeScreen() {
        super(Component.translatable("ac.app.skill_tree.name"));
    }

    private static ResourceLocation guiTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + name);
    }

    private static Map<String, Point> positions() {
        Map<String, Point> p = new HashMap<>();
        p.put("arc_gen", new Point(24, 46)); p.put("charging", new Point(55, 18));
        p.put("body_intensify", new Point(97, 15)); p.put("mine_detect", new Point(225, 12));
        p.put("mag_movement", new Point(137, 35)); p.put("thunder_bolt", new Point(86, 67));
        p.put("railgun", new Point(164, 59)); p.put("thunder_clap", new Point(204, 80));
        p.put("mag_manip", new Point(204, 33));

        p.put("electron_bomb", new Point(15, 45)); p.put("rad_intensify", new Point(35, 75));
        p.put("scatter_bomb", new Point(70, 50)); p.put("light_shield", new Point(55, 15));
        p.put("meltdowner", new Point(115, 40)); p.put("mine_ray_basic", new Point(140, 70));
        p.put("ray_barrage", new Point(140, 10)); p.put("jet_engine", new Point(170, 32));
        p.put("mine_ray_expert", new Point(172, 70)); p.put("mine_ray_luck", new Point(205, 82));
        p.put("electron_missile", new Point(210, 35));

        p.put("threatening_teleport", new Point(14, 42)); p.put("dim_folding_theorem", new Point(50, 75));
        p.put("penetrate_teleport", new Point(60, 46)); p.put("mark_teleport", new Point(70, 16));
        p.put("flesh_ripping", new Point(130, 12)); p.put("location_teleport", new Point(118, 50));
        p.put("shift_tp", new Point(175, 47)); p.put("space_fluct", new Point(160, 80));
        p.put("flashing", new Point(220, 20));

        p.put("dir_shock", new Point(16, 45)); p.put("ground_shock", new Point(64, 85));
        p.put("vec_accel", new Point(76, 40)); p.put("storm_wing", new Point(130, 20));
        p.put("dir_blast", new Point(136, 80)); p.put("vec_deviation", new Point(145, 53));
        p.put("plasma_cannon", new Point(175, 14)); p.put("vec_reflection", new Point(210, 50));
        p.put("blood_retro", new Point(204, 83));

        p.put("brain_course", new Point(30, 110)); p.put("brain_course_advanced", new Point(115, 110));
        p.put("mind_course", new Point(205, 110));
        return Map.copyOf(p);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        if (minecraft == null || minecraft.player == null) return;
        AbilityState state = AbilityState.load(minecraft.player);
        lastLearnedCount = state.learned().size();
        lastPreset = state.currentPreset();
        lastPresetHash = state.presets().hashCode();

        int panelWidth = Math.min(520, width - 24);
        int left = (width - panelWidth) / 2;
        addRenderableWidget(Button.builder(Component.literal("P" + (state.currentPreset() + 1)), ignored ->
                        PacketDistributor.sendToServer(AbilityActionPayload.switchPreset((state.currentPreset() + 1) % 4)))
                .bounds(left, 17, 42, 18).build());
        int slotWidth = (panelWidth - 48) / 4;
        for (int slot = 0; slot < 4; slot++) {
            final int index = slot;
            AbilitySkill selected = AbilityRegistry.skill(state.preset(slot));
            Component label = Component.literal((slot + 1) + ": ")
                    .append(selected == null ? Component.literal("-") : selected.displayName());
            addRenderableWidget(Button.builder(label, button -> {
                selectedSlot = index;
                rebuild();
            }).bounds(left + 48 + slot * slotWidth, 17, slotWidth - 3, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored ->
                minecraft.setScreen(new TerminalScreen())).bounds(width / 2 - 48, height - 25, 96, 18).build());

        graphLeft = left;
        graphTop = 52;
        graphWidth = panelWidth;
        graphHeight = Math.max(90, height - graphTop - 31);
    }

    private int nodeX(AbilitySkill skill) {
        Point point = POSITIONS.getOrDefault(skill.name(), new Point(125, 60));
        return graphLeft + 18 + Math.round(point.x() / 250f * (graphWidth - 52));
    }

    private int nodeY(AbilitySkill skill) {
        Point point = POSITIONS.getOrDefault(skill.name(), new Point(125, 60));
        return graphTop + 10 + Math.round(point.y() / 130f * (graphHeight - 42));
    }

    private AbilitySkill skillAt(double mouseX, double mouseY) {
        if (minecraft == null || minecraft.player == null) return null;
        AbilityCategory category = AbilityRegistry.category(AbilityState.load(minecraft.player).category());
        if (category == null) return null;
        for (AbilitySkill skill : category.skills()) {
            double dx = mouseX - nodeX(skill), dy = mouseY - nodeY(skill);
            if (dx * dx + dy * dy <= 15 * 15) return skill;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && minecraft != null && minecraft.player != null) {
            AbilitySkill skill = skillAt(mouseX, mouseY);
            AbilityState state = AbilityState.load(minecraft.player);
            if (skill != null && skill.controllable() && state.learned().contains(skill.id())) {
                PacketDistributor.sendToServer(AbilityActionPayload.select(selectedSlot, skill.id()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null) {
            AbilityState state = AbilityState.load(minecraft.player);
            if (state.learned().size() != lastLearnedCount || state.currentPreset() != lastPreset
                    || state.presets().hashCode() != lastPresetHash) rebuild();
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(graphLeft - 3, graphTop - 3, graphLeft + graphWidth + 3, graphTop + graphHeight + 3, 0xD0050B10);
        gui.setColor(.55f, .86f, 1f, .72f);
        gui.blit(AREA_BACK, graphLeft, graphTop, graphWidth, graphHeight, 0, 0, 512, 279, 512, 279);
        gui.setColor(1, 1, 1, 1);

        AbilityState state = minecraft != null && minecraft.player != null ? AbilityState.load(minecraft.player) : null;
        AbilityCategory category = state == null ? null : AbilityRegistry.category(state.category());
        hoveredSkill = skillAt(mouseX, mouseY);
        if (category != null) {
            // Dependency graph is drawn before nodes.
            for (AbilitySkill skill : category.skills()) {
                int x2 = nodeX(skill), y2 = nodeY(skill);
                for (AbilitySkill.Requirement requirement : skill.requirements()) {
                    AbilitySkill parent = category.skill(requirement.skillId());
                    if (parent == null) continue;
                    int color = state.learned().contains(skill.id()) ? 0xCC66DFFF : 0x664F7584;
                    drawLine(gui, nodeX(parent), nodeY(parent), x2, y2, color);
                }
            }

            for (AbilitySkill skill : category.skills()) {
                int x = nodeX(skill), y = nodeY(skill);
                boolean learned = state.learned().contains(skill.id());
                boolean available = skill.level() <= state.level();
                boolean assigned = skill.id().equals(state.preset(selectedSlot));
                int size = skill == hoveredSkill ? 34 : 30;
                gui.setColor(learned ? .65f : .35f, learned ? .95f : .45f, learned ? 1f : .52f,
                        learned ? 1f : available ? .65f : .28f);
                gui.blit(SKILL_BACK, x - size / 2, y - size / 2, size, size, 0, 0, 128, 128, 128, 128);
                gui.setColor(learned ? 1f : .35f, learned ? 1f : .35f, learned ? 1f : .35f, learned ? 1f : .6f);
                gui.blit(skill.icon(), x - 11, y - 11, 22, 22, 0, 0, 32, 32, 32, 32);
                gui.setColor(assigned ? .45f : 1f, assigned ? 1f : .75f, 1f, 1f);
                gui.blit(SKILL_OUTLINE, x - size / 2, y - size / 2, size, size, 0, 0, 128, 128, 128, 128);
                gui.setColor(1, 1, 1, 1);
                if (learned) {
                    String exp = Math.round(state.experience(skill.id()) * 100) + "%";
                    gui.drawCenteredString(font, exp, x, y + 14, 0xFF9DEAFF);
                }
            }

            gui.drawCenteredString(font, category.displayName().copy().append("  ")
                            .append(Component.translatable("ac.ability.level" + state.level())),
                    width / 2, 39, category.color() | 0xFF000000);
            if (hoveredSkill != null) {
                Component info = Component.literal("LV" + hoveredSkill.level() + "  ").append(hoveredSkill.displayName())
                        .append("  ").append(hoveredSkill.description());
                int infoWidth = Math.min(font.width(info) + 12, graphWidth - 12);
                int infoX = Mth.clamp(mouseX - infoWidth / 2, graphLeft + 6, graphLeft + graphWidth - infoWidth - 6);
                int infoY = graphTop + graphHeight - 22;
                gui.fill(infoX, infoY, infoX + infoWidth, infoY + 17, 0xE006121A);
                gui.drawString(font, font.plainSubstrByWidth(info.getString(), infoWidth - 10), infoX + 5, infoY + 5,
                        0xFFE4F8FF, false);
            }
        } else {
            gui.drawCenteredString(font, Component.translatable("ac.ability.no_category"), width / 2,
                    graphTop + graphHeight / 2, 0xFFFF8080);
        }
        gui.drawCenteredString(font, title, width / 2, 6, 0xFFD7F7FF);
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private static void drawLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            gui.fill(x, y, x + 2, y + 2, color);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
