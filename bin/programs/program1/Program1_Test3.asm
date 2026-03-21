; Program1_Test3.asm
; java -cp out part0_assembler.AssemblerMain src/programs/program1/Program1_Test3.asm
; Reads one digit character, echoes it, converts ASCII to numeric value,
; stores the numeric value in memory, then halts.

LOC 20

        IN  0,0         ; read one character into R0
        OUT 0,1         ; echo the character to printer
        SMR 0,0,31      ; R0 <- R0 - MEM[31]  (subtract ASCII '0' = 48)
        STR 0,0,30      ; store numeric value into MEM[30]
        HLT

LOC 30
        Data 0          ; result storage
        Data 48         ; ASCII value for '0'