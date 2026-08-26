package com.qrtransfer.sender.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;

public final class GoldenFixtureGenerator {
    public static void main(String[] args) throws IOException {
        Path hello = Paths.get("../protocol/hello.txt");
        ChunkedFile cf = FileChunker.chunk(hello, 512);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FrameEncoder.encodeMetadata(cf.manifest()));
        for (int i = 0; i < cf.chunks().size(); i++) {
            out.write(FrameEncoder.encodeData(cf.manifest(), i, cf.chunks().get(i)));
        }
        Files.write(Paths.get("../protocol/hello.frames.bin"), out.toByteArray());
        System.out.println("Wrote " + out.size() + " bytes to protocol/hello.frames.bin");
    }
}
