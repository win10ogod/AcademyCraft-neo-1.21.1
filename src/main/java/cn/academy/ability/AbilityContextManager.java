package cn.academy.ability;

import cn.academy.network.AbilityContextSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative reconstruction of the 1.12.2 Context/KeyDelegate lifecycle.
 * Key-down creates a context, server ticks own all consumption, and key-up either performs or terminates it.
 */
public final class AbilityContextManager {
    private enum Mode { INSTANT, HOLD, CONTINUOUS, TOGGLE }

    private static final Map<UUID, Map<Integer, Session>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> MOVEMENT_INPUT = new HashMap<>();

    private static final class Session {
        final int slot;
        final AbilitySkill skill;
        final Mode mode;
        int ticks;
        float value;
        int movementDirection;

        Session(int slot, AbilitySkill skill, Mode mode, float value) {
            this.slot = slot;
            this.skill = skill;
            this.mode = mode;
            this.value = value;
        }
    }

    public static void keyDown(ServerPlayer player, AbilityState state, int slot) {
        if (slot < 0 || slot >= 4) return;
        AbilitySkill skill = AbilityRegistry.skill(state.preset(slot));
        if (skill == null) {
            AbilityManager.deny(player, Component.translatable("ac.ability.empty_preset", slot + 1));
            return;
        }
        if (skill.name().equals("railgun") && AbilityExecutor.hasReadyRailgunCoin(player)) {
            float exp = state.experience(skill.id());
            if (state.consumeRaw(skill, lerp(200, 450, exp), lerp(180, 120, exp), player.isCreative())) {
                AbilityExecutor.execute(player, state, skill);
                state.finishContext(skill, Math.round(lerp(300, 160, exp)), .005f);
                AbilityManager.completeUse(player, state, skill);
            } else denyUse(player, state, skill);
            return;
        }
        Mode mode = skill.name().equals("railgun") && AbilityExecutor.hasRailgunChargeItem(player)
                ? Mode.HOLD : mode(skill.name());
        if (skill.name().equals("location_teleport")) {
            AbilityManager.usePreset(player, state, slot);
            return;
        }
        if (mode == Mode.INSTANT) {
            performInstant(player, state, skill);
            return;
        }

        Map<Integer, Session> sessions = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (Session existing : sessions.values().toArray(Session[]::new)) {
            if (existing.slot == slot || !existing.skill.id().equals(skill.id())) continue;
            if (mode == Mode.TOGGLE) {
                sessions.remove(existing.slot);
                terminate(player, state, existing, false, false);
                cleanup(player);
            } else AbilityManager.deny(player, Component.translatable("ac.ability.context.active"));
            return;
        }
        Session old = sessions.get(slot);
        if (old != null) {
            if (mode == Mode.TOGGLE && old.skill.id().equals(skill.id())) {
                terminate(player, state, old, false, false);
                sessions.remove(slot);
                cleanup(player);
                return;
            }
            terminate(player, state, old, true, false);
            sessions.remove(slot);
        }

        if (!AbilityExecutor.validate(player, skill)) {
            AbilityManager.deny(player, Component.translatable("ac.ability.missing_reagent", skill.displayName()));
            cleanup(player);
            return;
        }
        if (!state.canStartContext(skill)) {
            denyUse(player, state, skill);
            cleanup(player);
            return;
        }

        float exp = state.experience(skill.id());
        float[] initial = initialCost(skill.name(), exp);
        if (!state.consumeRaw(skill, initial[0], initial[1], player.isCreative())) {
            AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
            cleanup(player);
            return;
        }
        float value = skill.name().equals("penetrate_teleport") ? lerp(10, 35, exp) : 0;
        Session session = new Session(slot, skill, mode, value);
        sessions.put(slot, session);
        syncSession(player, state, session);
        AbilityExecutor.startContext(player, state, skill);
    }

