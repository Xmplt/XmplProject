package me.xmpl.topka.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.TerrainOption;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaVotingService {
    public static class VotingGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final TopkaExtension extension;
    private final List<String> currentRoundTerrainIds = new ArrayList<>();
    private final Map<UUID, String> playerVotes = new ConcurrentHashMap<>();

    public TopkaVotingService(TopkaExtension extension) {
        this.extension = extension;
        loadPersistedVotingState();
    }

    private void loadPersistedVotingState() {
        List<String> savedOptions = extension.getStateStorage().loadVotingRoundOptions();
        for (String id : savedOptions) {
            TerrainOption opt = extension.getTerrainStorage().getTerrain(id);
            if (opt != null && opt.isEnabled()) {
                currentRoundTerrainIds.add(opt.getId());
            }
        }
        playerVotes.putAll(extension.getStateStorage().loadPlayerVotes());
    }

    public boolean hasPlayerVoted(UUID uuid) {
        return uuid != null && playerVotes.containsKey(uuid);
    }

    public synchronized void ensureRoundOptionsRolled() {
        currentRoundTerrainIds.removeIf(id -> {
            TerrainOption opt = extension.getTerrainStorage().getTerrain(id);
            return opt == null || !opt.isEnabled();
        });

        if (!currentRoundTerrainIds.isEmpty()) {
            return;
        }

        List<TerrainOption> enabledPool = new ArrayList<>(extension.getTerrainStorage().getEnabledTerrains());
        if (enabledPool.isEmpty()) return;

        String activeId = extension.getLifecycleService() != null
                ? extension.getLifecycleService().getState().activeTerrainId
                : "";

        if (enabledPool.size() > 1 && activeId != null && !activeId.isEmpty()) {
            enabledPool.removeIf(t -> t.getId().equalsIgnoreCase(activeId));
        }

        Collections.shuffle(enabledPool);
        int maxOptions = Math.min(5, extension.getCustomConfig("voting.yml").get().getInt("settings.random-options-count", 5));
        int count = Math.min(maxOptions, enabledPool.size());

        for (int i = 0; i < count; i++) {
            currentRoundTerrainIds.add(enabledPool.get(i).getId());
        }
        extension.getStateStorage().saveVotingRoundOptionsAsync(currentRoundTerrainIds);
    }

    public synchronized void resetVotingRoundAndRollNew() {
        playerVotes.clear();
        currentRoundTerrainIds.clear();
        extension.getStateStorage().clearAllVotesAsync();
        ensureRoundOptionsRolled();
        if (extension.getVotingHologramService() != null) {
            extension.getVotingHologramService().rebuildAndBroadcastAll();
        }
    }

    public void openVotingGui(Player player) {
        FileConfiguration voteCfg = extension.getCustomConfig("voting.yml").get();
        if (!voteCfg.getBoolean("settings.enabled", true)) return;

        boolean allowRevote = voteCfg.getBoolean("settings.allow-revote", false);
        if (!allowRevote && hasPlayerVoted(player.getUniqueId())) {
            extension.getVotingHologramService().hideHologramForPlayer(player);
            return;
        }

        ensureRoundOptionsRolled();
        if (currentRoundTerrainIds.isEmpty()) {
            ChatUtil.sendMessage(player, voteCfg.getString("messages.no-terrains-configured"));
            return;
        }

        String title = ChatUtil.color(voteCfg.getString("settings.gui-title", "&8Голосование за территорию"));
        String invType = voteCfg.getString("settings.inventory-type", "HOPPER").toUpperCase();

        Inventory gui;
        if (invType.equals("ROW_9")) {
            gui = Bukkit.createInventory(new VotingGuiHolder(), 9, title);
        } else {
            gui = Bukkit.createInventory(new VotingGuiHolder(), InventoryType.HOPPER, title);
        }

        int votesRequired = Math.max(1, voteCfg.getInt("settings.votes-required-to-change", 5));
        String playerCurrentVote = playerVotes.get(player.getUniqueId());

        for (int slot = 0; slot < Math.min(gui.getSize(), currentRoundTerrainIds.size()); slot++) {
            String terraId = currentRoundTerrainIds.get(slot);
            TerrainOption opt = extension.getTerrainStorage().getTerrain(terraId);
            if (opt == null) continue;

            int currentVotes = countVotesFor(terraId);
            int remaining = Math.max(0, votesRequired - currentVotes);
            boolean votedByThisPlayer = terraId.equalsIgnoreCase(playerCurrentVote);

            List<String> formattedLore = new ArrayList<>();
            for (String line : opt.getLore()) {
                formattedLore.add(ChatUtil.applyReplacements(line,
                        "{votes}", currentVotes,
                        "{required}", votesRequired,
                        "{remaining}", remaining));
            }

            gui.setItem(slot, new ItemBuilder(opt.getIconMaterial(), 1, opt.getIconData())
                    .setName(opt.getDisplayName())
                    .setLore(formattedLore)
                    .setGlowing(votedByThisPlayer)
                    .build());
        }

        player.openInventory(gui);
    }

    public synchronized void handleSlotVoteClick(Player voter, int slot) {
        ensureRoundOptionsRolled();
        if (slot < 0 || slot >= currentRoundTerrainIds.size()) return;

        FileConfiguration voteCfg = extension.getCustomConfig("voting.yml").get();
        boolean allowRevote = voteCfg.getBoolean("settings.allow-revote", false);

        String previousVote = playerVotes.get(voter.getUniqueId());
        if (previousVote != null && !allowRevote) {
            voter.closeInventory();
            extension.getVotingHologramService().hideHologramForPlayer(voter);
            return;
        }

        String chosenId = currentRoundTerrainIds.get(slot);
        TerrainOption opt = extension.getTerrainStorage().getTerrain(chosenId);
        if (opt == null) return;

        int votesRequired = Math.max(1, voteCfg.getInt("settings.votes-required-to-change", 5));

        if (chosenId.equalsIgnoreCase(previousVote)) {
            ChatUtil.sendMessage(voter, voteCfg.getString("messages.already-voted-this"), "{terra}", opt.getDisplayName());
            voter.closeInventory();
            return;
        }

        playerVotes.put(voter.getUniqueId(), chosenId);
        extension.getStateStorage().savePlayerVoteAsync(voter.getUniqueId(), chosenId);

        int currentVotes = countVotesFor(chosenId);
        int remaining = Math.max(0, votesRequired - currentVotes);

        voter.closeInventory();

        // Если переголосование отключено — сразу скрываем голограмму у проголосовавшего игрока до следующего голосования!
        if (!allowRevote) {
            extension.getVotingHologramService().hideHologramForPlayer(voter);
        }

        // Если набрано нужное число голосов — меняем территорию и сбрасываем раунд голосования (голограмма снова появляется у всех)
        if (currentVotes >= votesRequired) {
            extension.getTerrainStorage().pasteTerrain(chosenId);
            ChatUtil.sendMessage(voter, voteCfg.getString("messages.terrain-changed-personal"),
                    "{terra}", opt.getDisplayName());
            resetVotingRoundAndRollNew();
            return;
        }

        if (previousVote == null) {
            ChatUtil.sendMessage(voter, voteCfg.getString("messages.voted-personal"),
                    "{terra}", opt.getDisplayName(),
                    "{votes}", currentVotes,
                    "{required}", votesRequired,
                    "{remaining}", remaining);
        } else {
            ChatUtil.sendMessage(voter, voteCfg.getString("messages.vote-changed-personal"),
                    "{terra}", opt.getDisplayName(),
                    "{votes}", currentVotes,
                    "{required}", votesRequired,
                    "{remaining}", remaining);
        }
    }

    public int countVotesFor(String terraId) {
        int count = 0;
        for (String v : playerVotes.values()) {
            if (v.equalsIgnoreCase(terraId)) count++;
        }
        return count;
    }

    public Map<String, Integer> getCurrentVoteCounts() {
        Map<String, Integer> map = new HashMap<>();
        for (String id : currentRoundTerrainIds) {
            map.put(id, countVotesFor(id));
        }
        return map;
    }
}