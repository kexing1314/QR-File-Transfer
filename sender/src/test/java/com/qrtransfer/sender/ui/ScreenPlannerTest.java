package com.qrtransfer.sender.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenPlannerTest {

    @Test
    void screenCountIsCeilOfTotalFramesOverGrid() {
        assertEquals(1, ScreenPlanner.screenCount(0, 4));    // 空文件：仅元数据
        assertEquals(2, ScreenPlanner.screenCount(4, 4));    // ceil(5/4)=2
        assertEquals(52, ScreenPlanner.screenCount(207, 4)); // ceil(208/4)=52
        assertEquals(104, ScreenPlanner.screenCount(207, 2));// ceil(208/2)=104
        assertEquals(3, ScreenPlanner.screenCount(5, 2));    // ceil(6/2)=3
    }

    @Test
    void screenZeroIncludesMetadataAndFirstChunks() {
        assertEquals(List.of(0, 1, 2, 3), ScreenPlanner.frameIndexes(0, 207, 4));
        assertEquals(List.of(0, 1), ScreenPlanner.frameIndexes(0, 5, 2));
    }

    @Test
    void dataScreensReturnConsecutiveFrameIndexes() {
        assertEquals(List.of(4, 5, 6, 7), ScreenPlanner.frameIndexes(1, 207, 4));
        assertEquals(List.of(2, 3), ScreenPlanner.frameIndexes(1, 5, 2));
    }

    @Test
    void lastScreenIsPartial() {
        // 5 块，g=4：6 帧 = 2 屏，screen 1 含帧 4,5 → 块 3,4
        assertEquals(List.of(4, 5), ScreenPlanner.frameIndexes(1, 5, 4));
        // 207 块，g=4：208 帧 = 52 屏，最后一屏 screen 51 含帧 204..207 → 块 203..206
        assertEquals(List.of(204, 205, 206, 207), ScreenPlanner.frameIndexes(51, 207, 4));
    }

    @Test
    void screenForChunk() {
        assertEquals(0, ScreenPlanner.screenForChunk(0, 4));
        assertEquals(0, ScreenPlanner.screenForChunk(2, 4));
        assertEquals(1, ScreenPlanner.screenForChunk(3, 4));
        assertEquals(51, ScreenPlanner.screenForChunk(206, 4));
    }

    @Test
    void gridShapeMapping() {
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{2, 1}, GridArrangement.shape(2, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.SQUARE));
        assertArrayEquals(new int[]{1, 4}, GridArrangement.shape(4, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{4, 1}, GridArrangement.shape(4, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{2, 2}, GridArrangement.shape(4, GridArrangement.SQUARE));
        assertArrayEquals(new int[]{2, 3}, GridArrangement.shape(6, GridArrangement.SQUARE));
    }
}
