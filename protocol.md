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
