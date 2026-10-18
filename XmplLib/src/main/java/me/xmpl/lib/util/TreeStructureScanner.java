package me.xmpl.lib.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.*;

public final class TreeStructureScanner {
    private TreeStructureScanner() {}

    public static class SavedBlockState {
        public final Location location;
        public final Material material;
        public final byte data;

        @SuppressWarnings("deprecation")
        public SavedBlockState(Block block) {
            this.location = block.getLocation();
            this.material = block.getType();
            this.data = block.getData();
        }

        @SuppressWarnings("deprecation")
        public void restore() {
            Block b = location.getBlock();
            b.setType(material, false);
            b.setData(data, false);
        }
    }

    public static class TreeSnapshot {
        public final Block baseBlock;
        public final byte saplingData; // 0=Дуб, 1=Ель, 2=Береза, 3=Тропическое, 4=Акация, 5=Темный дуб
        public final List<SavedBlockState> allBlocks;

        public TreeSnapshot(Block baseBlock, byte saplingData, List<SavedBlockState> allBlocks) {
            this.baseBlock = baseBlock;
            this.saplingData = saplingData;
            this.allBlocks = allBlocks;
        }
    }

    public static boolean isLog(Material mat) {
        return mat == Material.LOG || mat == Material.LOG_2;
    }

    public static boolean isLeaves(Material mat) {
        return mat == Material.LEAVES || mat == Material.LEAVES_2;
    }

    /**
     * Проверяет, является ли блок САМЫМ НИЖНИМ блоком ствола дерева
     */
    public static boolean isBottomTrunkBlock(Block block) {
        if (block == null || !isLog(block.getType())) return false;
        Block below = block.getRelative(BlockFace.DOWN);
        return !isLog(below.getType());
    }

    /**
     * Вычисляет data саженца (SAPLING, 0..5) по блоку бревна в 1.12.2
     */
    @SuppressWarnings("deprecation")
    public static byte resolveSaplingData(Block logBlock) {
        Material type = logBlock.getType();
        byte rawData = (byte) (logBlock.getData() & 0x3); // Младшие 2 бита хранят породу дерева
        if (type == Material.LOG) {
            // 0 = OAK, 1 = SPRUCE, 2 = BIRCH, 3 = JUNGLE
            return rawData;
        } else if (type == Material.LOG_2) {
            // 0 = ACACIA (саженец 4), 1 = DARK_OAK (саженец 5)
            return (byte) (rawData + 4);
        }
        return 0;
    }

    /**
     * Сканирует дерево от нижнего блока. Если это не полноценное дерево (нет листвы или < 3 бревен), возвращает null.
     */
    public static TreeSnapshot scanFullTree(Block baseBlock, int maxLogs, int maxLeavesRadius) {
        if (!isBottomTrunkBlock(baseBlock)) return null;

        byte saplingData = resolveSaplingData(baseBlock);
        Set<Location> visitedLogs = new HashSet<>();
        Queue<Block> logQueue = new ArrayDeque<>();

        logQueue.add(baseBlock);
        visitedLogs.add(baseBlock.getLocation());

        List<Block> foundLogs = new ArrayList<>();

        // 1. Собираем весь ствол и ветки (только вверх и в стороны, чтобы не захватить пол)
        while (!logQueue.isEmpty() && foundLogs.size() < maxLogs) {
            Block current = logQueue.poll();
            foundLogs.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block rel = current.getRelative(dx, dy, dz);
                        if (isLog(rel.getType()) && !visitedLogs.contains(rel.getLocation())) {
                            if (rel.getLocation().distanceSquared(baseBlock.getLocation()) <= 64.0) {
                                visitedLogs.add(rel.getLocation());
                                logQueue.add(rel);
                            }
                        }
                    }
                }
            }
        }

        // 2. Собираем листву вокруг найденных бревен
        Set<Location> visitedLeaves = new HashSet<>();
        List<Block> foundLeaves = new ArrayList<>();

        for (Block log : foundLogs) {
            for (int dx = -maxLeavesRadius; dx <= maxLeavesRadius; dx++) {
                for (int dy = -1; dy <= maxLeavesRadius; dy++) {
                    for (int dz = -maxLeavesRadius; dz <= maxLeavesRadius; dz++) {
                        Block rel = log.getRelative(dx, dy, dz);
                        if (isLeaves(rel.getType()) && !visitedLeaves.contains(rel.getLocation())) {
                            visitedLeaves.add(rel.getLocation());
                            foundLeaves.add(rel);
                        }
                    }
                }
            }
        }

        // Полноценное дерево обязано иметь минимум 3 блока ствола и хотя бы 3 блока листвы!
        if (foundLogs.size() < 3 || foundLeaves.size() < 3) {
            return null;
        }

        List<SavedBlockState> allStates = new ArrayList<>(foundLogs.size() + foundLeaves.size());
        for (Block b : foundLogs) allStates.add(new SavedBlockState(b));
        for (Block b : foundLeaves) allStates.add(new SavedBlockState(b));

        return new TreeSnapshot(baseBlock, saplingData, allStates);
    }
}