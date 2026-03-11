import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.TableModelEvent;
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
    static boolean running              = false;
    static long    clickCount           = 0;
    static int     hotkeyKeyCode        = NativeKeyEvent.VC_F6;
    static String  hotkeyName           = "F6";
    static int     smartKeyCode         = NativeKeyEvent.VC_F7;
    static String  smartKeyName         = "F7";
    static boolean listeningForHotkey   = false;
    static boolean listeningForSmartKey = false;
    static boolean smartModeActive      = false;
    static TurboClick instance;

    // === Live HUD state (updated by click thread) ===
    static volatile int    hudPointIndex    = 0;   // current click # (0-based)
    static volatile int    hudPointTotal    = 0;   // total click points
    static volatile long   hudLoopsLeft     = 0;   // max loops remaining (0=∞)
    static volatile int    hudSubClickIndex = 0;   // current sub-click index
    static volatile int    hudSubClickTotal = 0;   // sub-clicks for current point
    static volatile long   hudSubDelayLeft  = 0;   // ms countdown till next sub-click
    static volatile long   hudNextPointLeft = 0;   // ms countdown till next point

    // === UI ===
    static JLabel     statusLabel, clickCountLabel, hotkeyLabel, smartKeyLabel;
    static JLabel     smartIntervalStatusLabel;
    static JSpinner   spHours, spMinutes, spSeconds, spTenths, spHundredths, spThousandths;
    static JTextField maxClicksField;
    static JComboBox<String> mouseButtonCombo, clickTypeCombo;
    static JButton    startButton, stopButton, changeHotkeyBtn, changeSmartKeyBtn;
    static JPanel     intervalSpinnersPanel;
    static JFrame     frame;

    // === Table — columns: #, X, Y, Clicks, Sub-Click Delay (ms), Delay-After (ms) ===
    static DefaultTableModel tableModel;
    static JTable pointsTable;

    // === Smart pins ===
    static List<SmartPin> smartPins = new ArrayList<>();

    // === HUD window ===
    static JWindow hudWindow;
    static JLabel  hudPointLbl, hudLoopsLbl, hudSubLbl, hudSubDelayLbl, hudNextLbl;

    // =========================================================
    //  INTERVAL HELPERS
    // =========================================================
    static long getIntervalMs() {
        long h  = ((Number) spHours.getValue()).longValue();
        long m  = ((Number) spMinutes.getValue()).longValue();
        long s  = ((Number) spSeconds.getValue()).longValue();
        long t  = ((Number) spTenths.getValue()).longValue();
        long hu = ((Number) spHundredths.getValue()).longValue();
        long th = ((Number) spThousandths.getValue()).longValue();
        return Math.max(1, h*3_600_000L + m*60_000L + s*1_000L + t*100L + hu*10L + th);
    }

    static void setIntervalSpinnersEnabled(boolean on) {
        if (intervalSpinnersPanel == null) return;
        for (Component c : intervalSpinnersPanel.getComponents()) c.setEnabled(on);
    }

    static boolean isSmartIntervalActive() {
        if (tableModel == null) return false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try { if (Integer.parseInt(tableModel.getValueAt(i,3).toString()) > 1) return true; }
            catch (Exception ignored) {}
        }
        return false;
    }

    static void refreshSmartIntervalStatus() {
        boolean active = isSmartIntervalActive();
        setIntervalSpinnersEnabled(!active);
        if (smartIntervalStatusLabel == null) return;
        if (active) {
            smartIntervalStatusLabel.setText("⚡ Smart Interval active — Click Interval = gap between sub-clicks");
            smartIntervalStatusLabel.setForeground(new Color(30,150,220));
        } else {
            smartIntervalStatusLabel.setText("Smart Interval off — set Clicks > 1 in any row to activate");
            smartIntervalStatusLabel.setForeground(new Color(150,150,150));
        }
    }

    // =========================================================
    //  SMART PIN
    // =========================================================
    static class SmartPin {
        JWindow win;
        int screenX, screenY, rowIndex;
        JLabel infoLabel;
        int dragOffsetX, dragOffsetY;

        SmartPin(int x, int y, int row) { screenX=x; screenY=y; rowIndex=row; build(); }

        void build() {
            win = new JWindow();
            win.setAlwaysOnTop(true);
            win.setBackground(new Color(0,0,0,0));

            JPanel panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0,0,0,40));
                    g2.fillRoundRect(3,3,getWidth()-3,getHeight()-16,12,12);
                    g2.setColor(new Color(30,30,30,225));
                    g2.fillRoundRect(0,0,getWidth()-3,getHeight()-13,12,12);
                    int cx=(getWidth()-3)/2;
                    g2.fillPolygon(new int[]{cx-7,cx+7,cx},
                                   new int[]{getHeight()-13,getHeight()-13,getHeight()-1},3);
                    g2.setColor(new Color(255,80,80));
                    g2.fillOval(4,4,20,20);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif",Font.BOLD,11));
                    String num=String.valueOf(rowIndex+1);
                    FontMetrics fm=g2.getFontMetrics();
                    g2.drawString(num,14-fm.stringWidth(num)/2,18);
                }
            };
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.setPreferredSize(new Dimension(155,58));

            infoLabel = new JLabel(pinInfoHtml());
            infoLabel.setFont(new Font("SansSerif",Font.PLAIN,10));
            infoLabel.setBorder(new EmptyBorder(4,28,14,4));
            panel.add(infoLabel, BorderLayout.CENTER);

            JLabel closeBtn = new JLabel("×");
            closeBtn.setFont(new Font("SansSerif",Font.BOLD,13));
            closeBtn.setForeground(new Color(200,200,200));
            closeBtn.setBorder(new EmptyBorder(2,0,14,6));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){ removePin(SmartPin.this); }
                public void mouseEntered(MouseEvent e){ closeBtn.setForeground(Color.RED); }
                public void mouseExited(MouseEvent e){ closeBtn.setForeground(new Color(200,200,200)); }
            });
            panel.add(closeBtn, BorderLayout.EAST);

            panel.addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){ dragOffsetX=e.getX(); dragOffsetY=e.getY(); }
            });
            panel.addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    Point loc=win.getLocationOnScreen();
                    int nx=loc.x+e.getX()-dragOffsetX, ny=loc.y+e.getY()-dragOffsetY;
                    win.setLocation(nx,ny);
                    screenX=nx+win.getWidth()/2; screenY=ny+win.getHeight();
                    if (rowIndex<tableModel.getRowCount()) {
                        tableModel.setValueAt(screenX,rowIndex,1);
                        tableModel.setValueAt(screenY,rowIndex,2);
                    }
                }
            });

            win.setContentPane(panel);
            win.pack();
            win.setLocation(screenX-win.getWidth()/2, screenY-win.getHeight());
            win.setVisible(false);
        }

        String pinInfoHtml() {
            if (rowIndex < tableModel.getRowCount()) {
                Object clicks   = tableModel.getValueAt(rowIndex,3);
                Object subDelay = tableModel.getValueAt(rowIndex,4);
                Object delay    = tableModel.getValueAt(rowIndex,5);
                return "<html>"
                    + "<span style='color:#64dc64'>×"+clicks+" clicks</span> "
                    + "<span style='color:#88aaff'>"+subDelay+"ms gap</span><br>"
                    + "<span style='color:#aaaaaa'>→next: "+delay+"ms</span>"
                    + "</html>";
            }
            return "";
        }

        void refreshLabel() { infoLabel.setText(pinInfoHtml()); }
        void show()    { win.setVisible(true); }
        void hide()    { win.setVisible(false); }
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
        } catch (NativeHookException e) { System.err.println("Hook failed: "+e.getMessage()); }
        SwingUtilities.invokeLater(TurboClick::buildUI);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { GlobalScreen.unregisterNativeHook(); } catch (NativeHookException ignored) {}
        }));
    }

    // =========================================================
    //  BUILD UI
    // =========================================================
    static void buildUI() {
        frame = new JFrame("TurboClick");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main,BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(12,15,12,15));
        main.setBackground(new Color(245,245,245));

        JLabel title = new JLabel("TurboClick");
        title.setFont(new Font("SansSerif",Font.BOLD,22));
        title.setForeground(new Color(30,30,30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Auto Clicker");
        subtitle.setFont(new Font("SansSerif",Font.PLAIN,12));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        main.add(Box.createVerticalStrut(5));
        main.add(title); main.add(subtitle);
        main.add(Box.createVerticalStrut(12));

        // ── Click Interval ────────────────────────────────────
        JPanel settings = new JPanel();
        settings.setLayout(new BoxLayout(settings,BoxLayout.Y_AXIS));
        settings.setBackground(Color.WHITE);
        settings.setBorder(makeTitledBorder("Click Interval"));

        String[] unitLabels = {"Hours","Minutes","Seconds","1/10 s","1/100 s","1/1000 s"};
        JPanel spinnerHeaderRow = new JPanel(new GridLayout(1,6,4,0));
        spinnerHeaderRow.setBackground(Color.WHITE);
        spinnerHeaderRow.setBorder(new EmptyBorder(4,8,0,8));
        for (String u : unitLabels) {
            JLabel lbl = new JLabel(u,SwingConstants.CENTER);
            lbl.setFont(new Font("SansSerif",Font.PLAIN,10));
            lbl.setForeground(new Color(90,90,90));
            spinnerHeaderRow.add(lbl);
        }
        intervalSpinnersPanel = new JPanel(new GridLayout(1,6,4,0));
        intervalSpinnersPanel.setBackground(Color.WHITE);
        intervalSpinnersPanel.setBorder(new EmptyBorder(2,8,6,8));
        spHours=makeSpinner(0,0,23); spMinutes=makeSpinner(0,0,59); spSeconds=makeSpinner(0,0,59);
        spTenths=makeSpinner(1,0,9); spHundredths=makeSpinner(0,0,9); spThousandths=makeSpinner(0,0,9);
        for (JSpinner sp : new JSpinner[]{spHours,spMinutes,spSeconds,spTenths,spHundredths,spThousandths})
            intervalSpinnersPanel.add(sp);
        settings.add(spinnerHeaderRow);
        settings.add(intervalSpinnersPanel);
        settings.add(new JSeparator());

        JPanel otherGrid = new JPanel(new GridLayout(3,2,8,8));
        otherGrid.setBackground(Color.WHITE);
        otherGrid.setBorder(new EmptyBorder(6,6,6,6));
        otherGrid.add(makeLabel("Mouse Button:"));
        mouseButtonCombo = makeCombo(new String[]{"Left","Right","Middle"});
        otherGrid.add(mouseButtonCombo);
        otherGrid.add(makeLabel("Click Type:"));
        clickTypeCombo = makeCombo(new String[]{"Single Click","Double Click"});
        otherGrid.add(clickTypeCombo);
        otherGrid.add(makeLabel("Max Clicks (0=∞):"));
        maxClicksField = makeField("0");
        otherGrid.add(maxClicksField);
        settings.add(otherGrid);

        JPanel smartHintRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        smartHintRow.setBackground(Color.WHITE);
        smartIntervalStatusLabel = new JLabel("Smart Interval off — set Clicks > 1 in any row to activate");
        smartIntervalStatusLabel.setFont(new Font("SansSerif",Font.ITALIC,10));
        smartIntervalStatusLabel.setForeground(new Color(150,150,150));
        smartHintRow.add(smartIntervalStatusLabel);
        settings.add(smartHintRow);
        main.add(settings);
        main.add(Box.createVerticalStrut(10));

        // ── Click Points Table ────────────────────────────────
        // Columns: #, X, Y, Clicks, Sub-Click Delay (ms), Delay-After (ms)
        JPanel pointsPanel = new JPanel(new BorderLayout(6,6));
        pointsPanel.setBackground(Color.WHITE);
        pointsPanel.setBorder(makeTitledBorder("Click Points  (empty = click at cursor)"));

        tableModel = new DefaultTableModel(
            new String[]{"#","X","Y","Clicks","Sub-Delay (ms)","Delay-After (ms)"},0) {
            public boolean isCellEditable(int r, int c) { return c > 0; }
        };
        pointsTable = new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif",Font.PLAIN,12));
        pointsTable.setRowHeight(24);
        pointsTable.getColumnModel().getColumn(0).setPreferredWidth(22);
        pointsTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        pointsTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        pointsTable.getColumnModel().getColumn(3).setPreferredWidth(45);
        pointsTable.getColumnModel().getColumn(4).setPreferredWidth(95);
        pointsTable.getColumnModel().getColumn(5).setPreferredWidth(105);
        pointsTable.setGridColor(new Color(220,220,220));
        pointsTable.setSelectionBackground(new Color(210,230,255));

        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow(), col = e.getColumn();
            if (row < 0) return;
            if (row < smartPins.size()) {
                SmartPin pin = smartPins.get(row);
                try {
                    if (col==1) { pin.screenX=Integer.parseInt(tableModel.getValueAt(row,1).toString()); }
                    if (col==2) { pin.screenY=Integer.parseInt(tableModel.getValueAt(row,2).toString()); }
                    if (col==1||col==2)
                        pin.win.setLocation(pin.screenX-pin.win.getWidth()/2,pin.screenY-pin.win.getHeight());
                    if (col==3||col==4||col==5) pin.refreshLabel();
                } catch (Exception ignored) {}
            }
            if (col==3||col==TableModelEvent.ALL_COLUMNS)
                SwingUtilities.invokeLater(TurboClick::refreshSmartIntervalStatus);
        });

        JScrollPane scroll = new JScrollPane(pointsTable);
        scroll.setPreferredSize(new Dimension(300,110));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));

        JPanel tblBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
        tblBtns.setBackground(Color.WHITE);
        JButton addManualBtn = makeActionButton("+ Add Point", new Color(80,80,80));
        JButton removeBtn    = makeActionButton("✕ Remove",    new Color(180,60,60));
        JButton clearBtn     = makeActionButton("Clear All",   new Color(120,120,120));
        addManualBtn.addActionListener(e -> { Point m=MouseInfo.getPointerInfo().getLocation(); addSmartPin(m.x,m.y); });
        removeBtn.addActionListener(e -> {
            int row=pointsTable.getSelectedRow();
            if (row>=0) {
                smartPins.get(row).dispose(); smartPins.remove(row);
                tableModel.removeRow(row); refreshRowNumbers();
                for (int i=0;i<smartPins.size();i++) smartPins.get(i).rowIndex=i;
                refreshSmartIntervalStatus();
            }
        });
        clearBtn.addActionListener(e -> {
            for (SmartPin p:smartPins) p.dispose(); smartPins.clear();
            tableModel.setRowCount(0); refreshSmartIntervalStatus();
        });
        tblBtns.add(addManualBtn); tblBtns.add(removeBtn); tblBtns.add(clearBtn);
        pointsPanel.add(scroll,BorderLayout.CENTER);
        pointsPanel.add(tblBtns,BorderLayout.SOUTH);
        main.add(pointsPanel);
        main.add(Box.createVerticalStrut(10));

        // ── Hotkeys ──────────────────────────────────────────
        JPanel hotkeyPanel = new JPanel(new GridLayout(2,1,4,6));
        hotkeyPanel.setBackground(Color.WHITE);
        hotkeyPanel.setBorder(makeTitledBorder("Hotkeys"));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        row1.setBackground(Color.WHITE);
        hotkeyLabel = new JLabel("Start/Stop: ["+hotkeyName+"]");
        hotkeyLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        changeHotkeyBtn = makeActionButton("Change",new Color(100,100,200));
        changeHotkeyBtn.addActionListener(e -> startListening(true));
        row1.add(hotkeyLabel); row1.add(changeHotkeyBtn);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        row2.setBackground(Color.WHITE);
        smartKeyLabel = new JLabel("Smart Click: ["+smartKeyName+"]");
        smartKeyLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        changeSmartKeyBtn = makeActionButton("Change",new Color(100,100,200));
        changeSmartKeyBtn.addActionListener(e -> startListening(false));
        JButton smartToggleBtn = makeActionButton("⊕ Smart Click",new Color(40,140,180));
        smartToggleBtn.setFont(new Font("SansSerif",Font.BOLD,11));
        smartToggleBtn.addActionListener(e -> toggleSmartMode());
        row2.add(smartKeyLabel); row2.add(changeSmartKeyBtn); row2.add(smartToggleBtn);
        hotkeyPanel.add(row1); hotkeyPanel.add(row2);
        main.add(hotkeyPanel);
        main.add(Box.createVerticalStrut(10));

        // ── Status ───────────────────────────────────────────
        JPanel statusPanel = new JPanel(new GridLayout(2,1,4,4));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(makeTitledBorder("Status"));
        statusLabel = new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif",Font.BOLD,13));
        statusLabel.setForeground(new Color(100,100,100));
        statusLabel.setBorder(new EmptyBorder(2,8,0,0));
        clickCountLabel = new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        clickCountLabel.setForeground(new Color(80,80,80));
        clickCountLabel.setBorder(new EmptyBorder(0,8,4,0));
        statusPanel.add(statusLabel); statusPanel.add(clickCountLabel);
        main.add(statusPanel);
        main.add(Box.createVerticalStrut(14));

        // ── Start / Stop ─────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,16,0));
        btnPanel.setBackground(new Color(245,245,245));
        startButton = new JButton("▶  Start");
        startButton.setPreferredSize(new Dimension(130,42));
        startButton.setFont(new Font("SansSerif",Font.BOLD,14));
        startButton.setBackground(new Color(40,160,80)); startButton.setForeground(Color.WHITE);
        startButton.setOpaque(true); startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(20,110,50),2),
            BorderFactory.createEmptyBorder(6,16,6,16)));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> startClicking());
        stopButton = new JButton("■  Stop");
        stopButton.setPreferredSize(new Dimension(130,42));
        stopButton.setFont(new Font("SansSerif",Font.BOLD,14));
        stopButton.setBackground(new Color(200,50,50)); stopButton.setForeground(Color.WHITE);
        stopButton.setOpaque(true); stopButton.setFocusPainted(false);
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
        frame.setMinimumSize(new Dimension(420,630));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // =========================================================
    //  HUD OVERLAY  (read-only, non-clickable, top-center)
    // =========================================================
    static void showHud() {
        hudWindow = new JWindow();
        hudWindow.setAlwaysOnTop(true);
        hudWindow.setBackground(new Color(0,0,0,0));
        // Make it non-focusable so it never steals clicks
        hudWindow.setFocusableWindowState(false);

        JPanel hud = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,15,15,210));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                // green top accent
                g2.setColor(new Color(40,200,80,200));
                g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        hud.setOpaque(false);
        hud.setLayout(new GridLayout(1,5,0,0));
        hud.setBorder(new EmptyBorder(6,12,6,12));

        hudPointLbl    = hudCell("Click #", "─/─",    new Color(100,200,255));
        hudLoopsLbl    = hudCell("Loops left", "─",   new Color(180,180,180));
        hudSubLbl      = hudCell("Sub-click", "─/─",  new Color(100,255,160));
        hudSubDelayLbl = hudCell("Sub delay", "─ms",  new Color(180,140,255));
        hudNextLbl     = hudCell("Next point", "─ms", new Color(255,180,80));

        hud.add(hudPointLbl);
        hud.add(hudLoopsLbl);
        hud.add(hudSubLbl);
        hud.add(hudSubDelayLbl);
        hud.add(hudNextLbl);

        hudWindow.setContentPane(hud);
        hudWindow.pack();
        hudWindow.setSize(520, hudWindow.getHeight());

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        hudWindow.setLocation(screen.width/2 - hudWindow.getWidth()/2, 18);
        hudWindow.setVisible(true);

        // Update HUD labels at 100ms tick
        new Timer(100, e -> {
            if (!running) { ((Timer)e.getSource()).stop(); return; }
            String pointStr = (hudPointTotal>0)
                ? (hudPointIndex+1)+"/"+hudPointTotal
                : "cursor";
            String loopStr = hudLoopsLeft<=0 ? "∞" : String.valueOf(hudLoopsLeft);
            String subStr  = hudSubClickTotal>1
                ? (hudSubClickIndex+1)+"/"+hudSubClickTotal
                : "1/1";
            String subDelayStr = hudSubDelayLeft > 0 ? hudSubDelayLeft+"ms" : "─";
            String nextStr     = hudNextPointLeft > 0 ? hudNextPointLeft+"ms" : "─";

            setHudCell(hudPointLbl,    "Click #",    pointStr);
            setHudCell(hudLoopsLbl,    "Loops left",  loopStr);
            setHudCell(hudSubLbl,      "Sub-click",   subStr);
            setHudCell(hudSubDelayLbl, "Sub delay",   subDelayStr);
            setHudCell(hudNextLbl,     "Next point",  nextStr);
        }).start();
    }

    // Build a two-line label cell: small grey title + big colored value
    static JLabel hudCell(String title, String value, Color valueColor) {
        JLabel l = new JLabel(
            "<html><center><span style='font-size:8px;color:#888888'>"+title+"</span><br>"
            +"<b><span style='font-size:12px;color:"+colorHex(valueColor)+"'>"+value+"</span></b></center></html>",
            SwingConstants.CENTER);
        l.setForeground(Color.WHITE);
        return l;
    }

    static void setHudCell(JLabel l, String title, String value) {
        Color vc = l.getForeground(); // we stored accent via foreground hack — just rebuild
        l.setText("<html><center>"
            +"<span style='font-size:8px;color:#888888'>"+title+"</span><br>"
            +"<b><span style='font-size:12px;color:white'>"+value+"</span></b>"
            +"</center></html>");
    }

    static String colorHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static void hideHud() {
        if (hudWindow != null) { hudWindow.dispose(); hudWindow = null; }
    }

    // =========================================================
    //  SMART MODE
    // =========================================================
    static void toggleSmartMode() {
        smartModeActive = !smartModeActive;
        if (smartModeActive) {
            frame.setVisible(false);
            showSmartBar();
            for (SmartPin p:smartPins) p.show();
        } else {
            hideSmartBar();
            for (SmartPin p:smartPins) p.hide();
            frame.setVisible(true);
            frame.toFront();
        }
    }

    static JWindow smartBar;
    static JLabel  barCoordsLabel, barPinCountLabel;

    static void showSmartBar() {
        smartBar = new JWindow();
        smartBar.setAlwaysOnTop(true);
        smartBar.setBackground(new Color(0,0,0,0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20,20,20,235));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(new Color(40,140,200,180));
                g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER,10,7));

        JLabel dragHandle=new JLabel("⠿");
        dragHandle.setFont(new Font("SansSerif",Font.PLAIN,16));
        dragHandle.setForeground(new Color(120,120,120));
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        JLabel titleLbl=new JLabel("Smart Click");
        titleLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        titleLbl.setForeground(new Color(100,200,255));
        barPinCountLabel=new JLabel("Pins: "+smartPins.size());
        barPinCountLabel.setFont(new Font("SansSerif",Font.PLAIN,11));
        barPinCountLabel.setForeground(new Color(180,180,180));
        barCoordsLabel=new JLabel("X: ─── Y: ───");
        barCoordsLabel.setFont(new Font("Monospaced",Font.BOLD,12));
        barCoordsLabel.setForeground(new Color(80,220,120));
        JButton addPinBtn=makeActionButton("+ Pin",new Color(50,130,200));
        addPinBtn.addActionListener(e -> {
            Point m=MouseInfo.getPointerInfo().getLocation();
            addSmartPin(m.x,m.y); updateBarPinCount();
        });
        JButton doneBtn=makeActionButton("✓ Done",new Color(80,80,80));
        doneBtn.addActionListener(e -> toggleSmartMode());

        bar.add(dragHandle); bar.add(titleLbl);
        bar.add(makeSep()); bar.add(barPinCountLabel);
        bar.add(makeSep()); bar.add(barCoordsLabel);
        bar.add(makeSep()); bar.add(addPinBtn); bar.add(doneBtn);

        int[] off={0,0};
        bar.addMouseListener(new MouseAdapter(){ public void mousePressed(MouseEvent e){ off[0]=e.getX();off[1]=e.getY(); }});
        bar.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){
                Point loc=smartBar.getLocationOnScreen();
                smartBar.setLocation(loc.x+e.getX()-off[0],loc.y+e.getY()-off[1]);
            }
        });

        smartBar.setContentPane(bar);
        smartBar.pack();
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        smartBar.setLocation(screen.width/2-smartBar.getWidth()/2,18);
        smartBar.setVisible(true);

        new Timer(50, e -> {
            if (!smartModeActive){ ((Timer)e.getSource()).stop(); return; }
            Point p=MouseInfo.getPointerInfo().getLocation();
            barCoordsLabel.setText("X: "+p.x+"  Y: "+p.y);
        }).start();
    }

    static JLabel makeSep(){ JLabel s=new JLabel("|"); s.setForeground(new Color(80,80,80)); return s; }
    static void hideSmartBar(){ if(smartBar!=null){ smartBar.dispose(); smartBar=null; } }
    static void updateBarPinCount(){ if(barPinCountLabel!=null) barPinCountLabel.setText("Pins: "+smartPins.size()); }

    // =========================================================
    //  PIN MANAGEMENT
    // =========================================================
    static void addSmartPin(int x, int y) {
        int row=tableModel.getRowCount();
        long interval=getIntervalMs();
        // Columns: #, X, Y, Clicks, Sub-Click Delay (ms), Delay-After (ms)
        tableModel.addRow(new Object[]{row+1, x, y, 1, interval, interval});
        SmartPin pin=new SmartPin(x,y,row);
        smartPins.add(pin);
        if (smartModeActive) pin.show();
        updateBarPinCount();
        refreshSmartIntervalStatus();
    }

    static void removePin(SmartPin pin) {
        int idx=smartPins.indexOf(pin);
        if (idx>=0) {
            pin.dispose(); smartPins.remove(idx);
            tableModel.removeRow(idx); refreshRowNumbers();
            for (int i=0;i<smartPins.size();i++) smartPins.get(i).rowIndex=i;
            updateBarPinCount(); refreshSmartIntervalStatus();
        }
    }

    static void refreshRowNumbers(){
        for (int i=0;i<tableModel.getRowCount();i++) tableModel.setValueAt(i+1,i,0);
    }

    // =========================================================
    //  CLICKING ENGINE
    // =========================================================
    static void startClicking() {
        if (running) return;
        running = true;
        clickCount = 0;
        updateStatus("● Running", new Color(40,160,80));

        // Snapshot table: {x, y, clicks, subDelay, delayAfter}
        List<int[]> points = new ArrayList<>();
        for (int i=0; i<tableModel.getRowCount(); i++) {
            try {
                int x        = Integer.parseInt(tableModel.getValueAt(i,1).toString());
                int y        = Integer.parseInt(tableModel.getValueAt(i,2).toString());
                int clicks   = Integer.parseInt(tableModel.getValueAt(i,3).toString());
                int subDelay = Integer.parseInt(tableModel.getValueAt(i,4).toString());
                int delay    = Integer.parseInt(tableModel.getValueAt(i,5).toString());
                points.add(new int[]{x,y,clicks,subDelay,delay});
            } catch (Exception ignored) {}
        }

        final long globalInterval = getIntervalMs();
        final long maxClicksSetting;
        try { maxClicksSetting = Long.parseLong(maxClicksField.getText().trim()); }
        catch (Exception e) { stopClicking(); return; }

        // HUD setup
        hudPointTotal    = points.size();
        hudLoopsLeft     = maxClicksSetting;
        hudSubClickIndex = 0;
        hudSubClickTotal = 1;
        hudSubDelayLeft  = 0;
        hudNextPointLeft = 0;

        // Hide main frame, show HUD
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            showHud();
        });

        new Thread(() -> {
            try {
                Robot robot    = new Robot();
                boolean isDouble = clickTypeCombo.getSelectedIndex()==1;
                int button     = getSelectedButton();
                long loopCount = 0;

                outer:
                while (running) {
                    loopCount++;
                    if (maxClicksSetting > 0 && loopCount > maxClicksSetting) { stopClicking(); break; }
                    hudLoopsLeft = maxClicksSetting <= 0 ? 0 : maxClicksSetting - loopCount;

                    if (!points.isEmpty()) {
                        for (int pi=0; pi<points.size(); pi++) {
                            if (!running) break outer;
                            int[] pt = points.get(pi);
                            int px=pt[0], py=pt[1], pClicks=pt[2], pSubDelay=pt[3], pDelay=pt[4];

                            hudPointIndex    = pi;
                            hudSubClickTotal = pClicks;

                            robot.mouseMove(px, py);
                            Thread.sleep(40);

                            for (int ci=0; ci<pClicks; ci++) {
                                if (!running) break outer;
                                hudSubClickIndex = ci;
                                doClick(robot, button, isDouble);
                                clickCount++;
                                final long c=clickCount;
                                SwingUtilities.invokeLater(() -> clickCountLabel.setText("Clicks: "+c));

                                if (ci < pClicks-1) {
                                    // countdown sub-click delay
                                    long end = System.currentTimeMillis() + pSubDelay;
                                    while (System.currentTimeMillis() < end && running) {
                                        hudSubDelayLeft = end - System.currentTimeMillis();
                                        Thread.sleep(Math.min(50, hudSubDelayLeft+1));
                                    }
                                    hudSubDelayLeft = 0;
                                }
                            }

                            // countdown delay-after
                            long end = System.currentTimeMillis() + pDelay;
                            while (System.currentTimeMillis() < end && running) {
                                hudNextPointLeft = end - System.currentTimeMillis();
                                Thread.sleep(Math.min(50, hudNextPointLeft+1));
                            }
                            hudNextPointLeft = 0;
                        }
                    } else {
                        hudPointIndex=0; hudPointTotal=0;
                        hudSubClickIndex=0; hudSubClickTotal=1;
                        doClick(robot, button, isDouble);
                        clickCount++;
                        final long c=clickCount;
                        SwingUtilities.invokeLater(() -> clickCountLabel.setText("Clicks: "+c));

                        long end = System.currentTimeMillis()+globalInterval;
                        while (System.currentTimeMillis()<end && running) {
                            hudNextPointLeft = end-System.currentTimeMillis();
                            Thread.sleep(Math.min(50, hudNextPointLeft+1));
                        }
                        hudNextPointLeft=0;

                        // in cursor mode, maxClicks = total individual clicks
                        if (maxClicksSetting>0 && clickCount>=maxClicksSetting) { stopClicking(); break; }
                    }
                }
            } catch (Exception ex) { ex.printStackTrace(); }
            stopClicking();
        }).start();
    }

    static void doClick(Robot r, int btn, boolean dbl) throws InterruptedException {
        r.mousePress(btn); r.mouseRelease(btn);
        if (dbl) { Thread.sleep(40); r.mousePress(btn); r.mouseRelease(btn); }
    }

    static void stopClicking() {
        running = false;
        SwingUtilities.invokeLater(() -> {
            updateStatus("● Stopped", new Color(200,50,50));
            hideHud();
            frame.setVisible(true);
            frame.toFront();
        });
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
    //  HOTKEY
    // =========================================================
    static void startListening(boolean forStartStop) {
        if (forStartStop) {
            listeningForHotkey=true;
            hotkeyLabel.setText("Start/Stop: [Press any key...]");
            changeHotkeyBtn.setEnabled(false);
        } else {
            listeningForSmartKey=true;
            smartKeyLabel.setText("Smart Click: [Press any key...]");
            changeSmartKeyBtn.setEnabled(false);
        }
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            if (forStartStop&&listeningForHotkey) {
                listeningForHotkey=false;
                SwingUtilities.invokeLater(() -> { hotkeyLabel.setText("Start/Stop: ["+hotkeyName+"]"); changeHotkeyBtn.setEnabled(true); });
            } else if (!forStartStop&&listeningForSmartKey) {
                listeningForSmartKey=false;
                SwingUtilities.invokeLater(() -> { smartKeyLabel.setText("Smart Click: ["+smartKeyName+"]"); changeSmartKeyBtn.setEnabled(true); });
            }
        }).start();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (listeningForHotkey) {
            hotkeyKeyCode=e.getKeyCode(); hotkeyName=NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForHotkey=false;
            SwingUtilities.invokeLater(() -> { hotkeyLabel.setText("Start/Stop: ["+hotkeyName+"]"); changeHotkeyBtn.setEnabled(true); });
            return;
        }
        if (listeningForSmartKey) {
            smartKeyCode=e.getKeyCode(); smartKeyName=NativeKeyEvent.getKeyText(e.getKeyCode());
            listeningForSmartKey=false;
            SwingUtilities.invokeLater(() -> { smartKeyLabel.setText("Smart Click: ["+smartKeyName+"]"); changeSmartKeyBtn.setEnabled(true); });
            return;
        }
        if (e.getKeyCode()==smartKeyCode)  { SwingUtilities.invokeLater(TurboClick::toggleSmartMode); return; }
        if (e.getKeyCode()==hotkeyKeyCode) { if (running) stopClicking(); else startClicking(); }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    // =========================================================
    //  UI HELPERS
    // =========================================================
    static JSpinner makeSpinner(int val, int min, int max) {
        JSpinner sp=new JSpinner(new SpinnerNumberModel(val,min,max,1));
        sp.setFont(new Font("SansSerif",Font.PLAIN,12));
        sp.setPreferredSize(new Dimension(55,28));
        ((JSpinner.DefaultEditor)sp.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
        return sp;
    }
    static JLabel makeLabel(String t){ JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.PLAIN,12)); return l; }
    static JTextField makeField(String v){
        JTextField f=new JTextField(v,8);
        f.setFont(new Font("SansSerif",Font.PLAIN,12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180,180,180)),
            BorderFactory.createEmptyBorder(3,6,3,6)));
        return f;
    }
    static JComboBox<String> makeCombo(String[] items){
        JComboBox<String> c=new JComboBox<>(items);
        c.setFont(new Font("SansSerif",Font.PLAIN,12)); c.setBackground(Color.WHITE); return c;
    }
    static JButton makeActionButton(String text, Color bg){
        JButton b=new JButton(text);
        b.setFont(new Font("SansSerif",Font.PLAIN,11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static TitledBorder makeTitledBorder(String t){
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200,200,200)),t,
            TitledBorder.LEFT,TitledBorder.TOP,
            new Font("SansSerif",Font.BOLD,11),new Color(80,80,80));
    }
}