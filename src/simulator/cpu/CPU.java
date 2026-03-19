package simulator.cpu;

import simulator.machine.Memory;
import simulator.machine.MachineState;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * CPU implements the fetch/decode/execute cycle for the simulator.
 *
 * Non-implemented instructions
 *  - CHK
 *  - FLoating Point/Vector
 *  - Trap
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
    /** Reads one input character code from a device callback. Returns -1 if no data. */
    private final IntSupplier inputReader;
    /** Writes one output character code to a device callback. */
    private final IntConsumer outputWriter; 

    /**
     * Construct a CPU attached to memory and machine state.
     * Uses no-op I/O callbacks by default.
     *
     * @param mem   machine memory
     * @param state machine register state
     */
    public CPU(Memory mem, MachineState state) {
        this(mem, state, () -> -1, value -> {});
    }

    /**
     * Construct a CPU attached to memory, state, and I/O callbacks.
     *
     * @param mem           machine memory
     * @param state         machine register state
     * @param inputReader   callback that returns one input character code, or -1 if none
     * @param outputWriter  callback that consumes one output character code
     */
    public CPU(Memory mem, MachineState state, IntSupplier inputReader, IntConsumer outputWriter) {
        this.mem = mem;
        this.s = state;
        this.inputReader = inputReader;
        this.outputWriter = outputWriter;
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
                // Arithmetic and Logical Instructions
                // -------------------------------------------------

                // AMR (octal 004 => decimal 4)
                // R[r] <- R[r] + MEM[EA]
                case 4 -> {
                    int ea = computeEA(ix, ind, addr);

                    int regVal = toSigned16(s.getGPR(r));
                    int memVal = toSigned16(mem.read(ea));
                    int wideResult = regVal + memVal;

                    updateArithmeticCC(wideResult);

                    int finalVal = wideResult & 0xFFFF;
                    s.setGPR(r, finalVal);

                    return "[STEP] AMR R" + r + " <- "
                            + Memory.toOct6(finalVal)
                            + " using MEM[" + Memory.toOct6(ea) + "] = "
                            + Memory.toOct6(memVal & 0xFFFF) + "\n";
                }

                // SMR (octal 005 => decimal 5)
                // R[r] <- R[r] - MEM[EA]
                case 5 -> {
                    int ea = computeEA(ix, ind, addr);

                    int regVal = toSigned16(s.getGPR(r));
                    int memVal = toSigned16(mem.read(ea));
                    int wideResult = regVal - memVal;

                    updateArithmeticCC(wideResult);

                    int finalVal = wideResult & 0xFFFF;
                    s.setGPR(r, finalVal);

                    return "[STEP] SMR R" + r + " <- "
                            + Memory.toOct6(finalVal)
                            + " using MEM[" + Memory.toOct6(ea) + "] = "
                            + Memory.toOct6(memVal & 0xFFFF) + "\n";
                }

                // AIR (octal 006 => decimal 6)
                // R[r] <- R[r] + immed
                // IX and I are ignored
                case 6 -> {
                    int immed = addr & 0x1F;

                    // If immed is 0, the ISA says do nothing.
                    if (immed == 0) {
                        clearArithmeticCC();
                        return "[STEP] AIR no-op (immed = 0)\n";
                    }

                    int regVal = toSigned16(s.getGPR(r));
                    int wideResult = regVal + immed;

                    updateArithmeticCC(wideResult);

                    int finalVal = wideResult & 0xFFFF;
                    s.setGPR(r, finalVal);

                    return "[STEP] AIR R" + r + " <- "
                            + Memory.toOct6(finalVal)
                            + " using immed " + Memory.toOct6(immed) + "\n";
                }

                // SIR (octal 007 => decimal 7)
                // R[r] <- R[r] - immed
                // IX and I are ignored
                case 7 -> {
                    int immed = addr & 0x1F;

                    // If immed is 0, the ISA says do nothing.
                    if (immed == 0) {
                        clearArithmeticCC();
                        return "[STEP] SIR no-op (immed = 0)\n";
                    }

                    int regVal = toSigned16(s.getGPR(r));
                    int wideResult = regVal - immed;

                    updateArithmeticCC(wideResult);

                    int finalVal = wideResult & 0xFFFF;
                    s.setGPR(r, finalVal);

                    return "[STEP] SIR R" + r + " <- "
                            + Memory.toOct6(finalVal)
                            + " using immed " + Memory.toOct6(immed) + "\n";
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
                // Shift / Rotate INsstructions
                // -------------------------------------------------

                // SRC (octal 031 => decimal 25)
                // Shift Register by Count
                // - r     = target register
                // - addr  = count
                // - ind   = L/R bit (1 = left, 0 = right)
                // - low bit of ix = A/L bit (1 = logical, 0 = arithmetic)
                case 25 -> {
                    int count = addr & 0x1F;
                    int lr = ind & 0x1;          // 1 = left, 0 = right
                    int al = ix & 0x1;           // 1 = logical, 0 = arithmetic
                    int value = s.getGPR(r) & 0xFFFF;

                    // 0 means no shift
                    if (count == 0) {
                        return "[STEP] SRC no-op (count = 0)\n";
                    }

                    // count as 0..15
                    // masking with 0x0F keeps the operation inside that range
                    count &= 0x0F;

                    int result;

                    if (lr == 1) {
                        // Left shift: arithmetic and logical discard
                        // shifted-out bits and fill low bits with zeros.
                        result = (value << count) & 0xFFFF;
                    } else {
                        // Right shift:
                        // logical  -> zero-fill using >>>
                        // arithmetic -> sign-extend using signed >>
                        if (al == 1) {
                            result = (value >>> count) & 0xFFFF;
                        } else {
                            result = (toSigned16(value) >> count) & 0xFFFF;
                        }
                    }

                    s.setGPR(r, result);

                    return "[STEP] SRC R" + r + " -> " + Memory.toOct6(result) + "\n";
                }

                // RRC (octal 032 => decimal 26)
                // Rotate Register by count
                // - r     = target register
                // - addr  = count
                // - ind   = L/R bit (1 = left, 0 = right)
                // - A/L is ignored for rotate in this implementation
                case 26 -> {
                    int count = addr & 0x1F;
                    int lr = ind & 0x1;          // 1 = left, 0 = right
                    int value = s.getGPR(r) & 0xFFFF;

                    // 0 means no rotate
                    if (count == 0) {
                        return "[STEP] RRC no-op (count = 0)\n";
                    }

                    // rotating by 16 is equivalent to rotating by 0 on a 16 bit register
                    count &= 0x0F;

                    int result;
                    if (count == 0) {
                        result = value;
                    } else if (lr == 1) {
                        // rotate left on 16 bits
                        result = ((value << count) | (value >>> (16 - count))) & 0xFFFF;
                    } else {
                        // rotate right on 16 bits
                        result = ((value >>> count) | (value << (16 - count))) & 0xFFFF;
                    }

                    s.setGPR(r, result);

                    return "[STEP] RRC R" + r + " -> " + Memory.toOct6(result) + "\n";
                }

                // -------------------------------------------------
                // Index Register Load / Store INstructions
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
                // I/O Instructions
                // -------------------------------------------------

                // IN (octal 061 => decimal 49)
                // Input one character from device into register r
                case 49 -> {
                    int devid = addr & 0x1F;

                    // IN with keyboard (0) and card reader (2)
                    if (devid != 0 && devid != 2) {
                        halted = true;
                        return "[FAULT] IN only supports device 0 (keyboard) or 2 (card reader).\n";
                    }

                    int ch = inputReader.getAsInt();

                    // no input 
                    if (ch < 0) {
                        return "[STEP] IN waiting/no input available on device " + devid + "\n";
                    }

                    // store the character code in the target register
                    s.setGPR(r, ch & 0xFFFF);

                    return "[STEP] IN R" + r + " <- " + Memory.toOct6(ch & 0xFFFF)
                            + " from device " + devid + "\n";
                }

                // OUT (octal 062 => decimal 50)
                // Output one character from register r to device
                case 50 -> {
                    int devid = addr & 0x1F;

                    //  OUT with printer (1)
                    if (devid != 1) {
                        halted = true;
                        return "[FAULT] OUT only supports device 1 (console printer).\n";
                    }

                    int value = s.getGPR(r) & 0xFF;

                    // send the low 8 bits as one character to the output device
                    outputWriter.accept(value);

                    return "[STEP] OUT device 1 <- R" + r
                            + " = " + Memory.toOct6(value) + "\n";
                }

                // -------------------------------------------------
                // Register to Register / Mult-Div / Logical Instructions
                // -------------------------------------------------

                // MLT (octal 070 => decimal 56)
                // rx, rx+1 <- c(rx) * c(ry)
                // rx must be 0 or 2
                // ry must be 0 or 2
                case 56 -> {
                    int rx = r;
                    int ry = ix;

                    if (!isValidRxRyPair(rx, ry)) {
                        halted = true;
                        return "[FAULT] MLT requires rx and ry to be 0 or 2.\n";
                    }

                    long left = toSigned16(s.getGPR(rx));
                    long right = toSigned16(s.getGPR(ry));
                    long product = left * right;

                    // clear arithmetic overflow/underflow bits before updating
                    clearArithmeticCC();

                    // set overflow if the signed product does not fit in 16 bits
                    if (product > Short.MAX_VALUE || product < Short.MIN_VALUE) {
                        setCCBit(0, true);
                    }

                    // Store full 32-bit product across rx (high) and rx+1 (low).
                    int high = (int) ((product >>> 16) & 0xFFFF);
                    int low = (int) (product & 0xFFFF);

                    s.setGPR(rx, high);
                    s.setGPR(rx + 1, low);

                    return "[STEP] MLT R" + rx + ",R" + ry
                            + " -> high=" + Memory.toOct6(high)
                            + " low=" + Memory.toOct6(low) + "\n";
                }

                // DVD (octal 071 => decimal 57)
                // rx <- quotient
                // rx+1 <- remainder
                // rx must be 0 or 2
                // ry must be 0 or 2
                // if c(ry) = 0, set DIVZERO flag
                case 57 -> {
                    int rx = r;
                    int ry = ix;

                    if (!isValidRxRyPair(rx, ry)) {
                        halted = true;
                        return "[FAULT] DVD requires rx and ry to be 0 or 2.\n";
                    }

                    int divisor = toSigned16(s.getGPR(ry));

                    setCCBit(2, false);

                    if (divisor == 0) {
                        setCCBit(2, true);
                        return "[STEP] DVD divide by zero flag set.\n";
                    }

                    int dividend = toSigned16(s.getGPR(rx));
                    int quotient = dividend / divisor;
                    int remainder = dividend % divisor;

                    s.setGPR(rx, quotient & 0xFFFF);
                    s.setGPR(rx + 1, remainder & 0xFFFF);

                    return "[STEP] DVD R" + rx + ",R" + ry
                            + " -> quotient=" + Memory.toOct6(quotient & 0xFFFF)
                            + " remainder=" + Memory.toOct6(remainder & 0xFFFF) + "\n";
                }

                // TRR (octal 072 => decimal 58)
                // If c(rx) = c(ry), set EQUAL flag, else clear it
                case 58 -> {
                    int rx = r;
                    int ry = ix;

                    boolean equal = (s.getGPR(rx) & 0xFFFF) == (s.getGPR(ry) & 0xFFFF);
                    setCCBit(3, equal);

                    return "[STEP] TRR R" + rx + ",R" + ry
                            + (equal ? " -> equal\n" : " -> not equal\n");
                }

                // AND (octal 073 => decimal 59)
                // c(rx) <- c(rx) AND c(ry)
                case 59 -> {
                    int rx = r;
                    int ry = ix;

                    int result = (s.getGPR(rx) & s.getGPR(ry)) & 0xFFFF;
                    s.setGPR(rx, result);

                    return "[STEP] AND R" + rx + ",R" + ry
                            + " -> " + Memory.toOct6(result) + "\n";
                }

                // ORR (octal 074 => decimal 60)
                // c(rx) <- c(rx) OR c(ry)
                case 60 -> {
                    int rx = r;
                    int ry = ix;

                    int result = (s.getGPR(rx) | s.getGPR(ry)) & 0xFFFF;
                    s.setGPR(rx, result);

                    return "[STEP] ORR R" + rx + ",R" + ry
                            + " -> " + Memory.toOct6(result) + "\n";
                }

                // NOT (octal 075 => decimal 61)
                // c(rx) <- NOT c(rx)
                case 61 -> {
                    int rx = r;

                    int result = (~s.getGPR(rx)) & 0xFFFF;
                    s.setGPR(rx, result);

                    return "[STEP] NOT R" + rx + " -> "
                            + Memory.toOct6(result) + "\n";
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

    /**
     * Set or clear one condition-code bit.
     *
     * CC bit convention:
     * 0 = overflow
     * 1 = underflow
     * 2 = division by zero
     * 3 = equal-or-not
     *
     * @param bitIndex CC bit to modify
     * @param value true to set the bit, false to clear it
     */
    private void setCCBit(int bitIndex, boolean value) {
        int cc = s.getCC();

        if (value) {
            cc |= (1 << bitIndex);
        } else {
            cc &= ~(1 << bitIndex);
        }

        s.setCC(cc);
    }

    /**
     * Clear only the arithmetic CC bits before an arithmetic operation.
     *
     * We leave:
     * - DIVZERO (bit 2)
     * - EQUALORNOT (bit 3)
     *
     * unchanged here because those belong to other instructions.
     */
    private void clearArithmeticCC() {
        setCCBit(0, false); // overflow
        setCCBit(1, false); // underflow
    }

    /**
     * Update arithmetic CC flags from a full-width signed result.
     *
     * If the signed result is larger than 16-bit signed max, set overflow.
     * If the signed result is smaller than 16-bit signed min, set underflow.
     *
     * @param wideResult signed arithmetic result before 16-bit truncation
     */
    private void updateArithmeticCC(int wideResult) {
        clearArithmeticCC();

        if (wideResult > Short.MAX_VALUE) {
            setCCBit(0, true); // overflow
        } else if (wideResult < Short.MIN_VALUE) {
            setCCBit(1, true); // underflow
        }
    }

    /**
     * MLT and DVD require rx and ry to be register pair starts.
     * Valid values are 0 or 2 only.
     *
     * @param rx first register
     * @param ry second register
     * @return true if both registers are valid pair starts
     */
    private boolean isValidRxRyPair(int rx, int ry) {
        return (rx == 0 || rx == 2) && (ry == 0 || ry == 2);
    }
}