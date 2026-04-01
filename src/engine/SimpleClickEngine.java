package engine;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * SimpleClickEngine — the core auto-clicker loop.
 * Points array: {x, y, clicks, subDelayMs, afterDelayMs, btnType}
 * btnType: 0=left, 1=right, 2=middle (optional 6th element, falls back to global mouseButton)
 */
public class SimpleClickEngine implements Runnable {

    private final List<int[]>      points;
    private final long             intervalMs;
    private final long             maxClicks;
    private final int              mouseButton;       // global fallback
    private final boolean          doubleClick;
    private final boolean          repeatUntilStopped;
    private final int              repeatTimes;
    private final ExecutionContext ctx;

    private volatile boolean stopped    = false;
    private volatile boolean wasStopped = false;
    private volatile long    clickCount = 0;

    public interface ClickCallback { void onClick(long total); }
    private ClickCallback clickCallback;

    public SimpleClickEngine(List<int[]> points, long intervalMs, long maxClicks,
                             int mouseButton, boolean doubleClick,
                             boolean repeatUntilStopped, int repeatTimes,
                             ExecutionContext ctx) {
        this.points             = points;
        this.intervalMs         = intervalMs;
        this.maxClicks          = maxClicks;
        this.mouseButton        = mouseButton;
        this.doubleClick        = doubleClick;
        this.repeatUntilStopped = repeatUntilStopped;
        this.repeatTimes        = repeatTimes;
        this.ctx                = ctx;
    }

    public void setClickCallback(ClickCallback cb) { clickCallback = cb; }
    public void stop()         { stopped = true; wasStopped = true; }
    public boolean wasStopped(){ return wasStopped; }
    public long getClickCount(){ return clickCount; }

    private boolean isActive() {
        if (stopped) return false;
        if (ctx != null && !ctx.isRunning()) return false;
        if (maxClicks > 0 && clickCount >= maxClicks) return false;
        return true;
    }

    /** Resolve button mask for a point — reads pt[5] if available, falls back to global */
    private int btnMaskForPoint(int[] pt) {
        int btn = (pt.length > 5) ? pt[5] : mouseButton;
        switch (btn) {
            case 1:  return InputEvent.BUTTON3_DOWN_MASK; // right
            case 2:  return InputEvent.BUTTON2_DOWN_MASK; // middle
            default: return InputEvent.BUTTON1_DOWN_MASK; // left
        }
    }

    @Override
    public void run() {
        try {
            Robot robot = new Robot();
            int globalBtn = mouseButton == 1 ? InputEvent.BUTTON3_DOWN_MASK
                          : mouseButton == 2 ? InputEvent.BUTTON2_DOWN_MASK
                          :                    InputEvent.BUTTON1_DOWN_MASK;

            int loopsDone = 0;
            outer:
            while (isActive()) {
                if (!repeatUntilStopped) {
                    if (loopsDone >= repeatTimes) break;
                    loopsDone++;
                }

                if (!points.isEmpty()) {
                    for (int i = 0; i < points.size(); i++) {
                        if (!isActive()) break outer;
                        int[] pt = points.get(i);
                        int btnType = (pt.length > 5) ? pt[5] : mouseButton;

                        if (btnType == 3) {
                            // Drag: press at this point, move to next point, release
                            int[] next = (i + 1 < points.size()) ? points.get(i + 1) : pt;
                            int dragBtn = InputEvent.BUTTON1_DOWN_MASK;
                            robot.mouseMove(pt[0], pt[1]);
                            Thread.sleep(40);
                            robot.mousePress(dragBtn);
                            Thread.sleep(40);
                            // Smooth move to destination
                            int steps = 20;
                            for (int s = 1; s <= steps; s++) {
                                if (!isActive()) break;
                                int ix = pt[0] + (next[0]-pt[0]) * s / steps;
                                int iy = pt[1] + (next[1]-pt[1]) * s / steps;
                                robot.mouseMove(ix, iy);
                                Thread.sleep(Math.max(1, pt[3] / steps));
                            }
                            robot.mouseRelease(dragBtn);
                            clickCount++;
                            if (clickCallback != null) clickCallback.onClick(clickCount);
                            sleepCountdown(pt[4]);
                            i++; // skip the next point (it was the drag destination)
                        } else {
                            int btn = btnMaskForPoint(pt);
                            robot.mouseMove(pt[0], pt[1]);
                            Thread.sleep(40);
                            for (int ci = 0; ci < pt[2]; ci++) {
                                if (!isActive()) break outer;
                                doClick(robot, btn);
                                clickCount++;
                                if (clickCallback != null) clickCallback.onClick(clickCount);
                                if (ci < pt[2]-1) sleepCountdown(pt[3]);
                            }
                            sleepCountdown(pt[4]);
                        }
                    }
                } else {
                    doClick(robot, globalBtn);
                    clickCount++;
                    if (clickCallback != null) clickCallback.onClick(clickCount);
                    sleepCountdown((int) intervalMs);
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void doClick(Robot r, int btn) throws InterruptedException {
        r.mousePress(btn); r.mouseRelease(btn);
        if (doubleClick) { Thread.sleep(40); r.mousePress(btn); r.mouseRelease(btn); }
    }

    private void sleepCountdown(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && isActive())
            Thread.sleep(Math.min(50, end - System.currentTimeMillis() + 1));
    }
}