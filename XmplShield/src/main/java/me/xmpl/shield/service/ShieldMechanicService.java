package me.xmpl.shield.service;

import me.xmpl.shield.XmplShieldExtension;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ShieldMechanicService {
    private final XmplShieldExtension extension;

    public ShieldMechanicService(XmplShieldExtension extension) {
        this.extension = extension;
    }

    public boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        List<String> axes = extension.getConfig().getStringList("mechanics.axe-materials");
        String typeName = item.getType().name();
        for (String allowed : axes) {
            if (typeName.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return typeName.endsWith("_AXE");
    }

    public boolean isActivelyBlockingWithShield(Player player) {
        if (player == null || !player.isOnline()) return false;
        // Проверяем, что щит не находится уже в кулдауне
        if (player.getCooldown(Material.SHIELD) > 0) return false;

        boolean hasShieldInHand = isShield(player.getInventory().getItemInMainHand())
                || isShield(player.getInventory().getItemInOffHand());
        if (!hasShieldInHand) return false;

        // В 1.12.2 isBlocking() или isHandRaised() показывает, что игрок зажал ПКМ со щитом
        return player.isBlocking() || player.isHandRaised();
    }

    private boolean isShield(ItemStack item) {
        return item != null && item.getType() == Material.SHIELD;
    }

    /**
     * Принудительно уводит щит в кулдаун, сбивает стойку блока и проигрывает пакетную анимацию SHIELD_BREAK (EntityEffect)
     */
    public void applyShieldDisableState(Player victim) {
        int cooldownTicks = extension.getConfig().getInt("mechanics.shield-cooldown-ticks", 100);
        victim.setCooldown(Material.SHIELD, cooldownTicks);
        victim.playEffect(EntityEffect.SHIELD_BREAK);

        // Временно переключаем слот на 1 тик или обновляем инвентарь, чтобы клиент мгновенно опустил пробитый щит
        ItemStack main = victim.getInventory().getItemInMainHand();
        ItemStack off = victim.getInventory().getItemInOffHand();

        if (isShield(off)) {
            victim.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            extension.runTask(() -> {
                if (victim.isOnline()) {
                    victim.getInventory().setItemInOffHand(off);
                    victim.updateInventory();
                }
            });
        } else if (isShield(main)) {
            victim.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            extension.runTask(() -> {
                if (victim.isOnline()) {
                    victim.getInventory().setItemInMainHand(main);
                    victim.updateInventory();
                }
            });
        }
    }
}