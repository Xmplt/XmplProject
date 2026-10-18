package me.xmpl.nexusclans;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.nexusclans.command.ClanPlayerCommand;
import me.xmpl.nexusclans.command.NexusClansAdminCommand;
import me.xmpl.nexusclans.command.WarCommand;
import me.xmpl.nexusclans.hook.NexusClansPlaceholders;
import me.xmpl.nexusclans.listener.ClanChestAndSignListener;
import me.xmpl.nexusclans.listener.ClanCombatAndRideListener;
import me.xmpl.nexusclans.listener.ClanExternalEventsListener;
import me.xmpl.nexusclans.listener.ClanWarListener;
import me.xmpl.nexusclans.service.ClanAllianceService;
import me.xmpl.nexusclans.service.ClanCoreService;
import me.xmpl.nexusclans.service.ClanGlowAndRideService;
import me.xmpl.nexusclans.service.ClanModuleService;
import me.xmpl.nexusclans.service.ClanSmartPointsService;
import me.xmpl.nexusclans.service.ClanTopSignAndBoardService;
import me.xmpl.nexusclans.service.ClanVirtualChestService;
import me.xmpl.nexusclans.service.ClanWarService;
import me.xmpl.nexusclans.storage.ClanAlliancesDatabaseStorage;
import me.xmpl.nexusclans.storage.ClanLogsDatabaseStorage;
import me.xmpl.nexusclans.storage.ClanMainDatabaseStorage;
import me.xmpl.nexusclans.storage.ClanPointsAndKillsStorage;
import me.xmpl.nexusclans.storage.ClanVirtualChestStorage;
import me.xmpl.nexusclans.storage.ClanWarArenaStorage;

public class NexusClansExtension extends XmplExtension {
    private ClanMainDatabaseStorage mainStorage;
    private ClanPointsAndKillsStorage pointsStorage;
    private ClanLogsDatabaseStorage logsStorage;
    private ClanAlliancesDatabaseStorage alliancesStorage;
    private ClanVirtualChestStorage chestStorage;
    private ClanWarArenaStorage warArenaStorage;

    private ClanModuleService moduleService;
    private ClanSmartPointsService smartPointsService;
    private ClanCoreService clanCoreService;
    private ClanAllianceService allianceService;
    private ClanVirtualChestService virtualChestService;
    private ClanGlowAndRideService glowAndRideService;
    private ClanTopSignAndBoardService topSignAndBoardService;
    private ClanWarService warService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 12 конфигурационных файлов в plugins/XmplLib/Configs/NexusClans/
        saveDefaultConfig();
        getCustomConfig("modules.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("points-and-limits.yml");
        getCustomConfig("glow-and-ride.yml");
        getCustomConfig("alliances.yml");
        getCustomConfig("chest-and-home.yml");
        getCustomConfig("war-arena.yml");
        getCustomConfig("top-signs-and-board.yml");
        getCustomConfig("commands/clan-messages.yml");
        getCustomConfig("commands/war-messages.yml");
        getCustomConfig("commands/admin-messages.yml");

        // 2. Инициализируем 5 отдельных баз данных SQLite/MySQL и хранилище арены
        this.mainStorage = new ClanMainDatabaseStorage(this);
        this.pointsStorage = new ClanPointsAndKillsStorage(this);
        this.logsStorage = new ClanLogsDatabaseStorage(this);
        this.alliancesStorage = new ClanAlliancesDatabaseStorage(this);
        this.chestStorage = new ClanVirtualChestStorage(this);
        this.warArenaStorage = new ClanWarArenaStorage(this);

        // 3. Инициализируем все сервисы
        this.moduleService = new ClanModuleService(this);
        this.smartPointsService = new ClanSmartPointsService(this);
        this.clanCoreService = new ClanCoreService(this);
        this.allianceService = new ClanAllianceService(this);
        this.virtualChestService = new ClanVirtualChestService(this);
        this.glowAndRideService = new ClanGlowAndRideService(this);
        this.topSignAndBoardService = new ClanTopSignAndBoardService(this);
        this.warService = new ClanWarService(this);

        // 4. Запускаем фоновые задачи
        this.clanCoreService.startInviteExpirationTask();
        this.glowAndRideService.startGlowService();
        this.topSignAndBoardService.startTasks();
        this.warService.startWarClock();

        // 5. Регистрируем команды: /clan (алиас /c), /war и /nexusclans (алиас /nclans)
        registerCommand("clan", new ClanPlayerCommand(this), "c");
        registerCommand("war", new WarCommand(this));
        registerCommand("nexusclans", new NexusClansAdminCommand(this), "nclans");

        // 6. Регистрируем слушатели событий и PlaceholderAPI
        registerListener(new ClanCombatAndRideListener(this));
        registerListener(new ClanChestAndSignListener(this));
        registerListener(new ClanExternalEventsListener(this));
        registerListener(new ClanWarListener(this));
        registerPlaceholder(new NexusClansPlaceholders(this));
    }

    @Override
    public void onDisable() {
        if (warService != null) {
            warService.cancelWarAndRefundBets(null);
        }
        if (virtualChestService != null) {
            virtualChestService.saveAndCloseAllOnDisable();
        }
        if (topSignAndBoardService != null) {
            topSignAndBoardService.restoreAllBoards();
        }
        if (glowAndRideService != null) {
            glowAndRideService.stopGlowService();
        }
    }

    public ClanMainDatabaseStorage getMainStorage() { return mainStorage; }
    public ClanPointsAndKillsStorage getPointsStorage() { return pointsStorage; }
    public ClanLogsDatabaseStorage getLogsStorage() { return logsStorage; }
    public ClanAlliancesDatabaseStorage getAlliancesStorage() { return alliancesStorage; }
    public ClanVirtualChestStorage getChestStorage() { return chestStorage; }
    public ClanWarArenaStorage getWarArenaStorage() { return warArenaStorage; }

    public ClanModuleService getModuleService() { return moduleService; }
    public ClanSmartPointsService getSmartPointsService() { return smartPointsService; }
    public ClanCoreService getClanCoreService() { return clanCoreService; }
    public ClanAllianceService getAllianceService() { return allianceService; }
    public ClanVirtualChestService getVirtualChestService() { return virtualChestService; }
    public ClanGlowAndRideService getGlowAndRideService() { return glowAndRideService; }
    public ClanTopSignAndBoardService getTopSignAndBoardService() { return topSignAndBoardService; }
    public ClanWarService getWarService() { return warService; }
}