package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.menu.ACMachineMenu;
import cn.academy.network.InterfererConfigPayload;
import cn.academy.network.MachineActionPayload;
import cn.academy.network.MatrixConfigPayload;
import cn.academy.network.NodeConfigPayload;
import cn.academy.network.WirelessConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Native implementation of the 1.12.2 TechUI machine-page framework. */
public final class ACMachineScreen extends AbstractContainerScreen<ACMachineMenu> {
    private static final ResourceLocation PARENT = texture("parent/parent_background.png");
    private static final ResourceLocation INVENTORY = texture("ui/ui_inventory.png");
    private static final ResourceLocation HISTOGRAM = texture("histogram.png");
    private static final ResourceLocation TAB_INVENTORY = texture("icons/icon_inv.png");
    private static final ResourceLocation TAB_WIRELESS = texture("icons/icon_wireless.png");
    private static final ResourceLocation ELEMENT = texture("element/element_background300x32.png");
    private static final ResourceLocation CONNECTED = texture("icons/icon_connected.png");
    private static final ResourceLocation UNCONNECTED = texture("icons/icon_unconnected.png");
    private static final ResourceLocation KEY = texture("icons/icon_key.png");
    private static final ResourceLocation NODE = texture("icons/icon_node.png");
    private static final ResourceLocation MATRIX = texture("icons/icon_matrix.png");
    private static final ResourceLocation TO_NODE = texture("icons/icon_tonode.png");
    private static final ResourceLocation TO_MATRIX = texture("icons/icon_tomatrix.png");
    private static final ResourceLocation PROGRESS_FUSOR = texture("progress/progress_fusor.png");
    private static final ResourceLocation PROGRESS_FORMER = texture("progress/progress_metalformer.png");
    private static final ResourceLocation ARROW_LEFT = texture("button/button_arrowlefta.png");
    private static final ResourceLocation ARROW_RIGHT = texture("button/button_arrowrighta.png");
    private static final ResourceLocation ARROW_UP = texture("button/button_arrowupb.png");
    private static final ResourceLocation ARROW_DOWN = texture("button/button_arrowdownb.png");
    private static final ResourceLocation SWITCH_ON = texture("button/button_switch_on.png");
    private static final ResourceLocation SWITCH_OFF = texture("button/button_switch_off.png");
    private static final ResourceLocation ADD = texture("button/button_add.png");
    private static final ResourceLocation REMOVE = texture("button/button_remove.png");
    private static final ResourceLocation NODE_EFFECT = texture("effect/effect_node.png");
    private static final ResourceLocation SOLAR_EFFECT = texture("effect/effect_solar.png");

    private Page page = Page.INVENTORY;
    private EditBox configName;
    private EditBox configPassword;
    private EditBox wirelessPassword;
    private EditBox whitelistInput;
    private final List<WirelessTarget> wirelessTargets = new ArrayList<>();
    private int wirelessScroll;
    private int selectedWireless = -1;
    private int wirelessRefresh;
    private List<String> interfererWhitelist = new ArrayList<>();
    private boolean interfererEnabled;
    private double interfererRange = 10;
    private int selectedWhitelist = -1;
    private int whitelistScroll;
    private boolean configPasswordDirty;

