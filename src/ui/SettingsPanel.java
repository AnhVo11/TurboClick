package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.Properties;

public class SettingsPanel {
    private static final File CONFIG_FILE =
        new File(System.getProperty("user.home") + "/.turboclick/config.properties");
    private static Properties props = null;

    private static Properties getProps() {
        if (props != null) return props;
        props = new Properties();
        if (CONFIG_FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) { props.load(fis); }
            catch (Exception ignored) {}
        }
        return props;
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                getProps().store(fos, "TurboClick Settings");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String getApiKey() { return getProps().getProperty("anthropic.api.key", "").trim(); }
    public static void setApiKey(String key) { getProps().setProperty("anthropic.api.key", key.trim()); save(); }
    public static boolean hasApiKey() { return !getApiKey().isEmpty(); }

    public static void showDialog(Window parent) {
        JDialog dlg = new JDialog((Frame) null, "Settings", true);
        dlg.setSize(480, 260);
        dlg.setLocationRelativeTo(parent);
        dlg.getContentPane().setBackground(new Color(22, 22, 30));
        dlg.setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 15, 22));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("⚙  Settings");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(new Color(80, 160, 255));
        header.add(title, BorderLayout.WEST);
        dlg.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(new Color(22, 22, 30));
        body.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel apiLbl = new JLabel("ANTHROPIC API KEY");
        apiLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        apiLbl.setForeground(new Color(80, 140, 255));
        apiLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(apiLbl);
        body.add(Box.createVerticalStrut(6));

        JPanel keyRow = new JPanel(new BorderLayout(8, 0));
        keyRow.setBackground(new Color(22, 22, 30));
        keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JPasswordField keyField = new JPasswordField(getApiKey());
        keyField.setBackground(new Color(35, 35, 50));
        keyField.setForeground(new Color(220, 220, 230));
        keyField.setCaretColor(new Color(220, 220, 230));
        keyField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 85)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        keyField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JToggleButton showBtn = new JToggleButton("Show");
        showBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        showBtn.setBackground(new Color(40, 40, 58));
        showBtn.setForeground(new Color(160, 160, 200));
        showBtn.setBorderPainted(false); showBtn.setFocusPainted(false); showBtn.setOpaque(true);
        showBtn.setPreferredSize(new Dimension(60, 30));
        showBtn.addActionListener(e -> {
            keyField.setEchoChar(showBtn.isSelected() ? (char)0 : '•');
            showBtn.setText(showBtn.isSelected() ? "Hide" : "Show");
        });
        keyRow.add(keyField, BorderLayout.CENTER);
        keyRow.add(showBtn, BorderLayout.EAST);
        body.add(keyRow);
        body.add(Box.createVerticalStrut(6));

        JLabel hint = new JLabel("Get your key at console.anthropic.com");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 10));
        hint.setForeground(new Color(80, 80, 110));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        body.add(Box.createVerticalStrut(16));

        JLabel statusLbl = new JLabel(hasApiKey() ? "✓ API key is set" : "⚠ No API key — AI features disabled");
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLbl.setForeground(hasApiKey() ? new Color(80, 200, 100) : new Color(220, 160, 40));
        statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(statusLbl);
        dlg.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(new Color(18, 18, 24));
        JButton cancelBtn = footerBtn("Cancel", new Color(60, 60, 80));
        JButton saveBtn = footerBtn("Save", new Color(40, 120, 200));
        cancelBtn.addActionListener(e -> dlg.dispose());
        saveBtn.addActionListener(e -> {
            setApiKey(new String(keyField.getPassword()).trim());
            dlg.dispose();
        });
        footer.add(cancelBtn); footer.add(saveBtn);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private static JButton footerBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setOpaque(true); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(90, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
