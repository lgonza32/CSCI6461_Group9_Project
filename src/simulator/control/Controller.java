package simulator.control;

import simulator.io.ProgramLoader;
import simulator.machine.MachineState;
import simulator.machine.Memory;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Controller for the CSCI 6461 simulator.
 *
 * Responsibility:
 *  - Handle user actions (IPL/Run/Step/Halt/Reset, Load/Store ops)
 *  - Coordinate with ProgramLoader (and later: Machine/Memory/CPU)
 *  - Report status via a log callback
 * 
 * Owns and performs:
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

            refreshUI.run();
        } catch (IOException ex) {
            log.accept("[IPL] ERROR reading file: " + ex.getMessage() + "\n");
        } catch (IllegalArgumentException ex) {
            log.accept("[IPL] ERROR parsing file: " + ex.getMessage() + "\n");
        } catch (RuntimeException ex) {
            log.accept("[IPL] ERROR loading memory: " + ex.getMessage() + "\n");
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
        log.accept("[STEP] Single-step requested.\n");
        log.accept("[STEP] Execute exactly one instruction.\n");
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
}