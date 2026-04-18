; =========================================================
; Program2.asm
;
; Flow:
; 1. Read the paragraph already stored in memory
; 2. Print the paragraph to the console printer
; 3. Print the prompt "INPUT WORD SEARCH"
; 4. Print the prompt "TYPE WORD THEN SPACE"
; 5. Read one input word into the INPUTBUF page
; 6. Search the paragraph for a whole-word exact match
; 7. Print FOUND or NOT FOUND
; 8. Halt
;
; Implementation notes:
; - Uses base-address indexing because the instruction Address
;   field is only 5 bits.
; - X1 is used as the moving pointer into the active text page.
; - X2 is reserved for the WORK page base.
; - X3 is used as the active code-page base for jumps between
;   the main page, print page, prompt pages, input page,
;   search page, compare page, and result pages.
; =========================================================

LOC 0

BOOT:       LDX 1,PARABASEPTR          ; Load X1 with base address of PARAGRAPH page
            LDX 2,WORKBASEPTR          ; Load X2 with base address of WORK page
            LDX 3,CODE1PTR             ; Load X3 with base address of main code page

            ; Save the current X1 value into WORK[CURX1] so the program
            ; can increment and reload the active pointer through memory.
            STX 1,X1CURPTR,1

            ; jump into main code page base
            JMA 3,0

ZERO:           Data 0
CMPEXTPTR:      Data 384
SPACECHAR:      Data 32
PERIODCHAR:     Data 46
NEWLINECHAR:    Data 10

PARABASEPTR:    Data 480
PROMPT1BASEPTR: Data 600
PROMPT2BASEPTR: Data 620
INPUTBASEPTR:   Data 650
WORKBASEPTR:    Data 700
CODE1PTR:       Data 64
PRINTPTR:       Data 96
INPUTPTR:       Data 128
PROMPT1PTR:     Data 160
PROMPT2PTR:     Data 192
SEARCHPTR:      Data 224
CMPPTRLBL:      Data 256
FOUNDPTR:       Data 288
NOTFOUNDPTR:    Data 320

; low-memory indirect pointers
SKIPPTR:        Data 352
X1CURPTR:       Data 710
INPTRPTR:       Data 711
SCANPTRPTR:     Data 712
WORDPTRPTR:     Data 713
CMPINPTRPTR:    Data 715
FOUNDTXTPTR:    Data 760
NOTFDBASEPTR:   Data 770

; =========================================================
; MAIN CODE PAGE
; Responsibilities:
; - initialize paragraph print flow
; - jump to the print page
; - jump to the first prompt page
; - jump to the second prompt page
; - jump to the input page
; - jump to the search page
; - halt after result output completes
; =========================================================

LOC 64
CODE1:          LDX 1,PARABASEPTR
                STX 1,X1CURPTR,1

                ; jump to print page
                LDX 3,PRINTPTR
                JMA 3,0

RET_PRINT:      LDX 1,PROMPT1BASEPTR            ; reset X1 to start of first prompt string
                STX 1,X1CURPTR,1

                ; jump to first prompt page
                LDX 3,PROMPT1PTR
                JMA 3,0

RET_PROMPT1:    LDR 0,0,NEWLINECHAR
                OUT 0,1

                ; reset X1 to start of second prompt string
                LDX 1,PROMPT2BASEPTR
                STX 1,X1CURPTR,1

                ; jump to second prompt page
                LDX 3,PROMPT2PTR
                JMA 3,0

RET_PROMPT2:    LDR 0,0,NEWLINECHAR
                OUT 0,1

                ; reset X1 to start of input buffer
                LDX 1,INPUTBASEPTR
                STX 1,INPTRPTR,1

                ; jump to input page
                LDX 3,INPUTPTR
                JMA 3,0

