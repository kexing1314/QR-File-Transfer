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
            new JComboBox<>(new Integer[]{2, 3, 4, 5, 8, 10, 15});

    public MainFrame() {
        super("QR File Transfer — 发送端");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 8));

        JLabel title = new JLabel("纯二维码文件传输 · 发送端", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 4, 0));
        add(title, BorderLayout.NORTH);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("传输设置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0;
        settings.add(new JLabel("块大小:"), gbc);
        gbc.gridx = 1;
        settings.add(chunkSizeBox, gbc);
        chunkSizeBox.setSelectedItem(256);

        gbc.gridx = 0; gbc.gridy = 1;
        settings.add(new JLabel("帧率:"), gbc);
        gbc.gridx = 1;
        settings.add(fpsBox, gbc);
        fpsBox.setSelectedItem(5);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        JButton pick = new JButton("选择文件并开始传输");
        pick.setPreferredSize(new Dimension(260, 40));
        settings.add(pick, gbc);

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(12, 48, 12, 48));
        center.add(settings, BorderLayout.NORTH);

        JLabel hint = new JLabel(
                "<html><div style='text-align:center'>"
                        + "选择文件后弹出全屏二维码播放窗口，手机扫码即可接收。<br>"
                        + "传输中可实时调整帧率与二维码尺寸。</div></html>",
                SwingConstants.CENTER);
        hint.setBorder(BorderFactory.createEmptyBorder(12, 0, 20, 0));
        add(hint, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);

        pick.addActionListener(e -> onPick());
        setSize(540, 440);
        setLocationRelativeTo(null);
        setResizable(false);
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
        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        JButton startStop = new JButton("开始");

        // 传输控制行：帧率 + 尺寸 + 张数 + 排列（可随时改）
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

        JPanel manual = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        manual.setBorder(BorderFactory.createTitledBorder("手动补漏"));
        JTextField chunkField = new JTextField(6);
        JButton showChunkBtn = new JButton("显示块");
        JButton showMetaBtn = new JButton("显示元数据");
        manual.add(new JLabel("块序号:"));
        manual.add(chunkField);
        manual.add(showChunkBtn);
        manual.add(showMetaBtn);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(manual, BorderLayout.WEST);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(startStop, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        south.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        south.add(transmission, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.SOUTH);
        playerFrame.add(south, BorderLayout.SOUTH);

        showChunkBtn.addActionListener(e -> {
            try {
                int chunk = Integer.parseInt(chunkField.getText().trim());
                if (chunk < 0 || chunk >= player.chunkCount()) {
                    JOptionPane.showMessageDialog(playerFrame,
                            "块序号需在 0 ~ " + (player.chunkCount() - 1) + " 之间");
                    return;
                }
                player.showChunk(chunk);
                startStop.setText("开始");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(playerFrame, "请输入有效的块序号");
            }
        });
        showMetaBtn.addActionListener(e -> {
            player.showMetadata();
            startStop.setText("开始");
        });

        liveFpsBox.addActionListener(e -> {
            int newFps = (Integer) liveFpsBox.getSelectedItem();
            player.setFrameIntervalMs(1000 / newFps);
        });
        sizeSlider.addChangeListener(e -> player.setSizeScale(sizeSlider.getValue() / 100.0));
        gridSizeBox.addActionListener(e -> player.setGridSize((Integer) gridSizeBox.getSelectedItem()));
        layoutBox.addActionListener(e -> player.setArrangement((GridArrangement) layoutBox.getSelectedItem()));

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
                "当前: " + player.currentLabel() + " / 共 " + player.screenCount() + " 屏"))
                .start();
        playerFrame.setVisible(true);
    }
}
