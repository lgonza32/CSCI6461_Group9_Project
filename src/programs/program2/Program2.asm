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
; 7. If found, print:
;       FOUND
;       SENTENCE n
;       WORD m
;    otherwise print:
;       NOT FOUND
; 8. Halt
;
; Implementation notes:
; - Uses base-address indexing because the instruction Address
;   field is only 5 bits.
; - X1 is used as the moving pointer into the active text page.
; - X2 is used as the current input compare pointer during compare.
; - X3 is used as the active code-page base for jumps between
;   the main page, print page, prompt pages, input page,
;   search page, compare page, skip page, and result pages.
; =========================================================

LOC 0

BOOT:       LDX 1,PARABASEPTR
            LDX 2,WORKBASEPTR
            LDX 3,CODE1PTR

            STX 1,X1CURPTR,1
            JMA 3,0

ZERO:           Data 0
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
CMPEXTPTR:      Data 384
SEARCH2PTR:     Data 416
FOUNDEXTPTR:    Data 448

; low-memory indirect pointers into WORK page / text pages
X1CURPTR:       Data 710
INPTRPTR:       Data 711
SCANPTRPTR:     Data 712
WORDPTRPTR:     Data 713
CMPINPTRPTR:    Data 715
FOUNDTXTPTR:    Data 760

; =========================================================
; MAIN CODE PAGE
; =========================================================

LOC 64
CODE1:          LDX 1,PARABASEPTR
                STX 1,X1CURPTR,1

                LDX 3,PRINTPTR
                JMA 3,0

RET_PRINT:      LDX 1,PROMPT1BASEPTR
                STX 1,X1CURPTR,1

                LDX 3,PROMPT1PTR
                JMA 3,0

RET_PROMPT1:    LDR 0,0,NEWLINECHAR
                OUT 0,1

                LDX 1,PROMPT2BASEPTR
                STX 1,X1CURPTR,1

                LDX 3,PROMPT2PTR
                JMA 3,0

RET_PROMPT2:    LDR 0,0,NEWLINECHAR
                OUT 0,1

                LDX 1,INPUTBASEPTR
                STX 1,INPTRPTR,1

                LDX 3,INPUTPTR
                JMA 3,0

RET_INPUT:      HLT

; =========================================================
; PRINT PAGE
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
; =========================================================

LOC 128
INPUTPAGE:      LDX 1,INPTRPTR,1

INPUTLOOP:      IN 0,0

                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,INPUTDONE-INPUTPAGE

                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,INPUTDONE-INPUTPAGE

                STR 0,1,0

                STX 1,INPTRPTR,1
                LDR 0,0,INPTRPTR,1
                AIR 0,1
                STR 0,0,INPTRPTR,1
                LDX 1,INPTRPTR,1

                JMA 3,INPUTLOOP-INPUTPAGE

INPUTDONE:      LDR 0,0,ZERO
                STR 0,1,0

                ; initialize search state
                LDX 1,PARABASEPTR
                STX 1,SCANPTRPTR,1

                LDR 0,0,ZERO
                AIR 0,1
                STR 0,2,SENTNUM-WORK

                LDR 0,0,ZERO
                STR 0,2,WORDNUM-WORK
                STR 0,2,INWORDFLAG-WORK
                STR 0,2,MATCHFLAG-WORK
                STR 0,2,FOUND_SENT-WORK
                STR 0,2,FOUND_WORD-WORK

                LDX 3,SEARCHPTR
                JMA 3,SEARCHLOOP-SEARCHPAGE

; =========================================================
; PROMPT PAGE 1
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
; - detect separators on the main search page
; - delegate word-start and counter updates to SEARCH2PAGE
; - jump to FOUND or NOT FOUND result pages
; =========================================================

LOC 224
SEARCHPAGE:     LDX 1,SCANPTRPTR,1

