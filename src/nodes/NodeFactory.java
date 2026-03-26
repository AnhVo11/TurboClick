package nodes;

import engine.ExecutionContext;
import java.awt.*;
import java.awt.image.BufferedImage;

// ═══════════════════════════════════════════════════════════════
//  WATCH ZONE NODE
// ═══════════════════════════════════════════════════════════════
class WatchZoneNode extends BaseNode {

    public String        imageName         = "Unnamed Image";
    public BufferedImage template          = null;
    public Rectangle     watchZone         = null;
    public Rectangle     captureRect       = null;   // rect used when capturing template
    public boolean       sameAsCapture     = false;  // use captureRect as watchZone
    public int           matchThreshold    = 85;
    public int           pollIntervalMs    = 500;
    public int           preTriggerDelayMs = 0;
    public int           timeoutMs         = 0;
    public boolean       clickAtMatch      = true;
    public int           clickX = -1, clickY = -1;
    public int           retryCount        = 0;

    public WatchZoneNode(int x, int y) {
        super(NodeType.WATCH_ZONE, "Watch Zone", x, y);
        addOutputPort("Found");
        addOutputPort("Not Found");
        addOutputPort("Timeout");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        // If sameAsCapture, use the captureRect as the watchZone at runtime
        if (sameAsCapture && captureRect != null) watchZone = captureRect;
        if (template == null || watchZone == null) return "Not Found";

        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        int  tries    = 0;

        while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
            BufferedImage zoneImg = ctx.getRobot().createScreenCapture(watchZone);
            double[] result = ImageMatcher.findTemplateWithScore(template, zoneImg, matchThreshold);

            // Report current match % to HUD
            double pct = result != null ? result[2] : 0;
            ctx.setHudStatus("👁 " + imageName, String.format("Match: %.0f%%", pct));

            if (result != null) {
                Point match = new Point((int)result[0], (int)result[1]);
                if (preTriggerDelayMs > 0) {
                    ctx.setCountdown(imageName, preTriggerDelayMs);
                    Thread.sleep(preTriggerDelayMs);
                    ctx.clearCountdown();
                }
                int cx = clickAtMatch ? watchZone.x + match.x : clickX;
                int cy = clickAtMatch ? watchZone.y + match.y : clickY;
                ctx.getRobot().mouseMove(cx, cy);
                Thread.sleep(60);
                ctx.getRobot().mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                ctx.getRobot().mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                ctx.setHudStatus("👁 " + imageName, "✓ Found!");
                return "Found";
            }

            tries++;
            if (retryCount > 0 && tries > retryCount) break;
            Thread.sleep(pollIntervalMs);
        }

        return timeoutMs > 0 ? "Timeout" : "Not Found";
    }

    @Override public Color  nodeColor() { return new Color(50, 130, 200); }
    @Override public String nodeIcon()  { return "◎"; }
}

// ═══════════════════════════════════════════════════════════════
//  CLICK NODE
// ═══════════════════════════════════════════════════════════════
class ClickNode extends BaseNode {

    public int     clickX      = 0, clickY = 0;
    public int     clickCount  = 1;
    public int     subDelayMs  = 100;
    public int     mouseButton = 0;   // 0=left,1=right,2=middle
    public boolean doubleClick = false;

    public ClickNode(int x, int y) {
        super(NodeType.CLICK, "Click", x, y);
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        int btn = mouseButton == 1 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK
                : mouseButton == 2 ? java.awt.event.InputEvent.BUTTON2_DOWN_MASK
                :                    java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        ctx.getRobot().mouseMove(clickX, clickY);
        Thread.sleep(40);
        for (int i = 0; i < clickCount; i++) {
            ctx.getRobot().mousePress(btn);
            ctx.getRobot().mouseRelease(btn);
            if (doubleClick) { Thread.sleep(40); ctx.getRobot().mousePress(btn); ctx.getRobot().mouseRelease(btn); }
            if (i < clickCount - 1) Thread.sleep(subDelayMs);
        }
        return "Done";
    }

    @Override public Color  nodeColor() { return new Color(80, 160, 80); }
    @Override public String nodeIcon()  { return "↗"; }
}

// ═══════════════════════════════════════════════════════════════
//  SIMPLE CLICK NODE  (embedded full auto-clicker)
// ═══════════════════════════════════════════════════════════════
class SimpleClickNode extends BaseNode {

    // Mirror of SimpleClickEngine settings
    public java.util.List<int[]> points    = new java.util.ArrayList<>(); // {x,y,clicks,subDelay,delayAfter}
    public long   intervalMs    = 100;
    public long   maxClicks     = 0;       // 0 = ∞
    public int    mouseButton   = 0;
    public boolean doubleClick  = false;
    public boolean waitToFinish = true;    // ☐ wait to finish before next node
    public boolean runInBackground = false;// ☐ run in background
    public boolean repeatUntilStopped = false;
    public int     repeatTimes  = 1;       // if not repeatUntilStopped

