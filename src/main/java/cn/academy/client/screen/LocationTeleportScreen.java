package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.LocationActionPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Native recreation of LocationTeleport's 1.12.2 loctele_new.xml overlay. */
public final class LocationTeleportScreen extends Screen {
    private static final float SCALE = .24f;
    private static final float MENU_X = 20;
    private static final float MENU_Y = -179.1667f;
    private static final float MENU_WIDTH = 442;
    private static final float MENU_HEIGHT = 530;
    private static final float LIST_Y = MENU_Y + 18;
    private static final float ROW_HEIGHT = 80;
    private static final float ROW_STEP = 82;
    private static final int VISIBLE_ROWS = 6;
    private static final ResourceLocation TELEPORT_ICON = texture("guis/icons/icon_location_on.png");
    private static final ResourceLocation REMOVE_ICON = texture("guis/icons/icon_clear.png");
    private static final ResourceLocation CONFIRM_ICON = texture("guis/check.png");

    private final long openedAt = Util.getMillis();
    private int scrollRow;
    private int hoveredRow = -1;
    private int lastLocationsHash;
    private boolean editingName;
    private String newName = "";

    public LocationTeleportScreen() {
        super(Component.translatable("ac.ability.teleporter.location_teleport.name"));
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/" + path);
    }

    private float originX() { return width * .5f; }
    private float originY() { return height * .5f; }
    private float virtualX(double mouseX) { return (float) ((mouseX - originX()) / SCALE); }
    private float virtualY(double mouseY) { return (float) ((mouseY - originY()) / SCALE); }

    private List<AbilityState.TeleportLocation> locations() {
        if (minecraft == null || minecraft.player == null) return List.of();
        return AbilityState.load(minecraft.player).teleportLocations();
    }

    @Override
    protected void init() {
        lastLocationsHash = locations().hashCode();
    }

