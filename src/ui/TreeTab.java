package ui;

import engine.*;
import nodes.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

import ui.SettingsPanel;

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
    private JTextArea logArea;
    private boolean logVisible = false;
    private RecordingEngine recorder = new RecordingEngine();
    private JPanel chatPanel;
    private JTextArea chatLog;
    private java.util.List<String> chatHistory = new java.util.ArrayList<>();

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

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(15, 15, 22));
        logArea.setForeground(new Color(140, 200, 140));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 120));
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 65)));
        logScroll.setVisible(false);

        chatPanel = buildChatPanel();
        chatPanel.setVisible(false);

        JPanel centerStack = new JPanel(new BorderLayout());
        centerStack.setBackground(new Color(22, 22, 28));
        centerStack.add(body, BorderLayout.CENTER);
        centerStack.add(chatPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerStack, logScroll);
        split.setResizeWeight(1.0);
        split.setDividerSize(0);
        split.setBorder(null);
        split.setBackground(new Color(22, 22, 28));

        add(buildToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

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

        JButton runBtn = toolBtn("▶  Run", new Color(40, 200, 80));
        JButton stopBtn = toolBtn("■  Stop", new Color(220, 70, 70));
        JButton fitBtn = toolBtn("Fit View", new Color(100, 100, 130));
        JButton clearBtn = toolBtn("Clear", new Color(150, 60, 60));
        JButton architectBtn = toolBtn("⬡ Architect", new Color(80, 160, 255));
        JButton saveBtn = toolBtn("⬇  Save", new Color(80, 200, 160));
        JButton loadBtn = toolBtn("⬆  Load", new Color(160, 140, 220));

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
            if (w != null)
                w.setVisible(false);
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
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
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
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            File file = fc.getSelectedFile();
            try {
                String loadJson = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                SaveFormat.TaskFile tf = TaskSerializer.load(loadJson);
                // Confirm overwrite if canvas already has nodes
                if (!canvas.getNodes().isEmpty()) {
                    int r = JOptionPane.showConfirmDialog(this,
                            "Replace current canvas with \"" + tf.taskName + "\"?",
                            "Load Task", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION)
                        return;
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
        JButton logBtn = toolBtn("⬡ Event Log", new Color(90, 90, 120));
        JButton recordBtn = toolBtn("⏺ AI Learn", new Color(180, 60, 60));
        JButton chatBtn = toolBtn("✦ AI Chat", new Color(120, 80, 200));
        logBtn.addActionListener(e -> toggleLog());
        recordBtn.addActionListener(e -> toggleRecording(recordBtn));
        chatBtn.addActionListener(e -> toggleChat());

        JButton zoomInBtn = toolBtn("+", new Color(90, 90, 120));
        JButton zoomOutBtn = toolBtn("-", new Color(90, 90, 120));
        JButton zoomRstBtn = toolBtn("100%", new Color(90, 90, 120));
        zoomInBtn.addActionListener(e -> canvas.zoomIn());
        zoomOutBtn.addActionListener(e -> canvas.zoomOut());
        zoomRstBtn.addActionListener(e -> canvas.zoomReset());

        bar.add(runBtn);
        bar.add(loopBtn);
        bar.add(stopBtn);
        bar.add(architectBtn);
        bar.add(recordBtn);
        bar.add(chatBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(saveBtn);
        bar.add(loadBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(fitBtn);
        bar.add(clearBtn);
        bar.add(Box.createHorizontalStrut(8));
        JLabel zoomLbl = new JLabel("Zoom:");
        zoomLbl.setForeground(new Color(100, 100, 130));
        zoomLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        bar.add(zoomLbl);
        bar.add(zoomOutBtn);
        bar.add(zoomInBtn);
        bar.add(zoomRstBtn);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(logBtn);

        setOnRunStateChanged(() -> SwingUtilities
                .invokeLater(() -> dot.setForeground(treeRunning ? new Color(40, 220, 80) : new Color(60, 60, 80))));

        return bar;
    }

    public void startTree() {
        if (treeRunning)
            return;
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
            ctx.setLogCallback((nodeName, detail) -> appendLog(nodeName, detail));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                SwingUtilities.invokeLater(() -> {
                    hideRunHud();
                    Window w = SwingUtilities.getWindowAncestor(this);
                    if (w != null) {
                        w.setVisible(true);
                        w.toFront();
                    }
                    if (onRunStateChanged != null)
                        onRunStateChanged.run();
                });
            });
            treeRunning = true;
            if (onRunStateChanged != null)
                onRunStateChanged.run();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null)
                w.setVisible(false);
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
        if (nodeMap.isEmpty()) {
            looping = false;
            return;
        }
        String startId = canvas.getStartNodeId();
        if (startId == null || !nodeMap.containsKey(startId))
            startId = findStartNode(nodeMap);
        if (startId == null) {
            looping = false;
            return;
        }
        try {
            ExecutionContext ctx = new ExecutionContext(new java.awt.Robot(), treeId);
            engine = new RuleEngine(nodeMap, startId, ctx);
            engine.setOnNodeStart(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnNodeFinish(n -> SwingUtilities.invokeLater(canvas::repaint));
            ctx.setLogCallback((nodeName, detail) -> appendLog(nodeName, detail));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                if (looping) {
                    SwingUtilities.invokeLater(() -> runOneLoopIteration(loopBtn));
                } else {
                    SwingUtilities.invokeLater(() -> {
                        hideRunHud();
                        Window w = SwingUtilities.getWindowAncestor(TreeTab.this);
                        if (w != null) {
                            w.setVisible(true);
                            w.toFront();
                        }
                        loopBtn.setText("↺  Loop");
                        loopBtn.setForeground(new Color(220, 180, 40));
                        if (onRunStateChanged != null)
                            onRunStateChanged.run();
                    });
                }
            });
            treeRunning = true;
            if (onRunStateChanged != null)
                onRunStateChanged.run();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null && w.isVisible())
                w.setVisible(false);
            if (runHud == null)
                showRunHud(ctx);
            else
                updateHudCtx(ctx);
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
        if (engine != null)
            engine.stop();
        treeRunning = false;
        SwingUtilities.invokeLater(() -> {
            hideRunHud();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) {
                w.setVisible(true);
                w.toFront();
            }
            if (onRunStateChanged != null)
                onRunStateChanged.run();
        });
    }

    private JWindow runHud;
    private javax.swing.JLabel hudNodeLbl, hudDetailLbl;

    private void updateHudCtx(engine.ExecutionContext ctx) {
        if (hudNodeLbl == null)
            return;
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
        nodeLbl.setPreferredSize(new Dimension(220, 18));

        JLabel detailLbl = new JLabel("");
        detailLbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        detailLbl.setForeground(new Color(80, 220, 120));
        detailLbl.setPreferredSize(new Dimension(180, 18));

        hudNodeLbl = nodeLbl;
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
            if (!treeRunning) {
                ((javax.swing.Timer) e.getSource()).stop();
                return;
            }
            boolean on = dotLbl.getForeground().equals(new Color(40, 220, 80));
            dotLbl.setForeground(on ? new Color(20, 140, 50) : new Color(40, 220, 80));
        });
        pulse.start();

        runHud.setContentPane(bar);
        runHud.setSize(720, 46);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        runHud.setLocation(screen.width / 2 - 290, 18);
        runHud.setVisible(true);
    }

    private void hideRunHud() {
        if (runHud != null) {
            runHud.dispose();
            runHud = null;
        }
    }

    public boolean isRunning() {
        return treeRunning;
    }

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
                BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(40, 40, 55));
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(28, 28, 38));
            }
        });
        return b;
    }

    public void setOnRunStateChanged(Runnable cb) {
        onRunStateChanged = cb;
    }

    public NodeCanvas getCanvas() {
        return canvas;
    }

    // ── Recording ─────────────────────────────────────────────
    private void toggleRecording(JButton btn) {
        if (recorder.isRecording()) {
            recorder.stop();
            btn.setText("⏺ Record");
            btn.setForeground(new Color(180, 60, 60));
            int count = recorder.getActions().size();
            if (count == 0) {
                JOptionPane.showMessageDialog(this, "No actions recorded.");
                return;
            }
            int r = JOptionPane.showConfirmDialog(this,
                    count + " actions recorded. Send to AI for analysis?",
                    "Analyze Recording", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION)
                analyzeRecording();
        } else {
            if (!SettingsPanel.hasApiKey()) {
                JOptionPane.showMessageDialog(this,
                        "No API key set.\nGo to Settings to add your Anthropic API key.");
                return;
            }
            recorder.start();
            btn.setText("⏹ Stop");
            btn.setForeground(new Color(255, 80, 80));
            recorder.setOnActionRecorded(() -> SwingUtilities
                    .invokeLater(() -> appendLog("Recording", recorder.getActions().size() + " actions captured")));
        }
    }

    private void analyzeRecording() {
        JDialog progress = new JDialog((java.awt.Frame) null, "Analyzing...", false);
        progress.setSize(320, 90);
        progress.setLocationRelativeTo(this);
        progress.setUndecorated(true);
        JPanel pp = new JPanel(new BorderLayout());
        pp.setBackground(new Color(22, 22, 30));
        pp.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 120)));
        JLabel pl = new JLabel("  ✦ AI is analyzing your recording...", SwingConstants.LEFT);
        pl.setForeground(new Color(160, 120, 255));
        pl.setFont(new Font("SansSerif", Font.BOLD, 12));
        pl.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBackground(new Color(30, 30, 45));
        bar.setForeground(new Color(120, 80, 220));
        pp.add(pl, BorderLayout.CENTER);
        pp.add(bar, BorderLayout.SOUTH);
        progress.setContentPane(pp);
        progress.setVisible(true);

        java.util.List<RecordingEngine.RecordedAction> actions = new java.util.ArrayList<>(
                recorder.getActions());

        new Thread(() -> {
            try {
                String json = AIAnalyzer.analyzeRecording(actions);
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    applyAIResult(json);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    JOptionPane.showMessageDialog(TreeTab.this,
                            "Analysis failed:\n" + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void applyAIResult(String json) {
        // Strip any markdown fences
        json = json.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        try {
            SaveFormat.TaskFile tf = TaskSerializer.load(json);
            if (!canvas.getNodes().isEmpty()) {
                int r = JOptionPane.showConfirmDialog(this,
                        "Replace current canvas with AI-generated task?",
                        "Apply AI Task", JOptionPane.YES_NO_OPTION);
                if (r != JOptionPane.YES_OPTION)
                    return;
                canvas.getNodes().clear();
                canvas.getArrows().clear();
            }
            String newStart = TaskSerializer.applyToCanvas(tf, canvas, 0, 0);
            if (newStart != null)
                canvas.setStartNode(newStart);
            treeName = tf.taskName != null ? tf.taskName : treeName;
            canvas.repaint();
            // Store JSON for chat context
            chatHistory.clear();
            chatHistory.add(json);
            appendLog("AI", "Task generated — " + canvas.getNodes().size() + " nodes");
            // Open chat panel
            chatPanel.setVisible(true);
            revalidate();
            addToChatLog("AI", "Task generated! I created "
                    + canvas.getNodes().size() + " nodes based on your recording.\n"
                    + "You can ask me to adjust anything — e.g. 'add a 2 second wait between steps' "
                    + "or 'make step 3 wait for the button to appear first'.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not parse AI response:\n" + ex.getMessage()
                            + "\n\nRaw response:\n" + json.substring(0, Math.min(300, json.length())),
                    "Parse Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Chat panel ────────────────────────────────────────────
    private void toggleChat() {
        if (!SettingsPanel.hasApiKey()) {
            JOptionPane.showMessageDialog(this,
                    "No API key set.\nGo to Settings to add your Anthropic API key.");
            return;
        }
        chatPanel.setVisible(!chatPanel.isVisible());
        revalidate();
        repaint();
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(18, 18, 26));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(80, 60, 140)));
        panel.setPreferredSize(new Dimension(0, 200));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(24, 18, 38));
        header.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel title = new JLabel("✦ AI Chat  —  ask me to adjust your task");
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(new Color(160, 120, 255));
        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        closeBtn.setBackground(new Color(24, 18, 38));
        closeBtn.setForeground(new Color(120, 100, 160));
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.addActionListener(e -> {
            chatPanel.setVisible(false);
            revalidate();
        });
        header.add(title, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Chat log
        chatLog = new JTextArea();
        chatLog.setEditable(false);
        chatLog.setBackground(new Color(18, 18, 26));
        chatLog.setForeground(new Color(200, 200, 220));
        chatLog.setFont(new Font("SansSerif", Font.PLAIN, 11));
        chatLog.setLineWrap(true);
        chatLog.setWrapStyleWord(true);
        chatLog.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JScrollPane logScroll = new JScrollPane(chatLog);
        logScroll.setBorder(null);
        logScroll.getViewport().setBackground(new Color(18, 18, 26));
        panel.add(logScroll, BorderLayout.CENTER);

        // Input row
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(new Color(22, 18, 32));
        inputRow.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JTextField inputField = new JTextField();
        inputField.setBackground(new Color(32, 28, 46));
        inputField.setForeground(new Color(220, 220, 240));
        inputField.setCaretColor(new Color(160, 120, 255));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 60, 120)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 11));
        inputField.setToolTipText("Ask AI to adjust your task...");

        JButton sendBtn = new JButton("Send");
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        sendBtn.setBackground(new Color(100, 60, 200));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setOpaque(true);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setPreferredSize(new Dimension(70, 30));
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Runnable doSend = () -> {
            String msg = inputField.getText().trim();
            if (msg.isEmpty())
                return;
            inputField.setText("");
            addToChatLog("You", msg);
            sendChatMessage(msg);
        };

        sendBtn.addActionListener(e -> doSend.run());
        inputField.addActionListener(e -> doSend.run());

        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        panel.add(inputRow, BorderLayout.SOUTH);

        return panel;
    }

    private void addToChatLog(String who, String msg) {
        if (chatLog == null)
            return;
        SwingUtilities.invokeLater(() -> {
            chatLog.append(who + ":  " + msg + "\n\n");
            chatLog.setCaretPosition(chatLog.getDocument().getLength());
        });
    }

    private void sendChatMessage(String userMsg) {
        // Build current canvas JSON for context
        String canvasJson = "{}";
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            // Simple - just use last known json or serialize current state
            if (!chatHistory.isEmpty())
                canvasJson = chatHistory.get(chatHistory.size() - 1);
        } catch (Exception ignored) {
        }

        final String contextJson = canvasJson;
        String systemPrompt = AIAnalyzer.buildChatSystemPrompt(contextJson);

        // Build messages array with history
        StringBuilder messages = new StringBuilder("[");
        // Add previous turns
        for (int i = 0; i < chatHistory.size() - 1; i += 2) {
            if (i + 1 < chatHistory.size()) {
                messages.append("{\"role\":\"user\",\"content\":")
                        .append(jsonEsc(chatHistory.get(i))).append("},");
                messages.append("{\"role\":\"assistant\",\"content\":")
                        .append(jsonEsc(chatHistory.get(i + 1))).append("},");
            }
        }
        messages.append("{\"role\":\"user\",\"content\":")
                .append(jsonEsc(userMsg)).append("}]");

        addToChatLog("AI", "Thinking...");

        final String msgJson = messages.toString();
        new Thread(() -> {
            try {
                // Direct API call with conversation
                String body = "{"
                        + "\"model\":\"claude-sonnet-4-20250514\","
                        + "\"max_tokens\":4096,"
                        + "\"system\":" + jsonEsc(systemPrompt) + ","
                        + "\"messages\":" + msgJson
                        + "}";

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                        "https://api.anthropic.com/v1/messages").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-api-key", SettingsPanel.getApiKey());
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);
                conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                String response = new String(conn.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String reply = AIAnalyzer.callAPI(systemPrompt,
                        "[{\"type\":\"text\",\"text\":" + jsonEsc(userMsg) + "}]");

                SwingUtilities.invokeLater(() -> {
                    // Remove "Thinking..." line
                    String current = chatLog.getText();
                    int thinking = current.lastIndexOf("AI:  Thinking...");
                    if (thinking >= 0)
                        try {
                            chatLog.replaceRange("", thinking, current.length());
                        } catch (Exception ignored) {
                        }

                    chatHistory.add(userMsg);
                    chatHistory.add(reply);

                    // Check if reply looks like JSON
                    String trimmed = reply.replaceAll("(?s)```json\\s*", "")
                            .replaceAll("```", "").trim();
                    if (trimmed.startsWith("{") && trimmed.contains("\"nodes\"")) {
                        addToChatLog("AI", "Updated your task! Applying changes...");
                        applyAIResult(trimmed);
                    } else {
                        addToChatLog("AI", reply);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    String current = chatLog.getText();
                    int thinking = current.lastIndexOf("AI:  Thinking...");
                    if (thinking >= 0)
                        try {
                            chatLog.replaceRange("", thinking, current.length());
                        } catch (Exception ignored) {
                        }
                    addToChatLog("AI", "Error: " + ex.getMessage());
                });
            }
        }).start();
    }

    private static String jsonEsc(String s) {
        if (s == null)
            return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private void toggleLog() {
        logVisible = !logVisible;
        Component logScroll = ((JSplitPane) getComponent(1)).getBottomComponent();
        logScroll.setVisible(logVisible);
        ((JSplitPane) getComponent(1)).setDividerSize(logVisible ? 4 : 0);
        revalidate();
        repaint();
    }

    private void appendLog(String nodeName, String detail) {
        if (logArea == null)
            return;
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String line = "[" + time + "]  " + nodeName + "  →  " + detail + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}