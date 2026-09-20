/*
 * MyMenu - interactive chest-inventory menus for Paper
 * Copyright (C) 2026 ChaddTheMan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.chaddtheman.mymenu.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.chaddtheman.mymenu.model.MatchMode;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.service.MutationResult;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code /mymenu} Brigadier tree: the only place Brigadier appears.
 *
 * <h2>Brigadier stays at the edge</h2>
 *
 * This class parses arguments, turns them into the types the subcommands want (a {@link Menu}
 * looked up from the registry, a {@link Player}, a {@link MatchMode}, a validated new name),
 * gates each node with {@code requires}, and supplies suggestions. The subcommand classes receive
 * those finished values and never see a string array, a permission node or a Brigadier type. 1.x
 * had thirteen command classes each repeating the same sender, permission and argument-count
 * checks; here those checks exist once.
 *
 * <p>Three checks run in the {@code executes} wrapper rather than in {@code requires}, because a
 * sender who fails them should be told why rather than see "unknown command": a form that needs
 * a player run from the console, a mutating command while storage is degraded or a reload runs
 * (SPEC §12), and the {@code open <menu>} form for a sender who may only open for others.
 *
 * <h2>Registration runs more than once</h2>
 *
 * Paper fires the {@code COMMANDS} lifecycle event at startup and again on every datapack reload
 * ({@code /minecraft:reload}), each time into a fresh dispatcher. {@link #register} therefore
 * builds new nodes on every call and changes nothing else, apart from replacing the node map
 * that help reads usage lines from. Calling it twice leaves the same state as calling it once.
 *
 * <h2>Suggestions may run off the main thread</h2>
 *
 * Whether Paper computes suggestions on the main thread is not something the API promises. Every
 * provider here reads only immutable data: the registry's volatile snapshot of names (menus are
 * immutable and published copy-on-write), constant lists, and {@code hasPermission}, which the
 * {@code requires} predicates already call from wherever Paper evaluates them.
 */
public final class CommandTree {

    /** Every subcommand, in the order help lists them. */
    public record Subcommands(HelpCommand help, ListCommand list, InfoCommand info, OpenCommand open,
                              EditCommand edit, CreateCommand create, DeleteCommand delete, SetCommand set,
                              UnsetCommand unset, GiveCommand give, JoinMenuCommand joinMenu, NameCommand name,
                              SaveCommand save, ReloadCommand reload, ChangelogCommand changelog,
                              UpdateCommand update) {
    }

    private static final String ROOT = "mymenu";
    private static final List<String> ALIASES = List.of("mm");
    private static final String DESCRIPTION = "Chest menus: /mymenu help";
    private static final String NONE = "none";
    private static final String IN_GAME = "Only a player can use this command.";
    private static final List<String> MATCH_MODES = Stream.of(MatchMode.values()).map(Enum::name).toList();

    private static final List<CommandSpec> SPECS = List.of(HelpCommand.SPEC, ListCommand.SPEC, InfoCommand.SPEC,
            OpenCommand.SPEC, EditCommand.SPEC, CreateCommand.SPEC, DeleteCommand.SPEC, SetCommand.SPEC,
            UnsetCommand.SPEC, GiveCommand.SPEC, JoinMenuCommand.SPEC, NameCommand.SPEC, SaveCommand.SPEC,
            ReloadCommand.SPEC, ChangelogCommand.SPEC, UpdateCommand.SPEC);

    private final Logger logger;
    private final MenuService menus;
    private final BooleanSupplier reloading;
    private final Subcommands subs;

    // Replaced on each registration; help reads the usage lines from it.
    private volatile Map<String, CommandNode<CommandSourceStack>> nodes = Map.of();

