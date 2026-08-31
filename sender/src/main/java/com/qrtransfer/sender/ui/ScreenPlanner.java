package com.qrtransfer.sender.ui;

import java.util.ArrayList;
import java.util.List;

public final class ScreenPlanner {
    private ScreenPlanner() {}

    /** 屏总数 = 1（元数据屏）+ ceil(totalChunks / gridSize)。 */
    public static int screenCount(int totalChunks, int gridSize) {
        return 1 + (totalChunks + gridSize - 1) / gridSize;
    }

    /**
     * 给定屏索引，返回该屏包含的帧索引（帧 0 = 元数据，帧 i+1 = 块 i）。
     * screen 0 = 仅元数据；screen k (k≥1) = 连续 gridSize 个数据帧（最后一屏可不满）。
     */
    public static List<Integer> frameIndexes(int screen, int totalChunks, int gridSize) {
        if (screen == 0) return List.of(0);
        int firstChunk = (screen - 1) * gridSize;
        int lastChunk = Math.min(firstChunk + gridSize, totalChunks); // 不含
        List<Integer> out = new ArrayList<>();
        for (int c = firstChunk; c < lastChunk; c++) out.add(c + 1);
        return out;
    }

    /** 块 c 所在的屏索引（用于补漏后从该屏继续）。 */
    public static int screenForChunk(int chunkIndex, int gridSize) {
        return 1 + chunkIndex / gridSize;
    }
}
