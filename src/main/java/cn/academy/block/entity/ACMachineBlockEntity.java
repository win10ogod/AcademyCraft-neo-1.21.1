package cn.academy.block.entity;

import cn.academy.block.ACMachineBlock;
import cn.academy.block.MachineKind;
import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACFluids;
import cn.academy.registry.ACSounds;
import cn.academy.menu.ACMachineMenu;
import cn.academy.item.MatrixCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared persistent FE/inventory implementation for AcademyCraft's machines and wireless network. */
public final class ACMachineBlockEntity extends BlockEntity implements MenuProvider {
    private static final Map<ServerLevel, Set<ACMachineBlockEntity>> LOADED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final MachineKind kind;
    public final TrackedEnergyStorage energy;
    public final ItemStackHandler items;
    private int progress;
    private int mode;
    private int phaseLiquid;
    private int ticks;
    private float clientCatRotation;
    private String networkId = "";
    private String networkPasswordHash = "";
    private String networkOwner = "";
    private String nodeName = "Unnamed";
    private String nodePasswordHash = "";
    private boolean networkEncrypted;
    private boolean nodeEncrypted;
    private String nodeOwner = "";
    private boolean hasMatrixLink;
    private long linkedMatrixPos;
    private boolean hasNodeLink;
    private long linkedNodePos;
    private String placerId = "";
    private String placerName = "";
    private boolean interfererEnabled;
    private double interfererRange = 10;
    private final java.util.SortedSet<String> interfererWhitelist = new java.util.TreeSet<>();

