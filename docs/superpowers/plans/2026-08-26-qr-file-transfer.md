# 纯二维码文件传输工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个纯二维码文件传输工具——Java 桌面端把文件编码成多个二维码循环显示，Android 手机端连续扫码重组文件。

**Architecture:** 两个独立模块通过「协议文档 + golden 测试向量」耦合，无代码依赖。发送端（Java 17 + Swing + ZXing）负责分块、成帧、渲染二维码；接收端（Android + CameraX + ZXing）负责解码、解析、重组、校验、写盘。协议为固定分块 + 循环轮播 + 全文件 SHA-256 校验。

**Tech Stack:** Java 17、Maven、Swing、ZXing core 3.5.3、JUnit 5（发送端）；Android、Gradle 8.4、AGP 8.3.2、CameraX 1.3.3、ZXing core 3.5.3、JUnit 4（接收端，纯 JVM 单测）。

**Spec:** `docs/superpowers/specs/2026-08-26-qr-file-transfer-design.md`

## Global Constraints

- Java 版本：17（发送端与接收端均 `source/target 17`）。
- 编码：UTF-8；QR 字节负载用 ISO-8859-1 字节模式往返（每个 char 对应一个 byte，保证二进制无损）。
- 协议常量（写死，两端一致）：`MAGIC = "QRT1"`、`VERSION = 1`、`TYPE_METADATA = 0x01`、`TYPE_DATA = 0x02`；公共头 10 字节，大端序。
- 可配置参数默认值：`CHUNK_SIZE = 512`（三档 256/512/1024）、ECC `M`、帧率 ~5fps。
- 完成判据：收齐 `totalChunks` 个 DATA 帧 + 收到 METADATA + SHA-256 校验通过。
- 包名：发送端 `com.qrtransfer.sender`，接收端 `com.qrtransfer.receiver`。
- 两端帧编解码是**独立实现**，不得互相引用代码；一致性靠 `protocol/` 下的 golden 测试向量保证。

---

## 文件结构

```
sender/src/main/java/com/qrtransfer/sender/
  protocol/TransferManifest.java     # 值对象：文件名/大小/sha256/totalChunks/chunkSize
  protocol/ChunkedFile.java          # 值对象：manifest + 块列表
  protocol/FileChunker.java          # 读文件→算SHA-256→分块
  protocol/FrameEncoder.java         # manifest/块 → 协议帧字节（纯函数）
  protocol/GoldenFixtureGenerator.java # 生成 protocol/hello.frames.bin 的小工具
  render/QrRenderer.java             # 帧字节 → BufferedImage（ZXing）
  ui/FramePlayer.java                # Swing 面板：懒渲染 + Timer 循环播放
  ui/MainFrame.java                  # 文件选择 + 参数设置 + 启动
  Main.java                          # 入口
sender/src/test/java/com/qrtransfer/sender/
  protocol/FileChunkerTest.java
  protocol/FrameEncoderTest.java
  protocol/FrameEncoderGoldenTest.java
  render/QrRendererTest.java

receiver/app/src/main/java/com/qrtransfer/receiver/
  protocol/Frame.java                # 解析结果（metadata/data 二选一）
  protocol/FrameParser.java          # 帧字节 → Frame（FrameEncoder 的镜像，纯函数）
  protocol/Reassembler.java          # 累积/去重/判完成/组装
  camera/QrAnalyzer.java             # CameraX ImageAnalysis + ZXing 解码
  session/TransferSession.java       # SHA-256 校验 + 写 Downloads + 通知
  ui/MainActivity.java               # 相机预览 + 进度 + 结果
receiver/app/src/test/java/com/qrtransfer/receiver/
  protocol/FrameParserTest.java
  protocol/ReassemblerTest.java
receiver/app/src/test/resources/hello.frames.bin   # golden fixture 副本

protocol/hello.txt                   # 样例文件 "Hello, QR!"（10 字节）
protocol/hello.frames.bin            # golden fixture（发送端生成）
```

---

## Phase 1 — 发送端核心

### Task 1: 仓库重构为 Maven 多模块 + 依赖

**Files:**
- Modify: `pom.xml`（改为 parent/aggregator）
- Create: `sender/pom.xml`
- Create: `sender/src/main/java/com/qrtransfer/sender/Main.java`（占位 main，后续 Task 5 补全）

**Interfaces:**
- Consumes: 无
- Produces: 可运行 `mvn test` 的 `sender` 模块；依赖坐标 `com.google.zxing:core:3.5.3`、`org.junit.jupiter:junit-jupiter:5.10.2`

- [ ] **Step 1: 改写根 pom.xml 为 parent**

用以下内容替换 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.qrtransfer</groupId>
    <artifactId>qr-file-transfer-util</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>sender</module>
    </modules>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <zxing.version>3.5.3</zxing.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.zxing</groupId>
                <artifactId>core</artifactId>
                <version>${zxing.version}</version>
            </dependency>
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 sender/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.qrtransfer</groupId>
        <artifactId>qr-file-transfer-util</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <artifactId>sender</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 创建占位 Main.java**

```java
package com.qrtransfer.sender;

public final class Main {
    public static void main(String[] args) {
        // 在 Task 5 中补全 UI 启动逻辑
    }
}
```

- [ ] **Step 4: 验证构建通过**

Run: `mvn -q test`
Expected: BUILD SUCCESS（无测试，0 个失败）

- [ ] **Step 5: 提交**

