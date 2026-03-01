package simulator.control;

import simulator.io.ProgramLoader;
import simulator.machine.MachineState;
import simulator.machine.Memory;
import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import simulator.cpu.CPU;

/**
 * Controller for the CSCI 6461 simulator.
 *
 * Responsibility:
 *  - Handle user actions (IPL/Run/Step/Halt/Reset, Load/Store ops)
 *  - Coordinate with ProgramLoader (and later: Machine/Memory/CPU)
 *  - Report status via a log callback
 * 
 * Performs:
 *  - Registers
 *  - Memory
 *  - IPL load into memory
 *  - Reset to clear
 */
public final class Controller {

    // dependencies
    private final Component parent;
    private final Consumer<String> log;
    private final Consumer<String> setProgramFilePath;
    private final Consumer<String> setCacheText;
    private final Runnable refreshUI;
    private final ProgramLoader loader = new ProgramLoader();
    private final Memory memory = new Memory();
    private final MachineState state = new MachineState();
    private final CPU cpu = new CPU(memory, state);

    /**
     * Constructs the Controller.
     *
     * @param parent component used for dialog placement (file chooser)
     * @param log callback for writing messages to Console Output
     * @param setProgramFilePath callback for updating the GUI Program File field
     */
    public Controller(Component parent,
                      Consumer<String> log,
                      Consumer<String> setProgramFilePath,
                      Consumer<String> setCacheText,
                      Runnable refreshUI) {
        this.parent = parent;
        this.log = log;
        this.setProgramFilePath = setProgramFilePath;
        this.setCacheText = setCacheText;
        this.refreshUI = refreshUI;

        // Start “powered on” with cleared state
        memory.clear();
        state.clear();
    }

    /* ==========================
     * Expose model to GUI for refresh
     * ========================== */

    public MachineState getState() { return state; }
    public Memory getMemory() { return memory; }

    /** ==========================
     *  Control Button Handlers
     * ========================== */

