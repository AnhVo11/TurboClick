package nodes;

import engine.ExecutionContext;
import java.awt.*;
import java.awt.image.BufferedImage;

class WatchZoneNode extends BaseNode {
    public String imageName = "Unnamed Image";
    public BufferedImage template = null;
    public Rectangle watchZone = null;
    public int matchThreshold = 85;
    public int pollIntervalMs = 500;
    public int preTriggerDelayMs = 0;
    public int timeoutMs = 0;
    public boolean clickAtMatch = true;
    public int clickX = -1, clickY = -1;
    public int retryCount = 0;
    public WatchZoneNode(int x, int y) {
        super(NodeType.WATCH_ZONE, "Watch Zone", x, y);
        addOutputPort("Found"); addOutputPort("Not Found"); addOutputPort("Timeout");
    }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        if (template == null || watchZone == null) return "Not Found";
        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        int tries = 0;
        while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
            BufferedImage zoneImg = ctx.getRobot().createScreenCapture(watchZone);
            Point match = ImageMatcher.findTemplate(template, zoneImg, matchThreshold);
            if (match != null) {
                if (preTriggerDelayMs > 0) { ctx.setCountdown(imageName, preTriggerDelayMs); Thread.sleep(preTriggerDelayMs); ctx.clearCountdown(); }
                int cx = clickAtMatch ? watchZone.x + match.x : clickX;
                int cy = clickAtMatch ? watchZone.y + match.y : clickY;
                ctx.getRobot().mouseMove(cx, cy); Thread.sleep(60);
                ctx.getRobot().mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                ctx.getRobot().mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                return "Found";
            }
            tries++;
            if (retryCount > 0 && tries > retryCount) break;
            Thread.sleep(pollIntervalMs);
        }
        return timeoutMs > 0 ? "Timeout" : "Not Found";
    }
    @Override public Color nodeColor() { return new Color(50, 130, 200); }
    @Override public String nodeIcon() { return "W"; }
}

class ClickNode extends BaseNode {
    public int clickX = 0, clickY = 0;
    public int clickCount = 1;
    public int subDelayMs = 100;
    public int mouseButton = 0;
    public boolean doubleClick = false;
    public ClickNode(int x, int y) { super(NodeType.CLICK, "Click", x, y); addOutputPort("Done"); }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        int btn = mouseButton == 1 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : mouseButton == 2 ? java.awt.event.InputEvent.BUTTON2_DOWN_MASK : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        ctx.getRobot().mouseMove(clickX, clickY); Thread.sleep(40);
        for (int i = 0; i < clickCount; i++) {
            ctx.getRobot().mousePress(btn); ctx.getRobot().mouseRelease(btn);
            if (doubleClick) { Thread.sleep(40); ctx.getRobot().mousePress(btn); ctx.getRobot().mouseRelease(btn); }
            if (i < clickCount - 1) Thread.sleep(subDelayMs);
        }
        return "Done";
    }
    @Override public Color nodeColor() { return new Color(80, 160, 80); }
    @Override public String nodeIcon() { return "C"; }
}

class SimpleClickNode extends BaseNode {
    public java.util.List<int[]> points = new java.util.ArrayList<>();
    public long intervalMs = 100;
    public long maxClicks = 0;
    public int mouseButton = 0;
    public boolean doubleClick = false;
    public boolean waitToFinish = true;
    public boolean runInBackground = false;
    public boolean repeatUntilStopped = false;
    public int repeatTimes = 1;
    public SimpleClickNode(int x, int y) {
        super(NodeType.SIMPLE_CLICK, "Simple Click", x, y);
        height = 75; addOutputPort("Done"); addOutputPort("Stopped");
    }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        engine.SimpleClickEngine eng = new engine.SimpleClickEngine(points, intervalMs, maxClicks, mouseButton, doubleClick, repeatUntilStopped, repeatTimes, ctx);
        if (runInBackground) { new Thread(eng).start(); return "Done"; }
        eng.run();
        return eng.wasStopped() ? "Stopped" : "Done";
    }
    @Override public Color nodeColor() { return new Color(120, 60, 180); }
    @Override public String nodeIcon() { return "S"; }
}

class ConditionNode extends BaseNode {
    public String imageName = "Unnamed Image";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;
    public ConditionNode(int x, int y) {
        super(NodeType.CONDITION, "Condition", x, y);
        width = 140; height = 65; addOutputPort("Found"); addOutputPort("Not Found");
    }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        if (template == null || checkZone == null) return "Not Found";
        BufferedImage zoneImg = ctx.getRobot().createScreenCapture(checkZone);
        Point match = ImageMatcher.findTemplate(template, zoneImg, matchThreshold);
        return match != null ? "Found" : "Not Found";
    }
    @Override public Color nodeColor() { return new Color(200, 140, 30); }
    @Override public String nodeIcon() { return "?"; }
}

