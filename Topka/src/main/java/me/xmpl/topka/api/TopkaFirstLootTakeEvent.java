package me.xmpl.topka.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда игрок ПЕРВЫМ забирает предмет из сундука TOPKA в текущем раунде.
 * Будущее дополнение на Кланы сможет слушать этот ивент и начислять клановые очки!
 */
public class TopkaFirstLootTakeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location chestLocation;
    private final int clanPoints;

    public TopkaFirstLootTakeEvent(Player player, Location chestLocation, int clanPoints) {
        this.player = player;
        this.chestLocation = chestLocation;
        this.clanPoints = clanPoints;
    }

    public Player getPlayer() { return player; }
    public Location getChestLocation() { return chestLocation; }
    public int getClanPoints() { return clanPoints; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}