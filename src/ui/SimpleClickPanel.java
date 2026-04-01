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
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

public class SimpleClickPanel extends JPanel implements NativeKeyListener {

    private static final Color BG           = new Color(22, 22, 28);
    private static final Color PANEL_BG     = new Color(28, 28, 38);
    private static final Color BORDER_COL   = new Color(50, 50, 65);
    private static final Color TEXT_MAIN    = new Color(220, 220, 230);
    private static final Color TEXT_DIM     = new Color(120, 120, 150);
    private static final Color INPUT_BG     = new Color(35, 35, 50);
    private static final Color INPUT_BORDER = new Color(60, 60, 85);
    private static final Color ACCENT       = new Color(80, 140, 255);

    private volatile boolean running    = false;
    private volatile long    clickCount = 0;
    private SimpleClickEngine engine;
    private Thread            engineThread;

    // Smart pin
    private boolean        smartModeActive = false;
    private List<SmartPin> smartPins       = new ArrayList<>();
    private JWindow        smartBar;
    private JWindow        dragLineOverlay;
    private JLabel         barCoordsLabel, barPinCountLabel;
    private Color          pinRingColor    = new Color(255, 210, 0);
    private int            currentPinType  = 0; // 0=left,1=right,2=middle — persists across pins

    // Hotkeys
    private int     startStopKey          = NativeKeyEvent.VC_F6;
    private String  startStopName         = "F6";
    private int     smartPinKey           = NativeKeyEvent.VC_F7;
    private String  smartPinName          = "F7";
    private boolean listeningForStartStop = false;
    private boolean listeningForSmartPin  = false;

    // UI refs
    private JLabel     statusLabel, clickCountLabel, smartIntervalLabel;
    private JLabel     startStopKeyLabel, smartPinKeyLabel;
    private JSpinner   spHours, spMinutes, spSeconds, spTenths, spHundredths, spThousandths;
    private JTextField maxClicksField;
    private JComboBox<String> mouseButtonCombo, clickTypeCombo;
    private JPanel     intervalSpinnersPanel;
    private DefaultTableModel tableModel;
    private JTable     pointsTable;
    private Window     parentWindow;

