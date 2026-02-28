import part0_assembler.tests.opcode_table_test;
import part0_assembler.tests.encoder_test;
import simulator.ui.*;
import javax.swing.SwingUtilities;

/**
 * Single entry point for Part 0 JAR.
 * Runs the basic verification tests for opcode table + encoder.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        /** for tests */
        // System.out.println("PART 0");
        // opcode_table_test.main(args);
        // System.out.println();
        // encoder_test.main(args);

        /** launch swing UI for GUI */
        SwingUtilities.invokeLater(() -> {
            GUI frame = new GUI();
            frame.setVisible(true);
        });

    }
}
