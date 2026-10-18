package me.xmpl.lib.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigContainer {
    private final File baseFolder;
    private final ClassLoader resourceLoader;
    private final Logger logger;
    private final Map<String, CustomConfig> configs = new HashMap<>();

    public ConfigContainer(File baseFolder, ClassLoader resourceLoader, Logger logger) {
        this.baseFolder = baseFolder;
        this.resourceLoader = resourceLoader;
        this.logger = logger;
    }

    public CustomConfig getConfig(String relativePath) {
        String key = relativePath.endsWith(".yml") ? relativePath.toLowerCase() : (relativePath + ".yml").toLowerCase();
        return configs.computeIfAbsent(key, k -> new CustomConfig(baseFolder, relativePath, resourceLoader, logger));
    }

    public void reloadAll() {
        for (CustomConfig cfg : configs.values()) {
            cfg.reload();
        }
    }

    public void saveAll() {
        for (CustomConfig cfg : configs.values()) {
            cfg.save();
        }
    }
}