    /**
     * IPL behavior:
     * - Open file chooser
     * - Store selected path into GUI "Program File" field
     * - Parse file with ProgramLoader and log summary
     */
    public void handleIPL() {
        log.accept("[IPL] Initial Program Load requested.\n");

        // user chooses file
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Program File for IPL");

        // Default to txt/
        File txtDir = new File("txt");
        if (txtDir.exists() && txtDir.isDirectory()) {
            chooser.setCurrentDirectory(txtDir);
        }

        int result = chooser.showOpenDialog(parent);

        // user cancels and logs to debugger
        if (result != JFileChooser.APPROVE_OPTION) {
            log.accept("[IPL] File selection cancelled.\n");
            return;
        }

        // get chosen file and update GUI field
        File file = chooser.getSelectedFile();
        setProgramFilePath.accept(file.getAbsolutePath());
        log.accept("[IPL] Selected program file: " + file.getAbsolutePath() + "\n");

        // Clear machine before loading
        memory.clear();
        state.clear();

        // parse the file
        try {
            ProgramLoader.LoadFile parsed = loader.parse(file);

            // write each record into memory
            for (ProgramLoader.Record r : parsed.records) {
                memory.write(r.address, r.word);
            }

            // set PC to first loaded address (good “start point”)
            if (parsed.firstAddress >= 0) {
                state.setPC(parsed.firstAddress);
                state.setMAR(parsed.firstAddress); // common convenience
            }

            // show a dump of loaded words in the Cache Content text area (verification)
            setCacheText.accept(formatLoadDump(parsed));

            log.accept("[IPL] Loaded " + parsed.recordsLoaded + " word(s) into memory.\n");
            if (parsed.firstAddress >= 0) {
                log.accept("[IPL] PC set to " + Memory.toOct6(parsed.firstAddress) + " (octal).\n");
            }

            Integer start = tryFindStartAddressFromListing(file);
            if (start != null) {
                state.setPC(start);
                state.setMAR(start);
                log.accept("[IPL] Start address from listing: PC <- " + Memory.toOct6(start) + "\n");
            } else if (parsed.firstAddress >= 0) {
                state.setPC(parsed.firstAddress);
                state.setMAR(parsed.firstAddress);
                log.accept("[IPL] Start address fallback: PC <- " + Memory.toOct6(parsed.firstAddress) + "\n");
            }

            refreshUI.run();
        } catch (IOException ex) {
            log.accept("[IPL] ERROR reading file: " + ex.getMessage() + "\n");
        } catch (IllegalArgumentException ex) {
            log.accept("[IPL] ERROR parsing file: " + ex.getMessage() + "\n");
        } catch (RuntimeException ex) {
            log.accept("[IPL] ERROR loading memory: " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Sets a selected register from the operator "switch" inputs.
     *
     * Auto-detect rule (binary vs octal):
     *  - If octal field is empty -> use binary
     *  - Else if binary contains at least one '1' -> use binary
     *  - Else use octal
     *
     * This avoids ambiguity when binary default is all zeros.
     */
    public void handleSetTarget(String target, String binaryText, String octalText) {
        int value = parseSwitchValue(binaryText, octalText);

        switch (target) {
            case "PC" -> {
                // PC is 12-bit; also enforce 0..2047 for clarity
                if (value < 0 || value >= Memory.SIZE) {
                    log.accept("[SET] PC out of range (0..2047): " + value + "\n");
                    return;
                }
                state.setPC(value);
                log.accept("[SET] PC <- " + Memory.toOct6(state.getPC()) + "\n");
            }
            case "MAR" -> {
                if (value < 0 || value >= Memory.SIZE) {
                    log.accept("[SET] MAR out of range (0..2047): " + value + "\n");
                    return;
                }
                state.setMAR(value);
                log.accept("[SET] MAR <- " + Memory.toOct6(state.getMAR()) + "\n");

                // show memory at MAR on console area
                int word = memory.read(state.getMAR());
                setCacheText.accept(Memory.toOct6(state.getMAR()) + " " + Memory.toOct6(word) + "\n");
            }
            case "MBR" -> {
                state.setMBR(value);
                log.accept("[SET] MBR <- " + Memory.toOct6(state.getMBR()) + "\n");
            }
            case "R0", "R1", "R2", "R3" -> {
                int r = Integer.parseInt(target.substring(1));
                state.setGPR(r, value);
                log.accept("[SET] " + target + " <- " + Memory.toOct6(state.getGPR(r)) + "\n");
            }
            case "X1", "X2", "X3" -> {
                int x = Integer.parseInt(target.substring(1));
                state.setIXR(x, value);
                log.accept("[SET] " + target + " <- " + Memory.toOct6(state.getIXR(x)) + "\n");
            }
            default -> {
                log.accept("[SET] Unknown target: " + target + "\n");
                return;
            }
        }

        refreshUI.run();
    }

    /**
     * Parses operator input using the auto-detect rule described above.
     * Returns an int (0..65535 typically); masking happens inside MachineState setters.
     */
    private int parseSwitchValue(String binaryText, String octalText) {
        String bin = (binaryText == null) ? "" : binaryText.trim();
        String oct = (octalText == null) ? "" : octalText.trim();

        // decide which to use
        boolean octEmpty = oct.isEmpty();
        boolean binHasOne = bin.contains("1");
        boolean useBinary = octEmpty || binHasOne;

        if (useBinary) {
            if (bin.isEmpty()) bin = "0";
            // binary should be only 0/1; GUI filter enforces this, but validate anyway
            if (!bin.matches("[01]+")) {
                throw new IllegalArgumentException("Binary input invalid: " + bin);
            }
            log.accept("[SWITCH] Using BINARY input.\n");
            return Integer.parseInt(bin, 2);
        } else {
            if (oct.isEmpty()) oct = "0";
            if (!oct.matches("[0-7]+")) {
                throw new IllegalArgumentException("Octal input invalid: " + oct);
            }
            log.accept("[SWITCH] Using OCTAL input.\n");
            return Integer.parseInt(oct, 8);
        }
    }

   /** ==========================
     *  HANDLER LOGS
     * ========================== */

    public void handleRun() {
        log.accept("[RUN] Run requested.\n");
        log.accept("[RUN] Start fetch-decode-execute loop.\n");
    }

    public void handleStep() {
        String msg = cpu.step();
        log.accept(msg);

        // show memory contents at MAR on the user console
        int mar = state.getMAR();
        int word = memory.read(mar);
        setCacheText.accept(Memory.toOct6(mar) + " " + Memory.toOct6(word) + "\n");

    refreshUI.run();
    }

    public void handleHalt() {
        log.accept("[HALT] Halt requested.\n");
        log.accept("[HALT] Stop run loop.\n");
    }

    public void handleReset() {
        memory.clear();
        state.clear();
        setCacheText.accept("");
        log.accept("[RESET] Cleared registers and memory.\n");
        refreshUI.run();
        cpu.reset();
    }

    public void handleLoad() {
        int mar = state.getMAR();
        int word = memory.read(mar);
        state.setMBR(word);

        log.accept("[LOAD] MBR <- MEM[MAR]. MAR=" + Memory.toOct6(mar) +
                " WORD=" + Memory.toOct6(word) + "\n");

        // show memory contents at MAR as required by deliverable
        setCacheText.accept(Memory.toOct6(mar) + " " + Memory.toOct6(word) + "\n");

        refreshUI.run();
    }

    public void handleLoadPlus() {
        handleLoad();
        state.setMAR(state.getMAR() + 1);
        log.accept("[LOAD+] MAR incremented to " + Memory.toOct6(state.getMAR()) + "\n");
        refreshUI.run();
    }

    public void handleStore() {
        int mar = state.getMAR();
        int word = state.getMBR();
        memory.write(mar, word);

        log.accept("[STORE] MEM[MAR] <- MBR. MAR=" + Memory.toOct6(mar) +
                " WORD=" + Memory.toOct6(word) + "\n");

        // Show memory contents at MAR
        setCacheText.accept(Memory.toOct6(mar) + " " + Memory.toOct6(memory.read(mar)) + "\n");

        refreshUI.run();
    }

    public void handleStorePlus() {
        handleStore();
        state.setMAR(state.getMAR() + 1);
        log.accept("[STORE+] MAR incremented to " + Memory.toOct6(state.getMAR()) + "\n");
        refreshUI.run();
    }

    /* ==========================
     * Helpers
     * ========================== */

    private String formatLoadDump(ProgramLoader.LoadFile parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADDR    WORD\n");
        for (ProgramLoader.Record r : parsed.records) {
            sb.append(Memory.toOct6(r.address))
              .append("  ")
              .append(Memory.toOct6(r.word))
              .append("\n");
        }
        return sb.toString();
    }

    /**
     * Attempts to locate the matching listing file and extract the first instruction address.
     *
     * Convention:
     *  - If user selects:  txt/test_load.txt
     *  - We look for:      txt/test_listing.txt
     *
     * We scan listing lines and find the first line that has:
     *  - two octal columns (LOC and WORD), AND
     *  - source token is NOT "Data" and NOT "LOC"
     *
     * That gives the first instruction location (e.g., 000016).
     */
    private Integer tryFindStartAddressFromListing(File loadFile) {
        String name = loadFile.getName();
        String listingName = name.replace("_load", "_listing");
        File listing = new File(loadFile.getParentFile(), listingName);

        if (!listing.exists()) return null;

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(listing))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith(";")) continue;

                String[] parts = t.split("\\s+");
                if (parts.length < 3) continue;

                // Listing format: LOC WORD <source...>
                if (!parts[0].matches("[0-7]{6}") || !parts[1].matches("[0-7]{6}")) {
                    continue;
                }

                String sourceToken = parts[2];
                if (sourceToken.equalsIgnoreCase("Data") || sourceToken.equalsIgnoreCase("LOC")) {
                    continue; // skip data/directives
                }

                // first instruction line found
                return Integer.parseInt(parts[0], 8);
            }
        } catch (Exception e) {
            log.accept("[IPL] Warning: couldn't parse listing for start address.\n");
        }
        return null;
    }
}