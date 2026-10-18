package me.xmpl.topka;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.topka.command.KillMeCommand;
import me.xmpl.topka.command.TopkaAdminCommand;
import me.xmpl.topka.hook.TopkaPlaceholders;
import me.xmpl.topka.listener.TopkaChestAndGuiListener;
import me.xmpl.topka.listener.TopkaRegionAndCommandListener;
import me.xmpl.topka.listener.TopkaVotingListener;
import me.xmpl.topka.service.TopkaChestAntiCheatService;
import me.xmpl.topka.service.TopkaChestHologramService;
import me.xmpl.topka.service.TopkaLifecycleService;
import me.xmpl.topka.service.TopkaVotingHologramService;
import me.xmpl.topka.service.TopkaVotingService;
import me.xmpl.topka.storage.TopkaArenaStorage;
import me.xmpl.topka.storage.TopkaLootPoolStorage;
import me.xmpl.topka.storage.TopkaStateDatabaseStorage;
import me.xmpl.topka.storage.TopkaTerrainStorage;

import java.io.File;

public class TopkaExtension extends XmplExtension {
    private TopkaStateDatabaseStorage stateStorage;
    private TopkaArenaStorage arenaStorage;
    private TopkaLootPoolStorage lootPoolStorage;
    private TopkaTerrainStorage terrainStorage;

    private TopkaChestHologramService chestHologramService;
    private TopkaVotingHologramService votingHologramService;
    private TopkaChestAntiCheatService antiCheatService;
    private TopkaVotingService votingService;
    private TopkaLifecycleService lifecycleService;

    @Override
    public void onEnable() {
        cleanupOldEmptyCommandsDir();

        // 1. Генерируем конфиги в корне plugins/XmplLib/Configs/Topka/ (без папки commands/)
        saveDefaultConfig();
        getCustomConfig("databases.yml");
        getCustomConfig("holograms.yml");
        getCustomConfig("region-rules.yml");
        getCustomConfig("anticheat-loot.yml");
        getCustomConfig("voting.yml");
        getCustomConfig("terrains.yml");
        getCustomConfig("arena-data.yml");
        getCustomConfig("loot-pool.yml");
        getCustomConfig("messages.yml");

        // 2. Инициализируем хранилища
        this.stateStorage = new TopkaStateDatabaseStorage(this);
        this.arenaStorage = new TopkaArenaStorage(this);
        this.lootPoolStorage = new TopkaLootPoolStorage(this);
        this.terrainStorage = new TopkaTerrainStorage(this);

        // 3. Инициализируем сервисы
        this.chestHologramService = new TopkaChestHologramService(this);
        this.votingHologramService = new TopkaVotingHologramService(this);
        this.antiCheatService = new TopkaChestAntiCheatService(this);
        this.votingService = new TopkaVotingService(this);
        this.lifecycleService = new TopkaLifecycleService(this);

        this.lifecycleService.startMasterClock();

        // 4. Регистрируем команды /topka и /killme
        registerCommand("topka", new TopkaAdminCommand(this));
        registerCommand("killme", new KillMeCommand(this));

        // 5. Регистрируем слушатели и плейсхолдеры
        registerListener(new TopkaRegionAndCommandListener(this));
        registerListener(new TopkaChestAndGuiListener(this));
        registerListener(new TopkaVotingListener(this));
        registerPlaceholder(new TopkaPlaceholders(this));
    }

    private void cleanupOldEmptyCommandsDir() {
        File oldCmdDir = new File(getDataFolder(), "commands");
        if (oldCmdDir.exists() && oldCmdDir.isDirectory()) {
            File oldFile = new File(oldCmdDir, "topka.yml");
            if (oldFile.exists()) oldFile.delete();
            oldCmdDir.delete();
        }
    }

    @Override
    public void onDisable() {
        if (lifecycleService != null && stateStorage != null) {
            stateStorage.saveStateSync(lifecycleService.getState());
        }
        if (chestHologramService != null) {
            chestHologramService.removeAll();
        }
        if (votingHologramService != null) {
            votingHologramService.removeAll();
        }
    }

    public TopkaStateDatabaseStorage getStateStorage() { return stateStorage; }
    public TopkaArenaStorage getArenaStorage() { return arenaStorage; }
    public TopkaLootPoolStorage getLootPoolStorage() { return lootPoolStorage; }
    public TopkaTerrainStorage getTerrainStorage() { return terrainStorage; }
    public TopkaChestHologramService getChestHologramService() { return chestHologramService; }
    public TopkaVotingHologramService getVotingHologramService() { return votingHologramService; }
    public TopkaChestAntiCheatService getAntiCheatService() { return antiCheatService; }
    public TopkaVotingService getVotingService() { return votingService; }
    public TopkaLifecycleService getLifecycleService() { return lifecycleService; }
}