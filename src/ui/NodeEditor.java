package ui;

import nodes.BaseNode;
import nodes.BaseNode.NodePort;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class NodeEditor extends JPanel {

    private static final Color BG       = new Color(28, 28, 38);
    private static final Color BG_DARK  = new Color(22, 22, 30);
    private static final Color BORDER_C = new Color(50, 50, 65);
    private static final Color TEXT     = new Color(220, 220, 230);
    private static final Color TEXT_DIM = new Color(120, 120, 150);
    private static final Color ACCENT   = new Color(80, 140, 255);
    private static final Color INPUT_BG = new Color(35, 35, 50);
    private static final Color INPUT_BD = new Color(60, 60, 85);

    private BaseNode   currentNode;
    private NodeCanvas canvas;
    private JPanel     content;

    private JLabel    templatePreview;
    private JLabel    zoneStatusLabel;
    private JCheckBox sameAsCaptureCb;

    public NodeEditor() {
        setBackground(BG);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_C));
        setLayout(new BorderLayout());

        JLabel header = new JLabel("  NODE EDITOR");
        header.setForeground(TEXT_DIM);
        header.setFont(new Font("SansSerif", Font.BOLD, 10));
        header.setBorder(new EmptyBorder(10, 8, 8, 0));
        header.setBackground(BG_DARK);
        header.setOpaque(true);
        add(header, BorderLayout.NORTH);

        content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBackground(BG);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        showEmpty();
    }

    public void setNode(BaseNode node, NodeCanvas canvas) {
        this.currentNode = node;
        this.canvas      = canvas;
        rebuild();
    }

    private void showEmpty() {
        content.removeAll();
        JLabel lbl = new JLabel("<html><center><br><br>Select a node<br>to edit its<br>properties</center></html>");
        lbl.setForeground(new Color(70, 70, 90));
        lbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setAlignmentX(CENTER_ALIGNMENT);
        content.add(Box.createVerticalStrut(20));
        content.add(lbl);
        revalidate(); repaint();
    }

    private void rebuild() {
        if (currentNode == null) { showEmpty(); return; }
        content.removeAll();
        content.add(Box.createVerticalStrut(8));

        addSection("Identity");
        addLabeledField("Label", currentNode.label, val -> {
            currentNode.label = val; canvas.refreshNode(currentNode);
        });

        addSection("Branch Options");
        addCheckBox("Enabled", currentNode.branchEnabled, val -> {
            currentNode.branchEnabled = val; canvas.refreshNode(currentNode);
        });
        addCheckBox("Log transitions", currentNode.logTransition, val -> currentNode.logTransition = val);
        addLabeledSpinner("Entry delay (ms)", currentNode.entryDelayMs, 0, 60000, val -> currentNode.entryDelayMs = val);

        switch (currentNode.type) {
            case WATCH_ZONE:   buildWatchZone();   break;
            case CLICK:        buildClick();        break;
            case SIMPLE_CLICK: buildSimpleClick();  break;
            case CONDITION:    buildCondition();    break;
            case LOOP:         buildLoop();         break;
            case WAIT:         buildWait();         break;
            case STOP:         buildStop();         break;
        }

        if (!currentNode.outputs.isEmpty()) {
            addSection("Output Ports");
            for (NodePort port : currentNode.outputs) {
                addLabeledField("Label: "+port.name, port.displayLabel(), val -> {
                    port.customLabel = val; canvas.refreshNode(currentNode);
                });
                addCheckBox("Enabled: "+port.name, port.enabled, val -> port.enabled = val);
                addLabeledSpinner("Delay ms: "+port.name, port.arrowDelayMs, 0, 60000, val -> port.arrowDelayMs = val);
            }
        }

        content.add(Box.createVerticalGlue());
        revalidate(); repaint();
    }

    // =========================================================
    //  WATCH ZONE
    // =========================================================
    private void buildWatchZone() {
        addSection("Image Name");
        addLabeledField("Name", getStrField("imageName","Unnamed Image"), val -> setStrField("imageName", val));

        addSection("Capture Template");

        templatePreview = new JLabel("No image captured");
        templatePreview.setForeground(TEXT_DIM);
        templatePreview.setFont(new Font("SansSerif", Font.ITALIC, 10));
        templatePreview.setHorizontalAlignment(SwingConstants.CENTER);
        templatePreview.setPreferredSize(new Dimension(220, 60));
        templatePreview.setMaximumSize(new Dimension(240, 60));
        templatePreview.setAlignmentX(LEFT_ALIGNMENT);
        templatePreview.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C), BorderFactory.createEmptyBorder(4,4,4,4)));
        templatePreview.setBackground(new Color(20,20,30));
        templatePreview.setOpaque(true);

        BufferedImage existing = (BufferedImage) getObjField("template");
        if (existing != null) updatePreview(existing);

        JPanel previewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        previewRow.setBackground(BG); previewRow.setAlignmentX(LEFT_ALIGNMENT);
        previewRow.add(templatePreview);
        content.add(previewRow);

        JButton captureBtn = editorBtn("Capture Template", new Color(50,100,170));
        captureBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel capRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        capRow.setBackground(BG); capRow.setAlignmentX(LEFT_ALIGNMENT);
        capRow.add(captureBtn);
        content.add(capRow);
        captureBtn.addActionListener(e -> startCapture(true));

        addSection("Watch Zone");

        sameAsCaptureCb = new JCheckBox("Use same area as captured template");
        sameAsCaptureCb.setBackground(BG); sameAsCaptureCb.setForeground(TEXT);
        sameAsCaptureCb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sameAsCaptureCb.setBorder(new EmptyBorder(2,8,2,0));
        sameAsCaptureCb.setAlignmentX(LEFT_ALIGNMENT);
        sameAsCaptureCb.setSelected(getBoolField("sameAsCapture", false));
        content.add(sameAsCaptureCb);

        zoneStatusLabel = new JLabel();
        zoneStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        zoneStatusLabel.setAlignmentX(LEFT_ALIGNMENT);
        zoneStatusLabel.setBorder(new EmptyBorder(0,8,0,0));
        refreshZoneStatus();
        content.add(zoneStatusLabel);

        JButton setZoneBtn = editorBtn("Set Watch Zone", new Color(50,130,80));
        setZoneBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel zoneRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        zoneRow.setBackground(BG); zoneRow.setAlignmentX(LEFT_ALIGNMENT);
        zoneRow.add(setZoneBtn);
        content.add(zoneRow);

        sameAsCaptureCb.addActionListener(e -> {
            boolean same = sameAsCaptureCb.isSelected();
            setBoolField("sameAsCapture", same);
            setZoneBtn.setEnabled(!same);
            if (same) applySameAsCapture();
            refreshZoneStatus();
        });
        setZoneBtn.setEnabled(!getBoolField("sameAsCapture", false));
        setZoneBtn.addActionListener(e -> startCapture(false));

        addSection("Match Settings");
        addLabeledSpinner("Match % threshold",     getIntField("matchThreshold",85),    10, 100,   val -> setIntField("matchThreshold", val));
        addLabeledSpinner("Poll interval (ms)",    getIntField("pollIntervalMs",500),    50, 10000, val -> setIntField("pollIntervalMs", val));
        addLabeledSpinner("Pre-trigger delay (ms)",getIntField("preTriggerDelayMs",0),   0,  10000, val -> setIntField("preTriggerDelayMs", val));
        addLabeledSpinner("Timeout (ms, 0=inf)",   getIntField("timeoutMs",0),           0,  60000, val -> setIntField("timeoutMs", val));
        addLabeledSpinner("Retry count",           getIntField("retryCount",0),          0,  100,   val -> setIntField("retryCount", val));
    }

    private void startCapture(boolean isTemplate) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) win.setVisible(false);
        Timer delay = new Timer(300, ev -> showOverlay(isTemplate, win));
        delay.setRepeats(false); delay.start();
    }

    private void showOverlay(boolean isTemplate, Window parentWindow) {
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0,0,0,0));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        overlay.setBounds(0, 0, screen.width, screen.height);

        int[] drag = {0,0,0,0};
        boolean[] dragging = {false};

        JPanel glass = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0,0,0,80)); g2.fillRect(0,0,getWidth(),getHeight());
                String msg = isTemplate
                    ? "Drag to select the BUTTON IMAGE to detect   [ESC = cancel]"
                    : "Drag to select the WATCH ZONE scan area     [ESC = cancel]";
                g2.setFont(new Font("SansSerif",Font.BOLD,16));
                FontMetrics fm = g2.getFontMetrics(); int tw = fm.stringWidth(msg);
                g2.setColor(new Color(0,0,0,160));
                g2.fillRoundRect((getWidth()-tw)/2-12,18,tw+24,34,10,10);
                g2.setColor(isTemplate ? new Color(100,180,255) : new Color(100,255,160));
                g2.drawString(msg,(getWidth()-tw)/2,40);
                if (dragging[0]) {
                    int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                    int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                    Composite old=g2.getComposite();
                    g2.setComposite(AlphaComposite.Clear); g2.fillRect(rx,ry,rw,rh);
                    g2.setComposite(old);
                    Color bc = isTemplate ? new Color(100,180,255) : new Color(100,255,160);
                    g2.setColor(bc); g2.setStroke(new BasicStroke(2)); g2.drawRect(rx,ry,rw,rh);
                    g2.setFont(new Font("Monospaced",Font.BOLD,11));
                    g2.drawString(rw+"x"+rh+"  ("+rx+","+ry+")", rx+4, ry>20?ry-4:ry+rh+16);
                }
            }
        };
        glass.setOpaque(false);
        glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        glass.setFocusable(true);

        glass.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                drag[0]=e.getX(); drag[1]=e.getY();
                drag[2]=e.getX(); drag[3]=e.getY(); dragging[0]=true;
            }
            public void mouseReleased(MouseEvent e) {
                drag[2]=e.getX(); drag[3]=e.getY();
                int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                overlay.dispose();
                if (rw<4||rh<4) { if(parentWindow!=null) parentWindow.setVisible(true); return; }
                Timer t = new Timer(150, ev -> {
                    try {
                        java.awt.Robot robot = new java.awt.Robot();
                        java.awt.Rectangle rect = new java.awt.Rectangle(rx,ry,rw,rh);
                        BufferedImage capture = robot.createScreenCapture(rect);
                        if (isTemplate) {
                            setObjField("template", capture);
                            setObjField("captureRect", rect);
                            SwingUtilities.invokeLater(() -> {
                                updatePreview(capture);
                                if (getBoolField("sameAsCapture",false)) applySameAsCapture();
                                else refreshZoneStatus();
                            });
                        } else {
                            setObjField("watchZone", rect);
                            SwingUtilities.invokeLater(NodeEditor.this::refreshZoneStatus);
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                    if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
                });
                t.setRepeats(false); t.start();
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) { drag[2]=e.getX(); drag[3]=e.getY(); glass.repaint(); }
        });
        glass.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode()==KeyEvent.VK_ESCAPE) {
                    overlay.dispose();
                    if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
                }
            }
        });

        overlay.setContentPane(glass);
        overlay.setVisible(true);
        glass.requestFocusInWindow();
    }

    private void applySameAsCapture() {
        Object r = getObjField("captureRect");
        if (r instanceof java.awt.Rectangle) setObjField("watchZone", r);
        refreshZoneStatus();
    }

    private void refreshZoneStatus() {
        if (zoneStatusLabel==null) return;
        Object zone = getObjField("watchZone");
        if (zone instanceof java.awt.Rectangle) {
            java.awt.Rectangle r = (java.awt.Rectangle) zone;
            String tag = getBoolField("sameAsCapture",false) ? " (same as capture)" : "";
            zoneStatusLabel.setText("Zone: "+r.x+","+r.y+"  "+r.width+"x"+r.height+"px"+tag);
            zoneStatusLabel.setForeground(new Color(80,200,120));
        } else {
            zoneStatusLabel.setText("No zone set");
            zoneStatusLabel.setForeground(TEXT_DIM);
        }
    }

    private void updatePreview(BufferedImage img) {
        if (templatePreview==null||img==null) return;
        int pw=220,ph=56;
        double scale=Math.min((double)pw/img.getWidth(),(double)ph/img.getHeight());
        int sw=(int)(img.getWidth()*scale),sh=(int)(img.getHeight()*scale);
        Image scaled=img.getScaledInstance(sw,sh,Image.SCALE_SMOOTH);
        templatePreview.setIcon(new ImageIcon(scaled));
        templatePreview.setText("");
        templatePreview.revalidate(); templatePreview.repaint();
    }

    // =========================================================
    //  CLICK
    // =========================================================
    private void buildClick() {
        addSection("Click Settings");
        addLabeledSpinner("Click X",            getIntField("clickX",0),     -9999,9999,  val -> setIntField("clickX", val));
        addLabeledSpinner("Click Y",            getIntField("clickY",0),     -9999,9999,  val -> setIntField("clickY", val));
        addLabeledSpinner("Click count",        getIntField("clickCount",1),  1,   999,   val -> setIntField("clickCount", val));
        addLabeledSpinner("Sub-click delay (ms)",getIntField("subDelayMs",100),0,  10000, val -> setIntField("subDelayMs", val));
        addCombo("Mouse button", new String[]{"Left","Right","Middle"}, getIntField("mouseButton",0), val -> setIntField("mouseButton", val));
        addCheckBox("Double click",            getBoolField("doubleClick",false),          val -> setBoolField("doubleClick", val));
        addCheckBox("Click at last match location", getBoolField("useLastMatchLocation",false), val -> setBoolField("useLastMatchLocation", val));
    }

    // =========================================================
    //  SIMPLE CLICK
    // =========================================================
    private void buildSimpleClick() {
        addSection("Click Interval");

        String[] units = {"H","Min","Sec","1/10","1/100","1/1000"};
        JPanel unitHdr = new JPanel(new GridLayout(1,6,2,0));
        unitHdr.setBackground(BG); unitHdr.setBorder(new EmptyBorder(4,8,0,8));
        unitHdr.setAlignmentX(LEFT_ALIGNMENT);
        for (String u : units) {
            JLabel l = new JLabel(u, SwingConstants.CENTER);
            l.setFont(new Font("SansSerif",Font.PLAIN,9)); l.setForeground(TEXT_DIM);
            unitHdr.add(l);
        }
        content.add(unitHdr);

        JPanel spRow = new JPanel(new GridLayout(1,6,2,0));
        spRow.setBackground(BG); spRow.setBorder(new EmptyBorder(2,8,6,8));
        spRow.setAlignmentX(LEFT_ALIGNMENT);

        long iv = getLongField("intervalMs",100);
        int ivH=(int)(iv/3600000L), ivM=(int)((iv%3600000L)/60000L);
        int ivS=(int)((iv%60000L)/1000L), ivT=(int)((iv%1000L)/100L);
        int ivHu=(int)((iv%100L)/10L), ivTh=(int)(iv%10L);

        JSpinner[] sps = {
            nodeSpinner(ivH,0,23), nodeSpinner(ivM,0,59), nodeSpinner(ivS,0,59),
            nodeSpinner(ivT,0,9),  nodeSpinner(ivHu,0,9), nodeSpinner(ivTh,0,9)
        };
        for (JSpinner sp : sps) spRow.add(sp);
        for (JSpinner sp : sps) sp.addChangeListener(e -> {
            long h=((Number)sps[0].getValue()).longValue(), m=((Number)sps[1].getValue()).longValue();
            long s=((Number)sps[2].getValue()).longValue(), t=((Number)sps[3].getValue()).longValue();
            long hu=((Number)sps[4].getValue()).longValue(), th=((Number)sps[5].getValue()).longValue();
            setLongField("intervalMs", Math.max(1, h*3600000L+m*60000L+s*1000L+t*100L+hu*10L+th));
        });
        content.add(spRow);

        addSection("Click Settings");
        addLabeledSpinner("Max clicks (0=inf)", (int)getLongField("maxClicks",0), 0, 99999, val -> setLongField("maxClicks", val));
        addCombo("Mouse button", new String[]{"Left","Right","Middle"}, getIntField("mouseButton",0), val -> setIntField("mouseButton", val));
        addCheckBox("Double click",         getBoolField("doubleClick",false),          val -> setBoolField("doubleClick", val));
        addCheckBox("Click at last match",  getBoolField("useLastMatchLocation",false),  val -> setBoolField("useLastMatchLocation", val));

        addSection("Click Points");
        addInfo("Empty = click at cursor position");

        // Smart Pin button — pick points by clicking on screen
        JButton smartPinBtn = editorBtn("⊕  Smart Pin — pick points on screen", new Color(50,100,180));
        smartPinBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel pinRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        pinRow.setBackground(BG); pinRow.setAlignmentX(LEFT_ALIGNMENT);
        pinRow.add(smartPinBtn);
        content.add(pinRow);
        // Will be wired after tm is created
        final DefaultTableModel[] tmRef = {null};

        String[] cols = {"#","X","Y","Clicks","Sub-ms","After-ms"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return c > 0; }
        };
        java.util.List<int[]> pts = getPointsField();
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            tm.addRow(new Object[]{i+1, p[0], p[1], p[2], p[3], p[4]});
        }

        JTable tbl = new JTable(tm);
        tbl.setFont(new Font("SansSerif",Font.PLAIN,11)); tbl.setRowHeight(22);
        tbl.setBackground(new Color(32,32,44)); tbl.setForeground(new Color(220,220,230));
        tbl.setGridColor(new Color(45,45,60));
        tbl.setSelectionBackground(new Color(50,80,140)); tbl.setSelectionForeground(Color.WHITE);
        tbl.getTableHeader().setBackground(new Color(35,35,50));
        tbl.getTableHeader().setForeground(new Color(120,120,150));
        tbl.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,9));
        int[] cw = {18,45,45,40,50,55};
        for (int i=0;i<cw.length;i++) tbl.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);
        tm.addTableModelListener(e -> syncPointsToNode(tm));
        tmRef[0] = tm;

        // Wire Smart Pin button — hides window, shows crosshair, click to add points
        smartPinBtn.addActionListener(e -> startSmartPinSession(tmRef[0]));

        JScrollPane tblScroll = new JScrollPane(tbl);
        tblScroll.setPreferredSize(new Dimension(240,100));
        tblScroll.setMaximumSize(new Dimension(260,100));
        tblScroll.setBorder(BorderFactory.createLineBorder(new Color(50,50,65)));
        tblScroll.getViewport().setBackground(new Color(32,32,44));
        tblScroll.setAlignmentX(LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(4));
        content.add(tblScroll);

        JPanel tblBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,4,4));
        tblBtns.setBackground(BG); tblBtns.setAlignmentX(LEFT_ALIGNMENT);
        JButton addBtn = editorBtn("+ Add",   new Color(50,100,160));
        JButton remBtn = editorBtn("- Remove",new Color(130,40,40));
        JButton clrBtn = editorBtn("Clear",   new Color(55,55,75));

        addBtn.addActionListener(e -> {
            Point mouse = MouseInfo.getPointerInfo().getLocation();
            tm.addRow(new Object[]{tm.getRowCount()+1, mouse.x, mouse.y, 1, 100, 100});
            syncPointsToNode(tm);
        });
        remBtn.addActionListener(e -> {
            int row = tbl.getSelectedRow();
            if (row>=0) {
                tm.removeRow(row);
                for (int i=0;i<tm.getRowCount();i++) tm.setValueAt(i+1,i,0);
                syncPointsToNode(tm);
            }
        });
        clrBtn.addActionListener(e -> { tm.setRowCount(0); syncPointsToNode(tm); });
        tblBtns.add(addBtn); tblBtns.add(remBtn); tblBtns.add(clrBtn);
        content.add(tblBtns);

        addSection("Execution");
        addCheckBox("Wait to finish",       getBoolField("waitToFinish",true),        val -> setBoolField("waitToFinish", val));
        addCheckBox("Run in background",    getBoolField("runInBackground",false),     val -> setBoolField("runInBackground", val));
        addCheckBox("Repeat until stopped", getBoolField("repeatUntilStopped",false),  val -> setBoolField("repeatUntilStopped", val));
        addLabeledSpinner("Repeat times",   getIntField("repeatTimes",1), 1, 9999,     val -> setIntField("repeatTimes", val));
    }

    // =========================================================
    //  CONDITION
    // =========================================================
    private void buildCondition() {
        addSection("Condition Settings");
        addInfo("Capture an image to check for.");
        addLabeledSpinner("Match % threshold", getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
        JButton captureBtn = editorBtn("Capture Image", new Color(50,100,170));
        captureBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        row.setBackground(BG); row.setAlignmentX(LEFT_ALIGNMENT); row.add(captureBtn);
        content.add(row);
        captureBtn.addActionListener(e -> startCapture(true));
    }

    // =========================================================
    //  LOOP
    // =========================================================
    private void buildLoop() {
        addSection("Loop Settings");
        addCombo("Loop mode", new String[]{"Fixed count","Until found","Until not found","Forever"}, 0, val -> {});
        addLabeledSpinner("Loop count",     getIntField("loopCount",3),   1,    9999,  val -> setIntField("loopCount", val));
        addLabeledSpinner("Loop delay (ms)",getIntField("loopDelayMs",0), 0,   60000,  val -> setIntField("loopDelayMs", val));
        addLabeledSpinner("Match %",        getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
    }

    // =========================================================
    //  WAIT
    // =========================================================
    private void buildWait() {
        addSection("Wait Settings");
        addCombo("Wait mode", new String[]{"Fixed delay","Until found","Until not found"}, 0, val -> {});
        addLabeledSpinner("Delay (ms)",         getIntField("delayMs",1000),  1,  600000, val -> setIntField("delayMs", val));
        addLabeledSpinner("Timeout (ms, 0=inf)",getIntField("timeoutMs",0),   0,   60000, val -> setIntField("timeoutMs", val));
        addLabeledSpinner("Poll interval (ms)", getIntField("pollMs",500),    50,  10000, val -> setIntField("pollMs", val));
        addLabeledSpinner("Match %",            getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
    }

    // =========================================================
    //  STOP
    // =========================================================
    private void buildStop() {
        addSection("Stop Settings");
        addCombo("Stop mode", new String[]{"This tree only","All trees"}, 0, val -> {});
        addCheckBox("Show message", getBoolField("showMessage",false), val -> setBoolField("showMessage", val));
        addLabeledField("Custom message", getStrField("customMessage",""), val -> setStrField("customMessage", val));
    }

    // =========================================================
    //  SMART PIN SESSION — identical UI to SimpleClickPanel
    // =========================================================
    private void startSmartPinSession(DefaultTableModel tm) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) win.setVisible(false);

        // Capture node reference NOW — currentNode may change later
        final nodes.BaseNode sessionNode = currentNode;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        java.util.List<EditorPin> pins = new java.util.ArrayList<>();

        // Restore existing points as pins
        for (int i = 0; i < tm.getRowCount(); i++) {
            try {
                int px = Integer.parseInt(tm.getValueAt(i,1).toString());
                int py = Integer.parseInt(tm.getValueAt(i,2).toString());
                EditorPin ep = new EditorPin(px, py, i, tm);
                pins.add(ep);
                ep.show();
            } catch (Exception ignored) {}
        }

        // ── Floating bar ──────────────────────────────────────
        JWindow smartBar = new JWindow();
        smartBar.setAlwaysOnTop(true);
        smartBar.setBackground(new Color(0,0,0,0));
        smartBar.setFocusableWindowState(true);

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,15,22,240));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(new Color(80,140,255,200));
                g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER,10,7));

        JLabel dragHandle = new JLabel("⠿");
        dragHandle.setFont(new Font("SansSerif",Font.PLAIN,16));
        dragHandle.setForeground(new Color(100,100,130));
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLbl = new JLabel("Smart Pin");
        titleLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        titleLbl.setForeground(new Color(80,140,255));

        JLabel pinCountLbl = new JLabel("Pins: "+pins.size());
        pinCountLbl.setFont(new Font("SansSerif",Font.PLAIN,11));
        pinCountLbl.setForeground(new Color(120,120,150));

        JLabel coordsLbl = new JLabel("X: ─── Y: ───");
        coordsLbl.setFont(new Font("Monospaced",Font.BOLD,12));
        coordsLbl.setForeground(new Color(80,220,120));

        JButton addPinBtn = smartBarBtn("+ Pin", new Color(50,100,180));
        JButton doneBtn   = smartBarBtn("✓ Done", new Color(60,60,80));

        JLabel s1=new JLabel("|"); s1.setForeground(new Color(60,60,80));
        JLabel s2=new JLabel("|"); s2.setForeground(new Color(60,60,80));
        JLabel s3=new JLabel("|"); s3.setForeground(new Color(60,60,80));

        bar.add(dragHandle); bar.add(titleLbl);
        bar.add(s1); bar.add(pinCountLbl);
        bar.add(s2); bar.add(coordsLbl);
        bar.add(s3); bar.add(addPinBtn); bar.add(doneBtn);

        int[] off={0,0};
        bar.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){ off[0]=e.getX(); off[1]=e.getY(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){
                Point loc=smartBar.getLocationOnScreen();
                smartBar.setLocation(loc.x+e.getX()-off[0], loc.y+e.getY()-off[1]);
            }
        });

        smartBar.setContentPane(bar);
        smartBar.pack();
        smartBar.setLocation(screen.width/2-smartBar.getWidth()/2, 18);
        smartBar.setVisible(true);

        // Live coords + track last mouse pos
        int[] lastMouse = {0,0};
        javax.swing.Timer coordTimer = new javax.swing.Timer(50, e -> {
            Point p = MouseInfo.getPointerInfo().getLocation();
            lastMouse[0]=p.x; lastMouse[1]=p.y;
            coordsLbl.setText("X: "+p.x+"  Y: "+p.y);
        });
        coordTimer.start();

        Runnable finish = () -> {
            coordTimer.stop();
            // Build points list from table and save directly to captured node
            java.util.List<int[]> pts = new java.util.ArrayList<>();
            for (int i=0;i<tm.getRowCount();i++) {
                try {
                    pts.add(new int[]{
                        Integer.parseInt(tm.getValueAt(i,1).toString()),
                        Integer.parseInt(tm.getValueAt(i,2).toString()),
                        Integer.parseInt(tm.getValueAt(i,3).toString()),
                        Integer.parseInt(tm.getValueAt(i,4).toString()),
                        Integer.parseInt(tm.getValueAt(i,5).toString())
                    });
                } catch (Exception ignored) {}
            }
            try { getField(sessionNode, "points").set(sessionNode, pts); }
            catch (Exception e) { e.printStackTrace(); }
            for (EditorPin ep : pins) ep.dispose();
            try { smartBar.dispose(); } catch(Exception ignored) {}
            if (win!=null) SwingUtilities.invokeLater(() -> { win.setVisible(true); win.toFront(); });
            javax.swing.Timer t = new javax.swing.Timer(150, e -> {
                currentNode = sessionNode; // ensure we rebuild the right node
                rebuild();
            });
            t.setRepeats(false); t.start();
        };

        doneBtn.addActionListener(ae -> finish.run());

        addPinBtn.addActionListener(ae -> {
            int px=lastMouse[0], py=lastMouse[1];
            int row=tm.getRowCount();
            tm.addRow(new Object[]{row+1, px, py, 1, 100, 100});
            syncPointsToNode(tm);
            EditorPin ep = new EditorPin(px, py, row, tm);
            pins.add(ep);
            ep.onRemoved = () -> {
                pins.remove(ep);
                // Fix rowIndex for remaining pins
                for (int ri=0;ri<pins.size();ri++) pins.get(ri).rowIndex=ri;
                pinCountLbl.setText("Pins: "+pins.size());
            };
            ep.show();
            pinCountLbl.setText("Pins: "+pins.size());
        });
    }

    // ── Floating pin widget (mirrors SmartPin in SimpleClickPanel) ──
    private class EditorPin {
        JWindow win;
        int screenX, screenY, rowIndex;
        DefaultTableModel tm;
        int dragOffX, dragOffY;
        Runnable onRemoved;

        EditorPin(int x, int y, int row, DefaultTableModel tm) {
            screenX=x; screenY=y; rowIndex=row; this.tm=tm; build();
        }

        void build() {
            win = new JWindow();
            win.setAlwaysOnTop(true);
            win.setBackground(new Color(0,0,0,0));

            JPanel panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0,0,0,50));
                    g2.fillRoundRect(3,3,getWidth()-3,getHeight()-16,12,12);
                    g2.setColor(new Color(20,20,32,230));
                    g2.fillRoundRect(0,0,getWidth()-3,getHeight()-13,12,12);
                    int cx=(getWidth()-3)/2;
                    g2.fillPolygon(new int[]{cx-7,cx+7,cx},
                                   new int[]{getHeight()-13,getHeight()-13,getHeight()-1},3);
                    // Red dot at tip = exact click location (4px)
                    g2.setColor(new Color(255,60,60));
                    g2.fillRect(cx-2, getHeight()-4, 4, 4);
                    g2.setColor(new Color(80,140,255));
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
            panel.setPreferredSize(new Dimension(140,54));

            JLabel info = new JLabel(infoHtml());
            info.setFont(new Font("SansSerif",Font.PLAIN,10));
            info.setBorder(new EmptyBorder(4,28,14,4));
            panel.add(info, BorderLayout.CENTER);

            JLabel closeBtn = new JLabel("×");
            closeBtn.setFont(new Font("SansSerif",Font.BOLD,13));
            closeBtn.setForeground(new Color(180,180,200));
            closeBtn.setBorder(new EmptyBorder(2,0,14,6));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){
                    if (rowIndex < tm.getRowCount()) tm.removeRow(rowIndex);
                    // refresh row numbers
                    for (int i=0;i<tm.getRowCount();i++) tm.setValueAt(i+1,i,0);
                    syncPointsToNode(tm); dispose();
                    if (onRemoved != null) onRemoved.run();
                }
                public void mouseEntered(MouseEvent e){ closeBtn.setForeground(new Color(255,80,80)); }
                public void mouseExited(MouseEvent e) { closeBtn.setForeground(new Color(180,180,200)); }
            });
            panel.add(closeBtn, BorderLayout.EAST);

            panel.addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){ dragOffX=e.getX(); dragOffY=e.getY(); }
            });
            panel.addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    Point loc=win.getLocationOnScreen();
                    int nx=loc.x+e.getX()-dragOffX, ny=loc.y+e.getY()-dragOffY;
                    win.setLocation(nx,ny);
                    screenX=nx+win.getWidth()/2; screenY=ny+win.getHeight();
                    if (rowIndex<tm.getRowCount()) {
                        tm.setValueAt(screenX,rowIndex,1);
                        tm.setValueAt(screenY,rowIndex,2);
                        syncPointsToNode(tm);
                    }
                }
            });

            win.setContentPane(panel); win.pack();
            win.setLocation(screenX-win.getWidth()/2, screenY-win.getHeight());
        }

        String infoHtml() {
            return "<html><span style='color:#64b4ff'>"+screenX+", "+screenY+"</span></html>";
        }

        void show()    { win.setVisible(true); }
        void dispose() { win.dispose(); }
    }

    private JButton smartBarBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif",Font.PLAIN,11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // =========================================================
    //  REFLECTION HELPERS
    // =========================================================
    // ── Reflection helpers — use getDeclaredField+setAccessible for package-private node classes ──
    private java.lang.reflect.Field getField(nodes.BaseNode node, String name) throws Exception {
        Class<?> cls = node.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) { cls = cls.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private int getIntField(String name, int def) {
        try { return (int) getField(currentNode, name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setIntField(String name, int val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private long getLongField(String name, long def) {
        try { return (long) getField(currentNode, name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setLongField(String name, long val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private boolean getBoolField(String name, boolean def) {
        try { return (boolean) getField(currentNode, name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setBoolField(String name, boolean val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private String getStrField(String name, String def) {
        try { Object v=getField(currentNode,name).get(currentNode); return v!=null?v.toString():def; }
        catch (Exception e) { return def; }
    }
    private void setStrField(String name, String val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private Object getObjField(String name) {
        try { return getField(currentNode, name).get(currentNode); }
        catch (Exception e) { return null; }
    }
    private void setObjField(String name, Object val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private java.util.List<int[]> getPointsField() {
        try {
            Object v = getField(currentNode, "points").get(currentNode);
            if (v instanceof java.util.List) return (java.util.List<int[]>) v;
        } catch (Exception ignored) {}
        return new java.util.ArrayList<>();
    }

    private void syncPointsToNode(DefaultTableModel tm) {
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        for (int i=0;i<tm.getRowCount();i++) {
            try {
                pts.add(new int[]{
                    Integer.parseInt(tm.getValueAt(i,1).toString()),
                    Integer.parseInt(tm.getValueAt(i,2).toString()),
                    Integer.parseInt(tm.getValueAt(i,3).toString()),
                    Integer.parseInt(tm.getValueAt(i,4).toString()),
                    Integer.parseInt(tm.getValueAt(i,5).toString())
                });
            } catch (Exception ignored) {}
        }
        try { getField(currentNode, "points").set(currentNode, pts); }
        catch (Exception ignored) {}
    }

    private void syncPointsToNode(DefaultTableModel tm, nodes.BaseNode node) {
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        for (int i=0;i<tm.getRowCount();i++) {
            try {
                pts.add(new int[]{
                    Integer.parseInt(tm.getValueAt(i,1).toString()),
                    Integer.parseInt(tm.getValueAt(i,2).toString()),
                    Integer.parseInt(tm.getValueAt(i,3).toString()),
                    Integer.parseInt(tm.getValueAt(i,4).toString()),
                    Integer.parseInt(tm.getValueAt(i,5).toString())
                });
            } catch (Exception ignored) {}
        }
        try { getField(node, "points").set(node, pts); }
        catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================
    //  UI COMPONENT BUILDERS
    // =========================================================
    interface IntSetter    { void set(int val); }
    interface BoolSetter   { void set(boolean val); }
    interface StringSetter { void set(String val); }

    private void addSection(String title) {
        JLabel lbl = new JLabel("  " + title.toUpperCase());
        lbl.setForeground(ACCENT);
        lbl.setFont(new Font("SansSerif",Font.BOLD,10));
        lbl.setBorder(new EmptyBorder(10,0,4,0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lbl);
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_C);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        content.add(sep);
    }

    private void addInfo(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setForeground(new Color(100,160,100));
        lbl.setFont(new Font("SansSerif",Font.ITALIC,10));
        lbl.setBorder(new EmptyBorder(2,0,2,0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lbl);
    }

    private void addLabeledField(String label, String value, StringSetter setter) {
        JPanel row = rowPanel();
        row.add(fieldLabel(label));
        JTextField tf = new JTextField(value, 10);
        tf.setBackground(INPUT_BG); tf.setForeground(TEXT); tf.setCaretColor(TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(INPUT_BD), BorderFactory.createEmptyBorder(2,5,2,5)));
        tf.setFont(new Font("SansSerif",Font.PLAIN,11));
        tf.addActionListener(e -> setter.set(tf.getText()));
        tf.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { setter.set(tf.getText()); }});
        row.add(tf); content.add(row);
    }

    private void addLabeledSpinner(String label, int value, int min, int max, IntSetter setter) {
        JPanel row = rowPanel();
        row.add(fieldLabel(label));
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value,min,max,1));
        sp.setPreferredSize(new Dimension(80,24)); sp.setBackground(INPUT_BG);
        sp.setFont(new Font("SansSerif",Font.PLAIN,11));
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor)sp.getEditor();
        ed.getTextField().setBackground(INPUT_BG); ed.getTextField().setForeground(TEXT);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(INPUT_BD));
        sp.addChangeListener(e -> setter.set(((Number)sp.getValue()).intValue()));
        row.add(sp); content.add(row);
    }

    private void addCheckBox(String label, boolean value, BoolSetter setter) {
        JCheckBox cb = new JCheckBox(label, value);
        cb.setForeground(TEXT); cb.setBackground(BG);
        cb.setFont(new Font("SansSerif",Font.PLAIN,11));
        cb.setBorder(new EmptyBorder(2,8,2,0));
        cb.setAlignmentX(LEFT_ALIGNMENT);
        cb.addActionListener(e -> setter.set(cb.isSelected()));
        content.add(cb);
    }

    private void addCombo(String label, String[] options, int selected, IntSetter setter) {
        JPanel row = rowPanel();
        row.add(fieldLabel(label));
        JComboBox<String> cb = new JComboBox<>(options);
        cb.setSelectedIndex(Math.min(selected,options.length-1));
        cb.setBackground(INPUT_BG); cb.setForeground(TEXT);
        cb.setFont(new Font("SansSerif",Font.PLAIN,11));
        cb.addActionListener(e -> setter.set(cb.getSelectedIndex()));
        row.add(cb); content.add(row);
    }

    private JSpinner nodeSpinner(int val, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val,min,max,1));
        sp.setPreferredSize(new Dimension(36,24)); sp.setBackground(INPUT_BG);
        sp.setFont(new Font("SansSerif",Font.PLAIN,10));
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor)sp.getEditor();
        ed.getTextField().setBackground(INPUT_BG); ed.getTextField().setForeground(TEXT);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(INPUT_BD));
        ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        return sp;
    }

    private JButton editorBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif",Font.BOLD,11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(6,12,6,12));
        return b;
    }

    private JPanel rowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
        p.setBackground(BG); p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JLabel fieldLabel(String text) {
        String t = text.length()>22 ? text.substring(0,20)+"..." : text;
        JLabel l = new JLabel(t);
        l.setForeground(TEXT_DIM); l.setFont(new Font("SansSerif",Font.PLAIN,10));
        l.setPreferredSize(new Dimension(130,20));
        return l;
    }
}