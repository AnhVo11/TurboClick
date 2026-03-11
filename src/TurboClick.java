import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

public class TurboClick implements NativeKeyListener, NativeMouseListener {

    // === State ===
    static boolean running = false;
    static long clickCount = 0;
    static int hotkeyKeyCode = NativeKeyEvent.VC_F6;
    static String hotkeyName = "F6";
    static boolean listeningForHotkey = false;
    static boolean pickingCoordinate = false;
    static TurboClick instance;

    // === UI Components ===
    static JLabel statusLabel, clickCountLabel, hotkeyLabel;
    static JTextField intervalField, maxClicksField;
    static JComboBox<String> mouseButtonCombo, clickTypeCombo;
    static JButton startButton, stopButton, changeHotkeyBtn;
    static JFrame frame;

    // === Click Points Table ===
    static DefaultTableModel tableModel;
    static JTable pointsTable;

    public static void main(String[] args) {
        instance = new TurboClick();

        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(instance);
            GlobalScreen.addNativeMouseListener(instance);
        } catch (NativeHookException e) {
            System.err.println("Could not register native hook: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> buildUI());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { GlobalScreen.unregisterNativeHook(); } catch (NativeHookException e) {}
        }));
    }

    static void buildUI() {
        frame = new JFrame("TurboClick");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(12, 15, 12, 15));
        mainPanel.setBackground(new Color(245, 245, 245));

        // === Title ===
        JLabel title = new JLabel("TurboClick");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
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

        // === Click Settings ===
        JPanel settingsPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        settingsPanel.setBackground(Color.WHITE);
        settingsPanel.setBorder(makeTitledBorder("Click Settings"));

        settingsPanel.add(makeLabel("Interval (ms):"));
        intervalField = makeField("100");
        settingsPanel.add(intervalField);

        settingsPanel.add(makeLabel("Mouse Button:"));
        mouseButtonCombo = makeCombo(new String[]{"Left", "Right", "Middle"});
        settingsPanel.add(mouseButtonCombo);

        settingsPanel.add(makeLabel("Click Type:"));
        clickTypeCombo = makeCombo(new String[]{"Single Click", "Double Click"});
        settingsPanel.add(clickTypeCombo);

        settingsPanel.add(makeLabel("Max Clicks (0=∞):"));
        maxClicksField = makeField("0");
        settingsPanel.add(maxClicksField);

        mainPanel.add(settingsPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // === Click Points Table ===
        JPanel pointsPanel = new JPanel(new BorderLayout(6, 6));
        pointsPanel.setBackground(Color.WHITE);
        pointsPanel.setBorder(makeTitledBorder("Click Points  (empty = click at cursor position)"));

        String[] cols = {"#", "X", "Y", "Delay (ms)"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return c > 0; }
        };
        pointsTable = new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pointsTable.setRowHeight(24);
        pointsTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        pointsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        pointsTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        pointsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        pointsTable.setGridColor(new Color(220, 220, 220));
        pointsTable.setSelectionBackground(new Color(210, 230, 255));

        JScrollPane scrollPane = new JScrollPane(pointsTable);
        scrollPane.setPreferredSize(new Dimension(300, 110));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tableButtons.setBackground(Color.WHITE);

        JButton addPickBtn   = makeActionButton("+ Pick on Screen", new Color(50, 130, 200));
        JButton addManualBtn = makeActionButton("+ Manual", new Color(80, 80, 80));
        JButton removeBtn    = makeActionButton("✕ Remove", new Color(180, 60, 60));
        JButton clearBtn     = makeActionButton("Clear All", new Color(120, 120, 120));

        addPickBtn.addActionListener(e -> {
            pickingCoordinate = true;
            frame.setExtendedState(JFrame.ICONIFIED); // minimize
            Timer t = new Timer(400, ev -> showPickOverlay());
            t.setRepeats(false);
            t.start();
        });

        addManualBtn.addActionListener(e -> {
            JTextField xf = new JTextField("0", 5);
            JTextField yf = new JTextField("0", 5);
            JTextField df = new JTextField("100", 5);
            JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
            p.add(new JLabel("X:")); p.add(xf);
            p.add(new JLabel("Y:")); p.add(yf);
            p.add(new JLabel("Delay (ms):")); p.add(df);
            int res = JOptionPane.showConfirmDialog(frame, p, "Add Click Point",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                try {
                    addPoint(Integer.parseInt(xf.getText().trim()),
                             Integer.parseInt(yf.getText().trim()),
                             Integer.parseInt(df.getText().trim()));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Please enter valid numbers.");
                }
            }
        });

        removeBtn.addActionListener(e -> {
            int row = pointsTable.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
                refreshRowNumbers();
            }
        });

        clearBtn.addActionListener(e -> tableModel.setRowCount(0));

        tableButtons.add(addPickBtn);
        tableButtons.add(addManualBtn);
        tableButtons.add(removeBtn);
        tableButtons.add(clearBtn);

        pointsPanel.add(scrollPane, BorderLayout.CENTER);
        pointsPanel.add(tableButtons, BorderLayout.SOUTH);
        mainPanel.add(pointsPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // === Hotkey ===
        JPanel hotkeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        hotkeyPanel.setBackground(Color.WHITE);
        hotkeyPanel.setBorder(makeTitledBorder("Hotkey"));

        hotkeyLabel = new JLabel("Toggle Key: [" + hotkeyName + "]");
        hotkeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        changeHotkeyBtn = makeActionButton("Change", new Color(100, 100, 200));
        changeHotkeyBtn.addActionListener(e -> {
            listeningForHotkey = true;
            hotkeyLabel.setText("Toggle Key: [Press any key...]");
            changeHotkeyBtn.setEnabled(false);
            new Thread(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ex) {}
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

        // === Status ===
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(makeTitledBorder("Status"));

        statusLabel = new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(100, 100, 100));
        statusLabel.setBorder(new EmptyBorder(2, 8, 0, 0));

        clickCountLabel = new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        clickCountLabel.setForeground(new Color(80, 80, 80));
        clickCountLabel.setBorder(new EmptyBorder(0, 8, 4, 0));

        statusPanel.add(statusLabel);
        statusPanel.add(clickCountLabel);
        mainPanel.add(statusPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // === Start / Stop Buttons ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        buttonPanel.setBackground(new Color(245, 245, 245));

        startButton = new JButton("▶  Start");
        startButton.setPreferredSize(new Dimension(130, 42));
        startButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        startButton.setBackground(new Color(40, 160, 80));
        startButton.setForeground(Color.WHITE);
        startButton.setOpaque(true);
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(20, 110, 50), 2),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> startClicking());

        stopButton = new JButton("■  Stop");
        stopButton.setPreferredSize(new Dimension(130, 42));
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopButton.setBackground(new Color(200, 50, 50));
        stopButton.setForeground(Color.WHITE);
        stopButton.setOpaque(true);
        stopButton.setFocusPainted(false);
        stopButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(140, 20, 20), 2),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopButton.addActionListener(e -> stopClicking());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createVerticalStrut(8));

        frame.setContentPane(mainPanel);
        frame.pack();
        frame.setMinimumSize(new Dimension(380, 580));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // === Transparent overlay to pick a screen coordinate ===
    static void showPickOverlay() {
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode dm = gd.getDisplayMode();
        overlay.setBounds(0, 0, dm.getWidth(), dm.getHeight());

        // Track mouse position for live crosshair
        int[] mousePos = {0, 0};

        JPanel glass = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(0, 0, 0, 55));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Instructions
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                String msg = "Click anywhere to set a point   [ESC to cancel]";
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(msg)) / 2;
                g2.drawString(msg, tx, 50);

                // Crosshair
                int cx = mousePos[0], cy = mousePos[1];
                g2.setColor(new Color(255, 80, 80));
                g2.setStroke(new BasicStroke(2));
                g2.drawLine(cx - 20, cy, cx + 20, cy);
                g2.drawLine(cx, cy - 20, cx, cy + 20);
                g2.drawOval(cx - 8, cy - 8, 16, 16);

                // Coordinate label
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                g2.setColor(Color.YELLOW);
                g2.drawString("(" + cx + ", " + cy + ")", cx + 14, cy - 10);
            }
        };
        glass.setOpaque(false);
        glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        glass.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                mousePos[0] = e.getX();
                mousePos[1] = e.getY();
                glass.repaint();
            }
        });

        glass.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                overlay.dispose();
                pickingCoordinate = false;
                frame.setExtendedState(JFrame.NORMAL);
                frame.setVisible(true);
                frame.toFront();
                int delay = 100;
                try { delay = Integer.parseInt(intervalField.getText().trim()); } catch (Exception ex) {}
                addPoint(x, y, delay);
            }
        });

        glass.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    overlay.dispose();
                    pickingCoordinate = false;
                    frame.setExtendedState(JFrame.NORMAL);
                    frame.setVisible(true);
                    frame.toFront();
                }
            }
        });

        overlay.setContentPane(glass);
        overlay.setVisible(true);
        glass.requestFocusInWindow();
    }

    static void addPoint(int x, int y, int delay) {
        int row = tableModel.getRowCount() + 1;
        tableModel.addRow(new Object[]{row, x, y, delay});
    }

    static void refreshRowNumbers() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(i + 1, i, 0);
        }
    }

    static void startClicking() {
        if (running) return;
        running = true;
        clickCount = 0;
        updateStatus("● Running", new Color(40, 160, 80));

        // Snapshot table data on EDT before starting thread
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try {
                int x = Integer.parseInt(tableModel.getValueAt(i, 1).toString());
                int y = Integer.parseInt(tableModel.getValueAt(i, 2).toString());
                int d = Integer.parseInt(tableModel.getValueAt(i, 3).toString());
                points.add(new int[]{x, y, d});
            } catch (Exception e) {}
        }

        new Thread(() -> {
            try {
                Robot robot = new Robot();
                long maxClicks = Long.parseLong(maxClicksField.getText().trim());
                boolean isDouble = clickTypeCombo.getSelectedIndex() == 1;
                int button = getSelectedButton();

                while (running) {
                    if (maxClicks > 0 && clickCount >= maxClicks) { stopClicking(); break; }

                    if (!points.isEmpty()) {
                        for (int[] pt : points) {
                            if (!running) break;
                            robot.mouseMove(pt[0], pt[1]);
                            Thread.sleep(40);
                            doClick(robot, button, isDouble);
                            clickCount++;
                            final long c = clickCount;
                            SwingUtilities.invokeLater(() -> clickCountLabel.setText("Clicks: " + c));
                            Thread.sleep(Math.max(1, pt[2]));
                        }
                    } else {
                        int interval = Integer.parseInt(intervalField.getText().trim());
                        doClick(robot, button, isDouble);
                        clickCount++;
                        final long c = clickCount;
                        SwingUtilities.invokeLater(() -> clickCountLabel.setText("Clicks: " + c));
                        Thread.sleep(Math.max(1, interval));
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    static void doClick(Robot robot, int button, boolean isDouble) throws InterruptedException {
        robot.mousePress(button);
        robot.mouseRelease(button);
        if (isDouble) {
            Thread.sleep(40);
            robot.mousePress(button);
            robot.mouseRelease(button);
        }
    }

    static void stopClicking() {
        running = false;
        SwingUtilities.invokeLater(() -> updateStatus("● Stopped", new Color(200, 50, 50)));
    }

    static int getSelectedButton() {
        switch (mouseButtonCombo.getSelectedIndex()) {
            case 1: return InputEvent.BUTTON3_DOWN_MASK;
            case 2: return InputEvent.BUTTON2_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
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

    static JTextField makeField(String val) {
        JTextField f = new JTextField(val, 8);
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)
        ));
        return f;
    }

    static JComboBox<String> makeCombo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(new Font("SansSerif", Font.PLAIN, 12));
        c.setBackground(Color.WHITE);
        return c;
    }

    static JButton makeActionButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static TitledBorder makeTitledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            title, TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 11), new Color(80, 80, 80)
        );
    }

    // === NativeKeyListener ===
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (pickingCoordinate) return;
        if (listeningForHotkey) {
            hotkeyKeyCode = e.getKeyCode();
            hotkeyName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForHotkey = false;
            SwingUtilities.invokeLater(() -> {
                hotkeyLabel.setText("Toggle Key: [" + hotkeyName + "]");
                changeHotkeyBtn.setEnabled(true);
            });
            return;
        }
        if (e.getKeyCode() == hotkeyKeyCode) {
            if (running) stopClicking(); else startClicking();
        }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}
    @Override public void nativeMouseClicked(NativeMouseEvent e) {}
    @Override public void nativeMousePressed(NativeMouseEvent e) {}
    @Override public void nativeMouseReleased(NativeMouseEvent e) {}
}