package com.onlyk.justepicportals;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Advancement requirements for portal travel. Conditions use the same JSON shape as Just Epic Dimensions access rules
 * ({@code all}, {@code any}, {@code not}, {@code advancement}), plus {@code group} (a JEP named group) and {@code jed_rule}
 * (a JED reusable rule, when JED is installed).
 * <p>
 * A requirement that names something missing (an advancement, a criterion, a group, a JED rule, or JED itself) denies
 * travel and logs once, so a typo never opens a gated portal.
 */
public final class PortalRequirements {
    public static final String GROUP_DIRECTORY = "justepicportals/requirement";
    public static final String MATERIAL_DIRECTORY = "justepicportals/material_rule";
    /** Entries one anchor can hold; the menu sends them and their indices fit in its button range. */
    public static final int MAX_ANCHOR_ENTRIES = 32;
    private static final int MAX_NODES = 256, MAX_DEPTH = 32, MAX_GROUP_DEPTH = 16;
    private static final Gson GSON = new Gson();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    // ---- Model ------------------------------------------------------------------------------------------------------

    /** One condition. {@link #failure} is empty when the player meets it, or what the player is missing. */
    public sealed interface Node permits All, Any, Not, Advancement, Group, JedRule, Invalid {}
    public record All(List<Node> children) implements Node {}
    public record Any(List<Node> children) implements Node {}
    public record Not(Node child) implements Node {}
    public record Advancement(ResourceLocation id, Boolean complete, Map<String, Boolean> criteria) implements Node {}
    public record Group(ResourceLocation id) implements Node {}
    public record JedRule(ResourceLocation id) implements Node {}
    /** A definition that could not be read. It always denies. */
    public record Invalid(String source) implements Node {}

    public enum Kind {
        ADVANCEMENT("advancement"), GROUP("group"), JED_RULE("jed_rule");
        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
        public static Optional<Kind> byId(String id) { return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst(); }
    }
    /** One entry of an anchor's own requirement. */
    public record Entry(Kind kind, ResourceLocation id) {
        public String save() { return kind.id() + "=" + id; }
        public static Optional<Entry> load(String value) {
            int split = value.indexOf('=');
            if (split < 0) return Optional.ofNullable(ResourceLocation.tryParse(value)).map(id -> new Entry(Kind.ADVANCEMENT, id));
            var kind = Kind.byId(value.substring(0, split));
            var id = ResourceLocation.tryParse(value.substring(split + 1));
            return kind.isPresent() && id != null ? Optional.of(new Entry(kind.get(), id)) : Optional.empty();
        }
        Node node() {
            return switch (kind) {
                case ADVANCEMENT -> new Advancement(id, true, Map.of());
                case GROUP -> new Group(id);
                case JED_RULE -> new JedRule(id);
            };
        }
    }
    /** An anchor's own requirement: every entry ({@code any == false}) or at least one. Empty means no requirement. */
    public record AnchorRequirement(boolean any, List<Entry> entries) {
        public static final AnchorRequirement NONE = new AnchorRequirement(false, List.of());
        public AnchorRequirement { entries = List.copyOf(entries); }
        public boolean isEmpty() { return entries.isEmpty(); }
        public AnchorRequirement add(Entry entry) {
            if (entries.contains(entry) || entries.size() >= MAX_ANCHOR_ENTRIES) return this;
            var next = new ArrayList<>(entries); next.add(entry); return new AnchorRequirement(any, next);
        }
        public AnchorRequirement remove(Entry entry) {
            var next = new ArrayList<>(entries); next.remove(entry); return new AnchorRequirement(any, next);
        }
        public AnchorRequirement any(boolean value) { return new AnchorRequirement(value, entries); }
        Optional<Node> node() {
            if (entries.isEmpty()) return Optional.empty();
            var children = entries.stream().map(Entry::node).toList();
            return Optional.of(any ? new Any(children) : new All(children));
        }
    }
    /** A datapack material rule. */
    public record MaterialRule(ResourceLocation source, Set<PortalMaterial> materials, Node condition) {}

