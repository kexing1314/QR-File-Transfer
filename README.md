# 纯二维码文件传输工具（QR File Transfer）

一个「纯二维码」文件传输工具：把文件编码成多张二维码，在电脑屏幕上循环显示；手机用 App 连续扫码，重组并保存文件。

传输通道**只有视觉二维码**——不联网、不用蓝牙、不插数据线，严格单向（电脑 → 手机）。

## 工作原理

1. 发送端把文件切成固定大小的数据块，每块生成一张二维码；另加一张「元数据」二维码，携带文件名、大小、总块数、整文件 SHA-256。
2. 屏幕按 `[元数据, 块0, 块1, ...]` 的顺序**循环播放**二维码。
3. 接收端持续扫码，按块号去重、乱序收集；因为发送端一直循环，漏掉的帧下一轮会被补上。
4. 收齐所有块后，用 SHA-256 校验整文件，通过则保存。

## 项目结构

```
qr-file-transfer-util/
├── sender/      Java 17 + Swing 桌面发送端（Maven）
├── receiver/    Android 接收端（Gradle + CameraX + ZXing）
├── protocol/    golden 测试向量（锁定两端协议一致，防止漂移）
└── docs/        设计文档（spec）与实现计划（plan）
```

## 传输协议（要点）

- 每张二维码 = 一帧，二进制结构，公共头 10 字节大端：`magic("QRT1") + version(1) + type(1) + totalChunks(4)`
- 两种帧类型：`METADATA`（文件名 + 大小 + SHA-256）和 `DATA`（块号 + 数据）
- `totalChunks` 写进**每一帧**，接收端中途加入也能立刻知道总数
- 固定分块 + 循环轮播 + 全文件 SHA-256 校验，无需反向信道
- 完整格式见 `docs/superpowers/specs/2026-08-26-qr-file-transfer-design.md`

## 功能特性

**发送端（桌面 GUI）**
- 选择文件，可选块大小（256 / 512 / 1024，默认 512）与帧率（2~15，默认 5）
- 全屏循环播放，状态栏显示「当前: 块 N / 共 M 张」
- 传输中可**实时调整**帧率和二维码尺寸
- 手动补漏：输入块序号显示指定块的二维码，或单独显示元数据（配合接收端「查看缺失」）

**接收端（手机 App）**
- CameraX 实时扫码 + 进度条
- 「查看缺失」：查看哪些块还没收到
- 收齐后 SHA-256 校验、自动保存，弹「打开 / 分享」对话框

## 运行

### 发送端（需要 JDK 17+ 和 Maven）

```bash
cd sender
mvn exec:java -Dexec.mainClass=com.qrtransfer.sender.Main
```

选文件 → 设置块大小/帧率 → 点「选择文件并开始传输」→ 全屏循环播放二维码。

### 接收端（需要 Android Studio + Android SDK API 34）

1. 用 Android Studio 打开 `receiver/` 目录
2. 连接真机，点 Run 安装运行
3. 授权相机，对准发送端屏幕即可

> 接收的文件保存在 app 私有目录 `Android/data/com.qrtransfer.receiver/files/Download/`；收完可在对话框里直接「打开 / 分享」。

## 测试

- 发送端：`cd sender && mvn test`（8 个单元测试）
- 接收端核心逻辑：`cd receiver && ./gradlew :app:testDebugUnitTest`（Windows 用 `gradlew.bat`）
- 协议一致性：两端共用 `protocol/` 下的 golden 测试向量

## 技术栈

| 模块 | 技术 |
|------|------|
| 发送端 | Java 17、Swing、ZXing core、Maven、JUnit 5 |
| 接收端 | Android、CameraX、ZXing core、Gradle、JUnit 4 |

## 学习要点（本项目踩过/解决的技术点）

- 跨端二进制协议的**一致性设计**：两端独立实现，用 golden 测试向量锁死字节格式
- QR 码的**容量 / 密度 / 纠错级别**对扫描成功率的影响（屏幕比纸面更难扫）
- CameraX `ImageAnalysis` + ZXing 解码，含 Y 平面 `rowStride` 对齐处理
- Android 7.0+ 的 `FileProvider`（content:// URI）文件分享
- `Short.toUnsignedInt` 等 API 的 **minSdk 兼容**问题
- Kotlin stdlib 传递依赖版本冲突（duplicate class）的解决
- 纯二维码传输的吞吐上限（约 KB/s），以及多码网格、喷泉码等扩展方向
