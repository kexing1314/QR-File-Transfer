# 纯二维码文件传输工具 — 设计文档

- 日期：2026-08-26
- 状态：待审阅
- 分类：架构级（全新项目，两个子模块）

## 1. 目标与范围

一个"纯二维码"文件传输工具：把文件编码成多个二维码，在电脑屏幕上循环显示；手机用原生 Android App 连续扫码并重组文件。

**核心约束**：两端之间唯一的传输通道是**视觉二维码** —— 无网络、无蓝牙、无 USB，单向（PC → 手机）。

范围：

- 发送端：Java 17 + Swing 桌面 GUI（`sender` 模块）
- 接收端：原生 Android App（`receiver` 模块，CameraX + ZXing）
- 一个仓库、两个模块，本次都做
- 先做小文件（几 KB ~ 几 MB），单二维码动画，协议预留多码网格 / 喷泉码扩展

## 2. 整体架构与仓库结构

```
qr-file-transfer-util/
├── pom.xml                  # Maven 聚合根（parent），管理 sender
├── sender/                  # 【发送端】Java 17 + Swing，Maven 模块
│   ├── pom.xml
│   └── src/main/java/...
├── receiver/                # 【接收端】Android，独立 Gradle 工程
│   ├── settings.gradle / build.gradle
│   └── app/src/...
└── protocol/                # 【共享契约】golden 测试向量，非被引用代码
    └── ...
```

关键点：

- 发送端与接收端是**两个独立可构建、可测试**的单元，通过「协议文档 + 测试向量」耦合，而非代码依赖。
- 帧编解码逻辑两端各写一份（约几十行纯函数），用同一套 golden 测试向量保证二者行为一致，避免跨 Maven/Gradle 共享库的复杂度。
- `protocol/` 目录放测试数据（样例文件 + 期望的帧字节序列），供两端做一致性测试。

## 3. 传输协议 v1

### 3.1 编码模式

QR 码用 **byte mode 承载二进制字节**（不 base64，省 33% 空间）。发送端 ZXing `QRCodeWriter` 与接收端 ZXing 解码器同族，字节语义一致。

### 3.2 固定常量（结构性，v1 写死）

| 常量 | 值 |
|------|-----|
| `magic` | `"QRT1"`（0x51 0x52 0x54 0x31） |
| `version` | `1` |

### 3.3 可配置参数（运行参数，两端用同一组值，不进帧头）

| 参数 | 默认 | 说明 |
|------|------|------|
| `CHUNK_SIZE` | **512 字节** | 三档可调：256（最稳）/ 512（均衡）/ 1024（更快更密）。决定 QR 版本与扫码难度 |
| ECC 级别 | `M`（15%） | 可调 L/M/Q/H；越高越抗干扰，但同样数据需要更高版本 |
| 帧率 | ~5 帧/秒 | 可调 2~5；越低每帧停留越久、越易扫，但吞吐越低 |
| 显示尺寸 | 自适应填满窗口 | QR 尽量大，静区 ≥ 4 模块，模块 ≥ ~8px |

`CHUNK_SIZE` 变化**不影响协议兼容性**：接收端从每个数据帧的实际字节长度即可知块大小（仅最后一帧可能更短），`totalChunks` 已在公共头里。只需收发两端用同一个值。

### 3.4 帧格式

每个二维码 = 一帧，二进制结构，公共头 10 字节：

```
byte[0..3]   magic        = "QRT1"
byte[4]      version      = 1
byte[5]      type         # 0x01 = METADATA，0x02 = DATA
byte[6..9]   totalChunks  (uint32 大端)  # 数据帧总数，每个帧都带
```

**METADATA 帧（type 0x01）**，公共头之后：

```
byte[10..11] nameLen  (uint16 大端)
byte[..]     fileName (UTF-8)
byte[..]     fileSize (uint64 大端)
byte[..]     sha256   (32 字节，整文件)
```

**DATA 帧（type 0x02）**，公共头之后：

```
byte[10..13] chunkIndex (uint32 大端)   # 0-based
byte[14..]   chunkData  (≤ CHUNK_SIZE)
```

### 3.5 轮播顺序与关键性质

轮播顺序：`[METADATA, DATA_0, DATA_1, ..., DATA_{N-1}]` 无限循环。

- `totalChunks` 放在**每个**帧的公共头里 —— 接收端中途加入（错过 METADATA）也能立刻知道总数并开始收集数据帧。
- 接收端按 `chunkIndex` **去重**（同一帧在屏上停留 ~200ms，30fps 相机会重复扫到几十次）。
- **完成判据** = 收齐 `totalChunks` 个 DATA 帧 + 收到 METADATA + SHA-256 校验通过。
- 漏帧由**轮播自然补齐**，无需反向信道，严格单向、纯二维码。

## 4. 发送端组件（Java 17 + Swing）

