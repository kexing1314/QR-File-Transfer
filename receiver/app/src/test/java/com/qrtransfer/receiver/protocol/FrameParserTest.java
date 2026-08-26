package com.qrtransfer.receiver.protocol;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

public class FrameParserTest {

    private byte[] fixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("hello.frames.bin")) {
            return in.readAllBytes();
        }
    }

    @Test
    public void parsesGoldenMetadataFrame() throws Exception {
        byte[] all = fixture();
        // fixture 结构：METADATA 帧 + 1 个 DATA 帧。先解析 METADATA。
        // METADATA 帧长度 = 10 + 2 + nameLen(9) + 8 + 32 = 61
        Frame meta = FrameParser.parse(Arrays.copyOfRange(all, 0, 61));
        assertEquals(Frame.Kind.METADATA, meta.kind);
        assertEquals(1, meta.totalChunks);
        assertEquals("hello.txt", meta.fileName);
        assertEquals(10L, meta.fileSize);
        assertArrayEquals(sha256("Hello, QR!"), meta.sha256);
    }

    @Test
    public void parsesGoldenDataFrame() throws Exception {
        byte[] all = fixture();
        Frame data = FrameParser.parse(Arrays.copyOfRange(all, 61, all.length));
        assertEquals(Frame.Kind.DATA, data.kind);
        assertEquals(1, data.totalChunks);
        assertEquals(0, data.chunkIndex);
        assertArrayEquals("Hello, QR!".getBytes(StandardCharsets.UTF_8), data.data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadMagic() {
        FrameParser.parse(new byte[]{0, 0, 0, 0, 1, 1, 0, 0, 0, 1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortFrame() {
        FrameParser.parse(new byte[]{'Q', 'R', 'T'});
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }
}
