package part0_assembler;
import java.util.*;

/**
 * This class is responsible for converting supported assembly instructions
 * into their 16-bit instruction representation.
 * 
 * This does not support all ISA instruction formats, and only supports
 * the basic instruction format as referenced in Load/Store Instruction.
 * (additional formats supported in parts 2 and 3 of project)
 * 
 * Supported Basic Instruction Format:
 *      OP r, x, address[,I]
 *      opcode:6 | r:2 | ix:2 | I:1 | address: 5
 * 
 * Assembler encodes these fields.
 */
public final class Encoder {

    /** Opcode lookup table */
    private final Opcode_table op_table = new Opcode_table();

    /**
     * Pack individual instruction fields into 16-bits.
     * This method implements the bit layout shown in the ISA.
     * Each field is masked to its legal bit-width to prevent overflow
     * or accidental corruption of neighboring fields.
     * 
     * @param opcode opcode value (6 bits -> bits 15-10)
     * @param r      register field (2 bits -> bits 9-8)
     * @param ix     index register field (2 bits -> bits 7-6)
     * @param i      indirect bit (5 bits -> bits 4-0)
     * @param addr   address field (bits 4-0)
     * @return       16-bit encoded instruction
     */
    private int pack(int opcode, int r, int ix, int i, int addr) {
        // Mask each field to its legal bit width to prevent overflow
        opcode &= 0x3F; // 6 bits
        r &= 0x03;      // 2 bits
        ix &= 0x03;     // 2 bits
        i &= 0x01;      // 1 bit
        addr &= 0x1F;   // 5 bits

        // Shift each field into position and combine with bitwise OR
        return (opcode << 10) // bits 15-10
                | (r << 8)    // bits 9-8
                | (ix << 6)   // bits 7-6
                | (i << 5)    // bit 5
                | addr;       // bits 4-0
    }

    /**
     * Encodes basic instruction format (OP r, x, address[,I]) and
     * converts instruction into 16-bit code.
     * Only numeric operands are supported right now.
     * 
     * Constraints:
     * - r:     0-3
     * - ix:    0-3 (0 means no indexing)
     * - I:     0 or 1 (defaults to 0)
     * - addr:  0-31 (5 bit field)
     * 
     * @param mnemonic          instruction mnemonic
     * @param args              operand list in decimal form
     * @return                  encoded 16-bit machine instruction word
     * @throws RuntimeException if format is out of range
     */
    public int encodeFormat(String mnemonic, List<String> args) {
        int opcode = op_table.get(mnemonic);

        // Basic instrction format requires 3-4 operands
        if (args.size() != 3 && args.size() !=4) {
            throw new RuntimeException("Expected r,x, address[,I] for " + mnemonic);
        } 

        // Parse operands
        int r = Integer.parseInt(args.get(0));
        int ix = Integer.parseInt(args.get(1));
        int i = (args.size() == 4) 
                ? Integer.parseInt(args.get(3)) 
                : 0; // default: direct addresssing
        int a = Integer.parseInt(args.get(2));
        
        // Enforce field ranges
        if (r < 0 || r > 3) throw new RuntimeException("R out of range: " + r);
        if (ix < 0 || ix > 3) throw new RuntimeException("ix out of range: " + ix);
        if (i < 0 || i > 1) throw new RuntimeException("I must be 0 or 1:");
        if (a < 0 || a > 31) throw new RuntimeException("Addr out of range: " + a);

        return pack(opcode, r, ix, i , a);
    }
}
