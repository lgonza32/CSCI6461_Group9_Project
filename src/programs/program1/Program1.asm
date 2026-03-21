; =========================================================
; Program1.asm
;
; Behavior:
; 1) Read 20 signed integers separated by spaces
; 2) Store them in memory
; 3) Read one signed integer query separated by space
; 4) Find the closest stored value
; 5) Store query / best value / best diff in WORK page
;
; Current limitations:
; - On invalid input, program sets INVALIDFLAG and halts
; - Final formatted output is not implemented yet
; =========================================================

LOC 0

BOOT:       LDX 1,NUMBASEPTR          ; X1 <- numbers page base
            LDX 2,WORKBASEPTR         ; X2 <- work page base
            LDX 3,CODE1PTR            ; X3 <- main code page base

            ; mirror current X1 into work page so we can update/reload it
            STX 1,X1CURPTR,1

            ; jump into main code page base
            JMA 3,0

ZERO:        Data 0
ONE:         Data 1
TEN:         Data 10
TWENTY:      Data 20
SPACECHAR:   Data 32
ZEROCHAR:    Data 48
NINECHAR:    Data 57
MINUSCHAR:   Data 45

NUMBASEPTR:  Data 320
WORKBASEPTR: Data 360
CODE1PTR:    Data 64
PARSEPTR:    Data 96
PARSE2PTR:   Data 128
PARSE3PTR:   Data 160
SEARCH1PTR:  Data 192
SEARCHLPPTR: Data 224
RESULTPTR:   Data 256
PRINTPTR:    Data 288
BUFENDPTR:   Data 405
XBUFPTR:     Data 378
PRINTSMALLPTR: Data 288

; low-memory indirect pointer to WORK[CURX1]
X1CURPTR:    Data 370

; =========================================================
; MAIN CODE PAGE
; =========================================================

LOC 64
CODE1:      LDR 0,0,TWENTY
            STR 0,2,0                  ; WORK[0] = COUNT = 20

READ20CALL: LDR 0,0,ZERO
            STR 0,2,MODE-WORK          ; MODE = 0 => list input
            LDX 3,PARSEPTR
            JMA 3,0                    ; jump to parser page base

RET_PARSE20: LDR 0,2,CURVAL-WORK       ; store parsed value into MEM[X1+0]
             STR 0,1,0

             ; increment X1 properly:
             ; save X1 -> WORK[CURX1]
             STX 1,X1CURPTR,1

             ; R0 <- WORK[CURX1]
             LDR 0,0,X1CURPTR,1
             AIR 0,1
             STR 0,0,X1CURPTR,1
             LDX 1,X1CURPTR,1

             ; restore X3 to CODE1 page base before page-local branch
             LDX 3,CODE1PTR

             ; decrement count
             LDR 0,2,0
             SIR 0,1
             STR 0,2,0
             JNE 0,3,READ20CALL-CODE1

             ; after 20 numbers, reset X1 to NUMBERS base
             LDX 1,NUMBASEPTR
             STX 1,X1CURPTR,1

READQUERY:  LDR 0,0,ONE
            STR 0,2,MODE-WORK          ; MODE = 1 => query input
            LDX 3,PARSEPTR
            JMA 3,0

RET_QUERY:  LDR 0,2,CURVAL-WORK
            STR 0,2,QUERY-WORK

            ; reset X1 to start of array before searching
            LDX 1,NUMBASEPTR
            STX 1,X1CURPTR,1

            ; jump to search initialization page
            LDX 3,SEARCH1PTR
            JMA 3,0


; =========================================================
; PARSER PAGE 1
; Input loop / minus handling / route to validation or finish
; =========================================================

LOC 96
PARSE1:      LDR 0,0,ZERO
             STR 0,2,CURVAL-WORK
             STR 0,2,NEGFLAG-WORK
             STR 0,2,INVALIDFLAG-WORK
             STR 0,2,HASDIGIT-WORK

PARSE_LOOP1: LDR 1,0,SPACECHAR
             IN 0,0
             TRR 0,1
             JCC 3,3,TO_PARSE3_FINISH-PARSE1
             OUT 0,1

             ; save original character
             STR 0,2,TEMPCHAR-WORK

             ; optional leading '-'
             ; only allowed if no digit yet and NEGFLAG == 0
             LDR 1,2,HASDIGIT-WORK
             JNE 1,3,TO_PARSE2_VALIDATE-PARSE1
             LDR 1,2,NEGFLAG-WORK
             JNE 1,3,TO_PARSE2_VALIDATE-PARSE1
             LDR 0,2,TEMPCHAR-WORK
             LDR 1,0,MINUSCHAR
             TRR 0,1
             JCC 3,3,HANDLE_MINUS-PARSE1
             JMA 3,TO_PARSE2_VALIDATE-PARSE1

HANDLE_MINUS: LDR 0,0,ONE
              STR 0,2,NEGFLAG-WORK
              JMA 3,PARSE_LOOP1-PARSE1

TO_PARSE2_VALIDATE: LDX 3,PARSE2PTR
                    JMA 3,0

