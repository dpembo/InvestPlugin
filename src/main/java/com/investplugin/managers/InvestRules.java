package com.investplugin.managers;

import com.investplugin.model.Account;
import com.investplugin.util.Money;

/** Pure investment rules (no Bukkit, no I/O) so they are easy to reason about and test. */
public final class InvestRules {

    private InvestRules() {}

    public record Rules(double maxCapital, double freezeThreshold, double feePercent) {}

    public enum DepositStatus { OK, INVALID, FROZEN, EXCEEDS_MAX, NO_FUNDS, ECONOMY_ERROR }

    public enum WithdrawStatus { OK, INVALID, NOT_ENOUGH, ECONOMY_ERROR }

    public record Withdrawal(WithdrawStatus status, double amount, double feeAmount, double received) {
        public static Withdrawal fail(WithdrawStatus status) {
            return new Withdrawal(status, 0, 0, 0);
        }
    }

    /**
     * Freeze when total value reaches the threshold (0 = freezing disabled).
     * Unfreeze when capital is back at/below the max capital AND the total is below the threshold.
     */
    public static void refreshFreeze(Account a, Rules r) {
        if (r.freezeThreshold() <= 0) {
            a.setFrozen(false);
            return;
        }
        double total = a.total();
        if (!a.isFrozen()) {
            if (total >= r.freezeThreshold()) a.setFrozen(true);
        } else if (a.getCapital() <= r.maxCapital() && total < r.freezeThreshold()) {
            a.setFrozen(false);
        }
    }

    /** Applies the daily % to the account. Returns the change in total value. */
    public static double applyDaily(Account a, double pct, Rules r) {
        if (a.isFrozen() || a.getCapital() <= 0) return 0;
        double before = a.total();

        if (pct > 0) {
            double gain = Money.round2(a.getCapital() * pct / 100.0);
            if (a.isAutoInvest()) {
                a.setCapital(Money.round2(a.getCapital() + gain));
            } else {
                a.setPot(Money.round2(a.getPot() + gain));
            }
        } else if (pct < 0) {
            double loss = Money.round2(a.getCapital() * (-pct) / 100.0);
            double fromPot = Math.min(a.getPot(), loss);
            a.setPot(Money.round2(a.getPot() - fromPot));
            a.setCapital(Math.max(0, Money.round2(a.getCapital() - (loss - fromPot))));
        }

        refreshFreeze(a, r);
        return Money.round2(a.total() - before);
    }

    /** Validates and applies a deposit to the capital. Mutates the account only when OK. */
    public static DepositStatus deposit(Account a, double amount, Rules r) {
        amount = Money.round2(amount);
        if (!(amount > 0)) return DepositStatus.INVALID;
        if (a.isFrozen()) return DepositStatus.FROZEN;
        if (Money.round2(a.getCapital() + amount) > r.maxCapital()) return DepositStatus.EXCEEDS_MAX;
        a.setCapital(Money.round2(a.getCapital() + amount));
        refreshFreeze(a, r);
        return DepositStatus.OK;
    }

    /** Validates and applies a withdrawal (pot first, then capital). Mutates the account only when OK. */
    public static Withdrawal withdraw(Account a, double amount, Rules r) {
        amount = Money.round2(amount);
        if (!(amount > 0)) return Withdrawal.fail(WithdrawStatus.INVALID);
        if (amount > a.total()) return Withdrawal.fail(WithdrawStatus.NOT_ENOUGH);

        double fromPot = Math.min(a.getPot(), amount);
        a.setPot(Money.round2(a.getPot() - fromPot));
        a.setCapital(Math.max(0, Money.round2(a.getCapital() - (amount - fromPot))));

        double fee = Money.round2(amount * r.feePercent() / 100.0);
        double received = Money.round2(amount - fee);
        refreshFreeze(a, r);
        return new Withdrawal(WithdrawStatus.OK, amount, fee, received);
    }

    /** Moves the whole investment pot into capital (used when auto-invest is switched on). Returns the amount moved. */
    public static double foldPotIntoCapital(Account a, Rules r) {
        double moved = a.getPot();
        if (moved <= 0) return 0;
        a.setCapital(Money.round2(a.getCapital() + moved));
        a.setPot(0);
        refreshFreeze(a, r);
        return moved;
    }

    /** How much more capital can be deposited right now. */
    public static double maxDeposit(Account a, Rules r) {
        if (a.isFrozen()) return 0;
        return Math.max(0, Money.round2(r.maxCapital() - a.getCapital()));
    }

    /** What the player would actually receive if they withdrew everything. */
    public static double withdrawable(Account a, Rules r) {
        return Money.round2(a.total() * (1 - r.feePercent() / 100.0));
    }
}
