package me.xmpl.headdrop.service;

import me.xmpl.headdrop.HeadDropExtension;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SkullSkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class HeadFactoryService {
    private final HeadDropExtension extension;

    public HeadFactoryService(HeadDropExtension extension) {
        this.extension = extension;
    }

    /**
     * Создает трофейную голову убитого игрока с его текущим скином (SkinsRestorer / Mojang)
     */
    public ItemStack buildHeadForVictim(Player victim) {
        String name = formatHeadName(victim.getName());
        List<String> lore = formatHeadLore(victim.getName());
        return SkullSkinUtil.createPlayerHead(victim, name, lore);
    }

    /**
     * Создает трофейную голову по нику (онлайн или оффлайн)
     */
    public ItemStack buildHeadByName(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOnline()) {
            return buildHeadForVictim(online);
        }
        String name = formatHeadName(playerName);
        List<String> lore = formatHeadLore(playerName);
        return SkullSkinUtil.createOfflineHead(playerName, name, lore);
    }

    /**
     * Восстанавливает предмет головы при ломании поставленного блока черепа на земле
     */
    public ItemStack buildHeadFromBrokenBlock(Block skullBlock, String ownerName) {
        String name = formatHeadName(ownerName);
        List<String> lore = formatHeadLore(ownerName);
        return SkullSkinUtil.extractHeadFromBlock(skullBlock, name, lore);
    }

    private String formatHeadName(String victimName) {
        FileConfiguration itemCfg = extension.getCustomConfig("head-item.yml").get();
        String rawName = itemCfg.getString("head.display-name", "&6Голова игрока &e{victim}");
        return ChatUtil.applyReplacements(rawName, "{victim}", victimName);
    }

    private List<String> formatHeadLore(String victimName) {
        FileConfiguration itemCfg = extension.getCustomConfig("head-item.yml").get();
        List<String> rawLore = itemCfg.getStringList("head.lore");
        List<String> result = new ArrayList<>();
        if (rawLore != null) {
            for (String line : rawLore) {
                result.add(ChatUtil.applyReplacements(line, "{victim}", victimName));
            }
        }
        return result;
    }

    /**
     * Проверяет, должен ли сработать шанс выпадения головы при данном убийстве
     */
    public boolean rollDropChance(Player killer) {
        FileConfiguration cfg = extension.getConfig();
        double chance = cfg.getDouble("settings.base-drop-chance", 100.0);

        ConfigurationSection boosts = cfg.getConfigurationSection("permissions.chance-boosts");
        if (boosts != null) {
            for (String key : boosts.getKeys(false)) {
                String perm = boosts.getString(key + ".permission");
                double c = boosts.getDouble(key + ".chance", 0.0);
                if (perm != null && killer.hasPermission(perm) && c > chance) {
                    chance = c;
                }
            }
        }

        double lootingBonus = cfg.getDouble("settings.looting-bonus-per-level", 0.0);
        if (lootingBonus > 0 && killer.getInventory().getItemInMainHand() != null) {
            int lootingLvl = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
            chance += lootingLvl * lootingBonus;
        }

        if (chance >= 100.0) return true;
        if (chance <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble(0.0, 100.0) <= chance;
    }
}