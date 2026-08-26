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
