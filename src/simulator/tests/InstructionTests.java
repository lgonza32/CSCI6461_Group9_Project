package simulator.tests;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import part0_assembler.Encoder;
import simulator.cpu.CPU;
import simulator.machine.Memory;
import simulator.machine.MachineState;
import simulator.cache.Cache;

/**
 * Test validation for opcode instructions in simulator
 * - Demonstrates that individual instructions work
 * - Provides a repeatable PASS/FAIL test run
 * - Each test has its own Memory, Cache, MachineState, and CPU for debugging
 *
 * Not currently validated:
 * - CHK
 * - Floating Point/Vector Instructions
 * - Trap
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
        runRegisterToRegisterTests();
        runShiftRotateTests();
        runIOTests();
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
     * Run register to register instruction tests.
     */
    private static void runRegisterToRegisterTests() {
        System.out.println("=====================================================");
        System.out.println("Register to Register / Mult-Div / Logical Instructions");
        System.out.println("=====================================================");
        testMLTBasic();
        testMLTOverflow();
        testMLTInvalidPair();

        testDVDBasic();
        testDVDByZero();
        testDVDInvalidPair();

        testTRREqual();
        testTRRNotEqual();

        testANDBasic();
        testORRBasic();
        testNOTBasic();
        System.out.println();
    }

    /**
     * Run shift and rotate instruction tests.
     */
    private static void runShiftRotateTests() {
        System.out.println("=====================================================");
        System.out.println("Shift / Rotate Tests");
        System.out.println("=====================================================");
        testSRCLeftLogical();
        testSRCRightLogical();
        testSRCRightArithmetic();
        testSRCZeroCount();

        testRRCLeft();
        testRRCRight();
        testRRCZeroCount();
        System.out.println();
    }

    /**
     * Run I/O instruction tests.
     */
    private static void runIOTests() {
        System.out.println("=====================================================");
        System.out.println("I/O Tests");
        System.out.println("=====================================================");
        testINKeyboardBasic();
        testINNoInputAvailable();
        testOUTPrinterBasic();
        testINInvalidDevice();
        testOUTInvalidDevice();
        testINWaitRestoresPC();
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
    // Register to Register / Mult-Div / Logical Instructions
    // =====================================================

    /**
     * MLT basic:
     * Multiply 3 * 4 using R0 and R2.
     */
    private static void testMLTBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("MLT", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 3);
        s.setGPR(2, 4);
        s.setCC(0);

        cpu.step();

        check(
            "MLT basic",
            s.getGPR(0) == 0 && s.getGPR(1) == 12,
            "R0:R1 should contain product 12"
        );
    }

    /**
     * MLT overflow:
     * Force a product too large for signed 16 bits.
     *
     * Expected:
     * - overflow CC bit set
     */
    private static void testMLTOverflow() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("MLT", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 300);
        s.setGPR(2, 300);
        s.setCC(0);

        cpu.step();

        check(
            "MLT overflow",
            (s.getCC() & 0b0001) != 0,
            "MLT should set OVERFLOW when product exceeds signed 16-bit range"
        );
    }

    /**
     * MLT invalid register pair:
     * rx and ry must be 0 or 2.
     */
    private static void testMLTInvalidPair() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("MLT", 1, 2);

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "MLT invalid pair",
            cpu.isHalted() && log.toLowerCase().contains("requires"),
            "CPU should halt on invalid MLT register pair"
        );
    }

    /**
     * DVD basic:
     * Divide 17 by 5 using R0 and R2.
     */
    private static void testDVDBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("DVD", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 17);
        s.setGPR(2, 5);
        s.setCC(0);

        cpu.step();

        check(
            "DVD basic",
            s.getGPR(0) == 3 && s.getGPR(1) == 2,
            "R0 should contain quotient and R1 should contain remainder"
        );
    }

    /**
     * DVD divide-by-zero:
     * Should set DIVZERO CC bit and not crash.
     */
    private static void testDVDByZero() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("DVD", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 17);
        s.setGPR(2, 0);
        s.setCC(0);

        cpu.step();

        check(
            "DVD divide by zero",
            (s.getCC() & 0b0100) != 0,
            "DVD should set DIVZERO flag when divisor is zero"
        );
    }

    /**
     * DVD invalid register pair:
     * rx and ry must be 0 or 2.
     */
    private static void testDVDInvalidPair() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("DVD", 3, 0);

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "DVD invalid pair",
            cpu.isHalted() && log.toLowerCase().contains("requires"),
            "CPU should halt on invalid DVD register pair"
        );
    }

    /**
     * TRR equal:
     * If registers are equal, set equality CC bit.
     */
    private static void testTRREqual() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("TRR", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 25);
        s.setGPR(2, 25);
        s.setCC(0);

        cpu.step();

        check(
            "TRR equal",
            (s.getCC() & 0b1000) != 0,
            "TRR should set equality flag when registers match"
        );
    }

    /**
     * TRR not equal:
     * If registers differ, clear equality CC bit.
     */
    private static void testTRRNotEqual() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("TRR", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 25);
        s.setGPR(2, 26);
        s.setCC(0b1000);

        cpu.step();

        check(
            "TRR not equal",
            (s.getCC() & 0b1000) == 0,
            "TRR should clear equality flag when registers differ"
        );
    }

    /**
     * AND basic:
     * R0 <- R0 AND R2
     */
    private static void testANDBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("AND", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 0b1100);
        s.setGPR(2, 0b1010);

        cpu.step();

        check(
            "AND basic",
            s.getGPR(0) == 0b1000,
            "R0 should become bitwise AND of R0 and R2"
        );
    }

    /**
     * ORR basic:
     * R0 <- R0 OR R2
     */
    private static void testORRBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeRegReg("ORR", 0, 2);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 0b1100);
        s.setGPR(2, 0b1010);

        cpu.step();

        check(
            "ORR basic",
            s.getGPR(0) == 0b1110,
            "R0 should become bitwise OR of R0 and R2"
        );
    }

    /**
     * NOT basic:
     * R3 <- NOT R3
     */
    private static void testNOTBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeNot(3);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(3, 0x00F0);

        cpu.step();

        check(
            "NOT basic",
            s.getGPR(3) == ((~0x00F0) & 0xFFFF),
            "R3 should become bitwise NOT of original value"
        );
    }

    // =====================================================
    // Shift / Rotate Tests
    // =====================================================

    /**
     * SRC left logical:
     * SRC R3,3,1,1
     *
     * Example from ISA:
     * if r3 = 0000 0000 0000 0110,
     * shifting left by 3 yields 0000 0000 0011 0000.
     */
    private static void testSRCLeftLogical() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("SRC", 3, 3, 1, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(3, 0x0006);

        cpu.step();

        check(
            "SRC left logical",
            s.getGPR(3) == 0x0030,
            "R3 should shift left by 3"
        );
    }

    /**
     * SRC right logical:
     * shift zeros into the high bits.
     *
     * Example:
     * 0x8006 logically shifted right by 2 becomes 0x2001.
     */
    private static void testSRCRightLogical() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("SRC", 1, 2, 0, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0x8006);

        cpu.step();

        check(
            "SRC right logical",
            s.getGPR(1) == 0x2001,
            "R1 should shift right logically by 2"
        );
    }

    /**
     * SRC right arithmetic:
     * sign bit should be replicated on each right shift.
     *
     * ISA example:
     * starting from 1 000 000 000 000 110 and shifting right arithmetically
     * by 2 keeps the sign bit at 1. :contentReference[oaicite:2]{index=2}
     */
    private static void testSRCRightArithmetic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("SRC", 1, 2, 0, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0x8006);

        cpu.step();

        check(
            "SRC right arithmetic",
            s.getGPR(1) == 0xE001,
            "R1 should shift right arithmetically by 2 with sign extension"
        );
    }

    /**
     * SRC count = 0:
     * ISA says no shift occurs.
     */
    private static void testSRCZeroCount() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("SRC", 0, 0, 1, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 0x1234);

        cpu.step();

        check(
            "SRC zero count",
            s.getGPR(0) == 0x1234,
            "R0 should remain unchanged when count is 0"
        );
    }

    /**
     * RRC left:
     * rotate left by 1 on 16 bits.
     *
     * Example:
     * 0x8001 rotated left by 1 becomes 0x0003.
     */
    private static void testRRCLeft() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("RRC", 2, 1, 1, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 0x8001);

        cpu.step();

        check(
            "RRC left",
            s.getGPR(2) == 0x0003,
            "R2 should rotate left by 1"
        );
    }

    /**
     * RRC right:
     * rotate right by 1 on 16 bits.
     *
     * Example:
     * 0x8001 rotated right by 1 becomes 0xC000.
     */
    private static void testRRCRight() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("RRC", 2, 1, 0, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 0x8001);

        cpu.step();

        check(
            "RRC right",
            s.getGPR(2) == 0xC000,
            "R2 should rotate right by 1"
        );
    }

    /**
     * RRC count = 0:
     * ISA says no rotate occurs.
     */
    private static void testRRCZeroCount() {
        Memory mem = new Memory();
        MachineState s = new MachineState();
        CPU cpu = newCPU(mem, s);

        int instr = ENCODER.encodeShiftRotate("RRC", 3, 0, 0, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(3, 0xAAAA);

        cpu.step();

        check(
            "RRC zero count",
            s.getGPR(3) == 0xAAAA,
            "R3 should remain unchanged when count is 0"
        );
    }

    // =====================================================
    // I/O Tests
    // =====================================================

    /**
     * IN basic:
     * Read one character from keyboard device 0 into R0.
     */
    private static void testINKeyboardBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        final int[] input = { 'A' };
        CPU cpu = newCPU(mem, s, () -> input[0], value -> {});

        int instr = ENCODER.encodeIO("IN", 0, 0);

        mem.write(0, instr);
        s.setPC(0);

        cpu.step();

        check(
            "IN keyboard basic",
            s.getGPR(0) == 'A',
            "R0 should receive character code for 'A'"
        );
    }

    /**
     * IN with no available input should not halt.
     */
    private static void testINNoInputAvailable() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        CPU cpu = newCPU(mem, s, () -> -1, value -> {});

        int instr = ENCODER.encodeIO("IN", 1, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0);

        String log = cpu.step();

        check(
            "IN no input",
            !cpu.isHalted() && s.getGPR(1) == 0 && log.toLowerCase().contains("no input"),
            "CPU should not halt when keyboard buffer is empty"
        );
    }

    /**
     * OUT basic:
     * Output one character from R2 to printer device 1.
     */
    private static void testOUTPrinterBasic() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        StringBuilder printer = new StringBuilder();
        CPU cpu = newCPU(mem, s, () -> -1, value -> printer.append((char) value));

        int instr = ENCODER.encodeIO("OUT", 2, 1);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(2, 'Z');

        cpu.step();

        check(
            "OUT printer basic",
            "Z".contentEquals(printer),
            "Printer should receive character 'Z'"
        );
    }

    /**
     * IN invalid device:
     * IN should only allow device 0 or 2.
     */
    private static void testINInvalidDevice() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        CPU cpu = newCPU(mem, s, () -> 'A', value -> {});

        int instr = ENCODER.encodeIO("IN", 0, 1);

        mem.write(0, instr);
        s.setPC(0);

        String log = cpu.step();

        check(
            "IN invalid device",
            cpu.isHalted() && log.toLowerCase().contains("only supports"),
            "CPU should halt on invalid IN device"
        );
    }

    /**
     * OUT invalid device:
     * OUT should only allow device 1.
     */
    private static void testOUTInvalidDevice() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        StringBuilder printer = new StringBuilder();
        CPU cpu = newCPU(mem, s, () -> -1, value -> printer.append((char) value));

        int instr = ENCODER.encodeIO("OUT", 0, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(0, 'X');

        String log = cpu.step();

        check(
            "OUT invalid device",
            cpu.isHalted() && log.toLowerCase().contains("only supports"),
            "CPU should halt on invalid OUT device"
        );
    }

    /**
     * IN with no available input should wait by restoring PC
     * to the same instruction instead of skipping past it.
     */
    private static void testINWaitRestoresPC() {
        Memory mem = new Memory();
        MachineState s = new MachineState();

        CPU cpu = newCPU(mem, s, () -> -1, value -> {});

        int instr = ENCODER.encodeIO("IN", 1, 0);

        mem.write(0, instr);
        s.setPC(0);
        s.setGPR(1, 0);

        String log = cpu.step();

        check(
            "IN wait restores PC",
            !cpu.isHalted()
                && s.getPC() == 0
                && s.getGPR(1) == 0
                && log.toLowerCase().contains("waiting"),
            "IN should wait at the same PC when no input is available"
        );
    }

    // =====================================================
    // Helpers
    // =====================================================

    /**
     * Create a CPU for a test.
     * Build a CPU for one isolated instruction test.
     *
     * Each test gets:
     * - a fresh backing Memory
     * - a fresh unified Cache in front of that memory
     * - a fresh MachineState
     *
     * @param mem   backing memory for the test
     * @param s     machine state for the test
     * @return      CPU wired to a fresh cache
     */
    private static CPU newCPU(Memory mem, MachineState s) {
        Cache cache = new Cache(mem);
        return new CPU(cache, s);
    }

    /**
     * Overloaded newCPU for I/O tests that need custom input/output handlers.
     * 
     * @param mem           backing memory for the test
     * @param s             machine state for the test
     * @param inputReader   function to read input values for IN instruction
     * @param outputWriter  function to handle output values from OUT instruction
     * @return              CPU wired to a fresh cache
     */
    private static CPU newCPU(Memory mem, MachineState s,
                            IntSupplier inputReader,
                            IntConsumer outputWriter) {
        Cache cache = new Cache(mem);
        return new CPU(cache, s, inputReader, outputWriter);
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