package cn.academy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Tooltip-preserving port of ItemMagneticCoil. */
public final class MagneticCoilItem extends Item {
    public MagneticCoilItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.coil.tooltip.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ac.coil.tooltip.1").withStyle(ChatFormatting.GRAY));
    }
}