```bash
git add pom.xml sender/pom.xml sender/src/main/java/com/qrtransfer/sender/Main.java
git commit -m "build: restructure as Maven multi-module, add sender module"
```

---

### Task 2: TransferManifest + FileChunker（分块 + SHA-256）

**Files:**
- Create: `sender/src/main/java/com/qrtransfer/sender/protocol/TransferManifest.java`
- Create: `sender/src/main/java/com/qrtransfer/sender/protocol/ChunkedFile.java`
- Create: `sender/src/main/java/com/qrtransfer/sender/protocol/FileChunker.java`
- Test: `sender/src/test/java/com/qrtransfer/sender/protocol/FileChunkerTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `record TransferManifest(String fileName, long fileSize, byte[] sha256, int totalChunks, int chunkSize)`
  - `record ChunkedFile(TransferManifest manifest, java.util.List<byte[]> chunks)`
  - `static ChunkedFile FileChunker.chunk(java.nio.file.Path path, int chunkSize) throws java.io.IOException`

- [ ] **Step 1: 写失败测试**

```java
package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FileChunkerTest {

    @TempDir
    Path tmp;

    @Test
    void chunksExactMultiple() throws Exception {
        Path f = tmp.resolve("a.bin");
        Files.write(f, new byte[]{0, 1, 2, 3, 4, 5}); // 6 bytes, chunkSize 3
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals("a.bin", cf.manifest().fileName());
        assertEquals(6L, cf.manifest().fileSize());
        assertEquals(2, cf.manifest().totalChunks());
        assertEquals(3, cf.manifest().chunkSize());
        assertEquals(2, cf.chunks().size());
        assertArrayEquals(new byte[]{0, 1, 2}, cf.chunks().get(0));
        assertArrayEquals(new byte[]{3, 4, 5}, cf.chunks().get(1));
    }

    @Test
    void lastChunkPartial() throws Exception {
        Path f = tmp.resolve("b.bin");
        Files.write(f, new byte[]{0, 1, 2, 3, 4}); // 5 bytes, chunkSize 3
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals(2, cf.manifest().totalChunks());
        assertArrayEquals(new byte[]{3, 4}, cf.chunks().get(1));
    }

    @Test
    void emptyFile() throws Exception {
        Path f = tmp.resolve("empty.bin");
        Files.write(f, new byte[]{});
        ChunkedFile cf = FileChunker.chunk(f, 3);
        assertEquals(0, cf.manifest().totalChunks());
        assertTrue(cf.chunks().isEmpty());
    }

    @Test
    void sha256MatchesStandardLibrary() throws Exception {
        byte[] content = "Hello, QR!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path f = tmp.resolve("c.bin");
        Files.write(f, content);
        ChunkedFile cf = FileChunker.chunk(f, 512);
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(content);
        assertArrayEquals(expected, cf.manifest().sha256());
        assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex(cf.manifest().sha256()));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl sender test -Dtest=FileChunkerTest`
Expected: 编译失败（`FileChunker`/`TransferManifest`/`ChunkedFile` 不存在）

- [ ] **Step 3: 写最小实现**

`TransferManifest.java`:

```java
package com.qrtransfer.sender.protocol;

public record TransferManifest(String fileName, long fileSize, byte[] sha256, int totalChunks, int chunkSize) {}
```

`ChunkedFile.java`:

```java
package com.qrtransfer.sender.protocol;

import java.util.List;

public record ChunkedFile(TransferManifest manifest, List<byte[]> chunks) {}
```

`FileChunker.java`:

```java
package com.qrtransfer.sender.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileChunker {
    private FileChunker() {}

    public static ChunkedFile chunk(Path path, int chunkSize) throws IOException {
        byte[] all = Files.readAllBytes(path);
        byte[] sha256 = sha256(all);
        int totalChunks = all.length == 0 ? 0 : (all.length + chunkSize - 1) / chunkSize;
        List<byte[]> chunks = new ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(all.length, from + chunkSize);
            chunks.add(Arrays.copyOfRange(all, from, to));
        }
        TransferManifest manifest = new TransferManifest(
                path.getFileName().toString(), all.length, sha256, totalChunks, chunkSize);
        return new ChunkedFile(manifest, chunks);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl sender test -Dtest=FileChunkerTest`
Expected: PASS（4 个测试）

- [ ] **Step 5: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/protocol/TransferManifest.java \
        sender/src/main/java/com/qrtransfer/sender/protocol/ChunkedFile.java \
        sender/src/main/java/com/qrtransfer/sender/protocol/FileChunker.java \
        sender/src/test/java/com/qrtransfer/sender/protocol/FileChunkerTest.java
git commit -m "feat(sender): add FileChunker with SHA-256 and chunking"
```

---

### Task 3: FrameEncoder + golden fixture 生成

**Files:**
- Create: `sender/src/main/java/com/qrtransfer/sender/protocol/FrameEncoder.java`
- Create: `sender/src/main/java/com/qrtransfer/sender/protocol/GoldenFixtureGenerator.java`
- Test: `sender/src/test/java/com/qrtransfer/sender/protocol/FrameEncoderTest.java`
- Test: `sender/src/test/java/com/qrtransfer/sender/protocol/FrameEncoderGoldenTest.java`
- Create: `protocol/hello.txt`

**Interfaces:**
- Consumes: `TransferManifest`（Task 2）
- Produces:
  - `static byte[] FrameEncoder.encodeMetadata(TransferManifest m)`
  - `static byte[] FrameEncoder.encodeData(TransferManifest m, int chunkIndex, byte[] chunkData)`
  - 常量 `FrameEncoder.MAGIC`、`VERSION`、`TYPE_METADATA`、`TYPE_DATA`
  - `protocol/hello.frames.bin`（golden fixture）

- [ ] **Step 1: 创建样例文件 protocol/hello.txt**

内容（无换行，正好 10 字节）：`Hello, QR!`

```bash
mkdir -p protocol
printf 'Hello, QR!' > protocol/hello.txt
```

- [ ] **Step 2: 写失败测试 FrameEncoderTest**

```java
package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FrameEncoderTest {

    private static TransferManifest manifest() {
        byte[] sha = new byte[32];
        for (int i = 0; i < 32; i++) sha[i] = (byte) (0xA0 + i);
        return new TransferManifest("hello.txt", 10L, sha, 1, 512);
    }

    @Test
    void metadataFrameLayout() {
        byte[] b = FrameEncoder.encodeMetadata(manifest());
        assertEquals('Q', b[0]); assertEquals('R', b[1]); assertEquals('T', b[2]); assertEquals('1', b[3]);
        assertEquals(1, b[4]);
        assertEquals(0x01, b[5]);
        assertEquals(1, ByteBuffer.wrap(b, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertEquals(9, ByteBuffer.wrap(b, 10, 2).order(ByteOrder.BIG_ENDIAN).getShort());
        assertEquals("hello.txt", new String(b, 12, 9, StandardCharsets.UTF_8));
        assertEquals(10L, ByteBuffer.wrap(b, 21, 8).order(ByteOrder.BIG_ENDIAN).getLong());
        assertEquals(0xA0, b[29] & 0xFF);
        assertEquals(0xBF, b[60] & 0xFF); // 32 sha bytes end at index 60
        assertEquals(61, b.length);
    }

    @Test
    void dataFrameLayout() {
        byte[] chunk = new byte[]{1, 2, 3};
        byte[] b = FrameEncoder.encodeData(manifest(), 0, chunk);
        assertEquals("QRT1", new String(b, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(0x02, b[5]);
        assertEquals(1, ByteBuffer.wrap(b, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertEquals(0, ByteBuffer.wrap(b, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertArrayEquals(chunk, java.util.Arrays.copyOfRange(b, 14, 17));
        assertEquals(17, b.length);
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -pl sender test -Dtest=FrameEncoderTest`
Expected: 编译失败（`FrameEncoder` 不存在）

- [ ] **Step 4: 写实现 FrameEncoder**

```java
package com.qrtransfer.sender.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class FrameEncoder {
    public static final byte[] MAGIC = new byte[]{'Q', 'R', 'T', '1'};
    public static final byte VERSION = 1;
    public static final byte TYPE_METADATA = 0x01;
    public static final byte TYPE_DATA = 0x02;

    private FrameEncoder() {}

    public static byte[] encodeMetadata(TransferManifest m) {
        byte[] name = m.fileName().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(10 + 2 + name.length + 8 + 32)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(TYPE_METADATA);
        buf.putInt(m.totalChunks());
        buf.putShort((short) name.length);
        buf.put(name);
        buf.putLong(m.fileSize());
        buf.put(m.sha256());
        return buf.array();
    }

    public static byte[] encodeData(TransferManifest m, int chunkIndex, byte[] chunkData) {
        ByteBuffer buf = ByteBuffer.allocate(10 + 4 + chunkData.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(TYPE_DATA);
        buf.putInt(m.totalChunks());
        buf.putInt(chunkIndex);
        buf.put(chunkData);
        return buf.array();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl sender test -Dtest=FrameEncoderTest`
Expected: PASS（2 个测试）

- [ ] **Step 6: 写 GoldenFixtureGenerator（生成 golden fixture）**

```java
package com.qrtransfer.sender.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;

public final class GoldenFixtureGenerator {
    public static void main(String[] args) throws IOException {
        Path hello = Paths.get("../protocol/hello.txt");
        ChunkedFile cf = FileChunker.chunk(hello, 512);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FrameEncoder.encodeMetadata(cf.manifest()));
        for (int i = 0; i < cf.chunks().size(); i++) {
            out.write(FrameEncoder.encodeData(cf.manifest(), i, cf.chunks().get(i)));
        }
        Files.write(Paths.get("../protocol/hello.frames.bin"), out.toByteArray());
        System.out.println("Wrote " + out.size() + " bytes to protocol/hello.frames.bin");
    }
}
```

- [ ] **Step 7: 写 FrameEncoderGoldenTest（锁定编码器输出 = fixture）**

```java
package com.qrtransfer.sender.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FrameEncoderGoldenTest {

    @Test
    void outputMatchesCommittedFixture() throws Exception {
        ChunkedFile cf = FileChunker.chunk(Paths.get("../protocol/hello.txt"), 512);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FrameEncoder.encodeMetadata(cf.manifest()));
        for (int i = 0; i < cf.chunks().size(); i++) {
            out.write(FrameEncoder.encodeData(cf.manifest(), i, cf.chunks().get(i)));
        }
        byte[] committed = Files.readAllBytes(Path.of("../protocol/hello.frames.bin"));
        assertArrayEquals(committed, out.toByteArray(),
                "encoder output drifted from protocol/hello.frames.bin; run GoldenFixtureGenerator");
    }
}
```

- [ ] **Step 8: 给 sender/pom.xml 添加 exec 插件（用于运行生成器）**

在 `sender/pom.xml` 的 `<build><plugins>` 内、`maven-surefire-plugin` 之后加入：

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.2.0</version>
</plugin>
```

- [ ] **Step 9: 运行生成器，产出 golden fixture**

注意：生成器内部用相对路径 `../protocol/`，因此**必须从 `sender/` 目录运行**（Maven 的 exec:java 以调用 mvn 时所在目录为工作目录）。

Run: `cd sender && mvn -q compile exec:java -Dexec.mainClass=com.qrtransfer.sender.protocol.GoldenFixtureGenerator`
Expected: 输出 `Wrote 85 bytes to protocol/hello.frames.bin`，且 `../protocol/hello.frames.bin` 文件已生成（85 字节 = 61 METADATA + 24 DATA）。

- [ ] **Step 10: 运行 golden 测试确认编码器输出与 fixture 一致**

Run: `cd .. && mvn -q -pl sender test -Dtest=FrameEncoderGoldenTest`
Expected: PASS（若失败，说明编码器输出与 fixture 漂移，检查 Step 9 是否重新生成）

- [ ] **Step 11: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/protocol/FrameEncoder.java \
        sender/src/main/java/com/qrtransfer/sender/protocol/GoldenFixtureGenerator.java \
        sender/src/test/java/com/qrtransfer/sender/protocol/FrameEncoderTest.java \
        sender/src/test/java/com/qrtransfer/sender/protocol/FrameEncoderGoldenTest.java \
        protocol/hello.txt protocol/hello.frames.bin
git commit -m "feat(sender): add FrameEncoder and golden fixture"
```

---

## Phase 2 — 发送端渲染与 UI

### Task 4: QrRenderer（帧字节 → 二维码图像）

**Files:**
- Create: `sender/src/main/java/com/qrtransfer/sender/render/QrRenderer.java`
- Test: `sender/src/test/java/com/qrtransfer/sender/render/QrRendererTest.java`

**Interfaces:**
- Consumes: `FrameEncoder`（Task 3）、ZXing core
- Produces: `static java.awt.image.BufferedImage QrRenderer.render(byte[] frameBytes, int sizePx, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel ecLevel)`

- [ ] **Step 1: 写失败测试（编解码 round-trip）**

```java
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
```

注意：测试需要 ZXing 的 `BufferedImageLuminanceSource`，它在 `com.google.zxing:javase` 构件里，不在 `core` 里。因此在 `sender/pom.xml` 的 `<dependencies>` 增加（scope=test）：

```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>${zxing.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl sender test -Dtest=QrRendererTest`
Expected: 编译失败（`QrRenderer` 不存在）

- [ ] **Step 3: 写实现 QrRenderer**

```java
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl sender test -Dtest=QrRendererTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/render/QrRenderer.java \
        sender/src/test/java/com/qrtransfer/sender/render/QrRendererTest.java \
        sender/pom.xml
git commit -m "feat(sender): add QrRenderer with round-trip test"
```

---

### Task 5: FramePlayer + MainFrame + Main（Swing UI）

**Files:**
- Create: `sender/src/main/java/com/qrtransfer/sender/ui/FramePlayer.java`
- Create: `sender/src/main/java/com/qrtransfer/sender/ui/MainFrame.java`
- Modify: `sender/src/main/java/com/qrtransfer/sender/Main.java`

**Interfaces:**
- Consumes: `FileChunker`、`FrameEncoder`（Task 2/3）、`QrRenderer`（Task 4）
- Produces: 可运行 `mvn -q -pl sender exec:java -Dexec.mainClass=com.qrtransfer.sender.Main` 的桌面程序

说明：Swing UI 属视觉行为，无法做有意义的 JUnit 单测，本任务以**手动验证**代替自动化测试；核心逻辑（分块/成帧/渲染）已在 Task 2–4 用单测覆盖。

- [ ] **Step 1: 写 FramePlayer（懒渲染 + Timer 循环）**

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
```

- [ ] **Step 2: 写 MainFrame（文件选择 + 参数 + 启动）**

```java
package com.qrtransfer.sender.ui;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrtransfer.sender.protocol.ChunkedFile;
import com.qrtransfer.sender.protocol.FileChunker;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public final class MainFrame extends JFrame {
    private final JComboBox<Integer> chunkSizeBox =
            new JComboBox<>(new Integer[]{256, 512, 1024});
    private final JComboBox<Integer> fpsBox =
            new JComboBox<>(new Integer[]{2, 3, 4, 5});

    public MainFrame() {
        super("QR File Transfer — Sender");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel controls = new JPanel(new FlowLayout());
        JButton pick = new JButton("选择文件");
        controls.add(pick);
        controls.add(new JLabel("块大小:"));
        controls.add(chunkSizeBox);
        chunkSizeBox.setSelectedItem(512);
        controls.add(new JLabel("帧率:"));
        controls.add(fpsBox);
        fpsBox.setSelectedItem(5);
        add(controls, BorderLayout.NORTH);

        pick.addActionListener(e -> onPick());
        setSize(900, 900);
    }

    private void onPick() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f.length() > 20L * 1024 * 1024) {
            int r = JOptionPane.showConfirmDialog(this,
                    "文件超过 20MB，纯二维码传输会很慢，仍要继续吗？",
                    "文件较大", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
        }
        int chunkSize = (Integer) chunkSizeBox.getSelectedItem();
        int fps = (Integer) fpsBox.getSelectedItem();
        try {
            ChunkedFile cf = FileChunker.chunk(Path.of(f.toURI()), chunkSize);
            showPlayer(cf, fps);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "读取失败: " + ex.getMessage());
        }
    }

    private void showPlayer(ChunkedFile cf, int fps) {
        FramePlayer player = new FramePlayer(cf, 1000 / fps, ErrorCorrectionLevel.M);
        JFrame playerFrame = new JFrame("扫描我 — " + cf.manifest().fileName());
        playerFrame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        playerFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        playerFrame.setLayout(new BorderLayout());
        playerFrame.add(player, BorderLayout.CENTER);
        JLabel status = new JLabel("准备就绪", SwingConstants.CENTER);
        JButton startStop = new JButton("开始");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(startStop, BorderLayout.EAST);
        playerFrame.add(bottom, BorderLayout.SOUTH);
        startStop.addActionListener(e -> {
            if (startStop.getText().equals("开始")) {
                player.start();
                startStop.setText("停止");
            } else {
                player.stop();
                startStop.setText("开始");
            }
        });
        new Timer(250, e -> status.setText(
                "帧 " + (player.currentFrameIndex() + 1) + " / " + player.totalFrames()))
                .start();
        playerFrame.setVisible(true);
    }
}
```

- [ ] **Step 3: 补全 Main.java**

```java
package com.qrtransfer.sender;

