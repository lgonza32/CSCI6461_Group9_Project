package simulator.io;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ProgramLoader parses an assembler "load file" into (address, word) records.
 *
 * Input format:
 *  - One record per line: <address> <word>
 *  - Supports:
 *      * octal numbers (e.g., 0012 1777)
 *      * decimal numbers (e.g., 10 255)
 *      * hex numbers with 0x prefix (e.g., 0x0A 0xFF)
 *  - Ignores blank lines and comment lines starting with:
 *      #, //, ;
 */
public final class ProgramLoader {

    /** One parsed record: write "word" into memory at "address". */
    public static final class Record {
        public final int address;
        public final int word;

        public Record(int address, int word) {
            this.address = address;
            this.word = word;
        }
    }

    /** Parsed file summary + records. */
    public static final class LoadFile {
        public final List<Record> records;
        public final int recordsLoaded;
        public final int firstAddress;

        public LoadFile(List<Record> records, int firstAddress) {
            this.records = records;
            this.recordsLoaded = records.size();
            this.firstAddress = firstAddress;
        }
    }

    /**
     * Parse a load file into records.
     *
     * @param file program/load file
     * @return parsed LoadFile with records
     * @throws IOException if file can't be read
     * @throws IllegalArgumentException if a non-comment line is malformed
     */
    public LoadFile parse(File file) throws IOException {
        List<Record> out = new ArrayList<>();
        int firstAddr = -1;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNo = 0;

            while ((line = br.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();

                // Skip blanks
                if (trimmed.isEmpty()) continue;

                // Skip comments
                if (trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith(";")) {
                    continue;
                }

                // Split by whitespace (address and word)
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Line " + lineNo + " missing fields: " + trimmed);
                }

                int addr = parseNumber(parts[0]);
                int word = parseNumber(parts[1]);

                if (firstAddr < 0) firstAddr = addr;
                out.add(new Record(addr, word));
            }
        }

        return new LoadFile(out, firstAddr);
    }

    /**
     * Parses an integer token as:
     *  - hex if starts with 0x/0X
     *  - octal if token contains only [0-7] AND has a leading 0 (common for assembler outputs)
     *  - otherwise decimal
     */
    private int parseNumber(String token) {
        String t = token.trim();

        if (t.startsWith("0x") || t.startsWith("0X")) {
            return Integer.parseInt(t.substring(2), 16);
        }

        // octal if it’s only 0-7 digits (covers words like 102207)
        if (t.matches("[0-7]+")) {
            return Integer.parseInt(t, 8);
        }

        // decimal fallback
        return Integer.parseInt(t, 10);
    }
}