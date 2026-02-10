; Part 0 Assembler Test Case
; --------------------------
; Purpose: Validate encoding of the basic instruction format:
;   OP r, x, address[, I]
;
; Notes:
; - LOC is included to show the intended load address in the listing file.

LOC 6
LDR 3,0,31
LDR 3,0,31,1
