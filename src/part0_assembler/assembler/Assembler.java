package part0_assembler.assembler;

import part0_assembler.Encoder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Full Assembler
 *
 * Reads .asm (source numbers are DECIMAL) and outputs:
 *  - Listing file (LOC/WORD columns in OCTAL)
 *  - Load file    (LOC WORD in OCTAL only)
 *
 * Two-pass design:
 *  PASS 1:
 *    - parse each line into AsmLine
 *    - build SymbolTable (label -> address)
 *    - compute addresses for Data/instruction lines
 *  PASS 2:
 *    - generate machine words (Data or encoded instructions)
 *    - write listing + load outputs
 */
public final class Assembler {

    // uses your existing Encoder.java for basic-format instruction packing.
    private final Encoder encoder = new Encoder();

    // parses raw lines into AsmLine objects.
    private final Tokenizer tokenizer = new Tokenizer();

    /**
     * Assemble a source file.
     *
     * @param asmPath       path to input .asm file
     * @param listingOut    path to output listing file
     * @param loadOut       path to output load file
     * @throws IOException  if file I/O fails
     */
    public void assemble(Path asmPath, Path listingOut, Path loadOut) throws IOException {
        // Read all source lines
        List<String> lines = Files.readAllLines(asmPath);

        // PASS 1: parse + symbols + address assignment
        SymbolTable symtab = new SymbolTable();
        List<AsmLine> parsed = pass1(lines, symtab);

        // PASS 2: emit listing + load
        try (PrintWriter listPw = new PrintWriter(Files.newBufferedWriter(listingOut));
             PrintWriter loadPw = new PrintWriter(Files.newBufferedWriter(loadOut))) {

            ListingWriter listing = new ListingWriter(listPw);
            LoadWriter load = new LoadWriter(loadPw);

            pass2(parsed, symtab, listing, load);
        }
    }

    /**
     * PASS 1:
     *  - defines labels to the current LC
     *  - handles LOC and sets LC
     *  - marks data + instruction lines as allocators with addresses
     * 
     * @param lines     raw source lines
     * @param symtab    symbol table to populate
     * @return          parsed AsmLine list with address/allocation metadata
     */
    private List<AsmLine> pass1(List<String> lines, SymbolTable symtab) {
        List<AsmLine> out = new ArrayList<>();
        int lc = 0; // location counter in DECIMAL

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;

            // parse raw line into AsmLine
            AsmLine al = tokenizer.parseLine(lineNo, lines.get(i));

            // blank/comment-only => keep for listing (raw) and move on
            if (al.op == null) {
                out.add(al);
                continue;
            }

            // if there is a label, it points to the current LC (before allocating/LOC changes)
            if (al.label != null) {
                if (symtab.contains(al.label)) {
                    throw new RuntimeException("Duplicate label '" + al.label + "' at line " + lineNo);
                }
                symtab.put(al.label, lc);
            }

            // sets LC; does not allocate memory
            if (al.op.equalsIgnoreCase("LOC")) {
                al.isLoc = true;
                al.allocates = false;

                // the new location (DECIMAL in source)
                if (al.operands.size() != 1) {
                    throw new RuntimeException("LOC expects 1 operand at line " + lineNo);
                }

                // operand may be decimal or a label
                lc = parseDecimalOrLabel(al.operands.get(0), symtab, lineNo);
                out.add(al);
                continue;
            }

            // data or instruction => allocate one word at current LC
            al.allocates = true;
            al.address = lc;

            // increment LC after reserving the word
            lc++;

            out.add(al);
        }

