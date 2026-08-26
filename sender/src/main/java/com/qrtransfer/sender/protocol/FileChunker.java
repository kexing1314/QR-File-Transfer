package com.qrtransfer.sender.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileChunker {
    private FileChunker() {}

    public static ChunkedFile chunk(Path path, int chunkSize) throws IOException {
        byte[] all = Files.readAllBytes(path);
        byte[] sha256 = sha256(all);
        int totalChunks = all.length == 0 ? 0 : (all.length + chunkSize - 1) / chunkSize;
        List<byte[]> chunks = new ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(all.length, from + chunkSize);
            chunks.add(Arrays.copyOfRange(all, from, to));
        }
        TransferManifest manifest = new TransferManifest(
                path.getFileName().toString(), all.length, sha256, totalChunks, chunkSize);
        return new ChunkedFile(manifest, chunks);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
