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

        JPanel canvasRow = new JPanel(new BorderLayout(0,0));
        canvasRow.setBackground(new Color(22,22,28));
        canvasRow.add(canvas, BorderLayout.CENTER);
        canvasRow.add(editor, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0,0));
        body.setBackground(new Color(22,22,28));
        body.add(palette,   BorderLayout.NORTH);
        body.add(canvasRow, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.NORTH);
        add(body,           BorderLayout.CENTER);

        palette.setOnNodeDropped((type, dropPoint) -> {
            int cx = 80 + (int)(Math.random() * 500);
            int cy = 60 + (int)(Math.random() * 300);
            BaseNode node = NodeFactory.create(type, cx, cy);
            canvas.addNode(node);
            openEditor(node, canvasRow);
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

        JButton runBtn   = toolBtn("Run",     new Color(40,160,80));
        JButton stopBtn  = toolBtn("Stop",    new Color(180,50,50));
        JButton fitBtn   = toolBtn("Fit View",new Color(60,60,80));
        JButton clearBtn = toolBtn("Clear",   new Color(100,50,50));

        runBtn.addActionListener(e   -> startTree());
        stopBtn.addActionListener(e  -> stopTree());
        fitBtn.addActionListener(e   -> canvas.fitToScreen());
        clearBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,"Clear all nodes?","Confirm",JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) { canvas.getNodes().clear(); canvas.repaint(); }
        });

        bar.add(dot); bar.add(nameLbl);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(runBtn); bar.add(stopBtn);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(fitBtn); bar.add(clearBtn);

        setOnRunStateChanged(() -> SwingUtilities.invokeLater(() ->
            dot.setForeground(treeRunning ? new Color(40,220,80) : new Color(60,60,80))));

        return bar;
    }

    public void startTree() {
        if (treeRunning) return;
        Map<String, BaseNode> nodeMap = canvas.getNodes();
        if (nodeMap.isEmpty()) {
            JOptionPane.showMessageDialog(this,"No nodes on canvas.\nClick a node from the palette above to add one.");
            return;
        }
        String startId = findStartNode(nodeMap);
        if (startId == null) { JOptionPane.showMessageDialog(this,"Could not find a start node."); return; }
        try {
            ExecutionContext ctx = new ExecutionContext(new java.awt.Robot(), treeId);
            engine = new RuleEngine(nodeMap, startId, ctx);
            engine.setOnNodeStart(n  -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnNodeFinish(n -> SwingUtilities.invokeLater(canvas::repaint));
            engine.setOnTreeFinish(() -> {
                treeRunning = false;
                if (onRunStateChanged != null) onRunStateChanged.run();
            });
            treeRunning = true;
            if (onRunStateChanged != null) onRunStateChanged.run();
            Thread t = new Thread(engine); t.setDaemon(true); t.start();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void stopTree() {
        if (engine != null) engine.stop();
        treeRunning = false;
        if (onRunStateChanged != null) onRunStateChanged.run();
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

    private JButton toolBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif",Font.BOLD,11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public void setOnRunStateChanged(Runnable cb) { onRunStateChanged = cb; }
    public NodeCanvas getCanvas() { return canvas; }
}
