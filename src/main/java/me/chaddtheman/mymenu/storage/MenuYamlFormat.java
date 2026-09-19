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
package me.chaddtheman.mymenu.storage;

import me.chaddtheman.mymenu.action.Action;
import me.chaddtheman.mymenu.model.BoundItem;
import me.chaddtheman.mymenu.model.ClickKey;
import me.chaddtheman.mymenu.model.ItemTemplate;
import me.chaddtheman.mymenu.model.MatchMode;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.model.MenuItem;
import me.chaddtheman.mymenu.model.MenuType;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code menus.yml} layout (SPEC §5.3, §6): parsed YAML in, menus out, and back.
 *
 * <h2>Why one bad entry cannot stop the load</h2>
 *
 * 1.x threw on the first unknown material and loaded nothing after it. Here the model
 * constructors throw too, deliberately: {@code Menu} rejects bad rows and out-of-layout slots,
 * {@code ItemTemplate.Descriptive} rejects non-items. That is right for an invariant and wrong
 * to let escape, so {@link #read} catches at two boundaries:
 * <ul>
 *   <li><b>Per menu.</b> Anything wrong with the menu's own fields (name, rows, type, bound
 *       item) skips that menu. The layout is built and validated before any slot is read,
 *       because slots cannot be checked against a layout that does not exist yet.</li>
 *   <li><b>Per slot.</b> Anything wrong inside a slot skips only that slot. That includes a
 *       slot outside the layout, which is checked here so that the {@code Menu} constructor
 *       never sees it and the rest of the menu survives.</li>
 * </ul>
 * Every skip is reported as a problem naming the menu and slot. Unknown keys are reported too,
 * although the entry is kept, because the next save would drop them. Any problem at all puts
 * storage into the degraded state, which stops saves until the file is fixed, so nothing that
 * was skipped or ignored can be overwritten (SPEC §12).
 *
 * <p>Both boundaries catch {@code RuntimeException}, not only the expected types. The promise
 * is "one entry cannot abort the load", and an exception nobody expected is exactly what would
 * break it.
 *
 * <p>Reading runs on the main thread, because resolving a material consults the server's item
 * registry. Writing touches no server state and runs on the storage thread.
 */
final class MenuYamlFormat {

    private static final String ROOT_KEY = "menus";

    private static final Set<String> MENU_KEYS = Set.of(
            "inventoryName", "author", "authorUuid", "type", "rows", "giveItemOnJoin", "boundItem", "inventory");
    private static final Set<String> INVENTORY_KEYS = Set.of("slots");
    private static final Set<String> BOUND_KEYS = Set.of("matchMode", "item");
    private static final Set<String> SLOT_KEYS = Set.of(
            "item", "viewPermission", "hiddenFallback", "clickSound", "cooldown", "actions");
    private static final Set<String> ITEM_KEYS = Set.of(
            "material", "displayName", "amount", "itemLore", "glow", "serialized");
    private static final List<String> READABLE_ITEM_KEYS = List.of("material", "displayName", "amount", "itemLore");

    // Canonical form only, so "s03" and "s3" cannot both claim slot 3.
    private static final Pattern SLOT_KEY = Pattern.compile("s(0|[1-9][0-9]{0,3})");

    // Serialized items make large files; SnakeYAML's default limit is 3 MB.
    private static final int MAX_FILE_CODE_POINTS = 64 * 1024 * 1024;

    private final ActionCodec actions;

    MenuYamlFormat(ActionCodec actions) {
        this.actions = actions;
    }

    /** @param problems one line per skipped or partly ignored entry; empty means a clean load */
    record Result(List<Menu> menus, List<String> problems) {
    }

    /** A value that cannot be used. The message is written for an admin reading the log. */
    private static final class Malformed extends Exception {
        Malformed(String message) {
            super(message, null, false, false);
        }
    }

    // ---- YAML text ----------------------------------------------------------------------

    /** @throws org.yaml.snakeyaml.error.YAMLException on a syntax error or a duplicate key */
    static @Nullable Object parse(String text) {
        LoaderOptions options = new LoaderOptions();
        // Otherwise a duplicated key silently replaces the first copy.
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(MAX_FILE_CODE_POINTS);
        return new Yaml(new SafeConstructor(options)).load(text);
    }

