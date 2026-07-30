package cn.academy.item;

import cn.academy.registry.ACDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public final class MediaItem extends Item {
    public static final List<String> TRACKS = List.of("sisters_noise", "only_my_railgun", "level5_judgelight");

    public MediaItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(Item item, String media) {
        ItemStack stack = new ItemStack(item);
        stack.set(ACDataComponents.MEDIA_ID.get(), media);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.max(0, TRACKS.indexOf(media))));
        return stack;
    }

    public static String media(ItemStack stack) {
        String id = stack.getOrDefault(ACDataComponents.MEDIA_ID.get(), TRACKS.getFirst());
        return TRACKS.contains(id) ? id : TRACKS.getFirst();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ac.media." + media(stack) + ".name");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.media." + media(stack) + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