    // ---- Active definitions ------------------------------------------------------------------------------------------

    private static volatile Map<ResourceLocation, Node> groups = Map.of();
    private static volatile List<MaterialRule> datapackMaterialRules = List.of();
    private static volatile Map<PortalMaterial, List<Node>> configMaterialRules = Map.of();

    public static Set<ResourceLocation> groupIds() { return groups.keySet(); }
    public static Map<PortalMaterial, List<Node>> configMaterialRules() { return configMaterialRules; }
    public static List<MaterialRule> datapackMaterialRules() { return datapackMaterialRules; }

    /** Replaces the TOML material rules. Each entry is {@code "<material>: [all|any] <entry> <entry>..."}, where an entry
     * is an advancement id, {@code group=<id>} or {@code jed_rule=<id>}. An unreadable entry for a known material denies
     * that material; one naming no known material is logged and skipped. */
    public static void configure(List<? extends String> entries) {
        var result = new EnumMap<PortalMaterial, List<Node>>(PortalMaterial.class);
        for (var raw : entries) {
            String entry = raw.trim();
            int colon = entry.indexOf(':');
            // A material name has no namespace, so the first colon ends it.
            var material = colon < 0 ? Optional.<PortalMaterial>empty() : material(entry.substring(0, colon).trim());
            if (material.isEmpty()) { LogUtils.getLogger().error("Just Epic Portals materialRequirements entry '{}' does not start with a known material and a colon; it is ignored", raw); continue; }
            Node node;
            try { node = configEntry(entry.substring(colon + 1).trim()); }
            catch (IllegalArgumentException error) {
                LogUtils.getLogger().error("Just Epic Portals materialRequirements entry '{}' is invalid ({}); {} portals deny travel until it is fixed", raw, error.getMessage(), material.get().id());
                node = new Invalid("materialRequirements: " + raw);
            }
            result.computeIfAbsent(material.get(), ignored -> new ArrayList<>()).add(node);
        }
        result.replaceAll((material, nodes) -> List.copyOf(nodes));
        configMaterialRules = Collections.unmodifiableMap(result);
        LOGGED.clear();
    }
    private static Node configEntry(String text) {
        var words = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        words.removeIf(String::isEmpty);
        boolean any = false;
        if (!words.isEmpty() && (words.getFirst().equals("all") || words.getFirst().equals("any"))) any = words.removeFirst().equals("any");
        if (words.isEmpty()) throw new IllegalArgumentException("no advancements listed");
        var children = new ArrayList<Node>();
        for (var word : words) {
            var entry = Entry.load(word).orElseThrow(() -> new IllegalArgumentException("'" + word + "' is not an advancement id, group=<id> or jed_rule=<id>"));
            children.add(entry.node());
        }
        return children.size() == 1 ? children.getFirst() : any ? new Any(children) : new All(children);
    }
    public static Optional<PortalMaterial> material(String id) {
        return Arrays.stream(PortalMaterial.values()).filter(material -> material.id().equals(id)).findFirst();
    }

    // ---- JSON ---------------------------------------------------------------------------------------------------------