SEARCHLOOP:     LDR 0,1,0
                JZ 0,3,FAILLOCAL-SEARCHPAGE

                ; newline does not affect word/sentence count
                LDR 1,0,NEWLINECHAR
                TRR 0,1
                JCC 3,3,NEWLINELOCAL-SEARCHPAGE

                ; space ends current word
                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,SPACELOCAL-SEARCHPAGE

                ; period ends current word and sentence
                LDR 1,0,PERIODCHAR
                TRR 0,1
                JCC 3,3,PERIODLOCAL-SEARCHPAGE

                ; non-separator character
                ; if already inside a word, just advance
                LDR 0,2,INWORDFLAG-WORK
                JZ 0,3,STARTLOCAL-SEARCHPAGE

ADVLETTERLOCAL: LDX 3,SEARCH2PTR
                JMA 3,ADVANCECHAR2-SEARCH2PAGE

STARTLOCAL:     LDX 3,SEARCH2PTR
                JMA 3,0

NEWLINELOCAL:   LDX 3,SEARCH2PTR
                JMA 3,HANDLENEWLINE2-SEARCH2PAGE

SPACELOCAL:     LDX 3,SEARCH2PTR
                JMA 3,HANDLESPACE2-SEARCH2PAGE

PERIODLOCAL:    LDX 3,SEARCH2PTR
                JMA 3,HANDLEPERIOD2-SEARCH2PAGE

RET_COMPARE:    LDR 0,2,MATCHFLAG-WORK
                JZ 0,3,ADVLETTERLOCAL-SEARCHPAGE

                ; match found, print FOUND page immediately
                LDX 3,FOUNDPTR
                JMA 3,0

FAILLOCAL:      LDX 3,NOTFOUNDPTR
                JMA 3,0

; =========================================================
; COMPARE PAGE
; =========================================================

LOC 256
COMPAREPAGE:    LDX 1,WORDPTRPTR,1

CMPLOOP:        LDR 0,1,0

                ; X2 <- current input pointer
                LDX 2,CMPINPTRPTR,1

                ; R1 <- current input character
                LDR 1,2,0

                ; if input char == 0, paragraph must be at boundary
                JZ 1,3,CHECKBOUND-COMPAREPAGE

                TRR 0,1
                JCC 3,3,CMPEQUAL-COMPAREPAGE

                LDX 2,WORKBASEPTR
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

                LDX 2,WORKBASEPTR
                LDX 3,SEARCHPTR
                JMA 3,RET_COMPARE-SEARCHPAGE

CMPEQUAL:       LDX 3,CMPEXTPTR
                JMA 3,0

TO_CMPSUCCESS:  LDX 3,CMPEXTPTR
                JMA 3,CMPSUCCESS2-CMPEXTPAGE

; =========================================================
; FOUND PAGE
; Responsibilities:
; - print FOUND
; - reprint matched word
; - jump to found extension page for sentence/word output
; =========================================================

LOC 288
FOUNDPAGE:      LDX 1,FOUNDTXTPTR

FOUNDLOOP:      LDR 0,1,0
                JZ 0,3,PRINTFOUNDWORD-FOUNDPAGE
                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1
                JMA 3,FOUNDLOOP-FOUNDPAGE

PRINTFOUNDWORD: LDR 0,0,NEWLINECHAR
                OUT 0,1

                ; reload X1 from saved paragraph word start
                LDX 1,WORDPTRPTR,1

WORDOUTLOOP:    LDR 0,1,0

                ; stop printing at space
                LDR 1,0,SPACECHAR
                TRR 0,1
                JCC 3,3,TO_FOUNDEXT-FOUNDPAGE

                ; stop printing at period
                LDR 1,0,PERIODCHAR
                TRR 0,1
                JCC 3,3,TO_FOUNDEXT-FOUNDPAGE

                ; stop printing at zero
                JZ 0,3,TO_FOUNDEXT-FOUNDPAGE

                OUT 0,1

                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1
                JMA 3,WORDOUTLOOP-FOUNDPAGE

