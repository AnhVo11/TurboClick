package ui;

import engine.*;
import nodes.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

public class TreeTab extends JPanel {

    public String treeName;
    public String treeId;
    private boolean treeRunning = false;

    public int hotKeyCode = -1;
    public String hotKeyName = "None";
    public boolean autoStartOnLaunch = false;
    public boolean loopTree = false;
    public boolean runOnce = true;
    public int hudDismissMs = 3000;
    public boolean showHud = true;

    private final NodeCanvas canvas;
    private final NodePalette palette;
    private final NodeEditor editor;
    private RuleEngine engine;
    private Runnable onRunStateChanged;

    public TreeTab(String name) {
        this.treeName = name;
        this.treeId = UUID.randomUUID().toString().substring(0, 8);

        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(22, 22, 28));

        canvas = new NodeCanvas();
        palette = new NodePalette();
        editor = new NodeEditor();
        editor.setPreferredSize(new Dimension(310, 0));
        editor.setVisible(false);

        JPanel canvasRow = new JPanel(new BorderLayout(0, 0));
        canvasRow.setBackground(new Color(22, 22, 28));
        canvasRow.add(canvas, BorderLayout.CENTER);
        canvasRow.add(editor, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setBackground(new Color(22, 22, 28));
        body.add(palette, BorderLayout.NORTH);
        body.add(canvasRow, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        palette.setTargetCanvas(canvas);

        palette.setOnNodeDropped((type, dropPoint) -> {
            if (dropPoint.x == 0 && dropPoint.y == 0) {
                int cx = 80 + (int) (Math.random() * 400);
                int cy = 60 + (int) (Math.random() * 250);
                BaseNode node = NodeFactory.create(type, cx, cy);
                canvas.addNode(node);
                openEditor(node, canvasRow);
            } else {
                BaseNode sel = canvas.getSelectedNode();
                if (sel != null)
                    openEditor(sel, canvasRow);
            }
        });

        palette.setOnSmartPinClicked(() -> {
            JOptionPane.showMessageDialog(this,
                    "Smart Pin mode is available in Build Simple Click.\n" +
                            "Switch to Build Simple Click to use Smart Pin,\n" +
                            "then switch back — your pins are saved.",
                    "Smart Pin", JOptionPane.INFORMATION_MESSAGE);
        });

        canvas.setOnNodeSelected(n -> {
            if (n != null)
                openEditor(n, canvasRow);
            else
                closeEditor(canvasRow);
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
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 65)));

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif", Font.PLAIN, 14));
        dot.setForeground(new Color(60, 60, 80));

        JLabel nameLbl = new JLabel(treeName);
        nameLbl.setForeground(new Color(200, 200, 220));
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JButton runBtn     = toolBtn("▶  Run",       new Color(40, 200, 80));
        JButton stopBtn    = toolBtn("■  Stop",       new Color(220, 70, 70));
        JButton fitBtn     = toolBtn("Fit View",      new Color(100, 100, 130));
        JButton clearBtn   = toolBtn("Clear",         new Color(150, 60, 60));
        JButton architectBtn = toolBtn("⬡ Architect", new Color(80, 160, 255));
        JButton saveBtn    = toolBtn("💾 Save",        new Color(80, 200, 160));
        JButton loadBtn    = toolBtn("📂 Load",        new Color(160, 140, 220));

        JButton loopBtn = toolBtn("↺  Loop", new Color(220, 180, 40));
        loopBtn.addActionListener(e -> {
            if (treeRunning)
                stopTree();
            else {
                loopBtn.setForeground(new Color(255, 220, 60));
                startTreeLoop(loopBtn);
            }
        });

        runBtn.addActionListener(e -> startTree());
        stopBtn.addActionListener(e -> stopTree());
        fitBtn.addActionListener(e -> canvas.fitToScreen());

        architectBtn.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) w.setVisible(false);
            new javax.swing.Timer(200, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                TaskArchitectOverlay.show(canvas.getNodes(), w, () -> canvas.repaint());
            }).start();
        });

        clearBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, "Clear all nodes?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                canvas.getNodes().clear();
                canvas.getArrows().clear();
                canvas.repaint();
            }
        });

        // ── Save ─────────────────────────────────────────────
        saveBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save Task");
            fc.setFileFilter(new FileNameExtensionFilter("TurboClick Task (*.json)", "json"));
            fc.setSelectedFile(new File(treeName + ".json"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = fc.getSelectedFile();
            if (!file.getName().endsWith(".json"))
                file = new File(file.getAbsolutePath() + ".json");
            try {
                TaskSerializer.saveTask(
                        treeName,
                        canvas.getStartNodeId(),
                        canvas.getNodes(),
                        canvas.getArrows(),
                        file);
                JOptionPane.showMessageDialog(this,
                        "Saved to " + file.getName(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Save failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ── Load ─────────────────────────────────────────────
        loadBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Load Task");
            fc.setFileFilter(new FileNameExtensionFilter("TurboClick Task (*.json)", "json"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = fc.getSelectedFile();
            try {
                SaveFormat.TaskFile tf = TaskSerializer.load(file);
                // Confirm overwrite if canvas already has nodes
                if (!canvas.getNodes().isEmpty()) {
                    int r = JOptionPane.showConfirmDialog(this,
                            "Replace current canvas with \"" + tf.taskName + "\"?",
                            "Load Task", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION) return;
                    canvas.getNodes().clear();
                    canvas.getArrows().clear();
                }
                String newStartId = TaskSerializer.applyToCanvas(tf, canvas, 0, 0);
                if (newStartId != null)
                    canvas.setStartNode(newStartId);
                treeName = tf.taskName;
                nameLbl.setText(treeName);
                canvas.repaint();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Load failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton zoomInBtn  = toolBtn("+",   new Color(90, 90, 120));
        JButton zoomOutBtn = toolBtn("-",   new Color(90, 90, 120));
        JButton zoomRstBtn = toolBtn("1:1", new Color(90, 90, 120));
        zoomInBtn.addActionListener(e -> canvas.zoomIn());
        zoomOutBtn.addActionListener(e -> canvas.zoomOut());
        zoomRstBtn.addActionListener(e -> canvas.zoomReset());

        bar.add(dot);
        bar.add(nameLbl);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(runBtn);
        bar.add(loopBtn);
        bar.add(stopBtn);
        bar.add(architectBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(saveBtn);
        bar.add(loadBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(fitBtn);
        bar.add(clearBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(zoomOutBtn);
        bar.add(zoomInBtn);
        bar.add(zoomRstBtn);

        setOnRunStateChanged(() -> SwingUtilities
                .invokeLater(() -> dot.setForeground(treeRunning ? new Color(40, 220, 80) : new Color(60, 60, 80))));

        return bar;
    }

    public void startTree() {
        if (treeRunning) return;
        Map<String, BaseNode> nodeMap = canvas.getNodes();
        if (nodeMap.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No nodes on canvas.\nClick a node from the palette above to add one.");
            return;
        }
        String startId = canvas.getStartNodeId();
        if (startId == null || !nodeMap.containsKey(startId))
            startId = findStartNode(nodeMap);
        if (startId == null) {
            JOptionPane.showMessageDialog(this,
                    "Could not find a start node.\nRight-click a node → Set as start node.");
            return;
        }
        try {
            ExecutionContext ctx = new ExecutionContext(new java.awt.Robot(), treeId);
            engine = new RuleEngine(nodeMap, startId, ctx);
            engine.setOnNodeStart(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnNodeFinish(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                SwingUtilities.invokeLater(() -> {
                    hideRunHud();
                    Window w = SwingUtilities.getWindowAncestor(this);
                    if (w != null) { w.setVisible(true); w.toFront(); }
                    if (onRunStateChanged != null) onRunStateChanged.run();
                });
            });
            treeRunning = true;
            if (onRunStateChanged != null) onRunStateChanged.run();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) w.setVisible(false);
            showRunHud(ctx);
            Thread t = new Thread(engine);
            t.setDaemon(true);
            t.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private volatile boolean looping = false;

    private void startTreeLoop(JButton loopBtn) {
        looping = true;
        loopBtn.setText("↺  Looping");
        runOneLoopIteration(loopBtn);
    }

    private void runOneLoopIteration(JButton loopBtn) {
        if (!looping) {
            SwingUtilities.invokeLater(() -> {
                loopBtn.setText("↺  Loop");
                loopBtn.setForeground(new Color(220, 180, 40));
            });
            return;
        }
        Map<String, BaseNode> nodeMap = canvas.getNodes();
        if (nodeMap.isEmpty()) { looping = false; return; }
        String startId = canvas.getStartNodeId();
        if (startId == null || !nodeMap.containsKey(startId))
            startId = findStartNode(nodeMap);
        if (startId == null) { looping = false; return; }
        try {
            ExecutionContext ctx = new ExecutionContext(new java.awt.Robot(), treeId);
            engine = new RuleEngine(nodeMap, startId, ctx);
            engine.setOnNodeStart(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnNodeFinish(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                if (looping) {
                    SwingUtilities.invokeLater(() -> runOneLoopIteration(loopBtn));
                } else {
                    SwingUtilities.invokeLater(() -> {
                        hideRunHud();
                        Window w = SwingUtilities.getWindowAncestor(TreeTab.this);
                        if (w != null) { w.setVisible(true); w.toFront(); }
                        loopBtn.setText("↺  Loop");
                        loopBtn.setForeground(new Color(220, 180, 40));
                        if (onRunStateChanged != null) onRunStateChanged.run();
                    });
                }
            });
            treeRunning = true;
            if (onRunStateChanged != null) onRunStateChanged.run();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null && w.isVisible()) w.setVisible(false);
            if (runHud == null) showRunHud(ctx);
            else updateHudCtx(ctx);
            Thread t = new Thread(engine);
            t.setDaemon(true);
            t.start();
        } catch (Exception ex) {
            ex.printStackTrace();
            looping = false;
        }
    }

    public void stopTree() {
        looping = false;
        if (engine != null) engine.stop();
        treeRunning = false;
        SwingUtilities.invokeLater(() -> {
            hideRunHud();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) { w.setVisible(true); w.toFront(); }
            if (onRunStateChanged != null) onRunStateChanged.run();
        });
    }

    private JWindow runHud;
    private javax.swing.JLabel hudNodeLbl, hudDetailLbl;

    private void updateHudCtx(engine.ExecutionContext ctx) {
        if (hudNodeLbl == null) return;
        ctx.setStatusCallback((nodeName, detail) -> SwingUtilities.invokeLater(() -> {
            hudNodeLbl.setText(nodeName);
            hudDetailLbl.setText("  " + detail);
        }));
    }

    private void showRunHud(engine.ExecutionContext ctx) {
        runHud = new JWindow();
        runHud.setAlwaysOnTop(true);
        runHud.setFocusableWindowState(false);
        runHud.setBackground(new Color(0, 0, 0, 0));

        JPanel bar = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 15, 22, 235));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(40, 200, 80, 200));
                g2.fillRoundRect(0, 0, getWidth(), 3, 4, 4);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 7));

        JLabel dotLbl = new JLabel("●");
        dotLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        dotLbl.setForeground(new Color(40, 220, 80));

        JLabel taskLbl = new JLabel(treeName);
        taskLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        taskLbl.setForeground(new Color(200, 200, 220));

        JLabel sepLbl = new JLabel("|");
        sepLbl.setForeground(new Color(60, 60, 80));

        JLabel nodeLbl = new JLabel("Starting…");
        nodeLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        nodeLbl.setForeground(new Color(140, 200, 255));
        nodeLbl.setPreferredSize(new Dimension(160, 18));

        JLabel detailLbl = new JLabel("");
        detailLbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        detailLbl.setForeground(new Color(80, 220, 120));
        detailLbl.setPreferredSize(new Dimension(110, 18));

        hudNodeLbl  = nodeLbl;
        hudDetailLbl = detailLbl;

        JButton stopBtn = new JButton("■ Stop");
        stopBtn.setBackground(new Color(160, 40, 40));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setOpaque(true);
        stopBtn.setBorderPainted(false);
        stopBtn.setFocusPainted(false);
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        stopBtn.setPreferredSize(new Dimension(88, 26));
        stopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopBtn.addActionListener(e -> stopTree());

        bar.add(dotLbl);
        bar.add(taskLbl);
        bar.add(sepLbl);
        bar.add(nodeLbl);
        bar.add(detailLbl);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(stopBtn);

        ctx.setStatusCallback((nodeName, detail) -> SwingUtilities.invokeLater(() -> {
            nodeLbl.setText(nodeName);
            detailLbl.setText(detail);
            bar.repaint();
        }));

        javax.swing.Timer pulse = new javax.swing.Timer(600, e -> {
            if (!treeRunning) { ((javax.swing.Timer) e.getSource()).stop(); return; }
            boolean on = dotLbl.getForeground().equals(new Color(40, 220, 80));
            dotLbl.setForeground(on ? new Color(20, 140, 50) : new Color(40, 220, 80));
        });
        pulse.start();

        runHud.setContentPane(bar);
        runHud.setSize(580, 46);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        runHud.setLocation(screen.width / 2 - 290, 18);
        runHud.setVisible(true);
    }

    private void hideRunHud() {
        if (runHud != null) { runHud.dispose(); runHud = null; }
    }

    public boolean isRunning() { return treeRunning; }

    private String findStartNode(Map<String, BaseNode> nodeMap) {
        Set<String> hasIncoming = new HashSet<>();
        for (BaseNode n : nodeMap.values())
            for (BaseNode.NodePort p : n.outputs)
                if (p.targetNodeId != null)
                    hasIncoming.add(p.targetNodeId);
        for (String id : nodeMap.keySet())
            if (!hasIncoming.contains(id))
                return id;
        return null;
    }

    private JButton toolBtn(String text, Color borderColor) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setBackground(new Color(28, 28, 38));
        b.setForeground(borderColor);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(new Color(40, 40, 55)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { b.setBackground(new Color(28, 28, 38)); }
        });
        return b;
    }

    public void setOnRunStateChanged(Runnable cb) { onRunStateChanged = cb; }
    public NodeCanvas getCanvas() { return canvas; }
}