package org.rolypolyvole.trading.trade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.rolypolyvole.trading.Trading;

import java.util.HashMap;
import java.util.List;

public class Trade {
    private final Trading main;
    private final List<Player> players;
    private final HashMap<Player, HashMap<Integer, ItemStack>> playerToItemsMap = new HashMap<>(2);
    private final List<Integer> playerSlots = List.of(0, 1, 2, 3, 9, 10, 11, 12);
    private final List<Integer> otherPlayerSlots = List.of(5, 6, 7, 8, 14, 15, 16, 17);
    private int confirmations = 0;
    private BukkitTask task;
    private boolean isEnding = false;
    public Trade(Trading main, Player player1, Player player2) {
        this.main = main;
        this.players = List.of(player1, player2);
        this.playerToItemsMap.put(player1, new HashMap<>(8));
        this.playerToItemsMap.put(player2, new HashMap<>(8));
    }

    public void startTrade() {
        players.forEach(this::showGUI);
        players.forEach(player -> player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0F, 1.0F));
    }

    public void endTrade(boolean terminate) {
        if (isEnding) return; // Prevent recursion if already ending
        isEnding = true;

        players.forEach(main.tradingPlayers::remove);
        players.forEach(Player::closeInventory);
        main.invToTradeMap.entrySet().removeIf(entry -> entry.getValue().equals(this));

        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        if (terminate) {
            for (Player player : players) {
                Inventory playerInventory = player.getInventory();
                HashMap<Integer, ItemStack> itemsMap = playerToItemsMap.get(player);

                itemsMap.forEach((index, item) -> playerInventory.addItem(item).forEach(
                    (slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover)
                ));

                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0F, 1.0F);
            }
        } else {
            for (Player player : players) {
                Inventory playerInventory = player.getInventory();
                HashMap<Integer, ItemStack> itemsMap = playerToItemsMap.get(getOtherPlayer(player));

                itemsMap.forEach((index, item) -> playerInventory.addItem(item).forEach(
                    (slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover)
                ));

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);
            }
        }
    }

    public synchronized void addItem(Player player, ItemStack item) {
        Player otherPlayer = getOtherPlayer(player);

        for (int i : otherPlayerSlots) {
            ItemStack slotItem = otherPlayer.getOpenInventory().getItem(i);
            int index = otherPlayerSlots.indexOf(i);

            assert slotItem != null;
            if (slotItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                otherPlayer.getOpenInventory().setItem(i, item);
                playerToItemsMap.get(player).put(index, item);

                break;
            } else if (slotItem.isSimilar(item) && slotItem.getAmount() < 64) {
                int quantity = slotItem.getAmount() + item.getAmount();
                int stackSize = Math.min(quantity, 64);
                slotItem.setAmount(stackSize);

                otherPlayer.getOpenInventory().setItem(i, slotItem);
                playerToItemsMap.get(player).put(index, slotItem);

                if (quantity > 64) {
                    addItem(player, item.asQuantity(quantity - 64));
                }

                break;
            }
        }
    }

    public synchronized void removeItem(Player player, ItemStack item) {
        for (int i : otherPlayerSlots) {
            int index = otherPlayerSlots.indexOf(i);

            ItemStack slotItem = getOtherPlayer(player).getOpenInventory().getItem(i);
            ItemStack invisFiller = buildFiller(true);

            assert slotItem != null;
            if (slotItem.equals(item)) {
                getOtherPlayer(player).getOpenInventory().setItem(i, invisFiller);
                playerToItemsMap.get(player).remove(index);

                shiftItems(player, index);
                break;
            }
        }
    }

    public void confirm(Player player) {
        confirmations++;

        if (confirmations == 2) {
            startCountdown();
        }

        changeToConfirmedGUI(player);
    }

    public void unconfirm(Player player) {
        confirmations--;

        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        changeToCancelledGUI(player);
    }

    private void startCountdown() {
        task = new CountdownTask().runTaskTimer(main, 0, 1);
    }

    private void shiftItems(Player player, int removedIndex) {
        //Update items map
        HashMap<Integer, ItemStack> itemsMap = playerToItemsMap.get(player);

        List<Integer> keysToShift = itemsMap.keySet().stream()
            .filter(key -> key > removedIndex)
            .sorted()
            .toList();

        keysToShift.forEach(key -> {
            ItemStack value = itemsMap.remove(key);
            itemsMap.put(key - 1, value);
        });

        //Shift items visually in both GUIs
        for (int i : otherPlayerSlots) {
            Inventory otherInventory = getOtherPlayer(player).getOpenInventory().getTopInventory();
            ItemStack slotItem = otherInventory.getItem(i);
            int slotIndex = otherPlayerSlots.indexOf(i);

            if (slotIndex > removedIndex) {
                assert slotItem != null;
                if (slotItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                    break;
                }

                otherInventory.setItem(i, buildFiller(true));
                otherInventory.setItem(i - (i == 4 ? 6 : 1), slotItem);
            }
        }

        for (int i : playerSlots) {
            Inventory playerInventory = player.getOpenInventory().getTopInventory();
            ItemStack slotItem = playerInventory.getItem(i);
            int slotIndex = playerSlots.indexOf(i);

            if (slotIndex > removedIndex) {
                assert slotItem != null;
                if (slotItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                    break;
                }

                playerInventory.setItem(i, buildFiller(true));
                playerInventory.setItem(i - (i == 4 ? 6 : 1), slotItem);
            }
        }
    }

    private Player getOtherPlayer(@NotNull Player player) {
        return players
            .stream()
            .filter(p -> !p.equals(player))
            .findFirst()
            .orElse(null);
    }

    private void showGUI(@NotNull Player player) {
        String name = getOtherPlayer(player).getName();
        String shortName = name.length() > 10 ? name.substring(0, 10) + "..." : name;
        Component title = Component.text("You" + " ".repeat(18) + shortName);

        Inventory gui = Bukkit.createInventory(
            player,
            27,
            title
        );

        //Invisible filler
        ItemStack invisFiller = buildFiller(true);

        //Filler
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();

        fillerMeta.displayName(Component.text(" ", NamedTextColor.BLACK).decoration(TextDecoration.ITALIC, false));
        filler.setItemMeta(fillerMeta);

        //Confirm button
        ItemStack confirm = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();

        confirmMeta.displayName(Component.text("Confirm", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        confirm.setItemMeta(confirmMeta);

        //Status display
        ItemStack status = new ItemStack(Material.GRAY_DYE);
        ItemMeta statusMeta = status.getItemMeta();

        statusMeta.displayName(Component.text("Not Ready", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        status.setItemMeta(statusMeta);

        //Diamond
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        ItemMeta diamondMeta = diamond.getItemMeta();

        diamondMeta.displayName(Component.text("Diamond", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        diamondMeta.lore(List.of(Component.text("Offer diamonds", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        diamond.setItemMeta(addEnchantGlint(diamondMeta));

        for (int i : new int[] {4, 13, 18, 22, 24, 25, 26}) {
            gui.setItem(i, filler);
        }

        for (int i : otherPlayerSlots) {
            gui.setItem(i, invisFiller);
        }

        gui.setItem(19, confirm);
        gui.setItem(20, diamond);
        gui.setItem(21, status);
        gui.setItem(23, status);

        player.openInventory(gui);
        main.invToTradeMap.put(gui, this);
    }

    private void changeToConfirmedGUI(@NotNull Player confirmedPlayer) {
        //Cancel button
        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();

        cancelMeta.displayName(Component.text("Cancel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        cancel.setItemMeta(cancelMeta);

        //Status display
        ItemStack status = new ItemStack(Material.MAGENTA_DYE);
        ItemMeta statusMeta = status.getItemMeta();

        statusMeta.displayName(Component.text("Ready!", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        status.setItemMeta(addEnchantGlint(statusMeta));

        //Update GUI
        Inventory gui = confirmedPlayer.getOpenInventory().getTopInventory();
        gui.setItem(19, cancel);
        gui.setItem(20, buildFiller(false));
        gui.setItem(21, status);

        Inventory otherGui = getOtherPlayer(confirmedPlayer).getOpenInventory().getTopInventory();
        otherGui.setItem(23, status);
    }

    private void changeToCancelledGUI(@NotNull Player cancelledPlayer) {
        //Confirm button
        ItemStack confirm = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();

        confirmMeta.displayName(Component.text("Confirm", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        confirm.setItemMeta(confirmMeta);

        //Diamond
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        ItemMeta diamondMeta = diamond.getItemMeta();

        diamondMeta.displayName(Component.text("Diamond", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        diamondMeta.lore(List.of(Component.text("Offer diamonds", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        diamond.setItemMeta(addEnchantGlint(diamondMeta));

        //Status display
        ItemStack status = new ItemStack(Material.GRAY_DYE);
        ItemMeta statusMeta = status.getItemMeta();

        statusMeta.displayName(Component.text("Not Ready", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        status.setItemMeta(statusMeta);

        //Update GUI
        Inventory gui = cancelledPlayer.getOpenInventory().getTopInventory();
        gui.setItem(19, confirm);
        gui.setItem(20, diamond);
        gui.setItem(21, status);

        Inventory otherGui = getOtherPlayer(cancelledPlayer).getOpenInventory().getTopInventory();
        otherGui.setItem(23, status);
    }

    private @NotNull ItemStack buildFiller(boolean invisible) {
        ItemStack filler = new ItemStack(invisible ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();

        fillerMeta.displayName(Component.text(" ", NamedTextColor.BLACK).decoration(TextDecoration.ITALIC, false));
        filler.setItemMeta(fillerMeta);

        return filler;
    }

    @Contract("_ -> param1")
    private @NotNull ItemMeta addEnchantGlint(@NotNull ItemMeta meta) {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return meta;
    }

    private class CountdownTask extends BukkitRunnable {
        private int ticks = 60;
        private int seconds = 3;
        @Override
        public void run() {
            if (ticks == 0) {
                endTrade(false);
                cancel();
            }

            if (ticks % 20 == 0) {
                for (Player player : players) {
                    Inventory gui = player.getOpenInventory().getTopInventory();
                    if (!main.invToTradeMap.containsKey(gui)) {
                        cancel();
                        return;
                    }

                    ItemStack countdown = new ItemStack(Material.YELLOW_DYE, seconds);
                    ItemMeta countdownMeta = countdown.getItemMeta();

                    countdownMeta.displayName(Component.text("Countdown", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                    countdown.setItemMeta(countdownMeta);

                    gui.setItem(21, countdown);
                    gui.setItem(23, countdown);

                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
                }

                seconds--;
            }

            ticks--;
        }
    }
}
