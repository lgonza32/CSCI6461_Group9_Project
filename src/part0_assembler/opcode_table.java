package part0_assembler;
import java.util.HashMap;
import java.util.Map;

/**
 * This class maps assembly instruction mnemonics aligned with
 * the ISA document given.
 * - Opcode values given in octal.
 * - Java supports octal integer literals by using leading 0.
 * - This class only performs a lookup.
 */
public final class opcode_table {
   /**
    * Mapping:
    * - key = instruction mnemonic (string)
    * - value = opcode (int)
    */
    private final Map<String, Integer> map = new HashMap<>();
   /**
    * Constructor
    * - Populate opcode table given from ISA document
    */
    public opcode_table() {
        // Misc instructions
        map.put("HLT",000);
        // map.put("TRAP",030); // for part III

        // Load/Store instructions
        map.put("LDR",001); // Load Register From Memory
        map.put("STR",002); // Store Register To Memory
        map.put("LDA",003); // Load Register with Address
        map.put("LDX",041); // Load Index Register from Memory
        map.put("STX",042); // Store Index Register to Memory

        // Transfer instructions
        map.put("JZ",010);   // Jump If Zero
        map.put("JNE",011);  // Jump If Not Equal
        map.put("JCC",012);  // Jump If Condition Code
        map.put("JSR",014);  // Jump and Save Return Address
        map.put("RFS",015);  // Return From Subroutine
        map.put("SOB",016);  // Subtract One and Branch
        map.put("JGE",017);  // Jump Greater Than or Equal To

        // Arithmetic and Logical instructions
        map.put("AMR",004); // Add Memory To Register
        map.put("SMR",005); // Subtract Memory From Register
        map.put("AIR",006); // Add Immediate to Register
        map.put("SIR",007); // Subtract Immediate from Register

        // Multiply/Divide Logical operations
        map.put("MLT",070); // Multiply Register by Register
        map.put("DVD",071); // Divide Register by Register
        map.put("TRR",072); // Test the Equality of Register and Register
        map.put("AND",073); // Logical And of Register and Register
        map.put("ORR",074); // Logical Or of Register and Register
        map.put("NOT",075); // Logical Not of Register To Register

        // Shift/Rotate operations
        map.put("SRC",031); // Shift Register by Count
        map.put("RRC",032); // Rotate Register by Count

        // I/O operations
        map.put("IN",061);  // Input Character To Register from Device
        map.put("OUT",062); // Output Character to Device from Register
        map.put("CHK",063); // Check Device Status to Register

        // Floating Point Instructions/Vector operations (Implement in Part 4)
        // map.put("FADD",033);  // Floating Add Memory To Register
        // map.put("FSUB",034);  // Floating Subtract Memory From Register
        // map.put("VADD",035);  // Vector Add
        // map.put("VSUB",036);  // Vector Subtract
        // map.put("CNVRT",037); // Convert to Fixed/FloatingPoin
        // map.put("LDFR",050);  // Load Floating Register From Memory
        // map.put("STFR",051);  // Store Floating Register To Memory
    }

   /**
    * Lookup for opcode by mnemonic.
    * @param mnemonic instruction mnemonic
    * @return opcode value
    */
    public int get(String mnemonic) {
        Integer opcode = map.get(mnemonic.toUpperCase());
        if (opcode == null) {
            throw new RuntimeException("Unknown opcode: " + mnemonic);
        }
        return opcode;
    }
}
