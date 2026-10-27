package me.poggercity.ngarecipes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * All GUI input handling. Only the editor's 3x3 grid and result slot accept items (normal
 * drag-and-drop); every other slot in every GUI is locked.
 *
 * Note: uses event.getInventory() (top inventory) instead of getView() on purpose, so the built
 * jar works across 1.21.x despite InventoryView changing from class to interface.
 */
public final class GuiListener implements Listener {

    private final NgaRecipes plugin;
    private final Gui gui;
    private final RecipeStore store;

    GuiListener(NgaRecipes plugin, Gui gui, RecipeStore store) {
        this.plugin = plugin;
        this.gui = gui;
        this.store = store;
    }

    private static RecipeHolder holderOf(Inventory top) {
        return top.getHolder(false) instanceof RecipeHolder h ? h : null;
    }

    // ================================================================== clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        RecipeHolder h = holderOf(top);
        if (h == null || !(e.getWhoClicked() instanceof Player p)) return;

        if (h.type == RecipeHolder.Type.EDITOR) {
            editorClick(e, p, h, top);
            return;
        }

        // every other GUI is fully locked, including shift-clicks from the player inventory
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        int slot = e.getRawSlot();

        switch (h.type) {
            case LIST -> listClick(p, h, slot);
            case VIEWER -> viewerClick(p, h, slot);
            case DELETE -> deleteClick(p, h, slot);
            default -> { }
        }
    }

    private void listClick(Player p, RecipeHolder h, int slot) {
        if (slot == Gui.LIST_CLOSE) {
            gui.click(p);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        } else if (slot == Gui.LIST_PREV && h.page > 1) {
            gui.click(p);
            gui.openList(p, h.page - 1);
        } else if (slot == Gui.LIST_NEXT) {
            // openList clamps the page, so a click on an empty slot is harmless
            if (isButton(h, slot)) {
                gui.click(p);
                gui.openList(p, h.page + 1);
            }
        } else if (slot >= 0 && slot < 45) {
            Integer id = h.slotMap.get(slot);
            if (id != null) {
                gui.click(p);
                gui.openViewer(p, id, h.page);
            }
        }
    }

    /** True if the slot currently holds a real button (not glass), i.e. the arrow was actually shown. */
    private boolean isButton(RecipeHolder h, int slot) {
        ItemStack it = h.inventory.getItem(slot);
        return it != null && it.getType() == Material.ARROW;
    }

    private void viewerClick(Player p, RecipeHolder h, int slot) {
        if (slot == Gui.VIEW_BACK) {
            gui.click(p);
            gui.openList(p, h.page);
        } else if (slot == Gui.VIEW_PREV && h.prevId != null) {
            gui.openViewer(p, h.prevId, h.page);
        } else if (slot == Gui.VIEW_NEXT && h.nextId != null) {
            gui.openViewer(p, h.nextId, h.page);
        }
    }

    private void deleteClick(Player p, RecipeHolder h, int slot) {
        if (!p.hasPermission(Gui.PERM_ADMIN)) return;
        if (slot == Gui.DEL_CONFIRM) {
            store.remove(h.recipeId);
            Text.send(p, "&a✔ Recipe #" + h.recipeId + " deleted.");
            gui.anvil(p);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        } else if (slot == Gui.DEL_CANCEL) {
            Text.send(p, "&7Deletion cancelled.");
            gui.click(p);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        }
    }

    // ------------------------------------------------------------------ editor

    private void editorClick(InventoryClickEvent e, Player p, RecipeHolder h, Inventory top) {
        if (!p.hasPermission(Gui.PERM_ADMIN)) {
            e.setCancelled(true);
            return;
        }
        InventoryAction action = e.getAction();
        ClickType click = e.getClick();

        // Bulk/automatic movement could push items into locked slots; drag-and-drop only.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }

        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        // A ghost (copy of a saved item) must never leave the editor.
        if (!inTop && gui.isGhost(cursor)) {                       // placing it in the player's inventory / dropping outside
            e.setCancelled(true);
            return;
        }
        if (gui.isGhost(current) && (isDrop(click) || isHotkey(click))) { // Q-drop or hotbar swap would take it out
            e.setCancelled(true);
            return;
        }

        if (!inTop) return; // normal inventory handling in the player's own inventory

        if (raw == Gui.EDIT_SAVE) {
            e.setCancelled(true);
            gui.saveEditor(p, h);
        } else if (raw == Gui.EDIT_CANCEL) {
            e.setCancelled(true);
            Text.send(p, "&7Recipe creation cancelled. Your items were returned.");
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        } else if (!Gui.isEditable(raw)) {
            e.setCancelled(true); // border glass, info paper
        }
        // editable slots: let vanilla pick up / place / swap happen
    }

    private static boolean isDrop(ClickType c) {
        return c == ClickType.DROP || c == ClickType.CONTROL_DROP;
    }

    private static boolean isHotkey(ClickType c) {
        return c == ClickType.NUMBER_KEY || c == ClickType.SWAP_OFFHAND;
    }

    // ================================================================== drag

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getInventory();
        RecipeHolder h = holderOf(top);
        if (h == null) return;

        if (h.type != RecipeHolder.Type.EDITOR) {
            e.setCancelled(true);
            return;
        }
        boolean ghostCursor = gui.isGhost(e.getOldCursor());
        for (int raw : e.getRawSlots()) {
            boolean inTop = raw < top.getSize();
            if (inTop && !Gui.isEditable(raw)) {
                e.setCancelled(true);   // painting across a locked slot
                return;
            }
            if (!inTop && ghostCursor) {
                e.setCancelled(true);   // ghost into the player's inventory
                return;
            }
        }
    }

    // ================================================================== close

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        RecipeHolder h = holderOf(inv);
        if (h == null) return;

        if (h.type == RecipeHolder.Type.EDITOR) {
            gui.returnEditorItems(p, inv);
        }
        // Gui.show() registers the next GUI only after opening it, i.e. after this close fires,
        // so forgetting here is safe even when switching straight between our GUIs.
        gui.forget(p);
    }
}
