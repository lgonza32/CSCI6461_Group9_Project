; =========================================================
; Program1.asm
;
; Behavior:
; 1) Read 20 signed integers from console input
; 2) Store those 20 integers into the NUMBERS page
; 3) Read one signed query integer
; 4) Find the stored value with the smallest absolute difference
; 5) Print the query value and the closest stored value
;
; Error handling:
; - If a non-digit character appears inside a number token,
;   INVALIDFLAG is set and the program halts.
;
; Implementation notes:
; - Uses base-address indexing because the instruction Address
;   field is only 5 bits.
; - X1 is used as the moving pointer into the NUMBERS page.
; - X2 is the base pointer for the WORK page.
; - X3 is used as the active code-page base for jumps between
;   parser, search, result, and print pages.
; =========================================================

LOC 0

BOOT:       LDX 1,NUMBASEPTR          ; Load X1 with base address of NUMBERS page
            LDX 2,WORKBASEPTR         ; Load X2 with base address of WORK page
            LDX 3,CODE1PTR            ; Load X3 with base address of main code page

            ; Save the current X1 value into WORK[CURX1] so the program
            ; can increment and reload the number-array pointer through memory.
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

NUMBASEPTR:     Data 480
WORKBASEPTR:    Data 520
CODE1PTR:       Data 64
PARSEPTR:       Data 96
PARSE2PTR:      Data 128
PARSE3PTR:      Data 160
SEARCH1PTR:     Data 192
SEARCHLPPTR:    Data 224
RESULTPTR:      Data 256
PRINTPTR:       Data 288
PRINT2PTR:      Data 416
BUFENDPTR:      Data 565
XBUFPTR:        Data 538

; low-memory indirect pointer to WORK[CURX1]
X1CURPTR:       Data 530

; =========================================================
; MAIN CODE PAGE
; =========================================================

LOC 64
CODE1:      LDR 0,0,TWENTY
            STR 0,2,0                   ; WORK[0] = COUNT = 20

READ20CALL: LDR 0,0,ZERO                ; MODE = 0 means "reading one of the 20 list values"
            STR 0,2,MODE-WORK           ; Switch X3 to parser page base
            LDX 3,PARSEPTR              ; Enter parser
            JMA 3,0                     ; jump to parser page base

RET_PARSE20: LDR 0,2,CURVAL-WORK        ; Store it into the current NUMBERS slot pointed to by X1
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
; Responsibilities:
; - initialize parser state
; - read one character at a time
; - detect token termination on SPACE
; - handle optional leading '-'
; - dispatch either to digit validation or to parser finish
; =========================================================

LOC 96
PARSE1:      LDR 0,0,ZERO
             STR 0,2,CURVAL-WORK
             STR 0,2,NEGFLAG-WORK
             STR 0,2,INVALIDFLAG-WORK
             STR 0,2,HASDIGIT-WORK

PARSE_LOOP1: LDR 1,0,SPACECHAR
             IN 0,0                             ; Read one character from console input
             TRR 0,1                            ; Compare input character with SPACE delimiter
             JCC 3,3,TO_PARSE3_FINISH-PARSE1
             
             ; Input echo is disabled in the final flow so the printer only shows
             ; final result output, not the raw typed input stream.
             ; OUT 0,1           

             ; save original character
             STR 0,2,TEMPCHAR-WORK

             ; A leading '-' is allowed only if:
             ; - no digit has been seen yet
             ; - NEGFLAG is still 0
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
; Responsibilities:
; - validate that the current character is between '0' and '9'
; - convert ASCII digit to numeric digit
; - accumulate CURVAL = CURVAL * 10 + DIGIT
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
             STR 1,2,HASDIGIT-WORK          ; Record that at least one valid digit has been seen

             ; Decimal accumulation step:
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
; Responsibilities:
; - reject empty tokens or a lone '-'
; - apply the negative sign if NEGFLAG is set
; - return either to list-input mode or query-input mode
; - halt on invalid input
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
                    STR 0,2,INVALIDFLAG-WORK    ; Record that invalid input was seen
                    HLT                         ; Stop execution immediately on bad input

RET_TO_LIST: LDX 3,CODE1PTR
             JMA 3,RET_PARSE20-CODE1

RET_TO_QUERY:   LDX 3,CODE1PTR
                JMA 3,RET_QUERY-CODE1

