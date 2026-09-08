package com.qrtransfer.sender.ui;

import java.util.ArrayList;
import java.util.List;

public final class ScreenPlanner {
    private ScreenPlanner() {}

    /** 屏总数 = ceil(totalFrames / gridSize)，其中 totalFrames = totalChunks + 1（元数据也算一帧）。 */
    public static int screenCount(int totalChunks, int gridSize) {
        return (totalChunks + gridSize) / gridSize;
    }

    /**
     * 给定屏索引，返回该屏包含的帧索引（帧 0 = 元数据，帧 i = 块 i-1）。
     * 所有帧按 gridSize 一组均匀切分，元数据占第一屏第 1 格；最后一屏可不满。
     */
    public static List<Integer> frameIndexes(int screen, int totalChunks, int gridSize) {
        int totalFrames = totalChunks + 1;
        int first = screen * gridSize;
        int last = Math.min(first + gridSize, totalFrames); // 不含
        List<Integer> out = new ArrayList<>();
        for (int f = first; f < last; f++) out.add(f);
        return out;
    }

    /** 块 c 所在的屏索引（用于补漏后从该屏继续）。 */
    public static int screenForChunk(int chunkIndex, int gridSize) {
        return (chunkIndex + 1) / gridSize;
    }
}
