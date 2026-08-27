package com.qrtransfer.receiver.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FrameParser {
    public static final byte TYPE_METADATA = 0x01;
    public static final byte TYPE_DATA = 0x02;

    private FrameParser() {}

    public static Frame parse(byte[] b) {
        if (b.length < 10) throw new IllegalArgumentException("frame too short: " + b.length);
        if (b[0] != 'Q' || b[1] != 'R' || b[2] != 'T' || b[3] != '1') {
            throw new IllegalArgumentException("bad magic");
        }
        int totalChunks = ByteBuffer.wrap(b, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int type = b[5] & 0xFF;
        if (type == TYPE_METADATA) {
            if (b.length < 12) throw new IllegalArgumentException("metadata frame too short: " + b.length);
            int nameLen = ((int) ByteBuffer.wrap(b, 10, 2).order(ByteOrder.BIG_ENDIAN).getShort()) & 0xFFFF;
            int off = 12 + nameLen;
            if (off + 8 + 32 > b.length) throw new IllegalArgumentException("metadata frame truncated: " + b.length);
            String fileName = new String(b, 12, nameLen, StandardCharsets.UTF_8);
            long fileSize = ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).getLong();
            byte[] sha256 = Arrays.copyOfRange(b, off + 8, off + 8 + 32);
            return Frame.metadata(totalChunks, fileName, fileSize, sha256);
        } else if (type == TYPE_DATA) {
            if (b.length < 14) throw new IllegalArgumentException("data frame too short: " + b.length);
            int chunkIndex = ByteBuffer.wrap(b, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            byte[] data = Arrays.copyOfRange(b, 14, b.length);
            return Frame.data(totalChunks, chunkIndex, data);
        }
        throw new IllegalArgumentException("unknown frame type: " + type);
    }
}
