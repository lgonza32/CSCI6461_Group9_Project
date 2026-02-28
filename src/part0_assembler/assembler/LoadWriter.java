package part0_assembler.assembler;

import java.io.PrintWriter;

/**
 * Writes the Load File (Figure 5 style):
 *
 * LOC(Octal) WORD(Octal)
 *
 * Only lines that allocate a word are emitted here.
 * LOC directives and blank/comment lines do not produce load file output.
 */
public final class LoadWriter {

    private final PrintWriter out;

    public LoadWriter(PrintWriter out) {
        this.out = out;
    }

    public void writeWord(String locOct, String wordOct) {
        out.println(locOct + " " + wordOct);
    }
}