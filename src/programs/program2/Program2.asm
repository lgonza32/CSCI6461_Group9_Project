; =========================================================
; Program2.asm
;
; Flow:
; 1. Read the paragraph already stored in memory
; 2. Print the paragraph to the console printer
; 3. Halt after the paragraph is fully printed
;
; Implementation notes:
; - Uses base-address indexing because the instruction Address
;   field is only 5 bits.
; - X1 is used as the moving pointer into the PARAGRAPH page.
; - X2 is reserved for the WORK page base.
; - X3 is used as the active code-page base for jumps between
;   the main page and the print page.
; =========================================================

LOC 0

BOOT:       LDX 1,PARABASEPTR         ; Load X1 with base address of PARAGRAPH page
            LDX 2,WORKBASEPTR         ; Load X2 with base address of WORK page
            LDX 3,CODE1PTR            ; Load X3 with base address of main code page

            ; Save the current X1 value into WORK[CURX1] so the program
            ; can increment and reload the paragraph pointer through memory.
            STX 1,X1CURPTR,1

            ; jump into main code page base
            JMA 3,0

ZERO:        Data 0
ONE:         Data 1
SPACECHAR:   Data 32
PERIODCHAR:  Data 46
NEWLINECHAR: Data 10

PARABASEPTR:    Data 480
WORKBASEPTR:    Data 620
CODE1PTR:       Data 64
PRINTPTR:       Data 96

; low-memory indirect pointer to WORK[CURX1]
X1CURPTR:       Data 630

; =========================================================
; MAIN CODE PAGE
; Responsibilities:
; - initialize print flow
; - jump to the print page
; - halt after printing completes
; =========================================================

LOC 64
CODE1:      LDX 1,PARABASEPTR          ; Reset X1 to start of paragraph
            STX 1,X1CURPTR,1           ; Save current paragraph pointer in WORK[CURX1]

            ; jump to print page
            LDX 3,PRINTPTR
            JMA 3,0

RET_PRINT:  HLT

; =========================================================
; PRINT PAGE
; Responsibilities:
; - load one character at a time from the paragraph
; - stop at the 0 sentinel
; - print each character to the console printer
; =========================================================

LOC 96
PRINTPAGE:  LDX 1,X1CURPTR,1           ; reload current paragraph pointer into X1

            ; R0 <- MEM[X1]
PRINTLOOP:  LDR 0,1,0

            ; stop when sentinel 0 is reached
            JZ 0,3,PRINTDONE-PRINTPAGE

            ; print the current character
            OUT 0,1

            ; increment X1 properly:
            ; save X1 -> WORK[CURX1]
            STX 1,X1CURPTR,1

            ; R0 <- WORK[CURX1]
            LDR 0,0,X1CURPTR,1
            AIR 0,1
            STR 0,0,X1CURPTR,1
            LDX 1,X1CURPTR,1

            JMA 3,PRINTLOOP-PRINTPAGE

PRINTDONE:  LDX 3,CODE1PTR
            JMA 3,RET_PRINT-CODE1

; =========================================================
; PARAGRAPH PAGE
; Stored as ASCII decimal values, one character per word.
; Ends with 0 sentinel.
; =========================================================

LOC 480
PARAGRAPH:      Data 73      ; I
                Data 32      ; space
                Data 67      ; C
                Data 76      ; L
                Data 73      ; I
                Data 77      ; M
                Data 66      ; B
                Data 46      ; .
                Data 10      ; newline

                Data 77      ; M
                Data 89      ; Y
                Data 32      ; space
                Data 77      ; M
                Data 79      ; O
                Data 77      ; M
                Data 32      ; space
                Data 82      ; R
                Data 85      ; U
                Data 78      ; N
                Data 83      ; S
                Data 46      ; .
                Data 10      ; newline

                Data 77      ; M
                Data 89      ; Y
                Data 32      ; space
                Data 68      ; D
                Data 65      ; A
                Data 68      ; D
                Data 32      ; space
                Data 66      ; B
                Data 73      ; I
                Data 75      ; K
                Data 69      ; E
                Data 83      ; S
                Data 46      ; .
                Data 10      ; newline

                Data 77      ; M
                Data 89      ; Y
                Data 32      ; space
                Data 83      ; S
                Data 73      ; I
                Data 83      ; S
                Data 84      ; T
                Data 69      ; E
                Data 82      ; R
                Data 32      ; space
                Data 83      ; S
                Data 73      ; I
                Data 78      ; N
                Data 71      ; G
                Data 83      ; S
                Data 46      ; .
                Data 10      ; newline

                Data 77      ; M
                Data 89      ; Y
                Data 32      ; space
                Data 71      ; G
                Data 82      ; R
                Data 65      ; A
                Data 78      ; N
                Data 68      ; D
                Data 80      ; P
                Data 65      ; A
                Data 32      ; space
                Data 72      ; H
                Data 73      ; I
                Data 75      ; K
                Data 69      ; E
                Data 83      ; S
                Data 46      ; .
                Data 10      ; newline

                Data 77      ; M
                Data 89      ; Y
                Data 32      ; space
                Data 71      ; G
                Data 82      ; R
                Data 65      ; A
                Data 78      ; N
                Data 68      ; D
                Data 77      ; M
                Data 65      ; A
                Data 32      ; space
                Data 71      ; G
                Data 65      ; A
                Data 82      ; R
                Data 68      ; D
                Data 69      ; E
                Data 78      ; N
                Data 83      ; S
                Data 46      ; .

                Data 0       ; end sentinel

; =========================================================
; WORK PAGE
; =========================================================

LOC 620
WORK:           Data 0       ; [0] UNUSED
                Data 0       ; [1] UNUSED
                Data 0       ; [2] UNUSED
                Data 0       ; [3] UNUSED
                Data 0       ; [4] UNUSED
                Data 0       ; [5] UNUSED
                Data 0       ; [6] UNUSED
                Data 0       ; [7] UNUSED
                Data 0       ; [8] UNUSED
                Data 0       ; [9] UNUSED
CURX1:          Data 0       ; [10] CURX1: saved current X1 pointer value