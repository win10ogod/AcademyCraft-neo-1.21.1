package cn.academy.event;

import cn.academy.AcademyCraft;
import cn.academy.advancement.ACAdvancements;
import cn.academy.ability.AbilityManager;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.config.ACConfig;
import cn.academy.command.ACCommands;
import cn.academy.network.ACNetwork;
import cn.academy.network.OpenClientScreenPayload;
import cn.academy.item.MatterUnitItem;
import cn.academy.item.MediaItem;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACParticles;
import cn.academy.registry.ACSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ACCommonEvents {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ACCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        state.tick();

        if (player.tickCount % 10 == 0) {
            boolean interfered = ACMachineBlockEntity.interferesWith(player);
            state.setInterfered(interfered);
            if (interfered) cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
            unlockTutorials(player, state);
        }
        cn.academy.ability.AbilityContextManager.tick(player, state);
        cn.academy.ability.AbilityExecutor.tickTransient(player, state);
        cn.academy.ability.DevelopmentManager.tick(player, state);
        state.save(player);
        if (player.tickCount % 10 == 0) ACNetwork.sync(player, state);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        if (ACConfig.GIVE_MISAKA_TERMINAL.get() && !state.tutorialGiven()) {
            ItemStack tutorial = new ItemStack(ACItems.TUTORIAL.get());
            if (!player.getInventory().add(tutorial)) player.spawnAtLocation(tutorial);
            state.markTutorialGiven(1000 + player.getRandom().nextInt(18_000));
            state.save(player);
        }
        ACNetwork.sync(player, state);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cn.academy.ability.AbilityContextManager.abortAll(player, AbilityState.load(player));
            cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
            cn.academy.ability.DevelopmentManager.abort(player, false);
            ACNetwork.clearWirelessAuthorization(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACNetwork.sync(player, AbilityState.load(player));
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACNetwork.sync(player, AbilityState.load(player));
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        newPlayer.getPersistentData().put(AbilityState.ROOT_KEY,
                event.getOriginal().getPersistentData().getCompound(AbilityState.ROOT_KEY).copy());
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onRadiationDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        long until = target.getPersistentData().getLong("academy:md_mark_until");
        if (until >= target.level().getGameTime()) {
            float rate = target.getPersistentData().getFloat("academy:md_mark_rate");
            if (rate > 1) event.setAmount(event.getAmount() * rate);
        }
    }

    @SubscribeEvent
    public static void onRadiationTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel level)
                || !target.getPersistentData().contains("academy:md_mark_until")) return;
        long until = target.getPersistentData().getLong("academy:md_mark_until");
        if (until < level.getGameTime()) {
            target.getPersistentData().remove("academy:md_mark_until");
            target.getPersistentData().remove("academy:md_mark_rate");
            return;
        }
        int count = level.random.nextInt(3);
        if (count > 0) level.sendParticles(ACParticles.MELTDOWNER.get(), target.getX(),
                target.getY() + target.getBbHeight() * .5, target.getZ(), count,
                target.getBbWidth() * .6, target.getBbHeight() * .5, target.getBbWidth() * .6, .02);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getPersistentData().getBoolean("academy:reflecting_damage")) return;
        long now = player.level().getGameTime();
        boolean lightShield = now <= player.getPersistentData().getLong("academy:light_shield_until");
        boolean deviation = now <= player.getPersistentData().getLong("academy:vec_deviation_until");
        boolean reflection = now <= player.getPersistentData().getLong("academy:vec_reflection_until");
        net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
        AbilityState state = AbilityState.load(player);

        if (reflection) {
            net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
            AbilitySkill skill = cn.academy.ability.AbilityRegistry.skill("vecmanip.vec_reflection");
            float exp = skill == null ? 0 : state.experience(skill.id());
            float reflected = event.getAmount() * (.6f + .6f * exp);
            float originalDamage = event.getAmount();
            if (skill != null && state.consumeForce(skill, originalDamage * (20 - 5 * exp), 0, player.isCreative())) {
                event.setAmount(Math.max(0, originalDamage - reflected));
                state.addExperience(skill, originalDamage * .0004f);
                player.serverLevel().playSound(null, player.blockPosition(), ACSounds.VEC_REFLECTION.get(),
                        SoundSource.PLAYERS, .8f, 1f);
                if (attacker instanceof LivingEntity living && attacker != player
                        && (!(living instanceof ServerPlayer)
                        || ACConfig.ATTACK_PLAYERS.get() && state.attackPlayers())) {
                    player.getPersistentData().putBoolean("academy:reflecting_damage", true);
                    living.hurt(player.damageSources().magic(), reflected);
                    player.getPersistentData().remove("academy:reflecting_damage");
                }
                if (event.getAmount() <= 0) event.setCanceled(true);
                state.save(player);
                return;
            }
        }
        if (deviation) {
            AbilitySkill skill = cn.academy.ability.AbilityRegistry.skill("vecmanip.vec_deviation");
            float exp = skill == null ? 0 : state.experience(skill.id());
            float originalDamage = event.getAmount();
            float consumption = Math.min(state.cp(), 15 - 3 * exp);
            if (skill != null && state.consumeForce(skill, consumption, 0, player.isCreative())) {
                event.setAmount(originalDamage * (1 - (.4f + .5f * exp)));
                state.addExperience(skill, originalDamage * .0006f);
                player.serverLevel().playSound(null, player.blockPosition(), ACSounds.VEC_DEVIATION.get(),
                        SoundSource.PLAYERS, .8f, 1f);
                state.save(player);
                return;
            }
        }
        if (lightShield) {
            AbilitySkill skill = cn.academy.ability.AbilityRegistry.skill("meltdowner.light_shield");
            float exp = skill == null ? 0 : state.experience(skill.id());
            long last = player.getPersistentData().getLong("academy:light_shield_absorb");
            net.minecraft.world.entity.Entity source = event.getSource().getDirectEntity();
            boolean frontal = source == null || source.position().subtract(player.position()).normalize()
                    .dot(player.getLookAngle()) > .5;
            boolean intervalReady = !player.getPersistentData().contains("academy:light_shield_absorb")
                    || now - last > 18;
            if (frontal && intervalReady && skill != null) {
                player.getPersistentData().putLong("academy:light_shield_absorb", now);
                // Preserve the 1.12.2 attack path's historical consume argument order.
                if (state.consumeRaw(skill, 5 - 2 * exp, 50 - 20 * exp, player.isCreative())) {
                    event.setAmount(Math.max(0, event.getAmount() - (15 + 35 * exp)));
                    if (event.getAmount() <= 0) event.setCanceled(true);
                }
                state.addExperience(skill, .001f);
                state.save(player);
            }
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityState state = AbilityState.load(player);
            state.recoverAll();
            state.save(player);
            ACNetwork.sync(player, state);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityState state = AbilityState.load(player);
            state.setActive(false);
            cn.academy.ability.AbilityContextManager.abortAll(player, state);
            cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
            cn.academy.ability.DevelopmentManager.abort(player, false);
            state.recoverAll();
            state.save(player);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        AbilityState state = AbilityState.load(player);
        boolean handled = true;

        if (item == ACItems.INDUCTION_FACTOR.get()) {
            ACAdvancements.grant(player, "getting_factor");
            player.displayClientMessage(Component.translatable("ac.factor.developer_hint"), true);
        } else if (item == ACItems.DEVELOPER_PORTABLE.get()) {
            ACAdvancements.grant(player, "ac_developer");
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new OpenClientScreenPayload("developer:portable"));
        } else if (item == ACItems.MAGNETIC_COIL.get()) {
            player.displayClientMessage(Component.translatable("ac.coil.developer_hint"), true);
        } else if (item == ACItems.TERMINAL_INSTALLER.get()) {
            if (state.terminalInstalled()) {
                player.displayClientMessage(Component.translatable("ac.terminal.alrdy_installed"), true);
            } else {
                state.installTerminal();
                state.save(player);
                ACAdvancements.grant(player, "terminal_installed");
                consume(player, stack);
                player.displayClientMessage(Component.translatable("ac.terminal.key_hint",
                        Component.translatable("key.keyboard.left.alt")), false);
                ACNetwork.sync(player, state);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new OpenClientScreenPayload("terminal_install"));
            }
        } else if (item == ACItems.APP_SKILL_TREE.get()) {
            installApp(player, state, stack, "skill_tree");
        } else if (item == ACItems.APP_MEDIA_PLAYER.get()) {
            installApp(player, state, stack, "media_player");
        } else if (item == ACItems.APP_FREQ_TRANSMITTER.get()) {
            installApp(player, state, stack, "freq_transmitter");
        } else if (item == ACItems.MEDIA_ITEM.get()) {
            String media = MediaItem.media(stack);
            if (!state.terminalInstalled() || !state.apps().contains("media_player")) {
                AbilityManager.deny(player, Component.translatable("ac.media.notinstalled"));
            } else if (!state.installApp("media:" + media)) {
                player.displayClientMessage(Component.translatable("ac.media.haveone",
                        Component.translatable("ac.media." + media + ".name")), true);
            } else {
                state.save(player);
                consume(player, stack);
                player.displayClientMessage(Component.translatable("ac.media.acquired",
                        Component.translatable("ac.media." + media + ".name")), false);
                ACNetwork.sync(player, state);
            }
        } else if (item == ACItems.COIN.get()) {
            ACThrownItemEntity coin = new ACThrownItemEntity(player, player.serverLevel(), stack);
            coin.setPos(player.getX(), player.getY() + .2, player.getZ());
            coin.setDeltaMovement(0, .92 + Math.max(0, player.getDeltaMovement().y), 0);
            player.serverLevel().addFreshEntity(coin);
            consume(player, stack);
            player.serverLevel().playSound(null, player.blockPosition(), ACSounds.COIN_FLIP.get(), SoundSource.PLAYERS, 1, 1);
        } else if (item == ACItems.MAG_HOOK.get() || item == ACItems.SILBARN.get()) {
            ACThrownItemEntity projectile = new ACThrownItemEntity(player, player.serverLevel(), stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0,
                    item == ACItems.MAG_HOOK.get() ? 2f : 1f, 0);
            player.serverLevel().addFreshEntity(projectile);
            player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.EGG_THROW,
                    SoundSource.PLAYERS, .5f, .4f / (player.getRandom().nextFloat() * .4f + .8f));
            consume(player, stack);
        } else if (item == ACItems.TUTORIAL.get()) {
            ACAdvancements.grant(player, "open_misaka_cloud");
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new OpenClientScreenPayload("tutorial"));
        } else {
            handled = false;
        }

        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        if (level.getBlockEntity(clickedPos) instanceof ACMultiblockPartEntity part)
            clickedPos = part.origin();
        final BlockPos pos = clickedPos;
        AbilityState state = AbilityState.load(player);
        boolean handled = false;

        if (level.getBlockEntity(pos) instanceof ACMachineBlockEntity machine) {
            if (level.getBlockState(pos).is(ACBlocks.PHASE_GEN.get())) ACAdvancements.grant(player, "phase_generator");
            if (level.getBlockState(pos).is(ACBlocks.NODE_BASIC.get()) || level.getBlockState(pos).is(ACBlocks.NODE_STANDARD.get())
                    || level.getBlockState(pos).is(ACBlocks.NODE_ADVANCED.get())) ACAdvancements.grant(player, "ac_node");
            if (level.getBlockState(pos).is(ACBlocks.MATRIX.get())) ACAdvancements.grant(player, "ac_matrix");
            ItemStack held = event.getItemStack();
            if (level.getBlockState(pos).is(ACBlocks.DEV_NORMAL.get()) || level.getBlockState(pos).is(ACBlocks.DEV_ADVANCED.get())) {
                handled = true;
                ACAdvancements.grant(player, "ac_developer");
                if (player.isShiftKeyDown()) player.openMenu(machine, buffer -> buffer.writeBlockPos(pos));
                else net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new OpenClientScreenPayload("developer:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()));
            } else if ((machine.kind() == cn.academy.block.MachineKind.IMAG_FUSOR
                    || machine.kind() == cn.academy.block.MachineKind.PHASE_GENERATOR)
                    && held.is(ACItems.MATTER_UNIT.get()) && MatterUnitItem.isFilled(held)) {
                if (machine.fillPhaseLiquid(1_000)) replaceMatterUnit(player, held, false);
                else player.openMenu(machine, buffer -> buffer.writeBlockPos(pos));
                handled = true;
            } else if (held.is(ACItems.ENERGY_UNIT.get()) || held.is(ACItems.DEVELOPER_PORTABLE.get())) {
                handled = true;
                IEnergyStorage itemEnergy = held.getCapability(Capabilities.EnergyStorage.ITEM);
                if (itemEnergy != null) {
                    int rate = held.is(ACItems.ENERGY_UNIT.get()) ? 20 : 300;
                    if (player.isShiftKeyDown()) {
                        int accepted = machine.energy.receiveEnergy(itemEnergy.extractEnergy(rate, true), false);
                        itemEnergy.extractEnergy(accepted, false);
                    } else {
                        int accepted = itemEnergy.receiveEnergy(machine.energy.extractEnergy(rate, true), false);
                        machine.energy.extractEnergy(accepted, false);
                    }
                }
            } else {
                if (player.isShiftKeyDown() && machine.kind() == cn.academy.block.MachineKind.METAL_FORMER) {
                    machine.cycleMode();
                    player.displayClientMessage(Component.translatable("ac.machine.metal_former.mode",
                            Component.translatable("ac.machine.metal_former.mode." + machine.mode())), true);
                } else {
                    player.openMenu(machine, buffer -> buffer.writeBlockPos(pos));
                }
                handled = true;
            }
        } else if (event.getItemStack().is(ACItems.MATTER_UNIT.get())) {
            ItemStack unit = event.getItemStack();
            if (!MatterUnitItem.isFilled(unit) && level.getBlockState(pos).is(ACBlocks.IMAG_PHASE.get())
                    && canHarvest(player, level, pos)) {
                level.removeBlock(pos, false);
                replaceMatterUnit(player, unit, true);
                ACAdvancements.grant(player, "getting_phase");
                ACAdvancements.grant(player, "legacy/default/phase_liquid");
                handled = true;
            } else if (MatterUnitItem.isFilled(unit)) {
                BlockPos target = pos.relative(event.getFace());
                if (level.getBlockState(target).canBeReplaced()
                        && player.mayUseItemAt(target, event.getFace(), unit)) {
                    level.setBlockAndUpdate(target, ACBlocks.IMAG_PHASE.get().defaultBlockState());
                    replaceMatterUnit(player, unit, false);
                    handled = true;
                }
            }
        }

        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level
                && level.getBlockEntity(event.getPos()) instanceof ACMultiblockPartEntity part
                && event.getPlayer() instanceof ServerPlayer player) {
            BlockPos origin = part.origin();
            BlockEvent.BreakEvent originEvent = new BlockEvent.BreakEvent(level, origin,
                    level.getBlockState(origin), player);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(originEvent);
            if (originEvent.isCanceled()) event.setCanceled(true);
        }
        // Inventory drops are handled by ACMachineBlock.onRemove only after the break is actually accepted.
    }

    private static void unlockTutorials(ServerPlayer player, AbilityState state) {
        java.util.function.Predicate<net.minecraft.world.item.Item> owns = item ->
                player.getInventory().items.stream().anyMatch(stack -> stack.is(item))
                        || player.getInventory().offhand.stream().anyMatch(stack -> stack.is(item));
        if (owns.test(ACItems.CONSTRAINT_METAL.get()) || owns.test(ACItems.CRYSTAL_ORE.get())
                || owns.test(ACItems.IMAGSIL_ORE.get()) || owns.test(ACItems.RESO_ORE.get()))
            state.unlockTutorial("ores");
        if (state.terminalInstalled() || owns.test(ACItems.TERMINAL_INSTALLER.get())
                || owns.test(ACItems.APP_SKILL_TREE.get()) || owns.test(ACItems.APP_MEDIA_PLAYER.get())
                || owns.test(ACItems.APP_FREQ_TRANSMITTER.get())) state.unlockTutorial("terminal");
        if (state.hasCategory() || owns.test(ACItems.DEVELOPER_PORTABLE.get()) || owns.test(ACItems.DEV_NORMAL.get())
                || owns.test(ACItems.DEV_ADVANCED.get())) state.unlockTutorial("ability_developer");
        if (owns.test(ACItems.IMAG_FUSOR.get())) state.unlockTutorial("imag_fusor");
        if (owns.test(ACItems.METAL_FORMER.get())) state.unlockTutorial("metal_former");
        if (owns.test(ACItems.PHASE_GEN.get())) state.unlockTutorial("phase_generator");
        if (owns.test(ACItems.SOLAR_GEN.get())) state.unlockTutorial("solar_generator");
        if (owns.test(ACItems.WINDGEN_BASE.get()) || owns.test(ACItems.WINDGEN_MAIN.get())
                || owns.test(ACItems.WINDGEN_PILLAR.get()) || owns.test(ACItems.WINDGEN_FAN.get()))
            state.unlockTutorial("wind_generator");
    }

    private static boolean canHarvest(ServerPlayer player, Level level, BlockPos pos) {
        if (!player.mayUseItemAt(pos, net.minecraft.core.Direction.UP, player.getMainHandItem())) return false;
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakEvent);
        return !breakEvent.isCanceled();
    }

    private static void installApp(ServerPlayer player, AbilityState state, ItemStack stack, String app) {
        if (!state.terminalInstalled()) {
            AbilityManager.deny(player, Component.translatable("ac.terminal.notinstalled"));
        } else if (!state.installApp(app)) {
            player.displayClientMessage(Component.translatable("ac.terminal.app_alrdy_installed",
                    Component.translatable("ac.app." + app + ".name")), true);
        } else {
            state.save(player);
            consume(player, stack);
            player.displayClientMessage(Component.translatable("ac.terminal.app_installed",
                    Component.translatable("ac.app." + app + ".name")), false);
            ACNetwork.sync(player, state);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new OpenClientScreenPayload("notify:app:" + app));
        }
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.isCreative()) stack.shrink(1);
    }


    private static void replaceMatterUnit(ServerPlayer player, ItemStack held, boolean filled) {
        if (held.getCount() <= 1) {
            MatterUnitItem.setFilled(held, filled);
            return;
        }
        held.shrink(1);
        ItemStack converted = new ItemStack(ACItems.MATTER_UNIT.get());
        MatterUnitItem.setFilled(converted, filled);
        if (!player.getInventory().add(converted)) player.drop(converted, false);
    }

    private ACCommonEvents() {}
}
