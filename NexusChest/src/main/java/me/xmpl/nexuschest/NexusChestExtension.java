package me.xmpl.nexuschest;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.nexuschest.command.NexusAdminCommand;
import me.xmpl.nexuschest.command.NexusPlayerCommand;
import me.xmpl.nexuschest.hook.NexusChestPlaceholders;
import me.xmpl.nexuschest.listener.NexusArenaAndChestListener;
import me.xmpl.nexuschest.service.ChestLootAndAntiCheatService;
import me.xmpl.nexuschest.service.NexusEventManager;
import me.xmpl.nexuschest.service.NexusHologramService;
import me.xmpl.nexuschest.storage.ArenaConfigStorage;
import me.xmpl.nexuschest.storage.LootPoolStorage;
import me.xmpl.nexuschest.storage.TimerStateDatabaseStorage;

public class NexusChestExtension extends XmplExtension {
    private TimerStateDatabaseStorage timerStorage;
    private ArenaConfigStorage arenaStorage;
    private LootPoolStorage lootPoolStorage;

    private NexusHologramService hologramService;
    private ChestLootAndAntiCheatService lootService;
    private NexusEventManager eventManager;

    @Override
    public void onEnable() {
        // 1. Генерируем все 8 отдельных конфигов в plugins/XmplLib/Configs/NexusChest/
        saveDefaultConfig();
        getCustomConfig("anticheat-loot.yml");
        getCustomConfig("holograms.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("arena.yml");
        getCustomConfig("loot-pool.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/nchest.yml");

        // 2. Инициализируем хранилища БД таймера, арены и пула лута
        this.timerStorage = new TimerStateDatabaseStorage(this);
        this.arenaStorage = new ArenaConfigStorage(this);
        this.lootPoolStorage = new LootPoolStorage(this);

        // 3. Инициализируем сервисы голограмм, анти-чита красной шерсти и менеджера ивента
        this.hologramService = new NexusHologramService(this);
        this.lootService = new ChestLootAndAntiCheatService(this);
        this.eventManager = new NexusEventManager(this);

        this.eventManager.startMasterScheduler();

        // 4. Регистрируем команды: /nexuschest (для игроков) и /nchest (для админов)
        registerCommand("nexuschest", new NexusPlayerCommand(this));
        registerCommand("nchest", new NexusAdminCommand(this));

        // 5. Регистрируем слушатель и плейсхолдеры
        registerListener(new NexusArenaAndChestListener(this));
        registerPlaceholder(new NexusChestPlaceholders(this));
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            // Синхронно сохраняем точное оставшееся время таймера в БД перед выключением сервера!
            timerStorage.saveRemainingSecondsSync(eventManager.getSecondsUntilStart());
        }
        if (hologramService != null) {
            hologramService.hideAll();
        }
        if (lootService != null) {
            lootService.clearBothChests();
        }
    }

    public TimerStateDatabaseStorage getTimerStorage() { return timerStorage; }
    public ArenaConfigStorage getArenaStorage() { return arenaStorage; }
    public LootPoolStorage getLootPoolStorage() { return lootPoolStorage; }
    public NexusHologramService getHologramService() { return hologramService; }
    public ChestLootAndAntiCheatService getLootService() { return lootService; }
    public NexusEventManager getEventManager() { return eventManager; }
}