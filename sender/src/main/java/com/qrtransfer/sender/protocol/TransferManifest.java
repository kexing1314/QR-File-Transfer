package com.qrtransfer.sender.protocol;

public record TransferManifest(String fileName, long fileSize, byte[] sha256, int totalChunks, int chunkSize) {}
