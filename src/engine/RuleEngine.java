package engine;

import nodes.BaseNode;
import nodes.BaseNode.RunState;
import java.util.Map;
import java.util.function.Consumer;

public class RuleEngine implements Runnable {

    private final Map<String, BaseNode> nodes;
    private final String                startNodeId;
    private final ExecutionContext      ctx;
    private volatile boolean            running = true;

    private Consumer<BaseNode> onNodeStart;
    private Consumer<BaseNode> onNodeFinish;
    private Runnable           onTreeFinish;

    public RuleEngine(Map<String, BaseNode> nodes, String startNodeId, ExecutionContext ctx) {
        this.nodes       = nodes;
        this.startNodeId = startNodeId;
        this.ctx         = ctx;
    }

    public void setOnNodeStart(Consumer<BaseNode> cb)  { onNodeStart  = cb; }
    public void setOnNodeFinish(Consumer<BaseNode> cb) { onNodeFinish = cb; }
    public void setOnTreeFinish(Runnable cb)           { onTreeFinish = cb; }

    public void stop() { running = false; ctx.forceStop(); }

    @Override
    public void run() {
        ctx.setNodeMap(nodes);
        boolean treeCompleted = false;
        try {
            String currentId = startNodeId;
            while (currentId != null && running && ctx.isRunning()) {
                BaseNode node = nodes.get(currentId);
                if (node == null || !node.branchEnabled) break;

                if (node.entryDelayMs > 0) Thread.sleep(node.entryDelayMs);

                node.setRunState(RunState.RUNNING);
                if (onNodeStart != null) onNodeStart.accept(node);

                // ── Per-node loop ──────────────────────────────────────
                // Run up to nodeLoopCount times.
                // Stop early if any iteration succeeds.
                // Only fail if ALL iterations fail.
                String portResult = null;
                int loopCount = Math.max(1, node.nodeLoopCount);
                boolean anySuccess = false;

                for (int attempt = 0; attempt < loopCount && running && ctx.isRunning(); attempt++) {
                    try {
                        portResult = node.execute(ctx);
                        // A non-"Not Found" result (or any non-failure port) = success
                        if (portResult != null && !portResult.equalsIgnoreCase("Not Found")) {
                            anySuccess = true;
                            break; // early exit — success achieved
                        } else if (portResult == null) {
                            // null = Stop node (terminal) — always treat as success/done
                            anySuccess = true;
                            break;
                        }
                        // "Not Found" — try again if loops remain
                    } catch (Exception ex) {
                        node.setRunState(RunState.FAILED);
                        ex.printStackTrace();
                        break;
                    }
                }

                if (!anySuccess && portResult != null && portResult.equalsIgnoreCase("Not Found")) {
                    // All loop attempts exhausted with "Not Found" — keep portResult as-is (follow Not Found port)
                    node.setRunState(RunState.FAILED);
                } else if (portResult == null) {
                    node.setRunState(RunState.SUCCESS);
                } else {
                    node.setRunState(anySuccess ? RunState.SUCCESS : RunState.FAILED);
                }
                // ── End per-node loop ──────────────────────────────────

                if (onNodeFinish != null) onNodeFinish.accept(node);

                // null = Stop node (terminal)
                if (portResult == null) { treeCompleted = true; break; }

                // Follow matching connected output port
                String nextId = null;
                for (BaseNode.NodePort port : node.outputs) {
                    if (port.name.equals(portResult) && port.enabled) {
                        if (port.arrowDelayMs > 0) Thread.sleep(port.arrowDelayMs);
                        nextId = port.targetNodeId;
                        break;
                    }
                }

                if (nextId == null) { treeCompleted = true; break; }
                currentId = nextId;
            }
            if (currentId == null) treeCompleted = true;
        } catch (InterruptedException ignored) {
        } finally {
            final boolean notify = treeCompleted || !running;
            new Thread(() -> {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                for (BaseNode n : nodes.values()) n.setRunState(RunState.IDLE);
                if (notify && onTreeFinish != null) onTreeFinish.run();
            }).start();
        }
    }
}