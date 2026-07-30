package cn.academy.entity;

import cn.academy.ability.AbilityState;
import cn.academy.config.ACConfig;
import cn.academy.network.VisualEffectPayload;
import cn.academy.registry.ACEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;

/** Modern synced counterpart of EntityMdBall used by Electron Bomb. */
public final class ACElectronBallEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);

    private float damage;
    private boolean fired;

    public ACElectronBallEntity(EntityType<? extends ACElectronBallEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static ACElectronBallEntity create(ServerPlayer owner, int life, float damage) {
        ACElectronBallEntity ball = new ACElectronBallEntity(ACEntities.ELECTRON_BALL.get(), owner.level());
        float theta = (float) (-owner.getYRot() / 180f * Math.PI
                + (owner.getRandom().nextFloat() * 2 - 1) * Math.PI * .45);
        float range = .8f + owner.getRandom().nextFloat() * .5f;
        ball.getEntityData().set(OWNER, owner.getId());
        ball.getEntityData().set(OFFSET_X, (float) Math.sin(theta) * range);
        ball.getEntityData().set(OFFSET_Z, (float) Math.cos(theta) * range);
        ball.getEntityData().set(OFFSET_Y, -1.2f + owner.getRandom().nextFloat() * 1.4f);
        ball.getEntityData().set(LIFE, life);
        ball.damage = damage;
        ball.getEntityData().set(MODE, 0);
        ball.followOwner(owner);
        return ball;
    }

    public static ACElectronBallEntity createHeld(ServerPlayer owner, int mode) {
        ACElectronBallEntity ball = create(owner, 1_000_000, 0);
        ball.getEntityData().set(MODE, mode);
        return ball;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, 0);
        builder.define(OFFSET_X, 0f);
        builder.define(OFFSET_Y, 0f);
        builder.define(OFFSET_Z, 0f);
        builder.define(LIFE, 20);
        builder.define(MODE, 0);
    }

    public LivingEntity owner() {
        Entity value = level().getEntity(getEntityData().get(OWNER));
        return value instanceof LivingEntity living ? living : null;
    }

    public int life() { return getEntityData().get(LIFE); }
    public boolean isHeldScatterBall() { return life() > 1_000 && getEntityData().get(MODE) == 1; }
    public boolean isHeldMissileBall() { return life() > 1_000 && getEntityData().get(MODE) == 2; }
    public boolean belongsTo(Entity entity) { return getEntityData().get(OWNER) == entity.getId(); }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        followOwner(owner);
        int life = Math.max(3, life());
        if (!level().isClientSide && !fired && tickCount >= life - 2) {
            fired = true;
            fire(owner);
        }
        if (!level().isClientSide && tickCount >= life) discard();
    }

    private void followOwner(LivingEntity owner) {
        setPos(owner.getX() + getEntityData().get(OFFSET_X), owner.getY() + getEntityData().get(OFFSET_Y),
                owner.getZ() + getEntityData().get(OFFSET_Z));
        setDeltaMovement(Vec3.ZERO);
    }

    private void fire(LivingEntity owner) {
        Vec3 intended = owner.getEyePosition().add(owner.getLookAngle().scale(15));
        fireAt(owner, intended, damage);
    }

    public void fireAt(LivingEntity owner, Vec3 intended, float shotDamage) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        damage = shotDamage;
        Vec3 start = position().add(0, owner.getEyeHeight(), 0);
        HitResult blockHit = server.clip(new ClipContext(start, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = blockHit.getLocation();
        AABB search = new AABB(start, end).inflate(1);
        LivingEntity target = server.getEntitiesOfClass(LivingEntity.class, search,
                        value -> value != owner && value.isAlive() && !(value instanceof Player other
                                && (!ACConfig.ATTACK_PLAYERS.get()
                                || owner instanceof ServerPlayer player && !AbilityState.load(player).attackPlayers())))
                .stream().filter(value -> value.getBoundingBox().inflate(.3).clip(start, end).isPresent())
                .min(Comparator.comparingDouble(value -> value.distanceToSqr(start))).orElse(null);
        if (target != null) {
            target.invulnerableTime = 0;
            target.hurt(owner instanceof Player player ? server.damageSources().playerAttack(player)
                            : server.damageSources().mobAttack(owner),
                    (float) (damage * ACConfig.DAMAGE_SCALE.get()));
            if (owner instanceof ServerPlayer player)
                cn.academy.ability.AbilityExecutor.applyMeltdownerMark(player, target);
        }
        VisualEffectPayload visual = new VisualEffectPayload("meltdowner", start.x, start.y, start.z,
                end.x, end.y, end.z, .055f, 0xD86EFF87, 10);
        for (ServerPlayer viewer : server.players()) if (viewer.distanceToSqr(this) <= 128 * 128)
            PacketDistributor.sendToPlayer(viewer, visual);
        discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
