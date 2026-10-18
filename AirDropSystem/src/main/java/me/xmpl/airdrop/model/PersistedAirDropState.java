package me.xmpl.airdrop.model;

import org.bukkit.Location;

public class PersistedAirDropState {
    public AirDropStage stage = AirDropStage.IDLE;
    public int timerSeconds = 3600;
    public String worldKey = null;
    public Location chestLocation = null;
    public String wgRegionId = null;
    public boolean restrictElytra = false;
    public boolean restrictPearl = false;
    public boolean restrictChorus = false;
    public int currentPhase = 1;
    public int totalPhasesForRound = 2;
    public int remainingItemsInCurrentPhase = 0;
}