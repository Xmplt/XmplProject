package me.xmpl.enderrub.model;

public class TransactionRecord {
    private final String playerName;
    private final TransactionType type;
    private final long amount;
    private final long balanceAfter;
    private final String source;
    private final String description;
    private final long timestamp;

    public TransactionRecord(String playerName, TransactionType type, long amount, long balanceAfter,
                             String source, String description, long timestamp) {
        this.playerName = playerName;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.source = source;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getPlayerName() { return playerName; }
    public TransactionType getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getSource() { return source; }
    public String getDescription() { return description; }
    public long getTimestamp() { return timestamp; }
}