import com.qrtransfer.sender.ui.MainFrame;

import javax.swing.*;

public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
```

- [ ] **Step 4: 手动验证**

在 `sender/pom.xml` 的 `<plugins>` 加入 exec 插件（若已加则跳过）：

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.2.0</version>
</plugin>
```

Run: `mvn -q -pl sender exec:java -Dexec.mainClass=com.qrtransfer.sender.Main`
Expected: 弹出主窗口；选一个文件 → 弹出全屏播放窗口 → 点「开始」后二维码循环播放，底部显示 `帧 X / N`。

- [ ] **Step 5: 提交**

```bash
git add sender/src/main/java/com/qrtransfer/sender/ui/FramePlayer.java \
        sender/src/main/java/com/qrtransfer/sender/ui/MainFrame.java \
        sender/src/main/java/com/qrtransfer/sender/Main.java \
        sender/pom.xml
git commit -m "feat(sender): add Swing UI with file picker and QR player"
```

---

## Phase 3 — 接收端核心

### Task 6: Android 工程脚手架

**Files:**
- Create: `receiver/settings.gradle`
- Create: `receiver/build.gradle`
- Create: `receiver/gradle.properties`
- Create: `receiver/app/build.gradle`
- Create: `receiver/app/src/main/AndroidManifest.xml`
- Create: `receiver/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: 无
- Produces: 可在 Android Studio 打开、可 `./gradlew :app:testDebugUnitTest` 的工程

- [ ] **Step 1: 写 settings.gradle**

```groovy
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "receiver"
include ':app'
```

- [ ] **Step 2: 写根 build.gradle**

```groovy
plugins {
    id 'com.android.application' version '8.3.2' apply false
}
```

- [ ] **Step 3: 写 gradle.properties**

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2g
```

