package com.investplugin.commands;

import com.investplugin.InvestPlugin;
import com.investplugin.gui.InvestMenu;
import com.investplugin.util.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InvestCommand implements CommandExecutor, TabCompleter {

    private static final String GUIDE_URL = "https://globeworks.uk/player-guides/investment.html";

    private final InvestPlugin plugin;

    public InvestCommand(InvestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        // Admin sub-commands (console allowed)
        if (sub.equals("reload") || sub.equals("runcycle")) {
            if (!sender.hasPermission("investplugin.admin")) {
                sender.sendMessage(plugin.msg("no-permission", Map.of()));
                return true;
            }
            if (sub.equals("reload")) {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.msg("reloaded", Map.of()));
            } else {
                Double forced = null;
                if (args.length > 1) {
                    try {
                        forced = Double.parseDouble(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.msg("invalid-percent", Map.of()));
                        return true;
                    }
                    if (!Double.isFinite(forced)) {
                        sender.sendMessage(plugin.msg("invalid-percent", Map.of()));
                        return true;
                    }
                }
                double pct = plugin.getInvestManager().forceCycle(forced);
                sender.sendMessage(plugin.msg("cycle-forced", Map.of("percent", Money.percent(pct))));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("investplugin.invest")) {
            player.sendMessage(plugin.msg("no-permission", Map.of()));
            return true;
        }

        switch (sub) {
            case "" -> player.openInventory(InvestMenu.build(player, plugin));
            case "help" -> player.sendMessage(
                    LegacyComponentSerializer.legacySection().deserialize(plugin.msg("help", Map.of()))
                            .append(Component.text("Globeworks | Investment Guide", NamedTextColor.BLUE)
                                    .decorate(TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.openUrl(GUIDE_URL))
                                    .hoverEvent(HoverEvent.showText(Component.text("Open the investment guide"))))
            );
            case "status", "info" -> plugin.getActions().sendSummary(player);
            case "auto" -> plugin.getActions().toggleAuto(player);
            case "deposit", "withdraw" -> {
                boolean deposit = sub.equals("deposit");
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("usage", Map.of()));
                    return true;
                }
                boolean all = args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("max");
                double amount = all ? 0 : Money.parse(args[1]);
                if (!all && !(amount > 0)) {
                    player.sendMessage(plugin.msg("invalid-amount", Map.of()));
                    return true;
                }
                if (deposit) {
                    if (all) plugin.getActions().depositMax(player);
                    else plugin.getActions().deposit(player, amount);
                } else {
                    if (all) plugin.getActions().withdrawAll(player);
                    else plugin.getActions().withdraw(player, amount);
                }
            }
            default -> player.sendMessage(plugin.msg("usage", Map.of()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("deposit", "withdraw", "auto", "status", "help"));
            if (sender.hasPermission("investplugin.admin")) out.addAll(List.of("reload", "runcycle"));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.startsWith(prefix));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("runcycle") && sender.hasPermission("investplugin.admin")) {
            out.addAll(List.of("-1", "0", "2"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw"))) {
            out.add("all");
        }
        return out;
    }
}
