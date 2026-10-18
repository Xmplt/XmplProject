package me.xmpl.airdrop.api;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается за 5 минут до появления сундука (содержит уже найденную точную локацию и запреты),
 * чтобы плагин на Платную подписку мог работать с этими данными!
 */
public class AirDropPreSpawnWarningEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Location upcomingLocation;
    private final boolean elytraRestricted;
    private final boolean pearlRestricted;
    private final boolean chorusRestricted;

    public AirDropPreSpawnWarningEvent(Location upcomingLocation, boolean elytraRestricted, boolean pearlRestricted, boolean chorusRestricted) {
        this.upcomingLocation = upcomingLocation;
        this.elytraRestricted = elytraRestricted;
        this.pearlRestricted = pearlRestricted;
        this.chorusRestricted = chorusRestricted;
    }

    public Location getUpcomingLocation() { return upcomingLocation; }
    public boolean isElytraRestricted() { return elytraRestricted; }
    public boolean isPearlRestricted() { return pearlRestricted; }
    public boolean isChorusRestricted() { return chorusRestricted; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}