package me.poggercity.ngarecipes;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as one of ours and carries its per-GUI state. */
public final class RecipeHolder implements InventoryHolder {

    public enum Type { LIST, VIEWER, EDITOR, DELETE }

    final Type type;
    Inventory inventory;

    /** LIST: current page. VIEWER: page of the list to return to. */
    int page = 1;
    /** VIEWER / DELETE / EDITOR(edit mode): the recipe in question. */
    int recipeId = -1;
    /** VIEWER: neighbouring recipes in showcase order (null = none). */
    Integer prevId, nextId;
    /** LIST: inventory slot -> recipe id. */
    final Map<Integer, Integer> slotMap = new HashMap<>();
    /** EDITOR: true when editing an existing recipe. */
    boolean editMode;
    /** EDITOR: set once saved so a double click can't save twice. */
    boolean finished;

    RecipeHolder(Type type) {
        this.type = type;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
