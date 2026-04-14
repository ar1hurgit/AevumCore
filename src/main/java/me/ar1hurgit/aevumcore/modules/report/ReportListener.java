package me.ar1hurgit.aevumcore.modules.report;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class ReportListener implements Listener {

    private final ReportManager manager;

    public ReportListener(ReportManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!manager.isMenuTitle(event.getView().getTitle())) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick()) return;

        manager.handleMenuClick(player, event.getRawSlot(), click);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.isMenuTitle(event.getView().getTitle())) return;

        manager.handleMenuClose(player.getUniqueId());
    }
}
