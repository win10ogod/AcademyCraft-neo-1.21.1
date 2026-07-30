package cn.academy.registry;

import cn.academy.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ACFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, AcademyCraft.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, AcademyCraft.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> IMAG_PHASE_TYPE = FLUID_TYPES.register("imag_phase", () ->
            new FluidType(FluidType.Properties.create()
                    .descriptionId("block.academy.imag_phase")
                    .density(1350).viscosity(1800).temperature(295).lightLevel(6)
                    .canSwim(true).canDrown(false).canPushEntity(true).supportsBoating(false)));

    public static final DeferredHolder<Fluid, FlowingFluid> IMAG_PHASE = FLUIDS.register("imag_phase", () ->
            new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_IMAG_PHASE = FLUIDS.register("flowing_imag_phase", () ->
            new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(IMAG_PHASE_TYPE, IMAG_PHASE, FLOWING_IMAG_PHASE)
                .block(ACBlocks.IMAG_PHASE)
                .slopeFindDistance(3).levelDecreasePerBlock(2).tickRate(12).explosionResistance(100);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private ACFluids() {}
}
