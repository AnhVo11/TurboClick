package ui;

import nodes.BaseNode;
import nodes.BaseNode.*;
import nodes.NodeFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * NodeCanvas — the free-form diagram editor.
 * Supports: drag nodes, draw curved arrows, right-click menus,
 * zoom/pan, node run-state highlighting.
 */
public class NodeCanvas extends JPanel {

    // ── Data ─────────────────────────────────────────────────
    private final Map<String, BaseNode> nodes = new LinkedHashMap<>();
    private final List<Arrow>           arrows = new ArrayList<>();

    // ── Interaction state ────────────────────────────────────
    private BaseNode   dragNode       = null;
    private int        dragOffX       = 0, dragOffY = 0;
    private BaseNode   connectFrom    = null;  // arrow drawing: source node
    private String     connectPort    = null;  // which output port
    private Point      connectMouse   = null;  // live mouse pos while drawing
    private BaseNode   selectedNode   = null;

    // ── Pan / Zoom ────────────────────────────────────────────
    double  zoom    = 1.0;
    int     panX    = 0, panY = 0;
    private int     panDragStartX, panDragStartY;
    private boolean panning = false;

    // ── Callbacks ────────────────────────────────────────────
    private Consumer<BaseNode>  onNodeSelected;
    private Consumer<BaseNode>  onNodeDoubleClick;
    private Runnable            onCanvasChanged;

    // ── Colors ───────────────────────────────────────────────
    private static final Color BG_COLOR      = new Color(22, 22, 28);
    private static final Color GRID_COLOR    = new Color(38, 38, 48);
    private static final Color ARROW_COLOR   = new Color(120, 120, 140);
    private static final Color ARROW_HOVER   = new Color(80, 200, 255);
    private static final Color SELECT_GLOW   = new Color(80, 180, 255);
    private static final Color RUNNING_GLOW  = new Color(40, 220, 100);
    private static final Color SUCCESS_GLOW  = new Color(40, 220, 100);
    private static final Color FAILED_GLOW   = new Color(220, 60, 60);

    // ── Arrow class ───────────────────────────────────────────
    public static class Arrow {
        public String fromNodeId, fromPort, toNodeId;
        public String label;
        Arrow(String from, String port, String to, String label) {
            fromNodeId=from; fromPort=port; toNodeId=to; this.label=label;
        }
    }

