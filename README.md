# InvestPlugin 2.0 — Paper 1.21

A capital + investment-pot investing plugin (Globeworks Investment System) with a chest GUI,
a daily market move and SQLite or MariaDB storage.

## How it works

Each player has **Capital** (their principal) and an **Investment Pot** (profit / loss buffer).
**Total value = Capital + Pot.**

Every `daily-interval-hours` (default 24) one global % is drawn at random between
`daily-min-percent` (-1.0) and `daily-max-percent` (+2.0) and applied to every player's **capital**:

| Daily result | Auto-invest ON | Auto-invest OFF |
|---|---|---|
| Gain | added to capital (compounds) | added to the pot (capital fixed) |
| Loss | pot first, then capital | pot first, then capital |

* **Withdrawals** take `withdraw-fee-percent` (5%) of the amount withdrawn, from the pot first, then capital.
* **Deposits** go into capital, up to `max-capital` (1,000,000).
* **Freeze:** at total value ≥ `freeze-threshold` (2,000,000) the account is frozen — no growth, no deposits,
  withdrawals still allowed. It unfreezes when capital ≤ `max-capital` **and** total < `freeze-threshold`.
* Accounts with capital ≤ 0 and frozen accounts are skipped by the daily cycle. Capital, pot and total never go below 0.
* **Turning auto-invest ON moves the whole pot into capital** (the pot icon is greyed out while it's on). From then on losses come straight off capital, and turning it off later does not move money back. Not allowed while frozen.
* Players with an investment get a summary on join (`notify-on-join`); the daily % can be broadcast (`broadcast-daily-change`).

## Commands

| Command | Description |
|---|---|
| `/invest` | Open the GUI (capital, pot, total, withdrawable, 7-day market % and your gains/losses, auto-invest, deposit, withdraw) |
| `/invest deposit <amount\|all>` | Deposit (amounts accept `10k`, `1.5m`, `1b`) |
| `/invest withdraw <amount\|all>` | Withdraw (fee applies) |
| `/invest auto` | Toggle auto-invest |
| `/invest status` | Print your summary |
| `/invest reload` | Reload config.yml (admin; storage changes need a restart) |
| `/invest runcycle [percent]` | Run the daily cycle now (admin; restarts the timer). Optional percent forces that exact move, e.g. `/invest runcycle -1.5` |

Permissions: `investplugin.invest` (default: everyone), `investplugin.admin` (default: op).

## Storage

`storage.type` in `config.yml` is `sqlite` (default, `plugins/InvestPlugin/invest.db`) or `mariadb`.
The JDBC drivers are downloaded by the server on first start (declared as `libraries` in `plugin.yml`), so the
server needs access to Maven Central once. Everything is saved asynchronously, so the main thread never waits on the database.
Run **one** server per database: balances are cached in memory.

### Migrating from 1.x
If a `data.yml` from version 1.x exists it is imported on first start and renamed to `data.yml.migrated`.
`invested` becomes **capital**, uncollected `claimable` earnings become the **pot**; auto-invest follows `default-auto-invest`.
Old `invest-rate`, `max-invest` and `payout-interval` options no longer exist. Delete the old `config.yml` once
to generate the new, documented one.

## Configuration
Rates, fee, limits, schedule, join/broadcast toggles, all messages and all GUI items (material, name, lore) are in `config.yml`.

## Building
Java 21 and Maven: `./build.sh` → `target/InvestPlugin-2.0.0.jar`. Requires Vault and an economy plugin.
