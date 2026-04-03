package nodes;

import engine.ExecutionContext;
import java.awt.*;
import java.awt.image.BufferedImage;

// ═══════════════════════════════════════════════════════════════
//  WATCH ZONE NODE
// ═══════════════════════════════════════════════════════════════
class WatchZoneNode extends BaseNode {

    public String imageName = "Unnamed Image";
    public BufferedImage template = null;
    public Rectangle watchZone = null;
    public Rectangle captureRect = null;
    public boolean sameAsCapture = false;
    public int matchThreshold = 85;
    public int pollIntervalMs = 500;
    public int preTriggerDelayMs = 0;
    public int timeoutMs = 0;
    public boolean clickAtMatch = true;
    public int clickX = -1, clickY = -1;
    public int retryCount = 0;

    public WatchZoneNode(int x, int y) {
        super(NodeType.WATCH_ZONE, "Watch Zone", x, y);
        addOutputPort("Found");
        addOutputPort("Not Found");
        addOutputPort("Timeout");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (sameAsCapture && captureRect != null)
            watchZone = captureRect;
        if (template == null || watchZone == null)
            return "Not Found";

        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        int tries = 0;

        while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
            BufferedImage zoneImg = ctx.getRobot().createScreenCapture(watchZone);
            double[] result = ImageMatcher.findTemplateWithScore(template, zoneImg, matchThreshold);

            double pct = result != null ? result[2] : 0;
            ctx.setHudStatus("\u25ce " + imageName, String.format("Match: %.0f%%", pct));

            if (result != null) {
                Point match = new Point((int) result[0], (int) result[1]);
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
                ctx.setHudStatus("\u25ce " + imageName, "\u2713 Found!");
                return "Found";
            }

            tries++;
            if (retryCount > 0 && tries > retryCount)
                break;
            Thread.sleep(pollIntervalMs);
        }

        return timeoutMs > 0 ? "Timeout" : "Not Found";
    }

    @Override
    public Color nodeColor() {
        return new Color(50, 130, 200);
    }

    @Override
    public String nodeIcon() {
        return "\u25ce";
    }
}

// ═══════════════════════════════════════════════════════════════
// CLICK NODE
// ═══════════════════════════════════════════════════════════════
class ClickNode extends BaseNode {

    public int clickX = 0, clickY = 0;
    public int clickCount = 1;
    public int subDelayMs = 100;
    public int mouseButton = 0;
    public boolean doubleClick = false;

    public ClickNode(int x, int y) {
        super(NodeType.CLICK, "Click", x, y);
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        int btn = mouseButton == 1 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK
                : mouseButton == 2 ? java.awt.event.InputEvent.BUTTON2_DOWN_MASK
                        : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        ctx.getRobot().mouseMove(clickX, clickY);
        Thread.sleep(40);
        for (int i = 0; i < clickCount; i++) {
            ctx.getRobot().mousePress(btn);
            ctx.getRobot().mouseRelease(btn);
            if (doubleClick) {
                Thread.sleep(40);
                ctx.getRobot().mousePress(btn);
                ctx.getRobot().mouseRelease(btn);
            }
            if (i < clickCount - 1)
                Thread.sleep(subDelayMs);
        }
        return "Done";
    }

    @Override
    public Color nodeColor() {
        return new Color(80, 160, 80);
    }

    @Override
    public String nodeIcon() {
        return "\u2197";
    }
}

// ═══════════════════════════════════════════════════════════════
// SIMPLE CLICK NODE
// ═══════════════════════════════════════════════════════════════
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
        height = 75;
        addOutputPort("Done");
        addOutputPort("Stopped");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        ctx.setHudStatus("\u2295 " + label, "Clicks: 0");
        engine.SimpleClickEngine eng = new engine.SimpleClickEngine(
                points, intervalMs, maxClicks, mouseButton, doubleClick,
                repeatUntilStopped, repeatTimes, ctx);
        eng.setClickCallback(total -> ctx.setHudStatus("\u2295 " + label, "Clicks: " + total));
        if (runInBackground) {
            new Thread(eng).start();
            return "Done";
        }
        eng.run();
        return eng.wasStopped() ? "Stopped" : "Done";
    }

    @Override
    public Color nodeColor() {
        return new Color(120, 60, 180);
    }

    @Override
    public String nodeIcon() {
        return "\u2295";
    }
}

// ═══════════════════════════════════════════════════════════════
// CONDITION NODE
// ═══════════════════════════════════════════════════════════════
class ConditionNode extends BaseNode {

