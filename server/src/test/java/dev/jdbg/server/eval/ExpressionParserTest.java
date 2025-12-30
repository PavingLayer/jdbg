package dev.jdbg.server.eval;

import dev.jdbg.server.eval.ExpressionParser.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ExpressionParser.
 */
class ExpressionParserTest {

    // ========================================================================
    // Literal Tests
    // ========================================================================
    
    @Nested
    class LiteralTests {
        
        @Test
        void parseIntegerLiteral() throws ParseException {
            final Expr expr = parse("42");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(42, lit.value());
            assertEquals(int.class, lit.type());
        }
        
        @Test
        void parseNegativeInteger() throws ParseException {
            final Expr expr = parse("-42");
            assertInstanceOf(UnaryExpr.class, expr);
            final UnaryExpr unary = (UnaryExpr) expr;
            assertEquals("-", unary.operator());
            assertInstanceOf(LiteralExpr.class, unary.operand());
        }
        
        @Test
        void parseLongLiteral() throws ParseException {
            final Expr expr = parse("123456789012345L");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(123456789012345L, lit.value());
            assertEquals(long.class, lit.type());
        }
        
        @Test
        void parseDoubleLiteral() throws ParseException {
            final Expr expr = parse("3.14159");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(3.14159, (Double) lit.value(), 0.00001);
            assertEquals(double.class, lit.type());
        }
        
        @Test
        void parseFloatLiteral() throws ParseException {
            final Expr expr = parse("3.14f");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(3.14f, (Float) lit.value(), 0.001f);
            assertEquals(float.class, lit.type());
        }
        
        @Test
        void parseScientificNotation() throws ParseException {
            final Expr expr = parse("1.5e10");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(1.5e10, (Double) lit.value(), 1e5);
        }
        
        @Test
        void parseStringLiteral() throws ParseException {
            final Expr expr = parse("\"hello world\"");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals("hello world", lit.value());
            assertEquals(String.class, lit.type());
        }
        
        @Test
        void parseStringWithEscapes() throws ParseException {
            final Expr expr = parse("\"hello\\nworld\\t!\"");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals("hello\nworld\t!", lit.value());
        }
        
        @Test
        void parseEmptyString() throws ParseException {
            final Expr expr = parse("\"\"");
            assertInstanceOf(LiteralExpr.class, expr);
            assertEquals("", ((LiteralExpr) expr).value());
        }
        
        @Test
        void parseCharLiteral() throws ParseException {
            final Expr expr = parse("'a'");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals('a', lit.value());
            assertEquals(char.class, lit.type());
        }
        
        @Test
        void parseCharWithEscape() throws ParseException {
            final Expr expr = parse("'\\n'");
            assertInstanceOf(LiteralExpr.class, expr);
            assertEquals('\n', ((LiteralExpr) expr).value());
        }
        
        @Test
        void parseTrueLiteral() throws ParseException {
            final Expr expr = parse("true");
            assertInstanceOf(LiteralExpr.class, expr);
            final LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(true, lit.value());
            assertEquals(boolean.class, lit.type());
        }
        
        @Test
        void parseFalseLiteral() throws ParseException {
            final Expr expr = parse("false");
            assertInstanceOf(LiteralExpr.class, expr);
            assertEquals(false, ((LiteralExpr) expr).value());
        }
        
        @Test
        void parseNullLiteral() throws ParseException {
            final Expr expr = parse("null");
            assertInstanceOf(LiteralExpr.class, expr);
            assertNull(((LiteralExpr) expr).value());
        }
    }

    // ========================================================================
    // Variable and This Tests
    // ========================================================================
    
    @Nested
    class VariableTests {
        
        @Test
        void parseSimpleVariable() throws ParseException {
            final Expr expr = parse("foo");
            assertInstanceOf(VariableExpr.class, expr);
            assertEquals("foo", ((VariableExpr) expr).name());
        }
        
        @Test
        void parseVariableWithUnderscore() throws ParseException {
            final Expr expr = parse("_myVar");
            assertInstanceOf(VariableExpr.class, expr);
            assertEquals("_myVar", ((VariableExpr) expr).name());
        }
        
        @Test
        void parseVariableWithNumbers() throws ParseException {
            final Expr expr = parse("var123");
            assertInstanceOf(VariableExpr.class, expr);
            assertEquals("var123", ((VariableExpr) expr).name());
        }
        
        @Test
        void parseThis() throws ParseException {
            final Expr expr = parse("this");
            assertInstanceOf(ThisExpr.class, expr);
        }
    }

