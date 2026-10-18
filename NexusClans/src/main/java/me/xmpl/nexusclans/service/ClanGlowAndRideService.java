package me.xmpl.nexusclans.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.PacketTeamGlowManager;
import me.xmpl.lib.util.SoundParticleUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ClanGlowAndRideService {
    private final NexusClansExtension extension;

    public ClanGlowAndRideService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public void startGlowService() {
        PacketTeamGlowManager.registerRelationResolver(this::resolveGlowColorForPair);

        long period = Math.max(20L, extension.getCustomConfig("glow-and-ride.yml").get()
                .getLong("glowing.refresh-interval-ticks", 40L));
        extension.runTaskTimer(this::refreshAllOnlineGlows, 20L, period);
    }

    public void stopGlowService() {
        PacketTeamGlowManager.unregisterRelationResolver();
    }

    private ChatColor parseConfiguredColor(String path, ChatColor fallback) {
        String raw = extension.getCustomConfig("glow-and-ride.yml").get().getString(path, fallback.name());
        try {
            return ChatColor.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    public ChatColor resolveGlowColorForPair(Player viewer, Player target) {
        if (!extension.getModuleService().isGlowingEnabled()) return null;
        if (viewer == null || target == null || viewer.equals(target)) return null;
        if (target.hasMetadata("xmpl_vanished")) return null;

        ClanData viewerClan = extension.getMainStorage().getPlayerClan(viewer.getName());
        ClanData targetClan = extension.getMainStorage().getPlayerClan(target.getName());
        if (viewerClan == null || targetClan == null) return null;

        ClanMember viewerMem = viewerClan.getMember(viewer.getName());
        if (viewerMem == null) return null;

        // 1. Соклановец -> GREEN (или цвет из glow-and-ride.yml)
        if (viewerClan.getClanKey().equalsIgnoreCase(targetClan.getClanKey())) {
            return viewerMem.isOwnClanGlowEnabled()
                    ? parseConfiguredColor("glowing.clanmate-color", ChatColor.GREEN)
                    : null;
        }

        // 2. Союзник -> YELLOW (или цвет из glow-and-ride.yml)
        if (extension.getAlliancesStorage().areAllies(viewerClan.getClanKey(), targetClan.getClanKey())) {
            return viewerMem.isAllyClanGlowEnabled()
                    ? parseConfiguredColor("glowing.ally-color", ChatColor.YELLOW)
                    : null;
        }

        return null;
    }

    public void setPersonalClanGlow(Player player, boolean enabled) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanMember mem = extension.getMainStorage().getMember(player.getName());
        if (mem == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        mem.setOwnClanGlowEnabled(enabled);
        extension.getMainStorage().saveMemberAsync(mem);
        PacketTeamGlowManager.refreshViewerGlows(player);
        ChatUtil.sendMessage(player, msgCfg.getString(enabled ? "messages.glow-self-on" : "messages.glow-self-off"));
    }

    public void setPersonalAllyGlow(Player player, boolean enabled) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanMember mem = extension.getMainStorage().getMember(player.getName());
        if (mem == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        mem.setAllyClanGlowEnabled(enabled);
        extension.getMainStorage().saveMemberAsync(mem);
        PacketTeamGlowManager.refreshViewerGlows(player);
        ChatUtil.sendMessage(player, msgCfg.getString(enabled ? "messages.teamglow-self-on" : "messages.teamglow-self-off"));
    }

    public void refreshPlayerGlow(Player player) {
        PacketTeamGlowManager.refreshAllForPlayer(player);
    }

    public void refreshAllOnlineGlows() {
        if (!extension.getModuleService().isGlowingEnabled()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PacketTeamGlowManager.refreshViewerGlows(viewer);
        }
    }

    public boolean canRideTogether(Player a, Player b) {
        if (!extension.getModuleService().isHeadRidingEnabled()) return false;
        if (a == null || b == null || a.equals(b)) return false;

        ClanData clanA = extension.getMainStorage().getPlayerClan(a.getName());
        ClanData clanB = extension.getMainStorage().getPlayerClan(b.getName());
        if (clanA == null || clanB == null) return false;

        if (clanA.getClanKey().equalsIgnoreCase(clanB.getClanKey())) {
            return clanA.isSitAllowed();
        }
        if (extension.getAlliancesStorage().areAllies(clanA.getClanKey(), clanB.getClanKey())) {
            return extension.getAlliancesStorage().isEffectiveAllySitAllowed(clanA.getClanKey(), clanB.getClanKey());
        }
        return false;
    }

    public boolean handlePlayerInteractEntity(Player clicker, Player clicked) {
        if (!canRideTogether(clicker, clicked)) return false;

        if (clicker.isSneaking()) {
            if (tryThrowPassengerFromHead(clicker)) {
                return true;
            }
            if (clicker.getPassengers().isEmpty() && clicked.getVehicle() == null && !clicked.getPassengers().contains(clicker)) {
                clicker.addPassenger(clicked);
                String sound = extension.getCustomConfig("glow-and-ride.yml").get()
                        .getString("head-riding.mount-sound", "ENTITY_HORSE_SADDLE");
                SoundParticleUtil.playSoundInRadius(clicker.getLocation(), sound, 10.0, 1.0f, 1.1f);
                return true;
            }
        } else {
            if (clicked.getPassengers().isEmpty() && clicker.getVehicle() == null && !clicker.getPassengers().contains(clicked)) {
                clicked.addPassenger(clicker);
                String sound = extension.getCustomConfig("glow-and-ride.yml").get()
                        .getString("head-riding.mount-sound", "ENTITY_HORSE_SADDLE");
                SoundParticleUtil.playSoundInRadius(clicked.getLocation(), sound, 10.0, 1.0f, 1.1f);
                return true;
            }
        }
        return false;
    }

    public boolean tryThrowPassengerFromHead(Player carrier) {
        if (!extension.getModuleService().isHeadRidingEnabled()) return false;
        if (!carrier.isSneaking() || carrier.getPassengers().isEmpty()) return false;

        Entity firstPassenger = carrier.getPassengers().get(0);
        if (!(firstPassenger instanceof Player)) return false;

        Player passenger = (Player) firstPassenger;
        carrier.eject();

        FileConfiguration cfg = extension.getCustomConfig("glow-and-ride.yml").get();
        double hSpeed = cfg.getDouble("head-riding.throw-horizontal-speed", 1.45);
        double vBoost = cfg.getDouble("head-riding.throw-vertical-boost", 0.55);
        String sound = cfg.getString("head-riding.throw-sound", "ENTITY_SNOWBALL_THROW");

        Vector dir = carrier.getEyeLocation().getDirection().normalize().multiply(hSpeed);
        dir.setY(Math.max(0.35, dir.getY() + vBoost));

        extension.runTask(() -> {
            if (passenger.isOnline()) {
                passenger.setVelocity(dir);
            }
        });

        SoundParticleUtil.playSoundInRadius(carrier.getLocation(), sound, 14.0, 1.2f, 0.9f);
        return true;
    }
}