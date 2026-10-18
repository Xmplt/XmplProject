package me.xmpl.nexuschest.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Вызывается, когда игрок забирает САМЫЙ ПЕРВЫЙ предмет из сундука NexusChest в текущем раунде.
 * Будущий плагин на Кланы сможет слушать этот ивент и выдавать клану большое количество очков!
 */
public class NexusChestFirstLootEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int chestNumber;
    private final ItemStack lootedItem;

    public NexusChestFirstLootEvent(Player player, int chestNumber, ItemStack lootedItem) {
        this.player = player;
        this.chestNumber = chestNumber;
        this.lootedItem = lootedItem;
    }

    public Player getPlayer() { return player; }
    public int getChestNumber() { return chestNumber; }
    public ItemStack getLootedItem() { return lootedItem; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}