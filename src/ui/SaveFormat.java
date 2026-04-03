package ui;
import java.util.*;

public class SaveFormat {

    public static final int VERSION = 1;

    public static class TaskFile {
        public int version = VERSION;
        public String appVersion = "2.0";
        public String savedAt = java.time.LocalDate.now().toString();
        public String taskName = "Task";
        public String startNodeId = null;
        public List<NodeData> nodes = new ArrayList<>();
        public List<ArrowData> arrows = new ArrayList<>();
    }

    public static class NodeData {
        // Identity
        public String type;
        public String id;
        public String label;
        // Canvas
        public int x, y, width, height;
        // Common
        public boolean branchEnabled = true;
        public int entryDelayMs = 0;
        public int nodeLoopCount = 1;
        // Type-specific — all optional, null = not set
        // Images stored as base64
        public String templateBase64 = null;
        public String imageName = null;
        // Rectangles
        public int[] watchZone = null; // x,y,w,h
        public int[] captureRect = null;
        public int[] checkZone = null;
        // Watch Zone fields
        public Integer matchThreshold = null;
        public Integer pollIntervalMs = null;
        public Integer preTriggerDelayMs = null;
        public Integer timeoutMs = null;
        public Boolean clickAtMatch = null;
        public Integer clickX = null;
        public Integer clickY = null;
        public Integer retryCount = null;
        // Click fields
        public Integer clickCount = null;
        public Integer subDelayMs = null;
        public Integer mouseButton = null;
        public Boolean doubleClick = null;
        // Simple Click fields
        public List<int[]> points = null;
        public Long intervalMs = null;
        public Long maxClicks = null;
        public Boolean repeatUntilStopped = null;
        public Integer repeatTimes = null;
        public Boolean waitToFinish = null;
        public Boolean runInBackground = null;
        // Loop fields
        public String loopMode = null;
        public Integer loopCount = null;
        public Integer loopDelayMs = null;
        // Wait fields
        public String waitMode = null;
        public Integer delayMs = null;
        public Integer pollMs = null;
        // Stop fields
        public String stopMode = null;
        public Boolean showMessage = null;
        public String customMessage = null;
        // Keyboard fields
        public Integer kbMode = null;
        public String typeText = null;
        public Integer charDelayMs = null;
        public String hotkeyCombo = null;
        public String singleKey = null;
        public Integer repeatCount = null;
        public Integer repeatDelayMs = null;
        // Image node
        public Integer threshold = null;
        // Message node
        public Integer msgStyle = null;
        public String msgTitle = null;
        public String msgText = null;
        public Integer msgDisplaySeconds = null;
        public Boolean msgWaitForDismiss = null;
        public Boolean msgPauseTask = null;
        public Integer msgPosition = null;
        public Integer msgCustomX = null;
        public Integer msgCustomY = null;
        public Integer msgBoxWidth = null;
        public Integer msgBoxHeight = null;
        public Integer msgBgR = null, msgBgG = null, msgBgB = null;
        public Integer msgFgR = null, msgFgG = null, msgFgB = null;
        public Integer msgAcR = null, msgAcG = null, msgAcB = null;
        // Watch Case
        public Integer wcPollIntervalMs = null;
        public Boolean wcLoopOnMatch = null;
        public Integer wcLoopDelayMs = null;
        public List<WatchCaseData> wcCases = null;
        public List<WatchZoneData> wcZones = null;
    }

    public static class WatchCaseData {
        public String portName;
        public int threshold;
        public boolean hasOutput;
        public String imageNodeId; // which Image node is connected
    }

    public static class WatchZoneData {
        public String name;
        public int[] rect; // x,y,w,h — null if not set
        public int clickMode;
        public int customPinX;
        public int customPinY;
        public int clickDelayMs;
    }

    public static class ArrowData {
        public String fromNodeId;
        public String fromPort;
        public String toNodeId;
        public String label;
        public int bendOffset;
    }
}