package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

/** Inclusive bounds of a JVM int. Arithmetic keeps Java wraparound. */
record JavaIntRange(int min, int max) {
    static final JavaIntRange ANY = new JavaIntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);

    static JavaIntRange constant(int value) {
        return new JavaIntRange(value, value);
    }

    Integer exact() {
        return min == max ? min : null;
    }

    boolean insideArray(int length) {
        return min >= 0 && max < length;
    }

    boolean disjoint(JavaIntRange other) {
        return max < other.min || other.max < min;
    }

    JavaIntRange merge(JavaIntRange other) {
        int low = Math.min(min, other.min), high = Math.max(max, other.max);
        // Widen promptly on a backedge rather than iterating to an int endpoint.
        return (long)high - low <= 32 ? new JavaIntRange(low, high) : ANY;
    }

    JavaIntRange binary(int opcode, JavaIntRange right) {
        if (min == max && right.min == right.max) {
            int operand = right.min;
            return switch (opcode) {
                case IADD -> constant(min + operand);
                case ISUB -> constant(min - operand);
                case IMUL -> constant(min * operand);
                case IAND -> constant(min & operand);
                case IOR -> constant(min | operand);
                case IXOR -> constant(min ^ operand);
                case ISHL -> constant(min << operand);
                case ISHR -> constant(min >> operand);
                case IUSHR -> constant(min >>> operand);
                default -> ANY;
            };
        }
        return switch (opcode) {
            case IADD -> bounded((long)min + right.min, (long)max + right.max);
            case ISUB -> bounded((long)min - right.max, (long)max - right.min);
            case IAND -> right.min >= 0 ? new JavaIntRange(0, right.max)
                    : min >= 0 ? new JavaIntRange(0, max) : ANY;
            default -> ANY;
        };
    }

    private static JavaIntRange bounded(long low, long high) {
        return low >= Integer.MIN_VALUE && high <= Integer.MAX_VALUE
                ? new JavaIntRange((int)low, (int)high) : ANY;
    }
}
