package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardUtil {

    private static final Map<UUID, Scoreboard> previousBoards = new ConcurrentHashMap<>();
    private static final Map<UUID, Scoreboard> customBoards = new ConcurrentHashMap<>();
    private static final Map<UUID, String> boardOwners = new ConcurrentHashMap<>();

    private ScoreboardUtil() {}

    /**
     * Определяет пакет дополнения-вызывателя (например, "me.xmpl.mine" или "me.xmpl.wood"),
     * чтобы таймер WoodSystem не сбрасывал активный скорборд MineSystem и наоборот.
     */
    private static String resolveCallerOwner() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (StackTraceElement elem : trace) {
            String cls = elem.getClassName();
            if (!cls.equals(ScoreboardUtil.class.getName())) {
                String[] parts = cls.split("\\.");
                if (parts.length >= 3) {
                    return parts[0] + "." + parts[1] + "." + parts[2];
                }
                return cls;
            }
        }
        return "default";
    }

    public static void showCustomSidebar(Player player, String title, List<String> lines) {
        showCustomSidebar(player, resolveCallerOwner(), title, lines);
    }

    public static void showCustomSidebar(Player player, String ownerId, String title, List<String> lines) {
        if (player == null || !player.isOnline() || lines == null) return;
        UUID uuid = player.getUniqueId();
        String owner = (ownerId != null && !ownerId.isEmpty()) ? ownerId : "default";

        Scoreboard board = customBoards.get(uuid);
        if (board == null) {
            previousBoards.put(uuid, player.getScoreboard());
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            customBoards.put(uuid, board);
        }
        boardOwners.put(uuid, owner);

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective("xmpl_sb");
        if (obj == null) {
            obj = board.registerNewObjective("xmpl_sb", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        String coloredTitle = ChatUtil.color(title);
        if (coloredTitle.length() > 32) {
            coloredTitle = coloredTitle.substring(0, 32);
        }
        if (!coloredTitle.equals(obj.getDisplayName())) {
            obj.setDisplayName(coloredTitle);
        }

        List<String> newEntries = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String colored = ChatUtil.color(lines.get(i));
            String uniqueLine = colored + ChatColor.values()[i % ChatColor.values().length] + ChatColor.RESET;
            if (uniqueLine.length() > 40) {
                uniqueLine = uniqueLine.substring(0, 40);
            }
            newEntries.add(uniqueLine);
        }

        Set<String> currentEntries = new HashSet<>(board.getEntries());
        for (String existing : currentEntries) {
            if (!newEntries.contains(existing)) {
                board.resetScores(existing);
            }
        }

        int score = newEntries.size();
        for (String entry : newEntries) {
            obj.getScore(entry).setScore(score--);
        }
    }

    public static void restorePreviousBoard(Player player) {
        restorePreviousBoard(player, resolveCallerOwner());
    }

    public static void restorePreviousBoard(Player player, String ownerId) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String currentOwner = boardOwners.get(uuid);

        // Если скорборд открыт другим дополнением — не трогаем его!
        if (currentOwner != null && ownerId != null && !currentOwner.equals(ownerId)) {
            return;
        }

        boardOwners.remove(uuid);
        customBoards.remove(uuid);
        Scoreboard prev = previousBoards.remove(uuid);
        if (player.isOnline()) {
            if (prev != null) {
                player.setScoreboard(prev);
            } else if (Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    public static boolean hasCustomBoardActive(Player player) {
        return hasCustomBoardActive(player, resolveCallerOwner());
    }

    public static boolean hasCustomBoardActive(Player player, String ownerId) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (!customBoards.containsKey(uuid)) return false;
        String currentOwner = boardOwners.get(uuid);
        return currentOwner == null || ownerId == null || currentOwner.equals(ownerId);
    }
}