    // ========================================================================
    // Field Access Tests
    // ========================================================================
    
    @Nested
    class FieldAccessTests {
        
        @Test
        void parseSimpleFieldAccess() throws ParseException {
            final Expr expr = parse("obj.field");
            assertInstanceOf(FieldAccessExpr.class, expr);
            final FieldAccessExpr field = (FieldAccessExpr) expr;
            assertEquals("field", field.fieldName());
            assertInstanceOf(VariableExpr.class, field.target());
        }
        
        @Test
        void parseChainedFieldAccess() throws ParseException {
            final Expr expr = parse("obj.foo.bar.baz");
            assertInstanceOf(FieldAccessExpr.class, expr);
            final FieldAccessExpr field = (FieldAccessExpr) expr;
            assertEquals("baz", field.fieldName());
            assertInstanceOf(FieldAccessExpr.class, field.target());
        }
        
        @Test
        void parseThisFieldAccess() throws ParseException {
            final Expr expr = parse("this.name");
            assertInstanceOf(FieldAccessExpr.class, expr);
            final FieldAccessExpr field = (FieldAccessExpr) expr;
            assertEquals("name", field.fieldName());
            assertInstanceOf(ThisExpr.class, field.target());
        }
    }

    // ========================================================================
    // Method Call Tests
    // ========================================================================
    
    @Nested
    class MethodCallTests {
        
        @Test
        void parseMethodCallNoArgs() throws ParseException {
            final Expr expr = parse("obj.getName()");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals("getName", call.methodName());
            assertTrue(call.arguments().isEmpty());
        }
        
        @Test
        void parseMethodCallOneArg() throws ParseException {
            final Expr expr = parse("obj.setName(\"test\")");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals("setName", call.methodName());
            assertEquals(1, call.arguments().size());
        }
        
        @Test
        void parseMethodCallMultipleArgs() throws ParseException {
            final Expr expr = parse("obj.add(1, 2, 3)");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals("add", call.methodName());
            assertEquals(3, call.arguments().size());
        }
        
        @Test
        void parseChainedMethodCalls() throws ParseException {
            final Expr expr = parse("obj.foo().bar().baz()");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals("baz", call.methodName());
        }
        
        @Test
        void parseStaticMethodCall() throws ParseException {
            // This parses as a method call with null target
            final Expr expr = parse("toString()");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals("toString", call.methodName());
            assertNull(call.target());
        }
        
        @Test
        void parseMethodCallWithExpressionArgs() throws ParseException {
            final Expr expr = parse("obj.calculate(a + b, c * d)");
            assertInstanceOf(MethodCallExpr.class, expr);
            final MethodCallExpr call = (MethodCallExpr) expr;
            assertEquals(2, call.arguments().size());
            assertInstanceOf(BinaryExpr.class, call.arguments().get(0));
            assertInstanceOf(BinaryExpr.class, call.arguments().get(1));
        }
    }

    // ========================================================================
    // Array Access Tests
    // ========================================================================
    
    @Nested
    class ArrayAccessTests {
        
        @Test
        void parseSimpleArrayAccess() throws ParseException {
            final Expr expr = parse("arr[0]");
            assertInstanceOf(ArrayAccessExpr.class, expr);
            final ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            assertInstanceOf(VariableExpr.class, arr.target());
            assertInstanceOf(LiteralExpr.class, arr.index());
        }
        
        @Test
        void parseArrayAccessWithVariable() throws ParseException {
            final Expr expr = parse("arr[i]");
            assertInstanceOf(ArrayAccessExpr.class, expr);
            final ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            assertInstanceOf(VariableExpr.class, arr.index());
        }
        
        @Test
        void parseArrayAccessWithExpression() throws ParseException {
            final Expr expr = parse("arr[i + 1]");
            assertInstanceOf(ArrayAccessExpr.class, expr);
            final ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            assertInstanceOf(BinaryExpr.class, arr.index());
        }
        
        @Test
        void parseMultidimensionalArray() throws ParseException {
            final Expr expr = parse("matrix[i][j]");
            assertInstanceOf(ArrayAccessExpr.class, expr);
            final ArrayAccessExpr outer = (ArrayAccessExpr) expr;
            assertInstanceOf(ArrayAccessExpr.class, outer.target());
        }
        
