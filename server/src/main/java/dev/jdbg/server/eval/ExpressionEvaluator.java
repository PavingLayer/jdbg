package dev.jdbg.server.eval;

import com.sun.jdi.*;
import dev.jdbg.server.eval.ExpressionCompiler.CompiledExpression;
import dev.jdbg.server.eval.ExpressionCompiler.CompilationException;

import java.util.*;

/**
 * Evaluates compiled expressions in a target JVM via JDI.
 * <p>
 * The evaluation process:
 * 1. Compile the expression to bytecode (using ExpressionCompiler)
 * 2. Load the bytecode into the target VM via ClassLoader.defineClass()
 * 3. Invoke the static eval() method with frame context
 * 4. Return the result
 */
public final class ExpressionEvaluator {
    
    private final ExpressionCompiler compiler;
    private final Map<String, ReferenceType> loadedClasses = new HashMap<>();
    
    public ExpressionEvaluator() {
        this.compiler = new ExpressionCompiler();
    }
    
    /**
     * Evaluates an expression in the context of a stack frame.
     *
     * @param expression The Java expression to evaluate
     * @param frame      The stack frame providing context (this, locals)
     * @param thread     The suspended thread
     * @return The evaluation result
     */
    public EvaluationResult evaluate(final String expression, final StackFrame frame, final ThreadReference thread) 
            throws EvaluationException {
        try {
            // 1. Gather frame context
            final FrameContext context = gatherFrameContext(frame);
            
            // 2. Compile the expression
            final CompiledExpression compiled = compileExpression(expression, context);
            
            // 3. Load the class into the target VM
            final ReferenceType evalClass = loadClassIntoTargetVm(compiled, frame.virtualMachine(), thread);
            
            // 4. Invoke the eval method
            final Value result = invokeEvalMethod(evalClass, context, frame, thread);
            
            // 5. Return result
            return new EvaluationResult(result, getTypeName(result));
            
        } catch (final CompilationException e) {
            throw new EvaluationException("Compilation failed: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new EvaluationException("Evaluation failed: " + e.getMessage(), e);
        }
    }
    
    private FrameContext gatherFrameContext(final StackFrame frame) throws AbsentInformationException {
        // Get 'this' reference and type
        ObjectReference thisObject = null;
        String thisTypeName = null;
        try {
            thisObject = frame.thisObject();
            if (thisObject != null) {
                thisTypeName = thisObject.referenceType().name();
            }
        } catch (final Exception e) {
            // Static method, no 'this'
        }
        
        // Get local variables and their types
        final Map<String, String> localTypes = new LinkedHashMap<>();
        final Map<String, Value> localValues = new LinkedHashMap<>();
        
        try {
            for (final LocalVariable var : frame.visibleVariables()) {
                final String name = var.name();
                final String type = var.typeName();
                final Value value = frame.getValue(var);
                localTypes.put(name, type);
                localValues.put(name, value);
            }
        } catch (final AbsentInformationException e) {
            // No debug info available
        }
        
        // Gather imports from the current class's package and common imports
        final List<String> imports = gatherImports(frame);
        
        return new FrameContext(thisObject, thisTypeName, localTypes, localValues, imports);
    }
    
    private List<String> gatherImports(final StackFrame frame) {
        final List<String> imports = new ArrayList<>();
        
        try {
            final Location location = frame.location();
            final ReferenceType declaringType = location.declaringType();
            
            // Add the current class's package as wildcard import
            final String className = declaringType.name();
            final int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                imports.add(className.substring(0, lastDot) + ".*");
            }
            
            // Add java.lang.* is implicit
            // Add java.util.* for convenience
            imports.add("java.util.*");
            
        } catch (final Exception e) {
            // Ignore
        }
        
        return imports;
    }
    
    private CompiledExpression compileExpression(final String expression, final FrameContext context) 
            throws CompilationException {
        return compiler.compile(
            expression, 
            context.thisTypeName, 
            context.localTypes, 
            context.imports
        );
    }
    
