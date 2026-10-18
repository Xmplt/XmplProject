package me.xmpl.nexusclans.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemNbtUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClanVirtualChestService {
    public static final String NBT_DEPOSITOR = "xmpl_clan_depositor";

    public static class ClanChestHolder implements InventoryHolder {
        public final String clanKey;
        private Inventory inventory;

        public ClanChestHolder(String clanKey) {
            this.clanKey = clanKey.toLowerCase();
        }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final NexusClansExtension extension;
    private final Map<String, Inventory> liveChestInventories = new ConcurrentHashMap<>();

    public ClanVirtualChestService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public void openClanChest(Player player) {
        if (!extension.getModuleService().isVirtualChestEnabled()) {
            ChatUtil.sendMessage(player, "&cМодуль кланового сундука сейчас отключен!");
            return;
        }
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, extension.getClanCoreService().getMsgCfg().getString("messages.not-in-clan"));
            return;
        }

        Inventory inv = liveChestInventories.computeIfAbsent(clan.getClanKey(), k -> createAndLoadInventory(clan));
        player.openInventory(inv);
    }

    private Inventory createAndLoadInventory(ClanData clan) {
        String rawTitle = extension.getCustomConfig("chest-and-home.yml").get()
                .getString("virtual-chest.title", "&8Сундук клана: &0{clan}")
                .replace("{clan}", ChatUtil.color(clan.getRawColoredTag()));

        ClanChestHolder holder = new ClanChestHolder(clan.getClanKey());
        Inventory inv = Bukkit.createInventory(holder, 54, ChatUtil.color(rawTitle));
        holder.setInventory(inv);

        YamlConfiguration yaml = extension.getChestStorage().loadChestYaml(clan.getClanKey());
        ConfigurationSection sec = yaml.getConfigurationSection("slots");
        if (sec != null) {
            for (String slotStr : sec.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotStr);
                    if (slot >= 0 && slot < 54) {
                        ItemStack cleanItem = sec.getItemStack(slotStr + ".item");
                        String depositor = sec.getString(slotStr + ".by");
                        if (cleanItem != null && cleanItem.getType() != Material.AIR) {
                            inv.setItem(slot, applyDepositorTagAndLore(cleanItem.clone(), depositor));
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return inv;
    }

    private String getColoredLorePrefix() {
        String raw = extension.getCustomConfig("chest-and-home.yml").get()
                .getString("virtual-chest.depositor-lore-prefix", "&8▪ &7Положил в сундук: &e");
        return ChatUtil.color(raw);
    }

    public ItemStack applyDepositorTagAndLore(ItemStack item, String depositorName) {
        if (item == null || item.getType() == Material.AIR || depositorName == null || depositorName.isEmpty()) {
            return item;
        }
        ItemStack cleaned = stripDepositorTagAndLore(item.clone());
        String prefix = getColoredLorePrefix();

        ItemMeta meta = cleaned.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(prefix + depositorName);
            meta.setLore(lore);
            cleaned.setItemMeta(meta);
        }
        return ItemNbtUtil.setString(cleaned, NBT_DEPOSITOR, depositorName);
    }

    public ItemStack stripDepositorTagAndLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemStack clone = item.clone();
        String prefix = getColoredLorePrefix();

        if (clone.hasItemMeta()) {
            ItemMeta meta = clone.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.removeIf(line -> line.startsWith(prefix));
                meta.setLore(lore.isEmpty() ? null : lore);
                clone.setItemMeta(meta);
            }
        }
        return ItemNbtUtil.setString(clone, NBT_DEPOSITOR, "");
    }

    public void syncAndSaveChestAfterClick(Inventory chestInv, String clanKey, Player actor) {
        ItemStack[] cleanItems = new ItemStack[54];
        String[] depositors = new String[54];
        String prefix = getColoredLorePrefix();

        for (int i = 0; i < 54; i++) {
            ItemStack stack = chestInv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;

            String existingDep = ItemNbtUtil.getString(stack, NBT_DEPOSITOR);
            if (existingDep == null || existingDep.isEmpty()) {
                existingDep = extractDepositorFromLore(stack, prefix);
            }
            if (existingDep == null || existingDep.isEmpty()) {
                existingDep = actor.getName();
                stack = applyDepositorTagAndLore(stack, existingDep);
                chestInv.setItem(i, stack);
            }

            cleanItems[i] = stripDepositorTagAndLore(stack);
            depositors[i] = existingDep;
        }

        if (actor.isOnline()) {
            ItemStack cursor = actor.getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR && hasDepositorLore(cursor, prefix)) {
                actor.setItemOnCursor(stripDepositorTagAndLore(cursor));
            }
            ItemStack[] playerContents = actor.getInventory().getStorageContents();
            boolean changed = false;
            for (int i = 0; i < playerContents.length; i++) {
                if (playerContents[i] != null && hasDepositorLore(playerContents[i], prefix)) {
                    playerContents[i] = stripDepositorTagAndLore(playerContents[i]);
                    changed = true;
                }
            }
            if (changed) {
                actor.getInventory().setStorageContents(playerContents);
            }
        }

        extension.getChestStorage().saveChestYamlAsync(clanKey, cleanItems, depositors);
    }

    private boolean hasDepositorLore(ItemStack item, String prefix) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith(prefix)) return true;
        }
        return false;
    }

    private String extractDepositorFromLore(ItemStack item, String prefix) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return null;
    }

    public void closeAndRemoveClanChest(String clanKey) {
        Inventory inv = liveChestInventories.remove(clanKey.toLowerCase());
        if (inv != null) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    public void saveAndCloseAllOnDisable() {
        for (Inventory inv : liveChestInventories.values()) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
            }
        }
        liveChestInventories.clear();
    }
}