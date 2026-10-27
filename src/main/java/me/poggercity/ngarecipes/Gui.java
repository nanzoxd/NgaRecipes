package me.poggercity.ngarecipes;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and opens every GUI.
 *
 * Main list   (54): slots 0-44 recipe icons, 45 prev page, 49 close, 53 next page, rest white glass.
 * Viewer      (45): black glass border, 3x3 grid, output at 16, 36 prev / 40 back / 44 next.
 * Editor      (45): white glass border, 3x3 grid + output are free slots, 38 info, 40 save, 42 cancel.
 * Delete      (27): 11 confirm, 13 preview, 15 cancel.
 */
public final class Gui {

    /** Inventory slots of the 3x3 grid, row-major (shifted up one row, like the Skript). */
    static final int[] GRID = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    static final int OUT = 16;

    static final int EDIT_INFO = 38, EDIT_SAVE = 40, EDIT_CANCEL = 42;
    static final int VIEW_PREV = 36, VIEW_BACK = 40, VIEW_NEXT = 44;
    static final int LIST_PREV = 45, LIST_CLOSE = 49, LIST_NEXT = 53;
    static final int DEL_CONFIRM = 11, DEL_PREVIEW = 13, DEL_CANCEL = 15;

    static final String PERM_ADMIN = "recipes.admin";

    private static final Map<String, String> DEFAULT_TITLES = Map.of(
            "recipes", "<gradient:#e8c670:#fff0cc:#e8c670>ʀᴇʟɪǫᴜᴇꜱᴍᴘ ʀᴇᴄɪᴘᴇꜱ 🔍</gradient>",
            "viewer", "<gradient:#e8c670:#fff0cc:#e8c670>ʀᴇᴄɪᴘᴇ ᴠɪᴇᴡᴇʀ</gradient>",
            "editor", "<gradient:#e8c670:#fff0cc:#e8c670>ᴄᴜꜱᴛᴏᴍ ʀᴇᴄɪᴘᴇ ᴇᴅɪᴛᴏʀ</gradient>",
            "delete", "<gradient:#e8c670:#fff0cc:#e8c670>ᴅᴇʟᴇᴛᴇ ʀᴇᴄɪᴘᴇ?</gradient>");

    private final NgaRecipes plugin;
    private final RecipeStore store;
    private final NamespacedKey ghostKey;
    /** Who currently has one of our GUIs open (so we can close them on shutdown). */
    private final Map<UUID, RecipeHolder> open = new HashMap<>();

    Gui(NgaRecipes plugin, RecipeStore store) {
        this.plugin = plugin;
        this.store = store;
        this.ghostKey = new NamespacedKey(plugin, "ghost");
    }

    // ================================================================== helpers

