package dev.snehil.cohort.parse;

import dev.snehil.cohort.parse.Token.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns rule text into tokens in a single left-to-right pass. Hand-written rather than
 * regex-based, so each token keeps its exact position and errors are specific.
 */
public final class Lexer {
    private final String input;
    private int pos = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= input.length()) {
                tokens.add(new Token(Type.EOF, "", pos));
                return tokens;
            }
            tokens.add(next());
        }
    }

    private Token next() {
        int start = pos;
        char c = input.charAt(pos);

        if (c == '(') { pos++; return new Token(Type.LPAREN, "(", start); }
        if (c == ')') { pos++; return new Token(Type.RPAREN, ")", start); }
        if (c == ',') { pos++; return new Token(Type.COMMA, ",", start); }
        if (c == '\'') return string(start);
        if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
            return number(start);
        }
        if (Character.isLetter(c) || c == '_') return word(start);
        if (c == '=' ) { pos++; return new Token(Type.SYMBOL_OP, "=", start); }
        if (c == '!' && peek(1) == '=') { pos += 2; return new Token(Type.SYMBOL_OP, "!=", start); }
        if (c == '>' || c == '<') {
            pos++;
            if (peek(0) == '=') { pos++; return new Token(Type.SYMBOL_OP, c + "=", start); }
            return new Token(Type.SYMBOL_OP, String.valueOf(c), start);
        }
        throw new RuleParseException("unexpected character '" + c + "'", input, start);
    }

    private Token string(int start) {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\' && peek(1) == '\'') { sb.append('\''); pos += 2; continue; }
            if (c == '\'') { pos++; return new Token(Type.STRING, sb.toString(), start); }
            sb.append(c);
            pos++;
        }
        throw new RuleParseException("string is never closed; add a ' at the end", input, start);
    }

    private Token number(int start) {
        pos++; // first digit or '-'
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '_')) pos++;
        if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            throw new RuleParseException("numbers can't contain letters", input, start);
        }
        return new Token(Type.NUMBER, input.substring(start, pos).replace("_", ""), start);
    }

    private Token word(int start) {
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) pos++;
        String text = input.substring(start, pos);
        Type type = switch (text.toUpperCase(Locale.ROOT)) {
            case "AND" -> Type.AND;
            case "OR" -> Type.OR;
            case "NOT" -> Type.NOT;
            case "TRUE" -> Type.TRUE;
            case "FALSE" -> Type.FALSE;
            default -> Type.IDENT;
        };
        return new Token(type, text, start);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < input.length() ? input.charAt(i) : '\0';
    }
}
