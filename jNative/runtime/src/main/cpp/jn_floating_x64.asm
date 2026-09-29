; Preserve the x87 environment and MXCSR without the CRT's synchronization and
; mode translation. Do not restore XMM registers: they may contain return values.
.code
jn_save_floating_x64 PROC
    fnstenv [rcx]
    fldenv [rcx]
    stmxcsr dword ptr [rcx+28]
    ret
jn_save_floating_x64 ENDP
jn_restore_floating_x64 PROC
    fldenv [rcx]
    ldmxcsr dword ptr [rcx+28]
    ret
jn_restore_floating_x64 ENDP
; Java always uses the saved default controls with clear exception flags. Most
; imports leave x87 unchanged, so avoid reloading its whole environment. The
; caller's x64 home area is scratch space; this remains a leaf with no frame.
jn_java_floating_x64 PROC
    fnstcw word ptr [rsp+8]
    movzx eax, word ptr [rsp+8]
    cmp ax, word ptr [rcx]
    jne reset_all
    fnstsw ax
    test ax, ax
    jne reset_all
    stmxcsr dword ptr [rsp+8]
    mov eax, dword ptr [rsp+8]
    cmp eax, dword ptr [rcx+28]
    je ready
    ldmxcsr dword ptr [rcx+28]
ready:
    ret
reset_all:
    fldenv [rcx]
    ldmxcsr dword ptr [rcx+28]
    ret
jn_java_floating_x64 ENDP
END
