package me.xmpl.enderrub;

import me.xmpl.enderrub.command.EnderRubCommand;
import me.xmpl.enderrub.hook.EnderRubPlaceholders;
import me.xmpl.enderrub.listener.AccountInitListener;
import me.xmpl.enderrub.service.EnderRubService;
import me.xmpl.enderrub.storage.BalanceStorage;
import me.xmpl.enderrub.storage.HistoryStorage;
import me.xmpl.lib.extension.XmplExtension;

public class EnderRubExtension extends XmplExtension {
    private BalanceStorage balanceStorage;
    private HistoryStorage historyStorage;
    private EnderRubService service;

    @Override
    public void onEnable() {
        // 1. Генерируем все 6 отдельных конфигов в plugins/XmplLib/Configs/EnderRub/
        saveDefaultConfig();
        getCustomConfig("databases.yml");
        getCustomConfig("placeholders.yml");
        getCustomConfig("history.yml");
        getCustomConfig("commands/player-command.yml");
        getCustomConfig("commands/admin-command.yml");

        // 2. Инициализируем 2 отдельные базы данных (balances и history)
        this.balanceStorage = new BalanceStorage(this);
        this.historyStorage = new HistoryStorage(this);

        // 3. Запускаем сервис и регистрируем его в глобальном ядре XmplLib (для всех будущих плагинов!)
        this.service = new EnderRubService(this, balanceStorage, historyStorage);
        getCore().setDonateCurrency(this.service);

        // 4. Регистрируем команды /enderrub и /endrub
        EnderRubCommand cmd = new EnderRubCommand(this, service);
        registerCommand("enderrub", cmd, "endrub");

        // 5. Регистрируем слушатель и плейсхолдеры для TAB
        registerListener(new AccountInitListener(balanceStorage));
        registerPlaceholder(new EnderRubPlaceholders(this, service));
    }

    @Override
    public void onDisable() {
        if (getCore().getDonateCurrency() == this.service) {
            getCore().setDonateCurrency(null);
        }
    }

    public EnderRubService getService() { return service; }
}