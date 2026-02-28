package part0_assembler.assembler;

import java.io.PrintWriter;

/**
 * Writes the Listing Output File (Figure 4 style):
 *
 * LOC(Octal)  WORD(Octal)  source-code + comment
 *
 * For LOC directive lines:
 *  - LOC and WORD columns are blank (because LOC does not allocate memory).
 */
public final class ListingWriter {

    private final PrintWriter out;

    public ListingWriter(PrintWriter out) {
        this.out = out;
    }

    /** 
     * Write a formatted listing line with two octal columns. 
     */
    public void writeLine(String locOct, String wordOct, String source) {
        // %-6s keeps the columns aligned at width 6
        out.println(String.format("%-6s  %-6s  %s", locOct, wordOct, source));
    }

    /** 
     * Write the original line without formatting (blank/comment-only lines). 
     */
    public void writeRaw(String raw) {
        out.println(raw);
    }
}