TO_FOUNDEXT:    LDX 3,FOUNDEXTPTR
                JMA 3,0

; =========================================================
; NOT FOUND PAGE
; Responsibilities:
; - print NOT FOUND
; - halt
; =========================================================

LOC 320

; X1 <- base of this page
NOTFPG:         LDX 1,NOTFOUNDPTR
                STX 1,X1CURPTR,1

                ; advance X1 to start of inline NOT FOUND text
                LDR 0,0,X1CURPTR,1
                AIR 0,16
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

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

NOTFDTXTLOCAL:  Data 78
                Data 79
                Data 84
                Data 32
                Data 70
                Data 79
                Data 85
                Data 78
                Data 68
                Data 0

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

                ; separator means not inside word anymore
                LDR 0,0,ZERO
                STR 0,2,INWORDFLAG-WORK

                LDX 3,SEARCHPTR
                JMA 3,SEARCHLOOP-SEARCHPAGE

SEARCHFAIL2:    LDX 3,NOTFOUNDPTR
                JMA 3,0

; =========================================================
; COMPARE PAGE 2
; =========================================================

LOC 384
CMPEXTPAGE:     STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                ; advance input pointer using X2
                STX 2,CMPINPTRPTR,1
                LDR 0,0,CMPINPTRPTR,1
                AIR 0,1
                STR 0,0,CMPINPTRPTR,1

                LDX 3,CMPPTRLBL
                JMA 3,CMPLOOP-COMPAREPAGE

CMPSUCCESS2:    LDX 2,WORKBASEPTR
                LDR 0,0,ZERO
                AIR 0,1
                STR 0,2,MATCHFLAG-WORK

                LDX 3,SEARCHPTR
                JMA 3,RET_COMPARE-SEARCHPAGE

; =========================================================
; SEARCH PAGE 2
; Responsibilities:
; - handle word-start bookkeeping
; - handle sentence/word counter updates
; - advance the paragraph pointer and resume search
; =========================================================

LOC 416

; starting a new word
SEARCH2PAGE:    LDR 0,0,ZERO
                AIR 0,1
                STR 0,2,INWORDFLAG-WORK

                ; WORDNUM++
                LDR 0,2,WORDNUM-WORK
                AIR 0,1
                STR 0,2,WORDNUM-WORK

                ; save candidate word start
                STX 1,WORDPTRPTR,1

                ; reset input compare pointer to INPUTBUF
                LDX 1,INPUTBASEPTR
                STX 1,CMPINPTRPTR,1

                ; clear match flag
                LDR 0,0,ZERO
                STR 0,2,MATCHFLAG-WORK

                ; restore paragraph pointer into X1 for compare
                LDX 1,WORDPTRPTR,1
                LDX 3,CMPPTRLBL
                JMA 3,0

HANDLENEWLINE2: JMA 3,ADVANCECHAR2-SEARCH2PAGE

; space ends current word
HANDLESPACE2:   LDR 0,0,ZERO
                STR 0,2,INWORDFLAG-WORK
                JMA 3,ADVANCECHAR2-SEARCH2PAGE

; period ends current word and sentence
HANDLEPERIOD2:  LDR 0,0,ZERO
                STR 0,2,INWORDFLAG-WORK
                STR 0,2,WORDNUM-WORK

                ; SENTNUM++
                LDR 0,2,SENTNUM-WORK
                AIR 0,1
                STR 0,2,SENTNUM-WORK
                JMA 3,ADVANCECHAR2-SEARCH2PAGE

ADVANCECHAR2:   STX 1,SCANPTRPTR,1
                LDR 0,0,SCANPTRPTR,1
                AIR 0,1
                STR 0,0,SCANPTRPTR,1
                LDX 1,SCANPTRPTR,1
                LDX 3,SEARCHPTR
                JMA 3,SEARCHLOOP-SEARCHPAGE

; =========================================================
; FOUND PAGE 2
; Responsibilities:
; - print sentence number
; - print word number
; - halt
; =========================================================

