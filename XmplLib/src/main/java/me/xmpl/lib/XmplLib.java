package me.xmpl.lib;

import me.xmpl.lib.config.ConfigContainer;
import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.database.DatabaseManager;
import me.xmpl.lib.extension.ExtensionManager;
import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.lib.hook.XmplPlaceholderBridge;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class XmplLib extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static XmplLib instance;
    private ConfigContainer configContainer;
    private DatabaseManager databaseManager;
    private ExtensionManager extensionManager;
    private XmplPlaceholderBridge placeholderBridge;

    private File extensionsFolder;
    private File configsFolder;

    @Override
    public void onEnable() {
        instance = this;

        this.configContainer = new ConfigContainer(getDataFolder(), getClassLoader(), getLogger());
        saveDefaultConfig();
        getMessagesConfig();
        getCustomConfig("commands/xmpllib.yml");

        this.extensionsFolder = new File(getDataFolder(), "Extensions");
        this.configsFolder = new File(getDataFolder(), "Configs");
        if (!extensionsFolder.exists()) extensionsFolder.mkdirs();
        if (!configsFolder.exists()) configsFolder.mkdirs();

        // Подключаем мост к PlaceholderAPI, если плагин есть на сервере
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderBridge = new XmplPlaceholderBridge(this);
            this.placeholderBridge.register();
            getLogger().info("Интеграция с PlaceholderAPI успешно активирована!");
        }

        this.databaseManager = new DatabaseManager(this);
        this.extensionManager = new ExtensionManager(this, extensionsFolder, configsFolder);
        this.extensionManager.loadAndEnableAll();

        getCommand("xmpllib").setExecutor(this);
        getCommand("xmpllib").setTabCompleter(this);

        getLogger().info("XmplLib успешно запущен! Загружено дополнений: " + extensionManager.getExtensions().size());
    }

    @Override
    public void onDisable() {
        if (extensionManager != null) {
            extensionManager.unloadAll();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("XmplLib отключен.");
    }

    public CustomConfig getCustomConfig(String relativePath) {
        return configContainer.getConfig(relativePath);
    }

    public CustomConfig getMessagesConfig() {
        return getCustomConfig("messages.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CustomConfig cmdCfg = getCustomConfig("commands/xmpllib.yml");
        CustomConfig msgCfg = getMessagesConfig();

        String perm = cmdCfg.get().getString("permission", "xmpllib.admin");
        if (!sender.hasPermission(perm)) {
            ChatUtil.sendMessage(sender, msgCfg.get().getString("no-permission", "&cУ вас нет прав!"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.get().getStringList("help-message"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            ChatUtil.sendMessage(sender, cmdCfg.get().getString("list-header"),
                    "%total%", extensionManager.getExtensions().size());

            for (XmplExtension ext : extensionManager.getExtensions()) {
                String format = ext.isEnabled()
                        ? cmdCfg.get().getString("list-format-enabled")
                        : cmdCfg.get().getString("list-format-disabled");
                ChatUtil.sendMessage(sender, format,
                        "%name%", ext.getName(),
                        "%version%", ext.getDescription().getVersion());
            }
            return true;
        }

        if (sub.equals("disable") && args.length >= 2) {
            XmplExtension ext = extensionManager.getExtension(args[1]);
            if (ext == null) {
                ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-not-found"), "%extension%", args[1]);
                return true;
            }
            if (!ext.isEnabled()) {
                ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-already-disabled"), "%extension%", ext.getName());
                return true;
            }

            extensionManager.disableExtension(ext);
            updateDisabledListInConfig(ext.getName(), true);
            ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-disabled-success"), "%extension%", ext.getName());
            return true;
        }

        if (sub.equals("enable") && args.length >= 2) {
            XmplExtension ext = extensionManager.getExtension(args[1]);
            if (ext == null) {
                ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-not-found"), "%extension%", args[1]);
                return true;
            }
            if (ext.isEnabled()) {
                ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-already-enabled"), "%extension%", ext.getName());
                return true;
            }

            if (extensionManager.enableExtension(ext)) {
                updateDisabledListInConfig(ext.getName(), false);
                ChatUtil.sendMessage(sender, msgCfg.get().getString("extension-enabled-success"), "%extension%", ext.getName());
            }
            return true;
        }

        if (sub.equals("reload")) {
            extensionManager.unloadAll();
            reloadConfig();
            configContainer.reloadAll();
            databaseManager.shutdown();
            databaseManager = new DatabaseManager(this);
            extensionManager.loadAndEnableAll();

            long activeCount = extensionManager.getExtensions().stream().filter(XmplExtension::isEnabled).count();
            ChatUtil.sendMessage(sender, msgCfg.get().getString("reload-success"), "%count%", activeCount);
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.get().getStringList("help-message"));
        return true;
    }

    private void updateDisabledListInConfig(String extName, boolean add) {
        List<String> list = new ArrayList<>(getConfig().getStringList("disabled-extensions"));
        list.removeIf(s -> s.equalsIgnoreCase(extName));
        if (add) {
            list.add(extName);
        }
        getConfig().set("disabled-extensions", list);
        saveConfig();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("xmpllib.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("list", "enable", "disable", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            boolean wantEnabled = args[0].equalsIgnoreCase("disable");
            return extensionManager.getExtensions().stream()
                    .filter(ext -> ext.isEnabled() == wantEnabled)
                    .map(XmplExtension::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
    private me.xmpl.lib.economy.DonateCurrencyService donateCurrency;

    public me.xmpl.lib.economy.DonateCurrencyService getDonateCurrency() {
        return donateCurrency;
    }

    public void setDonateCurrency(me.xmpl.lib.economy.DonateCurrencyService donateCurrency) {
        this.donateCurrency = donateCurrency;
    }
    public static XmplLib getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ExtensionManager getExtensionManager() { return extensionManager; }
    public XmplPlaceholderBridge getPlaceholderBridge() { return placeholderBridge; }
}