; =========================================================
; SEARCH INIT PAGE
; Responsibilities:
; - initialize BESTVAL using the first stored number
; - initialize BESTDIFF as abs(first - query)
; - prepare SEARCHCOUNT for scanning the remaining 19 numbers
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
; Responsibilities:
; - scan each remaining stored number
; - compute abs(candidate - query)
; - update BESTVAL and BESTDIFF if the new candidate is closer
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
             JGE 0,3,NOUPDATE-SEARCHLPAGE       ; If tempdiff >= bestdiff, keep current best

             ; Found a closer value:
             ; update both BESTDIFF and BESTVAL
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
; Responsibilities:
; - print QUERY first
; - print a separator space
; - print BESTVAL second
; - halt after final output completes
; =========================================================

LOC 256
; Move QUERY into PRINTVAL so the print page can output it
RESULTPAGE:      LDR 0,0,SPACECHAR
                 OUT 0,1
                 LDR 0,0,SPACECHAR
                 OUT 0,1

                 ; Move QUERY into PRINTVAL so the print routine can output it
                 LDR 0,2,QUERY-WORK
                 STR 0,2,PRINTVAL-WORK

                 ; PRINTMODE = 0 means "printing query"
                 LDR 0,0,ZERO
                 STR 0,2,PRINTMODE-WORK

                 ; Jump to the print routine
                 LDX 3,PRINTPTR
                 JMA 3,0

; Print a space between QUERY and BESTVAL
RET_AFTER_QUERY: LDR 0,0,SPACECHAR
                 OUT 0,1

                 ; Move BESTVAL into PRINTVAL so the print page can output it
                 LDR 0,2,BESTVAL-WORK
                 STR 0,2,PRINTVAL-WORK

                 ; PRINTMODE = 1 means "printing best value"
                 LDR 0,0,ONE
                 STR 0,2,PRINTMODE-WORK

                 ; Jump to the same print routine again
                 LDX 3,PRINTPTR
                 JMA 3,0

RET_AFTER_BEST:  HLT

; =========================================================
; DATA PAGES
; =========================================================

LOC 480
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

LOC 520
WORK:           Data 0    ; [0] COUNT: numbers remaining to read
MODE:           Data 0    ; [1] MODE: 0=list input, 1=query input
CURVAL:         Data 0    ; [2] CURVAL: current parsed integer
QUERY:          Data 0    ; [3] QUERY: final query value
SEARCHCOUNT:    Data 0    ; [4] SEARCHCOUNT: numbers remaining in search loop
BESTVAL:        Data 0    ; [5] BESTVAL: closest stored value found so far
BESTDIFF:       Data 0    ; [6] BESTDIFF: smallest absolute difference so far
DIGIT:          Data 0    ; [7] DIGIT: current numeric digit during parsing
TEMPDIFF:       Data 0    ; [8] TEMPDIFF: temporary absolute difference
CANDVAL:        Data 0    ; [9] CANDVAL: current candidate value in search loop
CURX1:          Data 0    ; [10] CURX1: saved current X1 pointer value
NEGFLAG:        Data 0    ; [11] NEGFLAG: 1 if current token is negative
INVALIDFLAG:    Data 0    ; [12] INVALIDFLAG: 1 if parser sees invalid input
TEMPCHAR:       Data 0    ; [13] TEMPCHAR: raw input character being processed
HASDIGIT:       Data 0    ; [14] HASDIGIT: 1 once at least one digit is accepted
PRINTVAL:       Data 0    ; [15] PRINTVAL: value currently being printed
DIGCOUNT:       Data 0    ; [16] DIGCOUNT: number of digits buffered for output
PRINTMODE:      Data 0    ; [17] PRINTMODE: 0=query, 1=best value
XBUF:           Data 0    ; [18] XBUF: saved current print-buffer pointer

; =========================================================
; PRINT SIGNED INT PAGE 1
; Responsibilities:
; - handle sign
; - special-case zero
; - repeatedly divide by 10
; - store digits right-to-left into PRINTBUF
; - transfer control to page 2 for forward-order output
; =========================================================

