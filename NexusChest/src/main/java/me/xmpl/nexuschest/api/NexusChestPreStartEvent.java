package me.xmpl.nexuschest.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается заранее перед стартом NexusChest (например за 60 сек),
 * чтобы плагин на Платную подписку мог заранее отправить 4-значный код подписчикам!
 */
public class NexusChestPreStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String upcomingPinCode;
    private final int secondsUntilStart;

    public NexusChestPreStartEvent(String upcomingPinCode, int secondsUntilStart) {
        this.upcomingPinCode = upcomingPinCode;
        this.secondsUntilStart = secondsUntilStart;
    }

    public String getUpcomingPinCode() { return upcomingPinCode; }
    public int getSecondsUntilStart() { return secondsUntilStart; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}