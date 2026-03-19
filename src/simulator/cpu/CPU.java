package simulator.cpu;

import simulator.machine.Memory;
import simulator.machine.MachineState;

/**
 * CPU implements the fetch/decode/execute cycle for the simulator.
 *  - Single-step should fetch the instruction at PC, show it in IR,
 *    and execute enough opcodes to demo addressing modes.
 *
 * Implemented opcodes
 *  - HLT (000)
 *  - LDR (001)
 *  - LDA (003)
 *  - JZ  (010)
 *  - LDX (041)
 *
 * Notes:
 *  - Basic format decode: opcode(6) r(2) ix(2) I(1) addr(5)
 *  - Effective Address (EA) supports indexing + indirect for basic format.
 *  - Memory size is 2048; out-of-range triggers a halt with an error message.
 */
public final class CPU {

    private final Memory mem;
    private final MachineState s;
    private boolean halted = false;

    public CPU(Memory mem, MachineState state) {
        this.mem = mem;
        this.s = state;
    }

    public boolean isHalted() {
        return halted;
    }

    public void reset() {
        halted = false;
    }

    /**
     * Execute one full instruction step:
     *  1) FETCH (MAR<-PC, MBR<-MEM[MAR], IR<-MBR, PC++)
     *  2) DECODE/EXECUTE based on opcode
     *
     * @return a short log line describing what happened
     */
    public String step() {
        if (halted) {
            return "[STEP] CPU is halted; ignoring step.\n";
        }

        // =====================
        // 1) FETCH
        // =====================
        int pc0 = s.getPC();
        s.setMAR(pc0);

        int instr;
        try {
            instr = mem.read(pc0);
        } catch (IllegalArgumentException ex) {
            halted = true;
            return "[FAULT] Fetch address out of range: " + pc0 + "\n";
        }

        s.setMBR(instr);
        s.setIR(instr);
        s.setPC(pc0 + 1);

        // =====================
        // 2) DECODE
        // =====================
        int ir = s.getIR();
        int opcode = (ir >>> 10) & 0x3F;
        int r = (ir >>> 8) & 0x03;
        int ix = (ir >>> 6) & 0x03;
        int ind = (ir >>> 5) & 0x01;
        int addr = ir & 0x1F;

        // =====================
        // 3) EXECUTE
        // =====================
        try {
            switch (opcode) {

                // HLT (octal 000 => decimal 0)
                case 0 -> {
                    halted = true;
                    return "[STEP] FETCH @" + Memory.toOct6(pc0) +
                            " IR=" + Memory.toOct6(ir) + " (HLT)\n";
                }

                // LDR (octal 001 => decimal 1): R[r] <- MEM[EA]
                case 1 -> {
                    int ea = computeEA(ix, ind, addr);
                    int val = mem.read(ea);
                    s.setGPR(r, val);
                    return "[STEP] LDR R" + r + " <- MEM[" + Memory.toOct6(ea) + "] = " +
                            Memory.toOct6(val) + "\n";
                }

                // STR (octal 002 => decimal 2): MEM[EA] <- R[r]
                case 2 -> {
                    int ea = computeEA(ix, ind, addr);
                    int val = s.getGPR(r);
                    mem.write(ea, val);
                    return "[STEP] STR MEM[" + Memory.toOct6(ea) + "] <- R" + r
                            + " = " + Memory.toOct6(val) + "\n";
                }

                // LDA (octal 003 => decimal 3): R[r] <- EA
                case 3 -> {
                    int ea = computeEA(ix, ind, addr);
                    s.setGPR(r, ea);
                    return "[STEP] LDA R" + r + " <- EA " + Memory.toOct6(ea) + "\n";
                }

                // -------------------------------------------------
                // Transfer Instructions
                // -------------------------------------------------

                // JZ (octal 010 => decimal 8): if R[r]==0 then PC <- EA
                case 8 -> {
                    int ea = computeEA(ix, ind, addr);
                    if ((s.getGPR(r) & 0xFFFF) == 0) {
                        s.setPC(ea);
                        return "[STEP] JZ taken -> PC <- " + Memory.toOct6(ea) + "\n";
                    } else {
                        return "[STEP] JZ not taken (R" + r + " != 0)\n";
                    }
                }
                
                // JNE (octal 011 => decimal 9)
                // If R[r] != 0 then PC <- EA
                case 9 -> {
                    int ea = computeEA(ix, ind, addr);

                    if ((s.getGPR(r) & 0xFFFF) != 0) {
                        s.setPC(ea);
                        return "[STEP] JNE taken -> PC <- "
                                + Memory.toOct6(ea) + "\n";
                    } else {
                        return "[STEP] JNE not taken (R" + r + " == 0)\n";
                    }
                }

                // JCC (octal 012 => decimal 10)
                // If selected CC bit is set, then PC <- EA
                // Here, the r field is interpreted as the CC bit index:
                // r = 0..3 selects CC[0]..CC[3]
                case 10 -> {
                    int ccIndex = r;
                    int ea = computeEA(ix, ind, addr);

                    if (isCCBitSet(ccIndex)) {
                        s.setPC(ea);
                        return "[STEP] JCC taken on CC[" + ccIndex + "] -> PC <- "
                                + Memory.toOct6(ea) + "\n";
                    } else {
                        return "[STEP] JCC not taken on CC[" + ccIndex + "]\n";
                    }
                }

                // JMA (octal 013 => decimal 11)
                // Unconditional jump: PC <- EA
                // The r field is ignored for this instruction.
                case 11 -> {
                    int ea = computeEA(ix, ind, addr);
                    s.setPC(ea);

                    return "[STEP] JMA -> PC <- "
                            + Memory.toOct6(ea) + "\n";
                }

                // JSR (octal 014 => decimal 12)
                // R3 <- return address
                // PC <- EA
                // Since PC was already incremented during fetch,
                // the current PC already points to the return address.
                case 12 -> {
                    int ea = computeEA(ix, ind, addr);
                    int returnAddress = s.getPC();

                    s.setGPR(3, returnAddress);
                    s.setPC(ea);

                    return "[STEP] JSR R3 <- " + Memory.toOct6(returnAddress)
                            + ", PC <- " + Memory.toOct6(ea) + "\n";
                }

                // RFS (octal 015 => decimal 13)
                // R0 <- immed
                // PC <- R3
                // The immediate comes from the low 5-bit address field.
                // IX and I are ignored here.
                case 13 -> {
                    int immed = addr & 0x1F;
                    int returnAddress = s.getGPR(3) & 0xFFF;

                    s.setGPR(0, immed);
                    s.setPC(returnAddress);

                    return "[STEP] RFS R0 <- " + Memory.toOct6(immed)
                            + ", PC <- R3 = " + Memory.toOct6(returnAddress) + "\n";
                }

                // SOB (octal 016 => decimal 14)
                // R[r] <- R[r] - 1
                // If R[r] > 0 then PC <- EA
                case 14 -> {
                    int ea = computeEA(ix, ind, addr);

                    // Decrement first, then test the new value
                    int newVal = (s.getGPR(r) - 1) & 0xFFFF;
                    s.setGPR(r, newVal);

                    if (toSigned16(newVal) > 0) {
                        s.setPC(ea);
                        return "[STEP] SOB taken: R" + r + " <- "
                                + Memory.toOct6(newVal)
                                + ", PC <- " + Memory.toOct6(ea) + "\n";
                    } else {
                        return "[STEP] SOB not taken: R" + r + " <- "
                                + Memory.toOct6(newVal) + "\n";
                    }
                }

                
                // JGE (octal 017 => decimal 15)
                // If R[r] >= 0 then PC <- EA
                // Register values are interpreted as signed 16-bit values.
                case 15 -> {
                    int ea = computeEA(ix, ind, addr);

                    if (toSigned16(s.getGPR(r)) >= 0) {
                        s.setPC(ea);
                        return "[STEP] JGE taken -> PC <- "
                                + Memory.toOct6(ea) + "\n";
                    } else {
                        return "[STEP] JGE not taken (R" + r + " < 0)\n";
                    }
                }

                // -------------------------------------------------
                // Index Register Load / Store
                // -------------------------------------------------

                // LDX (octal 041 => decimal 33): X[ix] <- MEM[EA_no_index]
                // NOTE: For LDX, the ix field indicates which index register to load.
                case 33 -> {
                    int x = ix; // 1..3 expected
                    if (x == 0) {
                        halted = true;
                        return "[FAULT] LDX with X=0 is invalid.\n";
                    }

                    int ea = computeEA_noIndex(ind, addr);
                    int val = mem.read(ea);
                    s.setIXR(x, val);

                    return "[STEP] LDX X" + x + " <- MEM[" + Memory.toOct6(ea) + "] = " +
                            Memory.toOct6(val) + "\n";
                }

                // STX (octal 042 => decimal 34): MEM[EA] <- X[ix]
                // NOTE: For STX, the ix field indicates which index register is the source.
                case 34 -> {
                    int x = ix; // 1..3 expected
                    if (x == 0) {
                        halted = true;
                        return "[FAULT] STX with X=0 is invalid.\n";
                    }
                    int ea = computeEA_noIndex(ind, addr);
                    int val = s.getIXR(x);
                    mem.write(ea, val);
                    return "[STEP] STX MEM[" + Memory.toOct6(ea) + "] <- X" + x
                            + " = " + Memory.toOct6(val) + "\n";
                }

                // -------------------------------------------------
                // Unsupported Opcode
                // -------------------------------------------------
                default -> {
                    halted = true;
                    return "[FAULT] Unsupported opcode=" + opcode + " IR=" + Memory.toOct6(ir) + "\n";
                }
            }
        } catch (IllegalArgumentException ex) {
            // Convert helper/memory exceptions into a simulator fault instead
            // of crashing the whole program.
            halted = true;
            return "[FAULT] " + ex.getMessage() + "\n";
        }
        
        
    }