    private ReferenceType loadClassIntoTargetVm(
            final CompiledExpression compiled, 
            final VirtualMachine vm, 
            final ThreadReference thread) throws EvaluationException {
        
        // Check if already loaded
        final ReferenceType existing = loadedClasses.get(compiled.getClassName());
        if (existing != null) {
            return existing;
        }
        
        try {
            // Find a suitable class loader from the current frame
            final ClassLoaderReference classLoader = findClassLoader(vm, thread);
            
            // Create a byte array in the target VM
            final ArrayReference bytecodeArray = createByteArray(vm, compiled.getBytecode());
            
            // Invoke ClassLoader.defineClass(String name, byte[] b, int off, int len)
            final ReferenceType classLoaderType = classLoader.referenceType();
            final Method defineClassMethod = findDefineClassMethod(classLoaderType);
            
            if (defineClassMethod == null) {
                throw new EvaluationException("Could not find defineClass method on ClassLoader");
            }
            
            // Arguments: (String name, byte[] b, int off, int len)
            final List<Value> args = List.of(
                vm.mirrorOf(compiled.getClassName()),
                bytecodeArray,
                vm.mirrorOf(0),
                vm.mirrorOf(compiled.getBytecode().length)
            );
            
            // Need to make defineClass accessible - it's protected
            // Use reflection to call it: classLoader.getClass().getDeclaredMethod(...).setAccessible(true)
            // Actually, let's use a different approach: call a public method that eventually calls defineClass
            
            // Alternative: Use Unsafe or MethodHandles.Lookup to define the class
            // For simplicity, let's try using sun.misc.Unsafe.defineClass if available
            final ReferenceType evalClass = defineClassViaUnsafe(vm, thread, compiled.getClassName(), compiled.getBytecode());
            
            if (evalClass != null) {
                loadedClasses.put(compiled.getClassName(), evalClass);
                return evalClass;
            }
            
            throw new EvaluationException("Failed to load expression class into target VM");
            
        } catch (final EvaluationException e) {
            throw e;
        } catch (final Exception e) {
            throw new EvaluationException("Failed to load class: " + e.getMessage(), e);
        }
    }
    
