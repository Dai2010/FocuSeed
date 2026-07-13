package io.github.dai2010.focuseed.desktop;

import io.github.dai2010.focuseed.core.FocusPhase;
import io.github.dai2010.focuseed.core.FocusSession;
import io.github.dai2010.focuseed.core.FocusSettings;
import io.github.dai2010.focuseed.core.FocusSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.Locale;

public final class FocuSeedDesktop {
    private final JFrame frame = new JFrame("FocuSeed");
    private final JLabel phaseLabel = new JLabel("准备开始", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("00:00", SwingConstants.CENTER);
    private final JLabel roundLabel = new JLabel("轮次 0 / 0", SwingConstants.CENTER);
    private final JLabel platformLabel = new JLabel(platformText(), SwingConstants.CENTER);
    private final JSpinner workMinutes = new JSpinner(new SpinnerNumberModel(25, 1, 240, 1));
    private final JSpinner breakMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 120, 1));
    private final JSpinner rounds = new JSpinner(new SpinnerNumberModel(4, 1, 24, 1));
    private final FocusSession session = new FocusSession(new FocusSettings(25, 5, 4));
    private final Timer timer = new Timer(500, event -> render());

    private boolean fullscreen;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FocuSeedDesktop().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(24, 24));
        frame.getContentPane().setBackground(new Color(18, 22, 28));
        frame.add(header(), BorderLayout.NORTH);
        frame.add(center(), BorderLayout.CENTER);
        frame.add(controls(), BorderLayout.SOUTH);
        frame.setSize(900, 640);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        timer.start();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new GridLayout(3, 1));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 0, 32));

        JLabel title = new JLabel("FocuSeed", SwingConstants.CENTER);
        title.setForeground(new Color(124, 255, 178));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));

        JLabel subtitle = new JLabel("强制专注番茄钟 · 桌面预览版", SwingConstants.CENTER);
        subtitle.setForeground(new Color(220, 225, 232));
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));

        platformLabel.setForeground(new Color(168, 176, 186));
        platformLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        panel.add(title);
        panel.add(subtitle);
        panel.add(platformLabel);
        return panel;
    }

    private JPanel center() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 8, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        phaseLabel.setForeground(Color.WHITE);
        phaseLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));

        timerLabel.setForeground(new Color(124, 255, 178));
        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 96));

        roundLabel.setForeground(new Color(220, 225, 232));
        roundLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));

        panel.add(phaseLabel);
        panel.add(timerLabel);
        panel.add(roundLabel);
        return panel;
    }

    private JPanel controls() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 12, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 32, 32, 32));

        JPanel settings = new JPanel(new GridLayout(1, 6, 12, 12));
        settings.setOpaque(false);
        settings.add(label("工作分钟"));
        settings.add(workMinutes);
        settings.add(label("休息分钟"));
        settings.add(breakMinutes);
        settings.add(label("轮数"));
        settings.add(rounds);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 12, 12));
        buttons.setOpaque(false);
        JButton start = new JButton("开始并全屏");
        JButton exitFullscreen = new JButton("退出全屏");
        JButton stop = new JButton("停止");
        start.addActionListener(event -> startSession());
        exitFullscreen.addActionListener(event -> leaveFullscreen());
        stop.addActionListener(event -> stopSession());
        buttons.add(start);
        buttons.add(exitFullscreen);
        buttons.add(stop);

        panel.add(settings);
        panel.add(buttons);
        return panel;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setForeground(new Color(220, 225, 232));
        return label;
    }

    private void startSession() {
        FocusSettings settings = new FocusSettings(
            (Integer) workMinutes.getValue(),
            (Integer) breakMinutes.getValue(),
            (Integer) rounds.getValue()
        );
        session.start(settings, System.currentTimeMillis());
        enterFullscreen();
        render();
    }

    private void stopSession() {
        session.stop();
        render();
    }

    private void render() {
        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        FocusPhase phase = snapshot.phase();
        if (phase == FocusPhase.IDLE) {
            phaseLabel.setText("准备开始");
            timerLabel.setText("00:00");
            roundLabel.setText("轮次 0 / " + rounds.getValue());
            return;
        }
        if (phase == FocusPhase.FINISHED) {
            phaseLabel.setText("已完成");
            timerLabel.setText("00:00");
            roundLabel.setText("轮次 " + snapshot.roundText());
            return;
        }
        phaseLabel.setText(phase == FocusPhase.WORKING ? "专注中" : "休息中");
        timerLabel.setText(snapshot.remainingText());
        roundLabel.setText("轮次 " + snapshot.roundText());
    }

    private void enterFullscreen() {
        if (fullscreen) {
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
}
