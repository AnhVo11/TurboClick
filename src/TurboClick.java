import ui.*;
import nodes.*;
import engine.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

/**
 * TurboClick — main entry point.
 * Top-level window with:
 *   • Left sidebar: mode switcher (Smart Click / Simple Click)
 *   • Center: either the tree tab system or the Simple Click panel
 */
public class TurboClick implements NativeKeyListener {

    static TurboClick   instance;
    static JFrame       frame;
    static JPanel       centerPanel;   // swaps between smartPanel and simplePanel
    static CardLayout   centerLayout;

    // ── Smart Click tab system ────────────────────────────────
    static JPanel              tabBar;
    static JPanel              tabContent;
    static List<TreeTab>       treeTabs    = new ArrayList<>();
    static TreeTab             activeTab   = null;
    static int                 tabCounter  = 1;

    // ── Simple Click state ────────────────────────────────────
    static SimpleClickPanel simpleClickPanel;

    // ── Hotkey (global stop all) ──────────────────────────────
    static int     globalStopKey  = NativeKeyEvent.VC_F8;
    static String  globalStopName = "F8";

    // ──────────────────────────────────────────────────────────
    public static void main(String[] args) {
        instance = new TurboClick();
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(instance);
        } catch (NativeHookException e) { System.err.println("Hook: "+e.getMessage()); }
        SwingUtilities.invokeLater(TurboClick::buildUI);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { GlobalScreen.unregisterNativeHook(); } catch (NativeHookException ignored) {}
        }));
    }

    // ──────────────────────────────────────────────────────────
    static void buildUI() {
        frame = new JFrame("TurboClick");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 720);
        frame.setMinimumSize(new Dimension(800, 560));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(18, 18, 24));

        // ── Top title bar ─────────────────────────────────────
        root.add(buildTitleBar(), BorderLayout.NORTH);

        // ── Left mode switcher ────────────────────────────────
        root.add(buildModeSidebar(), BorderLayout.WEST);

        // ── Center — card layout ──────────────────────────────
        centerLayout = new CardLayout();
        centerPanel  = new JPanel(centerLayout);
        centerPanel.setBackground(new Color(22, 22, 28));

        // Smart Click area (tab system)
        JPanel smartPanel = buildSmartPanel();
        centerPanel.add(smartPanel, "smart");

        // Simple Click area
        simpleClickPanel = new SimpleClickPanel();
        centerPanel.add(simpleClickPanel, "simple");

        root.add(centerPanel, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Add first tree tab by default
        addNewTreeTab();
    }

    // ── Title bar ─────────────────────────────────────────────
    static JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(15, 15, 20));
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JLabel title = new JLabel("TurboClick");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(80, 180, 255));

        JLabel subtitle = new JLabel("Visual Automation Builder");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subtitle.setForeground(new Color(100, 100, 120));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(title); left.add(subtitle);

        // Global hotkey info
        JLabel hotkey = new JLabel("Global stop: ["+globalStopName+"]");
        hotkey.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hotkey.setForeground(new Color(100, 100, 120));

        bar.add(left, BorderLayout.WEST);
        bar.add(hotkey, BorderLayout.EAST);
        return bar;
    }

    // ── Mode sidebar ──────────────────────────────────────────
    static JPanel buildModeSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(18, 18, 24));
        side.setBorder(new EmptyBorder(16, 0, 0, 0));
        side.setPreferredSize(new Dimension(130, 0));

        JLabel lbl = new JLabel("  MODE");
        lbl.setForeground(new Color(80, 80, 100));
        lbl.setFont(new Font("SansSerif", Font.BOLD, 9));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(lbl);
        side.add(Box.createVerticalStrut(8));

        JButton smartBtn  = modeBtn("Build Smart Click", true);
        JButton simpleBtn = modeBtn("Build Simple Click", false);

        smartBtn.addActionListener(e  -> { centerLayout.show(centerPanel,"smart");  setModeActive(smartBtn, simpleBtn); });
        simpleBtn.addActionListener(e -> { centerLayout.show(centerPanel,"simple"); setModeActive(simpleBtn, smartBtn); });

        side.add(smartBtn);
        side.add(Box.createVerticalStrut(4));
        side.add(simpleBtn);
        side.add(Box.createVerticalGlue());

        // Version
        JLabel ver = new JLabel("  v2.0");
        ver.setForeground(new Color(60,60,80));
        ver.setFont(new Font("SansSerif",Font.PLAIN,9));
        side.add(ver);
        side.add(Box.createVerticalStrut(10));

        setModeActive(smartBtn, simpleBtn); // smart is default
        return side;
    }

    static void setModeActive(JButton active, JButton inactive) {
        active.setBackground(new Color(40,100,180));
        active.setForeground(Color.WHITE);
        inactive.setBackground(new Color(28,28,38));
        inactive.setForeground(new Color(160,160,180));
    }

    static JButton modeBtn(String text, boolean active) {
        JButton b = new JButton("<html>"+text.replace("\n","<br>")+"</html>");
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setBackground(active ? new Color(40,100,180) : new Color(28,28,38));
        b.setForeground(active ? Color.WHITE : new Color(160,160,180));
        b.setOpaque(true); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(130, 54));
        b.setPreferredSize(new Dimension(130, 54));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ── Smart Panel (tab bar + canvas area) ───────────────────
    static JPanel buildSmartPanel() {
        JPanel panel = new JPanel(new BorderLayout(0,0));
        panel.setBackground(new Color(22, 22, 28));

        // Tab bar at top
        tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, 38); }
        };
        tabBar.setBackground(new Color(18, 18, 24));
        tabBar.setBorder(BorderFactory.createMatteBorder(0,0,2,0,new Color(50,50,70)));
        tabBar.setPreferredSize(new Dimension(0, 38));

        // Add tab "+" button
        JButton addTabBtn = new JButton("+");
        addTabBtn.setFont(new Font("SansSerif",Font.BOLD,14));
        addTabBtn.setBackground(new Color(18,18,24));
        addTabBtn.setForeground(new Color(80,140,255));
        addTabBtn.setBorderPainted(false); addTabBtn.setFocusPainted(false);
        addTabBtn.setOpaque(true);
        addTabBtn.setPreferredSize(new Dimension(36,38));
        addTabBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addTabBtn.addActionListener(e -> addNewTreeTab());
        tabBar.add(addTabBtn);

        // Tab content area
        tabContent = new JPanel(new CardLayout());
        tabContent.setBackground(new Color(22,22,28));

        panel.add(tabBar,     BorderLayout.NORTH);
        panel.add(tabContent, BorderLayout.CENTER);
        return panel;
    }

    // ── Add new tree tab ──────────────────────────────────────
    static void addNewTreeTab() {
        String name = "Task " + tabCounter++;
        TreeTab tab = new TreeTab(name);
        treeTabs.add(tab);

        // Tab header button
        JPanel tabHeader = buildTabHeader(name, tab);
        // Insert before the "+" button
        tabBar.add(tabHeader, tabBar.getComponentCount()-1);
        tabBar.revalidate();

        tabContent.add(tab, tab.treeId);
        switchToTab(tab);

        tab.setOnRunStateChanged(() -> {
            // Refresh tab header dot
            tabBar.revalidate(); tabBar.repaint();
        });
    }

    static JPanel buildTabHeader(String name, TreeTab tab) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setBackground(new Color(18,18,24));
        header.setPreferredSize(new Dimension(140, 38));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif",Font.PLAIN,10));
        dot.setForeground(new Color(60,60,80));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(new Font("SansSerif",Font.PLAIN,12));
        nameLbl.setForeground(new Color(180,180,200));

        JLabel closeBtn = new JLabel("×");
        closeBtn.setFont(new Font("SansSerif",Font.BOLD,14));
        closeBtn.setForeground(new Color(100,100,120));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ e.consume(); closeTab(tab); }
            public void mouseEntered(MouseEvent e){ closeBtn.setForeground(new Color(220,80,80)); }
            public void mouseExited(MouseEvent e) { closeBtn.setForeground(new Color(100,100,120)); }
        });

        header.add(dot); header.add(nameLbl); header.add(closeBtn);

        header.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (e.getButton()==MouseEvent.BUTTON3) showTabContextMenu(e, tab, nameLbl);
                else switchToTab(tab);
            }
        });

        // Double-click name to rename
        nameLbl.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (e.getClickCount()==2) {
                    String newName = JOptionPane.showInputDialog(frame,"Rename task:",tab.treeName);
                    if (newName!=null&&!newName.trim().isEmpty()) {
                        tab.treeName = newName.trim();
                        nameLbl.setText(tab.treeName);
                        tabBar.revalidate();
                    }
                }
            }
        });

        tab.setOnRunStateChanged(() -> SwingUtilities.invokeLater(() ->
            dot.setForeground(tab.isRunning() ? new Color(40,220,80) : new Color(60,60,80))));

        return header;
    }

    static void switchToTab(TreeTab tab) {
        activeTab = tab;
        CardLayout cl = (CardLayout) tabContent.getLayout();
        cl.show(tabContent, tab.treeId);
        // Highlight active tab header
        for (Component c : tabBar.getComponents()) {
            if (c instanceof JPanel) {
                c.setBackground(new Color(18,18,24));
            }
        }
        tabBar.revalidate(); tabBar.repaint();
    }

    static void closeTab(TreeTab tab) {
        if (treeTabs.size() <= 1) { JOptionPane.showMessageDialog(frame,"Cannot close the last tab."); return; }
        tab.stopTree();
        treeTabs.remove(tab);
        tabContent.remove(tab);
        // Remove header
        for (int i = tabBar.getComponentCount()-1; i >= 0; i--) {
            Component c = tabBar.getComponent(i);
            // Simple check — remove matching panel
        }
        tabBar.removeAll();
        // Re-add all remaining headers + "+" button
        JButton addBtn = new JButton("+");
        addBtn.setFont(new Font("SansSerif",Font.BOLD,16));
        addBtn.setBackground(new Color(18,18,24)); addBtn.setForeground(new Color(100,150,200));
        addBtn.setBorderPainted(false); addBtn.setFocusPainted(false); addBtn.setOpaque(true);
        addBtn.setPreferredSize(new Dimension(36,38));
        addBtn.addActionListener(e -> addNewTreeTab());
        for (TreeTab t : treeTabs) tabBar.add(buildTabHeader(t.treeName, t));
        tabBar.add(addBtn);
        tabBar.revalidate(); tabBar.repaint();
        switchToTab(treeTabs.get(0));
    }

    static void showTabContextMenu(MouseEvent e, TreeTab tab, JLabel nameLbl) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("✏ Rename");
        rename.addActionListener(ev -> {
            String n = JOptionPane.showInputDialog(frame,"Rename:",tab.treeName);
            if (n!=null&&!n.trim().isEmpty()) { tab.treeName=n.trim(); nameLbl.setText(tab.treeName); tabBar.revalidate(); }
        });
        JMenuItem dup = new JMenuItem("⧉ Duplicate");
        dup.addActionListener(ev -> { /* TODO: deep copy nodes */ });
        JMenuItem run = new JMenuItem(tab.isRunning() ? "■ Stop" : "▶ Run");
        run.addActionListener(ev -> { if(tab.isRunning()) tab.stopTree(); else tab.startTree(); });
        JMenuItem del = new JMenuItem("🗑 Close Tab");
        del.addActionListener(ev -> closeTab(tab));
        menu.add(rename); menu.add(dup); menu.addSeparator(); menu.add(run); menu.addSeparator(); menu.add(del);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ── Global hotkey ─────────────────────────────────────────
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == globalStopKey) {
            SwingUtilities.invokeLater(() -> {
                for (TreeTab t : treeTabs) t.stopTree();
                if (simpleClickPanel != null) simpleClickPanel.stopClicking();
            });
        }
        // Forward to active tree tab if it has a hotkey
        if (activeTab != null) {
            if (e.getKeyCode() == activeTab.hotKeyCode) {
                SwingUtilities.invokeLater(() -> {
                    if (activeTab.isRunning()) activeTab.stopTree(); else activeTab.startTree();
                });
            }
        }
    }
    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}
}