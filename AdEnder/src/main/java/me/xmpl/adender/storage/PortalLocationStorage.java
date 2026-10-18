package me.xmpl.adender.storage;

import me.xmpl.adender.AdEnderExtension;
import me.xmpl.adender.model.PortalDestinationType;
import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.LocationSerializerUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

public class PortalLocationStorage {
    private final AdEnderExtension extension;
    private final Map<PortalDestinationType, Location> locations = new EnumMap<>(PortalDestinationType.class);

    public PortalLocationStorage(AdEnderExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        locations.clear();
        FileConfiguration cfg = extension.getCustomConfig("locations.yml").get();
        for (PortalDestinationType type : PortalDestinationType.values()) {
            Location loc = LocationSerializerUtil.readLocation(cfg, "points." + type.name());
            if (loc != null) {
                locations.put(type, loc);
            }
        }
    }

    public void setLocation(PortalDestinationType type, Location location) {
        Location copy = location.clone();
        locations.put(type, copy);

        CustomConfig customCfg = extension.getCustomConfig("locations.yml");
        LocationSerializerUtil.writeLocation(customCfg.get(), "points." + type.name(), copy);
        customCfg.save();
    }

    public Location getLocation(PortalDestinationType type) {
        Location loc = locations.get(type);
        return loc != null ? loc.clone() : null;
    }
}