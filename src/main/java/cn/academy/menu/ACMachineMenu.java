package cn.academy.menu;

import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.registry.ACMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ACMachineMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 5;
    private final ACMachineBlockEntity machine;
    private final ContainerData data;
    private boolean inventoryPage = true;

    public ACMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory,
                inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof ACMachineBlockEntity found ? found : null);
    }

    public ACMachineMenu(int containerId, Inventory inventory, ACMachineBlockEntity machine) {
        super(ACMenus.MACHINE.get(), containerId);
        this.machine = machine;
        ItemStackHandler handler = machine == null ? new ItemStackHandler(MACHINE_SLOTS) : machine.items;
        this.data = machine == null ? new SimpleContainerData(10) : createData(machine);

        int[][] layout = machineLayout(machine == null ? null : machine.kind());
        for (int index = 0; index < MACHINE_SLOTS; index++) {
            final int slotIndex = index;
            addSlot(new SlotItemHandler(handler, index, layout[index][0], layout[index][1]) {
                @Override public boolean isActive() { return inventoryPage && layout[slotIndex][0] >= 0; }
                @Override public boolean mayPlace(ItemStack stack) {
                    if (slotIndex == 2) return machine != null && machine.kind() == cn.academy.block.MachineKind.MATRIX
                            && super.mayPlace(stack);
                    if (slotIndex == 4) return false;
                    return super.mayPlace(stack);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 6 + column * 18, 105 + row * 18) {
                    @Override public boolean isActive() { return inventoryPage; }
                });
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 6 + column * 18, 163) {
                @Override public boolean isActive() { return inventoryPage; }
            });
        }
        addDataSlots(data);
    }

    private static int[][] machineLayout(cn.academy.block.MachineKind kind) {
        int[][] hidden = {{-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
        if (kind == null) return hidden;
        return switch (kind) {
            case IMAG_FUSOR -> new int[][]{{13, 49}, {42, 80}, {143, 49}, {13, 10}, {143, 10}};
            case METAL_FORMER -> new int[][]{{13, 49}, {42, 80}, {143, 49}, {-100, -100}, {-100, -100}};
            case MATRIX -> new int[][]{{78, 11}, {53, 60}, {104, 60}, {78, 36}, {-100, -100}};
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED ->
                    new int[][]{{42, 10}, {42, 80}, {-100, -100}, {-100, -100}, {-100, -100}};
            case PHASE_GENERATOR ->
                    new int[][]{{42, 80}, {-100, -100}, {-100, -100}, {45, 12}, {112, 51}};
            case SOLAR_GENERATOR, WIND_BASE ->
                    new int[][]{{42, 80}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
            case WIND_GENERATOR ->
                    new int[][]{{78, 9}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
            case ABILITY_INTERFERER, DEVELOPER_NORMAL, DEVELOPER_ADVANCED ->
                    new int[][]{{-100, -100}, {139, 25}, {-100, -100}, {-100, -100}, {-100, -100}};
            case RF_INPUT, RF_OUTPUT, EU_INPUT, EU_OUTPUT ->
                    new int[][]{{42, 10}, {42, 80}, {-100, -100}, {-100, -100}, {-100, -100}};
            case CAT_ENGINE, WIND_PILLAR -> hidden;
        };
    }

    private static ContainerData createData(ACMachineBlockEntity machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> machine.energy.getEnergyStored() & 0xFFFF;
                    case 1 -> machine.energy.getEnergyStored() >>> 16;
                    case 2 -> machine.energy.getMaxEnergyStored() & 0xFFFF;
                    case 3 -> machine.energy.getMaxEnergyStored() >>> 16;
                    case 4 -> machine.progress();
                    case 5 -> machine.maxProgress();
                    case 6 -> machine.mode();
                    case 7 -> machine.phaseLiquid();
                    case 8 -> machine.wirelessLoad();
                    case 9 -> machine.wirelessCapacity();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 10; }
        };
    }

    public ACMachineBlockEntity machine() { return machine; }
    public int energy() { return (data.get(1) << 16) | (data.get(0) & 0xFFFF); }
    public int maxEnergy() { return (data.get(3) << 16) | (data.get(2) & 0xFFFF); }
    public int progress() { return data.get(4); }
    public int maxProgress() { return data.get(5); }
    public int mode() { return data.get(6); }
    public int phaseLiquid() { return data.get(7); }
    public int wirelessLoad() { return data.get(8); }
    public int wirelessCapacity() { return data.get(9); }
    public void setInventoryPage(boolean inventoryPage) { this.inventoryPage = inventoryPage; }
    public boolean inventoryPage() { return inventoryPage; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(source, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, MACHINE_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && !machine.isRemoved()
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
}
