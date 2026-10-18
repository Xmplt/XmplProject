package me.xmpl.nexusclans.service;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.nexusclans.NexusClansExtension;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Set;

public class ClanModuleService {
    private final NexusClansExtension extension;

    public ClanModuleService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public boolean isModuleEnabled(String moduleKey) {
        return extension.getCustomConfig("modules.yml").get().getBoolean("modules." + moduleKey.toLowerCase(), true);
    }

    public boolean setModuleEnabled(String moduleKey, boolean enabled) {
        CustomConfig cc = extension.getCustomConfig("modules.yml");
        String path = "modules." + moduleKey.toLowerCase();
        if (!cc.get().contains(path)) {
            return false;
        }
        cc.get().set(path, enabled);
        cc.save();
        return true;
    }

    public Set<String> getAvailableModules() {
        ConfigurationSection sec = extension.getCustomConfig("modules.yml").get().getConfigurationSection("modules");
        return sec != null ? sec.getKeys(false) : Collections.emptySet();
    }

    public boolean isMoneyPointsEnabled() { return isModuleEnabled("money-points-system"); }
    public boolean isSmartPointsEnabled() { return isModuleEnabled("smart-points-system"); }
    public boolean isClanWarsEnabled() { return isModuleEnabled("clan-wars-arena"); }
    public boolean isGlowingEnabled() { return isModuleEnabled("glowing-packets"); }
    public boolean isHeadRidingEnabled() { return isModuleEnabled("head-riding"); }
    public boolean isVirtualChestEnabled() { return isModuleEnabled("virtual-clan-chest"); }
    public boolean isAlliancesEnabled() { return isModuleEnabled("alliances"); }
    public boolean isTopSignsEnabled() { return isModuleEnabled("top-signs-and-hopper"); }
    public boolean isTopScoreboardEnabled() { return isModuleEnabled("top-scoreboard-regions"); }
}