    @Override
    public void tick() {
        super.tick();
        List<AbilityState.TeleportLocation> locations = locations();
        if (locations.hashCode() != lastLocationsHash) {
            lastLocationsHash = locations.hashCode();
            scrollRow = Mth.clamp(scrollRow, 0, maxScroll(locations.size()));
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        float vx = virtualX(mouseX);
        float vy = virtualY(mouseY);
        List<AbilityState.TeleportLocation> locations = locations();
        int totalRows = locations.size() + 1;
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(locations.size()));
        hoveredRow = rowAt(vx, vy, totalRows);
        float opening = Mth.clamp((Util.getMillis() - openedAt) / 400f, 0, 1);
        float shownHeight = MENU_HEIGHT * easeOut(opening);

        gui.pose().pushPose();
        gui.pose().translate(originX(), originY(), 0);
        gui.pose().scale(SCALE, SCALE, 1);
        drawPanel(gui, MENU_X, MENU_Y, MENU_WIDTH, shownHeight, 0x300BD3F0, 0xA85CE5F5);

        int clipLeft = Math.round(originX() + MENU_X * SCALE);
        int clipTop = Math.round(originY() + MENU_Y * SCALE);
        int clipRight = Math.round(originX() + (MENU_X + MENU_WIDTH) * SCALE);
        int clipBottom = Math.round(originY() + (MENU_Y + shownHeight) * SCALE);
        gui.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int displayed = Math.min(VISIBLE_ROWS, totalRows - scrollRow);
        for (int visible = 0; visible < displayed; visible++) {
            int rowIndex = scrollRow + visible;
            float rowY = LIST_Y + visible * ROW_STEP;
            float alpha = Mth.clamp((Util.getMillis() - openedAt - rowIndex * 60f) / 200f, 0, 1);
            boolean hovered = hoveredRow == rowIndex;
            int panelAlpha = Math.round(255 * alpha * (hovered ? .4f : .1f));
            gui.fill(Math.round(MENU_X), Math.round(rowY), Math.round(MENU_X + MENU_WIDTH),
                    Math.round(rowY + ROW_HEIGHT), panelAlpha << 24 | 0xFFFFFF);
            if (rowIndex < locations.size()) drawLocationRow(gui, locations.get(rowIndex), rowIndex,
                    rowY, hovered, alpha);
            else drawAddRow(gui, rowY, hovered, alpha, locations.size() >= 32);
        }
        gui.disableScissor();

        if (hoveredRow >= 0) drawHint(gui, hoveredRow, locations, vx, vy);
        gui.pose().popPose();
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawLocationRow(GuiGraphics gui, AbilityState.TeleportLocation location, int index,
                                 float y, boolean hovered, float alpha) {
        Availability stat = availability(location);
        int textColor = stat.enabled ? (hovered ? 0xFF2E3B41 : withAlpha(0xFFC1CFD5, alpha))
                : withAlpha(0xFFA2A2A2, alpha);
        drawText(gui, location.name(), MENU_X + 45.28f, y + 16.13f, 377.8f, 43,
                textColor, Align.LEFT);
        if (stat.enabled) {
            drawIcon(gui, TELEPORT_ICON, MENU_X + 335, y + 29.92f, alpha, hovered);
        }
        drawIcon(gui, REMOVE_ICON, MENU_X + 375, y + 29.92f, alpha, hovered);
    }

    private void drawAddRow(GuiGraphics gui, float y, boolean hovered, float alpha, boolean full) {
        String text = editingName ? newName + ((Util.getMillis() / 400 & 1) == 0 ? "_" : "")
                : full ? Component.translatable("ac.gui.location.full").getString() : "Add...";
        int color = full ? withAlpha(0xFFA2A2A2, alpha)
                : hovered || editingName ? withAlpha(0xFFC1CFD5, alpha) : withAlpha(0x66C1CFD5, alpha);
        drawText(gui, text, MENU_X + 45.28f, y + 19.7f, 280.6f, 43, color, Align.LEFT);
        if (!full) drawIcon(gui, CONFIRM_ICON, MENU_X + 375.83f, y + 29.92f, alpha, hovered);
    }

    private void drawIcon(GuiGraphics gui, ResourceLocation texture, float x, float y, float alpha, boolean hovered) {
        gui.setColor(1, 1, 1, alpha * (hovered ? 1f : .7f));
        gui.blit(texture, Math.round(x), Math.round(y), 34, 34, 0, 0, 64, 64, 64, 64);
        gui.setColor(1, 1, 1, 1);
    }

    private void drawHint(GuiGraphics gui, int index, List<AbilityState.TeleportLocation> locations,
                          float mouseX, float mouseY) {
        List<String> lines = new ArrayList<>();
        if (index < locations.size()) {
            AbilityState.TeleportLocation location = locations.get(index);
            Availability stat = availability(location);
            lines.add(dimensionName(location.dimension()) + " (" + location.dimension() + ")");
            lines.add(String.format("(%.0f, %.0f, %.0f)", location.x(), location.y(), location.z()));
            lines.add(String.format("%.0f CP", stat.cpCost));
            if (stat.reason != null) lines.add(stat.reason);
        } else if (minecraft != null && minecraft.player != null) {
            String dimension = minecraft.player.level().dimension().location().toString();
            lines.add(dimensionName(dimension) + " (" + dimension + ")");
            lines.add(String.format("(%.0f, %.0f, %.0f)", minecraft.player.getX(),
                    minecraft.player.getY(), minecraft.player.getZ()));
        }
        if (lines.isEmpty()) return;
        float width = 0;
        for (String line : lines) width = Math.max(width, font.width(line) * 40f / font.lineHeight);
        width += 40;
        float height = lines.size() * 42 + 40;
        float rowVisible = index - scrollRow;
        float hintY = LIST_Y + rowVisible * ROW_STEP;
        float hintRight = -23.33f;
        float hintLeft = hintRight - width;
        drawPanel(gui, hintLeft, hintY, width, height, 0x260BD3F0, 0x905CE5F5);
        for (int line = 0; line < lines.size(); line++) {
            drawText(gui, lines.get(line), hintLeft + 20, hintY + 20 + line * 42,
                    width - 40, 40, 0xFFC1CFD5, Align.RIGHT);
        }
    }

    private Availability availability(AbilityState.TeleportLocation location) {
        if (minecraft == null || minecraft.player == null) return new Availability(false, 0, "");
        AbilityState state = AbilityState.load(minecraft.player);
        AbilitySkill skill = AbilityRegistry.skill("teleporter.location_teleport");
        boolean cross = !minecraft.player.level().dimension().location().toString().equals(location.dimension());
        double distance = minecraft.player.position().distanceTo(
                new net.minecraft.world.phys.Vec3(location.x(), location.y(), location.z()));
        float exp = skill == null ? 0 : state.experience(skill.id());
        float cp = (200 - exp * 50) * (cross ? 2 : 1)
                * Math.max(8f, (float) Math.sqrt(Math.min(800, distance)));
        String reason = null;
        if (cross && exp <= .8f) reason = Component.translatable("ac.gui.loctele.err_exp").getString();
        else if (state.cp() < cp) reason = Component.translatable("ac.gui.loctele.err_cp").getString();
        else if (!state.canUse() || skill == null || state.cooldown(skill.id()) > 0)
            reason = Component.translatable("ac.ability.context.unavailable").getString();
        return new Availability(reason == null, cp, reason);
    }

    private String dimensionName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? id : Component.translatable(Util.makeDescriptionId("dimension", location)).getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        List<AbilityState.TeleportLocation> locations = locations();
        int row = rowAt(vx, vy, locations.size() + 1);
        if (row < 0) {
            editingName = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float rowY = LIST_Y + (row - scrollRow) * ROW_STEP;
        if (row < locations.size()) {
            if (inside(vx, vy, MENU_X + 375, rowY + 25, 45, 45)) {
                PacketDistributor.sendToServer(LocationActionPayload.remove(row));
                return true;
            }
            if (inside(vx, vy, MENU_X + 330, rowY + 25, 43, 45) && availability(locations.get(row)).enabled) {
                PacketDistributor.sendToServer(LocationActionPayload.teleport(row));
                minecraft.setScreen(null);
                return true;
            }
        } else if (locations.size() < 32) {
            if (inside(vx, vy, MENU_X, rowY, MENU_WIDTH, ROW_HEIGHT)) {
                if (inside(vx, vy, MENU_X + 360, rowY + 20, 62, 55) && editingName) confirmAdd();
                else editingName = true;
                return true;
            }
        }
        return true;
    }

    private void confirmAdd() {
        if (!editingName || minecraft == null || minecraft.player == null) return;
        PacketDistributor.sendToServer(LocationActionPayload.add(newName.substring(0, Math.min(16, newName.length()))));
        newName = "";
        editingName = false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingName && !Character.isISOControl(codePoint) && newName.length() < 16) {
            newName += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { confirmAdd(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newName.isEmpty()) {
                newName = newName.substring(0, newName.offsetByCodePoints(newName.length(), -1)); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { editingName = false; newName = ""; return true; }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V && minecraft != null) {
                String pasted = minecraft.keyboardHandler.getClipboard();
                for (int i = 0; i < pasted.length() && newName.length() < 16; i++) {
                    char c = pasted.charAt(i);
                    if (!Character.isISOControl(c)) newName += c;
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        if (inside(vx, vy, MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll(locations().size()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int rowAt(float x, float y, int totalRows) {
        if (x < MENU_X || x > MENU_X + MENU_WIDTH || y < LIST_Y) return -1;
        int visible = (int) ((y - LIST_Y) / ROW_STEP);
        if (visible < 0 || visible >= VISIBLE_ROWS || y > LIST_Y + visible * ROW_STEP + ROW_HEIGHT) return -1;
        int row = scrollRow + visible;
        return row < totalRows ? row : -1;
    }

    private static int maxScroll(int locations) { return Math.max(0, locations + 1 - VISIBLE_ROWS); }
    private static float easeOut(float value) { return 1 - (1 - value) * (1 - value); }
    private static int withAlpha(int color, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private void drawPanel(GuiGraphics gui, float x, float y, float width, float height, int fill, int outline) {
        int l = Math.round(x), t = Math.round(y), r = Math.round(x + width), b = Math.round(y + height);
        gui.fill(l, t, r, b, fill);
        gui.fill(l, t, r, t + 2, outline); gui.fill(l, b - 2, r, b, outline);
        gui.fill(l, t, l + 2, b, outline); gui.fill(r - 2, t, r, b, outline);
    }

    private void drawText(GuiGraphics gui, String text, float x, float y, float maxWidth,
                          float size, int color, Align align) {
        float textScale = size / Math.max(1f, font.lineHeight);
        float renderedWidth = font.width(text) * textScale;
        if (renderedWidth > maxWidth) textScale *= maxWidth / renderedWidth;
        float actualWidth = font.width(text) * textScale;
        float drawX = align == Align.RIGHT ? x + maxWidth - actualWidth
                : align == Align.CENTER ? x + (maxWidth - actualWidth) * .5f : x;
        gui.pose().pushPose();
        gui.pose().translate(drawX, y, 5);
        gui.pose().scale(textScale, textScale, 1);
        gui.drawString(font, text, 0, 0, color, false);
        gui.pose().popPose();
    }

    private static boolean inside(float x, float y, float l, float t, float w, float h) {
        return x >= l && x <= l + w && y >= t && y <= t + h;
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Align { LEFT, CENTER, RIGHT }
    private record Availability(boolean enabled, float cpCost, String reason) {}
}