    public CommandTree(Logger logger, MenuService menus, BooleanSupplier reloading, Subcommands subs) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.reloading = Objects.requireNonNull(reloading, "reloading");
        this.subs = Objects.requireNonNull(subs, "subs");
    }

    /** The {@code COMMANDS} lifecycle handler. Safe to call any number of times. */
    public void register(Commands registrar) {
        List<LiteralArgumentBuilder<CommandSourceStack>> children = List.of(help(), list(), info(), open(), edit(),
                create(), delete(), set(), unset(), give(), joinMenu(), name(), save(), reload(), changelog(),
                update());
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
                .requires(source -> HelpCommand.SPEC.canUse(source.getSender()))
                .executes(run(HelpCommand.SPEC, ctx -> helpPage(ctx, false)));
        Map<String, CommandNode<CommandSourceStack>> built = new HashMap<>();
        for (LiteralArgumentBuilder<CommandSourceStack> child : children) {
            LiteralCommandNode<CommandSourceStack> node = child.build();
            built.put(node.getName(), node);
            root.then(node);
        }
        nodes = Map.copyOf(built);
        registrar.register(root.build(), DESCRIPTION, ALIASES);
    }

    // ---- Nodes --------------------------------------------------------------------------

    private LiteralArgumentBuilder<CommandSourceStack> help() {
        CommandSpec spec = HelpCommand.SPEC;
        return literal(spec)
                .executes(run(spec, ctx -> helpPage(ctx, false)))
                .then(Commands.literal("player").executes(run(spec, ctx -> helpPage(ctx, false))))
                .then(Commands.literal("admin")
                        .requires(source -> source.getSender().hasPermission(HelpCommand.ADMIN_PERMISSION))
                        .executes(run(spec, ctx -> helpPage(ctx, true))))
                .then(Commands.literal("command")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, usableNames(ctx.getSource().getSender())))
                                .executes(run(spec, ctx -> subs.help().executeOne(sender(ctx),
                                        entry(usableSpec(ctx)))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> list() {
        CommandSpec spec = ListCommand.SPEC;
        return literal(spec).executes(run(spec, ctx -> subs.list().execute(sender(ctx))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> info() {
        CommandSpec spec = InfoCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.info().execute(sender(ctx), menu(ctx, "menu")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> open() {
        CommandSpec spec = OpenCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> {
                    if (!sender(ctx).hasPermission(OpenCommand.OPEN)) {
                        throw fail("You may only open menus for other players: /mymenu open <menu> <player>.");
                    }
                    subs.open().execute(sender(ctx),
                            self(ctx, "From the console, name a player: /mymenu open <menu> <player>."),
                            menu(ctx, "menu"));
                }))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission(OpenCommand.OPEN_OTHER))
                        .executes(run(spec, ctx -> subs.open().execute(sender(ctx), player(ctx), menu(ctx, "menu"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> edit() {
        CommandSpec spec = EditCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.edit().execute(self(ctx, IN_GAME), menu(ctx, "menu")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> create() {
        CommandSpec spec = CreateCommand.SPEC;
        return literal(spec).then(Commands.argument("menu", StringArgumentType.word())
                .executes(run(spec, ctx -> {
                    String name = newMenuName(ctx);
                    subs.create().execute(sender(ctx), author(ctx), name, Menu.DEFAULT_ROWS, name);
                }))
                .then(Commands.argument("rows", IntegerArgumentType.integer(Menu.MIN_ROWS, Menu.MAX_ROWS))
                        .executes(run(spec, ctx -> {
                            String name = newMenuName(ctx);
                            subs.create().execute(sender(ctx), author(ctx), name,
                                    IntegerArgumentType.getInteger(ctx, "rows"), name);
                        }))
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(run(spec, ctx -> subs.create().execute(sender(ctx), author(ctx),
                                        newMenuName(ctx), IntegerArgumentType.getInteger(ctx, "rows"),
                                        StringArgumentType.getString(ctx, "title")))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> delete() {
        CommandSpec spec = DeleteCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.delete().execute(sender(ctx), menu(ctx, "menu")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> set() {
        CommandSpec spec = SetCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.set().execute(self(ctx, IN_GAME), menu(ctx, "menu"),
                        MatchMode.DEFAULT)))
                .then(Commands.argument("matchMode", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggest(builder, MATCH_MODES))
                        .executes(run(spec, ctx -> subs.set().execute(self(ctx, IN_GAME), menu(ctx, "menu"),
                                matchMode(ctx))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unset() {
        CommandSpec spec = UnsetCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.unset().execute(sender(ctx), menu(ctx, "menu")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> give() {
        CommandSpec spec = GiveCommand.SPEC;
        return literal(spec).then(menuArgument()
                .executes(run(spec, ctx -> subs.give().execute(sender(ctx), menu(ctx, "menu"),
                        self(ctx, "From the console, name a player: /mymenu give <menu> <player>."))))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(run(spec, ctx -> subs.give().execute(sender(ctx), menu(ctx, "menu"), player(ctx))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> joinMenu() {
        CommandSpec spec = JoinMenuCommand.SPEC;
        return literal(spec).then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder,
                        Stream.concat(Stream.of(NONE), menus.registry().names().stream()).toList()))
                .executes(run(spec, ctx -> subs.joinMenu().execute(sender(ctx), joinTarget(ctx)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name() {
        CommandSpec spec = NameCommand.SPEC;
        return literal(spec).then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(run(spec, ctx -> subs.name().execute(self(ctx, IN_GAME),
                        StringArgumentType.getString(ctx, "name")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> save() {
        CommandSpec spec = SaveCommand.SPEC;
        return literal(spec).executes(run(spec, ctx -> subs.save().execute(sender(ctx))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> reload() {
        CommandSpec spec = ReloadCommand.SPEC;
        return literal(spec)
                .executes(run(spec, ctx -> subs.reload().execute(sender(ctx), false)))
                .then(Commands.literal(ReloadCommand.DISCARD)
                        .executes(run(spec, ctx -> subs.reload().execute(sender(ctx), true))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> changelog() {
        CommandSpec spec = ChangelogCommand.SPEC;
        return literal(spec)
                .executes(run(spec, ctx -> subs.changelog().execute(sender(ctx), null)))
                .then(Commands.argument("version", StringArgumentType.word())
                        .executes(run(spec, ctx -> subs.changelog().execute(sender(ctx),
                                StringArgumentType.getString(ctx, "version")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> update() {
        CommandSpec spec = UpdateCommand.SPEC;
        return literal(spec).executes(run(spec, ctx -> subs.update().execute(sender(ctx))));
    }

    // ---- Building blocks ----------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> literal(CommandSpec spec) {
        return Commands.literal(spec.name()).requires(source -> spec.canUse(source.getSender()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> menuArgument() {
        return Commands.argument("menu", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, menus.registry().names()));
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> options) {
        String typed = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    @FunctionalInterface
    private interface Body {
        void run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    /** Wraps a node's action with the checks every subcommand shares. */
    private Command<CommandSourceStack> run(CommandSpec spec, Body body) {
        return ctx -> {
            if (spec.mutating()) {
                refuseWhileLocked();
            }
            try {
                body.run(ctx);
            } catch (RuntimeException e) {
                logger.error("/{} {} failed", ROOT, spec.name(), e);
                throw fail("That command failed unexpectedly; the server log has the details.");
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    private void refuseWhileLocked() throws CommandSyntaxException {
        if (reloading.getAsBoolean()) {
            throw fail("A reload is running; try again when it has finished.");
        }
        if (!menus.isLoaded()) {
            throw fail(Replies.refusal(MutationResult.Reason.NOT_LOADED, ""));
        }
        if (menus.isDegraded()) {
            throw fail(Replies.DEGRADED);
        }
    }

    private static CommandSyntaxException fail(String message) {
        return new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text(message)))
                .create();
    }

    // ---- Resolvers ----------------------------------------------------------------------

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    /**
     * The player the command acts as. The executor rather than the sender, so
     * {@code /execute as <player> run mymenu ...} works as it does for vanilla commands.
     */
    private static Player self(CommandContext<CommandSourceStack> ctx, String otherwise) throws CommandSyntaxException {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        throw fail(otherwise);
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> found = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (found.isEmpty()) {
            throw fail("No player was found.");
        }
        return found.getFirst();
    }

    private Menu menu(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
        String name = Menu.normalizeName(StringArgumentType.getString(ctx, argument));
        Optional<Menu> found = menus.registry().find(name);
        if (found.isEmpty()) {
            throw fail("There is no menu named '" + name + "'. /mymenu list shows them all.");
        }
        return found.get();
    }

    /** SPEC §3.2: lowercased, then {@code [a-z0-9_-]}, at most 32 characters, and not {@code none}. */
    private static String newMenuName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = Menu.normalizeName(StringArgumentType.getString(ctx, "menu"));
        if (!Menu.isValidName(name)) {
            throw fail("Menu names may use only a-z, 0-9, _ and -, and are at most " + Menu.MAX_NAME_LENGTH
                    + " characters long.");
        }
        if (name.equals(NONE)) {
            throw fail("'" + NONE + "' is a reserved name: /mymenu joinmenu " + NONE + " uses it to mean no menu. "
                    + "Pick another name.");
        }
        return name;
    }

    /** Who to record as the author, or null when the console creates a menu. */
    private static @Nullable Player author(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getExecutor() instanceof Player player ? player : null;
    }

    private static MatchMode matchMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String typed = StringArgumentType.getString(ctx, "matchMode");
        try {
            return MatchMode.valueOf(typed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw fail("Match mode must be one of " + String.join(", ", MATCH_MODES) + ".");
        }
    }

    /** {@code none} clears the setting; it is a reserved menu name, so nothing else can mean it. */
    private @Nullable Menu joinTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (StringArgumentType.getString(ctx, "target").equalsIgnoreCase(NONE)) {
            return null;
        }
        return menu(ctx, "target");
    }

    private static List<String> usableNames(CommandSender sender) {
        return SPECS.stream().filter(spec -> spec.canUse(sender)).map(CommandSpec::name).toList();
    }

    private static CommandSpec usableSpec(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String typed = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
        for (CommandSpec spec : SPECS) {
            if (spec.name().equals(typed) && spec.canUse(sender(ctx))) {
                return spec;
            }
        }
        throw fail("There is no command named '" + typed + "' that you can use.");
    }

    // ---- Help ---------------------------------------------------------------------------

    private void helpPage(CommandContext<CommandSourceStack> ctx, boolean all) {
        CommandSender sender = sender(ctx);
        List<HelpCommand.Entry> entries = new ArrayList<>();
        for (CommandSpec spec : SPECS) {
            if (all || spec.canUse(sender)) {
                entries.add(entry(spec));
            }
        }
        subs.help().execute(sender, entries, all);
    }

    private HelpCommand.Entry entry(CommandSpec spec) {
        CommandNode<CommandSourceStack> node = nodes.get(spec.name());
        return new HelpCommand.Entry(spec, node == null ? spec.name() : usage(node));
    }

    /**
     * A usage line built from the node itself: optional parts in brackets, alternatives split by
     * {@code |}. Requirements are ignored, so the admin page shows every form.
     */
    private static String usage(CommandNode<CommandSourceStack> node) {
        String self = node instanceof LiteralCommandNode ? node.getName() : "<" + node.getName() + ">";
        Collection<CommandNode<CommandSourceStack>> children = node.getChildren();
        if (children.isEmpty()) {
            return self;
        }
        String next = children.stream().map(CommandTree::usage).collect(Collectors.joining("|"));
        if (node.getCommand() != null) {
            return self + " [" + next + "]";
        }
        return children.size() == 1 ? self + " " + next : self + " (" + next + ")";
    }
}
