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

public class NodeCanvas extends JPanel {

    // ── Data ─────────────────────────────────────────────────
    private final Map<String, BaseNode> nodes = new LinkedHashMap<>();
    private final List<Arrow> arrows = new ArrayList<>();

    // ── Interaction ───────────────────────────────────────────
    private BaseNode dragNode = null;
    private int dragOffX = 0, dragOffY = 0;
    private BaseNode connectFrom = null;
    private String connectPort = null;
    private Point connectMouse = null;
    private BaseNode selectedNode = null;
    private Arrow selectedArrow = null;
    private Arrow bendDragArrow = null;
    private int bendDragStartY = 0;
    private int bendDragOrigOffset = 0;
    private String startNodeId = null;

    // ── Rubber-band selection ─────────────────────────────────
    private Set<BaseNode> selectedNodes = new LinkedHashSet<>();
    private Point rubberStart = null;
    private Rectangle rubberRect = null;

    // ── Ghost drag (from palette) ─────────────────────────────
    private BaseNode.NodeType ghostType = null;
    private Point ghostPoint = null;

    // ── Pan / Zoom ────────────────────────────────────────────
    double zoom = 1.0;
    int panX = 0, panY = 0;
    private int panDragStartX, panDragStartY;
    private boolean panning = false;

    // ── Callbacks ─────────────────────────────────────────────
    private Consumer<BaseNode> onNodeSelected;
    private Consumer<BaseNode> onNodeDoubleClick;
    private Runnable onCanvasChanged;

    // ── Colors ────────────────────────────────────────────────
    private static final Color BG_COLOR = new Color(22, 22, 28);
    private static final Color GRID_COLOR = new Color(38, 38, 48);
    private static final Color ARROW_COLOR = new Color(120, 120, 140);
    private static final Color ARROW_HOVER = new Color(80, 200, 255);
    private static final Color SELECT_GLOW = new Color(80, 180, 255);
    private static final Color RUNNING_GLOW = new Color(40, 220, 100);
    private static final Color SUCCESS_GLOW = new Color(40, 220, 100);
    private static final Color FAILED_GLOW = new Color(220, 60, 60);

    private static Color portColor(String portName) {
        String n = portName.toLowerCase();
        if (n.contains("not found"))
            return new Color(220, 60, 60);
        if (n.equals("stopped") || n.contains("fail"))
            return new Color(220, 60, 60);
        if (n.contains("timeout"))
            return new Color(220, 180, 40);
        if (n.contains("found") || n.contains("done") || n.equals("loop"))
            return new Color(60, 200, 80);
        if (n.equals("stop"))
            return new Color(220, 60, 60);
        return new Color(100, 140, 220);
    }

    private static String portIcon(String portName) {
        String n = portName.toLowerCase();
        if (n.contains("not found"))
            return "\u2717";
        if (n.equals("stopped") || n.contains("fail"))
            return "\u2717";
        if (n.equals("stop"))
            return "\u2717";
        if (n.contains("timeout"))
            return "\u2299";
        if (n.contains("found") || n.contains("done") || n.equals("loop"))
            return "\u2713";
        return "\u2192";
    }

    public static class Arrow {
        public String fromNodeId, fromPort, toNodeId, label;
        public int bendOffset = 40;
        public boolean selected = false;

        public Arrow(String from, String port, String to, String label) {
            fromNodeId = from;
            fromPort = port;
            toNodeId = to;
            this.label = label;
        }
    }

