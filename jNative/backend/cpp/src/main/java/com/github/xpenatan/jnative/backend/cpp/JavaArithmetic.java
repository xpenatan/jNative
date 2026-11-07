package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.Type;

/** Typed JVM binary arithmetic shared by ordinary lowering and inlined expressions. */
record JavaArithmetic(int opcode, Type type) {
    static JavaArithmetic decode(int opcode) {
        if (opcode >= IADD && opcode <= DREM) {
            Type type = switch ((opcode - IADD) % 4) {
                case 0 -> Type.INT_TYPE;
                case 1 -> Type.LONG_TYPE;
                case 2 -> Type.FLOAT_TYPE;
                default -> Type.DOUBLE_TYPE;
            };
            return new JavaArithmetic(opcode, type);
        }
        if (opcode >= ISHL && opcode <= LXOR)
            return new JavaArithmetic(opcode, (opcode & 1) == 0 ? Type.INT_TYPE : Type.LONG_TYPE);
        return null;
    }

    Type rightType() {
        return opcode >= ISHL && opcode <= LUSHR ? Type.INT_TYPE : type;
    }

    private boolean integral() {
        return type.equals(Type.INT_TYPE) || type.equals(Type.LONG_TYPE);
    }

    JavaEffects effects() {
        return integral() && opcode >= IDIV && opcode <= DREM ? JavaEffects.RUNTIME_CHECK : JavaEffects.PURE;
    }

    String render(String left, String right) {
        if (opcode <= DREM) {
            int operation = (opcode - IADD) / 4;
            if (integral()) {
                String name = switch (operation) {
                    case 0 -> "add";
                    case 1 -> "sub";
                    case 2 -> "mul";
                    case 3 -> "divide";
                    default -> "remainder";
                };
                return helper(name, left, right);
            }
            if (operation == 4) return "std::fmod(" + left + ", " + right + ")";
            return infix("+-*/".charAt(operation), left, right);
        }
        if (opcode <= LUSHR) {
            String name = switch ((opcode - ISHL) / 2) {
                case 0 -> "shift_left";
                case 1 -> "shift_right";
                default -> "unsigned_shift";
            };
            return helper(name, left, right);
        }
        return infix("&|^".charAt((opcode - IAND) / 2), left, right);
    }

    private static String helper(String name, String left, String right) {
        return "::jnative::" + name + "(" + left + ", " + right + ")";
    }

    private static String infix(char operator, String left, String right) {
        return "(" + left + " " + operator + " " + right + ")";
    }
}
