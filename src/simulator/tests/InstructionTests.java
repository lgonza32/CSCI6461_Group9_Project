package simulator.tests;

import java.util.List;

import part0_assembler.Encoder;
import simulator.cpu.CPU;
import simulator.machine.Memory;
import simulator.machine.MachineState;

/**
 * Test validation for opcode instructions in simulator
 * - Demonstrates that individual instructions work
 * - Provides a repeatable PASS/FAIL test run
 *
 * Current instruction groups validated here:
 * - Processor Control: HLT
 * - Load/Store: LDR, LDA, LDX, STR, STX
 * - Transfer: JZ
 *
 * Notes:
 * - The current CPU decoder uses:
 *   opcode(6) | r(2) | ix(2) | I(1) | address(5)
 */
public final class InstructionTests {

    // Shared encoder instance reused across all tests.
    private static final Encoder ENCODER = new Encoder();

    // Summary counters shown at the end of the run.
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        runProcessorControlTests();
        runLoadStoreTests();
        runTransferTests();
        printSummary();
    }

    // =====================================================
    // Test Group Runners
    // =====================================================

    /**
     * Runs all processor control tests currently supported.
     */
    private static void runProcessorControlTests() {
        System.out.println("=====================================================");
        System.out.println("Processor Control Tests");
        System.out.println("=====================================================");
        testHLT();
        testStepIgnoredAfterHLT();
        System.out.println();
    }

    /**
     * Runs all load/store tests currently supported.
     */
    private static void runLoadStoreTests() {
        System.out.println("=====================================================");
        System.out.println("Load / Store Tests");
        System.out.println("=====================================================");
        testLDRDirect();
        testLDRIndexed();
        testLDRIndirect();

        testLDADirect();
        testLDAIndexed();

        testLDXDirect();
        testLDXIndirect();
        testLDXInvalidX0();

        testSTRDirect();
        testSTRIndexed();

        testSTXDirect();
        testSTXIndirect();
        testSTXInvalidX0();

        System.out.println();
    }

    /**
     * Runs all transfer tests currently supported.
     */
    private static void runTransferTests() {
        System.out.println("=====================================================");
        System.out.println("Transfer Tests");
        System.out.println("=====================================================");
        testJZTaken();
        testJZNotTaken();
        System.out.println();
    }

    /**
     * Prints the final summary.
     */
    private static void printSummary() {
        System.out.println("=====================================================");
        System.out.println("Instruction Test Summary");
        System.out.println("=====================================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    // =====================================================
    // Helpers
    // =====================================================

    /**
     * Encodes a normal basic-format instruction with no indirect bit.
     * Format: OP r,x,address
     */
    private static int encodeBasic(String mnemonic, String r, String ix, String address) {
        return ENCODER.encodeFormat(mnemonic, List.of(r, ix, address));
    }

    /**
     * Encodes a normal basic-format instruction with indirect addressing.
     * Format: OP r,x,address,1
     */
    private static int encodeBasicIndirect(String mnemonic, String r, String ix, String address) {
        return ENCODER.encodeFormat(mnemonic, List.of(r, ix, address, "1"));
    }

    /**
     * Encodes LDX/STX with no indirect bit.
     * These use the assembler adaptation:
     * x,address -> 0,x,address
     */
    private static int encodeXInstruction(String mnemonic, String x, String address) {
        return ENCODER.encodeFormat(mnemonic, List.of("0", x, address));
    }

    /**
     * Encodes LDX/STX with indirect addressing.
     * These use the assembler adaptation:
     * x,address,1 -> 0,x,address,1
     */
    private static int encodeXInstructionIndirect(String mnemonic, String x, String address) {
        return ENCODER.encodeFormat(mnemonic, List.of("0", x, address, "1"));
    }

    /**
     * PASS/FAIL reporter.
     */
    private static void check(String name, boolean ok, String details) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + " - " + details);
        } else {
            failed++;
            System.out.println("[FAIL] " + name + " - " + details);
        }
    }

    /**
     * Convenience helper to build a clean CPU for each test.
     */
    private static CPU newCPU(Memory mem, MachineState s) {
        return new CPU(mem, s);
    }

    // =====================================================
    // Processor Control Tests
    // =====================================================

    /**
     * HLT should halt the CPU immediately after fetch/decode.
     *
     * HLT has opcode 0 and is represented as a zero word.
     */
    private static void testHLT() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        // HLT opcode is all zeros.
        mem.write(0, 0);
        s.setPC(0);

        String log = cpu.step();

        check(
            "HLT",
            cpu.isHalted()
                && s.getPC() == 1
                && s.getMAR() == 0
                && s.getMBR() == 0
                && s.getIR() == 0
                && log.contains("HLT"),
            "CPU should halt and advance PC after fetching HLT"
        );
    }

    /**
     * Once halted, another step should be ignored.
     */
    private static void testStepIgnoredAfterHLT() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        mem.write(0, 0); // HLT
        s.setPC(0);

        cpu.step();
        int pcAfterHalt = s.getPC();

        String log = cpu.step();

        check(
            "Step ignored after HLT",
            cpu.isHalted()
                && s.getPC() == pcAfterHalt
                && log.toLowerCase().contains("halted"),
            "Second step should be ignored when CPU is halted"
        );
    }

    // =====================================================
    // Load / Store Tests - LDR
    // =====================================================

    /**
     * LDR direct:
     * LDR R1,0,20 => R1 <- MEM[20]
     */
    private static void testLDRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("LDR", "1", "0", "20");

        mem.write(0, instr);
        mem.write(20, 83); // decimal 83 = octal 000123
        s.setPC(0);

        cpu.step();

        check(
            "LDR direct",
            s.getGPR(1) == 83
                && s.getPC() == 1
                && s.getMAR() == 0
                && s.getIR() == instr,
            "R1 should load MEM[20]"
        );
    }

    /**
     * LDR indexed:
     * LDR R2,1,5 with X1=10 => EA=15 => R2 <- MEM[15]
     */
    private static void testLDRIndexed() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("LDR", "2", "1", "5");

        mem.write(0, instr);
        mem.write(15, 302); // decimal 302 = octal 000456
        s.setPC(0);
        s.setIXR(1, 10);

        cpu.step();

        check(
            "LDR indexed",
            s.getGPR(2) == 302 && s.getIXR(1) == 10,
            "R2 should load MEM[X1+5] = MEM[15]"
        );
    }

    /**
     * LDR indirect:
     * LDR R0,0,12,1 => EA = MEM[12] => R0 <- MEM[EA]
     */
    private static void testLDRIndirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasicIndirect("LDR", "0", "0", "12");

        mem.write(0, instr);
        mem.write(12, 21);   // pointer
        mem.write(21, 511);  // decimal 511 = octal 000777
        s.setPC(0);

        cpu.step();

        check(
            "LDR indirect",
            s.getGPR(0) == 511 && mem.read(12) == 21,
            "R0 should load MEM[MEM[12]]"
        );
    }

    // =====================================================
    // Load / Store Tests - LDA
    // =====================================================

    /**
     * LDA direct:
     * LDA R3,0,17 => R3 <- EA = 17
     */
    private static void testLDADirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("LDA", "3", "0", "17");

        mem.write(0, instr);
        s.setPC(0);

        cpu.step();

        check(
            "LDA direct",
            s.getGPR(3) == 17,
            "R3 should receive direct EA value 17"
        );
    }

    /**
     * LDA indexed:
     * LDA R0,2,6 with X2=9 => EA=15 => R0 <- 15
     */
    private static void testLDAIndexed() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("LDA", "0", "2", "6");

        mem.write(0, instr);
        s.setPC(0);
        s.setIXR(2, 9);

        cpu.step();

        check(
            "LDA indexed",
            s.getGPR(0) == 15,
            "R0 should receive computed EA value 15"
        );
    }

    // =====================================================
    // Load / Store Tests - LDX
    // =====================================================

    /**
     * LDX direct:
     * LDX X2,25 => X2 <- MEM[25]
     */
    private static void testLDXDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstruction("LDX", "2", "25");

        mem.write(0, instr);
        mem.write(25, 209); // decimal 209 = octal 000321
        s.setPC(0);

        cpu.step();

        check(
            "LDX direct",
            s.getIXR(2) == 209,
            "X2 should load MEM[25]"
        );
    }

    /**
     * LDX indirect:
     * LDX X1,12,1 => EA = MEM[12] => X1 <- MEM[EA]
     */
    private static void testLDXIndirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstructionIndirect("LDX", "1", "12");

        mem.write(0, instr);
        mem.write(12, 20);  // pointer
        mem.write(20, 83);  // decimal 83 = octal 000123
        s.setPC(0);

        cpu.step();

        check(
            "LDX indirect",
            s.getIXR(1) == 83 && mem.read(12) == 20,
            "X1 should load MEM[MEM[12]]"
        );
    }

    /**
     * LDX with X=0 is invalid in the current CPU implementation.
     */
    private static void testLDXInvalidX0() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstruction("LDX", "0", "10");

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "LDX invalid X0",
            cpu.isHalted() && log.toLowerCase().contains("invalid"),
            "CPU should halt on invalid LDX with X=0"
        );
    }

    // =====================================================
    // Load / Store Tests - STR
    // =====================================================

    /**
     * STR direct:
     * STR R1,0,20 => MEM[20] <- R1
     */
    private static void testSTRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("STR", "1", "0", "20");

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 83); // decimal 83 = octal 000123

        String log = cpu.step();

        check(
            "STR direct",
            mem.read(20) == 83
                && s.getGPR(1) == 83
                && s.getPC() == 1
                && !log.toLowerCase().contains("unsupported"),
            "MEM[20] should receive R1"
        );
    }

    /**
     * STR indexed:
     * STR R3,2,5 with X2=10 => EA=15 => MEM[15] <- R3
     */
    private static void testSTRIndexed() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("STR", "3", "2", "5");

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(3, 511); // decimal 511 = octal 000777
        s.setIXR(2, 10);

        String log = cpu.step();

        check(
            "STR indexed",
            mem.read(15) == 511
                && s.getGPR(3) == 511
                && s.getIXR(2) == 10
                && !log.toLowerCase().contains("unsupported"),
            "MEM[15] should receive R3"
        );
    }

    // =====================================================
    // Load / Store Tests - STX
    // =====================================================

    /**
     * STX direct:
     * STX X2,25 => MEM[25] <- X2
     */
    private static void testSTXDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstruction("STX", "2", "25");

        mem.write(0, instr);
        s.setPC(0);
        s.setIXR(2, 302); // decimal 302 = octal 000456

        String log = cpu.step();

        check(
            "STX direct",
            mem.read(25) == 302
                && s.getIXR(2) == 302
                && !log.toLowerCase().contains("unsupported"),
            "MEM[25] should receive X2"
        );
    }

    /**
     * STX indirect:
     * STX X1,12,1 => EA = MEM[12] => MEM[EA] <- X1
     */
    private static void testSTXIndirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstructionIndirect("STX", "1", "12");

        mem.write(0, instr);
        mem.write(12, 20); // pointer
        s.setPC(0);
        s.setIXR(1, 209);  // decimal 209 = octal 000321

        String log = cpu.step();

        check(
            "STX indirect",
            mem.read(20) == 209
                && mem.read(12) == 20
                && s.getIXR(1) == 209
                && !log.toLowerCase().contains("unsupported"),
            "MEM[MEM[12]] should receive X1"
        );
    }

    /**
     * STX with X=0 is invalid.
     */
    private static void testSTXInvalidX0() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeXInstruction("STX", "0", "10");

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "STX invalid X0",
            cpu.isHalted() && log.toLowerCase().contains("invalid"),
            "CPU should halt on invalid STX with X=0"
        );
    }

    // =====================================================
    // Transfer Tests - JZ
    // =====================================================

    /**
     * JZ taken:
     * If R1 == 0, then PC <- EA
     */
    private static void testJZTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("JZ", "1", "0", "18");

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0);

        cpu.step();

        check(
            "JZ taken",
            s.getPC() == 18,
            "PC should branch to EA when tested register is zero"
        );
    }

    /**
     * JZ not taken:
     * If R1 != 0, then PC should remain at oldPC + 1 after fetch.
     */
    private static void testJZNotTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = encodeBasic("JZ", "1", "0", "18");

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 5);

        cpu.step();

        check(
            "JZ not taken",
            s.getPC() == 1,
            "PC should stay at sequential next instruction when register is nonzero"
        );
    }
}