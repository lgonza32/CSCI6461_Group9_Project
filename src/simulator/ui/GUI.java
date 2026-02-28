package simulator.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

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
        
        super("CSCI 6461 Machine Simulator"); // title
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close on exit
        
        // builds GUI
        // setContentPane(buildRightColumn()); // test right side
        // setContentPane(buildRegistersPanel()); // test register panels
        // setContentPane(buildProgramFilePanel()); // test file panel
        // setContentPane(buildControlsPanel()); // test control panel
        // setContentPane(buildConsoleOutputPanel()); // test console output panel
        // setContentPane(buildLeftColumn()); // test left side
        setContentPane(buildRootPanel());

        // only input binary
        applyBinaryFilter(binaryInputField, 16);


        // stylize GUI settings
        // label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // font
        setMinimumSize(new Dimension(900, 550));
        setPreferredSize(new Dimension(1100, 650)); 
        pack(); // sizes frame to preferred size
        setLocationRelativeTo(null); // center after pack
    }

    /**
     * Private fields for areas of the simulator
     */
    private final JTextArea cacheArea = new JTextArea(12, 28);
    private final JTextArea printerArea = new JTextArea(6, 28);
    private final JTextField consoleInputField = new JTextField(28);
    private final JTextField programFileField = new JTextField(40);
    private final JTextArea consoleArea = new JTextArea(16, 60);
    private final JTextField[] gprFields = new JTextField[4];   // GPR0..GPR3
    private final JTextField[] ixrFields = new JTextField[3];   // IX1..IX3
    private final JTextField pcField  = new JTextField("0", 10);
    private final JTextField marField = new JTextField("0", 10);
    private final JTextField mbrField = new JTextField("0", 10);
    private final JTextField irField  = new JTextField("0", 10);
    private final JTextField ccField  = new JTextField("0", 6);
    private final JTextField mfrField = new JTextField("0", 6);
    private final JTextField octalInputField = new JTextField("0", 8);
    private final JTextField binaryInputField = new JTextField("0000000000000000", 16);

    /**
     * Builds the overall root layout for the simulator.
     *
     * Two-column layout:
     *  - LEFT: main operator panels
     *  - RIGHT: cache/printer/console input
     *
     * @return simulator content
     */
    private JPanel buildRootPanel() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildLeftColumn(),
                buildRightColumn()
        );

        // keeps the right side visible and lets window resizing favor the left side
        split.setResizeWeight(0.75);                       // 75% left, 25% right
        split.setDividerLocation(0.75);     // initial divider position
        split.setOneTouchExpandable(true);

        root.add(split, BorderLayout.CENTER);
        return root;
    }

    /**
     * Builds the left column of the GUI
     * Contains the registers, controls, program files
     * 
     * @return left side of GUI
     */
    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;

        // registers
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0;
        left.add(buildRegistersPanel(), c);

        // binary/Octal Input (switch register)
        c.gridy = 1;
        c.weighty = 0;
        left.add(buildSwitchInputPanel(), c);

        // controls
        c.gridy = 2;
        c.weighty = 0;
        left.add(buildControlsPanel(), c);

        // program file path
        c.gridy = 3;
        c.weighty = 0;
        left.add(buildProgramFilePanel(), c);

        // console output
        c.gridy = 4;
        c.weighty = 1;
        left.add(buildConsoleOutputPanel(), c);

        return left;
    }

    /**
     * Builds the right column of the GUI
     * Contains cache, printer, and console input areas
     * 
     * @return right side of GUI
     */
    private JPanel buildRightColumn() {

        JPanel right = new JPanel(new GridBagLayout());

        // prevents panel from collapse
        right.setMinimumSize(new Dimension(380, 600));
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
     * ==================================
     * SECTION FOR INDIVIDUAL PANELS
     * ==================================
     */

    /**
     * Console input panel
     * @return input panel
     */
    private JPanel buildConsoleInputPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(new TitledBorder("Console Input"));

        p.add(consoleInputField, BorderLayout.CENTER);
        return p;    
    }

    /**
     * Printer output panel
     * @return printer panel
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
     * Execution controls panel.
     * @returns control panel buttons and fields
     */
    private JPanel buildControlsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setBorder(new TitledBorder("Controls"));

        JButton ipl = new JButton("IPL");
        JButton run = new JButton("Run");
        JButton step = new JButton("Single Step");
        JButton halt = new JButton("Halt");
        JButton reset = new JButton("Reset");

        p.add(ipl);
        p.add(run);
        p.add(step);
        p.add(halt);
        p.add(reset);

        return p;
    }

    /**
     * Program file panel.
     * Future behavior: IPL will set this text field to the selected file path.
     */
    private JPanel buildProgramFilePanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(new TitledBorder("Program File"));

        programFileField.setEditable(false);
        programFileField.setText("");

        p.add(programFileField, BorderLayout.CENTER);
        return p;
    }

    /**
     * Registers panel
     * @return register panels
     */
    private JPanel buildRegistersPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBorder(new TitledBorder("Registers"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;

        // GPR block (left)
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        outer.add(buildGprPanel(), c);

        // IXR block (middle)
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        outer.add(buildIxrPanel(), c);

        // Special registers block (right)
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 1;
        outer.add(buildSpecialRegPanel(), c);

        return outer;
    }

    /**
     * GPR block: displays GPR0..GPR3 in a compact grouped layout.
     * 
     * @return GPR block
     */
    private JPanel buildGprPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("GPR"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        for (int i = 0; i < 4; i++) {
            // label "0/1/2/3" like the reference GUI
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            p.add(new JLabel(String.valueOf(i)), c);

            // text field for register value
            gprFields[i] = new JTextField("0", 12);
            gprFields[i].setEditable(false);
            gprFields[i].setHorizontalAlignment(SwingConstants.RIGHT);

            c.gridx = 1;
            c.weightx = 1;
            p.add(gprFields[i], c);
        }

        return p;
    }

    /**
     * IXR block: displays IX1..IX3 in a compact grouped layout.
     * 
     * @return IXR block
     */
    private JPanel buildIxrPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("IXR"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        for (int i = 0; i < 3; i++) {
            int ixNum = i + 1;

            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            p.add(new JLabel(String.valueOf(ixNum)), c);

            ixrFields[i] = new JTextField("0", 12);
            ixrFields[i].setEditable(false);
            ixrFields[i].setHorizontalAlignment(SwingConstants.RIGHT);

            c.gridx = 1;
            c.weightx = 1;
            p.add(ixrFields[i], c);
        }

        return p;
    }

    /**
     * Special registers block:
     *  - PC, MAR, MBR, IR (main special regs)
     *  - CC and MFR (small fields like the reference)
     * 
     * @return special register panel
     */
    private JPanel buildSpecialRegPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Special"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Make special fields read-only + right aligned
        setupReadOnlyField(pcField);
        setupReadOnlyField(marField);
        setupReadOnlyField(mbrField);
        setupReadOnlyField(irField);
        setupReadOnlyField(ccField);
        setupReadOnlyField(mfrField);

        int row = 0;
        row = addLabeledField(p, c, row, "PC", pcField);
        row = addLabeledField(p, c, row, "MAR", marField);
        row = addLabeledField(p, c, row, "MBR", mbrField);
        row = addLabeledField(p, c, row, "IR", irField);

        // Add a little spacing
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        p.add(Box.createVerticalStrut(8), c);
        c.gridwidth = 1;

        row = addLabeledField(p, c, row, "CC", ccField);
        row = addLabeledField(p, c, row, "MFR", mfrField);

        return p;
    }

    /**
     * Cache display panel
     * 
     * @return cache panel
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

    /**
     * Builds the Binary + Octal input panel.
     *
     * Reference GUI expects a binary input area and an octal input area.
     *
     * Input conventions:
     *  - binaryInputField: 16-bit string
     *  - octalInputField: octal number (e.g., 12)
     */
    private JPanel buildSwitchInputPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBorder(new TitledBorder("Binary / Octal Input"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // ---- Binary row ----
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        outer.add(new JLabel("BINARY:"), c);

        binaryInputField.setHorizontalAlignment(SwingConstants.LEFT);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        outer.add(binaryInputField, c);

        // ---- Octal row ----
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        outer.add(new JLabel("OCTAL INPUT:"), c);

        octalInputField.setHorizontalAlignment(SwingConstants.RIGHT);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        outer.add(octalInputField, c);

        return outer;
    }


    /** ==================================
     * SECTION FOR UTILITIES/DEBUGGING
     * ===================================
    */

    /**
     * Ensures consistent style for register display fields.
     */
    private void setupReadOnlyField(JTextField tf) {
        tf.setEditable(false);
        tf.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    /**
     * Adds a label + field row to a GridBag panel.
     * @return label+field row
     */
    private int addLabeledField(JPanel p, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        p.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        p.add(field, c);

        return row + 1;
    }

    /**
     * Console output panel. Helps in debugging.
     * @return console output panel
     */
    private JScrollPane buildConsoleOutputPanel() {
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(consoleArea);
        sp.setBorder(new TitledBorder("Console Output"));
        return sp;
    }

    /**
     * Restricts a JTextField to accept only '0' and '1' characters,
     * and limits total length to maxBits.
     *
     * Used for the 16-bit switch register binary input.
     */
    private void applyBinaryFilter(JTextField field, int maxBits) {
        AbstractDocument doc = (AbstractDocument) field.getDocument();
        doc.setDocumentFilter(new DocumentFilter() {

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string == null) return;
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null) return;

                // Only allow 0/1 characters
                if (!text.matches("[01]*")) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                // Enforce max length
                int currentLen = fb.getDocument().getLength();
                int newLen = currentLen - length + text.length();
                if (newLen > maxBits) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                super.replace(fb, offset, length, text, attrs);
            }
        });
    }
}
