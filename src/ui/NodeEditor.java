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
    private Color     pinRingColor = new Color(255, 210, 0); // default yellow

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
        // Make scrollbar translucent and auto-hide
        JScrollBar vsb = scroll.getVerticalScrollBar();
        vsb.setOpaque(false);
        vsb.setPreferredSize(new Dimension(6, 0));
        vsb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() {
                thumbColor = new Color(80,80,110,140);
                trackColor = new Color(0,0,0,0);
            }
            protected JButton createDecreaseButton(int o){ return zeroBtn(); }
            protected JButton createIncreaseButton(int o){ return zeroBtn(); }
            private JButton zeroBtn(){
                JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0));
                b.setBorderPainted(false); return b;
            }
        });
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Fade scrollbar after 1.5s of no scroll
        javax.swing.Timer[] fadeTimer = {null};
        vsb.addAdjustmentListener(ae -> {
            vsb.setPreferredSize(new Dimension(6,0));
            if (fadeTimer[0]!=null) fadeTimer[0].stop();
            fadeTimer[0] = new javax.swing.Timer(1500, e2 -> {
                vsb.setPreferredSize(new Dimension(0,0));
                scroll.revalidate();
            });
            fadeTimer[0].setRepeats(false);
            fadeTimer[0].start();
            scroll.revalidate();
        });
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
            case KEYBOARD:     buildKeyboard();     break;
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

        // Pin ring color picker
        JPanel ringRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        ringRow.setBackground(BG); ringRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel ringLbl = new JLabel("Pin ring color:");
        ringLbl.setForeground(TEXT_DIM); ringLbl.setFont(new Font("SansSerif",Font.PLAIN,10));
        JButton ringBtn = new JButton();
        ringBtn.setBackground(pinRingColor);
        ringBtn.setPreferredSize(new Dimension(28,18));
        ringBtn.setBorder(BorderFactory.createLineBorder(new Color(80,80,100),1));
        ringBtn.setOpaque(true); ringBtn.setFocusPainted(false);
        ringBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ringBtn.addActionListener(e -> {
            Color[] presets = {
                new Color(255,210,0),   // Yellow
                new Color(255,80,80),   // Red
                new Color(80,200,80),   // Green
                new Color(80,140,255),  // Blue
                new Color(255,140,0),   // Orange
                new Color(200,80,255),  // Purple
                new Color(0,220,220),   // Cyan
                Color.WHITE,
                new Color(180,180,180)  // Gray
            };
            JDialog picker = new JDialog((java.awt.Frame)null, "Pin Ring Color", true);
            picker.setUndecorated(true);
            picker.getContentPane().setBackground(new Color(28,28,38));
            JPanel grid = new JPanel(new GridLayout(1,9,4,0));
            grid.setBackground(new Color(28,28,38)); grid.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            for (Color col : presets) {
                JButton swatch = new JButton();
                swatch.setPreferredSize(new Dimension(22,22));
                swatch.setBackground(col); swatch.setOpaque(true);
                swatch.setBorderPainted(false); swatch.setFocusPainted(false);
                swatch.setBorder(BorderFactory.createLineBorder(new Color(80,80,100),1));
                swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                swatch.addActionListener(ae -> { pinRingColor=col; ringBtn.setBackground(col); picker.dispose(); });
                grid.add(swatch);
            }
            picker.add(grid); picker.pack();
            picker.setLocationRelativeTo(ringBtn); picker.setVisible(true);
        });
        ringRow.add(ringLbl); ringRow.add(ringBtn);
        content.add(ringRow);

        // Smart Pin button — pick points by clicking on screen
        JButton smartPinBtn = editorBtn("⊕  Smart Pin — pick points on screen", new Color(50,100,180));
        smartPinBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel pinRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        pinRow.setBackground(BG); pinRow.setAlignmentX(LEFT_ALIGNMENT);
        pinRow.add(smartPinBtn);
        content.add(pinRow);
        // Will be wired after tm is created
        final DefaultTableModel[] tmRef = {null};

        // Columns: # X Y Clicks Sub-s(ms) After-s(ms) — store ms internally, display as seconds
        String[] cols = {"#","X","Y","Clicks","Sub-delay","After-delay"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return c==1||c==2||c==3; } // X,Y,Clicks editable; delays via popup
            public Object getValueAt(int r, int c) {
                Object v = super.getValueAt(r,c);
                // Display columns 4,5 as seconds with 3 decimal places
                if ((c==4||c==5) && v instanceof Number) {
                    long ms = ((Number)v).longValue();
                    return String.format("%.3f s", ms/1000.0);
                }
                return v;
            }
        };
        java.util.List<int[]> pts = getPointsField();
        // Disable listener while populating to avoid premature sync
        javax.swing.event.TableModelListener[] listeners = new javax.swing.event.TableModelListener[0];
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            tm.addRow(new Object[]{i+1, p[0], p[1], p[2], (long)p[3], (long)p[4]});
        }

        JTable tbl = new JTable(tm);
        tbl.setFont(new Font("SansSerif",Font.PLAIN,11)); tbl.setRowHeight(22);
        tbl.setBackground(new Color(32,32,44)); tbl.setForeground(new Color(220,220,230));
        tbl.setGridColor(new Color(45,45,60));
        tbl.setSelectionBackground(new Color(50,80,140)); tbl.setSelectionForeground(Color.WHITE);
        tbl.getTableHeader().setBackground(new Color(35,35,50));
        tbl.getTableHeader().setForeground(new Color(120,120,150));
        tbl.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,9));
        int[] cw = {18,44,44,38,65,65};
        for (int i=0;i<cw.length;i++) tbl.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);

        // Simple renderer for delay columns — shows value in blue tint
        javax.swing.table.TableCellRenderer delayRenderer = new javax.swing.table.DefaultTableCellRenderer() {
            public java.awt.Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t,val,sel,foc,row,col);
                lbl.setBackground(sel ? new Color(50,80,140) : new Color(32,32,44));
                lbl.setForeground(new Color(160,200,255));
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return lbl;
            }
        };
        tbl.getColumnModel().getColumn(4).setCellRenderer(delayRenderer);
        tbl.getColumnModel().getColumn(5).setCellRenderer(delayRenderer);
        tm.addTableModelListener(e -> syncPointsToNode(tm));
        tmRef[0] = tm;

        // Single-click on Sub-s or After-s column → open interval picker popup
        tbl.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (e.getClickCount()<1) return;
                int col = tbl.columnAtPoint(e.getPoint());
                int row = tbl.rowAtPoint(e.getPoint());
                if (row<0||(col!=4&&col!=5)) return;
                Object cur = tm.getValueAt(row, col);
                long currentMs = 100;
                try {
                    // stored as Long internally
                    if (cur instanceof Long) currentMs = (Long)cur;
                    else if (cur instanceof Number) currentMs = ((Number)cur).longValue();
                    else { // displayed as "0.100 s" string
                        String s = cur.toString().replace(" s","").trim();
                        currentMs = (long)(Double.parseDouble(s)*1000);
                    }
                } catch(Exception ignored){}
                String colName = col==4 ? "Sub-click delay" : "Delay after click";
                long result = showIntervalPicker(colName, currentMs);
                if (result >= 0) {
                    tm.setValueAt(result, row, col);
                    syncPointsToNode(tm);
                }
            }
        });

        // Sync stores ms as Long — override syncPointsToNode to handle Long
        smartPinBtn.addActionListener(e -> startSmartPinSession(tmRef[0]));

        JScrollPane tblScroll = new JScrollPane(tbl);
        tblScroll.setPreferredSize(new Dimension(240,110));
        tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));
        tblScroll.setBorder(BorderFactory.createLineBorder(new Color(50,50,65)));
        tblScroll.getViewport().setBackground(new Color(32,32,44));
        tblScroll.setAlignmentX(LEFT_ALIGNMENT);
        tblScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Translucent thin scrollbar
        JScrollBar vsb = tblScroll.getVerticalScrollBar();
        vsb.setPreferredSize(new Dimension(5,0));
        vsb.setOpaque(false);
        vsb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            protected void configureScrollBarColors(){ thumbColor=new Color(100,100,160,160); trackColor=new Color(0,0,0,0); }
            protected JButton createDecreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setBorderPainted(false); return b; }
            protected JButton createIncreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setBorderPainted(false); return b; }
        });
        // Fade scrollbar after idle
        javax.swing.Timer[] tblFade = {null};
        vsb.addAdjustmentListener(ae -> {
            vsb.setPreferredSize(new Dimension(5,0));
            if (tblFade[0]!=null) tblFade[0].stop();
            tblFade[0] = new javax.swing.Timer(1500, ev -> {
                vsb.setPreferredSize(new Dimension(0,0));
                tblScroll.revalidate();
            });
            tblFade[0].setRepeats(false); tblFade[0].start();
            tblScroll.revalidate();
        });

        content.add(Box.createVerticalStrut(4));
        content.add(tblScroll);

        JPanel tblBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,4,4));
        tblBtns.setBackground(BG); tblBtns.setAlignmentX(LEFT_ALIGNMENT);
        JButton addBtn = editorBtn("+ Add",   new Color(50,100,160));
        JButton remBtn = editorBtn("- Remove",new Color(130,40,40));
        JButton clrBtn = editorBtn("Clear",   new Color(55,55,75));

        addBtn.addActionListener(e -> {
            Point mouse = MouseInfo.getPointerInfo().getLocation();
            tm.addRow(new Object[]{tm.getRowCount()+1, mouse.x, mouse.y, 1, 100L, 100L});
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
    //  KEYBOARD
    // =========================================================
    private void buildKeyboard() {
        addSection("Mode");

        String[] modes = {"Type Text", "Hotkey Combo", "Single Key", "Record"};
        int currentMode = getIntField("mode", 0);
        addCombo("Action mode", modes, currentMode, val -> {
            setIntField("mode", val); rebuild();
        });

        addLabeledSpinner("Repeat count",     getIntField("repeatCount",1),    1, 999,   val -> setIntField("repeatCount", val));
        addLabeledSpinner("Repeat delay (ms)", getIntField("repeatDelayMs",100),0, 10000, val -> setIntField("repeatDelayMs", val));

        if (currentMode == 0) {
            // ── Type Text mode ────────────────────────────────
            addSection("Text to Type");

            JTextArea textArea = new JTextArea(getStrField("typeText",""), 3, 20);
            textArea.setFont(new Font("Monospaced",Font.PLAIN,12));
            textArea.setBackground(new Color(35,35,50)); textArea.setForeground(new Color(220,220,230));
            textArea.setCaretColor(new Color(220,220,230));
            textArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60,60,85)),
                BorderFactory.createEmptyBorder(4,6,4,6)));
            textArea.setLineWrap(true);
            textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                public void insertUpdate(javax.swing.event.DocumentEvent e)  { setStrField("typeText", textArea.getText()); }
                public void removeUpdate(javax.swing.event.DocumentEvent e)  { setStrField("typeText", textArea.getText()); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { setStrField("typeText", textArea.getText()); }
            });
            JScrollPane tsp = new JScrollPane(textArea);
            tsp.setAlignmentX(LEFT_ALIGNMENT);
            tsp.setMaximumSize(new Dimension(240,80));
            tsp.setBorder(null);
            content.add(Box.createVerticalStrut(4));
            content.add(tsp);

            addLabeledSpinner("Char delay (ms)", getIntField("charDelayMs",50), 0, 1000, val -> setIntField("charDelayMs", val));

            addSection("Special Keys");
            addInfo("Click to append to text");
            buildSpecialKeyGrid(textArea);

        } else if (currentMode == 1) {
            // ── Hotkey Combo mode ─────────────────────────────
            addSection("Hotkey Combination");
            addInfo("e.g.  ctrl+c   ctrl+shift+t   alt+f4");

            JTextField hotkeyField = new JTextField(getStrField("hotkeyCombo",""), 15);
            hotkeyField.setFont(new Font("Monospaced",Font.PLAIN,13));
            hotkeyField.setBackground(new Color(35,35,50)); hotkeyField.setForeground(new Color(220,220,230));
            hotkeyField.setCaretColor(new Color(220,220,230));
            hotkeyField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60,60,85)),
                BorderFactory.createEmptyBorder(4,8,4,8)));
            hotkeyField.setHorizontalAlignment(JTextField.CENTER);
            hotkeyField.addActionListener(e -> setStrField("hotkeyCombo", hotkeyField.getText()));
            hotkeyField.addFocusListener(new FocusAdapter(){ public void focusLost(FocusEvent e){ setStrField("hotkeyCombo", hotkeyField.getText()); }});
            JPanel hfRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));
            hfRow.setBackground(BG); hfRow.setAlignmentX(LEFT_ALIGNMENT);
            hfRow.add(hotkeyField);
            content.add(hfRow);

            addSection("Quick Combos");
            String[][] combos = {
                {"Ctrl+C","ctrl+c"}, {"Ctrl+V","ctrl+v"}, {"Ctrl+X","ctrl+x"},
                {"Ctrl+Z","ctrl+z"}, {"Ctrl+A","ctrl+a"}, {"Ctrl+S","ctrl+s"},
                {"Alt+F4","alt+f4"}, {"Ctrl+W","ctrl+w"}, {"Ctrl+T","ctrl+t"},
                {"Alt+Tab","alt+tab"}, {"Cmd+Tab","meta+tab"}, {"Cmd+Space","meta+space"}
            };
            buildComboGrid(combos, hotkeyField);

        } else if (currentMode == 2) {
            // ── Single Key mode ───────────────────────────────
            addSection("Key to Press");
            String[] keyNames = {
                "ENTER","ESCAPE","TAB","SPACE","BACKSPACE","DELETE",
                "UP","DOWN","LEFT","RIGHT","HOME","END","PAGEUP","PAGEDOWN",
                "F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"
            };
            String current = getStrField("singleKey","ENTER");
            addCombo("Key", keyNames, java.util.Arrays.asList(keyNames).indexOf(current),
                val -> setStrField("singleKey", keyNames[Math.max(0,val)]));

        } else {
            // ── Record mode ───────────────────────────────────
            addSection("Record Keystrokes");
            addInfo("Click Record, type on keyboard, click Stop");

            // Show currently recorded text
            String recorded = getStrField("typeText","");
            JLabel recPreview = new JLabel(recorded.isEmpty() ? "(nothing recorded yet)" : recorded);
            recPreview.setFont(new Font("Monospaced",Font.PLAIN,11));
            recPreview.setForeground(recorded.isEmpty() ? new Color(80,80,100) : new Color(180,220,255));
            recPreview.setBorder(new EmptyBorder(4,8,4,8));
            recPreview.setAlignmentX(LEFT_ALIGNMENT);
            content.add(recPreview);

            // Record / Stop buttons
            JPanel recBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
            recBtns.setBackground(BG); recBtns.setAlignmentX(LEFT_ALIGNMENT);
            JButton recBtn  = editorBtn("⏺  Record", new Color(200,40,40));
            JButton stopRecBtn = editorBtn("⏹  Stop", new Color(60,60,80));
            JButton clearRecBtn = editorBtn("Clear", new Color(60,60,80));
            stopRecBtn.setEnabled(false);

            // StringBuilder to accumulate recorded keys
            StringBuilder recorded_sb = new StringBuilder(recorded);
            boolean[] isRecording = {false};

            // Use jNativeHook to capture global keystrokes while recording
            com.github.kwhat.jnativehook.keyboard.NativeKeyListener[] recListener = {null};

            Runnable stopRec = () -> {
                isRecording[0] = false;
                recBtn.setEnabled(true);
                stopRecBtn.setEnabled(false);
                recBtn.setText("⏺  Record");
                if (recListener[0] != null) {
                    try { com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(recListener[0]); }
                    catch (Exception ignored) {}
                    recListener[0] = null;
                }
                setStrField("typeText", recorded_sb.toString());
                // Switch mode to Type Text so it actually uses the recorded text
                setIntField("mode", 0);
                SwingUtilities.invokeLater(this::rebuild);
            };

            recBtn.addActionListener(e -> {
                isRecording[0] = true;
                recBtn.setEnabled(false);
                stopRecBtn.setEnabled(true);
                recBtn.setText("⏺  Recording...");
                recorded_sb.setLength(0);
                recPreview.setText("(recording...)");
                recPreview.setForeground(new Color(255,80,80));

                recListener[0] = new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
                    public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent ev) {
                        if (!isRecording[0]) return;
                        int code = ev.getKeyCode();
                        String keyText = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code);
                        // Map to bracket tag or char
                        String append = mapNativeKey(code, ev.getModifiers());
                        if (append != null) {
                            recorded_sb.append(append);
                            SwingUtilities.invokeLater(() -> {
                                recPreview.setText(recorded_sb.toString());
                                recPreview.setForeground(new Color(180,220,255));
                            });
                        }
                    }
                    public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent ev) {}
                    public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent ev) {}
                };
                try { com.github.kwhat.jnativehook.GlobalScreen.addNativeKeyListener(recListener[0]); }
                catch (Exception ex) { ex.printStackTrace(); }
            });

            stopRecBtn.addActionListener(e -> stopRec.run());
            clearRecBtn.addActionListener(e -> {
                recorded_sb.setLength(0);
                setStrField("typeText","");
                recPreview.setText("(nothing recorded yet)");
                recPreview.setForeground(new Color(80,80,100));
            });

            recBtns.add(recBtn); recBtns.add(stopRecBtn); recBtns.add(clearRecBtn);
            content.add(recBtns);

            addLabeledSpinner("Char delay (ms)", getIntField("charDelayMs",50), 0, 1000, val -> setIntField("charDelayMs", val));
        }
    }

    /** Map a NativeKeyEvent code to a string to insert into typeText */
    private String mapNativeKey(int code, int mods) {
        boolean ctrl  = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.CTRL_MASK)  != 0;
        boolean alt   = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.ALT_MASK)   != 0;
        boolean shift = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.SHIFT_MASK) != 0;
        boolean meta  = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.META_MASK)  != 0;

        // Modifier-only keys — skip by checking key text
        String keyText = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code);
        if (keyText.equals("Shift") || keyText.equals("Ctrl") || keyText.equals("Alt") ||
            keyText.equals("Meta") || keyText.equals("Caps Lock") ||
            keyText.startsWith("Unknown")) return null;

        // Build modifier prefix for hotkey combos
        if (ctrl || alt || meta) {
            StringBuilder combo = new StringBuilder();
            if (ctrl)  combo.append("ctrl+");
            if (alt)   combo.append("alt+");
            if (meta)  combo.append("cmd+");
            if (shift) combo.append("shift+");
            String keyName = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code).toLowerCase();
            return "[COMBO:" + combo + keyName + "]";
        }

        // Special keys
        switch (code) {
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ENTER:     return "\n";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_TAB:       return "\t";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ESCAPE:    return "[ESC]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_BACKSPACE: return "[BACKSPACE]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_DELETE:    return "[DELETE]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_UP:        return "[UP]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_DOWN:      return "[DOWN]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_LEFT:      return "[LEFT]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_RIGHT:     return "[RIGHT]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_HOME:      return "[HOME]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_END:       return "[END]";
            case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_SPACE:     return " ";
            default:
                // Regular printable char
                char ch = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code).charAt(0);
                if (shift && Character.isLetter(ch)) return String.valueOf(Character.toUpperCase(ch));
                if (Character.isLetterOrDigit(ch) || ch == ' ') return String.valueOf(ch);
                // Punctuation
                String text = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code);
                if (text.length() == 1) return text;
                return null;
        }
    }

    private void buildSpecialKeyGrid(JTextArea target) {
        String[][] keys = {
            {"Enter","\n"}, {"Tab","\t"}, {"Esc","[ESC]"},
            {"Up","[UP]"}, {"Down","[DOWN]"}, {"Left","[LEFT]"}, {"Right","[RIGHT]"},
            {"Del","[DEL]"}, {"Home","[HOME]"}, {"End","[END]"}
        };
        JPanel grid = new JPanel(new java.awt.GridLayout(0,4,3,3));
        grid.setBackground(BG); grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setBorder(new EmptyBorder(4,4,4,4));
        for (String[] k : keys) {
            JButton btn = new JButton(k[0]);
            btn.setFont(new Font("SansSerif",Font.PLAIN,10));
            btn.setBackground(new Color(40,40,58)); btn.setForeground(new Color(180,220,255));
            btn.setBorder(BorderFactory.createLineBorder(new Color(60,80,120),1));
            btn.setFocusPainted(false); btn.setOpaque(true);
            final String insertVal = k[1];
            btn.addActionListener(e -> {
                // For special non-text keys, append a placeholder tag
                String toInsert = insertVal;
                if (!insertVal.equals("\n") && !insertVal.equals("\t")) {
                    toInsert = insertVal; // keep [ESC] [UP] etc as tags
                }
                target.insert(toInsert, target.getCaretPosition());
                setStrField("typeText", target.getText());
            });
            grid.add(btn);
        }
        content.add(grid);
    }

    private void buildComboGrid(String[][] combos, JTextField field) {
        JPanel grid = new JPanel(new java.awt.GridLayout(0,3,3,3));
        grid.setBackground(BG); grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setBorder(new EmptyBorder(4,4,4,4));
        for (String[] k : combos) {
            JButton btn = new JButton(k[0]);
            btn.setFont(new Font("SansSerif",Font.PLAIN,10));
            btn.setBackground(new Color(40,40,58)); btn.setForeground(new Color(180,220,255));
            btn.setBorder(BorderFactory.createLineBorder(new Color(60,80,120),1));
            btn.setFocusPainted(false); btn.setOpaque(true);
            btn.addActionListener(e -> {
                field.setText(k[1]);
                setStrField("hotkeyCombo", k[1]);
            });
            grid.add(btn);
        }
        content.add(grid);
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
            java.util.List<int[]> pts = buildPointsList(tm);
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
            // Spawn at center of screen so user can always see and drag it
            Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
            int px = scr.width/2;
            int py = scr.height/2;
            int row = tm.getRowCount();
            tm.addRow(new Object[]{row+1, px, py, 1, 100, 100});
            syncPointsToNode(tm);
            EditorPin ep = new EditorPin(px, py, row, tm);
            pins.add(ep);
            ep.onRemoved = () -> {
                pins.remove(ep);
                for (int ri=0;ri<pins.size();ri++) pins.get(ri).rowIndex=ri;
                pinCountLbl.setText("Pins: "+pins.size());
            };
            SwingUtilities.invokeLater(() -> {
                ep.show();
                ep.win.toFront();
            });
            pinCountLbl.setText("Pins: "+pins.size());
        });
    }

    private void placeEditorPinWindow(JWindow win, int sx, int sy) {
        // Center the crosshair on the exact screen point
        win.setLocation(sx - win.getWidth()/2, sy - win.getHeight()/2);
    }



    // ── Floating pin — crosshair + colored ring + drag ─────────────
    private class EditorPin {
        JWindow win;
        int screenX, screenY, rowIndex;
        DefaultTableModel tm;
        Runnable onRemoved;
        static final int PSZ = 44; // window size, center = exact screen point

        EditorPin(int x, int y, int row, DefaultTableModel tm) {
            screenX=x; screenY=y; rowIndex=row; this.tm=tm; build();
        }

        void build() {
            win = new JWindow();
            win.setAlwaysOnTop(true);
            // Use near-transparent bg so window is visible but mouse events work
            win.setBackground(new Color(0,0,0,1));

            // Use a solid-bg panel so mouse events are captured reliably on macOS
            JPanel panel = new JPanel(null) {
                boolean hovered = false;
                {
                    // NOT opaque=false — tiny 1-alpha bg catches events on macOS
                    setBackground(new Color(0,0,0,1));
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    addMouseListener(new MouseAdapter(){
                        public void mouseEntered(MouseEvent e){ hovered=true; repaint(); }
                        public void mouseExited(MouseEvent e) { hovered=false; repaint(); }
                        public void mouseClicked(MouseEvent e){
                            if (e.getButton()==MouseEvent.BUTTON3) {
                                if (rowIndex<tm.getRowCount()) tm.removeRow(rowIndex);
                                for (int i=0;i<tm.getRowCount();i++) tm.setValueAt(i+1,i,0);
                                syncPointsToNode(tm); dispose();
                                if (onRemoved!=null) onRemoved.run();
                            }
                        }
                    });

                    // Drag: move the JWindow directly, keep it visible
                    int[] off = {0,0};
                    addMouseListener(new MouseAdapter(){
                        public void mousePressed(MouseEvent e){ off[0]=e.getX(); off[1]=e.getY(); }
                        public void mouseReleased(MouseEvent e){
                            // Sync final position to table
                            if (rowIndex<tm.getRowCount()) {
                                tm.setValueAt(screenX,rowIndex,1);
                                tm.setValueAt(screenY,rowIndex,2);
                                syncPointsToNode(tm);
                            }
                        }
                    });
                    addMouseMotionListener(new MouseMotionAdapter(){
                        public void mouseDragged(MouseEvent e){
                            Point loc = win.getLocationOnScreen();
                            int nx = loc.x + e.getX() - off[0];
                            int ny = loc.y + e.getY() - off[1];
                            win.setLocation(nx, ny);
                            // Update screen coords (center of window)
                            screenX = nx + PSZ/2;
                            screenY = ny + PSZ/2;
                            repaint();
                        }
                    });
                }

                protected void paintComponent(Graphics g) {
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx=getWidth()/2, cy=getHeight()/2;

                    // Ring — smaller size
                    int ringR = 10;
                    g2.setColor(pinRingColor);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx-ringR, cy-ringR, ringR*2, ringR*2);

                    // Crosshair — gap at center
                    g2.setColor(new Color(255,255,255,230));
                    g2.setStroke(new BasicStroke(1.5f));
                    int arm=8, gap=4;
                    g2.drawLine(cx-arm,cy, cx-gap,cy);
                    g2.drawLine(cx+gap,cy, cx+arm,cy);
                    g2.drawLine(cx,cy-arm, cx,cy-gap);
                    g2.drawLine(cx,cy+gap, cx,cy+arm);

                    // Red center dot
                    g2.setColor(new Color(255,50,50));
                    g2.fillOval(cx-3,cy-3,6,6);

                    // Number overlaid ON the crosshair — only when NOT hovered (idle state)
                    // Hidden while hovered so user can see exact position clearly
                    if (!hovered) {
                        String lbl=String.valueOf(rowIndex+1);
                        g2.setFont(new Font("SansSerif",Font.BOLD,9));
                        FontMetrics fm=g2.getFontMetrics();
                        int lw=fm.stringWidth(lbl);
                        // Draw number centered over the cross
                        g2.setColor(new Color(0,0,0,160));
                        g2.fillOval(cx-6,cy-6,12,12);
                        g2.setColor(Color.WHITE);
                        g2.drawString(lbl, cx-lw/2, cy+4);
                    }
                }
            };

            panel.setPreferredSize(new Dimension(PSZ,PSZ));
            win.setContentPane(panel);
            win.setSize(PSZ,PSZ);
            placeEditorPinWindow(win, screenX, screenY);
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
    // ── Interval picker popup — same spinners as click interval panel ──
    private long showIntervalPicker(String title, long currentMs) {
        int ivH=(int)(currentMs/3600000L), ivM=(int)((currentMs%3600000L)/60000L);
        int ivS=(int)((currentMs%60000L)/1000L), ivT=(int)((currentMs%1000L)/100L);
        int ivHu=(int)((currentMs%100L)/10L), ivTh=(int)(currentMs%10L);

        JDialog dlg = new JDialog((java.awt.Frame)null, title, true);
        dlg.setUndecorated(false);
        dlg.getContentPane().setBackground(new Color(28,28,38));
        dlg.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(22,22,30));
        top.setBorder(BorderFactory.createEmptyBorder(10,12,8,12));
        JLabel lbl = new JLabel(title);
        lbl.setForeground(new Color(80,140,255)); lbl.setFont(new Font("SansSerif",Font.BOLD,12));
        top.add(lbl, BorderLayout.WEST);
        dlg.add(top, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0,4));
        body.setBackground(new Color(28,28,38));
        body.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));

        String[] units = {"H","Min","Sec","1/10","1/100","1/1000"};
        JPanel hdr = new JPanel(new GridLayout(1,6,4,0));
        hdr.setBackground(new Color(28,28,38));
        for (String u : units) {
            JLabel ul = new JLabel(u, SwingConstants.CENTER);
            ul.setFont(new Font("SansSerif",Font.PLAIN,9));
            ul.setForeground(new Color(120,120,150));
            hdr.add(ul);
        }

        JPanel spRow = new JPanel(new GridLayout(1,6,4,0));
        spRow.setBackground(new Color(28,28,38));
        JSpinner[] sps = {
            mkSp(ivH,0,23), mkSp(ivM,0,59), mkSp(ivS,0,59),
            mkSp(ivT,0,9),  mkSp(ivHu,0,9), mkSp(ivTh,0,9)
        };
        for (JSpinner sp : sps) spRow.add(sp);

        // Live preview
        JLabel preview = new JLabel("", SwingConstants.CENTER);
        preview.setForeground(new Color(80,200,120));
        preview.setFont(new Font("Monospaced",Font.BOLD,11));
        Runnable updatePreview = () -> {
            long h=((Number)sps[0].getValue()).longValue(), m=((Number)sps[1].getValue()).longValue();
            long s=((Number)sps[2].getValue()).longValue(), t=((Number)sps[3].getValue()).longValue();
            long hu=((Number)sps[4].getValue()).longValue(), th=((Number)sps[5].getValue()).longValue();
            long ms = Math.max(0, h*3600000L+m*60000L+s*1000L+t*100L+hu*10L+th);
            preview.setText(String.format("= %.3f seconds", ms/1000.0));
        };
        for (JSpinner sp : sps) sp.addChangeListener(e -> updatePreview.run());
        updatePreview.run();

        body.add(hdr, BorderLayout.NORTH);
        body.add(spRow, BorderLayout.CENTER);
        body.add(preview, BorderLayout.SOUTH);
        dlg.add(body, BorderLayout.CENTER);

        long[] result = {-1};
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,8));
        btnRow.setBackground(new Color(22,22,30));
        JButton ok = editorBtn("OK", new Color(50,100,160));
        JButton cancel = editorBtn("Cancel", new Color(55,55,75));
        ok.addActionListener(e -> {
            long h=((Number)sps[0].getValue()).longValue(), m=((Number)sps[1].getValue()).longValue();
            long s=((Number)sps[2].getValue()).longValue(), t=((Number)sps[3].getValue()).longValue();
            long hu=((Number)sps[4].getValue()).longValue(), th=((Number)sps[5].getValue()).longValue();
            result[0] = Math.max(0, h*3600000L+m*60000L+s*1000L+t*100L+hu*10L+th);
            dlg.dispose();
        });
        cancel.addActionListener(e -> dlg.dispose());
        btnRow.add(cancel); btnRow.add(ok);
        dlg.add(btnRow, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
        return result[0];
    }

    private JSpinner mkSp(int val, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val,min,max,1));
        sp.setPreferredSize(new Dimension(52,28));
        sp.setFont(new Font("SansSerif",Font.PLAIN,12));
        sp.setBackground(new Color(35,35,50));
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor)sp.getEditor();
        ed.getTextField().setBackground(new Color(35,35,50));
        ed.getTextField().setForeground(new Color(220,220,230));
        ed.getTextField().setCaretColor(new Color(220,220,230));
        ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(new Color(60,60,85)));
        return sp;
    }

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

    /** Parse a table cell value to int — handles Long, Integer, "0.100 s" formatted strings */
    private static int cellToInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer)val;
        if (val instanceof Long)    return (int)(long)(Long)val;
        if (val instanceof Number)  return ((Number)val).intValue();
        String s = val.toString().trim();
        // Handle "0.100 s" → parse as seconds, convert to ms
        if (s.endsWith(" s") || s.endsWith("s")) {
            try { return (int)(Double.parseDouble(s.replace(" s","").replace("s","").trim()) * 1000); }
            catch (Exception ignored) {}
        }
        // Try plain integer
        try { return Integer.parseInt(s); }
        catch (Exception ignored) {}
        // Try double
        try { return (int)Double.parseDouble(s); }
        catch (Exception ignored) {}
        return 0;
    }

    private void syncPointsToNode(DefaultTableModel tm) {
        if (currentNode == null) return;
        java.util.List<int[]> pts = buildPointsList(tm);
        try { getField(currentNode, "points").set(currentNode, pts); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void syncPointsToNode(DefaultTableModel tm, nodes.BaseNode node) {
        if (node == null) return;
        java.util.List<int[]> pts = buildPointsList(tm);
        try { getField(node, "points").set(node, pts); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private java.util.List<int[]> buildPointsList(DefaultTableModel tm) {
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        for (int i=0;i<tm.getRowCount();i++) {
            try {
                pts.add(new int[]{
                    cellToInt(tm.getValueAt(i,1)),
                    cellToInt(tm.getValueAt(i,2)),
                    cellToInt(tm.getValueAt(i,3)),
                    cellToInt(tm.getValueAt(i,4)),
                    cellToInt(tm.getValueAt(i,5))
                });
            } catch (Exception e) { e.printStackTrace(); }
        }
        return pts;
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