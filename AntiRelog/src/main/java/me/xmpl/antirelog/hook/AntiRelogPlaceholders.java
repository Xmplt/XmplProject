package me.xmpl.antirelog.hook;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import me.xmpl.antirelog.service.CombatManager;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class AntiRelogPlaceholders implements ExtensionPlaceholder {
    private final AntiRelogExtension extension;
    private final CombatManager combatManager;

    public AntiRelogPlaceholders(AntiRelogExtension extension, CombatManager combatManager) {
        this.extension = extension;
        this.combatManager = combatManager;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        boolean inPvp = combatManager.isInPvp(player);
        CombatSession session = combatManager.getSession(player);

        switch (params.toLowerCase()) {
            case "in_pvp":
                return ChatUtil.color(inPvp
                        ? msgCfg.getString("placeholders.in-pvp-true", "&cВ бою")
                        : msgCfg.getString("placeholders.in-pvp-false", "&aБезопасно"));
            case "in_pvp_raw":
                return String.valueOf(inPvp);
            case "time_left":
                return String.valueOf(session != null ? session.getRemainingSeconds() : 0);
            case "opponent":
                return session != null ? session.getOpponentName() : msgCfg.getString("placeholders.no-opponent", "Нет");
            default:
                return null;
        }
    }
}