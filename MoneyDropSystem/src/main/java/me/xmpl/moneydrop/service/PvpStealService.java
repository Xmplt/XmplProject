package me.xmpl.moneydrop.service;

import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.moneydrop.MoneyDropExtension;
import me.xmpl.moneydrop.storage.MoneyDropStatsStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class PvpStealService {
    private final MoneyDropExtension extension;
    private final MoneyDropStatsStorage statsStorage;

    public PvpStealService(MoneyDropExtension extension, MoneyDropStatsStorage statsStorage) {
        this.extension = extension;
        this.statsStorage = statsStorage;
    }

    public void handlePlayerKill(Player killer, Player victim) {
        if (!extension.getConfig().getBoolean("settings.enable-pvp-steal", true)) return;
        if (killer.equals(victim)) return;

        FileConfiguration pvpCfg = extension.getCustomConfig("pvp-steal.yml").get();
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();

        // 1. Защита от абуза с одного IP
        if (pvpCfg.getBoolean("pvp.prevent-same-ip-abuse", true)) {
            if (killer.getAddress() != null && victim.getAddress() != null
                    && killer.getAddress().getAddress().equals(victim.getAddress().getAddress())) {
                return;
            }
        }

        // 2. Проверка права сохранения денег (если включено)
        String keepPerm = pvpCfg.getString("pvp.keep-money-permission", "moneydrop.pvp.keep");
        if (keepPerm != null && !keepPerm.isEmpty() && !victim.isOp() && victim.hasPermission(keepPerm)) {
            return;
        }

        double victimBalance = VaultHook.getBalance(victim);
        double minBalance = pvpCfg.getDouble("pvp.min-victim-balance", 20.0);

        if (victimBalance < minBalance) {
            ChatUtil.sendMessage(killer, msgCfg.getString("pvp-killer-victim-poor"), "{victim}", victim.getName());
            return;
        }

        // 3. Считаем 5% от счета жертвы и ограничиваем потолком в 20 000!
        double percent = pvpCfg.getDouble("pvp.steal-percent", 5.0);
        double maxSteal = pvpCfg.getDouble("pvp.max-steal-amount", 20000.0);

        double calculatedSteal = victimBalance * (percent / 100.0);
        double finalSteal = Math.min(calculatedSteal, maxSteal);

        if (extension.getConfig().getBoolean("settings.round-to-integer", true)) {
            finalSteal = Math.round(finalSteal);
        } else {
            finalSteal = Math.round(finalSteal * 100.0) / 100.0;
        }

        if (finalSteal <= 0) return;

        // 4. Списываем у убитого и начисляем убийце
        if (VaultHook.withdrawMoney(victim, finalSteal)) {
            VaultHook.depositMoney(killer, finalSteal);
            statsStorage.recordPvpSteal(killer.getName(), victim.getName(), (long) finalSteal);

            String amountStr = extension.getConfig().getBoolean("settings.round-to-integer", true)
                    ? NumberDateUtil.formatInteger(Math.round(finalSteal))
                    : String.format("%.2f", finalSteal);

            // Сообщение убийце: "Вы убили игрока Ник и получили Сумма"
            ChatUtil.sendMessage(killer, msgCfg.getString("pvp-killer-chat"),
                    "{victim}", victim.getName(), "{amount}", amountStr);

            // Сообщение погибшему: "Вас убил игрок Ник, вы потеряли Сумма"
            ChatUtil.sendMessage(victim, msgCfg.getString("pvp-victim-chat"),
                    "{killer}", killer.getName(), "{amount}", amountStr);
        }
    }
}