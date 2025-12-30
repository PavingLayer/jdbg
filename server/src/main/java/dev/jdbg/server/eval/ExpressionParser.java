package dev.jdbg.server.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple recursive descent parser for Java expressions.
 * 
 * Supported syntax:
 * - Literals: 123, 3.14, "string", 'c', true, false, null
 * - Variables: foo, this
 * - Field access: obj.field
 * - Method calls: obj.method(arg1, arg2)
 * - Array access: arr[index]
 * - Unary operators: !, -, +
 * - Binary operators: +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||
 * - Parentheses: (expr)
 * - instanceof: obj instanceof Type
 * - Cast: (Type) expr
 */
public final class ExpressionParser {
    
    private final String source;
    private int pos;
    
    public ExpressionParser(final String source) {
        this.source = source;
        this.pos = 0;
    }
    
    public Expr parse() throws ParseException {
        skipWhitespace();
        final Expr expr = parseOr();
        skipWhitespace();
        if (pos < source.length()) {
            throw new ParseException("Unexpected character: " + source.charAt(pos), pos);
        }
        return expr;
    }
    
    // Precedence levels (lowest to highest):
    // ||
    // &&
    // == !=
    // < > <= >= instanceof
    // + -
    // * / %
    // unary (! - +)
    // postfix (. [] ())
    
    private Expr parseOr() throws ParseException {
        Expr left = parseAnd();
        while (match("||")) {
            final Expr right = parseAnd();
            left = new BinaryExpr(left, "||", right);
        }
        return left;
    }
    
    private Expr parseAnd() throws ParseException {
        Expr left = parseEquality();
        while (match("&&")) {
            final Expr right = parseEquality();
            left = new BinaryExpr(left, "&&", right);
        }
        return left;
    }
    
    private Expr parseEquality() throws ParseException {
        Expr left = parseComparison();
        while (true) {
            if (match("==")) {
                left = new BinaryExpr(left, "==", parseComparison());
            } else if (match("!=")) {
                left = new BinaryExpr(left, "!=", parseComparison());
            } else {
                break;
            }
        }
        return left;
    }
    
    private Expr parseComparison() throws ParseException {
        Expr left = parseAdditive();
        while (true) {
            if (match("<=")) {
                left = new BinaryExpr(left, "<=", parseAdditive());
            } else if (match(">=")) {
                left = new BinaryExpr(left, ">=", parseAdditive());
            } else if (match("<")) {
                left = new BinaryExpr(left, "<", parseAdditive());
            } else if (match(">")) {
                left = new BinaryExpr(left, ">", parseAdditive());
            } else if (matchKeyword("instanceof")) {
                final String typeName = parseTypeName();
                left = new InstanceOfExpr(left, typeName);
            } else {
                break;
            }
        }
        return left;
    }
    
    private Expr parseAdditive() throws ParseException {
        Expr left = parseMultiplicative();
        while (true) {
            if (match("+")) {
                left = new BinaryExpr(left, "+", parseMultiplicative());
            } else if (match("-")) {
                left = new BinaryExpr(left, "-", parseMultiplicative());
            } else {
                break;
            }
        }
        return left;
    }
    
    private Expr parseMultiplicative() throws ParseException {
        Expr left = parseUnary();
        while (true) {
            if (match("*")) {
                left = new BinaryExpr(left, "*", parseUnary());
            } else if (match("/")) {
                left = new BinaryExpr(left, "/", parseUnary());
            } else if (match("%")) {
                left = new BinaryExpr(left, "%", parseUnary());
            } else {
                break;
            }
        }
        return left;
    }
    
    private Expr parseUnary() throws ParseException {
        if (match("!")) {
            return new UnaryExpr("!", parseUnary());
        }
        if (match("-")) {
            return new UnaryExpr("-", parseUnary());
        }
        if (match("+")) {
            return parseUnary(); // Unary + is a no-op
        }
        
        // Check for cast: (Type) expr
        if (peek() == '(' && lookAheadForCast()) {
            expect('(');
            final String typeName = parseTypeName();
            expect(')');
            return new CastExpr(typeName, parseUnary());
        }
        
        return parsePostfix();
    }
    
    private boolean lookAheadForCast() {
        // Save position
        final int savedPos = pos;
        try {
            if (source.charAt(pos) != '(') return false;
            pos++;
            skipWhitespace();
            
            // Check if it looks like a type name (identifier, possibly with dots and [])
            if (!Character.isJavaIdentifierStart(peek())) return false;
            
            while (pos < source.length()) {
                if (Character.isJavaIdentifierPart(peek())) {
                    pos++;
                } else if (peek() == '.') {
                    pos++;
                } else if (peek() == '[') {
                    pos++;
                    if (peek() == ']') pos++;
                } else {
                    break;
                }
            }
            
            skipWhitespace();
            return peek() == ')';
        } finally {
            pos = savedPos;
        }
    }
    