    public ACMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.MACHINE.get(), pos, state);
        this.kind = state.getBlock() instanceof ACMachineBlock machine ? machine.kind() : MachineKind.MATRIX;
        this.energy = new TrackedEnergyStorage(kind.capacity(), kind.transfer()) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                int received = super.receiveEnergy(amount, simulate);
                if (!simulate && received > 0) ACMachineBlockEntity.this.changed();
                return received;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                int extracted = super.extractEnergy(amount, simulate);
                if (!simulate && extracted > 0) ACMachineBlockEntity.this.changed();
                return extracted;
            }
        };
        this.items = new ItemStackHandler(5) {
            @Override
            protected void onContentsChanged(int slot) {
                ACMachineBlockEntity.this.changed();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isValidInput(slot, stack);
            }
        };
    }

    public class TrackedEnergyStorage extends EnergyStorage {
        private TrackedEnergyStorage(int capacity, int transfer) {
            super(capacity, transfer, transfer);
        }

        public void setEnergy(int value) {
            this.energy = Math.max(0, Math.min(capacity, value));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) ACMachineBlockEntity.this.changed();
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (!simulate && extracted > 0) ACMachineBlockEntity.this.changed();
            return extracted;
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ACMachineMenu(containerId, inventory, this);
    }

    public IEnergyStorage externalEnergy() {
        boolean receive = !kind.isGenerator() && kind != MachineKind.WIND_BASE && !kind.isBridgeOutput();
        boolean extract = kind.isGenerator() || kind == MachineKind.WIND_BASE || kind.isNetworkNode() || kind.isBridgeOutput();
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                return receive ? energy.receiveEnergy(maxReceive, simulate) : 0;
            }
            @Override public int extractEnergy(int maxExtract, boolean simulate) {
                return extract ? energy.extractEnergy(maxExtract, simulate) : 0;
            }
            @Override public int getEnergyStored() { return energy.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return extract; }
            @Override public boolean canReceive() { return receive; }
        };
    }

    public MachineKind kind() { return kind; }
    public int progress() { return progress; }
    public int maxProgress() { return kind == MachineKind.IMAG_FUSOR ? 120 : kind == MachineKind.METAL_FORMER ? 60 : 0; }
    public int mode() { return mode; }
    public int phaseLiquid() { return phaseLiquid; }
    public float clientCatRotation(float partialTick) {
        float interpolation = energy.getEnergyStored() < energy.getMaxEnergyStored() ? 250 * partialTick : 0;
        return (clientCatRotation + interpolation) % 360;
    }
    public boolean isInterfererActive() { return kind == MachineKind.ABILITY_INTERFERER && interfererEnabled; }
    public double interfererRange() { return interfererRange; }
    public java.util.List<String> interfererWhitelist() { return java.util.List.copyOf(interfererWhitelist); }
    public int configureInterferer(ServerPlayer player, boolean enabled, double range, java.util.List<String> whitelist) {
        if (kind != MachineKind.ABILITY_INTERFERER) return 3;
        if (!placerId.isEmpty() && !placerId.equals(player.getStringUUID()) && !player.hasPermissions(2)) return 1;
        interfererEnabled = enabled;
        interfererRange = Math.max(10, Math.min(100, range));
        interfererWhitelist.clear();
        whitelist.stream().map(String::strip).filter(value -> !value.isEmpty()).limit(64)
                .forEach(value -> interfererWhitelist.add(value.length() > 32 ? value.substring(0, 32) : value));
        if (!placerName.isEmpty()) interfererWhitelist.add(placerName);
        changed();
        sync();
        return 0;
    }
    public static boolean interferesWith(ServerPlayer player) {
        Set<ACMachineBlockEntity> machines = LOADED.get(player.serverLevel());
        if (machines == null || player.isCreative()) return false;
        for (ACMachineBlockEntity machine : Set.copyOf(machines)) {
            if (machine.isRemoved() || !machine.isInterfererActive()
                    || machine.interfererWhitelist.contains(player.getGameProfile().getName())) continue;
            double range = machine.interfererRange;
            BlockPos pos = machine.worldPosition;
            if (Math.abs(player.getX() - (pos.getX() + .5)) <= range
                    && Math.abs(player.getY() - (pos.getY() + .5)) <= range
                    && Math.abs(player.getZ() - (pos.getZ() + .5)) <= range) return true;
        }
        return false;
    }
    public String networkId() { return networkId; }
    public String nodeName() { return nodeName; }
    public String placerName() { return placerName.isEmpty() ? "-" : placerName; }
    public boolean networkEncrypted() { return networkEncrypted; }
    public boolean nodeEncrypted() { return nodeEncrypted; }
    public @Nullable BlockPos linkedMatrixPos() { return hasMatrixLink ? BlockPos.of(linkedMatrixPos) : null; }
    public @Nullable BlockPos linkedNodePos() { return hasNodeLink ? BlockPos.of(linkedNodePos) : null; }
    public int wirelessLoad() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (machines == null) return 0;
        return (int) machines.stream().filter(value -> !value.isRemoved() && (kind == MachineKind.MATRIX
                ? value.hasMatrixLink && value.linkedMatrixPos == worldPosition.asLong()
                : kind.isNetworkNode() && kind != MachineKind.MATRIX
                && value.hasNodeLink && value.linkedNodePos == worldPosition.asLong())).count();
    }
    public int wirelessCapacity() {
        return switch (kind) {
            case MATRIX -> isMatrixWorking() ? 8 * matrixCoreLevel() : 0;
            case NODE_BASIC -> 5;
            case NODE_STANDARD -> 10;
            case NODE_ADVANCED -> 20;
            default -> 0;
        };
    }

    public void setPlacer(LivingEntity placer) {
        if (placer == null || !placerId.isEmpty()) return;
        placerId = placer.getStringUUID();
        placerName = placer.getName().getString();
        if (kind.isNetworkNode() && kind != MachineKind.MATRIX && nodeOwner.isEmpty()) nodeOwner = placerId;
        if (kind == MachineKind.ABILITY_INTERFERER) interfererWhitelist.add(placerName);
        changed();
        sync();
    }

    public int configureNode(ServerPlayer player, String requestedName, String password, boolean updatePassword) {
        if (!kind.isNetworkNode() || kind == MachineKind.MATRIX) return 3;
        if (!nodeOwner.isEmpty() && !nodeOwner.equals(player.getStringUUID()) && !player.hasPermissions(2)) return 1;
        String cleanName = requestedName == null ? "" : requestedName.strip();
        if (cleanName.isEmpty()) cleanName = "Unnamed";
        if (cleanName.length() > 24) cleanName = cleanName.substring(0, 24);
        nodeName = cleanName;
        if (updatePassword) {
            String pass = password == null ? "" : password;
            nodePasswordHash = hashPassword(pass);
            nodeEncrypted = !pass.isEmpty();
        }
        if (nodeOwner.isEmpty()) nodeOwner = player.getStringUUID();
        changed();
        sync();
        return 0;
    }
    public @Nullable IFluidHandler phaseTank() {
        if (kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) {
                return tank == 0 && phaseLiquid > 0 ? new FluidStack(ACFluids.IMAG_PHASE.get(), phaseLiquid) : FluidStack.EMPTY;
            }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? 8_000 : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return tank == 0 && stack.is(ACFluids.IMAG_PHASE.get());
            }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (!isFluidValid(0, resource)) return 0;
                int accepted = Math.min(resource.getAmount(), 8_000 - phaseLiquid);
                if (action.execute() && accepted > 0) {
                    phaseLiquid += accepted;
                    changed();
                    sync();
                }
                return accepted;
            }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) {
                return resource.is(ACFluids.IMAG_PHASE.get()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int maxDrain, FluidAction action) {
                int drained = Math.min(Math.max(0, maxDrain), phaseLiquid);
                if (drained <= 0) return FluidStack.EMPTY;
                if (action.execute()) {
                    phaseLiquid -= drained;
                    changed();
                    sync();
                }
                return new FluidStack(ACFluids.IMAG_PHASE.get(), drained);
            }
        };
    }
    public boolean isWirelessConfigurable() { return kind != MachineKind.WIND_PILLAR; }
    public boolean isMatrixWorking() {
        return kind == MachineKind.MATRIX && items.getStackInSlot(0).is(ACItems.CONSTRAINT_PLATE.get())
                && items.getStackInSlot(1).is(ACItems.CONSTRAINT_PLATE.get())
                && items.getStackInSlot(2).is(ACItems.CONSTRAINT_PLATE.get())
                && items.getStackInSlot(3).is(ACItems.MAT_CORE.get());
    }
    public int matrixTier() { return matrixCoreLevel(); }
    public double wirelessRange() {
        return switch (kind) {
            case MATRIX -> matrixRange();
            case NODE_BASIC -> 9;
            case NODE_STANDARD -> 12;
            case NODE_ADVANCED -> 19;
            default -> 0;
        };
    }
    private int matrixCoreLevel() {
        return isMatrixWorking() ? MatrixCoreItem.level(items.getStackInSlot(3)) : 0;
    }
    private double matrixRange() { return 24 * Math.sqrt(matrixCoreLevel()); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            LOADED.computeIfAbsent(serverLevel, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
            if (machines != null) machines.remove(this);
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACMachineBlockEntity machine) {
        machine.ticks++;
        if (machine.ticks % 20 == 1) machine.reconcileWirelessLink();
        machine.acceptPhaseContainers();
        machine.generate();
        machine.transferContainedEnergy();
        if (machine.kind.isGenerator() || machine.kind == MachineKind.WIND_BASE || machine.kind.isBridgeInput()) machine.pushAdjacent();
        if (machine.kind.isProcessor() || machine.kind == MachineKind.ABILITY_INTERFERER || machine.kind.isNetworkNode()) machine.pullAdjacent();
        if (machine.kind.isNetworkNode() && machine.ticks % 10 == 0) machine.pushWireless();
        if (machine.kind.isBridgeInput() && machine.ticks % 10 == 0) machine.pushToWirelessNode();
        machine.process();
        if (machine.progress > 0 && machine.ticks % 40 == 0) {
            level.playSound(null, pos, ACSounds.get(machine.kind == MachineKind.IMAG_FUSOR
                    ? "machine.imag_fusor_work" : "machine.machine_work"), SoundSource.BLOCKS, .45f, 1f);
        }
        if (machine.kind == MachineKind.ABILITY_INTERFERER && machine.interfererEnabled && machine.ticks % 10 == 0) {
            int cost = Math.max(1, (int) Math.round(machine.interfererRange * machine.interfererRange));
            if (machine.energy.getEnergyStored() > cost) machine.energy.setEnergy(machine.energy.getEnergyStored() - cost);
            else {
                machine.energy.setEnergy(0);
                machine.interfererEnabled = false;
            }
            machine.changed();
            machine.sync();
        }
        if (machine.ticks % 5 == 0 && state.hasProperty(ACMachineBlock.VISUAL_STAGE)) {
            int visualStage = switch (machine.kind) {
                case IMAG_FUSOR -> machine.progress > 0 ? 1 + (machine.ticks / 5) % 4 : 0;
                case ABILITY_INTERFERER -> machine.interfererEnabled ? 1 : 0;
                case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> Math.min(4, Math.round(
                        4f * machine.energy.getEnergyStored() / Math.max(1, machine.energy.getMaxEnergyStored())));
                default -> 0;
            };
            boolean connected = machine.kind.isNetworkNode() && machine.kind != MachineKind.MATRIX
                    && !machine.networkId.isEmpty();
            BlockState visualState = state.setValue(ACMachineBlock.VISUAL_STAGE, visualStage)
                    .setValue(ACMachineBlock.CONNECTED, connected);
            if (!visualState.equals(state)) level.setBlock(pos, visualState, Block.UPDATE_CLIENTS);
        }
        if (machine.ticks % 20 == 0) machine.sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ACMachineBlockEntity machine) {
        machine.ticks++;
        if (machine.kind == MachineKind.CAT_ENGINE
                && machine.energy.getEnergyStored() < machine.energy.getMaxEnergyStored()) {
            // 1.12.2: deltaMilliseconds * thisTickGeneration(500) * 1e-2.
            machine.clientCatRotation = (machine.clientCatRotation + 250) % 360;
        }
    }

    private void generate() {
        if (!(level instanceof ServerLevel serverLevel) || kind.generation() <= 0) return;
        int generated = switch (kind) {
            case SOLAR_GENERATOR -> serverLevel.isDay() && serverLevel.canSeeSky(worldPosition.above())
                    ? Math.max(1, Math.round(kind.generation() * (serverLevel.isRaining() ? .2f : 1f))) : 0;
            case WIND_GENERATOR -> windStructureComplete() && windNoObstacle()
                    && items.getStackInSlot(0).is(ACItems.WINDGEN_FAN.get())
                    ? Math.round(kind.generation() * (.5f + .5f * Math.max(0, Math.min(1,
                            (worldPosition.getY() - 70) / 90f)))) : 0;
            case PHASE_GENERATOR -> Math.min(kind.generation(), phaseLiquid / 2);
            case CAT_ENGINE -> kind.generation();
            default -> kind.generation();
        };
        TrackedEnergyStorage output = kind == MachineKind.WIND_GENERATOR && findWindBase() != null
                ? findWindBase().energy : energy;
        generated = Math.min(generated, output.getMaxEnergyStored() - output.getEnergyStored());
        if (generated > 0) {
            if (kind == MachineKind.PHASE_GENERATOR) phaseLiquid = Math.max(0, phaseLiquid - generated * 2);
            output.receiveEnergy(generated, false);
            changed();
        }
    }

    private boolean windStructureComplete() { return findWindBase() != null; }

    private boolean windNoObstacle() {
        if (level == null || kind != MachineKind.WIND_GENERATOR
                || !getBlockState().hasProperty(ACMachineBlock.FACING)) return false;
        Direction normal = getBlockState().getValue(ACMachineBlock.FACING);
        Direction horizontal = normal.getClockWise();
        BlockPos center = worldPosition.relative(normal);
        for (int side = -7; side <= 7; side++) {
            for (int vertical = -7; vertical <= 7; vertical++) {
                if (side == 0 && vertical == 0) continue;
                BlockPos check = center.relative(horizontal, side).above(vertical);
                if (!level.getBlockState(check).isAir()) return false;
            }
        }
        return true;
    }

    public boolean windNoObstacleForRender() { return windNoObstacle(); }

    public boolean windStructureCompleteForRender() {
        if (level == null) return false;
        if (kind == MachineKind.WIND_GENERATOR) return findWindBase() != null;
        if (kind != MachineKind.WIND_BASE) return false;
        int pillars = 0;
        for (int distance = 1; distance <= 42; distance++) {
            BlockPos abovePos = worldPosition.above(distance);
            BlockState above = level.getBlockState(abovePos);
            if (distance == 1 && level.getBlockEntity(abovePos) instanceof ACMultiblockPartEntity part
                    && part.origin().equals(worldPosition)) continue;
            if (above.is(ACBlocks.WINDGEN_PILLAR.get())) {
                if (++pillars > 40) return false;
            } else return pillars >= 8 && above.is(ACBlocks.WINDGEN_MAIN.get());
        }
        return false;
    }

    private @Nullable ACMachineBlockEntity findWindBase() {
        if (level == null) return null;
        int pillars = 0;
        for (int distance = 1; distance <= 42; distance++) {
            BlockPos belowPos = worldPosition.below(distance);
            BlockState below = level.getBlockState(belowPos);
            if (below.is(ACBlocks.WINDGEN_PILLAR.get())) {
                if (++pillars > 40) return null;
            } else if (pillars >= 8 && below.is(ACBlocks.WINDGEN_BASE.get())
                    && level.getBlockEntity(belowPos) instanceof ACMachineBlockEntity base) return base;
            else if (pillars >= 8 && level.getBlockEntity(belowPos) instanceof ACMultiblockPartEntity part
                    && level.getBlockEntity(part.origin()) instanceof ACMachineBlockEntity base
                    && base.kind == MachineKind.WIND_BASE) return base;
            else return null;
        }
        return null;
    }

    private void acceptPhaseContainers() {
        if (kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR) return;
        ItemStack input = items.getStackInSlot(3);
        ItemStack output = items.getStackInSlot(4);
        if (!input.is(ACItems.MATTER_UNIT.get()) || !cn.academy.item.MatterUnitItem.isFilled(input)
                || phaseLiquid + 1_000 > 8_000
                || !output.isEmpty() && (!output.is(ACItems.MATTER_UNIT.get()) || output.getCount() >= output.getMaxStackSize())) return;
        items.extractItem(3, 1, false);
        ItemStack empty = new ItemStack(ACItems.MATTER_UNIT.get());
        if (output.isEmpty()) items.setStackInSlot(4, empty);
        else output.grow(1);
        phaseLiquid += 1_000;
        changed();
    }

    private void transferContainedEnergy() {
        if (kind == MachineKind.MATRIX) return;
        int[] slots = {0, 1};
        for (int slot : slots) {
            ItemStack stack = items.getStackInSlot(slot);
            IEnergyStorage itemEnergy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (itemEnergy == null) continue;
            int rate = kind.transfer();
            boolean chargeItems = kind.isNetworkNode() ? slot == 1
                    : kind.isGenerator() || kind == MachineKind.WIND_BASE || kind.isBridgeOutput();
            if (chargeItems && energy.getEnergyStored() > 0 && itemEnergy.canReceive()) {
                int accepted = itemEnergy.receiveEnergy(Math.min(rate, energy.getEnergyStored()), false);
                energy.extractEnergy(accepted, false);
            } else if (!chargeItems && energy.getEnergyStored() < energy.getMaxEnergyStored() && itemEnergy.canExtract()) {
                int offered = itemEnergy.extractEnergy(Math.min(rate, energy.getEnergyStored() < energy.getMaxEnergyStored()
                        ? energy.getMaxEnergyStored() - energy.getEnergyStored() : 0), true);
                int accepted = energy.receiveEnergy(offered, false);
                itemEnergy.extractEnergy(accepted, false);
            }
        }
    }

    private void process() {
        if (!kind.isProcessor() || level == null) return;
        ItemStack result = recipeResult();
        if (result.isEmpty() || !canOutput(result)) {
            if (progress != 0) {
                progress = 0;
                changed();
            }
            return;
        }
        int cost = kind == MachineKind.IMAG_FUSOR ? 12 : ticks % 10 < 3 ? 14 : 13;
        if (energy.getEnergyStored() < cost) {
            if (progress != 0) {
                progress = 0;
                changed();
            }
            return;
        }
        energy.setEnergy(energy.getEnergyStored() - cost);
        progress++;
        changed();
        if (progress >= maxProgress()) {
            finishRecipe(result);
            progress = 0;
            sync();
        }
    }

    private ItemStack recipeResult() {
        ItemStack first = items.getStackInSlot(0);
        ItemStack second = items.getStackInSlot(1);
        ItemStack input = first.isEmpty() ? second : first;
        if (kind == MachineKind.IMAG_FUSOR) {
            if (input.is(ACItems.CRYSTAL_LOW.get()) && phaseLiquid >= 3_000) return new ItemStack(ACItems.CRYSTAL_NORMAL.get());
            if (input.is(ACItems.CRYSTAL_NORMAL.get()) && phaseLiquid >= 8_000) return new ItemStack(ACItems.CRYSTAL_PURE.get());
            return ItemStack.EMPTY;
        }
        if (kind != MachineKind.METAL_FORMER) return ItemStack.EMPTY;
        return switch (mode) {
            case 0 -> {
                if (input.is(Items.IRON_INGOT)) yield new ItemStack(ACItems.REINFORCED_IRON_PLATE.get());
                if (input.is(ACItems.CONSTRAINT_INGOT.get())) yield new ItemStack(ACItems.CONSTRAINT_PLATE.get());
                yield ItemStack.EMPTY;
            }
            case 1 -> {
                if (input.is(ACItems.IMAG_SILICON_INGOT.get())) yield new ItemStack(ACItems.WAFER.get(), 2);
                if (input.is(ACItems.WAFER.get())) yield new ItemStack(ACItems.IMAG_SILICON_PIECE.get(), 4);
                yield ItemStack.EMPTY;
            }
            case 2 -> {
                if (input.is(ACItems.DATA_CHIP.get())) yield new ItemStack(ACItems.CALC_CHIP.get());
                yield ItemStack.EMPTY;
            }
            case 3 -> refineResult(input);
            default -> ItemStack.EMPTY;
        };
    }

    private ItemStack refineResult(ItemStack input) {
        if (input.is(ACItems.IMAGSIL_ORE.get())) return new ItemStack(ACItems.IMAG_SILICON_INGOT.get(), 4);
        if (input.is(ACItems.CONSTRAINT_METAL.get())) return new ItemStack(ACItems.CONSTRAINT_INGOT.get(), 2);
        if (input.is(ACItems.RESO_ORE.get())) return new ItemStack(ACItems.RESO_CRYSTAL.get(), 3);
        if (input.is(ACItems.CRYSTAL_ORE.get())) return new ItemStack(ACItems.CRYSTAL_LOW.get(), 4);
        if (input.is(Blocks.IRON_ORE.asItem()) || input.is(Blocks.DEEPSLATE_IRON_ORE.asItem())) return new ItemStack(Items.IRON_INGOT, 2);
        if (input.is(Blocks.GOLD_ORE.asItem()) || input.is(Blocks.DEEPSLATE_GOLD_ORE.asItem())) return new ItemStack(Items.GOLD_INGOT, 2);
        if (input.is(Blocks.COAL_ORE.asItem()) || input.is(Blocks.DEEPSLATE_COAL_ORE.asItem())) return new ItemStack(Items.COAL, 2);
        if (input.is(Blocks.DIAMOND_ORE.asItem()) || input.is(Blocks.DEEPSLATE_DIAMOND_ORE.asItem())) return new ItemStack(Items.DIAMOND, 2);
        if (input.is(Blocks.EMERALD_ORE.asItem()) || input.is(Blocks.DEEPSLATE_EMERALD_ORE.asItem())) return new ItemStack(Items.EMERALD, 2);
        if (input.is(Blocks.LAPIS_ORE.asItem()) || input.is(Blocks.DEEPSLATE_LAPIS_ORE.asItem())) return new ItemStack(Items.LAPIS_LAZULI, 12);
        if (input.is(Blocks.REDSTONE_ORE.asItem()) || input.is(Blocks.DEEPSLATE_REDSTONE_ORE.asItem())) return new ItemStack(Blocks.REDSTONE_BLOCK);
        if (input.is(Blocks.NETHER_QUARTZ_ORE.asItem())) return new ItemStack(Items.QUARTZ, 2);
        if (input.is(Blocks.COPPER_ORE.asItem()) || input.is(Blocks.DEEPSLATE_COPPER_ORE.asItem())) return new ItemStack(Items.COPPER_INGOT, 2);
        return ItemStack.EMPTY;
    }

    private void finishRecipe(ItemStack result) {
        ItemStack first = items.getStackInSlot(0);
        int inputSlot = first.isEmpty() ? 1 : 0;
        int consumed = 1;
        items.extractItem(inputSlot, consumed, false);
        if (kind == MachineKind.IMAG_FUSOR) {
            phaseLiquid = Math.max(0, phaseLiquid - (result.is(ACItems.CRYSTAL_NORMAL.get()) ? 3_000 : 8_000));
        }
        ItemStack existing = items.getStackInSlot(2);
        if (existing.isEmpty()) items.setStackInSlot(2, result);
        else existing.grow(result.getCount());
        changed();
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = items.getStackInSlot(2);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private boolean isValidInput(int slot, ItemStack stack) {
        if (slot == 2 || slot == 4) return false;
        return switch (kind) {
            case IMAG_FUSOR -> slot == 0 && (stack.is(ACItems.CRYSTAL_LOW.get()) || stack.is(ACItems.CRYSTAL_NORMAL.get()))
                    || slot == 1 && hasEnergyCapability(stack)
                    || slot == 3 && stack.is(ACItems.MATTER_UNIT.get()) && cn.academy.item.MatterUnitItem.isFilled(stack);
            case METAL_FORMER -> slot == 0 && isMetalFormerInput(stack) || slot == 1 && hasEnergyCapability(stack);
            case WIND_GENERATOR -> slot == 0 && stack.is(ACItems.WINDGEN_FAN.get());
            case WIND_BASE -> slot == 0 && hasEnergyCapability(stack);
            case PHASE_GENERATOR -> slot == 0 && hasEnergyCapability(stack)
                    || slot == 3 && stack.is(ACItems.MATTER_UNIT.get()) && cn.academy.item.MatterUnitItem.isFilled(stack);
            case MATRIX -> slot <= 2 && stack.is(ACItems.CONSTRAINT_PLATE.get())
                    || slot == 3 && stack.is(ACItems.MAT_CORE.get());
            case SOLAR_GENERATOR, NODE_BASIC, NODE_STANDARD, NODE_ADVANCED,
                    RF_INPUT, RF_OUTPUT, EU_INPUT, EU_OUTPUT -> slot < 2 && hasEnergyCapability(stack);
            case DEVELOPER_NORMAL, DEVELOPER_ADVANCED, ABILITY_INTERFERER -> slot == 1 && hasEnergyCapability(stack);
            case CAT_ENGINE, WIND_PILLAR -> false;
        };
    }

    private static boolean hasEnergyCapability(ItemStack stack) {
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    private static boolean isMetalFormerInput(ItemStack stack) {
        return stack.is(ACItems.IMAG_SILICON_INGOT.get()) || stack.is(ACItems.WAFER.get())
                || stack.is(ACItems.REINFORCED_IRON_PLATE.get()) || stack.is(Items.RAIL)
                || stack.is(ACItems.DATA_CHIP.get()) || stack.is(Items.IRON_INGOT)
                || stack.is(ACItems.CONSTRAINT_INGOT.get()) || stack.is(ACItems.IMAGSIL_ORE.get())
                || stack.is(ACItems.CONSTRAINT_METAL.get()) || stack.is(ACItems.RESO_ORE.get())
                || stack.is(ACItems.CRYSTAL_ORE.get()) || stack.is(Blocks.IRON_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_IRON_ORE.asItem()) || stack.is(Blocks.GOLD_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_GOLD_ORE.asItem()) || stack.is(Blocks.COAL_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_COAL_ORE.asItem()) || stack.is(Blocks.DIAMOND_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_DIAMOND_ORE.asItem()) || stack.is(Blocks.EMERALD_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_EMERALD_ORE.asItem()) || stack.is(Blocks.LAPIS_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_LAPIS_ORE.asItem()) || stack.is(Blocks.REDSTONE_ORE.asItem())
                || stack.is(Blocks.DEEPSLATE_REDSTONE_ORE.asItem()) || stack.is(Blocks.NETHER_QUARTZ_ORE.asItem())
                || stack.is(Blocks.COPPER_ORE.asItem()) || stack.is(Blocks.DEEPSLATE_COPPER_ORE.asItem());
    }

    public ItemStack insert(ItemStack held) {
        if (held.isEmpty()) return held;
        ItemStack remainder = held;
        for (int slot = 0; slot < items.getSlots(); slot++) remainder = items.insertItem(slot, remainder, false);
        return remainder;
    }

    public ItemStack takeOutput() {
        return items.extractItem(2, 64, false);
    }

    public void cycleMode() { cycleMode(1); }

    public void cycleMode(int direction) {
        if (kind == MachineKind.METAL_FORMER) {
            mode = Math.floorMod(mode + direction, 4);
            progress = 0;
            changed();
            sync();
        }
    }

    public boolean fillPhaseLiquid(int amount) {
        if ((kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR)
                || phaseLiquid + amount > 8_000) return false;
        phaseLiquid += amount;
        changed();
        sync();
        return true;
    }

    public boolean consumeDevelopmentEnergy(int amount) {
        if (energy.getEnergyStored() < amount) return false;
        energy.setEnergy(energy.getEnergyStored() - amount);
        changed();
        return true;
    }

    private void pushAdjacent() {
        if (level == null || energy.getEnergyStored() <= 0) return;
        int remaining = Math.min(kind.transfer(), energy.getEnergyStored());
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) break;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null || target == energy || !target.canReceive()) continue;
            int accepted = target.receiveEnergy(remaining, false);
            energy.extractEnergy(accepted, false);
            remaining -= accepted;
        }
    }

    private void pullAdjacent() {
        if (level == null || energy.getEnergyStored() >= energy.getMaxEnergyStored()) return;
        int needed = Math.min(kind.transfer(), energy.getMaxEnergyStored() - energy.getEnergyStored());
        for (Direction direction : Direction.values()) {
            if (needed <= 0) break;
            IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (source == null || source == energy || !source.canExtract()) continue;
            int extracted = source.extractEnergy(needed, false);
            int accepted = energy.receiveEnergy(extracted, false);
            if (accepted < extracted) source.receiveEnergy(extracted - accepted, false);
            needed -= accepted;
        }
    }

    private void pushToWirelessNode() {
        if (!(level instanceof ServerLevel) || energy.getEnergyStored() <= 0 || !hasNodeLink) return;
        if (!(level.getBlockEntity(BlockPos.of(linkedNodePos)) instanceof ACMachineBlockEntity target)
                || target.isRemoved() || !target.kind.isNetworkNode() || target.kind == MachineKind.MATRIX
                || !networkId.equals(target.networkId)) return;
        int remaining = Math.min(kind.transfer(), energy.getEnergyStored());
        int accepted = target.energy.receiveEnergy(remaining, false);
        energy.extractEnergy(accepted, false);
    }

    private void pushWireless() {
        if (!(level instanceof ServerLevel serverLevel) || networkId.isEmpty()) return;
        Set<ACMachineBlockEntity> loaded = LOADED.get(serverLevel);
        if (loaded == null) return;
        if (kind == MachineKind.MATRIX) {
            balanceMatrixNodes(loaded);
            return;
        }
        if (!kind.isNetworkNode() || !hasMatrixLink) return;
        java.util.List<ACMachineBlockEntity> users = new java.util.ArrayList<>();
        for (ACMachineBlockEntity target : Set.copyOf(loaded)) {
            if (target == this || target.isRemoved() || !target.hasNodeLink
                    || target.linkedNodePos != worldPosition.asLong() || !networkId.equals(target.networkId)) continue;
            users.add(target);
        }
        java.util.Collections.shuffle(users);
        int remainingInput = kind.transfer();
        for (ACMachineBlockEntity target : users) {
            if (remainingInput <= 0) break;
            if (!target.kind.isGenerator() && target.kind != MachineKind.WIND_BASE && !target.kind.isBridgeInput()) continue;
            int offered = target.energy.extractEnergy(Math.min(remainingInput,
                    energy.getMaxEnergyStored() - energy.getEnergyStored()), true);
            int moved = energy.receiveEnergy(offered, false);
            target.energy.extractEnergy(moved, false);
            remainingInput -= moved;
        }
        int remainingOutput = kind.transfer();
        for (ACMachineBlockEntity target : users) {
            if (remainingOutput <= 0 || energy.getEnergyStored() <= 0) break;
            if (target.kind.isGenerator() || target.kind == MachineKind.WIND_BASE || target.kind.isBridgeInput()) continue;
            int moved = target.energy.receiveEnergy(Math.min(remainingOutput, energy.getEnergyStored()), false);
            energy.extractEnergy(moved, false);
            remainingOutput -= moved;
        }
    }

    private void balanceMatrixNodes(Set<ACMachineBlockEntity> loaded) {
        if (!isMatrixWorking()) return;
        java.util.List<ACMachineBlockEntity> nodes = new java.util.ArrayList<>();
        long matrixPos = worldPosition.asLong();
        for (ACMachineBlockEntity node : Set.copyOf(loaded)) {
            if (!node.isRemoved() && node.kind.isNetworkNode() && node.kind != MachineKind.MATRIX
                    && node.hasMatrixLink && node.linkedMatrixPos == matrixPos && networkId.equals(node.networkId))
                nodes.add(node);
        }
        if (nodes.isEmpty()) return;
        java.util.Collections.shuffle(nodes);
        long stored = 0, capacity = 0;
        for (ACMachineBlockEntity node : nodes) {
            stored += node.energy.getEnergyStored();
            capacity += node.energy.getMaxEnergyStored();
        }
        if (capacity <= 0) return;
        double percentage = stored / (double) capacity;
        int remaining = wirelessBandwidth();
        for (ACMachineBlockEntity node : nodes) {
            if (remaining <= 0 || energy.getEnergyStored() >= energy.getMaxEnergyStored()) break;
            int target = (int) Math.round(node.energy.getMaxEnergyStored() * percentage);
            int excess = Math.max(0, node.energy.getEnergyStored() - target);
            int moved = node.energy.extractEnergy(Math.min(excess, Math.min(remaining,
                    energy.getMaxEnergyStored() - energy.getEnergyStored())), false);
            energy.receiveEnergy(moved, false);
            remaining -= moved;
        }
        remaining = wirelessBandwidth();
        for (ACMachineBlockEntity node : nodes) {
            if (remaining <= 0 || energy.getEnergyStored() <= 0) break;
            int target = (int) Math.round(node.energy.getMaxEnergyStored() * percentage);
            int required = Math.max(0, target - node.energy.getEnergyStored());
            int moved = node.energy.receiveEnergy(Math.min(required, Math.min(remaining, energy.getEnergyStored())), false);
            energy.extractEnergy(moved, false);
            remaining -= moved;
        }
    }

    private int wirelessBandwidth() {
        return kind == MachineKind.MATRIX ? matrixCoreLevel() * matrixCoreLevel() * 60 : kind.transfer();
    }

    /** @return 0 success, 1 permission denied, 2 network/password not found, 3 invalid/capacity full. */
    public int configureNetwork(ServerPlayer player, String requestedId, String password) {
        if (!(level instanceof ServerLevel serverLevel) || !isWirelessConfigurable()) return 3;
        String id = requestedId == null ? "" : requestedId.strip();
        if (id.length() > 24) id = id.substring(0, 24);
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (machines == null) return 2;
        if (id.isEmpty()) {
            if (!canConfigureWireless(player)) return 1;
            disconnectLoadedChildren(machines);
            networkId = networkPasswordHash = "";
            networkEncrypted = false;
            hasMatrixLink = hasNodeLink = false;
            if (kind == MachineKind.MATRIX) networkOwner = "";
            changed();
            sync();
            return 0;
        }
        String pass = password == null ? "" : password;
        final String checkedId = id;
        if (kind == MachineKind.MATRIX) {
            if (!isMatrixWorking() || !canConfigureWireless(player)) return !isMatrixWorking() ? 3 : 1;
            for (ACMachineBlockEntity machine : Set.copyOf(machines)) {
                if (machine != this && !machine.isRemoved() && machine.kind == MachineKind.MATRIX
                        && id.equals(machine.networkId)) return 3;
            }
            String oldId = networkId;
            networkId = id;
            networkPasswordHash = hashPassword(pass);
            networkEncrypted = !pass.isEmpty();
            networkOwner = player.getStringUUID();
            if (!oldId.equals(id)) updateLinkedNetworkId(machines, id);
            changed();
            sync();
            return 0;
        }
        if (kind.isNetworkNode()) {
            ACMachineBlockEntity matrix = Set.copyOf(machines).stream()
                    .filter(value -> !value.isRemoved() && value.kind == MachineKind.MATRIX
                            && checkedId.equals(value.networkId) && value.authenticateMatrix(pass)
                            && worldPosition.distSqr(value.worldPosition) <= value.matrixRange() * value.matrixRange())
                    .findFirst().orElse(null);
            return matrix == null ? 2 : linkToMatrix(player, matrix, pass);
        }
        ACMachineBlockEntity node = Set.copyOf(machines).stream()
                .filter(value -> !value.isRemoved() && value.kind.isNetworkNode() && value.kind != MachineKind.MATRIX
                        && checkedId.equals(value.networkId) && value.authenticateNode(pass) && value.validMatrixLink()
                        && worldPosition.distSqr(value.worldPosition) <= value.wirelessRange() * value.wirelessRange())
                .min(java.util.Comparator.comparingDouble(value -> worldPosition.distSqr(value.worldPosition)))
                .orElse(null);
        return node == null ? 2 : linkToNode(player, node, pass);
    }

    private boolean canConfigureWireless(ServerPlayer player) {
        String owner = kind == MachineKind.MATRIX ? (networkOwner.isEmpty() ? placerId : networkOwner)
                : kind.isNetworkNode() ? (nodeOwner.isEmpty() ? placerId : nodeOwner) : placerId;
        return owner.isEmpty() || owner.equals(player.getStringUUID()) || player.hasPermissions(2);
    }

    private void disconnectLoadedChildren(Set<ACMachineBlockEntity> machines) {
        long ownPos = worldPosition.asLong();
        if (kind == MachineKind.MATRIX) {
            for (ACMachineBlockEntity node : Set.copyOf(machines)) {
                if (node.hasMatrixLink && node.linkedMatrixPos == ownPos) {
                    node.disconnectLoadedChildren(machines);
                    node.hasMatrixLink = false;
                    node.networkId = "";
                    node.changed();
                    node.sync();
                }
            }
        } else if (kind.isNetworkNode()) {
            for (ACMachineBlockEntity user : Set.copyOf(machines)) {
                if (user.hasNodeLink && user.linkedNodePos == ownPos) {
                    user.hasNodeLink = false;
                    user.networkId = "";
                    user.changed();
                    user.sync();
                }
            }
        }
    }

    private void updateLinkedNetworkId(Set<ACMachineBlockEntity> machines, String newId) {
        if (kind != MachineKind.MATRIX) return;
        long ownPos = worldPosition.asLong();
        for (ACMachineBlockEntity node : Set.copyOf(machines)) {
            if (!node.hasMatrixLink || node.linkedMatrixPos != ownPos) continue;
            node.networkId = newId;
            node.updateNodeUsersNetwork(machines, newId);
            node.changed();
            node.sync();
        }
    }

    private void updateNodeUsersNetwork(Set<ACMachineBlockEntity> machines, String newId) {
        long ownPos = worldPosition.asLong();
        for (ACMachineBlockEntity user : Set.copyOf(machines)) {
            if (user.hasNodeLink && user.linkedNodePos == ownPos) {
                user.networkId = newId;
                user.changed();
                user.sync();
            }
        }
    }

    /** Reconciles coordinate-backed links after either side was unloaded during rename/removal. */
    private void reconcileWirelessLink() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (kind.isNetworkNode() && kind != MachineKind.MATRIX && hasMatrixLink) {
            BlockPos parentPos = BlockPos.of(linkedMatrixPos);
            if (!level.hasChunkAt(parentPos)) return;
            if (level.getBlockEntity(parentPos) instanceof ACMachineBlockEntity matrix
                    && matrix.kind == MachineKind.MATRIX && matrix.isMatrixWorking()
                    && worldPosition.distSqr(parentPos) <= matrix.matrixRange() * matrix.matrixRange()) {
                if (!networkId.equals(matrix.networkId)) {
                    networkId = matrix.networkId;
                    if (machines != null) updateNodeUsersNetwork(machines, networkId);
                    changed();
                    sync();
                }
            } else {
                if (machines != null) disconnectLoadedChildren(machines);
                hasMatrixLink = false;
                networkId = "";
                changed();
                sync();
            }
        } else if (!kind.isNetworkNode() && hasNodeLink) {
            BlockPos parentPos = BlockPos.of(linkedNodePos);
            if (!level.hasChunkAt(parentPos)) return;
            if (level.getBlockEntity(parentPos) instanceof ACMachineBlockEntity node
                    && node.kind.isNetworkNode() && node.kind != MachineKind.MATRIX && node.validMatrixLink()
                    && worldPosition.distSqr(parentPos) <= node.wirelessRange() * node.wirelessRange()) {
                if (!networkId.equals(node.networkId)) {
                    networkId = node.networkId;
                    changed();
                    sync();
                }
            } else {
                hasNodeLink = false;
                networkId = "";
                changed();
                sync();
            }
        }
    }

    private boolean validMatrixLink() {
        if (!hasMatrixLink || level == null) return false;
        return level.getBlockEntity(BlockPos.of(linkedMatrixPos)) instanceof ACMachineBlockEntity matrix
                && matrix.kind == MachineKind.MATRIX && matrix.isMatrixWorking()
                && networkId.equals(matrix.networkId);
    }

    public int configureMatrix(ServerPlayer player, String ssid, String password, boolean updatePassword) {
        if (kind != MachineKind.MATRIX) return 3;
        String previousHash = networkPasswordHash;
        boolean previousEncrypted = networkEncrypted;
        boolean wasInitialized = !networkId.isEmpty();
        int result = configureNetwork(player, ssid, updatePassword ? password : "");
        if (result == 0 && !updatePassword && wasInitialized && !networkId.isEmpty()) {
            networkPasswordHash = previousHash;
            networkEncrypted = previousEncrypted;
            changed();
            sync();
        }
        return result;
    }

    public boolean authenticateMatrix(String password) {
        return kind == MachineKind.MATRIX && isMatrixWorking() && !networkId.isEmpty()
                && networkPasswordHash.equals(hashPassword(password == null ? "" : password));
    }

    public boolean authenticateNode(String password) {
        String pass = password == null ? "" : password;
        return kind.isNetworkNode() && kind != MachineKind.MATRIX
                && (nodePasswordHash.isEmpty() ? pass.isEmpty() : nodePasswordHash.equals(hashPassword(pass)));
    }

    /** Links this node to one explicitly authorized matrix. */
    public int linkToMatrix(ServerPlayer player, ACMachineBlockEntity matrix, String matrixPassword) {
        if (!(level instanceof ServerLevel serverLevel) || matrix == null || matrix.level != level
                || !kind.isNetworkNode() || kind == MachineKind.MATRIX || matrix.kind != MachineKind.MATRIX) return 3;
        if (!matrix.authenticateMatrix(matrixPassword)) return 2;
        if (worldPosition.distSqr(matrix.worldPosition) > matrix.matrixRange() * matrix.matrixRange()) return 3;
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (machines == null) return 3;
        long matrixPos = matrix.worldPosition.asLong();
        long linkedNodes = machines.stream().filter(value -> value != this && !value.isRemoved()
                && value.kind.isNetworkNode() && value.kind != MachineKind.MATRIX
                && value.hasMatrixLink && value.linkedMatrixPos == matrixPos).count();
        if (linkedNodes >= matrix.wirelessCapacity()) return 3;
        networkId = matrix.networkId;
        networkPasswordHash = "";
        hasMatrixLink = true;
        linkedMatrixPos = matrixPos;
        hasNodeLink = false;
        updateNodeUsersNetwork(machines, networkId);
        changed();
        sync();
        return 0;
    }

    /** Links this wireless machine/user to one explicitly authorized node. */
    public int linkToNode(ServerPlayer player, ACMachineBlockEntity node, String nodePassword) {
        if (!(level instanceof ServerLevel serverLevel) || node == null || node.level != level
                || kind.isNetworkNode() || !isWirelessConfigurable()
                || !node.kind.isNetworkNode() || node.kind == MachineKind.MATRIX) return 3;
        if (!node.authenticateNode(nodePassword)) return 2;
        int radius = switch (node.kind) {
            case NODE_BASIC -> 9;
            case NODE_STANDARD -> 12;
            case NODE_ADVANCED -> 19;
            default -> 0;
        };
        if (worldPosition.distSqr(node.worldPosition) > radius * radius) return 3;
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (machines == null || !node.validMatrixLink()) return 3;
        long nodePos = node.worldPosition.asLong();
        long linkedUsers = machines.stream().filter(value -> value != this && !value.isRemoved()
                && value.hasNodeLink && value.linkedNodePos == nodePos).count();
        if (linkedUsers >= node.wirelessCapacity()) return 3;
        networkId = node.networkId;
        networkPasswordHash = "";
        hasNodeLink = true;
        linkedNodePos = nodePos;
        hasMatrixLink = false;
        changed();
        sync();
        return 0;
    }

    private static String hashPassword(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public void disconnectNetworkOnBreak() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
        if (machines != null) disconnectLoadedChildren(machines);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5, stack.copy()));
                items.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private void changed() {
        setChanged();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("Mode", mode);
        tag.putInt("PhaseLiquid", phaseLiquid);
        tag.putString("NetworkId", networkId);
        tag.putString("NetworkPasswordHash", networkPasswordHash);
        tag.putString("NetworkOwner", networkOwner);
        tag.putString("NodeName", nodeName);
        tag.putString("NodePasswordHash", nodePasswordHash);
        tag.putBoolean("NetworkEncrypted", networkEncrypted);
        tag.putBoolean("NodeEncrypted", nodeEncrypted);
        tag.putString("NodeOwner", nodeOwner);
        tag.putBoolean("HasMatrixLink", hasMatrixLink);
        if (hasMatrixLink) tag.putLong("LinkedMatrixPos", linkedMatrixPos);
        tag.putBoolean("HasNodeLink", hasNodeLink);
        if (hasNodeLink) tag.putLong("LinkedNodePos", linkedNodePos);
        tag.putString("PlacerId", placerId);
        tag.putString("PlacerName", placerName);
        tag.putBoolean("InterfererEnabled", interfererEnabled);
        tag.putDouble("InterfererRange", interfererRange);
        net.minecraft.nbt.ListTag whitelistTag = new net.minecraft.nbt.ListTag();
        interfererWhitelist.forEach(value -> whitelistTag.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("InterfererWhitelist", whitelistTag);
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        mode = Math.max(0, Math.min(3, tag.getInt("Mode")));
        phaseLiquid = Math.max(0, Math.min(8_000, tag.getInt("PhaseLiquid")));
        networkId = tag.getString("NetworkId");
        networkPasswordHash = tag.getString("NetworkPasswordHash");
        networkOwner = tag.getString("NetworkOwner");
        nodeName = tag.contains("NodeName") ? tag.getString("NodeName") : "Unnamed";
        nodePasswordHash = tag.getString("NodePasswordHash");
        networkEncrypted = tag.contains("NetworkEncrypted") ? tag.getBoolean("NetworkEncrypted")
                : !networkPasswordHash.isEmpty() && !networkPasswordHash.equals(hashPassword(""));
        nodeEncrypted = tag.contains("NodeEncrypted") ? tag.getBoolean("NodeEncrypted")
                : !nodePasswordHash.isEmpty() && !nodePasswordHash.equals(hashPassword(""));
        nodeOwner = tag.getString("NodeOwner");
        hasMatrixLink = tag.getBoolean("HasMatrixLink");
        linkedMatrixPos = tag.getLong("LinkedMatrixPos");
        hasNodeLink = tag.getBoolean("HasNodeLink");
        linkedNodePos = tag.getLong("LinkedNodePos");
        placerId = tag.getString("PlacerId");
        placerName = tag.getString("PlacerName");
        interfererEnabled = tag.getBoolean("InterfererEnabled");
        interfererRange = tag.contains("InterfererRange") ? Math.max(10, Math.min(100, tag.getDouble("InterfererRange"))) : 10;
        interfererWhitelist.clear();
        net.minecraft.nbt.ListTag whitelistTag = tag.getList("InterfererWhitelist", net.minecraft.nbt.Tag.TAG_STRING);
        for (net.minecraft.nbt.Tag value : whitelistTag) interfererWhitelist.add(value.getAsString());
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.remove("NetworkPasswordHash");
        tag.remove("NetworkOwner");
        tag.remove("NodePasswordHash");
        tag.remove("NodeOwner");
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
    }
}
