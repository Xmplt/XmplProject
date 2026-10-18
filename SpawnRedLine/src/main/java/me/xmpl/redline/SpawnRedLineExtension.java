package me.xmpl.redline;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.redline.command.RedLineCommand;
import me.xmpl.redline.hook.RedLinePlaceholders;
import me.xmpl.redline.listener.RedLineCombatListener;
import me.xmpl.redline.listener.RedLineMovementListener;
import me.xmpl.redline.service.RedLineMarkService;
import me.xmpl.redline.service.ZoneDetectorService;
import me.xmpl.redline.storage.RedLineStatsStorage;

public class SpawnRedLineExtension extends XmplExtension {
    private RedLineStatsStorage statsStorage;
    private ZoneDetectorService zoneDetector;
    private RedLineMarkService markService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 6 отдельных конфигов в plugins/XmplLib/Configs/SpawnRedLine/
        saveDefaultConfig();
        getCustomConfig("regions.yml");
        getCustomConfig("teleports.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/redline.yml");

        // 2. Инициализируем БД статистики и сервисы зон/меток
        this.statsStorage = new RedLineStatsStorage(this);
        this.zoneDetector = new ZoneDetectorService(this);
        this.markService = new RedLineMarkService(this, zoneDetector);

        this.markService.startExpirationTask();

        // 3. Регистрируем слушатели движения/телепортов и боевки на спавне
        registerListener(new RedLineMovementListener(this, markService));
        registerListener(new RedLineCombatListener(this, markService, zoneDetector));

        // 4. Регистрируем команду /redline и плейсхолдеры
        registerCommand("redline", new RedLineCommand(this), "spawnredline");
        registerPlaceholder(new RedLinePlaceholders(this));
    }

    @Override
    public void onDisable() {}

    public RedLineStatsStorage getStatsStorage() { return statsStorage; }
    public ZoneDetectorService getZoneDetector() { return zoneDetector; }
    public RedLineMarkService getMarkService() { return markService; }
}