LOC 288
PRINTPAGE1:    LDR 0,2,PRINTVAL-WORK
               JZ 0,3,TO_PRINT_ZERO-PRINTPAGE1

               ; Clear DIGCOUNT before extracting digits
               LDR 1,0,ZERO
               STR 1,2,DIGCOUNT-WORK

               ; If negative, print '-' and negate the value first
               JGE 0,3,PRINT_INITBUF-PRINTPAGE1
               LDR 1,0,MINUSCHAR
               OUT 1,1

               LDR 1,0,ZERO
               SMR 1,2,PRINTVAL-WORK
               STR 1,2,PRINTVAL-WORK

PRINT_INITBUF: LDX 1,BUFENDPTR
               STX 1,XBUFPTR,1
               JMA 3,EXTRACT_LOOP-PRINTPAGE1

TO_PRINT_ZERO: LDX 3,PRINT2PTR
               JMA 3,PRINT_ZERO2-PRINTPAGE2

; Divide PRINTVAL by 10
; After DVD:
;   R0 = quotient
;   R1 = remainder
EXTRACT_LOOP:  LDR 2,0,TEN
               LDR 0,2,PRINTVAL-WORK
               DVD 0,2

               ; Save quotient immediately before R0 gets reused
               STR 0,2,PRINTVAL-WORK

               ; Convert remainder to ASCII and store in buffer
               AMR 1,0,ZEROCHAR
               STR 1,1,0

               ; DIGCOUNT++
               LDR 0,2,DIGCOUNT-WORK
               AIR 0,1
               STR 0,2,DIGCOUNT-WORK

               ; Move buffer pointer left by 1
               STX 1,XBUFPTR,1
               LDR 0,0,XBUFPTR,1
               SIR 0,1
               STR 0,0,XBUFPTR,1
               LDX 1,XBUFPTR,1

               ; If quotient != 0, continue extracting digits
               LDR 0,2,PRINTVAL-WORK
               JNE 0,3,EXTRACT_LOOP-PRINTPAGE1

               ; X1 currently points one slot before the first digit.
               ; Move it right once so it points at the first digit to print.
               STX 1,XBUFPTR,1
               LDR 0,0,XBUFPTR,1
               AIR 0,1
               STR 0,0,XBUFPTR,1
               LDX 1,XBUFPTR,1

               ; Jump to page 2 to print buffered digits
               LDX 3,PRINT2PTR
               JMA 3,OUTPUT_LOOP-PRINTPAGE2
                
    ; At this point X1 sits one slot before the first digit.
    ; Move it right once so page 2 starts output at the first stored digit.

; =========================================================
; PRINT SIGNED INT PAGE 2
; Responsibilities:
; - output buffered digits from left to right
; - return to RESULTPAGE after printing QUERY or BESTVAL
; =========================================================

LOC 416
PRINTPAGE2:    JMA 3,OUTPUT_LOOP-PRINTPAGE2

PRINT_ZERO2:   LDR 0,0,ZEROCHAR
               OUT 0,1
               JMA 3,PRINT_RETURN-PRINTPAGE2

OUTPUT_LOOP:   LDR 0,2,DIGCOUNT-WORK
               JZ 0,3,PRINT_RETURN-PRINTPAGE2       ; Stop when all buffered digits are printed

               ; Output the current buffered digit
               LDR 0,1,0
               OUT 0,1

               ; Advance X1 to the next digit slot
               STX 1,XBUFPTR,1
               LDR 0,0,XBUFPTR,1
               AIR 0,1
               STR 0,0,XBUFPTR,1
               LDX 1,XBUFPTR,1

               ; DIGCOUNT--
               LDR 0,2,DIGCOUNT-WORK
               SIR 0,1
               STR 0,2,DIGCOUNT-WORK

               JMA 3,OUTPUT_LOOP-PRINTPAGE2

PRINT_RETURN:  LDR 0,2,PRINTMODE-WORK
               JZ 0,3,RET_PRINT_QUERY-PRINTPAGE2
               JMA 3,RET_PRINT_BEST-PRINTPAGE2

RET_PRINT_QUERY:    LDX 3,RESULTPTR
                    JMA 3,RET_AFTER_QUERY-RESULTPAGE

RET_PRINT_BEST:     LDX 3,RESULTPTR
                    JMA 3,RET_AFTER_BEST-RESULTPAGE

; =========================================================
; BUFFER PAGE
; =========================================================

LOC 560
PRINTBUF:       Data 0
                Data 0
                Data 0
                Data 0
                Data 0
                Data 0