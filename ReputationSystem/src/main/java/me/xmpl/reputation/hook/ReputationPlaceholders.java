package me.xmpl.reputation.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.reputation.ReputationSystemExtension;
import org.bukkit.entity.Player;

public class ReputationPlaceholders implements ExtensionPlaceholder {
    private final ReputationSystemExtension extension;

    public ReputationPlaceholders(ReputationSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        int rep = extension.getScoreStorage().getReputation(player.getName());

        switch (params.toLowerCase()) {
            case "badge":
            case "formatted":
            case "tab":
            case "chat":
                return extension.getReputationService().formatBadge(rep);
            case "colored":
                return extension.getReputationService().formatColoredOnly(rep);
            case "amount":
            case "score":
            case "rep":
                return String.valueOf(rep);
            case "cooldown":
                return String.valueOf(extension.getCooldownStorage().getRemainingMinutes(player.getName()));
            default:
                return null;
        }
    }
}