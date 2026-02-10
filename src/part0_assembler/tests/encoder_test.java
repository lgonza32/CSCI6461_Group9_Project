package part0_assembler.tests;
import part0_assembler.*;
import java.util.List;
import java.util.Arrays;

public final class encoder_test {
    public static void main(String[] args) {
        Encoder enc = new Encoder();
        List<String> goodLDR = Arrays.asList("1", "2", "10");
        int test0 = enc.encodeFormat("LDR", goodLDR);

        // verify encoder produces correctly as format
        System.out.println("Instruction: LDR 1,2,10");
        System.out.println("Encoded (decimal): " + test0);
        System.out.println("Encoded (octal):   " + Integer.toOctalString(test0));
        System.out.println("Encoded (binary):  " + String.format("%16s",
                Integer.toBinaryString(test0)).replace(' ', '0'));

        // verify bad inputs are rejected
        List<String> badLDR1 = Arrays.asList("4", "0", "10");
        List<String> badLDR2 = Arrays.asList("1", "0", "32");
        List<String> badLDR3 = Arrays.asList("1", "2");

        try {
            // R is out of range (valid: 0..3)
            int test1 = enc.encodeFormat("LDR", badLDR1);
            System.out.println("ERROR: Expected exception for invalid R, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (invalid R): " + e.getMessage());
        }

        try {
            // Address is out of range (valid: 0..31)
            int test2 = enc.encodeFormat("LDR", badLDR2);
            System.out.println("ERROR: Expected exception for invalid address, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (invalid address): " + e.getMessage());
        }

        try {
            // Wrong operand count
            int test3 = enc.encodeFormat("LDR", badLDR3);
            System.out.println("ERROR: Expected exception for bad operand count, but none occurred.");
        } catch (RuntimeException e) {
            System.out.println("PASS (bad operand count): " + e.getMessage());
        }
    }
}
