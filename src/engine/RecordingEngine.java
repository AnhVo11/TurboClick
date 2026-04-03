package engine;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RecordingEngine implements NativeMouseListener, NativeMouseMotionListener {

    public static class RecordedAction {
        public enum Type { LEFT_CLICK, RIGHT_CLICK, DOUBLE_CLICK, DRAG }
        public Type type;
        public int x, y, endX, endY;
        public long timestamp;
        public String screenshotBase64;
        public String describe() {
            switch (type) {
                case LEFT_CLICK:   return "Left click at (" + x + ", " + y + ")";
                case RIGHT_CLICK:  return "Right click at (" + x + ", " + y + ")";
                case DOUBLE_CLICK: return "Double click at (" + x + ", " + y + ")";
                case DRAG:         return "Drag from (" + x+","+y+") to ("+endX+","+endY+")";
                default:           return "Action at (" + x + ", " + y + ")";
            }
        }
    }

    private final List<RecordedAction> actions = new ArrayList<>();
    private volatile boolean recording = false;
    private Runnable onActionRecorded;
    private int pressX, pressY;
    private boolean dragging = false;
    private static final int DRAG_THRESHOLD = 10;
    private static final long DOUBLE_CLICK_MS = 300;
    private long lastClickTime = 0;
    private int lastClickX, lastClickY;

    public void start() {
        actions.clear(); recording = true;
        try {
            GlobalScreen.addNativeMouseListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void stop() {
        recording = false;
        try {
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
        } catch (Exception ignored) {}
    }

    public List<RecordedAction> getActions() { return actions; }
    public boolean isRecording() { return recording; }
    public void setOnActionRecorded(Runnable cb) { onActionRecorded = cb; }

    private String captureScreen() {
        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            BufferedImage img = new Robot().createScreenCapture(
                new Rectangle(0, 0, screen.width, screen.height));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) { return null; }
    }

    private void record(RecordedAction action) {
        action.timestamp = System.currentTimeMillis();
        action.screenshotBase64 = captureScreen();
        actions.add(action);
        if (onActionRecorded != null) onActionRecorded.run();
    }

    public void nativeMousePressed(NativeMouseEvent e) {
        if (!recording) return;
        pressX = e.getX(); pressY = e.getY(); dragging = false;
    }

    public void nativeMouseReleased(NativeMouseEvent e) {
        if (!recording) return;
        int rx = e.getX(), ry = e.getY();
        int dx = Math.abs(rx-pressX), dy = Math.abs(ry-pressY);
        if (dragging || dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
            RecordedAction a = new RecordedAction();
            a.type = RecordedAction.Type.DRAG;
            a.x = pressX; a.y = pressY; a.endX = rx; a.endY = ry;
            record(a); dragging = false; return;
        }
        long now = System.currentTimeMillis();
        boolean isDouble = (now-lastClickTime < DOUBLE_CLICK_MS)
            && Math.abs(rx-lastClickX)<5 && Math.abs(ry-lastClickY)<5;
        RecordedAction a = new RecordedAction();
        if (isDouble) { a.type = RecordedAction.Type.DOUBLE_CLICK; lastClickTime = 0; }
        else if (e.getButton() == NativeMouseEvent.BUTTON2) { a.type = RecordedAction.Type.RIGHT_CLICK; }
        else { a.type = RecordedAction.Type.LEFT_CLICK; lastClickTime = now; lastClickX = rx; lastClickY = ry; }
        a.x = rx; a.y = ry;
        record(a);
    }

    public void nativeMouseClicked(NativeMouseEvent e) {}
    public void nativeMouseMoved(NativeMouseEvent e) {}
    public void nativeMouseDragged(NativeMouseEvent e) {
        if (!recording) return;
        if (Math.abs(e.getX()-pressX)>DRAG_THRESHOLD || Math.abs(e.getY()-pressY)>DRAG_THRESHOLD)
            dragging = true;
    }
}