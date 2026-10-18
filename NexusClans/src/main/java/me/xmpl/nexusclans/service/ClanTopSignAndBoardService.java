package me.xmpl.nexusclans.service;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.ScoreboardUtil;
import me.xmpl.lib.util.SignBlockUtil;
import me.xmpl.lib.util.SkullSkinUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClanTopSignAndBoardService {
    private static final String BOARD_OWNER = "me.xmpl.nexusclans";

    public static class TopClansHopperHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final NexusClansExtension extension;
    private final Map<Integer, Location> topSigns = new ConcurrentHashMap<>();
    private final Map<String, CuboidBox> boardRegions = new ConcurrentHashMap<>();

    public ClanTopSignAndBoardService(NexusClansExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        topSigns.clear();
        boardRegions.clear();

        FileConfiguration cfg = extension.getCustomConfig("top-signs-and-board.yml").get();
        ConfigurationSection signsSec = cfg.getConfigurationSection("signs");
        if (signsSec != null) {
            for (String rankKey : signsSec.getKeys(false)) {
                try {
                    int rank = Integer.parseInt(rankKey);
                    World w = Bukkit.getWorld(signsSec.getString(rankKey + ".world", "world"));
                    if (w != null) {
                        Location loc = new Location(w,
                                signsSec.getInt(rankKey + ".x"),
                                signsSec.getInt(rankKey + ".y"),
                                signsSec.getInt(rankKey + ".z"));
                        topSigns.put(rank, loc);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        ConfigurationSection rgSec = cfg.getConfigurationSection("regions");
        if (rgSec != null) {
            for (String key : rgSec.getKeys(false)) {
                String world = rgSec.getString(key + ".world", "world");
                CuboidBox box = new CuboidBox(key, world,
                        rgSec.getInt(key + ".minX"), rgSec.getInt(key + ".minY"), rgSec.getInt(key + ".minZ"),
                        rgSec.getInt(key + ".maxX"), rgSec.getInt(key + ".maxY"), rgSec.getInt(key + ".maxZ"));
                boardRegions.put(key.toLowerCase(), box);
            }
        }
    }

    public void startTasks() {
        long signPeriodTicks = Math.max(200L, extension.getCustomConfig("top-signs-and-board.yml").get()
                .getLong("signs-and-hopper.update-interval-seconds", 300L) * 20L);
        extension.runTaskTimer(this::updateAllTopSigns, 40L, signPeriodTicks);

        long sbPeriodTicks = Math.max(20L, extension.getCustomConfig("top-signs-and-board.yml").get()
                .getLong("scoreboard.update-interval-ticks", 40L));
        extension.runTaskTimer(this::tickScoreboards, 20L, sbPeriodTicks);
    }

    public void setTopSign(int rank, Location loc) {
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        topSigns.put(rank, blockLoc);

        CustomConfig cc = extension.getCustomConfig("top-signs-and-board.yml");
        String path = "signs." + rank;
        cc.get().set(path + ".world", blockLoc.getWorld().getName());
        cc.get().set(path + ".x", blockLoc.getBlockX());
        cc.get().set(path + ".y", blockLoc.getBlockY());
        cc.get().set(path + ".z", blockLoc.getBlockZ());
        cc.save();

        updateAllTopSigns();
    }

    public boolean isTopSign(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Location sLoc : topSigns.values()) {
            if (sLoc.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())
                    && sLoc.getBlockX() == loc.getBlockX()
                    && sLoc.getBlockY() == loc.getBlockY()
                    && sLoc.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    public void updateAllTopSigns() {
        if (!extension.getModuleService().isTopSignsEnabled()) return;
        FileConfiguration cfg = extension.getCustomConfig("top-signs-and-board.yml").get();
        List<ClanData> top = extension.getMainStorage().getSortedTopClans();

        for (Map.Entry<Integer, Location> entry : topSigns.entrySet()) {
            int rank = entry.getKey();
            Location loc = entry.getValue();
            int idx = rank - 1;

            if (idx >= 0 && idx < top.size()) {
                ClanData c = top.get(idx);
                String l1 = fmt(cfg.getString("signs-and-hopper.active-sign.line-1", "{tag}"), rank, c);
                String l2 = fmt(cfg.getString("signs-and-hopper.active-sign.line-2", "&0{leader}"), rank, c);
                String l3 = fmt(cfg.getString("signs-and-hopper.active-sign.line-3", "&0Репутация клана:"), rank, c);
                String l4 = fmt(cfg.getString("signs-and-hopper.active-sign.line-4", "&0{points}"), rank, c);
                SignBlockUtil.updateSignLines(loc, l1, l2, l3, l4);
            } else {
                String l1 = cfg.getString("signs-and-hopper.empty-sign.line-1", "&7Пусто #{rank}").replace("{rank}", String.valueOf(rank));
                String l2 = cfg.getString("signs-and-hopper.empty-sign.line-2", "&0-");
                String l3 = cfg.getString("signs-and-hopper.empty-sign.line-3", "&0Репутация клана:");
                String l4 = cfg.getString("signs-and-hopper.empty-sign.line-4", "&00");
                SignBlockUtil.updateSignLines(loc, l1, l2, l3, l4);
            }
        }
    }

    private String fmt(String raw, int rank, ClanData c) {
        return ChatUtil.applyReplacements(raw,
                "{rank}", rank,
                "{tag}", c.getRawColoredTag(),
                "{leader}", c.getLeaderName(),
                "{points}", c.getTotalPoints());
    }

    public void openTopHopperGui(Player viewer) {
        if (!extension.getModuleService().isTopSignsEnabled()) return;
        FileConfiguration cfg = extension.getCustomConfig("top-signs-and-board.yml").get();
        String title = ChatUtil.color(cfg.getString("signs-and-hopper.hopper-title", "&8Топ-3 клана сервера"));

        Inventory gui = Bukkit.createInventory(new TopClansHopperHolder(), InventoryType.HOPPER, title);
        List<ClanData> top = extension.getMainStorage().getSortedTopClans();
        List<Integer> slots = cfg.getIntegerList("signs-and-hopper.hopper-slots");
        if (slots == null || slots.size() < 3) {
            slots = java.util.Arrays.asList(1, 2, 3);
        }

        for (int i = 0; i < 3; i++) {
            int rank = i + 1;
            int targetSlot = slots.get(i);
            if (i < top.size()) {
                ClanData c = top.get(i);
                String name = fmt(cfg.getString("signs-and-hopper.head-item.display-name", "&6&lТоп-#{rank} &f{tag}"), rank, c);
                List<String> lore = new ArrayList<>();
                for (String l : cfg.getStringList("signs-and-hopper.head-item.lore")) {
                    lore.add(fmt(l, rank, c));
                }
                Player onlineLeader = Bukkit.getPlayerExact(c.getLeaderName());
                ItemStack head = (onlineLeader != null && onlineLeader.isOnline())
                        ? SkullSkinUtil.createPlayerHead(onlineLeader, name, lore)
                        : SkullSkinUtil.createOfflineHead(c.getLeaderName(), name, lore);
                gui.setItem(targetSlot, head);
            } else {
                String emptyName = "&7Топ-#" + rank + " Пусто";
                gui.setItem(targetSlot, SkullSkinUtil.createOfflineHead("MHF_Question", emptyName,
                        Collections.singletonList("&7Репутация клана: &00")));
            }
        }

        viewer.openInventory(gui);
    }

    public void saveBoardRegion(CuboidBox box) {
        boardRegions.put(box.getName().toLowerCase(), box);
        CustomConfig cc = extension.getCustomConfig("top-signs-and-board.yml");
        String path = "regions." + box.getName();
        cc.get().set(path + ".world", box.getWorldName());
        cc.get().set(path + ".minX", box.getMinX());
        cc.get().set(path + ".minY", box.getMinY());
        cc.get().set(path + ".minZ", box.getMinZ());
        cc.get().set(path + ".maxX", box.getMaxX());
        cc.get().set(path + ".maxY", box.getMaxY());
        cc.get().set(path + ".maxZ", box.getMaxZ());
        cc.save();
    }

    public boolean removeBoardRegion(String name) {
        CuboidBox removed = boardRegions.remove(name.toLowerCase());
        if (removed == null) return false;
        CustomConfig cc = extension.getCustomConfig("top-signs-and-board.yml");
        cc.get().set("regions." + removed.getName(), null);
        cc.save();
        return true;
    }

    public boolean isInAnyBoardRegion(Location loc) {
        if (loc == null) return false;
        for (CuboidBox box : boardRegions.values()) {
            if (box.contains(loc)) return true;
        }
        return false;
    }

    private void tickScoreboards() {
        FileConfiguration cfg = extension.getCustomConfig("top-signs-and-board.yml").get();
        boolean enabled = extension.getModuleService().isTopScoreboardEnabled()
                && cfg.getBoolean("scoreboard.enabled", true);

        List<ClanData> top = enabled ? extension.getMainStorage().getSortedTopClans() : Collections.emptyList();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled || !isInAnyBoardRegion(player.getLocation())) {
                if (ScoreboardUtil.hasCustomBoardActive(player, BOARD_OWNER)) {
                    ScoreboardUtil.restorePreviousBoard(player, BOARD_OWNER);
                }
                continue;
            }

            String title = cfg.getString("scoreboard.title", "&6&lТОП 10 КЛАНОВ");
            List<String> lines = new ArrayList<>(cfg.getStringList("scoreboard.header-lines"));
            String entryFmt = cfg.getString("scoreboard.entry-format", "&e#{rank} &f{tag} &8- &a{points}");
            String emptyFmt = cfg.getString("scoreboard.empty-entry-format", "&e#{rank} &7Пусто &8- &70");

            for (int i = 0; i < 10; i++) {
                int rank = i + 1;
                if (i < top.size()) {
                    ClanData c = top.get(i);
                    lines.add(fmt(entryFmt, rank, c));
                } else {
                    lines.add(emptyFmt.replace("{rank}", String.valueOf(rank)));
                }
            }

            lines.addAll(cfg.getStringList("scoreboard.footer-lines"));
            ScoreboardUtil.showCustomSidebar(player, BOARD_OWNER, title, lines);
        }
    }

    public void restoreAllBoards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ScoreboardUtil.hasCustomBoardActive(p, BOARD_OWNER)) {
                ScoreboardUtil.restorePreviousBoard(p, BOARD_OWNER);
            }
        }
    }

    public Collection<CuboidBox> getBoardRegions() {
        return Collections.unmodifiableCollection(boardRegions.values());
    }
}