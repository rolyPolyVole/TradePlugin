package org.rolypolyvole.trading.events;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.rolypolyvole.trading.Trading;
import org.rolypolyvole.trading.events.baseEvent.TradeMenuEvent;
import org.rolypolyvole.trading.trade.Trade;

import java.util.HashMap;
import java.util.List;

public class TradeMenuInteract extends TradeMenuEvent {
    public TradeMenuInteract(Trading main) {
        super(main);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTradeMenuRemoveItem(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        List<Integer> interactableSlots = List.of(0, 1, 2, 3, 9, 10, 11, 12);

        if (isTradeGUI(inventory)) {
            ItemStack item = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            if (item == null || item.getType().equals(Material.AIR)) {
                return;
            }

            if (interactableSlots.contains(event.getSlot())) {
                assert inventory != null;
                inventory.setItem(event.getSlot(), new ItemStack(Material.AIR));

                Trade trade = main.invToTradeMap.get(inventory);
                trade.removeItem(player, item);

                player.getInventory().addItem(item).forEach(
                    (slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover)
                );
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTradeMenuAddItem(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        if (item == null || item.getType().equals(Material.AIR)) {
            return;
        }

        Inventory upperInventory = event.getInventory();

        if (isTradeGUI(upperInventory) && !clickedInventory.equals(upperInventory)) {
            HashMap<Integer, ItemStack> leftOver = upperInventory.addItem(item);

            event.setCancelled(true);

            if (leftOver.isEmpty()) {
                player.getInventory().removeItem(item);
                main.invToTradeMap.get(upperInventory).addItem(player, item);

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.0F);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 0.75F);
            }
        }
    }
}
