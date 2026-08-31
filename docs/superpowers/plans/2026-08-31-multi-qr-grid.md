# 多码网格传输 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把发送端从「一次显示 1 张二维码」升级为「一次显示 2 或 4 张（可切换、可调排列方向）」，接收端从「一帧解 1 码」升级为「一帧解多码」，协议完全不变。

**Architecture:** 引入「屏」概念，把帧序列 `[METADATA, DATA_0, …, DATA_{N-1}]` 重排为屏序列（元数据独占屏 0，其余按 `gridSize` 分组）；发送端 `FramePlayer` 按 `GridArrangement` 布局渲染一屏多码，接收端 `QrAnalyzer` 用 ZXing `GenericMultipleBarcodeReader` 一帧解多码。协议帧字节格式冻结不动。

**Tech Stack:** Java 17、Swing、ZXing core（发送端）；Android、CameraX、ZXing core（接收端）；JUnit 5（发送端测试）。

**Spec:** `docs/superpowers/specs/2026-08-31-multi-qr-grid-design.md`

## Global Constraints

- 发送端 JDK 17+，Maven 构建；接收端 Android minSdk 24（不得引入 API > 24 的调用）。
- **协议字节格式冻结**：`FrameEncoder` / `FrameParser` / `Reassembler` / `TransferSession` / golden 向量一律不改。
- 不新增第三方依赖：`GenericMultipleBarcodeReader` 来自现有 `com.google.zxing:core`（发送端 compile scope、接收端已依赖）。
- 每步改动后 `cd sender && mvn test` 必须全绿（接收端 Task 5 除外，见该任务说明）。
- 提交信息用英文、`feat`/`test` 前缀（沿用仓库现有风格）。

---

### Task 1: `GridArrangement` + `ScreenPlanner`（纯逻辑）

