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
    private final ErrorCorrectionLevel ecLevel;
    private final Timer timer;
    private BufferedImage current;
    private int index = 0;
    private double sizeScale = 1.0;

    public FramePlayer(ChunkedFile cf, int frameIntervalMs, ErrorCorrectionLevel ecLevel) {
        this.frames = buildFrames(cf);
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
    public int chunkCount() { return frames.size() - 1; }

    public String currentLabel() {
        return index == 0 ? "元数据" : "块 " + (index - 1);
    }

    public void start() { if (!timer.isRunning()) timer.start(); }
    public void stop() { timer.stop(); }

    // 传输中动态改帧率（毫秒间隔）
    public void setFrameIntervalMs(int ms) {
        boolean running = timer.isRunning();
        timer.stop();
        timer.setDelay(ms);
        if (running) timer.start();
    }

    // 传输中动态改二维码显示尺寸（0.1 ~ 1.0，占面板比例）
    public void setSizeScale(double scale) {
        this.sizeScale = Math.max(0.1, Math.min(1.0, scale));
        if (current != null) renderCurrent();
    }

    // 显示元数据帧（frames[0]）
    public void showMetadata() {
        stop();
        index = 0;
        renderCurrent();
    }

    // 显示指定数据块（chunkIndex 从 0 开始）
    public void showChunk(int chunkIndex) {
        stop();
        int frameIndex = chunkIndex + 1;
        if (frameIndex < 0 || frameIndex >= frames.size()) return;
        index = frameIndex;
        renderCurrent();
    }

    private void renderCurrent() {
        current = QrRenderer.render(frames.get(index), targetSizePx(), ecLevel);
        repaint();
    }

    private void advance() {
        renderCurrent();
        index = (index + 1) % frames.size();
    }

    private int targetSizePx() {
        return Math.max(64, (int) (Math.min(getWidth(), getHeight()) * sizeScale));
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
