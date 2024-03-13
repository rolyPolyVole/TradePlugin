package org.rolypolyvole.trading.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.rolypolyvole.trading.Trading;
import org.rolypolyvole.trading.trade.Trade;

public class TradeCommand implements CommandExecutor {
    private final Trading main;
    public TradeCommand(Trading main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && args.length == 1) {
            Player target = player.getServer().getPlayerExact(args[0]);

            if (target != null) {
                if (target.equals(sender)) {
                    player.sendMessage(Component.text("You can't trade with yourself!", NamedTextColor.RED));
                    return true;
                }

                if (player.getLocation().distance(target.getLocation()) > 8) {
                    player.sendMessage(Component.text("That player is too far away!", NamedTextColor.RED));
                    return true;
                }

                if (main.tradingPlayers.containsKey(player)) {
                    player.sendMessage(Component.text("You can only trade with one person at a time!", NamedTextColor.RED));
                    return true;
                }

                if (main.tradingPlayers.containsKey(target)) {
                    if (main.tradingPlayers.get(target).equals(player)) {
                        new Trade(main, player, target).startTrade();
                        return true;
                    }

                    player.sendMessage(Component.text("That player is already in a trade!", NamedTextColor.RED));
                    return true;
                }

                main.tradingPlayers.put(player, target);

                player.sendMessage(Component.text("You sent a trade request to " + target.getName(), NamedTextColor.GREEN));
                target.sendMessage(Component.text(player.getName() + " has sent you a trade request! ", NamedTextColor.GREEN)
                      .append(
                          Component
                              .text("Click here", NamedTextColor.AQUA)
                              .clickEvent(ClickEvent.runCommand("/trade " + player.getName()))
                      )
                );

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 2.0F);

                return true;
            } else {
                player.sendMessage(Component.text("That player does not exist, or is not online.", NamedTextColor.RED));
                return true;
            }
        }

        return false;
    }
}
