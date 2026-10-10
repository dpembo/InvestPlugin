package com.investplugin.migration;

import com.investplugin.model.Account;
import com.investplugin.util.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads the old 1.x data.yml (players.&lt;uuid&gt;.invested / claimable / auto-collect).
 * invested -&gt; capital, claimable (uncollected earnings) -&gt; investment pot.
 * The old auto-collect flag has no equivalent, so every migrated account gets the configured default auto-invest.
 */
public final class YamlMigrator {

    private YamlMigrator() {}

    public static List<Account> read(File file, Set<UUID> alreadyPresent, boolean defaultAutoInvest, Logger log) {
        List<Account> out = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return out;

        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                log.warning("Migration: skipping invalid UUID '" + key + "'");
                continue;
            }
            if (alreadyPresent.contains(id)) continue;

            double invested = Math.max(0, players.getDouble(key + ".invested", 0));
            double claimable = Math.max(0, players.getDouble(key + ".claimable", 0));
            if (invested <= 0 && claimable <= 0) continue;

            out.add(new Account(id, Money.round2(invested), Money.round2(claimable), defaultAutoInvest, false));
        }
        return out;
    }
}
