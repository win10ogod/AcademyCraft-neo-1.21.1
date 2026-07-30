package cn.academy.ability;

import cn.academy.advancement.ACAdvancements;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.network.ACNetwork;
import cn.academy.network.DeveloperActionPayload;
import cn.academy.network.DevelopmentSyncPayload;
import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Restores DevelopData's per-stimulation, interruptible server lifecycle. */
public final class DevelopmentManager {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final int action;
        final BlockPos pos;
        final boolean portable;
        final boolean offhand;
        final String skill;
        final int ticksPerStimulation;
        final int energyPerTick;
        final int totalTicks;
        int ticks;

        Session(int action, BlockPos pos, boolean portable, boolean offhand, String skill,
                int ticksPerStimulation, int energyPerTick, int stimulations) {
            this.action = action;
            this.pos = pos;
            this.portable = portable;
            this.offhand = offhand;
            this.skill = skill;
            this.ticksPerStimulation = ticksPerStimulation;
            this.energyPerTick = energyPerTick;
            this.totalTicks = Math.max(1, ticksPerStimulation * stimulations);
        }
    }

    public static void handle(ServerPlayer player, DeveloperActionPayload payload) {
        if (payload.action() == DeveloperActionPayload.ABORT) {
            abort(player, true);
            return;
        }
        StartData start = validateStart(player, payload);
        if (start == null) {
            PacketDistributor.sendToPlayer(player, new DevelopmentSyncPayload(DevelopmentSyncPayload.FAILED,
                    payload.action(), payload.skill(), 0, payload.portable(), false, 0, 0));
            return;
        }
        // The old DevelopData API replaced an existing action when a new one started.
        ACTIVE.remove(player.getUUID());
        Session session = new Session(payload.action(), payload.pos(), payload.portable(), start.offhand,
                payload.skill(), start.ticksPerStimulation, start.energyPerTick, start.stimulations);
        ACTIVE.put(player.getUUID(), session);
        sync(player, session, DevelopmentSyncPayload.DEVELOPING, 0);
    }

    public static void tick(ServerPlayer player, AbilityState state) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        if (!consumeTick(player, session)) {
            ACTIVE.remove(player.getUUID());
            sync(player, session, DevelopmentSyncPayload.FAILED, progress(session));
            return;
        }
        session.ticks++;
        if (session.ticks >= session.totalTicks) {
            ACTIVE.remove(player.getUUID());
            boolean success = complete(player, state, session);
            sync(player, session, success ? DevelopmentSyncPayload.DONE : DevelopmentSyncPayload.FAILED,
                    success ? 1 : progress(session));
            return;
        }
        if (session.ticks % 5 == 0) sync(player, session, DevelopmentSyncPayload.DEVELOPING, progress(session));
    }

    public static void abort(ServerPlayer player, boolean notify) {
        Session session = ACTIVE.remove(player.getUUID());
        if (session != null && notify) sync(player, session, DevelopmentSyncPayload.FAILED, progress(session));
    }

    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    private static StartData validateStart(ServerPlayer player, DeveloperActionPayload payload) {
        Developer developer = findDeveloper(player, payload.pos(), payload.portable(), -1);
        if (developer == null) return null;
        AbilityState state = AbilityState.load(player);
        int stimulations;
        switch (payload.action()) {
            case DeveloperActionPayload.LEARN_SKILL -> {
                AbilitySkill skill = AbilityRegistry.skill(payload.skill());
                if (skill == null || skill.level() > developer.maximumLevel || !state.canLearn(skill)) return null;
                stimulations = (int) (3 + skill.level() * skill.level() * .5f);
            }
            case DeveloperActionPayload.LEVEL_UP -> {
                if (state.hasCategory() && (state.level() >= 5 || state.level() >= developer.maximumLevel
                        || state.levelProgress() < 1)) return null;
                stimulations = 5 * (state.level() + 1);
            }
            case DeveloperActionPayload.RESET_CATEGORY -> {
                if (developer.maximumLevel != 5 || state.level() < 3
                        || !player.getMainHandItem().is(ACItems.MAGNETIC_COIL.get())
                        || differentFactor(player, state.category()) == null) return null;
                stimulations = state.level() * 10;
            }
            default -> { return null; }
        }
        int requiredEnergy = developer.energyPerStimulation * stimulations;
        if (developerEnergy(developer) < requiredEnergy) return null;
        return new StartData(developer.offhand, developer.ticksPerStimulation,
                developer.energyPerStimulation / developer.ticksPerStimulation, stimulations);
    }

    private static boolean consumeTick(ServerPlayer player, Session session) {
        int expectedHand = session.offhand ? 1 : 0;
        Developer developer = findDeveloper(player, session.pos, session.portable, expectedHand);
        if (developer == null) return false;
        if (session.portable) {
            IEnergyStorage storage = developer.stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (storage == null || storage.getEnergyStored() < session.energyPerTick) return false;
            return storage.extractEnergy(session.energyPerTick, false) == session.energyPerTick;
        }
        return developer.machine.consumeDevelopmentEnergy(session.energyPerTick);
    }

    private static boolean complete(ServerPlayer player, AbilityState state, Session session) {
        Developer developer = findDeveloper(player, session.pos, session.portable, session.offhand ? 1 : 0);
        if (developer == null) return false;
        if (session.action == DeveloperActionPayload.LEARN_SKILL) {
            AbilitySkill skill = AbilityRegistry.skill(session.skill);
            if (skill == null || skill.level() > developer.maximumLevel || !state.canLearn(skill)) return false;
            state.learn(skill);
            player.displayClientMessage(Component.translatable("ac.developer.learned", skill.displayName()), false);
            ACAdvancements.grantLegacySkillLearn(player, skill.category(), skill.name());
            ACAdvancements.grant(player, "ac_learning_skill");
        } else if (session.action == DeveloperActionPayload.LEVEL_UP) {
            if (state.hasCategory()) {
                if (state.level() >= 5 || state.level() >= developer.maximumLevel
                        || state.levelProgress() < 1) return false;
                state.setLevel(state.level() + 1);
            } else {
                ItemStack factor = anyFactor(player, "");
                String category = factor == null ? AbilityRegistry.categoryIds().get(
                        player.getRandom().nextInt(AbilityRegistry.categoryIds().size()))
                        : factor.getOrDefault(ACDataComponents.ABILITY_CATEGORY.get(), "");
                if (AbilityRegistry.category(category) == null) return false;
                state.setCategory(category);
                if (factor != null && !player.isCreative()) factor.shrink(1);
                player.displayClientMessage(Component.translatable("ac.ability.category_acquired",
                        AbilityRegistry.category(category).displayName()), false);
                ACAdvancements.grant(player, "dev_category");
            }
            ACAdvancements.grantLegacyLevels(player, state.category(), state.level());
        } else if (session.action == DeveloperActionPayload.RESET_CATEGORY) {
            ItemStack factor = differentFactor(player, state.category());
            if (developer.maximumLevel != 5 || state.level() < 3 || factor == null
                    || !player.getMainHandItem().is(ACItems.MAGNETIC_COIL.get())) return false;
            String category = factor.getOrDefault(ACDataComponents.ABILITY_CATEGORY.get(), "");
            int previousLevel = state.level();
            state.setCategory(category);
            state.setLevel(previousLevel - 1);
            if (!player.isCreative()) {
                factor.shrink(1);
                player.getMainHandItem().shrink(1);
            }
            ACAdvancements.grant(player, "convert_category");
        } else return false;
        state.save(player);
        ACNetwork.sync(player, state);
        return true;
    }

    private static Developer findDeveloper(ServerPlayer player, BlockPos pos, boolean portable, int expectedHand) {
        if (portable) {
            ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
            boolean offhand = !main.is(ACItems.DEVELOPER_PORTABLE.get()) && off.is(ACItems.DEVELOPER_PORTABLE.get());
            ItemStack stack = offhand ? off : main;
            if (!stack.is(ACItems.DEVELOPER_PORTABLE.get())
                    || expectedHand >= 0 && (offhand ? 1 : 0) != expectedHand) return null;
            return new Developer(null, stack, offhand, 2, 25, 750);
        }
        if (player.distanceToSqr(pos.getCenter()) > 64
                || !(player.serverLevel().getBlockEntity(pos) instanceof ACMachineBlockEntity machine)) return null;
        return switch (machine.kind()) {
            case DEVELOPER_NORMAL -> new Developer(machine, ItemStack.EMPTY, false, 3, 20, 700);
            case DEVELOPER_ADVANCED -> new Developer(machine, ItemStack.EMPTY, false, 5, 15, 600);
            default -> null;
        };
    }

    private static ItemStack anyFactor(ServerPlayer player, String excludedCategory) {
        for (ItemStack candidate : player.getInventory().items) {
            String value = candidate.getOrDefault(ACDataComponents.ABILITY_CATEGORY.get(), "");
            if (candidate.is(ACItems.INDUCTION_FACTOR.get()) && AbilityRegistry.category(value) != null
                    && (excludedCategory.isEmpty() || !excludedCategory.equals(value))) return candidate;
        }
        return null;
    }

    private static ItemStack differentFactor(ServerPlayer player, String category) {
        return anyFactor(player, category);
    }

    private static int developerEnergy(Developer developer) {
        if (developer == null) return 0;
        if (developer.machine != null) return developer.machine.energy.getEnergyStored();
        IEnergyStorage storage = developer.stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? 0 : storage.getEnergyStored();
    }

    private static int developerCapacity(Developer developer) {
        if (developer == null) return 0;
        if (developer.machine != null) return developer.machine.energy.getMaxEnergyStored();
        IEnergyStorage storage = developer.stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? 0 : storage.getMaxEnergyStored();
    }

    private static float progress(Session session) {
        return Math.min(1, session.ticks / (float) session.totalTicks);
    }

    private static void sync(ServerPlayer player, Session session, int state, float progress) {
        Developer developer = findDeveloper(player, session.pos, session.portable, session.offhand ? 1 : 0);
        PacketDistributor.sendToPlayer(player, new DevelopmentSyncPayload(state, session.action, session.skill, progress,
                session.portable, session.offhand, developerEnergy(developer), developerCapacity(developer)));
    }

    private record StartData(boolean offhand, int ticksPerStimulation, int energyPerTick, int stimulations) {}
    private record Developer(ACMachineBlockEntity machine, ItemStack stack, boolean offhand,
                             int maximumLevel, int ticksPerStimulation, int energyPerStimulation) {}

    private DevelopmentManager() { }
}
