package me.xmpl.killsystem;

import me.xmpl.killsystem.command.ChatTopCommand;
import me.xmpl.killsystem.command.KillSystemAdminCommand;
import me.xmpl.killsystem.hook.KillSystemPlaceholders;
import me.xmpl.killsystem.listener.KillAndSignListener;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.killsystem.service.TopHopperGuiService;
import me.xmpl.killsystem.service.TopSignUpdaterService;
import me.xmpl.killsystem.storage.KillDatabaseStorage;
import me.xmpl.killsystem.storage.TopSignStorage;
import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.lib.util.SkullSkinUtil;

import java.util.Collections;
import java.util.List;

public class KillSystemExtension extends XmplExtension {
    private KillDatabaseStorage databaseStorage;
    private TopSignStorage signStorage;
    private TopSignUpdaterService signUpdaterService;
    private TopHopperGuiService hopperGuiService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 7 отдельных конфигов в plugins/XmplLib/Configs/KillSystem/
        saveDefaultConfig();
        getCustomConfig("signs-format.yml");
        getCustomConfig("gui-hopper.yml");
        getCustomConfig("chat-top.yml");
        getCustomConfig("signs-data.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/killsystem.yml");

        // 2. Инициализируем БД статистики и хранилище табличек
        this.databaseStorage = new KillDatabaseStorage(this);
        this.signStorage = new TopSignStorage(this);

        // 3. Инициализируем сервисы автообновления табличек (раз в 5 минут) и GUI-воронки
        this.signUpdaterService = new TopSignUpdaterService(this);
        this.hopperGuiService = new TopHopperGuiService(this);
        this.signUpdaterService.startAutoUpdateTask();

        // Фоновый прогрев голов топ-игроков при старте сервера, чтобы даже 1-й клик по табличке был со скинами и без задержек
        prewarmTopSkulls();

        // 4. Регистрируем слушатель убийств и кликов по табличкам
        registerListener(new KillAndSignListener(this));

        // 5. Регистрируем все команды чатового топа (/kills, /killtop, /topkills, /topkill, /killstop)
        ChatTopCommand chatTopCmd = new ChatTopCommand(this);
        registerCommand("kills", chatTopCmd, "killtop", "topkills", "topkill", "killstop");

        // 6. Регистрируем админ-команду /killsystem
        registerCommand("killsystem", new KillSystemAdminCommand(this));

        // 7. Регистрируем плейсхолдеры (%xmpl_killsystem_...%)
        registerPlaceholder(new KillSystemPlaceholders(this));
    }

    private void prewarmTopSkulls() {
        SkullSkinUtil.createOfflineHead("MHF_Question", null, Collections.emptyList());
        List<PlayerKillRecord> top = databaseStorage.getSortedTopList();
        int limit = Math.min(10, top.size());
        for (int i = 0; i < limit; i++) {
            SkullSkinUtil.createOfflineHead(top.get(i).getPlayerName(), null, Collections.emptyList());
        }
    }

    @Override
    public void onDisable() {}

    public KillDatabaseStorage getDatabaseStorage() { return databaseStorage; }
    public TopSignStorage getSignStorage() { return signStorage; }
    public TopSignUpdaterService getSignUpdaterService() { return signUpdaterService; }
    public TopHopperGuiService getHopperGuiService() { return hopperGuiService; }
}