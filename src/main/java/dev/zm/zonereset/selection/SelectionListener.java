package dev.zm.zonereset.selection;

import dev.zm.zonereset.lang.LanguageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class SelectionListener implements Listener {

    private final WandManager wandManager;
    private final PlayerSelectionStore selectionStore;
    private final LanguageManager langManager;

    public SelectionListener(WandManager wandManager, PlayerSelectionStore selectionStore,
            LanguageManager langManager) {
        this.wandManager = wandManager;
        this.selectionStore = selectionStore;
        this.langManager = langManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem())
            return;
        if (!wandManager.isWand(event.getItem()))
            return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("zmzonereset.admin"))
            return;

        Action action = event.getAction();
        Location clickedLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;

        if (clickedLoc == null)
            return;

        PlayerSelection sel = selectionStore.getSelection(player.getUniqueId());

        if (action == Action.LEFT_CLICK_BLOCK) {
            sel.setPos1(clickedLoc);
            player.sendMessage(langManager.getMessage("wand.pos1-set",
                    "x", String.valueOf(clickedLoc.getBlockX()),
                    "y", String.valueOf(clickedLoc.getBlockY()),
                    "z", String.valueOf(clickedLoc.getBlockZ())));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            sel.setPos2(clickedLoc);
            player.sendMessage(langManager.getMessage("wand.pos2-set",
                    "x", String.valueOf(clickedLoc.getBlockX()),
                    "y", String.valueOf(clickedLoc.getBlockY()),
                    "z", String.valueOf(clickedLoc.getBlockZ())));
        }
    }
}
