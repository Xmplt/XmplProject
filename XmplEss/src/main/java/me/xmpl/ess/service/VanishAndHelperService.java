package me.xmpl.ess.service;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.storage.VanishStorage;
import me.xmpl.lib.util.ActionBarUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class VanishAndHelperService {
    private final XmplEssExtension extension;
    private final VanishStorage storage;

    public VanishAndHelperService(XmplEssExtension extension, VanishStorage storage) {
        this.extension = extension;
        this.storage = storage;
    }

    public void startActionBarTask() {
        // Каждые 10 тиков (полсекунды) обновляем надпись над хотбаром, чтобы она никогда не тускнела и не пропадала!
        extension.runTaskTimer(() -> {
            String text = extension.getCustomConfig("commands/vanish.yml").get().getString("actionbar-vanish", "&a&lВы в невидимости!");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (isVanished(online)) {
                    ActionBarUtil.sendActionBar(online, text);
                }
            }
        }, 10L, 10L);
    }

    public int getVanishLevel(Player player) {
        if (player.isOp()) return Integer.MAX_VALUE;
        String prefix = extension.getCustomConfig("commands/vanish.yml").get()
                .getString("permissions.level-prefix", "xmpless.vanish.level.").toLowerCase();

        int maxLevel = 1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase();
            if (perm.startsWith(prefix)) {
                try {
                    int lvl = Integer.parseInt(perm.substring(prefix.length()));
                    if (lvl > maxLevel) maxLevel = lvl;
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxLevel;
    }

    public boolean canSee(Player viewer, Player target) {
        if (!isVanished(target)) return true;
        if (viewer.equals(target)) return true;

        String usePerm = extension.getCustomConfig("commands/vanish.yml").get().getString("permissions.vanish-use", "xmpless.vanish.use");
        if (!viewer.hasPermission(usePerm)) return false;

        return getVanishLevel(viewer) >= getVanishLevel(target);
    }

    public void updateVisibilityForAll() {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (isVanished(target)) {
                target.setMetadata("xmpl_vanished", new FixedMetadataValue(extension.getCore(), true));
                // Убираем эффект зелья невидимости, если его раньше выдал Essentials!
                if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    target.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            } else {
                target.removeMetadata("xmpl_vanished", extension.getCore());
            }

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                if (canSee(viewer, target)) {
                    viewer.showPlayer(extension.getCore(), target);
                } else {
                    viewer.hidePlayer(extension.getCore(), target);
                }
            }
        }
    }

    public boolean toggleVanish(Player player) {
        boolean next = !isVanished(player);
        storage.setVanished(player.getUniqueId(), next);
        updateVisibilityForAll();

        if (next) {
            String text = extension.getCustomConfig("commands/vanish.yml").get().getString("actionbar-vanish", "&a&lВы в невидимости!");
            ActionBarUtil.sendActionBar(player, text);
        } else {
            ActionBarUtil.sendActionBar(player, "");
        }
        return next;
    }

    public boolean toggleGod(Player player) {
        boolean next = !isGod(player);
        storage.setGod(player.getUniqueId(), next);
        return next;
    }

    public boolean isVanished(Player player) {
        return player != null && storage.isVanished(player.getUniqueId());
    }

    public boolean isGod(Player player) {
        return player != null && storage.isGod(player.getUniqueId());
    }

    public ItemStack createHelperStick(String targetName, String issuerName) {
        FileConfiguration cfg = extension.getCustomConfig("commands/stickhelper.yml").get();
        String name = ChatUtil.applyReplacements(cfg.getString("item.name", "&6Палка хелпера: &e{target}"),
                "{target}", targetName, "{issuer}", issuerName);

        List<String> lore = new ArrayList<>();
        for (String line : cfg.getStringList("item.lore")) {
            lore.add(ChatUtil.applyReplacements(line, "{target}", targetName, "{issuer}", issuerName));
        }

        int kbLevel = cfg.getInt("item.knockback-level", 5);
        return new ItemBuilder(Material.STICK)
                .setName(name)
                .setLore(lore)
                .addEnchant(Enchantment.KNOCKBACK, kbLevel)
                .build();
    }

    public boolean isHelperStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String rawPrefix = ChatUtil.color(extension.getCustomConfig("commands/stickhelper.yml").get()
                .getString("item.name", "&6Палка хелпера:")).split("\\{")[0];
        return item.getItemMeta().getDisplayName().startsWith(rawPrefix);
    }

    public boolean canHoldStick(Player player) {
        String perm = extension.getCustomConfig("commands/stickhelper.yml").get()
                .getString("permissions.hold-and-self", "xmpless.stickhelper");
        return player.hasPermission(perm);
    }
}