class LoopNode extends BaseNode {
    public enum LoopMode { FIXED_COUNT, UNTIL_FOUND, UNTIL_NOT_FOUND, FOREVER }
    public LoopMode loopMode = LoopMode.FIXED_COUNT;
    public int loopCount = 3;
    public int loopDelayMs = 0;
    public String imageName = "";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;
    private transient int currentIteration = 0;
    public LoopNode(int x, int y) { super(NodeType.LOOP, "Loop", x, y); addOutputPort("Loop"); addOutputPort("Done"); }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        if (loopDelayMs > 0) Thread.sleep(loopDelayMs);
        currentIteration++;
        switch (loopMode) {
            case FIXED_COUNT: if (currentIteration <= loopCount) return "Loop"; currentIteration = 0; return "Done";
            case FOREVER: return ctx.isRunning() ? "Loop" : "Done";
            case UNTIL_FOUND: case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null) return "Done";
                BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                boolean found = match != null;
                if (loopMode == LoopMode.UNTIL_FOUND) return found ? "Done" : "Loop";
                if (loopMode == LoopMode.UNTIL_NOT_FOUND) return found ? "Loop" : "Done";
        }
        return "Done";
    }
    public void resetIteration() { currentIteration = 0; }
    @Override public Color nodeColor() { return new Color(180, 80, 80); }
    @Override public String nodeIcon() { return "L"; }
}

class WaitNode extends BaseNode {
    public enum WaitMode { FIXED_DELAY, UNTIL_FOUND, UNTIL_NOT_FOUND }
    public WaitMode waitMode = WaitMode.FIXED_DELAY;
    public int delayMs = 1000;
    public int timeoutMs = 0;
    public String imageName = "";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;
    public int pollMs = 500;
    public WaitNode(int x, int y) { super(NodeType.WAIT, "Wait", x, y); addOutputPort("Done"); addOutputPort("Timeout"); }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        switch (waitMode) {
            case FIXED_DELAY:
                long end = System.currentTimeMillis() + delayMs;
                while (System.currentTimeMillis() < end && ctx.isRunning()) Thread.sleep(50);
                return "Done";
            case UNTIL_FOUND: case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null) return "Done";
                long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
                while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
                    BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                    Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                    boolean found = match != null;
                    if (waitMode == WaitMode.UNTIL_FOUND && found) return "Done";
                    if (waitMode == WaitMode.UNTIL_NOT_FOUND && !found) return "Done";
                    Thread.sleep(pollMs);
                }
                return "Timeout";
        }
        return "Done";
    }
    @Override public Color nodeColor() { return new Color(80, 150, 150); }
    @Override public String nodeIcon() { return "T"; }
}

class StopNode extends BaseNode {
    public enum StopMode { THIS_TREE, ALL_TREES }
    public StopMode stopMode = StopMode.THIS_TREE;
    public String customMessage = "";
    public boolean showMessage = false;
    public StopNode(int x, int y) { super(NodeType.STOP, "Stop", x, y); width = 120; height = 50; }
    @Override public String execute(ExecutionContext ctx) throws InterruptedException {
        if (stopMode == StopMode.ALL_TREES) ctx.stopAllTrees(); else ctx.stopThisTree();
        if (showMessage && !customMessage.isEmpty()) ctx.showMessage(customMessage);
        return null;
    }
    @Override public Color nodeColor() { return new Color(180, 50, 50); }
    @Override public String nodeIcon() { return "X"; }
}

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
            default: throw new IllegalArgumentException("Unknown: " + type);
        }
    }
    public static String displayName(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE: return "Watch Zone"; case CLICK: return "Click";
            case SIMPLE_CLICK: return "Simple Click"; case CONDITION: return "Condition";
            case LOOP: return "Loop"; case WAIT: return "Wait"; case STOP: return "Stop";
            default: return type.name();
        }
    }
    public static String icon(BaseNode.NodeType type) { return ""; }
    public static Color color(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE: return new Color(50,130,200); case CLICK: return new Color(80,160,80);
            case SIMPLE_CLICK: return new Color(120,60,180); case CONDITION: return new Color(200,140,30);
            case LOOP: return new Color(180,80,80); case WAIT: return new Color(80,150,150);
            case STOP: return new Color(180,50,50); default: return Color.GRAY;
        }
    }
}

class ImageMatcher {
    public static Point findTemplate(BufferedImage tmpl, BufferedImage zone, int thresholdPct) {
        if (tmpl == null || zone == null) return null;
        int tw = tmpl.getWidth(), th = tmpl.getHeight(), zw = zone.getWidth(), zh = zone.getHeight();
        if (tw > zw || th > zh) return null;
        int step = Math.max(1, Math.min(tw, th) / 6);
        double best = 0; int bestX = -1, bestY = -1;
        for (int y = 0; y <= zh - th; y += step)
            for (int x = 0; x <= zw - tw; x += step) {
                double score = matchScore(tmpl, zone, x, y, tw, th);
                if (score > best) { best = score; bestX = x; bestY = y; }
            }
        if (best * 100 >= thresholdPct && bestX >= 0) return new Point(bestX + tw/2, bestY + th/2);
        return null;
    }
    private static double matchScore(BufferedImage tmpl, BufferedImage zone, int ox, int oy, int tw, int th) {
        int samples = 0; long totalDiff = 0;
        int step = Math.max(1, Math.min(tw, th) / 8);
        for (int y = 0; y < th; y += step)
            for (int x = 0; x < tw; x += step) {
                Color tc = new Color(tmpl.getRGB(x, y)), rc = new Color(zone.getRGB(ox+x, oy+y));
                totalDiff += Math.abs(tc.getRed()-rc.getRed()) + Math.abs(tc.getGreen()-rc.getGreen()) + Math.abs(tc.getBlue()-rc.getBlue());
                samples++;
            }
        if (samples == 0) return 0;
        return 1.0 - (double) totalDiff / (samples * 255 * 3);
    }
}