RET_INPUT:      LDX 1,PARABASEPTR          ; reset scan pointer to start of paragraph
                STX 1,SCANPTRPTR,1

                LDR 0,0,ZERO
                STR 0,2,MATCHFLAG-WORK

                ; jump to search page
                LDX 3,SEARCHPTR
                JMA 3,0

; =========================================================
; PRINT PAGE
; Responsibilities:
; - load one character at a time from the paragraph
; - stop at the 0 sentinel
; - print each character to the console printer
; =========================================================

LOC 96
PRINTPAGE:      LDX 1,X1CURPTR,1

PRINTLOOP:      LDR 0,1,0

                JZ 0,3,PRINTDONE-PRINTPAGE

                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                JMA 3,PRINTLOOP-PRINTPAGE

PRINTDONE:      LDX 3,CODE1PTR
                JMA 3,RET_PRINT-CODE1

; =========================================================
; INPUT PAGE
; Responsibilities:
; - read one character at a time from the keyboard
; - stop when SPACE is entered
; - stop when NEWLINE is entered
; - store each character into INPUTBUF
; - write 0 sentinel at the end of the buffer
; =========================================================

LOC 128
INPUTPAGE:      LDX 1,INPTRPTR,1

INPUTLOOP:      IN 0,0

                ; stop input on SPACE
                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,INPUTDONE-INPUTPAGE

                ; stop input on NEWLINE
                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,INPUTDONE-INPUTPAGE

                ; store character into input buffer
                STR 0,1,0

                ; increment X1 properly:
                ; save X1 -> WORK[INPTR]
                STX 1,INPTRPTR,1
                LDR 0,0,INPTRPTR,1
                AIR 0,1
                STR 0,0,INPTRPTR,1
                LDX 1,INPTRPTR,1

                JMA 3,INPUTLOOP-INPUTPAGE

INPUTDONE:      LDR 0,0,ZERO
                STR 0,1,0

                LDX 3,CODE1PTR
                JMA 3,RET_INPUT-CODE1

; =========================================================
; PROMPT PAGE 1
; Responsibilities:
; - print INPUT WORD SEARCH from memory
; - stop at the 0 sentinel
; =========================================================

LOC 160
PROMPT1PAGE:    LDX 1,X1CURPTR,1

PROMPT1LOOP:    LDR 0,1,0
                JZ 0,3,PROMPT1DONE-PROMPT1PAGE
                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                JMA 3,PROMPT1LOOP-PROMPT1PAGE

PROMPT1DONE:    LDX 3,CODE1PTR
                JMA 3,RET_PROMPT1-CODE1

; =========================================================
; PROMPT PAGE 2
; Responsibilities:
; - print TYPE WORD THEN SPACE from memory
; - stop at the 0 sentinel
; =========================================================

LOC 192
PROMPT2PAGE:    LDX 1,X1CURPTR,1

PROMPT2LOOP:    LDR 0,1,0
                JZ 0,3,PROMPT2DONE-PROMPT2PAGE
                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                JMA 3,PROMPT2LOOP-PROMPT2PAGE

PROMPT2DONE:    LDX 3,CODE1PTR
                JMA 3,RET_PROMPT2-CODE1

; =========================================================
; SEARCH PAGE
; Responsibilities:
; - scan paragraph one character at a time
; - skip separators
; - when a word start is found, compare that paragraph word
;   against INPUTBUF
; - jump to FOUND or NOT FOUND result pages
; =========================================================

LOC 224
SEARCHPAGE:     LDX 1,SCANPTRPTR,1

SEARCHLOOP:     LDR 0,1,0
                JZ 0,3,SEARCHFAIL1-SEARCHPAGE

                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,ADVANCESEP-SEARCHPAGE

                LDR 1,0,PERIODCHAR
                TRR 0,1
                JCC 3,3,ADVANCESEP-SEARCHPAGE

                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,ADVANCESEP-SEARCHPAGE

                STX 1,WORDPTRPTR,1

                LDX 1,INPUTBASEPTR
                STX 1,CMPINPTRPTR,1

                LDR 0,0,ZERO
                STR 0,2,MATCHFLAG-WORK

                LDX 3,CMPPTRLBL
                JMA 3,0

