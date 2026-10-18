package me.xmpl.killsystem.hook;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.NumberDateUtil;
import org.bukkit.entity.Player;

import java.util.List;

public class KillSystemPlaceholders implements ExtensionPlaceholder {
    private final KillSystemExtension extension;

    public KillSystemPlaceholders(KillSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String lower = params.toLowerCase();

        if (lower.equals("kills")) {
            return player != null ? String.valueOf(extension.getDatabaseStorage().getOrCreate(player.getName()).getKills()) : "0";
        }
        if (lower.equals("deaths")) {
            return player != null ? String.valueOf(extension.getDatabaseStorage().getOrCreate(player.getName()).getDeaths()) : "0";
        }

        if (lower.startsWith("top_name_")) {
            long rank = NumberDateUtil.parseStrictPositiveLong(lower.substring("top_name_".length()));
            if (rank <= 0) return "-";
            List<PlayerKillRecord> top = extension.getDatabaseStorage().getSortedTopList();
            return rank <= top.size() ? top.get((int) rank - 1).getPlayerName() : "Пусто";
        }

        if (lower.startsWith("top_kills_")) {
            long rank = NumberDateUtil.parseStrictPositiveLong(lower.substring("top_kills_".length()));
            if (rank <= 0) return "0";
            List<PlayerKillRecord> top = extension.getDatabaseStorage().getSortedTopList();
            return rank <= top.size() ? String.valueOf(top.get((int) rank - 1).getKills()) : "0";
        }

        return null;
    }
}