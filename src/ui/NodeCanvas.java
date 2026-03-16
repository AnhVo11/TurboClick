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
    private final Map<String, BaseNode> nodes  = new LinkedHashMap<>();
    private final List<Arrow>           arrows = new ArrayList<>();

    // ── Interaction ───────────────────────────────────────────
    private BaseNode dragNode      = null;
    private int      dragOffX      = 0, dragOffY = 0;
    private BaseNode connectFrom   = null;
    private String   connectPort   = null;
    private Point    connectMouse  = null;
    private BaseNode selectedNode  = null;

    // ── Ghost drag (from palette) ─────────────────────────────
    private BaseNode.NodeType ghostType   = null;  // type being dragged in
    private Point             ghostPoint  = null;  // current mouse pos (screen)

    // ── Pan / Zoom ────────────────────────────────────────────
    double  zoom         = 1.0;
    int     panX         = 0, panY = 0;
    private int     panDragStartX, panDragStartY;
    private boolean panning = false;

    // ── Callbacks ─────────────────────────────────────────────
    private Consumer<BaseNode> onNodeSelected;
    private Consumer<BaseNode> onNodeDoubleClick;
    private Runnable           onCanvasChanged;

    // ── Colors ────────────────────────────────────────────────
    private static final Color BG_COLOR     = new Color(22,22,28);
    private static final Color GRID_COLOR   = new Color(38,38,48);
    private static final Color ARROW_COLOR  = new Color(120,120,140);
    private static final Color ARROW_HOVER  = new Color(80,200,255);
    private static final Color SELECT_GLOW  = new Color(80,180,255);
    private static final Color RUNNING_GLOW = new Color(40,220,100);
    private static final Color SUCCESS_GLOW = new Color(40,220,100);
    private static final Color FAILED_GLOW  = new Color(220,60,60);

    // ── Port appearance helpers ───────────────────────────────
    private static Color portColor(String portName) {
        String n = portName.toLowerCase();
        // Check negative cases FIRST before positive (e.g. "Not Found" before "Found")
        if (n.contains("not found") || n.contains("not found")) return new Color(220,60,60);
        if (n.equals("stopped") || n.contains("fail"))           return new Color(220,60,60);
        if (n.contains("timeout"))                               return new Color(220,180,40);
        if (n.contains("found") || n.contains("done") || n.equals("loop")) return new Color(60,200,80);
        if (n.equals("stop"))                                    return new Color(220,60,60);
        return new Color(100,140,220);
    }

    private static String portIcon(String portName) {
        String n = portName.toLowerCase();
        if (n.contains("not found"))                             return "✗";
        if (n.equals("stopped") || n.contains("fail"))           return "✗";
        if (n.equals("stop"))                                    return "✗";
        if (n.contains("timeout"))                               return "⊙";
        if (n.contains("found") || n.contains("done") || n.equals("loop")) return "✓";
        return "→";
    }

    // ── Arrow class ───────────────────────────────────────────
    public static class Arrow {
        public String fromNodeId, fromPort, toNodeId, label;
        Arrow(String from, String port, String to, String label) {
            fromNodeId=from; fromPort=port; toNodeId=to; this.label=label;
        }
    }

    public NodeCanvas() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(800,600));

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { handleMousePressed(e);  }
            public void mouseReleased(MouseEvent e) { handleMouseReleased(e); }
            public void mouseDragged(MouseEvent e)  { handleMouseDragged(e);  }
            public void mouseMoved(MouseEvent e)    { repaint(); }
            public void mouseClicked(MouseEvent e)  { handleMouseClicked(e);  }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        // Mouse wheel zoom disabled — use toolbar +/- buttons instead
        // addMouseWheelListener removed intentionally
    }

    // ── Public API ────────────────────────────────────────────
    public void setOnNodeSelected(Consumer<BaseNode> cb)    { onNodeSelected    = cb; }
    public void setOnNodeDoubleClick(Consumer<BaseNode> cb) { onNodeDoubleClick = cb; }
    public void setOnCanvasChanged(Runnable cb)             { onCanvasChanged   = cb; }

    /** Called by NodePalette when user starts dragging a node type */
    public void startGhostDrag(BaseNode.NodeType type) {
        ghostType  = type;
        ghostPoint = MouseInfo.getPointerInfo().getLocation();
        repaint();
    }

    /** Called by NodePalette on mouse move during drag */
    public void updateGhostDrag(Point screenPoint) {
        ghostPoint = screenPoint;
        repaint();
    }

    /** Called by NodePalette on mouse release — drop the node at exact screen position */
    public BaseNode finishGhostDrag(Point screenPoint) {
        if (ghostType == null) return null;
        // Convert screen coords to canvas coords
        Point compPt;
        try {
            Point loc = getLocationOnScreen();
            int sx = screenPoint.x - loc.x;
            int sy = screenPoint.y - loc.y;
            compPt = screenToCanvas(new Point(sx, sy));
        } catch (Exception e) {
            compPt = new Point(100 + (int)(Math.random()*300), 80 + (int)(Math.random()*200));
        }
        BaseNode node = NodeFactory.create(ghostType, Math.max(10, compPt.x - 90), Math.max(10, compPt.y - 37));
        ghostType  = null;
        ghostPoint = null;
        addNode(node);
        selectNode(node);
        repaint();
        return node;
    }

    public void cancelGhostDrag() { ghostType=null; ghostPoint=null; repaint(); }

    public void addNode(BaseNode node) {
        node.width  = 180;
        node.height = 75;
        nodes.put(node.id, node);
        for (NodePort port : node.outputs)
            if (port.targetNodeId!=null)
                arrows.add(new Arrow(node.id,port.name,port.targetNodeId,port.displayLabel()));
        repaint(); notifyChanged();
    }

    public void removeNode(BaseNode node) {
        nodes.remove(node.id);
        arrows.removeIf(a -> a.fromNodeId.equals(node.id)||a.toNodeId.equals(node.id));
        if (selectedNode==node) { selectedNode=null; if(onNodeSelected!=null) onNodeSelected.accept(null); }
        repaint(); notifyChanged();
    }

    public Map<String,BaseNode> getNodes() { return nodes; }
    public BaseNode getSelectedNode()       { return selectedNode; }
    public void refreshNode(BaseNode node)  { repaint(); }

    public void zoomIn()  { zoom = Math.min(3.0, zoom * 1.2); repaint(); }
    public void zoomOut() { zoom = Math.max(0.3, zoom / 1.2); repaint(); }
    public void zoomReset(){ zoom = 1.0; panX=0; panY=0; repaint(); }

    public void fitToScreen() {
        if (nodes.isEmpty()) return;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=0,maxY=0;
        for (BaseNode n:nodes.values()) {
            minX=Math.min(minX,n.x); minY=Math.min(minY,n.y);
            maxX=Math.max(maxX,n.x+n.width); maxY=Math.max(maxY,n.y+n.height);
        }
        double zx=(double)(getWidth()-120)/(maxX-minX+1);
        double zy=(double)(getHeight()-120)/(maxY-minY+1);
        zoom=Math.max(0.3,Math.min(1.5,Math.min(zx,zy)));
        panX=(int)(60-minX*zoom); panY=(int)(60-minY*zoom);
        repaint();
    }

    // ── Painting ─────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g2);
        g2.translate(panX,panY);
        g2.scale(zoom,zoom);

        drawArrows(g2);
        if (connectFrom!=null&&connectMouse!=null) drawLiveArrow(g2);
        for (BaseNode node:nodes.values()) drawNode(g2,node);

        // Draw ghost
        if (ghostType!=null&&ghostPoint!=null) drawGhost(g2);

        g2.dispose();
    }

    private static String nodeTypeBadge(BaseNode.NodeType type) {
        switch(type) {
            case WATCH_ZONE:   return "◎";   // eye/watch symbol
            case CLICK:        return "↗";
            case SIMPLE_CLICK: return "⊕";   // click/add symbol
            case CONDITION:    return "?";
            case LOOP:         return "↺";
            case WAIT:         return "⏸";
            case STOP:         return "■";
            default:           return "•";
        }
    }

    // Use ImageLabel trick for emoji on macOS — fallback to unicode if needed
    private static void drawNodeIcon(Graphics2D g2, BaseNode.NodeType type, int x, int y) {
        // Try rendering via JLabel for proper emoji support
        String emoji = nodeTypeBadge(type);
        g2.setFont(new Font("Apple Color Emoji", Font.PLAIN, 13));
        if (g2.getFont().canDisplayUpTo(emoji) == -1) {
            g2.drawString(emoji, x, y);
        } else {
            // Fallback: use SansSerif symbol
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            String fb;
            switch(type) {
                case WATCH_ZONE:   fb="👁"; break;
                case CLICK:        fb="↗"; break;
                case SIMPLE_CLICK: fb="⚡"; break;
                case CONDITION:    fb="◇"; break;
                case LOOP:         fb="↺"; break;
                case WAIT:         fb="⏸"; break;
                case STOP:         fb="◼"; break;
                default:           fb="•"; break;
            }
            g2.drawString(fb, x, y);
        }
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID_COLOR);
        int gs=(int)(20*zoom); if(gs<6) return;
        int ox=panX%gs, oy=panY%gs;
        for(int x=ox;x<getWidth();x+=gs)  g2.drawLine(x,0,x,getHeight());
        for(int y=oy;y<getHeight();y+=gs) g2.drawLine(0,y,getWidth(),y);
    }

    private void drawGhost(Graphics2D g2) {
        // Convert screen ghost point to canvas coords
        Point screen = ghostPoint;
        int cx = (int)((screen.x - getLocationOnScreen().x - panX)/zoom) - 90;
        int cy = (int)((screen.y - getLocationOnScreen().y - panY)/zoom) - 37;
        Color nc = NodeFactory.color(ghostType);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2.setColor(nc.darker());
        g2.fillRoundRect(cx,cy,180,75,12,12);
        g2.setColor(nc.brighter());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(cx,cy,180,75,12,12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif",Font.BOLD,14));
        g2.setColor(NodeFactory.color(ghostType).brighter());
        // icon
        try { BaseNode tmp=NodeFactory.create(ghostType,0,0); g2.drawString(tmp.nodeIcon(), cx+8, cy+26); } catch(Exception ex){}
        g2.setFont(new Font("SansSerif",Font.BOLD,11));
        g2.setColor(Color.WHITE);
        g2.drawString(NodeFactory.displayName(ghostType), cx+30, cy+26);
        g2.setComposite(old);
    }

    private void drawNode(Graphics2D g2, BaseNode node) {
        int x=node.x, y=node.y, w=node.width, h=node.height;

        // Run-state glow
        RunState state=node.runState;
        if (state==RunState.RUNNING) {
            long pulse=(System.currentTimeMillis()/300)%2;
            g2.setColor(pulse==0?RUNNING_GLOW:new Color(20,180,80,180));
            g2.setStroke(new BasicStroke(4));
            g2.drawRoundRect(x-3,y-3,w+6,h+6,14,14); repaint();
        } else if (state==RunState.SUCCESS&&!node.isRunStateExpired(1500)) {
            g2.setColor(SUCCESS_GLOW); g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x-2,y-2,w+4,h+4,14,14);
        } else if (state==RunState.FAILED&&!node.isRunStateExpired(1500)) {
            g2.setColor(FAILED_GLOW); g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(x-2,y-2,w+4,h+4,14,14);
        }

        // Selection glow
        if (node==selectedNode) {
            g2.setColor(SELECT_GLOW); g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(x-3,y-3,w+6,h+6,14,14);
        }

        // Shadow
        g2.setColor(new Color(0,0,0,60));
        g2.fillRoundRect(x+3,y+3,w,h,12,12);

        // Body
        GradientPaint gp=new GradientPaint(x,y,node.nodeColor().brighter(),x,y+h,node.nodeColor().darker());
        g2.setPaint(gp); g2.setStroke(new BasicStroke(1));
        g2.fillRoundRect(x,y,w,h,12,12);

        // Border
        g2.setColor(node.nodeColor().brighter().brighter()); g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x,y,w,h,12,12);

        // Disabled overlay
        if (!node.branchEnabled) { g2.setColor(new Color(0,0,0,120)); g2.fillRoundRect(x,y,w,h,12,12); }

        // Icon badge — try emoji font first, fallback to colored circle
        Color ic = node.nodeColor().brighter();
        g2.setColor(ic);
        g2.fillOval(x+7, y+7, 22, 22);
        g2.setColor(Color.WHITE);
        // Try Apple Color Emoji font for macOS
        Font emojiFont = new Font("Dialog", Font.BOLD, 12);
        g2.setFont(emojiFont);
        String badge = nodeTypeBadge(node.type);
        FontMetrics bfm = g2.getFontMetrics();
        int bx = x+7+(22-bfm.stringWidth(badge))/2;
        g2.drawString(badge, bx, y+7+16);

        // Label
        g2.setFont(new Font("SansSerif",Font.BOLD,11)); g2.setColor(Color.WHITE);
        String lbl = node.label.length()>16 ? node.label.substring(0,14)+"…" : node.label;
        g2.drawString(lbl, x+34, y+20);

        // Type subtitle
        g2.setFont(new Font("SansSerif",Font.PLAIN,9)); g2.setColor(new Color(255,255,255,140));
        g2.drawString(node.type.name().replace("_"," "), x+34, y+32);

        // ── Output ports with icon + colored dot ─────────────
        int numPorts = node.outputs.size();
        if (numPorts>0) {
            int spacing = w / (numPorts+1);
            for (int i=0; i<numPorts; i++) {
                NodePort port = node.outputs.get(i);
                int px = x + spacing*(i+1);
                int dotY = y+h;
                Color pc = portColor(port.name);
                String icon = portIcon(port.name);

                // Port icon above dot
                g2.setFont(new Font("SansSerif",Font.BOLD,10));
                g2.setColor(pc);
                FontMetrics fm = g2.getFontMetrics();
                int iw = fm.stringWidth(icon);
                g2.drawString(icon, px-iw/2, dotY-10);

                // Colored dot
                g2.setColor(pc);
                g2.fillOval(px-5, dotY-5, 10, 10);
                g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1));
                g2.drawOval(px-5, dotY-5, 10, 10);
            }
        }

        // Input dot (top center) — white/blue
        Point inp = node.inputAnchor();
        g2.setColor(new Color(160,200,255));
        g2.fillOval(inp.x-5,inp.y-5,10,10);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1));
        g2.drawOval(inp.x-5,inp.y-5,10,10);
    }

    private void drawArrows(Graphics2D g2) {
        for (Arrow a:arrows) {
            BaseNode from=nodes.get(a.fromNodeId), to=nodes.get(a.toNodeId);
            if(from==null||to==null) continue;
            Point src=from.outputAnchor(a.fromPort), dst=to.inputAnchor();
            Color ac = portColor(a.fromPort);
            drawCurvedArrow(g2,src,dst,ac,a.label);
        }
    }

    private void drawLiveArrow(Graphics2D g2) {
        Point src=connectFrom.outputAnchor(connectPort);
        int mx=(int)((connectMouse.x-panX)/zoom), my=(int)((connectMouse.y-panY)/zoom);
        drawCurvedArrow(g2,src,new Point(mx,my),ARROW_HOVER,null);
    }

    private void drawCurvedArrow(Graphics2D g2, Point src, Point dst, Color color, String label) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        int dy=Math.max(60,Math.abs(dst.y-src.y)/2);
        CubicCurve2D curve=new CubicCurve2D.Float(src.x,src.y,src.x,src.y+dy,dst.x,dst.y-dy,dst.x,dst.y);
        g2.draw(curve);
        drawArrowHead(g2,dst,color);
        if (label!=null&&!label.isEmpty()) {
            float mx=(float)(0.125*src.x+0.375*src.x+0.375*dst.x+0.125*dst.x);
            float my=(float)(0.125*src.y+0.375*(src.y+dy)+0.375*(dst.y-dy)+0.125*dst.y);
            g2.setFont(new Font("SansSerif",Font.BOLD,10));
            FontMetrics fm=g2.getFontMetrics(); int lw=fm.stringWidth(label);
            g2.setColor(new Color(30,30,40,200));
            g2.fillRoundRect((int)mx-lw/2-3,(int)my-12,lw+6,16,6,6);
            g2.setColor(new Color(220,220,100));
            g2.drawString(label,mx-lw/2,my);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point tip, Color color) {
        g2.setColor(color);
        int sz=8;
        g2.fillPolygon(new int[]{tip.x,tip.x-sz/2,tip.x+sz/2},
                       new int[]{tip.y,tip.y-sz,tip.y-sz},3);
    }

    // ── Mouse handlers ────────────────────────────────────────
    private void handleMousePressed(MouseEvent e) {
        Point cv=screenToCanvas(e.getPoint());
        if (SwingUtilities.isMiddleMouseButton(e)||(e.isAltDown()&&SwingUtilities.isLeftMouseButton(e))) {
            panning=true; panDragStartX=e.getX()-panX; panDragStartY=e.getY()-panY; return;
        }
        for (BaseNode node:nodes.values()) {
            for (NodePort port:node.outputs) {
                Point anchor=node.outputAnchor(port.name);
                if (cv.distance(anchor)<12) {
                    connectFrom=node; connectPort=port.name; connectMouse=e.getPoint(); return;
                }
            }
        }
        BaseNode hit=nodeAt(cv);
        if (hit!=null) { dragNode=hit; dragOffX=cv.x-hit.x; dragOffY=cv.y-hit.y; selectNode(hit); }
        else selectNode(null);
    }

    private void handleMouseReleased(MouseEvent e) {
        panning=false;
        if (connectFrom!=null) {
            Point cv=screenToCanvas(e.getPoint());
            BaseNode target=nodeAt(cv);
            if (target!=null&&target!=connectFrom) {
                connectFrom.setPortTarget(connectPort,target.id);
                arrows.removeIf(a->a.fromNodeId.equals(connectFrom.id)&&a.fromPort.equals(connectPort));
                NodePort port=null;
                for (NodePort p:connectFrom.outputs) if(p.name.equals(connectPort)){port=p;break;}
                String lbl=port!=null?port.displayLabel():connectPort;
                arrows.add(new Arrow(connectFrom.id,connectPort,target.id,lbl));
                notifyChanged();
            }
            connectFrom=null; connectPort=null; connectMouse=null; repaint(); return;
        }
        dragNode=null;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (panning) { panX=e.getX()-panDragStartX; panY=e.getY()-panDragStartY; repaint(); return; }
        if (connectFrom!=null) { connectMouse=e.getPoint(); repaint(); return; }
        if (dragNode!=null) {
            Point cv=screenToCanvas(e.getPoint());
            dragNode.x=Math.max(0,cv.x-dragOffX); dragNode.y=Math.max(0,cv.y-dragOffY);
            repaint(); notifyChanged();
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        Point cv=screenToCanvas(e.getPoint());
        if (e.getButton()==MouseEvent.BUTTON3) { showContextMenu(e,cv); return; }
        if (e.getClickCount()==2) {
            BaseNode hit=nodeAt(cv);
            if (hit!=null&&onNodeDoubleClick!=null) onNodeDoubleClick.accept(hit);
        }
    }

    private void showContextMenu(MouseEvent e, Point canvas) {
        JPopupMenu menu=new JPopupMenu();
        menu.setBackground(new Color(30,30,42));
        menu.setBorder(BorderFactory.createLineBorder(new Color(55,55,70),1));
        BaseNode hit=nodeAt(canvas);
        if (hit!=null) {
            addMenuHeader(menu, "Node: "+hit.label);
            addMenuItem(menu,"Edit settings",()->{ if(onNodeDoubleClick!=null) onNodeDoubleClick.accept(hit); });
            addMenuItem(menu,hit.branchEnabled?"Disable node":"Enable node",()->{hit.branchEnabled=!hit.branchEnabled;repaint();notifyChanged();});
            addMenuSep(menu);
            addMenuItem(menu,"Delete node",()->removeNode(hit));
        } else {
            addMenuHeader(menu, "Add Node");
            for (BaseNode.NodeType type:BaseNode.NodeType.values()) {
                final BaseNode.NodeType t=type;
                addMenuItemColored(menu, NodeFactory.displayName(t), NodeFactory.color(t), ()->{
                    BaseNode n=NodeFactory.create(t,canvas.x,canvas.y);
                    addNode(n); selectNode(n);
                    if(onNodeDoubleClick!=null) onNodeDoubleClick.accept(n);
                });
            }
        }
        menu.show(this,e.getX(),e.getY());
    }

    private void addMenuHeader(JPopupMenu m, String text) {
        JLabel hdr=new JLabel("  "+text);
        hdr.setForeground(new Color(140,140,170));
        hdr.setFont(new Font("SansSerif",Font.BOLD,10));
        hdr.setBorder(BorderFactory.createEmptyBorder(6,4,4,4));
        m.add(hdr);
        addMenuSep(m);
    }

    private void addMenuItem(JPopupMenu m,String text,Runnable action) {
        JMenuItem item=new JMenuItem(text);
        item.setBackground(new Color(30,30,42));
        item.setForeground(new Color(210,210,220));
        item.setFont(new Font("SansSerif",Font.PLAIN,12));
        item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        item.addActionListener(e->action.run());
        m.add(item);
    }

    private void addMenuItemColored(JPopupMenu m,String text,Color accent,Runnable action) {
        JMenuItem item=new JMenuItem(text);
        item.setBackground(new Color(30,30,42));
        item.setForeground(new Color(210,210,220));
        item.setFont(new Font("SansSerif",Font.PLAIN,12));
        item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        // Color indicator strip on left
        item.setIcon(new javax.swing.Icon(){
            public void paintIcon(Component c,Graphics g,int x,int y){
                g.setColor(accent); g.fillRoundRect(x,y+1,4,getIconHeight()-2,3,3);
            }
            public int getIconWidth(){ return 8; }
            public int getIconHeight(){ return 14; }
        });
        item.addActionListener(e->action.run());
        m.add(item);
    }
    private void addMenuSep(JPopupMenu m) { JSeparator s=new JSeparator(); s.setForeground(new Color(60,60,70)); m.add(s); }

    private BaseNode nodeAt(Point cv) {
        List<BaseNode> list=new ArrayList<>(nodes.values());
        for (int i=list.size()-1;i>=0;i--) if(list.get(i).bounds().contains(cv)) return list.get(i);
        return null;
    }
    private Point screenToCanvas(Point p) { return new Point((int)((p.x-panX)/zoom),(int)((p.y-panY)/zoom)); }
    private void selectNode(BaseNode node) { selectedNode=node; if(onNodeSelected!=null) onNodeSelected.accept(node); repaint(); }
    private void notifyChanged() { if(onCanvasChanged!=null) onCanvasChanged.run(); }
}