    static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return -1;
    }

    static boolean isEditable(int slot) {
        return slot == OUT || gridIndex(slot) >= 0;
    }

    static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private Component title(String key) {
        return Text.mini(plugin.getConfig().getString("titles." + key, DEFAULT_TITLES.get(key)));
    }

    private static ItemStack item(Material type, String name, String... lore) {
        ItemStack it = new ItemStack(type);
        it.editMeta(meta -> {
            meta.displayName(Text.legacy(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) l.add(Text.legacy(s));
                meta.lore(l);
            }
        });
        return it;
    }

    private static ItemStack glass(Material type) {
        return item(type, " ");
    }

    private static void fill(Inventory inv, Material glassType) {
        ItemStack g = glass(glassType);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, g.clone());
    }

    private static void addLore(ItemStack it, String... lines) {
        it.editMeta(meta -> {
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            for (String s : lines) lore.add(Text.legacy(s));
            meta.lore(lore);
        });
    }

    private static void sound(Player p, Sound s) {
        p.playSound(p.getLocation(), s, 1f, 1f);
    }

    private Inventory create(RecipeHolder holder, int size, String titleKey) {
        Inventory inv = Bukkit.createInventory(holder, size, title(titleKey));
        holder.inventory = inv;
        return inv;
    }

    private void show(Player p, Inventory inv, RecipeHolder holder) {
        p.openInventory(inv);
        open.put(p.getUniqueId(), holder);
    }

    void forget(Player p) {
        open.remove(p.getUniqueId());
    }

    void closeAll() {
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        open.clear();
    }

    // ------------------------------------------------------------------ ghost items
    // When an existing recipe is opened for editing, its items are shown as copies. They are
    // tagged "ghost" so they can never leave the editor (no free items) and vanish on close.

    ItemStack markGhost(ItemStack it) {
        ItemStack c = it.clone();
        c.editMeta(meta -> meta.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1));
        return c;
    }

    boolean isGhost(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE);
    }

    private ItemStack stripGhost(ItemStack it) {
        ItemStack c = it.clone();
        if (c.hasItemMeta()) {
            c.editMeta(meta -> meta.getPersistentDataContainer().remove(ghostKey));
        }
        return c;
    }

    // ================================================================== main list : /recipes

    void openList(Player p, int page) {
        List<Integer> ids = store.ids();
        int size = ids.size();
        int perPage = 45;
        int maxPage = Math.max(1, (int) Math.ceil(size / (double) perPage));
        page = Math.max(1, Math.min(page, maxPage));

        RecipeHolder holder = new RecipeHolder(RecipeHolder.Type.LIST);
        holder.page = page;
        Inventory inv = create(holder, 54, "recipes");
        boolean admin = p.hasPermission(PERM_ADMIN);

        if (size == 0) {
            List<String> lore = new ArrayList<>();
            lore.add("&7There are currently no recipes.");
            if (admin) {
                lore.add("&8");
                lore.add("&7Use /addrecipe to create one.");
            }
            inv.setItem(22, item(Material.BARRIER, "&c&lNo Recipes Available", lore.toArray(new String[0])));
        } else {
            int start = (page - 1) * perPage;
            for (int i = 0; i < perPage && start + i < size; i++) {
                int id = ids.get(start + i);
                inv.setItem(i, buildIcon(id, admin));
                holder.slotMap.put(i, id);
            }
        }

        for (int i = 45; i <= 53; i++) inv.setItem(i, glass(Material.WHITE_STAINED_GLASS_PANE));
        if (page > 1) inv.setItem(LIST_PREV, item(Material.ARROW, "&e« Previous Page", "&7Go to page &f" + (page - 1)));
        inv.setItem(LIST_CLOSE, item(Material.BARRIER, "&c&lClose"));
        if (page < maxPage) inv.setItem(LIST_NEXT, item(Material.ARROW, "&eNext Page »", "&7Go to page &f" + (page + 1)));

        show(p, inv, holder);
        sound(p, Sound.BLOCK_CHEST_OPEN);
    }

    private ItemStack buildIcon(int id, boolean admin) {
        Recipe r = store.get(id);
        if (r == null || r.output == null) return item(Material.BARRIER, "&c&lBroken Recipe #" + id);

        ItemStack icon = r.output.clone();
        int amount = icon.getAmount();
        icon.setAmount(1);
        Component name = Text.itemName(r.output);
        icon.editMeta(meta -> {
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Text.legacy("&7Click to view this recipe"));
            lore.add(Text.legacy("&8"));
            lore.add(Text.legacy("&fResult: &e" + amount + "x &f").append(name));
            lore.add(Text.legacy("&fRecipe: &7" + r.name));
            if (admin) lore.add(Text.legacy("&fID: &7#" + id));
            lore.add(Text.legacy("&8"));
            lore.add(Text.legacy("&eLeft Click &7» View Recipe"));
            meta.lore(lore);
        });
        return icon;
    }

    // ================================================================== viewer

    void openViewer(Player p, int id, int returnPage) {
        Recipe r = store.get(id);
        if (r == null) {
            Text.send(p, "&cThat recipe no longer exists.");
            return;
        }
        RecipeHolder holder = new RecipeHolder(RecipeHolder.Type.VIEWER);
        holder.recipeId = id;
        holder.page = returnPage;
        Inventory inv = create(holder, 45, "viewer");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        for (int i = 0; i < 9; i++) {
            ItemStack ing = r.ingredients[i];
            inv.setItem(GRID[i], ing == null ? null : ing.clone());
        }

        ItemStack display = r.output.clone();
        int amount = display.getAmount();
        Component name = Text.itemName(r.output);
        display.editMeta(meta -> {
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Text.legacy("&8"));
            lore.add(Text.legacy("&a&lCRAFTING RESULT"));
            lore.add(Text.legacy("&8"));
            lore.add(Text.legacy("&fAmount: &e" + amount));
            lore.add(Text.legacy("&fItem: &7").append(name));
            meta.lore(lore);
        });
        inv.setItem(OUT, display);

        List<Integer> ids = store.ids();
        int pos = ids.indexOf(id);
        if (pos > 0) holder.prevId = ids.get(pos - 1);
        if (pos >= 0 && pos < ids.size() - 1) holder.nextId = ids.get(pos + 1);

        if (holder.prevId != null) {
            inv.setItem(VIEW_PREV, item(Material.LIME_STAINED_GLASS_PANE, "&a« Previous Recipe", "&7View the previous recipe"));
        }
        inv.setItem(VIEW_BACK, item(Material.BARRIER, "&c« Close", "&7Return to the recipe list"));
        if (holder.nextId != null) {
            inv.setItem(VIEW_NEXT, item(Material.GREEN_STAINED_GLASS_PANE, "&aNext Recipe »", "&7View the next recipe"));
        }

        show(p, inv, holder);
        sound(p, Sound.UI_BUTTON_CLICK);
    }

    // ================================================================== editor : /addrecipe, /editrecipe

    /** @param existing recipe to edit, or null to create a new one */
    void openEditor(Player p, Recipe existing) {
        RecipeHolder holder = new RecipeHolder(RecipeHolder.Type.EDITOR);
        holder.editMode = existing != null;
        holder.recipeId = existing != null ? existing.id : -1;
        Inventory inv = create(holder, 45, "editor");
        fill(inv, Material.WHITE_STAINED_GLASS_PANE);

        for (int slot : GRID) inv.setItem(slot, null);
        inv.setItem(OUT, null);

        inv.setItem(EDIT_INFO, item(Material.PAPER, "&b&lHow To Use",
                "&7Drag ingredients into the 3x3 grid",
                "&7Drag the crafted result into the result slot",
                "&7Click SAVE when you're done",
                "&8",
                existing != null ? "&7Editing recipe &f#" + existing.id : "&7Creating a new recipe"));
        inv.setItem(EDIT_SAVE, item(Material.LIME_DYE, "&a&l✔ SAVE RECIPE", "&7Click to save this recipe"));
        inv.setItem(EDIT_CANCEL, item(Material.RED_DYE, "&c&l✖ Cancel", "&7Discard changes and close"));

        if (existing != null) {
            for (int i = 0; i < 9; i++) {
                if (existing.ingredients[i] != null) inv.setItem(GRID[i], markGhost(existing.ingredients[i]));
            }
            inv.setItem(OUT, markGhost(existing.output));
        }

        show(p, inv, holder);
        sound(p, Sound.BLOCK_CHEST_OPEN);
    }

    /** Called when SAVE is clicked. */
    void saveEditor(Player p, RecipeHolder holder) {
        if (holder.finished) return;
        Inventory inv = holder.inventory;

        ItemStack out = inv.getItem(OUT);
        if (isAir(out)) {
            Text.send(p, "&cYou must place an output item before saving.");
            return;
        }
        ItemStack[] ingredients = new ItemStack[9];
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack ing = inv.getItem(GRID[i]);
            if (!isAir(ing)) {
                ingredients[i] = stripGhost(ing);
                any = true;
            }
        }
        if (!any) {
            Text.send(p, "&cYou must place at least one ingredient before saving.");
            return;
        }
        ItemStack output = stripGhost(out);
        String name = Text.plainName(output);

        int id;
        if (holder.editMode) {
            Recipe r = store.get(holder.recipeId);
            if (r == null) {
                Text.send(p, "&cThat recipe no longer exists.");
                return;
            }
            store.update(r, name, ingredients, output);
            id = r.id;
            Text.send(p, "&a✔ Recipe successfully updated!");
        } else {
            id = store.add(name, ingredients, output).id;
            Text.send(p, "&a✔ Recipe successfully added!");
        }
        Text.send(p, "&7Recipe ID: &f#" + id);
        Text.send(p, "&7Name: &f" + name);
        sound(p, Sound.ENTITY_PLAYER_LEVELUP);

        holder.finished = true;
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
    }

    /**
     * Editor closed (save, cancel, Esc, disconnect, shutdown): hand everything the player put in
     * back to them. Ghost copies of existing recipe items are simply deleted.
     */
    void returnEditorItems(Player p, Inventory inv) {
        List<ItemStack> back = new ArrayList<>();
        for (int slot : GRID) {
            ItemStack it = inv.getItem(slot);
            if (!isAir(it) && !isGhost(it)) back.add(it);
            inv.setItem(slot, null);
        }
        ItemStack out = inv.getItem(OUT);
        if (!isAir(out) && !isGhost(out)) back.add(out);
        inv.setItem(OUT, null);

        for (ItemStack it : back) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
        }
        if (isGhost(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    // ================================================================== delete confirmation : /delrecipe

    void openDeleteConfirm(Player p, int id) {
        Recipe r = store.get(id);
        if (r == null) {
            Text.send(p, "&cRecipe #" + id + " does not exist.");
            return;
        }
        RecipeHolder holder = new RecipeHolder(RecipeHolder.Type.DELETE);
        holder.recipeId = id;
        Inventory inv = create(holder, 27, "delete");
        fill(inv, Material.WHITE_STAINED_GLASS_PANE);

        ItemStack show = r.output.clone();
        addLore(show, "&8", "&cThis recipe will be permanently deleted.");
        inv.setItem(DEL_PREVIEW, show);
        inv.setItem(DEL_CONFIRM, item(Material.LIME_WOOL, "&a✔ Confirm", "&7Permanently delete this recipe"));
        inv.setItem(DEL_CANCEL, item(Material.RED_WOOL, "&c✖ Cancel", "&7Keep this recipe"));

        show(p, inv, holder);
    }

    // ================================================================== misc used by the listener

    void click(Player p) {
        sound(p, Sound.UI_BUTTON_CLICK);
    }

    void anvil(Player p) {
        sound(p, Sound.BLOCK_ANVIL_BREAK);
    }
}
