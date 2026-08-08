package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.block.ACMachineBlock;
import cn.academy.block.ACMultiblockPartBlock;
import cn.academy.block.ACImagPhaseBlock;
import cn.academy.block.MachineKind;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ACBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AcademyCraft.MOD_ID);
    public static final Map<String, DeferredBlock<? extends Block>> ALL = new LinkedHashMap<>();

    public static final DeferredBlock<Block> CONSTRAINT_METAL = ore("constraint_metal", 4.0f, 1, 3);
    public static final DeferredBlock<Block> CRYSTAL_ORE = ore("crystal_ore", 3.0f, 2, 5);
    public static final DeferredBlock<Block> IMAGSIL_ORE = ore("imagsil_ore", 3.75f, 2, 4);
    public static final DeferredBlock<Block> RESO_ORE = ore("reso_ore", 3.0f, 2, 5);
    public static final DeferredBlock<ACImagPhaseBlock> IMAG_PHASE = add("imag_phase", () -> new ACImagPhaseBlock(
            ACFluids.IMAG_PHASE.get(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .replaceable().noCollission().strength(100.0f).pushReaction(PushReaction.DESTROY)
                    .liquid().noLootTable().lightLevel(state -> 6).sound(SoundType.EMPTY)));

    public static final DeferredBlock<ACMultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.register("multiblock_part", () ->
            new ACMultiblockPartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(4, 8)
                    .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<Block> MACHINE_FRAME = add("machine_frame", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                    .strength(4.0f, 8.0f).sound(SoundType.METAL)));
    public static final DeferredBlock<ACMachineBlock> NODE_BASIC = machine("node_basic", MachineKind.NODE_BASIC, 3.0f);
    public static final DeferredBlock<ACMachineBlock> NODE_STANDARD = machine("node_standard", MachineKind.NODE_STANDARD, 3.5f);
    public static final DeferredBlock<ACMachineBlock> NODE_ADVANCED = machine("node_advanced", MachineKind.NODE_ADVANCED, 4.0f);
    public static final DeferredBlock<ACMachineBlock> MATRIX = machine("matrix", MachineKind.MATRIX, 4.0f);
    public static final DeferredBlock<ACMachineBlock> CAT_ENGINE = machine("cat_engine", MachineKind.CAT_ENGINE, 3.0f);
    public static final DeferredBlock<ACMachineBlock> SOLAR_GEN = machine("solar_gen", MachineKind.SOLAR_GENERATOR, 3.0f);
    public static final DeferredBlock<ACMachineBlock> PHASE_GEN = machine("phase_gen", MachineKind.PHASE_GENERATOR, 3.0f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_BASE = machine("windgen_base", MachineKind.WIND_BASE, 4.0f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_PILLAR = machine("windgen_pillar", MachineKind.WIND_PILLAR, 4.0f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_MAIN = machine("windgen_main", MachineKind.WIND_GENERATOR, 4.0f);
    public static final DeferredBlock<ACMachineBlock> IMAG_FUSOR = machine("imag_fusor", MachineKind.IMAG_FUSOR, 4.0f);
    public static final DeferredBlock<ACMachineBlock> METAL_FORMER = machine("metal_former", MachineKind.METAL_FORMER, 4.0f);
    public static final DeferredBlock<ACMachineBlock> DEV_NORMAL = machine("dev_normal", MachineKind.DEVELOPER_NORMAL, 4.0f);
    public static final DeferredBlock<ACMachineBlock> DEV_ADVANCED = machine("dev_advanced", MachineKind.DEVELOPER_ADVANCED, 5.0f);
    public static final DeferredBlock<ACMachineBlock> ABILITY_INTERFERER = machine("ability_interferer", MachineKind.ABILITY_INTERFERER, 5.0f);
    public static final DeferredBlock<ACMachineBlock> RF_INPUT = machine("ac_rf_input", MachineKind.RF_INPUT, 4.0f);
    public static final DeferredBlock<ACMachineBlock> RF_OUTPUT = machine("ac_rf_output", MachineKind.RF_OUTPUT, 4.0f);
    public static final DeferredBlock<ACMachineBlock> EU_INPUT = machine("eu_input", MachineKind.EU_INPUT, 4.0f);
    public static final DeferredBlock<ACMachineBlock> EU_OUTPUT = machine("eu_output", MachineKind.EU_OUTPUT, 4.0f);

    private static DeferredBlock<Block> ore(String name, float hardness, int minXp, int maxXp) {
        return add(name, () -> new DropExperienceBlock(UniformInt.of(minXp, maxXp),
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
                        .strength(hardness, 6.0f).sound(SoundType.STONE)));
    }

    private static DeferredBlock<ACMachineBlock> machine(String name, MachineKind kind, float hardness) {
        return add(name, () -> {
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops().strength(hardness, 8.0f).sound(SoundType.METAL)
                    .lightLevel(state -> kind == MachineKind.MATRIX ? 15 : 0)
                    .pushReaction(PushReaction.BLOCK);
            // BlockMulti and the four standalone TESR blocks all returned isOpaqueCube=false in 1.12.2.
            // Keeping the default full-cube occlusion shape makes the block entity sample darkness at its
            // own position and turns the original OBJ textures almost black.
            if (ACMachineBlock.usesLegacyBlockEntityModel(kind)) properties.noOcclusion();
            return new ACMachineBlock(kind, properties);
        });
    }

    private static <T extends Block> DeferredBlock<T> add(String name, Supplier<T> supplier) {
        DeferredBlock<T> value = BLOCKS.register(name, supplier);
        ALL.put(name, value);
        return value;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private ACBlocks() {}
}
