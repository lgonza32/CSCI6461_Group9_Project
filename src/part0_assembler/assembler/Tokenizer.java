package part0_assembler.assembler;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer parses raw source lines into AsmLine objects.
 *
 * Rules from spec screenshots:
 *  - Comments begin with ';' and continue to end-of-line.
 *  - Optional label field: "Label:" at the start of the line.
 *  - Next token is op/directive/mnemonic.
 *  - Operands are comma-separated: "1,2,10,1"
 */
public final class Tokenizer {

    /**
     * Split a line into (code part) + (comment part).
     * Only ';' starts a comment for this project spec.
     */
    public Split splitComment(String originalLine) {
        int semi = originalLine.indexOf(';');
        if (semi < 0) {
            // No comment
            return new Split(originalLine, "");
        }
        // code is everything before ';'
        String code = originalLine.substring(0, semi);
        // comment is everything after ';'
        String comment = originalLine.substring(semi + 1).trim();
        return new Split(code, comment);
    }

    /**
     * Parse one raw line into an AsmLine:
     *  - detect optional label
     *  - read op
     *  - parse comma-separated operands
     */
    public AsmLine parseLine(int lineNo, String originalLine) {
        Split sc = splitComment(originalLine);

        // remove whitespace from code portion to decide if line is empty
        String trimmed = sc.codePart.trim();
        if (trimmed.isEmpty()) {
            // Blank/comment-only line => op null
            return new AsmLine(lineNo, originalLine, sc.commentText, null, null, List.of());
        }

        // Split remaining code by whitespace first
        String[] tokens = trimmed.split("\\s+");
        int idx = 0;

        // first token ending with ':'
        String label = null;
        if (tokens[idx].endsWith(":")) {
            label = tokens[idx].substring(0, tokens[idx].length() - 1);
            idx++;
        }

        // label-only line (rare) => no op
        if (idx >= tokens.length) {
            return new AsmLine(lineNo, originalLine, sc.commentText, label, null, List.of());
        }

        // next token is op/directive/mnemonic
        String op = tokens[idx];
        idx++;

        // rebuild the rest of the tokens into one operand string, then split by commas
        String operandString = "";
        if (idx < tokens.length) {
            StringBuilder sb = new StringBuilder();
            for (int j = idx; j < tokens.length; j++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(tokens[j]);
            }
            operandString = sb.toString().trim();
        }

        List<String> operands = parseOperands(operandString);
        return new AsmLine(lineNo, originalLine, sc.commentText, label, op, operands);
    }

    /**
     * Split operands on commas, trimming whitespace.
     * Example: "1, 2,10, 1" => ["1","2","10","1"]
     */
    private List<String> parseOperands(String operandString) {
        if (operandString == null || operandString.isEmpty()) return new ArrayList<>();

        String[] parts = operandString.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 
     * Simple record for comment splitting. 
     */
    public static final class Split {
        public final String codePart;
        public final String commentText;

        public Split(String codePart, String commentText) {
            this.codePart = codePart;
            this.commentText = commentText;
        }
    }
}