        return out;
    }

    /**
     * PASS 2:
     *  - For each allocatable line, compute 16-bit word
     *  - Emit listing and load file records in octal
     * 
     * @param parsed    parsed AsmLine list from pass 1
     * @param symtab    completed symbol table
     * @param listing   listing writer
     * @param load      load writer
     */
    private void pass2(List<AsmLine> parsed, SymbolTable symtab,
                       ListingWriter listing, LoadWriter load) {

        for (AsmLine al : parsed) {

            // blank/comment-only line: echo raw line into listing
            if (al.op == null) {
                listing.writeRaw(al.originalLine);
                continue;
            }

            // no LOC/WORD columns and no load file entry
            if (al.isLoc) {
                listing.writeLine("", "", rebuildSource(al));
                continue;
            }

            // non-allocating non-LOC lines
            if (!al.allocates) {
                listing.writeLine("", "", rebuildSource(al));
                continue;
            }

            // compute the 16-bit word for this line
            int word = computeWord(al, symtab) & 0xFFFF;

            // convert address + word to 6-digit octal
            String locOct = NumberUtil.toOct6(al.address);
            String wordOct = NumberUtil.toOct6(word);

            // listing includes the source text (directive/mnemonic + operands + comment)
            listing.writeLine(locOct, wordOct, rebuildSource(al));

            // load file contains only loc/word pairs
            load.writeWord(locOct, wordOct);
        }
    }

    /**
     * Compute the 16-bit machine word for one allocatable source line.
     *
     * This method acts as a dispatcher:
     * - directives/zero-operand instructions are handled directly
     * - operands are resolved once into integers
     * - the instruction is sent to a format-specific encoding helper
     *
     * @param al        parsed source line
     * @param symtab    symbol table for label resolution
     * @return          encoded machine word
     */
    private int computeWord(AsmLine al, SymbolTable symtab) {
        String op = al.op.toUpperCase(Locale.ROOT);

        // DATA stores a literal value or the decimal address of a label.
        if (op.equals("DATA")) {
            return encodeDataDirective(al, symtab);
        }

        // HLT is encoded as zero, matching the project examples.
        if (op.equals("HLT")) {
            return 0;
        }

        // Convert all operands into integer values before dispatching.
        List<Integer> ops = resolveOperands(al, symtab);

        // Route the instruction to the correct encoding format.
        if (isBasicFormatOp(op)) {
            return encodeBasicInstruction(op, ops, al.lineNo);
        }
        if (isXAddressFormatOp(op)) {
            return encodeXAddressInstruction(op, ops, al.lineNo);
        }
        if (isImmediateFormatOp(op)) {
            return encodeImmediateInstruction(op, ops, al.lineNo);
        }
        if (op.equals("RFS")) {
            return encodeRFSInstruction(ops, al.lineNo);
        }
        if (isRegRegFormatOp(op)) {
            return encodeRegRegInstruction(op, ops, al.lineNo);
        }
        if (op.equals("NOT")) {
            return encodeNotInstruction(ops, al.lineNo);
        }
        if (isShiftRotateFormatOp(op)) {
            return encodeShiftRotateInstruction(op, ops, al.lineNo);
        }
        if (isIOFormatOp(op)) {
            return encodeIOInstruction(op, ops, al.lineNo);
        }

        throw new RuntimeException(
                "Unsupported assembler opcode format for " + op + " at line " + al.lineNo
        );
    }

    /**
     * Rebuild the right-side listing "source" column.
     * Example:
     *  "End: HLT ;STOP"
     * 
     * @param al parsed source line
     * @return reconstructed source text
     */
    private String rebuildSource(AsmLine al) {
        StringBuilder sb = new StringBuilder();

        // print label if present
        if (al.label != null) {
            sb.append(al.label).append(": ");
        }

        // print op
        sb.append(al.op);

        // print operands (comma-separated, no spaces like the example)
        if (!al.operands.isEmpty()) {
            sb.append(" ").append(String.join(",", al.operands));
        }

        // print comment (prefixed with ';')
        if (al.commentText != null && !al.commentText.isBlank()) {
            sb.append(" ;").append(al.commentText);
        }

        return sb.toString();
    }

    /**
     * Resolve every operand token to an integer value.
     *
     * Each operand may be:
     * - a decimal literal
     * - a label
     * - a simple +/- expression involving labels and/or decimal literals
     *
     * Examples:
     * - 10
     * - LOOP
     * - WORK+5
     * - CURVAL-WORK
     *
     * @param al        source line whose operands will be resolved
     * @param symtab    symbol table for label lookup
     * @return          resolved integer operand list
     */
    private List<Integer> resolveOperands(AsmLine al, SymbolTable symtab) {
        List<Integer> resolved = new ArrayList<>();

        for (String tok : al.operands) {
            resolved.add(evaluateExpression(tok, symtab, al.lineNo));
        }

        return resolved;
    }

    /* ==========================
     * Helpers for Formatting
     * ========================== */

    /**
     * Determine whether an opcode uses the basic format:
     * OP r,x,address[,I]
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the basic format
     */
    private boolean isBasicFormatOp(String op) {
        return op.equals("LDR") || op.equals("STR") || op.equals("LDA")
                || op.equals("JZ") || op.equals("JNE") || op.equals("JCC")
                || op.equals("SOB") || op.equals("JGE")
                || op.equals("AMR") || op.equals("SMR");
    }

    /**
     * Determine whether an opcode uses the x,address format:
     * OP x,address[,I]
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the x,address format
     */
    private boolean isXAddressFormatOp(String op) {
        return op.equals("LDX") || op.equals("STX")
                || op.equals("JMA") || op.equals("JSR");
    }

    /**
     * Determine whether an opcode uses the immediate format:
     * OP r,immed
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the immediate format
     */
    private boolean isImmediateFormatOp(String op) {
        return op.equals("AIR") || op.equals("SIR");
    }

    /**
     * Determine whether an opcode uses the register-to-register format:
     * OP rx,ry
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the rx,ry format
     */
    private boolean isRegRegFormatOp(String op) {
        return op.equals("MLT") || op.equals("DVD")
                || op.equals("TRR") || op.equals("AND")
                || op.equals("ORR");
    }

    /**
     * Determine whether an opcode uses the shift/rotate format:
     * OP r,count,L/R,A/L
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the shift/rotate format
     */
    private boolean isShiftRotateFormatOp(String op) {
        return op.equals("SRC") || op.equals("RRC");
    }

    /**
     * Determine whether an opcode uses the I/O format:
     * OP r,devid
     *
     * @param op    uppercase mnemonic
     * @return      true if the instruction uses the I/O format
     */
    private boolean isIOFormatOp(String op) {
        return op.equals("IN") || op.equals("OUT") || op.equals("CHK");
    }

    /**
     * Enforce valid operand counts for an instruction format.
     *
     * Some formats allow either one exact count or one of two counts
     *
     * @param op            mnemonic being validated
     * @param actualCount   actual number of operands present
     * @param lineNo        source line number
     * @param expectedA     first valid count
     * @param expectedB     second valid count, or null if only one count is legal
     * @param usage         usage string shown in the error message
     */
    private void requireOperandCount(
            String op, int actualCount, int lineNo,
            int expectedA, Integer expectedB, String usage) {

        boolean ok = (actualCount == expectedA)
                || (expectedB != null && actualCount == expectedB);

        if (!ok) {
            throw new RuntimeException(op + " expects " + usage + " at line " + lineNo);
        }
    }

    /**
     * Encode a DATA directive.
     *
     * DATA stores exactly one value:
     * - a decimal literal, or
     * - the decimal address of a label
     *
     * @param al        source line
     * @param symtab    symbol table
     * @return          value to store in memory
     */
    private int encodeDataDirective(AsmLine al, SymbolTable symtab) {
        if (al.operands.size() != 1) {
            throw new RuntimeException("DATA expects 1 operand at line " + al.lineNo);
        }
        return parseDecimalOrLabel(al.operands.get(0), symtab, al.lineNo);
    }

    /**
     * Encode a basic-format instruction:
     * OP r,x,address[,I]
     *
     * @param op        mnemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeBasicInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 3, 4, "r,x,address[,I]");

        int r = ops.get(0);
        int ix = ops.get(1);
        int address = ops.get(2);
        int indirect = (ops.size() == 4) ? ops.get(3) : 0;

        return encoder.encodeBasicIndirect(op, r, ix, address, indirect);
    }

    /**
     * Encode an x,address-format instruction:
     * OP x,address[,I]
     *
     * @param op        mnemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeXAddressInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 2, 3, "x,address[,I]");

        int x = ops.get(0);
        int address = ops.get(1);
        int indirect = (ops.size() == 3) ? ops.get(2) : 0;

        return encoder.encodeXAddressIndirect(op, x, address, indirect);
    }

    /**
     * Encode an immediate-format instruction:
     * OP r,immed
     *
     * @param op        mnemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeImmediateInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 2, null, "r,immed");

        int r = ops.get(0);
        int immed = ops.get(1);

        return encoder.encodeImmediate(op, r, immed);
    }

    /**
     * Encode an RFS instruction:
     * RFS immed
     *
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeRFSInstruction(List<Integer> ops, int lineNo) {
        requireOperandCount("RFS", ops.size(), lineNo, 1, null, "immed");
        return encoder.encodeRFS(ops.get(0));
    }

    /**
     * Encode a register-to-register instruction:
     * OP rx,ry
     *
     * @param op        mnemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeRegRegInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 2, null, "rx,ry");

        int rx = ops.get(0);
        int ry = ops.get(1);

        return encoder.encodeRegReg(op, rx, ry);
    }

    /**
     * Encode a NOT instruction:
     * NOT rx
     *
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeNotInstruction(List<Integer> ops, int lineNo) {
        requireOperandCount("NOT", ops.size(), lineNo, 1, null, "rx");
        return encoder.encodeNot(ops.get(0));
    }

    /**
     * Encode a shift/rotate instruction:
     * OP r,count,L/R,A/L
     *
     * @param op        mnemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeShiftRotateInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 4, null, "r,count,L/R,A/L");

        int r = ops.get(0);
        int count = ops.get(1);
        int lr = ops.get(2);
        int al = ops.get(3);

        return encoder.encodeShiftRotate(op, r, count, lr, al);
    }

    /**
     * Encode an I/O instruction:
     * OP r,devid
     *
     * @param op        nemonic
     * @param ops       resolved integer operands
     * @param lineNo    source line number
     * @return          encoded instruction word
     */
    private int encodeIOInstruction(String op, List<Integer> ops, int lineNo) {
        requireOperandCount(op, ops.size(), lineNo, 2, null, "r,devid");

        int r = ops.get(0);
        int deviceId = ops.get(1);

        return encoder.encodeIO(op, r, deviceId);
    }

    /**
     * Resolve one term in an operand expression.
     *
     * A term is either:
     * - a decimal literal
     * - a label found in the symbol table
     *
     * @param term      expression term
     * @param symtab    symbol table
     * @param lineNo    source line number
     * @return          resolved integer value
     */
    private int resolveTerm(String term, SymbolTable symtab, int lineNo) {
        if (NumberUtil.isDecimalLiteral(term)) {
            return Integer.parseInt(term);
        }

        Integer addr = symtab.get(term);
        if (addr == null) {
            throw new RuntimeException(
                    "Unknown label '" + term + "' at line " + lineNo
            );
        }
        return addr;
    }

    /**
     * Evaluate a simple integer expression used as an assembler operand.
     *
     * Supported operators:
     * - plus (+)
     * - minus (-)
     *
     * Supported terms:
     * - decimal literals
     * - labels.
     *
     * Examples:
     * - 15
     * - LOOP
     * - WORK+5
     * - CURVAL-WORK
     * - NUMBERS+3-WORKBASE
     *
     * @param token     raw operand token
     * @param symtab    symbol table
     * @param lineNo    source line number
     * @return          evaluated integer result
     */
    private int evaluateExpression(String token, SymbolTable symtab, int lineNo) {
        if (token == null) {
            throw new RuntimeException("Null operand at line " + lineNo);
        }

        // remove all whitespace so forms like "WORK + 5" also work
        String expr = token.replaceAll("\\s+", "");
        if (expr.isEmpty()) {
            throw new RuntimeException("Empty operand at line " + lineNo);
        }

        int total = 0;
        int sign = 1;
        int i = 0;

        // allow an optional leading + or -.
        if (expr.charAt(0) == '+') {
            i++;
        } else if (expr.charAt(0) == '-') {
            sign = -1;
            i++;
        }

        StringBuilder term = new StringBuilder();

        while (i <= expr.length()) {
            boolean end = (i == expr.length());
            char ch = end ? '\0' : expr.charAt(i);

            // end of term when we hit +, -, or the end of the string
            if (end || ch == '+' || ch == '-') {
                if (term.length() == 0) {
                    throw new RuntimeException(
                            "Malformed expression '" + token + "' at line " + lineNo
                    );
                }

                int value = resolveTerm(term.toString(), symtab, lineNo);
                total += sign * value;

                term.setLength(0);

                if (!end) {
                    sign = (ch == '+') ? 1 : -1;
                }
            } else {
                term.append(ch);
            }
            i++;
        }
        return total;
    }

    /**
     * Parse one token as an integer expression.
     *
     * Supported forms:
     * - decimal literal: 15
     * - label: LOOP
     * - label +/- decimal: WORK+5, WORK-2
     * - label +/- label: CURVAL-WORK
     * - chained left-to-right forms: NUMBERS+5-WORKBASE
     *
     * @param token     operand token
     * @param symtab    symbol table
     * @param lineNo    source line number
     * @return          resolved integer value
     */
    private int parseDecimalOrLabel(String token, SymbolTable symtab, int lineNo) {
        return evaluateExpression(token, symtab, lineNo);
    }
    
}