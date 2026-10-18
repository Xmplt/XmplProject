package me.xmpl.topka.model;

public class PersistedTopkaState {
    public TopkaStage stage = TopkaStage.TIMER;
    public int remainingSeconds = 3600;
    public int chosenChestNumber = 1; // 1 или 2
    public boolean firstItemTaken = false;
    public String activeTerrainId = "";
}