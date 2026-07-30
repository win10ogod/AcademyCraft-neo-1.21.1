package cn.academy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Modern data-component representation of the original metadata 0..2 matrix cores. */
public final class MatrixCoreItem extends Item {
    public MatrixCoreItem(Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0)));
    }

    public static int level(ItemStack stack) {
        return Math.max(1, Math.min(3, stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value() + 1));
    }

    public static ItemStack of(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.max(0, Math.min(2, level - 1))));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.ac_mat_core_" + (level(stack) - 1) + ".name");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.matrix_core.level", level(stack)).withStyle(ChatFormatting.AQUA));
    }
}
