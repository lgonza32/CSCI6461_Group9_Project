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
     * @param asmPath path to input .asm file
     * @param listingOut path to output listing file
     * @param loadOut path to output load file
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
     * Compute the 16-bit machine word for a line.
     *
     * data:
     *  - stores literal decimal or the decimal address of a label
     *
     * HLT:
     *  - emits 0, matching the example figure
     *
     * LDX/STX:
     *  - source uses x,address[,I]
     *  - Encoder expects r,x,address[,I]
     *  - we adapt by inserting r=0
     *
     * Everything else:
     *  - uses Encoder for basic format: r,x,address[,I]
     */
    private int computeWord(AsmLine al, SymbolTable symtab) {
        String op = al.op.toUpperCase(Locale.ROOT);

        // data directive
        if (op.equals("DATA")) {
            if (al.operands.size() != 1) {
                throw new RuntimeException("Data expects 1 operand at line " + al.lineNo);
            }
            int value = parseDecimalOrLabel(al.operands.get(0), symtab, al.lineNo);
            return value;
        }

        // HLT has no operands; 
        // output word=0 matches sample listing/load
        if (op.equals("HLT")) {
            return 0;
        }

        // resolve label operands to decimal literals
        List<String> ops = new ArrayList<>();
        for (String tok : al.operands) {
            if (NumberUtil.isDecimalLiteral(tok)) {
                ops.add(tok);
            } else {
                Integer addr = symtab.get(tok);
                if (addr == null) {
                    throw new RuntimeException("Unknown label '" + tok + "' at line " + al.lineNo);
                }
                ops.add(Integer.toString(addr));
            }
        }

        // LDX/STX adaptation (x,address[,I] -> r=0,x,address[,I])
        if (op.equals("LDX") || op.equals("STX")) {
            if (ops.size() != 2 && ops.size() != 3) {
                throw new RuntimeException(op + " expects x,address[,I] at line " + al.lineNo);
            }

            List<String> adapted = new ArrayList<>();
            adapted.add("0");        // r=0 unused
            adapted.add(ops.get(0)); // ix (x)
            adapted.add(ops.get(1)); // address
            if (ops.size() == 3) adapted.add(ops.get(2)); // I bit

            return encoder.encodeFormat(op, adapted);
        }

        // Default: basic format r,x,address[,I]
        return encoder.encodeFormat(op, ops);
    }

    /**
     * Parse token as:
     *  - DECIMAL literal, or
     *  - label reference (resolved through SymbolTable)
     */
    private int parseDecimalOrLabel(String token, SymbolTable symtab, int lineNo) {
        if (NumberUtil.isDecimalLiteral(token)) {
            return Integer.parseInt(token);
        }
        Integer addr = symtab.get(token);
        if (addr == null) {
            throw new RuntimeException("Unknown label '" + token + "' at line " + lineNo);
        }
        return addr;
    }

    /**
     * Rebuild the right-side listing "source" column.
     * Example:
     *  "End: HLT ;STOP"
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
}