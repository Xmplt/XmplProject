package me.xmpl.lib.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultHook {
    private static Economy economy;

    private VaultHook() {}

    private static Economy getEconomy() {
        if (economy != null) return economy;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
        return economy;
    }

    public static double getBalance(OfflinePlayer player) {
        Economy econ = getEconomy();
        if (econ == null || player == null) return 0.0;
        return Math.max(0.0, econ.getBalance(player));
    }

    public static boolean hasMoney(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Economy econ = getEconomy();
        if (econ == null) return true;
        return econ.has(player, amount);
    }

    public static boolean withdrawMoney(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Economy econ = getEconomy();
        if (econ == null) return false;
        if (!econ.has(player, amount)) return false;
        return econ.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean depositMoney(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Economy econ = getEconomy();
        if (econ == null) return false;
        return econ.depositPlayer(player, amount).transactionSuccess();
    }
}