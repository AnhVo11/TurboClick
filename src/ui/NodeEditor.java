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
            case IMAGE:        buildImage();        break;
            case WATCH_CASE:   buildWatchCase();    break;
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
    //  SHARED CAPTURE SECTION
    // =========================================================
    private void buildCaptureSection(String templateField, String zoneField) {
        templatePreview = new JLabel("No image captured");
        templatePreview.setForeground(TEXT_DIM);
        templatePreview.setFont(new Font("SansSerif",Font.ITALIC,10));
        templatePreview.setHorizontalAlignment(SwingConstants.CENTER);
        templatePreview.setPreferredSize(new Dimension(220,60));
        templatePreview.setMaximumSize(new Dimension(240,60));
        templatePreview.setAlignmentX(LEFT_ALIGNMENT);
        templatePreview.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C), BorderFactory.createEmptyBorder(4,4,4,4)));
        templatePreview.setBackground(new Color(20,20,30)); templatePreview.setOpaque(true);
        try {
            Object existing = getObjField(templateField);
            if (existing instanceof java.awt.image.BufferedImage) updatePreview((java.awt.image.BufferedImage)existing);
        } catch(Exception ignored){}
        JPanel previewRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));
        previewRow.setBackground(BG); previewRow.setAlignmentX(LEFT_ALIGNMENT);
        previewRow.add(templatePreview);
        content.add(previewRow);

        JButton captureBtn  = editorBtn("Capture", new Color(50,100,170));
        JButton openFileBtn = editorBtn("Open File", new Color(60,100,60));
        JPanel capRow = new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
        capRow.setBackground(BG); capRow.setAlignmentX(LEFT_ALIGNMENT);
        capRow.add(captureBtn); capRow.add(openFileBtn);
        content.add(capRow);

        captureBtn.addActionListener(e -> startCaptureToField(templateField, zoneField));
        openFileBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images","png","jpg","jpeg","bmp","gif"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(fc.getSelectedFile());
                    if (img != null) {
                        setObjField(templateField, img);
                        updatePreview(img);
                    }
                } catch(Exception ex){ ex.printStackTrace(); }
            }
        });

        templatePreview.setTransferHandler(new TransferHandler(){
            public boolean canImport(TransferSupport ts){
                return ts.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }
            public boolean importData(TransferSupport ts){
                try {
                    java.util.List<java.io.File> files=(java.util.List<java.io.File>)
                        ts.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(files.get(0));
                        if (img!=null){ setObjField(templateField, img); updatePreview(img); return true; }
                    }
                } catch(Exception ignored){}
                return false;
            }
        });
        templatePreview.setToolTipText("Drag & drop an image here");

        if (zoneField != null) {
            addSection("Watch Zone");
            sameAsCaptureCb = new JCheckBox("Use same area as captured template");
            sameAsCaptureCb.setBackground(BG); sameAsCaptureCb.setForeground(TEXT);
            sameAsCaptureCb.setFont(new Font("SansSerif",Font.PLAIN,11));
            sameAsCaptureCb.setBorder(new EmptyBorder(2,8,2,0));
            sameAsCaptureCb.setAlignmentX(LEFT_ALIGNMENT);
            sameAsCaptureCb.setSelected(getBoolField("sameAsCapture",false));
            content.add(sameAsCaptureCb);
            zoneStatusLabel = new JLabel();
            zoneStatusLabel.setFont(new Font("SansSerif",Font.ITALIC,10));
            zoneStatusLabel.setAlignmentX(LEFT_ALIGNMENT);
            zoneStatusLabel.setBorder(new EmptyBorder(0,8,0,0));
            refreshZoneStatus(); content.add(zoneStatusLabel);

            // Context-aware zone button — Set Zone (green) or Edit Zone (yellow)
            boolean zoneSet = getObjField("watchZone") instanceof java.awt.Rectangle;
            JLabel[] zoneBtnRef = {null};
            JLabel zoneBtn = makeZoneActionBtn(zoneSet, zoneBtnRef);
            zoneBtnRef[0] = zoneBtn;
            JPanel zoneRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
            zoneRow.setBackground(BG); zoneRow.setAlignmentX(LEFT_ALIGNMENT);
            zoneRow.add(zoneBtn); content.add(zoneRow);

            sameAsCaptureCb.addActionListener(e -> {
                boolean same = sameAsCaptureCb.isSelected();
                setBoolField("sameAsCapture",same);
                zoneBtn.setEnabled(!same);
                if (same) applySameAsCapture();
                refreshZoneStatus();
            });
            zoneBtn.setEnabled(!getBoolField("sameAsCapture",false));
            zoneBtn.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){
                    if (!zoneBtn.isEnabled()) return;
                    Object cur = getObjField("watchZone");
                    if (cur instanceof java.awt.Rectangle) {
                        // Edit mode — open drag-resize overlay
                        java.awt.Rectangle[] ref = { (java.awt.Rectangle)cur };
                        Window win = SwingUtilities.getWindowAncestor(NodeEditor.this);
                        showRectEditOverlay(ref, win, () -> {
                            setObjField("watchZone", ref[0]);
                            refreshZoneStatus();
                            updateZoneBtnStyle(zoneBtn, true);
                        });
                    } else {
                        startCapture(false);
                    }
                }
            });
        }
    }

    private void startCaptureToField(String templateField, String zoneField) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win!=null) win.setVisible(false);
        Timer delay = new Timer(300, ev -> {
            showOverlayToField(templateField, zoneField, win);
        });
        delay.setRepeats(false); delay.start();
    }

    private void showOverlayToField(String templateField, String zoneField, Window parentWindow) {
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0,0,0,0));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        overlay.setBounds(0,0,screen.width,screen.height);
        int[] drag={0,0,0,0}; boolean[] dragging={false};
        JPanel glass = new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0,0,0,80)); g2.fillRect(0,0,getWidth(),getHeight());
                String msg="Drag to capture image   [ESC = cancel]";
                g2.setFont(new Font("SansSerif",Font.BOLD,16));
                FontMetrics fm=g2.getFontMetrics(); int tw=fm.stringWidth(msg);
                g2.setColor(new Color(0,0,0,160));
                g2.fillRoundRect((getWidth()-tw)/2-12,18,tw+24,34,10,10);
                g2.setColor(new Color(100,180,255)); g2.drawString(msg,(getWidth()-tw)/2,40);
                if (dragging[0]){
                    int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                    int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                    Composite old=g2.getComposite();
                    g2.setComposite(AlphaComposite.Clear); g2.fillRect(rx,ry,rw,rh);
                    g2.setComposite(old);
                    g2.setColor(new Color(100,180,255)); g2.setStroke(new BasicStroke(2)); g2.drawRect(rx,ry,rw,rh);
                    g2.setFont(new Font("Monospaced",Font.BOLD,11));
                    g2.drawString(rw+"x"+rh,rx+4,ry>20?ry-4:ry+rh+16);
                }
            }
        };
        glass.setOpaque(false); glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        glass.setFocusable(true);
        java.awt.KeyEventDispatcher kedF = e2 -> {
            if (e2.getID()==KeyEvent.KEY_PRESSED && e2.getKeyCode()==KeyEvent.VK_ESCAPE) {
                SwingUtilities.invokeLater(() -> {
                    overlay.dispose();
                    if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
                });
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(kedF);
        overlay.addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosed(java.awt.event.WindowEvent e){
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(kedF);
            }
        });
        glass.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){ drag[0]=e.getX(); drag[1]=e.getY(); dragging[0]=true; }
            public void mouseReleased(MouseEvent e){
                dragging[0]=false; overlay.dispose();
                int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                if (rw>4&&rh>4){
                    try {
                        java.awt.Rectangle rect=new java.awt.Rectangle(rx,ry,rw,rh);
                        java.awt.image.BufferedImage img=new java.awt.Robot().createScreenCapture(rect);
                        setObjField(templateField, img);
                        if (zoneField!=null){ setObjField(zoneField, rect); if(getBoolField("sameAsCapture",false)) applySameAsCapture(); }
                        else { setObjField("captureRect", rect); }
                        SwingUtilities.invokeLater(()->{ updatePreview(img); });
                    } catch(Exception ex){ ex.printStackTrace(); }
                }
                if (parentWindow!=null) SwingUtilities.invokeLater(()->{ parentWindow.setVisible(true); parentWindow.toFront(); });
                Timer t=new Timer(150, ev->rebuild()); t.setRepeats(false); t.start();
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){ drag[2]=e.getX(); drag[3]=e.getY(); glass.paintImmediately(0,0,glass.getWidth(),glass.getHeight()); }
        });
        overlay.setContentPane(glass); overlay.setVisible(true);
    }

    // =========================================================
    //  WATCH ZONE
    // =========================================================
    private void buildWatchZone() {
        addSection("Image Name");
        addLabeledField("Name", getStrField("imageName","Unnamed Image"), val -> setStrField("imageName", val));

        addSection("Capture Template");
        buildCaptureSection("template", "watchZone");

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
                            SwingUtilities.invokeLater(() -> {
                                refreshZoneStatus();
                                Timer rb = new Timer(200, ev2 -> rebuild());
                                rb.setRepeats(false); rb.start();
                            });
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                    if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
                });
                t.setRepeats(false); t.start();
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) { drag[2]=e.getX(); drag[3]=e.getY(); glass.paintImmediately(0,0,glass.getWidth(),glass.getHeight()); }
        });
        com.github.kwhat.jnativehook.keyboard.NativeKeyListener escO =
            new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
                public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){
                    if (e.getKeyCode()==com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ESCAPE)
                        SwingUtilities.invokeLater(() -> {
                            overlay.dispose();
                            if (parentWindow!=null) { parentWindow.setVisible(true); parentWindow.toFront(); }
                        });
                }
                public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
                public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
            };
        try { com.github.kwhat.jnativehook.GlobalScreen.addNativeKeyListener(escO); } catch(Exception ignored){}
        overlay.addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosed(java.awt.event.WindowEvent e){
                try { com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(escO); } catch(Exception ignored){}
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
                new Color(255,210,0), new Color(255,80,80), new Color(80,200,80),
                new Color(80,140,255), new Color(255,140,0), new Color(200,80,255),
                new Color(0,220,220), Color.WHITE, new Color(180,180,180)
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

        JButton smartPinBtn = editorBtn("⊕  Smart Pin — pick points on screen", new Color(50,100,180));
        smartPinBtn.setAlignmentX(LEFT_ALIGNMENT);
        JPanel pinRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        pinRow.setBackground(BG); pinRow.setAlignmentX(LEFT_ALIGNMENT);
        pinRow.add(smartPinBtn);
        content.add(pinRow);
        final DefaultTableModel[] tmRef = {null};

        String[] cols = {"#","X","Y","Clicks","Sub-delay","After-delay"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return c==1||c==2||c==3; }
            public Object getValueAt(int r, int c) {
                Object v = super.getValueAt(r,c);
                if ((c==4||c==5) && v instanceof Number) {
                    long ms = ((Number)v).longValue();
                    return String.format("%.3f s", ms/1000.0);
                }
                return v;
            }
        };
        java.util.List<int[]> pts = getPointsField();
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

        tbl.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (e.getClickCount()<1) return;
                int col = tbl.columnAtPoint(e.getPoint());
                int row = tbl.rowAtPoint(e.getPoint());
                if (row<0||(col!=4&&col!=5)) return;
                Object cur = tm.getValueAt(row, col);
                long currentMs = 100;
                try {
                    if (cur instanceof Long) currentMs = (Long)cur;
                    else if (cur instanceof Number) currentMs = ((Number)cur).longValue();
                    else { String s = cur.toString().replace(" s","").trim(); currentMs = (long)(Double.parseDouble(s)*1000); }
                } catch(Exception ignored){}
                String colName = col==4 ? "Sub-click delay" : "Delay after click";
                long result = showIntervalPicker(colName, currentMs);
                if (result >= 0) { tm.setValueAt(result, row, col); syncPointsToNode(tm); }
            }
        });

        smartPinBtn.addActionListener(e -> startSmartPinSession(tmRef[0]));

        JScrollPane tblScroll = new JScrollPane(tbl);
        tblScroll.setPreferredSize(new Dimension(240,110));
        tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));
        tblScroll.setBorder(BorderFactory.createLineBorder(new Color(50,50,65)));
        tblScroll.getViewport().setBackground(new Color(32,32,44));
        tblScroll.setAlignmentX(LEFT_ALIGNMENT);
        tblScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar vsb = tblScroll.getVerticalScrollBar();
        vsb.setPreferredSize(new Dimension(5,0)); vsb.setOpaque(false);
        vsb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            protected void configureScrollBarColors(){ thumbColor=new Color(100,100,160,160); trackColor=new Color(0,0,0,0); }
            protected JButton createDecreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setBorderPainted(false); return b; }
            protected JButton createIncreaseButton(int o){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setBorderPainted(false); return b; }
        });
        javax.swing.Timer[] tblFade = {null};
        vsb.addAdjustmentListener(ae -> {
            vsb.setPreferredSize(new Dimension(5,0));
            if (tblFade[0]!=null) tblFade[0].stop();
            tblFade[0] = new javax.swing.Timer(1500, ev -> { vsb.setPreferredSize(new Dimension(0,0)); tblScroll.revalidate(); });
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
            if (row>=0) { tm.removeRow(row); for (int i=0;i<tm.getRowCount();i++) tm.setValueAt(i+1,i,0); syncPointsToNode(tm); }
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
            addSection("Record Keystrokes");
            addInfo("Click Record, type on keyboard, click Stop");
            String recorded = getStrField("typeText","");
            JLabel recPreview = new JLabel(recorded.isEmpty() ? "(nothing recorded yet)" : recorded);
            recPreview.setFont(new Font("Monospaced",Font.PLAIN,11));
            recPreview.setForeground(recorded.isEmpty() ? new Color(80,80,100) : new Color(180,220,255));
            recPreview.setBorder(new EmptyBorder(4,8,4,8));
            recPreview.setAlignmentX(LEFT_ALIGNMENT);
            content.add(recPreview);

            JPanel recBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
            recBtns.setBackground(BG); recBtns.setAlignmentX(LEFT_ALIGNMENT);
            JButton recBtn  = editorBtn("\u23fa  Record", new Color(200,40,40));
            JButton stopRecBtn = editorBtn("\u23f9  Stop", new Color(60,60,80));
            JButton clearRecBtn = editorBtn("Clear", new Color(60,60,80));
            stopRecBtn.setEnabled(false);

            StringBuilder recorded_sb = new StringBuilder(recorded);
            boolean[] isRecording = {false};
            com.github.kwhat.jnativehook.keyboard.NativeKeyListener[] recListener = {null};

            Runnable stopRec = () -> {
                isRecording[0] = false;
                recBtn.setEnabled(true); stopRecBtn.setEnabled(false);
                recBtn.setText("\u23fa  Record");
                if (recListener[0] != null) {
                    try { com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(recListener[0]); }
                    catch (Exception ignored) {}
                    recListener[0] = null;
                }
                setStrField("typeText", recorded_sb.toString());
                setIntField("mode", 0);
                SwingUtilities.invokeLater(this::rebuild);
            };

            recBtn.addActionListener(e -> {
                isRecording[0] = true;
                recBtn.setEnabled(false); stopRecBtn.setEnabled(true);
                recBtn.setText("\u23fa  Recording...");
                recorded_sb.setLength(0);
                recPreview.setText("(recording...)");
                recPreview.setForeground(new Color(255,80,80));
                recListener[0] = new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
                    public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent ev) {
                        if (!isRecording[0]) return;
                        String append = mapNativeKey(ev.getKeyCode(), ev.getModifiers());
                        if (append != null) {
                            recorded_sb.append(append);
                            SwingUtilities.invokeLater(() -> { recPreview.setText(recorded_sb.toString()); recPreview.setForeground(new Color(180,220,255)); });
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
                recorded_sb.setLength(0); setStrField("typeText","");
                recPreview.setText("(nothing recorded yet)"); recPreview.setForeground(new Color(80,80,100));
            });
            recBtns.add(recBtn); recBtns.add(stopRecBtn); recBtns.add(clearRecBtn);
            content.add(recBtns);
            addLabeledSpinner("Char delay (ms)", getIntField("charDelayMs",50), 0, 1000, val -> setIntField("charDelayMs", val));
        }
    }

    private String mapNativeKey(int code, int mods) {
        boolean ctrl  = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.CTRL_MASK)  != 0;
        boolean alt   = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.ALT_MASK)   != 0;
        boolean shift = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.SHIFT_MASK) != 0;
        boolean meta  = (mods & com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.META_MASK)  != 0;
        String keyText = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code);
        if (keyText.equals("Shift") || keyText.equals("Ctrl") || keyText.equals("Alt") ||
            keyText.equals("Meta") || keyText.equals("Caps Lock") || keyText.startsWith("Unknown")) return null;
        if (ctrl || alt || meta) {
            StringBuilder combo = new StringBuilder();
            if (ctrl) combo.append("ctrl+"); if (alt) combo.append("alt+");
            if (meta) combo.append("cmd+"); if (shift) combo.append("shift+");
            String keyName = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code).toLowerCase();
            return "[COMBO:" + combo + keyName + "]";
        }
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
                char ch = com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.getKeyText(code).charAt(0);
                if (shift && Character.isLetter(ch)) return String.valueOf(Character.toUpperCase(ch));
                if (Character.isLetterOrDigit(ch) || ch == ' ') return String.valueOf(ch);
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
            btn.addActionListener(e -> { target.insert(insertVal, target.getCaretPosition()); setStrField("typeText", target.getText()); });
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
            btn.addActionListener(e -> { field.setText(k[1]); setStrField("hotkeyCombo", k[1]); });
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
    //  SMART PIN SESSION
    // =========================================================
    private void startSmartPinSession(DefaultTableModel tm) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) win.setVisible(false);

        final nodes.BaseNode sessionNode = currentNode;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        java.util.List<EditorPin> pins = new java.util.ArrayList<>();

        for (int i = 0; i < tm.getRowCount(); i++) {
            try {
                int px = Integer.parseInt(tm.getValueAt(i,1).toString());
                int py = Integer.parseInt(tm.getValueAt(i,2).toString());
                EditorPin ep = new EditorPin(px, py, i, tm);
                pins.add(ep); ep.show();
            } catch (Exception ignored) {}
        }

        JWindow smartBar = new JWindow();
        smartBar.setAlwaysOnTop(true);
        smartBar.setBackground(new Color(0,0,0,0));
        smartBar.setFocusableWindowState(true);

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,15,22,240)); g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(new Color(80,140,255,200)); g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER,10,7));

        JLabel titleLbl = new JLabel("Smart Pin");
        titleLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        titleLbl.setForeground(new Color(80,140,255));

        JLabel pinCountLbl = new JLabel("Pins: "+pins.size());
        pinCountLbl.setFont(new Font("SansSerif",Font.PLAIN,11));
        pinCountLbl.setForeground(new Color(120,120,150));

        JLabel coordsLbl = new JLabel("X: \u2500\u2500\u2500 Y: \u2500\u2500\u2500");
        coordsLbl.setFont(new Font("Monospaced",Font.BOLD,12));
        coordsLbl.setForeground(new Color(80,220,120));

        JButton addPinBtn = smartBarBtn("+ Pin", new Color(50,100,180));
        JButton doneBtn   = smartBarBtn("\u2713 Done", new Color(60,60,80));

        JLabel s1=new JLabel("|"); s1.setForeground(new Color(60,60,80));
        JLabel s2=new JLabel("|"); s2.setForeground(new Color(60,60,80));
        JLabel s3=new JLabel("|"); s3.setForeground(new Color(60,60,80));

        bar.add(titleLbl); bar.add(s1); bar.add(pinCountLbl);
        bar.add(s2); bar.add(coordsLbl); bar.add(s3); bar.add(addPinBtn); bar.add(doneBtn);

        int[] off={0,0};
        bar.addMouseListener(new MouseAdapter(){ public void mousePressed(MouseEvent e){ off[0]=e.getX(); off[1]=e.getY(); }});
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

        int[] lastMouse = {0,0};
        javax.swing.Timer coordTimer = new javax.swing.Timer(50, e -> {
            Point p = MouseInfo.getPointerInfo().getLocation();
            lastMouse[0]=p.x; lastMouse[1]=p.y;
            coordsLbl.setText("X: "+p.x+"  Y: "+p.y);
        });
        coordTimer.start();

        Runnable finish = () -> {
            coordTimer.stop();
            java.util.List<int[]> pts = buildPointsList(tm);
            try { getField(sessionNode, "points").set(sessionNode, pts); } catch (Exception e) { e.printStackTrace(); }
            for (EditorPin ep : pins) ep.dispose();
            try { smartBar.dispose(); } catch(Exception ignored) {}
            if (win!=null) SwingUtilities.invokeLater(() -> { win.setVisible(true); win.toFront(); });
            javax.swing.Timer t = new javax.swing.Timer(150, e -> { currentNode = sessionNode; rebuild(); });
            t.setRepeats(false); t.start();
        };

        doneBtn.addActionListener(ae -> finish.run());
        addPinBtn.addActionListener(ae -> {
            Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
            int px = scr.width/2, py = scr.height/2;
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
            SwingUtilities.invokeLater(() -> { ep.show(); ep.win.toFront(); });
            pinCountLbl.setText("Pins: "+pins.size());
        });
    }

    private void placeEditorPinWindow(JWindow win, int sx, int sy) {
        win.setLocation(sx - win.getWidth()/2, sy - win.getHeight()/2);
    }

    private class EditorPin {
        JWindow win;
        int screenX, screenY, rowIndex;
        DefaultTableModel tm;
        Runnable onRemoved;
        static final int PSZ = 44;

        EditorPin(int x, int y, int row, DefaultTableModel tm) {
            screenX=x; screenY=y; rowIndex=row; this.tm=tm; build();
        }

        void build() {
            win = new JWindow();
            win.setAlwaysOnTop(true);
            win.setBackground(new Color(0,0,0,1));

            JPanel panel = new JPanel(null) {
                boolean hovered = false;
                {
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
                    int[] off = {0,0};
                    addMouseListener(new MouseAdapter(){
                        public void mousePressed(MouseEvent e){ off[0]=e.getX(); off[1]=e.getY(); }
                        public void mouseReleased(MouseEvent e){
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
                            screenX = nx + PSZ/2; screenY = ny + PSZ/2;
                            repaint();
                        }
                    });
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx=getWidth()/2, cy=getHeight()/2;
                    int ringR = 10;
                    g2.setColor(pinRingColor); g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx-ringR, cy-ringR, ringR*2, ringR*2);
                    g2.setColor(new Color(255,255,255,230)); g2.setStroke(new BasicStroke(1.5f));
                    int arm=8, gap=4;
                    g2.drawLine(cx-arm,cy, cx-gap,cy); g2.drawLine(cx+gap,cy, cx+arm,cy);
                    g2.drawLine(cx,cy-arm, cx,cy-gap); g2.drawLine(cx,cy+gap, cx,cy+arm);
                    g2.setColor(new Color(255,50,50)); g2.fillOval(cx-3,cy-3,6,6);
                    if (!hovered) {
                        String lbl=String.valueOf(rowIndex+1);
                        g2.setFont(new Font("SansSerif",Font.BOLD,9));
                        FontMetrics fm=g2.getFontMetrics(); int lw=fm.stringWidth(lbl);
                        g2.setColor(new Color(0,0,0,160)); g2.fillOval(cx-6,cy-6,12,12);
                        g2.setColor(Color.WHITE); g2.drawString(lbl, cx-lw/2, cy+4);
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
    //  INTERVAL PICKER
    // =========================================================
    private long showIntervalPicker(String title, long currentMs) {
        int ivH=(int)(currentMs/3600000L), ivM=(int)((currentMs%3600000L)/60000L);
        int ivS=(int)((currentMs%60000L)/1000L), ivT=(int)((currentMs%1000L)/100L);
        int ivHu=(int)((currentMs%100L)/10L), ivTh=(int)(currentMs%10L);

        JDialog dlg = new JDialog((java.awt.Frame)null, title, true);
        dlg.getContentPane().setBackground(new Color(28,28,38));
        dlg.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(22,22,30));
        top.setBorder(BorderFactory.createEmptyBorder(10,12,8,12));
        JLabel lbl = new JLabel(title);
        lbl.setForeground(ACCENT); lbl.setFont(new Font("SansSerif",Font.BOLD,12));
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

    // =========================================================
    //  REFLECTION HELPERS
    // =========================================================
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
        try { return (int) getField(currentNode, name).get(currentNode); } catch (Exception e) { return def; }
    }
    private void setIntField(String name, int val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private long getLongField(String name, long def) {
        try { return (long) getField(currentNode, name).get(currentNode); } catch (Exception e) { return def; }
    }
    private void setLongField(String name, long val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private boolean getBoolField(String name, boolean def) {
        try { return (boolean) getField(currentNode, name).get(currentNode); } catch (Exception e) { return def; }
    }
    private void setBoolField(String name, boolean val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private String getStrField(String name, String def) {
        try { Object v=getField(currentNode,name).get(currentNode); return v!=null?v.toString():def; } catch (Exception e) { return def; }
    }
    private void setStrField(String name, String val) {
        try { getField(currentNode, name).set(currentNode, val); } catch (Exception ignored) {}
    }
    private Object getObjField(String name) {
        try { return getField(currentNode, name).get(currentNode); } catch (Exception e) { return null; }
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

    private static int cellToInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer)val;
        if (val instanceof Long)    return (int)(long)(Long)val;
        if (val instanceof Number)  return ((Number)val).intValue();
        String s = val.toString().trim();
        if (s.endsWith(" s") || s.endsWith("s")) {
            try { return (int)(Double.parseDouble(s.replace(" s","").replace("s","").trim()) * 1000); } catch (Exception ignored) {}
        }
        try { return Integer.parseInt(s); } catch (Exception ignored) {}
        try { return (int)Double.parseDouble(s); } catch (Exception ignored) {}
        return 0;
    }

    private void syncPointsToNode(DefaultTableModel tm) {
        if (currentNode == null) return;
        java.util.List<int[]> pts = buildPointsList(tm);
        try { getField(currentNode, "points").set(currentNode, pts); } catch (Exception e) { e.printStackTrace(); }
    }

    private void syncPointsToNode(DefaultTableModel tm, nodes.BaseNode node) {
        if (node == null) return;
        java.util.List<int[]> pts = buildPointsList(tm);
        try { getField(node, "points").set(node, pts); } catch (Exception e) { e.printStackTrace(); }
    }

    private java.util.List<int[]> buildPointsList(DefaultTableModel tm) {
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        for (int i=0;i<tm.getRowCount();i++) {
            try {
                pts.add(new int[]{
                    cellToInt(tm.getValueAt(i,1)), cellToInt(tm.getValueAt(i,2)),
                    cellToInt(tm.getValueAt(i,3)), cellToInt(tm.getValueAt(i,4)),
                    cellToInt(tm.getValueAt(i,5))
                });
            } catch (Exception e) { e.printStackTrace(); }
        }
        return pts;
    }

    // =========================================================
    //  IMAGE NODE
    // =========================================================
    private void buildImage() {
        addSection("Image Name");
        JTextField nameField = new JTextField(getStrField("imageName","Image"), 16);
        nameField.setBackground(new Color(35,35,50)); nameField.setForeground(new Color(220,220,230));
        nameField.setCaretColor(new Color(220,220,230));
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60,60,85)),
            BorderFactory.createEmptyBorder(4,8,4,8)));
        nameField.addActionListener(e -> { setStrField("imageName", nameField.getText()); currentNode.label=nameField.getText(); rebuild(); });
        nameField.addFocusListener(new FocusAdapter(){ public void focusLost(FocusEvent e){ setStrField("imageName", nameField.getText()); currentNode.label=nameField.getText(); rebuild(); }});
        JPanel nfRow = new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));
        nfRow.setBackground(BG); nfRow.setAlignmentX(LEFT_ALIGNMENT); nfRow.add(nameField);
        content.add(nfRow);

        addSection("Template Image");
        buildCaptureSection("template", "captureRect");
        addLabeledSpinner("Match threshold %", getIntField("threshold",85), 1, 100, val -> setIntField("threshold", val));
    }

    // =========================================================
    //  WATCH CASE NODE
    // =========================================================
    private void buildWatchCase() {
        if (!(currentNode instanceof nodes.WatchCaseNode)) return;
        nodes.WatchCaseNode wc = (nodes.WatchCaseNode) currentNode;

        addSection("Settings");
        addLabeledSpinner("Poll interval (ms)", wc.pollIntervalMs, 50, 10000, val -> wc.pollIntervalMs = val);
        addCheckBox("Loop on match", wc.loopOnMatch, val -> wc.loopOnMatch = val);
        addLabeledSpinner("Loop delay (ms)", wc.loopDelayMs, 0, 10000, val -> wc.loopDelayMs = val);

        addSection("Watch Zones");
        addInfo("Each zone is checked with all image cases");
        for (int zi=0; zi<wc.zones.size(); zi++) {
            addZoneRow(wc, wc.zones.get(zi), zi);
        }
        JLabel addZoneBtn = new JLabel("+ Add Zone");
        addZoneBtn.setFont(new Font("SansSerif",Font.BOLD,11));
        addZoneBtn.setForeground(new Color(80,200,120));
        addZoneBtn.setOpaque(false);
        addZoneBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80,200,120),1),
            BorderFactory.createEmptyBorder(4,14,4,14)));
        addZoneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addZoneBtn.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ wc.addZone(); rebuild(); }
            public void mouseEntered(MouseEvent e){ addZoneBtn.setForeground(new Color(140,255,170)); addZoneBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(140,255,170),1),BorderFactory.createEmptyBorder(4,14,4,14))); }
            public void mouseExited(MouseEvent e) { addZoneBtn.setForeground(new Color(80,200,120));  addZoneBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(80,200,120),1),BorderFactory.createEmptyBorder(4,14,4,14))); }
        });
        JPanel azRow = new JPanel();
        azRow.setLayout(new BoxLayout(azRow, BoxLayout.X_AXIS));
        azRow.setBackground(BG); azRow.setAlignmentX(LEFT_ALIGNMENT);
        azRow.setBorder(new EmptyBorder(4,0,4,0));
        azRow.add(Box.createHorizontalGlue());
        azRow.add(addZoneBtn);
        azRow.add(Box.createHorizontalGlue());
        content.add(azRow);
        addSection("Image Cases");
        addInfo("Connect Image nodes to add cases (drag Image \u2192 Watch Case)");
        for (nodes.WatchCaseNode.WatchCase wcase : wc.cases) {
            addCaseRow(wc, wcase);
        }
    }

    // =========================================================
    //  ZONE ROW  — THE FIXED METHOD
    // =========================================================
    private void addZoneRow(nodes.WatchCaseNode wc, nodes.WatchCaseNode.WatchZone zone, int idx) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(new Color(28,28,40));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50,80,130)),
            BorderFactory.createEmptyBorder(3,3,3,3)));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        JPanel rowWrap = new JPanel(new BorderLayout());
        rowWrap.setBackground(BG);
        rowWrap.setBorder(new EmptyBorder(0,4,0,4));
        rowWrap.setAlignmentX(LEFT_ALIGNMENT);
        rowWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        rowWrap.add(row, BorderLayout.CENTER);

        // ── Header: FlowLayout so name field stays 80px wide ──
        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        hdr.setBackground(new Color(28,28,40));
        hdr.setAlignmentX(LEFT_ALIGNMENT);
        hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JTextField nameF = new JTextField(zone.name);
        nameF.setBackground(new Color(35,35,55));
        nameF.setForeground(new Color(140,190,255));
        nameF.setCaretColor(Color.WHITE);
        nameF.setBorder(BorderFactory.createLineBorder(new Color(60,80,120)));
        nameF.setFont(new Font("SansSerif",Font.BOLD,10));
        nameF.setPreferredSize(new Dimension(110, 22));
        nameF.setMaximumSize(new Dimension(110, 22));
        nameF.addFocusListener(new FocusAdapter(){
            public void focusLost(FocusEvent e){ zone.name=nameF.getText().trim(); }
        });

        // Set Zone / Edit Zone — outline style, context-aware
        boolean zoneAlreadySet = zone.rect != null;
        JLabel setZoneBtn = new JLabel(zoneAlreadySet ? "\u270e Edit Zone" : "\u25a3 Set Zone");
        setZoneBtn.setFont(new Font("SansSerif",Font.BOLD,10));
        Color szNormal = zoneAlreadySet ? new Color(220,190,50) : new Color(80,200,120);
        Color szHover  = zoneAlreadySet ? new Color(255,230,80) : new Color(140,255,170);
        setZoneBtn.setForeground(szNormal);
        setZoneBtn.setOpaque(false);
        setZoneBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(szNormal,1),
            BorderFactory.createEmptyBorder(2,6,2,6)));
        setZoneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setZoneBtn.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (zone.rect != null) showZoneEditOverlay(zone, setZoneBtn);
                else captureZoneRect(zone, setZoneBtn);
            }
            public void mouseEntered(MouseEvent e){
                Color c = zone.rect!=null ? new Color(255,230,80) : new Color(140,255,170);
                setZoneBtn.setForeground(c);
                setZoneBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c,1),BorderFactory.createEmptyBorder(2,6,2,6)));
            }
            public void mouseExited(MouseEvent e){
                Color c = zone.rect!=null ? new Color(220,190,50) : new Color(80,200,120);
                setZoneBtn.setForeground(c);
                setZoneBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c,1),BorderFactory.createEmptyBorder(2,6,2,6)));
            }
        });

        // Delete Zone — outline style
        JLabel removeBtn = new JLabel("Delete Zone");
        removeBtn.setFont(new Font("SansSerif",Font.BOLD,10));
        removeBtn.setForeground(new Color(220,80,80));
        removeBtn.setOpaque(false);
        removeBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220,80,80),1),
            BorderFactory.createEmptyBorder(2,6,2,6)));
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ wc.removeZone(idx); rebuild(); }
            public void mouseEntered(MouseEvent e){ removeBtn.setForeground(new Color(255,120,120)); removeBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(255,120,120),1),BorderFactory.createEmptyBorder(2,6,2,6))); }
            public void mouseExited(MouseEvent e) { removeBtn.setForeground(new Color(220,80,80));   removeBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,80,80),1),BorderFactory.createEmptyBorder(2,6,2,6))); }
        });
        // Add all three directly — FlowLayout handles spacing
        hdr.add(nameF);
        hdr.add(setZoneBtn);
        hdr.add(removeBtn);
        row.add(hdr);

        // Click mode
        JPanel cmRow = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        cmRow.setBackground(new Color(28,28,40));
        cmRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel cmLbl = new JLabel("Click:");
        cmLbl.setForeground(TEXT_DIM); cmLbl.setFont(new Font("SansSerif",Font.PLAIN,10));
        String[] modes = {"At match","Zone center","Custom pin","No click"};
        JComboBox<String> cmCombo = new JComboBox<>(modes);
        cmCombo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
        cmCombo.setSelectedIndex(zone.clickMode);
        cmCombo.setFont(new Font("SansSerif",Font.PLAIN,10));
        cmCombo.setBackground(new Color(35,35,55));
        cmCombo.setForeground(new Color(210,210,220));
        cmCombo.setOpaque(true);
        cmCombo.setPreferredSize(new Dimension(110,20));
        cmCombo.setRenderer(new javax.swing.plaf.basic.BasicComboBoxRenderer(){
            public java.awt.Component getListCellRendererComponent(JList l,Object v,int i2,boolean s,boolean f){
                JLabel lbl=(JLabel)super.getListCellRendererComponent(l,v,i2,s,f);
                lbl.setBackground(s?new Color(60,60,90):new Color(35,35,55));
                lbl.setForeground(new Color(210,210,220));
                lbl.setOpaque(true); return lbl;
            }
        });
        cmRow.add(cmLbl); cmRow.add(cmCombo);
        row.add(cmRow);

        JButton pinBtn = editorBtn("Set Pin", new Color(80,100,160));
        pinBtn.setFont(new Font("SansSerif",Font.PLAIN,10));
        pinBtn.setVisible(zone.clickMode == nodes.WatchCaseNode.CLICK_CUSTOM_PIN);
        if (zone.customPinX > 0 || zone.customPinY > 0)
            pinBtn.setText("Pin: "+zone.customPinX+","+zone.customPinY);
        JPanel pinRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        pinRow2.setBackground(new Color(28,28,40));
        pinRow2.setAlignmentX(LEFT_ALIGNMENT);
        pinRow2.add(pinBtn);
        row.add(pinRow2);

        cmCombo.addActionListener(e -> {
            zone.clickMode = cmCombo.getSelectedIndex();
            pinBtn.setVisible(zone.clickMode == nodes.WatchCaseNode.CLICK_CUSTOM_PIN);
            row.revalidate();
        });
        pinBtn.addActionListener(e -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win!=null) win.setVisible(false);
            new javax.swing.Timer(300, ev -> {
                ((javax.swing.Timer)ev.getSource()).stop();
                showSinglePointCapture(zone, pinBtn, win);
            }).start();
        });

        JPanel dRow = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        dRow.setBackground(new Color(28,28,40));
        dRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel dLbl = new JLabel("Click delay ms:");
        dLbl.setForeground(TEXT_DIM); dLbl.setFont(new Font("SansSerif",Font.PLAIN,10));
        JSpinner dSp = new JSpinner(new SpinnerNumberModel(zone.clickDelayMs,0,10000,50));
        dSp.setPreferredSize(new Dimension(65,20));
        dSp.setBackground(new Color(35,35,55));
        JSpinner.DefaultEditor dEd = (JSpinner.DefaultEditor)dSp.getEditor();
        dEd.getTextField().setBackground(new Color(35,35,55));
        dEd.getTextField().setForeground(new Color(210,210,220));
        dSp.addChangeListener(e -> zone.clickDelayMs = ((Number)dSp.getValue()).intValue());
        dRow.add(dLbl); dRow.add(dSp);
        row.add(dRow);

        content.add(Box.createVerticalStrut(4));
        content.add(rowWrap);
    }

    private JLabel makeZoneActionBtn(boolean zoneSet, JLabel[] ref) {
        JLabel btn = new JLabel(zoneSet ? "\u270e Edit Zone" : "\u25a3 Set Zone");
        updateZoneBtnStyle(btn, zoneSet);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){
                boolean set = btn.getText().contains("Edit");
                Color c = set ? new Color(255,230,80) : new Color(140,255,170);
                btn.setForeground(c);
                btn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c,1),BorderFactory.createEmptyBorder(2,6,2,6)));
            }
            public void mouseExited(MouseEvent e){
                boolean set = btn.getText().contains("Edit");
                updateZoneBtnStyle(btn, set);
            }
        });
        return btn;
    }

    private void updateZoneBtnStyle(JLabel btn, boolean zoneSet) {
        Color c = zoneSet ? new Color(220,190,50) : new Color(80,200,120);
        btn.setText(zoneSet ? "\u270e Edit Zone" : "\u25a3 Set Zone");
        btn.setForeground(c);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(c,1),
            BorderFactory.createEmptyBorder(2,6,2,6)));
    }

    /** Shared drag-resize overlay used by both Watch Zone and Watch Case */
    private void showRectEditOverlay(java.awt.Rectangle[] rectRef, Window win, Runnable onDone) {
        if (win!=null) win.setVisible(false);
        new javax.swing.Timer(300, ev -> {
            ((javax.swing.Timer)ev.getSource()).stop();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            JWindow overlay = new JWindow();
            overlay.setAlwaysOnTop(true);
            overlay.setBackground(new Color(0,0,0,0));
            overlay.setBounds(0,0,screen.width,screen.height);

            int[] R = { rectRef[0].x, rectRef[0].y, rectRef[0].width, rectRef[0].height };
            final int H = 12;
            int[] dragMode={0}, dragStart={0,0}, rectAtDrag={0,0,0,0};
            final String[] sizeTxt = { R[2]+"x"+R[3]+"  ("+R[0]+","+R[1]+")" };

            JPanel glass = new JPanel(null) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0,0,0,120)); g2.fillRect(0,0,getWidth(),getHeight());
                    g2.setColor(new Color(0,200,255,80)); g2.fillRect(R[0],R[1],R[2],R[3]);
                    g2.setColor(new Color(80,200,255)); g2.setStroke(new BasicStroke(2));
                    g2.drawRect(R[0],R[1],R[2],R[3]);
                    g2.setColor(Color.WHITE);
                    for (int[] hh : getHandles(R,H)) g2.fillRect(hh[0],hh[1],H,H);
                    String info = sizeTxt[0];
                    g2.setFont(new Font("Monospaced",Font.BOLD,11));
                    FontMetrics fm=g2.getFontMetrics(); int sw=fm.stringWidth(info);
                    int lx=R[0], ly=R[1]>22?R[1]-6:R[1]+R[3]+16;
                    g2.setColor(new Color(0,0,0,180)); g2.fillRoundRect(lx-2,ly-13,sw+8,17,4,4);
                    g2.setColor(new Color(80,220,255)); g2.drawString(info,lx+2,ly);
                }
            };
            glass.setOpaque(false);
            glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            // Control bar embedded in glass
            int barW=520, barH=42;
            JPanel ctrlBar = new JPanel(new FlowLayout(FlowLayout.CENTER,12,8)) {
                protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15,15,22,245)); g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                    g2.setColor(new Color(80,200,255,200)); g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);
                    g2.setColor(new Color(80,200,255,200)); g2.fillRoundRect(0,0,getWidth(),3,4,4);
                }
            };
            ctrlBar.setOpaque(false);
            ctrlBar.setBounds(screen.width/2-barW/2, 12, barW, barH);

            JLabel infoLbl2 = new JLabel("Drag to move  ·  Handles to resize");
            infoLbl2.setFont(new Font("SansSerif",Font.PLAIN,11));
            infoLbl2.setForeground(new Color(160,200,255));

            JLabel sizeLbl2 = new JLabel(sizeTxt[0]);
            sizeLbl2.setFont(new Font("Monospaced",Font.BOLD,11));
            sizeLbl2.setForeground(new Color(80,220,120));
            sizeLbl2.setPreferredSize(new Dimension(160,16));

            JLabel doneBtn2 = new JLabel("  \u2713 Done  ");
            doneBtn2.setFont(new Font("SansSerif",Font.BOLD,12));
            doneBtn2.setForeground(new Color(80,220,120));
            doneBtn2.setOpaque(false);
            doneBtn2.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80,220,120),1),
                BorderFactory.createEmptyBorder(2,8,2,8)));
            doneBtn2.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel escLbl2 = new JLabel("ESC = cancel");
            escLbl2.setFont(new Font("SansSerif",Font.PLAIN,10));
            escLbl2.setForeground(new Color(100,100,130));

            JLabel s1=new JLabel("|"); s1.setForeground(new Color(60,60,90));
            JLabel s2=new JLabel("|"); s2.setForeground(new Color(60,60,90));
            ctrlBar.add(infoLbl2); ctrlBar.add(s1); ctrlBar.add(sizeLbl2);
            ctrlBar.add(s2); ctrlBar.add(doneBtn2); ctrlBar.add(escLbl2);
            glass.add(ctrlBar);

            Runnable confirm2 = () -> SwingUtilities.invokeLater(() -> {
                rectRef[0] = new java.awt.Rectangle(R[0],R[1],R[2],R[3]);
                overlay.dispose();
                if(win!=null) { win.setVisible(true); win.toFront(); }
                if(onDone!=null) onDone.run();
            });
            Runnable cancel2 = () -> SwingUtilities.invokeLater(() -> {
                overlay.dispose();
                if(win!=null) { win.setVisible(true); win.toFront(); }
            });

            doneBtn2.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){ confirm2.run(); }
                public void mouseEntered(MouseEvent e){ doneBtn2.setForeground(new Color(140,255,170)); doneBtn2.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(140,255,170),1),BorderFactory.createEmptyBorder(2,8,2,8))); }
                public void mouseExited(MouseEvent e) { doneBtn2.setForeground(new Color(80,220,120));  doneBtn2.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(80,220,120),1),BorderFactory.createEmptyBorder(2,8,2,8))); }
            });

            com.github.kwhat.jnativehook.keyboard.NativeKeyListener escR =
                new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
                    public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){
                        if (e.getKeyCode()==com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ESCAPE)
                            cancel2.run();
                    }
                    public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
                    public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
                };
            try { com.github.kwhat.jnativehook.GlobalScreen.addNativeKeyListener(escR); } catch(Exception ignored){}
            overlay.addWindowListener(new java.awt.event.WindowAdapter(){
                public void windowClosed(java.awt.event.WindowEvent e){
                    try { com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(escR); } catch(Exception ignored){}
                }
            });

            glass.addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    if (ctrlBar.getBounds().contains(e.getPoint())) return;
                    dragStart[0]=e.getX(); dragStart[1]=e.getY();
                    rectAtDrag[0]=R[0]; rectAtDrag[1]=R[1]; rectAtDrag[2]=R[2]; rectAtDrag[3]=R[3];
                    int[][] handles=getHandles(R,H); dragMode[0]=1;
                    for (int i=0;i<handles.length;i++){
                        if (new java.awt.Rectangle(handles[i][0],handles[i][1],H,H).contains(e.getPoint())){ dragMode[0]=i+2; break; }
                    }
                    java.awt.Rectangle rr=new java.awt.Rectangle(R[0],R[1],R[2],R[3]);
                    if (!rr.contains(e.getPoint()) && dragMode[0]==1) dragMode[0]=0;
                }
                public void mouseReleased(MouseEvent e){ dragMode[0]=0; }
            });
            glass.addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    if (dragMode[0]==0) return;
                    int dx=e.getX()-dragStart[0], dy=e.getY()-dragStart[1];
                    switch(dragMode[0]){
                        case 1: R[0]=rectAtDrag[0]+dx; R[1]=rectAtDrag[1]+dy; break;
                        case 2: R[0]=rectAtDrag[0]+dx; R[1]=rectAtDrag[1]+dy; R[2]=Math.max(20,rectAtDrag[2]-dx); R[3]=Math.max(20,rectAtDrag[3]-dy); break;
                        case 3: R[1]=rectAtDrag[1]+dy; R[3]=Math.max(20,rectAtDrag[3]-dy); break;
                        case 4: R[1]=rectAtDrag[1]+dy; R[2]=Math.max(20,rectAtDrag[2]+dx); R[3]=Math.max(20,rectAtDrag[3]-dy); break;
                        case 5: R[2]=Math.max(20,rectAtDrag[2]+dx); break;
                        case 6: R[2]=Math.max(20,rectAtDrag[2]+dx); R[3]=Math.max(20,rectAtDrag[3]+dy); break;
                        case 7: R[3]=Math.max(20,rectAtDrag[3]+dy); break;
                        case 8: R[0]=rectAtDrag[0]+dx; R[2]=Math.max(20,rectAtDrag[2]-dx); R[3]=Math.max(20,rectAtDrag[3]+dy); break;
                        case 9: R[0]=rectAtDrag[0]+dx; R[2]=Math.max(20,rectAtDrag[2]-dx); break;
                    }
                    sizeTxt[0]=R[2]+"x"+R[3]+"  ("+R[0]+","+R[1]+")";
                    sizeLbl2.setText(R[2]+"x"+R[3]);
                    glass.repaint();
                }
                public void mouseMoved(MouseEvent e){
                    if (ctrlBar.getBounds().contains(e.getPoint())){ glass.setCursor(Cursor.getDefaultCursor()); return; }
                    int[][] handles=getHandles(R,H);
                    int[] cursors={Cursor.NW_RESIZE_CURSOR,Cursor.N_RESIZE_CURSOR,Cursor.NE_RESIZE_CURSOR,
                                   Cursor.E_RESIZE_CURSOR,Cursor.SE_RESIZE_CURSOR,Cursor.S_RESIZE_CURSOR,
                                   Cursor.SW_RESIZE_CURSOR,Cursor.W_RESIZE_CURSOR};
                    for (int i=0;i<handles.length;i++){
                        if (new java.awt.Rectangle(handles[i][0],handles[i][1],H,H).contains(e.getPoint())){
                            glass.setCursor(Cursor.getPredefinedCursor(cursors[i])); return;
                        }
                    }
                    java.awt.Rectangle rr=new java.awt.Rectangle(R[0],R[1],R[2],R[3]);
                    glass.setCursor(Cursor.getPredefinedCursor(rr.contains(e.getPoint())?Cursor.MOVE_CURSOR:Cursor.DEFAULT_CURSOR));
                }
            });
            overlay.setContentPane(glass);
            overlay.setVisible(true);
        }).start();
    }

    private void showZoneEditOverlay(nodes.WatchCaseNode.WatchZone zone, JLabel btn) {
        Window win = SwingUtilities.getWindowAncestor(this);
        java.awt.Rectangle[] ref = { zone.rect };
        showRectEditOverlay(ref, win, () -> {
            zone.rect = ref[0];
            btn.setText("\u270e Edit Zone");
            btn.setForeground(new Color(220,190,50));
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,190,50),1),
                BorderFactory.createEmptyBorder(2,6,2,6)));
        });
    }

    private void captureZoneRect(nodes.WatchCaseNode.WatchZone zone, JLabel btn) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win!=null) win.setVisible(false);
        new javax.swing.Timer(300, ev -> {
            ((javax.swing.Timer)ev.getSource()).stop();
            showZoneCaptureOverlay(zone, btn, win);
        }).start();
    }

    private void showZoneCaptureOverlay(nodes.WatchCaseNode.WatchZone zone, JLabel btn, Window parentWin) {
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0,0,0,0));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        overlay.setBounds(0,0,screen.width,screen.height);
        int[] drag={0,0,0,0}; boolean[] dragging={false};
        JPanel glass = new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0,0,0,80)); g2.fillRect(0,0,getWidth(),getHeight());
                String msg = "Drag to set Watch Zone: "+zone.name+"   [ESC = cancel]";
                g2.setFont(new Font("SansSerif",Font.BOLD,15));
                FontMetrics fm=g2.getFontMetrics(); int tw=fm.stringWidth(msg);
                g2.setColor(new Color(0,0,0,160));
                g2.fillRoundRect((getWidth()-tw)/2-12,18,tw+24,32,10,10);
                g2.setColor(new Color(100,255,160)); g2.drawString(msg,(getWidth()-tw)/2,40);
                if (dragging[0]){
                    int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                    int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                    Composite old=g2.getComposite();
                    g2.setComposite(AlphaComposite.Clear); g2.fillRect(rx,ry,rw,rh);
                    g2.setComposite(old);
                    g2.setColor(new Color(100,255,160)); g2.setStroke(new BasicStroke(2)); g2.drawRect(rx,ry,rw,rh);
                    g2.setFont(new Font("Monospaced",Font.BOLD,11));
                    g2.drawString(rw+"x"+rh, rx+4, ry>20?ry-4:ry+rh+16);
                }
            }
        };
        glass.setOpaque(false); glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        glass.setFocusable(true);
        com.github.kwhat.jnativehook.keyboard.NativeKeyListener escZ =
            new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
                public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){
                    if (e.getKeyCode()==com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ESCAPE)
                        SwingUtilities.invokeLater(() -> {
                            overlay.dispose();
                            if(parentWin!=null) { parentWin.setVisible(true); parentWin.toFront(); }
                        });
                }
                public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
                public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e){}
            };
        try { com.github.kwhat.jnativehook.GlobalScreen.addNativeKeyListener(escZ); } catch(Exception ignored){}
        overlay.addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosed(java.awt.event.WindowEvent e){
                try { com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(escZ); } catch(Exception ignored){}
            }
        });
        glass.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){drag[0]=e.getX();drag[1]=e.getY();dragging[0]=true;}
            public void mouseReleased(MouseEvent e){
                dragging[0]=false; overlay.dispose();
                int rx=Math.min(drag[0],drag[2]),ry=Math.min(drag[1],drag[3]);
                int rw=Math.abs(drag[2]-drag[0]),rh=Math.abs(drag[3]-drag[1]);
                if(rw>4&&rh>4){
                    zone.rect=new java.awt.Rectangle(rx,ry,rw,rh);
                    btn.setText("\u270e Edit Zone");
                    btn.setForeground(new Color(220,190,50));
                    btn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,190,50),1),BorderFactory.createEmptyBorder(2,6,2,6)));
                }
                if(parentWin!=null) SwingUtilities.invokeLater(()->{parentWin.setVisible(true);parentWin.toFront();});
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){drag[2]=e.getX();drag[3]=e.getY();glass.paintImmediately(0,0,glass.getWidth(),glass.getHeight());}
        });
        overlay.setContentPane(glass); overlay.setVisible(true);
    }

    /** Returns 8 handle rects: TL, T, TR, R, BR, B, BL, L */
    private int[][] getHandles(int[] R, int H) {
        int x=R[0],y=R[1],w=R[2],h=R[3],h2=H/2;
        return new int[][]{
            {x-h2,         y-h2        }, // TL
            {x+w/2-h2,     y-h2        }, // T
            {x+w-h2,       y-h2        }, // TR
            {x+w-h2,       y+h/2-h2    }, // R
            {x+w-h2,       y+h-h2      }, // BR
            {x+w/2-h2,     y+h-h2      }, // B
            {x-h2,         y+h-h2      }, // BL
            {x-h2,         y+h/2-h2    }, // L
        };
    }
    private void showSinglePointCapture(nodes.WatchCaseNode.WatchZone zone, JButton btn, Window parentWin) {
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0,0,0,0));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        overlay.setBounds(0,0,screen.width,screen.height);
        JPanel glass = new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setColor(new Color(0,0,0,60)); g2.fillRect(0,0,getWidth(),getHeight());
                String msg="Click to set pin location   [ESC = cancel]";
                g2.setFont(new Font("SansSerif",Font.BOLD,15));
                FontMetrics fm=g2.getFontMetrics(); int tw=fm.stringWidth(msg);
                g2.setColor(new Color(0,0,0,160));
                g2.fillRoundRect((getWidth()-tw)/2-12,18,tw+24,32,10,10);
                g2.setColor(new Color(255,200,80)); g2.drawString(msg,(getWidth()-tw)/2,40);
            }
        };
        glass.setOpaque(false); glass.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        glass.setFocusable(true);
        java.awt.KeyEventDispatcher kedS = e2 -> {
            if (e2.getID()==KeyEvent.KEY_PRESSED && e2.getKeyCode()==KeyEvent.VK_ESCAPE) {
                SwingUtilities.invokeLater(() -> {
                    overlay.dispose();
                    if(parentWin!=null) { parentWin.setVisible(true); parentWin.toFront(); }
                });
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(kedS);
        overlay.addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosed(java.awt.event.WindowEvent e){
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(kedS);
            }
        });
        glass.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                zone.customPinX=e.getX(); zone.customPinY=e.getY();
                btn.setText("Pin: "+e.getX()+","+e.getY());
                overlay.dispose();
                if(parentWin!=null) SwingUtilities.invokeLater(()->{parentWin.setVisible(true);parentWin.toFront();});
            }
        });
        overlay.setContentPane(glass); overlay.setVisible(true);
    }

    private void addCaseRow(nodes.WatchCaseNode wc, nodes.WatchCaseNode.WatchCase wcase) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(new Color(32,32,44));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55,55,75)),
            BorderFactory.createEmptyBorder(4,6,4,6)));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(240,90));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        topRow.setBackground(new Color(32,32,44));
        JLabel nameLbl = new JLabel(wcase.portName);
        nameLbl.setForeground(new Color(140,200,255)); nameLbl.setFont(new Font("SansSerif",Font.BOLD,11));
        JLabel thrLbl = new JLabel("Threshold:");
        thrLbl.setForeground(TEXT_DIM); thrLbl.setFont(new Font("SansSerif",Font.PLAIN,10));
        JSpinner thrSp = new JSpinner(new SpinnerNumberModel(wcase.threshold,1,100,1));
        thrSp.setPreferredSize(new Dimension(55,20));
        thrSp.addChangeListener(e -> wcase.threshold = ((Number)thrSp.getValue()).intValue());
        topRow.add(nameLbl); topRow.add(thrLbl); topRow.add(thrSp);
        row.add(topRow);

        JCheckBox outCb = new JCheckBox("Has output port", wcase.hasOutput);
        outCb.setBackground(new Color(32,32,44)); outCb.setForeground(TEXT_DIM);
        outCb.setFont(new Font("SansSerif",Font.PLAIN,10));
        outCb.addActionListener(e -> { wcase.hasOutput = outCb.isSelected(); rebuild(); });
        row.add(outCb);

        content.add(Box.createVerticalStrut(3));
        content.add(row);
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
        JLabel lbl = new JLabel("<html><body style='width:220px'>" + text + "</body></html>");
        lbl.setForeground(new Color(100,160,100));
        lbl.setFont(new Font("SansSerif",Font.ITALIC,10));
        lbl.setBorder(new EmptyBorder(2,8,2,8));
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