    /** Reads {@code {"format_version":1,"condition":{...}}}. */
    public static Node decodeGroup(JsonElement json, String at) {
        var object = object(json, at);
        keys(object, Set.of("format_version", "condition"), at);
        version(object, at);
        return decode(object.get("condition"), at + ".condition", 0, new int[1]);
    }
    /** Reads {@code {"format_version":1,"materials":[...],"condition":{...}}}. */
    public static MaterialRule decodeMaterialRule(ResourceLocation id, JsonElement json) {
        String at = id.toString();
        var object = object(json, at);
        keys(object, Set.of("format_version", "materials", "condition"), at);
        version(object, at);
        var list = object.get("materials");
        if (list == null || !list.isJsonArray() || list.getAsJsonArray().isEmpty()) throw new IllegalArgumentException(at + ": materials must be a non-empty list");
        var materials = EnumSet.noneOf(PortalMaterial.class);
        for (var value : list.getAsJsonArray()) {
            String name = string(value, at + ".materials");
            materials.add(material(name).orElseThrow(() -> new IllegalArgumentException(at + ": unknown material '" + name + "'")));
        }
        Node condition;
        try { condition = decode(object.get("condition"), at + ".condition", 0, new int[1]); }
        catch (IllegalArgumentException error) {
            // The materials are known, so deny them rather than leave them open.
            LogUtils.getLogger().error("Just Epic Portals material rule {} is invalid ({}); its materials deny travel until it is fixed", id, error.getMessage());
            condition = new Invalid(at);
        }
        return new MaterialRule(id, Collections.unmodifiableSet(materials), condition);
    }
    private static Node decode(JsonElement json, String at, int depth, int[] count) {
        if (depth > MAX_DEPTH || ++count[0] > MAX_NODES) throw new IllegalArgumentException(at + ": conditions support at most " + MAX_NODES + " nodes and depth " + MAX_DEPTH);
        var object = object(json, at);
        keys(object, Set.of("all", "any", "not", "advancement", "group", "jed_rule"), at);
        if (object.size() != 1) throw new IllegalArgumentException(at + ": expected exactly one condition kind");
        String kind = object.keySet().iterator().next();
        var value = object.get(kind);
        switch (kind) {
            case "all", "any" -> {
                if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException(at + ": condition groups must not be empty");
                var children = new ArrayList<Node>(); int index = 0;
                for (var child : value.getAsJsonArray()) children.add(decode(child, at + "." + kind + "[" + index++ + "]", depth + 1, count));
                return kind.equals("all") ? new All(List.copyOf(children)) : new Any(List.copyOf(children));
            }
            case "not" -> { return new Not(decode(value, at + ".not", depth + 1, count)); }
            case "group" -> { return new Group(id(string(value, at + ".group"), at + ".group")); }
            case "jed_rule" -> { return new JedRule(id(string(value, at + ".jed_rule"), at + ".jed_rule")); }
            default -> {
                var settings = object(value, at + ".advancement");
                keys(settings, Set.of("id", "complete", "criteria"), at + ".advancement");
                var id = id(string(settings.get("id"), at + ".advancement.id"), at + ".advancement.id");
                Boolean complete = settings.has("complete") || !settings.has("criteria") ? flag(settings, "complete", true, at) : null;
                var criteria = new TreeMap<String, Boolean>();
                if (settings.has("criteria")) {
                    var values = object(settings.get("criteria"), at + ".advancement.criteria");
                    if (values.isEmpty()) throw new IllegalArgumentException(at + ": criteria must not be empty");
                    for (var key : values.keySet()) criteria.put(key, flag(values, key, false, at + ".advancement.criteria"));
                }
                return new Advancement(id, complete, Collections.unmodifiableMap(criteria));
            }
        }
    }
    private static JsonObject object(JsonElement json, String at) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException(at + ": expected an object");
        return json.getAsJsonObject();
    }
    private static void keys(JsonObject object, Set<String> allowed, String at) {
        for (var key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException(at + ": unknown field '" + key + "'");
    }
    private static void version(JsonObject object, String at) {
        var value = object.get("format_version");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || value.getAsInt() != 1)
            throw new IllegalArgumentException(at + ": expected format_version 1");
    }
    private static String string(JsonElement json, String at) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(at + ": expected a string");
        return json.getAsString();
    }
    private static boolean flag(JsonObject object, String key, boolean fallback, String at) {
        var value = object.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(at + "." + key + ": expected true or false");
        return value.getAsBoolean();
    }
    private static ResourceLocation id(String value, String at) {
        var id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException(at + ": '" + value + "' is not a valid id");
        return id;
    }

    // ---- Reloading ----------------------------------------------------------------------------------------------------

    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, GROUP_DIRECTORY) {
            @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                var result = new HashMap<ResourceLocation, Node>();
                files.forEach((id, json) -> {
                    try { result.put(id, decodeGroup(json, id.toString())); }
                    catch (IllegalArgumentException error) {
                        LogUtils.getLogger().error("Just Epic Portals requirement group {} is invalid ({}); anything using it denies travel until it is fixed", id, error.getMessage());
                        result.put(id, new Invalid(id.toString()));
                    }
                });
                groups = Map.copyOf(result);
                LOGGED.clear();
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(GSON, MATERIAL_DIRECTORY) {
            @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                var result = new ArrayList<MaterialRule>();
                files.forEach((id, json) -> {
                    try { result.add(decodeMaterialRule(id, json)); }
                    catch (IllegalArgumentException error) {
                        LogUtils.getLogger().error("Just Epic Portals material rule {} is invalid and names no usable materials ({}); it is ignored", id, error.getMessage());
                    }
                });
                datapackMaterialRules = List.copyOf(result);
                // /reload also rereads the TOML material rules, so every requirement source reloads together.
                configure(PortalConfig.readMaterialRequirements());
            }
        });
    }

    // ---- Evaluation ---------------------------------------------------------------------------------------------------

    /** Whether an advancement, group or JED rule with this id exists right now. */
    public static boolean exists(net.minecraft.server.MinecraftServer server, Kind kind, ResourceLocation id) {
        return switch (kind) {
            case ADVANCEMENT -> server.getAdvancements().get(id) != null;
            case GROUP -> groups.containsKey(id);
            case JED_RULE -> PortalJed.ruleIds(server).contains(id);
        };
    }

    /** Operators who may administer anchors, and Creative or Spectator players, skip advancement requirements. */
    public static boolean bypasses(ServerPlayer player) {
        return PortalPermissions.canAdminister(player) || player.isCreative() || player.isSpectator();
    }

    /** What {@code player} is missing to use {@code anchor}, or empty when they may. Covers the material rules and the
     * anchor's own requirement; Self/Team/Everyone access is checked separately. */
    public static Optional<Component> missing(PortalAnchorBlockEntity anchor, ServerPlayer player) {
        if (bypasses(player)) return Optional.empty();
        var failures = new ArrayList<Component>();
        var material = anchor.material();
        for (var node : configMaterialRules.getOrDefault(material, List.of())) failure(node, player, new ArrayDeque<>()).ifPresent(failures::add);
        for (var rule : datapackMaterialRules) if (rule.materials().contains(material)) failure(rule.condition(), player, new ArrayDeque<>()).ifPresent(failures::add);
        anchor.requirement().node().flatMap(node -> failure(node, player, new ArrayDeque<>())).ifPresent(failures::add);
        return failures.isEmpty() ? Optional.empty() : Optional.of(join(failures));
    }
    /** Whether any requirement applies to this anchor at all. */
    public static boolean gated(PortalAnchorBlockEntity anchor) {
        var material = anchor.material();
        return !anchor.requirement().isEmpty() || configMaterialRules.containsKey(material)
                || datapackMaterialRules.stream().anyMatch(rule -> rule.materials().contains(material));
    }

    /** Evaluates one condition for one player; for tests and other callers that build their own node. */
    public static Optional<Component> failure(Node node, ServerPlayer player) { return failure(node, player, new ArrayDeque<>()); }

    private static Optional<Component> failure(Node node, ServerPlayer player, Deque<ResourceLocation> groupsVisited) {
        return switch (node) {
            case All all -> {
                var failures = new ArrayList<Component>();
                for (var child : all.children()) failure(child, player, groupsVisited).ifPresent(failures::add);
                yield failures.isEmpty() ? Optional.empty() : Optional.of(join(failures));
            }
            case Any any -> {
                var failures = new ArrayList<Component>();
                for (var child : any.children()) {
                    var result = failure(child, player, groupsVisited);
                    if (result.isEmpty()) yield Optional.empty();
                    failures.add(result.get());
                }
                yield Optional.of(failures.size() == 1 ? failures.getFirst() : Component.translatable("message.justepicportals.requirement.any", join(failures)));
            }
            case Not not -> failure(not.child(), player, groupsVisited).isPresent() ? Optional.empty()
                    : Optional.of(Component.translatable("message.justepicportals.requirement.not", describe(not.child(), player, new ArrayDeque<>(groupsVisited))));
            case Advancement advancement -> advancementFailure(advancement, player);
            case Group group -> {
                var definition = groups.get(group.id());
                if (definition == null) yield unavailable("Unknown requirement group " + group.id());
                if (groupsVisited.contains(group.id()) || groupsVisited.size() >= MAX_GROUP_DEPTH) yield unavailable("Requirement group " + group.id() + " refers to itself or nests too deeply");
                groupsVisited.push(group.id());
                try { yield failure(definition, player, groupsVisited); } finally { groupsVisited.pop(); }
            }
            case JedRule rule -> {
                var result = PortalJed.evaluateRule(player, rule.id());
                if (result.isEmpty()) yield unavailable(PortalJed.loaded() ? "Unknown or unavailable JED access rule " + rule.id() : "JED access rule " + rule.id() + " needs Just Epic Dimensions");
                yield result.get();
            }
            case Invalid invalid -> unavailable("Invalid requirement definition " + invalid.source());
        };
    }
    private static Optional<Component> advancementFailure(Advancement node, ServerPlayer player) {
        var holder = player.server.getAdvancements().get(node.id());
        if (holder == null) return unavailable("Unknown advancement " + node.id());
        for (var key : node.criteria().keySet()) if (!holder.value().criteria().containsKey(key)) return unavailable("Unknown criterion " + key + " of advancement " + node.id());
        var progress = player.getAdvancements().getOrStartProgress(holder);
        boolean met = node.complete() == null || progress.isDone() == node.complete();
        for (var entry : node.criteria().entrySet()) {
            var criterion = progress.getCriterion(entry.getKey());
            if (criterion == null || criterion.isDone() != entry.getValue()) met = false;
        }
        if (met) return Optional.empty();
        Component name = advancementName(player, node.id());
        if (Boolean.FALSE.equals(node.complete())) name = Component.translatable("message.justepicportals.requirement.not", name);
        return Optional.of(name);
    }
    /** The advancement's title, or ??? for a hidden one the player has not earned, or its id when it has no display. */
    public static Component advancementName(ServerPlayer player, ResourceLocation id) {
        var holder = player.server.getAdvancements().get(id);
        if (holder == null) return Component.literal(id.toString());
        var display = holder.value().display();
        if (display.isEmpty()) return Component.literal(id.toString());
        if (display.get().isHidden() && !player.getAdvancements().getOrStartProgress(holder).isDone()) return Component.literal("???");
        return display.get().getTitle();
    }
    private static Component describe(Node node, ServerPlayer player, Deque<ResourceLocation> groupsVisited) {
        return switch (node) {
            case All all -> join(all.children().stream().map(child -> describe(child, player, groupsVisited)).toList());
            case Any any -> Component.translatable("message.justepicportals.requirement.any", join(any.children().stream().map(child -> describe(child, player, groupsVisited)).toList()));
            case Not not -> Component.translatable("message.justepicportals.requirement.not", describe(not.child(), player, groupsVisited));
            case Advancement advancement -> advancementName(player, advancement.id());
            case Group group -> Component.literal(group.id().toString());
            case JedRule rule -> Component.literal(rule.id().toString());
            case Invalid invalid -> Component.translatable("message.justepicportals.requirement.unavailable");
        };
    }
    private static Optional<Component> unavailable(String problem) {
        if (LOGGED.add(problem)) LogUtils.getLogger().warn("Just Epic Portals: {}. Portals that require it deny travel.", problem);
        return Optional.of(Component.translatable("message.justepicportals.requirement.unavailable"));
    }
    private static Component join(List<Component> parts) {
        if (parts.size() == 1) return parts.getFirst();
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) result.append(", "); result.append(parts.get(i)); }
        return result;
    }

    private PortalRequirements() {}
}
