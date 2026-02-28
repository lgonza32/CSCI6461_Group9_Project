package simulator.machine;

/**
 * Test for Memory.java
 * Run: java -cp out simulator.machine.MemoryTest
 */
public final class MemoryTest {
    public static void main(String[] args) {
        Memory mem = new Memory();

        mem.write(6, 10);            // store decimal 10
        mem.write(7, 3);             // store decimal 3
        mem.write(1024, 0);          // HLT is typically 0

        System.out.println("mem[6]  = " + Memory.toOct6(mem.read(6)) + " (expected 000012)");
        System.out.println("mem[7]  = " + Memory.toOct6(mem.read(7)) + " (expected 000003)");
        System.out.println("mem[1024]= " + Memory.toOct6(mem.read(1024)) + " (expected 000000)");

        mem.clear();
        System.out.println("after clear, mem[6] = " + Memory.toOct6(mem.read(6)) + " (expected 000000)");
    }
}