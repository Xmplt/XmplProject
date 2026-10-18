package me.xmpl.nexusclans.model;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanWarSession {

    public enum WarStage {
        /** Глава первого клана прописал /c war и собирает состав (/c warmembers) и отправляет вызов (/c war invite) — 5 минут */
        ATTACKER_ROSTER_ASSEMBLY,
        /** Вызов отправлен второму клану, ставка заморожена, ожидание /war accept — 2 минуты */
        WAITING_DEFENDER_ACCEPT,
        /** Второй клан принял вызов, у него есть 10 минут, чтобы собрать 5 игроков через /c warmembers */
        DEFENDER_ROSTER_ASSEMBLY,
        /** Оба состава собраны: 10 минут на подготовку инвентарей, замены (/war membersedit) и ввод /clan pvpready */
        INVENTORY_PREP,
        /** Все нажали /clan pvpready (или вышло время подготовки): 3 минуты полной блокировки инвентарей перед телепортом */
        INVENTORY_LOCK_3MIN,
        /** Бойцы телепортированы в барьерные капсулы на точках арены: 1 минута до снятия барьеров (с отсчетом 5..1 на экране) */
        CAGE_COUNTDOWN_1MIN,
        /** Идет активный бой 5 на 5 на арене (максимум 30 минут) */
        ACTIVE_BATTLE,
        /** Все враги повержены: у победителей есть 2 минуты, чтобы собрать лут на арене до телепортации и регенерации */
        WINNER_LOOT_2MIN
    }

    public static class SpectatorState {
        public final Location previousLocation;
        public final GameMode previousGameMode;

        public SpectatorState(Location previousLocation, GameMode previousGameMode) {
            this.previousLocation = previousLocation;
            this.previousGameMode = previousGameMode;
        }
    }

    public static class PendingReplacementInvite {
        public final String clanKey;
        public final String oldPlayerName;
        public final String newPlayerName;
        public final long expireAtMillis;

        public PendingReplacementInvite(String clanKey, String oldPlayerName, String newPlayerName, long expireAtMillis) {
            this.clanKey = clanKey.toLowerCase();
            this.oldPlayerName = oldPlayerName;
            this.newPlayerName = newPlayerName;
            this.expireAtMillis = expireAtMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expireAtMillis;
        }
    }

    private WarStage stage = WarStage.ATTACKER_ROSTER_ASSEMBLY;
    private int timerSeconds;

    private final String clan1Key; // Клан-инициатор
    private String clan2Key;       // Клан-защитник
    private double betAmount = 0.0;
    private boolean clan1BetEscrowed = false;
    private boolean clan2BetEscrowed = false;

    // Участники (по 5 ников с каждой стороны)
    private final List<String> clan1Roster = Collections.synchronizedList(new ArrayList<>());
    private final List<String> clan2Roster = Collections.synchronizedList(new ArrayList<>());

    // Игроки, прописавшие /clan pvpready (в нижнем регистре)
    private final Set<String> readyPlayers = ConcurrentHashMap.newKeySet();

    // Привязка каждого бойца к его персональной стартовой точке на арене (для удержания в барьере)
    private final Map<String, Location> assignedSpawnPoints = new ConcurrentHashMap<>();

    // Живые участники во время боя (в нижнем регистре)
    private final Set<String> aliveClan1 = ConcurrentHashMap.newKeySet();
    private final Set<String> aliveClan2 = ConcurrentHashMap.newKeySet();

    // Порядок смертей в каждом клане (1-й умерший добавляется первым, последний — последним)
    private final List<String> clan1DeathOrder = Collections.synchronizedList(new ArrayList<>());
    private final List<String> clan2DeathOrder = Collections.synchronizedList(new ArrayList<>());

    // Количество убийств каждого бойца в текущей войне (playerKey -> kills)
    private final Map<String, Integer> warKills = new ConcurrentHashMap<>();
    // Флаг: вышли ли проигравшие игроки с сервера (для рандомного распределения очков при дисконнекте)
    private int quitDeathsCountClan1 = 0;
    private int quitDeathsCountClan2 = 0;

    private String winningClanKey = null;

    // Активные приглашения на замену (/war membersedit <старый> <новый>): newPlayerKey -> PendingReplacementInvite
    private final Map<String, PendingReplacementInvite> replacementInvites = new ConcurrentHashMap<>();

    // Наблюдатели (/war spectate): UUID -> предыдущее состояние
    private final Map<UUID, SpectatorState> spectators = new ConcurrentHashMap<>();

    public ClanWarSession(String clan1Key, int initialTimeoutSeconds) {
        this.clan1Key = clan1Key.toLowerCase();
        this.timerSeconds = initialTimeoutSeconds;
    }

    public boolean isParticipant(String playerName) {
        if (playerName == null) return false;
        return isClan1Fighter(playerName) || isClan2Fighter(playerName);
    }

    public boolean isAliveFighter(String playerName) {
        if (playerName == null) return false;
        String k = playerName.toLowerCase();
        return aliveClan1.contains(k) || aliveClan2.contains(k);
    }

    public boolean isClan1Fighter(String playerName) {
        if (playerName == null) return false;
        synchronized (clan1Roster) {
            for (String s : clan1Roster) {
                if (s.equalsIgnoreCase(playerName)) return true;
            }
        }
        return false;
    }

    public boolean isClan2Fighter(String playerName) {
        if (playerName == null) return false;
        synchronized (clan2Roster) {
            for (String s : clan2Roster) {
                if (s.equalsIgnoreCase(playerName)) return true;
            }
        }
        return false;
    }

    public List<String> getAllParticipantsNames() {
        List<String> all = new ArrayList<>();
        synchronized (clan1Roster) { all.addAll(clan1Roster); }
        synchronized (clan2Roster) { all.addAll(clan2Roster); }
        return all;
    }

    public WarStage getStage() { return stage; }
    public void setStage(WarStage stage) { this.stage = stage; }
    public int getTimerSeconds() { return timerSeconds; }
    public void setTimerSeconds(int timerSeconds) { this.timerSeconds = timerSeconds; }
    public void decrementTimer() { this.timerSeconds--; }

    public String getClan1Key() { return clan1Key; }
    public String getClan2Key() { return clan2Key; }
    public void setClan2Key(String clan2Key) { this.clan2Key = clan2Key != null ? clan2Key.toLowerCase() : null; }

    public double getBetAmount() { return betAmount; }
    public void setBetAmount(double betAmount) { this.betAmount = betAmount; }
    public boolean isClan1BetEscrowed() { return clan1BetEscrowed; }
    public void setClan1BetEscrowed(boolean clan1BetEscrowed) { this.clan1BetEscrowed = clan1BetEscrowed; }
    public boolean isClan2BetEscrowed() { return clan2BetEscrowed; }
    public void setClan2BetEscrowed(boolean clan2BetEscrowed) { this.clan2BetEscrowed = clan2BetEscrowed; }

    public List<String> getClan1Roster() { return clan1Roster; }
    public List<String> getClan2Roster() { return clan2Roster; }
    public Set<String> getReadyPlayers() { return readyPlayers; }
    public Map<String, Location> getAssignedSpawnPoints() { return assignedSpawnPoints; }
    public Set<String> getAliveClan1() { return aliveClan1; }
    public Set<String> getAliveClan2() { return aliveClan2; }
    public List<String> getClan1DeathOrder() { return clan1DeathOrder; }
    public List<String> getClan2DeathOrder() { return clan2DeathOrder; }
    public Map<String, Integer> getWarKills() { return warKills; }

    public int getQuitDeathsCountClan1() { return quitDeathsCountClan1; }
    public void incrementQuitDeathsClan1() { this.quitDeathsCountClan1++; }
    public int getQuitDeathsCountClan2() { return quitDeathsCountClan2; }
    public void incrementQuitDeathsClan2() { this.quitDeathsCountClan2++; }

    public String getWinningClanKey() { return winningClanKey; }
    public void setWinningClanKey(String winningClanKey) { this.winningClanKey = winningClanKey; }

    public Map<String, PendingReplacementInvite> getReplacementInvites() { return replacementInvites; }
    public Map<UUID, SpectatorState> getSpectators() { return spectators; }
}