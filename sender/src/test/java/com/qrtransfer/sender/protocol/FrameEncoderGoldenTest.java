package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FrameEncoderGoldenTest {

    @Test
    void outputMatchesCommittedFixture() throws Exception {
        ChunkedFile cf = FileChunker.chunk(Paths.get("../protocol/hello.txt"), 512);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FrameEncoder.encodeMetadata(cf.manifest()));
        for (int i = 0; i < cf.chunks().size(); i++) {
            out.write(FrameEncoder.encodeData(cf.manifest(), i, cf.chunks().get(i)));
        }
        byte[] committed = Files.readAllBytes(Path.of("../protocol/hello.frames.bin"));
        assertArrayEquals(committed, out.toByteArray(),
                "encoder output drifted from protocol/hello.frames.bin; run GoldenFixtureGenerator");
    }
}
