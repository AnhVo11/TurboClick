package nodes;

import engine.ExecutionContext;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WatchCaseNode extends BaseNode {

    // ── Image cases (shared across all zones) ─────────────────
    public List<WatchCase> cases = new ArrayList<>();
    public Map<String, String> inputPortToNodeId = new LinkedHashMap<>();

    // ── Watch zones (multiple) ────────────────────────────────
    public List<WatchZone> zones = new ArrayList<>();

    // ── Global settings ───────────────────────────────────────
    public int pollIntervalMs = 500;
    public boolean loopOnMatch = false; // keep cycling until nothing found anywhere
    public int loopDelayMs = 200;
    public boolean mergedOutput = false; // all cases → single Done port

    // ── Click modes ───────────────────────────────────────────
    public static final int CLICK_CENTER_MATCH = 0; // click where image was found
    public static final int CLICK_CENTER_ZONE = 1; // click center of zone rect
    public static final int CLICK_CUSTOM_PIN = 2; // click fixed custom coordinate
    public static final int CLICK_NONE = 3; // no click

    // ── Per-image case ────────────────────────────────────────
    public static class WatchCase {
        public String portName = "";
        public int threshold = 85;
        public boolean hasOutput = true;

        public WatchCase(String portName) {
            this.portName = portName;
        }
    }

    // ── Per-zone settings ─────────────────────────────────────
    public static class WatchZone {
        public String name = "Zone";
        public Rectangle rect = null;
        public int clickMode = CLICK_CENTER_MATCH; // default
        public int customPinX = 0, customPinY = 0; // for CLICK_CUSTOM_PIN
        public int clickDelayMs = 100;

        public WatchZone(String name) {
            this.name = name;
        }
    }

    public WatchCaseNode(int x, int y) {
        super(NodeType.WATCH_CASE, "Watch Case", x, y);
        width = 182;
        height = 122;
        // Add default first zone
        zones.add(new WatchZone("Zone 1"));
        addOutputPort("Done");
        addOutputPort("None Found");
    }

    // ── Case management ───────────────────────────────────────
    public void addCase(String portName) {
        if (cases.size() >= 8)
            return;
        cases.add(new WatchCase(portName));
        addInputPort(portName);
        if (!mergedOutput) {
            // Add output port before Done/None Found
            outputs.add(outputs.size() - 2 < 0 ? 0 : outputs.size() - 2,
                    new NodePort(portName, null));
        }
        rebuildHeight();
    }

    public void removeCase(String portName) {
        cases.removeIf(wc -> wc.portName.equals(portName));
        inputs.removeIf(p -> p.name.equals(portName));
        if (!mergedOutput)
            outputs.removeIf(p -> p.name.equals(portName));
        inputPortToNodeId.remove(portName);
        rebuildHeight();
    }

    public void rebuildHeight() {
        int caseH = cases.size() * 26;
        int zoneH = zones.size() * 20;
        height = Math.max(120, 60 + Math.max(caseH, zoneH));
    }

    public void renameCase(String oldName, String newName) {
        for (WatchCase wc : cases)
            if (wc.portName.equals(oldName))
                wc.portName = newName;
        for (NodePort p : inputs)
            if (p.name.equals(oldName)) {
                p.name = newName;
                p.customLabel = newName;
            }
        if (!mergedOutput)
            for (NodePort p : outputs)
                if (p.name.equals(oldName)) {
                    p.name = newName;
                    p.customLabel = newName;
                }
        String nodeId = inputPortToNodeId.remove(oldName);
        if (nodeId != null)
            inputPortToNodeId.put(newName, nodeId);
    }

    // ── Zone management ───────────────────────────────────────
    public WatchZone addZone() {
        WatchZone z = new WatchZone("Zone " + (zones.size() + 1));
        zones.add(z);
        rebuildHeight();
        return z;
    }

    public void removeZone(int idx) {
        if (idx >= 0 && idx < zones.size()) {
            zones.remove(idx);
            rebuildHeight();
        }
    }

    // ── Execute ───────────────────────────────────────────────
    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        Map<String, nodes.BaseNode> nodeMap = ctx.getNodeMap();
        if (nodeMap == null || zones.isEmpty())
            return "None Found";

        do {
            boolean anyMatchAcrossAllZones = false;

            // Process each zone in order
            for (WatchZone zone : zones) {
                if (!ctx.isRunning())
                    return "None Found";
                if (zone.rect == null)
                    continue;

                BufferedImage zoneImg = ctx.getRobot().createScreenCapture(zone.rect);

                // Check each image case against this zone — first match wins
                boolean zoneMatched = false;
                for (WatchCase wc : cases) {
                    if (!ctx.isRunning())
                        return "None Found";
                    String imgNodeId = inputPortToNodeId.get(wc.portName);
                    if (imgNodeId == null)
                        continue;
                    nodes.BaseNode imgNode = nodeMap.get(imgNodeId);
                    if (!(imgNode instanceof ImageNode))
                        continue;
                    ImageNode imageNode = (ImageNode) imgNode;
                    if (imageNode.template == null)
                        continue;

                    double[] result = ImageMatcher.findTemplateWithScore(
                            imageNode.template, zoneImg, wc.threshold);

                    ctx.setHudStatus("\u25c9 " + label,
                            zone.name + " \u2192 " + wc.portName + ": " +
                                    (result != null ? String.format("\u2713 %.0f%%", result[2]) : "0%"));

                    if (result != null) {
                        anyMatchAcrossAllZones = true;
                        zoneMatched = true;
                        performClick(ctx, zone, result);
                        break; // first match wins — stop checking other images for this zone
                    }
                }
                if (!zoneMatched) {
                    ctx.setHudStatus("\u25c9 " + label, zone.name + " \u2192 no match");
                }
            }

            if (loopOnMatch && anyMatchAcrossAllZones) {
                // At least one match found — loop again after delay
                if (loopDelayMs > 0)
                    Thread.sleep(loopDelayMs);
                Thread.sleep(pollIntervalMs);
                continue;
            } else if (loopOnMatch && !anyMatchAcrossAllZones) {
                // Nothing found anywhere — exit loop
                return "None Found";
            }

            // Non-loop mode
            return anyMatchAcrossAllZones ? "Done" : "None Found";

        } while (loopOnMatch && ctx.isRunning());

        return "None Found";
    }

    private void performClick(ExecutionContext ctx, WatchZone zone, double[] matchResult)
            throws InterruptedException {
        int cx, cy;
        switch (zone.clickMode) {
            case CLICK_NONE:
                return;
            case CLICK_CENTER_MATCH:
                cx = zone.rect.x + (int) matchResult[0];
                cy = zone.rect.y + (int) matchResult[1];
                break;
            case CLICK_CENTER_ZONE:
                cx = zone.rect.x + zone.rect.width / 2;
                cy = zone.rect.y + zone.rect.height / 2;
                break;
            case CLICK_CUSTOM_PIN:
                cx = zone.customPinX;
                cy = zone.customPinY;
                break;
            default:
                return;
        }
        ctx.getRobot().mouseMove(cx, cy);
        Thread.sleep(40);
        ctx.getRobot().mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        ctx.getRobot().mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        if (zone.clickDelayMs > 0)
            Thread.sleep(zone.clickDelayMs);
    }

    @Override
    public Color nodeColor() {
        return new Color(90, 100, 115);
    }

    @Override
    public String nodeIcon() {
        return "\u25c9";
    }
}