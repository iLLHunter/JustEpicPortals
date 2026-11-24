package com.onlyk.justepicportals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PortalCommands {
    private static final SimpleCommandExceptionType NOT_ANCHOR = new SimpleCommandExceptionType(
            Component.translatable("command.justepicportals.not_anchor"));
    private static final SimpleCommandExceptionType ADMIN_REQUIRED = new SimpleCommandExceptionType(
            Component.translatable("command.justepicportals.admin_required"));

    public static void registerCommands(RegisterCommandsEvent event) { register(event.getDispatcher()); }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jep")
                .then(Commands.literal("anchor")
                        .then(Commands.literal("remove").requires(source -> PortalPermissions.canAdminister(source))
                                .executes(context -> remove(context.getSource(), aimedAnchor(context.getSource())))
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .executes(context -> remove(context.getSource(), BlockPosArgument.getLoadedBlockPos(context,"position")))))
                        .then(requirement())));
    }

    /** /jep anchor requirement show|add|remove|mode|clear, each with an optional trailing position. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> requirement() {
        var root = Commands.literal("requirement").requires(source -> PortalPermissions.canAdminister(source));
        root.then(positioned(Commands.literal("show"), (source, pos) -> show(source, pos)));
        root.then(positioned(Commands.literal("clear"), (source, pos) -> change(source, pos, current -> PortalRequirements.AnchorRequirement.NONE.any(current.any()),
                "command.justepicportals.requirement.cleared")));
        var modes = Commands.literal("mode");
        for (var mode : new String[]{"all", "any"}) modes.then(positioned(Commands.literal(mode),
                (source, pos) -> change(source, pos, current -> current.any(mode.equals("any")), "command.justepicportals.requirement.mode_" + mode)));
        root.then(modes);
        var add = Commands.literal("add"); var remove = Commands.literal("remove");
        for (var kind : PortalRequirements.Kind.values()) {
            add.then(Commands.literal(kind.id()).then(positioned(Commands.argument("id", ResourceLocationArgument.id()).suggests((context, builder) ->
                    SharedSuggestionProvider.suggestResource(available(context.getSource().getServer(), kind), builder)),
                    (source, pos, context) -> add(source, pos, new PortalRequirements.Entry(kind, ResourceLocationArgument.getId(context, "id"))))));
            remove.then(Commands.literal(kind.id()).then(positioned(Commands.argument("id", ResourceLocationArgument.id()).suggests((context, builder) ->
                    SharedSuggestionProvider.suggestResource(available(context.getSource().getServer(), kind), builder)),
                    (source, pos, context) -> removeEntry(source, pos, new PortalRequirements.Entry(kind, ResourceLocationArgument.getId(context, "id"))))));
        }
        return root.then(add).then(remove);
    }
    private interface Action { int run(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException; }
    private interface ContextAction { int run(CommandSourceStack source, BlockPos pos, CommandContext<CommandSourceStack> context) throws CommandSyntaxException; }
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T positioned(T node, Action action) {
        return positioned(node, (source, pos, context) -> action.run(source, pos));
    }
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T positioned(T node, ContextAction action) {
        node.executes(context -> action.run(context.getSource(), aimedAnchor(context.getSource()), context));
        node.then(Commands.argument("position", BlockPosArgument.blockPos())
                .executes(context -> action.run(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "position"), context)));
        return node;
    }
    private static java.util.Collection<ResourceLocation> available(net.minecraft.server.MinecraftServer server, PortalRequirements.Kind kind) {
        return switch (kind) {
            case ADVANCEMENT -> server.getAdvancements().getAllAdvancements().stream().map(net.minecraft.advancements.AdvancementHolder::id).toList();
            case GROUP -> PortalRequirements.groupIds();
            case JED_RULE -> PortalJed.ruleIds(server);
        };
    }
    private static PortalAnchorBlockEntity anchor(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        if (!PortalPermissions.canAdminister(source)) throw ADMIN_REQUIRED.create();
        if (!(source.getLevel().getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor)) throw NOT_ANCHOR.create();
        return anchor;
    }
    private static int add(CommandSourceStack source, BlockPos pos, PortalRequirements.Entry entry) throws CommandSyntaxException {
        var anchor = anchor(source, pos);
        // Only entries that exist now can be added, so a typo is caught here. One that disappears later denies travel.
        if (!available(source.getServer(), entry.kind()).contains(entry.id()))
            throw new SimpleCommandExceptionType(Component.translatable(entry.kind() == PortalRequirements.Kind.JED_RULE && !PortalJed.loaded()
                    ? "command.justepicportals.requirement.no_jed" : "command.justepicportals.requirement.unknown", describe(source.getServer(), entry))).create();
        var current = anchor.requirement();
        if (current.entries().contains(entry)) throw new SimpleCommandExceptionType(Component.translatable("command.justepicportals.requirement.already", describe(source.getServer(), entry))).create();
        if (current.entries().size() >= PortalRequirements.MAX_ANCHOR_ENTRIES)
            throw new SimpleCommandExceptionType(Component.translatable("command.justepicportals.requirement.full", PortalRequirements.MAX_ANCHOR_ENTRIES)).create();
        anchor.requirement(current.add(entry));
        source.sendSuccess(() -> Component.translatable("command.justepicportals.requirement.added", describe(source.getServer(), entry)), true);
        return anchor.requirement().entries().size();
    }
    private static int removeEntry(CommandSourceStack source, BlockPos pos, PortalRequirements.Entry entry) throws CommandSyntaxException {
        var anchor = anchor(source, pos);
        if (!anchor.requirement().entries().contains(entry))
            throw new SimpleCommandExceptionType(Component.translatable("command.justepicportals.requirement.not_present", describe(source.getServer(), entry))).create();
        anchor.requirement(anchor.requirement().remove(entry));
        source.sendSuccess(() -> Component.translatable("command.justepicportals.requirement.removed", describe(source.getServer(), entry)), true);
        return anchor.requirement().entries().size();
    }
    private static int change(CommandSourceStack source, BlockPos pos, java.util.function.UnaryOperator<PortalRequirements.AnchorRequirement> change, String message) throws CommandSyntaxException {
        var anchor = anchor(source, pos);
        anchor.requirement(change.apply(anchor.requirement()));
        source.sendSuccess(() -> Component.translatable(message), true);
        return 1;
    }
    private static int show(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        var anchor = anchor(source, pos);
        var requirement = anchor.requirement();
        if (requirement.isEmpty()) source.sendSuccess(() -> Component.translatable("command.justepicportals.requirement.none"), false);
        else {
            var list = Component.empty();
            for (int i = 0; i < requirement.entries().size(); i++) { if (i > 0) list.append(", "); list.append(describe(source.getServer(), requirement.entries().get(i))); }
            source.sendSuccess(() -> Component.translatable(requirement.any() ? "command.justepicportals.requirement.show_any" : "command.justepicportals.requirement.show_all", list), false);
        }
        var material = anchor.material();
        boolean materialRules = PortalRequirements.configMaterialRules().containsKey(material)
                || PortalRequirements.datapackMaterialRules().stream().anyMatch(rule -> rule.materials().contains(material));
        if (materialRules) source.sendSuccess(() -> Component.translatable("command.justepicportals.requirement.material", material.id()), false);
        return requirement.entries().size();
    }
    /** How an entry reads in command feedback and the anchor screen: an advancement's title and id, a group or JED rule's id. */
    public static Component describe(net.minecraft.server.MinecraftServer server, PortalRequirements.Entry entry) {
        return switch (entry.kind()) {
            case ADVANCEMENT -> {
                var holder = server.getAdvancements().get(entry.id());
                var title = holder == null ? null : holder.value().display().map(net.minecraft.advancements.DisplayInfo::getTitle).orElse(null);
                yield title == null ? Component.literal(entry.id().toString()) : Component.translatable("gui.justepicportals.requirement.entry.advancement", title, entry.id().toString());
            }
            case GROUP -> Component.translatable("gui.justepicportals.requirement.entry.group", entry.id().toString());
            case JED_RULE -> Component.translatable("gui.justepicportals.requirement.entry.jed_rule", entry.id().toString());
        };
    }

    private static BlockPos aimedAnchor(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(player.blockInteractionRange()));
        BlockHitResult hit = source.getLevel().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if (hit.getType() != HitResult.Type.BLOCK) throw NOT_ANCHOR.create();
        return hit.getBlockPos();
    }

    public static int remove(CommandSourceStack source, BlockPos position) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!PortalPermissions.canAdminister(source)) throw ADMIN_REQUIRED.create();
        if (!(source.getLevel().getBlockEntity(position) instanceof PortalAnchorBlockEntity)) throw NOT_ANCHOR.create();
        if (!source.getLevel().destroyBlock(position,true,source.getEntity())) return 0;
        source.sendSuccess(() -> Component.translatable("command.justepicportals.removed",position.getX(),position.getY(),position.getZ()),true);
        return 1;
    }

    private PortalCommands() {}
}