    public ACMachineScreen(ACMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 207;
        imageHeight = 187;
        titleLabelX = 0;
        inventoryLabelY = 10_000;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private ACMachineBlockEntity machine() { return menu.machine(); }
    private MachineKind kind() { return machine() == null ? null : machine().kind(); }
    private int panelX() { return leftPos; }
    private int panelY() { return topPos; }
    private int infoX() { return leftPos + 183; }

    @Override
    protected void init() {
        super.init();
        ACMachineBlockEntity machine = machine();
        if (machine == null) return;
        int x = infoX();
        if (kind() == MachineKind.MATRIX || isNode()) {
            configName = new EditBox(font, x + 43, topPos + 136, 49, 10,
                    Component.translatable(kind() == MachineKind.MATRIX ? "ac.gui.common.prop.ssid" : "ac.node.name"));
            configName.setBordered(false);
            configName.setMaxLength(24);
            configName.setTextColor(0xFFFFFFFF);
            configName.setValue(kind() == MachineKind.MATRIX ? machine.networkId() : machine.nodeName());
            addRenderableWidget(configName);
            configPassword = new EditBox(font, x + 43, topPos + 149, 49, 10,
                    Component.translatable("ac.gui.common.prop.password"));
            configPassword.setBordered(false);
            configPassword.setMaxLength(64);
            configPassword.setHint(Component.literal("••••"));
            configPassword.setResponder(ignored -> configPasswordDirty = true);
            addRenderableWidget(configPassword);
        }
        if (hasWirelessPage()) {
            wirelessPassword = new EditBox(font, leftPos + 82, topPos + 64, 48, 10,
                    Component.translatable("ac.frequency.password"));
            wirelessPassword.setBordered(false);
            wirelessPassword.setMaxLength(64);
            wirelessPassword.visible = false;
            addRenderableWidget(wirelessPassword);
            refreshWirelessTargets();
        }
        if (kind() == MachineKind.ABILITY_INTERFERER) {
            interfererEnabled = machine.isInterfererActive();
            interfererRange = machine.interfererRange();
            interfererWhitelist = new ArrayList<>(machine.interfererWhitelist());
            whitelistInput = new EditBox(font, leftPos + 58, topPos + 86, 40, 10,
                    Component.translatable("ac.interferer.player"));
            whitelistInput.setBordered(false);
            whitelistInput.setMaxLength(32);
            whitelistInput.visible = false;
            addRenderableWidget(whitelistInput);
        }
        updatePageState();
    }

    private boolean isNode() {
        return kind() == MachineKind.NODE_BASIC || kind() == MachineKind.NODE_STANDARD
                || kind() == MachineKind.NODE_ADVANCED;
    }

    private boolean hasWirelessPage() {
        return machine() != null && machine().isWirelessConfigurable() && kind() != MachineKind.MATRIX;
    }

    private void updatePageState() {
        menu.setInventoryPage(page == Page.INVENTORY);
        if (wirelessPassword != null) wirelessPassword.visible = page == Page.WIRELESS && selectedWireless >= 0;
        if (whitelistInput != null) whitelistInput.visible = page == Page.INVENTORY && whitelistInput.visible;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (machine() == null) return;
        if (++wirelessRefresh >= 20) {
            wirelessRefresh = 0;
            if (hasWirelessPage()) refreshWirelessTargets();
            if (kind() == MachineKind.ABILITY_INTERFERER && whitelistInput != null && !whitelistInput.isFocused()) {
                interfererEnabled = machine().isInterfererActive();
                interfererRange = machine().interfererRange();
                interfererWhitelist = new ArrayList<>(machine().interfererWhitelist());
            }
        }
        if (configName != null && !configName.isFocused()) {
            String value = kind() == MachineKind.MATRIX ? machine().networkId() : machine().nodeName();
            if (!configName.getValue().equals(value)) configName.setValue(value);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        if (page == Page.INVENTORY) renderInventoryPage(gui, partialTick, mouseX, mouseY);
        else renderWirelessPage(gui, mouseX, mouseY);
        renderPageTabs(gui, mouseX, mouseY);
        renderInfoPanel(gui);
    }

    private void renderInventoryPage(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = panelX(), y = panelY();
        gui.blit(PARENT, x, y, 176, 187, 0, 0, 352, 374, 352, 374);
        if (kind() != MachineKind.ABILITY_INTERFERER)
            gui.blit(INVENTORY, x, y, 176, 187, 0, 0, 352, 374, 352, 374);
        ResourceLocation overlay = inventoryOverlay();
        if (overlay != null) gui.blit(overlay, x, y, 176, 187, 0, 0, 352, 374, 352, 374);
        renderMachineSpecific(gui, x, y, mouseX, mouseY);
    }

    private ResourceLocation inventoryOverlay() {
        if (kind() == null) return null;
        String name = switch (kind()) {
            case IMAG_FUSOR -> "ui_imagfusor";
            case METAL_FORMER -> "ui_metalformer";
            case MATRIX -> "ui_matrix";
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> "ui_node";
            case PHASE_GENERATOR -> "ui_phasegen";
            case SOLAR_GENERATOR, WIND_BASE -> "ui_windbase";
            case WIND_GENERATOR -> "ui_windmain";
            case ABILITY_INTERFERER -> "ui_interfere";
            default -> null;
        };
        return name == null ? null : texture("ui/" + name + ".png");
    }

    private void renderMachineSpecific(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        if (kind() == MachineKind.IMAG_FUSOR) {
            int width = menu.maxProgress() <= 0 ? 0 : Math.round(61f * menu.progress() / menu.maxProgress());
            if (width > 0) {
                gui.enableScissor(x + 58, y + 47, x + 58 + width, y + 62);
                gui.blit(PROGRESS_FUSOR, x + 58, y + 47, 61, 15, 0, 0, 126, 30, 126, 30);
                gui.disableScissor();
            }
            String requirement = menu.progress() > 0 ? (menu.machine().items.getStackInSlot(0).is(cn.academy.registry.ACItems.CRYSTAL_LOW.get())
                    ? "3000" : "8000") : "IDLE";
            drawCentered(gui, requirement, x + 68, y + 12, 44, 0xFFFFFFFF);
        } else if (kind() == MachineKind.METAL_FORMER) {
            int width = menu.maxProgress() <= 0 ? 0 : Math.round(57f * menu.progress() / menu.maxProgress());
            if (width > 0) {
                gui.enableScissor(x + 60, y + 47, x + 60 + width, y + 62);
                gui.blit(PROGRESS_FORMER, x + 60, y + 47, 57, 15, 0, 0, 114, 30, 114, 30);
                gui.disableScissor();
            }
            ResourceLocation mode = texture("icons/icon_former_" + modeName(menu.mode()) + ".png");
            gui.blit(mode, x + 76, y + 5, 24, 24, 0, 0, 48, 48, 48, 48);
            tintIcon(gui, ARROW_LEFT, x + 60, y + 9, 16, 16, 32, 32,
                    inside(mouseX, mouseY, x + 60, y + 9, 16, 16));
            tintIcon(gui, ARROW_RIGHT, x + 100, y + 9, 16, 16, 32, 32,
                    inside(mouseX, mouseY, x + 100, y + 9, 16, 16));
        } else if (isNode()) {
            int linkedOffset = machine().networkId().isEmpty() ? 8 : 0;
            int frames = machine().networkId().isEmpty() ? 2 : 8;
            long frameTime = machine().networkId().isEmpty() ? 3000 : 800;
            int frame = linkedOffset + (int) ((net.minecraft.Util.getMillis() / frameTime) % frames);
            gui.blit(NODE_EFFECT, x + 42, y + 36, 93, 38,
                    0, frame * 75, 186, 75, 186, 750);
        } else if (kind() == MachineKind.SOLAR_GENERATOR && minecraft != null && minecraft.level != null) {
            boolean sky = minecraft.level.canSeeSky(machine().getBlockPos().above()) && minecraft.level.isDay();
            int frame = !sky ? 1 : minecraft.level.isRaining() ? 2 : 0;
            gui.blit(SOLAR_EFFECT, x + 56, y + 23, 62, 42,
                    0, frame * 70, 104, 70, 104, 210);
        } else if (kind() == MachineKind.WIND_BASE) {
            ResourceLocation base = texture("icons/icon_wind_base.png");
            ResourceLocation middle = texture("icons/icon_wind_middle.png");
            ResourceLocation main = texture("icons/icon_wind_main.png");
            boolean complete = machine().windStructureCompleteForRender();
            tintIcon(gui, base, x + 76, y + 49, 24, 24, 48, 48, true);
            tintIcon(gui, middle, x + 76, y + 31, 24, 24, 48, 48, complete);
            tintIcon(gui, main, x + 76, y + 13, 24, 24, 48, 48, complete);
        } else if (kind() == MachineKind.ABILITY_INTERFERER) {
            renderInterferer(gui, x, y, mouseX, mouseY);
        }
    }

    private void renderInterferer(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        gui.drawString(font, Component.translatable("ac.interferer.switch"), x + 8, y + 27, 0xFFFFFFFF, false);
        tintIcon(gui, interfererEnabled ? SWITCH_ON : SWITCH_OFF, x + 48, y + 25, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 48, y + 25, 16, 16));
        gui.drawString(font, Component.translatable("ac.interferer.range"), x + 8, y + 43, 0xFFFFFFFF, false);
        tintIcon(gui, ARROW_LEFT, x + 48, y + 41, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 48, y + 41, 16, 16));
        tintIcon(gui, ARROW_RIGHT, x + 108, y + 41, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 108, y + 41, 16, 16));
        drawCentered(gui, String.format("%.0f", interfererRange), x + 64, y + 43, 44, 0xFFFFFFFF);

