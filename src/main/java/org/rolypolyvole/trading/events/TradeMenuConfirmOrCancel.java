package org.rolypolyvole.trading.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.rolypolyvole.trading.Trading;
import org.rolypolyvole.trading.events.baseEvent.TradeMenuEvent;
import org.rolypolyvole.trading.trade.Trade;

public class TradeMenuConfirmOrCancel extends TradeMenuEvent {
    public TradeMenuConfirmOrCancel(Trading main) {
        super(main);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTradeMenuConfirm(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Player player = (Player) event.getWhoClicked();

        if (clickedInventory == null) {
            return;
        }

        if (isTradeGUI(clickedInventory)) {
            ItemStack item = event.getCurrentItem();

            if (item == null) {
                return;
            }

            if (item.getType() == Material.GREEN_CONCRETE) {
                Trade trade = main.invToTradeMap.get(clickedInventory);
                trade.confirm(player);
            } else if (item.getType() == Material.RED_CONCRETE) {
                Trade trade = main.invToTradeMap.get(clickedInventory);
                trade.unconfirm(player);
            }
        }
    }
}
