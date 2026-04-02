package ui;

import nodes.*;
import nodes.BaseNode.NodeType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class TaskArchitectOverlay {

    // ── What we can select/drag ───────────────────────────────
    private interface ArchItem {
        String nodeLabel();

        String itemLabel();

        Color nodeColor();

        void draw(Graphics2D g2, boolean hovered, boolean selected);

        boolean hitTest(Point p);

        void dragTo(int dx, int dy);

        void commitDrag();
    }

    // ── Rect item (Watch Zone rect, Watch Case zone rect) ─────
    private static class RectItem implements ArchItem {
        String nodeLbl, itemLbl;
        Color color;
        int[] R; // x,y,w,h — live during drag
        int[] origin = new int[4];
        Runnable onCommit;

        RectItem(String nodeLbl, String itemLbl, Color color, Rectangle rect, Runnable onCommit) {
            this.nodeLbl = nodeLbl;
            this.itemLbl = itemLbl;
            this.color = color;
            this.onCommit = onCommit;
            R = new int[] { rect.x, rect.y, rect.width, rect.height };
            origin = new int[] { rect.x, rect.y, rect.width, rect.height };
        }

        public String nodeLabel() {
            return nodeLbl;
        }

        public String itemLabel() {
            return itemLbl;
        }

        public Color nodeColor() {
            return color;
        }

        public void draw(Graphics2D g2, boolean hovered, boolean selected) {
            Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    selected ? 80 : hovered ? 60 : 40);
            Color border = selected ? color.brighter() : hovered ? color.brighter() : color;
            g2.setColor(fill);
            g2.fillRect(R[0], R[1], R[2], R[3]);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(selected ? 2.5f : 1.8f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, selected ? null : new float[] { 6f, 4f }, 0f));
            g2.drawRect(R[0], R[1], R[2], R[3]);
            // Label inside top-left
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            String lbl = itemLbl;
            int lw = fm.stringWidth(lbl) + 10;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(R[0] + 4, R[1] + 4, lw, 18, 6, 6);
            g2.setColor(Color.WHITE);
            g2.drawString(lbl, R[0] + 9, R[1] + 17);
            // Size hint bottom-right
            String sz = R[2] + "×" + R[3];
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            int sw = g2.getFontMetrics().stringWidth(sz);
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fillRoundRect(R[0] + R[2] - sw - 8, R[1] + R[3] - 16, sw + 6, 13, 4, 4);
            g2.setColor(new Color(200, 220, 255));
            g2.drawString(sz, R[0] + R[2] - sw - 5, R[1] + R[3] - 5);
        }

        public boolean hitTest(Point p) {
            return p.x >= R[0] && p.x <= R[0] + R[2] && p.y >= R[1] && p.y <= R[1] + R[3];
        }

        public void dragTo(int dx, int dy) {
            R[0] = origin[0] + dx;
            R[1] = origin[1] + dy;
        }

        public void commitDrag() {
            System.arraycopy(R, 0, origin, 0, 4);
            if (onCommit != null)
                onCommit.run();
        }

        Rectangle toRect() {
            return new Rectangle(R[0], R[1], R[2], R[3]);
        }
    }

    // ── Resize handle on a RectItem ───────────────────────────
    private static final int H = 10; // handle size

    private static int[][] getHandles(int[] R) {
        int x = R[0], y = R[1], w = R[2], h = R[3];
        return new int[][] {
                { x - H / 2, y - H / 2 }, // TL
                { x + w / 2 - H / 2, y - H / 2 }, // T
                { x + w - H / 2, y - H / 2 }, // TR
                { x + w - H / 2, y + h / 2 - H / 2 }, // R
                { x + w - H / 2, y + h - H / 2 }, // BR
                { x + w / 2 - H / 2, y + h - H / 2 }, // B
                { x - H / 2, y + h - H / 2 }, // BL
                { x - H / 2, y + h / 2 - H / 2 }, // L
        };
    }

    private static int hitHandle(int[] R, Point p) {
        int[][] handles = getHandles(R);
        for (int i = 0; i < handles.length; i++)
            if (new Rectangle(handles[i][0], handles[i][1], H, H).contains(p))
                return i;
        return -1;
    }

    private static void applyHandleDrag(int[] R, int[] origin, int handle, int dx, int dy) {
        switch (handle) {
            case 0:
                R[0] = origin[0] + dx;
                R[1] = origin[1] + dy;
                R[2] = Math.max(20, origin[2] - dx);
                R[3] = Math.max(20, origin[3] - dy);
                break;
            case 1:
                R[1] = origin[1] + dy;
                R[3] = Math.max(20, origin[3] - dy);
                break;
            case 2:
                R[1] = origin[1] + dy;
                R[2] = Math.max(20, origin[2] + dx);
                R[3] = Math.max(20, origin[3] - dy);
                break;
            case 3:
                R[2] = Math.max(20, origin[2] + dx);
                break;
            case 4:
                R[2] = Math.max(20, origin[2] + dx);
                R[3] = Math.max(20, origin[3] + dy);
                break;
            case 5:
                R[3] = Math.max(20, origin[3] + dy);
                break;
            case 6:
                R[0] = origin[0] + dx;
                R[2] = Math.max(20, origin[2] - dx);
                R[3] = Math.max(20, origin[3] + dy);
                break;
            case 7:
                R[0] = origin[0] + dx;
                R[2] = Math.max(20, origin[2] - dx);
                break;
        }
    }

    // ── Pin item (Click node pin, Simple Click pins) ──────────
    private static class PinItem implements ArchItem {
        String nodeLbl, itemLbl;
        Color color;
        int x, y, ox, oy;
        int btnType; // 0=L,1=R,2=M,3=drag-end
        boolean isDragEnd;
        Runnable onCommit;

        PinItem(String nodeLbl, String itemLbl, Color color,
                int x, int y, int btnType, boolean isDragEnd, Runnable onCommit) {
            this.nodeLbl = nodeLbl;
            this.itemLbl = itemLbl;
            this.color = color;
            this.x = x;
            this.y = y;
            this.ox = x;
            this.oy = y;
            this.btnType = btnType;
            this.isDragEnd = isDragEnd;
            this.onCommit = onCommit;
        }

        public String nodeLabel() {
            return nodeLbl;
        }

        public String itemLabel() {
            return itemLbl;
        }

        public Color nodeColor() {
            return color;
        }

        public void draw(Graphics2D g2, boolean hovered, boolean selected) {
            int r = selected ? 13 : hovered ? 12 : 10;
            Color ring;
            switch (btnType) {
                case 1:
                    ring = new Color(220, 80, 80);
                    break; // right
                case 2:
                    ring = new Color(80, 200, 120);
                    break; // middle
                case 3:
                    ring = new Color(255, 140, 40);
                    break; // drag
                default:
                    ring = new Color(80, 140, 255);
                    break; // left
            }
            // Shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillOval(x - r + 2, y - r + 2, r * 2, r * 2);
            // Ring
            g2.setColor(ring);
            g2.setStroke(new BasicStroke(selected ? 2.5f : 2f));
            g2.drawOval(x - r, y - r, r * 2, r * 2);
            // Crosshair
            g2.setColor(new Color(255, 255, 255, 230));
            g2.setStroke(new BasicStroke(1.5f));
            int arm = r + 4, gap = 4;
            g2.drawLine(x - arm, y, x - gap, y);
            g2.drawLine(x + gap, y, x + arm, y);
            g2.drawLine(x, y - arm, x, y - gap);
            g2.drawLine(x, y + gap, x, y + arm);
            // Center red dot
            g2.setColor(new Color(255, 50, 50));
            g2.fillOval(x - 3, y - 3, 6, 6);
            // Number badge — only when not hovered (exactly like smart pin)
            if (!hovered) {
                // Show number badge over center dot
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                FontMetrics fm = g2.getFontMetrics();
                int lw = fm.stringWidth(itemLbl);
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillOval(x - 6, y - 6, 12, 12);
                g2.setColor(Color.WHITE);
                g2.drawString(itemLbl, x - lw / 2, y + 4);
            } else {
                // Hovered — just enlarge red dot, no text at all
                g2.setColor(new Color(255, 50, 50));
                g2.fillOval(x - 5, y - 5, 10, 10);
            }
        }

        public boolean hitTest(Point p) {
            return p.distance(x, y) < 14;
        }

        public void dragTo(int dx, int dy) {
            x = ox + dx;
            y = oy + dy;
        }

        public void commitDrag() {
            ox = x;
            oy = y;
            if (onCommit != null)
                onCommit.run();
        }
    }

    // ── Build items from all nodes in the canvas ──────────────
    private static List<ArchItem> buildItems(Map<String, BaseNode> nodeMap) {
        List<ArchItem> items = new ArrayList<>();

        for (BaseNode node : nodeMap.values()) {
            Color nc = node.nodeColor();
            String nl = node.label;

            try {
                switch (node.type) {

                    case WATCH_ZONE: {
                        java.lang.reflect.Field wz = getField(node, "watchZone");
                        Rectangle rect = (Rectangle) wz.get(node);
                        if (rect == null)
                            break;
                        RectItem[] riRef = { null };
                        riRef[0] = new RectItem(nl, nl, nc, rect, () -> {
                            try {
                                wz.set(node, riRef[0].toRect());
                            } catch (Exception ignored) {
                            }
                        });
                        items.add(riRef[0]);

                        java.lang.reflect.Field caf = getField(node, "clickAtMatch");
                        boolean clickAtMatch = (boolean) caf.get(node);
                        if (!clickAtMatch) {
                            java.lang.reflect.Field cxf = getField(node, "clickX");
                            java.lang.reflect.Field cyf = getField(node, "clickY");
                            int cx = (int) cxf.get(node), cy = (int) cyf.get(node);
                            PinItem[] piRef = { null };
                            piRef[0] = new PinItem(nl, "pin", nc, cx, cy, 0, false, () -> {
                                try {
                                    cxf.set(node, piRef[0].x);
                                    cyf.set(node, piRef[0].y);
                                } catch (Exception ignored) {
                                }
                            });
                            items.add(piRef[0]);
                        }
                        break;
                    }

                    case CLICK: {
                        java.lang.reflect.Field cxf = getField(node, "clickX");
                        java.lang.reflect.Field cyf = getField(node, "clickY");
                        java.lang.reflect.Field btnf = getField(node, "mouseButton");
                        int cx = (int) cxf.get(node), cy = (int) cyf.get(node), btn = (int) btnf.get(node);
                        PinItem[] piRef = { null };
                        piRef[0] = new PinItem(nl, "1", nc, cx, cy, btn, false, () -> {
                            try {
                                cxf.set(node, piRef[0].x);
                                cyf.set(node, piRef[0].y);
                            } catch (Exception ignored) {
                            }
                        });
                        items.add(piRef[0]);
                        break;
                    }

                    case SIMPLE_CLICK: {
                        java.lang.reflect.Field ptf = getField(node, "points");
                        @SuppressWarnings("unchecked")
                        List<int[]> pts = (List<int[]>) ptf.get(node);
                        if (pts == null)
                            break;
                        for (int i = 0; i < pts.size(); i++) {
                            final int idx = i;
                            int[] pt = pts.get(i);
                            int btnT = pt.length > 5 ? pt[5] : 0;
                            boolean isDragEnd = btnT == 3 && i > 0 && pts.get(i - 1).length > 5
                                    && pts.get(i - 1)[5] == 3;
                            String lbl = String.valueOf(i + 1); // number only — no name suffix
                            PinItem[] piRef = { null };
                            piRef[0] = new PinItem(nl, lbl, nc, pt[0], pt[1], btnT, isDragEnd, () -> {
                                pts.get(idx)[0] = piRef[0].x;
                                pts.get(idx)[1] = piRef[0].y;
                            });
                            items.add(piRef[0]);
                        }
                        break;
                    }

                    case WATCH_CASE: {
                        if (!(node instanceof WatchCaseNode))
                            break;
                        WatchCaseNode wc = (WatchCaseNode) node;
                        for (int zi = 0; zi < wc.zones.size(); zi++) {
                            final int zidx = zi;
                            WatchCaseNode.WatchZone zone = wc.zones.get(zi);
                            if (zone.rect == null)
                                continue;
                            RectItem[] riRef = { null };
                            riRef[0] = new RectItem(nl, nl + " / " + zone.name, nc, zone.rect, () -> {
                                wc.zones.get(zidx).rect = riRef[0].toRect();
                            });
                            items.add(riRef[0]);
                            if (zone.clickMode == WatchCaseNode.CLICK_CUSTOM_PIN) {
                                PinItem[] piRef = { null };
                                piRef[0] = new PinItem(nl, zone.name + " pin",
                                        nc, zone.customPinX, zone.customPinY, 0, false, () -> {
                                            wc.zones.get(zidx).customPinX = piRef[0].x;
                                            wc.zones.get(zidx).customPinY = piRef[0].y;
                                        });
                                items.add(piRef[0]);
                            }
                        }
                        break;
                    }

                    default:
                        break;
                }
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private static java.lang.reflect.Field getField(Object obj, String name) throws Exception {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ── Show the overlay ──────────────────────────────────────
    public static void show(Map<String, BaseNode> nodeMap, Window parentWindow, Runnable onDone) {
        List<ArchItem> items = buildItems(nodeMap);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0, 0, 0, 0));
        overlay.setBounds(0, 0, screen.width, screen.height);

        // State
        ArchItem[] hovered = { null };
        ArchItem[] selected = { null };
        int[] dragStart = { 0, 0 };
        int[] originSnap = { 0, 0 }; // origin rect backup for RectItem resize
        int[] handleIdx = { -1 }; // -1=move, 0-7=resize handle
        boolean[] dragging = { false };

        // HUD label refs
        JLabel[] hudNodeLbl = { null };
        JLabel[] hudItemLbl = { null };

        // ── Glass panel ───────────────────────────────────────
        JPanel glass = new JPanel(null) {
            {
                setDoubleBuffered(true);
            }

            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Subtle dark tint
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Draw rect items first, then pins on top
                for (ArchItem item : items) {
                    if (!(item instanceof RectItem))
                        continue;
                    item.draw(g2, item == hovered[0], item == selected[0]);
                    // Draw resize handles if selected
                    if (item == selected[0]) {
                        RectItem ri = (RectItem) item;
                        g2.setColor(Color.WHITE);
                        for (int[] hh : getHandles(ri.R))
                            g2.fillRect(hh[0], hh[1], H, H);
                        g2.setColor(new Color(100, 160, 255));
                        g2.setStroke(new BasicStroke(1f));
                        for (int[] hh : getHandles(ri.R))
                            g2.drawRect(hh[0], hh[1], H, H);
                    }
                }
                // Drag lines between consecutive drag pins
                drawDragLines(g2, items);

                for (ArchItem item : items) {
                    if (!(item instanceof PinItem))
                        continue;
                    item.draw(g2, item == hovered[0], item == selected[0]);
                }
                g2.dispose();
            }
        };
        glass.setOpaque(false);
        glass.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // ── HUD bar ───────────────────────────────────────────
        JPanel hud = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 15, 22, 235));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(80, 160, 255, 200));
                g2.fillRect(0, 0, getWidth(), 3);
            }
        };
        hud.setOpaque(false);
        hud.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8));

        JLabel archLbl = new JLabel("⬡ Task Architect");
        archLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        archLbl.setForeground(new Color(80, 160, 255));

        JLabel sep1 = hudSep();

        JLabel nodeLbl = new JLabel("Hover a zone or pin");
        nodeLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        nodeLbl.setForeground(new Color(200, 200, 220));
        nodeLbl.setPreferredSize(new Dimension(180, 18));
        hudNodeLbl[0] = nodeLbl;

        JLabel itemLbl = new JLabel("");
        itemLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        itemLbl.setForeground(new Color(120, 180, 255));
        itemLbl.setPreferredSize(new Dimension(160, 18));
        hudItemLbl[0] = itemLbl;

        JLabel sep2 = hudSep();

        JLabel hintLbl = new JLabel("Drag to move  ·  Handles resize zones  ·  Right-click delete");
        hintLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hintLbl.setForeground(new Color(80, 80, 110));

        JLabel sep3 = hudSep();

        JButton doneBtn = new JButton("✓  Done");
        doneBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        doneBtn.setBackground(new Color(40, 160, 80));
        doneBtn.setForeground(Color.WHITE);
        doneBtn.setOpaque(true);
        doneBtn.setBorderPainted(false);
        doneBtn.setFocusPainted(false);
        doneBtn.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        doneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        hud.add(archLbl);
        hud.add(sep1);
        hud.add(nodeLbl);
        hud.add(itemLbl);
        hud.add(sep2);
        hud.add(hintLbl);
        hud.add(sep3);
        hud.add(doneBtn);

        hud.setSize(screen.width, 46);
        hud.setLocation(0, 28);
        glass.add(hud);

        // ── Close logic ───────────────────────────────────────
        Runnable close = () -> SwingUtilities.invokeLater(() -> {
            overlay.dispose();
            if (parentWindow != null) {
                parentWindow.setVisible(true);
                parentWindow.toFront();
            }
            if (onDone != null)
                onDone.run();
        });

        doneBtn.addActionListener(e -> close.run());

        // ESC to close
        com.github.kwhat.jnativehook.keyboard.NativeKeyListener escL = new com.github.kwhat.jnativehook.keyboard.NativeKeyListener() {
            public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
                if (e.getKeyCode() == com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ESCAPE)
                    close.run();
            }

            public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
            }

            public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
            }
        };
        try {
            com.github.kwhat.jnativehook.GlobalScreen.addNativeKeyListener(escL);
        } catch (Exception ignored) {
        }
        overlay.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                try {
                    com.github.kwhat.jnativehook.GlobalScreen.removeNativeKeyListener(escL);
                } catch (Exception ignored) {
                }
            }
        });

        // ── Mouse handling ────────────────────────────────────
        glass.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                if (hud.getBounds().contains(e.getPoint())) {
                    if (hovered[0] != null) {
                        hovered[0] = null;
                        glass.repaint();
                    }
                    return;
                }
                ArchItem found = hitTest(items, e.getPoint());
                if (found == hovered[0])
                    return; // no change — skip repaint
                hovered[0] = found;
                if (found != null) {
                    hudNodeLbl[0].setText(found.nodeLabel());
                    hudItemLbl[0].setText("· " + found.itemLabel());
                    glass.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                    hudNodeLbl[0].setText("Hover a zone or pin");
                    hudItemLbl[0].setText("");
                    glass.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                glass.repaint();
            }

            public void mouseDragged(MouseEvent e) {
                if (!dragging[0] || selected[0] == null)
                    return;
                int dx = e.getX() - dragStart[0], dy = e.getY() - dragStart[1];
                if (selected[0] instanceof RectItem && handleIdx[0] >= 0) {
                    RectItem ri = (RectItem) selected[0];
                    System.arraycopy(originSnap, 0, ri.R, 0, 4);
                    applyHandleDrag(ri.R, originSnap, handleIdx[0], dx, dy);
                } else {
                    selected[0].dragTo(dx, dy);
                }
                hudNodeLbl[0].setText(selected[0].nodeLabel());
                hudItemLbl[0].setText("· " + selected[0].itemLabel());
                glass.repaint();
            }
        });

        glass.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (hud.getBounds().contains(e.getPoint()))
                    return;
                Point p = e.getPoint();
                // Check resize handles first
                if (selected[0] instanceof RectItem) {
                    RectItem ri = (RectItem) selected[0];
                    int hi = hitHandle(ri.R, p);
                    if (hi >= 0) {
                        dragging[0] = true;
                        dragStart[0] = p.x;
                        dragStart[1] = p.y;
                        handleIdx[0] = hi;
                        System.arraycopy(ri.R, 0, originSnap, 0, 4);
                        return;
                    }
                }
                ArchItem hit = hitTest(items, p);
                selected[0] = hit;
                if (hit != null) {
                    dragging[0] = true;
                    dragStart[0] = p.x;
                    dragStart[1] = p.y;
                    handleIdx[0] = -1;
                    if (hit instanceof RectItem) {
                        RectItem ri = (RectItem) hit;
                        System.arraycopy(ri.R, 0, originSnap, 0, 4);
                        System.arraycopy(ri.R, 0, ri.origin, 0, 4); // sync origin so dragTo offset is correct
                    }
                    if (hit instanceof PinItem) {
                        PinItem pi = (PinItem) hit;
                        pi.ox = pi.x;
                        pi.oy = pi.y;
                    }
                }
                glass.repaint();
            }

            public void mouseReleased(MouseEvent e) {
                if (dragging[0] && selected[0] != null)
                    selected[0].commitDrag();
                dragging[0] = false;
                handleIdx[0] = -1;
                glass.repaint();
            }

            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    ArchItem hit = hitTest(items, e.getPoint());
                    if (hit != null) {
                        int r = JOptionPane.showConfirmDialog(overlay,
                                "Remove \"" + hit.itemLabel() + "\" from its node?\n(Clears the stored position/zone)",
                                "Delete", JOptionPane.YES_NO_OPTION);
                        if (r == JOptionPane.YES_OPTION) {
                            clearItem(hit, nodeMap);
                            items.remove(hit);
                            selected[0] = null;
                            hovered[0] = null;
                            glass.repaint();
                        }
                    }
                }
            }
        });

        overlay.setContentPane(glass);
        overlay.setVisible(true);
        glass.requestFocusInWindow();
    }

    // ── Draw dashed lines between drag pin pairs ──────────────
    private static void drawDragLines(Graphics2D g2, List<ArchItem> items) {
        PinItem prev = null;
        for (ArchItem item : items) {
            if (!(item instanceof PinItem)) {
                prev = null;
                continue;
            }
            PinItem pi = (PinItem) item;
            if (pi.isDragEnd && prev != null && prev.nodeLbl.equals(pi.nodeLbl)) {
                float[] dash = { 10f, 5f };
                g2.setColor(new Color(255, 140, 40, 180));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
                g2.drawLine(prev.x, prev.y, pi.x, pi.y);
                // Arrowhead
                double angle = Math.atan2(pi.y - prev.y, pi.x - prev.x);
                int sz = 10;
                int ax1 = (int) (pi.x - sz * Math.cos(angle - 0.4)), ay1 = (int) (pi.y - sz * Math.sin(angle - 0.4));
                int ax2 = (int) (pi.x - sz * Math.cos(angle + 0.4)), ay2 = (int) (pi.y - sz * Math.sin(angle + 0.4));
                g2.fillPolygon(new int[] { pi.x, ax1, ax2 }, new int[] { pi.y, ay1, ay2 }, 3);
            }
            prev = pi;
        }
    }

    // ── Hit test — pins on top of rects ──────────────────────
    private static ArchItem hitTest(List<ArchItem> items, Point p) {
        // Pins first (higher priority)
        for (int i = items.size() - 1; i >= 0; i--)
            if (items.get(i) instanceof PinItem && items.get(i).hitTest(p))
                return items.get(i);
        for (int i = items.size() - 1; i >= 0; i--)
            if (items.get(i) instanceof RectItem && items.get(i).hitTest(p))
                return items.get(i);
        return null;
    }

    // ── Clear item — null out the underlying node field ───────
    private static void clearItem(ArchItem item, Map<String, BaseNode> nodeMap) {
        for (BaseNode node : nodeMap.values()) {
            if (!node.label.equals(item.nodeLabel()))
                continue;
            try {
                if (item instanceof RectItem) {
                    if (node.type == NodeType.WATCH_ZONE)
                        getField(node, "watchZone").set(node, null);
                    // WatchCase zones are not deleted — just the rect inside
                    if (node instanceof WatchCaseNode) {
                        WatchCaseNode wc = (WatchCaseNode) node;
                        for (WatchCaseNode.WatchZone z : wc.zones)
                            if (item.itemLabel().contains(z.name))
                                z.rect = null;
                    }
                } else if (item instanceof PinItem) {
                    if (node.type == NodeType.CLICK) {
                        getField(node, "clickX").set(node, 0);
                        getField(node, "clickY").set(node, 0);
                    }
                    if (node.type == NodeType.WATCH_ZONE) {
                        getField(node, "clickX").set(node, -1);
                        getField(node, "clickY").set(node, -1);
                    }
                    if (node.type == NodeType.SIMPLE_CLICK) {
                        java.lang.reflect.Field f = getField(node, "points");
                        @SuppressWarnings("unchecked")
                        List<int[]> pts = (List<int[]>) f.get(node);
                        // Remove the matching pin by label index
                        String lbl = item.itemLabel();
                        try {
                            int idx = Integer.parseInt(lbl.replaceAll("\\D+", "")) - 1;
                            if (idx >= 0 && idx < pts.size())
                                pts.remove(idx);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static JLabel hudSep() {
        JLabel s = new JLabel("|");
        s.setForeground(new Color(50, 50, 70));
        return s;
    }
}