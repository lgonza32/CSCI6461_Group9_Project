package part0_assembler;
import java.util.HashMap;
import java.util.Map;

public final class opcode_table {
    
    private final Map<String, Integer> map = new HashMap<>();

    public opcode_table() {
        // Misc instructions
        map.put("HLT",00);
        // map.put("TRAP",30); // for part III

        // Load/Store instructions
        map.put("LDR",01); // Load Register From Memory
        map.put("STR",02); // Store Register To Memory
        map.put("LDA",03); // Load Register with Address
        map.put("LDX",41); // Load Index Register from Memory
        map.put("STX",42); // Store Index Register to Memory

        // Transfer instructions
        map.put("JZ",10);   // Jump If Zero
        map.put("JNE",11);  // Jump If Not Equa
        map.put("JCC",12);  // Jump If Condition Code
        map.put("JSR",14);  // Jump and Save Return Addres
        map.put("RFS",15);  // Return From Subroutine
        map.put("SOB",16);  // Subtract One and Branch
        map.put("JGE",17);  // Jump Greater Than or Equal To

        // Arithmetic and Logical instructions
        map.put("AMR",04); // Add Memory To Register
        map.put("SMR",05); // Subtract Memory From Register
        map.put("AIR",06); // Add Immediate to Register
        map.put("SIR",07); // Subtract Immediate from Register

        // Multiply/Divide Logical operations
        map.put("MLT",70); // Multiply Register by Register
        map.put("DVD",71); // Divide Register by Register
        map.put("TRR",72); // Test the Equality of Register and Register
        map.put("AND",73); // Logical And of Register and Register
        map.put("ORR",74); // Logical Or of Register and Register
        map.put("NOT",75); // Logical Not of Register To Register

        // Shift/Rotate operations
        map.put("SRC",31); // Shift Register by Count
        map.put("RRC",32); // Rotate Register by Count

        // I/O operations
        map.put("IN",31);  // Input Character To Register from Device
        map.put("OUT",31); // Output Character to Device from Register
        map.put("CHK",31); // Check Device Status to Register

        // FLoating Point Instructions/Vector operations (Implement in Part 4)
        // map.put("FADD",33);  // Floating Add Memory To Register
        // map.put("FSUB",34);  // Floating Subtract Memory From Register
        // map.put("VADD",35);  // Vector Add
        // map.put("VSUB",36);  // Vector Subtract
        // map.put("CNVRT",37); // Convert to Fixed/FloatingPoin
        // map.put("LDFR",50);  // Load Floating Register From Memory
        // map.put("STFR",51);  // Store Floating Register To Memory
    }

    public int get(String mnemonic) {
        Integer opcode = map.get(mnemonic.toUpperCase());
        if (opcode == null) {
            throw new RuntimeException("Unknown opcode: " + mnemonic);
        }
        return opcode;
    }
}