    public SimpleClickPanel() {
        setBackground(BG);
        setLayout(new BorderLayout());
        try { GlobalScreen.addNativeKeyListener(this); } catch (Exception ignored) {}

        JPanel titleBar = new JPanel();
        titleBar.setLayout(new BoxLayout(titleBar, BoxLayout.Y_AXIS));
        titleBar.setBackground(BG);
        titleBar.setBorder(new EmptyBorder(14,18,8,18));
        JLabel title = new JLabel("Build Simple Click");
        title.setFont(new Font("SansSerif",Font.BOLD,20)); title.setForeground(ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Fast & Simple Auto Clicker");
        subtitle.setFont(new Font("SansSerif",Font.PLAIN,11)); subtitle.setForeground(TEXT_DIM);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleBar.add(title); titleBar.add(Box.createVerticalStrut(2)); titleBar.add(subtitle);

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBackground(BG); leftCol.setBorder(new EmptyBorder(0,6,8,4));
        leftCol.add(buildIntervalPanel()); leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(buildStatusPanel());  leftCol.add(Box.createVerticalStrut(14));
        leftCol.add(buildButtons());      leftCol.add(Box.createVerticalGlue());

        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setBackground(BG); rightCol.setBorder(new EmptyBorder(0,4,8,6));
        rightCol.add(buildPointsTable()); rightCol.add(Box.createVerticalStrut(10));
        rightCol.add(buildHotkeyPanel()); rightCol.add(Box.createVerticalGlue());

        JPanel body = new JPanel(new GridLayout(1,2,8,0));
        body.setBackground(BG); body.add(leftCol); body.add(rightCol);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(titleBar, BorderLayout.NORTH);
        root.add(body,     BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null); scroll.getViewport().setBackground(BG); scroll.setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        addHierarchyListener(e -> { if (parentWindow==null) parentWindow=SwingUtilities.getWindowAncestor(this); });
    }

    // ── Interval panel ────────────────────────────────────────
    private JPanel buildIntervalPanel() {
        JPanel p = darkPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(darkTitledBorder("Click Interval"));

        String[] unitLabels = {"Hours","Minutes","Seconds","1/10 s","1/100 s","1/1000 s"};
        JPanel headerRow = new JPanel(new GridLayout(1,6,2,0));
        headerRow.setBackground(PANEL_BG);
        for (String u : unitLabels) {
            JLabel lbl = new JLabel(u, SwingConstants.CENTER);
            lbl.setFont(new Font("SansSerif",Font.PLAIN,10)); lbl.setForeground(TEXT_DIM);
            headerRow.add(lbl);
        }
        intervalSpinnersPanel = new JPanel(new GridLayout(1,6,2,0));
        intervalSpinnersPanel.setBackground(PANEL_BG);
        spHours=dsp(0,0,23); spMinutes=dsp(0,0,59); spSeconds=dsp(0,0,59);
        spTenths=dsp(1,0,9); spHundredths=dsp(0,0,9); spThousandths=dsp(0,0,9);
        for (JSpinner s : new JSpinner[]{spHours,spMinutes,spSeconds,spTenths,spHundredths,spThousandths})
            intervalSpinnersPanel.add(s);

        JPanel spinnerWrapper = new JPanel(new BorderLayout(0,2)) {
            public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 48); }
        };
        spinnerWrapper.setBackground(PANEL_BG); spinnerWrapper.setBorder(new EmptyBorder(4,4,6,4));
        headerRow.setPreferredSize(new Dimension(0,16));
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,16));
        intervalSpinnersPanel.setPreferredSize(new Dimension(0,26));
        intervalSpinnersPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));
        spinnerWrapper.add(headerRow, BorderLayout.NORTH);
        spinnerWrapper.add(intervalSpinnersPanel, BorderLayout.CENTER);
        spinnerWrapper.setPreferredSize(new Dimension(0,48));

        p.add(spinnerWrapper); p.add(darkSeparator());

        JPanel grid = new JPanel(new GridLayout(3,2,8,8));
        grid.setBackground(PANEL_BG); grid.setBorder(new EmptyBorder(8,8,8,8));
        grid.add(dlbl("Mouse Button:")); mouseButtonCombo=dcombo(new String[]{"Left","Right","Middle"}); grid.add(mouseButtonCombo);
        grid.add(dlbl("Click Type:"));  clickTypeCombo=dcombo(new String[]{"Single","Double"});          grid.add(clickTypeCombo);
        grid.add(dlbl("Max Clicks (0=∞):")); maxClicksField=dfield("0");                                 grid.add(maxClicksField);
        p.add(grid);

        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEFT,8,4)); hint.setBackground(PANEL_BG);
        smartIntervalLabel = new JLabel("Smart Interval off — set Clicks > 1 in any row to activate");
        smartIntervalLabel.setFont(new Font("SansSerif",Font.ITALIC,10)); smartIntervalLabel.setForeground(TEXT_DIM);
        hint.add(smartIntervalLabel); p.add(hint);
        return p;
    }

    // ── Points table ──────────────────────────────────────────
    private JPanel buildPointsTable() {
        JPanel p = darkPanel();
        p.setLayout(new BorderLayout(6,6));
        p.setBorder(darkTitledBorder("Click Points  (empty = click at cursor)"));

        // Columns: # X Y Clicks Sub-delay After-delay Type
        tableModel = new DefaultTableModel(
            new String[]{"#","X","Y","Clicks","Sub-delay","After-delay","Type"}, 0) {
            public boolean isCellEditable(int r, int c) { return c==1||c==2||c==3; }
            public Object getValueAt(int r, int c) {
                Object v = super.getValueAt(r,c);
                if ((c==4||c==5) && v instanceof Number)
                    return String.format("%.3f s", ((Number)v).longValue()/1000.0);
                // c==6: return raw int — renderer handles display
                return v;
            }
        };

        pointsTable = new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif",Font.PLAIN,12)); pointsTable.setRowHeight(24);
        pointsTable.setBackground(new Color(32,32,44)); pointsTable.setForeground(TEXT_MAIN);
        pointsTable.setGridColor(new Color(45,45,60));
        pointsTable.setSelectionBackground(new Color(50,80,140)); pointsTable.setSelectionForeground(Color.WHITE);
        pointsTable.getTableHeader().setBackground(new Color(35,35,50));
        pointsTable.getTableHeader().setForeground(TEXT_DIM);
        pointsTable.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,10));
        int[] cw = {22,50,50,42,68,72,38};
        for (int i=0;i<cw.length;i++) pointsTable.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);

        // Delay columns renderer
        TableCellRenderer delayRenderer = new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                JLabel lbl = (JLabel)super.getTableCellRendererComponent(t,val,sel,foc,r,c);
                lbl.setBackground(sel ? new Color(50,80,140) : new Color(32,32,44));
                lbl.setForeground(new Color(160,200,255));
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return lbl;
            }
        };
        // Full-row renderer: highlight drag pair rows with orange tint
        TableCellRenderer rowRenderer = new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                JLabel lbl = (JLabel)super.getTableCellRendererComponent(t,val,sel,foc,r,c);
                int typeInt=0;
                try { typeInt=((Number)t.getModel().getValueAt(r,6)).intValue(); } catch(Exception ignored){}
                if (typeInt==3) {
                    lbl.setBackground(sel ? new Color(80,60,20) : new Color(42,35,22));
                } else {
                    lbl.setBackground(sel ? new Color(50,80,140) : new Color(32,32,44));
                }
                lbl.setForeground(TEXT_MAIN);
                return lbl;
            }
        };
        for (int i=0;i<6;i++) pointsTable.getColumnModel().getColumn(i).setCellRenderer(rowRenderer);

        // # column: show drag indicators with numbers
        pointsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                int typeInt=0;
                try { typeInt=((Number)t.getModel().getValueAt(r,6)).intValue(); } catch(Exception ignored){}
                String display = val!=null?val.toString():"";
                if (typeInt==3) {
                    boolean prevIsDrag=false;
                    if (r>0) try{ prevIsDrag=((Number)t.getModel().getValueAt(r-1,6)).intValue()==3; }catch(Exception ignored){}
                    display = prevIsDrag ? "\u21b3"+display : "\u25c6"+display;
                }
                JLabel lbl=(JLabel)super.getTableCellRendererComponent(t,display,sel,foc,r,c);
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                lbl.setFont(new Font("SansSerif",Font.BOLD,10));
                lbl.setBackground(sel ? new Color(80,60,20) : (typeInt==3 ? new Color(42,35,22) : new Color(32,32,44)));
                lbl.setForeground(typeInt==3 ? new Color(255,140,40) : TEXT_DIM);
                return lbl;
            }
        });

        // Delay columns still need their own renderer on top
        pointsTable.getColumnModel().getColumn(4).setCellRenderer(delayRenderer);
        pointsTable.getColumnModel().getColumn(5).setCellRenderer(delayRenderer);

        // Type column renderer — raw int → L/R/M
        pointsTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                int typeInt = 0;
                try { typeInt = ((Number)val).intValue(); } catch(Exception ignored){}
                String display; Color fc;
                switch(typeInt){
                    case 1: display="R"; fc=new Color(220,80,80); break;
                    case 2: display="M"; fc=new Color(80,200,120); break;
                    case 3: display="D"; fc=new Color(255,140,40); break;
                    default: display="L"; fc=new Color(80,140,255); break;
                }
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t,display,sel,foc,r,c);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(new Font("SansSerif",Font.BOLD,11));
                lbl.setBackground(sel ? new Color(50,80,140) : new Color(32,32,44));
                lbl.setForeground(fc);
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return lbl;
            }
        });

        // Click on delay cell → interval picker; click on Type → cycle
        pointsTable.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                int col=pointsTable.columnAtPoint(e.getPoint());
                int row=pointsTable.rowAtPoint(e.getPoint());
                if (row<0) return;
                if (col==4||col==5) {
                    Object cur = tableModel.getValueAt(row,col);
                    long currentMs=100;
                    try {
                        if (cur instanceof Long) currentMs=(Long)cur;
                        else if (cur instanceof Number) currentMs=((Number)cur).longValue();
                        else { String s=cur.toString().replace(" s","").trim(); currentMs=(long)(Double.parseDouble(s)*1000); }
                    } catch(Exception ignored){}
                    String colName = col==4 ? "Sub-click delay" : "Delay after click";
                    long result = showIntervalPicker(colName, currentMs);
                    if (result>=0) { tableModel.setValueAt(result,row,col); }
                } else if (col==6) {
                    Object raw = tableModel.getValueAt(row, 6);
                    int oldType = 0;
                    try { oldType = ((Number)raw).intValue(); } catch(Exception ignored){}
                    tableModel.setValueAt((oldType+1)%3, row, 6);
                }
            }
        });

        tableModel.addTableModelListener(e -> {
            int col=e.getColumn(), row=e.getFirstRow();
            if (col==3||col==TableModelEvent.ALL_COLUMNS) SwingUtilities.invokeLater(this::refreshSmartInterval);
            if (row>=0&&row<smartPins.size()) {
                SmartPin pin=smartPins.get(row);
                try {
                    if (col==1) { pin.screenX=Integer.parseInt(tableModel.getValueAt(row,1).toString()); pin.win.setLocation(pin.screenX-pin.win.getWidth()/2, pin.screenY-pin.win.getHeight()/2); }
                    if (col==2) { pin.screenY=Integer.parseInt(tableModel.getValueAt(row,2).toString()); pin.win.setLocation(pin.screenX-pin.win.getWidth()/2, pin.screenY-pin.win.getHeight()/2); }
                } catch(Exception ignored){}
            }
        });

        JScrollPane scroll = new JScrollPane(pointsTable);
        scroll.setPreferredSize(new Dimension(300,130));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        scroll.getViewport().setBackground(new Color(32,32,44));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar vsb=scroll.getVerticalScrollBar();
        vsb.setPreferredSize(new Dimension(5,0)); vsb.setOpaque(false);
        vsb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            protected void configureScrollBarColors(){ thumbColor=new Color(100,100,160,150); trackColor=new Color(0,0,0,0); }
            protected JButton createDecreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
            protected JButton createIncreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT,6,6));
        btns.setBackground(PANEL_BG);
        JButton smartPinBtn = dactionBtn("\u2295 Smart Pin", ACCENT);
        JButton addBtn      = dactionBtn("+ Add",       ACCENT);
        JButton remBtn      = dactionBtn("- Remove",    new Color(220,70,70));
        JButton clrBtn      = dactionBtn("Clear All",   new Color(120,120,150));

        smartPinBtn.addActionListener(e -> toggleSmartMode());
        addBtn.addActionListener(e -> {
            Dimension scr=Toolkit.getDefaultToolkit().getScreenSize();
            int row=tableModel.getRowCount();
            tableModel.addRow(new Object[]{row+1, scr.width/2, scr.height/2, 1, 100L, 100L, 0});
            refreshSmartInterval();
        });
        final boolean[] suppressDragWarning = {false};
        remBtn.addActionListener(e -> {
            int row=pointsTable.getSelectedRow();
            if (row<0) return;
            int typeInt=0;
            try { typeInt=((Number)tableModel.getValueAt(row,6)).intValue(); } catch(Exception ignored){}
            if (typeInt==3) {
                if (!suppressDragWarning[0]) {
                    JCheckBox dontShow = new JCheckBox("Don't show this again");
                    dontShow.setBackground(new Color(28,28,38)); dontShow.setForeground(new Color(180,180,200));
                    Object[] msg = {"This will delete BOTH points of the drag pair.", dontShow};
                    int result = JOptionPane.showConfirmDialog(SimpleClickPanel.this, msg,
                        "Delete drag pair?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (result != JOptionPane.OK_OPTION) return;
                    if (dontShow.isSelected()) suppressDragWarning[0]=true;
                }
                // Find which drag row it is (start or end) then remove both
                boolean prevIsDrag = row>0 && isDragRow(row-1);
                int startRow = prevIsDrag ? row-1 : row;
                // Remove the DragPin from smartPins
                for (int i=smartPins.size()-1;i>=0;i--) {
                    if (smartPins.get(i) instanceof DragPin && smartPins.get(i).rowIndex==startRow) {
                        smartPins.get(i).dispose(); smartPins.remove(i); break;
                    }
                }
                // Remove both table rows (remove higher index first)
                if (startRow+1 < tableModel.getRowCount()) tableModel.removeRow(startRow+1);
                if (startRow < tableModel.getRowCount()) tableModel.removeRow(startRow);
            } else {
                if (row<smartPins.size()) { smartPins.get(row).dispose(); smartPins.remove(row); }
                tableModel.removeRow(row);
            }
            refreshRowNumbers();
            int r=0; for (SmartPin sp:smartPins){ sp.rowIndex=r; r+=sp.rowSpan(); }
            refreshSmartInterval(); refreshLineOverlay();
        });
        clrBtn.addActionListener(e -> {
            for (SmartPin pin:smartPins) pin.dispose(); smartPins.clear();
            tableModel.setRowCount(0); refreshSmartInterval();
        });

        btns.add(smartPinBtn); btns.add(addBtn); btns.add(remBtn); btns.add(clrBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(PANEL_BG); top.add(scroll, BorderLayout.CENTER);
        p.add(top, BorderLayout.CENTER); p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    // Raw type helpers — bypass the display override in getValueAt
    private String getRawType(int row) {
        try {
            java.lang.reflect.Field f = tableModel.getClass().getSuperclass().getDeclaredField("dataVector");
            f.setAccessible(true);
            java.util.Vector data = (java.util.Vector)f.get(tableModel);
            java.util.Vector rowVec = (java.util.Vector)data.get(row);
            Object v = rowVec.get(6);
            if (v instanceof Number) return String.valueOf(((Number)v).intValue());
        } catch(Exception ignored){}
        return "0";
    }

    private void setRawType(int row, int type) {
        try {
            java.lang.reflect.Field f = tableModel.getClass().getSuperclass().getDeclaredField("dataVector");
            f.setAccessible(true);
            java.util.Vector data = (java.util.Vector)f.get(tableModel);
            java.util.Vector rowVec = (java.util.Vector)data.get(row);
            rowVec.set(6, type);
        } catch(Exception ignored){}
    }

    // ── Hotkey panel ──────────────────────────────────────────
    private JPanel buildHotkeyPanel() {
        JPanel p = darkPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(darkTitledBorder("Hotkeys"));
        JPanel grid = new JPanel(new GridLayout(2,3,8,6));
        grid.setBackground(PANEL_BG); grid.setBorder(new EmptyBorder(6,8,8,8));
        startStopKeyLabel = new JLabel("Start/Stop: ["+startStopName+"]");
        startStopKeyLabel.setForeground(TEXT_MAIN); startStopKeyLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        smartPinKeyLabel = new JLabel("Smart Pin: ["+smartPinName+"]");
        smartPinKeyLabel.setForeground(TEXT_MAIN); smartPinKeyLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        JButton changeSSBtn  = dactionBtn("Change",            new Color(120,120,180));
        JButton changeSPBtn  = dactionBtn("Change",            new Color(120,120,180));
        JButton smartPinBtn2 = dactionBtn("\u2295 Enter Smart Pin", ACCENT);
        changeSSBtn.addActionListener(e  -> startListeningForKey(true));
        changeSPBtn.addActionListener(e  -> startListeningForKey(false));
        smartPinBtn2.addActionListener(e -> toggleSmartMode());
        grid.add(startStopKeyLabel); grid.add(changeSSBtn); grid.add(new JLabel());
        grid.add(smartPinKeyLabel);  grid.add(changeSPBtn);  grid.add(smartPinBtn2);
        for (Component c:grid.getComponents()) if (c instanceof JLabel) c.setBackground(PANEL_BG);
        p.add(grid); return p;
    }

    // ── Status + buttons ──────────────────────────────────────
    private JPanel buildStatusPanel() {
        JPanel p = darkPanel(); p.setLayout(new GridLayout(2,1,4,4)); p.setBorder(darkTitledBorder("Status"));
        statusLabel=new JLabel("\u25cf Idle"); statusLabel.setFont(new Font("SansSerif",Font.BOLD,13)); statusLabel.setForeground(TEXT_DIM); statusLabel.setBorder(new EmptyBorder(4,10,0,0));
        clickCountLabel=new JLabel("Clicks: 0"); clickCountLabel.setFont(new Font("SansSerif",Font.PLAIN,12)); clickCountLabel.setForeground(TEXT_DIM); clickCountLabel.setBorder(new EmptyBorder(0,10,4,0));
        p.add(statusLabel); p.add(clickCountLabel); return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER,10,0)); p.setBackground(BG); p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton startBtn=bigBtn("\u25b6  Start",new Color(40,200,80),new Color(40,160,80));
        JButton stopBtn =bigBtn("\u25a0  Stop", new Color(220,70,70), new Color(180,50,50));
        startBtn.addActionListener(e->startClicking()); stopBtn.addActionListener(e->stopClicking());
        p.add(startBtn); p.add(stopBtn); return p;
    }

    // ── Mouse widget — painted mouse shape ────────────────────
    /** Paints a simple mouse icon. active: 0=left,1=right,2=middle */
    private void paintMouseWidget(Graphics2D g2in, int x, int y, int w, int h, int active) {
        Graphics2D g2 = (Graphics2D) g2in.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color colLeft   = (active==0) ? new Color(80,140,255) : new Color(55,55,75);
        Color colRight  = (active==1) ? new Color(220,80,80)  : new Color(55,55,75);
        Color colMiddle = (active==2) ? new Color(80,200,120) : new Color(55,55,75);
        Color colDrag   = (active==3) ? new Color(255,140,40) : new Color(55,55,75);
        int bx=x+4, by=y+4, bw=w-8, bh=h-8, mx=x+w/2;
        int topH = bh/2;
        // Body
        g2.setColor(new Color(30,30,45)); g2.fillRoundRect(bx,by,bw,bh,14,14);
        // Left button
        g2.setClip(bx,by,bw/2,topH+2); g2.setColor(colLeft); g2.fillRoundRect(bx,by,bw,bh,14,14);
        // Right button
        g2.setClip(mx,by,bw/2+2,topH+2); g2.setColor(colRight); g2.fillRoundRect(bx,by,bw,bh,14,14);
        // Drag zone bottom
        g2.setClip(bx,by+topH,bw,bh-topH+2); g2.setColor(colDrag); g2.fillRoundRect(bx,by,bw,bh,14,14);
        g2.setClip(null);
        // Dividers
        g2.setColor(new Color(15,15,25)); g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(bx+2,by+topH,bx+bw-2,by+topH);
        g2.drawLine(mx,by,mx,by+topH);
        // Scroll wheel — bigger
        int swW=8, swH=16, swX=mx-swW/2, swY=by+4;
        g2.setColor(colMiddle); g2.fillRoundRect(swX,swY,swW,swH,5,5);
        g2.setColor(new Color(15,15,25)); g2.setStroke(new BasicStroke(0.8f));
        g2.drawRoundRect(swX,swY,swW,swH,5,5);
        // Drag label
        g2.setFont(new Font("SansSerif",Font.BOLD,8));
        g2.setColor(active==3 ? Color.WHITE : new Color(90,90,110));
        String dragTxt = "\u2194";
        FontMetrics fm=g2.getFontMetrics();
        g2.drawString(dragTxt, mx-fm.stringWidth(dragTxt)/2, by+topH+(bh-topH)/2+fm.getAscent()/2-1);
        // Outline
        g2.setColor(new Color(100,100,130)); g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(bx,by,bw,bh,14,14);
        g2.dispose();
    }

    // ── Smart pin mode ────────────────────────────────────────
    private void toggleSmartMode() {
        smartModeActive=!smartModeActive;
        if (smartModeActive) {
            if (parentWindow==null) parentWindow=SwingUtilities.getWindowAncestor(this);
            if (parentWindow!=null) parentWindow.setVisible(false);
            showDragLineOverlay();
            showSmartBar(); for (SmartPin p:smartPins) p.show();
        } else {
            hideDragLineOverlay();
            hideSmartBar(); for (SmartPin p:smartPins) p.hide();
            if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
        }
    }

    private void showDragLineOverlay() {
        if (dragLineOverlay!=null) { dragLineOverlay.dispose(); }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dragLineOverlay = new JWindow();
        dragLineOverlay.setType(Window.Type.POPUP);   // POPUP type doesn't intercept mouse on macOS
        dragLineOverlay.setAlwaysOnTop(false);
        dragLineOverlay.setBackground(new Color(0,0,0,0));
        dragLineOverlay.setFocusableWindowState(false);
        dragLineOverlay.setEnabled(false);  // prevents ALL mouse interception on macOS
        dragLineOverlay.setBounds(0,0,screen.width,screen.height);

        JPanel linePanel = new JPanel() {
            { setOpaque(false); }
            // Return false so ALL mouse events pass through to windows beneath
            public boolean contains(int x, int y) { return false; }
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)((Graphics2D)g).create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (SmartPin pin : smartPins) {
                    if (pin instanceof DragPin) {
                        DragPin dp = (DragPin)pin;
                        if (dp.startWin==null || dp.endWin==null) continue;
                        // Read directly from window positions — always accurate during drag
                        int sx=dp.startWin.getX()+dp.HSZ/2, sy=dp.startWin.getY()+dp.HSZ/2;
                        int ex=dp.endWin.getX()+dp.HSZ/2,   ey=dp.endWin.getY()+dp.HSZ/2;
                        // Dashed orange line
                        float[] dash={10f,5f};
                        g2.setColor(new Color(255,140,40));
                        g2.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,dash,0f));
                        g2.drawLine(sx,sy,ex,ey);
                        // Arrowhead at end
                        g2.setStroke(new BasicStroke(2f));
                        g2.setColor(new Color(255,140,40));
                        double angle=Math.atan2(ey-sy,ex-sx); int sz=12;
                        int ax1=(int)(ex-sz*Math.cos(angle-0.4)),ay1=(int)(ey-sz*Math.sin(angle-0.4));
                        int ax2=(int)(ex-sz*Math.cos(angle+0.4)),ay2=(int)(ey-sz*Math.sin(angle+0.4));
                        g2.fillPolygon(new int[]{ex,ax1,ax2},new int[]{ey,ay1,ay2},3);
                    }
                }
                g2.dispose();
            }
        };
        dragLineOverlay.setContentPane(linePanel);
        dragLineOverlay.setVisible(true);
    }

    private void refreshLineOverlay() {
        if (dragLineOverlay!=null) dragLineOverlay.repaint();
    }

    private void hideDragLineOverlay() {
        if (dragLineOverlay!=null) { dragLineOverlay.dispose(); dragLineOverlay=null; }
    }

    private void showSmartBar() {
        smartBar=new JWindow(); smartBar.setAlwaysOnTop(true); smartBar.setBackground(new Color(0,0,0,0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,15,22,240)); g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(new Color(80,140,255,200)); g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        bar.setOpaque(false); bar.setLayout(new FlowLayout(FlowLayout.CENTER,8,6));

        JLabel titleLbl=new JLabel("Smart Pin"); titleLbl.setFont(new Font("SansSerif",Font.BOLD,12)); titleLbl.setForeground(ACCENT);
        barPinCountLabel=new JLabel("Pins: "+smartPins.size()); barPinCountLabel.setFont(new Font("SansSerif",Font.PLAIN,11)); barPinCountLabel.setForeground(TEXT_DIM);
        barCoordsLabel=new JLabel("X: \u2500\u2500\u2500 Y: \u2500\u2500\u2500"); 
        barCoordsLabel.setFont(new Font("Monospaced",Font.BOLD,12)); 
        barCoordsLabel.setForeground(new Color(80,220,120));
        barCoordsLabel.setPreferredSize(new Dimension(130, 18));
        barCoordsLabel.setHorizontalAlignment(SwingConstants.LEFT);

        // ── Mouse widget panel ────────────────────────────────
        int mw=56, mh=48;
        JPanel mouseWidget = new JPanel(null) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintMouseWidget((Graphics2D)g, 0, 0, getWidth(), getHeight(), currentPinType);
            }
        };
        mouseWidget.setPreferredSize(new Dimension(mw, mh));
        mouseWidget.setOpaque(false);
        mouseWidget.setToolTipText("Click to select click type");
        mouseWidget.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel typeNameLbl = new JLabel(typeName(currentPinType));
        typeNameLbl.setFont(new Font("SansSerif",Font.BOLD,10));
        typeNameLbl.setForeground(typeColor(currentPinType));
        typeNameLbl.setPreferredSize(new Dimension(60, 18));
        typeNameLbl.setHorizontalAlignment(SwingConstants.LEFT);

        mouseWidget.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e) {
                int mx = mw/2;
                int topH = (mh-8)/2 + 4;
                if (e.getX()>=mx-6 && e.getX()<=mx+6 && e.getY()>=4 && e.getY()<=topH-2) {
                    currentPinType = 2; // middle
                } else if (e.getY() >= topH) {
                    currentPinType = 3; // drag
                } else if (e.getX() < mx) {
                    currentPinType = 0; // left
                } else {
                    currentPinType = 1; // right
                }
                mouseWidget.repaint();
                typeNameLbl.setText(typeName(currentPinType));
                typeNameLbl.setForeground(typeColor(currentPinType));
            }
        });

        JButton addPinBtn=dactionBtn("+ Pin",ACCENT);
        addPinBtn.addActionListener(e -> {
            Dimension scr=Toolkit.getDefaultToolkit().getScreenSize();
            addSmartPin(scr.width/2, scr.height/2, currentPinType);
        });
        JButton doneBtn=dactionBtn("\u2713 Done",new Color(120,120,150)); doneBtn.addActionListener(e->toggleSmartMode());

        bar.add(titleLbl); bar.add(sep()); bar.add(barPinCountLabel); bar.add(sep());
        bar.add(barCoordsLabel); bar.add(sep());
        bar.add(mouseWidget); bar.add(typeNameLbl);
        bar.add(sep()); bar.add(addPinBtn); bar.add(doneBtn);

        int[] off={0,0};
        bar.addMouseListener(new MouseAdapter(){ public void mousePressed(MouseEvent e){off[0]=e.getX();off[1]=e.getY();}});
        bar.addMouseMotionListener(new MouseMotionAdapter(){ public void mouseDragged(MouseEvent e){ Point loc=smartBar.getLocationOnScreen(); smartBar.setLocation(loc.x+e.getX()-off[0],loc.y+e.getY()-off[1]); }});
        smartBar.setContentPane(bar); smartBar.pack();
        smartBar.setSize(smartBar.getWidth() + 70, smartBar.getHeight());
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize(); smartBar.setLocation(screen.width/2-smartBar.getWidth()/2,18); smartBar.setVisible(true);

        // Coords timer
        new Timer(50,e->{ if(!smartModeActive){((Timer)e.getSource()).stop();return;} Point p=MouseInfo.getPointerInfo().getLocation(); barCoordsLabel.setText("X: "+p.x+"  Y: "+p.y); }).start();
    }

    private String typeName(int t) {
        switch(t){ case 1: return "Right"; case 2: return "Middle"; case 3: return "Drag"; default: return "Left"; }
    }
    private Color typeColor(int t) {
        switch(t){ case 1: return new Color(220,80,80); case 2: return new Color(80,200,120); case 3: return new Color(255,140,40); default: return new Color(80,140,255); }
    }

    private boolean isDragRow(int row) {
        if (row<0||row>=tableModel.getRowCount()) return false;
        try { return ((Number)tableModel.getValueAt(row,6)).intValue()==3; } catch(Exception e){ return false; }
    }

    private void hideSmartBar() { if(smartBar!=null){smartBar.dispose();smartBar=null;} }

    private void addSmartPin(int x, int y, int btnType) {
        if (btnType == 3) {
            addDragPin(x, y, x+120, y+80); // default drag: start + end offset
        } else {
            int row=tableModel.getRowCount(); long iv=getIntervalMs();
            tableModel.addRow(new Object[]{row+1, x, y, 1, iv, iv, btnType});
            SmartPin pin=new SmartPin(x, y, row, btnType); smartPins.add(pin);
            if (smartModeActive) { SwingUtilities.invokeLater(()->{ pin.show(); pin.win.toFront(); }); }
            if (barPinCountLabel!=null) barPinCountLabel.setText("Pins: "+smartPins.size());
            refreshSmartInterval();
        }
    }

    private void addDragPin(int sx, int sy, int ex, int ey) {
        int row = tableModel.getRowCount(); long iv = getIntervalMs();
        tableModel.addRow(new Object[]{row+1, sx, sy, 1, iv, iv, 3}); // start
        tableModel.addRow(new Object[]{row+2, ex, ey, 1, iv, iv, 3}); // end
        DragPin dp = new DragPin(sx, sy, ex, ey, row);
        smartPins.add(dp);
        if (smartModeActive) { SwingUtilities.invokeLater(()->{ dp.show(); dp.startWin.toFront(); }); }
        if (barPinCountLabel!=null) barPinCountLabel.setText("Pins: "+smartPins.size());
        refreshSmartInterval();
    }

    private JLabel sep(){ JLabel s=new JLabel("|"); s.setForeground(new Color(60,60,80)); return s; }

    // ── Smart pin widget ──────────────────────────────────────
    class SmartPin {
        JWindow win;
        int screenX, screenY, rowIndex, btnType;
        static final int PSZ = 44;

        SmartPin(int x, int y, int row, int btnType) {
            screenX=x; screenY=y; rowIndex=row; this.btnType=btnType; build();
        }

        /** Constructor that skips build() — used by subclasses that manage their own window */
        SmartPin(int x, int y, int row, int btnType, boolean skipBuild) {
            screenX=x; screenY=y; rowIndex=row; this.btnType=btnType;
            if (!skipBuild) build();
        }

        /** How many table rows this pin occupies */
        int rowSpan() { return 1; }

        void build() {
            win=new JWindow(); win.setAlwaysOnTop(true); win.setBackground(new Color(0,0,0,1));
            JPanel panel = new JPanel(null) {
                boolean hovered=false;
                {
                    setBackground(new Color(0,0,0,1));
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    int[] off={0,0};
                    addMouseListener(new MouseAdapter(){
                        public void mouseEntered(MouseEvent e){hovered=true;repaint();}
                        public void mouseExited(MouseEvent e) {hovered=false;repaint();}
                        public void mousePressed(MouseEvent e){off[0]=e.getX();off[1]=e.getY();}
                        public void mouseReleased(MouseEvent e){
                            if (rowIndex<tableModel.getRowCount()) {
                                tableModel.setValueAt(screenX,rowIndex,1);
                                tableModel.setValueAt(screenY,rowIndex,2);
                            }
                        }
                        public void mouseClicked(MouseEvent e){
                            if (e.getButton()==MouseEvent.BUTTON3) { removePin(SmartPin.this); }
                        }
                    });
                    addMouseMotionListener(new MouseMotionAdapter(){
                        public void mouseDragged(MouseEvent e){
                            Point loc=win.getLocationOnScreen();
                            int nx=loc.x+e.getX()-off[0], ny=loc.y+e.getY()-off[1];
                            win.setLocation(nx,ny);
                            screenX=nx+PSZ/2; screenY=ny+PSZ/2;
                            repaint();
                        }
                    });
                }
                protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx=getWidth()/2, cy=getHeight()/2;
                    // Ring color by type: left=blue, right=red, middle=green
                    Color rc;
                    switch(btnType){
                        case 1: rc=new Color(220,80,80); break;
                        case 2: rc=new Color(80,200,120); break;
                        default: rc=new Color(80,140,255); break;
                    }
                    int ringR=10;
                    g2.setColor(rc); g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx-ringR,cy-ringR,ringR*2,ringR*2);
                    g2.setColor(new Color(255,255,255,230)); g2.setStroke(new BasicStroke(1.5f));
                    int arm=8,gap=4;
                    g2.drawLine(cx-arm,cy,cx-gap,cy); g2.drawLine(cx+gap,cy,cx+arm,cy);
                    g2.drawLine(cx,cy-arm,cx,cy-gap); g2.drawLine(cx,cy+gap,cx,cy+arm);
                    g2.setColor(new Color(255,50,50)); g2.fillOval(cx-3,cy-3,6,6);
                    if (!hovered) {
                        String lbl=String.valueOf(rowIndex+1);
                        g2.setFont(new Font("SansSerif",Font.BOLD,9));
                        FontMetrics fm=g2.getFontMetrics(); int lw=fm.stringWidth(lbl);
                        g2.setColor(new Color(0,0,0,160)); g2.fillOval(cx-6,cy-6,12,12);
                        g2.setColor(Color.WHITE); g2.drawString(lbl,cx-lw/2,cy+4);
                    }
                }
            };
            panel.setPreferredSize(new Dimension(PSZ,PSZ));
            win.setContentPane(panel); win.setSize(PSZ,PSZ);
            win.setLocation(screenX-PSZ/2, screenY-PSZ/2);
        }
        void show()    { win.setVisible(true); }
        void hide()    { win.setVisible(false); }
        void dispose() { win.dispose(); }
    }

    // ── Drag pin — red arrow line between two points ──────────
    // ── Drag pin — red arrow line between two draggable handles ─
    // ── Drag pin — two handle pins + line drawn on shared overlay ──
    class DragPin extends SmartPin {
        int endX, endY;
        JWindow startWin, endWin, midWin;
        static final int HSZ = 44;
        static final int MSZ = 20; // midpoint handle size

        DragPin(int sx, int sy, int ex, int ey, int row) {
            super(sx, sy, row, 3, true);
            endX=ex; endY=ey;
            buildHandles();
        }

        @Override int rowSpan() { return 2; }

        void buildHandles() {
            startWin = buildHandle(true);
            endWin   = buildHandle(false);
            buildMidHandle();
        }

        void buildMidHandle() {
            midWin = new JWindow();
            midWin.setAlwaysOnTop(true);
            midWin.setFocusableWindowState(false);
            midWin.setBackground(new Color(0,0,0,1));
            JPanel mp = new JPanel(null) {
                { setBackground(new Color(0,0,0,1)); }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx=getWidth()/2, cy=getHeight()/2;
                    // Small orange diamond — indicates draggable line
                    g2.setColor(new Color(255,140,40,180));
                    g2.setStroke(new BasicStroke(1.5f));
                    int[] xs={cx,cx+7,cx,cx-7}, ys={cy-7,cy,cy+7,cy};
                    g2.fillPolygon(xs,ys,4);
                    g2.setColor(new Color(255,200,100));
                    g2.drawPolygon(xs,ys,4);
                }
            };
            int[] lastScreen={0,0};
            mp.addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    Point sc=MouseInfo.getPointerInfo().getLocation();
                    lastScreen[0]=sc.x; lastScreen[1]=sc.y;
                }
                public void mouseReleased(MouseEvent e){ syncToTable(); refreshLineOverlay(); }
                public void mouseClicked(MouseEvent e){ if(e.getButton()==MouseEvent.BUTTON3) removePin(DragPin.this); }
            });
            mp.addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    Point sc=MouseInfo.getPointerInfo().getLocation();
                    int dx=sc.x-lastScreen[0], dy=sc.y-lastScreen[1];
                    lastScreen[0]=sc.x; lastScreen[1]=sc.y;
                    screenX+=dx; screenY+=dy; endX+=dx; endY+=dy;
                    placeHandle(startWin,true); placeHandle(endWin,false); placeMidHandle();
                    refreshLineOverlay();
                }
            });
            mp.setPreferredSize(new Dimension(MSZ,MSZ));
            midWin.setContentPane(mp); midWin.setSize(MSZ,MSZ);
            placeMidHandle();
        }

        void placeMidHandle() {
            int mx=(screenX+endX)/2, my=(screenY+endY)/2;
            midWin.setLocation(mx-MSZ/2, my-MSZ/2);
        }

        JWindow buildHandle(boolean isStart) {
            JWindow hw = new JWindow();
            hw.setAlwaysOnTop(true);
            hw.setFocusableWindowState(false);  // never steal focus
            hw.setBackground(new Color(0,0,0,1));
            JPanel p = new JPanel(null) {
                { setBackground(new Color(0,0,0,1)); }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx=getWidth()/2, cy=getHeight()/2;
                    boolean hovered = Boolean.TRUE.equals(getClientProperty("h"));
                    // Orange ring
                    g2.setColor(new Color(255,140,40)); g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx-10,cy-10,20,20);
                    // Crosshair
                    g2.setColor(new Color(255,255,255,230)); g2.setStroke(new BasicStroke(1.5f));
                    int arm=7, gap=3;
                    g2.drawLine(cx-arm,cy,cx-gap,cy); g2.drawLine(cx+gap,cy,cx+arm,cy);
                    g2.drawLine(cx,cy-arm,cx,cy-gap); g2.drawLine(cx,cy+gap,cx,cy+arm);
                    if (hovered) {
                        // Red dot on hover — hide number
                        g2.setColor(new Color(255,50,50)); g2.fillOval(cx-4,cy-4,8,8);
                    } else {
                        // Number when idle
                        g2.setColor(new Color(255,50,50)); g2.fillOval(cx-3,cy-3,6,6);
                        String num = isStart ? String.valueOf(rowIndex+1) : String.valueOf(rowIndex+2);
                        g2.setFont(new Font("SansSerif",Font.BOLD,9));
                        FontMetrics fm=g2.getFontMetrics(); int lw=fm.stringWidth(num);
                        g2.setColor(new Color(0,0,0,160)); g2.fillOval(cx-6,cy-6,12,12);
                        g2.setColor(Color.WHITE); g2.drawString(num,cx-lw/2,cy+4);
                    }
                }
            };
            int[] lastSc={0,0};
            p.addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){ p.putClientProperty("h",true); p.repaint(); }
                public void mouseExited(MouseEvent e) { p.putClientProperty("h",false); p.repaint(); }
                public void mousePressed(MouseEvent e){
                    Point sc=MouseInfo.getPointerInfo().getLocation();
                    lastSc[0]=sc.x; lastSc[1]=sc.y;
                }
                public void mouseReleased(MouseEvent e){ syncToTable(); refreshLineOverlay(); }
                public void mouseClicked(MouseEvent e){ if(e.getButton()==MouseEvent.BUTTON3) removePin(DragPin.this); }
            });
            p.addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    Point sc=MouseInfo.getPointerInfo().getLocation();
                    int dx=sc.x-lastSc[0], dy=sc.y-lastSc[1];
                    lastSc[0]=sc.x; lastSc[1]=sc.y;
                    if (isStart){ screenX+=dx; screenY+=dy; placeHandle(startWin,true); }
                    else { endX+=dx; endY+=dy; placeHandle(endWin,false); }
                    placeMidHandle();
                    refreshLineOverlay();
                }
            });
            p.setPreferredSize(new Dimension(HSZ,HSZ));
            hw.setContentPane(p); hw.setSize(HSZ,HSZ);
            placeHandle(hw, isStart);
            return hw;
        }

        void placeHandle(JWindow hw, boolean isStart) {
            int sx = isStart ? screenX : endX;
            int sy = isStart ? screenY : endY;
            hw.setLocation(sx-HSZ/2, sy-HSZ/2);
        }

        void syncToTable(){
            if (rowIndex<tableModel.getRowCount()){ tableModel.setValueAt(screenX,rowIndex,1); tableModel.setValueAt(screenY,rowIndex,2); }
            if (rowIndex+1<tableModel.getRowCount()){ tableModel.setValueAt(endX,rowIndex+1,1); tableModel.setValueAt(endY,rowIndex+1,2); }
        }

        @Override void show(){
            startWin.setVisible(true); endWin.setVisible(true); midWin.setVisible(true);
            placeMidHandle();
            refreshLineOverlay();
        }
        @Override void hide(){
            startWin.setVisible(false); endWin.setVisible(false); midWin.setVisible(false);
        }
        @Override void dispose(){
            startWin.dispose(); endWin.dispose(); midWin.dispose();
            win = null;
        }
        // win is unused for DragPin
        { win = null; }
    }

    private void removePin(SmartPin pin) {
        int idx=smartPins.indexOf(pin); if(idx<0)return;
        int span = pin.rowSpan();
        pin.dispose(); smartPins.remove(idx);
        for (int i=0; i<span && pin.rowIndex < tableModel.getRowCount(); i++)
            tableModel.removeRow(pin.rowIndex);
        int r=0;
        for (SmartPin p : smartPins) { p.rowIndex=r; r+=p.rowSpan(); }
        refreshRowNumbers();
        if (barPinCountLabel!=null) barPinCountLabel.setText("Pins: "+smartPins.size());
        refreshSmartInterval();
        refreshLineOverlay();
    }


    // ── Interval picker ───────────────────────────────────────
    private long showIntervalPicker(String title, long currentMs) {
        int ivH=(int)(currentMs/3600000L), ivM=(int)((currentMs%3600000L)/60000L);
        int ivS=(int)((currentMs%60000L)/1000L), ivT=(int)((currentMs%1000L)/100L);
        int ivHu=(int)((currentMs%100L)/10L), ivTh=(int)(currentMs%10L);
        JDialog dlg=new JDialog((java.awt.Frame)null,title,true);
        dlg.getContentPane().setBackground(new Color(28,28,38)); dlg.setLayout(new BorderLayout());
        JPanel top=new JPanel(new BorderLayout()); top.setBackground(new Color(22,22,30)); top.setBorder(BorderFactory.createEmptyBorder(10,12,8,12));
        JLabel lbl=new JLabel(title); lbl.setForeground(ACCENT); lbl.setFont(new Font("SansSerif",Font.BOLD,12)); top.add(lbl,BorderLayout.WEST); dlg.add(top,BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,4)); body.setBackground(new Color(28,28,38)); body.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        String[] units={"H","Min","Sec","1/10","1/100","1/1000"};
        JPanel hdr=new JPanel(new GridLayout(1,6,4,0)); hdr.setBackground(new Color(28,28,38));
        for (String u:units){ JLabel ul=new JLabel(u,SwingConstants.CENTER); ul.setFont(new Font("SansSerif",Font.PLAIN,9)); ul.setForeground(TEXT_DIM); hdr.add(ul); }
        JPanel spRow=new JPanel(new GridLayout(1,6,4,0)); spRow.setBackground(new Color(28,28,38));
        JSpinner[] sps={mkSp(ivH,0,23),mkSp(ivM,0,59),mkSp(ivS,0,59),mkSp(ivT,0,9),mkSp(ivHu,0,9),mkSp(ivTh,0,9)};
        for (JSpinner sp:sps) spRow.add(sp);
        JLabel preview=new JLabel("",SwingConstants.CENTER); preview.setForeground(new Color(80,200,120)); preview.setFont(new Font("Monospaced",Font.BOLD,11));
        Runnable upd=()->{
            long h=((Number)sps[0].getValue()).longValue(),m=((Number)sps[1].getValue()).longValue();
            long s=((Number)sps[2].getValue()).longValue(),t=((Number)sps[3].getValue()).longValue();
            long hu=((Number)sps[4].getValue()).longValue(),th=((Number)sps[5].getValue()).longValue();
            preview.setText(String.format("= %.3f seconds",Math.max(0,h*3600000L+m*60000L+s*1000L+t*100L+hu*10L+th)/1000.0));
        };
        for (JSpinner sp:sps) sp.addChangeListener(e->upd.run()); upd.run();
        body.add(hdr,BorderLayout.NORTH); body.add(spRow,BorderLayout.CENTER); body.add(preview,BorderLayout.SOUTH); dlg.add(body,BorderLayout.CENTER);
        long[] result={-1};
        JPanel btnRow=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,8)); btnRow.setBackground(new Color(22,22,30));
        JButton ok=mkBtn("OK",new Color(50,100,160)); JButton cancel=mkBtn("Cancel",new Color(55,55,75));
        ok.addActionListener(e->{
            long h=((Number)sps[0].getValue()).longValue(),m=((Number)sps[1].getValue()).longValue();
            long s=((Number)sps[2].getValue()).longValue(),t=((Number)sps[3].getValue()).longValue();
            long hu=((Number)sps[4].getValue()).longValue(),th=((Number)sps[5].getValue()).longValue();
            result[0]=Math.max(0,h*3600000L+m*60000L+s*1000L+t*100L+hu*10L+th); dlg.dispose();
        });
        cancel.addActionListener(e->dlg.dispose());
        btnRow.add(cancel); btnRow.add(ok); dlg.add(btnRow,BorderLayout.SOUTH);
        dlg.pack(); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
        return result[0];
    }

    private JSpinner mkSp(int v,int min,int max){
        JSpinner sp=new JSpinner(new SpinnerNumberModel(v,min,max,1));
        sp.setPreferredSize(new Dimension(52,28)); sp.setBackground(INPUT_BG);
        JSpinner.DefaultEditor ed=(JSpinner.DefaultEditor)sp.getEditor();
        ed.getTextField().setBackground(INPUT_BG); ed.getTextField().setForeground(TEXT_MAIN);
        ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        return sp;
    }

    private JButton mkBtn(String t,Color bg){
        JButton b=new JButton(t); b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setBorderPainted(false); b.setFocusPainted(false); return b;
    }

    // ── Hotkey listening ──────────────────────────────────────
    private void startListeningForKey(boolean forStartStop) {
        if (forStartStop){ listeningForStartStop=true; startStopKeyLabel.setText("Start/Stop: [Press any key...]"); startStopKeyLabel.setForeground(ACCENT); }
        else { listeningForSmartPin=true; smartPinKeyLabel.setText("Smart Pin: [Press any key...]"); smartPinKeyLabel.setForeground(ACCENT); }
        new Thread(()->{
            try{Thread.sleep(5000);}catch(InterruptedException ignored){}
            SwingUtilities.invokeLater(()->{
                if(listeningForStartStop){listeningForStartStop=false;startStopKeyLabel.setText("Start/Stop: ["+startStopName+"]");startStopKeyLabel.setForeground(TEXT_MAIN);}
                if(listeningForSmartPin) {listeningForSmartPin=false;smartPinKeyLabel.setText("Smart Pin: ["+smartPinName+"]");smartPinKeyLabel.setForeground(TEXT_MAIN);}
            });
        }).start();
    }

    public void nativeKeyPressed(NativeKeyEvent e) {
        if (listeningForStartStop){startStopKey=e.getKeyCode();startStopName=NativeKeyEvent.getKeyText(e.getKeyCode());listeningForStartStop=false;SwingUtilities.invokeLater(()->{startStopKeyLabel.setText("Start/Stop: ["+startStopName+"]");startStopKeyLabel.setForeground(TEXT_MAIN);});return;}
        if (listeningForSmartPin) {smartPinKey=e.getKeyCode();smartPinName=NativeKeyEvent.getKeyText(e.getKeyCode());listeningForSmartPin=false;SwingUtilities.invokeLater(()->{smartPinKeyLabel.setText("Smart Pin: ["+smartPinName+"]");smartPinKeyLabel.setForeground(TEXT_MAIN);});return;}
        if (e.getKeyCode()==smartPinKey)  SwingUtilities.invokeLater(this::toggleSmartMode);
        if (e.getKeyCode()==startStopKey) { if(running)stopClicking();else startClicking(); }
    }
    public void nativeKeyReleased(NativeKeyEvent e){}
    public void nativeKeyTyped(NativeKeyEvent e){}

    // ── Engine control ────────────────────────────────────────
    public void startClicking() {
        if (running) return;
        running=true; clickCount=0; updateStatus("\u25cf Running",new Color(40,200,80));
        List<int[]> points=new ArrayList<>();
        for (int i=0;i<tableModel.getRowCount();i++) {
            try {
                int btnType = 0;
                try {
                    Object tv = tableModel.getValueAt(i, 6);
                    btnType = ((Number)tv).intValue();
                } catch(Exception ignored){}
                points.add(new int[]{
                    Integer.parseInt(tableModel.getValueAt(i,1).toString()),
                    Integer.parseInt(tableModel.getValueAt(i,2).toString()),
                    Integer.parseInt(tableModel.getValueAt(i,3).toString()),
                    cellToInt(tableModel.getValueAt(i,4)),
                    cellToInt(tableModel.getValueAt(i,5)),
                    btnType
                });
            } catch(Exception ignored){}
        }
        long maxClicks; try{maxClicks=Long.parseLong(maxClicksField.getText().trim());}catch(Exception e){maxClicks=0;}
        int btn=mouseButtonCombo.getSelectedIndex(); boolean dbl=clickTypeCombo.getSelectedIndex()==1;
        engine=new SimpleClickEngine(points,getIntervalMs(),maxClicks,btn,dbl,true,1,null);
        engine.setClickCallback(total->{ clickCount=total; SwingUtilities.invokeLater(()->{ clickCountLabel.setText("Clicks: "+total); clickCountLabel.setForeground(new Color(80,200,120)); }); });
        engineThread=new Thread(engine); engineThread.setDaemon(true); engineThread.start();
        new Thread(()->{ while(running&&engineThread.isAlive()){try{Thread.sleep(200);}catch(InterruptedException ignored){}} stopClicking(); }).start();
    }

    public void stopClicking() { running=false; if(engine!=null)engine.stop(); SwingUtilities.invokeLater(()->updateStatus("\u25cf Stopped",new Color(200,60,60))); }

    // ── Helpers ───────────────────────────────────────────────
    private static int cellToInt(Object val) {
        if (val==null) return 0;
        if (val instanceof Integer) return (Integer)val;
        if (val instanceof Long)    return (int)(long)(Long)val;
        if (val instanceof Number)  return ((Number)val).intValue();
        String s=val.toString().trim();
        if (s.endsWith(" s")||s.endsWith("s")){ try{return(int)(Double.parseDouble(s.replace(" s","").replace("s","").trim())*1000);}catch(Exception ignored){} }
        try{return Integer.parseInt(s);}catch(Exception ignored){}
        try{return(int)Double.parseDouble(s);}catch(Exception ignored){}
        return 0;
    }

    private long getIntervalMs() {
        long h=((Number)spHours.getValue()).longValue(),m=((Number)spMinutes.getValue()).longValue();
        long s=((Number)spSeconds.getValue()).longValue(),t=((Number)spTenths.getValue()).longValue();
        long hu=((Number)spHundredths.getValue()).longValue(),th=((Number)spThousandths.getValue()).longValue();
        return Math.max(1,h*3_600_000L+m*60_000L+s*1_000L+t*100L+hu*10L+th);
    }

    private void refreshSmartInterval() {
        boolean active=false;
        for (int i=0;i<tableModel.getRowCount();i++) { try{if(Integer.parseInt(tableModel.getValueAt(i,3).toString())>1){active=true;break;}}catch(Exception ignored){} }
        if (intervalSpinnersPanel!=null) for (Component c:intervalSpinnersPanel.getComponents()) c.setEnabled(!active);
        if (smartIntervalLabel!=null) { if(active){smartIntervalLabel.setText("\u26a1 Smart Interval active");smartIntervalLabel.setForeground(ACCENT);}else{smartIntervalLabel.setText("Smart Interval off \u2014 set Clicks > 1 to activate");smartIntervalLabel.setForeground(TEXT_DIM);} }
    }

    private void refreshRowNumbers(){ for(int i=0;i<tableModel.getRowCount();i++) tableModel.setValueAt(i+1,i,0); }
    private void updateStatus(String t,Color c){ statusLabel.setText(t); statusLabel.setForeground(c); }

    // ── Dark theme builders ───────────────────────────────────
    private JPanel darkPanel(){ JPanel p=new JPanel(); p.setBackground(PANEL_BG); p.setAlignmentX(Component.LEFT_ALIGNMENT); return p; }
    private TitledBorder darkTitledBorder(String t){ return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COL),t,TitledBorder.LEFT,TitledBorder.TOP,new Font("SansSerif",Font.BOLD,10),TEXT_DIM); }
    private JSeparator darkSeparator(){ JSeparator s=new JSeparator(); s.setForeground(BORDER_COL); s.setBackground(PANEL_BG); return s; }

    private JSpinner dsp(int val,int min,int max){
        JSpinner sp=new JSpinner(new SpinnerNumberModel(val,min,max,1));
        sp.setFont(new Font("SansSerif",Font.PLAIN,11)); sp.setBackground(INPUT_BG);
        JSpinner.DefaultEditor ed=(JSpinner.DefaultEditor)sp.getEditor();
        ed.getTextField().setBackground(INPUT_BG); ed.getTextField().setForeground(TEXT_MAIN);
        ed.getTextField().setCaretColor(TEXT_MAIN); ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(INPUT_BORDER));
        return sp;
    }
    private JLabel dlbl(String t){ JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.PLAIN,11)); l.setForeground(TEXT_MAIN); return l; }
    private JTextField dfield(String v){ JTextField f=new JTextField(v,8); f.setFont(new Font("SansSerif",Font.PLAIN,12)); f.setBackground(INPUT_BG); f.setForeground(TEXT_MAIN); f.setCaretColor(TEXT_MAIN); f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INPUT_BORDER),BorderFactory.createEmptyBorder(3,6,3,6))); return f; }
    private JComboBox<String> dcombo(String[] items){ JComboBox<String> c=new JComboBox<>(items); c.setFont(new Font("SansSerif",Font.PLAIN,11)); c.setBackground(INPUT_BG); c.setForeground(TEXT_MAIN); c.setBorder(BorderFactory.createLineBorder(INPUT_BORDER)); return c; }
    private JButton dactionBtn(String text,Color accent){ JButton b=new JButton(text); b.setFont(new Font("SansSerif",Font.PLAIN,11)); b.setBackground(new Color(28,28,38)); b.setForeground(accent); b.setOpaque(true); b.setFocusPainted(false); b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(accent,1),BorderFactory.createEmptyBorder(3,10,3,10))); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.addMouseListener(new MouseAdapter(){ public void mouseEntered(MouseEvent e){b.setBackground(new Color(40,40,55));} public void mouseExited(MouseEvent e){b.setBackground(new Color(28,28,38));} }); return b; }
    private JButton bigBtn(String text,Color textColor,Color border){ JButton b=new JButton(text); b.setPreferredSize(new Dimension(120,36)); b.setFont(new Font("SansSerif",Font.BOLD,13)); b.setBackground(new Color(28,28,38)); b.setForeground(textColor); b.setOpaque(true); b.setFocusPainted(false); b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border,2),BorderFactory.createEmptyBorder(4,14,4,14))); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.addMouseListener(new MouseAdapter(){ public void mouseEntered(MouseEvent e){b.setBackground(new Color(40,40,55));} public void mouseExited(MouseEvent e){b.setBackground(new Color(28,28,38));} }); return b; }
}