package ui;

import nodes.BaseNode;
import nodes.NodeFactory;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BiConsumer;

public class NodePalette extends JPanel {

    private BiConsumer<BaseNode.NodeType, Point> onNodeDropped;

    public NodePalette() {
        setBackground(new Color(28, 28, 38));
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 8));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 65)));
        setPreferredSize(new Dimension(0, 62));

        JLabel header = new JLabel("NODES:");
        header.setForeground(new Color(120, 120, 150));
        header.setFont(new Font("SansSerif", Font.BOLD, 10));
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
        add(header);

        for (BaseNode.NodeType type : BaseNode.NodeType.values()) {
            add(buildCard(type));
        }

        JLabel hint = new JLabel("  Click to add  |  Right-click canvas for menu");
        hint.setForeground(new Color(70, 70, 90));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 9));
        add(hint);
    }

    public void setOnNodeDropped(BiConsumer<BaseNode.NodeType, Point> cb) {
        onNodeDropped = cb;
    }

    private JPanel buildCard(BaseNode.NodeType type) {
        Color nodeColor = NodeFactory.color(type);
        String name     = NodeFactory.displayName(type);

        JPanel card = new JPanel() {
            boolean hovered = false;
            {
                setPreferredSize(new Dimension(90, 44));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Add " + name);

                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                    public void mouseClicked(MouseEvent e) {
                        if (onNodeDropped != null)
                            onNodeDropped.accept(type, new Point(0, 0));
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hovered ? nodeColor.darker() : new Color(40, 40, 55));
                g2.fillRoundRect(0, 2, getWidth()-1, getHeight()-4, 10, 10);
                g2.setColor(nodeColor);
                g2.fillRoundRect(0, 2, getWidth()-1, 4, 6, 6);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.setColor(nodeColor.brighter());
                String abbr = name.substring(0, Math.min(2, name.length())).toUpperCase();
                g2.drawString(abbr, 6, 24);
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                g2.setColor(hovered ? Color.WHITE : new Color(200, 200, 210));
                String[] words = name.split(" ");
                g2.drawString(words[0], 6, 34);
                if (words.length >= 2) g2.drawString(words[1], 6, 44);
            }
        };
        return card;
    }
}