- [ ] **Step 4: 写 app/build.gradle**

```groovy
plugins { id 'com.android.application' }

android {
    namespace 'com.qrtransfer.receiver'
    compileSdk 34

    defaultConfig {
        applicationId "com.qrtransfer.receiver"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.returnDefaultValues = true
    }
}

dependencies {
    implementation 'androidx.camera:camera-camera2:1.3.3'
    implementation 'androidx.camera:camera-lifecycle:1.3.3'
    implementation 'androidx.camera:camera-view:1.3.3'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.zxing:core:3.5.3'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 5: 写 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="true" />

    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name=".ui.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: 写 strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">QR 文件接收</string>
</resources>
```

- [ ] **Step 7: 提交**

```bash
git add receiver/settings.gradle receiver/build.gradle receiver/gradle.properties \
        receiver/app/build.gradle receiver/app/src/main/AndroidManifest.xml \
        receiver/app/src/main/res/values/strings.xml
git commit -m "build(receiver): scaffold Android app with CameraX and ZXing deps"
```

---

### Task 7: FrameParser（帧字节 → Frame，镜像实现）

**Files:**
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/protocol/Frame.java`
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/protocol/FrameParser.java`
- Test: `receiver/app/src/test/java/com/qrtransfer/receiver/protocol/FrameParserTest.java`
- Create: `receiver/app/src/test/resources/hello.frames.bin`（复制 `protocol/hello.frames.bin`）

