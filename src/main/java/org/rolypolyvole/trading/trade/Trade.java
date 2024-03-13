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
import java.util.Map;

public class Trade {
    public static final List<Integer> playerSlots = List.of(0, 1, 2, 3, 9, 10, 11, 12);
    public static final List<Integer> otherPlayerSlots = List.of(5, 6, 7, 8, 14, 15, 16, 17);
    private final Trading main;
    private final List<Player> players;
    private final HashMap<Player, HashMap<Integer, ItemStack>> playerToItemsMap = new HashMap<>(2);
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

    public void endTrade(boolean force) {
        if (isEnding) return; // Prevent recursion if already ending
        isEnding = true;

        players.forEach(main.tradingPlayers::remove);
        players.forEach(Player::closeInventory);
        main.invToTradeMap.entrySet().removeIf(entry -> entry.getValue().equals(this));

        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        for (Player player : players) {
            Inventory playerInventory = player.getInventory();
            HashMap<Integer, ItemStack> itemsMap = playerToItemsMap.get(force ? player : getOtherPlayer(player));

            itemsMap.forEach((index, item) -> playerInventory.addItem(item).forEach(
                (slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover)
            ));

            if (force) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0F, 1.0F);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);
            }
        }
    }

    public synchronized void addItemToTrade(Player player, ItemStack item) {
        Player otherPlayer = getOtherPlayer(player);
        Inventory otherInventory = otherPlayer.getOpenInventory().getTopInventory();
        Inventory topInventory = player.getOpenInventory().getTopInventory();

        if (!attemptAddItemToGUI(item, player)) { //Returns true if it dropped any items
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 0.75F);
            return;
        }

        playerToItemsMap.get(player).clear();

        for (int slot : playerSlots) {
            ItemStack slotItem = topInventory.getItem(slot);
            int index = playerSlots.indexOf(slot);

            if (slotItem != null && slotItem.getType() != Material.AIR) {
                otherInventory.setItem(otherPlayerSlots.get(index), slotItem);
                playerToItemsMap.get(player).put(index, slotItem);
            }
        }
    }

    public synchronized void removeItemFromTrade(Player player, ItemStack item, int clickedSlot) {
        Player otherPlayer = getOtherPlayer(player);
        Inventory otherInventory = otherPlayer.getOpenInventory().getTopInventory();
        Inventory playerInventory = player.getOpenInventory().getTopInventory();

        int removedIndex = playerSlots.indexOf(clickedSlot);
        playerToItemsMap.get(player).remove(removedIndex);
        playerInventory.removeItem(item);

        HashMap<Integer, ItemStack> shiftedItems = new HashMap<>();

        int index = 0;
        for (Map.Entry<Integer, ItemStack> entry : playerToItemsMap.get(player).entrySet()) {
            shiftedItems.put(index++, entry.getValue());
        }

        shiftedItems.forEach((i, itemStack) -> {
            playerInventory.setItem(playerSlots.get(i), itemStack);
            otherInventory.setItem(otherPlayerSlots.get(i), itemStack);
        });

        playerToItemsMap.get(player).clear();
        playerToItemsMap.get(player).putAll(shiftedItems);
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

    private boolean attemptAddItemToGUI(ItemStack item, Player player) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

        if (leftovers.isEmpty()) {
            return true;
        } else {
            leftovers.forEach((slot, leftover) -> player.getInventory().addItem(leftover));
            return false;
        }
    }

    private void startCountdown() {
        task = new CountdownTask().runTaskTimer(main, 0, 1);
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
