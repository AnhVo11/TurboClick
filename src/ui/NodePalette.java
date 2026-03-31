package ui;

import nodes.BaseNode;
import nodes.NodeFactory;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BiConsumer;

public class NodePalette extends JPanel {

    private BiConsumer<BaseNode.NodeType, Point> onNodeDropped;
    private Runnable onSmartPinClicked;
    private NodeCanvas targetCanvas;

    private static final BaseNode.NodeType[] PALETTE_TYPES = {
        BaseNode.NodeType.WATCH_ZONE,
        BaseNode.NodeType.IMAGE,
        BaseNode.NodeType.WATCH_CASE,
        BaseNode.NodeType.SIMPLE_CLICK,
        BaseNode.NodeType.KEYBOARD,
        BaseNode.NodeType.WAIT,
        BaseNode.NodeType.STOP
    };
    private static final String[] ICONS = { "\u25ce","\u25a3","\u25c9","\u2295","\u2328","\u23f1","\u25a0" };
    private static final String[] NAMES = { "Watch Zone","Image","Watch Case","Simple Click","Keyboard","Wait","Stop" };

    private BaseNode.NodeType draggingType = null;

    public NodePalette() {
        setBackground(new Color(25,25,35));
        setLayout(new FlowLayout(FlowLayout.LEFT,4,6));
        setBorder(BorderFactory.createMatteBorder(0,0,1,0,new Color(50,50,65)));
        setPreferredSize(new Dimension(0,58));

        JLabel header=new JLabel("NODES:");
        header.setForeground(new Color(100,100,130));
        header.setFont(new Font("SansSerif",Font.BOLD,10));
        header.setBorder(BorderFactory.createEmptyBorder(0,6,0,4));
        add(header);

        for (int i=0;i<PALETTE_TYPES.length;i++) add(buildCard(PALETTE_TYPES[i],ICONS[i],NAMES[i]));

        JLabel hint=new JLabel("Click or drag to canvas  \u00b7  Right-click canvas for menu");
        hint.setForeground(new Color(60,60,80)); hint.setFont(new Font("SansSerif",Font.PLAIN,9));
        hint.setBorder(BorderFactory.createEmptyBorder(0,8,0,0));
        add(hint);
    }

    public void setOnNodeDropped(BiConsumer<BaseNode.NodeType,Point> cb) { onNodeDropped=cb; }
    public void setOnSmartPinClicked(Runnable cb) { onSmartPinClicked=cb; }
    public void setTargetCanvas(NodeCanvas canvas) { targetCanvas=canvas; }

    private JPanel buildCard(BaseNode.NodeType type, String icon, String name) {
        Color nc=NodeFactory.color(type);

        JPanel card=new JPanel() {
            boolean hovered=false;
            {
                setPreferredSize(new Dimension(105,44));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Add "+name+" \u2014 drag to position");

                addMouseListener(new MouseAdapter(){
                    public void mouseEntered(MouseEvent e){ hovered=true; repaint(); }
                    public void mouseExited(MouseEvent e) { hovered=false; repaint(); }
                    public void mousePressed(MouseEvent e){
                        draggingType=type;
                        if (targetCanvas!=null) targetCanvas.startGhostDrag(type);
                    }
                    public void mouseReleased(MouseEvent e){
                        if (draggingType!=null) {
                            Point screenPt=e.getLocationOnScreen();
                            if (targetCanvas!=null && isOverCanvas(screenPt)) {
                                targetCanvas.finishGhostDrag(screenPt);
                                if (onNodeDropped!=null) onNodeDropped.accept(type,screenPt);
                            } else {
                                if (targetCanvas!=null) targetCanvas.cancelGhostDrag();
                                if (onNodeDropped!=null) onNodeDropped.accept(type,new Point(0,0));
                            }
                            draggingType=null;
                        }
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter(){
                    public void mouseDragged(MouseEvent e){
                        if (targetCanvas!=null&&draggingType!=null)
                            targetCanvas.updateGhostDrag(e.getLocationOnScreen());
                    }
                });
            }

            private boolean isOverCanvas(Point screenPt) {
                if (targetCanvas==null) return false;
                try {
                    Point canvasLoc=targetCanvas.getLocationOnScreen();
                    Rectangle bounds=new Rectangle(canvasLoc.x,canvasLoc.y,targetCanvas.getWidth(),targetCanvas.getHeight());
                    return bounds.contains(screenPt);
                } catch(Exception e){ return false; }
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color bg = hovered ? nc.darker().darker() : new Color(35,35,48);
                g2.setColor(bg); g2.fillRoundRect(0,2,getWidth()-1,getHeight()-4,8,8);
                g2.setColor(nc); g2.fillRoundRect(0,2,getWidth()-1,3,4,4);
                g2.setFont(new Font("SansSerif",Font.BOLD,14)); g2.setColor(nc.brighter());
                g2.drawString(icon,8,22);
                g2.setFont(new Font("SansSerif",Font.PLAIN,10));
                g2.setColor(hovered?Color.WHITE:new Color(200,200,215));
                String d=name;
                while(d.length()>1&&g2.getFontMetrics().stringWidth(d)>getWidth()-14) d=d.substring(0,d.length()-1);
                g2.drawString(d,8,36);
            }
        };
        return card;
    }
}