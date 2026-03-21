; Program1_Test6.asm
; Read a positive integer until a space delimiter.
; Assumes all non-space characters are digits.

LOC 0

        LDR 1,0,SPACE       ; R1 <- ASCII space delimiter
        LDR 2,0,TEN         ; R2 <- 10

LOOP:   IN 0,0              ; read one character into R0
        TRR 0,1             ; compare char with space
        JCC 3,0,DONE        ; if equal, stop
        OUT 0,1             ; echo the digit character

        SMR 0,0,ZEROCHAR    ; convert ASCII digit to numeric digit
        STR 0,0,DIGIT       ; save digit temporarily

        LDR 0,0,CURRENT     ; R0 <- current accumulated value
        MLT 0,2             ; R0:R1 <- current * 10, low word goes to R1
        AMR 1,0,DIGIT       ; R1 <- (current * 10) + digit
        STR 1,0,CURRENT     ; save updated current value

        JMA 0,LOOP          ; repeat

DONE:   HLT

CURRENT:  Data 0            ; final accumulated value
ZEROCHAR: Data 48           ; ASCII '0'
SPACE:    Data 32           ; ASCII space delimiter
TEN:      Data 10
DIGIT:    Data 0            ; temporary digit storage