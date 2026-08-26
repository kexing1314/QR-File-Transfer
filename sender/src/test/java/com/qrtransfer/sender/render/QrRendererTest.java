package com.qrtransfer.sender.render;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrtransfer.sender.protocol.FrameEncoder;
import com.qrtransfer.sender.protocol.TransferManifest;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QrRendererTest {

    @Test
    void renderedQrRoundTripsThroughZxingDecoder() throws Exception {
        byte[] sha = new byte[32];
        TransferManifest m = new TransferManifest("hello.txt", 10L, sha, 1, 512);
        byte[] frame = FrameEncoder.encodeData(m, 0, new byte[]{1, 2, 3, (byte) 0xFF, 0x00, (byte) 0x80});

        BufferedImage img = QrRenderer.render(frame, 400, ErrorCorrectionLevel.M);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(img)));
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        Result result = new MultiFormatReader().decode(bitmap, hints);

        String text = result.getText();
        byte[] decoded = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) decoded[i] = (byte) text.charAt(i);
        assertArrayEquals(frame, decoded);
    }
}
