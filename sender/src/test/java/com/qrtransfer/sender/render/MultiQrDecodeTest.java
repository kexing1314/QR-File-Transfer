package com.qrtransfer.sender.render;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrtransfer.sender.protocol.FrameEncoder;
import com.qrtransfer.sender.protocol.TransferManifest;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiQrDecodeTest {

    // 注意：GenericMultipleBarcodeReader 内部有 MAX_DEPTH=4 的硬上限（一次最多解 ~5 个码）。
    // 因此本测试只覆盖 2×2（4 张）——这是发送端「张数=4」时的稳定场景。
    // 发送端「张数=6」时每帧可能漏最后 1 个码，属于已知取舍（下一轮循环会补上）。
    @Test
    void decodesAllFourQrsFromTwoByTwoGrid() throws Exception {
        byte[] sha = new byte[32];
        TransferManifest m = new TransferManifest("hello.txt", 100L, sha, 4, 512);
        byte[][] chunks = {
            {1, 2, 3},
            {(byte) 0xFF, 0x00, (byte) 0x80},
            {4, 5, 6},
            {7, 8, 9}
        };
        byte[][] frames = new byte[4][];
        for (int i = 0; i < 4; i++) frames[i] = FrameEncoder.encodeData(m, i, chunks[i]);

        int cell = 300;
        BufferedImage grid = new BufferedImage(cell * 2, cell * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grid.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, cell * 2, cell * 2);
        for (int i = 0; i < 4; i++) {
            BufferedImage qr = QrRenderer.render(frames[i], cell, ErrorCorrectionLevel.M);
            g.drawImage(qr, (i % 2) * cell, (i / 2) * cell, null);
        }
        g.dispose();

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        GenericMultipleBarcodeReader multi = new GenericMultipleBarcodeReader(reader);

        Result[] results = multi.decodeMultiple(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(grid))), hints);

        List<byte[]> decoded = new ArrayList<>();
        for (Result r : results) {
            String text = r.getText();
            byte[] bytes = new byte[text.length()];
            for (int i = 0; i < text.length(); i++) bytes[i] = (byte) text.charAt(i);
            decoded.add(bytes);
        }
        assertEquals(4, decoded.size());
        for (byte[] frame : frames) {
            boolean found = decoded.stream().anyMatch(d -> java.util.Arrays.equals(d, frame));
            assertTrue(found, "expected frame missing from decode results");
        }
    }
}
