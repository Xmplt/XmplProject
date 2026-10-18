package me.xmpl.headdrop;

import me.xmpl.headdrop.command.HeadDropCommand;
import me.xmpl.headdrop.hook.HeadDropPlaceholders;
import me.xmpl.headdrop.listener.HeadBlockPersistListener;
import me.xmpl.headdrop.listener.PvpHeadDropListener;
import me.xmpl.headdrop.service.HeadFactoryService;
import me.xmpl.headdrop.storage.HeadStatsStorage;
import me.xmpl.lib.extension.XmplExtension;

public class HeadDropExtension extends XmplExtension {
    private HeadStatsStorage statsStorage;
    private HeadFactoryService headFactory;

    @Override
    public void onEnable() {
        // 1. Генерируем все 5 отдельных конфигов в plugins/XmplLib/Configs/HeadDropSystem/
        saveDefaultConfig();
        getCustomConfig("head-item.yml");
        getCustomConfig("messages.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/headdrop.yml");

        // 2. Инициализируем БД статистики и фабрику голов
        this.statsStorage = new HeadStatsStorage(this);
        this.headFactory = new HeadFactoryService(this);

        // 3. Регистрируем слушатели ПвП-смерти и сохранения подписи при ломании блока головы
        registerListener(new PvpHeadDropListener(this, headFactory, statsStorage));
        registerListener(new HeadBlockPersistListener(this, headFactory));

        // 4. Регистрируем команду /headdrop (алиас /hdrop)
        registerCommand("headdrop", new HeadDropCommand(this, headFactory, statsStorage), "hdrop");

        // 5. Регистрируем плейсхолдеры (%xmpl_headdropsystem_...%)
        registerPlaceholder(new HeadDropPlaceholders(statsStorage));
    }

    @Override
    public void onDisable() {}
}