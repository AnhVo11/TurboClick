package nodes;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BaseNode — foundation for every node on the canvas.
 * Holds position, size, connections, label, and runtime state.
 */
public abstract class BaseNode {

    // ── Identity ─────────────────────────────────────────────
    public String id;
    public String label;
    public NodeType type;

    // ── Canvas geometry ──────────────────────────────────────
    public int x, y;          // top-left on canvas
    public int width  = 160;
    public int height = 60;

    // ── Connections ──────────────────────────────────────────
    // Each output port has a name (e.g. "Found","Not Found") and a target node id
    public List<NodePort> outputs = new ArrayList<>();
    public List<String>   inputIds = new ArrayList<>();  // nodes that point TO this one
    public List<NodePort>  inputs   = new ArrayList<>();   // named input ports (Watch Case)

    // ── Execution state (runtime, not saved) ─────────────────
    public enum RunState { IDLE, RUNNING, SUCCESS, FAILED, SKIPPED }
    public transient RunState runState = RunState.IDLE;
    public transient long     runStateTimestamp = 0;

    // ── Branch options ───────────────────────────────────────
    public boolean branchEnabled  = true;   // ☐ enabled/disabled
    public boolean logTransition  = false;  // ☐ log this node
    public int     entryDelayMs   = 0;      // delay before this node runs

    // ── Constructor ──────────────────────────────────────────
    public BaseNode(NodeType type, String label, int x, int y) {
        this.id    = UUID.randomUUID().toString().substring(0,8);
        this.type  = type;
        this.label = label;
        this.x     = x;
        this.y     = y;
    }

    // ── Abstract ─────────────────────────────────────────────
    /** Called by RuleEngine to execute this node. Returns the port name to follow next. */
    public abstract String execute(engine.ExecutionContext ctx) throws InterruptedException;

    /** Returns the color used to paint this node */
    public abstract Color nodeColor();

    /** Returns the icon character shown on the node */
    public abstract String nodeIcon();

    // ── Port helpers ─────────────────────────────────────────
    public void addInputPort(String portName) {
        inputs.add(new NodePort(portName, null));
    }

    public void addOutputPort(String portName) {
        outputs.add(new NodePort(portName, null));
    }

    public void setPortTarget(String portName, String targetNodeId) {
        for (NodePort p : outputs) {
            if (p.name.equals(portName)) { p.targetNodeId = targetNodeId; return; }
        }
    }

    public String getPortTarget(String portName) {
        for (NodePort p : outputs) if (p.name.equals(portName)) return p.targetNodeId;
        return null;
    }

    // ── Geometry helpers ─────────────────────────────────────
    public Rectangle bounds() { return new Rectangle(x, y, width, height); }
    public Point center()     { return new Point(x + width/2, y + height/2); }

    /** Output port anchor point for drawing arrows */
    public Point outputAnchor(String portName) {
        // For WATCH_CASE: hardcoded bottom positions matching canvas drawing
        if (type == NodeType.WATCH_CASE) {
            if (portName.equals("Done"))       return new Point(x + width/3,   y + height);
            if (portName.equals("None Found")) return new Point(x + width*2/3, y + height);
        }
        int idx = 0;
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i).name.equals(portName)) { idx = i; break; }
        int spacing = outputs.isEmpty() ? 0 : width / (outputs.size() + 1);
        return new Point(x + spacing * (idx + 1), y + height);
    }

    /** Input port anchor (top-center) — default for standard nodes */
    public Point inputAnchor() { return new Point(x + width/2, y); }

    /** Left-side input anchor for Watch Case named input ports */
    public Point leftInputAnchor(String portName) {
        int idx = 0;
        for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).name.equals(portName)) { idx = i; break; }
        int spacing = inputs.isEmpty() ? 0 : height / (inputs.size() + 1);
        return new Point(x, y + spacing * (idx + 1));
    }

    /** Right-side output anchor for Watch Case named output ports */
    public Point rightOutputAnchor(String portName) {
        int idx = 0;
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i).name.equals(portName)) { idx = i; break; }
        int spacing = outputs.isEmpty() ? 0 : height / (outputs.size() + 1);
        return new Point(x + width, y + spacing * (idx + 1));
    }

    // ── Runtime state helpers ────────────────────────────────
    public void setRunState(RunState s) {
        runState = s;
        runStateTimestamp = System.currentTimeMillis();
    }

    public boolean isRunStateExpired(long ttlMs) {
        return System.currentTimeMillis() - runStateTimestamp > ttlMs;
    }

    // ── Port class ───────────────────────────────────────────
    public static class NodePort {
        public String name;
        public String targetNodeId;
        public String customLabel;   // user-editable label on the arrow
        public boolean enabled = true;
        public int     arrowDelayMs = 0;

        public NodePort(String name, String targetNodeId) {
            this.name = name;
            this.targetNodeId = targetNodeId;
            this.customLabel = name;
        }
        public String displayLabel() {
            return (customLabel != null && !customLabel.isEmpty()) ? customLabel : name;
        }
    }

    // ── Node types enum ──────────────────────────────────────
    public enum NodeType {
        WATCH_ZONE, CLICK, SIMPLE_CLICK, CONDITION, LOOP, WAIT, STOP, KEYBOARD, IMAGE, WATCH_CASE
    }
}