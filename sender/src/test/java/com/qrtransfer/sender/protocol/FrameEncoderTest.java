package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FrameEncoderTest {

    private static TransferManifest manifest() {
        byte[] sha = new byte[32];
        for (int i = 0; i < 32; i++) sha[i] = (byte) (0xA0 + i);
        return new TransferManifest("hello.txt", 10L, sha, 1, 512);
    }

    @Test
    void metadataFrameLayout() {
        byte[] b = FrameEncoder.encodeMetadata(manifest());
        assertEquals('Q', b[0]); assertEquals('R', b[1]); assertEquals('T', b[2]); assertEquals('1', b[3]);
        assertEquals(1, b[4]);
        assertEquals(0x01, b[5]);
        assertEquals(1, ByteBuffer.wrap(b, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertEquals(9, ByteBuffer.wrap(b, 10, 2).order(ByteOrder.BIG_ENDIAN).getShort());
        assertEquals("hello.txt", new String(b, 12, 9, StandardCharsets.UTF_8));
        assertEquals(10L, ByteBuffer.wrap(b, 21, 8).order(ByteOrder.BIG_ENDIAN).getLong());
        assertEquals(0xA0, b[29] & 0xFF);
        assertEquals(0xBF, b[60] & 0xFF); // 32 sha bytes end at index 60
        assertEquals(61, b.length);
    }

    @Test
    void dataFrameLayout() {
        byte[] chunk = new byte[]{1, 2, 3};
        byte[] b = FrameEncoder.encodeData(manifest(), 0, chunk);
        assertEquals("QRT1", new String(b, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(0x02, b[5]);
        assertEquals(1, ByteBuffer.wrap(b, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertEquals(0, ByteBuffer.wrap(b, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertArrayEquals(chunk, java.util.Arrays.copyOfRange(b, 14, 17));
        assertEquals(17, b.length);
    }
}
