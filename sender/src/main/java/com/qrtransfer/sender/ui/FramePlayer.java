package com.qrtransfer.sender.ui;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrtransfer.sender.protocol.ChunkedFile;
import com.qrtransfer.sender.protocol.FrameEncoder;
import com.qrtransfer.sender.render.QrRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class FramePlayer extends JPanel {
    private final List<byte[]> frames;
    private final int frameIntervalMs;
    private final ErrorCorrectionLevel ecLevel;
    private final Timer timer;
    private BufferedImage current;
    private int index = 0;

    public FramePlayer(ChunkedFile cf, int frameIntervalMs, ErrorCorrectionLevel ecLevel) {
        this.frames = buildFrames(cf);
        this.frameIntervalMs = frameIntervalMs;
        this.ecLevel = ecLevel;
        this.timer = new Timer(frameIntervalMs, e -> advance());
        setBackground(Color.WHITE);
    }

    private static List<byte[]> buildFrames(ChunkedFile cf) {
        List<byte[]> frames = new ArrayList<>();
        frames.add(FrameEncoder.encodeMetadata(cf.manifest()));
        for (int i = 0; i < cf.chunks().size(); i++) {
            frames.add(FrameEncoder.encodeData(cf.manifest(), i, cf.chunks().get(i)));
        }
        return frames;
    }

    public int totalFrames() { return frames.size(); }
    public int currentFrameIndex() { return index; }

    public void start() { if (!timer.isRunning()) timer.start(); }
    public void stop() { timer.stop(); }

    private void advance() {
        index = (index + 1) % frames.size();
        current = QrRenderer.render(frames.get(index), targetSizePx(), ecLevel);
        repaint();
    }

    private int targetSizePx() {
        return Math.max(64, Math.min(getWidth(), getHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (current == null) return;
        int size = targetSizePx();
        if (size < 200) {
            g.setColor(Color.RED);
            g.drawString("窗口过小，二维码可能无法被扫描，请放大窗口", 20, 40);
        }
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        g.drawImage(current, x, y, size, size, null);
    }
}
