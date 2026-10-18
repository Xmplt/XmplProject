package me.xmpl.fishing;

import me.xmpl.fishing.command.FishingAdminCommand;
import me.xmpl.fishing.command.SellFishCommand;
import me.xmpl.fishing.hook.FishingPlaceholders;
import me.xmpl.fishing.listener.FishingMechanicListener;
import me.xmpl.fishing.service.FishGeneratorService;
import me.xmpl.fishing.service.FishSellService;
import me.xmpl.fishing.storage.FishingRegionStorage;
import me.xmpl.fishing.storage.FishingStatsStorage;
import me.xmpl.lib.extension.XmplExtension;

public class FishingSystemExtension extends XmplExtension {
    private FishingRegionStorage regionStorage;
    private FishingStatsStorage statsStorage;
    private FishGeneratorService generatorService;
    private FishSellService sellService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/FishingSystem/
        saveDefaultConfig();
        getCustomConfig("fish-species.yml");
        getCustomConfig("rarities.yml");
        getCustomConfig("sell-settings.yml");
        getCustomConfig("regions.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/fishing.yml");

        // 2. Инициализируем хранилища регионов x2 и БД статистики
        this.regionStorage = new FishingRegionStorage(this);
        this.statsStorage = new FishingStatsStorage(this);

        // 3. Инициализируем сервисы генерации веса и продажи
        this.generatorService = new FishGeneratorService(this);
        this.sellService = new FishSellService(this);

        // 4. Регистрируем слушатель ловли и печек
        registerListener(new FishingMechanicListener(this));

        // 5. Регистрируем команды: /sellfish (для игроков) и /fishingsystem (для админов)
        registerCommand("sellfish", new SellFishCommand(this));
        registerCommand("fishingsystem", new FishingAdminCommand(this), "fsystem");

        // 6. Регистрируем плейсхолдеры (%xmpl_fishingsystem_...%)
        registerPlaceholder(new FishingPlaceholders(statsStorage));
    }

    @Override
    public void onDisable() {}

    public FishingRegionStorage getRegionStorage() { return regionStorage; }
    public FishingStatsStorage getStatsStorage() { return statsStorage; }
    public FishGeneratorService getGeneratorService() { return generatorService; }
    public FishSellService getSellService() { return sellService; }
}