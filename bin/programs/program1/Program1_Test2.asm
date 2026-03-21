; Program1_Test2.asm
; java -cp out part0_assembler.AssemblerMain src/programs/program1/Program1_Test2.asm
; tests for numerical input

LOC 20

        LDR 1,0,31      ; R1 <- delimiter character (space = 32)

LOOP:   IN  0,0         ; read one character into R0
        TRR 0,1         ; compare input char with delimiter
        JCC 3,0,26      ; if equal flag set, jump to DONE
        OUT 0,1         ; echo character to printer
        JMA 0,21      ; jump back to LOOP

DONE:   HLT

LOC 31
        Data 32         ; ASCII space delimiter