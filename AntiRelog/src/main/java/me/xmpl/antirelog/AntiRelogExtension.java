package me.xmpl.antirelog;

import me.xmpl.antirelog.command.AntiRelogCommand;
import me.xmpl.antirelog.hook.AntiRelogPlaceholders;
import me.xmpl.antirelog.listener.CombatCommandListener;
import me.xmpl.antirelog.listener.CombatDamageListener;
import me.xmpl.antirelog.listener.CombatQuitDeathListener;
import me.xmpl.antirelog.service.CombatBossBarService;
import me.xmpl.antirelog.service.CombatManager;
import me.xmpl.antirelog.service.CombatNotificationService;
import me.xmpl.antirelog.storage.CombatLogStorage;
import me.xmpl.lib.extension.XmplExtension;

public class AntiRelogExtension extends XmplExtension {
    private CombatManager combatManager;
    private CombatLogStorage logStorage;

    @Override
    public void onEnable() {
        // 1. Генерируем и загружаем все отдельные конфиги в plugins/XmplLib/Configs/AntiRelog/
        saveDefaultConfig();
        getCustomConfig("bossbar.yml");
        getCustomConfig("titles.yml");
        getCustomConfig("whitelist-commands.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("commands/antirelog.yml");
        getCustomConfig("databases.yml");

        // 2. Инициализируем сервисы и хранилище БД
        CombatBossBarService bossBarService = new CombatBossBarService(this);
        CombatNotificationService notificationService = new CombatNotificationService(this);
        this.combatManager = new CombatManager(this, bossBarService, notificationService);
        this.logStorage = new CombatLogStorage(this);

        // 3. Запускаем ежесекундный таймер Босс-бара и ПвП
        this.combatManager.startTimerTask();

        // 4. Регистрируем слушатели событий
        registerListener(new CombatDamageListener(this, combatManager));
        registerListener(new CombatCommandListener(this, combatManager));
        registerListener(new CombatQuitDeathListener(this, combatManager, logStorage));

        // 5. Регистрируем команду /antirelog (и алиас /ar)
        registerCommand("antirelog", new AntiRelogCommand(this, combatManager), "ar");

        // 6. Регистрируем плейсхолдеры (%xmpl_antirelog_...%)
        registerPlaceholder(new AntiRelogPlaceholders(this, combatManager));
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.clearAll();
        }
    }

    public CombatManager getCombatManager() { return combatManager; }
    public CombatLogStorage getLogStorage() { return logStorage; }
}