**Interfaces:**
- Consumes: golden fixture `hello.frames.bin`（Task 3 生成）
- Produces:
  - `class Frame`（字段 `kind`、`totalChunks`、`fileName`、`fileSize`、`sha256`、`chunkIndex`、`data`；工厂 `Frame.metadata(...)` / `Frame.data(...)`）
  - `static Frame FrameParser.parse(byte[] bytes)`

- [ ] **Step 1: 复制 golden fixture 到测试资源**

```bash
mkdir -p receiver/app/src/test/resources
cp protocol/hello.frames.bin receiver/app/src/test/resources/hello.frames.bin
```

- [ ] **Step 2: 写失败测试 FrameParserTest**

```java
package com.qrtransfer.receiver.protocol;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

public class FrameParserTest {

    private byte[] fixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("hello.frames.bin")) {
            return in.readAllBytes();
        }
    }

    @Test
    public void parsesGoldenMetadataFrame() throws Exception {
        byte[] all = fixture();
        // fixture 结构：METADATA 帧 + 1 个 DATA 帧。先解析 METADATA。
        // METADATA 帧长度 = 10 + 2 + nameLen(9) + 8 + 32 = 61
        Frame meta = FrameParser.parse(Arrays.copyOfRange(all, 0, 61));
        assertEquals(Frame.Kind.METADATA, meta.kind);
        assertEquals(1, meta.totalChunks);
        assertEquals("hello.txt", meta.fileName);
        assertEquals(10L, meta.fileSize);
        assertArrayEquals(sha256("Hello, QR!"), meta.sha256);
    }

    @Test
    public void parsesGoldenDataFrame() throws Exception {
        byte[] all = fixture();
        Frame data = FrameParser.parse(Arrays.copyOfRange(all, 61, all.length));
        assertEquals(Frame.Kind.DATA, data.kind);
        assertEquals(1, data.totalChunks);
        assertEquals(0, data.chunkIndex);
        assertArrayEquals("Hello, QR!".getBytes(StandardCharsets.UTF_8), data.data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadMagic() {
        FrameParser.parse(new byte[]{0, 0, 0, 0, 1, 1, 0, 0, 0, 1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortFrame() {
        FrameParser.parse(new byte[]{'Q', 'R', 'T'});
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd receiver && ./gradlew :app:testDebugUnitTest --tests "com.qrtransfer.receiver.protocol.FrameParserTest"`
Expected: 编译失败（`Frame`/`FrameParser` 不存在）。Windows 下用 `gradlew.bat`。

