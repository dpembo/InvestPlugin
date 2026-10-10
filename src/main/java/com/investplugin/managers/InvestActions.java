package com.investplugin.managers;

import com.investplugin.InvestPlugin;
import com.investplugin.managers.InvestRules.DepositStatus;
import com.investplugin.managers.InvestRules.Rules;
import com.investplugin.managers.InvestRules.Withdrawal;
import com.investplugin.model.Account;
import com.investplugin.util.Money;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player-facing actions: runs the operation on the manager and sends the configured messages. */
public final class InvestActions {

    private final InvestPlugin plugin;
    private final InvestManager manager;

    public InvestActions(InvestPlugin plugin, InvestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** All placeholders for a player (money values are formatted; extra pairs override). */
    public Map<String, String> ph(UUID uuid, String... extra) {
        Account a = manager.view(uuid);
        Rules r = manager.rules();
        long now = System.currentTimeMillis();
        double market = manager.marketChangePercent(now);
        double gains = manager.gainsTotal(uuid, now);

        Map<String, String> m = new HashMap<>();
        m.put("capital", plugin.money(a.getCapital()));
        m.put("pot", plugin.money(a.getPot()));
        m.put("total", plugin.money(a.total()));
        m.put("withdrawable", plugin.money(InvestRules.withdrawable(a, r)));
        m.put("auto", plugin.text(a.isAutoInvest() ? "messages.state-on" : "messages.state-off", Map.of()));
        m.put("state", plugin.text(a.isFrozen() ? "messages.status-frozen" : "messages.status-active", Map.of()));
        m.put("fee", Money.plain(r.feePercent()));
        m.put("max", plugin.money(r.maxCapital()));
        m.put("room", plugin.money(InvestRules.maxDeposit(a, r)));
        m.put("freeze", plugin.money(r.freezeThreshold()));
        m.put("next", Money.duration(manager.millisUntilNextCycle(now)));
        m.put("window", String.valueOf(Math.max(1, plugin.getConfig().getInt("stats-window-days", 7))));
        m.put("market", plugin.colorFor(market) + Money.percent(market));
        m.put("gains", plugin.colorFor(gains) + plugin.signedMoney(gains));
        for (int i = 0; i + 1 < extra.length; i += 2) m.put(extra[i], extra[i + 1]);
        return m;
    }

    public void deposit(Player p, double amount) {
        UUID id = p.getUniqueId();
        boolean wasFrozen = manager.view(id).isFrozen();
        DepositStatus status = manager.deposit(p, amount);
        switch (status) {
            case OK -> {
                p.sendMessage(plugin.msg("deposit-success", ph(id, "amount", plugin.money(Money.round2(amount)))));
                if (!wasFrozen && manager.view(id).isFrozen()) p.sendMessage(plugin.msg("frozen-notice", ph(id)));
            }
            case FROZEN -> p.sendMessage(plugin.msg("deposit-frozen", ph(id)));
            case EXCEEDS_MAX -> p.sendMessage(plugin.msg("deposit-exceeds-max", ph(id)));
            case NO_FUNDS -> p.sendMessage(plugin.msg("not-enough-money", ph(id)));
            case ECONOMY_ERROR -> p.sendMessage(plugin.msg("economy-error", ph(id)));
            case INVALID -> p.sendMessage(plugin.msg("invalid-amount", ph(id)));
        }
    }

    /** Deposit as much as the player can afford and the capital cap allows. */
    public void depositMax(Player p) {
        UUID id = p.getUniqueId();
        Account a = manager.view(id);
        if (a.isFrozen()) {
            p.sendMessage(plugin.msg("deposit-frozen", ph(id)));
            return;
        }
        double room = InvestRules.maxDeposit(a, manager.rules());
        double balance = Money.floor2(plugin.getEconomy().getBalance(p));
        double amount = Math.min(room, balance);
        if (room <= 0) {
            p.sendMessage(plugin.msg("deposit-exceeds-max", ph(id)));
        } else if (amount <= 0) {
            p.sendMessage(plugin.msg("not-enough-money", ph(id)));
        } else {
            deposit(p, amount);
        }
    }

    public void withdraw(Player p, double amount) {
        UUID id = p.getUniqueId();
        boolean wasFrozen = manager.view(id).isFrozen();
        Withdrawal w = manager.withdraw(p, amount);
        switch (w.status()) {
            case OK -> {
                p.sendMessage(plugin.msg("withdraw-success", ph(id,
                        "amount", plugin.money(w.amount()),
                        "fee_amount", plugin.money(w.feeAmount()),
                        "received", plugin.money(w.received()))));
                if (wasFrozen && !manager.view(id).isFrozen()) p.sendMessage(plugin.msg("unfrozen-notice", ph(id)));
            }
            case NOT_ENOUGH -> p.sendMessage(plugin.msg("withdraw-too-much", ph(id)));
            case ECONOMY_ERROR -> p.sendMessage(plugin.msg("economy-error", ph(id)));
            case INVALID -> p.sendMessage(plugin.msg("invalid-amount", ph(id)));
        }
    }

    public void withdrawAll(Player p) {
        UUID id = p.getUniqueId();
        double total = manager.view(id).total();
        if (total <= 0) {
            p.sendMessage(plugin.msg("nothing-to-withdraw", ph(id)));
        } else {
            withdraw(p, total);
        }
    }

    public void toggleAuto(Player p) {
        boolean on = manager.toggleAuto(p.getUniqueId());
        p.sendMessage(plugin.msg(on ? "auto-on" : "auto-off", ph(p.getUniqueId())));
    }

    public void sendSummary(Player p) {
        for (String line : plugin.textList("messages.summary", ph(p.getUniqueId()))) p.sendMessage(line);
    }
}
