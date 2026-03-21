; Program1_Test4.asm
; java -cp out part0_assembler.AssemblerMain src/programs/program1/Program1_Test4.asm
; Read one character, validate that it is a digit, then convert it.
; If invalid, store -1 in RESULT.

LOC 0

        IN 0,0
        OUT 0,1
        STR 0,0,SAVECHAR

        SMR 0,0,ZEROCHAR
        JGE 0,0,CHECKUP
        JMA 0,INVALID

CHECKUP: STR 0,0,CANDIDATE
        LDR 0,0,SAVECHAR
        SMR 0,0,NINECHAR
        JGE 0,0,CHECK9
        JMA 0,VALID

CHECK9: LDR 1,0,ZERO
        TRR 0,1
        JCC 3,0,VALID
        JMA 0,INVALID

VALID:  LDR 0,0,CANDIDATE
        STR 0,0,RESULT
        HLT

INVALID: LDR 0,0,NEGONE
         STR 0,0,RESULT
         HLT

RESULT:    Data 0
ZEROCHAR:  Data 48
NINECHAR:  Data 57
ZERO:      Data 0
NEGONE:    Data 65535
SAVECHAR:  Data 0
CANDIDATE: Data 0