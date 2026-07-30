package cn.academy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Keeps ItemApp's shared item name while exposing the installed app name in its tooltip. */
public final class AppInstallerItem extends Item {
    private final String app;

    public AppInstallerItem(Properties properties, String app) {
        super(properties);
        this.app = app;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.app." + app + ".name").withStyle(ChatFormatting.AQUA));
    }
}
