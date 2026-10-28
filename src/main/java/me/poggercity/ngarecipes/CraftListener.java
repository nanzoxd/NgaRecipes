package me.poggercity.ngarecipes;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Makes ingredient AMOUNTS count. Bukkit recipes only match item type, so a recipe saved as
 * "2 ender pearls" would otherwise craft with just 1. This listener:
 *  - hides the result unless every slot holds at least the saved amount, and
 *  - takes the full saved amount from every slot when crafting (click and shift-click).
 */
public final class CraftListener implements Listener {

    private final NgaRecipes plugin;
    private final RecipeStore store;

    CraftListener(NgaRecipes plugin, RecipeStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private Recipe ours(org.bukkit.inventory.Recipe r) {
        return r instanceof Keyed k ? store.byKey(k.getKey()) : null;
    }

    // ------------------------------------------------------------------ preview

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = ours(e.getRecipe());
        if (r == null) return;
        CraftingInventory inv = e.getInventory();
        if (required(inv.getMatrix(), r) == null) inv.setResult(null);
    }

    // ------------------------------------------------------------------ crafting

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        Recipe r = ours(e.getRecipe());
        if (r == null || !(e.getWhoClicked() instanceof Player p)) return;

        e.setCancelled(true); // we do the crafting ourselves so the right amounts are consumed
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        int[] req = required(matrix, r);
        if (req == null) {
            inv.setResult(null);
            p.updateInventory();
            return;
        }

        ItemStack result = r.output.clone();
        ClickType click = e.getClick();
        int crafts = 0;

        if (click.isShiftClick()) {
            int max = maxCrafts(matrix, req);
            while (crafts < max && space(p.getInventory(), result) >= result.getAmount()) {
                p.getInventory().addItem(result.clone());
                crafts++;
            }
        } else if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            ItemStack cursor = p.getItemOnCursor();
            if (Gui.isAir(cursor)) {
                p.setItemOnCursor(result);
                crafts = 1;
            } else if (cursor.isSimilar(result) && cursor.getAmount() + result.getAmount() <= cursor.getMaxStackSize()) {
                ItemStack c = cursor.clone();
                c.setAmount(c.getAmount() + result.getAmount());
                p.setItemOnCursor(c);
                crafts = 1;
            }
        } else if (click == ClickType.NUMBER_KEY) {
            int slot = e.getHotbarButton();
            if (slot >= 0 && slot < 9 && Gui.isAir(p.getInventory().getItem(slot))) {
                p.getInventory().setItem(slot, result);
                crafts = 1;
            }
        }

        if (crafts > 0) {
            for (int i = 0; i < matrix.length; i++) {
                if (req[i] <= 0 || matrix[i] == null) continue;
                ItemStack left = matrix[i].clone();
                left.setAmount(left.getAmount() - req[i] * crafts);
                matrix[i] = left.getAmount() > 0 ? left : null;
            }
            inv.setMatrix(matrix);
            inv.setResult(required(inv.getMatrix(), r) != null ? r.output.clone() : null);
        }
        p.updateInventory();
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Compares the crafting grid with the recipe, amounts included.
     * @return amount needed per matrix slot, or null if the grid doesn't match / is short
     */
    static int[] required(ItemStack[] m, Recipe r) {
        int n = m.length;
        int mw = n == 4 ? 2 : 3;

        int[] mb = bounds(m, mw);
        int[] rb = bounds(r.ingredients, 3);
        if (mb == null || rb == null) return null;
        int h = mb[1] - mb[0] + 1, w = mb[3] - mb[2] + 1;
        if (h != rb[1] - rb[0] + 1 || w != rb[3] - rb[2] + 1) return null;

        for (int pass = 0; pass < 2; pass++) { // pass 1 = recipe mirrored left/right (Bukkit allows it)
            boolean mirror = pass == 1;
            int[] req = new int[n];
            boolean ok = true;
            for (int row = 0; row < h && ok; row++) {
                for (int col = 0; col < w && ok; col++) {
                    int mi = (mb[0] + row) * mw + mb[2] + col;
                    int rc = mirror ? w - 1 - col : col;
                    ItemStack need = r.ingredients[(rb[0] + row) * 3 + rb[2] + rc];
                    ItemStack have = m[mi];
                    if (Gui.isAir(need) != Gui.isAir(have)) {
                        ok = false;
                    } else if (!Gui.isAir(need)) {
                        if (!have.isSimilar(need) || have.getAmount() < need.getAmount()) ok = false;
                        else req[mi] = need.getAmount();
                    }
                }
            }
            if (ok) return req;
        }
        return null;
    }

    /** {minRow, maxRow, minCol, maxCol} of the non-empty cells, or null if empty. */
    private static int[] bounds(ItemStack[] grid, int width) {
        int minR = width, maxR = -1, minC = width, maxC = -1;
        for (int i = 0; i < grid.length; i++) {
            if (Gui.isAir(grid[i])) continue;
            int row = i / width, col = i % width;
            minR = Math.min(minR, row);
            maxR = Math.max(maxR, row);
            minC = Math.min(minC, col);
            maxC = Math.max(maxC, col);
        }
        return maxR < 0 ? null : new int[]{minR, maxR, minC, maxC};
    }

    private static int maxCrafts(ItemStack[] m, int[] req) {
        int max = Integer.MAX_VALUE;
        for (int i = 0; i < m.length; i++) {
            if (req[i] > 0 && m[i] != null) max = Math.min(max, m[i].getAmount() / req[i]);
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    /** How many more of this item fit in the player's main inventory. */
    private static int space(PlayerInventory inv, ItemStack item) {
        int free = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (Gui.isAir(s)) free += item.getMaxStackSize();
            else if (s.isSimilar(item)) free += Math.max(0, s.getMaxStackSize() - s.getAmount());
        }
        return free;
    }
}
