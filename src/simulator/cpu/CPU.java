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

            // LDA (octal 003 => decimal 3): R[r] <- EA
            case 3 -> {
                int ea = computeEA(ix, ind, addr);
                s.setGPR(r, ea);
                return "[STEP] LDA R" + r + " <- EA " + Memory.toOct6(ea) + "\n";
            }

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

            default -> {
                halted = true;
                return "[FAULT] Unsupported opcode=" + opcode + " IR=" + Memory.toOct6(ir) + "\n";
            }
        }
    }

    /**
     * Compute Effective Address for BASIC format instructions that support indexing.
     *
     * EA = addr + IXR[ix] (if ix != 0)
     * if I==1 then EA = MEM[EA] (use low 12 bits as address)
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
}