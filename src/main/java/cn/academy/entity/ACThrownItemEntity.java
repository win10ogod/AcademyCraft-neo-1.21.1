package cn.academy.entity;

import cn.academy.registry.ACEntities;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACSounds;
import cn.academy.registry.ACParticles;
import cn.academy.ability.AbilityState;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class ACThrownItemEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_HOOKED = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HOOK_FACE = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.INT);
    private int hookedTicks;
    private double coinStartY;
    private double coinMaxY;
    private BlockPos hookAnchor;

    public boolean isHooked() { return getEntityData().get(DATA_HOOKED); }

    private void setHooked(boolean hooked) { getEntityData().set(DATA_HOOKED, hooked); }
    public net.minecraft.core.Direction hookFace() {
        return net.minecraft.core.Direction.from3DDataValue(getEntityData().get(DATA_HOOK_FACE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HOOKED, false);
        builder.define(DATA_HOOK_FACE, net.minecraft.core.Direction.DOWN.get3DDataValue());
    }

    public ACThrownItemEntity(EntityType<? extends ACThrownItemEntity> type, Level level) {
        super(type, level);
    }

    public ACThrownItemEntity(LivingEntity owner, Level level, ItemStack item) {
        super(ACEntities.THROWN_ITEM.get(), owner, level);
        setItem(item.copyWithCount(1));
        if (item.is(ACItems.COIN.get())) coinStartY = coinMaxY = owner.getY();
    }

    @Override
    protected Item getDefaultItem() {
        return ACItems.COIN.get();
    }

    @Override
    protected double getDefaultGravity() {
        return getItem().is(ACItems.MAG_HOOK.get()) ? .015 : .04;
    }

    @Override
    public void tick() {
        if (getItem().is(ACItems.COIN.get())) {
            super.tick();
            coinMaxY = Math.max(coinMaxY, getY());
            if (getOwner() instanceof Player owner) {
                setPos(owner.getX(), getY(), owner.getZ());
                if (tickCount > 120 || getDeltaMovement().y < 0 && getY() <= owner.getY() + .15)
                    returnCoin(owner);
            } else if (tickCount > 120) discard();
            return;
        }
        if (!isHooked()) {
            super.tick();
            return;
        }
        hookedTicks++;
        setDeltaMovement(0, 0, 0);
        if (!level().isClientSide && hookAnchor != null && level().getBlockState(hookAnchor).isAir())
            returnItemAndDiscard();
    }

    public double coinProgress() {
        if (!getItem().is(ACItems.COIN.get())) return 0;
        double velocity = getDeltaMovement().y;
        if (velocity > 0) return Math.max(0, Math.min(.5, (.92 - velocity) / .92 * .5));
        double height = Math.max(.01, coinMaxY - coinStartY);
        return Math.max(.5, Math.min(1, .5 + (coinMaxY - getY()) / height * .5));
    }

    public void consumeForRailgun() { discard(); }


    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide) return;
        ItemStack item = getItem();
        if (item.is(ACItems.COIN.get())) {
            if (getOwner() instanceof Player owner) returnCoin(owner); else discard();
            return;
        }
        float damage = item.is(ACItems.SILBARN.get()) ? 0f : item.is(ACItems.COIN.get()) ? 2f : 4f;
        if (damage > 0) hit.getEntity().hurt(level().damageSources().thrown(this, getOwner()), damage);
        if (item.is(ACItems.MAG_HOOK.get())) {
            returnItemAndDiscard();
        } else {
            if (item.is(ACItems.SILBARN.get())) {
                boolean heavy = hit.getEntity() instanceof ACThrownItemEntity other
                        && other.getItem().is(ACItems.SILBARN.get());
                level().playSound(null, blockPosition(), ACSounds.get(heavy
                        ? "entity.silbarn_heavy" : "entity.silbarn_light"), SoundSource.PLAYERS, 1, 1);
                silbarnFragments();
            }
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide) return;
        if (getItem().is(ACItems.COIN.get())) {
            if (getOwner() instanceof Player owner) returnCoin(owner); else discard();
        } else if (getItem().is(ACItems.MAG_HOOK.get())) {
            setHooked(true);
            hookAnchor = hit.getBlockPos().immutable();
            getEntityData().set(DATA_HOOK_FACE, hit.getDirection().get3DDataValue());
            setNoGravity(true);
            setDeltaMovement(0, 0, 0);
            setPos(hit.getLocation());
        } else {
            if (getItem().is(ACItems.SILBARN.get())) {
                level().playSound(null, blockPosition(), ACSounds.get("entity.silbarn_light"), SoundSource.PLAYERS, 1, 1);
                silbarnFragments();
            }
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return isHooked();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isHooked()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            ItemStack returned = getItem().copyWithCount(1);
            if (!player.getInventory().add(returned)) spawnAtLocation(returned);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isHooked() && source.getEntity() instanceof Player) {
            returnItemAndDiscard();
            return true;
        }
        return super.hurt(source, amount);
    }

    private void returnCoin(Player player) {
        if (level().isClientSide || isRemoved()) return;
        ItemStack returned = getItem().copyWithCount(1);
        if (!player.isCreative() && !player.getInventory().add(returned)) spawnAtLocation(returned);
        AbilityState state = AbilityState.load(player);
        if (state.coinFlip()) player.displayClientMessage(Component.translatable(
                player.getRandom().nextBoolean() ? "ac.coin.heads" : "ac.coin.tails"), true);
        discard();
    }

    public void breakSilbarn() {
        if (level().isClientSide || !getItem().is(ACItems.SILBARN.get()) || isRemoved()) return;
        level().playSound(null, blockPosition(), ACSounds.get("entity.silbarn_heavy"),
                SoundSource.PLAYERS, 1, 1);
        silbarnFragments();
        discard();
    }

    private void silbarnFragments() {
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(ACParticles.SILBARN_FRAGMENT.get(), getX(), getY(), getZ(), 24,
                    .22, .22, .22, .12);
        }
    }

    private void returnItemAndDiscard() {
        if (!level().isClientSide && getItem().is(ACItems.MAG_HOOK.get())) spawnAtLocation(getItem().copyWithCount(1));
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Hooked", isHooked());
        tag.putInt("HookedTicks", hookedTicks);
        tag.putDouble("CoinStartY", coinStartY);
        tag.putDouble("CoinMaxY", coinMaxY);
        if (hookAnchor != null) tag.putLong("HookAnchor", hookAnchor.asLong());
        tag.putInt("HookFace", getEntityData().get(DATA_HOOK_FACE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHooked(tag.getBoolean("Hooked"));
        hookedTicks = tag.getInt("HookedTicks");
        coinStartY = tag.getDouble("CoinStartY");
        coinMaxY = tag.getDouble("CoinMaxY");
        hookAnchor = tag.contains("HookAnchor") ? BlockPos.of(tag.getLong("HookAnchor")) : null;
        getEntityData().set(DATA_HOOK_FACE, tag.getInt("HookFace"));
    }
}
