package dev.jdbg.server.eval;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compiles Java expressions to bytecode at runtime.
 * <p>
 * The approach:
 * 1. Generate a wrapper class containing the expression as a method body
 * 2. Compile it using javax.tools.JavaCompiler
 * 3. Return the bytecode for injection into the target VM
 */
public final class ExpressionCompiler {
    
    private static final String EVAL_CLASS_PREFIX = "__JdbgEval_";
    private static final AtomicLong EVAL_COUNTER = new AtomicLong(0);
    
    private final JavaCompiler compiler;
    private final Map<String, CompiledExpression> cache = new ConcurrentHashMap<>();
    
    public ExpressionCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException(
                "No Java compiler available. Ensure you're running on a JDK, not a JRE.");
        }
    }
    
    /**
     * Compiles an expression in the context of a frame.
     *
     * @param expression      The Java expression to evaluate
     * @param thisTypeName    The fully qualified type of 'this' (null if static context)
     * @param localVariables  Map of variable name -> fully qualified type name
     * @param imports         List of import statements to include
     * @return Compiled expression with bytecode
     */
    public CompiledExpression compile(
            final String expression,
            final String thisTypeName,
            final Map<String, String> localVariables,
            final List<String> imports) throws CompilationException {
        
        // Check cache
        final String cacheKey = buildCacheKey(expression, thisTypeName, localVariables);
        final CompiledExpression cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        final String className = EVAL_CLASS_PREFIX + EVAL_COUNTER.incrementAndGet();
        final String sourceCode = generateSourceCode(className, expression, thisTypeName, localVariables, imports);
        
        final byte[] bytecode = compileSource(className, sourceCode);
        
        final CompiledExpression result = new CompiledExpression(
            className, bytecode, thisTypeName != null, new ArrayList<>(localVariables.keySet()));
        
        cache.put(cacheKey, result);
        return result;
    }
    
    private String buildCacheKey(final String expression, final String thisTypeName, 
                                  final Map<String, String> localVariables) {
        final StringBuilder sb = new StringBuilder();
        sb.append(expression).append("@").append(thisTypeName != null ? thisTypeName : "static");
        for (final Map.Entry<String, String> entry : localVariables.entrySet()) {
            sb.append(":").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private String generateSourceCode(
            final String className,
            final String expression,
            final String thisTypeName,
            final Map<String, String> localVariables,
            final List<String> imports) {
        
        final StringBuilder sb = new StringBuilder();
        
        // Standard imports for reflection-based evaluation
        sb.append("import java.lang.reflect.*;\n");
        sb.append("import java.util.*;\n");
        sb.append("\n");
        
        // Class declaration
        sb.append("public class ").append(className).append(" {\n");
        
        // Store variable names for lookup
        sb.append("    private static final String[] VAR_NAMES = {");
        boolean first = true;
        for (final String name : localVariables.keySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(name).append("\"");
            first = false;
        }
        sb.append("};\n\n");
        
        // Generate the evaluation method
        // Method signature: public static Object eval(Object $this, Object[] $locals, Map<String, Object> $varMap)
        sb.append("    public static Object eval(Object $this, Object[] $locals) throws Throwable {\n");
        sb.append("        // Build variable map for expression access\n");
        sb.append("        Map<String, Object> $vars = new HashMap<>();\n");
        sb.append("        for (int i = 0; i < VAR_NAMES.length && i < $locals.length; i++) {\n");
        sb.append("            $vars.put(VAR_NAMES[i], $locals[i]);\n");
        sb.append("        }\n");
        sb.append("        $vars.put(\"this\", $this);\n");
        sb.append("\n");
        
        // Generate helper to get variable as specific primitive type
        sb.append("        // Helper lambdas for type-safe variable access\n");
        sb.append("        java.util.function.Function<String, Object> $get = $vars::get;\n");
        sb.append("        java.util.function.Function<String, Integer> $int = n -> ((Number)$vars.get(n)).intValue();\n");
        sb.append("        java.util.function.Function<String, Long> $long = n -> ((Number)$vars.get(n)).longValue();\n");
        sb.append("        java.util.function.Function<String, Double> $double = n -> ((Number)$vars.get(n)).doubleValue();\n");
        sb.append("        java.util.function.Function<String, Boolean> $bool = n -> (Boolean)$vars.get(n);\n");
        sb.append("        java.util.function.Function<String, String> $str = n -> String.valueOf($vars.get(n));\n");
        sb.append("\n");
        
        // Unpack commonly used variables directly (for simple expressions)
        int localIndex = 0;
        for (final Map.Entry<String, String> entry : localVariables.entrySet()) {
            final String varName = entry.getKey();
            final String varType = entry.getValue();
            
            // Use Object type for everything - cast happens at runtime
            if (isPrimitiveType(varType)) {
                final String wrapperType = getWrapperType(varType);
                sb.append("        final ").append(wrapperType).append(" ").append(varName)
                  .append(" = (").append(wrapperType).append(") $locals[").append(localIndex).append("];\n");
            } else {
                sb.append("        final Object ").append(varName)
                  .append(" = $locals[").append(localIndex).append("];\n");
            }
            localIndex++;
        }
        
        if (thisTypeName != null) {
            sb.append("        final Object __this = $this;\n");
        }
        
        sb.append("\n");
        
        // The expression - wrap in try-catch for better error messages
        sb.append("        try {\n");
        sb.append("            return (Object) (").append(expression).append(");\n");
        sb.append("        } catch (ClassCastException e) {\n");
        sb.append("            throw new RuntimeException(\"Type error in expression: \" + e.getMessage(), e);\n");
        sb.append("        }\n");
        
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    private boolean isPrimitiveType(final String typeName) {
        return getUnboxedType(typeName) != null;
    }
    
    private String getUnboxedType(final String typeName) {
        return switch (typeName) {
            case "int", "boolean", "byte", "short", "long", "float", "double", "char" -> typeName;
            default -> null;
        };
    }
    
    private String getWrapperType(final String primitiveType) {
        return switch (primitiveType) {
            case "int" -> "Integer";
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "char" -> "Character";
            default -> throw new IllegalArgumentException("Not a primitive: " + primitiveType);
        };
    }
    
    private byte[] compileSource(final String className, final String sourceCode) throws CompilationException {
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final InMemoryFileManager fileManager = new InMemoryFileManager(
            compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8));
        
        final JavaFileObject sourceFile = new InMemoryJavaSource(className, sourceCode);
        
        final JavaCompiler.CompilationTask task = compiler.getTask(
            null,                           // Writer for additional output
            fileManager,                    // File manager
            diagnostics,                    // Diagnostics collector
            List.of("-g", "-parameters"),   // Compiler options: debug info + parameter names
            null,                           // Classes for annotation processing
            List.of(sourceFile)             // Compilation units
        );
        
        final boolean success = task.call();
        
        if (!success) {
            final StringBuilder errors = new StringBuilder("Compilation failed:\n");
            for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    errors.append("  Line ").append(diagnostic.getLineNumber())
                          .append(": ").append(diagnostic.getMessage(null)).append("\n");
                }
            }
            errors.append("\nGenerated source:\n").append(sourceCode);
            throw new CompilationException(errors.toString());
        }
        
        final byte[] bytecode = fileManager.getClassBytes(className);
        if (bytecode == null) {
            throw new CompilationException("No bytecode generated for " + className);
        }
        
        return bytecode;
    }
    
    /**
     * Clears the expression cache.
     */
    public void clearCache() {
        cache.clear();
    }
    
    // --- Inner classes for in-memory compilation ---
    
    private static final class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;
        
        InMemoryJavaSource(final String className, final String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        
        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return code;
        }
    }
    
    private static final class InMemoryClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        InMemoryClassFile(final String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }
        
        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }
        
        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }
    
    private static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClassFile> classFiles = new HashMap<>();
        
        InMemoryFileManager(final StandardJavaFileManager fileManager) {
            super(fileManager);
        }
        
        @Override
        public JavaFileObject getJavaFileForOutput(
                final Location location,
                final String className,
                final JavaFileObject.Kind kind,
                final FileObject sibling) {
            final InMemoryClassFile classFile = new InMemoryClassFile(className);
            classFiles.put(className, classFile);
            return classFile;
        }
        
        byte[] getClassBytes(final String className) {
            final InMemoryClassFile classFile = classFiles.get(className);
            return classFile != null ? classFile.getBytes() : null;
        }
    }
    
    // --- Result types ---
    
    /**
     * Represents a compiled expression ready for execution.
     */
    public static final class CompiledExpression {
        private final String className;
        private final byte[] bytecode;
        private final boolean hasThis;
        private final List<String> localVariableNames;
        
        CompiledExpression(final String className, final byte[] bytecode, 
                          final boolean hasThis, final List<String> localVariableNames) {
            this.className = className;
            this.bytecode = bytecode;
            this.hasThis = hasThis;
            this.localVariableNames = localVariableNames;
        }
        
        public String getClassName() { return className; }
        public byte[] getBytecode() { return bytecode; }
        public boolean hasThis() { return hasThis; }
        public List<String> getLocalVariableNames() { return localVariableNames; }
    }
    
    /**
     * Exception thrown when expression compilation fails.
     */
    public static final class CompilationException extends Exception {
        public CompilationException(final String message) {
            super(message);
        }
        
        public CompilationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}

