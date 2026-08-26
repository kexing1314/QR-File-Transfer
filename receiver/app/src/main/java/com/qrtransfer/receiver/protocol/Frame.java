package com.qrtransfer.receiver.protocol;

public final class Frame {
    public enum Kind { METADATA, DATA }

    public final Kind kind;
    public final int totalChunks;
    public final String fileName;
    public final long fileSize;
    public final byte[] sha256;
    public final int chunkIndex;
    public final byte[] data;

    private Frame(Kind kind, int totalChunks, String fileName, long fileSize,
                  byte[] sha256, int chunkIndex, byte[] data) {
        this.kind = kind;
        this.totalChunks = totalChunks;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public static Frame metadata(int totalChunks, String fileName, long fileSize, byte[] sha256) {
        return new Frame(Kind.METADATA, totalChunks, fileName, fileSize, sha256, -1, null);
    }

    public static Frame data(int totalChunks, int chunkIndex, byte[] data) {
        return new Frame(Kind.DATA, totalChunks, null, -1, null, chunkIndex, data);
    }
}
