package part0_assembler.tests;
import part0_assembler.*;
import java.util.List;
import java.util.Arrays;

public final class encoder_test {
    public static void main(String[] args) {
        Encoder e = new Encoder();
        List<String> operands = Arrays.asList("1", "2", "10");
        int word1 = e.encodeFormat("LDR", operands);
    }
}