- [ ] **Step 4: 写实现 Frame + FrameParser**

`Frame.java`:

```java
package com.qrtransfer.receiver.protocol;

public final class Frame {
    public enum Kind { METADATA, DATA }

    public final Kind kind;
    public final int totalChunks;
    public final String fileName;
    public final long fileSize;
    public final byte[] sha256;
    public final int chunkIndex;
    public final byte[] data;

    private Frame(Kind kind, int totalChunks, String fileName, long fileSize,
                  byte[] sha256, int chunkIndex, byte[] data) {
        this.kind = kind;
        this.totalChunks = totalChunks;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public static Frame metadata(int totalChunks, String fileName, long fileSize, byte[] sha256) {
        return new Frame(Kind.METADATA, totalChunks, fileName, fileSize, sha256, -1, null);
    }

    public static Frame data(int totalChunks, int chunkIndex, byte[] data) {
        return new Frame(Kind.DATA, totalChunks, -1, -1, null, chunkIndex, data);
    }
}
```

`FrameParser.java`:

```java
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
            int nameLen = Short.toUnsignedInt(ByteBuffer.wrap(b, 10, 2).order(ByteOrder.BIG_ENDIAN).getShort());
            String fileName = new String(b, 12, nameLen, StandardCharsets.UTF_8);
            int off = 12 + nameLen;
            long fileSize = ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).getLong();
            byte[] sha256 = Arrays.copyOfRange(b, off + 8, off + 8 + 32);
            return Frame.metadata(totalChunks, fileName, fileSize, sha256);
        } else if (type == TYPE_DATA) {
            int chunkIndex = ByteBuffer.wrap(b, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            byte[] data = Arrays.copyOfRange(b, 14, b.length);
            return Frame.data(totalChunks, chunkIndex, data);
        }
        throw new IllegalArgumentException("unknown frame type: " + type);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd receiver && ./gradlew :app:testDebugUnitTest --tests "com.qrtransfer.receiver.protocol.FrameParserTest"`
Expected: PASS（4 个测试）

- [ ] **Step 6: 提交**

```bash
git add receiver/app/src/main/java/com/qrtransfer/receiver/protocol/Frame.java \
        receiver/app/src/main/java/com/qrtransfer/receiver/protocol/FrameParser.java \
        receiver/app/src/test/java/com/qrtransfer/receiver/protocol/FrameParserTest.java \
        receiver/app/src/test/resources/hello.frames.bin
git commit -m "feat(receiver): add FrameParser against golden fixture"
```

---

### Task 8: Reassembler（累积/去重/判完成/组装/校验）

