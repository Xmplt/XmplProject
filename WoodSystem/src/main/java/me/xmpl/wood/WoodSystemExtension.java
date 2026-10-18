package me.xmpl.wood;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.wood.command.WoodCommand;
import me.xmpl.wood.hook.WoodPlaceholders;
import me.xmpl.wood.listener.WoodBlockListener;
import me.xmpl.wood.service.TreeChopAndRegenService;
import me.xmpl.wood.service.WoodEconomyService;
import me.xmpl.wood.service.WoodScoreboardService;
import me.xmpl.wood.storage.WoodBagDatabaseStorage;
import me.xmpl.wood.storage.WoodRegionStorage;

public class WoodSystemExtension extends XmplExtension {
    private WoodRegionStorage regionStorage;
    private WoodBagDatabaseStorage bagStorage;
    private TreeChopAndRegenService chopService;
    private WoodScoreboardService scoreboardService;
    private WoodEconomyService economyService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/WoodSystem/
        saveDefaultConfig();
        getCustomConfig("trees.yml");
        getCustomConfig("sell-settings.yml");
        getCustomConfig("scoreboard.yml");
        getCustomConfig("regions.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/wood.yml");

        // 2. Инициализируем хранилища регионов и БД срубленного леса
        this.regionStorage = new WoodRegionStorage(this);
        this.bagStorage = new WoodBagDatabaseStorage(this);

        // 3. Инициализируем сервисы рубки, скорборда и продажи
        this.chopService = new TreeChopAndRegenService(this);
        this.scoreboardService = new WoodScoreboardService(this);
        this.economyService = new WoodEconomyService(this);

        this.scoreboardService.startRegionCheckTask();

        // 4. Регистрируем слушатель, команду /wood и плейсхолдеры
        registerListener(new WoodBlockListener(this));
        registerCommand("wood", new WoodCommand(this));
        registerPlaceholder(new WoodPlaceholders(this));
    }

    @Override
    public void onDisable() {
        if (chopService != null) {
            chopService.forceRestoreAllTrees();
        }
        if (scoreboardService != null) {
            scoreboardService.restoreAllBoards();
        }
    }

    public WoodRegionStorage getRegionStorage() { return regionStorage; }
    public WoodBagDatabaseStorage getBagStorage() { return bagStorage; }
    public TreeChopAndRegenService getChopService() { return chopService; }
    public WoodScoreboardService getScoreboardService() { return scoreboardService; }
    public WoodEconomyService getEconomyService() { return economyService; }
}