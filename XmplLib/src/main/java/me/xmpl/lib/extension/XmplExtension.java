package me.xmpl.lib.extension;

import me.xmpl.lib.XmplLib;
import me.xmpl.lib.config.ConfigContainer;
import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public abstract class XmplExtension {
    private XmplLib core;
    private ExtensionDescription description;
    private File dataFolder;
    private ExtensionClassLoader classLoader;
    private boolean enabled = false;
    private ConfigContainer configContainer;

    private final Map<String, DatabaseProvider> databases = new HashMap<>();
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final List<Integer> activeTasks = new ArrayList<>();
    private final Map<String, DynamicExtensionCommand> registeredCommands = new HashMap<>();

    final void init(XmplLib core, ExtensionDescription description, File dataFolder, ExtensionClassLoader classLoader) {
        this.core = core;
        this.description = description;
        this.dataFolder = dataFolder;
        this.classLoader = classLoader;
        this.configContainer = new ConfigContainer(dataFolder, classLoader, core.getLogger());
    }

    public void onLoad() {}
    public abstract void onEnable();
    public abstract void onDisable();

    // --- МУЛЬТИ-КОНФИГИ ДОПОЛНЕНИЯ ---

    public CustomConfig getCustomConfig(String relativePath) {
        return configContainer.getConfig(relativePath);
    }

    public void reloadAllConfigs() {
        configContainer.reloadAll();
    }

    public void saveDefaultConfig() {
        getCustomConfig("config.yml").saveDefault();
    }

    public FileConfiguration getConfig() {
        return getCustomConfig("config.yml").get();
    }

    public void reloadConfig() {
        getCustomConfig("config.yml").reload();
    }

    public void saveConfig() {
        getCustomConfig("config.yml").save();
    }

    // --- БАЗЫ ДАННЫХ (ОДНА ИЛИ МНОЖЕСТВО) ---

    public DatabaseProvider getDatabase() {
        return getDatabase("default");
    }

    public DatabaseProvider getDatabase(String dbKey) {
        return databases.computeIfAbsent(dbKey.toLowerCase(), k -> core.getDatabaseManager().createProviderFor(this, k));
    }

    // --- ПЛЕЙСХОЛДЕРЫ ---

    public void registerPlaceholder(ExtensionPlaceholder placeholder) {
        if (core.getPlaceholderBridge() != null) {
            core.getPlaceholderBridge().registerHandler(getName(), placeholder);
        }
    }

    // --- ОБЕРТКИ BUKKIT API ---

    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, core);
        registeredListeners.add(listener);
    }

    public void registerCommand(String commandName, CommandExecutor executor, String... aliases) {
        String key = commandName.toLowerCase();
        TabCompleter completer = (executor instanceof TabCompleter) ? (TabCompleter) executor : null;

        if (registeredCommands.containsKey(key)) {
            registeredCommands.get(key).updateExecutor(executor, completer);
            return;
        }

        DynamicExtensionCommand dynamicCmd = new DynamicExtensionCommand(
                commandName,
                Arrays.asList(aliases),
                this,
                executor
        );
        core.getExtensionManager().registerDynamicCommand(getName().toLowerCase(), dynamicCmd);
        registeredCommands.put(key, dynamicCmd);
    }

    public int runTaskTimerAsync(Runnable runnable, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimerAsynchronously(core, runnable, delay, period).getTaskId();
        activeTasks.add(id);
        return id;
    }

    public int runTaskTimer(Runnable runnable, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimer(core, runnable, delay, period).getTaskId();
        activeTasks.add(id);
        return id;
    }

    public void runTaskAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(core, runnable);
    }

    public void runTask(Runnable runnable) {
        Bukkit.getScheduler().runTask(core, runnable);
    }

    final void cleanupOnDisable() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();

        for (int taskId : activeTasks) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        activeTasks.clear();

        for (DatabaseProvider db : databases.values()) {
            db.close();
        }
        databases.clear();

        if (core.getPlaceholderBridge() != null) {
            core.getPlaceholderBridge().unregisterHandler(getName());
        }
    }

    final void unregisterAllCommands() {
        for (DynamicExtensionCommand cmd : registeredCommands.values()) {
            core.getExtensionManager().unregisterDynamicCommand(cmd);
        }
        registeredCommands.clear();
    }

    public String getName() { return description.getName(); }
    public ExtensionDescription getDescription() { return description; }
    public File getDataFolder() { return dataFolder; }
    public XmplLib getCore() { return core; }
    public boolean isEnabled() { return enabled; }
    final void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Logger getLogger() { return core.getLogger(); }
}