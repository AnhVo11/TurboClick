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
        boolean treeCompleted = false; // true when tree ends for any non-error reason
        try {
            String currentId = startNodeId;
            while (currentId != null && running && ctx.isRunning()) {
                BaseNode node = nodes.get(currentId);
                if (node == null || !node.branchEnabled) break;

                if (node.entryDelayMs > 0) Thread.sleep(node.entryDelayMs);

                node.setRunState(RunState.RUNNING);
                if (onNodeStart != null) onNodeStart.accept(node);

                String portResult = null;
                try {
                    portResult = node.execute(ctx);
                    node.setRunState(RunState.SUCCESS);
                } catch (Exception ex) {
                    node.setRunState(RunState.FAILED);
                    ex.printStackTrace();
                    break;
                }

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

                // Unconnected port = end of chain, treat as completed
                if (nextId == null) { treeCompleted = true; break; }
                currentId = nextId;
            }
            // Loop ran to end normally
            if (currentId == null) treeCompleted = true;
        } catch (InterruptedException ignored) {
        } finally {
            // Fire onTreeFinish if: tree completed naturally OR user stopped it
            final boolean notify = treeCompleted || !running;
            new Thread(() -> {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                for (BaseNode n : nodes.values()) n.setRunState(RunState.IDLE);
                if (notify && onTreeFinish != null) onTreeFinish.run();
            }).start();
        }
    }
}