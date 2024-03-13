package org.rolypolyvole.trading.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.rolypolyvole.trading.Trading;
import org.rolypolyvole.trading.events.baseEvent.TradeMenuEvent;
import org.rolypolyvole.trading.trade.Trade;

public class TradeMenuClose extends TradeMenuEvent {
    public TradeMenuClose(Trading main) {
        super(main);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTradeMenuClose(InventoryCloseEvent event) {
        Inventory closedInventory = event.getInventory();

        if (isTradeGUI(closedInventory)) {
            Trade trade = main.invToTradeMap.get(closedInventory);
            trade.endTrade(true);
        }
    }
}