    /* ==========================
     * Helpers
     * ========================== */

    /**
     * Compute Effective Address for BASIC format instructions that support indexing.
     *
     * EA = addr + IXR[ix] (if ix != 0)
     * if I==1 then EA = MEM[EA] (use low 12 bits as address)
     * 
     * Steps:
     * 1) Start with the low 5-bit address field
     * 2) If ix != 0, add IXR[ix]
     * 3) If indirect bit is set, use MEM[EA] as the pointer
     * 4) Enforce legal memory range
     * 
     * @param ix    index register field
     * @param ind   indirect bit
     * @param addr5 low 5-bit address field
     * @return      effective address
     */
    private int computeEA(int ix, int ind, int addr5) {
        int ea = addr5;

        // Indexing
        if (ix != 0) {
            ea = (ea + (s.getIXR(ix) & 0xFFFF)) & 0xFFF;
        } else {
            ea = ea & 0xFFF;
        }

        // Indirect
        if (ind == 1) {
            int ptr = mem.read(ea);
            ea = ptr & 0xFFF;
        }

        // Bounds check vs memory size
        if (ea < 0 || ea >= Memory.SIZE) {
            throw new IllegalArgumentException("EA out of range: " + ea);
        }
        return ea;
    }

    /**
     * EA compute for LDX/STX style instructions where the ix field is the DEST register,
     * so we do not apply indexing.
     * 
     * Used by:
     * - LDX
     * - STX
     * 
     * @param ind   indirect bit
     * @param addr5 low 5-bit address field
     * @return      effective address
     */
    private int computeEA_noIndex(int ind, int addr5) {
        int ea = addr5 & 0xFFF;

        if (ind == 1) {
            int ptr = mem.read(ea);
            ea = ptr & 0xFFF;
        }

        if (ea < 0 || ea >= Memory.SIZE) {
            throw new IllegalArgumentException("EA out of range: " + ea);
        }
        return ea;
    }

    /**
     * Convert a 16-bit register value into a signed Java int.
     *
     * Examples:
     * - 0x0001 -> 1
     * - 0xFFFF -> -1
     * - 0x8000 -> -32768
     *
     * @param value raw 16-bit value
     * @return      signed 16-bit interpretation
     */
    private int toSigned16(int value) {
        return (short) (value & 0xFFFF);
    }

    /**
     * Check whether a condition-code bit is set.
     *
     * Convention used here:
     * - ccIndex = 0..3 maps to CC bit positions 0..3
     *
     * @param ccIndex CC bit index
     * @return true if the selected CC bit is 1
     */
    private boolean isCCBitSet(int ccIndex) {
        return ((s.getCC() >>> ccIndex) & 0x1) == 1;
    }
}