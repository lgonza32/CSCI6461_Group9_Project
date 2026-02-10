import part0_assembler.tests.opcode_table_test;
import part0_assembler.tests.encoder_test;

/**
 * Single entry point for Part 0 JAR.
 * Runs the basic verification tests for opcode table + encoder.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("----OPCODE TABLE TEST----");
        opcode_table_test.main(args);
        System.out.println("----ENCODING TEST-----");
        encoder_test.main(args);
    }
}