TO_PARSE3_FINISH:   LDX 3,PARSE3PTR
                    JMA 3,0

; =========================================================
; PARSER PAGE 2
; Digit validation and accumulation
; =========================================================

LOC 128
PARSE2:      LDR 0,2,TEMPCHAR-WORK
             SMR 0,0,ZEROCHAR
             JGE 0,3,CHECK_UPPER-PARSE2
             JMA 3,TO_PARSE3_INVALID-PARSE2

CHECK_UPPER: STR 0,2,DIGIT-WORK

             LDR 0,2,TEMPCHAR-WORK
             SMR 0,0,NINECHAR
             JGE 0,3,CHECK_EQUAL_NINE-PARSE2
             JMA 3,ACCUMULATE-PARSE2

CHECK_EQUAL_NINE:   LDR 1,0,ZERO
                    TRR 0,1
                    JCC 3,3,ACCUMULATE-PARSE2
                    JMA 3,TO_PARSE3_INVALID-PARSE2

ACCUMULATE:  LDR 1,0,ONE
             STR 1,2,HASDIGIT-WORK

             ; CURVAL = CURVAL * 10 + DIGIT
             LDR 2,0,TEN
             LDR 0,2,CURVAL-WORK
             MLT 0,2
             AMR 1,2,DIGIT-WORK
             STR 1,2,CURVAL-WORK

             LDX 3,PARSEPTR
             JMA 3,PARSE_LOOP1-PARSE1

TO_PARSE3_INVALID:  LDX 3,PARSE3PTR
                    JMA 3,PARSE_INVALID3-PARSE3

; =========================================================
; PARSER PAGE 3
; Finish / sign apply / return / invalid halt
; =========================================================

LOC 160
PARSE3:      LDR 0,2,HASDIGIT-WORK
             JZ 0,3,PARSE_INVALID3-PARSE3

             LDR 0,2,NEGFLAG-WORK
             JNE 0,3,APPLY_NEG-PARSE3
             JMA 3,RETURNMODE-PARSE3

APPLY_NEG:   LDR 1,0,ZERO
             SMR 1,2,CURVAL-WORK
             STR 1,2,CURVAL-WORK

RETURNMODE:  LDR 0,2,MODE-WORK
             JZ 0,3,RET_TO_LIST-PARSE3
             JMA 3,RET_TO_QUERY-PARSE3

PARSE_INVALID3:     LDR 0,0,ONE
                    STR 0,2,INVALIDFLAG-WORK
                    HLT

RET_TO_LIST: LDX 3,CODE1PTR
             JMA 3,RET_PARSE20-CODE1

RET_TO_QUERY:   LDX 3,CODE1PTR
                JMA 3,RET_QUERY-CODE1

; =========================================================
; SEARCH INIT PAGE
; Initializes best value / best diff, then jumps to loop page
; =========================================================

LOC 192
SEARCH1:    LDR 0,0,TWENTY
            STR 0,2,SEARCHCOUNT-WORK

            ; BESTVAL = first stored number
            LDR 0,1,0
            STR 0,2,BESTVAL-WORK

            ; TEMPDIFF = first value - query
            SMR 0,2,QUERY-WORK
            JGE 0,3,INITABSOK-SEARCH1

            ; if negative, TEMPDIFF = 0 - TEMPDIFF
            STR 0,2,TEMPDIFF-WORK
            LDR 1,0,ZERO
            SMR 1,2,TEMPDIFF-WORK
            STR 1,2,TEMPDIFF-WORK
            JMA 3,INITSETBEST-SEARCH1

INITABSOK:  STR 0,2,TEMPDIFF-WORK

INITSETBEST:    LDR 0,2,TEMPDIFF-WORK
                STR 0,2,BESTDIFF-WORK

                ; advance X1 to second element
                STX 1,X1CURPTR,1
                LDR 0,0,X1CURPTR,1
                AIR 0,1
                STR 0,0,X1CURPTR,1
                LDX 1,X1CURPTR,1

                ; SEARCHCOUNT = 19 remaining
                LDR 0,2,SEARCHCOUNT-WORK
                SIR 0,1
                STR 0,2,SEARCHCOUNT-WORK

                ; jump to main search loop page
                LDX 3,SEARCHLPPTR
                JMA 3,0


; =========================================================
; SEARCH LOOP PAGE
; Scans remaining 19 numbers and updates BESTVAL/BESTDIFF
; =========================================================

LOC 224
SEARCHLPAGE: LDR 0,2,SEARCHCOUNT-WORK
             JZ 0,3,SEARCHDONE-SEARCHLPAGE

             ; CANDVAL = MEM[X1+0]
             LDR 0,1,0
             STR 0,2,CANDVAL-WORK

             ; TEMPDIFF = CANDVAL - QUERY
             SMR 0,2,QUERY-WORK
             JGE 0,3,ABSOK-SEARCHLPAGE

             ; if negative, TEMPDIFF = 0 - TEMPDIFF
             STR 0,2,TEMPDIFF-WORK
             LDR 1,0,ZERO
             SMR 1,2,TEMPDIFF-WORK
             STR 1,2,TEMPDIFF-WORK
             JMA 3,COMPARE-SEARCHLPAGE

