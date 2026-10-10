package com.investplugin.listeners;

import com.investplugin.InvestPlugin;
import com.investplugin.gui.InvestMenu;
import com.investplugin.managers.InvestActions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class MenuListener implements Listener {

    private final InvestPlugin plugin;
    private final ChatListener chat;

    public MenuListener(InvestPlugin plugin, ChatListener chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof InvestMenu.Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvestMenu.Holder)) return;
        event.setCancelled(true); // nothing can be taken out of, or moved into, the menu

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= InvestMenu.SIZE) return;

        InvestActions actions = plugin.getActions();
        boolean alt = event.getClick().isRightClick();

        switch (slot) {
            case InvestMenu.SLOT_DEPOSIT -> {
                if (alt) {
                    actions.depositMax(player);
                    InvestMenu.refresh(event.getInventory(), player, plugin);
                } else {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("enter-deposit", actions.ph(player.getUniqueId())));
                    chat.await(player.getUniqueId(), ChatListener.Type.DEPOSIT);
                }
            }
            case InvestMenu.SLOT_WITHDRAW -> {
                if (alt) {
                    actions.withdrawAll(player);
                    InvestMenu.refresh(event.getInventory(), player, plugin);
                } else {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("enter-withdraw", actions.ph(player.getUniqueId())));
                    chat.await(player.getUniqueId(), ChatListener.Type.WITHDRAW);
                }
            }
            case InvestMenu.SLOT_AUTO -> {
                actions.toggleAuto(player);
                InvestMenu.refresh(event.getInventory(), player, plugin);
            }
            default -> { }
        }
    }
}
