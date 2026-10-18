package me.xmpl.airdrop.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда игрок ПЕРВЫМ кликает ПКМ по Сундуку по координатам и активирует его!
 * Твой будущий плагин на Кланы сможет слушать этот ивент и начислять клану очки!
 */
public class AirDropFirstActivateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player activator;
    private final Location chestLocation;

    public AirDropFirstActivateEvent(Player activator, Location chestLocation) {
        this.activator = activator;
        this.chestLocation = chestLocation;
    }

    public Player getActivator() { return activator; }
    public Location getChestLocation() { return chestLocation; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}