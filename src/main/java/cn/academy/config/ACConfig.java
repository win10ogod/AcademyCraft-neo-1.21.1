package cn.academy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Values mirrored from the original default.conf where they still apply. */
public final class ACConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue PROGRESSION_SCALE;
    public static final ModConfigSpec.IntValue CP_RECOVERY_DELAY;
    public static final ModConfigSpec.DoubleValue CP_RECOVERY_SCALE;
    public static final ModConfigSpec.IntValue OVERLOAD_RECOVERY_DELAY;
    public static final ModConfigSpec.DoubleValue OVERLOAD_RECOVERY_SCALE;
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ModConfigSpec.BooleanValue ATTACK_PLAYERS;
    public static final ModConfigSpec.BooleanValue GIVE_MISAKA_TERMINAL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("ability");
        DAMAGE_SCALE = builder.comment("Global skill damage multiplier.")
                .defineInRange("damageScale", 1.0, 0.0, 100.0);
        PROGRESSION_SCALE = builder.comment("Global skill and level progression multiplier.")
                .defineInRange("progressionScale", 1.0, 0.0, 100.0);
        CP_RECOVERY_DELAY = builder.defineInRange("cpRecoveryDelay", 15, 0, 1200);
        CP_RECOVERY_SCALE = builder.defineInRange("cpRecoveryScale", 1.0, 0.0, 100.0);
        OVERLOAD_RECOVERY_DELAY = builder.defineInRange("overloadRecoveryDelay", 32, 0, 1200);
        OVERLOAD_RECOVERY_SCALE = builder.defineInRange("overloadRecoveryScale", 1.0, 0.0, 100.0);
        DESTROY_BLOCKS = builder.comment("Whether destructive skills may break blocks.")
                .define("destroyBlocks", true);
        ATTACK_PLAYERS = builder.comment("Whether skills can directly damage another player.")
                .define("attackPlayers", true);
        builder.pop();
        GIVE_MISAKA_TERMINAL = builder.comment("Give each new player the original MisakaCloud tutorial terminal.")
                .define("giveMisakaCloudTerminal", true);
        SPEC = builder.build();
    }

    private ACConfig() {}
}
