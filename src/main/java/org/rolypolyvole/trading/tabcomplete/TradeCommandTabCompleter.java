package org.rolypolyvole.trading.tabcomplete;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TradeCommandTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();

            for (Player player : sender.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase()) && !player.equals(sender)) {
                    playerNames.add(player.getName());
                }
            }

            return playerNames;
        }

        return null;
    }
}