    public static void keyUp(ServerPlayer player, AbilityState state, int slot, boolean abort) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        Session session = sessions.get(slot);
        if (session == null || session.mode == Mode.TOGGLE) return;
        sessions.remove(slot);
        terminate(player, state, session, abort, false);
        cleanup(player);
    }

    public static void mouseWheel(ServerPlayer player, AbilityState state, int delta) {
        if (!state.mouseWheelTeleport() || delta == 0) return;
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values()) {
            if (session.skill.name().equals("penetrate_teleport")) {
                float max = lerp(10, 35, state.experience(session.skill.id()));
                session.value = Math.max(.5f, Math.min(max, session.value + Math.signum(delta)));
                return;
            }
        }
    }

    public static void tick(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null || sessions.isEmpty()) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!state.canUse() || !state.learned().contains(session.skill.id())
                    || !AbilityExecutor.contextAlive(player, session.skill)) {
                sessions.remove(session.slot);
                terminate(player, state, session, true, false);
                continue;
            }
            session.ticks++;
            float exp = state.experience(session.skill.id());
            String name = session.skill.name();
            float cp = tickCp(name, exp, session.ticks);
            float overload = tickOverload(name, exp, session.ticks);
            if ((cp > 0 || overload > 0) && !state.consumeRaw(session.skill, cp, overload, player.isCreative())) {
                sessions.remove(session.slot);
                boolean autoPerform = autoPerforms(name, session.ticks, exp);
                terminate(player, state, session, !autoPerform, autoPerform);
                continue;
            }

            int currentMovement = movementDirection(player);
            AbilityExecutor.tickContext(player, state, session.skill, session.ticks,
                    currentMovement, session.value);
            if (session.ticks % 3 == 0) syncSession(player, state, session);

            if (name.equals("flashing")) {
                if (session.movementDirection != 0 && currentMovement == 0) {
                    AbilityExecutor.performContextStep(player, state, session.skill, session.movementDirection);
                    state.addExperience(session.skill, .002f);
                }
                session.movementDirection = currentMovement;
            }

            int limit = lifetime(name, exp);
            if (limit > 0 && session.ticks >= limit) {
                sessions.remove(session.slot);
                boolean perform = autoPerforms(name, session.ticks, exp);
                if (name.equals("scatter_bomb") && session.ticks >= 200 && !player.isCreative())
                    player.hurt(player.damageSources().magic(), 6);
                terminate(player, state, session, !perform, perform);
            }
        }
        cleanup(player);
    }

    public static boolean terminateToggleContexts(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return false;
        boolean terminated = false;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (session.mode == Mode.TOGGLE) {
                sessions.remove(session.slot);
                terminate(player, state, session, false, false);
                terminated = true;
            }
        }
        cleanup(player);
        return terminated;
    }

    public static void abortAll(ServerPlayer player, AbilityState state) {
        MOVEMENT_INPUT.remove(player.getUUID());
        Map<Integer, Session> sessions = ACTIVE.remove(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values()) terminate(player, state, session, true, false);
    }

    public static boolean hasContext(ServerPlayer player, String skillName) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        return sessions != null && sessions.values().stream().anyMatch(s -> s.skill.name().equals(skillName));
    }

    private static void terminate(ServerPlayer player, AbilityState state, Session session,
                                  boolean abort, boolean perform) {
        PacketDistributor.sendToPlayer(player, new AbilityContextSyncPayload(session.slot, session.skill.id(),
                session.ticks, 0, AbilityContextSyncPayload.ENDED));
        String name = session.skill.name();
        float exp = state.experience(session.skill.id());
        boolean didPerform = false;
        boolean effectiveAction = true;
        // Scatter Bomb fires every accumulated ball whenever its context terminates,
        // including key-abort or CP exhaustion (but never after the caster dies).
        if (name.equals("scatter_bomb") && session.ticks >= 20 && player.isAlive()) perform = true;

        if (!abort && session.mode == Mode.HOLD && !perform) {
            perform = canRelease(name, session.ticks, exp);
            if (name.equals("vec_accel") && exp <= .5f && !hasGroundBelow(player)) perform = false;
        } else if (!abort && session.mode == Mode.CONTINUOUS) {
            perform = name.equals("scatter_bomb") && session.ticks >= 20;
        }

        if (perform) {
            float[] finalCost = releaseCost(name, exp, session.ticks, session.value);
            boolean validFinal = true;
            if (name.equals("penetrate_teleport")) {
                session.value = (float) AbilityExecutor.penetrateAllowedRange(state, session.skill, session.value);
                finalCost[0] = (float) (AbilityExecutor.penetratingTravelDistance(player, session.value)
                        * lerp(14, 9, exp));
            } else if (name.equals("mark_teleport")) {
                session.value = (float) AbilityExecutor.markAllowedRange(state, session.skill, session.ticks);
                double distance = AbilityExecutor.markTravelDistance(player, session.value);
                validFinal = distance >= 3;
                finalCost[0] = (float) (distance * lerp(12, 4, exp));
            } else if (name.equals("flesh_ripping")) {
                validFinal = AbilityExecutor.hasFleshTarget(player, state, session.skill);
            } else if (name.equals("ground_shock")) {
                validFinal = player.onGround();
            } else if (name.equals("blood_retro")) {
                validFinal = AbilityExecutor.hasBloodRetroTarget(player);
            }
            boolean forced = name.equals("penetrate_teleport") || name.equals("mark_teleport")
                    || name.equals("flesh_ripping");
            if (validFinal && (forced ? state.consumeForce(session.skill, finalCost[0], finalCost[1], player.isCreative())
                    : state.consumeRaw(session.skill, finalCost[0], finalCost[1], player.isCreative()))) {
                if (name.equals("dir_shock")) effectiveAction = AbilityExecutor.hasDirectedShockTarget(player);
                AbilityExecutor.executeContext(player, state, session.skill, session.ticks, session.value);
                didPerform = true;
            }
        }

        int cooldown = name.equals("dir_shock") && !effectiveAction ? 0
                : cooldown(name, exp, session.ticks, didPerform);
        float gain = didPerform ? experienceGain(name, session.ticks) : continuousExperience(name, session.ticks, exp);
        if (cooldown > 0 || gain > 0) state.finishContext(session.skill, cooldown, gain);
        AbilityExecutor.endContext(player, session.skill);
        if (didPerform || session.mode == Mode.CONTINUOUS || session.mode == Mode.TOGGLE)
            AbilityManager.completeUse(player, state, session.skill);
    }

    private static void performInstant(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        if (!AbilityExecutor.validate(player, skill)) {
            AbilityManager.deny(player, Component.translatable("ac.ability.missing_reagent", skill.displayName()));
            return;
        }
        if (!state.canStartContext(skill)) {
            denyUse(player, state, skill);
            return;
        }
        float exp = state.experience(skill.id());
        float[] cost = switch (skill.name()) {
            case "arc_gen" -> cost(lerp(30, 70, exp), lerp(18, 11, exp));
            case "mine_detect" -> cost(lerp(1500, 1000, exp), lerp(200, 180, exp));
            case "thunder_bolt" -> cost(lerp(280, 420, exp), lerp(50, 27, exp));
            case "electron_bomb" -> cost(0, 0);
            case "ray_barrage" -> cost(lerp(450, 380, exp), lerp(300, 140, exp));
            default -> cost(skill.cpCost(), skill.overload());
        };
        if (!state.consumeRaw(skill, cost[0], cost[1], player.isCreative())) {
            AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
            return;
        }
        AbilityExecutor.execute(player, state, skill);
        int cooldown = switch (skill.name()) {
            case "arc_gen" -> Math.round(lerp(15, 5, exp));
            case "mine_detect" -> Math.round(lerp(900, 400, exp));
            case "thunder_bolt" -> Math.round(lerp(120, 50, exp));
            case "electron_bomb" -> Math.round(lerp(20, 10, exp));
            case "ray_barrage" -> Math.round(lerp(100, 40, exp));
            default -> state.cooldownFor(skill);
        };
        float gain = switch (skill.name()) {
            case "mine_detect" -> .008f;
            case "electron_bomb", "ray_barrage" -> .005f;
            case "arc_gen", "thunder_bolt" -> 0;
            default -> .003f;
        };
        state.finishContext(skill, cooldown, gain);
        AbilityManager.completeUse(player, state, skill);
    }

    private static Mode mode(String name) {
        return switch (name) {
            case "arc_gen", "mine_detect", "thunder_bolt", "railgun", "electron_bomb", "ray_barrage" -> Mode.INSTANT;
            case "charging", "mag_movement", "scatter_bomb", "light_shield",
                    "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "electron_missile" -> Mode.CONTINUOUS;
            case "flashing", "vec_deviation", "vec_reflection", "storm_wing" -> Mode.TOGGLE;
            default -> Mode.HOLD;
        };
    }

    private static float[] initialCost(String name, float exp) {
        return switch (name) {
            case "charging" -> cost(0, lerp(65, 48, exp));
            case "mag_movement" -> cost(0, lerp(60, 30, exp));
            case "scatter_bomb" -> cost(0, lerp(80, 60, exp));
            case "light_shield" -> cost(0, lerp(110, 60, exp));
            case "body_intensify" -> cost(0, lerp(200, 120, exp));
            case "thunder_clap" -> cost(0, lerp(390, 252, exp));
            case "meltdowner" -> cost(0, lerp(200, 170, exp));
            case "mine_ray_basic" -> cost(0, lerp(200, 150, exp));
            case "mine_ray_expert" -> cost(0, lerp(300, 200, exp));
            case "mine_ray_luck" -> cost(0, lerp(350, 300, exp));
            case "electron_missile" -> cost(0, 200);
            case "vec_deviation" -> cost(0, lerp(80, 50, exp));
            case "vec_reflection" -> cost(0, lerp(350, 250, exp));
            case "plasma_cannon" -> cost(0, lerp(500, 400, exp));
            case "flashing" -> cost(lerp(80, 60, exp), lerp(250, 180, exp));
            default -> cost(0, 0);
        };
    }

    private static float tickCp(String name, float exp, int ticks) {
        return switch (name) {
            case "charging" -> lerp(3, 7, exp);
            case "mag_movement" -> lerp(15, 8, exp);
            case "scatter_bomb" -> ticks <= 80 ? lerp(3, 6, exp) : 0;
            case "light_shield" -> lerp(9, 4, exp);
            case "body_intensify" -> lerp(20, 15, exp);
            case "thunder_clap" -> ticks <= 40 ? lerp(18, 25, exp) : 0;
            case "meltdowner" -> lerp(10, 15, exp);
            case "mine_ray_basic" -> lerp(12, 7, exp);
            case "mine_ray_expert" -> lerp(25, 15, exp);
            case "mine_ray_luck" -> lerp(50, 35, exp);
            case "electron_missile" -> lerp(12, 5, exp);
            case "vec_deviation" -> lerp(18, 7.5f, exp);
            case "vec_reflection" -> lerp(15, 11, exp);
            case "plasma_cannon" -> ticks < Math.round(lerp(60, 30, exp)) ? lerp(18, 25, exp) : 0;
            case "storm_wing" -> ticks > Math.round(lerp(70, 30, exp)) ? lerp(40, 25, exp) : 0;
            default -> 0;
        };
    }

    private static float tickOverload(String name, float exp, int ticks) {
        return switch (name) {
            case "vec_deviation" -> lerp(.5f, .2f, exp);
            case "storm_wing" -> ticks > Math.round(lerp(70, 30, exp)) ? lerp(10, 7, exp) : 0;
            default -> 0;
        };
    }

    private static float[] releaseCost(String name, float exp, int ticks, float value) {
        return switch (name) {
            case "railgun" -> cost(lerp(200, 450, exp), lerp(180, 120, exp));
            case "mag_manip" -> cost(lerp(140, 270, exp), lerp(35, 20, exp));
            case "threatening_teleport" -> cost(lerp(35, 100, exp), lerp(18, 10, exp));
            case "penetrate_teleport" -> cost(value * lerp(14, 9, exp), lerp(80, 50, exp));
            case "mark_teleport" -> cost(Math.min((ticks + 1) * 2, lerp(25, 60, exp)) * lerp(12, 4, exp), lerp(40, 20, exp));
            case "flesh_ripping" -> cost(lerp(130, 270, exp), lerp(60, 50, exp));
            case "shift_tp" -> cost(lerp(260, 320, exp), lerp(40, 30, exp));
            case "jet_engine" -> cost(lerp(60, 50, exp), lerp(170, 140, exp));
            case "dir_shock" -> cost(lerp(50, 100, exp), lerp(18, 12, exp));
            case "ground_shock" -> cost(lerp(80, 150, exp), lerp(15, 10, exp));
            case "vec_accel" -> cost(lerp(120, 80, exp), lerp(30, 15, exp));
            case "dir_blast" -> cost(lerp(160, 200, exp), lerp(50, 30, exp));
            case "blood_retro" -> cost(lerp(280, 350, exp), lerp(55, 40, exp));
            default -> cost(0, 0);
        };
    }

    private static boolean canRelease(String name, int ticks, float exp) {
        return switch (name) {
            case "railgun" -> ticks >= 20;
            case "body_intensify" -> ticks >= 10 && ticks < 100;
            case "thunder_clap" -> false; // The 1.12.2 context fires only at tick 60 or on CP exhaustion.
            case "meltdowner" -> ticks >= 20 && ticks <= 100;
            case "dir_shock", "dir_blast" -> ticks > 6 && ticks < 50;
            case "ground_shock" -> ticks >= 5;
            case "plasma_cannon" -> ticks >= Math.round(lerp(60, 30, exp));
            default -> true;
        };
    }

    private static int lifetime(String name, float exp) {
        return switch (name) {
            case "scatter_bomb" -> 200;
            case "light_shield" -> Math.round(lerp(120, 180, exp));
            case "body_intensify", "meltdowner" -> 100;
            case "thunder_clap" -> 60;
            case "electron_missile" -> Math.round(lerp(80, 200, exp));
            case "dir_shock", "dir_blast" -> 200;
            case "blood_retro" -> 30;
            case "railgun" -> 20;
            case "flashing" -> Math.round(lerp(60, 150, exp));
            default -> 0;
        };
    }

    private static boolean autoPerforms(String name, int ticks, float exp) {
        return name.equals("thunder_clap") && ticks >= 40 || name.equals("blood_retro") && ticks >= 30
                || name.equals("railgun") && ticks >= 20;
    }

    private static int cooldown(String name, float exp, int ticks, boolean performed) {
        return switch (name) {
            case "light_shield" -> Math.round(lerp(2 * ticks, ticks, exp));
            case "mine_ray_basic" -> Math.round(lerp(40, 20, exp));
            case "mine_ray_expert", "mine_ray_luck" -> Math.round(lerp(60, 30, exp));
            case "electron_missile" -> Math.round(lerp(700, 400, exp));
            case "storm_wing" -> Math.round(lerp(30, 10, exp));
            case "flashing" -> Math.round(lerp(900, 400, exp));
            default -> performed ? switch (name) {
                case "railgun" -> Math.round(lerp(300, 160, exp));
                case "mag_manip" -> Math.round(lerp(60, 40, exp));
                case "body_intensify" -> Math.round(lerp(900, 600, exp));
                case "thunder_clap" -> Math.round(ticks * lerp(10, 6, exp));
                case "meltdowner" -> Math.round(lerp(.8f, 1.2f, (Math.min(ticks, 40) - 20) / 20f) * 20 * lerp(15, 7, exp));
                case "threatening_teleport" -> Math.round(lerp(30, 15, exp));
                case "penetrate_teleport" -> Math.round(lerp(50, 30, exp));
                case "mark_teleport" -> Math.round(lerp(30, 0, exp));
                case "flesh_ripping" -> Math.round(lerp(90, 40, exp));
                case "shift_tp" -> Math.round(lerp(100, 60, exp));
                case "jet_engine" -> Math.round(lerp(60, 30, exp));
                case "dir_shock" -> Math.round(lerp(60, 20, exp));
                case "ground_shock" -> Math.round(lerp(80, 40, exp));
                case "vec_accel" -> Math.round(lerp(80, 50, exp));
                case "dir_blast" -> Math.round(lerp(80, 50, exp));
                case "blood_retro" -> Math.round(lerp(90, 40, exp));
                case "plasma_cannon" -> Math.round(lerp(1000, 600, exp));
                default -> 0;
            } : 0;
        };
    }

    private static float experienceGain(String name, int ticks) {
        return switch (name) {
            case "railgun" -> .005f;
            case "body_intensify" -> .01f;
            case "thunder_clap" -> .003f;
            case "meltdowner" -> .002f * Math.max(.8f, Math.min(1.2f, .8f + (Math.min(ticks, 40) - 20) / 50f));
            case "mag_manip", "flesh_ripping" -> .005f;
            case "threatening_teleport", "penetrate_teleport", "mark_teleport", "shift_tp" -> 0;
            case "jet_engine" -> .004f;
            case "dir_shock" -> 0;
            case "ground_shock" -> 0;
            case "vec_accel", "blood_retro" -> .002f;
            case "dir_blast" -> 0;
            case "plasma_cannon" -> .008f;
            case "scatter_bomb" -> 0;
            default -> .003f;
        };
    }

    private static float continuousExperience(String name, int ticks, float exp) {
        return switch (name) {
            case "mag_movement" -> 0;
            case "storm_wing" -> Math.max(0, ticks - Math.round(lerp(70, 30, exp))) * .00005f;
            default -> 0;
        };
    }

    private static void syncSession(ServerPlayer player, AbilityState state, Session session) {
        float exp = state.experience(session.skill.id());
        int target = chargeTarget(session.skill.name(), exp);
        int contextState = target > 0 && session.ticks < target
                ? AbilityContextSyncPayload.CHARGING : AbilityContextSyncPayload.ACTIVE;
        PacketDistributor.sendToPlayer(player, new AbilityContextSyncPayload(session.slot, session.skill.id(),
                session.ticks, target, contextState));
    }

    private static int chargeTarget(String name, float exp) {
        return switch (name) {
            case "railgun" -> 20;
            case "body_intensify" -> 40;
            case "thunder_clap" -> 40;
            case "meltdowner" -> 40;
            case "dir_shock", "dir_blast" -> 6;
            case "ground_shock" -> 5;
            case "vec_accel" -> 20;
            case "blood_retro" -> 30;
            case "plasma_cannon" -> Math.round(lerp(60, 30, exp));
            case "storm_wing" -> Math.round(lerp(70, 30, exp));
            default -> 0;
        };
    }

    private static boolean hasGroundBelow(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 start = player.position();
        net.minecraft.world.phys.HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                start, start.add(0, -2, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    public static void updateMovementInput(ServerPlayer player, int bits) {
        if ((bits & 15) == 0) MOVEMENT_INPUT.remove(player.getUUID());
        else MOVEMENT_INPUT.put(player.getUUID(), bits & 15);
    }

    private static int movementDirection(ServerPlayer player) {
        int bits = MOVEMENT_INPUT.getOrDefault(player.getUUID(), 0);
        if ((bits & 1) != 0) return 1;
        if ((bits & 2) != 0) return 2;
        if ((bits & 4) != 0) return 3;
        if ((bits & 8) != 0) return 4;
        return 0;
    }

    private static void denyUse(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        Component reason = state.interfered() ? Component.translatable("ac.ability.interfered")
                : !state.active() ? Component.translatable("ac.ability.not_activated")
                : state.overloadLocked() ? Component.translatable("ac.ability.overload_locked")
                : state.cooldown(skill.id()) > 0
                ? Component.translatable("ac.ability.cooldown", state.cooldown(skill.id()) / 20f)
                : Component.translatable("ac.ability.insufficient_cp");
        AbilityManager.deny(player, reason);
    }

    private static float[] cost(float cp, float overload) { return new float[]{cp, overload}; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }
    private static void cleanup(ServerPlayer player) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions != null && sessions.isEmpty()) ACTIVE.remove(player.getUUID());
    }

    private AbilityContextManager() { }
}
