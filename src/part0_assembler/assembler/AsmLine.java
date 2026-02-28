package part0_assembler.assembler;

import java.util.List;

/**
 * Represents one line of the .asm source after parsing.
 *
 * We keep both:
 *  - the original line (for listing output), and
 *  - the parsed pieces (label/op/operands/comment) for assembly logic.
 *
 * Pass 1 fills in address/allocates/isLoc.
 * Pass 2 uses those fields to emit listing + load files.
 */
public final class AsmLine {

    public final int lineNo;
    public final String originalLine;
    public final String commentText;
    public final String label;
    public final String op;

    // operand tokens split by commas, with whitespace trimmed.
    // Example: "LDR 3,0,10" -> ["3","0","10"]
    public final List<String> operands;

    /* ============================
     * Pass-1 computed metadata
     * ============================ */

    public Integer address; // location
    public boolean allocates; // instructions
    public boolean isLoc; // true if this is a LOC directive line (sets LC but does not allocate memory).

    public AsmLine(int lineNo, String originalLine, String commentText,
                   String label, String op, List<String> operands) {
        this.lineNo = lineNo;
        this.originalLine = originalLine;
        this.commentText = commentText;
        this.label = label;
        this.op = op;
        this.operands = operands;
    }
}