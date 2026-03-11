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

public class TurboClick implements NativeKeyListener {

    // === State ===
    static boolean running = false;
    static long clickCount = 0;
    static int hotkeyKeyCode  = NativeKeyEvent.VC_F6;
    static String hotkeyName  = "F6";
    static int smartKeyCode   = NativeKeyEvent.VC_F7;
    static String smartKeyName = "F7";
    static boolean listeningForHotkey = false;
    static boolean listeningForSmartKey = false;
    static boolean smartModeActive = false;
    static TurboClick instance;

    // === UI ===
    static JLabel statusLabel, clickCountLabel, hotkeyLabel, smartKeyLabel;
    static JTextField intervalField, maxClicksField;
    static JComboBox<String> mouseButtonCombo, clickTypeCombo;
    static JButton startButton, stopButton, changeHotkeyBtn, changeSmartKeyBtn;
    static JFrame frame;

    // === Click Points Table ===
    static DefaultTableModel tableModel;
    static JTable pointsTable;

    // === Smart Click floating widgets ===
    static List<SmartPin> smartPins = new ArrayList<>();

    // =========================================================
    //  SMART PIN — draggable floating coordinate pin
    // =========================================================
    static class SmartPin {
        JWindow win;
        int screenX, screenY;
        int rowIndex;
        JLabel coordLabel;
        int dragOffsetX, dragOffsetY;

        SmartPin(int x, int y, int row) {
            this.screenX  = x;
            this.screenY  = y;
            this.rowIndex = row;
            build();
        }

        void build() {
            win = new JWindow();
            win.setAlwaysOnTop(true);
            win.setBackground(new Color(0, 0, 0, 0));

            JPanel panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Drop shadow
                    g2.setColor(new Color(0, 0, 0, 40));
                    g2.fillRoundRect(3, 3, getWidth() - 3, getHeight() - 16, 10, 10);

                    // Body
                    g2.setColor(new Color(30, 30, 30, 220));
                    g2.fillRoundRect(0, 0, getWidth() - 3, getHeight() - 13, 10, 10);

                    // Pin triangle (pointing down)
                    int cx = (getWidth() - 3) / 2;
                    int tipY = getHeight() - 1;
                    int[] px = {cx - 7, cx + 7, cx};
                    int[] py = {getHeight() - 13, getHeight() - 13, tipY};
                    g2.setColor(new Color(30, 30, 30, 220));
                    g2.fillPolygon(px, py, 3);

                    // Row number badge
                    g2.setColor(new Color(255, 80, 80));
                    g2.fillOval(2, 2, 18, 18);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    String num = String.valueOf(rowIndex + 1);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(num, 11 - fm.stringWidth(num) / 2, 15);

                    // Drag handle dots
                    g2.setColor(new Color(180, 180, 180));
                    for (int i = 0; i < 3; i++) {
                        g2.fillOval(getWidth() - 18 + i * 4, 8, 3, 3);
                        g2.fillOval(getWidth() - 18 + i * 4, 14, 3, 3);
                    }
                }
            };
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.setPreferredSize(new Dimension(110, 52));

            coordLabel = new JLabel("(" + screenX + ", " + screenY + ")");
            coordLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
            coordLabel.setForeground(new Color(100, 220, 100));
            coordLabel.setBorder(new EmptyBorder(4, 24, 12, 6));
            panel.add(coordLabel, BorderLayout.CENTER);

