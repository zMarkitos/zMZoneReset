package dev.zm.zonereset.selection;

import dev.zm.zonereset.lang.ColorParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class WandManager {

    private final NamespacedKey wandKey;
    private final dev.zm.zonereset.lang.LanguageManager lang;

    public WandManager(JavaPlugin plugin, dev.zm.zonereset.lang.LanguageManager lang) {
        this.wandKey = new NamespacedKey(plugin, "wand");
        this.lang = lang;
    }

    public void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            String name = lang.getMessage("wand.name");
            if (name.equals(dev.zm.zonereset.lang.ColorParser.parse("&cError: Message 'wand.name' not found."))) {
                name = dev.zm.zonereset.lang.ColorParser.parse("&6&lVarita de zMZoneReset");
            }
            meta.setDisplayName(name);

            List<String> lore = lang.getMessageList("wand.lore");
            if (lore.isEmpty() || lore.get(0).startsWith(dev.zm.zonereset.lang.ColorParser.parse("&cMissing list key"))) {
                lore = new ArrayList<>();
                lore.add(dev.zm.zonereset.lang.ColorParser.parse("&7Click Izquierdo: &dSeleccionar Posición 1"));
                lore.add(dev.zm.zonereset.lang.ColorParser.parse("&7Click Derecho: &dSeleccionar Posición 2"));
            }
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }
}