    public SimpleClickNode(int x, int y) {
        super(NodeType.SIMPLE_CLICK, "Simple Click", x, y);
        height = 75;
        addOutputPort("Done");
        addOutputPort("Stopped");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        ctx.setHudStatus("⊕ " + label, "Clicks: 0");
        engine.SimpleClickEngine eng = new engine.SimpleClickEngine(
            points, intervalMs, maxClicks, mouseButton, doubleClick,
            repeatUntilStopped, repeatTimes, ctx
        );
        eng.setClickCallback(total -> ctx.setHudStatus("⊕ " + label, "Clicks: " + total));
        if (runInBackground) {
            new Thread(eng).start();
            return "Done";
        }
        eng.run();
        return eng.wasStopped() ? "Stopped" : "Done";
    }

    @Override public Color  nodeColor() { return new Color(120, 60, 180); }
    @Override public String nodeIcon()  { return "⊕"; }
}

// ═══════════════════════════════════════════════════════════════
//  CONDITION NODE
// ═══════════════════════════════════════════════════════════════
class ConditionNode extends BaseNode {

    public String       imageName      = "Unnamed Image";
    public BufferedImage template      = null;
    public Rectangle    checkZone      = null;
    public int          matchThreshold = 85;

    public ConditionNode(int x, int y) {
        super(NodeType.CONDITION, "Condition", x, y);
        width = 140; height = 65;
        addOutputPort("Found");
        addOutputPort("Not Found");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (template == null || checkZone == null) return "Not Found";
        BufferedImage zoneImg = ctx.getRobot().createScreenCapture(checkZone);
        Point match = ImageMatcher.findTemplate(template, zoneImg, matchThreshold);
        return match != null ? "Found" : "Not Found";
    }

    @Override public Color  nodeColor() { return new Color(200, 140, 30); }
    @Override public String nodeIcon()  { return "◇"; }
}

// ═══════════════════════════════════════════════════════════════
//  LOOP NODE
// ═══════════════════════════════════════════════════════════════
class LoopNode extends BaseNode {

    public enum LoopMode { FIXED_COUNT, UNTIL_FOUND, UNTIL_NOT_FOUND, FOREVER }
    public LoopMode     loopMode       = LoopMode.FIXED_COUNT;
    public int          loopCount      = 3;
    public int          loopDelayMs    = 0;
    public String       imageName      = "";
    public BufferedImage template      = null;
    public Rectangle    checkZone      = null;
    public int          matchThreshold = 85;

    private transient int currentIteration = 0;

    public LoopNode(int x, int y) {
        super(NodeType.LOOP, "Loop", x, y);
        addOutputPort("Loop");
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (loopDelayMs > 0) Thread.sleep(loopDelayMs);
        currentIteration++;

        switch (loopMode) {
            case FIXED_COUNT:
                if (currentIteration <= loopCount) return "Loop";
                currentIteration = 0; return "Done";

            case FOREVER:
                return ctx.isRunning() ? "Loop" : "Done";

            case UNTIL_FOUND:
            case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null) return "Done";
                BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                boolean found = match != null;
                if (loopMode == LoopMode.UNTIL_FOUND)     return found ? "Done" : "Loop";
                if (loopMode == LoopMode.UNTIL_NOT_FOUND) return found ? "Loop" : "Done";
        }
        return "Done";
    }

    public void resetIteration() { currentIteration = 0; }

    @Override public Color  nodeColor() { return new Color(180, 80, 80); }
    @Override public String nodeIcon()  { return "↺"; }
}

// ═══════════════════════════════════════════════════════════════
//  WAIT NODE
// ═══════════════════════════════════════════════════════════════
class WaitNode extends BaseNode {

    public enum WaitMode { FIXED_DELAY, UNTIL_FOUND, UNTIL_NOT_FOUND }
    public WaitMode     waitMode       = WaitMode.FIXED_DELAY;
    public int          delayMs        = 1000;
    public int          timeoutMs      = 0;
    public String       imageName      = "";
    public BufferedImage template      = null;
    public Rectangle    checkZone      = null;
    public int          matchThreshold = 85;
    public int          pollMs         = 500;

    public WaitNode(int x, int y) {
        super(NodeType.WAIT, "Wait", x, y);
        addOutputPort("Done");
        addOutputPort("Timeout");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        switch (waitMode) {
            case FIXED_DELAY:
                long end = System.currentTimeMillis() + delayMs;
                while (System.currentTimeMillis() < end && ctx.isRunning()) Thread.sleep(50);
                return "Done";

            case UNTIL_FOUND:
            case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null) return "Done";
                long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
                while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
                    BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                    Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                    boolean found = match != null;
                    if (waitMode == WaitMode.UNTIL_FOUND && found)     return "Done";
                    if (waitMode == WaitMode.UNTIL_NOT_FOUND && !found) return "Done";
                    Thread.sleep(pollMs);
                }
                return "Timeout";
        }
        return "Done";
    }

    @Override public Color  nodeColor() { return new Color(80, 150, 150); }
    @Override public String nodeIcon()  { return "⏱"; }
}

// ═══════════════════════════════════════════════════════════════
//  STOP NODE
// ═══════════════════════════════════════════════════════════════
class StopNode extends BaseNode {

