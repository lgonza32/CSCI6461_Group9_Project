package part0_assembler.assembler;

import java.util.HashMap;
import java.util.Map;

/**
 * Symbol table: maps label -> address (decimal).
 *
 * Example:
 *  End: HLT
 *  symtab["End"] = 1024 (decimal)
 */
public final class SymbolTable {

    private final Map<String, Integer> map = new HashMap<>();

    public boolean contains(String label) {
        return map.containsKey(label);
    }

    public void put(String label, int addressDecimal) {
        map.put(label, addressDecimal);
    }

    public Integer get(String label) {
        return map.get(label);
    }
}