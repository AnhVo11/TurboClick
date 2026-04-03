package ui;

import nodes.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

public class TaskSerializer {

    // ── Save ─────────────────────────────────────────────────
    public static void saveTask(String taskName, String startNodeId,
            Map<String, nodes.BaseNode> nodeMap,
            List<NodeCanvas.Arrow> arrows,
            File file) throws Exception {
        SaveFormat.TaskFile tf = new SaveFormat.TaskFile();
        tf.taskName = taskName;
        tf.startNodeId = startNodeId;
        tf.savedAt = java.time.LocalDate.now().toString();

        for (nodes.BaseNode node : nodeMap.values())
            tf.nodes.add(nodeToData(node, nodeMap));

        for (NodeCanvas.Arrow a : arrows) {
            SaveFormat.ArrowData ad = new SaveFormat.ArrowData();
            ad.fromNodeId = a.fromNodeId;
            ad.fromPort = a.fromPort;
            ad.toNodeId = a.toNodeId;
            ad.label = a.label;
            ad.bendOffset = a.bendOffset;
            tf.arrows.add(ad);
        }

        writeJson(tf, file);
    }

    // ── Save subset (selected nodes only) ────────────────────
    public static void saveNodes(String taskName,
            Collection<nodes.BaseNode> selectedNodes,
            List<NodeCanvas.Arrow> allArrows,
            Map<String, nodes.BaseNode> nodeMap,
            File file) throws Exception {
        SaveFormat.TaskFile tf = new SaveFormat.TaskFile();
        tf.taskName = taskName;

        Set<String> ids = new HashSet<>();
        for (nodes.BaseNode n : selectedNodes)
            ids.add(n.id);

        for (nodes.BaseNode n : selectedNodes)
            tf.nodes.add(nodeToData(n, nodeMap));

        // Only save arrows where BOTH ends are in selection
        for (NodeCanvas.Arrow a : allArrows)
            if (ids.contains(a.fromNodeId) && ids.contains(a.toNodeId)) {
                SaveFormat.ArrowData ad = new SaveFormat.ArrowData();
                ad.fromNodeId = a.fromNodeId;
                ad.fromPort = a.fromPort;
                ad.toNodeId = a.toNodeId;
                ad.label = a.label;
                ad.bendOffset = a.bendOffset;
                tf.arrows.add(ad);
            }

        writeJson(tf, file);
    }

    // ── Load ─────────────────────────────────────────────────
    public static SaveFormat.TaskFile load(String json) throws Exception {
        return parseJson(json);
    }

    // ── Apply loaded file to a canvas ────────────────────────
    public static String applyToCanvas(SaveFormat.TaskFile tf,
            NodeCanvas canvas,
            int offsetX, int offsetY) {
        Map<String, String> idRemap = new HashMap<>(); // old→new id

        for (SaveFormat.NodeData nd : tf.nodes) {
            nodes.BaseNode node = dataToNode(nd, offsetX, offsetY);
            if (node == null)
                continue; // unknown type — skip
            String oldId = nd.id;
            idRemap.put(oldId, node.id); // new UUID assigned in constructor
            canvas.addNode(node);
        }

        // Re-wire arrows with remapped ids
        for (SaveFormat.ArrowData ad : tf.arrows) {
            String newFrom = idRemap.get(ad.fromNodeId);
            String newTo = idRemap.get(ad.toNodeId);
            if (newFrom == null || newTo == null)
                continue;
            nodes.BaseNode fromNode = canvas.getNodes().get(newFrom);
            if (fromNode == null)
                continue;
            fromNode.setPortTarget(ad.fromPort, newTo);
            NodeCanvas.Arrow arrow = new NodeCanvas.Arrow(newFrom, ad.fromPort, newTo, ad.label);
            arrow.bendOffset = ad.bendOffset;
            canvas.getArrows().add(arrow);
        }

        // Return remapped start node id
        if (tf.startNodeId != null && idRemap.containsKey(tf.startNodeId))
            return idRemap.get(tf.startNodeId);
        return null;
    }

