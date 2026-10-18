package me.xmpl.moneydrop.service;

import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ActionBarUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.moneydrop.MoneyDropExtension;
import me.xmpl.moneydrop.model.MobRewardRule;
import me.xmpl.moneydrop.storage.MoneyDropStatsStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public class MobRewardService {
    private final MoneyDropExtension extension;
    private final SpawnerTagService spawnerTagService;
    private final MoneyDropStatsStorage statsStorage;

    public MobRewardService(MoneyDropExtension extension, SpawnerTagService spawnerTagService, MoneyDropStatsStorage statsStorage) {
        this.extension = extension;
        this.spawnerTagService = spawnerTagService;
        this.statsStorage = statsStorage;
    }

    public void handleMobKill(Player killer, LivingEntity entity) {
        if (!extension.getConfig().getBoolean("settings.enable-mob-rewards", true)) return;
        if (isWorldDisabled(killer.getWorld().getName())) return;

        // 1. Проверяем, не появился ли моб из спавнера
        if (spawnerTagService.isSpawnerMob(entity)) {
            return;
        }

        EntityType type = entity.getType();
        // 2. Проверяем черный список сущностей (жители, стойки для брони, рамки и т.д.)
        if (isBlacklistedEntity(type)) {
            return;
        }

        // 3. Получаем правило награды для этого типа моба
        MobRewardRule rule = getRuleFor(type);
        if (rule == null || !rule.isEnabled() || rule.getMax() <= 0) {
            return;
        }

        double rawAmount = RandomUtil.nextDouble(rule.getMin(), rule.getMax());
        rawAmount *= getPlayerMultiplier(killer);

        double finalAmount = extension.getConfig().getBoolean("settings.round-to-integer", true)
                ? Math.round(rawAmount)
                : Math.round(rawAmount * 100.0) / 100.0;

        if (finalAmount <= 0) return;

        // 4. Выдаем деньги через Vault и записываем в статистику
        VaultHook.depositMoney(killer, finalAmount);
        statsStorage.addMobEarned(killer.getName(), (long) finalAmount);

        String formattedAmount = formatAmount(finalAmount);
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();

        String chatMsg = msgCfg.getString("mob-kill-chat", "&a&lВы убили {mob} и получили {amount}$!");
        ChatUtil.sendMessage(killer, chatMsg, "{mob}", rule.getDisplayName(), "{amount}", formattedAmount);

        if (msgCfg.getBoolean("mob-kill-actionbar-enabled", false)) {
            String abMsg = ChatUtil.applyReplacements(msgCfg.getString("mob-kill-actionbar"),
                    "{mob}", rule.getDisplayName(), "{amount}", formattedAmount);
            ActionBarUtil.sendActionBar(killer, abMsg);
        }
    }

    private boolean isBlacklistedEntity(EntityType type) {
        List<String> blacklist = extension.getCustomConfig("blacklisted-entities.yml").get().getStringList("blacklisted-entities");
        for (String entry : blacklist) {
            if (entry.equalsIgnoreCase(type.name())) {
                return true;
            }
        }
        return false;
    }

    private MobRewardRule getRuleFor(EntityType type) {
        FileConfiguration rewardsCfg = extension.getCustomConfig("mobs-rewards.yml").get();
        String path = "mobs." + type.name();
        if (rewardsCfg.isConfigurationSection(path)) {
            return new MobRewardRule(
                    rewardsCfg.getBoolean(path + ".enabled", true),
                    rewardsCfg.getDouble(path + ".min", 10.0),
                    rewardsCfg.getDouble(path + ".max", 25.0),
                    rewardsCfg.getString(path + ".display-name", type.name())
            );
        }

        if (rewardsCfg.getBoolean("default-mob.enabled", false)) {
            return new MobRewardRule(
                    true,
                    rewardsCfg.getDouble("default-mob.min", 5.0),
                    rewardsCfg.getDouble("default-mob.max", 15.0),
                    rewardsCfg.getString("default-mob.display-name", "Существо")
            );
        }
        return null;
    }

    private double getPlayerMultiplier(Player player) {
        double maxMult = 1.0;
        ConfigurationSection sec = extension.getConfig().getConfigurationSection("permissions.multipliers");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String perm = sec.getString(key + ".permission");
                double m = sec.getDouble(key + ".multiplier", 1.0);
                if (perm != null && player.hasPermission(perm) && m > maxMult) {
                    maxMult = m;
                }
            }
        }
        return maxMult;
    }

    private boolean isWorldDisabled(String worldName) {
        List<String> list = extension.getConfig().getStringList("settings.disabled-worlds");
        for (String w : list) {
            if (w.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    public String formatAmount(double val) {
        if (extension.getConfig().getBoolean("settings.round-to-integer", true)) {
            return NumberDateUtil.formatInteger(Math.round(val));
        }
        return String.format("%.2f", val);
    }
}