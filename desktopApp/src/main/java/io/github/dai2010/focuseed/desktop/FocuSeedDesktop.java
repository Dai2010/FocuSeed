package io.github.dai2010.focuseed.desktop;

import io.github.dai2010.focuseed.core.FocusPhase;
import io.github.dai2010.focuseed.core.FocusSession;
import io.github.dai2010.focuseed.core.FocusSettings;
import io.github.dai2010.focuseed.core.FocusSnapshot;
import io.github.dai2010.focuseed.core.UpdateChecker;
import io.github.dai2010.focuseed.core.UpdateInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class FocuSeedDesktop {
    private static final Color BACKGROUND_TOP = new Color(255, 246, 236);
    private static final Color BACKGROUND_BOTTOM = new Color(232, 249, 239);
    private static final Color CARD = new Color(255, 255, 255, 226);
    private static final Color TEXT = new Color(82, 65, 72);
    private static final Color MUTED = new Color(137, 118, 128);
    private static final Color MINT = new Color(112, 213, 176);
    private static final Color PEACH = new Color(255, 166, 148);
    private static final Color PINK = new Color(255, 214, 224);
    private static final int EXIT_CHANCES = 3;

    private final JFrame frame = new JFrame("FocuSeed");
    private final Font cuteFont = loadCuteFont();
    private final JLabel phaseLabel = new JLabel("准备发芽 🌱", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("00:00", SwingConstants.CENTER);
    private final JLabel roundLabel = new JLabel("轮次 0 / 0", SwingConstants.CENTER);
    private final JLabel hintLabel = new JLabel("休息时可以退出；工作开始会自动回到全屏。", SwingConstants.CENTER);
    private final JLabel platformLabel = new JLabel(platformText(), SwingConstants.CENTER);
    private final JLabel updateLabel = new JLabel("更新：启动时会静默检查", SwingConstants.CENTER);
    private final JSpinner workMinutes = new JSpinner(new SpinnerNumberModel(25, 1, 240, 1));
    private final JSpinner breakMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 120, 1));
    private final JSpinner rounds = new JSpinner(new SpinnerNumberModel(4, 1, 24, 1));
    private final FocusSession session = new FocusSession(new FocusSettings(25, 5, 4));
    private final Timer timer = new Timer(500, event -> render());

    private boolean fullscreen;
    private int exitChancesLeft = EXIT_CHANCES;
    private FocusPhase hiddenPhase = FocusPhase.IDLE;
    private int hiddenRound = -1;
    private long hiddenUntilMillis;
    private JButton updateDownloadButton;
    private JButton updateAccelerateButton;
    private UpdateInfo pendingUpdate;
    private Thread updateDownloadThread;
    private int updateDownloadGeneration;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FocuSeedDesktop().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                handleWindowClosing();
            }
        });
        setWindowIcon();
        frame.setLayout(new BorderLayout());
        frame.setContentPane(shell());
        frame.setMinimumSize(new Dimension(900, 680));
        frame.setSize(980, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        timer.start();
        checkForUpdatesSilently();
    }

    private JPanel shell() {
        JPanel shell = new GradientPanel();
        shell.setLayout(new GridBagLayout());
        shell.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        JPanel card = roundedPanel(CARD, 34);
        card.setLayout(new BorderLayout(20, 20));
        card.setBorder(BorderFactory.createEmptyBorder(30, 34, 30, 34));
        card.add(header(), BorderLayout.NORTH);
        card.add(center(), BorderLayout.CENTER);
        card.add(controls(), BorderLayout.SOUTH);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        shell.add(card, constraints);
        return shell;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 0, 6));
        panel.setOpaque(false);

        JLabel title = new JLabel("FocuSeed 软萌专注花园", SwingConstants.CENTER);
        title.setForeground(TEXT);
        title.setFont(cuteFont.deriveFont(Font.PLAIN, 42f));

        JLabel subtitle = new JLabel("种下一颗番茄种子，把注意力轻轻收回来 ✨", SwingConstants.CENTER);
        subtitle.setForeground(MUTED);
        subtitle.setFont(cuteFont.deriveFont(Font.PLAIN, 18f));

        platformLabel.setForeground(MUTED);
        platformLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 14f));
        hintLabel.setForeground(MUTED);
        hintLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 15f));
        updateLabel.setForeground(MUTED);
        updateLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 14f));

        panel.add(title);
        panel.add(subtitle);
        panel.add(hintLabel);
        panel.add(platformLabel);
        panel.add(updateLabel);
        return panel;
    }

    private JPanel center() {
        JPanel panel = roundedPanel(new Color(255, 248, 251, 210), 28);
        panel.setLayout(new GridLayout(3, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 24));

        phaseLabel.setForeground(TEXT);
        phaseLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 34f));

        timerLabel.setForeground(MINT.darker());
        timerLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 92f));

        roundLabel.setForeground(TEXT);
        roundLabel.setFont(cuteFont.deriveFont(Font.PLAIN, 24f));

        panel.add(phaseLabel);
        panel.add(timerLabel);
        panel.add(roundLabel);
        return panel;
    }

    private JPanel controls() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 14, 14));
        panel.setOpaque(false);

        JPanel settings = new JPanel(new GridLayout(1, 6, 12, 12));
        settings.setOpaque(false);
        settings.add(label("工作分钟"));
        settings.add(styleSpinner(workMinutes));
        settings.add(label("休息分钟"));
        settings.add(styleSpinner(breakMinutes));
        settings.add(label("轮数"));
        settings.add(styleSpinner(rounds));

        JPanel buttons = new JPanel(new GridLayout(1, 3, 12, 12));
        buttons.setOpaque(false);
        JButton start = button("🌱 开始种下");
        JButton exitFullscreen = button("🍵 休息退出");
        JButton stop = button("🌙 停止");
        start.addActionListener(event -> startSession());
        exitFullscreen.addActionListener(event -> requestExitDuringSession());
        stop.addActionListener(event -> stopSession());
        buttons.add(start);
        buttons.add(exitFullscreen);
        buttons.add(stop);

        JPanel updateButtons = new JPanel(new GridLayout(1, 2, 12, 12));
        updateButtons.setOpaque(false);
        updateDownloadButton = button("⬇ 下载更新");
        updateAccelerateButton = button("⚡ 切换加速下载");
        updateDownloadButton.setEnabled(false);
        updateAccelerateButton.setEnabled(false);
        updateDownloadButton.addActionListener(event -> startUpdateDownload(false));
        updateAccelerateButton.addActionListener(event -> startUpdateDownload(true));
        updateButtons.add(updateDownloadButton);
        updateButtons.add(updateAccelerateButton);

        panel.add(settings);
        panel.add(buttons);
        panel.add(updateButtons);
        return panel;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setForeground(MUTED);
        label.setFont(cuteFont.deriveFont(Font.PLAIN, 16f));
        return label;
    }

    private JSpinner styleSpinner(JSpinner spinner) {
        spinner.setFont(cuteFont.deriveFont(Font.PLAIN, 18f));
        spinner.setBorder(BorderFactory.createLineBorder(PINK, 2, true));
        JComponent editor = spinner.getEditor();
        editor.setFont(cuteFont.deriveFont(Font.PLAIN, 18f));
        return spinner;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(cuteFont.deriveFont(Font.PLAIN, 17f));
        button.setForeground(TEXT);
        button.setBackground(PINK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 189, 205), 1, true),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));
        return button;
    }

    private void startSession() {
        FocusSettings settings = new FocusSettings(
            (Integer) workMinutes.getValue(),
            (Integer) breakMinutes.getValue(),
            (Integer) rounds.getValue()
        );
        exitChancesLeft = EXIT_CHANCES;
        session.start(settings, System.currentTimeMillis());
        enterFullscreen();
        render();
    }

    private void stopSession() {
        session.stop();
        leaveFullscreen();
        render();
    }

    private void requestExitDuringSession() {
        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        if (snapshot.phase() == FocusPhase.BREAK) {
            hideUntilPhaseChanges(snapshot);
            return;
        }
        if (snapshot.phase() == FocusPhase.WORKING) {
            if (exitChancesLeft <= 0) {
                enterFullscreen();
                hintLabel.setText("三次机会已经用完啦，先陪小种子完成这一轮吧。🌱");
                return;
            }
            exitChancesLeft--;
            hideBrieflyDuringWork();
            hintLabel.setText("工作期临时退出机会剩余 " + exitChancesLeft + " 次，计时不会暂停。🐾");
            return;
        }
        frame.dispose();
        System.exit(0);
    }

    private void render() {
        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        FocusPhase phase = snapshot.phase();
        if (phase == FocusPhase.IDLE) {
            phaseLabel.setText("准备发芽 🌱");
            timerLabel.setText("00:00");
            roundLabel.setText("轮次 0 / " + rounds.getValue());
            hintLabel.setText("休息时可以退出；工作开始会自动回到全屏。剩余退出机会 " + exitChancesLeft + " 次。");
            return;
        }
        if (phase == FocusPhase.FINISHED) {
            leaveFullscreen();
            hiddenPhase = FocusPhase.IDLE;
            hiddenRound = -1;
            hiddenUntilMillis = 0L;
            phaseLabel.setText("花开完成啦 🌸");
            timerLabel.setText("00:00");
            roundLabel.setText("轮次 " + snapshot.roundText());
            hintLabel.setText("今天也认真照顾了自己的注意力。辛苦啦！");
            return;
        }
        if (phase == FocusPhase.WORKING) {
            if (isBrieflyHiddenDuringWork()) {
                return;
            }
            hiddenPhase = FocusPhase.IDLE;
            hiddenRound = -1;
            hiddenUntilMillis = 0L;
            enterFullscreen();
            phaseLabel.setText("专注发芽中 🌱");
            hintLabel.setText("工作期会保持全屏。临时退出机会剩余 " + exitChancesLeft + " 次。");
        } else {
            if (!frame.isVisible() && isTemporarilyHiddenFor(snapshot)) {
                return;
            }
            phaseLabel.setText("软软休息中 🍵");
            hintLabel.setText("休息期间可以退出应用；下一段工作会自动回到全屏。");
        }
        timerLabel.setText(snapshot.remainingText());
        roundLabel.setText("轮次 " + snapshot.roundText());
    }

    private void handleWindowClosing() {
        FocusPhase phase = session.snapshot(System.currentTimeMillis()).phase();
        if (phase == FocusPhase.IDLE || phase == FocusPhase.FINISHED) {
            frame.dispose();
            System.exit(0);
            return;
        }
        requestExitDuringSession();
    }

    private void checkForUpdatesSilently() {
        Thread thread = new Thread(() -> {
            try {
                UpdateInfo info = UpdateChecker.checkLatest(UpdateChecker.detectDesktopAssetKey());
                SwingUtilities.invokeLater(() -> onUpdateChecked(info));
            } catch (Exception ignored) {
                SwingUtilities.invokeLater(() -> updateLabel.setText("更新：静默检查失败，可稍后重启应用再试"));
            }
        }, "FocuSeed-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private void onUpdateChecked(UpdateInfo info) {
        if (!info.updateAvailable()) {
            updateLabel.setText("更新：已是最新版 v" + info.currentVersion());
            return;
        }
        pendingUpdate = info;
        updateDownloadButton.setEnabled(true);
        updateAccelerateButton.setEnabled(true);
        updateLabel.setText("发现新版本 v" + info.latestVersion() + "：" + info.assetName());
        Object[] options = {"下载更新", "稍后", "加速下载"};
        int choice = JOptionPane.showOptionDialog(
            frame,
            "当前 v" + info.currentVersion() + "，最新 v" + info.latestVersion()
                + "。\n默认从 GitHub 原地址下载到系统下载目录；如果卡住，可手动切换 ghfast 加速。",
            "发现 FocuSeed 新版本",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]
        );
        if (choice == 0) {
            startUpdateDownload(false);
        } else if (choice == 2) {
            startUpdateDownload(true);
        }
    }

    private synchronized void startUpdateDownload(boolean accelerated) {
        if (pendingUpdate == null || pendingUpdate.downloadUrl().trim().isEmpty()) {
            updateLabel.setText("更新：暂时没有可下载的更新");
            return;
        }
        updateDownloadGeneration++;
        int generation = updateDownloadGeneration;
        if (updateDownloadThread != null) {
            updateDownloadThread.interrupt();
        }
        Path downloads = downloadsDirectory();
        Path destination = downloads.resolve(pendingUpdate.assetName());
        String sourceUrl = accelerated ? UpdateChecker.acceleratedUrl(pendingUpdate.downloadUrl()) : pendingUpdate.downloadUrl();
        updateLabel.setText(accelerated ? "更新：已切换 ghfast 加速，准备下载…" : "更新：正在从 GitHub 原地址下载…");
        updateDownloadThread = new Thread(() -> downloadUpdate(sourceUrl, destination, generation), "FocuSeed-UpdateDownload");
        updateDownloadThread.setDaemon(true);
        updateDownloadThread.start();
    }

    private void downloadUpdate(String sourceUrl, Path destination, int generation) {
        HttpURLConnection connection = null;
        Path partialDestination = destination.resolveSibling(destination.getFileName() + ".part-" + generation);
        try {
            Files.createDirectories(destination.getParent());
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", "FocuSeed/" + UpdateChecker.CURRENT_VERSION);
            long total = connection.getContentLengthLong();
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(partialDestination)) {
                byte[] buffer = new byte[16_384];
                long downloaded = 0L;
                long lastUpdate = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (generation != updateDownloadGeneration || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    output.write(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 250L) {
                        lastUpdate = now;
                        setUpdateProgress(downloaded, total);
                    }
                }
            }
            if (generation == updateDownloadGeneration) {
                Files.move(partialDestination, destination, StandardCopyOption.REPLACE_EXISTING);
                SwingUtilities.invokeLater(() -> {
                    updateLabel.setText("更新：下载完成，文件已保存到 " + destination);
                    JOptionPane.showMessageDialog(
                        frame,
                        "安装包已保存到：\n" + destination + "\n\n请运行该安装包覆盖安装。Windows/Linux 包名保持一致，后续版本可直接升级。",
                        "FocuSeed 更新已下载",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                });
            }
        } catch (IOException error) {
            if (generation == updateDownloadGeneration) {
                try {
                    Files.deleteIfExists(partialDestination);
                } catch (IOException ignored) {
                }
                SwingUtilities.invokeLater(() -> updateLabel.setText("更新：下载失败，可点“切换加速下载”重试"));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (generation != updateDownloadGeneration) {
                try {
                    Files.deleteIfExists(partialDestination);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void setUpdateProgress(long downloaded, long total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0L) {
                long percent = Math.min(100L, downloaded * 100L / total);
                updateLabel.setText("更新下载进度：" + percent + "%（" + readableBytes(downloaded) + " / " + readableBytes(total) + "）");
            } else {
                updateLabel.setText("更新：正在下载 " + readableBytes(downloaded) + "，如卡住可切换加速");
            }
        });
    }

    private static Path downloadsDirectory() {
        return new File(System.getProperty("user.home", "."), "Downloads").toPath();
    }

    private static String readableBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KiB", kib);
        }
        return String.format(Locale.ROOT, "%.1f MiB", kib / 1024.0);
    }

    private void hideUntilPhaseChanges(FocusSnapshot snapshot) {
        hiddenPhase = snapshot.phase();
        hiddenRound = snapshot.currentRound();
        hiddenUntilMillis = 0L;
        leaveFullscreen();
        frame.setVisible(false);
        hintLabel.setText("已经躲进休息小窝；下一段工作会自动弹回全屏。🍵");
    }

    private void hideBrieflyDuringWork() {
        hiddenPhase = FocusPhase.IDLE;
        hiddenRound = -1;
        hiddenUntilMillis = System.currentTimeMillis() + 3_000L;
        leaveFullscreen();
        frame.setVisible(false);
    }

    private boolean isTemporarilyHiddenFor(FocusSnapshot snapshot) {
        return hiddenPhase == snapshot.phase() && hiddenRound == snapshot.currentRound();
    }

    private boolean isBrieflyHiddenDuringWork() {
        return hiddenUntilMillis > System.currentTimeMillis();
    }

    private void enterFullscreen() {
        if (fullscreen) {
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            return;
        }
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        frame.dispose();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        device.setFullScreenWindow(frame);
        fullscreen = true;
    }

    private void leaveFullscreen() {
        if (!fullscreen) {
            return;
        }
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(null);
        frame.dispose();
        frame.setUndecorated(false);
        frame.setAlwaysOnTop(false);
        frame.setVisible(true);
        fullscreen = false;
    }

    private static JPanel roundedPanel(Color color, int radius) {
        return new JPanel() {
            {
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(color);
                graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                graphics2D.dispose();
            }
        };
    }

    private Font loadCuteFont() {
        try (InputStream stream = FocuSeedDesktop.class.getResourceAsStream("/fonts/ZCOOLKuaiLe-Regular.ttf")) {
            if (stream != null) {
                return Font.createFont(Font.TRUETYPE_FONT, stream);
            }
        } catch (FontFormatException | IOException ignored) {
            JOptionPane.showMessageDialog(null, "可爱字体加载失败，将使用系统中文字体。", "FocuSeed", JOptionPane.INFORMATION_MESSAGE);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    }

    private void setWindowIcon() {
        java.net.URL iconUrl = FocuSeedDesktop.class.getResource("/icons/focuseed.png");
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }
    }

    private static String platformText() {
        String os = System.getProperty("os.name", "unknown");
        if (os.toLowerCase(Locale.ROOT).contains("linux")) {
            String desktop = env("XDG_CURRENT_DESKTOP");
            String session = env("XDG_SESSION_TYPE");
            return "Linux 桌面：" + desktop + " · 会话：" + session + " · 普通桌面仅支持最佳努力全屏";
        }
        return os + " · 计时基于真实时间，窗口失焦不暂停";
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static final class GradientPanel extends JPanel {
        private GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setPaint(new java.awt.GradientPaint(0, 0, BACKGROUND_TOP, 0, getHeight(), BACKGROUND_BOTTOM));
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.setColor(new Color(255, 201, 214, 90));
            graphics2D.fillOval(60, 70, 160, 160);
            graphics2D.setColor(new Color(154, 222, 196, 90));
            graphics2D.fillOval(getWidth() - 220, getHeight() - 210, 170, 170);
            graphics2D.dispose();
        }
    }
}
