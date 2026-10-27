package me.poggercity.ngarecipes;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /recipes [reload], /addrecipe, /editrecipe <id|item>, /delrecipe <id|item> */
public final class RecipeCommands implements TabExecutor {

    private final NgaRecipes plugin;
    private final Gui gui;
    private final RecipeStore store;

    RecipeCommands(NgaRecipes plugin, Gui gui, RecipeStore store) {
        this.plugin = plugin;
        this.gui = gui;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "recipes" -> recipes(sender, args);
            case "addrecipe" -> {
                Player p = playerOnly(sender);
                if (p != null) gui.openEditor(p, null);
            }
            case "editrecipe" -> {
                Player p = playerOnly(sender);
                Integer id = p == null ? null : idArg(p, label, args);
                if (id != null) gui.openEditor(p, store.get(id));
            }
            case "delrecipe" -> {
                Player p = playerOnly(sender);
                Integer id = p == null ? null : idArg(p, label, args);
                if (id != null) gui.openDeleteConfirm(p, id);
            }
            default -> { return false; }
        }
        return true;
    }

    private void recipes(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(Gui.PERM_ADMIN)) {
                Text.send(sender, "&cYou don't have permission to do that.");
                return;
            }
            plugin.reloadAll();
            Text.send(sender, "&a✔ Recipes reloaded. &7(" + store.size() + " recipe(s) found)");
            return;
        }
        Player p = playerOnly(sender);
        if (p != null) gui.openList(p, 1);
    }

    private Player playerOnly(CommandSender sender) {
        if (sender instanceof Player p) return p;
        Text.send(sender, "&cThis command can only be used by a player.");
        return null;
    }

    /**
     * Resolves the target recipe from what was typed: an id (/editrecipe 3) or an output item
     * name (/editrecipe apple, /editrecipe golden_apple). If several recipes match, they are
     * listed as clickable entries. Sends the error itself and returns null on failure.
     */
    private Integer idArg(Player p, String label, String[] args) {
        if (args.length < 1) {
            Text.send(p, "&cUsage: &f/" + label + " <id | item name>&7  e.g. /" + label + " apple");
            return null;
        }
        String query = String.join(" ", args);

        int id = -1;
        try {
            id = Integer.parseInt(query.trim());
        } catch (NumberFormatException ignored) {
        }
        if (id >= 0 && store.exists(id)) return id;
        if (id >= 0 && args.length == 1 && query.trim().matches("\\d+")) {
            Text.send(p, "&cRecipe #" + id + " does not exist.");
            return null;
        }

        List<Integer> found = store.findByName(query);
        if (found.isEmpty()) {
            Text.send(p, "&cNo recipe found for '&f" + query + "&c'. Use /recipes to see them all.");
            return null;
        }
        if (found.size() == 1) return found.get(0);

        Text.send(p, "&eMore than one recipe matches '&f" + query + "&e' - click one:");
        for (int fid : found) {
            Recipe r = store.get(fid);
            p.sendMessage(net.kyori.adventure.text.Component.text("  #" + fid + " ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(net.kyori.adventure.text.Component.text(r.name + " x" + r.output.getAmount(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/" + label + " " + fid))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to select"))));
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String prefix = args[0].toLowerCase(Locale.ROOT);
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("recipes")) {
            if (sender.hasPermission(Gui.PERM_ADMIN) && "reload".startsWith(prefix)) out.add("reload");
        } else if (name.equals("editrecipe") || name.equals("delrecipe")) {
            for (int id : store.ids()) {
                String s = String.valueOf(id);
                if (s.startsWith(prefix)) out.add(s);
            }
            for (String n : store.outputNames()) {
                if (n.startsWith(prefix)) out.add(n);
            }
        }
        return out;
    }
}
