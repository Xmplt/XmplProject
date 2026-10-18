package me.xmpl.lib.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.xmpl.lib.XmplLib;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class XmplPlaceholderBridge extends PlaceholderExpansion {
    private final XmplLib core;
    private final Map<String, ExtensionPlaceholder> handlers = new HashMap<>();

    public XmplPlaceholderBridge(XmplLib core) {
        this.core = core;
    }

    public void registerHandler(String extensionName, ExtensionPlaceholder handler) {
        handlers.put(extensionName.toLowerCase(), handler);
    }

    public void unregisterHandler(String extensionName) {
        handlers.remove(extensionName.toLowerCase());
    }

    @Override
    public String getIdentifier() {
        return "xmpl";
    }

    @Override
    public String getAuthor() {
        return "Xmpl";
    }

    @Override
    public String getVersion() {
        return core.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        int firstUnderscore = identifier.indexOf('_');
        if (firstUnderscore == -1) return null;

        String extKey = identifier.substring(0, firstUnderscore).toLowerCase();
        String params = identifier.substring(firstUnderscore + 1);

        ExtensionPlaceholder handler = handlers.get(extKey);
        if (handler == null) return "";
        return handler.onPlaceholderRequest(player, params);
    }
}