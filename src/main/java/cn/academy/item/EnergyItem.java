package cn.academy.item;

import cn.academy.registry.ACDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import cn.academy.registry.ACItems;

public final class EnergyItem extends Item {
    private final int capacity;

    public EnergyItem(Properties properties, int capacity) {
        this(properties, capacity, 1);
    }

    public EnergyItem(Properties properties, int capacity, int emptyStackSize) {
        super(properties.stacksTo(emptyStackSize).component(ACDataComponents.ENERGY.get(), 0));
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!stack.is(ACItems.ENERGY_UNIT.get())) return;
        int energy = stack.getOrDefault(ACDataComponents.ENERGY.get(), 0);
        // Empty Energy Units retain the legacy 2/4-item recipe yields. As soon as one is charged,
        // separate the remaining units as empty stacks so component energy can never duplicate on split.
        if (!level.isClientSide && energy > 0 && stack.getCount() > 1 && entity instanceof Player player) {
            int remainder = stack.getCount() - 1;
            stack.setCount(1);
            ItemStack empty = new ItemStack(ACItems.ENERGY_UNIT.get(), remainder);
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }
    }

    /**
     * Reproduces ItemEnergyBase's 1.12.2 damage-to-icon thresholds. The legacy item first mapped
     * energy onto damage 0..13, then used damage &lt; 3 for full, damage &gt; 10 for empty and the
     * remaining values for half. Keeping that quantisation matters near the two boundaries.
     */
    public static int legacyModelStage(int energy, int capacity) {
        int damage = Math.round((1 - Math.max(0, Math.min(energy, capacity)) / (float) Math.max(1, capacity)) * 13);
        return damage < 3 ? 2 : damage > 10 ? 0 : 1;
    }

    public static float legacyModelValue(ItemStack stack) {
        if (!(stack.getItem() instanceof EnergyItem item)) return 0;
        return legacyModelStage(stack.getOrDefault(ACDataComponents.ENERGY.get(), 0), item.capacity) * .5f;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int stored = stack.getOrDefault(ACDataComponents.ENERGY.get(), 0);
        tooltip.add(Component.translatable("ac.item.energy", stored, capacity).withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getOrDefault(ACDataComponents.ENERGY.get(), 0) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * stack.getOrDefault(ACDataComponents.ENERGY.get(), 0) / Math.max(1, capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x35A8FF;
    }
}
