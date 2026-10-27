package me.poggercity.ngarecipes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/** Small text helpers: '&' colour codes, MiniMessage titles, item names. */
final class Text {
    private Text() {}

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** '&' coded text, with italics switched off (so lore/names don't render italic). */
    static Component legacy(String s) {
        return LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static Component mini(String s) {
        return MiniMessage.miniMessage().deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static void send(CommandSender to, String legacy) {
        to.sendMessage(LEGACY.deserialize(legacy));
    }

    /** Name shown to players: custom name if set, otherwise the client-translated item name. */
    static Component itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) return meta.displayName();
            if (meta.hasItemName()) return meta.itemName();
        }
        return Component.translatable(item.getType().translationKey());
    }

    /** Plain-text name for storing in recipes.yml / chat messages. */
    static String plainName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            if (meta.hasItemName()) return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        }
        return pretty(item.getType());
    }

    static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
