package me.xmpl.shield;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.shield.command.ShieldCommand;
import me.xmpl.shield.hook.XmplShieldPlaceholders;
import me.xmpl.shield.listener.ShieldCombatListener;
import me.xmpl.shield.listener.ShieldDurabilityListener;
import me.xmpl.shield.service.ShieldBroadcastService;
import me.xmpl.shield.service.ShieldMechanicService;
import me.xmpl.shield.storage.ShieldStatsStorage;

public class XmplShieldExtension extends XmplExtension {
    private ShieldStatsStorage statsStorage;
    private ShieldBroadcastService broadcastService;
    private ShieldMechanicService mechanicService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 6 отдельных конфигов в plugins/XmplLib/Configs/XmplShield/
        saveDefaultConfig();
        getCustomConfig("sounds.yml");
        getCustomConfig("effects.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/shield.yml");

        // 2. Инициализируем БД статистики и сервисы
        this.statsStorage = new ShieldStatsStorage(this);
        this.broadcastService = new ShieldBroadcastService(this);
        this.mechanicService = new ShieldMechanicService(this);

        // 3. Регистрируем слушатели боевки и поломки щита
        registerListener(new ShieldCombatListener(this, mechanicService, broadcastService, statsStorage));
        registerListener(new ShieldDurabilityListener(this, broadcastService, statsStorage));

        // 4. Регистрируем команду /xmplshield (алиас /xshield)
        registerCommand("xmplshield", new ShieldCommand(this, broadcastService, statsStorage), "xshield");

        // 5. Регистрируем плейсхолдеры (%xmpl_xmplshield_...%)
        registerPlaceholder(new XmplShieldPlaceholders(statsStorage));
    }

    @Override
    public void onDisable() {}
}