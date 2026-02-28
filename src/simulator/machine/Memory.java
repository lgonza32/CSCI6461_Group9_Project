package simulator.machine;

import java.util.Arrays;

/**
 * Memory model for the CSCI 6461 simulator.
 *
 * Responsibilities:
 *  - Memory holds up to 2048 words (addresses 0..2047).
 *  - Each word is 16 bits.
 *  - Reset should clear memory to 0.
 *
 * Notes:
 *  - We store words in an int[] but always mask to 16 bits (0..65535).
 *  - Address bounds are enforced; invalid access throws IllegalArgumentException.
 */
public final class Memory {

    public static final int SIZE = 2048; // memory size in words (per project spec)
    private final int[] mem = new int[SIZE]; // backing storage. Each entry represents one 16-bit word

    /**
     * Clears all memory words to 0.
     * Called during reset / power-on.
     */
    public void clear() {
        Arrays.fill(mem, 0);
    }

    /**
     * Read a 16-bit word from memory.
     *
     * @param address memory address (0..2047)
     * @return 16-bit word value (0..65535)
     */
    public int read(int address) {
        checkAddress(address);
        return mem[address] & 0xFFFF;
    }

    /**
     * Write a 16-bit word into memory.
     *
     * @param address memory address (0..2047)
     * @param word value to write (only low 16 bits are stored)
     */
    public void write(int address, int word) {
        checkAddress(address);
        mem[address] = word & 0xFFFF;
    }

    /**
     * Convenience helper: returns a word formatted as 6-digit octal.
     * Example: 10 decimal -> "000012"
     */
    public static String toOct6(int value) {
        String s = Integer.toOctalString(value & 0xFFFF);
        if (s.length() > 6) s = s.substring(s.length() - 6);
        return "0".repeat(Math.max(0, 6 - s.length())) + s;
    }

    /**
     * Enforces the legal address range for the machine.
     */
    private void checkAddress(int address) {
        if (address < 0 || address >= SIZE) {
            throw new IllegalArgumentException(
                    "Memory address out of range: " + address + " (valid 0.." + (SIZE - 1) + ")"
            );
        }
    }
}