package me.xmpl.ess;

import me.xmpl.ess.command.BanCommands;
import me.xmpl.ess.command.MuteAndNbcCommands;
import me.xmpl.ess.command.SeenCommand;
import me.xmpl.ess.command.UtilityCommands;
import me.xmpl.ess.command.VanishAndStickCommands;
import me.xmpl.ess.hook.XmplEssPlaceholders;
import me.xmpl.ess.listener.GuiListener;
import me.xmpl.ess.listener.ModerationAndCombatListener;
import me.xmpl.ess.service.InvSeeService;
import me.xmpl.ess.service.RtpService;
import me.xmpl.ess.service.VanishAndHelperService;
import me.xmpl.ess.storage.BanStorage;
import me.xmpl.ess.storage.MuteStorage;
import me.xmpl.ess.storage.UserStorage;
import me.xmpl.ess.storage.VanishStorage;
import me.xmpl.lib.extension.XmplExtension;

public class XmplEssExtension extends XmplExtension {
    private BanStorage banStorage;
    private MuteStorage muteStorage;
    private VanishStorage vanishStorage;
    private UserStorage userStorage;

    private VanishAndHelperService vanishService;
    private InvSeeService invSeeService;
    private RtpService rtpService;

    @Override
    public void onEnable() {
        // 1. Генерируем все отдельные конфиги в plugins/XmplLib/Configs/XmplEss/
        saveDefaultConfig();
        getCustomConfig("databases.yml");
        getCustomConfig("commands/bans.yml");
        getCustomConfig("commands/mutes.yml");
        getCustomConfig("commands/seen.yml");
        getCustomConfig("commands/vanish.yml");
        getCustomConfig("commands/stickhelper.yml");
        getCustomConfig("commands/invsee.yml");
        getCustomConfig("commands/near.yml");
        getCustomConfig("commands/rtp.yml");

        // 2. Инициализируем 4 отдельные базы данных
        this.banStorage = new BanStorage(this);
        this.muteStorage = new MuteStorage(this);
        this.vanishStorage = new VanishStorage(this);
        this.userStorage = new UserStorage(this);

        // 3. Инициализируем сервисы
        this.vanishService = new VanishAndHelperService(this, vanishStorage);
        this.invSeeService = new InvSeeService(this);
        this.rtpService = new RtpService(this);

        this.vanishService.startActionBarTask();
        this.invSeeService.startLiveSyncTask();

        // 4. Регистрируем команды
        BanCommands banCmd = new BanCommands(this);
        registerCommand("ban", banCmd, "tempban");
        registerCommand("pban", banCmd);
        registerCommand("unban", banCmd, "pardon");
        registerCommand("kick", banCmd);

        MuteAndNbcCommands muteCmd = new MuteAndNbcCommands(this);
        registerCommand("mute", muteCmd, "tempmute");
        registerCommand("pmute", muteCmd);
        registerCommand("unmute", muteCmd);
        registerCommand("takecmd", muteCmd);
        registerCommand("nbc", muteCmd);

        registerCommand("seen", new SeenCommand(this));

        VanishAndStickCommands vanishCmd = new VanishAndStickCommands(this, vanishService);
        registerCommand("vanish", vanishCmd, "v");
        registerCommand("online", vanishCmd, "list");
        registerCommand("god", vanishCmd);
        registerCommand("stickhelper", vanishCmd);

        UtilityCommands utilCmd = new UtilityCommands(this);
        registerCommand("invsee", utilCmd);
        registerCommand("near", utilCmd);
        registerCommand("rtp", utilCmd);

        // 5. Регистрируем слушатели событий и плейсхолдеры
        registerListener(new ModerationAndCombatListener(this, vanishService));
        registerListener(new GuiListener(this));
        registerPlaceholder(new XmplEssPlaceholders(this));
    }

    @Override
    public void onDisable() {}

    public BanStorage getBanStorage() { return banStorage; }
    public MuteStorage getMuteStorage() { return muteStorage; }
    public VanishStorage getVanishStorage() { return vanishStorage; }
    public UserStorage getUserStorage() { return userStorage; }
    public VanishAndHelperService getVanishService() { return vanishService; }
    public InvSeeService getInvSeeService() { return invSeeService; }
    public RtpService getRtpService() { return rtpService; }
}