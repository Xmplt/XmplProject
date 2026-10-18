package me.xmpl.lib.extension;

import me.xmpl.lib.XmplLib;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ExtensionManager {

    private final XmplLib core;
    private final File extensionsDir;
    private final File configsDir;

    private final Map<String, XmplExtension> extensions = new LinkedHashMap<>();
    private final Map<String, ExtensionClassLoader> loaders = new HashMap<>();
    private final List<DynamicExtensionCommand> allRegisteredCommands = new ArrayList<>();

    private SimpleCommandMap commandMap;
    private Map<String, Command> knownCommands;

    @SuppressWarnings("unchecked")
    public ExtensionManager(XmplLib core, File extensionsDir, File configsDir) {
        this.core = core;
        this.extensionsDir = extensionsDir;
        this.configsDir = configsDir;

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            this.commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            this.knownCommands = (Map<String, Command>) knownCommandsField.get(this.commandMap);
        } catch (Exception e) {
            core.getLogger().severe("Не удалось получить доступ к SimpleCommandMap: " + e.getMessage());
        }
    }

    public void loadAndEnableAll() {
        if (!extensionsDir.exists()) extensionsDir.mkdirs();
        if (!configsDir.exists()) configsDir.mkdirs();

        File[] files = extensionsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            core.getLogger().info("В папке Extensions не найдено .jar дополнений.");
            return;
        }

        for (File file : files) {
            loadExtension(file);
        }

        List<String> disabledInConfig = core.getConfig().getStringList("disabled-extensions");

        for (XmplExtension ext : extensions.values()) {
            enableExtension(ext);
            if (containsIgnoreCase(disabledInConfig, ext.getName())) {
                disableExtension(ext);
                core.getLogger().info("Дополнение [" + ext.getName() + "] оставлено выключенным (согласно config.yml).");
            }
        }

        Bukkit.getScheduler().runTaskLater(core, this::forceOverrideAllCommands, 1L);
    }

    public void forceOverrideAllCommands() {
        if (knownCommands == null) return;
        for (DynamicExtensionCommand cmd : allRegisteredCommands) {
            knownCommands.put(cmd.getName().toLowerCase(), cmd);
            for (String alias : cmd.getAliases()) {
                knownCommands.put(alias.toLowerCase(), cmd);
            }
        }
    }

    private boolean containsIgnoreCase(List<String> list, String target) {
        for (String s : list) {
            if (s.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private void loadExtension(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("extension.yml");
            if (entry == null) {
                core.getLogger().warning("Файл " + file.getName() + " не содержит extension.yml!");
                return;
            }

            ExtensionDescription desc;
            try (InputStream stream = jar.getInputStream(entry)) {
                desc = new ExtensionDescription(stream);
            }

            if (extensions.containsKey(desc.getName())) {
                core.getLogger().warning("Дополнение " + desc.getName() + " уже загружено!");
                return;
            }

            ExtensionClassLoader loader = new ExtensionClassLoader(file, core.getClass().getClassLoader(), this);
            Class<?> jarClass = Class.forName(desc.getMainClass(), true, loader);
            Class<? extends XmplExtension> extClass = jarClass.asSubclass(XmplExtension.class);
            XmplExtension extension = extClass.getDeclaredConstructor().newInstance();

            File extConfigFolder = new File(configsDir, desc.getName());
            if (!extConfigFolder.exists()) {
                extConfigFolder.mkdirs();
            }

            extension.init(core, desc, extConfigFolder, loader);
            extensions.put(desc.getName(), extension);
            loaders.put(desc.getName(), loader);

            extension.onLoad();
            core.getLogger().info("Загружено дополнение: " + desc.getName() + " v" + desc.getVersion());
        } catch (Throwable e) {
            core.getLogger().severe("Ошибка при загрузке дополнения " + file.getName() + ":");
            e.printStackTrace();
        }
    }

    public boolean enableExtension(XmplExtension ext) {
        if (ext.isEnabled()) return false;

        for (String pluginDep : ext.getDescription().getPluginDepends()) {
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginDep)) {
                core.getLogger().severe("Не удалось включить [" + ext.getName() + "]: отсутствует плагин " + pluginDep + "!");
                return false;
            }
        }

        for (String extDep : ext.getDescription().getExtensionDepends()) {
            XmplExtension target = getExtension(extDep);
            if (target == null) {
                core.getLogger().severe("Не удалось включить [" + ext.getName() + "]: отсутствует дополнение " + extDep + "!");
                return false;
            }
            if (!target.isEnabled()) {
                enableExtension(target);
            }
        }

        try {
            ext.reloadAllConfigs();
            ext.setEnabled(true);
            ext.onEnable();
            forceOverrideAllCommands();
            core.getLogger().info("Включено дополнение: [" + ext.getName() + "]");
            return true;
        } catch (Throwable e) {
            ext.setEnabled(false);
            core.getLogger().severe("Ошибка при включении дополнения [" + ext.getName() + "]:");
            e.printStackTrace();
            return false;
        }
    }

    public boolean disableExtension(XmplExtension ext) {
        if (!ext.isEnabled()) return false;

        try {
            ext.onDisable();
        } catch (Throwable e) {
            core.getLogger().severe("Ошибка при выключении дополнения [" + ext.getName() + "]:");
            e.printStackTrace();
        } finally {
            ext.cleanupOnDisable();
            ext.setEnabled(false);
            core.getLogger().info("Выключено дополнение: [" + ext.getName() + "]");
        }
        return true;
    }

    public void unloadAll() {
        List<XmplExtension> reverseList = new ArrayList<>(extensions.values());
        Collections.reverse(reverseList);

        for (XmplExtension ext : reverseList) {
            if (ext.isEnabled()) {
                disableExtension(ext);
            }
            ext.unregisterAllCommands();
        }

        extensions.clear();
        allRegisteredCommands.clear();

        for (ExtensionClassLoader loader : loaders.values()) {
            try {
                loader.close();
            } catch (Exception ignored) {}
        }
        loaders.clear();
    }

    Class<?> getClassByName(String name, ExtensionClassLoader requester) {
        for (ExtensionClassLoader loader : loaders.values()) {
            if (loader == requester) continue;
            try {
                return loader.findClass(name, false);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    public void registerDynamicCommand(String fallbackPrefix, DynamicExtensionCommand command) {
        if (commandMap != null) {
            commandMap.register(fallbackPrefix, command);
        }
        if (knownCommands != null) {
            knownCommands.put(command.getName().toLowerCase(), command);
            for (String alias : command.getAliases()) {
                knownCommands.put(alias.toLowerCase(), command);
            }
        }
        if (!allRegisteredCommands.contains(command)) {
            allRegisteredCommands.add(command);
        }
    }

    public void unregisterDynamicCommand(DynamicExtensionCommand command) {
        if (knownCommands != null) {
            knownCommands.remove(command.getName().toLowerCase());
            knownCommands.remove(command.getLabel().toLowerCase());
            for (String alias : command.getAliases()) {
                knownCommands.remove(alias.toLowerCase());
            }
        }
        allRegisteredCommands.remove(command);
    }

    public Collection<XmplExtension> getExtensions() {
        return Collections.unmodifiableCollection(extensions.values());
    }

    public XmplExtension getExtension(String name) {
        for (Map.Entry<String, XmplExtension> entry : extensions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}