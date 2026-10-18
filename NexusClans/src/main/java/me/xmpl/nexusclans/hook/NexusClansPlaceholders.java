package me.xmpl.nexusclans.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import org.bukkit.entity.Player;

public class NexusClansPlaceholders implements ExtensionPlaceholder {
    private final NexusClansExtension extension;

    public NexusClansPlaceholders(NexusClansExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());

        switch (params.toLowerCase()) {
            case "tag": {
                if (clan == null || !clan.isTagShown()) {
                    return ChatUtil.color(extension.getConfig().getString("placeholders.hidden-tag-value", ""));
                }
                String fmt = extension.getConfig().getString("placeholders.visible-tag-format", "{tag}");
                return ChatUtil.color(fmt.replace("{tag}", clan.getRawColoredTag()));
            }
            case "name_raw":
                return clan != null ? ChatUtil.color(clan.getRawColoredTag()) : "";
            case "points":
                return clan != null ? String.valueOf(clan.getTotalPoints()) : "0";
            case "rank":
                return clan != null ? String.valueOf(extension.getMainStorage().getClanRankPosition(clan.getClanKey())) : "-";
            case "role": {
                if (clan == null) return "";
                ClanMember m = clan.getMember(player.getName());
                return m != null ? m.getRole().getDisplay() : "";
            }
            default:
                return null;
        }
    }
}