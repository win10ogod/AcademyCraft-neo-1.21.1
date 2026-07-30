package cn.academy.energy;

import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class ItemEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final int capacity;
    private final int maxTransfer;

    public ItemEnergyStorage(ItemStack stack, int capacity, int maxTransfer) {
        this.stack = stack;
        this.capacity = capacity;
        this.maxTransfer = maxTransfer;
    }

    private int stored() {
        return Math.max(0, Math.min(capacity, stack.getOrDefault(ACDataComponents.ENERGY.get(), 0)));
    }

    private void stored(int value) {
        int clamped = Math.max(0, Math.min(capacity, value));
        stack.set(ACDataComponents.ENERGY.get(), clamped);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (stack.is(ACItems.ENERGY_UNIT.get())) {
            int stage = clamped <= 0 ? 0 : clamped >= capacity ? 2 : 1;
            if (stage == 0) stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            else stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(stage));
        }
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (stack.getCount() != 1) return 0;
        int accepted = Math.min(Math.max(0, amount), Math.min(maxTransfer, capacity - stored()));
        if (!simulate && accepted > 0) stored(stored() + accepted);
        return accepted;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (stack.getCount() != 1) return 0;
        int extracted = Math.min(Math.max(0, amount), Math.min(maxTransfer, stored()));
        if (!simulate && extracted > 0) stored(stored() - extracted);
        return extracted;
    }

    @Override public int getEnergyStored() { return stored(); }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return maxTransfer > 0 && stack.getCount() == 1; }
    @Override public boolean canReceive() { return maxTransfer > 0 && stack.getCount() == 1; }
}
