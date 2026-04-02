package nodes;

import engine.ExecutionContext;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseNode {

    public enum NodeType {
        WATCH_ZONE, CLICK, SIMPLE_CLICK, CONDITION, LOOP, WAIT, STOP, KEYBOARD, IMAGE, WATCH_CASE
    }

    public enum RunState {
        IDLE, RUNNING, SUCCESS, FAILED
    }

    // ── Identity ─────────────────────────────────────────────
    public final NodeType type;
    public String label;
    public String id;

    // ── Canvas position / size ───────────────────────────────
    public int x, y, width, height;

    // ── Branch control ───────────────────────────────────────
    public boolean branchEnabled = true;
    public boolean logTransition = false;
    public int entryDelayMs = 0;
    public int nodeLoopCount = 1; // loop feature: 1 = no loop

    // ── Runtime state ────────────────────────────────────────
    public volatile RunState runState = RunState.IDLE;
    private volatile long runStateTime = 0;

    // ── Ports ────────────────────────────────────────────────
    public List<NodePort> outputs = new ArrayList<>();
    public List<NodePort> inputs = new ArrayList<>();

    // ── Last match location ───────────────────────────────────
    public volatile int lastMatchX = -1;
    public volatile int lastMatchY = -1;

    public BaseNode(NodeType type, String label, int x, int y) {
        this.type = type;
        this.label = label;
        this.x = x;
        this.y = y;
        this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // ── Execution ─────────────────────────────────────────────
    public abstract String execute(ExecutionContext ctx) throws InterruptedException;

    // ── Appearance overrides ──────────────────────────────────
    public Color nodeColor() {
        return new Color(60, 80, 120);
    }

    public String nodeIcon() {
        return "\u25b6";
    }

    // ── Run state ─────────────────────────────────────────────
    public RunState getRunState() {
        return runState;
    }

    public void setRunState(RunState rs) {
        this.runState = rs;
        this.runStateTime = System.currentTimeMillis();
    }

    public boolean isRunStateExpired(long ms) {
        return System.currentTimeMillis() - runStateTime > ms;
    }

    // ── Port helpers ──────────────────────────────────────────
    protected NodePort addOutputPort(String name) {
        NodePort p = new NodePort(name);
        outputs.add(p);
        return p;
    }

    public NodePort addInputPort(String name) {
        NodePort p = new NodePort(name);
        inputs.add(p);
        return p;
    }

    public void setPortTarget(String portName, String targetNodeId) {
        for (NodePort p : outputs) {
            if (p.name.equals(portName)) {
                p.targetNodeId = targetNodeId;
                return;
            }
        }
    }

    // ── Geometry ─────────────────────────────────────────────
    public Rectangle bounds() {
        return new Rectangle(x, y, width, height);
    }

    public Point inputAnchor() {
        return new Point(x + width / 2, y);
    }

    public Point outputAnchor(String portName) {
        int n = outputs.size();
        if (n == 0)
            return new Point(x + width / 2, y + height);
        int spacing = width / (n + 1);
        for (int i = 0; i < n; i++) {
            if (outputs.get(i).name.equals(portName))
                return new Point(x + spacing * (i + 1), y + height);
        }
        return new Point(x + width / 2, y + height);
    }

    public Point leftInputAnchor(String portName) {
        int n = inputs.size();
        if (n == 0)
            return new Point(x, y + height / 2);
        int spacing = height / (n + 1);
        for (int i = 0; i < n; i++) {
            if (inputs.get(i).name.equals(portName))
                return new Point(x, y + spacing * (i + 1));
        }
        return new Point(x, y + height / 2);
    }

    public Point rightOutputAnchor(String portName) {
        List<NodePort> casePorts = new ArrayList<>();
        for (NodePort p : outputs) {
            if (!p.name.equals("Done") && !p.name.equals("None Found"))
                casePorts.add(p);
        }
        int n = casePorts.size();
        if (n == 0)
            return new Point(x + width, y + height / 2);
        int spacing = inputs.isEmpty() ? height / 2 : height / (inputs.size() + 1);
        for (int i = 0; i < n; i++) {
            if (casePorts.get(i).name.equals(portName))
                return new Point(x + width, y + spacing * (i + 1));
        }
        return new Point(x + width, y + height / 2);
    }

    // ── Port definition ───────────────────────────────────────
    public static class NodePort {
        public String name;
        public String customLabel = "";
        public String targetNodeId = null;
        public boolean enabled = true;
        public int arrowDelayMs = 0;

        public NodePort(String name) {
            this.name = name;
        }

        public NodePort(String name, String targetNodeId) {
            this.name = name;
            this.targetNodeId = targetNodeId;
        }

        public String displayLabel() {
            return customLabel.isEmpty() ? name : customLabel;
        }
    }
}