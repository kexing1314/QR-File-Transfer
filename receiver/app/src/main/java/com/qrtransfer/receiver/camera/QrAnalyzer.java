package com.qrtransfer.receiver.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class QrAnalyzer implements ImageAnalysis.Analyzer {

    public interface Listener { void onFrame(byte[] frameBytes); }

    private final Map<DecodeHintType, Object> hints = new HashMap<>();
    private final GenericMultipleBarcodeReader multiReader;
    private final Listener listener;

    public QrAnalyzer(Listener listener) {
        this.listener = listener;
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        this.multiReader = new GenericMultipleBarcodeReader(reader);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            byte[] luma = luminance(image);
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    luma, image.getWidth(), image.getHeight(),
                    0, 0, image.getWidth(), image.getHeight(), false);
            Result[] results = multiReader.decodeMultiple(
                    new BinaryBitmap(new HybridBinarizer(source)), hints);
            for (Result result : results) {
                String text = result.getText();
                byte[] bytes = new byte[text.length()];
                for (int i = 0; i < text.length(); i++) bytes[i] = (byte) text.charAt(i);
                listener.onFrame(bytes);
            }
        } catch (Exception ignored) {
            // 本帧无有效二维码
        } finally {
            image.close();
        }
    }

    private byte[] luminance(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ByteBuffer buf = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height];
        if (rowStride == width) {
            buf.get(data); // fast path: contiguous, no padding
        } else {
            for (int row = 0; row < height; row++) {
                buf.position(row * rowStride);
                buf.get(data, row * width, width);
            }
        }
        return data;
    }
}
