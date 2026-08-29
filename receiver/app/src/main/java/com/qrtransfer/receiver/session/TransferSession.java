package com.qrtransfer.receiver.session;

import android.content.Context;
import android.os.Environment;

import com.qrtransfer.receiver.protocol.Reassembler;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public final class TransferSession {

    public interface Listener {
        void onFileSaved(File file);
        void onSaveError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Reassembler reassembler = new Reassembler();
    private boolean completed = false;

    public TransferSession(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void onFrame(byte[] frameBytes) {
        try {
            reassembler.accept(com.qrtransfer.receiver.protocol.FrameParser.parse(frameBytes));
        } catch (IllegalArgumentException ignored) {
            return; // 非法帧，忽略
        }
        if (reassembler.isComplete() && !completed) {
            completed = true;
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

    public boolean hasMetadata() {
        return reassembler.totalChunks() >= 0;
    }

    public java.util.List<Integer> missingChunks() {
        return reassembler.missingChunks();
    }

    private void complete() {
        if (!reassembler.verify()) {
            listener.onSaveError("校验失败，请重扫");
            return;
        }
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();
        File out = new File(dir, sanitize(reassembler.fileName()));
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(reassembler.assemble());
            listener.onFileSaved(out);
        } catch (Exception e) {
            listener.onSaveError("写入失败: " + e.getMessage());
        }
    }

    private static String sanitize(String name) {
        String s = name == null || name.isEmpty() ? "received_file" : name;
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.length() > 120 ? s.substring(s.length() - 120) : s;
    }
}
