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
