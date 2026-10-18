package me.xmpl.airdrop;

import me.xmpl.airdrop.command.AirDropAdminCommand;
import me.xmpl.airdrop.hook.AirDropPlaceholders;
import me.xmpl.airdrop.listener.AirDropGuiListener;
import me.xmpl.airdrop.listener.AirDropWorldAndRestrictionListener;
import me.xmpl.airdrop.service.*;
import me.xmpl.airdrop.storage.AirDropLootStorage;
import me.xmpl.airdrop.storage.AirDropStateDatabaseStorage;
import me.xmpl.lib.extension.XmplExtension;

import java.util.List;

public class AirDropExtension extends XmplExtension {
    private AirDropStateDatabaseStorage stateStorage;
    private AirDropLootStorage lootStorage;

    private SafeWorldLocationFinder locationFinder;
    private ProtectiveRegionService regionService;
    private RestrictionRollerService restrictionRoller;
    private AirDropHologramService hologramService;
    private LootFountainPhysicsService fountainPhysics;
    private AirDropLootEditorGuiService guiService;
    private AirDropLifecycleManager lifecycleManager;

    @Override
    public void onEnable() {
        // 1. Генерируем все 10 отдельных конфигов в plugins/XmplLib/Configs/AirDropSystem/
        saveDefaultConfig();
        getCustomConfig("restrictions.yml");
        getCustomConfig("worlds.yml");
        getCustomConfig("holograms.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("gui-editor.yml");
        getCustomConfig("loot-items.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/airdrop.yml");

        // 2. Инициализируем хранилища БД состояния и лута
        this.stateStorage = new AirDropStateDatabaseStorage(this);
        this.lootStorage = new AirDropLootStorage(this);

        // 3. Инициализируем все 7 специализированных сервисов
        this.locationFinder = new SafeWorldLocationFinder(this);
        this.regionService = new ProtectiveRegionService(this);
        this.restrictionRoller = new RestrictionRollerService(this);
        this.hologramService = new AirDropHologramService(this);
        this.fountainPhysics = new LootFountainPhysicsService(this);
        this.guiService = new AirDropLootEditorGuiService(this);
        this.lifecycleManager = new AirDropLifecycleManager(this);

        this.lifecycleManager.startMasterClock();

        // 4. Регистрируем единственную админ-команду /airdrop
        List<String> aliases = getCustomConfig("commands/airdrop.yml").get().getStringList("aliases");
        registerCommand("airdrop", new AirDropAdminCommand(this), aliases.toArray(new String[0]));

        // 5. Регистрируем слушатели и плейсхолдеры
        registerListener(new AirDropWorldAndRestrictionListener(this));
        registerListener(new AirDropGuiListener(this));
        registerPlaceholder(new AirDropPlaceholders(this));
    }

    @Override
    public void onDisable() {
        if (lifecycleManager != null && stateStorage != null) {
            // Синхронно сохраняем точное состояние (включая текущую фазу и оставшиеся предметы!) перед рестартом
            stateStorage.saveStateSync(lifecycleManager.getState());
        }
        if (hologramService != null) {
            hologramService.removeHologramForAll();
        }
    }

    public AirDropStateDatabaseStorage getStateStorage() { return stateStorage; }
    public AirDropLootStorage getLootStorage() { return lootStorage; }
    public SafeWorldLocationFinder getLocationFinder() { return locationFinder; }
    public ProtectiveRegionService getRegionService() { return regionService; }
    public RestrictionRollerService getRestrictionRoller() { return restrictionRoller; }
    public AirDropHologramService getHologramService() { return hologramService; }
    public LootFountainPhysicsService getFountainPhysics() { return fountainPhysics; }
    public AirDropLootEditorGuiService getGuiService() { return guiService; }
    public AirDropLifecycleManager getLifecycleManager() { return lifecycleManager; }
}