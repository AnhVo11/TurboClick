package ui;

import engine.SimpleClickEngine;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class SimpleClickPanel extends JPanel {

    private static final Color BG         = new Color(22, 22, 28);
    private static final Color PANEL_BG   = new Color(28, 28, 38);
    private static final Color BORDER_C   = new Color(50, 50, 65);
    private static final Color TEXT       = new Color(220, 220, 230);
    private static final Color TEXT_DIM   = new Color(120, 120, 150);
    private static final Color INPUT_BG   = new Color(35, 35, 50);
    private static final Color INPUT_BD   = new Color(60, 60, 85);
    private static final Color ACCENT     = new Color(80, 140, 255);

    private volatile boolean running    = false;
    private volatile long    clickCount = 0;
    private SimpleClickEngine engine;
    private Thread            engineThread;

    private JLabel     statusLabel, clickCountLabel, smartIntervalLabel;
    private JSpinner   spHours, spMinutes, spSeconds, spTenths, spHundredths, spThousandths;
    private JTextField maxClicksField;
    private JComboBox<String> mouseButtonCombo, clickTypeCombo;
    private JPanel     intervalSpinnersPanel;
    private DefaultTableModel tableModel;
    private JTable     pointsTable;

    public SimpleClickPanel() {
        setBackground(BG);
        setLayout(new BorderLayout());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(12,15,12,15));
        inner.setBackground(BG);

        JLabel title = new JLabel("Build Simple Click");
        title.setFont(new Font("SansSerif",Font.BOLD,20));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Fast & Simple Auto Clicker");
        subtitle.setFont(new Font("SansSerif",Font.PLAIN,11));
        subtitle.setForeground(TEXT_DIM);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(Box.createVerticalStrut(6));
        inner.add(title); inner.add(subtitle);
        inner.add(Box.createVerticalStrut(12));

        inner.add(buildIntervalPanel());
        inner.add(Box.createVerticalStrut(10));
        inner.add(buildPointsTable());
        inner.add(Box.createVerticalStrut(10));
        inner.add(buildStatusPanel());
        inner.add(Box.createVerticalStrut(12));
        inner.add(buildButtons());
        inner.add(Box.createVerticalStrut(8));

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildIntervalPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setBorder(titledBorder("Click Interval"));

        String[] unitLabels={"Hours","Minutes","Seconds","1/10 s","1/100 s","1/1000 s"};
        JPanel headerRow=new JPanel(new GridLayout(1,6,4,0));
        headerRow.setBackground(PANEL_BG); headerRow.setBorder(new EmptyBorder(4,8,0,8));
        for (String u:unitLabels) {
            JLabel l=new JLabel(u,SwingConstants.CENTER);
            l.setFont(new Font("SansSerif",Font.PLAIN,10)); l.setForeground(TEXT_DIM);
            headerRow.add(l);
        }
        intervalSpinnersPanel=new JPanel(new GridLayout(1,6,4,0));
        intervalSpinnersPanel.setBackground(PANEL_BG); intervalSpinnersPanel.setBorder(new EmptyBorder(2,8,6,8));
        spHours=sp(0,0,23); spMinutes=sp(0,0,59); spSeconds=sp(0,0,59);
        spTenths=sp(1,0,9); spHundredths=sp(0,0,9); spThousandths=sp(0,0,9);
        for (JSpinner s:new JSpinner[]{spHours,spMinutes,spSeconds,spTenths,spHundredths,spThousandths})
            intervalSpinnersPanel.add(s);
        p.add(headerRow); p.add(intervalSpinnersPanel);
        JSeparator sep = new JSeparator(); sep.setForeground(BORDER_C); p.add(sep);

        JPanel grid=new JPanel(new GridLayout(3,2,8,8));
        grid.setBackground(PANEL_BG); grid.setBorder(new EmptyBorder(6,6,6,6));
        grid.add(lbl("Mouse Button:")); mouseButtonCombo=combo(new String[]{"Left","Right","Middle"}); grid.add(mouseButtonCombo);
        grid.add(lbl("Click Type:")); clickTypeCombo=combo(new String[]{"Single","Double"}); grid.add(clickTypeCombo);
        grid.add(lbl("Max Clicks (0=∞):")); maxClicksField=field("0"); grid.add(maxClicksField);
        p.add(grid);

        JPanel hint=new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        hint.setBackground(PANEL_BG);
        smartIntervalLabel=new JLabel("Smart Interval off — set Clicks > 1 in any row to activate");
        smartIntervalLabel.setFont(new Font("SansSerif",Font.ITALIC,10));
        smartIntervalLabel.setForeground(TEXT_DIM);
        hint.add(smartIntervalLabel); p.add(hint);
        return p;
    }

    private JPanel buildPointsTable() {
        JPanel p=new JPanel(new BorderLayout(6,6));
        p.setBackground(PANEL_BG);
        p.setBorder(titledBorder("Click Points  (empty = click at cursor)"));

        tableModel=new DefaultTableModel(new String[]{"#","X","Y","Clicks","Sub-Delay(ms)","Delay-After(ms)"},0){
            public boolean isCellEditable(int r,int c){ return c>0; }
        };
        pointsTable=new JTable(tableModel);
        pointsTable.setFont(new Font("SansSerif",Font.PLAIN,12));
        pointsTable.setRowHeight(24);
        pointsTable.setBackground(new Color(32,32,44));
        pointsTable.setForeground(TEXT);
        pointsTable.setGridColor(new Color(45,45,60));
        pointsTable.setSelectionBackground(new Color(50,80,140));
        pointsTable.setSelectionForeground(Color.WHITE);
        pointsTable.getTableHeader().setBackground(new Color(35,35,50));
        pointsTable.getTableHeader().setForeground(TEXT_DIM);
        pointsTable.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,10));
        int[] cw={22,50,50,45,95,105};
        for(int i=0;i<cw.length;i++) pointsTable.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);

        tableModel.addTableModelListener(e -> {
            if(e.getColumn()==3||e.getColumn()==TableModelEvent.ALL_COLUMNS)
                SwingUtilities.invokeLater(this::refreshSmartInterval);
        });

        JScrollPane scroll=new JScrollPane(pointsTable);
        scroll.setPreferredSize(new Dimension(300,110));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C));
        scroll.getViewport().setBackground(new Color(32,32,44));

        JPanel btns=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
        btns.setBackground(PANEL_BG);
        JButton addBtn=actionBtn("+ Add Point",new Color(50,100,160));
        JButton remBtn=actionBtn("✕ Remove",   new Color(130,40,40));
        JButton clrBtn=actionBtn("Clear All",  new Color(55,55,75));
        addBtn.addActionListener(e -> {
            Point m=MouseInfo.getPointerInfo().getLocation();
            long iv=getIntervalMs(); int row=tableModel.getRowCount();
            tableModel.addRow(new Object[]{row+1,m.x,m.y,1,iv,iv});
            refreshSmartInterval();
        });
        remBtn.addActionListener(e -> {
            int row=pointsTable.getSelectedRow();
            if(row>=0){ tableModel.removeRow(row); refreshRowNumbers(); refreshSmartInterval(); }
        });
        clrBtn.addActionListener(e -> { tableModel.setRowCount(0); refreshSmartInterval(); });
        btns.add(addBtn); btns.add(remBtn); btns.add(clrBtn);
        p.add(scroll,BorderLayout.CENTER); p.add(btns,BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildStatusPanel() {
        JPanel p=new JPanel(new GridLayout(2,1,4,4));
        p.setBackground(PANEL_BG); p.setBorder(titledBorder("Status"));
        statusLabel=new JLabel("● Idle");
        statusLabel.setFont(new Font("SansSerif",Font.BOLD,13));
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setBorder(new EmptyBorder(2,8,0,0));
        clickCountLabel=new JLabel("Clicks: 0");
        clickCountLabel.setFont(new Font("SansSerif",Font.PLAIN,12));
        clickCountLabel.setForeground(TEXT_DIM);
        clickCountLabel.setBorder(new EmptyBorder(0,8,4,0));
        p.add(statusLabel); p.add(clickCountLabel);
        return p;
    }

    private JPanel buildButtons() {
        JPanel p=new JPanel(new FlowLayout(FlowLayout.CENTER,16,0));
        p.setBackground(BG);

        JButton startBtn=new JButton("▶  Start");
        startBtn.setPreferredSize(new Dimension(130,42)); startBtn.setFont(new Font("SansSerif",Font.BOLD,14));
        startBtn.setBackground(new Color(40,160,80)); startBtn.setForeground(Color.WHITE); startBtn.setOpaque(true);
        startBtn.setFocusPainted(false);
        startBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(20,110,50),2),BorderFactory.createEmptyBorder(6,16,6,16)));
        startBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startBtn.addActionListener(e -> startClicking());

        JButton stopBtn=new JButton("■  Stop");
        stopBtn.setPreferredSize(new Dimension(130,42)); stopBtn.setFont(new Font("SansSerif",Font.BOLD,14));
        stopBtn.setBackground(new Color(180,50,50)); stopBtn.setForeground(Color.WHITE); stopBtn.setOpaque(true);
        stopBtn.setFocusPainted(false);
        stopBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(120,20,20),2),BorderFactory.createEmptyBorder(6,16,6,16)));
        stopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopBtn.addActionListener(e -> stopClicking());

        p.add(startBtn); p.add(stopBtn);
        return p;
    }

    public void startClicking() {
        if (running) return;
        running = true; clickCount = 0;
        updateStatus("● Running", new Color(40,200,80));

        List<int[]> points=new ArrayList<>();
        for(int i=0;i<tableModel.getRowCount();i++){
            try { points.add(new int[]{
                Integer.parseInt(tableModel.getValueAt(i,1).toString()),
                Integer.parseInt(tableModel.getValueAt(i,2).toString()),
                Integer.parseInt(tableModel.getValueAt(i,3).toString()),
                Integer.parseInt(tableModel.getValueAt(i,4).toString()),
                Integer.parseInt(tableModel.getValueAt(i,5).toString())});
            } catch(Exception ignored){}
        }
        long maxClicks; try{ maxClicks=Long.parseLong(maxClicksField.getText().trim()); }catch(Exception e){ maxClicks=0; }
        int btn=mouseButtonCombo.getSelectedIndex();
        boolean dbl=clickTypeCombo.getSelectedIndex()==1;

        engine=new SimpleClickEngine(points,getIntervalMs(),maxClicks,btn,dbl,true,1,null);
        engine.setClickCallback(total -> {
            clickCount=total;
            SwingUtilities.invokeLater(() -> {
                clickCountLabel.setText("Clicks: "+total);
                clickCountLabel.setForeground(new Color(80,200,120));
            });
        });
        engineThread=new Thread(engine); engineThread.setDaemon(true); engineThread.start();
        new Thread(() -> {
            while(running && engineThread.isAlive()) { try { Thread.sleep(200); } catch(InterruptedException ignored){} }
            stopClicking();
        }).start();
    }

    public void stopClicking() {
        running=false;
        if(engine!=null) engine.stop();
        SwingUtilities.invokeLater(() -> updateStatus("● Stopped", new Color(200,60,60)));
    }

    private long getIntervalMs() {
        long h=((Number)spHours.getValue()).longValue(), m=((Number)spMinutes.getValue()).longValue();
        long s=((Number)spSeconds.getValue()).longValue(), t=((Number)spTenths.getValue()).longValue();
        long hu=((Number)spHundredths.getValue()).longValue(), th=((Number)spThousandths.getValue()).longValue();
        return Math.max(1,h*3_600_000L+m*60_000L+s*1_000L+t*100L+hu*10L+th);
    }

    private void refreshSmartInterval() {
        boolean active=false;
        for(int i=0;i<tableModel.getRowCount();i++){
            try{ if(Integer.parseInt(tableModel.getValueAt(i,3).toString())>1){ active=true; break; }}catch(Exception ignored){}
        }
        if(intervalSpinnersPanel!=null) for(Component c:intervalSpinnersPanel.getComponents()) c.setEnabled(!active);
        if(smartIntervalLabel!=null){
            if(active){ smartIntervalLabel.setText("⚡ Smart Interval active"); smartIntervalLabel.setForeground(ACCENT); }
            else { smartIntervalLabel.setText("Smart Interval off — set Clicks > 1 to activate"); smartIntervalLabel.setForeground(TEXT_DIM); }
        }
    }

    private void refreshRowNumbers(){ for(int i=0;i<tableModel.getRowCount();i++) tableModel.setValueAt(i+1,i,0); }
    private void updateStatus(String t,Color c){ statusLabel.setText(t); statusLabel.setForeground(c); }

    private JSpinner sp(int v,int min,int max){
        JSpinner s=new JSpinner(new SpinnerNumberModel(v,min,max,1));
        s.setFont(new Font("SansSerif",Font.PLAIN,12)); s.setPreferredSize(new Dimension(55,28));
        s.setBackground(INPUT_BG);
        JSpinner.DefaultEditor ed=(JSpinner.DefaultEditor)s.getEditor();
        ed.getTextField().setBackground(INPUT_BG); ed.getTextField().setForeground(TEXT);
        ed.getTextField().setCaretColor(TEXT); ed.getTextField().setHorizontalAlignment(JTextField.CENTER);
        ed.getTextField().setBorder(BorderFactory.createLineBorder(INPUT_BD));
        return s;
    }
    private JLabel lbl(String t){ JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.PLAIN,12)); l.setForeground(TEXT); return l; }
    private JTextField field(String v){
        JTextField f=new JTextField(v,8); f.setFont(new Font("SansSerif",Font.PLAIN,12));
        f.setBackground(INPUT_BG); f.setForeground(TEXT); f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INPUT_BD),BorderFactory.createEmptyBorder(3,6,3,6)));
        return f;
    }
    private JComboBox<String> combo(String[] items){
        JComboBox<String> c=new JComboBox<>(items); c.setFont(new Font("SansSerif",Font.PLAIN,12));
        c.setBackground(INPUT_BG); c.setForeground(TEXT); return c;
    }
    private JButton actionBtn(String text,Color bg){
        JButton b=new JButton(text); b.setFont(new Font("SansSerif",Font.PLAIN,11));
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    private TitledBorder titledBorder(String t){
        return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_C),t,
            TitledBorder.LEFT,TitledBorder.TOP,new Font("SansSerif",Font.BOLD,11),TEXT_DIM);
    }
}
