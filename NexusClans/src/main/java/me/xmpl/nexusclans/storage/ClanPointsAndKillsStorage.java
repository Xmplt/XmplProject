package me.xmpl.nexusclans.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexusclans.NexusClansExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClanPointsAndKillsStorage {
    public static class MoneyProgress {
        public double depositRemainder = 0.0;
        public double withdrawRemainder = 0.0;
    }

    public static class DailyJobProgress {
        public String dateKey;
        public int minePointsEarnedToday;
        public int woodPointsEarnedToday;
        public int mineOresProgress;
        public int woodTreesProgress;

        public DailyJobProgress(String dateKey) {
            this.dateKey = dateKey;
        }
    }

    private final NexusClansExtension extension;
    // Уникальные пары убийств (killer_key:victim_key), за которые уже было начислено +1 / -1 очко
    private final Set<String> recordedKillPairs = ConcurrentHashMap.newKeySet();
    // Накопители дробных сумм до 10 000$ при /c donate и /c take: "clanKey:playerKey" -> MoneyProgress
    private final Map<String, MoneyProgress> moneyProgressMap = new ConcurrentHashMap<>();
    // Дневные лимиты кланов по шахте (100/день) и дереву (100/день): clanKey -> DailyJobProgress
    private final Map<String, DailyJobProgress> dailyJobsMap = new ConcurrentHashMap<>();

    public ClanPointsAndKillsStorage(NexusClansExtension extension) {
        this.extension = extension;
        initTables();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("clans_points");
    }

    private void initTables() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS clan_unique_pvp_pairs (" +
                "pair_key VARCHAR(68) PRIMARY KEY, " +
                "created_at BIGINT NOT NULL" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS clan_money_accumulators (" +
                "acc_key VARCHAR(68) PRIMARY KEY, " +
                "deposit_rem DOUBLE NOT NULL DEFAULT 0.0, " +
                "withdraw_rem DOUBLE NOT NULL DEFAULT 0.0" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS clan_daily_job_limits (" +
                "clan_key VARCHAR(32) PRIMARY KEY, " +
                "date_key VARCHAR(16) NOT NULL, " +
                "mine_points INT NOT NULL DEFAULT 0, " +
                "wood_points INT NOT NULL DEFAULT 0, " +
                "mine_ores_rem INT NOT NULL DEFAULT 0, " +
                "wood_trees_rem INT NOT NULL DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        recordedKillPairs.clear();
        moneyProgressMap.clear();
        dailyJobsMap.clear();

        try (Connection conn = db().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT pair_key FROM clan_unique_pvp_pairs");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recordedKillPairs.add(rs.getString("pair_key").toLowerCase());
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_money_accumulators");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MoneyProgress mp = new MoneyProgress();
                    mp.depositRemainder = rs.getDouble("deposit_rem");
                    mp.withdrawRemainder = rs.getDouble("withdraw_rem");
                    moneyProgressMap.put(rs.getString("acc_key").toLowerCase(), mp);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_daily_job_limits");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DailyJobProgress djp = new DailyJobProgress(rs.getString("date_key"));
                    djp.minePointsEarnedToday = rs.getInt("mine_points");
                    djp.woodPointsEarnedToday = rs.getInt("wood_points");
                    djp.mineOresProgress = rs.getInt("mine_ores_rem");
                    djp.woodTreesProgress = rs.getInt("wood_trees_rem");
                    dailyJobsMap.put(rs.getString("clan_key").toLowerCase(), djp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Проверяет, убивал ли уже конкретный игрок killerName конкретного игрока victimName.
     * Если нет — регистрирует уникальную пару и возвращает true (начисляем +1 убийце и -1 жертве).
     * Если уже убивал — возвращает false (повторно за этого же игрока этому убийце очки не даются!).
     */
    public boolean tryRegisterUniqueClanKillPair(String killerName, String victimName) {
        String pairKey = killerName.toLowerCase() + ":" + victimName.toLowerCase();
        if (!recordedKillPairs.add(pairKey)) {
            return false;
        }
        long now = System.currentTimeMillis();
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM clan_unique_pvp_pairs WHERE pair_key = ?", pairKey);
            db().executeUpdate("INSERT INTO clan_unique_pvp_pairs VALUES (?, ?)", pairKey, now);
        });
        return true;
    }

    /**
     * Добавляет сумму доната в накопитель игрока (например 5000 + 3000 + 2000 = 10000 -> возвращает 1 очко).
     */
    public synchronized int accumulateDepositAndCalcPoints(String clanKey, String playerName, double amount, double stepMoney, int pointsPerStep) {
        if (stepMoney <= 0) return 0;
        String accKey = clanKey.toLowerCase() + ":" + playerName.toLowerCase();
        MoneyProgress mp = moneyProgressMap.computeIfAbsent(accKey, k -> new MoneyProgress());
        mp.depositRemainder += amount;

        int steps = (int) Math.floor(mp.depositRemainder / stepMoney);
        if (steps > 0) {
            mp.depositRemainder -= steps * stepMoney;
        }
        saveMoneyProgressAsync(accKey, mp);
        return steps * pointsPerStep;
    }

    /**
     * Добавляет сумму снятия (/c take) в накопитель главы (например 5000 + 5000 = 10000 -> возвращает 1 очко штрафа).
     */
    public synchronized int accumulateWithdrawAndCalcPenaltyPoints(String clanKey, String leaderName, double amount, double stepMoney, int pointsPerStep) {
        if (stepMoney <= 0) return 0;
        String accKey = clanKey.toLowerCase() + ":" + leaderName.toLowerCase();
        MoneyProgress mp = moneyProgressMap.computeIfAbsent(accKey, k -> new MoneyProgress());
        mp.withdrawRemainder += amount;

        int steps = (int) Math.floor(mp.withdrawRemainder / stepMoney);
        if (steps > 0) {
            mp.withdrawRemainder -= steps * stepMoney;
        }
        saveMoneyProgressAsync(accKey, mp);
        return steps * pointsPerStep;
    }

    private void saveMoneyProgressAsync(String accKey, MoneyProgress mp) {
        double dep = mp.depositRemainder;
        double wit = mp.withdrawRemainder;
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM clan_money_accumulators WHERE acc_key = ?", accKey);
            db().executeUpdate("INSERT INTO clan_money_accumulators VALUES (?, ?, ?)", accKey, dep, wit);
        });
    }

    private DailyJobProgress getOrResetDailyProgress(String clanKey) {
        String today = LocalDate.now().toString();
        DailyJobProgress djp = dailyJobsMap.computeIfAbsent(clanKey.toLowerCase(), k -> new DailyJobProgress(today));
        if (!today.equals(djp.dateKey)) {
            djp.dateKey = today;
            djp.minePointsEarnedToday = 0;
            djp.woodPointsEarnedToday = 0;
            djp.mineOresProgress = 0;
            djp.woodTreesProgress = 0;
        }
        return djp;
    }

    /**
     * Добавляет +1 выкопанную руду в общий прогресс клана.
     * Каждые 20 руд возвращает +1 очко, пока не достигнут дневной лимит клана (100 очков в день).
     */
    public synchronized int incrementMineOreAndCheckPoint(String clanKey, int oresPerPoint, int maxDailyPoints) {
        DailyJobProgress djp = getOrResetDailyProgress(clanKey);
        if (djp.minePointsEarnedToday >= maxDailyPoints) {
            return 0;
        }
        djp.mineOresProgress++;
        int earned = 0;
        if (djp.mineOresProgress >= oresPerPoint) {
            djp.mineOresProgress -= oresPerPoint;
            djp.minePointsEarnedToday++;
            earned = 1;
        }
        saveDailyJobAsync(clanKey.toLowerCase(), djp);
        return earned;
    }

    /**
     * Добавляет +1 срубленное дерево в общий прогресс клана.
     * Каждые 15 деревьев возвращает +1 очко, пока не достигнут дневной лимит клана (100 очков в день).
     */
    public synchronized int incrementWoodTreeAndCheckPoint(String clanKey, int treesPerPoint, int maxDailyPoints) {
        DailyJobProgress djp = getOrResetDailyProgress(clanKey);
        if (djp.woodPointsEarnedToday >= maxDailyPoints) {
            return 0;
        }
        djp.woodTreesProgress++;
        int earned = 0;
        if (djp.woodTreesProgress >= treesPerPoint) {
            djp.woodTreesProgress -= treesPerPoint;
            djp.woodPointsEarnedToday++;
            earned = 1;
        }
        saveDailyJobAsync(clanKey.toLowerCase(), djp);
        return earned;
    }

    private void saveDailyJobAsync(String clanKey, DailyJobProgress djp) {
        String date = djp.dateKey;
        int mp = djp.minePointsEarnedToday;
        int wp = djp.woodPointsEarnedToday;
        int mo = djp.mineOresProgress;
        int wt = djp.woodTreesProgress;
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM clan_daily_job_limits WHERE clan_key = ?", clanKey);
            db().executeUpdate("INSERT INTO clan_daily_job_limits VALUES (?, ?, ?, ?, ?, ?)",
                    clanKey, date, mp, wp, mo, wt);
        });
    }
}