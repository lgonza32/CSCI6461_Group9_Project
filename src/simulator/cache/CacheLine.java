package simulator.cache;

/**
 * One cache line in the simulator cache.
 * - Fully associative cache, so each line may hold any memory address.
 * - Unified cache, so the same line structure is used for both
 *   instruction fetches and normal data reads/writes.
 * - One memory word per line, which matches the simulator's current
 *   word-based Memory API and keeps Part II simple.
 */
public final class CacheLine {

    private boolean valid;
    private int addressTag;
    private int dataWord;
    private long fifoOrder;

    /**
     * Construct an invalid cache line.
     */
    public CacheLine() {
        this.valid = false;
        this.addressTag = 0;
        this.dataWord = 0;
        this.fifoOrder = 0L;
    }

    public boolean isValid() {
        return valid;
    }

    public int getAddressTag() {
        return addressTag;
    }

    public int getDataWord() {
        return dataWord;
    }

    public long getFifoOrder() {
        return fifoOrder;
    }

    /**
     * Fill or overwrite this cache line.
     *
     * @param addressTag full memory address for the cached word
     * @param dataWord cached 16-bit word
     * @param fifoOrder insertion order used for FIFO replacement
     */
    public void fill(int addressTag, int dataWord, long fifoOrder) {
        this.valid = true;
        this.addressTag = addressTag;
        this.dataWord = dataWord & 0xFFFF;
        this.fifoOrder = fifoOrder;
    }

    /**
     * Update only the cached data word while keeping the tag and FIFO age.
     * Useful on write hits.
     *
     * @param dataWord new 16-bit word value
     */
    public void updateWord(int dataWord) {
        this.dataWord = dataWord & 0xFFFF;
    }

    /**
     * Mark this cache line invalid.
     */
    public void invalidate() {
        this.valid = false;
        this.addressTag = 0;
        this.dataWord = 0;
        this.fifoOrder = 0L;
    }
}