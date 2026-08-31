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
    private List<BufferedImage> currentImages = new ArrayList<>();
    private int screen = 0;                          // 当前屏索引
    private int manualChunk = -1;                    // ≥0 表示补漏单张模式
    private double sizeScale = 1.0;
    private int gridSize = 4;                        // 每屏数据块数（2 或 4）
    private GridArrangement arrangement = GridArrangement.SQUARE;

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
    public int chunkCount() { return frames.size() - 1; }
    public int screenCount() { return ScreenPlanner.screenCount(chunkCount(), gridSize); }

    public void setGridSize(int g) {
        this.gridSize = Math.max(2, Math.min(4, g));
        this.screen = Math.min(this.screen, screenCount() - 1);
        renderCurrent();
    }

    public void setArrangement(GridArrangement a) {
        this.arrangement = a;
        renderCurrent();
    }

    public String currentLabel() {
        if (manualChunk >= 0) return "块 " + manualChunk;
        if (screen == 0) return "元数据";
        List<Integer> idx = ScreenPlanner.frameIndexes(screen, chunkCount(), gridSize);
        int first = idx.get(0) - 1;
        int last = idx.get(idx.size() - 1) - 1;
        return "屏 " + screen + "：块 " + first + "~" + last;
    }

    public void start() { if (!timer.isRunning()) timer.start(); }
    public void stop() { timer.stop(); }

    public void setFrameIntervalMs(int ms) {
        boolean running = timer.isRunning();
        timer.stop();
        timer.setDelay(ms);
        if (running) timer.start();
    }

    public void setSizeScale(double scale) {
        this.sizeScale = Math.max(0.1, Math.min(1.0, scale));
        renderCurrent();
    }

    public void showMetadata() {
        stop();
        manualChunk = -1;
        screen = 0;
        renderCurrent();
    }

    public void showChunk(int chunkIndex) {
        stop();
        if (chunkIndex < 0 || chunkIndex >= chunkCount()) return;
        manualChunk = chunkIndex;               // 补漏：单张全屏
        renderCurrent();
    }

    private void renderCurrent() {
        List<Integer> indexes;
        int size;
        if (manualChunk >= 0) {
            indexes = List.of(manualChunk + 1);
            size = targetSizePx();              // 单张全屏
        } else {
            indexes = ScreenPlanner.frameIndexes(screen, chunkCount(), gridSize);
            size = cellSizePx(indexes.size());  // 网格按格子尺寸
        }
        currentImages = new ArrayList<>(indexes.size());
        for (int idx : indexes) {
            currentImages.add(QrRenderer.render(frames.get(idx), size, ecLevel));
        }
        repaint();
    }

    private void advance() {
        manualChunk = -1;
        renderCurrent();
        screen = (screen + 1) % screenCount();
    }

    private int targetSizePx() {
        return Math.max(64, (int) (Math.min(getWidth(), getHeight()) * sizeScale));
    }

    /** 网格模式下每格二维码的渲染尺寸（留 10% 间隙，最小 64）。 */
    private int cellSizePx(int count) {
        int[] shape = GridArrangement.shape(count, arrangement);
        int w = getWidth(), h = getHeight();
        int cell = Math.min(w / Math.max(1, shape[1]), h / Math.max(1, shape[0]));
        return Math.max(64, (int) (cell * 0.9 * sizeScale));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentImages == null || currentImages.isEmpty()) return;
        int[] shape = GridArrangement.shape(currentImages.size(), arrangement);
        int rows = shape[0], cols = shape[1];
        int cellW = getWidth() / cols;
        int cellH = getHeight() / rows;
        for (int i = 0; i < currentImages.size(); i++) {
            int r = i / cols, c = i % cols;
            int imgW = currentImages.get(i).getWidth();
            int imgH = currentImages.get(i).getHeight();
            int x = c * cellW + (cellW - imgW) / 2;
            int y = r * cellH + (cellH - imgH) / 2;
            g.drawImage(currentImages.get(i), x, y, null);
        }
    }
}
