package cn.academy.command;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.advancement.ACAdvancements;
import cn.academy.network.ACNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ACCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> academy = dispatcher.register(Commands.literal("academy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .then(playerArgument().executes(context -> status(context.getSource(), player(context)))))
                .then(Commands.literal("category")
                        .then(playerArgument()
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AbilityRegistry.categoryIds(), builder))
                                        .executes(context -> category(context.getSource(), player(context),
                                                StringArgumentType.getString(context, "category"))))
                                .then(Commands.literal("clear").executes(context -> clearCategory(context.getSource(), player(context))))))
                .then(Commands.literal("level")
                        .then(playerArgument().then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(context -> level(context.getSource(), player(context),
                                        IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("learn")
                        .then(playerArgument()
                                .then(Commands.literal("all").executes(context -> learnAll(player(context))))
                                .then(skillArgument().executes(context -> learn(context.getSource(), player(context), skillName(context))))))
                .then(Commands.literal("unlearn")
                        .then(playerArgument().then(skillArgument().executes(context ->
                                unlearn(context.getSource(), player(context), skillName(context))))))
                .then(Commands.literal("experience")
                        .then(playerArgument().then(skillArgument()
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(context -> experience(context.getSource(), player(context), skillName(context),
                                                (float) DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("recover")
                        .then(playerArgument().executes(context -> recover(player(context)))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("clear").then(playerArgument().executes(context -> clearCooldowns(player(context))))))
                .then(Commands.literal("maxout")
                        .then(playerArgument().executes(context -> maxout(player(context)))))
                .then(Commands.literal("advancement")
                        .then(playerArgument().then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> grantAdvancement(context.getSource(), player(context),
                                        StringArgumentType.getString(context, "id")))))));

        // Syntax-compatible forms of the 1.7.10 /aim (self) and /aimp (operator target) commands.
        var aim = Commands.literal("aim").requires(source -> source.getEntity() instanceof ServerPlayer
                && (source.hasPermission(2) || ((ServerPlayer) source.getEntity()).isCreative()));
        aim.then(Commands.literal("cat").executes(context -> status(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AbilityRegistry.categoryIds(), builder))
                        .executes(context -> category(context.getSource(), context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "category")))));
        aim.then(Commands.literal("catlist").executes(context -> listCategories(context.getSource())));
        aim.then(Commands.literal("learn").then(skillArgument().executes(context ->
                learn(context.getSource(), context.getSource().getPlayerOrException(), skillName(context)))));
        aim.then(Commands.literal("unlearn").then(skillArgument().executes(context ->
                unlearn(context.getSource(), context.getSource().getPlayerOrException(), skillName(context)))));
        aim.then(Commands.literal("learn_all").executes(context -> learnAll(context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("reset").executes(context -> clearCategory(context.getSource(), context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("fullcp").executes(context -> recover(context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                .executes(context -> level(context.getSource(), context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "level")))));
        aim.then(Commands.literal("exp").then(skillArgument().then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                .executes(context -> experience(context.getSource(), context.getSource().getPlayerOrException(),
                        skillName(context), (float) DoubleArgumentType.getDouble(context, "value"))))));
        aim.then(Commands.literal("cd_clear").executes(context -> clearCooldowns(context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("maxout").executes(context -> maxout(context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("status").executes(context -> status(context.getSource(), context.getSource().getPlayerOrException())));
        aim.then(Commands.literal("cheats_on").executes(context -> 1));
        aim.then(Commands.literal("cheats_off").executes(context -> 1));
        dispatcher.register(aim);

        var aimpTarget = playerArgument();
        aimpTarget.then(Commands.literal("cat").then(Commands.argument("category", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(AbilityRegistry.categoryIds(), builder))
                .executes(context -> category(context.getSource(), player(context), StringArgumentType.getString(context, "category")))));
        aimpTarget.then(Commands.literal("learn").then(skillArgument().executes(context ->
                learn(context.getSource(), player(context), skillName(context)))));
        aimpTarget.then(Commands.literal("unlearn").then(skillArgument().executes(context ->
                unlearn(context.getSource(), player(context), skillName(context)))));
        aimpTarget.then(Commands.literal("learn_all").executes(context -> learnAll(player(context))));
        aimpTarget.then(Commands.literal("reset").executes(context -> clearCategory(context.getSource(), player(context))));
        aimpTarget.then(Commands.literal("fullcp").executes(context -> recover(player(context))));
        aimpTarget.then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                .executes(context -> level(context.getSource(), player(context), IntegerArgumentType.getInteger(context, "level")))));
        aimpTarget.then(Commands.literal("exp").then(skillArgument().then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                .executes(context -> experience(context.getSource(), player(context), skillName(context),
                        (float) DoubleArgumentType.getDouble(context, "value"))))));
        aimpTarget.then(Commands.literal("cd_clear").executes(context -> clearCooldowns(player(context))));
        aimpTarget.then(Commands.literal("maxout").executes(context -> maxout(player(context))));
        aimpTarget.then(Commands.literal("status").executes(context -> status(context.getSource(), player(context))));
        dispatcher.register(Commands.literal("aimp").requires(source -> source.hasPermission(2)).then(aimpTarget));

        dispatcher.register(Commands.literal("acach").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(context -> grantAdvancement(context.getSource(), context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "id")))
                        .then(playerArgument().executes(context -> grantAdvancement(context.getSource(), player(context),
                                StringArgumentType.getString(context, "id"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArgument() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> skillArgument() {
        return Commands.argument("skill", StringArgumentType.word()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(AbilityRegistry.categories().stream()
                        .flatMap(category -> category.skills().stream()).map(AbilitySkill::id), builder));
    }

    private static ServerPlayer player(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }

    private static String skillName(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "skill");
    }

    private static AbilitySkill resolveSkill(AbilityState state, String skillId) {
        return AbilityRegistry.skill(skillId.contains(".") ? skillId : state.category() + "." + skillId);
    }

    private static int listCategories(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(String.join(", ", AbilityRegistry.categoryIds())), false);
        return AbilityRegistry.categoryIds().size();
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        source.sendSuccess(() -> Component.literal(player.getScoreboardName() + ": category=" + state.category()
                + ", level=" + state.level() + ", CP=" + Math.round(state.cp()) + "/" + Math.round(state.maxCp())
                + ", overload=" + Math.round(state.overload()) + "/" + Math.round(state.maxOverload())
                + ", active=" + state.active() + ", learned=" + state.learned().size()), false);
        return 1;
    }

    private static int category(CommandSourceStack source, ServerPlayer player, String category) {
        if (AbilityRegistry.category(category) == null) {
            source.sendFailure(Component.literal("Unknown AcademyCraft category: " + category));
            return 0;
        }
        AbilityState state = AbilityState.load(player);
        state.setCategory(category);
        saveSync(player, state);
        ACAdvancements.grantLegacyLevels(player, category, 1);
        source.sendSuccess(() -> Component.literal("Set " + player.getScoreboardName() + " category to " + category), true);
        return 1;
    }

    private static int clearCategory(CommandSourceStack source, ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.clearCategory();
        saveSync(player, state);
        source.sendSuccess(() -> Component.literal("Cleared " + player.getScoreboardName() + " category"), true);
        return 1;
    }

    private static int level(CommandSourceStack source, ServerPlayer player, int level) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) {
            source.sendFailure(Component.literal("Player has no AcademyCraft category"));
            return 0;
        }
        state.setLevel(level);
        saveSync(player, state);
        ACAdvancements.grantLegacyLevels(player, state.category(), level);
        return 1;
    }

    private static int learnAll(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) return 0;
        state.learnAll();
        saveSync(player, state);
        return 1;
    }

    private static int learn(CommandSourceStack source, ServerPlayer player, String skillId) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (skill == null || !skill.category().equals(state.category())) {
            source.sendFailure(Component.literal("Unknown skill for player's category: " + skillId));
            return 0;
        }
        state.learn(skill);
        saveSync(player, state);
        ACAdvancements.grantLegacySkillLearn(player, skill.category(), skill.name());
        return 1;
    }

    private static int unlearn(CommandSourceStack source, ServerPlayer player, String skillId) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (!state.unlearn(skill)) {
            source.sendFailure(Component.literal("Skill was not learned: " + skillId));
            return 0;
        }
        saveSync(player, state);
        return 1;
    }

    private static int experience(CommandSourceStack source, ServerPlayer player, String skillId, float value) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (!state.setExperience(skill, value)) {
            source.sendFailure(Component.literal("Skill was not learned: " + skillId));
            return 0;
        }
        saveSync(player, state);
        return 1;
    }

    private static int recover(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) return 0;
        state.recoverAll();
        saveSync(player, state);
        return 1;
    }

    private static int clearCooldowns(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.clearCooldowns();
        saveSync(player, state);
        return 1;
    }

    private static int maxout(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.maxOutLevelProgress();
        saveSync(player, state);
        return 1;
    }

    private static void saveSync(ServerPlayer player, AbilityState state) {
        state.save(player);
        ACNetwork.sync(player, state);
    }

    private static int grantAdvancement(CommandSourceStack source, ServerPlayer player, String id) {
        String requested = id.startsWith("academy:") ? id.substring("academy:".length()) : id;
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(requested);
        candidates.add(requested.replace('.', '/'));
        candidates.add("legacy/default/" + requested);
        for (String category : AbilityRegistry.categoryIds()) {
            candidates.add("legacy/" + category + "/" + requested);
            if (requested.startsWith(category + "."))
                candidates.add("legacy/" + category + "/" + requested.substring(category.length() + 1));
        }
        for (String path : candidates) {
            if (player.server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "academy", path)) != null) {
                ACAdvancements.grant(player, path);
                source.sendSuccess(() -> Component.literal("Granted AcademyCraft advancement " + path), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("Unknown AcademyCraft advancement: " + requested));
        return 0;
    }

    private ACCommands() {}
}
