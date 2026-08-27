package com.qrtransfer.receiver.protocol;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Reassembler {
    private int totalChunks = -1;
    private String fileName;
    private long fileSize;
    private byte[] sha256;
    private byte[][] chunks;
    private int received;

    public void accept(Frame f) {
        if (f.kind == Frame.Kind.METADATA) {
            if (f.totalChunks < 0) return;
            this.totalChunks = f.totalChunks;
            this.fileName = f.fileName;
            this.fileSize = f.fileSize;
            this.sha256 = f.sha256;
            if (chunks == null) chunks = new byte[f.totalChunks][];
            else if (chunks.length != f.totalChunks) chunks = new byte[f.totalChunks][];
        } else if (f.kind == Frame.Kind.DATA) {
            if (f.totalChunks < 0 || f.chunkIndex < 0 || f.chunkIndex >= f.totalChunks) return;
            if (chunks == null || chunks.length != f.totalChunks) chunks = new byte[f.totalChunks][];
            if (chunks[f.chunkIndex] == null) {
                chunks[f.chunkIndex] = f.data;
                received++;
            }
        }
    }

    public boolean isComplete() {
        return totalChunks >= 0 && received == totalChunks;
    }

    public byte[] assemble() {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) fileSize);
        for (byte[] c : chunks) out.write(c, 0, c.length);
        return out.toByteArray();
    }

    public boolean verify() {
        byte[] assembled = assemble();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(assembled);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        return MessageDigest.isEqual(sha256, digest);
    }

    public String fileName() { return fileName; }
    public int receivedChunks() { return received; }
    public int totalChunks() { return totalChunks; }
}
