package org.rolypolyvole.trading.events.baseEvent;

import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.rolypolyvole.trading.Trading;

public abstract class TradeMenuEvent implements Listener {
    protected final Trading main;
    public TradeMenuEvent(Trading main) {
        this.main = main;
    }

    protected boolean isTradeGUI(Inventory gui) {
        return gui != null && main.invToTradeMap.containsKey(gui);
    }
}
