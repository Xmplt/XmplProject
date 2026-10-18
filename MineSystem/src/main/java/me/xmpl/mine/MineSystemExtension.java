package me.xmpl.mine;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.mine.command.MineCommand;
import me.xmpl.mine.hook.MinePlaceholders;
import me.xmpl.mine.listener.MineBlockListener;
import me.xmpl.mine.service.MineEconomyService;
import me.xmpl.mine.service.MineScoreboardService;
import me.xmpl.mine.service.OreRegenService;
import me.xmpl.mine.storage.MineBagDatabaseStorage;
import me.xmpl.mine.storage.MineRegionStorage;

public class MineSystemExtension extends XmplExtension {
    private MineRegionStorage regionStorage;
    private MineBagDatabaseStorage bagStorage;
    private OreRegenService regenService;
    private MineScoreboardService scoreboardService;
    private MineEconomyService economyService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/MineSystem/
        saveDefaultConfig();
        getCustomConfig("ores.yml");
        getCustomConfig("sell-settings.yml");
        getCustomConfig("scoreboard.yml");
        getCustomConfig("regions.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/mine.yml");

        // 2. Инициализируем хранилища регионов и БД рюкзака руды
        this.regionStorage = new MineRegionStorage(this);
        this.bagStorage = new MineBagDatabaseStorage(this);

        // 3. Инициализируем сервисы светофора стекла, скорборда и продажи
        this.regenService = new OreRegenService(this);
        this.scoreboardService = new MineScoreboardService(this);
        this.economyService = new MineEconomyService(this);

        this.scoreboardService.startRegionCheckTask();

        // 4. Регистрируем слушатель копания и команду /mine
        registerListener(new MineBlockListener(this));
        registerCommand("mine", new MineCommand(this));
        registerPlaceholder(new MinePlaceholders(this));
    }

    @Override
    public void onDisable() {
        if (regenService != null) {
            regenService.forceRestoreAllBlocks();
        }
        if (scoreboardService != null) {
            scoreboardService.restoreAllBoards();
        }
    }

    public MineRegionStorage getRegionStorage() { return regionStorage; }
    public MineBagDatabaseStorage getBagStorage() { return bagStorage; }
    public OreRegenService getRegenService() { return regenService; }
    public MineScoreboardService getScoreboardService() { return scoreboardService; }
    public MineEconomyService getEconomyService() { return economyService; }
}