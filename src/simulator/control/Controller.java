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
import java.util.function.Supplier;
import simulator.cache.Cache;

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
    private final Supplier<String> getConsoleInputText;
    private final Consumer<String> setConsoleInputText;
    private final Consumer<String> appendPrinterOutput;
    private final ProgramLoader loader = new ProgramLoader();
    private final Memory memory = new Memory();
    private final MachineState state = new MachineState();
    private final CPU cpu;
    private static final int RUN_DELAY_MS = 50; // delay in ms between steps in run mode
    private Timer runTimer;
    private final Runnable clearPrinterOutput;
    private final Cache cache = new Cache(memory);

    /**
     * Cointroller construct that connects the simulator core to the GUI.
     *
     * This controller owns the main machine components (memory, state, CPU)
     * and uses callbacks to communicate with the Swing user interface without
     * tightly coupling simulator logic to specific GUI widgets.
     *
     * The callbacks provide access to:
     * - simulator log output
     * - selected program file path display
     * - cache/debug display
     * - register/state refresh behavior
     * - console keyboard input
     * - console keyboard input replacement after characters are consumed
     * - console printer output
     *
     * @param parent                parent component used for dialogs
     * @param log                   callback used to append text to the GUI log area
     * @param setProgramFilePath    callback used to update the program file text field
     * @param setCacheText          callback used to update the cache display area
     * @param refreshUI             callback used to refresh GUI register/state widgets
     * @param getConsoleInputText   callback that returns the current console input text
     * @param setConsoleInputText   callback that replaces the console input text
     * @param appendPrinterOutput   callback that appends text to the printer area
     */
    public Controller(
            Component parent,
            Consumer<String> log,
            Consumer<String> setProgramFilePath,
            Consumer<String> setCacheText,
            Runnable refreshUI,
            Supplier<String> getConsoleInputText,
            Consumer<String> setConsoleInputText,
            Consumer<String> appendPrinterOutput,
            Runnable clearPrinterOutput) {

        this.parent = parent;
        this.log = log;
        this.setProgramFilePath = setProgramFilePath;
        this.setCacheText = setCacheText;
        this.refreshUI = refreshUI;
        this.getConsoleInputText = getConsoleInputText;
        this.setConsoleInputText = setConsoleInputText;
        this.appendPrinterOutput = appendPrinterOutput;
        this.clearPrinterOutput = clearPrinterOutput;

        // start from a clean machine state
        memory.clear();
        state.clear();
        cache.clear();

        // build the CPU with callbacks for keyboard/printer device I/O.
        this.cpu = new CPU(
                cache,
                state,
                this::readNextConsoleChar,
                this::writePrinterChar
        );
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

        stopRunTimer();
        // Clear machine before loading
        memory.clear();
        state.clear();
        cache.clear();

        // parse the file
        try {
            ProgramLoader.LoadFile parsed = loader.parse(file);

            // write each record into memory
            for (ProgramLoader.Record r : parsed.records) {
                memory.write(r.address, r.word);
            }

            cache.clear();

            // set PC to first loaded address (good “start point”)
            if (parsed.firstAddress >= 0) {
                state.setPC(parsed.firstAddress);
                state.setMAR(parsed.firstAddress); // common convenience
            }

            // show a dump of loaded words in the Cache Content text area (verification)
            setCacheText.accept(cache.dump());

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
                setCacheText.accept(cache.dump());
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

    /**
     * Start timed instruction execution until the CPU halts
     * or the user presses Halt/Reset.
     *
     * Uses a Swing Timer so the GUI remains responsive while the
     * simulator continues stepping through instructions.
     */
    public void handleRun() {
        // Dd not start a second run loop if one is already active
        if (runTimer != null && runTimer.isRunning()) {
            log.accept("[RUN] Run loop is already active.\n");
            return;
        }

        // if CPU is currently halted, reset only the halted flag
        // so execution can continue from the current machine state
        if (cpu.isHalted()) {
            cpu.reset();
        }

        log.accept("[RUN] Run requested.\n");
        log.accept("[RUN] Starting timed fetch-decode-execute loop.\n");

        runTimer = new Timer(RUN_DELAY_MS, e -> {
            String msg = executeOneStep();

            // stop automatically once the CPU halts
            if (cpu.isHalted()) {
                stopRunTimer();
                log.accept("[RUN] CPU halted. Run loop stopped.\n");
                return;
            }

            // If the CPU is waiting for keyboard/card input, stop the timer
            // so the user can type into the console input field and press Run again
            if (msg.contains("waiting/no input")) {
                stopRunTimer();
                log.accept("[RUN] Execution paused waiting for input.\n");
            }
        });

        runTimer.start();
    }

    /**
     * Execute exactly one instruction.
     *
     * If the timed run loop is active, single-step is ignored to avoid
     * conflicting execution modes.
     */
    public void handleStep() {
        if (runTimer != null && runTimer.isRunning()) {
            log.accept("[STEP] Ignored because RUN is active.\n");
            return;
        }
        executeOneStep();
    }

    /**
     * Halt CPU execution and stop the active run loop.
     */
    public void handleHalt() {
        stopRunTimer();
        cpu.halt();
        log.accept("[HALT] Halt requested.\n");
        log.accept("[HALT] CPU halted and run loop stopped.\n");
        refreshUI.run();
    }

    /**
     * Reset the simulator to a clean state.
     *
     * This stops any active run loop, clears memory/registers,
     * clears the cache/debug display, and re-enables future stepping/running.
     */
    public void handleReset() {
        stopRunTimer();
        memory.clear();
        state.clear();
        cache.clear();
        setCacheText.accept("");
        clearPrinterOutput.run();
        setConsoleInputText.accept("");
        cpu.reset();
        log.accept("[RESET] Cleared registers, memory, and printer output.\n");
        refreshUI.run();
    }

    public void handleLoad() {
        int mar = state.getMAR();
        int word = cache.read(mar);
        state.setMBR(word);

        log.accept("[LOAD] MBR <- MEM[MAR]. MAR=" + Memory.toOct6(mar) +
                " WORD=" + Memory.toOct6(word) + "\n");

        // show memory contents at MAR as required by deliverable
        setCacheText.accept(cache.dump());
        refreshUI.run();
    }

    /**
     * Load the memory word at the current MAR into MBR, then advance MAR by one.
     */
    public void handleLoadPlus() {
        handleLoad();
        state.setMAR(state.getMAR() + 1); // advance MAR to the next memory location
        log.accept("[LOAD+] MAR incremented to " + Memory.toOct6(state.getMAR()) + "\n");
        refreshUI.run();
    }

    /**
     * Store the contents of MBR into memory at the address currently held in MAR.
     */
    public void handleStore() {
        int mar = state.getMAR();
        int word = state.getMBR();
        memory.write(mar, word);

        log.accept("[STORE] MEM[MAR] <- MBR. MAR=" + Memory.toOct6(mar) +
                " WORD=" + Memory.toOct6(word) + "\n");

        // Show memory contents at MAR
        setCacheText.accept(cache.dump());
        refreshUI.run();
    }

    /**
     * Store the contents of MBR into memory at MAR, then advance MAR by one.
     */
    public void handleStorePlus() {
        handleStore();
        state.setMAR(state.getMAR() + 1); // advance MAR to the next memory location
        log.accept("[STORE+] MAR incremented to " + Memory.toOct6(state.getMAR()) + "\n");
        refreshUI.run();
    }

    /** ==========================
     *  Cache Functions
     * ========================== */

    /**
     * Refresh the cache/debug display using the current MAR contents.
     *
     * If MAR is outside legal memory range, the display is cleared instead
     * of throwing an exception.
     */
    private void refreshCacheAtMAR() {
        setCacheText.accept(cache.dump());
    }

    
    /**
     * Stop the active run timer if it exists.
     */
    private void stopRunTimer() {
        if (runTimer != null && runTimer.isRunning()) {
            runTimer.stop();
        }
    }

    /**
     * Execute one CPU step and refresh GUI state.
     *
     * @return CPU step log message
     */
    private String executeOneStep() {
        String msg = cpu.step();
        log.accept(msg);
        refreshCacheAtMAR();
        refreshUI.run();
        return msg;
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

    /**
     * Read one character from the GUI console input field.
     *
     * Behavior:
     * - If the input field is empty, return -1 to indicate that no input is ready
     * - Otherwise, consume the first character and remove it from the field
     *
     * This method is used by CPU device input instructions such as IN.
     *
     * @return next character code as an integer, or -1 if no input is available
     */
    private int readNextConsoleChar() {
        String text = getConsoleInputText.get();

        // No input is available right now.
        if (text == null || text.isEmpty()) {
            return -1;
        }

        // Consume the first available character.
        char ch = text.charAt(0);

        // Remove the consumed character from the GUI input field.
        String remaining = text.substring(1);
        setConsoleInputText.accept(remaining);

        return ch;
    }

    /**
     * Append one character to the GUI printer area.
     *
     * This method is used by CPU device output instructions such as OUT.
     *
     * @param value low 8-bit character code to print
     */
    private void writePrinterChar(int value) {
        char ch = (char) (value & 0xFF);
        appendPrinterOutput.accept(Character.toString(ch));
    }
}