    public String imageName = "Unnamed Image";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;

    public ConditionNode(int x, int y) {
        super(NodeType.CONDITION, "Condition", x, y);
        width = 140;
        height = 65;
        addOutputPort("Found");
        addOutputPort("Not Found");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (template == null || checkZone == null)
            return "Not Found";
        BufferedImage zoneImg = ctx.getRobot().createScreenCapture(checkZone);
        Point match = ImageMatcher.findTemplate(template, zoneImg, matchThreshold);
        return match != null ? "Found" : "Not Found";
    }

    @Override
    public Color nodeColor() {
        return new Color(200, 140, 30);
    }

    @Override
    public String nodeIcon() {
        return "\u25c7";
    }
}

// ═══════════════════════════════════════════════════════════════
// LOOP NODE
// ═══════════════════════════════════════════════════════════════
class LoopNode extends BaseNode {
    public static enum LoopMode {
        FIXED_COUNT, UNTIL_FOUND, UNTIL_NOT_FOUND, FOREVER
    }

    public LoopMode loopMode = LoopMode.FIXED_COUNT;
    public int loopCount = 3;
    public int loopDelayMs = 0;
    public String imageName = "";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;

    private transient int currentIteration = 0;

    public LoopNode(int x, int y) {
        super(NodeType.LOOP, "Loop", x, y);
        addOutputPort("Loop");
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        if (loopDelayMs > 0)
            Thread.sleep(loopDelayMs);
        currentIteration++;
        switch (loopMode) {
            case FIXED_COUNT:
                if (currentIteration <= loopCount)
                    return "Loop";
                currentIteration = 0;
                return "Done";
            case FOREVER:
                return ctx.isRunning() ? "Loop" : "Done";
            case UNTIL_FOUND:
            case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null)
                    return "Done";
                BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                boolean found = match != null;
                if (loopMode == LoopMode.UNTIL_FOUND)
                    return found ? "Done" : "Loop";
                if (loopMode == LoopMode.UNTIL_NOT_FOUND)
                    return found ? "Loop" : "Done";
        }
        return "Done";
    }

    public void resetIteration() {
        currentIteration = 0;
    }

    @Override
    public Color nodeColor() {
        return new Color(180, 80, 80);
    }

    @Override
    public String nodeIcon() {
        return "\u21ba";
    }
}

// ═══════════════════════════════════════════════════════════════
// WAIT NODE
// ═══════════════════════════════════════════════════════════════
class WaitNode extends BaseNode {
    public static enum WaitMode {
        FIXED_DELAY, UNTIL_FOUND, UNTIL_NOT_FOUND, COUNTDOWN, WAIT_UNTIL_TIME
    }

    public WaitMode waitMode = WaitMode.FIXED_DELAY;
    public int delayMs = 1000;
    public int timeoutMs = 0;
    public String imageName = "";
    public BufferedImage template = null;
    public Rectangle checkZone = null;
    public int matchThreshold = 85;
    public int pollMs = 500;
    public int targetHour = 9;
    public int targetMinute = 0;

    private static String fmtMs(long ms) {
        if (ms < 0)
            ms = 0;
        long h = ms / 3600000, m = (ms % 3600000) / 60000;
        long s = (ms % 60000) / 1000, tenth = (ms % 1000) / 100;
        if (h > 0)
            return String.format("%d:%02d:%02d", h, m, s);
        if (m > 0)
            return String.format("%d:%02d.%d", m, s, tenth);
        return String.format("%d.%ds", s, tenth);
    }

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
                while (System.currentTimeMillis() < end && ctx.isRunning()) {
                    long rem = end - System.currentTimeMillis();
                    ctx.setHudStatus("\u23f1 " + label, fmtMs(rem));
                    Thread.sleep(50);
                }
                ctx.setHudStatus("\u23f1 " + label, "Done");
                return "Done";

            case COUNTDOWN: {
                long endC = System.currentTimeMillis() + delayMs;
                while (System.currentTimeMillis() < endC && ctx.isRunning()) {
                    long rem = endC - System.currentTimeMillis();
                    ctx.setHudStatus("\u23f1 " + label, "⏳ " + fmtMs(rem));
                    Thread.sleep(50);
                }
                ctx.setHudStatus("\u23f1 " + label, "Done");
                return "Done";
            }

