package me.xmpl.funeffects.service;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VocalSoundService {
    private final FunEffectsExtension extension;

    public VocalSoundService(FunEffectsExtension extension) {
        this.extension = extension;
    }

    public void executeVocalCommand(Player sender, String cmdKey) {
        FileConfiguration cfg = extension.getCustomConfig("sounds-commands.yml").get();
        String path = "commands." + cmdKey.toLowerCase();
        if (!cfg.isConfigurationSection(path)) return;

        if (cfg.getBoolean(path + ".require-permission", false)) {
            String perm = cfg.getString(path + ".permission", "funeffects.sound." + cmdKey.toLowerCase());
            if (!sender.hasPermission(perm)) {
                ChatUtil.sendMessage(sender, cfg.getString("general.no-permission-message"));
                return;
            }
        }

        int cdSec = cfg.getInt("general.cooldown-seconds", 5);
        int remCd = extension.getCooldownTracker().tryUse(sender.getUniqueId(), "VOCAL_SOUND", 1, cdSec);
        if (remCd > 0) {
            ChatUtil.sendMessage(sender, cfg.getString("general.cooldown-message"), "{time}", remCd);
            return;
        }

        double radius = cfg.getDouble(path + ".radius", 16.0);
        double rSq = radius * radius;
        String soundName = cfg.getString(path + ".sound", "ENTITY_WOLF_AMBIENT");
        float volume = (float) cfg.getDouble(path + ".volume", 1.5);
        float pitch = (float) cfg.getDouble(path + ".pitch", 1.0);
        String actionSelf = cfg.getString(path + ".action-self", "издали звук");
        String actionTarget = cfg.getString(path + ".action-target", "издал звук");

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sound = Sound.ENTITY_WOLF_AMBIENT;
        }

        // Проигрываем звук самому отправителю
        sender.playSound(sender.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);

        // Ищем всех игроков в радиусе слышимости (кроме скрытых в ванише)
        List<Player> listeners = new ArrayList<>();
        for (Player other : sender.getWorld().getPlayers()) {
            if (other.equals(sender)) continue;
            if (other.getLocation().distanceSquared(sender.getLocation()) <= rSq) {
                other.playSound(sender.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
                if (!other.hasMetadata("xmpl_vanished")) {
                    listeners.add(other);
                }
            }
        }

        String listenerFormat = cfg.getString("general.listener-format", "&eВам {action_target} игрок {player}!");
        for (Player target : listeners) {
            ChatUtil.sendMessage(target, listenerFormat,
                    "{action_target}", actionTarget,
                    "{player}", sender.getName());
        }

        String senderFormat = cfg.getString("general.sender-format", "&eВы {action_self}, и это услышало {count} игроков!");
        ChatUtil.sendMessage(sender, senderFormat,
                "{action_self}", actionSelf,
                "{count}", listeners.size());
    }

    public ConfigurationSection getCommandsSection() {
        return extension.getCustomConfig("sounds-commands.yml").get().getConfigurationSection("commands");
    }
}