| 组件 | 职责 | 依赖 |
|------|------|------|
| `TransferManifest` | 值对象：文件名、大小、SHA-256、`totalChunks`、`chunkSize` | 无 |
| `FileChunker` | 读文件 → 算 SHA-256 → 产出 `TransferManifest` + 字节块序列（懒加载） | `TransferManifest` |
| `FrameEncoder` | manifest + 块序列 → 协议帧字节（METADATA + DATA）。纯函数、无 I/O | `TransferManifest` |
| `QrRenderer` | 帧字节 → `BufferedImage`（ZXing `QRCodeWriter`，按 ECC/尺寸渲染，留静区） | ZXing core |
| `FramePlayer` | Swing 面板：`SwingWorker` 懒渲染下一帧，`javax.swing.Timer` 循环播放；显示进度 + 开始/停止 | `QrRenderer` |
| `MainFrame` | 文件选择器 + 参数设置（块大小三档、帧率、ECC）+ 启动播放 | 上述所有 |

数据流：选文件 → `FileChunker` 分块 → `FrameEncoder` 成帧 → `FramePlayer` 逐帧懒渲染 → 全屏循环显示。

关键点：二维码**懒渲染**（只渲染当前 + 预取下一帧），内存占用有界，不会因几千帧占满内存。

## 5. 接收端组件（Android）

| 组件 | 职责 | 依赖 |
|------|------|------|
| `FrameParser` | 解码字节 → `Frame`（metadata/data），`FrameEncoder` 的镜像。纯函数 | 无 |
| `Reassembler` | 保存 metadata + 按 `chunkIndex` 去重存块；跟踪进度；判断是否收齐。线程安全（单线程消费） | `FrameParser` |
| `CameraScanner` | CameraX `ImageAnalysis` + ZXing 解码器，持续输出识别到的字节 | CameraX、ZXing |
| `TransferSession` | 收齐后：SHA-256 校验 → 写 `Downloads` → 成功通知；失败提示重扫 | `Reassembler` |
| `MainActivity` | UI：相机预览 + 进度条（已收/总）+ 结果提示 + 重新开始 | 上述所有 |

数据流：相机帧 → `CameraScanner` 解码 → `FrameParser` 解析 → `Reassembler` 累积/去重 → 收齐 → `TransferSession` 校验写盘。

关键点：

- **去重**必须：按 `chunkIndex` 忽略已收块。
- 相机帧与重组用**单线程消费**（CameraX analyzer 线程），避免加锁复杂度。
- 扫码引擎选 **ZXing**（与发送端同族、开源、无 Google Play Services 依赖）；若识别率不够再评估 Google ML Kit。

## 6. 错误处理与边界情况

| 场景 | 处理 |
|------|------|
| 空文件（0 字节） | 正常处理：只有 METADATA、`totalChunks=0`，接收端直接写空文件 |
| 大文件 | 协议用 uint32 存块数，上限很大；实际**软提示**：> ~20MB 警告"传输会很慢"，不设硬限制 |
| 文件名非法/过长/非 UTF-8 | 接收端**净化**：截断到合理长度、剔除非法字符，兜底名 `received_file` |
| SHA-256 校验失败 | 接收端报错 + 提供"重新扫描"，不写盘 |
| 相机瞬时解码失败 | 忽略单次失败，保留"最近一次成功"状态 |
| 用户中途停止/重启 | 发送端可停止；接收端"重新开始"清空 `Reassembler` 状态 |
| 乱序/重复扫描 | 按 `chunkIndex` 去重、存 map 乱序收集，最后按索引顺序写盘 |
| 二维码太密扫不出 | 切到 256 字节档、降低帧率 |
| 中途加入（错过开头） | 无需等 METADATA 也能先收数据帧（`totalChunks` 每帧都有），METADATA 下一轮补上 |
| 窗口过小 | 发送端设最小显示尺寸，过小则警告 |

## 7. 测试策略

**发送端（JUnit）**
- `FileChunker`：分块边界、SHA-256 正确性、round-trip
- `FrameEncoder` ↔ `FrameParser` round-trip：编码再解析，字节完全一致
- `QrRenderer` → ZXing 解码 round-trip：字节经二维码编解码后无损

**接收端（JVM 单测，无需真机）**
- `FrameParser`：合法帧 / 非法帧 / 截断帧
- `Reassembler`：乱序 + 重复 + 丢帧输入下，仅当收齐才判完成、SHA-256 校验通过
- 属性式测试：随机丢/重排后重喂，最终仍能重组

**协议一致性（关键）**
- `protocol/` 放 golden 测试向量：样例文件 + 期望帧字节序列。两端各自跑同一组向量，保证两套独立实现不漂移。

**端到端**
- 手动冒烟：真机摄像头扫一个正在播放的发送端，完整走一遍传文件。

## 8. 已知限制与未来扩展

- 单二维码动画吞吐约 4~6KB/s；大文件一轮周期长，中途加入等待时间长。
- 未来升级路径（协议 `version` 字段已预留）：多码网格提升吞吐、METADATA 更高频插入、喷泉码抗丢帧。

## 9. 技术栈

- 发送端：Java 17、Maven、Swing（JDK 内置）、ZXing core（二维码生成）
- 接收端：Android、Gradle、CameraX、ZXing（扫码解码）
- 测试：JUnit（发送端）、JUnit + Robolectric 或纯 JVM 单测（接收端）
