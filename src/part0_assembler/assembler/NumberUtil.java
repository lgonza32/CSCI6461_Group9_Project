package part0_assembler.assembler;

/**
 * Utilities for number handling.
 *
 * per spec:
 *  - Numbers written in the SOURCE (.asm) are in DECIMAL.
 *  - Output columns (listing/load) must be OCTAL (fixed width).
 */
public final class NumberUtil {

    private NumberUtil() {}

    /** 
     * True if token is a decimal integer literal (supports optional leading '-'). 
     */
    public static boolean isDecimalLiteral(String token) {
        return token != null && token.matches("-?\\d+");
    }

    /**
     * Convert a value to a 6-digit octal string with leading zeros.
     * Example: decimal 10 => octal "000012"
     */
    public static String toOct6(int value) {
        // Convert to octal string
        String s = Integer.toOctalString(value);

        // If longer than 6, keep the last 6 digits (defensive)
        if (s.length() > 6) s = s.substring(s.length() - 6);

        // Left-pad with zeros to width 6
        return "0".repeat(Math.max(0, 6 - s.length())) + s;
    }
}