        int panelY = y + 82;
        tintIcon(gui, ADD, x + 12, panelY, 12, 12, 24, 24,
                inside(mouseX, mouseY, x + 12, panelY, 12, 12));
        tintIcon(gui, REMOVE, x + 32, panelY, 12, 12, 24, 24,
                inside(mouseX, mouseY, x + 32, panelY, 12, 12));
        tintIcon(gui, ARROW_UP, x + 152, panelY + 13, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 152, panelY + 13, 16, 16));
        tintIcon(gui, ARROW_DOWN, x + 152, panelY + 63, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 152, panelY + 63, 16, 16));
        int start = Math.min(whitelistScroll, Math.max(0, interfererWhitelist.size() - 4));
        for (int row = 0; row < 4 && start + row < interfererWhitelist.size(); row++) {
            int index = start + row;
            int rowY = y + 95 + row * 16;
            gui.setColor(1, 1, 1, selectedWhitelist == index ? 1 : .7f);
            gui.blit(ELEMENT, x + 8, rowY, 140, 16, 0, 0, 300, 32, 300, 32);
            gui.setColor(1, 1, 1, 1);
            gui.blit(texture("icons/icon_whitelist_single.png"), x + 18, rowY + 2, 12, 12,
                    0, 0, 24, 24, 24, 24);
            gui.drawString(font, interfererWhitelist.get(index), x + 34, rowY + 4,
                    selectedWhitelist == index ? 0xFFFFFFFF : 0xFFC5D1D5, false);
        }
    }

    private void renderWirelessPage(GuiGraphics gui, int mouseX, int mouseY) {
        int x = panelX(), y = panelY();
        gui.blit(PARENT, x, y, 176, 187, 0, 0, 352, 374, 352, 374);
        ResourceLocation logo = isNode() ? TO_MATRIX : TO_NODE;
        gui.blit(logo, x + 10, y + 10, 16, 16, 0, 0, 32, 32, 32, 32);
        gui.drawString(font, Component.translatable("ac.gui.common.pg_wireless.connected"),
                x + 13, y + 28, 0xFFDDE7EA, false);
        gui.drawString(font, Component.translatable("ac.gui.common.pg_wireless.available"),
                x + 13, y + 52, 0xFFDDE7EA, false);

        gui.setColor(1, 1, 1, .68f);
        gui.blit(ELEMENT, x + 8, y + 35, 150, 16, 0, 0, 300, 32, 300, 32);
        gui.setColor(1, 1, 1, 1);
        WirelessTarget connected = connectedTarget();
        ResourceLocation typeIcon = isNode() ? MATRIX : NODE;
        gui.blit(typeIcon, x + 16, y + 37, 12, 12, 0, 0, 24, 24, 24, 24);
        String connectedName = connected == null ? Component.translatable("ac.gui.common.pg_wireless.not_connected").getString()
                : connected.name;
        gui.drawString(font, fit(connectedName, 94), x + 30, y + 39,
                connected == null ? 0x999AAAAA : 0xFFDDDDDD, false);
        gui.blit(connected == null ? UNCONNECTED : CONNECTED, x + 133, y + 37, 12, 12,
                0, 0, 24, 24, 24, 24);

        int maxScroll = Math.max(0, wirelessTargets.size() - 7);
        wirelessScroll = Mth.clamp(wirelessScroll, 0, maxScroll);
        int visible = Math.min(7, wirelessTargets.size() - wirelessScroll);
        for (int row = 0; row < visible; row++) {
            int index = wirelessScroll + row;
            WirelessTarget target = wirelessTargets.get(index);
            int rowY = y + 61 + row * 16;
            gui.setColor(1, 1, 1, selectedWireless == index ? .9f : .62f);
            gui.blit(ELEMENT, x + 8, rowY, 150, 16, 0, 0, 300, 32, 300, 32);
            gui.setColor(1, 1, 1, 1);
            gui.blit(typeIcon, x + 16, rowY + 2, 12, 12, 0, 0, 24, 24, 24, 24);
            gui.drawString(font, fit(target.name, target.encrypted ? 36 : 92), x + 30, rowY + 4,
                    selectedWireless == index ? 0xFFF2F2F2 : 0xFFDDDDDD, false);
            if (target.encrypted) gui.blit(KEY, x + 68, rowY + 2, 12, 12, 0, 0, 24, 24, 24, 24);
            gui.blit(UNCONNECTED, x + 133, rowY + 2, 12, 12, 0, 0, 24, 24, 24, 24);
        }
        tintIcon(gui, ARROW_UP, x + 154, y + 52, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 154, y + 52, 16, 16));
        tintIcon(gui, ARROW_DOWN, x + 154, y + 161, 16, 16, 32, 32,
                inside(mouseX, mouseY, x + 154, y + 161, 16, 16));

        if (wirelessPassword != null && selectedWireless >= 0
                && selectedWireless >= wirelessScroll && selectedWireless < wirelessScroll + 7) {
            int row = selectedWireless - wirelessScroll;
            wirelessPassword.setX(x + 81);
            wirelessPassword.setY(y + 64 + row * 16);
            wirelessPassword.visible = true;
        } else if (wirelessPassword != null) wirelessPassword.visible = false;
    }

    private void renderPageTabs(GuiGraphics gui, int mouseX, int mouseY) {
        tintIcon(gui, TAB_INVENTORY, leftPos - 18, topPos, 17, 17, 48, 48,
                page == Page.INVENTORY || inside(mouseX, mouseY, leftPos - 18, topPos, 17, 17));
        if (hasWirelessPage()) tintIcon(gui, TAB_WIRELESS, leftPos - 18, topPos + 22, 17, 17, 48, 48,
                page == Page.WIRELESS || inside(mouseX, mouseY, leftPos - 18, topPos + 22, 17, 17));
    }

    private void renderInfoPanel(GuiGraphics gui) {
        if (machine() == null) return;
        int x = infoX(), y = topPos + 5;
        int height = kind() == MachineKind.MATRIX || isNode() ? 160 : 122;
        gui.fill(x - 4, y - 4, x + 104, y + height + 4, 0x8A081015);
        gui.fill(x - 3, y - 8, x + 103, y - 5, 0xBCE9F8FC);
        gui.fill(x - 3, y + height + 3, x + 103, y + height + 6, 0xBCE9F8FC);
        gui.blit(HISTOGRAM, x + 8, y + 2, 84, 84, 0, 0, 210, 210, 210, 210);
        int energyHeight = menu.maxEnergy() <= 0 ? 0 : Math.round(48f * menu.energy() / menu.maxEnergy());
        gui.fill(x + 22, y + 79 - energyHeight, x + 28, y + 79, 0xFF25C4FF);
        int secondaryHeight = hasFluidTank() ? Math.round(48f * menu.phaseLiquid() / 8000f)
                : kind() == MachineKind.MATRIX || isNode() ? Math.round(48f * menu.wirelessLoad()
                / Math.max(1f, menu.wirelessCapacity())) : energyHeight;
        gui.fill(x + 62, y + 79 - secondaryHeight, x + 68, y + 79,
                hasFluidTank() ? 0xFF7680DE : kind() == MachineKind.MATRIX || isNode() ? 0xFFFFC247 : 0xFFFF6C00);
        gui.drawString(font, Component.translatable("ac.gui.common.hist.energy"), x + 5, y + 88, 0xFFE2EEF1, false);
        gui.drawString(font, menu.energy() + " IF", x + 48, y + 88, 0xFFE2EEF1, false);
        if (hasFluidTank()) {
            gui.drawString(font, Component.translatable("ac.gui.common.hist.liquid"), x + 5, y + 99, 0xFFE2EEF1, false);
            gui.drawString(font, menu.phaseLiquid() + " mB", x + 48, y + 99, 0xFFE2EEF1, false);
        } else if (kind() == MachineKind.MATRIX || isNode()) {
            gui.drawString(font, Component.translatable("ac.gui.common.hist.capacity"), x + 5, y + 99, 0xFFE2EEF1, false);
            gui.drawString(font, menu.wirelessLoad() + "/" + menu.wirelessCapacity(), x + 48, y + 99, 0xFFE2EEF1, false);
        }
        int row = hasFluidTank() || kind() == MachineKind.MATRIX || isNode() ? y + 111 : y + 101;
        if (kind() == MachineKind.MATRIX || isNode()) {
            gui.drawString(font, Component.translatable("ac.gui.common.prop.owner"), x + 5, row, 0xFFB8C7CC, false);
            gui.drawString(font, fit(machine().placerName(), 48), x + 48, row, 0xFFFFFFFF, false);
            row += 12;
            gui.drawString(font, Component.translatable("ac.gui.common.prop.range"), x + 5, row, 0xFFB8C7CC, false);
            gui.drawString(font, String.format("%.0f", machine().wirelessRange()), x + 48, row, 0xFFFFFFFF, false);
            row += 12;
            Component key = Component.translatable(kind() == MachineKind.MATRIX
                    ? "ac.gui.common.prop.ssid" : "ac.gui.common.prop.node_name");
            gui.drawString(font, key, x + 5, row, 0xFFB8C7CC, false);
            gui.drawString(font, "[", x + 39, row, 0xFF76DFF6, false);
            gui.drawString(font, "]", x + 94, row, 0xFF76DFF6, false);
            row += 13;
            gui.drawString(font, Component.translatable("ac.gui.common.prop.password"), x + 5, row, 0xFFB8C7CC, false);
            gui.drawString(font, "[", x + 39, row, 0xFF76DFF6, false);
            gui.drawString(font, "]", x + 94, row, 0xFF76DFF6, false);
            gui.blit(texture("guis/check.png".replace("guis/", "")), x + 87, row + 12, 10, 10,
                    0, 0, 64, 64, 64, 64);
        } else {
            gui.drawString(font, Component.translatable("ac.gui.common.prop.owner"), x + 5, row, 0xFFB8C7CC, false);
            gui.drawString(font, fit(machine().placerName(), 48), x + 48, row, 0xFFFFFFFF, false);
            if (kind().isGenerator()) {
                row += 11;
                gui.drawString(font, Component.translatable("ac.gui.common.prop.gen_speed"), x + 5, row, 0xFFB8C7CC, false);
                gui.drawString(font, kind().generation() + " IF/T", x + 48, row, 0xFFFFFFFF, false);
            }
        }
    }

    private boolean hasFluidTank() {
        return kind() == MachineKind.IMAG_FUSOR || kind() == MachineKind.PHASE_GENERATOR;
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // TechUI used texture labels and its floating information widget rather than vanilla captions.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(mouseX, mouseY, leftPos - 20, topPos - 2, 21, 21)) {
                page = Page.INVENTORY; updatePageState(); return true;
            }
            if (hasWirelessPage() && inside(mouseX, mouseY, leftPos - 20, topPos + 20, 21, 21)) {
                page = Page.WIRELESS; selectedWireless = -1; updatePageState(); return true;
            }
            if (page == Page.INVENTORY && kind() == MachineKind.METAL_FORMER) {
                if (inside(mouseX, mouseY, leftPos + 58, topPos + 7, 20, 20)) {
                    cycleMode(false); return true;
                }
                if (inside(mouseX, mouseY, leftPos + 98, topPos + 7, 20, 20)) {
                    cycleMode(true); return true;
                }
            }
            if (configPassword != null && inside(mouseX, mouseY, configPassword.getX(), configPassword.getY(),
                    configPassword.getWidth(), configPassword.getHeight())) configPasswordDirty = true;
            if (configName != null && inside(mouseX, mouseY, infoX() + 83, topPos + 160, 18, 18)) {
                applyMachineConfig(); return true;
            }
            if (page == Page.WIRELESS && handleWirelessClick(mouseX, mouseY)) return true;
            if (page == Page.INVENTORY && kind() == MachineKind.ABILITY_INTERFERER
                    && handleInterfererClick(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void cycleMode(boolean forward) {
        PacketDistributor.sendToServer(new MachineActionPayload(machine().getBlockPos(), forward
                ? MachineActionPayload.CYCLE_MODE : MachineActionPayload.CYCLE_MODE_BACK));
    }

    private boolean handleWirelessClick(double mouseX, double mouseY) {
        int x = leftPos, y = topPos;
        WirelessTarget connected = connectedTarget();
        if (connected != null && inside(mouseX, mouseY, x + 125, y + 33, 28, 20)) {
            PacketDistributor.sendToServer(new WirelessConfigPayload(machine().getBlockPos(), "", ""));
            return true;
        }
        if (inside(mouseX, mouseY, x + 150, y + 49, 22, 22)) {
            wirelessScroll = Math.max(0, wirelessScroll - 1); return true;
        }
        if (inside(mouseX, mouseY, x + 150, y + 158, 22, 22)) {
            wirelessScroll = Math.min(Math.max(0, wirelessTargets.size() - 7), wirelessScroll + 1); return true;
        }
        for (int row = 0; row < 7 && wirelessScroll + row < wirelessTargets.size(); row++) {
            int index = wirelessScroll + row;
            if (inside(mouseX, mouseY, x + 8, y + 61 + row * 16, 150, 16)) {
                selectedWireless = index;
                wirelessPassword.setValue("");
                wirelessPassword.visible = true;
                wirelessPassword.setFocused(true);
                if (mouseX >= x + 126) connectSelectedWireless();
                return true;
            }
        }
        return false;
    }

    private void connectSelectedWireless() {
        if (selectedWireless < 0 || selectedWireless >= wirelessTargets.size()) return;
        WirelessTarget target = wirelessTargets.get(selectedWireless);
        PacketDistributor.sendToServer(new WirelessConfigPayload(machine().getBlockPos(), target.network,
                wirelessPassword == null ? "" : wirelessPassword.getValue()));
        if (wirelessPassword != null) wirelessPassword.setValue("");
    }

    private boolean handleInterfererClick(double mouseX, double mouseY) {
        int x = leftPos, y = topPos;
        if (inside(mouseX, mouseY, x + 46, y + 23, 20, 20)) {
            interfererEnabled = !interfererEnabled; applyInterferer(); return true;
        }
        if (inside(mouseX, mouseY, x + 46, y + 39, 20, 20)) {
            interfererRange = Math.max(10, interfererRange - 10); applyInterferer(); return true;
        }
        if (inside(mouseX, mouseY, x + 106, y + 39, 20, 20)) {
            interfererRange = Math.min(100, interfererRange + 10); applyInterferer(); return true;
        }
        if (inside(mouseX, mouseY, x + 10, y + 80, 16, 16)) {
            whitelistInput.visible = true; whitelistInput.setFocused(true); return true;
        }
        if (inside(mouseX, mouseY, x + 30, y + 80, 16, 16)) {
            if (selectedWhitelist >= 0 && selectedWhitelist < interfererWhitelist.size()) {
                interfererWhitelist.remove(selectedWhitelist); selectedWhitelist = -1; applyInterferer();
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 150, y + 93, 20, 20)) {
            whitelistScroll = Math.max(0, whitelistScroll - 1); return true;
        }
        if (inside(mouseX, mouseY, x + 150, y + 143, 20, 20)) {
            whitelistScroll = Math.min(Math.max(0, interfererWhitelist.size() - 4), whitelistScroll + 1); return true;
        }
        for (int row = 0; row < 4 && whitelistScroll + row < interfererWhitelist.size(); row++) {
            if (inside(mouseX, mouseY, x + 8, y + 95 + row * 16, 140, 16)) {
                selectedWhitelist = whitelistScroll + row; return true;
            }
        }
        return false;
    }

    private void addWhitelistEntry() {
        if (whitelistInput == null) return;
        String name = whitelistInput.getValue().strip();
        if (!name.isEmpty() && !interfererWhitelist.contains(name)) interfererWhitelist.add(name);
        whitelistInput.setValue(""); whitelistInput.visible = false; applyInterferer();
    }

    private void applyInterferer() {
        PacketDistributor.sendToServer(new InterfererConfigPayload(machine().getBlockPos(),
                interfererEnabled, interfererRange, List.copyOf(interfererWhitelist)));
    }

    private void applyMachineConfig() {
        if (configName == null || configPassword == null) return;
        if (kind() == MachineKind.MATRIX)
            PacketDistributor.sendToServer(new MatrixConfigPayload(machine().getBlockPos(),
                    configName.getValue(), configPassword.getValue(), configPasswordDirty));
        else PacketDistributor.sendToServer(new NodeConfigPayload(machine().getBlockPos(),
                configName.getValue(), configPassword.getValue(), configPasswordDirty));
        configPasswordDirty = false;
        configPassword.setValue("");
        configPasswordDirty = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (whitelistInput != null && whitelistInput.visible && whitelistInput.isFocused()) {
                addWhitelistEntry(); return true;
            }
            if (wirelessPassword != null && wirelessPassword.visible && wirelessPassword.isFocused()) {
                connectSelectedWireless(); return true;
            }
            if (configName != null && (configName.isFocused() || configPassword.isFocused())) {
                applyMachineConfig(); return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void refreshWirelessTargets() {
        wirelessTargets.clear();
        if (minecraft == null || minecraft.level == null || machine() == null) return;
        int centerChunkX = machine().getBlockPos().getX() >> 4;
        int centerChunkZ = machine().getBlockPos().getZ() >> 4;
        for (int cx = centerChunkX - 3; cx <= centerChunkX + 3; cx++) {
            for (int cz = centerChunkZ - 3; cz <= centerChunkZ + 3; cz++) {
                var chunk = minecraft.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk levelChunk)) continue;
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ACMachineBlockEntity target) || target == machine()) continue;
                    if (isNode()) {
                        if (target.kind() != MachineKind.MATRIX || target.networkId().isEmpty()
                                || target.getBlockPos().distSqr(machine().getBlockPos()) > target.wirelessRange() * target.wirelessRange()) continue;
                        wirelessTargets.add(new WirelessTarget(target.getBlockPos(), target.networkId(),
                                target.networkId(), target.networkEncrypted()));
                    } else {
                        if (!target.kind().isNetworkNode() || target.kind() == MachineKind.MATRIX
                                || target.networkId().isEmpty()
                                || target.getBlockPos().distSqr(machine().getBlockPos()) > target.wirelessRange() * target.wirelessRange()) continue;
                        wirelessTargets.add(new WirelessTarget(target.getBlockPos(), target.networkId(),
                                target.nodeName(), target.nodeEncrypted()));
                    }
                }
            }
        }
        BlockPos connectedPos = isNode() ? machine().linkedMatrixPos() : machine().linkedNodePos();
        if (connectedPos != null) wirelessTargets.removeIf(target -> target.pos.equals(connectedPos));
        wirelessTargets.sort(Comparator.comparingDouble(target -> target.pos.distSqr(machine().getBlockPos())));
        selectedWireless = Mth.clamp(selectedWireless, -1, wirelessTargets.size() - 1);
        wirelessScroll = Mth.clamp(wirelessScroll, 0, Math.max(0, wirelessTargets.size() - 7));
    }

    private WirelessTarget connectedTarget() {
        if (machine() == null || minecraft == null || minecraft.level == null) return null;
        BlockPos linked = isNode() ? machine().linkedMatrixPos() : machine().linkedNodePos();
        if (linked == null) return null;
        BlockEntity value = minecraft.level.getBlockEntity(linked);
        if (value instanceof ACMachineBlockEntity target) {
            return new WirelessTarget(linked, target.networkId(), isNode() ? target.networkId() : target.nodeName(),
                    isNode() ? target.networkEncrypted() : target.nodeEncrypted());
        }
        return new WirelessTarget(linked, machine().networkId(), machine().networkId(), false);
    }

    private List<WirelessTarget> allNearbyConnections() {
        List<WirelessTarget> result = new ArrayList<>(wirelessTargets);
        if (minecraft == null || minecraft.level == null || machine() == null) return result;
        int cx0 = machine().getBlockPos().getX() >> 4, cz0 = machine().getBlockPos().getZ() >> 4;
        for (int cx = cx0 - 2; cx <= cx0 + 2; cx++) for (int cz = cz0 - 2; cz <= cz0 + 2; cz++) {
            var chunk = minecraft.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (!(chunk instanceof LevelChunk levelChunk)) continue;
            for (BlockEntity value : levelChunk.getBlockEntities().values()) {
                if (!(value instanceof ACMachineBlockEntity target) || target == machine()) continue;
                if (isNode() && target.kind() == MachineKind.MATRIX)
                    result.add(new WirelessTarget(target.getBlockPos(), target.networkId(), target.networkId(), target.networkEncrypted()));
                else if (!isNode() && target.kind().isNetworkNode() && target.kind() != MachineKind.MATRIX)
                    result.add(new WirelessTarget(target.getBlockPos(), target.networkId(), target.nodeName(), target.nodeEncrypted()));
            }
        }
        return result;
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderTooltip(gui, mouseX, mouseY);
        if (page == Page.INVENTORY && kind() == MachineKind.METAL_FORMER
                && inside(mouseX, mouseY, leftPos + 74, topPos + 3, 28, 29))
            gui.renderTooltip(font, Component.translatable("ac.machine.metal_former.mode." + menu.mode()), mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }

    private static String modeName(int mode) {
        return switch (Math.floorMod(mode, 4)) { case 0 -> "plate"; case 1 -> "incise"; case 2 -> "etch"; default -> "refine"; };
    }

    private void drawCentered(GuiGraphics gui, String text, int x, int y, int width, int color) {
        gui.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(1, width - font.width("…"))) + "…";
    }

    private static void tintIcon(GuiGraphics gui, ResourceLocation texture, int x, int y, int width, int height,
                                 int sourceWidth, int sourceHeight, boolean bright) {
        gui.setColor(bright ? 1 : .65f, bright ? 1 : .65f, bright ? 1 : .65f, bright ? 1 : .72f);
        gui.blit(texture, x, y, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        gui.setColor(1, 1, 1, 1);
    }

    private static boolean inside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private enum Page { INVENTORY, WIRELESS }
    private record WirelessTarget(net.minecraft.core.BlockPos pos, String network, String name, boolean encrypted) {}
}
