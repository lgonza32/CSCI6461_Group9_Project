package simulator.control;

import simulator.io.ProgramLoader;
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
 */
public final class Controller {

    // dependencies
    private final Component parent;
    private final Consumer<String> log;
    private final Consumer<String> setProgramFilePath;
    private final ProgramLoader loader;

    /**
     * Constructs the Controller.
     *
     * @param parent component used for dialog placement (file chooser)
     * @param log callback for writing messages to Console Output
     * @param setProgramFilePath callback for updating the GUI Program File field
     */
    public Controller(Component parent,
                      Consumer<String> log,
                      Consumer<String> setProgramFilePath) {
        this.parent = parent;
        this.log = log;
        this.setProgramFilePath = setProgramFilePath;
        this.loader = new ProgramLoader();
    }

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

        // parse the file
        try {
            ProgramLoader.LoadFile parsed = loader.parse(file);
            log.accept("[IPL] Parsed " + parsed.recordsLoaded + " word(s).\n");
            if (parsed.firstAddress >= 0) {
                log.accept("[IPL] First address: " + parsed.firstAddress + "\n");
            }
            log.accept("[IPL] Write parsed words into Memory.\n");
        } catch (IOException ex) { // IOException: file not found, permission denied, etc.
            log.accept("[IPL] ERROR reading file: " + ex.getMessage() + "\n");
        } catch (IllegalArgumentException ex) { // IllegalArgumentException: format/parse error in file contents.
            log.accept("[IPL] ERROR parsing file: " + ex.getMessage() + "\n");
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
        log.accept("[RESET] Reset requested.\n");
        log.accept("[RESET] Clear registers and memory.\n");
    }

    public void handleLoad() {
        log.accept("[LOAD] Requested: MBR <- MEM[MAR]\n");
    }

    public void handleLoadPlus() {
        log.accept("[LOAD+] Requested: Load then MAR++\n");
    }

    public void handleStore() {
        log.accept("[STORE] Requested: MEM[MAR] <- MBR\n");
    }

    public void handleStorePlus() {
        log.accept("[STORE+] Requested: Store then MAR++\n");
    }
}