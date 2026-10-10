package com.investplugin.model;

import com.investplugin.util.Money;

import java.util.UUID;

/** A player's investment account. Only touched from the main server thread (async code gets copies). */
public final class Account {

    private final UUID uuid;
    private double capital;
    private double pot;
    private boolean autoInvest;
    private boolean frozen;

    public Account(UUID uuid, double capital, double pot, boolean autoInvest, boolean frozen) {
        this.uuid = uuid;
        this.capital = capital;
        this.pot = pot;
        this.autoInvest = autoInvest;
        this.frozen = frozen;
    }

    public UUID getUuid() { return uuid; }
    public double getCapital() { return capital; }
    public double getPot() { return pot; }
    public boolean isAutoInvest() { return autoInvest; }
    public boolean isFrozen() { return frozen; }

    public void setCapital(double capital) { this.capital = capital; }
    public void setPot(double pot) { this.pot = pot; }
    public void setAutoInvest(boolean autoInvest) { this.autoInvest = autoInvest; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }

    /** Total value = capital + investment pot. */
    public double total() { return Money.round2(capital + pot); }

    public Account copy() { return new Account(uuid, capital, pot, autoInvest, frozen); }
}