    public enum StopMode { THIS_TREE, ALL_TREES }
    public StopMode stopMode       = StopMode.THIS_TREE;
    public String   customMessage  = "";
    public boolean  showMessage    = false;

    public StopNode(int x, int y) {
        super(NodeType.STOP, "Stop", x, y);
        width = 120; height = 50;
        // No output ports — terminal node
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (stopMode == StopMode.ALL_TREES) ctx.stopAllTrees();
        else ctx.stopThisTree();
        if (showMessage && !customMessage.isEmpty()) ctx.showMessage(customMessage);
        return null; // terminal
    }

    @Override public Color  nodeColor() { return new Color(180, 50, 50); }
    @Override public String nodeIcon()  { return "■"; }
}

// ═══════════════════════════════════════════════════════════════
//  NODE FACTORY  — create nodes by type
// ═══════════════════════════════════════════════════════════════
public class NodeFactory {

    public static BaseNode create(BaseNode.NodeType type, int x, int y) {
        switch (type) {
            case WATCH_ZONE:   return new WatchZoneNode(x, y);
            case CLICK:        return new ClickNode(x, y);
            case SIMPLE_CLICK: return new SimpleClickNode(x, y);
            case CONDITION:    return new ConditionNode(x, y);
            case LOOP:         return new LoopNode(x, y);
            case WAIT:         return new WaitNode(x, y);
            case STOP:         return new StopNode(x, y);
            default:           throw new IllegalArgumentException("Unknown node type: " + type);
        }
    }

    /** Display name for palette */
    public static String displayName(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:   return "Watch Zone";
            case CLICK:        return "Click";
            case SIMPLE_CLICK: return "Simple Click";
            case CONDITION:    return "Condition";
            case LOOP:         return "Loop";
            case WAIT:         return "Wait";
            case STOP:         return "Stop";
            default:           return type.name();
        }
    }

    /** Icon for palette */
    public static String icon(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:   return "👁";
            case CLICK:        return "🖱";
            case SIMPLE_CLICK: return "⚡";
            case CONDITION:    return "◇";
            case LOOP:         return "↺";
            case WAIT:         return "⏱";
            case STOP:         return "■";
            default:           return "?";
        }
    }

    /** Color for palette */
    public static Color color(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:   return new Color(50, 130, 200);
            case CLICK:        return new Color(80, 160, 80);
            case SIMPLE_CLICK: return new Color(120, 60, 180);
            case CONDITION:    return new Color(200, 140, 30);
            case LOOP:         return new Color(180, 80, 80);
            case WAIT:         return new Color(80, 150, 150);
            case STOP:         return new Color(180, 50, 50);
            default:           return Color.GRAY;
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  IMAGE MATCHER  — shared pixel matching utility
// ═══════════════════════════════════════════════════════════════
class ImageMatcher {

    public static Point findTemplate(java.awt.image.BufferedImage tmpl,
                                     java.awt.image.BufferedImage zone,
                                     int thresholdPct) {
        double[] result = findTemplateWithScore(tmpl, zone, thresholdPct);
        if (result == null) return null;
        return new Point((int)result[0], (int)result[1]);
    }

    /** Returns [centerX, centerY, scorePct] or null if below threshold */
    public static double[] findTemplateWithScore(java.awt.image.BufferedImage tmpl,
                                                  java.awt.image.BufferedImage zone,
                                                  int thresholdPct) {
        if (tmpl == null || zone == null) return null;
        int tw = tmpl.getWidth(), th = tmpl.getHeight();
        int zw = zone.getWidth(),  zh = zone.getHeight();
        if (tw > zw || th > zh) return null;

        int step = Math.max(1, Math.min(tw, th) / 6);
        double best = 0; int bestX = -1, bestY = -1;

        for (int y = 0; y <= zh - th; y += step) {
            for (int x = 0; x <= zw - tw; x += step) {
                double score = matchScore(tmpl, zone, x, y, tw, th);
                if (score > best) { best = score; bestX = x; bestY = y; }
            }
        }
        if (best * 100 >= thresholdPct && bestX >= 0)
            return new double[]{ bestX + tw/2.0, bestY + th/2.0, best * 100 };
        return null;
    }

    private static double matchScore(java.awt.image.BufferedImage tmpl,
                                     java.awt.image.BufferedImage zone,
                                     int ox, int oy, int tw, int th) {
        int samples = 0; long totalDiff = 0;
        int step = Math.max(1, Math.min(tw, th) / 8);
        for (int y = 0; y < th; y += step) {
            for (int x = 0; x < tw; x += step) {
                Color tc = new Color(tmpl.getRGB(x, y));
                Color rc = new Color(zone.getRGB(ox + x, oy + y));
                totalDiff += Math.abs(tc.getRed()  - rc.getRed())
                           + Math.abs(tc.getGreen()- rc.getGreen())
                           + Math.abs(tc.getBlue() - rc.getBlue());
                samples++;
            }
        }
        if (samples == 0) return 0;
        return 1.0 - (double) totalDiff / (samples * 255 * 3);
    }
}