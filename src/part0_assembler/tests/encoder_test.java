package part0_assembler.tests;
import part0_assembler.*;
import java.util.List;
import java.util.Arrays;

public final class encoder_test {
    public static void main(String[] args) {
        Encoder e = new Encoder();
        List<String> operands = Arrays.asList("1", "2", "10");
        int word = e.encodeFormat("LDR", operands);

        System.out.println("Instruction: LDR 1,2,10");
        System.out.println("Encoded (decimal): " + word);
        System.out.println("Encoded (octal):   " + Integer.toOctalString(word));
        System.out.println("Encoded (binary):  " + String.format("%16s",
                Integer.toBinaryString(word)).replace(' ', '0'));
    }
}
