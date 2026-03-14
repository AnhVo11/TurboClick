package engine;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * SimpleClickEngine — the core auto-clicker loop.
 * Used by both the Build Simple Click panel and the SimpleClickNode.
 */
public class SimpleClickEngine implements Runnable {

    private final List<int[]>      points;       // {x,y,clicks,subDelay,delayAfter}
    private final long             intervalMs;
    private final long             maxClicks;
    private final int              mouseButton;
    private final boolean          doubleClick;
    private final boolean          repeatUntilStopped;
    private final int              repeatTimes;
    private final ExecutionContext ctx;          // nullable — used for ctx.isRunning()

    private volatile boolean stopped    = false;
    private volatile boolean wasStopped = false;
    private volatile long    clickCount = 0;

    // Callbacks for UI updates
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

    @Override
    public void run() {
        try {
            Robot robot = new Robot();
            int btn = mouseButton == 1 ? InputEvent.BUTTON3_DOWN_MASK
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
                    for (int[] pt : points) {
                        if (!isActive()) break outer;
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
                } else {
                    doClick(robot, btn);
                    clickCount++;
                    if (clickCallback != null) clickCallback.onClick(clickCount);
                    sleepCountdown((int) intervalMs);
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private int lastMatchX = -1, lastMatchY = -1;
    public void setLastMatch(int x, int y) { lastMatchX=x; lastMatchY=y; }

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