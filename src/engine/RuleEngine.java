package engine;

import nodes.BaseNode;
import nodes.BaseNode.RunState;
import java.awt.Robot;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RuleEngine — walks the node graph and executes each node in sequence.
 * Runs on its own thread. Notifies UI of state changes via callbacks.
 */
public class RuleEngine implements Runnable {

    private final Map<String, BaseNode> nodes;     // all nodes in the tree, keyed by id
    private final String                startNodeId;
    private final ExecutionContext      ctx;
    private volatile boolean            running = true;

    // UI callbacks
    private Consumer<BaseNode> onNodeStart;    // node began executing
    private Consumer<BaseNode> onNodeFinish;   // node finished (with state set)
    private Runnable           onTreeFinish;   // whole tree is done

    public RuleEngine(Map<String, BaseNode> nodes, String startNodeId, ExecutionContext ctx) {
        this.nodes       = nodes;
        this.startNodeId = startNodeId;
        this.ctx         = ctx;
    }

    public void setOnNodeStart(Consumer<BaseNode> cb)  { onNodeStart   = cb; }
    public void setOnNodeFinish(Consumer<BaseNode> cb) { onNodeFinish  = cb; }
    public void setOnTreeFinish(Runnable cb)           { onTreeFinish  = cb; }

    public void stop() { running = false; ctx.forceStop(); }

    @Override
    public void run() {
        try {
            String currentId = startNodeId;
            while (currentId != null && running && ctx.isRunning()) {
                BaseNode node = nodes.get(currentId);
                if (node == null || !node.branchEnabled) break;

                // Entry delay
                if (node.entryDelayMs > 0) Thread.sleep(node.entryDelayMs);

                // Notify UI: node starting
                node.setRunState(RunState.RUNNING);
                if (onNodeStart != null) onNodeStart.accept(node);

                // Execute
                String portResult = null;
                try {
                    portResult = node.execute(ctx);
                    node.setRunState(RunState.SUCCESS);
                } catch (Exception ex) {
                    node.setRunState(RunState.FAILED);
                    ex.printStackTrace();
                    break;
                }

                // Notify UI: node done
                if (onNodeFinish != null) onNodeFinish.accept(node);

                // Null = terminal (Stop node)
                if (portResult == null) break;

                // Follow the matching output port
                String nextId = null;
                for (BaseNode.NodePort port : node.outputs) {
                    if (port.name.equals(portResult) && port.enabled) {
                        // Arrow delay
                        if (port.arrowDelayMs > 0) Thread.sleep(port.arrowDelayMs);
                        nextId = port.targetNodeId;
                        break;
                    }
                }
                currentId = nextId;
            }
        } catch (InterruptedException ignored) {
        } finally {
            // Reset all node run states after a short display window
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                for (BaseNode n : nodes.values()) n.setRunState(RunState.IDLE);
                if (onTreeFinish != null) onTreeFinish.run();
            }).start();
        }
    }
}