package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RestrictionRollerService {
    private final AirDropExtension extension;

    public RestrictionRollerService(AirDropExtension extension) {
        this.extension = extension;
    }

    public void rollRestrictionsForWorld(PersistedAirDropState state, String worldName) {
        state.restrictElytra = false;
        state.restrictPearl = false;
        state.restrictChorus = false;

        FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();
        double chance3 = rCfg.getDouble("chances.three-restrictions", 15.0);
        double chance2 = rCfg.getDouble("chances.two-restrictions", 35.0);
        double chance1 = rCfg.getDouble("chances.one-restriction", 45.0);

        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        int targetCount;
        if (roll < chance3) {
            targetCount = 3;
        } else if (roll < (chance3 + chance2)) {
            targetCount = 2;
        } else if (roll < (chance3 + chance2 + chance1)) {
            targetCount = 1;
        } else {
            targetCount = 0; // 5% шанс на 0 запретов!
        }

        if (targetCount == 0) return;

        FileConfiguration wCfg = extension.getCustomConfig("worlds.yml").get();
        String basePath = "worlds." + worldName + ".allowed-restrictions.";

        List<String> availablePool = new ArrayList<>();
        if (wCfg.getBoolean(basePath + "elytra", true)) availablePool.add("ELYTRA");
        if (wCfg.getBoolean(basePath + "pearl", true)) availablePool.add("PEARL");
        if (wCfg.getBoolean(basePath + "chorus", true)) availablePool.add("CHORUS");

        Collections.shuffle(availablePool);
        int applied = 0;
        for (String type : availablePool) {
            if (applied >= targetCount) break;
            switch (type) {
                case "ELYTRA": state.restrictElytra = true; break;
                case "PEARL": state.restrictPearl = true; break;
                case "CHORUS": state.restrictChorus = true; break;
            }
            applied++;
        }
    }

    public String formatRestrictionTag(boolean isRestricted) {
        FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();
        return ChatUtil.color(isRestricted
                ? rCfg.getString("status-tags.denied", "&cЗапрещено")
                : rCfg.getString("status-tags.allowed", "&aРазрешено"));
    }
}