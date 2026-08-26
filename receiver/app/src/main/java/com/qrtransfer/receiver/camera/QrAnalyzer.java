package com.qrtransfer.receiver.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class QrAnalyzer implements ImageAnalysis.Analyzer {

    public interface Listener { void onFrame(byte[] frameBytes); }

    private final MultiFormatReader reader = new MultiFormatReader();
    private final Listener listener;

    public QrAnalyzer(Listener listener) {
        this.listener = listener;
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        reader.setHints(hints);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            byte[] luma = luminance(image);
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    luma, image.getWidth(), image.getHeight(),
                    0, 0, image.getWidth(), image.getHeight(), false);
            Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            if (result != null) {
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
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }
}