RET_COMPARE:    LDR 0,2,MATCHFLAG-WORK
                JZ 0,3,TO_SKIPPAGE-SEARCHPAGE

                LDX 3,FOUNDPTR
                JMA 3,0

ADVANCESEP:     STX 1,SCANPTRPTR,1
                LDR 0,0,SCANPTRPTR,1
                AIR 0,1
                STR 0,0,SCANPTRPTR,1
                LDX 1,SCANPTRPTR,1
                JMA 3,SEARCHLOOP-SEARCHPAGE

TO_SKIPPAGE:    LDX 3,SKIPPTR
                JMA 3,0

SEARCHFAIL1:    LDX 3,NOTFOUNDPTR
                JMA 3,0

; =========================================================
; COMPARE PAGE
; Responsibilities:
; - compare paragraph word at WORDPTR against INPUTBUF
; - set MATCHFLAG = 1 only for an exact whole-word match
; - return to search page
; =========================================================

LOC 256
COMPAREPAGE:    LDX 1,WORDPTRPTR,1

CMPLOOP:        LDR 0,1,0
                
                ; X2 <- current input pointer
                LDX 2,CMPINPTRPTR,1

                ; R1 <- current input character
                LDR 1,2,0

                ; if input char == 0, paragraph must be at a boundary to match
                JZ 1,3,CHECKBOUND-COMPAREPAGE

                ; if chars differ, fail
                TRR 0,1
                JCC 3,3,TO_CMPEXT-COMPAREPAGE

                LDX 3,SEARCHPTR
                JMA 3,RET_COMPARE-SEARCHPAGE

CHECKBOUND:     LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,TO_CMPSUCCESS-COMPAREPAGE

                LDR 1,0,PERIODCHAR
                TRR 0,1
                JCC 3,3,TO_CMPSUCCESS-COMPAREPAGE

                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,TO_CMPSUCCESS-COMPAREPAGE

                LDR 1,0,ZERO
                TRR 0,1
                JCC 3,3,TO_CMPSUCCESS-COMPAREPAGE

                LDX 3,SEARCHPTR
                JMA 3,RET_COMPARE-SEARCHPAGE

TO_CMPEXT:      LDX 3,CMPEXTPTR
                JMA 3,0

TO_CMPSUCCESS:  LDX 3,CMPEXTPTR
                JMA 3,CMPSUCCESS2-CMPEXTPAGE

; =========================================================
; FOUND PAGE
; Responsibilities:
; - print FOUND
; - halt
; =========================================================

LOC 288
FOUNDPAGE:      LDX 1,FOUNDTXTPTR

FOUNDLOOP:      LDR 0,1,0
                JZ 0,3,FOUNDDONE-FOUNDPAGE
                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                JMA 3,FOUNDLOOP-FOUNDPAGE

FOUNDDONE:      HLT

; =========================================================
; NOT FOUND PAGE
; Responsibilities:
; - print NOT FOUND
; - halt
; =========================================================

LOC 320
NOTFPG:         LDX 1,NOTFDBASEPTR

NOTFLOOP:       LDR 0,1,0
                JZ 0,3,NOTFDONE-NOTFPG
                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                JMA 3,NOTFLOOP-NOTFPG

NOTFDONE:       HLT

; =========================================================
; SKIP PAGE
; =========================================================

LOC 352
SKIPPAGE:       LDX 1,SCANPTRPTR,1

SKIPLOOP:       LDR 0,1,0
                JZ 0,3,SEARCHFAIL2-SKIPPAGE

                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,ADVANCE2-SKIPPAGE

                LDR 1,0,PERIODCHAR
                TRR 0,1
                JCC 3,3,ADVANCE2-SKIPPAGE

                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,ADVANCE2-SKIPPAGE

                STX 1,SCANPTRPTR,1
                LDR 0,0,SCANPTRPTR,1
                AIR 0,1
                STR 0,0,SCANPTRPTR,1
                LDX 1,SCANPTRPTR,1
                JMA 3,SKIPLOOP-SKIPPAGE

