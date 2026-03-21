package simulator.tests;

import simulator.cache.Cache;
import simulator.machine.Memory;

/**
 * Cache tests for Cache.java and CacheLine.java.
 * - verify basic cache behavior
 * - check read hits/misses
 * - check write-through behavior
 * - check write-allocate behavior
 * - check FIFO replacement
 */
public final class CacheTests {

    private static int passed = 0;
    private static int failed = 0;

    private CacheTests() {}

    /**
     * Run all cache tests.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("Cache Tests");
        System.out.println("=====================================================");

        testReadMissThenHit();
        testWriteHitUpdatesBackingMemory();
        testWriteMissAllocatesLine();
        testFifoReplacement();

        System.out.println();
        System.out.println("=====================================================");
        System.out.println("Cache Test Summary");
        System.out.println("=====================================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    /**
     * Verify that the first read of an address is a miss and the second read
     * of the same address is a hit.
     */
    private static void testReadMissThenHit() {
        Memory memory = new Memory();
        Cache cache = new Cache(memory);

        // Put a known value in backing memory.
        memory.write(10, 012345);

        long hitsBefore = cache.getHitCount();
        long missesBefore = cache.getMissCount();

        int firstRead = cache.read(10);
        int secondRead = cache.read(10);

        long hitsAfter = cache.getHitCount();
        long missesAfter = cache.getMissCount();

        check(
                "read miss then hit - values",
                firstRead == 012345 && secondRead == 012345,
                "Expected both reads to return 012345."
        );

        check(
                "read miss then hit - counters",
                (missesAfter - missesBefore) == 1 && (hitsAfter - hitsBefore) == 1,
                "Expected first read to miss and second read to hit."
        );
    }

    /**
     * Verify that a write hit updates both the cache line and the backing memory.
     */
    private static void testWriteHitUpdatesBackingMemory() {
        Memory memory = new Memory();
        Cache cache = new Cache(memory);

        // Seed backing memory and pull the word into cache first.
        memory.write(20, 000111);
        cache.read(20);

        long hitsBefore = cache.getHitCount();
        long missesBefore = cache.getMissCount();

        // This should be a write hit because address 20 is already cached.
        cache.write(20, 000222);

        int memoryValue = memory.read(20);
        int cachedRead = cache.read(20);

        long hitsAfter = cache.getHitCount();
        long missesAfter = cache.getMissCount();

        check(
                "write hit updates backing memory",
                memoryValue == 000222,
                "Expected backing memory at address 20 to become 000222."
        );

        check(
                "write hit updates cached value",
                cachedRead == 000222,
                "Expected cached read at address 20 to return 000222."
        );

        check(
                "write hit counter behavior",
                (hitsAfter - hitsBefore) >= 2 && (missesAfter - missesBefore) == 0,
                "Expected write hit plus later read hit, with no new miss."
        );
    }

    /**
     * Verify that a write miss stores into backing memory and allocates the line
     * so the next read becomes a hit.
     */
    private static void testWriteMissAllocatesLine() {
        Memory memory = new Memory();
        Cache cache = new Cache(memory);

        long hitsBefore = cache.getHitCount();
        long missesBefore = cache.getMissCount();

        // Address 30 is not cached yet, so this should be a write miss.
        cache.write(30, 000333);

        int memoryValue = memory.read(30);
        int cachedRead = cache.read(30);

        long hitsAfter = cache.getHitCount();
        long missesAfter = cache.getMissCount();

        check(
                "write miss updates backing memory",
                memoryValue == 000333,
                "Expected backing memory at address 30 to become 000333."
        );

        check(
                "write miss allocates line",
                cachedRead == 000333,
                "Expected subsequent read of address 30 to return cached 000333."
        );

        check(
                "write miss counter behavior",
                (missesAfter - missesBefore) == 1 && (hitsAfter - hitsBefore) >= 1,
                "Expected one miss on write and at least one later hit on read."
        );
    }

    /**
     * Verify FIFO replacement:
     * - fill all 16 cache lines with distinct addresses
     * - access one more distinct address to evict the oldest line
     * - re-read the oldest address and confirm it misses again
     */
    private static void testFifoReplacement() {
        Memory memory = new Memory();
        Cache cache = new Cache(memory);

        // Fill backing memory with recognizable values.
        for (int i = 0; i < Cache.DEFAULT_LINE_COUNT + 1; i++) {
            memory.write(i, 010000 + i);
        }

        // Fill the cache with the first 16 distinct addresses.
        for (int i = 0; i < Cache.DEFAULT_LINE_COUNT; i++) {
            cache.read(i);
        }

        long hitsBefore = cache.getHitCount();
        long missesBefore = cache.getMissCount();

        // This 17th distinct read should evict address 0 by FIFO.
        cache.read(Cache.DEFAULT_LINE_COUNT);

        // Re-read address 0. If FIFO replacement worked, address 0 was evicted,
        // so this should miss and then be reloaded.
        int rereadZero = cache.read(0);

        long hitsAfter = cache.getHitCount();
        long missesAfter = cache.getMissCount();

        check(
                "fifo replacement reread value",
                rereadZero == 010000,
                "Expected reread of address 0 to return its original value."
        );

        check(
                "fifo replacement counters",
                (missesAfter - missesBefore) == 2,
                "Expected one miss for the 17th distinct access and one miss when rereading evicted address 0."
        );

        check(
                "fifo replacement no false hit",
                (hitsAfter - hitsBefore) == 0,
                "Expected no new hit during the FIFO eviction test window."
        );
    }


    /* ==========================
     * Helpers
     * ========================== */

    /**
     * Record one test result.
     *
     * @param name              test name
     * @param condition         true if passing
     * @param failureMessage    explanation shown on failure
     */
    private static void check(String name, boolean condition, String failureMessage) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
            System.out.println("       " + failureMessage);
        }
    }
}