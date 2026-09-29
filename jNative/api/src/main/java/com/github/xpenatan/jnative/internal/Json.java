package com.github.xpenatan.jnative.internal;

import java.math.BigDecimal;
import java.util.*;

/**
 * Small strict JSON codec for versioned diagnostic artifacts; no polymorphic deserialization.
 */
public final class Json {
    private Json() {
    }

    public static String write(Object value) {
        if(value == null) return "null";
        if(value instanceof String s) {
            var b = new StringBuilder("\"");
            for(int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch(c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> {
                        if(c < 32) b.append(String.format("\\u%04x", (int)c));
                        else b.append(c);
                    }
                }
            }
            return b.append('"').toString();
        }
        if(value instanceof Boolean || value instanceof Number) return value.toString();
        if(value instanceof Map<?, ?> map) {
            var parts = new ArrayList<String>();
            map.forEach((k, v) -> parts.add(write(k.toString()) + ":" + write(v)));
            return "{" + String.join(",", parts) + "}";
        }
        if(value instanceof Collection<?> list)
            return "[" + String.join(",", list.stream().map(Json::write).toList()) + "]";
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass());
    }

    public static Object read(String input) {
        var p = new Parser(input);
        Object value = p.value(0);
        p.space();
        if(p.at != input.length()) throw p.error();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if(!(value instanceof Map<?, ?>))
            throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>)value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        if(!(value instanceof List<?>)) throw new IllegalArgumentException("Expected JSON array");
        return (List<Object>)value;
    }

    private static final class Parser {
        final String text;
        int at;

        Parser(String text) {
            if(text.length() > 32 * 1024 * 1024)
                throw new IllegalArgumentException("JSON exceeds 32 MiB");
            this.text = text;
        }

        IllegalArgumentException error() {
            return new IllegalArgumentException("Invalid JSON at " + at);
        }

        void space() {
            while(at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++;
        }

        boolean take(char c) {
            space();
            if(at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        Object value(int depth) {
            space();
            if(depth > 64 || at == text.length()) throw error();
            char c = text.charAt(at);
            if(c == '"') return string();
            if(take('{')) {
                var map = new LinkedHashMap<String, Object>();
                if(take('}')) return map;
                do {
                    space();
                    if(at == text.length() || text.charAt(at) != '"') throw error();
                    String key = string();
                    if(map.containsKey(key) || !take(':')) throw error();
                    map.put(key, value(depth + 1));
                    if(take('}')) return map;
                } while(take(','));
                throw error();
            }
            if(take('[')) {
                var list = new ArrayList<>();
                if(take(']')) return list;
                do {
                    list.add(value(depth + 1));
                    if(take(']')) return list;
                } while(take(','));
                throw error();
            }
            for(String word : List.of("true", "false", "null"))
                if(text.startsWith(word, at)) {
                    at += word.length();
                    return word.equals("null") ? null : Boolean.valueOf(word);
                }
            int start = at;
            while(at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String number = text.substring(start, at);
            if(!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw error();
            try {
                return new BigDecimal(number);
            } catch(NumberFormatException ex) {
                throw error();
            }
        }

        String string() {
            at++;
            var b = new StringBuilder();
            while(at < text.length()) {
                char c = text.charAt(at++);
                if(c == '"') return b.toString();
                if(c < 32) throw error();
                if(c != '\\') {
                    b.append(c);
                    continue;
                }
                if(at == text.length()) throw error();
                c = text.charAt(at++);
                switch(c) {
                    case '"', '\\', '/' -> b.append(c);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if(at + 4 > text.length()) throw error();
                        try {
                            b.append((char)Integer.parseInt(text.substring(at, at + 4), 16));
                        } catch(NumberFormatException ex) {
                            throw error();
                        }
                        at += 4;
                    }
                    default -> throw error();
                }
            }
            throw error();
        }
    }
}
