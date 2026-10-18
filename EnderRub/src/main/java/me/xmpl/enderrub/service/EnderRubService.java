package me.xmpl.enderrub.service;

import me.xmpl.enderrub.EnderRubExtension;
import me.xmpl.enderrub.model.TransactionRecord;
import me.xmpl.enderrub.model.TransactionType;
import me.xmpl.enderrub.storage.BalanceStorage;
import me.xmpl.enderrub.storage.HistoryStorage;
import me.xmpl.lib.economy.DonateCurrencyService;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class EnderRubService implements DonateCurrencyService {
    private final EnderRubExtension extension;
    private final BalanceStorage balanceStorage;
    private final HistoryStorage historyStorage;

    public EnderRubService(EnderRubExtension extension, BalanceStorage balanceStorage, HistoryStorage historyStorage) {
        this.extension = extension;
        this.balanceStorage = balanceStorage;
        this.historyStorage = historyStorage;
    }

    @Override
    public long getBalance(String playerName) {
        return balanceStorage.getBalance(playerName);
    }

    @Override
    public boolean has(String playerName, long amount) {
        if (amount <= 0) return true;
        return getBalance(playerName) >= amount;
    }

    @Override
    public synchronized boolean deposit(String playerName, long amount, String source, String description) {
        if (playerName == null || amount <= 0) return false;
        long current = getBalance(playerName);
        long next = current + amount;

        balanceStorage.setBalance(playerName, next);
        historyStorage.addRecordAsync(new TransactionRecord(
                playerName, TransactionType.DEPOSIT, amount, next,
                source != null ? source : "Система",
                description != null ? description : "Пополнение счета",
                System.currentTimeMillis()
        ));

        if (extension.getConfig().getBoolean("currency.notify-player-on-change", true)) {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                String msg = extension.getCustomConfig("commands/admin-command.yml").get()
                        .getString("messages.give-notify-target");
                ChatUtil.sendMessage(target, msg,
                        "{amount}", NumberDateUtil.formatInteger(amount),
                        "{new_balance}", NumberDateUtil.formatInteger(next));
            }
        }
        return true;
    }

    @Override
    public synchronized boolean withdraw(String playerName, long amount, String source, String description) {
        if (playerName == null || amount <= 0) return false;
        long current = getBalance(playerName);
        if (current < amount) return false;

        long next = current - amount;
        balanceStorage.setBalance(playerName, next);
        historyStorage.addRecordAsync(new TransactionRecord(
                playerName, TransactionType.WITHDRAW, amount, next,
                source != null ? source : "Магазин",
                description != null ? description : "Покупка",
                System.currentTimeMillis()
        ));

        if (extension.getConfig().getBoolean("currency.notify-player-on-change", true)) {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                String msg = extension.getCustomConfig("commands/admin-command.yml").get()
                        .getString("messages.take-notify-target");
                ChatUtil.sendMessage(target, msg,
                        "{amount}", NumberDateUtil.formatInteger(amount),
                        "{new_balance}", NumberDateUtil.formatInteger(next));
            }
        }
        return true;
    }

    @Override
    public synchronized boolean setBalance(String playerName, long newBalance, String source, String description) {
        if (playerName == null || newBalance < 0) return false;
        balanceStorage.setBalance(playerName, newBalance);
        historyStorage.addRecordAsync(new TransactionRecord(
                playerName, TransactionType.SET, newBalance, newBalance,
                source != null ? source : "Администратор",
                description != null ? description : "Установка баланса",
                System.currentTimeMillis()
        ));
        return true;
    }

    /**
     * Асинхронно загружает страницу истории из БД и отправляет её игроку в чат
     */
    public void sendBalanceAndHistoryPage(CommandSender viewer, String targetName, int requestedPage) {
        extension.runTaskAsync(() -> {
            FileConfiguration histCfg = extension.getCustomConfig("history.yml").get();
            FileConfiguration playerCmdCfg = extension.getCustomConfig("commands/player-command.yml").get();
            String symbol = extension.getConfig().getString("currency.symbol", "₪");

            int perPage = Math.max(1, histCfg.getInt("settings.entries-per-page", 5));
            int totalRecords = historyStorage.countRecords(targetName);
            int maxPages = Math.max(1, (int) Math.ceil((double) totalRecords / perPage));

            if (requestedPage < 1 || requestedPage > maxPages) {
                ChatUtil.sendMessage(viewer, playerCmdCfg.getString("messages.invalid-page"),
                        "{page}", requestedPage, "{max_pages}", maxPages);
                return;
            }

            long balance = getBalance(targetName);
            String balanceFormatted = NumberDateUtil.formatInteger(balance);
            List<TransactionRecord> records = totalRecords > 0
                    ? historyStorage.getPage(targetName, requestedPage, perPage)
                    : java.util.Collections.emptyList();

            // Отправляем шапку
            ChatUtil.sendMessages(viewer, histCfg.getStringList("view.header"),
                    "{player}", targetName,
                    "{balance}", balance,
                    "{balance_formatted}", balanceFormatted,
                    "{symbol}", symbol,
                    "{page}", requestedPage,
                    "{max_pages}", maxPages);

            // Отправляем строки истории
            if (records.isEmpty()) {
                ChatUtil.sendMessage(viewer, histCfg.getString("view.empty-history"));
            } else {
                String entryFormat = histCfg.getString("view.entry-format");
                for (TransactionRecord rec : records) {
                    String rawTypeFmt = histCfg.getString("types." + rec.getType().name(), "{amount}");
                    String typeFormatted = rawTypeFmt.replace("{amount}", NumberDateUtil.formatInteger(rec.getAmount()));

                    ChatUtil.sendMessage(viewer, entryFormat,
                            "{date}", NumberDateUtil.formatDate(rec.getTimestamp()),
                            "{type_format}", typeFormatted,
                            "{description}", rec.getDescription(),
                            "{source}", rec.getSource(),
                            "{balance_after}", NumberDateUtil.formatInteger(rec.getBalanceAfter()));
                }
            }

            // Отправляем подвал (с подсказкой следующей страницы, если она есть)
            if (requestedPage < maxPages) {
                ChatUtil.sendMessages(viewer, histCfg.getStringList("view.footer"),
                        "{next_page}", requestedPage + 1,
                        "{page}", requestedPage,
                        "{max_pages}", maxPages);
            } else {
                ChatUtil.sendMessages(viewer, histCfg.getStringList("view.footer-last-page"));
            }
        });
    }
}