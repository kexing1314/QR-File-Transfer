package com.qrtransfer.receiver.protocol;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ReassemblerTest {

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    @Test
    public void reassemblesOutOfOrderAndDedupes() throws Exception {
        byte[] all = "Hello, QR!".getBytes(StandardCharsets.UTF_8);
        Reassembler r = new Reassembler();
        r.accept(Frame.data(2, 1, Arrays.copyOfRange(all, 3, all.length)));
        r.accept(Frame.data(2, 0, Arrays.copyOfRange(all, 0, 3)));
        r.accept(Frame.data(2, 0, Arrays.copyOfRange(all, 0, 3))); // 重复，应忽略
        r.accept(Frame.metadata(2, "hello.txt", all.length, sha256(all)));

        assertTrue(r.isComplete());
        assertEquals(2, r.receivedChunks());
        assertTrue(r.verify());
        assertArrayEquals(all, r.assemble());
        assertEquals("hello.txt", r.fileName());
    }

    @Test
    public void notCompleteUntilAllChunksAndMetadata() {
        Reassembler r = new Reassembler();
        r.accept(Frame.data(2, 0, new byte[]{1, 2}));
        assertFalse(r.isComplete());
        r.accept(Frame.data(2, 1, new byte[]{3}));
        assertFalse(r.isComplete()); // 缺 metadata
    }

    @Test
    public void ignoresOutOfRangeOrNegativeChunkIndex() {
        Reassembler r = new Reassembler();
        r.accept(Frame.data(2, 5, new byte[]{1, 2, 3})); // chunkIndex 5 >= totalChunks 2
        r.accept(Frame.data(-1, 0, new byte[]{1}));       // negative totalChunks
        assertEquals(0, r.receivedChunks());
        assertFalse(r.isComplete());
    }

    @Test
    public void verifyFailsOnCorruptData() throws Exception {
        byte[] all = "Hello, QR!".getBytes(StandardCharsets.UTF_8);
        Reassembler r = new Reassembler();
        r.accept(Frame.data(1, 0, new byte[]{9, 9, 9})); // 内容错误
        r.accept(Frame.metadata(1, "hello.txt", all.length, sha256(all)));
        assertTrue(r.isComplete());
        assertFalse(r.verify());
    }
}