**Files:**
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/protocol/Reassembler.java`
- Test: `receiver/app/src/test/java/com/qrtransfer/receiver/protocol/ReassemblerTest.java`

**Interfaces:**
- Consumes: `Frame`（Task 7）
- Produces:
  - `void Reassembler.accept(Frame f)`
  - `boolean Reassembler.isComplete()`
  - `byte[] Reassembler.assemble()`
  - `boolean Reassembler.verify()`
  - `String Reassembler.fileName()`
  - `int Reassembler.receivedChunks()`
  - `int Reassembler.totalChunks()`

- [ ] **Step 1: 写失败测试**

```java
package com.qrtransfer.receiver.protocol;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ReassemblerTest {

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    @Test
    public void reassemblesOutOfOrderAndDedupes() throws Exception {
        byte[] all = "Hello, QR!".getBytes(StandardCharsets.UTF_8);
        Reassembler r = new Reassembler();
        r.accept(Frame.data(2, 1, Arrays.copyOfRange(all, 3, all.length)));
        r.accept(Frame.data(2, 0, Arrays.copyOfRange(all, 0, 3)));
        r.accept(Frame.data(2, 0, Arrays.copyOfRange(all, 0, 3))); // 重复，应忽略
        r.accept(Frame.metadata(2, "hello.txt", all.length, sha256(all)));

        assertTrue(r.isComplete());
        assertEquals(2, r.receivedChunks());
        assertTrue(r.verify());
        assertArrayEquals(all, r.assemble());
        assertEquals("hello.txt", r.fileName());
    }

    @Test
    public void notCompleteUntilAllChunksAndMetadata() {
        Reassembler r = new Reassembler();
        r.accept(Frame.data(2, 0, new byte[]{1, 2}));
        assertFalse(r.isComplete());
        r.accept(Frame.data(2, 1, new byte[]{3}));
        assertFalse(r.isComplete()); // 缺 metadata
    }

    @Test
    public void verifyFailsOnCorruptData() throws Exception {
        byte[] all = "Hello, QR!".getBytes(StandardCharsets.UTF_8);
        Reassembler r = new Reassembler();
        r.accept(Frame.data(1, 0, new byte[]{9, 9, 9})); // 内容错误
        r.accept(Frame.metadata(1, "hello.txt", all.length, sha256(all)));
        assertTrue(r.isComplete());
        assertFalse(r.verify());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd receiver && ./gradlew :app:testDebugUnitTest --tests "com.qrtransfer.receiver.protocol.ReassemblerTest"`
Expected: 编译失败（`Reassembler` 不存在）

- [ ] **Step 3: 写实现 Reassembler**

```java
package com.qrtransfer.receiver.protocol;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Reassembler {
    private int totalChunks = -1;
    private String fileName;
    private long fileSize;
    private byte[] sha256;
    private byte[][] chunks;
    private int received;

    public void accept(Frame f) {
        if (f.kind == Frame.Kind.METADATA) {
            this.totalChunks = f.totalChunks;
            this.fileName = f.fileName;
            this.fileSize = f.fileSize;
            this.sha256 = f.sha256;
            if (chunks == null) chunks = new byte[f.totalChunks][];
            else if (chunks.length != f.totalChunks) chunks = new byte[f.totalChunks][];
        } else if (f.kind == Frame.Kind.DATA) {
            if (chunks == null || chunks.length != f.totalChunks) chunks = new byte[f.totalChunks][];
            if (chunks[f.chunkIndex] == null) {
                chunks[f.chunkIndex] = f.data;
                received++;
            }
        }
    }

    public boolean isComplete() {
        return totalChunks >= 0 && received == totalChunks;
    }

    public byte[] assemble() {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) fileSize);
        for (byte[] c : chunks) out.write(c, 0, c.length);
        return out.toByteArray();
    }

    public boolean verify() {
        byte[] assembled = assemble();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(assembled);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        return MessageDigest.isEqual(sha256, digest);
    }

    public String fileName() { return fileName; }
    public int receivedChunks() { return received; }
    public int totalChunks() { return totalChunks; }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd receiver && ./gradlew :app:testDebugUnitTest --tests "com.qrtransfer.receiver.protocol.ReassemblerTest"`
Expected: PASS（3 个测试）

- [ ] **Step 5: 提交**

```bash
git add receiver/app/src/main/java/com/qrtransfer/receiver/protocol/Reassembler.java \
        receiver/app/src/test/java/com/qrtransfer/receiver/protocol/ReassemblerTest.java
git commit -m "feat(receiver): add Reassembler with dedup, ordering, and SHA-256 verify"
```

---

## Phase 4 — 接收端相机与会话

### Task 9: QrAnalyzer（CameraX + ZXing 解码）

**Files:**
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/camera/QrAnalyzer.java`

**Interfaces:**
- Consumes: CameraX、ZXing core
- Produces:
  - `interface QrAnalyzer.Listener { void onFrame(byte[] frameBytes); }`
  - `class QrAnalyzer implements androidx.camera.core.ImageAnalysis.Analyzer`

说明：相机解码属设备相关行为，无法在 JVM 单测中覆盖，本任务以**真机手动验证**为准。

- [ ] **Step 1: 写实现 QrAnalyzer**

```java
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
```

- [ ] **Step 2: 提交**

```bash
git add receiver/app/src/main/java/com/qrtransfer/receiver/camera/QrAnalyzer.java
git commit -m "feat(receiver): add CameraX QrAnalyzer with ZXing decoding"
```

---

### Task 10: TransferSession + MainActivity（校验 + 写盘 + UI）

**Files:**
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/session/TransferSession.java`
- Create: `receiver/app/src/main/java/com/qrtransfer/receiver/ui/MainActivity.java`

**Interfaces:**
- Consumes: `Reassembler`（Task 8）、`QrAnalyzer`（Task 9）
- Produces: 可运行的真机 App，扫描发送端 → 重组 → 保存到 Downloads

说明：文件写入 `Downloads`、相机绑定为设备相关，真机手动验证。

- [ ] **Step 1: 写 TransferSession**

```java
package com.qrtransfer.receiver.session;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import com.qrtransfer.receiver.protocol.Reassembler;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public final class TransferSession {
    private final Context context;
    private final Reassembler reassembler = new Reassembler();

    public TransferSession(Context context) { this.context = context; }

    public void onFrame(byte[] frameBytes) {
        try {
            reassembler.accept(com.qrtransfer.receiver.protocol.FrameParser.parse(frameBytes));
        } catch (IllegalArgumentException ignored) {
            return; // 非法帧，忽略
        }
        if (reassembler.isComplete()) {
            complete();
        }
    }

    public String progressText() {
        if (reassembler.totalChunks() < 0) return "等待文件…";
        return String.format(Locale.US, "%d / %d 块", reassembler.receivedChunks(), reassembler.totalChunks());
    }

    public int progressPercent() {
        int total = reassembler.totalChunks();
        if (total <= 0) return 0;
        return (int) (100L * reassembler.receivedChunks() / total);
    }

    private void complete() {
        if (!reassembler.verify()) {
            Toast.makeText(context, "校验失败，请重扫", Toast.LENGTH_LONG).show();
            return;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File out = new File(dir, sanitize(reassembler.fileName()));
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(reassembler.assemble());
            Toast.makeText(context, "已保存: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "写入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String sanitize(String name) {
        String s = name == null || name.isEmpty() ? "received_file" : name;
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.length() > 120 ? s.substring(s.length() - 120) : s;
    }
}
```

- [ ] **Step 2: 写 MainActivity**

```java
package com.qrtransfer.receiver.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.qrtransfer.receiver.camera.QrAnalyzer;
import com.qrtransfer.receiver.session.TransferSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;

    private PreviewView previewView;
    private TextView status;
    private ProgressBar progress;
    private TransferSession session;
    private ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        session = new TransferSession(this);
        findViewById(R.id.restart).setOnClickListener(v -> {
            session = new TransferSession(this);
            progress.setProgress(0);
            status.setText("等待文件…");
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, new QrAnalyzer(bytes ->
                        runOnUiThread(() -> onFrame(bytes))));

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                status.setText("相机启动失败: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onFrame(byte[] bytes) {
        session.onFrame(bytes);
        status.setText(session.progressText());
        progress.setProgress(session.progressPercent());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
    }
}
```

- [ ] **Step 3: 写布局 res/layout/activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <TextView
        android:id="@+id/status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:padding="12dp"
        android:background="#CC000000"
        android:textColor="#FFFFFF"
        android:text="等待文件…"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <ProgressBar
        android:id="@+id/progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:max="100"
        app:layout_constraintBottom_toTopOf="@id/status"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <Button
        android:id="@+id/restart"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="重新开始"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

布局用到 `ConstraintLayout`，需在 `app/build.gradle` 的 dependencies 增加：

```groovy
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
```

- [ ] **Step 4: 真机手动验证**

用 Android Studio 打开 `receiver/`，连真机运行。在电脑上启动发送端播放二维码，手机对准屏幕：
Expected: 状态文字从「等待文件…」→「x / N 块」→ 收齐后弹出「已保存: …/Downloads/hello.txt」，手机 `Downloads` 里出现该文件且内容为 `Hello, QR!`。

- [ ] **Step 5: 提交**

```bash
git add receiver/app/src/main/java/com/qrtransfer/receiver/session/TransferSession.java \
        receiver/app/src/main/java/com/qrtransfer/receiver/ui/MainActivity.java \
        receiver/app/src/main/res/layout/activity_main.xml \
        receiver/app/build.gradle
git commit -m "feat(receiver): add TransferSession and MainActivity camera UI"
```

---

## Phase 5 — 端到端与文档

### Task 11: 端到端冒烟 + README

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: 全部已完成模块
- Produces: 可复现的端到端流程文档

- [ ] **Step 1: 写 README.md**

```markdown
# QR File Transfer（纯二维码文件传输）

把文件编码成多个二维码在电脑屏幕循环显示，手机用本 App 扫码重组文件。传输通道只有二维码，无网络/蓝牙/USB。

## 结构
- `sender/` — Java 17 + Swing 发送端（Maven）
- `receiver/` — Android 接收端（Gradle）
- `protocol/` — golden 测试向量，锁定两端协议一致性

## 发送端运行
    cd sender && mvn -q exec:java -Dexec.mainClass=com.qrtransfer.sender.Main

选文件 → 选块大小/帧率 → 点「开始」全屏循环显示二维码。

## 接收端运行
用 Android Studio 打开 `receiver/`，真机运行，授权相机，对准发送端屏幕即可。

## 协议要点
- 固定分块 + 循环轮播 + 全文件 SHA-256 校验
- 块大小 256/512/1024（默认 512），ECC M，~5fps
- 详见 `docs/superpowers/specs/2026-08-26-qr-file-transfer-design.md`

## 测试
- 发送端：`cd sender && mvn -q test`
- 接收端：`cd receiver && ./gradlew :app:testDebugUnitTest`
```

- [ ] **Step 2: 端到端冒烟**

在电脑跑发送端播放一个几 KB 的文本文件，真机扫描，确认文件内容一致、SHA-256 校验通过、`Downloads` 里文件完整。

- [ ] **Step 3: 提交**

```bash
git add README.md
git commit -m "docs: add README with run and test instructions"
```

---

## Self-Review 备注（已内联修正）

- **Spec 覆盖**：协议常量/帧格式（Task 3/7）、分块+SHA-256（Task 2）、轮播（Task 5）、去重/完成判据/校验（Task 8）、扫码（Task 9）、写盘+通知（Task 10）、错误处理（Task 7/8/10 内联：坏 magic、短帧、非法帧忽略、校验失败提示、文件名净化）、测试策略（各 Task 的单测 + golden fixture + Task 11 冒烟）。全部覆盖。
- **类型一致性**：`FrameEncoder.encodeMetadata/encodeData` 签名、`FrameParser.parse`、`Reassembler.accept/isComplete/assemble/verify`、`QrAnalyzer.Listener.onFrame` 在各 Task 的 Consumes/Produces 中一致。
- **占位符清理**：已修正 Task 3 生成 fixture 的混乱命令；已删除空 `reset()`，改为 `progressPercent()` 并接入进度条；已补齐大文件软提示（Task 5）、窗口过小警告（Task 5）、接收端「重新开始」按钮（Task 10）。