ADVANCE2:       STX 1,SCANPTRPTR,1
                LDR 0,0,SCANPTRPTR,1
                AIR 0,1
                STR 0,0,SCANPTRPTR,1

                LDX 3,SEARCHPTR
                JMA 3,SEARCHLOOP-SEARCHPAGE

SEARCHFAIL2:    LDX 3,NOTFOUNDPTR
                JMA 3,0

; =========================================================
; COMPARE PAGE 2
; =========================================================

LOC 384
CMPEXTPAGE:     STX 1,WORDPTRPTR,1
                LDR 0,0,WORDPTRPTR,1
                AIR 0,1
                STR 0,0,WORDPTRPTR,1
                LDX 1,WORDPTRPTR,1

                ; advance input pointer using X2
                STX 2,CMPINPTRPTR,1
                LDR 0,0,CMPINPTRPTR,1
                AIR 0,1
                STR 0,0,CMPINPTRPTR,1

                LDX 3,CMPPTRLBL
                JMA 3,CMPLOOP-COMPAREPAGE

CMPSUCCESS2:    LDR 0,0,ZERO
                AIR 0,1
                STR 0,2,MATCHFLAG-WORK

                LDX 3,SEARCHPTR
                JMA 3,RET_COMPARE-SEARCHPAGE

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
                Data 10      ; newline
                Data 0       ; end sentinel

; =========================================================
; PROMPT PAGE 1 DATA
; =========================================================

LOC 600
PROMPT1:        Data 73      ; I
                Data 78      ; N
                Data 80      ; P
                Data 85      ; U
                Data 84      ; T
                Data 32      ; space
                Data 87      ; W
                Data 79      ; O
                Data 82      ; R
                Data 68      ; D
                Data 32      ; space
                Data 83      ; S
                Data 69      ; E
                Data 65      ; A
                Data 82      ; R
                Data 67      ; C
                Data 72      ; H
                Data 0

; =========================================================
; PROMPT PAGE 2 DATA
; =========================================================

LOC 620
PROMPT2:        Data 84      ; T
                Data 89      ; Y
                Data 80      ; P
                Data 69      ; E
                Data 32      ; space
                Data 87      ; W
                Data 79      ; O
                Data 82      ; R
                Data 68      ; D
                Data 32      ; space
                Data 84      ; T
                Data 72      ; H
                Data 69      ; E
                Data 78      ; N
                Data 32      ; space
                Data 83      ; S
                Data 80      ; P
                Data 65      ; A
                Data 67      ; C
                Data 69      ; E
                Data 0

; =========================================================
; INPUT BUFFER PAGE
; =========================================================

LOC 650
INPUTBUF:       Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0

; =========================================================
; WORK PAGE
; =========================================================

LOC 700
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
INPTR:          Data 0       ; [11] INPTR: saved current input pointer value
SCANPTR:        Data 0       ; [12] paragraph scan pointer
WORDPTR:        Data 0       ; [13] candidate paragraph word pointer
MATCHFLAG:      Data 0       ; [14] 1 if exact match found
CMPINPTR:       Data 0       ; [15] input compare pointer

; =========================================================
; RESULT TEXT DATA
; =========================================================

LOC 760
FOUNDTXT:       Data 70      ; F
                Data 79      ; O
                Data 85      ; U
                Data 78      ; N
                Data 68      ; D
                Data 0

LOC 770
NOTFDTXT:       Data 78      ; N
                Data 79      ; O
                Data 84      ; T
                Data 32      ; space
                Data 70      ; F
                Data 79      ; O
                Data 85      ; U
                Data 78      ; N
                Data 68      ; D
                Data 0