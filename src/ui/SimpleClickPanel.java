package ui;

import engine.SimpleClickEngine;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

public class SimpleClickPanel extends JPanel implements NativeKeyListener {

    // ── Colors ────────────────────────────────────────────────
    private static final Color BG = new Color(22, 22, 28);
    private static final Color PANEL_BG = new Color(28, 28, 38);
    private static final Color BORDER_COL = new Color(50, 50, 65);
    private static final Color TEXT_MAIN = new Color(220, 220, 230);
    private static final Color TEXT_DIM = new Color(120, 120, 150);
    private static final Color INPUT_BG = new Color(35, 35, 50);
    private static final Color INPUT_BORDER = new Color(60, 60, 85);
    private static final Color ACCENT = new Color(80, 140, 255);

    // ── Engine state ──────────────────────────────────────────
    private volatile boolean running = false;
    private volatile long clickCount = 0;
    private SimpleClickEngine engine;
    private Thread engineThread;

    // ── Smart pin mode ────────────────────────────────────────
    private boolean smartModeActive = false;
    private List<SmartPin> smartPins = new ArrayList<>();
    private JWindow smartBar;
    private JLabel barCoordsLabel, barPinCountLabel;

    // ── Hotkeys ───────────────────────────────────────────────
    private int startStopKey = NativeKeyEvent.VC_F6;
    private String startStopName = "F6";
    private int smartPinKey = NativeKeyEvent.VC_F7;
    private String smartPinName = "F7";
    private boolean listeningForStartStop = false;
    private boolean listeningForSmartPin = false;

    // ── UI refs ───────────────────────────────────────────────
    private JLabel statusLabel, clickCountLabel, smartIntervalLabel;
    private JLabel startStopKeyLabel, smartPinKeyLabel;
    private JSpinner spHours, spMinutes, spSeconds, spTenths, spHundredths, spThousandths;
    private JTextField maxClicksField;
    private JComboBox<String> mouseButtonCombo, clickTypeCombo;
    private JPanel intervalSpinnersPanel;
    private DefaultTableModel tableModel;
    private JTable pointsTable;

    // ── Parent frame ref ──────────────────────────────────────
    private Window parentWindow;

