package com.investplugin.listeners;

import com.investplugin.InvestPlugin;
import com.investplugin.util.Money;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Captures the next chat message of a player who clicked Deposit/Withdraw in the menu. */
public class ChatListener implements Listener {

    public enum Type { DEPOSIT, WITHDRAW }

    private record Pending(Type type, long expiresAt) {}

    private static final long TIMEOUT_MILLIS = 60_000L;

    private final InvestPlugin plugin;
    private final Map<UUID, Pending> awaiting = new ConcurrentHashMap<>();

    public ChatListener(InvestPlugin plugin) {
        this.plugin = plugin;
    }

    public void await(UUID uuid, Type type) {
        awaiting.put(uuid, new Pending(type, System.currentTimeMillis() + TIMEOUT_MILLIS));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Pending pending = awaiting.get(id);
        if (pending == null) return;

        event.setCancelled(true);

        if (System.currentTimeMillis() > pending.expiresAt()) {
            awaiting.remove(id);
            return;
        }

        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            awaiting.remove(id);
            player.sendMessage(plugin.msg("cancelled", Map.of()));
            return;
        }

        boolean all = message.equalsIgnoreCase("all") || message.equalsIgnoreCase("max");
        double amount = all ? 0 : Money.parse(message);
        if (!all && !(amount > 0)) {
            // keep waiting so the player can just retype it
            player.sendMessage(plugin.msg("invalid-amount", Map.of()));
            return;
        }

        awaiting.remove(id);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (pending.type() == Type.DEPOSIT) {
                if (all) plugin.getActions().depositMax(player);
                else plugin.getActions().deposit(player, amount);
            } else {
                if (all) plugin.getActions().withdrawAll(player);
                else plugin.getActions().withdraw(player, amount);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }
}