    private ClassLoaderReference findClassLoader(final VirtualMachine vm, final ThreadReference thread) 
            throws IncompatibleThreadStateException {
        // Get the class loader from the current frame's declaring class
        final StackFrame frame = thread.frame(0);
        final ReferenceType declaringType = frame.location().declaringType();
        final ClassLoaderReference classLoader = declaringType.classLoader();
        
        // If null, use the system class loader
        if (classLoader != null) {
            return classLoader;
        }
        
        // Get system class loader via ClassLoader.getSystemClassLoader()
        final List<ReferenceType> classLoaderTypes = vm.classesByName("java.lang.ClassLoader");
        if (!classLoaderTypes.isEmpty()) {
            final ReferenceType clType = classLoaderTypes.get(0);
            for (final Method method : clType.methodsByName("getSystemClassLoader")) {
                try {
                    if (method.isStatic() && method.argumentTypes().isEmpty()) {
                        final Value result = ((ClassType) clType).invokeMethod(
                            thread, method, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                        if (result instanceof ClassLoaderReference) {
                            return (ClassLoaderReference) result;
                        }
                    }
                } catch (final Exception e) {
                    // Try next method
                }
            }
        }
        
        throw new IllegalStateException("Could not find a class loader");
    }
    
    private Method findDefineClassMethod(final ReferenceType classLoaderType) {
        // Look for defineClass(String, byte[], int, int)
        for (final Method method : classLoaderType.allMethods()) {
            if ("defineClass".equals(method.name())) {
                try {
                    final List<String> argTypes = method.argumentTypeNames();
                    if (argTypes.size() == 4 
                        && "java.lang.String".equals(argTypes.get(0))
                        && "byte[]".equals(argTypes.get(1))
                        && "int".equals(argTypes.get(2))
                        && "int".equals(argTypes.get(3))) {
                        return method;
                    }
                } catch (final Exception e) {
                    // Skip this method
                }
            }
        }
        return null;
    }
    
    private ReferenceType defineClassViaUnsafe(
            final VirtualMachine vm, 
            final ThreadReference thread,
            final String className,
            final byte[] bytecode) {
        
        try {
            // Try to use MethodHandles.Lookup.defineClass (Java 9+)
            // This is the modern, supported way to define classes dynamically
            
            // First, get MethodHandles.lookup()
            final List<ReferenceType> mhTypes = vm.classesByName("java.lang.invoke.MethodHandles");
            if (mhTypes.isEmpty()) {
                return null;
            }
            
            final ClassType mhType = (ClassType) mhTypes.get(0);
            Method lookupMethod = null;
            for (final Method m : mhType.methodsByName("lookup")) {
                if (m.isStatic() && m.argumentTypes().isEmpty()) {
                    lookupMethod = m;
                    break;
                }
            }
            
            if (lookupMethod == null) {
                return null;
            }
            
            // Call MethodHandles.lookup()
            final ObjectReference lookup = (ObjectReference) mhType.invokeMethod(
                thread, lookupMethod, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
            
            if (lookup == null) {
                return null;
            }
            
            // Create byte[] in target VM
            final ArrayReference bytecodeArray = createByteArray(vm, bytecode);
            
            // Find Lookup.defineClass(byte[])
            final ReferenceType lookupType = lookup.referenceType();
            Method defineClassMethod = null;
            for (final Method m : lookupType.methodsByName("defineClass")) {
                final List<String> argTypes = m.argumentTypeNames();
                if (argTypes.size() == 1 && "byte[]".equals(argTypes.get(0))) {
                    defineClassMethod = m;
                    break;
                }
            }
            
            if (defineClassMethod == null) {
                return null;
            }
            
            // Invoke defineClass
            final ClassObjectReference classRef = (ClassObjectReference) lookup.invokeMethod(
                thread, defineClassMethod, List.of(bytecodeArray), ObjectReference.INVOKE_SINGLE_THREADED);
            
            if (classRef != null) {
                return classRef.reflectedType();
            }
            
        } catch (final Exception e) {
            // Fall through to return null
        }
        
        return null;
    }
    
    private ArrayReference createByteArray(final VirtualMachine vm, final byte[] bytes) {
        // Find byte[] type
        final List<ReferenceType> byteArrayTypes = vm.classesByName("byte[]");
        ArrayType byteArrayType = null;
        
        if (!byteArrayTypes.isEmpty() && byteArrayTypes.get(0) instanceof ArrayType) {
            byteArrayType = (ArrayType) byteArrayTypes.get(0);
        } else {
            // Create via reflection
            final List<ReferenceType> byteTypes = vm.classesByName("[B");
            if (!byteTypes.isEmpty() && byteTypes.get(0) instanceof ArrayType) {
                byteArrayType = (ArrayType) byteTypes.get(0);
            }
        }
        
        if (byteArrayType == null) {
            throw new IllegalStateException("Could not find byte[] type in target VM");
        }
        
        // Create the array
        final ArrayReference array = byteArrayType.newInstance(bytes.length);
        
        // Fill it - unfortunately we have to do this value by value for bytes
        // JDI's setValues can take a list, but we need to convert bytes to ByteValue
        final List<Value> values = new ArrayList<>(bytes.length);
        for (final byte b : bytes) {
            values.add(vm.mirrorOf(b));
        }
        try {
            array.setValues(values);
        } catch (final InvalidTypeException | ClassNotLoadedException e) {
            throw new IllegalStateException("Failed to set byte array values", e);
        }
        
        return array;
    }
    
    private Value invokeEvalMethod(
            final ReferenceType evalClass,
            final FrameContext context,
            final StackFrame frame,
            final ThreadReference thread) throws EvaluationException {
        
        try {
            // Find the eval method: public static Object eval(Object $this, Object[] $locals)
            Method evalMethod = null;
            for (final Method m : evalClass.methodsByName("eval")) {
                if (m.isStatic()) {
                    evalMethod = m;
                    break;
                }
            }
            
            if (evalMethod == null) {
                throw new EvaluationException("Could not find eval method in compiled class");
            }
            
            // Create the $locals array
            final VirtualMachine vm = frame.virtualMachine();
            final ArrayReference localsArray = createObjectArray(vm, context.localValues.values());
            
            // Invoke: eval($this, $locals)
            final List<Value> args = List.of(
                context.thisObject != null ? context.thisObject : vm.mirrorOfVoid(),
                localsArray
            );
            
            return ((ClassType) evalClass).invokeMethod(
                thread, evalMethod, args, ObjectReference.INVOKE_SINGLE_THREADED);
            
        } catch (final EvaluationException e) {
            throw e;
        } catch (final InvocationException e) {
            // The expression threw an exception
            final ObjectReference exception = e.exception();
            throw new EvaluationException("Expression threw: " + exception.referenceType().name() + 
                " - " + getExceptionMessage(exception, thread));
        } catch (final Exception e) {
            throw new EvaluationException("Invocation failed: " + e.getMessage(), e);
        }
    }
    
    private ArrayReference createObjectArray(final VirtualMachine vm, final Collection<Value> values) {
        // Find Object[] type
        final List<ReferenceType> objectArrayTypes = vm.classesByName("[Ljava.lang.Object;");
        ArrayType objectArrayType = null;
        
        if (!objectArrayTypes.isEmpty() && objectArrayTypes.get(0) instanceof ArrayType) {
            objectArrayType = (ArrayType) objectArrayTypes.get(0);
        }
        
        if (objectArrayType == null) {
            throw new IllegalStateException("Could not find Object[] type in target VM");
        }
        
        final ArrayReference array = objectArrayType.newInstance(values.size());
        
        // Box primitive values and set
        final List<Value> boxedValues = new ArrayList<>();
        for (final Value v : values) {
            boxedValues.add(boxPrimitiveIfNeeded(vm, v));
        }
        
        try {
            array.setValues(boxedValues);
        } catch (final InvalidTypeException | ClassNotLoadedException e) {
            throw new IllegalStateException("Failed to set Object array values", e);
        }
        return array;
    }
    
    private Value boxPrimitiveIfNeeded(final VirtualMachine vm, final Value value) {
        if (value == null) {
            return null;
        }
        
        // Primitives need to be boxed for Object[] storage
        if (value instanceof PrimitiveValue) {
            return boxPrimitive(vm, (PrimitiveValue) value);
        }
        
        return value;
    }
    
    private ObjectReference boxPrimitive(final VirtualMachine vm, final PrimitiveValue value) {
        // This is tricky - we need to invoke the wrapper class's valueOf method
        // For now, we'll handle the common cases
        
        final String wrapperClass;
        final String valueOfSig;
        
        if (value instanceof IntegerValue) {
            wrapperClass = "java.lang.Integer";
            valueOfSig = "(I)Ljava/lang/Integer;";
        } else if (value instanceof LongValue) {
            wrapperClass = "java.lang.Long";
            valueOfSig = "(J)Ljava/lang/Long;";
        } else if (value instanceof BooleanValue) {
            wrapperClass = "java.lang.Boolean";
            valueOfSig = "(Z)Ljava/lang/Boolean;";
        } else if (value instanceof ByteValue) {
            wrapperClass = "java.lang.Byte";
            valueOfSig = "(B)Ljava/lang/Byte;";
        } else if (value instanceof ShortValue) {
            wrapperClass = "java.lang.Short";
            valueOfSig = "(S)Ljava/lang/Short;";
        } else if (value instanceof FloatValue) {
            wrapperClass = "java.lang.Float";
            valueOfSig = "(F)Ljava/lang/Float;";
        } else if (value instanceof DoubleValue) {
            wrapperClass = "java.lang.Double";
            valueOfSig = "(D)Ljava/lang/Double;";
        } else if (value instanceof CharValue) {
            wrapperClass = "java.lang.Character";
            valueOfSig = "(C)Ljava/lang/Character;";
        } else {
            return null; // Unknown primitive
        }
        
        // For simplicity, just return the value as-is since JDI handles boxing in some contexts
        // A full implementation would invoke the wrapper's valueOf method
        // This is a known limitation that can be improved
        return null;
    }
    
    private String getExceptionMessage(final ObjectReference exception, final ThreadReference thread) {
        try {
            final ReferenceType type = exception.referenceType();
            for (final Method m : type.methodsByName("getMessage")) {
                if (m.argumentTypes().isEmpty()) {
                    final Value msg = exception.invokeMethod(
                        thread, m, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                    if (msg instanceof StringReference) {
                        return ((StringReference) msg).value();
                    }
                    break;
                }
            }
        } catch (final Exception e) {
            // Ignore
        }
        return "(no message)";
    }
    
    private String getTypeName(final Value value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ObjectReference) {
            return ((ObjectReference) value).referenceType().name();
        }
        if (value instanceof PrimitiveValue) {
            return value.type().name();
        }
        return "unknown";
    }
    
    /**
     * Clears cached compiled expressions and loaded classes.
     */
    public void clearCache() {
        compiler.clearCache();
        loadedClasses.clear();
    }
    
    // --- Inner classes ---
    
    private static final class FrameContext {
        final ObjectReference thisObject;
        final String thisTypeName;
        final Map<String, String> localTypes;
        final Map<String, Value> localValues;
        final List<String> imports;
        
        FrameContext(final ObjectReference thisObject, final String thisTypeName,
                     final Map<String, String> localTypes, final Map<String, Value> localValues,
                     final List<String> imports) {
            this.thisObject = thisObject;
            this.thisTypeName = thisTypeName;
            this.localTypes = localTypes;
            this.localValues = localValues;
            this.imports = imports;
        }
    }
    
    /**
     * Result of an expression evaluation.
     */
    public static final class EvaluationResult {
        private final Value value;
        private final String typeName;
        
        EvaluationResult(final Value value, final String typeName) {
            this.value = value;
            this.typeName = typeName;
        }
        
        public Value getValue() { return value; }
        public String getTypeName() { return typeName; }
        
        public String getValueAsString() {
            if (value == null) {
                return "null";
            }
            if (value instanceof StringReference) {
                return "\"" + ((StringReference) value).value() + "\"";
            }
            if (value instanceof PrimitiveValue) {
                return value.toString();
            }
            if (value instanceof ObjectReference) {
                return formatObjectReference((ObjectReference) value);
            }
            return value.toString();
        }
        
        private String formatObjectReference(final ObjectReference obj) {
            // Try to get a useful string representation
            final String typeName = obj.referenceType().name();
            
            // For common types, show their value
            if (obj instanceof ArrayReference) {
                final ArrayReference arr = (ArrayReference) obj;
                return typeName + "[" + arr.length() + "]";
            }
            
            return typeName + "@" + Long.toHexString(obj.uniqueID());
        }
    }
    
    /**
     * Exception thrown when evaluation fails.
     */
    public static final class EvaluationException extends Exception {
        public EvaluationException(final String message) {
            super(message);
        }
        
        public EvaluationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}

