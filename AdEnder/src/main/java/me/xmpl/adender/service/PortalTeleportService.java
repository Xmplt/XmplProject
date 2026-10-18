package me.xmpl.adender.service;

import me.xmpl.adender.AdEnderExtension;
import me.xmpl.adender.model.PortalDestinationType;
import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.hook.WorldGuardHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class PortalTeleportService {
    private final AdEnderExtension extension;

    public PortalTeleportService(AdEnderExtension extension) {
        this.extension = extension;
    }

    public void attemptTeleport(Player player, PortalDestinationType type) {
        FileConfiguration mainCfg = extension.getConfig();
        FileConfiguration portalsCfg = extension.getCustomConfig("portals.yml").get();
        FileConfiguration msgCfg = extension.getCustomConfig("commands/adender.yml").get();

        String pPath = "portals." + type.name();
        String portalDisplayName = portalsCfg.getString(pPath + ".display-name", type == PortalDestinationType.AD ? "в Ад" : "в Энд");

        // 1. Проверка права (если включено в portals.yml)
        if (portalsCfg.getBoolean(pPath + ".require-permission", false)) {
            String perm = portalsCfg.getString(pPath + ".permission", "adender.use." + type.name().toLowerCase());
            if (!player.hasPermission(perm)) {
                ChatUtil.sendMessage(player, msgCfg.getString("messages.no-permission"));
                return;
            }
        }

        // 2. ЖЕСТКАЯ проверка измерения (даже для OP!): если игрок в Аду (NETHER) или Энде (THE_END) — запрещаем!
        if (mainCfg.getBoolean("restrictions.only-in-overworld", true)) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
                ChatUtil.sendMessage(player, msgCfg.getString("messages.wrong-world-dimension"),
                        "{portal}", portalDisplayName);
                return;
            }
        }

        // 3. ЖЕСТКАЯ проверка нахождения строго в регионе spawn1 (даже для OP!)
        if (mainCfg.getBoolean("restrictions.require-spawn-region", true)) {
            String requiredRegion = mainCfg.getString("restrictions.allowed-spawn-region", "spawn1");
            if (!WorldGuardHook.isLocationInRegion(player.getLocation(), requiredRegion)) {
                ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-spawn-region"),
                        "{portal}", portalDisplayName,
                        "{region}", requiredRegion);
                return;
            }
        }

        // 4. Проверяем, установлена ли точка телепортации
        Location targetLoc = extension.getLocationStorage().getLocation(type);
        if (targetLoc == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.point-not-set"),
                    "{portal}", portalDisplayName);
            return;
        }

        // 5. Проверка баланса и списание 500$ (списывает у всех, включая OP!)
        double cost = portalsCfg.getDouble(pPath + ".cost", 500.0);
        long roundedCost = Math.round(cost);
        String costStr = NumberDateUtil.formatInteger(roundedCost);

        if (cost > 0.0) {
            if (!VaultHook.hasMoney(player, cost)) {
                long currentBal = Math.round(VaultHook.getBalance(player));
                ChatUtil.sendMessage(player, msgCfg.getString("messages.not-enough-money"),
                        "{portal}", portalDisplayName,
                        "{cost}", costStr,
                        "{balance}", NumberDateUtil.formatInteger(currentBal));
                return;
            }
            if (!VaultHook.withdrawMoney(player, cost)) {
                return;
            }
        }

        // 6. Телепортируем игрока со взглядом (yaw/pitch)
        player.teleport(targetLoc);
        extension.getStatsStorage().recordTeleport(player.getName(), type);

        String sound = portalsCfg.getString(pPath + ".sound", "ENTITY_ENDERMEN_TELEPORT");
        String particle = portalsCfg.getString(pPath + ".particle", "PORTAL");
        SoundParticleUtil.playSoundInRadius(targetLoc, sound, 5.0, 1.0f, 1.0f);
        SoundParticleUtil.spawnParticles(targetLoc, particle, 25, 0.4, 0.8, 0.4, 0.05);

        // 7. Полностью ярко-зеленое сообщение об успехе
        ChatUtil.sendMessage(player, msgCfg.getString("messages.teleport-success"),
                "{portal}", portalDisplayName,
                "{cost}", costStr);
    }
}