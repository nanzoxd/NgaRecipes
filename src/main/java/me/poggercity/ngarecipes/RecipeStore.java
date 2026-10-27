package me.poggercity.ngarecipes;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;

/** Holds every recipe, keeps showcase order, persists to recipes.yml and registers crafting recipes. */
public final class RecipeStore {

    private final NgaRecipes plugin;
    private final File file;
    private final Map<Integer, Recipe> recipes = new HashMap<>();
    private final List<Integer> order = new ArrayList<>();
    private int nextId = 0;

    RecipeStore(NgaRecipes plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recipes.yml");
    }

    // ------------------------------------------------------------------ queries

    List<Integer> ids() {
        return Collections.unmodifiableList(order);
    }

    Recipe get(int id) {
        return recipes.get(id);
    }

    boolean exists(int id) {
        return recipes.containsKey(id);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Recipes whose output matches a typed name, e.g. "apple", "golden_apple", "Golden Apple".
     * Compares against the item type and the recipe name. Exact matches win; if there are none,
     * recipes whose output name merely contains the text are returned.
     */
    List<Integer> findByName(String query) {
        String q = norm(query);
        List<Integer> exact = new ArrayList<>(), partial = new ArrayList<>();
        if (q.isEmpty()) return exact;
        for (int id : order) {
            Recipe r = recipes.get(id);
            String type = norm(r.output.getType().name());
            String name = norm(r.name);
            if (type.equals(q) || name.equals(q)) exact.add(id);
            else if (type.contains(q) || name.contains(q)) partial.add(id);
        }
        return exact.isEmpty() ? partial : exact;
    }

    /** Distinct lowercase output item names, for tab completion. */
    List<String> outputNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (int id : order) names.add(recipes.get(id).output.getType().name().toLowerCase(java.util.Locale.ROOT));
        return new ArrayList<>(names);
    }

    int size() {
        return order.size();
    }

    // ------------------------------------------------------------------ mutations

    /** Adds a new recipe at the end of the showcase and returns it. */
    Recipe add(String name, ItemStack[] ingredients, ItemStack output) {
        Recipe r = new Recipe(++nextId);
        r.name = name;
        r.ingredients = ingredients;
        r.output = output;
        recipes.put(r.id, r);
        order.add(r.id);
        register(r);
        save();
        return r;
    }

    void update(Recipe r, String name, ItemStack[] ingredients, ItemStack output) {
        r.name = name;
        r.ingredients = ingredients;
        r.output = output;
        register(r);
        save();
    }

    void remove(int id) {
        Recipe r = recipes.remove(id);
        order.remove(Integer.valueOf(id));
        if (r != null) Bukkit.removeRecipe(key(id));
        save();
    }

    // ------------------------------------------------------------------ persistence

    void load() {
        unregisterAll();
        recipes.clear();
        order.clear();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        nextId = cfg.getInt("next-id", 0);

        ConfigurationSection sec = cfg.getConfigurationSection("recipes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    continue;
                }
                ConfigurationSection rs = sec.getConfigurationSection(key);
                if (rs == null) continue;
                ItemStack out = rs.getItemStack("output");
                if (out == null) {
                    plugin.getLogger().warning("Recipe #" + id + " has no valid output, skipping.");
                    continue;
                }
                Recipe r = new Recipe(id);
                r.name = rs.getString("name", Text.plainName(out));
                r.output = out;
                for (int i = 0; i < 9; i++) {
                    r.ingredients[i] = rs.getItemStack("ingredients." + (i + 1));
                }
                recipes.put(id, r);
            }
        }

        // showcase order: saved order first, then anything left over by id
        for (int id : cfg.getIntegerList("order")) {
            if (recipes.containsKey(id) && !order.contains(id)) order.add(id);
        }
        for (int id : new TreeSet<>(recipes.keySet())) {
            if (!order.contains(id)) order.add(id);
        }
        for (int id : recipes.keySet()) nextId = Math.max(nextId, id);

        for (int id : order) register(recipes.get(id));
        plugin.getLogger().info("Loaded " + order.size() + " recipe(s).");
    }

    void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextId);
        cfg.set("order", new ArrayList<>(order));
        for (int id : order) {
            Recipe r = recipes.get(id);
            String base = "recipes." + id;
            cfg.set(base + ".name", r.name);
            cfg.set(base + ".output", r.output);
            for (int i = 0; i < 9; i++) {
                if (r.ingredients[i] != null) cfg.set(base + ".ingredients." + (i + 1), r.ingredients[i]);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save recipes.yml", ex);
        }
    }

    // ------------------------------------------------------------------ crafting registration

    private NamespacedKey key(int id) {
        return new NamespacedKey(plugin, "recipe_" + id);
    }

    void unregisterAll() {
        for (int id : recipes.keySet()) Bukkit.removeRecipe(key(id));
    }

    /** Registers (or re-registers) a recipe as a real shaped crafting recipe. */
    void register(Recipe r) {
        NamespacedKey key = key(r.id);
        Bukkit.removeRecipe(key);
        if (!plugin.getConfig().getBoolean("register-crafting-recipes", true)) return;

        // trim the 3x3 grid to its bounding box
        int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
        for (int i = 0; i < 9; i++) {
            if (r.ingredients[i] == null) continue;
            int row = i / 3, col = i % 3;
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
        }
        if (maxRow < 0) return; // no ingredients, display-only

        try {
            ShapedRecipe shaped = new ShapedRecipe(key, r.output.clone());
            String[] shape = new String[maxRow - minRow + 1];
            for (int row = minRow; row <= maxRow; row++) {
                StringBuilder sb = new StringBuilder();
                for (int col = minCol; col <= maxCol; col++) {
                    int i = row * 3 + col;
                    sb.append(r.ingredients[i] == null ? ' ' : (char) ('A' + i));
                }
                shape[row - minRow] = sb.toString();
            }
            shaped.shape(shape);
            for (int i = 0; i < 9; i++) {
                if (r.ingredients[i] == null) continue;
                ItemStack one = r.ingredients[i].clone();
                one.setAmount(1);
                shaped.setIngredient((char) ('A' + i), new RecipeChoice.ExactChoice(one));
            }
            Bukkit.addRecipe(shaped);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not register crafting recipe #" + r.id + ": " + ex.getMessage());
        }
    }
}