    // ── Node → Data ───────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static SaveFormat.NodeData nodeToData(nodes.BaseNode node,
            Map<String, nodes.BaseNode> nodeMap) {
        SaveFormat.NodeData d = new SaveFormat.NodeData();
        d.type = node.type.name();
        d.id = node.id;
        d.label = node.label;
        d.x = node.x;
        d.y = node.y;
        d.width = node.width;
        d.height = node.height;
        d.branchEnabled = node.branchEnabled;
        d.entryDelayMs = node.entryDelayMs;
        d.nodeLoopCount = node.nodeLoopCount;

        try {
            switch (node.type) {
                case WATCH_ZONE: {
                    d.imageName = str(node, "imageName");
                    d.templateBase64 = imgToBase64(img(node, "template"));
                    d.watchZone = rectToArr(rect(node, "watchZone"));
                    d.captureRect = rectToArr(rect(node, "captureRect"));
                    d.matchThreshold = intF(node, "matchThreshold");
                    d.pollIntervalMs = intF(node, "pollIntervalMs");
                    d.preTriggerDelayMs = intF(node, "preTriggerDelayMs");
                    d.timeoutMs = intF(node, "timeoutMs");
                    d.clickAtMatch = boolF(node, "clickAtMatch");
                    d.clickX = intF(node, "clickX");
                    d.clickY = intF(node, "clickY");
                    d.retryCount = intF(node, "retryCount");
                    break;
                }
                case CLICK: {
                    d.clickX = intF(node, "clickX");
                    d.clickY = intF(node, "clickY");
                    d.clickCount = intF(node, "clickCount");
                    d.subDelayMs = intF(node, "subDelayMs");
                    d.mouseButton = intF(node, "mouseButton");
                    d.doubleClick = boolF(node, "doubleClick");
                    break;
                }
                case SIMPLE_CLICK: {
                    d.points = (List<int[]>) getF(node, "points");
                    d.intervalMs = longF(node, "intervalMs");
                    d.maxClicks = longF(node, "maxClicks");
                    d.mouseButton = intF(node, "mouseButton");
                    d.doubleClick = boolF(node, "doubleClick");
                    d.repeatUntilStopped = boolF(node, "repeatUntilStopped");
                    d.repeatTimes = intF(node, "repeatTimes");
                    d.waitToFinish = boolF(node, "waitToFinish");
                    d.runInBackground = boolF(node, "runInBackground");
                    break;
                }
                case CONDITION: {
                    d.imageName = str(node, "imageName");
                    d.templateBase64 = imgToBase64(img(node, "template"));
                    d.checkZone = rectToArr(rect(node, "checkZone"));
                    d.matchThreshold = intF(node, "matchThreshold");
                    break;
                }
                case LOOP: {
                    Object lm = getF(node, "loopMode");
                    d.loopMode = lm != null ? lm.toString() : null;
                    d.loopCount = intF(node, "loopCount");
                    d.loopDelayMs = intF(node, "loopDelayMs");
                    d.matchThreshold = intF(node, "matchThreshold");
                    d.templateBase64 = imgToBase64(img(node, "template"));
                    d.checkZone = rectToArr(rect(node, "checkZone"));
                    break;
                }
                case WAIT: {
                    Object wm = getF(node, "waitMode");
                    d.waitMode = wm != null ? wm.toString() : null;
                    d.delayMs = intF(node, "delayMs");
                    d.timeoutMs = intF(node, "timeoutMs");
                    d.pollMs = intF(node, "pollMs");
                    d.matchThreshold = intF(node, "matchThreshold");
                    d.templateBase64 = imgToBase64(img(node, "template"));
                    d.checkZone = rectToArr(rect(node, "checkZone"));
                    break;
                }
                case STOP: {
                    Object sm = getF(node, "stopMode");
                    d.stopMode = sm != null ? sm.toString() : null;
                    d.showMessage = boolF(node, "showMessage");
                    d.customMessage = str(node, "customMessage");
                    break;
                }
                case KEYBOARD: {
                    d.kbMode = intF(node, "mode");
                    d.typeText = str(node, "typeText");
                    d.charDelayMs = intF(node, "charDelayMs");
                    d.hotkeyCombo = str(node, "hotkeyCombo");
                    d.singleKey = str(node, "singleKey");
                    d.repeatCount = intF(node, "repeatCount");
                    d.repeatDelayMs = intF(node, "repeatDelayMs");
                    break;
                }
                case IMAGE: {
                    d.imageName = str(node, "imageName");
                    d.templateBase64 = imgToBase64(img(node, "template"));
                    d.threshold = intF(node, "threshold");
                    break;
                }
                case WATCH_CASE: {
                    if (!(node instanceof nodes.WatchCaseNode))
                        break;
                    nodes.WatchCaseNode wc = (nodes.WatchCaseNode) node;
                    d.wcPollIntervalMs = wc.pollIntervalMs;
                    d.wcLoopOnMatch = wc.loopOnMatch;
                    d.wcLoopDelayMs = wc.loopDelayMs;
                    d.wcCases = new ArrayList<>();
                    for (nodes.WatchCaseNode.WatchCase c : wc.cases) {
                        SaveFormat.WatchCaseData cd = new SaveFormat.WatchCaseData();
                        cd.portName = c.portName;
                        cd.threshold = c.threshold;
                        cd.hasOutput = c.hasOutput;
                        cd.imageNodeId = wc.inputPortToNodeId.get(c.portName);
                        d.wcCases.add(cd);
                    }
                    d.wcZones = new ArrayList<>();
                    for (nodes.WatchCaseNode.WatchZone z : wc.zones) {
                        SaveFormat.WatchZoneData zd = new SaveFormat.WatchZoneData();
                        zd.name = z.name;
                        zd.rect = rectToArr(z.rect);
                        zd.clickMode = z.clickMode;
                        zd.customPinX = z.customPinX;
                        zd.customPinY = z.customPinY;
                        zd.clickDelayMs = z.clickDelayMs;
                        d.wcZones.add(zd);
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return d;
    }

    // ── Data → Node ───────────────────────────────────────────
    private static nodes.BaseNode dataToNode(SaveFormat.NodeData d, int ox, int oy) {
        nodes.BaseNode.NodeType type;
        try {
            type = nodes.BaseNode.NodeType.valueOf(d.type);
        } catch (Exception e) {
            return null;
        } // unknown type

        nodes.BaseNode node = nodes.NodeFactory.create(type, d.x + ox, d.y + oy);
        // Restore saved id so arrows can be remapped
        node.id = d.id;
        node.label = d.label != null ? d.label : node.label;
        node.width = d.width;
        node.height = d.height;
        node.branchEnabled = d.branchEnabled;
        node.entryDelayMs = d.entryDelayMs;
        node.nodeLoopCount = d.nodeLoopCount;

        try {
            switch (type) {
                case WATCH_ZONE: {
                    setF(node, "imageName", d.imageName);
                    setF(node, "template", base64ToImg(d.templateBase64));
                    setF(node, "watchZone", arrToRect(d.watchZone));
                    setF(node, "captureRect", arrToRect(d.captureRect));
                    setI(node, "matchThreshold", d.matchThreshold, 85);
                    setI(node, "pollIntervalMs", d.pollIntervalMs, 500);
                    setI(node, "preTriggerDelayMs", d.preTriggerDelayMs, 0);
                    setI(node, "timeoutMs", d.timeoutMs, 0);
                    setB(node, "clickAtMatch", d.clickAtMatch, true);
                    setI(node, "clickX", d.clickX, -1);
                    setI(node, "clickY", d.clickY, -1);
                    setI(node, "retryCount", d.retryCount, 0);
                    break;
                }
                case CLICK: {
                    setI(node, "clickX", d.clickX, 0);
                    setI(node, "clickY", d.clickY, 0);
                    setI(node, "clickCount", d.clickCount, 1);
                    setI(node, "subDelayMs", d.subDelayMs, 100);
                    setI(node, "mouseButton", d.mouseButton, 0);
                    setB(node, "doubleClick", d.doubleClick, false);
                    break;
                }
                case SIMPLE_CLICK: {
                    if (d.points != null)
                        setF(node, "points", new ArrayList<>(d.points));
                    if (d.intervalMs != null)
                        setF(node, "intervalMs", d.intervalMs);
                    if (d.maxClicks != null)
                        setF(node, "maxClicks", d.maxClicks);
                    setI(node, "mouseButton", d.mouseButton, 0);
                    setB(node, "doubleClick", d.doubleClick, false);
                    setB(node, "repeatUntilStopped", d.repeatUntilStopped, false);
                    setI(node, "repeatTimes", d.repeatTimes, 1);
                    setB(node, "waitToFinish", d.waitToFinish, true);
                    setB(node, "runInBackground", d.runInBackground, false);
                    break;
                }
                case CONDITION: {
                    setF(node, "imageName", d.imageName);
                    setF(node, "template", base64ToImg(d.templateBase64));
                    setF(node, "checkZone", arrToRect(d.checkZone));
                    setI(node, "matchThreshold", d.matchThreshold, 85);
                    break;
                }
                case LOOP: {
                    if (d.loopMode != null) {
                        try {
                            java.lang.reflect.Field lmf = field(node, "loopMode");
                            for (Object e : lmf.getType().getEnumConstants())
                                if (e.toString().equals(d.loopMode)) {
                                    lmf.set(node, e);
                                    break;
                                }
                        } catch (Exception ignored) {
                        }
                    }
                    setI(node, "loopCount", d.loopCount, 3);
                    setI(node, "loopDelayMs", d.loopDelayMs, 0);
                    setI(node, "matchThreshold", d.matchThreshold, 85);
                    setF(node, "template", base64ToImg(d.templateBase64));
                    setF(node, "checkZone", arrToRect(d.checkZone));
                    break;
                }
                case WAIT: {
                    if (d.waitMode != null) {
                        try {
                            java.lang.reflect.Field wmf = field(node, "waitMode");
                            for (Object e : wmf.getType().getEnumConstants())
                                if (e.toString().equals(d.waitMode)) {
                                    wmf.set(node, e);
                                    break;
                                }
                        } catch (Exception ignored) {
                        }
                    }
                    setI(node, "delayMs", d.delayMs, 1000);
                    setI(node, "timeoutMs", d.timeoutMs, 0);
                    setI(node, "pollMs", d.pollMs, 500);
                    setI(node, "matchThreshold", d.matchThreshold, 85);
                    setF(node, "template", base64ToImg(d.templateBase64));
                    setF(node, "checkZone", arrToRect(d.checkZone));
                    break;
                }
                case STOP: {
                    if (d.stopMode != null) {
                        try {
                            java.lang.reflect.Field smf = field(node, "stopMode");
                            for (Object e : smf.getType().getEnumConstants())
                                if (e.toString().equals(d.stopMode)) {
                                    smf.set(node, e);
                                    break;
                                }
                        } catch (Exception ignored) {
                        }
                    }
                    setB(node, "showMessage", d.showMessage, false);
                    setF(node, "customMessage", d.customMessage);
                    break;
                }
                case KEYBOARD: {
                    setI(node, "mode", d.kbMode, 0);
                    setF(node, "typeText", d.typeText);
                    setI(node, "charDelayMs", d.charDelayMs, 50);
                    setF(node, "hotkeyCombo", d.hotkeyCombo);
                    setF(node, "singleKey", d.singleKey);
                    setI(node, "repeatCount", d.repeatCount, 1);
                    setI(node, "repeatDelayMs", d.repeatDelayMs, 100);
                    break;
                }
                case IMAGE: {
                    setF(node, "imageName", d.imageName);
                    setF(node, "template", base64ToImg(d.templateBase64));
                    setI(node, "threshold", d.threshold, 85);
                    node.label = d.imageName != null ? d.imageName : node.label;
                    break;
                }
                case WATCH_CASE: {
                    if (!(node instanceof nodes.WatchCaseNode))
                        break;
                    nodes.WatchCaseNode wc = (nodes.WatchCaseNode) node;
                    if (d.wcPollIntervalMs != null)
                        wc.pollIntervalMs = d.wcPollIntervalMs;
                    if (d.wcLoopOnMatch != null)
                        wc.loopOnMatch = d.wcLoopOnMatch;
                    if (d.wcLoopDelayMs != null)
                        wc.loopDelayMs = d.wcLoopDelayMs;
                    wc.cases.clear();
                    wc.zones.clear();
                    wc.inputs.clear();
                    // Remove auto-added Done/None Found, re-add after cases
                    wc.outputs.removeIf(p -> !p.name.equals("Done") && !p.name.equals("None Found"));
                    if (d.wcCases != null)
                        for (SaveFormat.WatchCaseData cd : d.wcCases) {
                            nodes.WatchCaseNode.WatchCase c = new nodes.WatchCaseNode.WatchCase(cd.portName);
                            c.threshold = cd.threshold;
                            c.hasOutput = cd.hasOutput;
                            wc.cases.add(c);
                            wc.addInputPort(cd.portName);
                            if (cd.imageNodeId != null)
                                wc.inputPortToNodeId.put(cd.portName, cd.imageNodeId);
                            if (cd.hasOutput) {
                                int ins = wc.outputs.size() - 2;
                                wc.outputs.add(ins < 0 ? 0 : ins, new nodes.BaseNode.NodePort(cd.portName));
                            }
                        }
                    if (d.wcZones != null)
                        for (SaveFormat.WatchZoneData zd : d.wcZones) {
                            nodes.WatchCaseNode.WatchZone z = new nodes.WatchCaseNode.WatchZone(zd.name);
                            z.rect = arrToRect(zd.rect);
                            z.clickMode = zd.clickMode;
                            z.customPinX = zd.customPinX;
                            z.customPinY = zd.customPinY;
                            z.clickDelayMs = zd.clickDelayMs;
                            wc.zones.add(z);
                        }
                    wc.rebuildHeight();
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return node;
    }

    // ── JSON write (no external library — hand-rolled) ────────
    private static void writeJson(SaveFormat.TaskFile tf, File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(tf.version).append(",\n");
        sb.append("  \"appVersion\": \"").append(esc(tf.appVersion)).append("\",\n");
        sb.append("  \"savedAt\": \"").append(esc(tf.savedAt)).append("\",\n");
        sb.append("  \"taskName\": \"").append(esc(tf.taskName)).append("\",\n");
        sb.append("  \"startNodeId\": ").append(tf.startNodeId == null ? "null" : "\"" + esc(tf.startNodeId) + "\"")
                .append(",\n");
        sb.append("  \"nodes\": [\n");
        for (int i = 0; i < tf.nodes.size(); i++) {
            sb.append(nodeDataToJson(tf.nodes.get(i), "    "));
            if (i < tf.nodes.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"arrows\": [\n");
        for (int i = 0; i < tf.arrows.size(); i++) {
            SaveFormat.ArrowData a = tf.arrows.get(i);
            sb.append("    {\"fromNodeId\":\"").append(esc(a.fromNodeId))
                    .append("\",\"fromPort\":\"").append(esc(a.fromPort))
                    .append("\",\"toNodeId\":\"").append(esc(a.toNodeId))
                    .append("\",\"label\":\"").append(esc(a.label != null ? a.label : ""))
                    .append("\",\"bendOffset\":").append(a.bendOffset).append("}");
            if (i < tf.arrows.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(sb.toString());
        }
    }

    private static String nodeDataToJson(SaveFormat.NodeData d, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("{\n");
        jStr(sb, indent, "type", d.type);
        sb.append(",\n");
        jStr(sb, indent, "id", d.id);
        sb.append(",\n");
        jStr(sb, indent, "label", d.label);
        sb.append(",\n");
        jInt(sb, indent, "x", d.x);
        sb.append(",\n");
        jInt(sb, indent, "y", d.y);
        sb.append(",\n");
        jInt(sb, indent, "width", d.width);
        sb.append(",\n");
        jInt(sb, indent, "height", d.height);
        sb.append(",\n");
        jBool(sb, indent, "branchEnabled", d.branchEnabled);
        sb.append(",\n");
        jInt(sb, indent, "entryDelayMs", d.entryDelayMs);
        sb.append(",\n");
        jInt(sb, indent, "nodeLoopCount", d.nodeLoopCount);
        // Optional fields
        if (d.imageName != null) {
            sb.append(",\n");
            jStr(sb, indent, "imageName", d.imageName);
        }
        if (d.templateBase64 != null) {
            sb.append(",\n");
            jStr(sb, indent, "templateBase64", d.templateBase64);
        }
        if (d.watchZone != null) {
            sb.append(",\n");
            jIntArr(sb, indent, "watchZone", d.watchZone);
        }
        if (d.captureRect != null) {
            sb.append(",\n");
            jIntArr(sb, indent, "captureRect", d.captureRect);
        }
        if (d.checkZone != null) {
            sb.append(",\n");
            jIntArr(sb, indent, "checkZone", d.checkZone);
        }
        if (d.matchThreshold != null) {
            sb.append(",\n");
            jInt(sb, indent, "matchThreshold", d.matchThreshold);
        }
        if (d.pollIntervalMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "pollIntervalMs", d.pollIntervalMs);
        }
        if (d.preTriggerDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "preTriggerDelayMs", d.preTriggerDelayMs);
        }
        if (d.timeoutMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "timeoutMs", d.timeoutMs);
        }
        if (d.clickAtMatch != null) {
            sb.append(",\n");
            jBool(sb, indent, "clickAtMatch", d.clickAtMatch);
        }
        if (d.clickX != null) {
            sb.append(",\n");
            jInt(sb, indent, "clickX", d.clickX);
        }
        if (d.clickY != null) {
            sb.append(",\n");
            jInt(sb, indent, "clickY", d.clickY);
        }
        if (d.retryCount != null) {
            sb.append(",\n");
            jInt(sb, indent, "retryCount", d.retryCount);
        }
        if (d.clickCount != null) {
            sb.append(",\n");
            jInt(sb, indent, "clickCount", d.clickCount);
        }
        if (d.subDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "subDelayMs", d.subDelayMs);
        }
        if (d.mouseButton != null) {
            sb.append(",\n");
            jInt(sb, indent, "mouseButton", d.mouseButton);
        }
        if (d.doubleClick != null) {
            sb.append(",\n");
            jBool(sb, indent, "doubleClick", d.doubleClick);
        }
        if (d.points != null) {
            sb.append(",\n");
            jPoints(sb, indent, "points", d.points);
        }
        if (d.intervalMs != null) {
            sb.append(",\n");
            jLong(sb, indent, "intervalMs", d.intervalMs);
        }
        if (d.maxClicks != null) {
            sb.append(",\n");
            jLong(sb, indent, "maxClicks", d.maxClicks);
        }
        if (d.repeatUntilStopped != null) {
            sb.append(",\n");
            jBool(sb, indent, "repeatUntilStopped", d.repeatUntilStopped);
        }
        if (d.repeatTimes != null) {
            sb.append(",\n");
            jInt(sb, indent, "repeatTimes", d.repeatTimes);
        }
        if (d.waitToFinish != null) {
            sb.append(",\n");
            jBool(sb, indent, "waitToFinish", d.waitToFinish);
        }
        if (d.runInBackground != null) {
            sb.append(",\n");
            jBool(sb, indent, "runInBackground", d.runInBackground);
        }
        if (d.loopMode != null) {
            sb.append(",\n");
            jStr(sb, indent, "loopMode", d.loopMode);
        }
        if (d.loopCount != null) {
            sb.append(",\n");
            jInt(sb, indent, "loopCount", d.loopCount);
        }
        if (d.loopDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "loopDelayMs", d.loopDelayMs);
        }
        if (d.waitMode != null) {
            sb.append(",\n");
            jStr(sb, indent, "waitMode", d.waitMode);
        }
        if (d.delayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "delayMs", d.delayMs);
        }
        if (d.pollMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "pollMs", d.pollMs);
        }
        if (d.stopMode != null) {
            sb.append(",\n");
            jStr(sb, indent, "stopMode", d.stopMode);
        }
        if (d.showMessage != null) {
            sb.append(",\n");
            jBool(sb, indent, "showMessage", d.showMessage);
        }
        if (d.customMessage != null) {
            sb.append(",\n");
            jStr(sb, indent, "customMessage", d.customMessage);
        }
        if (d.kbMode != null) {
            sb.append(",\n");
            jInt(sb, indent, "kbMode", d.kbMode);
        }
        if (d.typeText != null) {
            sb.append(",\n");
            jStr(sb, indent, "typeText", d.typeText);
        }
        if (d.charDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "charDelayMs", d.charDelayMs);
        }
        if (d.hotkeyCombo != null) {
            sb.append(",\n");
            jStr(sb, indent, "hotkeyCombo", d.hotkeyCombo);
        }
        if (d.singleKey != null) {
            sb.append(",\n");
            jStr(sb, indent, "singleKey", d.singleKey);
        }
        if (d.repeatCount != null) {
            sb.append(",\n");
            jInt(sb, indent, "repeatCount", d.repeatCount);
        }
        if (d.repeatDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "repeatDelayMs", d.repeatDelayMs);
        }
        if (d.threshold != null) {
            sb.append(",\n");
            jInt(sb, indent, "threshold", d.threshold);
        }
        if (d.wcPollIntervalMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "wcPollIntervalMs", d.wcPollIntervalMs);
        }
        if (d.wcLoopOnMatch != null) {
            sb.append(",\n");
            jBool(sb, indent, "wcLoopOnMatch", d.wcLoopOnMatch);
        }
        if (d.wcLoopDelayMs != null) {
            sb.append(",\n");
            jInt(sb, indent, "wcLoopDelayMs", d.wcLoopDelayMs);
        }
        if (d.wcCases != null) {
            sb.append(",\n");
            jWcCases(sb, indent, d.wcCases);
        }
        if (d.wcZones != null) {
            sb.append(",\n");
            jWcZones(sb, indent, d.wcZones);
        }
        sb.append("\n").append(indent).append("}");
        return sb.toString();
    }

    // ── JSON parse (hand-rolled minimal parser) ───────────────
    private static SaveFormat.TaskFile parseJson(String json) throws Exception {
        SaveFormat.TaskFile tf = new SaveFormat.TaskFile();
        tf.version = intVal(json, "version", 1);
        tf.appVersion = strVal(json, "appVersion", "");
        tf.savedAt = strVal(json, "savedAt", "");
        tf.taskName = strVal(json, "taskName", "Task");
        tf.startNodeId = strValNull(json, "startNodeId");
        tf.nodes = parseNodeArray(json);
        tf.arrows = parseArrowArray(json);
        return tf;
    }

    private static List<SaveFormat.NodeData> parseNodeArray(String json) {
        List<SaveFormat.NodeData> list = new ArrayList<>();
        String block = arrayBlock(json, "nodes");
        if (block == null)
            return list;
        for (String obj : splitObjects(block)) {
            SaveFormat.NodeData d = new SaveFormat.NodeData();
            d.type = strVal(obj, "type", "");
            d.id = strVal(obj, "id", "");
            d.label = strVal(obj, "label", "");
            d.x = intVal(obj, "x", 0);
            d.y = intVal(obj, "y", 0);
            d.width = intVal(obj, "width", 180);
            d.height = intVal(obj, "height", 75);
            d.branchEnabled = boolVal(obj, "branchEnabled", true);
            d.entryDelayMs = intVal(obj, "entryDelayMs", 0);
            d.nodeLoopCount = intVal(obj, "nodeLoopCount", 1);
            d.imageName = strValNull(obj, "imageName");
            d.templateBase64 = strValNull(obj, "templateBase64");
            d.watchZone = intArrVal(obj, "watchZone");
            d.captureRect = intArrVal(obj, "captureRect");
            d.checkZone = intArrVal(obj, "checkZone");
            d.matchThreshold = intValNull(obj, "matchThreshold");
            d.pollIntervalMs = intValNull(obj, "pollIntervalMs");
            d.preTriggerDelayMs = intValNull(obj, "preTriggerDelayMs");
            d.timeoutMs = intValNull(obj, "timeoutMs");
            d.clickAtMatch = boolValNull(obj, "clickAtMatch");
            d.clickX = intValNull(obj, "clickX");
            d.clickY = intValNull(obj, "clickY");
            d.retryCount = intValNull(obj, "retryCount");
            d.clickCount = intValNull(obj, "clickCount");
            d.subDelayMs = intValNull(obj, "subDelayMs");
            d.mouseButton = intValNull(obj, "mouseButton");
            d.doubleClick = boolValNull(obj, "doubleClick");
            d.points = pointsVal(obj, "points");
            d.intervalMs = longValNull(obj, "intervalMs");
            d.maxClicks = longValNull(obj, "maxClicks");
            d.repeatUntilStopped = boolValNull(obj, "repeatUntilStopped");
            d.repeatTimes = intValNull(obj, "repeatTimes");
            d.waitToFinish = boolValNull(obj, "waitToFinish");
            d.runInBackground = boolValNull(obj, "runInBackground");
            d.loopMode = strValNull(obj, "loopMode");
            d.loopCount = intValNull(obj, "loopCount");
            d.loopDelayMs = intValNull(obj, "loopDelayMs");
            d.waitMode = strValNull(obj, "waitMode");
            d.delayMs = intValNull(obj, "delayMs");
            d.pollMs = intValNull(obj, "pollMs");
            d.stopMode = strValNull(obj, "stopMode");
            d.showMessage = boolValNull(obj, "showMessage");
            d.customMessage = strValNull(obj, "customMessage");
            d.kbMode = intValNull(obj, "kbMode");
            d.typeText = strValNull(obj, "typeText");
            d.charDelayMs = intValNull(obj, "charDelayMs");
            d.hotkeyCombo = strValNull(obj, "hotkeyCombo");
            d.singleKey = strValNull(obj, "singleKey");
            d.repeatCount = intValNull(obj, "repeatCount");
            d.repeatDelayMs = intValNull(obj, "repeatDelayMs");
            d.threshold = intValNull(obj, "threshold");
            d.wcPollIntervalMs = intValNull(obj, "wcPollIntervalMs");
            d.wcLoopOnMatch = boolValNull(obj, "wcLoopOnMatch");
            d.wcLoopDelayMs = intValNull(obj, "wcLoopDelayMs");
            d.wcCases = parseWcCases(obj);
            d.wcZones = parseWcZones(obj);
            list.add(d);
        }
        return list;
    }

    private static List<SaveFormat.ArrowData> parseArrowArray(String json) {
        List<SaveFormat.ArrowData> list = new ArrayList<>();
        String block = arrayBlock(json, "arrows");
        if (block == null)
            return list;
        for (String obj : splitObjects(block)) {
            SaveFormat.ArrowData a = new SaveFormat.ArrowData();
            a.fromNodeId = strVal(obj, "fromNodeId", "");
            a.fromPort = strVal(obj, "fromPort", "");
            a.toNodeId = strVal(obj, "toNodeId", "");
            a.label = strVal(obj, "label", "");
            a.bendOffset = intVal(obj, "bendOffset", 40);
            list.add(a);
        }
        return list;
    }

    private static List<SaveFormat.WatchCaseData> parseWcCases(String json) {
        String block = arrayBlock(json, "wcCases");
        if (block == null)
            return null;
        List<SaveFormat.WatchCaseData> list = new ArrayList<>();
        for (String obj : splitObjects(block)) {
            SaveFormat.WatchCaseData cd = new SaveFormat.WatchCaseData();
            cd.portName = strVal(obj, "portName", "");
            cd.threshold = intVal(obj, "threshold", 85);
            cd.hasOutput = boolVal(obj, "hasOutput", true);
            cd.imageNodeId = strValNull(obj, "imageNodeId");
            list.add(cd);
        }
        return list;
    }

    private static List<SaveFormat.WatchZoneData> parseWcZones(String json) {
        String block = arrayBlock(json, "wcZones");
        if (block == null)
            return null;
        List<SaveFormat.WatchZoneData> list = new ArrayList<>();
        for (String obj : splitObjects(block)) {
            SaveFormat.WatchZoneData zd = new SaveFormat.WatchZoneData();
            zd.name = strVal(obj, "name", "Zone");
            zd.rect = intArrVal(obj, "rect");
            zd.clickMode = intVal(obj, "clickMode", 0);
            zd.customPinX = intVal(obj, "customPinX", 0);
            zd.customPinY = intVal(obj, "customPinY", 0);
            zd.clickDelayMs = intVal(obj, "clickDelayMs", 100);
            list.add(zd);
        }
        return list;
    }

    // ── Minimal JSON helpers ──────────────────────────────────
    private static String strVal(String json, String key, String def) {
        String v = strValNull(json, key);
        return v != null ? v : def;
    }

    private static String strValNull(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int q1 = json.indexOf('"', col + 1);
        if (q1 < 0)
            return null;
        // Check for null
        String between = json.substring(col + 1, q1).trim();
        if (between.equals("null"))
            return null;
        StringBuilder sb = new StringBuilder();
        int j = q1 + 1;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"')
                break;
            if (c == '\\' && j + 1 < json.length()) {
                j++;
                c = json.charAt(j);
                switch (c) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    default:
                        sb.append(c);
                }
            } else
                sb.append(c);
            j++;
        }
        return sb.toString();
    }

    private static int intVal(String json, String key, int def) {
        Integer v = intValNull(json, key);
        return v != null ? v : def;
    }

    private static Integer intValNull(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int j = col + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j)))
            j++;
        if (j < json.length() && json.charAt(j) == 'n')
            return null; // null
        StringBuilder sb = new StringBuilder();
        while (j < json.length()) {
            char c = json.charAt(j);
            if (Character.isDigit(c) || c == '-')
                sb.append(c);
            else if (sb.length() > 0)
                break;
            j++;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static long longVal(String json, String key, long def) {
        Long v = longValNull(json, key);
        return v != null ? v : def;
    }

    private static Long longValNull(String json, String key) {
        Integer v = intValNull(json, key);
        return v != null ? (long) v : null;
    }

    private static boolean boolVal(String json, String key, boolean def) {
        Boolean v = boolValNull(json, key);
        return v != null ? v : def;
    }

    private static Boolean boolValNull(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int j = col + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j)))
            j++;
        if (j + 4 <= json.length() && json.substring(j, j + 4).equals("true"))
            return true;
        if (j + 5 <= json.length() && json.substring(j, j + 5).equals("false"))
            return false;
        return null;
    }

    private static int[] intArrVal(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int j = col + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j)))
            j++;
        if (j >= json.length() || json.charAt(j) != '[')
            return null;
        int end = json.indexOf(']', j);
        if (end < 0)
            return null;
        String[] parts = json.substring(j + 1, end).split(",");
        int[] arr = new int[parts.length];
        try {
            for (int k = 0; k < parts.length; k++)
                arr[k] = Integer.parseInt(parts[k].trim());
        } catch (Exception e) {
            return null;
        }
        return arr;
    }

    private static List<int[]> pointsVal(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int j = col + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j)))
            j++;
        if (j >= json.length() || json.charAt(j) != '[')
            return null;
        // Find matching ]
        int depth = 0, start = j, end = -1;
        for (int k = j; k < json.length(); k++) {
            if (json.charAt(k) == '[')
                depth++;
            else if (json.charAt(k) == ']') {
                depth--;
                if (depth == 0) {
                    end = k;
                    break;
                }
            }
        }
        if (end < 0)
            return null;
        String inner = json.substring(start + 1, end).trim();
        if (inner.isEmpty())
            return new ArrayList<>();
        List<int[]> list = new ArrayList<>();
        for (String arr : inner.split("\\],\\s*\\[")) {
            arr = arr.replaceAll("[\\[\\]]", "").trim();
            String[] parts = arr.split(",");
            int[] pt = new int[parts.length];
            try {
                for (int k = 0; k < parts.length; k++)
                    pt[k] = Integer.parseInt(parts[k].trim());
                list.add(pt);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private static String arrayBlock(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int col = json.indexOf(':', i + pat.length());
        if (col < 0)
            return null;
        int j = col + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j)))
            j++;
        if (j >= json.length() || json.charAt(j) != '[')
            return null;
        int depth = 0, end = -1;
        for (int k = j; k < json.length(); k++) {
            if (json.charAt(k) == '[')
                depth++;
            else if (json.charAt(k) == ']') {
                depth--;
                if (depth == 0) {
                    end = k;
                    break;
                }
            }
        }
        return end < 0 ? null : json.substring(j + 1, end);
    }

    private static List<String> splitObjects(String block) {
        List<String> objs = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '{') {
                if (depth == 0)
                    start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objs.add(block.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objs;
    }

    // ── JSON write helpers ────────────────────────────────────
    private static void jStr(StringBuilder sb, String ind, String k, String v) {
        sb.append(ind).append("  \"").append(k).append("\": ")
                .append(v == null ? "null" : "\"" + esc(v) + "\"");
    }

    private static void jInt(StringBuilder sb, String ind, String k, int v) {
        sb.append(ind).append("  \"").append(k).append("\": ").append(v);
    }

    private static void jLong(StringBuilder sb, String ind, String k, long v) {
        sb.append(ind).append("  \"").append(k).append("\": ").append(v);
    }

    private static void jBool(StringBuilder sb, String ind, String k, boolean v) {
        sb.append(ind).append("  \"").append(k).append("\": ").append(v);
    }

    private static void jIntArr(StringBuilder sb, String ind, String k, int[] arr) {
        sb.append(ind).append("  \"").append(k).append("\": [");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1)
                sb.append(",");
        }
        sb.append("]");
    }

    private static void jPoints(StringBuilder sb, String ind, String k, List<int[]> pts) {
        sb.append(ind).append("  \"").append(k).append("\": [");
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            sb.append("[");
            for (int j = 0; j < p.length; j++) {
                sb.append(p[j]);
                if (j < p.length - 1)
                    sb.append(",");
            }
            sb.append("]");
            if (i < pts.size() - 1)
                sb.append(",");
        }
        sb.append("]");
    }

    private static void jWcCases(StringBuilder sb, String ind, List<SaveFormat.WatchCaseData> cases) {
        sb.append(ind).append("  \"wcCases\": [\n");
        for (int i = 0; i < cases.size(); i++) {
            SaveFormat.WatchCaseData c = cases.get(i);
            sb.append(ind).append("    {");
            sb.append("\"portName\":\"").append(esc(c.portName)).append("\",");
            sb.append("\"threshold\":").append(c.threshold).append(",");
            sb.append("\"hasOutput\":").append(c.hasOutput).append(",");
            sb.append("\"imageNodeId\":").append(c.imageNodeId == null ? "null" : "\"" + esc(c.imageNodeId) + "\"")
                    .append("}");
            if (i < cases.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append(ind).append("  ]");
    }

    private static void jWcZones(StringBuilder sb, String ind, List<SaveFormat.WatchZoneData> zones) {
        sb.append(ind).append("  \"wcZones\": [\n");
        for (int i = 0; i < zones.size(); i++) {
            SaveFormat.WatchZoneData z = zones.get(i);
            sb.append(ind).append("    {");
            sb.append("\"name\":\"").append(esc(z.name)).append("\",");
            sb.append("\"rect\":").append(z.rect == null ? "null"
                    : "[" + z.rect[0] + "," + z.rect[1] + "," + z.rect[2] + "," + z.rect[3] + "]").append(",");
            sb.append("\"clickMode\":").append(z.clickMode).append(",");
            sb.append("\"customPinX\":").append(z.customPinX).append(",");
            sb.append("\"customPinY\":").append(z.customPinY).append(",");
            sb.append("\"clickDelayMs\":").append(z.clickDelayMs).append("}");
            if (i < zones.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append(ind).append("  ]");
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ── Image helpers ─────────────────────────────────────────
    private static String imgToBase64(BufferedImage img) {
        if (img == null)
            return null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage base64ToImg(String b64) {
        if (b64 == null)
            return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            return null;
        }
    }

    // ── Reflection helpers ────────────────────────────────────
    private static java.lang.reflect.Field field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + o.getClass().getName());
    }

    private static Object getF(Object o, String n) throws Exception {
        return field(o, n).get(o);
    }

    private static String str(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v != null ? v.toString() : null;
    }

    private static Integer intF(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private static Long longF(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private static Boolean boolF(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    private static BufferedImage img(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v instanceof BufferedImage ? (BufferedImage) v : null;
    }

    private static Rectangle rect(Object o, String n) throws Exception {
        Object v = getF(o, n);
        return v instanceof Rectangle ? (Rectangle) v : null;
    }

    private static int[] rectToArr(Rectangle r) {
        return r == null ? null : new int[] { r.x, r.y, r.width, r.height };
    }

    private static Rectangle arrToRect(int[] a) {
        return a == null || a.length < 4 ? null : new Rectangle(a[0], a[1], a[2], a[3]);
    }

    private static void setF(Object o, String n, Object v) throws Exception {
        if (v != null)
            field(o, n).set(o, v);
    }

    private static void setI(Object o, String n, Integer v, int def) throws Exception {
        field(o, n).set(o, v != null ? v : def);
    }

    private static void setB(Object o, String n, Boolean v, boolean def) throws Exception {
        field(o, n).set(o, v != null ? v : def);
    }
}