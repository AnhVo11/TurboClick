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
    private Arrow      selectedArrow      = null;
    private Arrow      bendDragArrow      = null;
    private int        bendDragStartY     = 0;
    private int        bendDragOrigOffset = 0;
    private String     startNodeId        = null;

    // ── Ghost drag (from palette) ─────────────────────────────
    private BaseNode.NodeType ghostType   = null;
    private Point             ghostPoint  = null;

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

    private static Color portColor(String portName) {
        String n = portName.toLowerCase();
        if (n.contains("not found")) return new Color(220,60,60);
        if (n.equals("stopped") || n.contains("fail")) return new Color(220,60,60);
        if (n.contains("timeout"))                     return new Color(220,180,40);
        if (n.contains("found") || n.contains("done") || n.equals("loop")) return new Color(60,200,80);
        if (n.equals("stop"))                          return new Color(220,60,60);
        return new Color(100,140,220);
    }

    private static String portIcon(String portName) {
        String n = portName.toLowerCase();
        if (n.contains("not found"))                             return "\u2717";
        if (n.equals("stopped") || n.contains("fail"))           return "\u2717";
        if (n.equals("stop"))                                    return "\u2717";
        if (n.contains("timeout"))                               return "\u2299";
        if (n.contains("found") || n.contains("done") || n.equals("loop")) return "\u2713";
        return "\u2192";
    }

    public static class Arrow {
        public String fromNodeId, fromPort, toNodeId, label;
        public int    bendOffset = 40;
        public boolean selected  = false;
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
        setFocusable(true);
        addKeyListener(new KeyAdapter(){
            public void keyPressed(KeyEvent e){
                if ((e.getKeyCode()==KeyEvent.VK_DELETE||e.getKeyCode()==KeyEvent.VK_BACK_SPACE)
                        && selectedArrow!=null) {
                    BaseNode from = nodes.get(selectedArrow.fromNodeId);
                    if (from!=null) from.setPortTarget(selectedArrow.fromPort, null);
                    arrows.remove(selectedArrow);
                    selectedArrow=null;
                    repaint(); notifyChanged();
                }
            }
        });
        addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){ requestFocusInWindow(); }
        });
    }

    public void setOnNodeSelected(Consumer<BaseNode> cb)    { onNodeSelected    = cb; }
    public void setStartNode(String id) { startNodeId = id; repaint(); }
    public String getStartNodeId()      { return startNodeId; }
    public void setOnNodeDoubleClick(Consumer<BaseNode> cb) { onNodeDoubleClick = cb; }
    public void setOnCanvasChanged(Runnable cb)             { onCanvasChanged   = cb; }

    public void startGhostDrag(BaseNode.NodeType type) {
        ghostType  = type;
        ghostPoint = MouseInfo.getPointerInfo().getLocation();
        repaint();
    }

    public void updateGhostDrag(Point screenPoint) { ghostPoint = screenPoint; repaint(); }

    public BaseNode finishGhostDrag(Point screenPoint) {
        if (ghostType == null) return null;
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
        ghostType = null; ghostPoint = null;
        addNode(node); selectNode(node); repaint();
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

    public void zoomIn()   { zoom = Math.min(3.0, zoom * 1.2); repaint(); }
    public void zoomOut()  { zoom = Math.max(0.3, zoom / 1.2); repaint(); }
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
        if (ghostType!=null&&ghostPoint!=null) drawGhost(g2);
        g2.dispose();
    }

    private static String nodeTypeBadge(BaseNode.NodeType type) {
        switch(type) {
            case WATCH_ZONE:   return "\u25ce";
            case CLICK:        return "\u2197";
            case SIMPLE_CLICK: return "\u2295";
            case CONDITION:    return "?";
            case LOOP:         return "\u21ba";
            case WAIT:         return "\u23f1";
            case STOP:         return "\u25a0";
            case KEYBOARD:     return "\u2328";
            default:           return "\u2022";
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
        Point screen = ghostPoint;
        int cx,cy;
        try {
            Point loc = getLocationOnScreen();
            cx = (int)((screen.x - loc.x - panX)/zoom) - 90;
            cy = (int)((screen.y - loc.y - panY)/zoom) - 37;
        } catch(Exception e) { return; }
        Color nc = NodeFactory.color(ghostType);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2.setColor(nc.darker()); g2.fillRoundRect(cx,cy,180,75,12,12);
        g2.setColor(nc.brighter()); g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(cx,cy,180,75,12,12);
        // Badge circle
        g2.setColor(nc.brighter()); g2.fillOval(cx+7, cy+7, 22, 22);
        g2.setColor(Color.WHITE);
        String badge = nodeTypeBadge(ghostType);
        Font bf = new Font("Dialog", Font.BOLD, 13);
        g2.setFont(bf); FontMetrics bfm = g2.getFontMetrics();
        g2.drawString(badge, cx+7+(22-bfm.stringWidth(badge))/2, cy+7+(22-bfm.getHeight())/2+bfm.getAscent());
        g2.setFont(new Font("SansSerif",Font.BOLD,11)); g2.setColor(Color.WHITE);
        g2.drawString(NodeFactory.displayName(ghostType), cx+34, cy+22);
        g2.setComposite(old);
    }

    private void drawNode(Graphics2D g2, BaseNode node) {
        int x=node.x, y=node.y, w=node.width, h=node.height;

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
        if (node==selectedNode) {
            g2.setColor(SELECT_GLOW); g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(x-3,y-3,w+6,h+6,14,14);
        }
        g2.setColor(new Color(0,0,0,60)); g2.fillRoundRect(x+3,y+3,w,h,12,12);
        GradientPaint gp=new GradientPaint(x,y,node.nodeColor().brighter(),x,y+h,node.nodeColor().darker());
        g2.setPaint(gp); g2.setStroke(new BasicStroke(1));
        g2.fillRoundRect(x,y,w,h,12,12);
        g2.setColor(node.nodeColor().brighter().brighter()); g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x,y,w,h,12,12);
        if (!node.branchEnabled) { g2.setColor(new Color(0,0,0,120)); g2.fillRoundRect(x,y,w,h,12,12); }

        // Badge circle — use unicode so it renders well
        Color ic = node.nodeColor().brighter();
        g2.setColor(ic); g2.fillOval(x+7, y+7, 22, 22);
        g2.setColor(Color.WHITE);
        String badge = nodeTypeBadge(node.type);
        Font bf = new Font("Dialog", Font.BOLD, 13);
        g2.setFont(bf); FontMetrics bfm = g2.getFontMetrics();
        g2.drawString(badge, x+7+(22-bfm.stringWidth(badge))/2, y+7+(22-bfm.getHeight())/2+bfm.getAscent());

        g2.setFont(new Font("SansSerif",Font.BOLD,11)); g2.setColor(Color.WHITE);
        String lbl = node.label.length()>16 ? node.label.substring(0,14)+"\u2026" : node.label;
        g2.drawString(lbl, x+34, y+20);
        g2.setFont(new Font("SansSerif",Font.PLAIN,9)); g2.setColor(new Color(255,255,255,140));
        g2.drawString(node.type.name().replace("_"," "), x+34, y+32);

        if (node.id.equals(startNodeId)) {
            g2.setColor(new Color(40,220,80));
            g2.setFont(new Font("SansSerif",Font.BOLD,11));
            g2.drawString("\u25b6", x+w-18, y+14);
        }

        int numPorts = node.outputs.size();
        if (numPorts>0) {
            int spacing = w / (numPorts+1);
            for (int i=0; i<numPorts; i++) {
                NodePort port = node.outputs.get(i);
                int px = x + spacing*(i+1);
                int dotY = y+h;
                Color pc = portColor(port.name);
                String icon = portIcon(port.name);
                g2.setFont(new Font("SansSerif",Font.BOLD,10));
                g2.setColor(pc);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(icon, px-fm.stringWidth(icon)/2, dotY-10);
                g2.setColor(pc); g2.fillOval(px-5, dotY-5, 10, 10);
                g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1));
                g2.drawOval(px-5, dotY-5, 10, 10);
            }
        }
        Point inp = node.inputAnchor();
        g2.setColor(new Color(160,200,255));
        g2.fillOval(inp.x-5,inp.y-5,10,10);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1));
        g2.drawOval(inp.x-5,inp.y-5,10,10);
    }

    private void drawArrows(Graphics2D g2) {
        for (Arrow a : arrows) {
            BaseNode from=nodes.get(a.fromNodeId), to=nodes.get(a.toNodeId);
            if (from==null||to==null) continue;
            Point src=from.outputAnchor(a.fromPort), dst=to.inputAnchor();
            Color ac = a.selected ? new Color(255,220,60) : portColor(a.fromPort);
            drawOrthogonalArrow(g2, a, src, dst, ac);
        }
    }

    private void drawLiveArrow(Graphics2D g2) {
        Point src=connectFrom.outputAnchor(connectPort);
        int mx=(int)((connectMouse.x-panX)/zoom), my=(int)((connectMouse.y-panY)/zoom);
        g2.setColor(ARROW_HOVER);
        g2.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g2.drawLine(src.x, src.y, mx, my);
        drawArrowHead(g2, new Point(mx,my), ARROW_HOVER);
    }

    private void drawOrthogonalArrow(Graphics2D g2, Arrow a, Point src, Point dst, Color color) {
        int bendY = src.y + a.bendOffset;
        int[] xs = { src.x, src.x, dst.x, dst.x };
        int[] ys = { src.y, bendY,  bendY,  dst.y };
        g2.setColor(color);
        g2.setStroke(new BasicStroke(a.selected?2.5f:2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i=0; i<3; i++) g2.drawLine(xs[i],ys[i],xs[i+1],ys[i+1]);
        drawArrowHead(g2, dst, color);
        if (a.selected) {
            int hx = (src.x + dst.x)/2;
            g2.setColor(new Color(255,220,60));
            g2.fillOval(hx-5, bendY-5, 10, 10);
            g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1f));
            g2.drawOval(hx-5, bendY-5, 10, 10);
        }
        if (a.label!=null&&!a.label.isEmpty()) {
            int lx=(src.x+dst.x)/2, ly=bendY-8;
            g2.setFont(new Font("SansSerif",Font.BOLD,10));
            FontMetrics fm=g2.getFontMetrics(); int lw=fm.stringWidth(a.label);
            g2.setColor(new Color(30,30,40,200));
            g2.fillRoundRect(lx-lw/2-3,ly-12,lw+6,16,6,6);
            g2.setColor(new Color(220,220,100));
            g2.drawString(a.label, lx-lw/2, ly);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point tip, Color color) {
        g2.setColor(color); int sz=8;
        g2.fillPolygon(new int[]{tip.x,tip.x-sz/2,tip.x+sz/2},
                       new int[]{tip.y,tip.y-sz,tip.y-sz},3);
    }

    private boolean arrowHitTest(Arrow a, Point p) {
        BaseNode from=nodes.get(a.fromNodeId), to=nodes.get(a.toNodeId);
        if (from==null||to==null) return false;
        Point src=from.outputAnchor(a.fromPort), dst=to.inputAnchor();
        int bendY = src.y + a.bendOffset, tol = 6;
        if (nearSegment(p, src.x,src.y, src.x,bendY, tol)) return true;
        if (nearSegment(p, src.x,bendY, dst.x,bendY, tol)) return true;
        if (nearSegment(p, dst.x,bendY, dst.x,dst.y, tol)) return true;
        return false;
    }

    private boolean nearSegment(Point p, int x1, int y1, int x2, int y2, int tol) {
        int minX=Math.min(x1,x2)-tol, maxX=Math.max(x1,x2)+tol;
        int minY=Math.min(y1,y2)-tol, maxY=Math.max(y1,y2)+tol;
        if (p.x<minX||p.x>maxX||p.y<minY||p.y>maxY) return false;
        double dx=x2-x1, dy=y2-y1, len2=dx*dx+dy*dy;
        if (len2==0) return p.distance(x1,y1)<tol;
        double t=Math.max(0,Math.min(1,((p.x-x1)*dx+(p.y-y1)*dy)/len2));
        return p.distance(x1+t*dx, y1+t*dy)<tol;
    }

    private void handleMousePressed(MouseEvent e) {
        Point cv=screenToCanvas(e.getPoint());
        requestFocusInWindow();
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
        if (selectedArrow!=null) {
            BaseNode from=nodes.get(selectedArrow.fromNodeId);
            BaseNode to=nodes.get(selectedArrow.toNodeId);
            if (from!=null&&to!=null) {
                Point src=from.outputAnchor(selectedArrow.fromPort);
                int bendY=(int)((src.y+selectedArrow.bendOffset)*zoom)+panY;
                int hx=(int)(((src.x+to.inputAnchor().x)/2.0)*zoom)+panX;
                if (e.getPoint().distance(new Point(hx,bendY))<12) {
                    bendDragArrow=selectedArrow; bendDragStartY=e.getY();
                    bendDragOrigOffset=selectedArrow.bendOffset; return;
                }
            }
        }
        BaseNode hit=nodeAt(cv);
        if (hit!=null) {
            if (selectedArrow!=null) { selectedArrow.selected=false; selectedArrow=null; }
            dragNode=hit; dragOffX=cv.x-hit.x; dragOffY=cv.y-hit.y; selectNode(hit);
        } else {
            Arrow hitArrow=null;
            for (Arrow a:arrows) { if (arrowHitTest(a,cv)) { hitArrow=a; break; } }
            if (hitArrow!=null) {
                if (selectedArrow!=null) selectedArrow.selected=false;
                selectedArrow=hitArrow; hitArrow.selected=true;
                if (selectedNode!=null) { selectedNode=null; if(onNodeSelected!=null) onNodeSelected.accept(null); }
            } else {
                if (selectedArrow!=null) { selectedArrow.selected=false; selectedArrow=null; }
                selectNode(null);
            }
            repaint();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        panning=false;
        if (bendDragArrow!=null) { bendDragArrow=null; notifyChanged(); return; }
        if (connectFrom!=null) {
            Point cv=screenToCanvas(e.getPoint());
            BaseNode target=nodeAt(cv);
            if (target!=null&&target!=connectFrom) {
                connectFrom.setPortTarget(connectPort,target.id);
                arrows.removeIf(a->a.fromNodeId.equals(connectFrom.id)&&a.fromPort.equals(connectPort));
                NodePort port=null;
                for (NodePort p:connectFrom.outputs) if(p.name.equals(connectPort)){port=p;break;}
                arrows.add(new Arrow(connectFrom.id,connectPort,target.id,port!=null?port.displayLabel():connectPort));
                notifyChanged();
            }
            connectFrom=null; connectPort=null; connectMouse=null; repaint(); return;
        }
        dragNode=null;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (panning) { panX=e.getX()-panDragStartX; panY=e.getY()-panDragStartY; repaint(); return; }
        if (bendDragArrow!=null) {
            bendDragArrow.bendOffset=Math.max(10, bendDragOrigOffset+(int)((e.getY()-bendDragStartY)/zoom));
            repaint(); return;
        }
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
            String startTxt = hit.id.equals(startNodeId) ? "\u2605 Start node (click to unset)" : "\u25b6 Set as start node";
            addMenuItem(menu, startTxt, ()->{ startNodeId=hit.id.equals(startNodeId)?null:hit.id; repaint(); notifyChanged(); });
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
        hdr.setForeground(new Color(140,140,170)); hdr.setFont(new Font("SansSerif",Font.BOLD,10));
        hdr.setBorder(BorderFactory.createEmptyBorder(6,4,4,4)); m.add(hdr); addMenuSep(m);
    }

    private void addMenuItem(JPopupMenu m,String text,Runnable action) {
        JMenuItem item=new JMenuItem(text);
        item.setBackground(new Color(30,30,42)); item.setForeground(new Color(210,210,220));
        item.setFont(new Font("SansSerif",Font.PLAIN,12)); item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(5,12,5,12)); item.setOpaque(true);
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI(){
            protected void paintBackground(Graphics g,JMenuItem mi,Color bgColor){
                g.setColor(mi.isArmed()||mi.isSelected()?new Color(50,50,68):new Color(30,30,42));
                g.fillRect(0,0,mi.getWidth(),mi.getHeight());
            }
        });
        item.addActionListener(e->action.run()); m.add(item);
    }

    private void addMenuItemColored(JPopupMenu m,String text,Color accent,Runnable action) {
        JMenuItem item=new JMenuItem(text);
        item.setBackground(new Color(30,30,42)); item.setForeground(new Color(210,210,220));
        item.setFont(new Font("SansSerif",Font.PLAIN,12)); item.setBorderPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder(5,12,5,12)); item.setOpaque(true);
        item.setIcon(new javax.swing.Icon(){
            public void paintIcon(Component c,Graphics g,int x,int y){ g.setColor(accent); g.fillRoundRect(x,y+1,4,getIconHeight()-2,3,3); }
            public int getIconWidth(){ return 8; }
            public int getIconHeight(){ return 14; }
        });
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI(){
            protected void paintBackground(Graphics g,JMenuItem mi,Color bgColor){
                g.setColor(mi.isArmed()||mi.isSelected()?new Color(50,50,68):new Color(30,30,42));
                g.fillRect(0,0,mi.getWidth(),mi.getHeight());
            }
        });
        item.addActionListener(e->action.run()); m.add(item);
    }

    private void addMenuSep(JPopupMenu m) {
        JSeparator s=new JSeparator();
        s.setForeground(new Color(55,55,68)); s.setBackground(new Color(30,30,42)); m.add(s);
    }

    private BaseNode nodeAt(Point cv) {
        List<BaseNode> list=new ArrayList<>(nodes.values());
        for (int i=list.size()-1;i>=0;i--) if(list.get(i).bounds().contains(cv)) return list.get(i);
        return null;
    }
    private Point screenToCanvas(Point p) { return new Point((int)((p.x-panX)/zoom),(int)((p.y-panY)/zoom)); }
    private void selectNode(BaseNode node) { selectedNode=node; if(onNodeSelected!=null) onNodeSelected.accept(node); repaint(); }
    private void notifyChanged() { if(onCanvasChanged!=null) onCanvasChanged.run(); }
}