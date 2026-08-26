package com.qrtransfer.sender.render;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class QrRenderer {
    private QrRenderer() {}

    public static BufferedImage render(byte[] frameBytes, int sizePx, ErrorCorrectionLevel ecLevel) {
        String contents = new String(frameBytes, StandardCharsets.ISO_8859_1);
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, "ISO-8859-1",
                EncodeHintType.ERROR_CORRECTION, ecLevel,
                EncodeHintType.MARGIN, 2);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            BufferedImage img = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return img;
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to render QR for " + frameBytes.length + " bytes", e);
        }
    }
}
