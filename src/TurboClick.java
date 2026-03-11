import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.InputEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

public class TurboClick implements NativeKeyListener {

    static boolean running = false;
    static long clickCount = 0;
    static JLabel statusLabel;
    static JLabel clickCountLabel;
    static JTextField intervalField;
    static JTextField maxClicksField;
    static JComboBox<String> mouseButtonCombo;
    static JComboBox<String> clickTypeCombo;
    static JButton startButton;
    static JButton stopButton;
    static JLabel hotkeyLabel;
    static int hotkeyKeyCode = NativeKeyEvent.VC_F6;
    static String hotkeyName = "F6";
    static boolean listeningForHotkey = false;
    static TurboClick instance;

    public static void main(String[] args) {
        instance = new TurboClick();

        // Suppress jnativehook logging
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        // Register global key hook
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(instance);
        } catch (NativeHookException e) {
            System.err.println("Could not register native hook: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> buildUI());

        // Shutdown hook to unregister
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException e) {
                e.printStackTrace();
            }
        }));
    }

    static void buildUI() {
        JFrame frame = new JFrame("TurboClick");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        frame.pack();
        frame.setMinimumSize(new Dimension(340, 500));
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        mainPanel.setBackground(new Color(245, 245, 245));

        // === Title ===
        JLabel title = new JLabel("TurboClick");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(new Color(30, 30, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Auto Clicker");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(title);
        mainPanel.add(subtitle);
        mainPanel.add(Box.createVerticalStrut(12));

        // === Click Settings Panel ===
        JPanel settingsPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        settingsPanel.setBackground(Color.WHITE);
        settingsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Click Settings", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11), new Color(80, 80, 80)));

        settingsPanel.add(makeLabel("Interval (ms):"));
        intervalField = new JTextField("100", 8);
        styleField(intervalField);
        settingsPanel.add(intervalField);

        settingsPanel.add(makeLabel("Mouse Button:"));
        mouseButtonCombo = new JComboBox<>(new String[] { "Left", "Right", "Middle" });
        styleCombo(mouseButtonCombo);
        settingsPanel.add(mouseButtonCombo);

        settingsPanel.add(makeLabel("Click Type:"));
        clickTypeCombo = new JComboBox<>(new String[] { "Single Click", "Double Click" });
        styleCombo(clickTypeCombo);
        settingsPanel.add(clickTypeCombo);

        settingsPanel.add(makeLabel("Max Clicks (0=∞):"));
        maxClicksField = new JTextField("0", 8);
        styleField(maxClicksField);
        settingsPanel.add(maxClicksField);

        mainPanel.add(settingsPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // === Hotkey Panel ===
        JPanel hotkeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        hotkeyPanel.setBackground(Color.WHITE);
        hotkeyPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Hotkey", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11), new Color(80, 80, 80)));

        hotkeyLabel = new JLabel("Toggle Key: [" + hotkeyName + "]");
        hotkeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton changeHotkeyBtn = new JButton("Change");
        changeHotkeyBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        changeHotkeyBtn.setPreferredSize(new Dimension(75, 26));
        changeHotkeyBtn.setBackground(new Color(100, 100, 200));
        changeHotkeyBtn.setForeground(Color.WHITE);
        changeHotkeyBtn.setFocusPainted(false);
        changeHotkeyBtn.setBorderPainted(false);
        changeHotkeyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        changeHotkeyBtn.addActionListener(e -> {
            listeningForHotkey = true;
            hotkeyLabel.setText("Toggle Key: [Press any key...]");
            changeHotkeyBtn.setEnabled(false);
            // Re-enable after 5 seconds if no key pressed
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                }
                if (listeningForHotkey) {
                    listeningForHotkey = false;
                    SwingUtilities.invokeLater(() -> {
                        hotkeyLabel.setText("Toggle Key: [" + hotkeyName + "]");
                        changeHotkeyBtn.setEnabled(true);
                    });
                }
                SwingUtilities.invokeLater(() -> changeHotkeyBtn.setEnabled(true));
            }).start();
        });

        hotkeyPanel.add(hotkeyLabel);
        hotkeyPanel.add(changeHotkeyBtn);
        mainPanel.add(hotkeyPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // === Status Panel ===
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Status", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11), new Color(80, 80, 80)));

        statusLabel = new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(100, 100, 100));
        statusLabel.setBorder(new EmptyBorder(0, 8, 0, 0));

        clickCountLabel = new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        clickCountLabel.setForeground(new Color(80, 80, 80));
        clickCountLabel.setBorder(new EmptyBorder(0, 8, 4, 0));

        statusPanel.add(statusLabel);
        statusPanel.add(clickCountLabel);
        mainPanel.add(statusPanel);
        mainPanel.add(Box.createVerticalStrut(12));

        // === Buttons ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setBackground(new Color(245, 245, 245));

        startButton = new JButton("▶  Start");
        startButton.setPreferredSize(new Dimension(120, 36));
        startButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        startButton.setBackground(new Color(50, 170, 90));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        stopButton = new JButton("■  Stop");
        stopButton.setPreferredSize(new Dimension(120, 36));
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        stopButton.setBackground(new Color(210, 60, 60));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.setBorderPainted(false);
        stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        startButton.addActionListener(e -> startClicking());
        stopButton.addActionListener(e -> stopClicking());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        mainPanel.add(buttonPanel);

        frame.setContentPane(mainPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static void startClicking() {
        if (running)
            return;
        running = true;
        clickCount = 0;
        updateStatus("● Running", new Color(50, 170, 90));

        new Thread(() -> {
            try {
                Robot robot = new Robot();
                int maxClicks = Integer.parseInt(maxClicksField.getText().trim());
                boolean isDouble = clickTypeCombo.getSelectedIndex() == 1;
                int button = getSelectedButton();

                while (running) {
                    if (maxClicks > 0 && clickCount >= maxClicks) {
                        stopClicking();
                        break;
                    }

                    int interval = Integer.parseInt(intervalField.getText().trim());

                    robot.mousePress(button);
                    robot.mouseRelease(button);

                    if (isDouble) {
                        Thread.sleep(50);
                        robot.mousePress(button);
                        robot.mouseRelease(button);
                    }

                    clickCount++;
                    final long count = clickCount;
                    SwingUtilities.invokeLater(() -> clickCountLabel.setText("Clicks: " + count));

                    Thread.sleep(interval);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    static void stopClicking() {
        running = false;
        SwingUtilities.invokeLater(() -> updateStatus("● Stopped", new Color(210, 60, 60)));
    }

    static int getSelectedButton() {
        switch (mouseButtonCombo.getSelectedIndex()) {
            case 1:
                return InputEvent.BUTTON3_DOWN_MASK; // Right
            case 2:
                return InputEvent.BUTTON2_DOWN_MASK; // Middle
            default:
                return InputEvent.BUTTON1_DOWN_MASK; // Left
        }
    }

    static void updateStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    static void styleField(JTextField f) {
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
    }

    static void styleCombo(JComboBox<?> c) {
        c.setFont(new Font("SansSerif", Font.PLAIN, 12));
        c.setBackground(Color.WHITE);
    }

    // === NativeKeyListener ===
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (listeningForHotkey) {
            hotkeyKeyCode = e.getKeyCode();
            hotkeyName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForHotkey = false;
            SwingUtilities.invokeLater(() -> hotkeyLabel.setText("Toggle Key: [" + hotkeyName + "]"));
            return;
        }

        if (e.getKeyCode() == hotkeyKeyCode) {
            if (running) {
                stopClicking();
            } else {
                startClicking();
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }
}