**Files:**
- Create: `sender/src/main/java/com/qrtransfer/sender/ui/GridArrangement.java`
- Create: `sender/src/main/java/com/qrtransfer/sender/ui/ScreenPlanner.java`
- Test: `sender/src/test/java/com/qrtransfer/sender/ui/ScreenPlannerTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `GridArrangement`（枚举 `HORIZONTAL/VERTICAL/SQUARE`）+ 静态方法 `int[] shape(int count, GridArrangement)` → `{rows, cols}`
  - `ScreenPlanner.screenCount(int totalChunks, int gridSize)` → `int`
  - `ScreenPlanner.frameIndexes(int screen, int totalChunks, int gridSize)` → `List<Integer>`（帧索引，帧 0 = 元数据，帧 i+1 = 块 i）
  - `ScreenPlanner.screenForChunk(int chunkIndex, int gridSize)` → `int`

- [ ] **Step 1: 写失败测试**

创建 `sender/src/test/java/com/qrtransfer/sender/ui/ScreenPlannerTest.java`：

```java
package com.qrtransfer.sender.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenPlannerTest {

    @Test
    void screenCountIncludesMetadataScreen() {
        assertEquals(1, ScreenPlanner.screenCount(0, 4));    // 空文件：仅元数据
        assertEquals(2, ScreenPlanner.screenCount(4, 4));    // 1 + ceil(4/4)=1
        assertEquals(53, ScreenPlanner.screenCount(207, 4)); // 1 + ceil(207/4)=52
        assertEquals(105, ScreenPlanner.screenCount(207, 2));// 1 + ceil(207/2)=104
    }

    @Test
    void screenZeroIsMetadataOnly() {
        assertEquals(List.of(0), ScreenPlanner.frameIndexes(0, 207, 4));
    }

    @Test
    void dataScreenReturnsConsecutiveFrameIndexes() {
        assertEquals(List.of(1, 2, 3, 4), ScreenPlanner.frameIndexes(1, 207, 4));
        assertEquals(List.of(5, 6, 7, 8), ScreenPlanner.frameIndexes(2, 207, 4));
    }

    @Test
    void lastScreenIsPartial() {
        // 207 块，g=4：最后一屏 screen 52 含块 204..206 → 帧 205..207
        assertEquals(List.of(205, 206, 207), ScreenPlanner.frameIndexes(52, 207, 4));
    }

    @Test
    void gridSizeTwo() {
        assertEquals(List.of(1, 2), ScreenPlanner.frameIndexes(1, 5, 2));
        assertEquals(List.of(5), ScreenPlanner.frameIndexes(3, 5, 2)); // 最后一屏只剩 1 块
        assertEquals(4, ScreenPlanner.screenCount(5, 2));              // 1 + ceil(5/2)=3
    }

    @Test
    void screenForChunk() {
        assertEquals(1, ScreenPlanner.screenForChunk(0, 4));
        assertEquals(1, ScreenPlanner.screenForChunk(3, 4));
        assertEquals(2, ScreenPlanner.screenForChunk(4, 4));
        assertEquals(52, ScreenPlanner.screenForChunk(206, 4));
    }

    @Test
    void gridShapeMapping() {
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{2, 1}, GridArrangement.shape(2, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{1, 2}, GridArrangement.shape(2, GridArrangement.SQUARE)); // 2 张时方阵退化 1×2
        assertArrayEquals(new int[]{1, 4}, GridArrangement.shape(4, GridArrangement.HORIZONTAL));
        assertArrayEquals(new int[]{4, 1}, GridArrangement.shape(4, GridArrangement.VERTICAL));
        assertArrayEquals(new int[]{2, 2}, GridArrangement.shape(4, GridArrangement.SQUARE));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd sender && mvn -q test -Dtest=ScreenPlannerTest`
Expected: 编译失败（`GridArrangement`、`ScreenPlanner` 不存在）

- [ ] **Step 3: 实现**

创建 `sender/src/main/java/com/qrtransfer/sender/ui/GridArrangement.java`：

```java
package com.qrtransfer.sender.ui;

public enum GridArrangement {
    HORIZONTAL, VERTICAL, SQUARE;

    /** 返回 {行数, 列数}；SQUARE 对 2 张退化为 1×2。 */
    public static int[] shape(int count, GridArrangement arrangement) {
        switch (arrangement) {
            case HORIZONTAL:
                return new int[]{1, count};
            case VERTICAL:
                return new int[]{count, 1};
            case SQUARE:
                return count == 4 ? new int[]{2, 2} : new int[]{1, count};
            default:
                throw new IllegalStateException("unknown arrangement: " + arrangement);
        }
    }
}
```

创建 `sender/src/main/java/com/qrtransfer/sender/ui/ScreenPlanner.java`：

```java
package com.qrtransfer.sender.ui;

import java.util.ArrayList;
import java.util.List;

public final class ScreenPlanner {
    private ScreenPlanner() {}

    /** 屏总数 = 1（元数据屏）+ ceil(totalChunks / gridSize)。 */
    public static int screenCount(int totalChunks, int gridSize) {
        return 1 + (totalChunks + gridSize - 1) / gridSize;
    }

    /**
     * 给定屏索引，返回该屏包含的帧索引（帧 0 = 元数据，帧 i+1 = 块 i）。
     * screen 0 = 仅元数据；screen k (k≥1) = 连续 gridSize 个数据帧（最后一屏可不满）。
     */
    public static List<Integer> frameIndexes(int screen, int totalChunks, int gridSize) {
        if (screen == 0) return List.of(0);
        int firstChunk = (screen - 1) * gridSize;
        int lastChunk = Math.min(firstChunk + gridSize, totalChunks); // 不含
        List<Integer> out = new ArrayList<>();
        for (int c = firstChunk; c < lastChunk; c++) out.add(c + 1);
        return out;
    }

    /** 块 c 所在的屏索引（用于补漏后从该屏继续）。 */
    public static int screenForChunk(int chunkIndex, int gridSize) {
        return 1 + chunkIndex / gridSize;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd sender && mvn -q test -Dtest=ScreenPlannerTest`
Expected: PASS（8 个用例全过）

- [ ] **Step 5: 提交**

```bash
cd sender && mvn -q test   # 全量回归确认无破坏
git add sender/src/main/java/com/qrtransfer/sender/ui/GridArrangement.java \
        sender/src/main/java/com/qrtransfer/sender/ui/ScreenPlanner.java \
        sender/src/test/java/com/qrtransfer/sender/ui/ScreenPlannerTest.java
git commit -m "feat(sender): add grid arrangement and screen planner"
```

---

### Task 2: `FramePlayer` 网格渲染

**Files:**
- Modify: `sender/src/main/java/com/qrtransfer/sender/ui/FramePlayer.java`（整体重写，见 Step 3 完整代码）

**Interfaces:**
- Consumes: `ScreenPlanner`、`GridArrangement`（Task 1）；`QrRenderer.render`、`FrameEncoder`、`ChunkedFile`（已有，签名不变）
- Produces: `FramePlayer` 新增 `setGridSize(int)`、`setArrangement(GridArrangement)`、`screenCount()`、改造后的 `currentLabel()`；保留 `start/stop/setFrameIntervalMs/setSizeScale/showChunk/showMetadata/chunkCount/totalFrames`

> 说明：本任务为 UI 胶水层，无可独立单测的纯逻辑（可测核心已在 Task 1 的 `ScreenPlanner`/`GridArrangement` 覆盖），故以「编译 + 回归 + 手动冒烟」为验证手段，无独立测试文件。

- [ ] **Step 1: 确认基线**

Run: `cd sender && mvn -q test`
Expected: 全绿（Task 1 提交后状态）

- [ ] **Step 2: 重写 `FramePlayer.java`**

用下面完整内容**整体替换** `sender/src/main/java/com/qrtransfer/sender/ui/FramePlayer.java`：

```java
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
```

- [ ] **Step 3: 编译并回归**

Run: `cd sender && mvn -q test`
Expected: 全绿。`MainFrame` 只用 `FramePlayer` 的构造器与 `chunkCount/totalFrames/currentLabel/showChunk/showMetadata/start/stop/setFrameIntervalMs/setSizeScale`，这些签名均未变，故 `MainFrame` 不受影响。

- [ ] **Step 4: 手动冒烟（可选，桌面）**

Run: `cd sender && mvn -q exec:java -Dexec.mainClass=com.qrtransfer.sender.Main`
Expected: 播放窗口默认按方阵 2×2 显示（`gridSize=4`、`SQUARE`）；「手动补漏」的「显示块 N」「显示元数据」仍为单张全屏。状态栏文字暂为旧版（Task 3 会更新）。

- [ ] **Step 5: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/ui/FramePlayer.java
git commit -m "feat(sender): render multi-QR grid in FramePlayer"
```

---

### Task 3: `MainFrame` 新增控件与状态显示

**Files:**
- Modify: `sender/src/main/java/com/qrtransfer/sender/ui/MainFrame.java`

**Interfaces:**
- Consumes: `FramePlayer.setGridSize(int)`、`FramePlayer.setArrangement(GridArrangement)`、`FramePlayer.screenCount()`、`FramePlayer.currentLabel()`（Task 2）；`GridArrangement`（Task 1）
- Produces: 无新公共 API

- [ ] **Step 1: 确认基线**

Run: `cd sender && mvn -q test`
Expected: 全绿（Task 2 提交后状态）

- [ ] **Step 2: 改 `showPlayer` 增加「张数/排列方向」控件**

在 `showPlayer` 内、`transmission` 面板里，紧跟现有 `sizeSlider` 之后，加入两个下拉框并接线。将 `transmission` 面板的构建块替换为：

```java
JPanel transmission = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
transmission.setBorder(BorderFactory.createTitledBorder("传输控制"));
JComboBox<Integer> liveFpsBox = new JComboBox<>(new Integer[]{2, 3, 4, 5, 8, 10, 15});
liveFpsBox.setSelectedItem(fps);
JSlider sizeSlider = new JSlider(20, 100, 100);
JComboBox<Integer> gridSizeBox = new JComboBox<>(new Integer[]{2, 4});
gridSizeBox.setSelectedItem(4);
JComboBox<GridArrangement> layoutBox = new JComboBox<>(
        new GridArrangement[]{GridArrangement.HORIZONTAL, GridArrangement.VERTICAL, GridArrangement.SQUARE});
layoutBox.setSelectedItem(GridArrangement.SQUARE);
transmission.add(new JLabel("帧率:"));
transmission.add(liveFpsBox);
transmission.add(new JLabel("尺寸:"));
transmission.add(sizeSlider);
transmission.add(new JLabel("张数:"));
transmission.add(gridSizeBox);
transmission.add(new JLabel("排列:"));
transmission.add(layoutBox);
```

- [ ] **Step 3: 接线两个新控件**

在 `sizeSlider.addChangeListener(...)` 那一行之后，追加：

```java
gridSizeBox.addActionListener(e -> player.setGridSize((Integer) gridSizeBox.getSelectedItem()));
layoutBox.addActionListener(e -> player.setArrangement((GridArrangement) layoutBox.getSelectedItem()));
```

- [ ] **Step 4: 改状态显示为「屏 X / 共 Y 屏」**

将状态定时器那一行：

```java
new Timer(250, e -> status.setText(
        "当前: " + player.currentLabel() + " / 共 " + player.totalFrames() + " 张"))
        .start();
```

替换为：

```java
new Timer(250, e -> status.setText(
        "当前: " + player.currentLabel() + " / 共 " + player.screenCount() + " 屏"))
        .start();
```

- [ ] **Step 5: 块大小默认改 256**

在构造函数里，把 `chunkSizeBox.setSelectedItem(512);` 改为 `chunkSizeBox.setSelectedItem(256);`。

- [ ] **Step 6: 运行确认通过**

Run: `cd sender && mvn -q test`
Expected: 全绿（UI 改动不影响现有 8+ 单测）

- [ ] **Step 7: 手动冒烟（可选，真机/桌面）**

Run: `cd sender && mvn -q exec:java -Dexec.mainClass=com.qrtransfer.sender.Main`
Expected: 选择文件后播放窗口出现「张数/排列」下拉框；切 2/4 与三种排列，网格实时变化；状态栏显示「当前: 屏 X / 共 Y 屏」。

- [ ] **Step 8: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/ui/MainFrame.java
git commit -m "feat(sender): add grid size/layout controls to MainFrame"
```

---

### Task 4: 多码解码可行性验证（发送端 round-trip 测试）

> 目的：接收端改动（Task 5）在本环境**无法编译**（无 Android SDK/Gradle），故先用发送端已有的 ZXing + 渲染能力，证明 `GenericMultipleBarcodeReader` 能正确解出 2×2 网格里的 4 个码。这是对 Task 5 核心假设的前置验证。

**Files:**
- Test: `sender/src/test/java/com/qrtransfer/sender/render/MultiQrDecodeTest.java`

**Interfaces:**
- Consumes: `QrRenderer.render`、`FrameEncoder.encodeData`、`TransferManifest`（已有）；ZXing `GenericMultipleBarcodeReader`（`com.google.zxing.multi`，来自 core）
- Produces: 无新生产代码

- [ ] **Step 1: 写失败测试**

创建 `sender/src/test/java/com/qrtransfer/sender/render/MultiQrDecodeTest.java`：

```java
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
```

- [ ] **Step 2: 运行确认通过**

Run: `cd sender && mvn -q test -Dtest=MultiQrDecodeTest`
Expected: PASS（4 个码全部 round-trip）

> 若此测试失败（`GenericMultipleBarcodeReader` 对该网格解不全），**停下**：改用 `com.google.zxing.multi.qrcode.QRCodeMultiReader` 重试，并把结论回传（本测试存在意义正在于此——在碰不到真机时先证伪/证实多码方案）。

- [ ] **Step 3: 提交**

```bash
git add sender/src/test/java/com/qrtransfer/sender/render/MultiQrDecodeTest.java
git commit -m "test(sender): prove multi-QR decode with ZXing multi-reader"
```

---

### Task 5: `QrAnalyzer` 一帧解多码（接收端）

> **环境限制**：本机无 Android SDK / Gradle，此任务**无法编译或单测**。按下面改动手动改代码，靠**真机手动验证**收尾。纯逻辑（`FrameParser`/`Reassembler`）不受影响，无新增单测。

**Files:**
- Modify: `receiver/app/src/main/java/com/qrtransfer/receiver/camera/QrAnalyzer.java`

**Interfaces:**
- Consumes: ZXing `GenericMultipleBarcodeReader`（`com.google.zxing.multi`）
- Produces: `QrAnalyzer.Listener.onFrame(byte[])` 从「每帧一次」变为「每帧 N 次」（每个解码结果各调一次）

- [ ] **Step 1: 加 import**

在 `QrAnalyzer.java` 现有 `import com.google.zxing.MultiFormatReader;` 附近，追加：

```java
import com.google.zxing.multi.GenericMultipleBarcodeReader;
```

- [ ] **Step 2: 改字段与构造函数**

把字段：

```java
private final MultiFormatReader reader = new MultiFormatReader();
private final Listener listener;
```

和构造函数体：

```java
public QrAnalyzer(Listener listener) {
    this.listener = listener;
    Map<DecodeHintType, Object> hints = new HashMap<>();
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
    reader.setHints(hints);
}
```

整体替换为：

```java
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
```

- [ ] **Step 3: 改 `analyze()` 为多码解码**

把 `analyze()` 中这一段：

```java
Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
if (result != null) {
    String text = result.getText();
    byte[] bytes = new byte[text.length()];
    for (int i = 0; i < text.length(); i++) bytes[i] = (byte) text.charAt(i);
    listener.onFrame(bytes);
}
```

替换为：

```java
Result[] results = multiReader.decodeMultiple(
        new BinaryBitmap(new HybridBinarizer(source)), hints);
for (Result result : results) {
    String text = result.getText();
    byte[] bytes = new byte[text.length()];
    for (int i = 0; i < text.length(); i++) bytes[i] = (byte) text.charAt(i);
    listener.onFrame(bytes);
}
```

`luminance()` 方法保持不动。确认 `Result`、`BinaryBitmap`、`HybridBinarizer` 的 import 仍在（均在用）。

- [ ] **Step 4: 真机手动验证**

在 Android Studio 中 Build → Run 到真机，与发送端（张数 4、方阵）对扫：

Expected:
1. 发送端一屏 4 个码，接收端一帧能同时解出多个块，进度条跳动加快。
2. 「查看缺失」中缺失块明显少于单码模式（约 2~3 倍吞吐）。
3. 收齐后 SHA-256 校验通过、正常保存、「打开/分享」弹窗出现。
4. 切「张数 2」与「排列 横向/纵向」，均能正常收齐。

- [ ] **Step 5: 提交**

```bash
git add receiver/app/src/main/java/com/qrtransfer/receiver/camera/QrAnalyzer.java
git commit -m "feat(receiver): decode multiple QR codes per camera frame"
```

---

## 提交顺序与收尾

1. Task 1 → Task 2 → Task 3 → Task 4 → Task 5，各自独立提交。
2. 全部完成后：`cd sender && mvn test` 全绿；接收端真机冒烟通过。
3. 合并回 `master` 前走最终审查（`superpowers:finishing-a-development-branch`）。
