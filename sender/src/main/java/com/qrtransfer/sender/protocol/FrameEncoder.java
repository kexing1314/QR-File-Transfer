package com.qrtransfer.sender.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class FrameEncoder {
    public static final byte[] MAGIC = new byte[]{'Q', 'R', 'T', '1'};
    public static final byte VERSION = 1;
    public static final byte TYPE_METADATA = 0x01;
    public static final byte TYPE_DATA = 0x02;

    private FrameEncoder() {}

    public static byte[] encodeMetadata(TransferManifest m) {
        byte[] name = m.fileName().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(10 + 2 + name.length + 8 + 32)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(TYPE_METADATA);
        buf.putInt(m.totalChunks());
        buf.putShort((short) name.length);
        buf.put(name);
        buf.putLong(m.fileSize());
        buf.put(m.sha256());
        return buf.array();
    }

    public static byte[] encodeData(TransferManifest m, int chunkIndex, byte[] chunkData) {
        ByteBuffer buf = ByteBuffer.allocate(10 + 4 + chunkData.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(TYPE_DATA);
        buf.putInt(m.totalChunks());
        buf.putInt(chunkIndex);
        buf.put(chunkData);
        return buf.array();
    }
}
