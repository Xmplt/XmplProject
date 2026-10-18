package me.xmpl.lib.config;

import me.xmpl.lib.extension.ExtensionClassLoader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class CustomConfig {

    private final File file;
    private final String relativePath;
    private final ClassLoader resourceLoader;
    private final Logger logger;
    private FileConfiguration config;

    public CustomConfig(File baseFolder, String relativePath, ClassLoader resourceLoader, Logger logger) {
        String normalizedPath = relativePath.endsWith(".yml") ? relativePath : relativePath + ".yml";
        this.relativePath = normalizedPath;
        this.file = new File(baseFolder, normalizedPath);
        this.resourceLoader = resourceLoader;
        this.logger = logger;
        saveDefault();
        reload();
        repairIfCorruptedByLib();
    }

    public void saveDefault() {
        if (!file.exists()) {
            copyResourceToFile(false);
        }
    }

    /**
     * Автоматически заменяет ошибочно скопированные ранее config.yml и messages.yml от XmplLib
     * на настоящие конфиги дополнения из его собственного .jar.
     */
    private void repairIfCorruptedByLib() {
        if (!(resourceLoader instanceof ExtensionClassLoader) || config == null) return;

        boolean isLibConfig = relativePath.equalsIgnoreCase("config.yml")
                && config.contains("database-mode")
                && config.contains("disabled-extensions");

        boolean isLibMessages = relativePath.equalsIgnoreCase("messages.yml")
                && config.contains("extension-disabled-command")
                && config.contains("extension-not-found");

        if (isLibConfig || isLibMessages) {
            logger.info("[XmplLib] Восстановление родного конфига дополнения: " + file.getPath());
            copyResourceToFile(true);
            this.config = YamlConfiguration.loadConfiguration(file);
        }
    }

    private void copyResourceToFile(boolean overwrite) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = resourceLoader.getResourceAsStream(relativePath)) {
            if (in != null) {
                if (overwrite) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(in, file.toPath());
                }
            } else if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            logger.severe("Не удалось сохранить конфиг " + file.getPath() + ": " + e.getMessage());
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        repairIfCorruptedByLib();
    }

    public void save() {
        if (config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("Не удалось сохранить " + file.getPath() + ": " + e.getMessage());
        }
    }

    public FileConfiguration get() {
        if (config == null) {
            reload();
        }
        return config;
    }

    public File getFile() {
        return file;
    }
}