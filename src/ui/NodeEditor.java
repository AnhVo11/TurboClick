package ui;

import nodes.BaseNode;
import nodes.BaseNode.NodePort;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * NodeEditor — right panel.
 * Shows editable properties for the currently selected node.
 * Dynamically rebuilds based on node type.
 */
public class NodeEditor extends JPanel {

    private BaseNode    currentNode;
    private NodeCanvas  canvas;
    private JPanel      content;
    private JScrollPane scroll;

    public NodeEditor() {
        setBackground(new Color(28, 28, 38));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 50, 65)));
        setLayout(new BorderLayout());

        // Header
        JLabel header = new JLabel("  NODE EDITOR");
        header.setForeground(new Color(120, 120, 150));
        header.setFont(new Font("SansSerif", Font.BOLD, 10));
        header.setBorder(new EmptyBorder(10, 8, 8, 0));
        header.setBackground(new Color(22, 22, 30));
        header.setOpaque(true);
        add(header, BorderLayout.NORTH);

        content = new JPanel();
        content.setBackground(new Color(28, 28, 38));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        scroll = new JScrollPane(content);
        scroll.setBackground(new Color(28, 28, 38));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(28, 28, 38));
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
        lbl.setForeground(new Color(80, 80, 100));
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

        // ── Node identity ─────────────────────────────────────
        addSection("Identity");
        addLabeledField("Label", currentNode.label, val -> {
            currentNode.label = val; canvas.refreshNode(currentNode);
        });

        // ── General options ───────────────────────────────────
        addSection("Branch Options");
        addCheckBox("Enabled", currentNode.branchEnabled, val -> {
            currentNode.branchEnabled = val; canvas.refreshNode(currentNode);
        });
        addCheckBox("Log transitions", currentNode.logTransition, val -> currentNode.logTransition = val);
        addLabeledSpinner("Entry delay (ms)", currentNode.entryDelayMs, 0, 60000, val -> currentNode.entryDelayMs = val);

        // ── Type-specific options ─────────────────────────────
        switch (currentNode.type) {
            case WATCH_ZONE:   buildWatchZone();   break;
            case CLICK:        buildClick();        break;
            case SIMPLE_CLICK: buildSimpleClick();  break;
            case CONDITION:    buildCondition();    break;
            case LOOP:         buildLoop();         break;
            case WAIT:         buildWait();         break;
            case STOP:         buildStop();         break;
        }

        // ── Output ports ─────────────────────────────────────
        if (!currentNode.outputs.isEmpty()) {
            addSection("Output Ports");
            for (NodePort port : currentNode.outputs) {
                addLabeledField("Arrow label: "+port.name, port.displayLabel(), val -> {
                    port.customLabel = val; canvas.refreshNode(currentNode);
                });
                addCheckBox("Port enabled: "+port.name, port.enabled, val -> port.enabled = val);
                addLabeledSpinner("Arrow delay ms: "+port.name, port.arrowDelayMs, 0, 60000, val -> port.arrowDelayMs = val);
            }
        }

        content.add(Box.createVerticalGlue());
        revalidate(); repaint();
    }

    // ── Type builders ─────────────────────────────────────────
    private void buildWatchZone() {
        // Cast via reflection-friendly approach — use field names via casting
        // We access fields by casting to the concrete type stored in NodeFactory
        // Since inner classes are package-private, we access via BaseNode fields set by getters
        addSection("Watch Zone Settings");
        addInfo("📷 Capture Button — use Zone Selector");
        addLabeledSpinner("Match % threshold", getIntField("matchThreshold", 85), 10, 100, val -> setIntField("matchThreshold", val));
        addLabeledSpinner("Poll interval (ms)", getIntField("pollIntervalMs", 500), 50, 10000, val -> setIntField("pollIntervalMs", val));
        addLabeledSpinner("Pre-trigger delay (ms)", getIntField("preTriggerDelayMs", 0), 0, 10000, val -> setIntField("preTriggerDelayMs", val));
        addLabeledSpinner("Timeout (ms, 0=∞)", getIntField("timeoutMs", 0), 0, 60000, val -> setIntField("timeoutMs", val));
        addLabeledSpinner("Retry count", getIntField("retryCount", 0), 0, 100, val -> setIntField("retryCount", val));
        addCheckBox("Click at match location", getBoolField("clickAtMatch", true), val -> setBoolField("clickAtMatch", val));
        addLabeledSpinner("Click X (if custom)", getIntField("clickX", 0), -9999, 9999, val -> setIntField("clickX", val));
        addLabeledSpinner("Click Y (if custom)", getIntField("clickY", 0), -9999, 9999, val -> setIntField("clickY", val));
    }

    private void buildClick() {
        addSection("Click Settings");
        addLabeledSpinner("Click X", getIntField("clickX", 0), -9999, 9999, val -> setIntField("clickX", val));
        addLabeledSpinner("Click Y", getIntField("clickY", 0), -9999, 9999, val -> setIntField("clickY", val));
        addLabeledSpinner("Click count", getIntField("clickCount", 1), 1, 999, val -> setIntField("clickCount", val));
        addLabeledSpinner("Sub-click delay (ms)", getIntField("subDelayMs", 100), 0, 10000, val -> setIntField("subDelayMs", val));
        addCombo("Mouse button", new String[]{"Left","Right","Middle"}, getIntField("mouseButton",0), val -> setIntField("mouseButton", val));
        addCheckBox("Double click", getBoolField("doubleClick", false), val -> setBoolField("doubleClick", val));
    }

    private void buildSimpleClick() {
        addSection("Simple Click Settings");
        addLabeledSpinner("Interval (ms)", (int)getLongField("intervalMs",100), 1, 60000, val -> setLongField("intervalMs", val));
        addLabeledSpinner("Max clicks (0=∞)", (int)getLongField("maxClicks",0), 0, 99999, val -> setLongField("maxClicks", val));
        addCombo("Mouse button", new String[]{"Left","Right","Middle"}, getIntField("mouseButton",0), val -> setIntField("mouseButton", val));
        addCheckBox("Double click", getBoolField("doubleClick",false), val -> setBoolField("doubleClick", val));
        addSection("Execution");
        addCheckBox("Wait to finish", getBoolField("waitToFinish",true), val -> setBoolField("waitToFinish", val));
        addCheckBox("Run in background", getBoolField("runInBackground",false), val -> setBoolField("runInBackground", val));
        addCheckBox("Repeat until stopped", getBoolField("repeatUntilStopped",false), val -> setBoolField("repeatUntilStopped", val));
        addLabeledSpinner("Repeat times", getIntField("repeatTimes",1), 1, 9999, val -> setIntField("repeatTimes", val));
    }

    private void buildCondition() {
        addSection("Condition Settings");
        addInfo("Uses Watch Zone image match.");
        addLabeledSpinner("Match % threshold", getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
    }

    private void buildLoop() {
        addSection("Loop Settings");
        addCombo("Loop mode", new String[]{"Fixed count","Until found","Until not found","Forever"},
            0, val -> {});
        addLabeledSpinner("Loop count", getIntField("loopCount",3), 1, 9999, val -> setIntField("loopCount", val));
        addLabeledSpinner("Loop delay (ms)", getIntField("loopDelayMs",0), 0, 60000, val -> setIntField("loopDelayMs", val));
        addLabeledSpinner("Match %", getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
    }

    private void buildWait() {
        addSection("Wait Settings");
        addCombo("Wait mode", new String[]{"Fixed delay","Until found","Until not found"},
            0, val -> {});
        addLabeledSpinner("Delay (ms)", getIntField("delayMs",1000), 1, 600000, val -> setIntField("delayMs", val));
        addLabeledSpinner("Timeout (ms, 0=∞)", getIntField("timeoutMs",0), 0, 60000, val -> setIntField("timeoutMs", val));
        addLabeledSpinner("Poll interval (ms)", getIntField("pollMs",500), 50, 10000, val -> setIntField("pollMs", val));
        addLabeledSpinner("Match %", getIntField("matchThreshold",85), 10, 100, val -> setIntField("matchThreshold", val));
    }

    private void buildStop() {
        addSection("Stop Settings");
        addCombo("Stop mode", new String[]{"This tree only","All trees"}, 0, val -> {});
        addCheckBox("Show message", getBoolField("showMessage",false), val -> setBoolField("showMessage", val));
        addLabeledField("Custom message", getStrField("customMessage",""), val -> setStrField("customMessage", val));
    }

    // ── Reflection field helpers ──────────────────────────────
    private int getIntField(String name, int def) {
        try { return (int) currentNode.getClass().getField(name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setIntField(String name, int val) {
        try { currentNode.getClass().getField(name).set(currentNode, val); }
        catch (Exception ignored) {}
    }
    private long getLongField(String name, long def) {
        try { return (long) currentNode.getClass().getField(name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setLongField(String name, long val) {
        try { currentNode.getClass().getField(name).set(currentNode, val); }
        catch (Exception ignored) {}
    }
    private boolean getBoolField(String name, boolean def) {
        try { return (boolean) currentNode.getClass().getField(name).get(currentNode); }
        catch (Exception e) { return def; }
    }
    private void setBoolField(String name, boolean val) {
        try { currentNode.getClass().getField(name).set(currentNode, val); }
        catch (Exception ignored) {}
    }
    private String getStrField(String name, String def) {
        try { Object v = currentNode.getClass().getField(name).get(currentNode); return v != null ? v.toString() : def; }
        catch (Exception e) { return def; }
    }
    private void setStrField(String name, String val) {
        try { currentNode.getClass().getField(name).set(currentNode, val); }
        catch (Exception ignored) {}
    }

    // ── UI component builders ─────────────────────────────────
    interface IntSetter    { void set(int val); }
    interface BoolSetter   { void set(boolean val); }
    interface StringSetter { void set(String val); }

    private void addSection(String title) {
        JLabel lbl = new JLabel("  " + title.toUpperCase());
        lbl.setForeground(new Color(100, 180, 255));
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setBorder(new EmptyBorder(10, 0, 4, 0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lbl);
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(50, 70, 90));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        content.add(sep);
    }

    private void addInfo(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setForeground(new Color(120, 160, 120));
        lbl.setFont(new Font("SansSerif", Font.ITALIC, 10));
        lbl.setBorder(new EmptyBorder(2, 0, 2, 0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lbl);
    }

    private void addLabeledField(String label, String value, StringSetter setter) {
        JPanel row = rowPanel();
        JLabel lbl = fieldLabel(label);
        JTextField tf = new JTextField(value, 10);
        tf.setBackground(new Color(40, 40, 55));
        tf.setForeground(new Color(220, 220, 220));
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 80)),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        tf.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tf.addActionListener(e -> setter.set(tf.getText()));
        tf.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) { setter.set(tf.getText()); }
        });
        row.add(lbl); row.add(tf);
        content.add(row);
    }

    private void addLabeledSpinner(String label, int value, int min, int max, IntSetter setter) {
        JPanel row = rowPanel();
        JLabel lbl = fieldLabel(label);
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        sp.setPreferredSize(new Dimension(80, 24));
        sp.setBackground(new Color(40, 40, 55));
        sp.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ((JSpinner.DefaultEditor)sp.getEditor()).getTextField().setBackground(new Color(40,40,55));
        ((JSpinner.DefaultEditor)sp.getEditor()).getTextField().setForeground(new Color(220,220,220));
        sp.addChangeListener(e -> setter.set(((Number)sp.getValue()).intValue()));
        row.add(lbl); row.add(sp);
        content.add(row);
    }

    private void addCheckBox(String label, boolean value, BoolSetter setter) {
        JCheckBox cb = new JCheckBox(label, value);
        cb.setForeground(new Color(200, 200, 210));
        cb.setBackground(new Color(28, 28, 38));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cb.setBorder(new EmptyBorder(2, 8, 2, 0));
        cb.setAlignmentX(LEFT_ALIGNMENT);
        cb.addActionListener(e -> setter.set(cb.isSelected()));
        content.add(cb);
    }

    private void addCombo(String label, String[] options, int selected, IntSetter setter) {
        JPanel row = rowPanel();
        row.add(fieldLabel(label));
        JComboBox<String> cb = new JComboBox<>(options);
        cb.setSelectedIndex(Math.min(selected, options.length-1));
        cb.setBackground(new Color(40,40,55));
        cb.setForeground(new Color(220,220,220));
        cb.setFont(new Font("SansSerif",Font.PLAIN,11));
        cb.addActionListener(e -> setter.set(cb.getSelectedIndex()));
        row.add(cb);
        content.add(row);
    }

    private JPanel rowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setBackground(new Color(28,28,38));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JLabel fieldLabel(String text) {
        // Truncate long labels
        String t = text.length() > 20 ? text.substring(0,18)+"…" : text;
        JLabel l = new JLabel(t);
        l.setForeground(new Color(160,160,180));
        l.setFont(new Font("SansSerif",Font.PLAIN,10));
        l.setPreferredSize(new Dimension(130, 20));
        return l;
    }
}