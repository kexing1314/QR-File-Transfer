package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FileChunkerTest {

    @TempDir
    Path tmp;

    @Test
    void chunksExactMultiple() throws Exception {
        Path f = tmp.resolve("a.bin");
        Files.write(f, new byte[]{0, 1, 2, 3, 4, 5}); // 6 bytes, chunkSize 3
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals("a.bin", cf.manifest().fileName());
        assertEquals(6L, cf.manifest().fileSize());
        assertEquals(2, cf.manifest().totalChunks());
        assertEquals(3, cf.manifest().chunkSize());
        assertEquals(2, cf.chunks().size());
        assertArrayEquals(new byte[]{0, 1, 2}, cf.chunks().get(0));
        assertArrayEquals(new byte[]{3, 4, 5}, cf.chunks().get(1));
    }

    @Test
    void lastChunkPartial() throws Exception {
        Path f = tmp.resolve("b.bin");
        Files.write(f, new byte[]{0, 1, 2, 3, 4}); // 5 bytes, chunkSize 3
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals(2, cf.manifest().totalChunks());
        assertArrayEquals(new byte[]{3, 4}, cf.chunks().get(1));
    }

    @Test
    void emptyFile() throws Exception {
        Path f = tmp.resolve("empty.bin");
        Files.write(f, new byte[]{});
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals(0, cf.manifest().totalChunks());
        assertTrue(cf.chunks().isEmpty());
    }

    @Test
    void sha256MatchesStandardLibrary() throws Exception {
        byte[] content = "Hello, QR!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path f = tmp.resolve("c.bin");
        Files.write(f, content);
        ChunkedFile cf = FileChunker.chunk(f, 512);
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(content);
        assertArrayEquals(expected, cf.manifest().sha256());
        assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex(cf.manifest().sha256()));
    }
}
