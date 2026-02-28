package simulator.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import java.awt.*;

/**
 * This class represents the GUI console for the simulator (front panel).
 * Implemented libraries:
 *  - Java Swing
 *  - Java AWT
 * 
 * Responsible for graphics of the simulator.
 */
public final class GUI extends JFrame {

    /**
     * Constructor for GUI frame
     */
    public GUI() {
        
        super("Window title"); // title
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close on exit
        
        // builds GUI
        setContentPane(buildRightColumn());

        // stylize GUI settings
        setLocationRelativeTo(null); // center
        // label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // font
        pack(); // sizes frame to preferred size
        setMinimumSize(new Dimension(1100, 650));
        setPreferredSize(new Dimension(1100, 650)); 
    }

    /**
     * Private fields for areas of the simulator
     */
    private final JTextArea cacheArea = new JTextArea(12, 28);
    private final JTextArea printerArea = new JTextArea(6, 28);
    private final JTextField consoleInputField = new JTextField(28);
   
    /** 
     * SECTION FOR RIGHT SIDE OF SIMULATOR
    */
    
    /**
     * Builds the right column of the GUI
     * 
     * Contains cache, printer, and console input areas
     * @return right side of console
     */
    private JPanel buildRightColumn() {

        JPanel right = new JPanel(new GridBagLayout());

        // prevents panel from collapse
        right.setPreferredSize(new Dimension(420, 600));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;

        // cache display
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        right.add(buildCachePanel(), c);

        // Printer display
        c.gridy = 1;
        c.weighty = 0;
        right.add(buildPrinterPanel(), c);

        // console input
        c.gridy = 2;
        right.add(buildConsoleInputPanel(), c);

        return right;
    }

    /**
     * Console input panel
     */
    private JPanel buildConsoleInputPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(new TitledBorder("Console Input"));

        p.add(consoleInputField, BorderLayout.CENTER);
        return p;    
    }

    /**
     * Printer output panel
     */
    private JPanel buildPrinterPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder("Printer"));

        printerArea.setEditable(false);
        printerArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        printerArea.setText("(printer output placeholder)\n");

        p.add(new JScrollPane(printerArea), BorderLayout.CENTER);
        return p;
    }

    /**
     * Cache display panel
     */
    private JPanel buildCachePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder("Cache Content"));

        cacheArea.setEditable(false);
        cacheArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        cacheArea.setText("(cache display placeholder)\n");

        p.add(new JScrollPane(cacheArea), BorderLayout.CENTER);
        return p;
    }
}
