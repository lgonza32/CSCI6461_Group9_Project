package part0_assembler;
import java.util.List;

/**
 * This class is responsible for converting supported assembly instructions
 * into their 16-bit instruction representation.
 * - Should support all referenced instructions from ISA
 * - Assembler encodes these fields.
 */
public final class Encoder {

    /** Opcode lookup table */
    public final opcode_table table = new opcode_table();

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
        opcode &= 0x3F;
        r &= 0x03;   
        ix &= 0x03; 
        i &= 0x01;  
        addr &= 0x1F;

        // Shift each field into position and combine with bitwise OR
        return (opcode << 10)
                | (r << 8)
                | (ix << 6)
                | (i << 5)
                | addr;
    }

    /* ==========================
     * Encoding Format Functions
     * ========================== */

    /**
     * Encode a basic-format instruction with optional indirect addressing.
     *
     * Format:
     * OP r,x,address[,I]
     *
     * @param mnemonic  instruction mnemonic
     * @param r         register field
     * @param ix        index register field
     * @param address   address field
     * @param indirect  indirect bit (0 or 1)
     * @return          encoded instruction word
     */
    public int encodeBasicIndirect(String mnemonic, int r, int ix, int address, int indirect) {
        int opcode = table.get(mnemonic);

        checkRange("R", r, 0, 3);
        checkRange("IX", ix, 0, 3);
        checkRange("I", indirect, 0, 1);
        checkRange("Address", address, 0, 31);

        return pack(opcode, r, ix, indirect, address);
    }

    /**
     * Encode a basic-format instruction with direct addressing.
     *
     * Format:
     * OP r,x,address
     *
     * Used by instructions such as:
     * LDR, STR, LDA, JZ, JNE, JCC, SOB, JGE, AMR, SMR
     *
     * @param mnemonic  instruction mnemonic
     * @param r         register field
     * @param ix        index register field
     * @param address   address field
     * @return          encoded instruction word
     */
    public int encodeBasic(String mnemonic, int r, int ix, int address) {
        return encodeBasicIndirect(mnemonic, r, ix, address, 0);
    }

    /**
     * Encode an x,address-format instruction with optional indirect addressing.
     *
     * Format:
     * OP x,address[,I]
     *
     * The r field is unused in this format, so it is packed as 0.
     * The x register number is packed into the ix field.
     *
     * @param mnemonic  instruction mnemonic
     * @param x         index register number
     * @param address   address field
     * @param indirect  indirect bit (0 or 1)
     * @return          encoded instruction word
     */
    public int encodeXAddressIndirect(String mnemonic, int x, int address, int indirect) {
        int opcode = table.get(mnemonic);

        checkRange("X", x, 0, 3);
        checkRange("I", indirect, 0, 1);
        checkRange("Address", address, 0, 31);

        return pack(opcode, 0, x, indirect, address);
    }

    /**
     * Encode an x,address-format instruction with direct addressing.
     *
     * Format:
     * OP x,address
     *
     * Used by:
     * LDX, STX, JMA, JSR
     *
     * @param mnemonic  instruction mnemonic
     * @param x         index register number
     * @param address   address field
     * @return          encoded instruction word
     */
    public int encodeXAddress(String mnemonic, int x, int address) {
        return encodeXAddressIndirect(mnemonic, x, address, 0);
    }

    /**
     * Encode an immediate-format instruction.
     *
     * Format:
     * OP r,immed
     *
     * Used by:
     * AIR, SIR
     *
     * ix and i are packed as 0.
     *
     * @param mnemonic  instruction mnemonic
     * @param r         register field
     * @param immed     immediate value
     * @return          encoded instruction word
     */
    public int encodeImmediate(String mnemonic, int r, int immed) {
        int opcode = table.get(mnemonic);

        checkRange("R", r, 0, 3);
        checkRange("Immediate", immed, 0, 31);

        return pack(opcode, r, 0, 0, immed);
    }

    /**
     * Encode an RFS instruction.
     *
     * Format:
     * RFS immed
     *
     * r, ix, and i are unused and packed as 0.
     *
     * @param immed immediate return code
     * @return      encoded instruction word
     */
    public int encodeRFS(int immed) {
        int opcode = table.get("RFS");

        checkRange("Immediate", immed, 0, 31);

        return pack(opcode, 0, 0, 0, immed);
    }

    
    /**
     * Encode a register-to-register instruction.
     *
     * Format:
     * OP rx,ry
     *
     * Used by:
     * MLT, DVD, TRR, AND, ORR
     *
     * rx is packed into the r field.
     * ry is packed into the ix field.
     *
     * @param mnemonic  instruction mnemonic
     * @param rx        first register
     * @param ry        second register
     * @return          encoded instruction word
     */
    public int encodeRegReg(String mnemonic, int rx, int ry) {
        int opcode = table.get(mnemonic);

        checkRange("RX", rx, 0, 3);
        checkRange("RY", ry, 0, 3);

        return pack(opcode, rx, ry, 0, 0);
    }

    
    /**
     * Encode a NOT instruction.
     *
     * Format:
     * NOT rx
     *
     * @param rx    register to invert
     * @return      encoded instruction word
     */
    public int encodeNot(int rx) {
        int opcode = table.get("NOT");

        checkRange("RX", rx, 0, 3);

        return pack(opcode, rx, 0, 0, 0);
    }

    /**
     * Encode a shift/rotate instruction.
     *
     * Format:
     * OP r,count,L/R,A/L
     *
     * Packing used here:
     * - r     -> r field
     * - A/L   -> low bit of ix field
     * - L/R   -> i field
     * - count -> addr field
     *
     * With the current CPU decoder, bit 6 of the ix field is unused for
     * this format, so only the low bit is used for A/L.
     *
     * @param mnemonic  instruction mnemonic
     * @param r         target register
     * @param count     shift/rotate count
     * @param lr        left/right bit
     * @param al        arithmetic/logical bit
     * @return          encoded instruction word
     */
    public int encodeShiftRotate(String mnemonic, int r, int count, int lr, int al) {
        int opcode = table.get(mnemonic);

        checkRange("R", r, 0, 3);
        checkRange("Count", count, 0, 31);
        checkRange("L/R", lr, 0, 1);
        checkRange("A/L", al, 0, 1);

        // Store A/L in the low bit of the ix field.
        int ixField = al;

        return pack(opcode, r, ixField, lr, count);
    }

    /**
     * Encode an I/O instruction.
     *
     * Format:
     * OP r,devid
     *
     * Used by:
     * IN, OUT, CHK
     *
     * ix and i are packed as 0.
     *
     * @param mnemonic  instruction mnemonic
     * @param r         register field
     * @param deviceId  device identifier
     * @return          encoded instruction word
     */
    public int encodeIO(String mnemonic, int r, int deviceId) {
        int opcode = table.get(mnemonic);

        checkRange("R", r, 0, 3);
        checkRange("Device ID", deviceId, 0, 31);

        return pack(opcode, r, 0, 0, deviceId);
    }

    /**
     * Encodes basic instruction format (OP r, x, address[,I]) and
     * converts instruction into 16-bit code.
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
        
        return encodeBasicIndirect(mnemonic, r, ix, a, i);
    }

    /* ==========================
     * Helpers
     * ========================== */

    /**
     * Shared field-range validator.
     *
     * @param fieldName name of the field being validated
     * @param value actual value supplied
     * @param min minimum valid value
     * @param max maximum valid value
     */
    private void checkRange(String fieldName, int value, int min, int max) {
        if (value < min || value > max) {
            throw new RuntimeException(fieldName + " out of range: " + value);
        }
    }

}