        @Test
        void parseArrayAccessOnMethodResult() throws ParseException {
            final Expr expr = parse("getArray()[0]");
            assertInstanceOf(ArrayAccessExpr.class, expr);
            final ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            assertInstanceOf(MethodCallExpr.class, arr.target());
        }
    }

    // ========================================================================
    // Binary Operator Tests
    // ========================================================================
    
    @Nested
    class BinaryOperatorTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"+", "-", "*", "/", "%"})
        void parseArithmeticOperators(final String op) throws ParseException {
            final Expr expr = parse("a " + op + " b");
            assertInstanceOf(BinaryExpr.class, expr);
            assertEquals(op, ((BinaryExpr) expr).operator());
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"==", "!=", "<", ">", "<=", ">="})
        void parseComparisonOperators(final String op) throws ParseException {
            final Expr expr = parse("a " + op + " b");
            assertInstanceOf(BinaryExpr.class, expr);
            assertEquals(op, ((BinaryExpr) expr).operator());
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"&&", "||"})
        void parseLogicalOperators(final String op) throws ParseException {
            final Expr expr = parse("a " + op + " b");
            assertInstanceOf(BinaryExpr.class, expr);
            assertEquals(op, ((BinaryExpr) expr).operator());
        }
        
        @Test
        void parseOperatorPrecedence_MultiplicationBeforeAddition() throws ParseException {
            // a + b * c should be parsed as a + (b * c)
            final Expr expr = parse("a + b * c");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("+", bin.operator());
            assertInstanceOf(VariableExpr.class, bin.left());
            assertInstanceOf(BinaryExpr.class, bin.right());
            assertEquals("*", ((BinaryExpr) bin.right()).operator());
        }
        
        @Test
        void parseOperatorPrecedence_ParenthesesOverride() throws ParseException {
            // (a + b) * c
            final Expr expr = parse("(a + b) * c");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("*", bin.operator());
            assertInstanceOf(BinaryExpr.class, bin.left());
            assertEquals("+", ((BinaryExpr) bin.left()).operator());
        }
        
        @Test
        void parseOperatorPrecedence_ComparisonBeforeLogical() throws ParseException {
            // a < b && c > d should be (a < b) && (c > d)
            final Expr expr = parse("a < b && c > d");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("&&", bin.operator());
            assertInstanceOf(BinaryExpr.class, bin.left());
            assertInstanceOf(BinaryExpr.class, bin.right());
        }
        
        @Test
        void parseLeftAssociativity() throws ParseException {
            // a - b - c should be (a - b) - c
            final Expr expr = parse("a - b - c");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("-", bin.operator());
            assertInstanceOf(BinaryExpr.class, bin.left());
            assertInstanceOf(VariableExpr.class, bin.right());
        }
        
        @Test
        void parseStringConcatenation() throws ParseException {
            final Expr expr = parse("\"hello\" + \" \" + \"world\"");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("+", bin.operator());
        }
    }

    // ========================================================================
    // Unary Operator Tests
    // ========================================================================
    
    @Nested
    class UnaryOperatorTests {
        
        @Test
        void parseLogicalNot() throws ParseException {
            final Expr expr = parse("!flag");
            assertInstanceOf(UnaryExpr.class, expr);
            final UnaryExpr unary = (UnaryExpr) expr;
            assertEquals("!", unary.operator());
            assertInstanceOf(VariableExpr.class, unary.operand());
        }
        
        @Test
        void parseDoubleNegation() throws ParseException {
            final Expr expr = parse("!!flag");
            assertInstanceOf(UnaryExpr.class, expr);
            final UnaryExpr outer = (UnaryExpr) expr;
            assertEquals("!", outer.operator());
            assertInstanceOf(UnaryExpr.class, outer.operand());
        }
        
        @Test
        void parseUnaryMinus() throws ParseException {
            final Expr expr = parse("-value");
            assertInstanceOf(UnaryExpr.class, expr);
            final UnaryExpr unary = (UnaryExpr) expr;
            assertEquals("-", unary.operator());
        }
        
        @Test
        void parseUnaryPlus() throws ParseException {
            // Unary + should be a no-op, just returning the operand
            final Expr expr = parse("+value");
            assertInstanceOf(VariableExpr.class, expr);
        }
    }

    // ========================================================================
    // Cast Tests
    // ========================================================================
    
    @Nested
    class CastTests {
        
        @Test
        void parsePrimitiveCast() throws ParseException {
            final Expr expr = parse("(int) value");
            assertInstanceOf(CastExpr.class, expr);
            final CastExpr cast = (CastExpr) expr;
            assertEquals("int", cast.typeName());
        }
        
        @Test
        void parseObjectCast() throws ParseException {
            final Expr expr = parse("(String) obj");
            assertInstanceOf(CastExpr.class, expr);
            final CastExpr cast = (CastExpr) expr;
            assertEquals("String", cast.typeName());
        }
        
        @Test
        void parseQualifiedTypeCast() throws ParseException {
            final Expr expr = parse("(java.util.List) obj");
            assertInstanceOf(CastExpr.class, expr);
            final CastExpr cast = (CastExpr) expr;
            assertEquals("java.util.List", cast.typeName());
        }
        
        @Test
        void parseArrayTypeCast() throws ParseException {
            final Expr expr = parse("(int[]) obj");
            assertInstanceOf(CastExpr.class, expr);
            final CastExpr cast = (CastExpr) expr;
            assertEquals("int[]", cast.typeName());
        }
        
        @Test
        void parseCastInExpression() throws ParseException {
            final Expr expr = parse("(int) a + b");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertInstanceOf(CastExpr.class, bin.left());
        }
    }

    // ========================================================================
    // instanceof Tests
    // ========================================================================
    
    @Nested
    class InstanceOfTests {
        
        @Test
        void parseSimpleInstanceof() throws ParseException {
            final Expr expr = parse("obj instanceof String");
            assertInstanceOf(InstanceOfExpr.class, expr);
            final InstanceOfExpr inst = (InstanceOfExpr) expr;
            assertEquals("String", inst.typeName());
        }
        
        @Test
        void parseQualifiedInstanceof() throws ParseException {
            final Expr expr = parse("obj instanceof java.util.List");
            assertInstanceOf(InstanceOfExpr.class, expr);
            final InstanceOfExpr inst = (InstanceOfExpr) expr;
            assertEquals("java.util.List", inst.typeName());
        }
        
        @Test
        void parseInstanceofInCondition() throws ParseException {
            final Expr expr = parse("obj instanceof String && obj.length() > 0");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertEquals("&&", bin.operator());
            assertInstanceOf(InstanceOfExpr.class, bin.left());
        }
    }

    // ========================================================================
    // new Expression Tests
    // ========================================================================
    
    @Nested
    class NewExpressionTests {
        
        @Test
        void parseNewObjectNoArgs() throws ParseException {
            final Expr expr = parse("new StringBuilder()");
            assertInstanceOf(NewObjectExpr.class, expr);
            final NewObjectExpr newObj = (NewObjectExpr) expr;
            assertEquals("StringBuilder", newObj.typeName());
            assertTrue(newObj.arguments().isEmpty());
        }
        
        @Test
        void parseNewObjectWithArgs() throws ParseException {
            final Expr expr = parse("new String(\"hello\")");
            assertInstanceOf(NewObjectExpr.class, expr);
            final NewObjectExpr newObj = (NewObjectExpr) expr;
            assertEquals("String", newObj.typeName());
            assertEquals(1, newObj.arguments().size());
        }
        
        @Test
        void parseNewQualifiedType() throws ParseException {
            final Expr expr = parse("new java.util.ArrayList()");
            assertInstanceOf(NewObjectExpr.class, expr);
            assertEquals("java.util.ArrayList", ((NewObjectExpr) expr).typeName());
        }
        
        @Test
        void parseNewArray() throws ParseException {
            final Expr expr = parse("new int[10]");
            assertInstanceOf(NewArrayExpr.class, expr);
            final NewArrayExpr newArr = (NewArrayExpr) expr;
            assertEquals("int", newArr.typeName());
            assertInstanceOf(LiteralExpr.class, newArr.size());
        }
        
        @Test
        void parseNewArrayWithExpression() throws ParseException {
            final Expr expr = parse("new String[n + 1]");
            assertInstanceOf(NewArrayExpr.class, expr);
            final NewArrayExpr newArr = (NewArrayExpr) expr;
            assertEquals("String", newArr.typeName());
            assertInstanceOf(BinaryExpr.class, newArr.size());
        }
    }

    // ========================================================================
    // Complex Expression Tests
    // ========================================================================
    
    @Nested
    class ComplexExpressionTests {
        
        @Test
        void parseComplexArithmetic() throws ParseException {
            final Expr expr = parse("(a + b) * (c - d) / e");
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseMethodChainWithArithmetic() throws ParseException {
            final Expr expr = parse("list.get(0).getValue() + list.get(1).getValue()");
            assertInstanceOf(BinaryExpr.class, expr);
            final BinaryExpr bin = (BinaryExpr) expr;
            assertInstanceOf(MethodCallExpr.class, bin.left());
            assertInstanceOf(MethodCallExpr.class, bin.right());
        }
        
        @Test
        void parseConditionalExpression() throws ParseException {
            final Expr expr = parse("a > 0 && b < 100 || c == 0");
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseNestedArrayAccess() throws ParseException {
            final Expr expr = parse("matrix[row][col].getValue()");
            assertInstanceOf(MethodCallExpr.class, expr);
        }
        
        @Test
        void parseBuilderPattern() throws ParseException {
            final Expr expr = parse("new StringBuilder().append(\"a\").append(\"b\").toString()");
            assertInstanceOf(MethodCallExpr.class, expr);
            assertEquals("toString", ((MethodCallExpr) expr).methodName());
        }
    }

    // ========================================================================
    // Whitespace and Formatting Tests
    // ========================================================================
    
    @Nested
    class WhitespaceTests {
        
        @Test
        void parseWithExtraWhitespace() throws ParseException {
            final Expr expr = parse("  a   +   b  ");
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseWithNoWhitespace() throws ParseException {
            final Expr expr = parse("a+b*c");
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseWithNewlines() throws ParseException {
            final Expr expr = parse("a\n+\nb");
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseWithTabs() throws ParseException {
            final Expr expr = parse("a\t+\tb");
            assertInstanceOf(BinaryExpr.class, expr);
        }
    }

    // ========================================================================
    // Error Cases
    // ========================================================================
    
    @Nested
    class ErrorCaseTests {
        
        @Test
        void parseEmptyStringThrows() {
            assertThrows(ParseException.class, () -> parse(""));
        }
        
        @Test
        void parseUnmatchedParenthesisThrows() {
            assertThrows(ParseException.class, () -> parse("(a + b"));
        }
        
        @Test
        void parseUnmatchedBracketThrows() {
            assertThrows(ParseException.class, () -> parse("arr[0"));
        }
        
        @Test
        void parseInvalidOperatorThrows() {
            assertThrows(ParseException.class, () -> parse("a @ b"));
        }
        
        @Test
        void parseTrailingOperatorThrows() {
            assertThrows(ParseException.class, () -> parse("a +"));
        }
        
        @Test
        void parseLeadingOperatorThrows() {
            // Note: - and + are valid unary operators, so use *
            assertThrows(ParseException.class, () -> parse("* a"));
        }
        
        @Test
        void parseUnterminatedStringThrows() {
            assertThrows(ParseException.class, () -> parse("\"hello"));
        }
        
        @Test
        void parseUnterminatedCharThrows() {
            assertThrows(ParseException.class, () -> parse("'a"));
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================
    
    @Nested
    class EdgeCaseTests {
        
        @Test
        void parseKeywordLikeIdentifier() throws ParseException {
            // "trueValue" should be parsed as identifier, not boolean + identifier
            final Expr expr = parse("trueValue");
            assertInstanceOf(VariableExpr.class, expr);
            assertEquals("trueValue", ((VariableExpr) expr).name());
        }
        
        @Test
        void parseNullFieldAccess() throws ParseException {
            final Expr expr = parse("null");
            assertInstanceOf(LiteralExpr.class, expr);
        }
        
        @Test
        void parseVeryLongExpression() throws ParseException {
            // Test that parser handles long chains
            final StringBuilder sb = new StringBuilder("a");
            for (int i = 0; i < 100; i++) {
                sb.append(" + b");
            }
            final Expr expr = parse(sb.toString());
            assertInstanceOf(BinaryExpr.class, expr);
        }
        
        @Test
        void parseDeeplyNestedParentheses() throws ParseException {
            final Expr expr = parse("((((a))))");
            assertInstanceOf(VariableExpr.class, expr);
        }
        
        @Test
        void parseZeroLiteral() throws ParseException {
            final Expr expr = parse("0");
            assertInstanceOf(LiteralExpr.class, expr);
            assertEquals(0, ((LiteralExpr) expr).value());
        }
        
        @Test
        void parseDecimalStartingWithDot() throws ParseException {
            final Expr expr = parse(".5");
            assertInstanceOf(LiteralExpr.class, expr);
            assertEquals(0.5, (Double) ((LiteralExpr) expr).value(), 0.001);
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    private Expr parse(final String source) throws ParseException {
        return new ExpressionParser(source).parse();
    }
}

