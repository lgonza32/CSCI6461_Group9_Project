package part0_assembler.tests;
import part0_assembler.opcode_table;

public final class opcode_table_test {
    public static void main(String[] args) {
        opcode_table table = new opcode_table();
        System.out.println("HLT test (octal): " + Integer.toOctalString(table.get("HLT")));

        // fail test
        // table.get("asdf");
    }
}
