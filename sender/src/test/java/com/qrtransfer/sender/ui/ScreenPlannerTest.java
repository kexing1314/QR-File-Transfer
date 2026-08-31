package com.qrtransfer.sender.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenPlannerTest {

    @Test
    void screenCountIncludesMetadataScreen() {
        assertEquals(1, ScreenPlanner.screenCount(0, 4));    // 空文件：仅元数据
        assertEquals(2, ScreenPlanner.screenCount(4, 4));    // 1 + ceil(4/4)=1
        assertEquals(53, ScreenPlanner.screenCount(207, 4)); // 1 + ceil(207/4)=52
        assertEquals(105, ScreenPlanner.screenCount(207, 2));// 1 + ceil(207/2)=104
    }

    @Test
    void screenZeroIsMetadataOnly() {
        assertEquals(List.of(0), ScreenPlanner.frameIndexes(0, 207, 4));
    }

    @Test
    void dataScreenReturnsConsecutiveFrameIndexes() {
        assertEquals(List.of(1, 2, 3, 4), ScreenPlanner.frameIndexes(1, 207, 4));
        assertEquals(List.of(5, 6, 7, 8), ScreenPlanner.frameIndexes(2, 207, 4));
    }

    @Test
    void lastScreenIsPartial() {
        // 207 块，g=4：最后一屏 screen 52 含块 204..206 → 帧 205..207
        assertEquals(List.of(205, 206, 207), ScreenPlanner.frameIndexes(52, 207, 4));
    }

    @Test
    void gridSizeTwo() {
        assertEquals(List.of(1, 2), ScreenPlanner.frameIndexes(1, 5, 2));
        assertEquals(List.of(5), ScreenPlanner.frameIndexes(3, 5, 2)); // 最后一屏只剩 1 块
        assertEquals(4, ScreenPlanner.screenCount(5, 2));              // 1 + ceil(5/2)=3
    }

    @Test
    void screenForChunk() {
        assertEquals(1, ScreenPlanner.screenForChunk(0, 4));
        assertEquals(1, ScreenPlanner.screenForChunk(3, 4));
        assertEquals(2, ScreenPlanner.screenForChunk(4, 4));
        assertEquals(52, ScreenPlanner.screenForChunk(206, 4));
    }

    @Test
    void gridShapeMapping() {
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{2, 1}, GridArrangement.shape(2, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.SQUARE)); // 2 张时方阵退化 1×2
        assertArrayEquals(new int[]{1, 4}, GridArrangement.shape(4, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{4, 1}, GridArrangement.shape(4, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{2, 2}, GridArrangement.shape(4, GridArrangement.SQUARE));
    }
}
