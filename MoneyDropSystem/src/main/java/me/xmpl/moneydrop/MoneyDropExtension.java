package me.xmpl.moneydrop;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.moneydrop.command.MoneyDropCommand;
import me.xmpl.moneydrop.hook.MoneyDropPlaceholders;
import me.xmpl.moneydrop.listener.KillRewardListener;
import me.xmpl.moneydrop.listener.MobSpawnListener;
import me.xmpl.moneydrop.service.MobRewardService;
import me.xmpl.moneydrop.service.PvpStealService;
import me.xmpl.moneydrop.service.SpawnerTagService;
import me.xmpl.moneydrop.storage.MoneyDropStatsStorage;

public class MoneyDropExtension extends XmplExtension {
    private MoneyDropStatsStorage statsStorage;
    private SpawnerTagService spawnerTagService;
    private MobRewardService mobRewardService;
    private PvpStealService pvpStealService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/MoneyDropSystem/
        saveDefaultConfig();
        getCustomConfig("blacklisted-entities.yml");
        getCustomConfig("mobs-rewards.yml");
        getCustomConfig("pvp-steal.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/moneydrop.yml");

        // 2. Инициализируем БД статистики и сервисы
        this.statsStorage = new MoneyDropStatsStorage(this);
        this.spawnerTagService = new SpawnerTagService(this);
        this.mobRewardService = new MobRewardService(this, spawnerTagService, statsStorage);
        this.pvpStealService = new PvpStealService(this, statsStorage);

        // 3. Регистрируем слушатели спавна и убийств
        registerListener(new MobSpawnListener(spawnerTagService));
        registerListener(new KillRewardListener(mobRewardService, pvpStealService));

        // 4. Регистрируем команду /moneydrop (алиас /mdrop)
        registerCommand("moneydrop", new MoneyDropCommand(this, statsStorage), "mdrop");

        // 5. Регистрируем плейсхолдеры (%xmpl_moneydropsystem_...%)
        registerPlaceholder(new MoneyDropPlaceholders(statsStorage));
    }

    @Override
    public void onDisable() {}
}