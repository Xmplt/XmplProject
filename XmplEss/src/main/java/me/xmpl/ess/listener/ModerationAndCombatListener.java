package me.xmpl.ess.listener;

import com.sk89q.worldguard.protection.events.DisallowedPVPEvent;
import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.command.BanCommands;
import me.xmpl.ess.model.PunishmentRecord;
import me.xmpl.ess.model.UserRecord;
import me.xmpl.ess.service.VanishAndHelperService;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.TimeUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ModerationAndCombatListener implements Listener {
    private final XmplEssExtension extension;
    private final VanishAndHelperService vanishService;

    public ModerationAndCombatListener(XmplEssExtension extension, VanishAndHelperService vanishService) {
        this.extension = extension;
        this.vanishService = vanishService;
    }

    // 1. Полное отключение стандартного спам-сообщения от WorldGuard ("Hey! Sorry, but you can't PvP here")
    // и разрешение удара палкой хелпера в ванише на спавне!
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldGuardDisallowedPvp(DisallowedPVPEvent event) {
        Player attacker = event.getAttacker();
        if (attacker != null) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (vanishService.isHelperStick(hand) && vanishService.isVanished(attacker)) {
                // Разрешаем удар палкой в ванише на уровне самого WorldGuard (без всяких сообщений!)
                event.setCancelled(true);
                return;
            }
        }
        // Для обычных ударов на спавне: отменяем стандартный WG-отказ с сообщением,
        // а сам урон тихо блокируем в onDamageByEntity (чтобы в чате не было спама от WorldGuard!)
        event.setCancelled(true);
        if (event.getCause() instanceof EntityDamageByEntityEvent) {
            ((EntityDamageByEntityEvent) event.getCause()).setCancelled(true);
        }
    }

    // 2. Проверка бана при входе на сервер
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        PunishmentRecord ban = extension.getBanStorage().getBan(name);
        if (ban == null) return;

        FileConfiguration cfg = extension.getCustomConfig("commands/bans.yml").get();
        if (ban.isExpired()) {
            extension.getBanStorage().removeBan(name);
            UserRecord uRec = extension.getUserStorage().getOrCreate(name, event.getUniqueId().toString());
            extension.getUserStorage().saveUserRecord(new UserRecord(
                    uRec.name, event.getUniqueId().toString(), System.currentTimeMillis(),
                    uRec.pendingUnbanAdmin, uRec.pendingUnbanReason, true,
                    uRec.pendingOfflineMuteNotice, uRec.pendingTakeCmdNotice
            ));
            return;
        }

        List<String> screenLines = ban.isPermanent()
                ? cfg.getStringList("screens.permban-kick")
                : cfg.getStringList("screens.tempban-kick");
        String timeStr = ban.isPermanent() ? "Навсегда" : TimeUtil.formatDuration(ban.getRemainingMillis());

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                BanCommands.buildScreen(screenLines, ban.getAdminName(), ban.getReason(), timeStr));
    }

    // 3. Уведомления при заходе в игру + восстановление ваниша из БД
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        extension.getUserStorage().markOnline(player.getUniqueId());
        vanishService.updateVisibilityForAll();

        UserRecord uRec = extension.getUserStorage().getOrCreate(player.getName(), player.getUniqueId().toString());
        FileConfiguration banCfg = extension.getCustomConfig("commands/bans.yml").get();

        boolean changed = false;
        if (uRec.pendingExpiredBanNotice) {
            ChatUtil.sendMessage(player, banCfg.getString("messages.join-expired-ban"));
            changed = true;
        }
        if (uRec.pendingUnbanAdmin != null && !uRec.pendingUnbanAdmin.isEmpty()) {
            if (uRec.pendingUnbanReason != null && !uRec.pendingUnbanReason.isEmpty()) {
                ChatUtil.sendMessage(player, banCfg.getString("messages.join-unbanned-with-reason"),
                        "{admin}", uRec.pendingUnbanAdmin, "{reason}", uRec.pendingUnbanReason);
            } else {
                ChatUtil.sendMessage(player, banCfg.getString("messages.join-unbanned-no-reason"),
                        "{admin}", uRec.pendingUnbanAdmin);
            }
            changed = true;
        }
        if (uRec.pendingOfflineMuteNotice != null && !uRec.pendingOfflineMuteNotice.isEmpty()) {
            ChatUtil.sendMessage(player, uRec.pendingOfflineMuteNotice);
            changed = true;
        }
        if (uRec.pendingTakeCmdNotice != null && !uRec.pendingTakeCmdNotice.isEmpty()) {
            ChatUtil.sendMessage(player, uRec.pendingTakeCmdNotice);
            changed = true;
        }

        if (changed || !uRec.uuid.equals(player.getUniqueId().toString())) {
            extension.getUserStorage().saveUserRecord(new UserRecord(
                    player.getName(), player.getUniqueId().toString(), System.currentTimeMillis(),
                    null, null, false, null, null
            ));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UserRecord uRec = extension.getUserStorage().getOrCreate(p.getName(), p.getUniqueId().toString());
        extension.getUserStorage().saveUserRecord(new UserRecord(
                p.getName(), p.getUniqueId().toString(), System.currentTimeMillis(),
                uRec.pendingUnbanAdmin, uRec.pendingUnbanReason,
                uRec.pendingExpiredBanNotice, uRec.pendingOfflineMuteNotice, uRec.pendingTakeCmdNotice
        ));
    }

    // 4. Блокировка чата при муте
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PunishmentRecord mute = extension.getMuteStorage().getActiveMute(player.getName());
        if (mute == null) return;

        event.setCancelled(true);
        FileConfiguration cfg = extension.getCustomConfig("commands/mutes.yml").get();
        if (mute.isPermanent()) {
            ChatUtil.sendMessage(player, cfg.getString("messages.chat-blocked-perm"),
                    "{admin}", mute.getAdminName(), "{reason}", mute.getReason());
        } else {
            ChatUtil.sendMessage(player, cfg.getString("messages.chat-blocked-temp"),
                    "{admin}", mute.getAdminName(), "{reason}", mute.getReason(),
                    "{time}", TimeUtil.formatDuration(mute.getRemainingMillis()));
        }
    }

    // 5. Жесткий перехват всех команд Essentials
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!extension.getConfig().getBoolean("intercept-essentials-commands", true)) return;

        String msg = event.getMessage().trim();
        String[] parts = msg.split(" ");
        String cmd = parts[0].toLowerCase();
        String rest = msg.length() > parts[0].length() ? msg.substring(parts[0].length()) : "";

        switch (cmd) {
            case "/ban": case "/tempban": case "/etempban": case "/eban": case "/essentials:ban": case "/essentials:tempban":
                event.setMessage("/xmpless:ban" + rest);
                break;
            case "/mute": case "/emute": case "/tempmute": case "/essentials:mute":
                event.setMessage("/xmpless:mute" + rest);
                break;
            case "/unban": case "/eunban": case "/pardon": case "/essentials:unban":
                event.setMessage("/xmpless:unban" + rest);
                break;
            case "/kick": case "/ekick": case "/essentials:kick":
                event.setMessage("/xmpless:kick" + rest);
                break;
            case "/seen": case "/eseen": case "/essentials:seen":
                event.setMessage("/xmpless:seen" + rest);
                break;
            case "/v": case "/vanish": case "/evanish": case "/ev": case "/essentials:vanish": case "/essentials:v":
                event.setMessage("/xmpless:vanish" + rest);
                break;
            case "/invsee": case "/einvsee": case "/essentials:invsee":
                event.setMessage("/xmpless:invsee" + rest);
                break;
            case "/near": case "/enear": case "/essentials:near":
                event.setMessage("/xmpless:near" + rest);
                break;
            case "/god": case "/egod": case "/essentials:god":
                event.setMessage("/xmpless:god" + rest);
                break;
            case "/list": case "/online": case "/elist": case "/essentials:list":
                event.setMessage("/xmpless:online" + rest);
                break;
        }
    }

    // 6. Логика Палки Хелпера (уведомление в чат жертве + защита от убийства на 0.5 сердца в ванише)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        boolean holdingStick = vanishService.isHelperStick(hand);

        // Если у атакующего включен Режим Бога — бить можно ТОЛЬКО палкой хелпера!
        if (vanishService.isGod(attacker) && !holdingStick) {
            event.setCancelled(true);
            String msg = extension.getCustomConfig("commands/vanish.yml").get().getString("messages.god-attack-blocked");
            ChatUtil.sendMessage(attacker, msg);
            return;
        }

        if (holdingStick) {
            if (!vanishService.canHoldStick(attacker)) {
                attacker.getInventory().setItemInMainHand(null);
                event.setCancelled(true);
                return;
            }

            boolean inVanish = vanishService.isVanished(attacker);
            if (inVanish) {
                // В ванише удар палкой проходит везде (даже на спавне)
                event.setCancelled(false);
            }

            if (event.getEntity() instanceof Player && !event.isCancelled()) {
                Player victim = (Player) event.getEntity();

                // Пишем жертве при каждом ударе палкой: "&eВас проверяет хелпер: &e&lНик&e!"
                String checkMsg = extension.getCustomConfig("commands/stickhelper.yml").get()
                        .getString("messages.victim-check-notify", "&eВас проверяет хелпер: &e&l{helper}&e!");
                ChatUtil.sendMessage(victim, checkMsg, "{helper}", attacker.getName());

                // В ВАНИШЕ палкой хелпера НЕЛЬЗЯ убить! Оставляем минимум полсердца (1.0 HP), работает только отдача!
                // Если хелпер НЕ в ванише — убить можно.
                if (inVanish) {
                    double currentHp = victim.getHealth();
                    if (currentHp <= 1.0) {
                        event.setDamage(0.0);
                    } else if (currentHp - event.getFinalDamage() < 1.0) {
                        event.setDamage(0.0);
                        victim.setHealth(1.0);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGodTakeDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && vanishService.isGod((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClickStickCheck(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (vanishService.canHoldStick(player)) return;

        ItemStack current = event.getCurrentItem();
        if (vanishService.isHelperStick(current)) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            ChatUtil.sendMessage(player, extension.getCustomConfig("commands/stickhelper.yml").get().getString("messages.illegal-removed"));
        }
    }

    @EventHandler
    public void onPickupStick(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (vanishService.canHoldStick(player)) return;

        if (vanishService.isHelperStick(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
            ChatUtil.sendMessage(player, extension.getCustomConfig("commands/stickhelper.yml").get().getString("messages.illegal-removed"));
        }
    }
}