    public NodeCanvas() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(800, 600));

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }

            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }

            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }

            public void mouseMoved(MouseEvent e) {
                repaint();
            }

            public void mouseClicked(MouseEvent e) {
                handleMouseClicked(e);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
                        && selectedArrow != null) {
                    BaseNode from = nodes.get(selectedArrow.fromNodeId);
                    if (from != null)
                        from.setPortTarget(selectedArrow.fromPort, null);
                    arrows.remove(selectedArrow);
                    selectedArrow = null;
                    repaint();
                    notifyChanged();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
    }

    public void setOnNodeSelected(Consumer<BaseNode> cb) {
        onNodeSelected = cb;
    }

    public void setStartNode(String id) {
        startNodeId = id;
        repaint();
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setOnNodeDoubleClick(Consumer<BaseNode> cb) {
        onNodeDoubleClick = cb;
    }

    public void setOnCanvasChanged(Runnable cb) {
        onCanvasChanged = cb;
    }

    public void startGhostDrag(BaseNode.NodeType type) {
        ghostType = type;
        ghostPoint = MouseInfo.getPointerInfo().getLocation();
        repaint();
    }

    public void updateGhostDrag(Point screenPoint) {
        ghostPoint = screenPoint;
        repaint();
    }

    public BaseNode finishGhostDrag(Point screenPoint) {
        if (ghostType == null)
            return null;
        Point compPt;
        try {
            Point loc = getLocationOnScreen();
            int sx = screenPoint.x - loc.x;
            int sy = screenPoint.y - loc.y;
            compPt = screenToCanvas(new Point(sx, sy));
        } catch (Exception e) {
            compPt = new Point(100 + (int) (Math.random() * 300), 80 + (int) (Math.random() * 200));
        }
        BaseNode node = NodeFactory.create(ghostType, Math.max(10, compPt.x - 90), Math.max(10, compPt.y - 37));
        ghostType = null;
        ghostPoint = null;
        addNode(node);
        selectNode(node);
        repaint();
        return node;
    }

    public void cancelGhostDrag() {
        ghostType = null;
        ghostPoint = null;
        repaint();
    }

    public void addNode(BaseNode node) {
        node.width = 180;
        node.height = 75;
        nodes.put(node.id, node);
        for (NodePort port : node.outputs)
            if (port.targetNodeId != null)
                arrows.add(new Arrow(node.id, port.name, port.targetNodeId, port.displayLabel()));
        repaint();
        notifyChanged();
    }

    public void removeNode(BaseNode node) {
        nodes.remove(node.id);
        arrows.removeIf(a -> a.fromNodeId.equals(node.id) || a.toNodeId.equals(node.id));
        if (selectedNode == node) {
            selectedNode = null;
            if (onNodeSelected != null)
                onNodeSelected.accept(null);
        }
        repaint();
        notifyChanged();
    }

    public Map<String, BaseNode> getNodes() {
        return nodes;
    }

    public List<Arrow> getArrows() {
        return arrows;
    }

    public BaseNode getSelectedNode() {
        return selectedNode;
    }

    public void refreshNode(BaseNode node) {
        repaint();
    }

    public void zoomIn() {
        zoom = Math.min(3.0, zoom * 1.2);
        repaint();
    }

    public void zoomOut() {
        zoom = Math.max(0.3, zoom / 1.2);
        repaint();
    }

    public void zoomReset() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
    }

    public void fitToScreen() {
        if (nodes.isEmpty())
            return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        for (BaseNode n : nodes.values()) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + n.width);
            maxY = Math.max(maxY, n.y + n.height);
        }
        double zx = (double) (getWidth() - 120) / (maxX - minX + 1);
        double zy = (double) (getHeight() - 120) / (maxY - minY + 1);
        zoom = Math.max(0.3, Math.min(1.5, Math.min(zx, zy)));
        panX = (int) (60 - minX * zoom);
        panY = (int) (60 - minY * zoom);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGrid(g2);
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);
        drawArrows(g2);
        if (connectFrom != null && connectMouse != null)
            drawLiveArrow(g2);
        for (BaseNode node : nodes.values())
            drawNode(g2, node);
        if (ghostType != null && ghostPoint != null)
            drawGhost(g2);
        g2.dispose();
        // Rubber band — drawn in screen coords, no transform
        if (rubberRect != null) {
            Graphics2D gr = (Graphics2D) g.create();
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gr.setColor(new Color(80, 160, 255, 35));
            gr.fill(rubberRect);
            gr.setColor(new Color(80, 160, 255, 200));
            gr.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[] { 5f, 3f }, 0f));
            gr.draw(rubberRect);
            gr.dispose();
        }
    }

    private static String nodeTypeBadge(BaseNode.NodeType type) {
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
                return "\u25a3";
            case WATCH_CASE:
                return "\u2756";
            default:
                return "\u2022";
        }
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID_COLOR);
        int gs = (int) (20 * zoom);
        if (gs < 6)
            return;
        int ox = panX % gs, oy = panY % gs;
        for (int x = ox; x < getWidth(); x += gs)
            g2.drawLine(x, 0, x, getHeight());
        for (int y = oy; y < getHeight(); y += gs)
            g2.drawLine(0, y, getWidth(), y);
    }

    private void drawGhost(Graphics2D g2) {
        Point screen = ghostPoint;
        int cx, cy;
        try {
            Point loc = getLocationOnScreen();
            cx = (int) ((screen.x - loc.x - panX) / zoom) - 90;
            cy = (int) ((screen.y - loc.y - panY) / zoom) - 37;
        } catch (Exception e) {
            return;
        }
        Color nc = NodeFactory.color(ghostType);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2.setColor(nc.darker());
        g2.fillRoundRect(cx, cy, 180, 75, 12, 12);
        g2.setColor(nc.brighter());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(cx, cy, 180, 75, 12, 12);
        // Badge circle
        g2.setColor(nc.brighter());
        g2.fillOval(cx + 7, cy + 7, 22, 22);
        g2.setColor(Color.WHITE);
        String badge = nodeTypeBadge(ghostType);
        boolean bigIcon2 = ghostType == BaseNode.NodeType.KEYBOARD
                || ghostType == BaseNode.NodeType.SIMPLE_CLICK
                || ghostType == BaseNode.NodeType.MESSAGE;
        Font bf = new Font("Dialog", Font.BOLD, bigIcon2 ? 17 : 13);
        g2.setFont(bf);
        FontMetrics bfm = g2.getFontMetrics();
        g2.drawString(badge, cx + 7 + (22 - bfm.stringWidth(badge)) / 2,
                cy + 7 + (22 - bfm.getHeight()) / 2 + bfm.getAscent());
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        g2.drawString(NodeFactory.displayName(ghostType), cx + 34, cy + 22);
        g2.setComposite(old);
    }

    // ── Loop badge geometry ─────────────────────────────────────
    /**
     * Returns the bounding rect [x, y, w, h] of the loop badge for a node (canvas
     * coords).
     */
    private static int[] loopBadgeRect(BaseNode node) {
        int bw = 38;
        int bh = 17;
        int bx = node.x + node.width - bw - 6; // inside, right-aligned
        int by = node.y + 6; // inside, top-aligned
        return new int[] { bx, by, bw, bh };
    }

    private boolean loopBadgeHit(BaseNode node, Point cv) {
        int[] r = loopBadgeRect(node);
        return cv.x >= r[0] && cv.x <= r[0] + r[2] && cv.y >= r[1] && cv.y <= r[1] + r[3];
    }

    /**
     * Draws the ↺ loop-count badge floating above the top-right corner of a node.
     */
    private void drawLoopBadge(Graphics2D g2, BaseNode node) {
        int lc = node.nodeLoopCount;
        int[] r = loopBadgeRect(node);
        int bx = r[0], by = r[1], bw = r[2], bh = r[3];

        Color nodeCol = node.nodeColor();
        Color bg, fg, border;
        if (lc > 1) {
            // Active — warm tint derived from node color
            bg = new Color(
                    Math.min(255, nodeCol.getRed() + 60),
                    Math.min(255, nodeCol.getGreen() + 40),
                    Math.min(255, nodeCol.getBlue() + 10),
                    210);
            fg = Color.WHITE;
            border = new Color(255, 220, 100, 180);
        } else {
            // Inactive — translucent overlay of node color, nearly invisible
            bg = new Color(nodeCol.getRed(), nodeCol.getGreen(), nodeCol.getBlue(), 80);
            fg = new Color(255, 255, 255, 200);
            border = new Color(255, 255, 255, 80);
        }

        g2.setColor(bg);
        g2.fillRoundRect(bx, by, bw, bh, 7, 7);

        g2.setColor(border);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawRoundRect(bx, by, bw, bh, 7, 7);

        String text = lc > 1 ? "\u21ba " + (lc - 1) : "\u21ba 0";
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        g2.drawString(text, bx + (bw - tw) / 2, by + bh - 4);
    }

    private void drawNode(Graphics2D g2, BaseNode node) {
        // Force Watch Case to minimum size so it looks consistent
        if (node.type == BaseNode.NodeType.WATCH_CASE) {
            node.width = Math.max(182, node.width);
            node.height = Math.max(122, node.height);
        }
        int x = node.x, y = node.y, w = node.width, h = node.height;

        RunState state = node.runState;
        if (state == RunState.RUNNING) {
            long pulse = (System.currentTimeMillis() / 300) % 2;
            g2.setColor(pulse == 0 ? RUNNING_GLOW : new Color(20, 180, 80, 180));
            g2.setStroke(new BasicStroke(4));
            g2.drawRoundRect(x - 3, y - 3, w + 6, h + 6, 14, 14);
            repaint();
        } else if (state == RunState.SUCCESS && !node.isRunStateExpired(1500)) {
            g2.setColor(SUCCESS_GLOW);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x - 2, y - 2, w + 4, h + 4, 14, 14);
        } else if (state == RunState.FAILED && !node.isRunStateExpired(1500)) {
            g2.setColor(FAILED_GLOW);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x - 2, y - 2, w + 4, h + 4, 14, 14);
        }
        if (selectedNodes.contains(node)) {
            g2.setColor(new Color(80, 160, 255, 160));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x - 2, y - 2, w + 4, h + 4, 14, 14);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(x - 3, y - 3, w + 6, h + 6, 14, 14);
        }
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(x + 3, y + 3, w, h, 12, 12);
        GradientPaint gp = new GradientPaint(x, y, node.nodeColor().brighter(), x, y + h, node.nodeColor().darker());
        g2.setPaint(gp);
        g2.setStroke(new BasicStroke(1));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(node.nodeColor().brighter().brighter());
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        if (!node.branchEnabled) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(x, y, w, h, 12, 12);
        }

        // Badge circle — use unicode so it renders well
        Color ic = node.nodeColor().brighter();
        g2.setColor(ic);
        g2.fillOval(x + 7, y + 7, 22, 22);
        g2.setColor(Color.WHITE);
        String badge = nodeTypeBadge(node.type);
        boolean bigIcon = node.type == BaseNode.NodeType.KEYBOARD
                || node.type == BaseNode.NodeType.SIMPLE_CLICK
                || node.type == BaseNode.NodeType.MESSAGE;
        Font bf = new Font("Dialog", Font.BOLD, bigIcon ? 17 : 13);
        g2.setFont(bf);
        FontMetrics bfm = g2.getFontMetrics();
        g2.drawString(badge, x + 7 + (22 - bfm.stringWidth(badge)) / 2,
                y + 7 + (22 - bfm.getHeight()) / 2 + bfm.getAscent());

        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        String lbl = node.label.length() > 16 ? node.label.substring(0, 14) + "\u2026" : node.label;
        g2.drawString(lbl, x + 34, y + 20);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawString(node.type.name().replace("_", " "), x + 34, y + 32);

        if (node.id.equals(startNodeId)) {
            g2.setColor(new Color(40, 220, 80));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("\u25b6", x + w - 18, y + 14);
        }

        // ── Loop badge ──────────────────────────────────────────
        drawLoopBadge(g2, node);

        if (node.type == BaseNode.NodeType.IMAGE) {
            // Image node: output port on right-center side
            int rx = x + w, ry = y + h / 2;
            g2.setColor(new Color(100, 200, 100));
            g2.fillOval(rx - 5, ry - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(rx - 5, ry - 5, 10, 10);
            // Arrow label hint
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            g2.setColor(new Color(150, 220, 150));
            g2.drawString("→", rx - 14, ry + 4);
        } else if (node.type == BaseNode.NodeType.WATCH_CASE) {
            // ── Top: standard input dot (accepts connections from other nodes) ──
            Point inp = node.inputAnchor();
            g2.setColor(new Color(160, 200, 255));
            g2.fillOval(inp.x - 5, inp.y - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(inp.x - 5, inp.y - 5, 10, 10);

            // ── Left: placeholder or numbered input ports (Image nodes) ──
            if (node.inputs.isEmpty()) {
                // White dot + arrow hint, shifted down a bit from center
                int phx = x, phy = y + h / 2;
                g2.setColor(Color.WHITE);
                g2.fillOval(phx - 5, phy - 5, 10, 10);
                g2.setColor(new Color(180, 180, 200));
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(phx - 5, phy - 5, 10, 10);
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                g2.setColor(Color.WHITE);
                g2.drawString("← Draw Image Line to Here", phx + 8, phy + 4);
            } else {
                for (int i = 0; i < node.inputs.size(); i++) {
                    NodePort port = node.inputs.get(i);
                    Point ap = node.leftInputAnchor(port.name);
                    g2.setColor(new Color(100, 180, 255));
                    g2.fillOval(ap.x - 5, ap.y - 5, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1));
                    g2.drawOval(ap.x - 5, ap.y - 5, 10, 10);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                    g2.setColor(new Color(200, 220, 255));
                    g2.drawString("Input " + (i + 1), ap.x + 8, ap.y + 4);
                }
            }
            // ── Right: numbered output ports (skip Done/None Found) ──
            int rOutIdx = 0;
            for (int i = 0; i < node.outputs.size(); i++) {
                NodePort port = node.outputs.get(i);
                if (port.name.equals("None Found") || port.name.equals("Done"))
                    continue;
                int spacing2 = node.inputs.isEmpty() ? h / 2 : h / (node.inputs.size() + 1);
                int py = y + spacing2 * (rOutIdx + 1);
                g2.setColor(new Color(60, 200, 80));
                g2.fillOval(x + w - 5, py - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(x + w - 5, py - 5, 10, 10);
                String outLbl = "Output " + (rOutIdx + 1);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(new Color(180, 255, 180));
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(outLbl, x + w - 8 - fm2.stringWidth(outLbl), py + 4);
                rOutIdx++;
            }
            // ── Bottom: Done (left) + None Found (right) ───────
            int by2 = y + h, donePx = x + w / 3, nonePx = x + w * 2 / 3;
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(new Color(60, 200, 80));
            g2.drawString("\u2713", donePx - 3, by2 - 10);
            g2.fillOval(donePx - 5, by2 - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(donePx - 5, by2 - 5, 10, 10);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(new Color(220, 60, 60));
            g2.drawString("\u2717", nonePx - 3, by2 - 10);
            g2.fillOval(nonePx - 5, by2 - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(nonePx - 5, by2 - 5, 10, 10);
        } else {
            // Standard bottom output ports
            int numPorts = node.outputs.size();
            if (numPorts > 0) {
                int spacing = w / (numPorts + 1);
                for (int i = 0; i < numPorts; i++) {
                    NodePort port = node.outputs.get(i);
                    int px = x + spacing * (i + 1);
                    int dotY = y + h;
                    Color pc = portColor(port.name);
                    String icon = portIcon(port.name);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                    g2.setColor(pc);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(icon, px - fm.stringWidth(icon) / 2, dotY - 10);
                    g2.setColor(pc);
                    g2.fillOval(px - 5, dotY - 5, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1));
                    g2.drawOval(px - 5, dotY - 5, 10, 10);
                }
            }
            // Standard top-center input dot (not for IMAGE or WATCH_CASE nodes)
            if (node.type != BaseNode.NodeType.IMAGE && node.type != BaseNode.NodeType.WATCH_CASE) {
                Point inp = node.inputAnchor();
                g2.setColor(new Color(160, 200, 255));
                g2.fillOval(inp.x - 5, inp.y - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(inp.x - 5, inp.y - 5, 10, 10);
            }
        }
    }

    // ── Loop count popup ────────────────────────────────────────
    /** Shows a compact popup near the badge to edit nodeLoopCount. */
    private void showLoopCountPopup(BaseNode node, Point screenPt) {
        JDialog dlg = new JDialog((java.awt.Frame) null, false);
        dlg.setUndecorated(true);
        dlg.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);

        JPanel panel = new JPanel(new BorderLayout(6, 5));
        panel.setBackground(new Color(24, 24, 36));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 160, 35), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        // Title row
        JLabel titleLbl = new JLabel("\u21ba  Node loop count");
        titleLbl.setForeground(new Color(220, 160, 35));
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));

        // Spinner
        JSpinner sp = new JSpinner(new SpinnerNumberModel(Math.max(1, node.nodeLoopCount), 1, 999, 1));
        sp.setPreferredSize(new Dimension(72, 26));
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) sp.getEditor();
        ed.getTextField().setBackground(new Color(35, 35, 50));
        ed.getTextField().setForeground(new Color(220, 220, 230));
        ed.getTextField().setCaretColor(new Color(220, 220, 230));
        ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(new Color(60, 60, 85)));
        ed.getTextField().setFont(new Font("SansSerif", Font.BOLD, 13));

        // Hint
        JLabel hint = new JLabel(
                "<html><i>1 = no loop &nbsp;·&nbsp; any success = done &nbsp;·&nbsp; all fail = Not Found</i></html>");
        hint.setForeground(new Color(100, 160, 100));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 9));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topRow.setBackground(new Color(24, 24, 36));
        topRow.add(titleLbl);
        topRow.add(sp);

        panel.add(topRow, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);

        // Live-update the node as spinner changes
        sp.addChangeListener(ev -> {
            node.nodeLoopCount = ((Number) sp.getValue()).intValue();
            repaint();
        });

        dlg.add(panel);
        dlg.pack();

        // Position the popup above the click point
        Point loc;
        try {
            loc = getLocationOnScreen();
        } catch (Exception ex) {
            loc = new Point(0, 0);
        }
        int px = loc.x + screenPt.x - dlg.getWidth() / 2;
        int py = loc.y + screenPt.y - dlg.getHeight() - 6;
        dlg.setLocation(px, py);

        // Close when focus is lost
        dlg.addWindowFocusListener(new WindowFocusListener() {
            public void windowGainedFocus(WindowEvent e) {
                sp.requestFocusInWindow();
            }

            public void windowLostFocus(WindowEvent e) {
                dlg.dispose();
            }
        });

        dlg.setVisible(true);
    }

    private void drawArrows(Graphics2D g2) {
        for (Arrow a : arrows) {
            BaseNode from = nodes.get(a.fromNodeId), to = nodes.get(a.toNodeId);
            if (from == null || to == null)
                continue;
            Color ac = a.selected ? new Color(255, 220, 60) : portColor(a.fromPort);
            // Image → WatchCase: straight horizontal line, right→left
            if (from.type == BaseNode.NodeType.IMAGE && to.type == BaseNode.NodeType.WATCH_CASE) {
                // src = right-center of Image node
                Point src = new Point(from.x + from.width, from.y + from.height / 2);
                Point dst = to.leftInputAnchor(a.label);
                ac = a.selected ? new Color(255, 220, 60) : new Color(100, 180, 255);
                drawStraightArrow(g2, src, dst, ac, a.label);
            } else if (from.type == BaseNode.NodeType.WATCH_CASE) {
                // Watch Case: right-side ports use rightOutputAnchor, bottom ports use normal
                Point src;
                if (a.fromPort.equals("Done") || a.fromPort.equals("None Found")) {
                    src = from.outputAnchor(a.fromPort);
                } else {
                    src = from.rightOutputAnchor(a.fromPort);
                }
                Point dst = to.inputAnchor();
                drawOrthogonalArrow(g2, a, src, dst, ac);
            } else {
                Point src = from.outputAnchor(a.fromPort);
                Point dst = to.inputAnchor();
                drawOrthogonalArrow(g2, a, src, dst, ac);
            }
        }
    }

    /** Draw a straight line arrow (for Image→WatchCase connections) */
    private void drawStraightArrow(Graphics2D g2, Point src, Point dst, Color color, String label) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(src.x, src.y, dst.x, dst.y);
        // Arrowhead pointing left (into WatchCase)
        drawArrowHeadDir(g2, src, dst, color);
        // Label at midpoint
        if (label != null && !label.isEmpty()) {
            int mx = (src.x + dst.x) / 2, my = (src.y + dst.y) / 2;
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            g2.setColor(new Color(20, 20, 32, 180));
            g2.fillRoundRect(mx - lw / 2 - 3, my - 12, lw + 6, 14, 5, 5);
            g2.setColor(new Color(180, 220, 255));
            g2.drawString(label, mx - lw / 2, my);
        }
    }

    /** Arrowhead pointing in direction from src→dst */
    private void drawArrowHeadDir(Graphics2D g2, Point src, Point dst, Color color) {
        double angle = Math.atan2(dst.y - src.y, dst.x - src.x);
        int sz = 7;
        int ax = (int) (dst.x - sz * Math.cos(angle - 0.4));
        int ay = (int) (dst.y - sz * Math.sin(angle - 0.4));
        int bx = (int) (dst.x - sz * Math.cos(angle + 0.4));
        int by = (int) (dst.y - sz * Math.sin(angle + 0.4));
        g2.setColor(color);
        g2.fillPolygon(new int[] { dst.x, ax, bx }, new int[] { dst.y, ay, by }, 3);
    }

    private void drawLiveArrow(Graphics2D g2) {
        Point src = connectFrom.type == BaseNode.NodeType.IMAGE
                ? new Point(connectFrom.x + connectFrom.width, connectFrom.y + connectFrom.height / 2)
                : connectFrom.outputAnchor(connectPort);
        int mx = (int) ((connectMouse.x - panX) / zoom), my = (int) ((connectMouse.y - panY) / zoom);
        g2.setColor(ARROW_HOVER);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(src.x, src.y, mx, my);
        drawArrowHead(g2, new Point(mx, my), ARROW_HOVER);
    }

    private void drawOrthogonalArrow(Graphics2D g2, Arrow a, Point src, Point dst, Color color) {
        int bendY = src.y + a.bendOffset;
        int[] xs = { src.x, src.x, dst.x, dst.x };
        int[] ys = { src.y, bendY, bendY, dst.y };
        g2.setColor(color);
        g2.setStroke(new BasicStroke(a.selected ? 2.5f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++)
            g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        drawArrowHead(g2, dst, color);
        if (a.selected) {
            int hx = (src.x + dst.x) / 2;
            g2.setColor(new Color(255, 220, 60));
            g2.fillOval(hx - 5, bendY - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(hx - 5, bendY - 5, 10, 10);
        }
        if (a.label != null && !a.label.isEmpty()) {
            int lx = (src.x + dst.x) / 2, ly = bendY - 8;
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(a.label);
            g2.setColor(new Color(30, 30, 40, 200));
            g2.fillRoundRect(lx - lw / 2 - 3, ly - 12, lw + 6, 16, 6, 6);
            g2.setColor(new Color(220, 220, 100));
            g2.drawString(a.label, lx - lw / 2, ly);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point tip, Color color) {
        g2.setColor(color);
        int sz = 8;
        g2.fillPolygon(new int[] { tip.x, tip.x - sz / 2, tip.x + sz / 2 },
                new int[] { tip.y, tip.y - sz, tip.y - sz }, 3);
    }

    private boolean arrowHitTest(Arrow a, Point p) {
        BaseNode from = nodes.get(a.fromNodeId), to = nodes.get(a.toNodeId);
        if (from == null || to == null)
            return false;
        Point src = from.outputAnchor(a.fromPort), dst = to.inputAnchor();
        int bendY = src.y + a.bendOffset, tol = 6;
        if (nearSegment(p, src.x, src.y, src.x, bendY, tol))
            return true;
        if (nearSegment(p, src.x, bendY, dst.x, bendY, tol))
            return true;
        if (nearSegment(p, dst.x, bendY, dst.x, dst.y, tol))
            return true;
        return false;
    }

    private boolean nearSegment(Point p, int x1, int y1, int x2, int y2, int tol) {
        int minX = Math.min(x1, x2) - tol, maxX = Math.max(x1, x2) + tol;
        int minY = Math.min(y1, y2) - tol, maxY = Math.max(y1, y2) + tol;
        if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY)
            return false;
        double dx = x2 - x1, dy = y2 - y1, len2 = dx * dx + dy * dy;
        if (len2 == 0)
            return p.distance(x1, y1) < tol;
        double t = Math.max(0, Math.min(1, ((p.x - x1) * dx + (p.y - y1) * dy) / len2));
        return p.distance(x1 + t * dx, y1 + t * dy) < tol;
    }

    private void handleMousePressed(MouseEvent e) {
        Point cv = screenToCanvas(e.getPoint());
        requestFocusInWindow();
        if (SwingUtilities.isMiddleMouseButton(e) || (e.isAltDown() && SwingUtilities.isLeftMouseButton(e))) {
            panning = true;
            panDragStartX = e.getX() - panX;
            panDragStartY = e.getY() - panY;
            return;
        }
        for (BaseNode node : nodes.values()) {
            // WATCH_CASE: right-side case ports + bottom Done/None Found
            if (node.type == BaseNode.NodeType.WATCH_CASE) {
                int bhy = node.y + node.height;
                // Done — hardcoded left-third bottom
                Point doneAnchor = new Point(node.x + node.width / 3, bhy);
                if (cv.distance(doneAnchor) < 12) {
                    connectFrom = node;
                    connectPort = "Done";
                    connectMouse = e.getPoint();
                    return;
                }
                // None Found — hardcoded right-third bottom
                Point noneAnchor = new Point(node.x + node.width * 2 / 3, bhy);
                if (cv.distance(noneAnchor) < 12) {
                    connectFrom = node;
                    connectPort = "None Found";
                    connectMouse = e.getPoint();
                    return;
                }
                // Right-side case output ports
                for (NodePort port : node.outputs) {
                    if (port.name.equals("Done") || port.name.equals("None Found"))
                        continue;
                    Point anchor = node.rightOutputAnchor(port.name);
                    if (cv.distance(anchor) < 12) {
                        connectFrom = node;
                        connectPort = port.name;
                        connectMouse = e.getPoint();
                        return;
                    }
                }
                continue;
            }
            // IMAGE node: output is right-center, not bottom
            if (node.type == BaseNode.NodeType.IMAGE) {
                Point anchor = new Point(node.x + node.width, node.y + node.height / 2);
                if (cv.distance(anchor) < 12) {
                    connectFrom = node;
                    connectPort = "Image";
                    connectMouse = e.getPoint();
                    return;
                }
                continue;
            }
            for (NodePort port : node.outputs) {
                Point anchor = node.outputAnchor(port.name);
                if (cv.distance(anchor) < 12) {
                    connectFrom = node;
                    connectPort = port.name;
                    connectMouse = e.getPoint();
                    return;
                }
            }
        }
        if (selectedArrow != null) {
            BaseNode from = nodes.get(selectedArrow.fromNodeId);
            BaseNode to = nodes.get(selectedArrow.toNodeId);
            if (from != null && to != null) {
                Point src = from.outputAnchor(selectedArrow.fromPort);
                int bendY = (int) ((src.y + selectedArrow.bendOffset) * zoom) + panY;
                int hx = (int) (((src.x + to.inputAnchor().x) / 2.0) * zoom) + panX;
                if (e.getPoint().distance(new Point(hx, bendY)) < 12) {
                    bendDragArrow = selectedArrow;
                    bendDragStartY = e.getY();
                    bendDragOrigOffset = selectedArrow.bendOffset;
                    return;
                }
            }
        }
        BaseNode hit = nodeAt(cv);
        if (hit != null) {
            if (selectedArrow != null) {
                selectedArrow.selected = false;
                selectedArrow = null;
            }
            rubberRect = null;
            dragNode = hit;
            dragOffX = cv.x - hit.x;
            dragOffY = cv.y - hit.y;
            // If hit node is not in selection, clear and select just it
            if (!selectedNodes.contains(hit)) {
                selectedNodes.clear();
                selectNode(hit);
            } else {
                // Keep existing selection, just update single node selection
                selectedNode = hit;
                if (onNodeSelected != null)
                    onNodeSelected.accept(hit);
            }
        } else {
            Arrow hitArrow = null;
            for (Arrow a : arrows) {
                if (arrowHitTest(a, cv)) {
                    hitArrow = a;
                    break;
                }
            }
            if (hitArrow != null) {
                if (selectedArrow != null)
                    selectedArrow.selected = false;
                selectedArrow = hitArrow;
                hitArrow.selected = true;
                if (selectedNode != null) {
                    selectedNode = null;
                    if (onNodeSelected != null)
                        onNodeSelected.accept(null);
                }
            } else {
                if (selectedArrow != null) {
                    selectedArrow.selected = false;
                    selectedArrow = null;
                }
                selectNode(null);
            }
            if (SwingUtilities.isLeftMouseButton(e)) {
                rubberStart = e.getPoint();
                rubberRect = null;
                selectedNodes.clear();
            }
            repaint();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        panning = false;
        rubberStart = null;
        if (bendDragArrow != null) {
            bendDragArrow = null;
            notifyChanged();
            return;
        }
        if (connectFrom != null) {
            Point cv = screenToCanvas(e.getPoint());
            BaseNode target = nodeAt(cv);
            if (target != null && target != connectFrom) {
                // Special: Image node → Watch Case creates a new input case
                if (connectFrom.type == BaseNode.NodeType.IMAGE &&
                        target.type == BaseNode.NodeType.WATCH_CASE) {
                    nodes.WatchCaseNode wc = (nodes.WatchCaseNode) target;
                    String caseName = connectFrom.label;
                    // Avoid duplicate
                    // Prevent same Image node connecting twice
                    boolean alreadyConnected = wc.inputPortToNodeId.containsValue(connectFrom.id);
                    if (!alreadyConnected) {
                        // Use image label, but make unique if name collision
                        String cn = connectFrom.label;
                        int suffix = 2;
                        while (wc.inputPortToNodeId.containsKey(cn))
                            cn = connectFrom.label + " " + suffix++;
                        wc.addCase(cn);
                        wc.inputPortToNodeId.put(cn, connectFrom.id);
                        arrows.add(new Arrow(connectFrom.id, "Image", target.id, cn));
                    }
                } else {
                    connectFrom.setPortTarget(connectPort, target.id);
                    arrows.removeIf(a -> a.fromNodeId.equals(connectFrom.id) && a.fromPort.equals(connectPort));
                    NodePort port = null;
                    for (NodePort p : connectFrom.outputs)
                        if (p.name.equals(connectPort)) {
                            port = p;
                            break;
                        }
                    arrows.add(new Arrow(connectFrom.id, connectPort, target.id,
                            port != null ? port.displayLabel() : connectPort));
                }
                notifyChanged();
            }
            connectFrom = null;
            connectPort = null;
            connectMouse = null;
            repaint();
            return;
        }
        dragNode = null;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (panning) {
            panX = e.getX() - panDragStartX;
            panY = e.getY() - panDragStartY;
            repaint();
            return;
        }
        if (bendDragArrow != null) {
            bendDragArrow.bendOffset = Math.max(10, bendDragOrigOffset + (int) ((e.getY() - bendDragStartY) / zoom));
            repaint();
            return;
        }
        if (connectFrom != null) {
            connectMouse = e.getPoint();
            repaint();
            return;
        }
        if (rubberStart != null && dragNode == null && connectFrom == null && !panning) {
            int rx = Math.min(rubberStart.x, e.getX());
            int ry = Math.min(rubberStart.y, e.getY());
            int rw = Math.abs(e.getX() - rubberStart.x);
            int rh = Math.abs(e.getY() - rubberStart.y);
            rubberRect = new Rectangle(rx, ry, rw, rh);
            selectedNodes.clear();
            for (BaseNode node : nodes.values()) {
                Rectangle nb = new Rectangle(
                        (int) (node.x * zoom + panX), (int) (node.y * zoom + panY),
                        (int) (node.width * zoom), (int) (node.height * zoom));
                if (rubberRect.intersects(nb))
                    selectedNodes.add(node);
            }
            repaint();
            return;
        }
        if (dragNode != null) {
            Point cv = screenToCanvas(e.getPoint());
            int newX = Math.max(0, cv.x - dragOffX);
            int newY = Math.max(0, cv.y - dragOffY);
            int dx = newX - dragNode.x;
            int dy = newY - dragNode.y;
            if (selectedNodes.size() > 1 && selectedNodes.contains(dragNode)) {
                for (BaseNode n : selectedNodes) {
                    n.x += dx;
                    n.y += dy;
                }
            } else {
                dragNode.x = newX;
                dragNode.y = newY;
            }
            repaint();
            notifyChanged();
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        Point cv = screenToCanvas(e.getPoint());
        if (e.getButton() == MouseEvent.BUTTON3) {
            for (BaseNode node : nodes.values()) {
                if (loopBadgeHit(node, cv)) {
                    node.nodeLoopCount = 1;
                    notifyChanged();
                    repaint();
                    return;
                }
            }
            showContextMenu(e, cv);
            return;
        }

        // ── Loop badge click (left, single click) ──────────────
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
            List<BaseNode> nodeList = new ArrayList<>(nodes.values());
            for (int i = nodeList.size() - 1; i >= 0; i--) {
                BaseNode node = nodeList.get(i);
                if (loopBadgeHit(node, cv)) {
                    node.nodeLoopCount = Math.max(2, node.nodeLoopCount + 1);
                    notifyChanged();
                    repaint();
                    return;
                }
            }
        }

        if (e.getClickCount() == 2) {
            BaseNode hit = nodeAt(cv);
            if (hit != null && onNodeDoubleClick != null)
                onNodeDoubleClick.accept(hit);
        }
    }

    private void showContextMenu(MouseEvent e, Point canvas) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(30, 30, 42));
        menu.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 70), 1));
        BaseNode hit = nodeAt(canvas);
        if (hit != null) {
            addMenuHeader(menu, "Node: " + hit.label);
            addMenuItem(menu, "Edit settings", () -> {
                if (onNodeDoubleClick != null)
                    onNodeDoubleClick.accept(hit);
            });
            addMenuItem(menu, hit.branchEnabled ? "Disable node" : "Enable node", () -> {
                hit.branchEnabled = !hit.branchEnabled;
                repaint();
                notifyChanged();
            });
            addMenuSep(menu);
            String startTxt = hit.id.equals(startNodeId) ? "\u2605 Start node (click to unset)"
                    : "\u25b6 Set as start node";
            addMenuItem(menu, startTxt, () -> {
                startNodeId = hit.id.equals(startNodeId) ? null : hit.id;
                repaint();
                notifyChanged();
            });
            addMenuSep(menu);
            addMenuSep(menu);
            addMenuItem(menu, "💾 Save this node", () -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Save Node");
                fc.setFileFilter(
                        new javax.swing.filechooser.FileNameExtensionFilter("TurboClick Task (*.json)", "json"));
                fc.setSelectedFile(new java.io.File(hit.label + ".json"));
                if (fc.showSaveDialog(NodeCanvas.this) != JFileChooser.APPROVE_OPTION)
                    return;
                java.io.File file = fc.getSelectedFile();
                if (!file.getName().endsWith(".json"))
                    file = new java.io.File(file.getAbsolutePath() + ".json");
                try {
                    TaskSerializer.saveNodes(hit.label, java.util.Collections.singletonList(hit), arrows, nodes, file);
                    JOptionPane.showMessageDialog(NodeCanvas.this, "Saved!", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(NodeCanvas.this, "Save failed:\n" + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
            addMenuItem(menu, "Delete node", () -> removeNode(hit));
        } else {
            if (!selectedNodes.isEmpty()) {
                addMenuHeader(menu, "Selection — " + selectedNodes.size() + " nodes");
                addMenuItem(menu, "💾 Save selection", () -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setDialogTitle("Save Selection");
                    fc.setFileFilter(
                            new javax.swing.filechooser.FileNameExtensionFilter("TurboClick Task (*.json)", "json"));
                    fc.setSelectedFile(new java.io.File("selection.json"));
                    if (fc.showSaveDialog(NodeCanvas.this) != JFileChooser.APPROVE_OPTION)
                        return;
                    java.io.File file = fc.getSelectedFile();
                    if (!file.getName().endsWith(".json"))
                        file = new java.io.File(file.getAbsolutePath() + ".json");
                    try {
                        TaskSerializer.saveNodes("Selection", new ArrayList<>(selectedNodes), arrows, nodes, file);
                        JOptionPane.showMessageDialog(NodeCanvas.this, "Saved " + selectedNodes.size() + " nodes!",
                                "Saved", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(NodeCanvas.this, "Save failed:\n" + ex.getMessage(), "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                });
                addMenuItem(menu, "⧉ Duplicate selection", () -> {
                    Map<String, String> idRemap = new HashMap<>();
                    List<BaseNode> newNodes = new ArrayList<>();
                    for (BaseNode n : new ArrayList<>(selectedNodes)) {
                        BaseNode copy = NodeFactory.create(n.type, n.x + 40, n.y + 40);
                        copy.label = n.label;
                        copy.width = n.width;
                        copy.height = n.height;
                        copy.branchEnabled = n.branchEnabled;
                        copy.entryDelayMs = n.entryDelayMs;
                        copy.nodeLoopCount = n.nodeLoopCount;
                        idRemap.put(n.id, copy.id);
                        newNodes.add(copy);
                        nodes.put(copy.id, copy);
                    }
                    // Re-wire internal arrows
                    for (Arrow a : new ArrayList<>(arrows)) {
                        String newFrom = idRemap.get(a.fromNodeId);
                        String newTo = idRemap.get(a.toNodeId);
                        if (newFrom != null && newTo != null) {
                            BaseNode fromNode = nodes.get(newFrom);
                            if (fromNode != null)
                                fromNode.setPortTarget(a.fromPort, newTo);
                            arrows.add(new Arrow(newFrom, a.fromPort, newTo, a.label));
                        }
                    }
                    selectedNodes.clear();
                    selectedNodes.addAll(newNodes);
                    repaint();
                    notifyChanged();
                });
                addMenuItem(menu, "🗑 Delete selection", () -> {
                    int r = JOptionPane.showConfirmDialog(NodeCanvas.this,
                            "Delete " + selectedNodes.size() + " nodes?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (r == JOptionPane.YES_OPTION) {
                        for (BaseNode n : new ArrayList<>(selectedNodes))
                            removeNode(n);
                        selectedNodes.clear();
                    }
                });
                addMenuSep(menu);
            }
            addMenuHeader(menu, "Add Node");
            for (BaseNode.NodeType type : BaseNode.NodeType.values()) {
                if (type == BaseNode.NodeType.CLICK || type == BaseNode.NodeType.CONDITION
                        || type == BaseNode.NodeType.LOOP)
                    continue;
                final BaseNode.NodeType t = type;
                addMenuItemColored(menu, NodeFactory.displayName(t), NodeFactory.color(t), () -> {
                    BaseNode n = NodeFactory.create(t, canvas.x, canvas.y);
                    addNode(n);
                    selectNode(n);
                    if (onNodeDoubleClick != null)
                        onNodeDoubleClick.accept(n);
                });
            }
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addMenuHeader(JPopupMenu m, String text) {
        JLabel hdr = new JLabel("  " + text);
        hdr.setForeground(new Color(140, 140, 170));
        hdr.setFont(new Font("SansSerif", Font.BOLD, 10));
        hdr.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        m.add(hdr);
        addMenuSep(m);
    }

    private void addMenuItem(JPopupMenu m, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(new Color(30, 30, 42));
        item.setForeground(new Color(210, 210, 220));
        item.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        item.setOpaque(true);
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
            protected void paintBackground(Graphics g, JMenuItem mi, Color bgColor) {
                g.setColor(mi.isArmed() || mi.isSelected() ? new Color(50, 50, 68) : new Color(30, 30, 42));
                g.fillRect(0, 0, mi.getWidth(), mi.getHeight());
            }
        });
        item.addActionListener(e -> action.run());
        m.add(item);
    }

    private void addMenuItemColored(JPopupMenu m, String text, Color accent, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(new Color(30, 30, 42));
        item.setForeground(new Color(210, 210, 220));
        item.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        item.setOpaque(true);
        item.setIcon(new javax.swing.Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(accent);
                g.fillRoundRect(x, y + 1, 4, getIconHeight() - 2, 3, 3);
            }

            public int getIconWidth() {
                return 8;
            }

            public int getIconHeight() {
                return 14;
            }
        });
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
            protected void paintBackground(Graphics g, JMenuItem mi, Color bgColor) {
                g.setColor(mi.isArmed() || mi.isSelected() ? new Color(50, 50, 68) : new Color(30, 30, 42));
                g.fillRect(0, 0, mi.getWidth(), mi.getHeight());
            }
        });
        item.addActionListener(e -> action.run());
        m.add(item);
    }

    private void addMenuSep(JPopupMenu m) {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(55, 55, 68));
        s.setBackground(new Color(30, 30, 42));
        m.add(s);
    }

    private BaseNode nodeAt(Point cv) {
        List<BaseNode> list = new ArrayList<>(nodes.values());
        for (int i = list.size() - 1; i >= 0; i--)
            if (list.get(i).bounds().contains(cv))
                return list.get(i);
        return null;
    }

    private Point screenToCanvas(Point p) {
        return new Point((int) ((p.x - panX) / zoom), (int) ((p.y - panY) / zoom));
    }

    private void selectNode(BaseNode node) {
        selectedNode = node;
        if (onNodeSelected != null)
            onNodeSelected.accept(node);
        repaint();
    }

    private void notifyChanged() {
        if (onCanvasChanged != null)
            onCanvasChanged.run();
    }
}