package me.xmpl.funeffects;

import me.xmpl.funeffects.command.ActionCommands;
import me.xmpl.funeffects.command.VocalCommandExecutor;
import me.xmpl.funeffects.listener.FunEffectsListener;
import me.xmpl.funeffects.service.BurstCooldownTracker;
import me.xmpl.funeffects.service.FartService;
import me.xmpl.funeffects.service.KakaPipiService;
import me.xmpl.funeffects.service.SpitService;
import me.xmpl.funeffects.service.VocalSoundService;
import me.xmpl.funeffects.storage.FunPlayerStorage;
import me.xmpl.lib.extension.XmplExtension;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class FunEffectsExtension extends XmplExtension {
    private FunPlayerStorage playerStorage;
    private BurstCooldownTracker cooldownTracker;
    private SpitService spitService;
    private FartService fartService;
    private KakaPipiService kakaPipiService;
    private VocalSoundService vocalSoundService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 8 отдельных конфигов в plugins/XmplLib/Configs/FunEffectsSystem/
        saveDefaultConfig();
        getCustomConfig("databases.yml");
        getCustomConfig("spits.yml");
        getCustomConfig("fart.yml");
        getCustomConfig("kaka.yml");
        getCustomConfig("pipi.yml");
        getCustomConfig("sounds-commands.yml");

        // 2. Инициализируем БД выбранных плевков и сервисы
        this.playerStorage = new FunPlayerStorage(this);
        this.cooldownTracker = new BurstCooldownTracker();
        this.spitService = new SpitService(this);
        this.fartService = new FartService(this);
        this.kakaPipiService = new KakaPipiService(this);
        this.vocalSoundService = new VocalSoundService(this);

        this.kakaPipiService.startGroundStepCheckTask();

        // 3. Регистрируем основные команды действий
        ActionCommands actionCmd = new ActionCommands(this);
        registerCommand("spit", actionCmd);
        registerCommand("spitlist", actionCmd, "spits");
        registerCommand("fart", actionCmd);
        registerCommand("kaka", actionCmd, "poop");
        registerCommand("pipi", actionCmd, "pee");
        registerCommand("funeffects", actionCmd);

        // 4. Динамически регистрируем все звуковые команды из sounds-commands.yml (/skyli, /hurk, /bark, /meow, /roar, /hiss)
        ConfigurationSection vocalSec = vocalSoundService.getCommandsSection();
        if (vocalSec != null) {
            for (String cmdKey : vocalSec.getKeys(false)) {
                List<String> aliases = vocalSec.getStringList(cmdKey + ".aliases");
                registerCommand(cmdKey.toLowerCase(), new VocalCommandExecutor(this, cmdKey),
                        aliases.toArray(new String[0]));
            }
        }

        // 5. Регистрируем слушатель событий
        registerListener(new FunEffectsListener(this));
    }

    @Override
    public void onDisable() {
        if (kakaPipiService != null) {
            kakaPipiService.cleanupAllDrops();
        }
    }

    public FunPlayerStorage getPlayerStorage() { return playerStorage; }
    public BurstCooldownTracker getCooldownTracker() { return cooldownTracker; }
    public SpitService getSpitService() { return spitService; }
    public FartService getFartService() { return fartService; }
    public KakaPipiService getKakaPipiService() { return kakaPipiService; }
    public VocalSoundService getVocalSoundService() { return vocalSoundService; }
}