    // ── Constructor ───────────────────────────────────────────
    public NodeCanvas() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(800, 600));
        setLayout(null);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { handleMousePressed(e);  }
            @Override public void mouseReleased(MouseEvent e) { handleMouseReleased(e); }
            @Override public void mouseDragged(MouseEvent e)  { handleMouseDragged(e);  }
            @Override public void mouseMoved(MouseEvent e)    { repaint(); }
            @Override public void mouseClicked(MouseEvent e)  { handleMouseClicked(e);  }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // Zoom with scroll wheel
        addMouseWheelListener(e -> {
            double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
            zoom = Math.max(0.3, Math.min(3.0, zoom * factor));
            repaint();
        });
    }

    // ── Public API ────────────────────────────────────────────
    public void setOnNodeSelected(Consumer<BaseNode> cb)     { onNodeSelected     = cb; }
    public void setOnNodeDoubleClick(Consumer<BaseNode> cb)  { onNodeDoubleClick  = cb; }
    public void setOnCanvasChanged(Runnable cb)              { onCanvasChanged    = cb; }

    public void addNode(BaseNode node) {
        nodes.put(node.id, node);
        // Sync existing port connections to arrows
        for (NodePort port : node.outputs) {
            if (port.targetNodeId != null) {
                arrows.add(new Arrow(node.id, port.name, port.targetNodeId, port.displayLabel()));
            }
        }
        repaint();
        notifyChanged();
    }

    public void removeNode(BaseNode node) {
        nodes.remove(node.id);
        arrows.removeIf(a -> a.fromNodeId.equals(node.id) || a.toNodeId.equals(node.id));
        if (selectedNode == node) { selectedNode = null; if (onNodeSelected != null) onNodeSelected.accept(null); }
        repaint(); notifyChanged();
    }

    public Map<String, BaseNode> getNodes() { return nodes; }
    public BaseNode getSelectedNode()       { return selectedNode; }

    public void refreshNode(BaseNode node) { repaint(); }

    public void fitToScreen() {
        if (nodes.isEmpty()) return;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=0,maxY=0;
        for (BaseNode n : nodes.values()) {
            minX=Math.min(minX,n.x); minY=Math.min(minY,n.y);
            maxX=Math.max(maxX,n.x+n.width); maxY=Math.max(maxY,n.y+n.height);
        }
        int padX=60, padY=60;
        double zx = (double)(getWidth()-padX*2)/(maxX-minX+1);
        double zy = (double)(getHeight()-padY*2)/(maxY-minY+1);
        zoom = Math.max(0.3, Math.min(1.5, Math.min(zx,zy)));
        panX = (int)(padX - minX*zoom);
        panY = (int)(padY - minY*zoom);
        repaint();
    }

    // ── Painting ─────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background grid
        drawGrid(g2);

        // Apply pan + zoom transform
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        // Draw arrows first (behind nodes)
        drawArrows(g2);

        // Draw live arrow while connecting
        if (connectFrom != null && connectMouse != null) {
            drawLiveArrow(g2);
        }

        // Draw nodes
        for (BaseNode node : nodes.values()) drawNode(g2, node);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID_COLOR);
        int gridSize = (int)(20 * zoom);
        if (gridSize < 6) return;
        int offX = panX % gridSize, offY = panY % gridSize;
        for (int x = offX; x < getWidth();  x += gridSize) g2.drawLine(x, 0, x, getHeight());
        for (int y = offY; y < getHeight(); y += gridSize) g2.drawLine(0, y, getWidth(), y);
    }

    private void drawNode(Graphics2D g2, BaseNode node) {
        int x = node.x, y = node.y, w = node.width, h = node.height;

        // Run-state glow
        RunState state = node.runState;
        if (state == RunState.RUNNING) {
            long pulse = (System.currentTimeMillis() / 300) % 2;
            g2.setColor(pulse == 0 ? RUNNING_GLOW : new Color(20,180,80,180));
            g2.setStroke(new BasicStroke(4));
            g2.drawRoundRect(x-3, y-3, w+6, h+6, 14, 14);
            repaint(); // keep pulsing
        } else if (state == RunState.SUCCESS && !node.isRunStateExpired(1500)) {
            g2.setColor(SUCCESS_GLOW);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x-2, y-2, w+4, h+4, 14, 14);
        } else if (state == RunState.FAILED && !node.isRunStateExpired(1500)) {
            g2.setColor(FAILED_GLOW);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x-2, y-2, w+4, h+4, 14, 14);
        }

        // Selection glow
        if (node == selectedNode) {
            g2.setColor(SELECT_GLOW);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(x-3, y-3, w+6, h+6, 14, 14);
        }

        // Shadow
        g2.setColor(new Color(0,0,0,60));
        g2.fillRoundRect(x+3, y+3, w, h, 12, 12);

        // Body gradient
        GradientPaint gp = new GradientPaint(x, y, node.nodeColor().brighter(),
                                              x, y+h, node.nodeColor().darker());
        g2.setPaint(gp);
        g2.setStroke(new BasicStroke(1));
        g2.fillRoundRect(x, y, w, h, 12, 12);

        // Border
        g2.setColor(node.nodeColor().brighter().brighter());
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        // Disabled overlay
        if (!node.branchEnabled) {
            g2.setColor(new Color(0,0,0,120));
            g2.fillRoundRect(x, y, w, h, 12, 12);
        }

        // Icon
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.drawString(node.nodeIcon(), x + 8, y + 22);

        // Label
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        String lbl = node.label.length() > 18 ? node.label.substring(0,16)+"…" : node.label;
        g2.drawString(lbl, x + 28, y + 22);

        // Type subtitle
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(new Color(255,255,255,160));
        g2.drawString(node.type.name().replace("_"," "), x + 28, y + 34);

        // Output port dots
        for (int i = 0; i < node.outputs.size(); i++) {
            NodePort port = node.outputs.get(i);
            Point anchor = node.outputAnchor(port.name);
            // Port dot
            g2.setColor(port.enabled ? new Color(180,255,180) : new Color(120,120,120));
            g2.fillOval(anchor.x-5, anchor.y-5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(anchor.x-5, anchor.y-5, 10, 10);
        }

        // Input dot (top center)
        Point inp = node.inputAnchor();
        g2.setColor(new Color(180,200,255));
        g2.fillOval(inp.x-5, inp.y-5, 10, 10);
        g2.setColor(Color.WHITE);
        g2.drawOval(inp.x-5, inp.y-5, 10, 10);
    }

    private void drawArrows(Graphics2D g2) {
        for (Arrow a : arrows) {
            BaseNode from = nodes.get(a.fromNodeId);
            BaseNode to   = nodes.get(a.toNodeId);
            if (from == null || to == null) continue;
            Point src = from.outputAnchor(a.fromPort);
            Point dst = to.inputAnchor();
            drawCurvedArrow(g2, src, dst, ARROW_COLOR, a.label);
        }
    }

    private void drawLiveArrow(Graphics2D g2) {
        Point src = connectFrom.outputAnchor(connectPort);
        // Convert mouse from screen to canvas coords
        int mx = (int)((connectMouse.x - panX) / zoom);
        int my = (int)((connectMouse.y - panY) / zoom);
        drawCurvedArrow(g2, src, new Point(mx, my), ARROW_HOVER, null);
    }

    private void drawCurvedArrow(Graphics2D g2, Point src, Point dst, Color color, String label) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Control points for cubic bezier — curves downward
        int dy = Math.max(60, Math.abs(dst.y - src.y) / 2);
        CubicCurve2D curve = new CubicCurve2D.Float(
            src.x, src.y,
            src.x, src.y + dy,
            dst.x, dst.y - dy,
            dst.x, dst.y
        );
        g2.draw(curve);

        // Arrowhead
        drawArrowHead(g2, dst, color);

        // Label at midpoint
        if (label != null && !label.isEmpty()) {
            double t = 0.5;
            // Approximate midpoint of cubic bezier
            float mx = (float)(Math.pow(1-t,3)*src.x + 3*Math.pow(1-t,2)*t*src.x
                      + 3*(1-t)*t*t*dst.x + Math.pow(t,3)*dst.x);
            float my = (float)(Math.pow(1-t,3)*src.y + 3*Math.pow(1-t,2)*t*(src.y+dy)
                      + 3*(1-t)*t*t*(dst.y-dy) + Math.pow(t,3)*dst.y);
            g2.setColor(new Color(220,220,100));
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            // Small pill background
            g2.setColor(new Color(40,40,50,200));
            g2.fillRoundRect((int)mx - lw/2 - 3, (int)my - 12, lw+6, 16, 6, 6);
            g2.setColor(new Color(220,220,100));
            g2.drawString(label, mx - lw/2, my);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point tip, Color color) {
        g2.setColor(color);
        int size = 8;
        // Simple downward triangle (since arrows generally flow downward)
        int[] xs = { tip.x, tip.x - size/2, tip.x + size/2 };
        int[] ys = { tip.y, tip.y - size,   tip.y - size   };
        g2.fillPolygon(xs, ys, 3);
    }

    // ── Mouse handlers ────────────────────────────────────────
    private void handleMousePressed(MouseEvent e) {
        Point canvas = screenToCanvas(e.getPoint());

        if (SwingUtilities.isMiddleMouseButton(e) || (e.isAltDown() && SwingUtilities.isLeftMouseButton(e))) {
            panning = true; panDragStartX = e.getX() - panX; panDragStartY = e.getY() - panY; return;
        }

        // Check if clicking a port dot → start arrow drawing
        for (BaseNode node : nodes.values()) {
            for (NodePort port : node.outputs) {
                Point anchor = node.outputAnchor(port.name);
                if (canvas.distance(anchor) < 10) {
                    connectFrom = node; connectPort = port.name; connectMouse = e.getPoint(); return;
                }
            }
        }

        // Check if clicking a node body
        BaseNode hit = nodeAt(canvas);
        if (hit != null) {
            dragNode = hit; dragOffX = canvas.x - hit.x; dragOffY = canvas.y - hit.y;
            selectNode(hit);
        } else {
            selectNode(null);
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        panning = false;

        if (connectFrom != null) {
            // Check if dropped on a node input
            Point canvas = screenToCanvas(e.getPoint());
            BaseNode target = nodeAt(canvas);
            if (target != null && target != connectFrom) {
                // Create the arrow
                connectFrom.setPortTarget(connectPort, target.id);
                arrows.removeIf(a -> a.fromNodeId.equals(connectFrom.id) && a.fromPort.equals(connectPort));
                NodePort port = null;
                for (NodePort p : connectFrom.outputs) if (p.name.equals(connectPort)) { port = p; break; }
                String lbl = port != null ? port.displayLabel() : connectPort;
                arrows.add(new Arrow(connectFrom.id, connectPort, target.id, lbl));
                notifyChanged();
            }
            connectFrom = null; connectPort = null; connectMouse = null;
            repaint(); return;
        }

        dragNode = null;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (panning) { panX = e.getX()-panDragStartX; panY = e.getY()-panDragStartY; repaint(); return; }
        if (connectFrom != null) { connectMouse = e.getPoint(); repaint(); return; }
        if (dragNode != null) {
            Point canvas = screenToCanvas(e.getPoint());
            dragNode.x = Math.max(0, canvas.x - dragOffX);
            dragNode.y = Math.max(0, canvas.y - dragOffY);
            repaint(); notifyChanged();
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        Point canvas = screenToCanvas(e.getPoint());

        if (e.getButton() == MouseEvent.BUTTON3) {
            showContextMenu(e, canvas); return;
        }

        if (e.getClickCount() == 2) {
            BaseNode hit = nodeAt(canvas);
            if (hit != null && onNodeDoubleClick != null) onNodeDoubleClick.accept(hit);
        }
    }

    // ── Context menu ─────────────────────────────────────────
    private void showContextMenu(MouseEvent e, Point canvas) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(35,35,45));

        BaseNode hit = nodeAt(canvas);
        if (hit != null) {
            addMenuItem(menu, "✏ Edit \"" + hit.label+"\"",  () -> { if(onNodeDoubleClick!=null) onNodeDoubleClick.accept(hit); });
            addMenuItem(menu, hit.branchEnabled ? "⊘ Disable Node" : "✓ Enable Node",
                () -> { hit.branchEnabled = !hit.branchEnabled; repaint(); notifyChanged(); });
            addMenuSep(menu);
            addMenuItem(menu, "🗑 Delete Node", () -> removeNode(hit));
        } else {
            JLabel header = new JLabel("  Add Node");
            header.setForeground(new Color(150,150,180));
            header.setFont(new Font("SansSerif",Font.BOLD,11));
            menu.add(header);
            addMenuSep(menu);
            for (BaseNode.NodeType type : BaseNode.NodeType.values()) {
                final BaseNode.NodeType t = type;
                addMenuItem(menu,
                    NodeFactory.icon(t) + "  " + NodeFactory.displayName(t),
                    () -> {
                        BaseNode n = NodeFactory.create(t, canvas.x, canvas.y);
                        addNode(n);
                        selectNode(n);
                        if (onNodeDoubleClick != null) onNodeDoubleClick.accept(n);
                    });
            }
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addMenuItem(JPopupMenu m, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(new Color(35,35,45));
        item.setForeground(new Color(220,220,220));
        item.setFont(new Font("SansSerif",Font.PLAIN,12));
        item.setBorderPainted(false);
        item.addActionListener(e -> action.run());
        m.add(item);
    }

    private void addMenuSep(JPopupMenu m) {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60,60,70));
        m.add(sep);
    }

    // ── Helpers ───────────────────────────────────────────────
    private BaseNode nodeAt(Point canvas) {
        // Iterate reverse so top-painted nodes are hit first
        List<BaseNode> list = new ArrayList<>(nodes.values());
        for (int i = list.size()-1; i >= 0; i--)
            if (list.get(i).bounds().contains(canvas)) return list.get(i);
        return null;
    }

    private Point screenToCanvas(Point p) {
        return new Point((int)((p.x - panX) / zoom), (int)((p.y - panY) / zoom));
    }

    private void selectNode(BaseNode node) {
        selectedNode = node;
        if (onNodeSelected != null) onNodeSelected.accept(node);
        repaint();
    }

    private void notifyChanged() {
        if (onCanvasChanged != null) onCanvasChanged.run();
    }
}