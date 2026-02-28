package simulator.machine;

/**
 * MachineState stores the architectural registers for the CSCI 6461 simulator.
 *
 * Responsibilities:
 *  - GPR0..GPR3 (16-bit)
 *  - IX1..IX3   (16-bit)
 *  - PC (12-bit), MAR (12-bit)
 *  - MBR (16-bit), IR (16-bit)
 *  - CC (4-bit), MFR (4-bit)
 *
 * Notes:
 *  - We store values as int but mask to required bit widths.
 *  - This class contains NO GUI logic.
 */
public final class MachineState {

    // general purpose registers
    private final int[] gpr = new int[4]; // R0..R3

    // index registers (IX1..IX3). Index 0 is unused.
    private final int[] ixr = new int[4];

    // special registers
    private int pc;   // 12-bit
    private int mar;  // 12-bit
    private int mbr;  // 16-bit
    private int ir;   // 16-bit

    // condition + Machine Fault registers
    private int cc;   // 4-bit
    private int mfr;  // 4-bit

    /** 
     * Reset registers to 0. (Memory is reset separately.) 
     */
    public void clear() {
        for (int i = 0; i < 4; i++) gpr[i] = 0;
        for (int i = 0; i < 4; i++) ixr[i] = 0;
        pc = mar = mbr = ir = 0;
        cc = mfr = 0;
    }

    /* =========================
     * GPR access
     * ========================= */

    public int getGPR(int r) {
        checkRange(r, 0, 3, "GPR");
        return gpr[r] & 0xFFFF;
    }

    public void setGPR(int r, int value) {
        checkRange(r, 0, 3, "GPR");
        gpr[r] = value & 0xFFFF;
    }

    /* =========================
     * IXR access (1..3)
     * ========================= */

    public int getIXR(int x) {
        checkRange(x, 1, 3, "IXR");
        return ixr[x] & 0xFFFF;
    }

    public void setIXR(int x, int value) {
        checkRange(x, 1, 3, "IXR");
        ixr[x] = value & 0xFFFF;
    }

    /* =========================
     * Special registers
     * ========================= */

    public int getPC() { return pc & 0xFFF; }
    public void setPC(int value) { pc = value & 0xFFF; }

    public int getMAR() { return mar & 0xFFF; }
    public void setMAR(int value) { mar = value & 0xFFF; }

    public int getMBR() { return mbr & 0xFFFF; }
    public void setMBR(int value) { mbr = value & 0xFFFF; }

    public int getIR() { return ir & 0xFFFF; }
    public void setIR(int value) { ir = value & 0xFFFF; }

    public int getCC() { return cc & 0xF; }
    public void setCC(int value) { cc = value & 0xF; }

    public int getMFR() { return mfr & 0xF; }
    public void setMFR(int value) { mfr = value & 0xF; }

    /* =========================
     * Helpers
     * ========================= */

    private void checkRange(int v, int lo, int hi, String name) {
        if (v < lo || v > hi) {
            throw new IllegalArgumentException(name + " index out of range: " + v);
        }
    }
}