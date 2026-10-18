package me.xmpl.ess.hook;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.model.PunishmentRecord;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class XmplEssPlaceholders implements ExtensionPlaceholder {
    private final XmplEssExtension extension;

    public XmplEssPlaceholders(XmplEssExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        FileConfiguration cfg = extension.getConfig();

        switch (params.toLowerCase()) {
            case "ban_status": {
                PunishmentRecord ban = extension.getBanStorage().getBan(player.getName());
                if (ban == null || ban.isExpired()) {
                    return ChatUtil.color(cfg.getString("placeholders.ban.no", "&a&lНет"));
                }
                String base = ban.isPermanent()
                        ? cfg.getString("placeholders.ban.yes-perm", "&c&lНавсегда")
                        : cfg.getString("placeholders.ban.yes-temp", "&c&lДа");
                if (ban.isCustomReason()) {
                    base += cfg.getString("placeholders.ban.custom-reason-suffix", " &7({reason})")
                            .replace("{reason}", ban.getReason());
                }
                return ChatUtil.color(base);
            }
            case "mute_status": {
                PunishmentRecord mute = extension.getMuteStorage().getActiveMute(player.getName());
                if (mute == null) {
                    return ChatUtil.color(cfg.getString("placeholders.mute.no", "&a&lНет"));
                }
                String base = mute.isPermanent()
                        ? cfg.getString("placeholders.mute.yes-perm", "&c&lНавсегда")
                        : cfg.getString("placeholders.mute.yes-temp", "&c&lДа");
                if (mute.isCustomReason()) {
                    base += cfg.getString("placeholders.mute.custom-reason-suffix", " &7({reason})")
                            .replace("{reason}", mute.getReason());
                }
                return ChatUtil.color(base);
            }
            case "cmd_status": {
                boolean taken = extension.getMuteStorage().isMuteRightTaken(player.getName());
                return ChatUtil.color(taken
                        ? cfg.getString("placeholders.cmd.no", "&c&lНет")
                        : cfg.getString("placeholders.cmd.yes", "&a&lДа"));
            }
            default:
                return null;
        }
    }
}