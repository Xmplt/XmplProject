package me.xmpl.fishing.listener;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.fishing.service.FishGeneratorService;
import me.xmpl.lib.util.ItemNbtUtil;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingMechanicListener implements Listener {
    private final FishingSystemExtension extension;

    public FishingMechanicListener(FishingSystemExtension extension) {
        this.extension = extension;
    }

    // 1. Ловля рыбы удочкой в любом месте карты
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCatchFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Item caughtEntity = (Item) event.getCaught();
        ItemStack rawStack = caughtEntity.getItemStack();
        if (rawStack == null || rawStack.getType() != Material.RAW_FISH) return;

        Player angler = event.getPlayer();

        // Проверяем, находится ли игрок ИЛИ поплавок удочки в бонусном регионе x2
        boolean inBonusRegion = extension.getRegionStorage().isInBonusRegion(angler.getLocation())
                || (event.getHook() != null && extension.getRegionStorage().isInBonusRegion(event.getHook().getLocation()));

        ItemStack customFish = extension.getGeneratorService().transformCaughtFish(angler, rawStack, inBonusRegion);
        if (customFish != null) {
            caughtEntity.setItemStack(customFish);
        }
    }

    // 2. Если пожарить нашу рыбу в печке — все наши теги и лор пропадают (выходит чистая ванильная жареная рыба)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFishSmelt(FurnaceSmeltEvent event) {
        if (!extension.getConfig().getBoolean("settings.strip-tags-on-smelt", true)) return;

        ItemStack source = event.getSource();
        if (source == null || source.getType() != Material.RAW_FISH) return;

        double weight = ItemNbtUtil.getDouble(source, FishGeneratorService.NBT_WEIGHT, -1.0);
        if (weight > 0.0 && event.getResult() != null) {
            // Создаем абсолютно чистый ванильный предмет жареной рыбы без метаданных и тегов
            ItemStack cleanCooked = new ItemStack(
                    event.getResult().getType(),
                    event.getResult().getAmount(),
                    event.getResult().getDurability()
            );
            event.setResult(cleanCooked);
        }
    }

    // 3. Защита от переименования или размножения тегов на наковальне
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!extension.getConfig().getBoolean("settings.prevent-anvil-rename", true)) return;
        ItemStack first = event.getInventory().getItem(0);
        if (first != null && ItemNbtUtil.getDouble(first, FishGeneratorService.NBT_WEIGHT, -1.0) > 0.0) {
            event.setResult(null);
        }
    }
}