    /**
     * SnakeYAML writes an anchor and an alias ({@code &id001} / {@code *id001}) whenever one
     * collection instance appears twice, and 2.2 has no switch to stop it. Immutable empty lists
     * are shared singletons, so every collection in the tree handed to this method must be a
     * fresh instance, including maps returned by {@link ActionCodec#write}.
     */
    static String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setSplitLines(false);
        return new Yaml(new Representer(options), options).dump(root);
    }

    // ---- Reading ------------------------------------------------------------------------

    Result read(@Nullable Object root) {
        List<Menu> menus = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (root == null) {
            return new Result(menus, problems);
        }
        if (!(root instanceof Map<?, ?> top)) {
            problems.add("the file is not a YAML mapping, so no menus were loaded");
            return new Result(menus, problems);
        }
        for (Object key : top.keySet()) {
            if (!ROOT_KEY.equals(key)) {
                problems.add("unknown top-level key '" + key + "' ignored");
            }
        }
        Object all = top.get(ROOT_KEY);
        if (all == null) {
            return new Result(menus, problems);
        }
        if (!(all instanceof Map<?, ?> byName)) {
            problems.add("'" + ROOT_KEY + "' is not a mapping, so no menus were loaded");
            return new Result(menus, problems);
        }
        for (Map.Entry<?, ?> entry : byName.entrySet()) {
            // An unquoted numeric key comes back as a number and can change on the way; 007
            // becomes 7.
            if (!(entry.getKey() instanceof String name)) {
                problems.add("menu " + entry.getKey() + ": the name must be quoted text; menu skipped");
                continue;
            }
            try {
                menus.add(readMenu(name, entry.getValue(), problems));
            } catch (Malformed | RuntimeException e) {
                problems.add("menu '" + name + "': " + describe(e) + "; menu skipped");
            }
        }
        return new Result(menus, problems);
    }

    private Menu readMenu(String name, @Nullable Object node, List<String> problems) throws Malformed {
        String where = "menu '" + name + "'";
        Map<String, Object> fields = mapping(node, "the menu");
        reportUnknown(fields, MENU_KEYS, where, problems);

        String title = optText(fields, "inventoryName");
        BoundItem boundItem = fields.get("boundItem") == null
                ? null
                : readBoundItem(fields.get("boundItem"), where, problems);
        Menu shell = new Menu(name,
                title == null ? name : title,
                optText(fields, "author"),
                optUuid(fields, "authorUuid"),
                optEnum(fields, "type", MenuType.class, MenuType.CHEST),
                optInt(fields, "rows", Menu.DEFAULT_ROWS),
                boundItem,
                optBool(fields, "giveItemOnJoin", false),
                Map.of());

        Map<Integer, MenuItem> items = readSlots(shell, fields.get("inventory"), where, problems);
        return new Menu(shell.name(), shell.title(), shell.author(), shell.authorUuid(), shell.type(),
                shell.rows(), shell.boundItem(), shell.giveItemOnJoin(), items);
    }

    private BoundItem readBoundItem(Object node, String where, List<String> problems) throws Malformed {
        Map<String, Object> fields = mapping(node, "'boundItem'");
        reportUnknown(fields, BOUND_KEYS, where + " boundItem", problems);
        Object item = fields.get("item");
        if (item == null) {
            throw new Malformed("'boundItem' has no 'item'");
        }
        return new BoundItem(readTemplate(item, "'boundItem.item'", where + " boundItem item", problems),
                optEnum(fields, "matchMode", MatchMode.class, MatchMode.DEFAULT));
    }

    private Map<Integer, MenuItem> readSlots(Menu shell, @Nullable Object inventoryNode, String where,
                                             List<String> problems) throws Malformed {
        Map<Integer, MenuItem> items = new TreeMap<>();
        if (inventoryNode == null) {
            return items;
        }
        Map<String, Object> inventory = mapping(inventoryNode, "'inventory'");
        reportUnknown(inventory, INVENTORY_KEYS, where + " inventory", problems);
        if (inventory.get("slots") == null) {
            return items;
        }
        for (Map.Entry<String, Object> entry : mapping(inventory.get("slots"), "'inventory.slots'").entrySet()) {
            Matcher matcher = SLOT_KEY.matcher(entry.getKey());
            if (!matcher.matches()) {
                problems.add(where + ": '" + entry.getKey() + "' is not a slot key (expected s0, s1, ...); slot skipped");
                continue;
            }
            int slot = Integer.parseInt(matcher.group(1));
            String slotWhere = where + " slot " + slot;
            if (slot >= shell.size()) {
                problems.add(slotWhere + ": outside the menu (" + describeLayout(shell) + "); slot skipped");
                continue;
            }
            try {
                items.put(slot, readSlot(entry.getValue(), slotWhere, problems));
            } catch (Malformed | RuntimeException e) {
                problems.add(slotWhere + ": " + describe(e) + "; slot skipped");
            }
        }
        return items;
    }

    private MenuItem readSlot(@Nullable Object node, String where, List<String> problems) throws Malformed {
        Map<String, Object> fields = mapping(node, "the slot");
        reportUnknown(fields, SLOT_KEYS, where, problems);
        Object icon = fields.get("item");
        if (icon == null) {
            throw new Malformed("no 'item'");
        }
        ItemTemplate hiddenFallback = fields.get("hiddenFallback") == null
                ? null
                : readTemplate(fields.get("hiddenFallback"), "'hiddenFallback'", where + " hiddenFallback", problems);
        return new MenuItem(
                readTemplate(icon, "'item'", where + " item", problems),
                readActions(fields.get("actions"), where),
                optText(fields, "viewPermission"),
                hiddenFallback,
                optKey(fields, "clickSound"),
                optInt(fields, "cooldown", 0));
    }

    private ItemTemplate readTemplate(Object node, String what, String where, List<String> problems)
            throws Malformed {
        Map<String, Object> fields = mapping(node, what);
        reportUnknown(fields, ITEM_KEYS, where, problems);
        boolean glow = optBool(fields, "glow", false);

        if (fields.containsKey("serialized")) {
            List<String> mixed = READABLE_ITEM_KEYS.stream().filter(fields::containsKey).toList();
            if (!mixed.isEmpty()) {
                throw new Malformed(what + " has both 'serialized' and " + mixed
                        + "; only 'glow' may appear beside 'serialized'");
            }
            String data = requireText(fields, "serialized");
            try {
                // A hand-wrapped scalar folds its line breaks into spaces; base64 has none.
                return new ItemTemplate.Opaque(Base64.getDecoder().decode(data.replaceAll("\\s+", "")), glow);
            } catch (IllegalArgumentException e) {
                throw new Malformed("'serialized' is not valid base64");
            }
        }

        String materialName = requireText(fields, "material");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isLegacy()) {
            throw new Malformed("unknown material '" + materialName + "'");
        }
        return new ItemTemplate.Descriptive(material,
                optText(fields, "displayName"),
                optInt(fields, "amount", 1),
                optTextList(fields, "itemLore"),
                glow);
    }

    private Map<ClickKey, List<Action>> readActions(@Nullable Object node, String where) throws Malformed {
        Map<ClickKey, List<Action>> result = new EnumMap<>(ClickKey.class);
        if (node == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : mapping(node, "'actions'").entrySet()) {
            ClickKey key = parseEnum(entry.getKey(), "click type", ClickKey.class);
            List<Action> list = List.of();
            // A bare "LEFT:" is an empty list, which still stops the OTHER fallback.
            if (entry.getValue() != null) {
                if (!(entry.getValue() instanceof List<?> raw)) {
                    throw new Malformed("the actions under " + key + " are not a list");
                }
                List<Map<String, Object>> entries = new ArrayList<>(raw.size());
                for (Object action : raw) {
                    entries.add(mapping(action, "an action under " + key));
                }
                // The codec sees the whole list so list-level rules (the delay cap) apply at parse time.
                list = actions.readList(entries, where + " " + key);
            }
            result.put(key, list);
        }
        return result;
    }

    // ---- Writing ------------------------------------------------------------------------

    /** The whole file for these menus, in the order given. */
    Map<String, Object> write(Collection<Menu> menus) {
        Map<String, Object> byName = new LinkedHashMap<>();
        for (Menu menu : menus) {
            byName.put(menu.name(), writeMenu(menu));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(ROOT_KEY, byName);
        return root;
    }

    private Map<String, Object> writeMenu(Menu menu) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inventoryName", menu.title());
        putIfPresent(out, "author", menu.author());
        putIfPresent(out, "authorUuid", menu.authorUuid() == null ? null : menu.authorUuid().toString());
        out.put("type", menu.type().name());
        out.put("rows", menu.rows());
        out.put("giveItemOnJoin", menu.giveItemOnJoin());
        if (menu.boundItem() != null) {
            Map<String, Object> bound = new LinkedHashMap<>();
            bound.put("matchMode", menu.boundItem().matchMode().name());
            bound.put("item", writeTemplate(menu.boundItem().item()));
            out.put("boundItem", bound);
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        menu.items().forEach((slot, item) -> slots.put("s" + slot, writeSlot(item)));
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("slots", slots);
        out.put("inventory", inventory);
        return out;
    }

    private Map<String, Object> writeSlot(MenuItem item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", writeTemplate(item.icon()));
        putIfPresent(out, "viewPermission", item.viewPermission());
        if (item.hiddenFallback() != null) {
            out.put("hiddenFallback", writeTemplate(item.hiddenFallback()));
        }
        putIfPresent(out, "clickSound", item.clickSound() == null ? null : item.clickSound().asString());
        if (item.cooldownSeconds() > 0) {
            out.put("cooldown", item.cooldownSeconds());
        }
        if (!item.actions().isEmpty()) {
            Map<String, Object> byKey = new LinkedHashMap<>();
            item.actions().forEach((key, list) ->
                    byKey.put(key.name(), new ArrayList<>(list.stream().map(actions::write).toList())));
            out.put("actions", byKey);
        }
        return out;
    }

    private static Map<String, Object> writeTemplate(ItemTemplate template) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (template) {
            case ItemTemplate.Descriptive readable -> {
                out.put("material", readable.material().name());
                putIfPresent(out, "displayName", readable.displayName());
                if (readable.amount() != 1) {
                    out.put("amount", readable.amount());
                }
                if (!readable.lore().isEmpty()) {
                    out.put("itemLore", new ArrayList<>(readable.lore()));
                }
            }
            case ItemTemplate.Opaque opaque -> out.put("serialized", Base64.getEncoder().encodeToString(opaque.data()));
        }
        if (template.glow()) {
            out.put("glow", true);
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> out, String key, @Nullable Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    // ---- Field access -------------------------------------------------------------------

    private static Map<String, Object> mapping(@Nullable Object node, String what) throws Malformed {
        if (!(node instanceof Map<?, ?> raw)) {
            throw new Malformed(what + " is not a mapping");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        raw.forEach((key, value) -> fields.put(String.valueOf(key), value));
        return fields;
    }

    private static void reportUnknown(Map<String, Object> fields, Set<String> known, String where,
                                      List<String> problems) {
        for (String key : fields.keySet()) {
            if (!known.contains(key)) {
                problems.add(where + ": unknown key '" + key + "' ignored");
            }
        }
    }

    private static @Nullable String optText(Map<String, Object> fields, String key) throws Malformed {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        // Numbers and booleans are accepted as their text; a date or a nested block is not.
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new Malformed("'" + key + "' must be text; put it in quotes");
    }

    private static String requireText(Map<String, Object> fields, String key) throws Malformed {
        String value = optText(fields, key);
        if (value == null) {
            throw new Malformed("no '" + key + "'");
        }
        return value;
    }

    private static List<String> optTextList(Map<String, Object> fields, String key) throws Malformed {
        Object value = fields.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new Malformed("'" + key + "' must be a list of lines");
        }
        List<String> lines = new ArrayList<>(raw.size());
        for (Object line : raw) {
            if (!(line instanceof String || line instanceof Number || line instanceof Boolean)) {
                throw new Malformed("every line of '" + key + "' must be text");
            }
            lines.add(String.valueOf(line));
        }
        return lines;
    }

    private static int optInt(Map<String, Object> fields, String key, int fallback) throws Malformed {
        Object value = fields.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer number) {
            return number;
        }
        throw new Malformed("'" + key + "' must be a whole number, not '" + value + "'");
    }

    private static boolean optBool(Map<String, Object> fields, String key, boolean fallback) throws Malformed {
        Object value = fields.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new Malformed("'" + key + "' must be true or false, not '" + value + "'");
    }

    private static <E extends Enum<E>> E optEnum(Map<String, Object> fields, String key, Class<E> type,
                                                 E fallback) throws Malformed {
        String value = optText(fields, key);
        return value == null ? fallback : parseEnum(value, "'" + key + "'", type);
    }

    private static <E extends Enum<E>> E parseEnum(String value, String what, Class<E> type) throws Malformed {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Malformed("unknown " + what + " '" + value + "' (expected one of "
                    + Arrays.toString(type.getEnumConstants()) + ")");
        }
    }

    private static @Nullable UUID optUuid(Map<String, Object> fields, String key) throws Malformed {
        String value = optText(fields, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new Malformed("'" + key + "' is not a UUID");
        }
    }

    // Bare enum-style names (UI_BUTTON_CLICK) are an editor affordance (SPEC §8.3); the file
    // holds keys. A key is not checked against the sound registry, because resource packs can
    // add sounds the server has never heard of.
    private static @Nullable Key optKey(Map<String, Object> fields, String key) throws Malformed {
        String value = optText(fields, key);
        if (value == null) {
            return null;
        }
        try {
            return Key.key(value);
        } catch (InvalidKeyException e) {
            throw new Malformed("'" + key + "' must be a sound key such as minecraft:ui.button.click, not '"
                    + value + "'");
        }
    }

    private static String describeLayout(Menu shell) {
        String shape = shell.type().usesRows() ? shell.type() + " with " + shell.rows() + " rows" : shell.type().name();
        return shape + " has slots 0-" + (shell.size() - 1);
    }

    private static String describe(Exception e) {
        if (e instanceof Malformed || e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        return "unexpected " + e;
    }
}