    private Expr parsePostfix() throws ParseException {
        Expr expr = parsePrimary();
        
        while (true) {
            skipWhitespace();
            if (match(".")) {
                final String name = parseIdentifier();
                if (peek() == '(') {
                    // Method call
                    final List<Expr> args = parseArguments();
                    expr = new MethodCallExpr(expr, name, args);
                } else {
                    // Field access
                    expr = new FieldAccessExpr(expr, name);
                }
            } else if (match("[")) {
                final Expr index = parseOr();
                expect(']');
                expr = new ArrayAccessExpr(expr, index);
            } else {
                break;
            }
        }
        
        return expr;
    }
    
    private Expr parsePrimary() throws ParseException {
        skipWhitespace();
        
        // Parenthesized expression
        if (match("(")) {
            final Expr expr = parseOr();
            expect(')');
            return expr;
        }
        
        // String literal
        if (peek() == '"') {
            return new LiteralExpr(parseStringLiteral(), String.class);
        }
        
        // Character literal
        if (peek() == '\'') {
            return new LiteralExpr(parseCharLiteral(), char.class);
        }
        
        // Number literal
        if (Character.isDigit(peek()) || (peek() == '.' && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1)))) {
            return parseNumberLiteral();
        }
        
        // Keywords and identifiers
        if (Character.isJavaIdentifierStart(peek())) {
            final String name = parseIdentifier();
            
            // Keywords
            switch (name) {
                case "true":
                    return new LiteralExpr(true, boolean.class);
                case "false":
                    return new LiteralExpr(false, boolean.class);
                case "null":
                    return new LiteralExpr(null, Object.class);
                case "this":
                    return new ThisExpr();
                case "new":
                    return parseNewExpression();
                default:
                    // Check if it's a method call
                    if (peek() == '(') {
                        final List<Expr> args = parseArguments();
                        return new MethodCallExpr(null, name, args);
                    }
                    // Variable reference
                    return new VariableExpr(name);
            }
        }
        
        throw new ParseException("Expected expression, got: " + (pos < source.length() ? source.charAt(pos) : "EOF"), pos);
    }
    
    private Expr parseNewExpression() throws ParseException {
        skipWhitespace();
        final String typeName = parseTypeName();
        
        if (peek() == '[') {
            // Array creation: new Type[size]
            expect('[');
            final Expr size = parseOr();
            expect(']');
            return new NewArrayExpr(typeName, size);
        } else {
            // Object creation: new Type(args)
            final List<Expr> args = parseArguments();
            return new NewObjectExpr(typeName, args);
        }
    }
    
    private List<Expr> parseArguments() throws ParseException {
        final List<Expr> args = new ArrayList<>();
        expect('(');
        skipWhitespace();
        
        if (peek() != ')') {
            args.add(parseOr());
            while (match(",")) {
                args.add(parseOr());
            }
        }
        
        expect(')');
        return args;
    }
    
    private String parseIdentifier() throws ParseException {
        skipWhitespace();
        final int start = pos;
        if (!Character.isJavaIdentifierStart(peek())) {
            throw new ParseException("Expected identifier", pos);
        }
        while (pos < source.length() && Character.isJavaIdentifierPart(source.charAt(pos))) {
            pos++;
        }
        return source.substring(start, pos);
    }
    
    private String parseTypeName() throws ParseException {
        skipWhitespace();
        final StringBuilder sb = new StringBuilder();
        sb.append(parseIdentifier());
        
        // Handle qualified names (java.lang.String)
        while (peek() == '.') {
            final int savedPos = pos;
            pos++;
            skipWhitespace();
            if (Character.isJavaIdentifierStart(peek())) {
                sb.append('.').append(parseIdentifier());
            } else {
                pos = savedPos;
                break;
            }
        }
        
        // Handle array types
        while (peek() == '[') {
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                sb.append("[]");
            } else {
                throw new ParseException("Expected ']'", pos);
            }
        }
        
        return sb.toString();
    }
    
    private String parseStringLiteral() throws ParseException {
        expect('"');
        final StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (source.charAt(pos) == '\\') {
                pos++;
                if (pos >= source.length()) {
                    throw new ParseException("Unterminated string", pos);
                }
                sb.append(parseEscapeSequence());
            } else {
                sb.append(source.charAt(pos++));
            }
        }
        expect('"');
        return sb.toString();
    }
    
    private char parseCharLiteral() throws ParseException {
        expect('\'');
        char c;
        if (source.charAt(pos) == '\\') {
            pos++;
            c = parseEscapeSequence();
        } else {
            c = source.charAt(pos++);
        }
        expect('\'');
        return c;
    }
    
    private char parseEscapeSequence() throws ParseException {
        final char c = source.charAt(pos++);
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case '0' -> '\0';
            default -> c;
        };
    }
    
    private Expr parseNumberLiteral() throws ParseException {
        final int start = pos;
        boolean isDouble = false;
        boolean isLong = false;
        boolean isFloat = false;
        
        // Integer part
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }
        
        // Decimal part
        if (pos < source.length() && source.charAt(pos) == '.') {
            isDouble = true;
            pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        
        // Exponent
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            isDouble = true;
            pos++;
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        
        // Type suffix
        if (pos < source.length()) {
            final char suffix = source.charAt(pos);
            if (suffix == 'L' || suffix == 'l') {
                isLong = true;
                pos++;
            } else if (suffix == 'F' || suffix == 'f') {
                isFloat = true;
                pos++;
            } else if (suffix == 'D' || suffix == 'd') {
                isDouble = true;
                pos++;
            }
        }
        
        final String numStr = source.substring(start, pos - (isLong || isFloat || (isDouble && source.charAt(pos-1) == 'd') ? 1 : 0));
        
        if (isFloat) {
            return new LiteralExpr(Float.parseFloat(numStr), float.class);
        } else if (isDouble) {
            return new LiteralExpr(Double.parseDouble(numStr), double.class);
        } else if (isLong) {
            return new LiteralExpr(Long.parseLong(numStr), long.class);
        } else {
            return new LiteralExpr(Integer.parseInt(numStr), int.class);
        }
    }
    
    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }
    
    private char peek() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }
    
    private boolean match(final String s) {
        skipWhitespace();
        if (source.startsWith(s, pos)) {
            // Make sure we're not matching a prefix of a longer operator
            if (s.length() == 1 && pos + 1 < source.length()) {
                final char next = source.charAt(pos + 1);
                if ((s.equals("<") || s.equals(">") || s.equals("=") || s.equals("!")) && next == '=') {
                    return false;
                }
                if ((s.equals("&") && next == '&') || (s.equals("|") && next == '|')) {
                    return false;
                }
            }
            pos += s.length();
            return true;
        }
        return false;
    }
    
    private boolean matchKeyword(final String keyword) {
        skipWhitespace();
        if (source.startsWith(keyword, pos)) {
            final int end = pos + keyword.length();
            if (end >= source.length() || !Character.isJavaIdentifierPart(source.charAt(end))) {
                pos = end;
                return true;
            }
        }
        return false;
    }
    
    private void expect(final char c) throws ParseException {
        skipWhitespace();
        if (pos >= source.length() || source.charAt(pos) != c) {
            throw new ParseException("Expected '" + c + "'", pos);
        }
        pos++;
    }
    
    // --- AST Node Types ---
    
    public sealed interface Expr permits 
            LiteralExpr, VariableExpr, ThisExpr, FieldAccessExpr, MethodCallExpr,
            ArrayAccessExpr, BinaryExpr, UnaryExpr, CastExpr, InstanceOfExpr,
            NewObjectExpr, NewArrayExpr {}
    
    public record LiteralExpr(Object value, Class<?> type) implements Expr {}
    public record VariableExpr(String name) implements Expr {}
    public record ThisExpr() implements Expr {}
    public record FieldAccessExpr(Expr target, String fieldName) implements Expr {}
    public record MethodCallExpr(Expr target, String methodName, List<Expr> arguments) implements Expr {}
    public record ArrayAccessExpr(Expr target, Expr index) implements Expr {}
    public record BinaryExpr(Expr left, String operator, Expr right) implements Expr {}
    public record UnaryExpr(String operator, Expr operand) implements Expr {}
    public record CastExpr(String typeName, Expr operand) implements Expr {}
    public record InstanceOfExpr(Expr operand, String typeName) implements Expr {}
    public record NewObjectExpr(String typeName, List<Expr> arguments) implements Expr {}
    public record NewArrayExpr(String typeName, Expr size) implements Expr {}
    
    public static final class ParseException extends Exception {
        private final int position;
        
        public ParseException(final String message, final int position) {
            super(message + " at position " + position);
            this.position = position;
        }
        
        public int getPosition() {
            return position;
        }
    }
}

