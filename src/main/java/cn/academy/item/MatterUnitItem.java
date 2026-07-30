package cn.academy.item;

import cn.academy.registry.ACDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class MatterUnitItem extends Item {
    public MatterUnitItem(Properties properties) {
        super(properties.component(ACDataComponents.PHASE_FILLED.get(), false));
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.getOrDefault(ACDataComponents.PHASE_FILLED.get(), false);
    }

    public static void setFilled(ItemStack stack, boolean filled) {
        stack.set(ACDataComponents.PHASE_FILLED.get(), filled);
        if (filled) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        else stack.remove(DataComponents.CUSTOM_MODEL_DATA);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(isFilled(stack) ? "item.academy.matter_unit.filled" : "item.academy.matter_unit.empty")
                .withStyle(isFilled(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
