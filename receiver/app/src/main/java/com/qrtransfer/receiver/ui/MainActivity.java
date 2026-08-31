package com.qrtransfer.receiver.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.common.util.concurrent.ListenableFuture;
import com.qrtransfer.receiver.R;
import com.qrtransfer.receiver.camera.QrAnalyzer;
import com.qrtransfer.receiver.session.TransferSession;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String FILE_PROVIDER_AUTHORITY = "com.qrtransfer.receiver.fileprovider";

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
        resetSession();
        findViewById(R.id.restart).setOnClickListener(v -> resetSession());
        findViewById(R.id.showMissing).setOnClickListener(v -> showMissingDialog());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void resetSession() {
        session = new TransferSession(this, new TransferSession.Listener() {
            @Override
            public void onFileSaved(File file) {
                showFileSavedDialog(file);
            }

            @Override
            public void onSaveError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        progress.setProgress(0);
        status.setText("等待文件…");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1920, 1080))
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

    private void showFileSavedDialog(File file) {
        Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file);
        String detectedType = getContentResolver().getType(uri);
        final String mime = detectedType != null ? detectedType : "application/octet-stream";

        new AlertDialog.Builder(this)
                .setTitle("接收完成")
                .setMessage("已保存: " + file.getAbsolutePath())
                .setPositiveButton("打开", (d, w) -> openFile(uri, mime))
                .setNegativeButton("分享", (d, w) -> shareFile(uri, mime))
                .setNeutralButton("关闭", null)
                .show();
    }

    private void openFile(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivitySafe(intent);
    }

    private void shareFile(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivitySafe(Intent.createChooser(intent, "分享文件"));
    }

    private void startActivitySafe(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可处理此文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMissingDialog() {
        java.util.List<Integer> missing = session.missingChunks();
        StringBuilder sb = new StringBuilder();
        if (!session.hasMetadata()) {
            sb.append("元数据未收到（缺文件名等信息）\n");
        }
        if (missing.isEmpty()) {
            sb.append("数据块已收齐");
        } else {
            sb.append("缺失块（").append(missing.size()).append(" 个）:\n").append(missing);
        }
        new AlertDialog.Builder(this)
                .setTitle("接收状态")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
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