LOC 448
FOUNDEXTPAGE:   LDX 2,WORKBASEPTR
                LDR 0,0,NEWLINECHAR
                OUT 0,1

                LDR 0,2,SENTNUM-WORK
                AMR 0,0,PERIODCHAR
                AIR 0,2
                OUT 0,1

                LDR 0,0,NEWLINECHAR
                OUT 0,1

                LDR 0,2,WORDNUM-WORK
                AMR 0,0,PERIODCHAR
                AIR 0,2
                OUT 0,1

FOUNDDONE2:     HLT

; =========================================================
; PARAGRAPH PAGE
; =========================================================

LOC 480
PARAGRAPH:      Data 73
                Data 32
                Data 67
                Data 76
                Data 73
                Data 77
                Data 66
                Data 46
                Data 10

                Data 77
                Data 89
                Data 32
                Data 77
                Data 79
                Data 77
                Data 32
                Data 82
                Data 85
                Data 78
                Data 83
                Data 46
                Data 10

                Data 77
                Data 89
                Data 32
                Data 68
                Data 65
                Data 68
                Data 32
                Data 66
                Data 73
                Data 75
                Data 69
                Data 83
                Data 46
                Data 10

                Data 77
                Data 89
                Data 32
                Data 83
                Data 73
                Data 83
                Data 84
                Data 69
                Data 82
                Data 32
                Data 83
                Data 73
                Data 78
                Data 71
                Data 83
                Data 46
                Data 10

                Data 77
                Data 89
                Data 32
                Data 71
                Data 82
                Data 65
                Data 78
                Data 68
                Data 80
                Data 65
                Data 32
                Data 72
                Data 73
                Data 75
                Data 69
                Data 83
                Data 46
                Data 10

                Data 77
                Data 89
                Data 32
                Data 71
                Data 82
                Data 65
                Data 78
                Data 68
                Data 77
                Data 65
                Data 32
                Data 71
                Data 65
                Data 82
                Data 68
                Data 69
                Data 78
                Data 83
                Data 46
                Data 10
                Data 0

; =========================================================
; PROMPT PAGE 1 DATA
; =========================================================

LOC 600
PROMPT1:        Data 73
                Data 78
                Data 80
                Data 85
                Data 84
                Data 32
                Data 87
                Data 79
                Data 82
                Data 68
                Data 32
                Data 83
                Data 69
                Data 65
                Data 82
                Data 67
                Data 72
                Data 0

; =========================================================
; PROMPT PAGE 2 DATA
; =========================================================

LOC 620
PROMPT2:        Data 84
                Data 89
                Data 80
                Data 69
                Data 32
                Data 87
                Data 79
                Data 82
                Data 68
                Data 32
                Data 84
                Data 72
                Data 69
                Data 78
                Data 32
                Data 83
                Data 80
                Data 65
                Data 67
                Data 69
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
WORK:           Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0
CURX1:          Data 0
INPTR:          Data 0
SCANPTR:        Data 0
WORDPTR:        Data 0
MATCHFLAG:      Data 0
CMPINPTR:       Data 0
SENTNUM:        Data 0
WORDNUM:        Data 0
FOUND_SENT:     Data 0
FOUND_WORD:     Data 0
INWORDFLAG:     Data 0

; =========================================================
; RESULT TEXT DATA
; =========================================================

LOC 760
FOUNDTXT:       Data 70
                Data 79
                Data 85
                Data 78
                Data 68
                Data 0

LOC 770
NOTFDTXT:       Data 78
                Data 79
                Data 84
                Data 32
                Data 70
                Data 79
                Data 85
                Data 78
                Data 68
                Data 0

LOC 780
SENTTXT:        Data 83
                Data 69
                Data 78
                Data 84
                Data 69
                Data 78
                Data 67
                Data 69
                Data 32
                Data 0

LOC 790
WORDTXT:        Data 87
                Data 79
                Data 82
                Data 68
                Data 32
                Data 0