            // Close button
            JLabel closeBtn = new JLabel("×");
            closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
            closeBtn.setForeground(new Color(200, 200, 200));
            closeBtn.setBorder(new EmptyBorder(2, 0, 10, 6));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { removePin(SmartPin.this); }
                public void mouseEntered(MouseEvent e) { closeBtn.setForeground(Color.RED); }
                public void mouseExited(MouseEvent e)  { closeBtn.setForeground(new Color(200,200,200)); }
            });
            panel.add(closeBtn, BorderLayout.EAST);

            // Drag logic — moves the pin AND updates the coord label + table
            panel.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragOffsetX = e.getX();
                    dragOffsetY = e.getY();
                }
            });
            panel.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    Point loc = win.getLocationOnScreen();
                    int nx = loc.x + e.getX() - dragOffsetX;
                    int ny = loc.y + e.getY() - dragOffsetY;
                    win.setLocation(nx, ny);
                    // The pin tip is at bottom-center of the window
                    screenX = nx + win.getWidth() / 2;
                    screenY = ny + win.getHeight();
                    coordLabel.setText("(" + screenX + ", " + screenY + ")");
                    // Update table row
                    SwingUtilities.invokeLater(() -> {
                        if (rowIndex < tableModel.getRowCount()) {
                            tableModel.setValueAt(screenX, rowIndex, 1);
                            tableModel.setValueAt(screenY, rowIndex, 2);
                        }
                    });
                }
            });

            win.setContentPane(panel);
            win.pack();
            // Position so the pin tip points to (screenX, screenY)
            win.setLocation(screenX - win.getWidth() / 2, screenY - win.getHeight());
            win.setVisible(false);
        }

        void show() { win.setVisible(true); }
        void hide() { win.setVisible(false); }
        void dispose() { win.dispose(); }
    }

    // =========================================================
    //  MAIN
    // =========================================================
    public static void main(String[] args) {
        instance = new TurboClick();

        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(instance);
        } catch (NativeHookException e) {
            System.err.println("Native hook failed: " + e.getMessage());
        }

        SwingUtilities.invokeLater(TurboClick::buildUI);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { GlobalScreen.unregisterNativeHook(); } catch (NativeHookException e) {}
        }));
    }

    // =========================================================
    //  BUILD MAIN UI
    // =========================================================
    static void buildUI() {
        frame = new JFrame("TurboClick");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(12, 15, 12, 15));
        main.setBackground(new Color(245, 245, 245));

        // Title
        JLabel title = new JLabel("TurboClick");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(30, 30, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Auto Clicker");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        main.add(Box.createVerticalStrut(5));
        main.add(title); main.add(subtitle);
        main.add(Box.createVerticalStrut(12));

        // Click Settings
        JPanel settings = new JPanel(new GridLayout(4, 2, 8, 8));
        settings.setBackground(Color.WHITE);
        settings.setBorder(makeTitledBorder("Click Settings"));
        settings.add(makeLabel("Interval (ms):")); intervalField = makeField("100"); settings.add(intervalField);
        settings.add(makeLabel("Mouse Button:")); mouseButtonCombo = makeCombo(new String[]{"Left","Right","Middle"}); settings.add(mouseButtonCombo);
        settings.add(makeLabel("Click Type:")); clickTypeCombo = makeCombo(new String[]{"Single Click","Double Click"}); settings.add(clickTypeCombo);
        settings.add(makeLabel("Max Clicks (0=∞):")); maxClicksField = makeField("0"); settings.add(maxClicksField);
        main.add(settings);
        main.add(Box.createVerticalStrut(10));

        // Click Points Table
        JPanel pointsPanel = new JPanel(new BorderLayout(6, 6));
        pointsPanel.setBackground(Color.WHITE);
        pointsPanel.setBorder(makeTitledBorder("Click Points  (empty = click at cursor)"));

        tableModel = new DefaultTableModel(new String[]{"#","X","Y","Delay (ms)"}, 0) {
            public boolean isCellEditable(int r, int c) { return c > 0; }
        };
        pointsTable = new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pointsTable.setRowHeight(24);
        pointsTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        pointsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        pointsTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        pointsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        pointsTable.setGridColor(new Color(220,220,220));
        pointsTable.setSelectionBackground(new Color(210,230,255));

        // Sync table edits back to pins
        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int col = e.getColumn();
            if (row < 0 || row >= smartPins.size()) return;
            SmartPin pin = smartPins.get(row);
            try {
                if (col == 1) { pin.screenX = Integer.parseInt(tableModel.getValueAt(row,1).toString()); }
                if (col == 2) { pin.screenY = Integer.parseInt(tableModel.getValueAt(row,2).toString()); }
                if (col == 1 || col == 2) {
                    pin.coordLabel.setText("(" + pin.screenX + ", " + pin.screenY + ")");
                    pin.win.setLocation(pin.screenX - pin.win.getWidth()/2, pin.screenY - pin.win.getHeight());
                }
            } catch (Exception ex) {}
        });

        JScrollPane scroll = new JScrollPane(pointsTable);
        scroll.setPreferredSize(new Dimension(300, 110));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));

        JPanel tblBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tblBtns.setBackground(Color.WHITE);

        JButton addManualBtn = makeActionButton("+ Add Point", new Color(80,80,80));
        JButton removeBtn    = makeActionButton("✕ Remove",    new Color(180,60,60));
        JButton clearBtn     = makeActionButton("Clear All",   new Color(120,120,120));

        addManualBtn.addActionListener(e -> {
            Point mouse = MouseInfo.getPointerInfo().getLocation();
            addSmartPin(mouse.x, mouse.y);
        });

        removeBtn.addActionListener(e -> {
            int row = pointsTable.getSelectedRow();
            if (row >= 0) {
                smartPins.get(row).dispose();
                smartPins.remove(row);
                tableModel.removeRow(row);
                refreshRowNumbers();
            }
        });

        clearBtn.addActionListener(e -> {
            for (SmartPin p : smartPins) p.dispose();
            smartPins.clear();
            tableModel.setRowCount(0);
        });

        tblBtns.add(addManualBtn); tblBtns.add(removeBtn); tblBtns.add(clearBtn);
        pointsPanel.add(scroll, BorderLayout.CENTER);
        pointsPanel.add(tblBtns, BorderLayout.SOUTH);
        main.add(pointsPanel);
        main.add(Box.createVerticalStrut(10));

        // Hotkeys panel
        JPanel hotkeyPanel = new JPanel(new GridLayout(2, 1, 4, 6));
        hotkeyPanel.setBackground(Color.WHITE);
        hotkeyPanel.setBorder(makeTitledBorder("Hotkeys"));

        // Row 1 — toggle start/stop
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.setBackground(Color.WHITE);
        hotkeyLabel = new JLabel("Start/Stop: [" + hotkeyName + "]");
        hotkeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        changeHotkeyBtn = makeActionButton("Change", new Color(100,100,200));
        changeHotkeyBtn.addActionListener(e -> startListening(true));
        row1.add(hotkeyLabel); row1.add(changeHotkeyBtn);

        // Row 2 — smart click toggle
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.setBackground(Color.WHITE);
        smartKeyLabel = new JLabel("Smart Click:  [" + smartKeyName + "]");
        smartKeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        changeSmartKeyBtn = makeActionButton("Change", new Color(100,100,200));
        changeSmartKeyBtn.addActionListener(e -> startListening(false));

        // Manual Smart Click toggle button
        JButton smartToggleBtn = makeActionButton("⊕ Smart Click", new Color(40,140,180));
        smartToggleBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        smartToggleBtn.addActionListener(e -> toggleSmartMode());

        row2.add(smartKeyLabel); row2.add(changeSmartKeyBtn); row2.add(smartToggleBtn);

        hotkeyPanel.add(row1);
        hotkeyPanel.add(row2);
        main.add(hotkeyPanel);
        main.add(Box.createVerticalStrut(10));

        // Status
        JPanel statusPanel = new JPanel(new GridLayout(2,1,4,4));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(makeTitledBorder("Status"));
        statusLabel = new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(100,100,100));
        statusLabel.setBorder(new EmptyBorder(2,8,0,0));
        clickCountLabel = new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        clickCountLabel.setForeground(new Color(80,80,80));
        clickCountLabel.setBorder(new EmptyBorder(0,8,4,0));
        statusPanel.add(statusLabel); statusPanel.add(clickCountLabel);
        main.add(statusPanel);
        main.add(Box.createVerticalStrut(14));

        // Start / Stop buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        btnPanel.setBackground(new Color(245,245,245));

        startButton = new JButton("▶  Start");
        startButton.setPreferredSize(new Dimension(130,42));
        startButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        startButton.setBackground(new Color(40,160,80));
        startButton.setForeground(Color.WHITE); startButton.setOpaque(true);
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(20,110,50),2),
            BorderFactory.createEmptyBorder(6,16,6,16)));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> startClicking());

        stopButton = new JButton("■  Stop");
        stopButton.setPreferredSize(new Dimension(130,42));
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopButton.setBackground(new Color(200,50,50));
        stopButton.setForeground(Color.WHITE); stopButton.setOpaque(true);
        stopButton.setFocusPainted(false);
        stopButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(140,20,20),2),
            BorderFactory.createEmptyBorder(6,16,6,16)));
        stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopButton.addActionListener(e -> stopClicking());

        btnPanel.add(startButton); btnPanel.add(stopButton);
        main.add(btnPanel);
        main.add(Box.createVerticalStrut(8));

        frame.setContentPane(main);
        frame.pack();
        frame.setMinimumSize(new Dimension(390, 600));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // =========================================================
    //  SMART MODE
    // =========================================================
    static void toggleSmartMode() {
        smartModeActive = !smartModeActive;
        if (smartModeActive) {
            // Hide main window, show all pins
            frame.setVisible(false);
            showSmartBar();
            for (SmartPin p : smartPins) p.show();
        } else {
            hideSmartBar();
            for (SmartPin p : smartPins) p.hide();
            frame.setVisible(true);
            frame.toFront();
        }
    }

    // Small floating bar at bottom-center when smart mode is on
    static JWindow smartBar;

    static void showSmartBar() {
        smartBar = new JWindow();
        smartBar.setAlwaysOnTop(true);
        smartBar.setBackground(new Color(0,0,0,0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(25, 25, 25, 230));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8));

        // Drag handle
        JLabel dragHandle = new JLabel("⠿");
        dragHandle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        dragHandle.setForeground(new Color(150,150,150));
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLbl = new JLabel("Smart Click");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLbl.setForeground(new Color(100, 200, 255));

        JButton addPinBtn = makeActionButton("+ Pin", new Color(50,130,200));
        addPinBtn.addActionListener(e -> {
            // Add a pin at center of screen
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            addSmartPin(screen.width / 2, screen.height / 2);
        });

        JButton startBtn = makeActionButton("▶ Start", new Color(40,160,80));
        startBtn.addActionListener(e -> startClicking());

        JButton stopBtn = makeActionButton("■ Stop", new Color(200,50,50));
        stopBtn.addActionListener(e -> stopClicking());

        JButton doneBtn = makeActionButton("✓ Done", new Color(100,100,100));
        doneBtn.addActionListener(e -> toggleSmartMode());

        // Running status indicator on bar
        JLabel barStatus = new JLabel("●");
        barStatus.setFont(new Font("SansSerif", Font.BOLD, 14));
        barStatus.setForeground(running ? new Color(40,200,80) : new Color(150,150,150));

        bar.add(dragHandle);
        bar.add(titleLbl);
        bar.add(barStatus);
        bar.add(addPinBtn);
        bar.add(startBtn);
        bar.add(stopBtn);
        bar.add(doneBtn);

        // Drag the bar itself
        int[] off = {0,0};
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { off[0]=e.getX(); off[1]=e.getY(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point loc = smartBar.getLocationOnScreen();
                smartBar.setLocation(loc.x + e.getX() - off[0], loc.y + e.getY() - off[1]);
            }
        });

        smartBar.setContentPane(bar);
        smartBar.pack();

        // Position at bottom-center
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        smartBar.setLocation(screen.width/2 - smartBar.getWidth()/2, screen.height - 80);
        smartBar.setVisible(true);
    }

    static void hideSmartBar() {
        if (smartBar != null) { smartBar.dispose(); smartBar = null; }
    }

    static void addSmartPin(int x, int y) {
        int row = tableModel.getRowCount();
        int delay = 100;
        try { delay = Integer.parseInt(intervalField.getText().trim()); } catch (Exception e) {}
        tableModel.addRow(new Object[]{row+1, x, y, delay});
        SmartPin pin = new SmartPin(x, y, row);
        smartPins.add(pin);
        if (smartModeActive) pin.show();
    }

    static void removePin(SmartPin pin) {
        int idx = smartPins.indexOf(pin);
        if (idx >= 0) {
            pin.dispose();
            smartPins.remove(idx);
            tableModel.removeRow(idx);
            refreshRowNumbers();
            // Update row indices on remaining pins
            for (int i = 0; i < smartPins.size(); i++) smartPins.get(i).rowIndex = i;
        }
    }

    static void refreshRowNumbers() {
        for (int i = 0; i < tableModel.getRowCount(); i++) tableModel.setValueAt(i+1, i, 0);
    }

    // =========================================================
    //  CLICKING
    // =========================================================
    static void startClicking() {
        if (running) return;
        running = true;
        clickCount = 0;
        updateStatus("● Running", new Color(40,160,80));

        // Snapshot points
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try {
                int x = Integer.parseInt(tableModel.getValueAt(i,1).toString());
                int y = Integer.parseInt(tableModel.getValueAt(i,2).toString());
                int d = Integer.parseInt(tableModel.getValueAt(i,3).toString());
                points.add(new int[]{x,y,d});
            } catch (Exception e) {}
        }

        new Thread(() -> {
            try {
                Robot robot = new Robot();
                long maxClicks  = Long.parseLong(maxClicksField.getText().trim());
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
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    static void doClick(Robot robot, int button, boolean isDouble) throws InterruptedException {
        robot.mousePress(button); robot.mouseRelease(button);
        if (isDouble) { Thread.sleep(40); robot.mousePress(button); robot.mouseRelease(button); }
    }

    static void stopClicking() {
        running = false;
        SwingUtilities.invokeLater(() -> updateStatus("● Stopped", new Color(200,50,50)));
    }

    static int getSelectedButton() {
        switch (mouseButtonCombo.getSelectedIndex()) {
            case 1: return InputEvent.BUTTON3_DOWN_MASK;
            case 2: return InputEvent.BUTTON2_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }

    static void updateStatus(String text, Color color) {
        statusLabel.setText(text); statusLabel.setForeground(color);
    }

    // =========================================================
    //  HOTKEY LISTENING
    // =========================================================
    static void startListening(boolean forStartStop) {
        if (forStartStop) {
            listeningForHotkey = true;
            hotkeyLabel.setText("Start/Stop: [Press any key...]");
            changeHotkeyBtn.setEnabled(false);
        } else {
            listeningForSmartKey = true;
            smartKeyLabel.setText("Smart Click:  [Press any key...]");
            changeSmartKeyBtn.setEnabled(false);
        }
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ex) {}
            if (forStartStop && listeningForHotkey) {
                listeningForHotkey = false;
                SwingUtilities.invokeLater(() -> { hotkeyLabel.setText("Start/Stop: ["+hotkeyName+"]"); changeHotkeyBtn.setEnabled(true); });
            } else if (!forStartStop && listeningForSmartKey) {
                listeningForSmartKey = false;
                SwingUtilities.invokeLater(() -> { smartKeyLabel.setText("Smart Click:  ["+smartKeyName+"]"); changeSmartKeyBtn.setEnabled(true); });
            }
        }).start();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (listeningForHotkey) {
            hotkeyKeyCode = e.getKeyCode(); hotkeyName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForHotkey = false;
            SwingUtilities.invokeLater(() -> { hotkeyLabel.setText("Start/Stop: ["+hotkeyName+"]"); changeHotkeyBtn.setEnabled(true); });
            return;
        }
        if (listeningForSmartKey) {
            smartKeyCode = e.getKeyCode(); smartKeyName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForSmartKey = false;
            SwingUtilities.invokeLater(() -> { smartKeyLabel.setText("Smart Click:  ["+smartKeyName+"]"); changeSmartKeyBtn.setEnabled(true); });
            return;
        }
        if (e.getKeyCode() == smartKeyCode)  { SwingUtilities.invokeLater(TurboClick::toggleSmartMode); return; }
        if (e.getKeyCode() == hotkeyKeyCode) { if (running) stopClicking(); else startClicking(); }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    // =========================================================
    //  UI HELPERS
    // =========================================================
    static JLabel makeLabel(String t) {
        JLabel l = new JLabel(t); l.setFont(new Font("SansSerif", Font.PLAIN, 12)); return l;
    }
    static JTextField makeField(String v) {
        JTextField f = new JTextField(v, 8);
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180,180,180)),
            BorderFactory.createEmptyBorder(3,6,3,6)));
        return f;
    }
    static JComboBox<String> makeCombo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(new Font("SansSerif", Font.PLAIN, 12)); c.setBackground(Color.WHITE); return c;
    }
    static JButton makeActionButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static TitledBorder makeTitledBorder(String t) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200,200,200)), t,
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 11), new Color(80,80,80));
    }
}