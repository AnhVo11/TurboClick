package engine;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.List;

// ═══════════════════════════════════════════════════════════════
//  EXECUTION CONTEXT
//  Passed to every node during execution. Holds runtime state,
//  robot reference, and callbacks into the running tree.
// ═══════════════════════════════════════════════════════════════
public class ExecutionContext {

    private volatile boolean running      = true;
    private volatile boolean treeStopped  = false;
    private final    Robot   robot;
    private final    String  treeId;
    private          TreeStopCallback stopAllCallback;
    private          HudCallback      hudCallback;
    private          MessageCallback  msgCallback;

    public ExecutionContext(Robot robot, String treeId) {
        this.robot  = robot;
        this.treeId = treeId;
    }

    // ── Robot ────────────────────────────────────────────────
    public Robot getRobot() { return robot; }

    // ── Running state ────────────────────────────────────────
    public boolean isRunning()    { return running && !treeStopped; }
    public void    stopThisTree() { treeStopped = true; running = false; }
    public void    forceStop()    { running = false; }

    public void stopAllTrees() {
        treeStopped = true; running = false;
        if (stopAllCallback != null) stopAllCallback.stopAll();
    }

    // ── Live HUD status ──────────────────────────────────────
    private volatile String hudNodeName   = "";
    private volatile String hudDetail     = "";
    private volatile StatusCallback statusCallback;

    public void setHudStatus(String nodeName, String detail) {
        hudNodeName = nodeName; hudDetail = detail;
        if (statusCallback != null) statusCallback.update(nodeName, detail);
    }
    public void setStatusCallback(StatusCallback cb) { statusCallback = cb; }
    public interface StatusCallback { void update(String nodeName, String detail); }

    // ── HUD countdown display ────────────────────────────────
    public void setCountdown(String imageName, int ms) {
        if (hudCallback != null) hudCallback.showCountdown(imageName, ms);
    }
    public void clearCountdown() {
        if (hudCallback != null) hudCallback.clear();
    }

    // ── Message notification ─────────────────────────────────
    public void showMessage(String msg) {
        if (msgCallback != null) msgCallback.show(msg);
    }

    // ── Callback setters ─────────────────────────────────────
    public void setStopAllCallback(TreeStopCallback cb) { stopAllCallback = cb; }
    public void setHudCallback(HudCallback cb)          { hudCallback     = cb; }
    public void setMessageCallback(MessageCallback cb)  { msgCallback     = cb; }

    // ── Callback interfaces ──────────────────────────────────
    public interface TreeStopCallback { void stopAll(); }
    public interface HudCallback      { void showCountdown(String name, int ms); void clear(); }
    public interface MessageCallback  { void show(String message); }
}