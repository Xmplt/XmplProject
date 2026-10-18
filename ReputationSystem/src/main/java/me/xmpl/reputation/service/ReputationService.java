package me.xmpl.reputation.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.reputation.ReputationSystemExtension;
import me.xmpl.reputation.model.VoteType;
import me.xmpl.reputation.storage.ReputationScoreStorage.PlayerRepEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ReputationService {
    private final ReputationSystemExtension extension;

    public ReputationService(ReputationSystemExtension extension) {
        this.extension = extension;
    }

    public void handleVoteCommand(Player voter, String targetNameArg, VoteType newVoteType) {
        FileConfiguration mainCfg = extension.getConfig();
        FileConfiguration msgCfg = extension.getCustomConfig("commands/rep.yml").get();

        // 1. Проверка права (если включена в config.yml)
        if (mainCfg.getBoolean("permissions.require-vote-permission", false)) {
            String perm = mainCfg.getString("permissions.vote", "reputationsystem.vote");
            if (!voter.hasPermission(perm)) {
                ChatUtil.sendMessage(voter, msgCfg.getString("messages.no-permission"));
                return;
            }
        }

        // 2. Запрет голосовать за самого себя
        if (voter.getName().equalsIgnoreCase(targetNameArg)) {
            ChatUtil.sendMessage(voter, msgCfg.getString("messages.self-vote", "&cНельзя оценивать самого себя!"));
            return;
        }

        // 3. Поиск цели (онлайн или известный оффлайн игрок)
        String resolvedTargetName = resolveTargetName(targetNameArg);
        if (resolvedTargetName == null) {
            ChatUtil.sendMessage(voter, msgCfg.getString("messages.player-not-found", "&cИгрок не найден."));
            return;
        }

        // 4. Проверяем, голосовал ли уже этот игрок за эту цель такой же оценкой
        VoteType previousVote = extension.getVoteStorage().getPreviousVote(voter.getName(), resolvedTargetName);
        if (previousVote == newVoteType) {
            ChatUtil.sendMessage(voter, msgCfg.getString("messages.already-voted", "&cВы уже оценивали этого игрока!"));
            return;
        }

        // 5. Проверка общего 10-минутного кулдауна (и на лайк, и на дизлайк)
        boolean bypassCooldown = mainCfg.getBoolean("permissions.enable-cooldown-bypass", false)
                && voter.hasPermission(mainCfg.getString("permissions.bypass-cooldown", "reputationsystem.bypass.cooldown"));

        if (!bypassCooldown && extension.getCooldownStorage().isOnCooldown(voter.getName())) {
            int remMinutes = extension.getCooldownStorage().getRemainingMinutes(voter.getName());
            String cdMsg = msgCfg.getString("messages.cooldown", "&cПодождите еще %time% мин. перед следующим голосованием.");
            ChatUtil.sendMessage(voter, cdMsg.replace("%time%", String.valueOf(remMinutes)));
            return;
        }

        // 6. Вычисляем изменение репутации:
        // - Если раньше оценки не было: +1 (для LIKE) или -1 (для DISLIKE)
        // - Если раньше стоял LIKE, а теперь ставит DISLIKE: отнимаем старый лайк (-1) и ставим дизлайк (-1) = -2!
        // - Если раньше стоял DISLIKE, а теперь ставит LIKE: убираем дизлайк (+1) и ставим лайк (+1) = +2!
        boolean isChangingVote = (previousVote != null);
        int delta = isChangingVote ? (newVoteType.getPointValue() * 2) : newVoteType.getPointValue();

        // Сохраняем новую связь и запускаем общий 10-минутный кулдаун
        extension.getVoteStorage().setVote(voter.getName(), resolvedTargetName, newVoteType);
        if (!bypassCooldown) {
            int cooldownSec = mainCfg.getInt("settings.vote-cooldown-seconds", 600);
            extension.getCooldownStorage().applyCooldown(voter.getName(), cooldownSec);
        }

        // Готовим сообщения для голосующего и цели
        String voterMsg;
        String targetMsg;
        if (!isChangingVote) {
            if (newVoteType == VoteType.LIKE) {
                voterMsg = msgCfg.getString("messages.like-success", "&aВы лайкнули игрока %player%! (+1 к репутации)");
                targetMsg = msgCfg.getString("messages.got-like", "&aВам поставил лайк игрок %player%!");
            } else {
                voterMsg = msgCfg.getString("messages.dislike-success", "&cВы дизлайкнули игрока %player%! (-1 к репутации)");
                targetMsg = msgCfg.getString("messages.got-dislike", "&cВам поставил дизлайк игрок %player%!");
            }
        } else {
            if (newVoteType == VoteType.LIKE) {
                voterMsg = msgCfg.getString("messages.changed-to-like", "&aВы изменили свою оценку игроку %player% с дизлайка на лайк! (+2 к репутации)");
                targetMsg = msgCfg.getString("messages.got-changed-to-like", "&aИгрок %player% изменил свою оценку вам на лайк!");
            } else {
                voterMsg = msgCfg.getString("messages.changed-to-dislike", "&cВы изменили свою оценку игроку %player% с лайка на дизлайк! (-2 к репутации)");
                targetMsg = msgCfg.getString("messages.got-changed-to-dislike", "&cИгрок %player% изменил свою оценку вам на дизлайк!");
            }
        }

        voterMsg = voterMsg.replace("%player%", resolvedTargetName);
        targetMsg = targetMsg.replace("%player%", voter.getName());

        Player onlineTarget = Bukkit.getPlayerExact(resolvedTargetName);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            extension.getScoreStorage().addReputationDelta(resolvedTargetName, delta, null);
            ChatUtil.sendMessage(onlineTarget, targetMsg);
        } else {
            String offlineNotice = mainCfg.getBoolean("settings.notify-on-join-if-voted-offline", true) ? targetMsg : null;
            extension.getScoreStorage().addReputationDelta(resolvedTargetName, delta, offlineNotice);
        }

        ChatUtil.sendMessage(voter, voterMsg);
    }

    @SuppressWarnings("deprecation")
    public String resolveTargetName(String inputName) {
        Player online = Bukkit.getPlayerExact(inputName);
        if (online != null) {
            return online.getName();
        }

        if (!extension.getConfig().getBoolean("settings.allow-offline-target-voting", true)) {
            return null;
        }

        if (extension.getScoreStorage().isKnownPlayer(inputName)) {
            PlayerRepEntry entry = extension.getScoreStorage().getOrCreate(inputName);
            return entry.realName;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(inputName);
        if (offline != null && offline.hasPlayedBefore() && offline.getName() != null) {
            extension.getScoreStorage().ensurePlayerRegistered(offline.getName());
            return offline.getName();
        }
        return null;
    }

    /**
     * Форматирует плейсхолдер со скобочками: &7(&a&l+15&7) или &7(&c&l-5&7)
     */
    public String formatBadge(int rep) {
        FileConfiguration phCfg = extension.getCustomConfig("placeholders.yml").get();
        String raw;
        if (rep > 0) {
            raw = phCfg.getString("badge-format.positive", "&7(&a&l+%amount%&7)");
        } else if (rep < 0) {
            raw = phCfg.getString("badge-format.negative", "&7(&c&l-%abs_amount%&7)");
        } else {
            raw = phCfg.getString("badge-format.zero", "&7(&a&l+0&7)");
        }
        return ChatUtil.color(raw
                .replace("%amount%", String.valueOf(rep))
                .replace("%abs_amount%", String.valueOf(Math.abs(rep))));
    }

    public String formatColoredOnly(int rep) {
        FileConfiguration phCfg = extension.getCustomConfig("placeholders.yml").get();
        String raw;
        if (rep > 0) {
            raw = phCfg.getString("colored-only-format.positive", "&a&l+%amount%");
        } else if (rep < 0) {
            raw = phCfg.getString("colored-only-format.negative", "&c&l-%abs_amount%");
        } else {
            raw = phCfg.getString("colored-only-format.zero", "&a&l+0");
        }
        return ChatUtil.color(raw
                .replace("%amount%", String.valueOf(rep))
                .replace("%abs_amount%", String.valueOf(Math.abs(rep))));
    }
}