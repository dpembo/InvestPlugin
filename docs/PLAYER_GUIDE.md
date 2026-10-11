# Globeworks Investment System: Player Guide

Put some of your in-game money to work. Your investment grows (or shrinks) once a day, and you can add to it, check on it, or take money out whenever you like.

> ## ⚠️ Please read first
> **Investments can go down as well as up.** Each day's result can be a gain *or* a loss, and you can lose part of what you put in.
>
> This system is built for **long-term wealth building, not quick profit.** Daily moves are small and there is a fee to withdraw, so it rewards patience. Only invest money you won't need soon.

---

## The basics

You'll see three amounts in the menu:

| Amount | What it means |
|---|---|
| **Capital** | The money you've invested. Daily growth is worked out from this. |
| **Investment Pot** | Profit you've earned, held separately from your capital. |
| **Total Value** | Capital + Investment Pot: everything you currently have in the system. |

Once a day the investment market moves by a small amount, up or down, and your investment follows. When it happens you may see a message in chat telling you how the market did.

---

## Opening the menu

Type **`/invest`** to open your investment menu. From here you can see everything and do everything with a click.

| Item in the menu | What it shows / does |
|---|---|
| **Capital** | The money you've invested |
| **Investment Pot** | Profit you've built up (greyed out while Auto-Invest is on) |
| **Total Value** | Capital + Pot |
| **Withdrawable** | What you would actually receive if you withdrew everything, after the fee |
| **Market – Last 7 Days** | How the market moved each day, and overall. Green is up, red is down |
| **Your Gains/Losses – Last 7 Days** | How much your own investment gained or lost each day, and in total. Green is a gain, red is a loss |
| **Status clock / ice block** | Shows your investment is active (and when the next update is due) or frozen (see *Costs and limits*) |
| **Deposit** | Add money to your investment |
| **Auto-Invest** | Switch automatic reinvesting on or off |
| **Withdraw** | Take money out |

---

## How to invest money

**With the menu:**
1. Type `/invest`.
2. **Left-click Deposit.**
3. Type the amount in chat and press Enter. Type `cancel` to back out.

Shortcut: **right-click Deposit** to invest as much as you can afford (up to the limit).

**With commands:**

```
/invest deposit 5000
/invest deposit 10k
/invest deposit 1.5m
/invest deposit all
```

`k` means thousand, `m` means million, and `all` invests as much as you can afford up to the limit.

Money you deposit becomes your **Capital** straight away. You can add more at any time (unless your investment is frozen).

---

## Auto-Invest: let your gains compound

Auto-Invest decides what happens to your daily gains.

| | Auto-Invest **OFF** | Auto-Invest **ON** |
|---|---|---|
| Daily gains go to… | your **Investment Pot** | your **Capital** |
| Your capital… | stays the same | grows over time, so tomorrow's gain is calculated on a bigger amount |
| Best for | keeping profits separate | growing your wealth steadily over the long term |

**Turning Auto-Invest ON also moves everything in your Investment Pot into your Capital**, so your whole investment starts working together. That is why the pot icon is greyed out while it's on.

Good to know before you switch it on:
- With no pot, any daily loss comes straight off your **Capital**. (With Auto-Invest off, losses come out of your pot first.)
- Turning it **off** again does **not** move money back into the pot. New gains simply go to the pot from then on.
- You can't switch it on while your investment is frozen.

**Toggle it:** click the **Auto-Invest** item in the menu, or type `/invest auto`.

---

## How to track your investment

- **The menu (`/invest`)** shows your capital, pot, total value, and a 7-day history of the market and of your own gains and losses.
- **`/invest status`** prints a quick summary in chat.
- **When you log in**, you'll get a summary of your investment automatically.
- **Daily broadcast:** the server may announce each day's market move in chat.

---

## How to withdraw

**With the menu:**
1. Type `/invest`.
2. **Left-click Withdraw.**
3. Type the amount in chat. Type `all` for everything, or `cancel` to back out.

Shortcut: **right-click Withdraw** to take everything out.

**With commands:**

```
/invest withdraw 2000
/invest withdraw 25k
/invest withdraw all
```

Withdrawals come out of your **Investment Pot first**, then your **Capital**. The money is paid into your normal balance straight away.

---

## Costs and limits

These are the standard settings. Your server may have adjusted them.

| | Default |
|---|---|
| **Withdrawal fee** | **5%** of the amount you withdraw |
| **Maximum capital** | **1,000,000**. You can't deposit past this |
| **Freeze point** | If your **Total Value** reaches **2,000,000**, your investment is **frozen** |

**Withdrawal fee example:** withdraw 1,000 and you receive 950. The **Withdrawable** item in the menu always shows what you'd get after the fee.

**If your investment is frozen:**
- It stops growing, and you can't deposit more.
- You can still withdraw whenever you want.
- It unfreezes once you've withdrawn enough to bring your capital back to the maximum and your total value back under the freeze point. The menu tells you what's needed.

---

## Quick reference

| Command | What it does |
|---|---|
| `/invest` | Open the investment menu |
| `/invest deposit <amount>` | Invest money (`10k`, `1.5m`, or `all`) |
| `/invest withdraw <amount>` | Take money out (5% fee; `all` works too) |
| `/invest auto` | Turn Auto-Invest on or off |
| `/invest status` | Show a summary of your investment |

## Tips

- Think in weeks and months, not days. One bad day is normal and doesn't tell you much.
- Because of the withdrawal fee, investing money you may need back tomorrow isn't a good idea.
- Auto-Invest tends to suit people who plan to leave their investment alone for a while.
- Not sure about a number? Check **Withdrawable** in the menu before you take money out.