package me.xmpl.lib.extension;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class ExtensionDescription {
    private final String name;
    private final String mainClass;
    private final String version;
    private final List<String> authors;
    private final List<String> pluginDepends; // Зависимости от Bukkit плагинов (WorldGuard и т.д.)
    private final List<String> extensionDepends; // Зависимости от других наших дополнений

    public ExtensionDescription(InputStream stream) throws IllegalArgumentException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        this.name = yaml.getString("name");
        this.mainClass = yaml.getString("main");
        this.version = yaml.getString("version", "1.0.0");
        this.authors = yaml.getStringList("authors");
        this.pluginDepends = yaml.getStringList("depend");
        this.extensionDepends = yaml.getStringList("ext-depend");

        if (this.name == null || this.mainClass == null) {
            throw new IllegalArgumentException("Invalid extension.yml: 'name' and 'main' fields are required!");
        }
    }

    public String getName() { return name; }
    public String getMainClass() { return mainClass; }
    public String getVersion() { return version; }
    public List<String> getAuthors() { return authors != null ? authors : Collections.emptyList(); }
    public List<String> getPluginDepends() { return pluginDepends != null ? pluginDepends : Collections.emptyList(); }
    public List<String> getExtensionDepends() { return extensionDepends != null ? extensionDepends : Collections.emptyList(); }
}