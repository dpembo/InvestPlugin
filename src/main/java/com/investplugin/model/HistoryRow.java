package com.investplugin.model;

import java.util.UUID;

/** What one daily cycle did to one player's total value. */
public record HistoryRow(UUID uuid, long time, double pct, double delta) {}