            case WAIT_UNTIL_TIME: {
                java.util.Calendar target = java.util.Calendar.getInstance();
                target.set(java.util.Calendar.HOUR_OF_DAY, targetHour);
                target.set(java.util.Calendar.MINUTE, targetMinute);
                target.set(java.util.Calendar.SECOND, 0);
                target.set(java.util.Calendar.MILLISECOND, 0);
                // If time already passed today, skip
                if (target.getTimeInMillis() <= System.currentTimeMillis()) {
                    ctx.setHudStatus("\u23f1 " + label, "Time passed — skipping");
                    return "Done";
                }
                while (System.currentTimeMillis() < target.getTimeInMillis() && ctx.isRunning()) {
                    long rem = target.getTimeInMillis() - System.currentTimeMillis();
                    ctx.setHudStatus("\u23f1 " + label,
                            String.format("Wait until %02d:%02d  (%s)", targetHour, targetMinute, fmtMs(rem)));
                    Thread.sleep(50);
                }
                ctx.setHudStatus("\u23f1 " + label, "Done");
                return "Done";
            }

            case UNTIL_FOUND:
            case UNTIL_NOT_FOUND:
                if (template == null || checkZone == null)
                    return "Done";
                long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
                while (System.currentTimeMillis() < deadline && ctx.isRunning()) {
                    BufferedImage img = ctx.getRobot().createScreenCapture(checkZone);
                    Point match = ImageMatcher.findTemplate(template, img, matchThreshold);
                    boolean found = match != null;
                    if (waitMode == WaitMode.UNTIL_FOUND && found)
                        return "Done";
                    if (waitMode == WaitMode.UNTIL_NOT_FOUND && !found)
                        return "Done";
                    long rem = deadline == Long.MAX_VALUE ? -1 : deadline - System.currentTimeMillis();
                    ctx.setHudStatus("\u23f1 " + label, rem < 0 ? "Waiting..." : "Timeout in " + fmtMs(rem));
                    Thread.sleep(pollMs);
                }
                return "Timeout";
        }
        return "Done";
    }

    @Override
    public Color nodeColor() {
        return new Color(80, 150, 150);
    }

    @Override
    public String nodeIcon() {
        return "\u23f1";
    }
}

// ═══════════════════════════════════════════════════════════════
// STOP NODE
// ═══════════════════════════════════════════════════════════════
class MessageNode extends BaseNode {
    // Style: 0=Toast HUD, 1=Floating Window
    public int style = 0;
    public String title = "Alert";
    public String message = "Your message here";
    public int displaySeconds = 3;
    public boolean waitForDismiss = false;
    public boolean pauseTask = false;
    // Position: 0=Center, 1=Custom
    public int position = 0;
    public int customX = 200, customY = 200;
    public int boxWidth = 320, boxHeight = 120;
    // Colors stored as RGB ints
    public int bgColorR = 22, bgColorG = 22, bgColorB = 30;
    public int textColorR = 220, textColorG = 220, textColorB = 230;
    public int accentColorR = 80, accentColorG = 140, accentColorB = 255;

    public MessageNode(int x, int y) {
        super(NodeType.MESSAGE, "Message", x, y);
        width = 160;
        height = 60;
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        Color bg = new Color(bgColorR, bgColorG, bgColorB);
        Color fg = new Color(textColorR, textColorG, textColorB);
        Color accent = new Color(accentColorR, accentColorG, accentColorB);

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JWindow win = new javax.swing.JWindow();
            win.setAlwaysOnTop(true);
            win.setBackground(new Color(0, 0, 0, 0));

            javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 0)) {
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(accent);
                    if (style == 0)
                        g2.fillRoundRect(0, 0, getWidth(), 3, 4, 4); // top bar toast
                    else
                        g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4); // left bar window
                }
            };
            panel.setOpaque(false);
            panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16));

            javax.swing.JPanel textPanel = new javax.swing.JPanel();
            textPanel.setLayout(new javax.swing.BoxLayout(textPanel, javax.swing.BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            if (style == 1 && !title.isEmpty()) {
                javax.swing.JLabel titleLbl = new javax.swing.JLabel(title);
                titleLbl.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13));
                titleLbl.setForeground(accent);
                textPanel.add(titleLbl);
                textPanel.add(javax.swing.Box.createVerticalStrut(6));
            }

            javax.swing.JLabel msgLbl = new javax.swing.JLabel(
                    "<html><body style='width:" + (boxWidth - 50) + "px'>" + message + "</body></html>");
            msgLbl.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
            msgLbl.setForeground(fg);
            textPanel.add(msgLbl);

            panel.add(textPanel, java.awt.BorderLayout.CENTER);

            if (waitForDismiss) {
                javax.swing.JButton okBtn = new javax.swing.JButton("OK");
                okBtn.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));
                okBtn.setBackground(accent);
                okBtn.setForeground(java.awt.Color.WHITE);
                okBtn.setOpaque(true);
                okBtn.setBorderPainted(false);
                okBtn.setFocusPainted(false);
                okBtn.addActionListener(e -> {
                    win.dispose();
                    latch.countDown();
                });
                javax.swing.JPanel btnRow = new javax.swing.JPanel(
                        new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 4));
                btnRow.setOpaque(false);
                btnRow.add(okBtn);
                panel.add(btnRow, java.awt.BorderLayout.SOUTH);
            }

            win.setContentPane(panel);
            win.setSize(boxWidth, boxHeight);

            // Position
            java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            if (position == 0) {
                win.setLocation(screen.width / 2 - boxWidth / 2,
                        screen.height / 2 - boxHeight / 2);
            } else {
                win.setLocation(customX, customY);
            }

            win.setVisible(true);

            if (!waitForDismiss) {
                new javax.swing.Timer(displaySeconds * 1000, e -> {
                    win.dispose();
                    latch.countDown();
                }) {
                    {
                        setRepeats(false);
                    }
                }.start();
            }
        });

        if (pauseTask || waitForDismiss) {
            latch.await();
        }

        ctx.setHudStatus("\u2709 " + label, "Shown");
        return "Done";
    }

    @Override
    public Color nodeColor() {
        return new Color(180, 120, 40);
    }

    @Override
    public String nodeIcon() {
        return "\u2709";
    }
}

