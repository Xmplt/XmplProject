package me.xmpl.adender;

import me.xmpl.adender.command.AdEnderAdminCommand;
import me.xmpl.adender.command.AdPortalCommand;
import me.xmpl.adender.command.EnderPortalCommand;
import me.xmpl.adender.hook.AdEnderPlaceholders;
import me.xmpl.adender.service.PortalTeleportService;
import me.xmpl.adender.storage.PortalLocationStorage;
import me.xmpl.adender.storage.PortalStatsDatabaseStorage;
import me.xmpl.lib.extension.XmplExtension;

import java.util.List;

public class AdEnderExtension extends XmplExtension {
    private PortalLocationStorage locationStorage;
    private PortalStatsDatabaseStorage statsStorage;
    private PortalTeleportService teleportService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/AdEnder/
        saveDefaultConfig();
        getCustomConfig("portals.yml");
        getCustomConfig("locations.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/ad.yml");
        getCustomConfig("commands/ender.yml");
        getCustomConfig("commands/adender.yml");

        // 2. Инициализируем хранилища точек и БД статистики
        this.locationStorage = new PortalLocationStorage(this);
        this.statsStorage = new PortalStatsDatabaseStorage(this);
        this.teleportService = new PortalTeleportService(this);

        // 3. Регистрируем команды /ad, /ender и админ-команду /adender
        List<String> adAliases = getCustomConfig("commands/ad.yml").get().getStringList("aliases");
        registerCommand("ad", new AdPortalCommand(this), adAliases.toArray(new String[0]));

        List<String> enderAliases = getCustomConfig("commands/ender.yml").get().getStringList("aliases");
        registerCommand("ender", new EnderPortalCommand(this), enderAliases.toArray(new String[0]));

        registerCommand("adender", new AdEnderAdminCommand(this), "setportal");

        // 4. Регистрируем плейсхолдеры (%xmpl_adender_...%)
        registerPlaceholder(new AdEnderPlaceholders(statsStorage));
    }

    @Override
    public void onDisable() {}

    public PortalLocationStorage getLocationStorage() { return locationStorage; }
    public PortalStatsDatabaseStorage getStatsStorage() { return statsStorage; }
    public PortalTeleportService getTeleportService() { return teleportService; }
}