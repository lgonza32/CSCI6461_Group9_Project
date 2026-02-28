package simulator.ui;

import javax.swing.*;
import java.awt.*;

/**
 * This class represents the GUI console for the simulator (front panel)
 */
public final class ConsoleFrame extends JFrame {

    /**
     * Constructor for GUI frame
     */
    public ConsoleFrame() {
        
        super("Window title"); // title
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close on exit

        // label to ensure GUI initializes
        JLabel label = new JLabel(
                "GUI successfully initialized.",
                SwingConstants.CENTER
        );
        
        add(label); // Add label
        
        // stylize GUI settings
        setSize(600, 300); // set window size
        setLocationRelativeTo(null); // center
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // font
    }
}