ABSOK:       STR 0,2,TEMPDIFF-WORK

COMPARE:     LDR 0,2,TEMPDIFF-WORK
             SMR 0,2,BESTDIFF-WORK
             JGE 0,3,NOUPDATE-SEARCHLPAGE

             ; update best diff and best value
             LDR 0,2,TEMPDIFF-WORK
             STR 0,2,BESTDIFF-WORK
             LDR 0,2,CANDVAL-WORK
             STR 0,2,BESTVAL-WORK

NOUPDATE:    STX 1,X1CURPTR,1           ; advance X1 to next number
             LDR 0,0,X1CURPTR,1
             AIR 0,1
             STR 0,0,X1CURPTR,1
             LDX 1,X1CURPTR,1

             ; decrement remaining count
             LDR 0,2,SEARCHCOUNT-WORK
             SIR 0,1
             STR 0,2,SEARCHCOUNT-WORK

             JMA 3,0

SEARCHDONE:  LDX 3,RESULTPTR
             JMA 3,0

; =========================================================
; RESULT PAGE
; Prints QUERY, then a space, then BESTVAL
; =========================================================

LOC 256
RESULTPAGE:   LDR 0,2,QUERY-WORK
              STR 0,2,PRINTVAL-WORK
              LDR 0,0,ZERO
              STR 0,2,PRINTMODE-WORK     ; 0 = printing query
              LDX 3,PRINTSMALLPTR
              JMA 3,0

RET_AFTER_QUERY:    LDR 0,0,SPACECHAR
                    OUT 0,1

                    LDR 0,2,BESTVAL-WORK
                    STR 0,2,PRINTVAL-WORK
                    LDR 0,0,ONE
                    STR 0,2,PRINTMODE-WORK     ; 1 = printing best value
                    LDX 3,PRINTSMALLPTR
                    JMA 3,0

RET_AFTER_BEST: HLT

; =========================================================
; DATA PAGES
; =========================================================

LOC 320
NUMBERS:        Data 0
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
                Data 0
                Data 0
                Data 0
                Data 0

LOC 360
WORK:           Data 0    ; COUNT
MODE:           Data 0    ; 0=list, 1=query
CURVAL:         Data 0    ; parsed value
QUERY:          Data 0    ; query value
SEARCHCOUNT:    Data 0
BESTVAL:        Data 0
BESTDIFF:       Data 0
DIGIT:          Data 0
TEMPDIFF:       Data 0
CANDVAL:        Data 0
CURX1:          Data 0    ; current X1 mirror (absolute address)
NEGFLAG:        Data 0    ; 0=positive, 1=negative
INVALIDFLAG:    Data 0    ; 0=ok, 1=bad input
TEMPCHAR:       Data 0    ; saved raw input char
HASDIGIT:       Data 0    ; 0=no digit yet, 1=at least one digit
PRINTVAL:       Data 0    ; value currently being printed
DIGCOUNT:       Data 0    ; number of digits stored in buffer
PRINTMODE:      Data 0    ; 0=query, 1=best
XBUF:           Data 0    ; current print buffer pointer mirror

; =========================================================
; PRINT SMALL SIGNED INT PAGE
; Current simple version:
; - prints 0 correctly
; - prints leading '-' for negatives
; - prints one digit 0..9
; This is enough to verify the end-to-end result path first.
; =========================================================

LOC 288
PRINTSMALL:   LDR 0,2,PRINTVAL-WORK
              JZ 0,3,PRINT_ZERO-PRINTSMALL

              ; if negative, print '-' and negate value
              JGE 0,3,PRINT_POSITIVE-PRINTSMALL
              LDR 1,0,MINUSCHAR
              OUT 1,1

              LDR 1,0,ZERO
              SMR 1,2,PRINTVAL-WORK
              STR 1,2,PRINTVAL-WORK

PRINT_POSITIVE: LDR 0,2,PRINTVAL-WORK   ; convert one-digit value to ASCII by adding '0'
                AMR 0,0,ZEROCHAR
                OUT 0,1
                JMA 3,PRINT_RETURN-PRINTSMALL

PRINT_ZERO:   LDR 0,0,ZEROCHAR
              OUT 0,1

PRINT_RETURN: LDR 0,2,PRINTMODE-WORK
              JZ 0,3,RET_PRINT_QUERY-PRINTSMALL
              JMA 3,RET_PRINT_BEST-PRINTSMALL

RET_PRINT_QUERY:    LDX 3,RESULTPTR
                    JMA 3,RET_AFTER_QUERY-RESULTPAGE

RET_PRINT_BEST:     LDX 3,RESULTPTR
                    JMA 3,RET_AFTER_BEST-RESULTPAGE

; =========================================================
; BUFFER PAGE
; =========================================================

LOC 400
PRINTBUF:       Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0