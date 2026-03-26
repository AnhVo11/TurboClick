package ui;

import engine.*;
import nodes.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class TreeTab extends JPanel {

    public  String  treeName;
    public  String  treeId;
    private boolean treeRunning = false;

    public int    hotKeyCode = -1;
    public String hotKeyName = "None";
    public boolean autoStartOnLaunch = false;
    public boolean loopTree          = false;
    public boolean runOnce           = true;
    public int     hudDismissMs      = 3000;
    public boolean showHud           = true;

    private final NodeCanvas  canvas;
    private final NodePalette palette;
    private final NodeEditor  editor;
    private       RuleEngine  engine;
    private       Runnable    onRunStateChanged;

    public TreeTab(String name) {
        this.treeName = name;
        this.treeId   = UUID.randomUUID().toString().substring(0,8);

        setLayout(new BorderLayout(0,0));
        setBackground(new Color(22,22,28));

        canvas  = new NodeCanvas();
        palette = new NodePalette();
        editor  = new NodeEditor();
        editor.setPreferredSize(new Dimension(270, 0));
        editor.setVisible(false);

        // Canvas row: canvas + editor side by side
        JPanel canvasRow = new JPanel(new BorderLayout(0,0));
        canvasRow.setBackground(new Color(22,22,28));
        canvasRow.add(canvas, BorderLayout.CENTER);
        canvasRow.add(editor, BorderLayout.EAST);

        // Body: palette on top, canvas below
        JPanel body = new JPanel(new BorderLayout(0,0));
        body.setBackground(new Color(22,22,28));
        body.add(palette,   BorderLayout.NORTH);
        body.add(canvasRow, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.NORTH);
        add(body,           BorderLayout.CENTER);

        // Wire palette to canvas for ghost drag
        palette.setTargetCanvas(canvas);

        // Palette click (no drag) → add node at random spread position
        palette.setOnNodeDropped((type, dropPoint) -> {
            // Only handle click case (drag is handled by canvas.finishGhostDrag)
            if (dropPoint.x == 0 && dropPoint.y == 0) {
                int cx = 80 + (int)(Math.random() * 400);
                int cy = 60 + (int)(Math.random() * 250);
                BaseNode node = NodeFactory.create(type, cx, cy);
                canvas.addNode(node);
                openEditor(node, canvasRow);
            } else {
                // Drag drop — editor opens via canvas callback
                BaseNode sel = canvas.getSelectedNode();
                if (sel != null) openEditor(sel, canvasRow);
            }
        });

        // Smart Pin button → show smart pin bar (same as SimpleClickPanel)
        palette.setOnSmartPinClicked(() -> {
            // Find the SimpleClickPanel in the app and trigger its smart pin mode
            // For now show a tooltip-style message on canvas
            JOptionPane.showMessageDialog(this,
                "Smart Pin mode is available in Build Simple Click.\n" +
                "Switch to Build Simple Click to use Smart Pin,\n" +
                "then switch back — your pins are saved.",
                "Smart Pin", JOptionPane.INFORMATION_MESSAGE);
        });

        canvas.setOnNodeSelected(n -> {
            if (n != null) openEditor(n, canvasRow);
            else           closeEditor(canvasRow);
        });

        canvas.setOnNodeDoubleClick(n -> openEditor(n, canvasRow));
    }

    private void openEditor(BaseNode node, JPanel canvasRow) {
        editor.setNode(node, canvas);
        editor.setVisible(true);
        canvasRow.revalidate();
        canvasRow.repaint();
    }

    private void closeEditor(JPanel canvasRow) {
        editor.setVisible(false);
        canvasRow.revalidate();
        canvasRow.repaint();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        bar.setBackground(new Color(25, 25, 35));
        bar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,new Color(50,50,65)));

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif",Font.PLAIN,14));
        dot.setForeground(new Color(60,60,80));

        JLabel nameLbl = new JLabel(treeName);
        nameLbl.setForeground(new Color(200,200,220));
        nameLbl.setFont(new Font("SansSerif",Font.BOLD,12));

        JButton runBtn   = toolBtn("▶  Run",   new Color(40,200,80));
        JButton stopBtn  = toolBtn("■  Stop",  new Color(220,70,70));
        JButton fitBtn   = toolBtn("Fit View", new Color(100,100,130));
        JButton clearBtn = toolBtn("Clear",    new Color(150,60,60));

        JButton loopBtn = toolBtn("↺  Loop", new Color(220,180,40));
        loopBtn.addActionListener(e -> {
            if (treeRunning) stopTree();
            else {
                // Loop mode — restart tree when it finishes
                loopBtn.setForeground(new Color(255,220,60));
                startTreeLoop(loopBtn);
            }
        });

        runBtn.addActionListener(e   -> startTree());
        stopBtn.addActionListener(e  -> stopTree());
        fitBtn.addActionListener(e   -> canvas.fitToScreen());
        clearBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,"Clear all nodes?","Confirm",JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) { canvas.getNodes().clear(); canvas.repaint(); }
        });

        JButton zoomInBtn  = toolBtn("+",  new Color(90,90,120));
        JButton zoomOutBtn = toolBtn("-",  new Color(90,90,120));
        JButton zoomRstBtn = toolBtn("1:1",new Color(90,90,120));
        zoomInBtn.addActionListener(e  -> canvas.zoomIn());
        zoomOutBtn.addActionListener(e -> canvas.zoomOut());
        zoomRstBtn.addActionListener(e -> canvas.zoomReset());

        bar.add(dot); bar.add(nameLbl);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(runBtn); bar.add(loopBtn); bar.add(stopBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(fitBtn); bar.add(clearBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(zoomOutBtn); bar.add(zoomInBtn); bar.add(zoomRstBtn);

        setOnRunStateChanged(() -> SwingUtilities.invokeLater(() ->
            dot.setForeground(treeRunning ? new Color(40,220,80) : new Color(60,60,80))));

        return bar;
    }

    public void startTree() {
        if (treeRunning) return;
        Map<String, BaseNode> nodeMap = canvas.getNodes();
        if (nodeMap.isEmpty()) { JOptionPane.showMessageDialog(this,"No nodes on canvas.\nClick a node from the palette above to add one."); return; }
        // Use explicit start node if set, otherwise auto-detect
        String startId = canvas.getStartNodeId();
        if (startId == null || !nodeMap.containsKey(startId))
            startId = findStartNode(nodeMap);
        if (startId == null) { JOptionPane.showMessageDialog(this,"Could not find a start node.\nRight-click a node → Set as start node."); return; }
        try {
            ExecutionContext ctx = new ExecutionContext(new java.awt.Robot(), treeId);
            engine = new RuleEngine(nodeMap, startId, ctx);
            engine.setOnNodeStart(n  -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnNodeFinish(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                SwingUtilities.invokeLater(() -> {
                    hideRunHud();
                    Window w = SwingUtilities.getWindowAncestor(this);
                    if (w!=null) { w.setVisible(true); w.toFront(); }
                    if(onRunStateChanged!=null) onRunStateChanged.run();
                });
            });
            treeRunning = true;
            if (onRunStateChanged!=null) onRunStateChanged.run();
            // Hide main window and show run HUD
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w!=null) w.setVisible(false);
            showRunHud(ctx);
            Thread t = new Thread(engine); t.setDaemon(true); t.start();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private volatile boolean looping = false;

    private void startTreeLoop(JButton loopBtn) {
        looping = true;
        loopBtn.setText("↺  Looping");
        Runnable loop = new Runnable() {
            public void run() {
                if (!looping) {
                    loopBtn.setText("↺  Loop");
                    loopBtn.setForeground(new Color(220,180,40));
                    return;
                }
                startTree();
                // After tree finishes, restart if still looping
                new Thread(() -> {
                    while (treeRunning) {
                        try { Thread.sleep(200); } catch(InterruptedException ignored) {}
                    }
                    if (looping) SwingUtilities.invokeLater(this::run);
                    else {
                        SwingUtilities.invokeLater(() -> {
                            loopBtn.setText("↺  Loop");
                            loopBtn.setForeground(new Color(220,180,40));
                        });
                    }
                }).start();
            }
        };
        SwingUtilities.invokeLater(loop);
    }

    public void stopTree() {
        looping = false;
        if (engine != null) engine.stop();
        treeRunning = false;
        SwingUtilities.invokeLater(() -> {
            hideRunHud();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w!=null) { w.setVisible(true); w.toFront(); }
            if (onRunStateChanged != null) onRunStateChanged.run();
        });
    }

    // ── Run HUD — small floating bar shown while tree is running ──
    private JWindow runHud;

    private void showRunHud(engine.ExecutionContext ctx) {
        runHud = new JWindow();
        runHud.setAlwaysOnTop(true);
        runHud.setFocusableWindowState(false);
        runHud.setBackground(new Color(0,0,0,0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,15,22,235));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                g2.setColor(new Color(40,200,80,200));
                g2.fillRoundRect(0,0,getWidth(),3,4,4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.CENTER,12,7));

        JLabel dotLbl = new JLabel("●");
        dotLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        dotLbl.setForeground(new Color(40,220,80));

        JLabel taskLbl = new JLabel(treeName);
        taskLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        taskLbl.setForeground(new Color(200,200,220));

        JLabel sepLbl = new JLabel("|");
        sepLbl.setForeground(new Color(60,60,80));

        JLabel nodeLbl = new JLabel("Starting…");
        nodeLbl.setFont(new Font("SansSerif",Font.PLAIN,11));
        nodeLbl.setForeground(new Color(140,200,255));

        JLabel detailLbl = new JLabel("");
        detailLbl.setFont(new Font("Monospaced",Font.BOLD,11));
        detailLbl.setForeground(new Color(80,220,120));

        JButton stopBtn = new JButton("■ Stop");
        stopBtn.setBackground(new Color(160,40,40));
        stopBtn.setForeground(Color.WHITE); stopBtn.setOpaque(true);
        stopBtn.setBorderPainted(false); stopBtn.setFocusPainted(false);
        stopBtn.setFont(new Font("SansSerif",Font.BOLD,11));
        stopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopBtn.addActionListener(e -> stopTree());

        bar.add(dotLbl); bar.add(taskLbl);
        bar.add(sepLbl); bar.add(nodeLbl); bar.add(detailLbl);
        bar.add(stopBtn);

        // Wire status callback so nodes can update HUD live
        ctx.setStatusCallback((nodeName, detail) -> SwingUtilities.invokeLater(() -> {
            nodeLbl.setText(nodeName);
            detailLbl.setText("  " + detail);
            runHud.pack();
        }));

        // Pulse the dot
        javax.swing.Timer pulse = new javax.swing.Timer(600, e -> {
            if (!treeRunning) { ((javax.swing.Timer)e.getSource()).stop(); return; }
            boolean on = dotLbl.getForeground().equals(new Color(40,220,80));
            dotLbl.setForeground(on ? new Color(20,140,50) : new Color(40,220,80));
        });
        pulse.start();

        runHud.setContentPane(bar);
        runHud.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        runHud.setLocation(screen.width/2 - runHud.getWidth()/2, 18);
        runHud.setVisible(true);
    }

    private void hideRunHud() {
        if (runHud!=null) { runHud.dispose(); runHud=null; }
    }

    public boolean isRunning() { return treeRunning; }

    private String findStartNode(Map<String, BaseNode> nodeMap) {
        Set<String> hasIncoming = new HashSet<>();
        for (BaseNode n : nodeMap.values())
            for (BaseNode.NodePort p : n.outputs)
                if (p.targetNodeId != null) hasIncoming.add(p.targetNodeId);
        for (String id : nodeMap.keySet())
            if (!hasIncoming.contains(id)) return id;
        return null;
    }

    private JButton toolBtn(String text, Color borderColor) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif",Font.BOLD,11));
        b.setBackground(new Color(28,28,38));
        b.setForeground(borderColor);
        b.setOpaque(true); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            BorderFactory.createEmptyBorder(4,10,4,10)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseEntered(java.awt.event.MouseEvent e){ b.setBackground(new Color(40,40,55)); }
            public void mouseExited(java.awt.event.MouseEvent e) { b.setBackground(new Color(28,28,38)); }
        });
        return b;
    }

    public void setOnRunStateChanged(Runnable cb) { onRunStateChanged = cb; }
    public NodeCanvas getCanvas() { return canvas; }
}