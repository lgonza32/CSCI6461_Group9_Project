package simulator.cache;

import simulator.machine.Memory;

/**
 * Simple fully associative unified cache.
 * - Fully associative: any address may be stored in any cache line
 * - Unified: instruction fetches and data accesses use the same cache
 * - FIFO replacement: evict the oldest inserted cache line when full
 * - One word per line: matches the simulator's current Memory API
 *
 * Current write policy:
 * - Write-through
 * - Write-allocate
 *
 * That means:
 * - Reads use the cache first, then memory on a miss
 * - Writes always update backing memory
 * - Writes also update the cache and allocate a line on a miss
 */
public final class Cache {

    public static final int DEFAULT_LINE_COUNT = 16;
    private final Memory backingMemory;
    private final CacheLine[] lines;
    private long nextFifoOrder = 1L;
    // for debugging
    private long hitCount = 0L;
    private long missCount = 0L;
    private long accessCount = 0L;
    private String lastAccessSummary = "[CACHE] No accesses yet.";

    /**
     * Construct a 16-line cache backed by the given memory.
     *
     * @param backingMemory real memory backing store
     */
    public Cache(Memory backingMemory) {
        this(backingMemory, DEFAULT_LINE_COUNT);
    }

    /**
     * Construct a cache with a caller-specified number of lines.
     *
     * @param backingMemory real memory backing store
     * @param lineCount     number of cache lines
     * @throws IllegalArgumentException if backingMemory is null or lineCount is not positive
     */
    public Cache(Memory backingMemory, int lineCount) {
        if (backingMemory == null) {
            throw new IllegalArgumentException("Backing memory cannot be null.");
        }
        if (lineCount <= 0) {
            throw new IllegalArgumentException("Cache line count must be positive.");
        }

        this.backingMemory = backingMemory;
        this.lines = new CacheLine[lineCount];

        for (int i = 0; i < lineCount; i++) {
            lines[i] = new CacheLine();
        }
    }

    /**
     * Read one 16-bit word from the cache.
     *
     * On a hit:
     * - return the cached word
     *
     * On a miss:
     * - fetch from backing memory
     * - insert into cache using FIFO replacement
     * - return the fetched word
     *
     * @param address   memory address
     * @return          cached or fetched 16-bit word
     */
    public int read(int address) {
        accessCount++;

        int hitIndex = findHitIndex(address);
        if (hitIndex >= 0) {
            hitCount++;
            int value = lines[hitIndex].getDataWord() & 0xFFFF;
            lastAccessSummary = "[CACHE] READ hit  addr="
                    + Memory.toOct6(address)
                    + " word=" + Memory.toOct6(value)
                    + " line=" + hitIndex;
            return value;
        }

        missCount++;
        int value = backingMemory.read(address);
        int insertedAt = insertOrReplace(address, value);

        lastAccessSummary = "[CACHE] READ miss addr="
                + Memory.toOct6(address)
                + " word=" + Memory.toOct6(value)
                + " line=" + insertedAt;

        return value & 0xFFFF;
    }

    /**
     * Write one 16-bit word through the cache.
     * - Always update backing memory (write-through)
     * - On hit, update the cache line too
     * - On miss, allocate a cache line and store the new word there
     *
     * @param address   memory address
     * @param word      16-bit word to write
     */
    public void write(int address, int word) {
        accessCount++;

        // always update real memory
        backingMemory.write(address, word);
        int maskedWord = word & 0xFFFF;

        int hitIndex = findHitIndex(address);
        if (hitIndex >= 0) {
            hitCount++;
            lines[hitIndex].updateWord(maskedWord);

            lastAccessSummary = "[CACHE] WRITE hit  addr="
                    + Memory.toOct6(address)
                    + " word=" + Memory.toOct6(maskedWord)
                    + " line=" + hitIndex;
            return;
        }

        missCount++;
        int insertedAt = insertOrReplace(address, maskedWord);

        lastAccessSummary = "[CACHE] WRITE miss addr="
                + Memory.toOct6(address)
                + " word=" + Memory.toOct6(maskedWord)
                + " line=" + insertedAt;
    }

    /**
     * Expose a single cache line for display/debug purposes.
     *
     * @param index                     cache line index
     * @return                          cache line object
     * @throws IllegalArgumentException if index is out of range
     */
    public CacheLine getLine(int index) {
        if (index < 0 || index >= lines.length) {
            throw new IllegalArgumentException("Cache line index out of range: " + index);
        }
        return lines[index];
    }

    /**
     * Build a human-readable dump of the cache for the GUI.
     *
     * @return multi-line cache contents text
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(lastAccessSummary).append('\n');
        sb.append("[CACHE] accesses=").append(accessCount)
          .append(" hits=").append(hitCount)
          .append(" misses=").append(missCount)
          .append('\n');

        for (int i = 0; i < lines.length; i++) {
            CacheLine line = lines[i];
            sb.append("L").append(i).append(": ");

            if (!line.isValid()) {
                sb.append("INVALID");
            } else {
                sb.append("addr=")
                  .append(Memory.toOct6(line.getAddressTag()))
                  .append(" word=")
                  .append(Memory.toOct6(line.getDataWord()))
                  .append(" fifo=")
                  .append(line.getFifoOrder());
            }

            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Find the cache line containing the given address.
     *
     * @param address   memory address
     * @return          matching line index, or -1 if not found
     */
    private int findHitIndex(int address) {
        for (int i = 0; i < lines.length; i++) {
            CacheLine line = lines[i];
            if (line.isValid() && line.getAddressTag() == address) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Insert a word into the cache.
     *
     * Strategy:
     * - first use any invalid line
     * - otherwise evict the oldest line by FIFO order
     *
     * @param address   memory address
     * @param word      16-bit data word
     * @return          index of the inserted/replaced cache line
     */
    private int insertOrReplace(int address, int word) {
        int invalidIndex = findFirstInvalidIndex();
        if (invalidIndex >= 0) {
            lines[invalidIndex].fill(address, word, nextFifoOrder++);
            return invalidIndex;
        }

        int victimIndex = findFifoVictimIndex();
        lines[victimIndex].fill(address, word, nextFifoOrder++);
        return victimIndex;
    }

    /**
     * Find the first invalid cache line.
     *
     * @return invalid line index, or -1 if none exists
     */
    private int findFirstInvalidIndex() {
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isValid()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the FIFO victim line.
     *
     * @return index of the oldest valid cache line
     */
    private int findFifoVictimIndex() {
        int victimIndex = 0;
        long oldestOrder = Long.MAX_VALUE;

        for (int i = 0; i < lines.length; i++) {
            CacheLine line = lines[i];
            if (line.isValid() && line.getFifoOrder() < oldestOrder) {
                oldestOrder = line.getFifoOrder();
                victimIndex = i;
            }
        }

        return victimIndex;
    }

    
    /* ==========================
     * Helpers
     * ========================== */

    /**
     * Clear the cache and reset statistics.
     *
     * This does not clear backing memory. It only clears the cache contents.
     */
    public void clear() {
        for (CacheLine line : lines) {
            line.invalidate();
        }

        nextFifoOrder = 1L;
        hitCount = 0L;
        missCount = 0L;
        accessCount = 0L;
        lastAccessSummary = "[CACHE] Cleared.";
    }
    
    /**
     * Return the most recent cache event summary.
     *
     * @return last access summary string
     */
    public String getLastAccessSummary() {
        return lastAccessSummary;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public int getLineCount() {
        return lines.length;
    }

}