; Program1_Test5.asm
; Read exactly two digit characters, convert them, and assemble a 2-digit integer.
; Example: input 4 then 2 -> store numeric 42 in RESULT.

LOC 0

        IN 0,0                  ; read first digit char
        OUT 0,1                 ; echo first digit
        SMR 0,0,ZEROCHAR        ; convert ASCII digit to numeric value
        STR 0,0,FIRSTDIGIT      ; save first numeric digit

        IN 0,0                  ; read second digit char
        OUT 0,1                 ; echo second digit
        SMR 0,0,ZEROCHAR        ; convert ASCII digit to numeric value
        STR 0,0,SECONDDIGIT     ; save second numeric digit

        LDR 2,0,TEN             ; R2 <- 10
        LDR 0,0,FIRSTDIGIT      ; R0 <- first digit
        MLT 0,2                 ; R0:R1 <- firstDigit * 10
                                ; low result is now in R1

        AMR 1,0,SECONDDIGIT     ; R1 <- (firstDigit * 10) + secondDigit
        STR 1,0,RESULT          ; store final 2-digit value
        HLT

RESULT:      Data 0
ZEROCHAR:    Data 48            ; ASCII '0'
TEN:         Data 10
FIRSTDIGIT:  Data 0
SECONDDIGIT: Data 0