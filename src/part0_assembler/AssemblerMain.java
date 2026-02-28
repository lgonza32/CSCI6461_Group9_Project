package part0_assembler;

import part0_assembler.assembler.Assembler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI runner for the Part 0 assembler.
 *
 * Usage:
 *   java -cp out part0_assembler.AssemblerMain <.asm path>
 *
 * Outputs:
 *  - test_listing.txt
 *  - test_load.txt
 */
public final class AssemblerMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java -cp out part0_assembler.AssemblerMain <.asm path>");
            return;
        }

        Path asm = Path.of(args[0]);

        // ensure output directory exists
        Path outDir = Path.of("txt");
        Files.createDirectories(outDir);

        // base name: test.asm -> test
        String fileName = asm.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;

        // outputs go into txt/
        Path listing = outDir.resolve(base + "_listing.txt");
        Path load    = outDir.resolve(base + "_load.txt");

        new Assembler().assemble(asm, listing, load);

        System.out.println("Wrote listing: " + listing.toAbsolutePath());
        System.out.println("Wrote load:    " + load.toAbsolutePath());
    }
}