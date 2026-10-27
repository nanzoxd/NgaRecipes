package me.poggercity.ngarecipes;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NgaRecipes extends JavaPlugin {

    private RecipeStore store;
    private Gui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new RecipeStore(this);
        store.load();
        gui = new Gui(this, store);

        getServer().getPluginManager().registerEvents(new GuiListener(this, gui, store), this);

        RecipeCommands commands = new RecipeCommands(this, gui, store);
        for (String name : new String[]{"recipes", "addrecipe", "editrecipe", "delrecipe"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }
    }

    @Override
    public void onDisable() {
        // Close open GUIs so anything sitting in an editor is handed back to its owner.
        if (gui != null) gui.closeAll();
        if (store != null) store.unregisterAll();
    }

    /** Re-reads config.yml and recipes.yml from disk. */
    void reloadAll() {
        reloadConfig();
        store.load();
    }
}
