package simulator.tests;

import part0_assembler.Encoder;
import simulator.cpu.CPU;
import simulator.machine.Memory;
import simulator.machine.MachineState;

/**
 * Test validation for opcode instructions in simulator
 * - Demonstrates that individual instructions work
 * - Provides a repeatable PASS/FAIL test run
 * - Each test has its own Memory, MachineState, and CPU for debugging
 *
 * Current instruction groups validated here:
 * - Processor Control: HLT
 * - Load / Store: LDR, LDA, LDX, STR, STX
 * - Transfer: JZ, JNE, JCC, JMA, JSR, RFS, SOB, JGE
 *
 * Notes:
 * - The current CPU decoder uses:
 *   opcode(6) | r(2) | ix(2) | I(1) | address(5)
 * - Tests for instructions not yet implemented in CPU.java will fail until
 *   their CPU switch cases are added.
 */
public final class InstructionTests {

     /** Shared encoder used to build machine instructions for tests. */
    private static final Encoder ENCODER = new Encoder();

    /** Running count of passed tests. */
    private static int passed = 0;

    /** Running count of failed tests. */
    private static int failed = 0;
    
    /**
     * Run all current simulator instruction tests.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        runProcessorControlTests();
        runLoadStoreTests();
        runTransferTests();
        runArithmeticLogicTests();
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

        testJNETaken();
        testJNENotTaken();

        testJCCTaken();
        testJCCNotTaken();

        testJMA();
        testJSR();
        testRFS();

        testSOBTaken();
        testSOBNotTaken();

        testJGETaken();
        testJGENotTaken();
        System.out.println();
    }

    /**
     * Run arithmetic and immediate instruction tests.
     */
    private static void runArithmeticLogicTests() {
        System.out.println("=====================================================");
        System.out.println("Arithmetic / Logic Tests");
        System.out.println("=====================================================");
        testAMRDirect();
        testAMRIndexed();
        testAMROverflow();

        testSMRDirect();
        testSMRUnderflow();

        testAIRBasic();
        testAIRZeroImmediate();
        testAIROverflow();

        testSIRBasic();
        testSIRZeroImmediate();
        testSIRFromZero();
        testSIRUnderflow();
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
    // Load / Store Tests
    // =====================================================

    /**
     * LDR direct:
     * LDR R1,0,20 => R1 <- MEM[20]
     */
    private static void testLDRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("LDR", 1, 0, 20);

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

        int instr = ENCODER.encodeBasic("LDR", 2, 1, 5);

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

        int instr = ENCODER.encodeBasicIndirect("LDR", 0, 0, 12, 1);

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

    /**
     * LDA direct:
     * LDA R3,0,17 => R3 <- EA = 17
     */
    private static void testLDADirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("LDA", 3, 0, 17);

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

        int instr = ENCODER.encodeBasic("LDA", 0, 2, 6);

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

    /**
     * LDX direct:
     * LDX X2,25 => X2 <- MEM[25]
     */
    private static void testLDXDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeXAddress("LDX", 2, 25);

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

        int instr = ENCODER.encodeXAddressIndirect("LDX", 1, 12, 1);

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

        int instr = ENCODER.encodeXAddress("LDX", 0, 10);

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "LDX invalid X0",
            cpu.isHalted() && log.toLowerCase().contains("invalid"),
            "CPU should halt on invalid LDX with X=0"
        );
    }

    /**
     * STR direct:
     * STR R1,0,20 => MEM[20] <- R1
     */
    private static void testSTRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("STR", 1, 0, 20);

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

        int instr = ENCODER.encodeBasic("STR", 3, 2, 5);

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

    /**
     * STX direct:
     * STX X2,25 => MEM[25] <- X2
     */
    private static void testSTXDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeXAddress("STX", 2, 25);

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

        int instr = ENCODER.encodeXAddressIndirect("STX", 1, 12, 1);

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

        int instr = ENCODER.encodeXAddress("STX", 0, 10);

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
    // Transfer Tests
    // =====================================================

    /**
     * JZ taken:
     * If R1 == 0, then PC <- EA
     */
    private static void testJZTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JZ", 1, 0, 18);

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

        int instr = ENCODER.encodeBasic("JZ", 1, 0, 18);

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

    /**
     * JNE taken:
     * If R1 != 0, then PC <- EA
     */
    private static void testJNETaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JNE", 1, 0, 19);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 7);

        cpu.step();

        check(
            "JNE taken",
            s.getPC() == 19,
            "PC should branch when register is nonzero"
        );
    }

    /**
     * JNE not taken:
     * If R1 == 0, then execution continues sequentially.
     */
    private static void testJNENotTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JNE", 1, 0, 19);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0);

        cpu.step();

        check(
            "JNE not taken",
            s.getPC() == 1,
            "PC should continue sequentially"
        );
    }

    /**
     * JCC taken:
     * If selected CC bit is set, then PC <- EA.
     */
    private static void testJCCTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JCC", 2, 0, 21);

        mem.write(0, instr);
        s.setPC(0);
        s.setCC(1 << 2);

        cpu.step();

        check(
            "JCC taken",
            s.getPC() == 21,
            "PC should branch when CC bit is set"
        );
    }

    /**
     * JCC not taken:
     * If selected CC bit is clear, then execution continues sequentially.
     */
    private static void testJCCNotTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JCC", 2, 0, 21);

        mem.write(0, instr);
        s.setPC(0);
        s.setCC(0);

        cpu.step();

        check(
            "JCC not taken",
            s.getPC() == 1,
            "PC should continue sequentially"
        );
    }

    /**
     * JMA:
     * Unconditional jump to EA.
     */
    private static void testJMA() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeXAddress("JMA", 2, 5);

        mem.write(0, instr);
        s.setPC(0);
        s.setIXR(2, 10); // EA = 15

        cpu.step();

        check(
            "JMA",
            s.getPC() == 15,
            "PC should jump unconditionally"
        );
    }

    
    /**
     * JSR:
     * Save return address in R3 and jump to EA.
     */
    private static void testJSR() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeXAddress("JSR", 1, 7);

        mem.write(0, instr);
        s.setPC(0);
        s.setIXR(1, 8); // EA = 15

        cpu.step();

        check(
            "JSR",
            s.getGPR(3) == 1 && s.getPC() == 15,
            "R3 should store return address and PC should jump"
        );
    }

    /**
     * RFS:
     * R0 <- immed, PC <- R3
     */
    private static void testRFS() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRFS(9);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(3, 22);

        cpu.step();

        check(
            "RFS",
            s.getGPR(0) == 9 && s.getPC() == 22,
            "R0 should receive immed and PC should return through R3"
        );
    }

    /**
     * SOB taken:
     * Decrement register, then branch if result > 0.
     */
    private static void testSOBTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("SOB", 2, 0, 14);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 2);

        cpu.step();

        check(
            "SOB taken",
            s.getGPR(2) == 1 && s.getPC() == 14,
            "Register should decrement and branch"
        );
    }

    /**
     * SOB not taken:
     * Decrement register, but do not branch if result <= 0.
     */
    private static void testSOBNotTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("SOB", 2, 0, 14);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 1);

        cpu.step();

        check(
            "SOB not taken",
            s.getGPR(2) == 0 && s.getPC() == 1,
            "Register should decrement and continue sequentially"
        );
    }

    /**
     * JGE taken:
     * Branch if register is nonnegative in signed 16-bit form.
     */
    private static void testJGETaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JGE", 0, 0, 17);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 0x0001);

        cpu.step();

        check(
            "JGE taken",
            s.getPC() == 17,
            "PC should branch when register is nonnegative"
        );
    }

    /**
     * JGE not taken:
     * Do not branch if register is negative in signed 16-bit form.
     */
    private static void testJGENotTaken() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("JGE", 0, 0, 17);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 0xFFFF); // -1 in signed 16-bit form

        cpu.step();

        check(
            "JGE not taken",
            s.getPC() == 1,
            "PC should continue sequentially when register is negative"
        );
    }

    // =====================================================
    // Arithmetic / Logic Tests
    // =====================================================

    /**
     * AMR direct:
     * AMR R1,0,20 => R1 <- R1 + MEM[20]
     */
    private static void testAMRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("AMR", 1, 0, 20);

        mem.write(0, instr);
        mem.write(20, 5);
        s.setPC(0);
        s.setGPR(1, 7);

        cpu.step();

        check(
            "AMR direct",
            s.getGPR(1) == 12,
            "R1 should become R1 + MEM[20]"
        );
    }

    /**
     * AMR indexed:
     * AMR R2,1,5 with X1=10 => EA=15 => R2 <- R2 + MEM[15]
     */
    private static void testAMRIndexed() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("AMR", 2, 1, 5);

        mem.write(0, instr);
        mem.write(15, 9);
        s.setPC(0);
        s.setIXR(1, 10);
        s.setGPR(2, 4);

        cpu.step();

        check(
            "AMR indexed",
            s.getGPR(2) == 13,
            "R2 should become R2 + MEM[X1+5]"
        );
    }

    /**
     * AMR overflow:
     * Force overflow using a memory operand instead of an immediate operand.
     * - R2 starts at 32760
     * - MEM[20] contains 10
     * - AMR R2,0,20 adds MEM[20] to R2
     *
     * Expected:
     * - mathematical result = 32770
     * - stored result wraps into 16 bits
     * - overflow CC bit should be set
     */
    private static void testAMROverflow() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("AMR", 2, 0, 20);

        mem.write(0, instr);
        mem.write(20, 10);

        s.setPC(0);
        s.setGPR(2, 32760);
        s.setCC(0);

        cpu.step();

        check(
            "AMR overflow",
            s.getGPR(2) == ((32760 + 10) & 0xFFFF)
                && ((s.getCC() & 0b0001) != 0)   // overflow bit set
                && ((s.getCC() & 0b0010) == 0),  // underflow bit clear
            "AMR should set OVERFLOW when sum exceeds +32767"
        );
    }

    /**
     * SMR direct:
     * SMR R3,0,12 => R3 <- R3 - MEM[12]
     */
    private static void testSMRDirect() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("SMR", 3, 0, 12);

        mem.write(0, instr);
        mem.write(12, 7);
        s.setPC(0);
        s.setGPR(3, 20);

        cpu.step();

        check(
            "SMR direct",
            s.getGPR(3) == 13,
            "R3 should become R3 - MEM[12]"
        );
    }

    /**
     * SMR underflow:
     * Force underflow using a memory operand.
     * - R3 starts at 0x8008, which is -32760 in signed 16-bit form
     * - MEM[12] contains 20
     * - SMR R3,0,12 subtracts MEM[12] from R3
     *
     * Expected:
     * - mathematical result = -32780
     * - stored result wraps into 16 bits
     * - underflow CC bit should be set
     */
    private static void testSMRUnderflow() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeBasic("SMR", 3, 0, 12);

        mem.write(0, instr);
        mem.write(12, 20);

        s.setPC(0);
        s.setGPR(3, 0x8008);
        s.setCC(0);

        cpu.step();

        check(
            "SMR underflow",
            s.getGPR(3) == ((0x8008 - 20) & 0xFFFF)
                && ((s.getCC() & 0b0010) != 0)   // underflow bit set
                && ((s.getCC() & 0b0001) == 0),  // overflow bit clear
            "SMR should set UNDERFLOW when result goes below -32768"
        );
    }

    /**
     * AIR basic:
     * AIR R0,9 => R0 <- R0 + 9
     */
    private static void testAIRBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("AIR", 0, 9);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 3);

        cpu.step();

        check(
            "AIR basic",
            s.getGPR(0) == 12,
            "R0 should increase by immediate value"
        );
    }

    /**
     * AIR with immed = 0 should do nothing.
     */
    private static void testAIRZeroImmediate() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("AIR", 1, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 25);

        cpu.step();

        check(
            "AIR zero immed",
            s.getGPR(1) == 25,
            "R1 should remain unchanged when immed = 0"
        );
    }

    /**
     * AIR overflow:
     * Force a signed 16-bit positive overflow.
     * - R0 starts at 0x7FFF (32767), the maximum signed 16-bit value
     * - AIR R0,1 adds 1
     *
     * Expected:
     * - mathematical result = 32768
     * - stored 16-bit result wraps to 0x8000
     * - overflow CC bit should be set
     * - underflow CC bit should remain clear
     */
    private static void testAIROverflow() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("AIR", 0, 1);

        mem.write(0, instr);

        s.setPC(0);
        s.setGPR(0, 0x7FFF);
        s.setCC(0);

        cpu.step();

        check(
            "AIR overflow",
            s.getGPR(0) == 0x8000
                && ((s.getCC() & 0b0001) != 0)   // overflow bit set
                && ((s.getCC() & 0b0010) == 0),  // underflow bit clear
            "Result should wrap to 0x8000 and set OVERFLOW"
        );
    }

    /**
     * SIR basic:
     * SIR R2,4 => R2 <- R2 - 4
     */
    private static void testSIRBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("SIR", 2, 4);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 11);

        cpu.step();

        check(
            "SIR basic",
            s.getGPR(2) == 7,
            "R2 should decrease by immediate value"
        );
    }

    
    /**
     * SIR with immed = 0 should do nothing.
     */
    private static void testSIRZeroImmediate() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("SIR", 0, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 18);

        cpu.step();

        check(
            "SIR zero immed",
            s.getGPR(0) == 18,
            "R0 should remain unchanged when immed = 0"
        );
    }

    /**
     * SIR special note from ISA:
     * if c(r) = 0, result becomes -(immed)
     *
     * Example:
     * SIR R1,5 => R1 should become -5 (0xFFFB)
     */
    private static void testSIRFromZero() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("SIR", 1, 5);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0);

        cpu.step();

        check(
            "SIR from zero",
            s.getGPR(1) == 0xFFFB,
            "R1 should become -immed in 16-bit form"
        );
    }

    /**
     * SIR underflow:
     * Force a signed 16-bit negative underflow.
     * - R1 starts at 0x8000 (-32768), the minimum signed 16-bit value
     * - SIR R1,1 subtracts 1
     *
     * Expected:
     * - mathematical result = -32769
     * - stored 16-bit result wraps to 0x7FFF
     * - underflow CC bit should be set
     * - overflow CC bit should remain clear
     */
    private static void testSIRUnderflow() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeImmediate("SIR", 1, 1);

        mem.write(0, instr);

        s.setPC(0);
        s.setGPR(1, 0x8000);
        s.setCC(0);

        cpu.step();

        check(
            "SIR underflow",
            s.getGPR(1) == 0x7FFF
                && ((s.getCC() & 0b0010) != 0)   // underflow bit set
                && ((s.getCC() & 0b0001) == 0),  // overflow bit clear
            "Result should wrap to 0x7FFF and set UNDERFLOW"
        );
    }

    // =====================================================
    // Helpers
    // =====================================================

    /**
     * Create a CPU for a test.
     *
     * @param mem   memory instance
     * @param state machine state instance
     * @return      connected CPU
     */
    private static CPU newCPU(Memory mem, MachineState state) {
        return new CPU(mem, state);
    }

    /**
     * Record and print PASS/FAIL result for one test.
     *
     * @param name      short test name
     * @param ok        whether the test passed
     * @param details   short human-readable description
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
}