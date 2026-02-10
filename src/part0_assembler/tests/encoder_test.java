package part0_assembler.tests;
import part0_assembler.*;
import java.util.List;
import java.util.Arrays;

public final class encoder_test {
    public static void main(String[] args) {
        Encoder enc = new Encoder();
        // Using example provided in ISA
        List<String> goodLDR = Arrays.asList("3", "0", "31");
        int test0 = enc.encodeFormat("LDR", goodLDR);
        String test0_binary = String.format("%16s",
                Integer.toBinaryString(test0)).replace(' ', '0');
        
        String test0_opcodeBits = test0_binary.substring(0, 6);
        String test0_rBits = test0_binary.substring(6, 8);
        String test0_ixBits = test0_binary.substring(8, 10);
        String test0_iBit = test0_binary.substring(10, 11);
        String test0_addrBits = test0_binary.substring(11, 16);

        // verify encoder produces correctly as format
        System.out.println("-----TESTS FOR ENCODER-----");
        System.out.println("Instruction:       LDR 3,0,31");
        System.out.println("Encoded (decimal): " + test0);
        System.out.println("Encoded (octal):   " + Integer.toOctalString(test0));
        System.out.println("Encoded (binary):  " + test0_binary);
        System.out.println("Field breakdown:");
        System.out.println("  opcode : " + test0_opcodeBits);
        System.out.println("  r      : " + test0_rBits);
        System.out.println("  ix     : " + test0_ixBits);
        System.out.println("  I      : " + test0_iBit);
        System.out.println("  addr   : " + test0_addrBits);

        // Indirect adressing test
        System.out.println("-----TEST FOR I BIT-----");
        List<String> ibitLDR = Arrays.asList("3", "0", "31", "1");
        int test_ibit = enc.encodeFormat("LDR", ibitLDR);
        String test_ibit_binary = String.format("%16s",
                Integer.toBinaryString(test_ibit)).replace(' ', '0');

        String test_ibit_opcodeBits = test_ibit_binary.substring(0, 6);
        String test_ibit_rBits = test_ibit_binary.substring(6, 8);
        String test_ibit_ixBits = test_ibit_binary.substring(8, 10);
        String test_ibit_iBit = test_ibit_binary.substring(10, 11);
        String test_ibit_addrBits = test_ibit_binary.substring(11, 16);

        System.out.println("Instruction:      LDR 3,0,31,1 (indirect)");
        System.out.println("Encoded (binary): " + test_ibit_binary);
        System.out.println("Field breakdown:");
        System.out.println("  opcode : " + test_ibit_opcodeBits);
        System.out.println("  r      : " + test_ibit_rBits);
        System.out.println("  ix     : " + test_ibit_ixBits);
        System.out.println("  I      : " + test_ibit_iBit + "  <-- indirect bit");
        System.out.println("  addr   : " + test_ibit_addrBits);

        System.out.println("-----TESTS FOR EXCEPTIONS HANDLING-----");
        // verify bad inputs are rejected
        List<String> badLDR1 = Arrays.asList("4", "0", "10");
        List<String> badLDR2 = Arrays.asList("1", "0", "32");
        List<String> badLDR3 = Arrays.asList("1", "2");

        // R is out of range (valid: 0..3)
        try {
            int test1 = enc.encodeFormat("LDR", badLDR1);
            System.out.println("ERROR: Expected exception for invalid R, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (invalid R): " + e.getMessage());
        }

        // Address is out of range (valid: 0..31)
        try {
            int test2 = enc.encodeFormat("LDR", badLDR2);
            System.out.println("ERROR: Expected exception for invalid address, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (invalid address): " + e.getMessage());
        }

        // Wrong operand count
        try {
            int test3 = enc.encodeFormat("LDR", badLDR3);
            System.out.println("ERROR: Expected exception for bad operand count, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (bad operand count): " + e.getMessage());
        }
    }
}
