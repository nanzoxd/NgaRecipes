package me.poggercity.ngarecipes;

import org.bukkit.inventory.ItemStack;

/** One saved recipe: a 3x3 ingredient layout (index 0-8, row-major, null = empty) and an output. */
public final class Recipe {
    final int id;
    String name;
    ItemStack[] ingredients = new ItemStack[9];
    ItemStack output;
    /** true = also a real crafting recipe; false = showcase only (display-only). */
    boolean working = true;

    Recipe(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