// ═══════════════════════════════════════════════════════════════
// NODE FACTORY
// ═══════════════════════════════════════════════════════════════
public class NodeFactory {

    public static BaseNode create(BaseNode.NodeType type, int x, int y) {
        switch (type) {
            case WATCH_ZONE:
                return new WatchZoneNode(x, y);
            case CLICK:
                return new ClickNode(x, y);
            case SIMPLE_CLICK:
                return new SimpleClickNode(x, y);
            case CONDITION:
                return new ConditionNode(x, y);
            case LOOP:
                return new LoopNode(x, y);
            case WAIT:
                return new WaitNode(x, y);
            case MESSAGE:
                return new MessageNode(x, y);
            case KEYBOARD:
                return new KeyboardNode(x, y);
            case IMAGE:
                return new ImageNode(x, y);
            case WATCH_CASE:
                return new WatchCaseNode(x, y);
            default:
                throw new IllegalArgumentException("Unknown node type: " + type);
        }
    }

    public static String displayName(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:
                return "Watch Zone";
            case CLICK:
                return "Click";
            case SIMPLE_CLICK:
                return "Simple Click";
            case CONDITION:
                return "Condition";
            case LOOP:
                return "Loop";
            case WAIT:
                return "Wait";
            case MESSAGE:
                return "Message";
            case KEYBOARD:
                return "Keyboard";
            case IMAGE:
                return "Image";
            case WATCH_CASE:
                return "Watch Case";
            default:
                return type.name();
        }
    }

    public static String icon(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:
                return "\u25ce";
            case CLICK:
                return "\u2197";
            case SIMPLE_CLICK:
                return "\u2295";
            case CONDITION:
                return "?";
            case LOOP:
                return "\u21ba";
            case WAIT:
                return "\u23f1";
            case MESSAGE:
                return "\u2709";
            case KEYBOARD:
                return "\u2328";
            case IMAGE:
                return "\ud83d\uddbc";
            case WATCH_CASE:
                return "\u25c9";
            default:
                return "?";
        }
    }

    public static Color color(BaseNode.NodeType type) {
        switch (type) {
            case WATCH_ZONE:
                return new Color(50, 130, 200);
            case CLICK:
                return new Color(80, 160, 80);
            case SIMPLE_CLICK:
                return new Color(120, 60, 180);
            case CONDITION:
                return new Color(200, 140, 30);
            case LOOP:
                return new Color(180, 80, 80);
            case WAIT:
                return new Color(80, 150, 150);
            case MESSAGE:
                return new Color(180, 120, 40);
            case KEYBOARD:
                return new Color(60, 120, 160);
            case IMAGE:
                return new Color(80, 120, 80);
            case WATCH_CASE:
                return new Color(90, 100, 115);
            default:
                return Color.GRAY;
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// KEYBOARD NODE
// ═══════════════════════════════════════════════════════════════
class KeyboardNode extends BaseNode {

    public int mode = 0;
    public String typeText = "";
    public int charDelayMs = 50;
    public String hotkeyCombo = "";
    public String singleKey = "ENTER";
    public int repeatCount = 1;
    public int repeatDelayMs = 100;

    public KeyboardNode(int x, int y) {
        super(NodeType.KEYBOARD, "Keyboard", x, y);
        addOutputPort("Done");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        java.awt.Robot robot = ctx.getRobot();
        int total = Math.max(1, repeatCount);
        for (int r = 0; r < total && ctx.isRunning(); r++) {
            String rs = total > 1 ? "  (" + (r + 1) + "/" + total + ")" : "";
            switch (mode) {
                case 0:
                    String preview = typeText.length() > 20 ? typeText.substring(0, 20) + "\u2026" : typeText;
                    ctx.setHudStatus("\u2328 " + label, "Typing: \"" + preview + "\"" + rs);
                    typeText(robot, typeText, charDelayMs, ctx);
                    break;
                case 1:
                    ctx.setHudStatus("\u2328 " + label, "Hotkey: " + hotkeyCombo + rs);
                    pressHotkey(robot, hotkeyCombo);
                    break;
                case 2:
                    ctx.setHudStatus("\u2328 " + label, "Key: " + singleKey + rs);
                    pressSingleKey(robot, singleKey);
                    break;
            }
            if (r < total - 1)
                Thread.sleep(repeatDelayMs);
        }
        ctx.setHudStatus("\u2328 " + label, "Done");
        return "Done";
    }

    private void typeText(java.awt.Robot robot, String text, int delay, ExecutionContext ctx)
            throws InterruptedException {
        int i = 0;
        while (i < text.length() && ctx.isRunning()) {
            if (text.charAt(i) == '[') {
                int end = text.indexOf(']', i);
                if (end > i) {
                    String tag = text.substring(i + 1, end);
                    if (tag.toUpperCase().startsWith("COMBO:")) {
                        pressHotkey(robot, tag.substring(6));
                    } else {
                        pressSingleKey(robot, tag.toUpperCase());
                    }
                    i = end + 1;
                    if (delay > 0)
                        Thread.sleep(delay);
                    continue;
                }
            }
            typeChar(robot, text.charAt(i));
            if (delay > 0)
                Thread.sleep(delay);
            i++;
        }
    }

    private void typeChar(java.awt.Robot robot, char ch) {
        try {
            boolean shift = Character.isUpperCase(ch) || "!@#$%^&*()_+{}|:\"<>?~".indexOf(ch) >= 0;
            int keyCode = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(ch);
            if (keyCode == java.awt.event.KeyEvent.VK_UNDEFINED)
                return;
            if (shift)
                robot.keyPress(java.awt.event.KeyEvent.VK_SHIFT);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (shift)
                robot.keyRelease(java.awt.event.KeyEvent.VK_SHIFT);
        } catch (Exception ignored) {
        }
    }

    private void pressHotkey(java.awt.Robot robot, String combo) {
        String[] parts = combo.toLowerCase().split("\\+");
        int[] codes = new int[parts.length];
        for (int i = 0; i < parts.length; i++)
            codes[i] = keyNameToCode(parts[i].trim());
        for (int code : codes)
            if (code != -1)
                robot.keyPress(code);
        try {
            Thread.sleep(30);
        } catch (InterruptedException ignored) {
        }
        for (int i = codes.length - 1; i >= 0; i--)
            if (codes[i] != -1)
                robot.keyRelease(codes[i]);
    }

    private void pressSingleKey(java.awt.Robot robot, String keyName) {
        int code = keyNameToCode(keyName.toLowerCase().trim());
        if (code != -1) {
            robot.keyPress(code);
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
            }
            robot.keyRelease(code);
        }
    }

    static int keyNameToCode(String name) {
        switch (name) {
            case "enter":
                return java.awt.event.KeyEvent.VK_ENTER;
            case "escape":
            case "esc":
                return java.awt.event.KeyEvent.VK_ESCAPE;
            case "tab":
                return java.awt.event.KeyEvent.VK_TAB;
            case "space":
                return java.awt.event.KeyEvent.VK_SPACE;
            case "backspace":
                return java.awt.event.KeyEvent.VK_BACK_SPACE;
            case "delete":
                return java.awt.event.KeyEvent.VK_DELETE;
            case "up":
                return java.awt.event.KeyEvent.VK_UP;
            case "down":
                return java.awt.event.KeyEvent.VK_DOWN;
            case "left":
                return java.awt.event.KeyEvent.VK_LEFT;
            case "right":
                return java.awt.event.KeyEvent.VK_RIGHT;
            case "home":
                return java.awt.event.KeyEvent.VK_HOME;
            case "end":
                return java.awt.event.KeyEvent.VK_END;
            case "pageup":
                return java.awt.event.KeyEvent.VK_PAGE_UP;
            case "pagedown":
                return java.awt.event.KeyEvent.VK_PAGE_DOWN;
            case "ctrl":
            case "control":
                return java.awt.event.KeyEvent.VK_CONTROL;
            case "alt":
                return java.awt.event.KeyEvent.VK_ALT;
            case "shift":
                return java.awt.event.KeyEvent.VK_SHIFT;
            case "meta":
            case "cmd":
            case "win":
                return java.awt.event.KeyEvent.VK_META;
            case "f1":
                return java.awt.event.KeyEvent.VK_F1;
            case "f2":
                return java.awt.event.KeyEvent.VK_F2;
            case "f3":
                return java.awt.event.KeyEvent.VK_F3;
            case "f4":
                return java.awt.event.KeyEvent.VK_F4;
            case "f5":
                return java.awt.event.KeyEvent.VK_F5;
            case "f6":
                return java.awt.event.KeyEvent.VK_F6;
            case "f7":
                return java.awt.event.KeyEvent.VK_F7;
            case "f8":
                return java.awt.event.KeyEvent.VK_F8;
            case "f9":
                return java.awt.event.KeyEvent.VK_F9;
            case "f10":
                return java.awt.event.KeyEvent.VK_F10;
            case "f11":
                return java.awt.event.KeyEvent.VK_F11;
            case "f12":
                return java.awt.event.KeyEvent.VK_F12;
            default:
                if (name.length() == 1) {
                    int code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(name.charAt(0));
                    return code != java.awt.event.KeyEvent.VK_UNDEFINED ? code : -1;
                }
                return -1;
        }
    }

    @Override
    public Color nodeColor() {
        return new Color(60, 120, 160);
    }

    @Override
    public String nodeIcon() {
        return "\u2328";
    }
}

// ═══════════════════════════════════════════════════════════════
// IMAGE MATCHER
// ═══════════════════════════════════════════════════════════════
class ImageMatcher {

    public static Point findTemplate(java.awt.image.BufferedImage tmpl,
            java.awt.image.BufferedImage zone,
            int thresholdPct) {
        double[] result = findTemplateWithScore(tmpl, zone, thresholdPct);
        if (result == null)
            return null;
        return new Point((int) result[0], (int) result[1]);
    }

    public static double[] findTemplateWithScore(java.awt.image.BufferedImage tmpl,
            java.awt.image.BufferedImage zone,
            int thresholdPct) {
        if (tmpl == null || zone == null)
            return null;
        int tw = tmpl.getWidth(), th = tmpl.getHeight();
        int zw = zone.getWidth(), zh = zone.getHeight();
        if (tw > zw || th > zh)
            return null;

        int step = Math.max(1, Math.min(tw, th) / 6);
        double best = 0;
        int bestX = -1, bestY = -1;

        for (int y = 0; y <= zh - th; y += step)
            for (int x = 0; x <= zw - tw; x += step) {
                double score = matchScore(tmpl, zone, x, y, tw, th);
                if (score > best) {
                    best = score;
                    bestX = x;
                    bestY = y;
                }
            }
        if (best * 100 >= thresholdPct && bestX >= 0)
            return new double[] { bestX + tw / 2.0, bestY + th / 2.0, best * 100 };
        return null;
    }

    private static double matchScore(java.awt.image.BufferedImage tmpl,
            java.awt.image.BufferedImage zone,
            int ox, int oy, int tw, int th) {
        int samples = 0;
        long totalDiff = 0;
        int step = Math.max(1, Math.min(tw, th) / 8);
        for (int y = 0; y < th; y += step)
            for (int x = 0; x < tw; x += step) {
                Color tc = new Color(tmpl.getRGB(x, y));
                Color rc = new Color(zone.getRGB(ox + x, oy + y));
                totalDiff += Math.abs(tc.getRed() - rc.getRed())
                        + Math.abs(tc.getGreen() - rc.getGreen())
                        + Math.abs(tc.getBlue() - rc.getBlue());
                samples++;
            }
        if (samples == 0)
            return 0;
        return 1.0 - (double) totalDiff / (samples * 255 * 3);
    }
}