    public SimpleClickPanel() {
        setBackground(BG);
        setLayout(new BorderLayout());
        try {
            GlobalScreen.addNativeKeyListener(this);
        } catch (Exception ignored) {
        }

        // ── Title ─────────────────────────────────────────────
        JPanel titleBar = new JPanel();
        titleBar.setLayout(new BoxLayout(titleBar, BoxLayout.Y_AXIS));
        titleBar.setBackground(BG);
        titleBar.setBorder(new EmptyBorder(14, 18, 8, 18));
        JLabel title = new JLabel("Build Simple Click");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Fast & Simple Auto Clicker");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subtitle.setForeground(TEXT_DIM);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleBar.add(title);
        titleBar.add(Box.createVerticalStrut(2));
        titleBar.add(subtitle);

        // ── Left column: interval + status + buttons ──────────
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBackground(BG);
        leftCol.setBorder(new EmptyBorder(0, 6, 8, 4));
        leftCol.add(buildIntervalPanel());
        leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(buildStatusPanel());
        leftCol.add(Box.createVerticalStrut(14));
        leftCol.add(buildButtons());
        leftCol.add(Box.createVerticalGlue());

        // ── Right column: click points + hotkeys ──────────────
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setBackground(BG);
        rightCol.setBorder(new EmptyBorder(0, 4, 8, 6));
        rightCol.add(buildPointsTable());
        rightCol.add(Box.createVerticalStrut(10));
        rightCol.add(buildHotkeyPanel());
        rightCol.add(Box.createVerticalGlue());

        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setBackground(BG);
        body.add(leftCol);
        body.add(rightCol);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(titleBar, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        addHierarchyListener(e -> {
            if (parentWindow == null)
                parentWindow = SwingUtilities.getWindowAncestor(this);
        });
    }

    // =========================================================
    // INTERVAL PANEL
    // =========================================================
    private JPanel buildIntervalPanel() {
        JPanel p = darkPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(darkTitledBorder("Click Interval"));

        String[] unitLabels = { "Hours", "Minutes", "Seconds", "1/10 s", "1/100 s", "1/1000 s" };
        JPanel headerRow = new JPanel(new GridLayout(1, 6, 0, 0));
        headerRow.setBackground(PANEL_BG);
        headerRow.setBorder(new EmptyBorder(2, 0, 2, 0));
        headerRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerRow.setBorder(new EmptyBorder(2, 0, 2, 0));
        for (String u : unitLabels) {
            JLabel lbl = new JLabel(u, SwingConstants.CENTER);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lbl.setForeground(TEXT_DIM);
            headerRow.add(lbl);
        }

        intervalSpinnersPanel = new JPanel(new GridLayout(1, 6, 2, 0));
        intervalSpinnersPanel.setBackground(PANEL_BG);
        intervalSpinnersPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        spHours = dsp(0, 0, 23);
        spMinutes = dsp(0, 0, 59);
        spSeconds = dsp(0, 0, 59);
        spTenths = dsp(1, 0, 9);
        spHundredths = dsp(0, 0, 9);
        spThousandths = dsp(0, 0, 9);
        for (JSpinner s : new JSpinner[] { spHours, spMinutes, spSeconds, spTenths, spHundredths, spThousandths })
            intervalSpinnersPanel.add(s);

        // Fix header alignment
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

        // NEW
        JPanel spinnerWrapper = new JPanel();
        spinnerWrapper.setLayout(new BoxLayout(spinnerWrapper, BoxLayout.Y_AXIS));
        spinnerWrapper.setBackground(PANEL_BG);
        spinnerWrapper.setBorder(new EmptyBorder(4, 4, 6, 4));
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        headerRow.setPreferredSize(new Dimension(100, 16));
        intervalSpinnersPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        intervalSpinnersPanel.setPreferredSize(new Dimension(100, 26));
        spinnerWrapper.add(headerRow);
        spinnerWrapper.add(Box.createVerticalStrut(2));
        spinnerWrapper.add(intervalSpinnersPanel);
        spinnerWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        p.add(spinnerWrapper);
        p.add(darkSeparator());

        p.add(spinnerWrapper);
        p.add(darkSeparator());

        JPanel grid = new JPanel(new GridLayout(3, 2, 8, 8));
        grid.setBackground(PANEL_BG);
        grid.setBorder(new EmptyBorder(8, 8, 8, 8));
        grid.add(dlbl("Mouse Button:"));
        mouseButtonCombo = dcombo(new String[] { "Left", "Right", "Middle" });
        grid.add(mouseButtonCombo);
        grid.add(dlbl("Click Type:"));
        clickTypeCombo = dcombo(new String[] { "Single", "Double" });
        grid.add(clickTypeCombo);
        grid.add(dlbl("Max Clicks (0=∞):"));
        maxClicksField = dfield("0");
        grid.add(maxClicksField);
        p.add(grid);

        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        hint.setBackground(PANEL_BG);
        smartIntervalLabel = new JLabel("Smart Interval off — set Clicks > 1 in any row to activate");
        smartIntervalLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        smartIntervalLabel.setForeground(TEXT_DIM);
        hint.add(smartIntervalLabel);
        p.add(hint);
        return p;
    }

    // =========================================================
    // CLICK POINTS TABLE (with Smart Pin button)
    // =========================================================
    private JPanel buildPointsTable() {
        JPanel p = darkPanel();
        p.setLayout(new BorderLayout(6, 6));
        p.setBorder(darkTitledBorder("Click Points  (empty = click at cursor)"));

        tableModel = new DefaultTableModel(
                new String[] { "#", "X", "Y", "Clicks", "Sub-Delay(ms)", "Delay-After(ms)" }, 0) {
            public boolean isCellEditable(int r, int c) {
                return c > 0;
            }
        };

        pointsTable = new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pointsTable.setRowHeight(26);
        pointsTable.setBackground(new Color(32, 32, 44));
        pointsTable.setForeground(TEXT_MAIN);
        pointsTable.setGridColor(new Color(45, 45, 60));
        pointsTable.setSelectionBackground(new Color(50, 80, 140));
        pointsTable.setSelectionForeground(Color.WHITE);
        pointsTable.getTableHeader().setBackground(new Color(35, 35, 50));
        pointsTable.getTableHeader().setForeground(TEXT_DIM);
        pointsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 10));
        int[] cw = { 22, 55, 55, 50, 100, 110 };
        for (int i = 0; i < cw.length; i++)
            pointsTable.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);

        tableModel.addTableModelListener(e -> {
            int col = e.getColumn(), row = e.getFirstRow();
            if (col == 3 || col == TableModelEvent.ALL_COLUMNS)
                SwingUtilities.invokeLater(this::refreshSmartInterval);
            // Sync table X,Y edits back to pins
            if (row >= 0 && row < smartPins.size()) {
                SmartPin pin = smartPins.get(row);
                try {
                    if (col == 1)
                        pin.screenX = Integer.parseInt(tableModel.getValueAt(row, 1).toString());
                    if (col == 2)
                        pin.screenY = Integer.parseInt(tableModel.getValueAt(row, 2).toString());
                    if (col == 1 || col == 2)
                        pin.win.setLocation(pin.screenX - pin.win.getWidth() / 2, pin.screenY - pin.win.getHeight());
                    if (col == 3 || col == 4 || col == 5)
                        pin.refreshLabel();
                } catch (Exception ignored) {
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pointsTable);
        scroll.setPreferredSize(new Dimension(300, 120));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        scroll.getViewport().setBackground(new Color(32, 32, 44));

        // Button row
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        btns.setBackground(PANEL_BG);

        JButton smartPinBtn = dactionBtn("⊕ Smart Pin", new Color(80, 140, 255));
        JButton addBtn = dactionBtn("+ Add", new Color(80, 140, 255));
        JButton remBtn = dactionBtn("- Remove", new Color(220, 70, 70));
        JButton clrBtn = dactionBtn("Clear All", new Color(120, 120, 150));

        smartPinBtn.addActionListener(e -> toggleSmartMode());
        addBtn.addActionListener(e -> {
            Point m = MouseInfo.getPointerInfo().getLocation();
            long iv = getIntervalMs();
            int row = tableModel.getRowCount();
            tableModel.addRow(new Object[] { row + 1, m.x, m.y, 1, iv, iv });
            refreshSmartInterval();
        });
        remBtn.addActionListener(e -> {
            int row = pointsTable.getSelectedRow();
            if (row >= 0) {
                if (row < smartPins.size()) {
                    smartPins.get(row).dispose();
                    smartPins.remove(row);
                }
                tableModel.removeRow(row);
                refreshRowNumbers();
                for (int i = 0; i < smartPins.size(); i++)
                    smartPins.get(i).rowIndex = i;
                refreshSmartInterval();
            }
        });
        clrBtn.addActionListener(e -> {
            for (SmartPin pin : smartPins)
                pin.dispose();
            smartPins.clear();
            tableModel.setRowCount(0);
            refreshSmartInterval();
        });

        btns.add(smartPinBtn);
        btns.add(addBtn);
        btns.add(remBtn);
        btns.add(clrBtn);
        p.add(scroll, BorderLayout.CENTER);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    // =========================================================
    // HOTKEY PANEL
    // =========================================================
    // Replace the entire buildHotkeyPanel method body:
    private JPanel buildHotkeyPanel() {
        JPanel p = darkPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(darkTitledBorder("Hotkeys"));

        JPanel grid = new JPanel(new GridLayout(2, 3, 8, 6));
        grid.setBackground(PANEL_BG);
        grid.setBorder(new EmptyBorder(6, 8, 8, 8));

        startStopKeyLabel = new JLabel("Start/Stop: [" + startStopName + "]");
        startStopKeyLabel.setForeground(TEXT_MAIN);
        startStopKeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        smartPinKeyLabel = new JLabel("Smart Pin: [" + smartPinName + "]");
        smartPinKeyLabel.setForeground(TEXT_MAIN);
        smartPinKeyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton changeSSBtn = dactionBtn("Change", new Color(120, 120, 180));
        JButton changeSPBtn = dactionBtn("Change", new Color(120, 120, 180));
        JButton smartPinBtn2 = dactionBtn("⊕ Enter Smart Pin", new Color(80, 140, 255));

        changeSSBtn.addActionListener(e -> startListeningForKey(true));
        changeSPBtn.addActionListener(e -> startListeningForKey(false));
        smartPinBtn2.addActionListener(e -> toggleSmartMode());

        grid.add(startStopKeyLabel);
        grid.add(changeSSBtn);
        grid.add(new JLabel());
        grid.add(smartPinKeyLabel);
        grid.add(changeSPBtn);
        grid.add(smartPinBtn2);

        // Fix empty label bg
        for (Component c : grid.getComponents())
            if (c instanceof JLabel)
                ((JLabel) c).setBackground(PANEL_BG);

        p.add(grid);
        return p;
    }

    // =========================================================
    // STATUS + BUTTONS
    // =========================================================
    private JPanel buildStatusPanel() {
        JPanel p = darkPanel();
        p.setLayout(new GridLayout(2, 1, 4, 4));
        p.setBorder(darkTitledBorder("Status"));
        statusLabel = new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setBorder(new EmptyBorder(4, 10, 0, 0));
        clickCountLabel = new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        clickCountLabel.setForeground(TEXT_DIM);
        clickCountLabel.setBorder(new EmptyBorder(0, 10, 4, 0));
        p.add(statusLabel);
        p.add(clickCountLabel);
        return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        p.setBackground(BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton startBtn = bigBtn("▶  Start", new Color(40, 200, 80), new Color(40, 160, 80));
        JButton stopBtn = bigBtn("■  Stop", new Color(220, 70, 70), new Color(180, 50, 50));
        startBtn.addActionListener(e -> startClicking());
        stopBtn.addActionListener(e -> stopClicking());
        p.add(startBtn);
        p.add(stopBtn);
        return p;
    }

    // =========================================================
    // SMART PIN MODE
    // =========================================================
    private void toggleSmartMode() {
        smartModeActive = !smartModeActive;
        if (smartModeActive) {
            if (parentWindow == null)
                parentWindow = SwingUtilities.getWindowAncestor(this);
            if (parentWindow != null)
                parentWindow.setVisible(false);
            showSmartBar();
            for (SmartPin p : smartPins)
                p.show();
        } else {
            hideSmartBar();
            for (SmartPin p : smartPins)
                p.hide();
            if (parentWindow != null) {
                parentWindow.setVisible(true);
                parentWindow.toFront();
            }
        }
    }

    private void showSmartBar() {
        smartBar = new JWindow();
        smartBar.setAlwaysOnTop(true);
        smartBar.setBackground(new Color(0, 0, 0, 0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 15, 22, 240));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(80, 140, 255, 200));
                g2.fillRoundRect(0, 0, getWidth(), 3, 4, 4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 7));

        JLabel dragHandle = new JLabel("⠿");
        dragHandle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        dragHandle.setForeground(new Color(100, 100, 130));
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLbl = new JLabel("Smart Pin");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLbl.setForeground(ACCENT);

        barPinCountLabel = new JLabel("Pins: " + smartPins.size());
        barPinCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        barPinCountLabel.setForeground(TEXT_DIM);

        barCoordsLabel = new JLabel("X: ─── Y: ───");
        barCoordsLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        barCoordsLabel.setForeground(new Color(80, 220, 120));

        JButton addPinBtn = dactionBtn("+ Pin", new Color(80, 140, 255));
        addPinBtn.addActionListener(e -> {
            Point m = MouseInfo.getPointerInfo().getLocation();
            addSmartPin(m.x, m.y);
        });

        JButton doneBtn = dactionBtn("✓ Done", new Color(120, 120, 150));
        doneBtn.addActionListener(e -> toggleSmartMode());

        bar.add(dragHandle);
        bar.add(titleLbl);
        bar.add(sep());
        bar.add(barPinCountLabel);
        bar.add(sep());
        bar.add(barCoordsLabel);
        bar.add(sep());
        bar.add(addPinBtn);
        bar.add(doneBtn);

        int[] off = { 0, 0 };
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                off[0] = e.getX();
                off[1] = e.getY();
            }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point loc = smartBar.getLocationOnScreen();
                smartBar.setLocation(loc.x + e.getX() - off[0], loc.y + e.getY() - off[1]);
            }
        });

        smartBar.setContentPane(bar);
        smartBar.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        smartBar.setLocation(screen.width / 2 - smartBar.getWidth() / 2, 18);
        smartBar.setVisible(true);

        // Live coordinate ticker
        new Timer(50, e -> {
            if (!smartModeActive) {
                ((Timer) e.getSource()).stop();
                return;
            }
            Point p = MouseInfo.getPointerInfo().getLocation();
            barCoordsLabel.setText("X: " + p.x + "  Y: " + p.y);
        }).start();
    }

    private void hideSmartBar() {
        if (smartBar != null) {
            smartBar.dispose();
            smartBar = null;
        }
    }

    private void addSmartPin(int x, int y) {
        int row = tableModel.getRowCount();
        long iv = getIntervalMs();
        tableModel.addRow(new Object[] { row + 1, x, y, 1, iv, iv });
        SmartPin pin = new SmartPin(x, y, row);
        smartPins.add(pin);
        if (smartModeActive)
            pin.show();
        if (barPinCountLabel != null)
            barPinCountLabel.setText("Pins: " + smartPins.size());
        refreshSmartInterval();
    }

    private JLabel sep() {
        JLabel s = new JLabel("|");
        s.setForeground(new Color(60, 60, 80));
        return s;
    }

    // =========================================================
    // SMART PIN WIDGET
    // =========================================================
    class SmartPin {
        JWindow win;
        int screenX, screenY, rowIndex;
        JLabel infoLabel;
        int dragOffX, dragOffY;

        SmartPin(int x, int y, int row) {
            screenX = x;
            screenY = y;
            rowIndex = row;
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
                    // Shadow
                    g2.setColor(new Color(0, 0, 0, 50));
                    g2.fillRoundRect(3, 3, getWidth() - 3, getHeight() - 16, 12, 12);
                    // Body
                    g2.setColor(new Color(20, 20, 32, 230));
                    g2.fillRoundRect(0, 0, getWidth() - 3, getHeight() - 13, 12, 12);
                    // Tip
                    int cx = (getWidth() - 3) / 2;
                    g2.fillPolygon(new int[] { cx - 7, cx + 7, cx },
                            new int[] { getHeight() - 13, getHeight() - 13, getHeight() - 1 }, 3);
                    // Badge
                    g2.setColor(new Color(80, 140, 255));
                    g2.fillOval(4, 4, 20, 20);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    String num = String.valueOf(rowIndex + 1);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(num, 14 - fm.stringWidth(num) / 2, 18);
                }
            };
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.setPreferredSize(new Dimension(155, 58));

            infoLabel = new JLabel(pinInfoHtml());
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            infoLabel.setBorder(new EmptyBorder(4, 28, 14, 4));
            panel.add(infoLabel, BorderLayout.CENTER);

            JLabel closeBtn = new JLabel("×");
            closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
            closeBtn.setForeground(new Color(180, 180, 200));
            closeBtn.setBorder(new EmptyBorder(2, 0, 14, 6));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    removePin(SmartPin.this);
                }

                public void mouseEntered(MouseEvent e) {
                    closeBtn.setForeground(new Color(255, 80, 80));
                }

                public void mouseExited(MouseEvent e) {
                    closeBtn.setForeground(new Color(180, 180, 200));
                }
            });
            panel.add(closeBtn, BorderLayout.EAST);

            panel.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragOffX = e.getX();
                    dragOffY = e.getY();
                }
            });
            panel.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    Point loc = win.getLocationOnScreen();
                    int nx = loc.x + e.getX() - dragOffX, ny = loc.y + e.getY() - dragOffY;
                    win.setLocation(nx, ny);
                    screenX = nx + win.getWidth() / 2;
                    screenY = ny + win.getHeight();
                    if (rowIndex < tableModel.getRowCount()) {
                        tableModel.setValueAt(screenX, rowIndex, 1);
                        tableModel.setValueAt(screenY, rowIndex, 2);
                    }
                }
            });

            win.setContentPane(panel);
            win.pack();
            win.setLocation(screenX - win.getWidth() / 2, screenY - win.getHeight());
            win.setVisible(false);
        }

        String pinInfoHtml() {
            if (rowIndex < tableModel.getRowCount()) {
                Object clicks = tableModel.getValueAt(rowIndex, 3);
                Object subDelay = tableModel.getValueAt(rowIndex, 4);
                Object delay = tableModel.getValueAt(rowIndex, 5);
                return "<html><span style='color:#64b4ff'>×" + clicks + "</span> "
                        + "<span style='color:#8888ff'>" + subDelay + "ms</span><br>"
                        + "<span style='color:#888888'>→" + delay + "ms</span></html>";
            }
            return "";
        }

        void refreshLabel() {
            infoLabel.setText(pinInfoHtml());
        }

        void show() {
            win.setVisible(true);
        }

        void hide() {
            win.setVisible(false);
        }

        void dispose() {
            win.dispose();
        }
    }

    private void removePin(SmartPin pin) {
        int idx = smartPins.indexOf(pin);
        if (idx >= 0) {
            pin.dispose();
            smartPins.remove(idx);
            tableModel.removeRow(idx);
            refreshRowNumbers();
            for (int i = 0; i < smartPins.size(); i++)
                smartPins.get(i).rowIndex = i;
            if (barPinCountLabel != null)
                barPinCountLabel.setText("Pins: " + smartPins.size());
            refreshSmartInterval();
        }
    }

    // =========================================================
    // HOTKEY LISTENING
    // =========================================================
    private void startListeningForKey(boolean forStartStop) {
        if (forStartStop) {
            listeningForStartStop = true;
            startStopKeyLabel.setText("Start/Stop: [Press any key...]");
            startStopKeyLabel.setForeground(ACCENT);
        } else {
            listeningForSmartPin = true;
            smartPinKeyLabel.setText("Smart Pin: [Press any key...]");
            smartPinKeyLabel.setForeground(ACCENT);
        }
        // 5 second timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            SwingUtilities.invokeLater(() -> {
                if (listeningForStartStop) {
                    listeningForStartStop = false;
                    startStopKeyLabel.setText("Start/Stop: [" + startStopName + "]");
                    startStopKeyLabel.setForeground(TEXT_MAIN);
                }
                if (listeningForSmartPin) {
                    listeningForSmartPin = false;
                    smartPinKeyLabel.setText("Smart Pin: [" + smartPinName + "]");
                    smartPinKeyLabel.setForeground(TEXT_MAIN);
                }
            });
        }).start();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (listeningForStartStop) {
            startStopKey = e.getKeyCode();
            startStopName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForStartStop = false;
            SwingUtilities.invokeLater(() -> {
                startStopKeyLabel.setText("Start/Stop: [" + startStopName + "]");
                startStopKeyLabel.setForeground(TEXT_MAIN);
            });
            return;
        }
        if (listeningForSmartPin) {
            smartPinKey = e.getKeyCode();
            smartPinName = NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForSmartPin = false;
            SwingUtilities.invokeLater(() -> {
                smartPinKeyLabel.setText("Smart Pin: [" + smartPinName + "]");
                smartPinKeyLabel.setForeground(TEXT_MAIN);
            });
            return;
        }
        if (e.getKeyCode() == smartPinKey)
            SwingUtilities.invokeLater(this::toggleSmartMode);
        if (e.getKeyCode() == startStopKey) {
            if (running)
                stopClicking();
            else
                startClicking();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    // =========================================================
    // ENGINE CONTROL
    // =========================================================
    public void startClicking() {
        if (running)
            return;
        running = true;
        clickCount = 0;
        updateStatus("● Running", new Color(40, 200, 80));

        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try {
                points.add(new int[] {
                        Integer.parseInt(tableModel.getValueAt(i, 1).toString()),
                        Integer.parseInt(tableModel.getValueAt(i, 2).toString()),
                        Integer.parseInt(tableModel.getValueAt(i, 3).toString()),
                        Integer.parseInt(tableModel.getValueAt(i, 4).toString()),
                        Integer.parseInt(tableModel.getValueAt(i, 5).toString()) });
            } catch (Exception ignored) {
            }
        }
        long maxClicks;
        try {
            maxClicks = Long.parseLong(maxClicksField.getText().trim());
        } catch (Exception e) {
            maxClicks = 0;
        }
        int btn = mouseButtonCombo.getSelectedIndex();
        boolean dbl = clickTypeCombo.getSelectedIndex() == 1;

        engine = new SimpleClickEngine(points, getIntervalMs(), maxClicks, btn, dbl, true, 1, null);
        engine.setClickCallback(total -> {
            clickCount = total;
            SwingUtilities.invokeLater(() -> {
                clickCountLabel.setText("Clicks: " + total);
                clickCountLabel.setForeground(new Color(80, 200, 120));
            });
        });
        engineThread = new Thread(engine);
        engineThread.setDaemon(true);
        engineThread.start();
        new Thread(() -> {
            while (running && engineThread.isAlive()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            }
            stopClicking();
        }).start();
    }

    public void stopClicking() {
        running = false;
        if (engine != null)
            engine.stop();
        SwingUtilities.invokeLater(() -> updateStatus("● Stopped", new Color(200, 60, 60)));
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private long getIntervalMs() {
        long h = ((Number) spHours.getValue()).longValue(), m = ((Number) spMinutes.getValue()).longValue();
        long s = ((Number) spSeconds.getValue()).longValue(), t = ((Number) spTenths.getValue()).longValue();
        long hu = ((Number) spHundredths.getValue()).longValue(), th = ((Number) spThousandths.getValue()).longValue();
        return Math.max(1, h * 3_600_000L + m * 60_000L + s * 1_000L + t * 100L + hu * 10L + th);
    }

    private void refreshSmartInterval() {
        boolean active = false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try {
                if (Integer.parseInt(tableModel.getValueAt(i, 3).toString()) > 1) {
                    active = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (intervalSpinnersPanel != null)
            for (Component c : intervalSpinnersPanel.getComponents())
                c.setEnabled(!active);
        if (smartIntervalLabel != null) {
            if (active) {
                smartIntervalLabel.setText("⚡ Smart Interval active");
                smartIntervalLabel.setForeground(ACCENT);
            } else {
                smartIntervalLabel.setText("Smart Interval off — set Clicks > 1 to activate");
                smartIntervalLabel.setForeground(TEXT_DIM);
            }
        }
    }

    private void refreshRowNumbers() {
        for (int i = 0; i < tableModel.getRowCount(); i++)
            tableModel.setValueAt(i + 1, i, 0);
    }

    private void updateStatus(String t, Color c) {
        statusLabel.setText(t);
        statusLabel.setForeground(c);
    }

    // ── Dark theme builders ───────────────────────────────────
    private JPanel darkPanel() {
        JPanel p = new JPanel();
        p.setBackground(PANEL_BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private TitledBorder darkTitledBorder(String t) {
        return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COL), t, TitledBorder.LEFT,
                TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 10), TEXT_DIM);
    }

    private JSeparator darkSeparator() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER_COL);
        s.setBackground(PANEL_BG);
        return s;
    }

    private JSpinner dsp(int val, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        sp.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sp.setPreferredSize(new Dimension(999, 24));
        sp.setBackground(INPUT_BG);
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) sp.getEditor();
        ed.getTextField().setBackground(INPUT_BG);
        ed.getTextField().setForeground(TEXT_MAIN);
        ed.getTextField().setCaretColor(TEXT_MAIN);
        ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(INPUT_BORDER));
        return sp;
    }

    private JLabel dlbl(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(TEXT_MAIN);
        return l;
    }

    private JTextField dfield(String v) {
        JTextField f = new JTextField(v, 8);
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBackground(INPUT_BG);
        f.setForeground(TEXT_MAIN);
        f.setCaretColor(TEXT_MAIN);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INPUT_BORDER),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JComboBox<String> dcombo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(new Font("SansSerif", Font.PLAIN, 11));
        c.setBackground(INPUT_BG);
        c.setForeground(TEXT_MAIN);
        c.setBorder(BorderFactory.createLineBorder(INPUT_BORDER));
        return c;
    }

    private JButton dactionBtn(String text, Color accentColor) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setBackground(new Color(28, 28, 38));
        b.setForeground(accentColor);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 1),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(40, 40, 55));
            }

            public void mouseExited(MouseEvent e) {
                b.setBackground(new Color(28, 28, 38));
            }
        });
        return b;
    }

    private JButton bigBtn(String text, Color textColor, Color border) {
        JButton b = new JButton(text);
        b.setPreferredSize(new Dimension(120, 36));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setBackground(new Color(28, 28, 38));
        b.setForeground(textColor);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 2),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(40, 40, 55));
            }

            public void mouseExited(MouseEvent e) {
                b.setBackground(new Color(28, 28, 38));
            }
        });
        return b;
    }
}