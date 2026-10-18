package me.xmpl.antirelog.service;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.TitleUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class CombatNotificationService {
    private final AntiRelogExtension extension;

    public CombatNotificationService(AntiRelogExtension extension) {
        this.extension = extension;
    }

    public void notifyEnterPvp(Player player, String opponentName, int duration) {
        // Сообщение в чат
        String chatMsg = extension.getCustomConfig("messages.yml").get().getString("pvp-entered-chat");
        ChatUtil.sendMessage(player, chatMsg, "%opponent%", opponentName, "%time%", duration);

        // Большое оповещение на экран (Title)
        FileConfiguration titleCfg = extension.getCustomConfig("titles.yml").get();
        if (titleCfg.getBoolean("enter-pvp.enabled", true)) {
            TitleUtil.sendTitle(
                    player,
                    titleCfg.getString("enter-pvp.title", "&4&lРЕЖИМ ПВП"),
                    titleCfg.getString("enter-pvp.subtitle", "&fВы в бою с &c%opponent%&f!"),
                    titleCfg.getInt("enter-pvp.fade-in", 5),
                    titleCfg.getInt("enter-pvp.stay", 40),
                    titleCfg.getInt("enter-pvp.fade-out", 10),
                    "%opponent%", opponentName,
                    "%time%", duration
            );
        }
    }

    public void notifyLeavePvp(Player player) {
        if (player == null || !player.isOnline()) return;

        String chatMsg = extension.getCustomConfig("messages.yml").get().getString("pvp-expired-chat");
        ChatUtil.sendMessage(player, chatMsg);

        FileConfiguration titleCfg = extension.getCustomConfig("titles.yml").get();
        if (titleCfg.getBoolean("leave-pvp.enabled", true)) {
            TitleUtil.sendTitle(
                    player,
                    titleCfg.getString("leave-pvp.title", "&a&lБОЙ ОКОНЧЕН"),
                    titleCfg.getString("leave-pvp.subtitle", "&fВы вышли из режима ПвП."),
                    titleCfg.getInt("leave-pvp.fade-in", 5),
                    titleCfg.getInt("leave-pvp.stay", 30),
                    titleCfg.getInt("leave-pvp.fade-out", 10)
            );
        }
    }
}