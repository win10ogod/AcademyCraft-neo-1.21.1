package cn.academy.registry;

import cn.academy.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import cn.academy.ability.AbilityRegistry;
import cn.academy.item.InductionFactorItem;
import cn.academy.item.MediaItem;
import cn.academy.item.MatrixCoreItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AcademyCraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.academy"))
                    .icon(() -> new ItemStack(ACItems.LOGO.get()))
                    .displayItems((parameters, output) -> {
                        ACItems.ALL.forEach((name, holder) -> {
                            if (!name.equals("logo") && !name.equals("induction_factor") && !name.equals("media_item")
                                    && !name.equals("mat_core") && !name.equals("energy_unit")
                                    && !name.equals("developer_portable")) output.accept(holder.get());
                        });
                        for (var item : java.util.List.of(ACItems.ENERGY_UNIT.get(), ACItems.DEVELOPER_PORTABLE.get())) {
                            output.accept(item);
                            ItemStack full = new ItemStack(item);
                            full.set(ACDataComponents.ENERGY.get(), 10_000);
                            if (item == ACItems.ENERGY_UNIT.get()) full.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                                    new net.minecraft.world.item.component.CustomModelData(2));
                            output.accept(full);
                        }
                        AbilityRegistry.categoryIds().forEach(category -> output.accept(
                                InductionFactorItem.forCategory(ACItems.INDUCTION_FACTOR.get(), category)));
                        MediaItem.TRACKS.forEach(track -> output.accept(MediaItem.of(ACItems.MEDIA_ITEM.get(), track)));
                        for (int level = 1; level <= 3; level++) output.accept(MatrixCoreItem.of(ACItems.MAT_CORE.get(), level));
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private ACCreativeTabs() {}
}
