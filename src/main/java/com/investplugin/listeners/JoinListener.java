package com.investplugin.listeners;

import com.investplugin.InvestPlugin;
import com.investplugin.model.Account;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sends the investment summary when a player with an investment joins. */
public class JoinListener implements Listener {

    private final InvestPlugin plugin;

    public JoinListener(InvestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("notify-on-join", true)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("investplugin.invest")) return;

        Account account = plugin.getInvestManager().getAccount(player.getUniqueId());
        if (account == null || (account.getCapital() <= 0 && account.getPot() <= 0)) return;

        long delay = Math.max(0L, plugin.getConfig().getLong("join-notify-delay-ticks", 40L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.getActions().sendSummary(player);
        }, delay);
    }
}
