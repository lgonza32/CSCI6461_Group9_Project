package part0_assembler.tests;
import part0_assembler.opcode_table;

public final class opcode_table_test {
    public static void main(String[] args) {
        // verify opcode table construct
        opcode_table table = new opcode_table();
        System.out.println("HLT test (octal): " + Integer.toOctalString(table.get("HLT")));
        System.out.println("LDR test (octal): " + Integer.toOctalString(table.get("LDR")));
        System.out.println("STR test (octal): " + Integer.toOctalString(table.get("STR")));
        System.out.println("LDA test (octal): " + Integer.toOctalString(table.get("LDA")));
        System.out.println("LDX test (octal): " + Integer.toOctalString(table.get("LDX")));
        System.out.println("STX test (octal): " + Integer.toOctalString(table.get("STX")));

        // fail test
        try {
            table.get("asdf");
            System.out.println("ERROR: Expected exception for unknown opcode, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS " + e.getMessage());
        }
    }
}
