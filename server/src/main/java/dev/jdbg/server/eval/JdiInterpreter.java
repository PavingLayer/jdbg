package dev.jdbg.server.eval;

import com.sun.jdi.*;
import dev.jdbg.server.eval.ExpressionParser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interprets expression AST nodes using JDI method invocations.
 * 
 * This approach executes expressions directly on the target JVM by:
 * - Using ObjectReference.invokeMethod() for method calls
 * - Using ObjectReference.getValue() for field access  
 * - Using VirtualMachine.mirrorOf() for literals
 * - Using ArrayReference for array operations
 */
public final class JdiInterpreter {
    
    private final VirtualMachine vm;
    private final ThreadReference thread;
    private final StackFrame frame;
    private final Map<String, Value> localVariables;
    private final ObjectReference thisObject;
    
    public JdiInterpreter(final VirtualMachine vm, final ThreadReference thread, final StackFrame frame) {
        this.vm = vm;
        this.thread = thread;
        this.frame = frame;
        this.localVariables = gatherLocalVariables();
        this.thisObject = getThisObject();
    }
    
    private Map<String, Value> gatherLocalVariables() {
        final Map<String, Value> vars = new HashMap<>();
        try {
            for (final LocalVariable var : frame.visibleVariables()) {
                vars.put(var.name(), frame.getValue(var));
            }
        } catch (final AbsentInformationException e) {
            // No debug info available
        }
        return vars;
    }
    
    private ObjectReference getThisObject() {
        try {
            return frame.thisObject();
        } catch (final Exception e) {
            return null; // Static context
        }
    }
    
    /**
     * Evaluates an expression and returns the result.
     */
    public Value evaluate(final Expr expr) throws EvaluationException {
        try {
            final Value result = evalExpr(expr);
            // Ensure we never return a ClassReferenceMarker as a final result
            if (result instanceof ClassReferenceMarker marker) {
                throw new EvaluationException("Cannot use class '" + marker.type.name() + "' as a value");
            }
            return result;
        } catch (final EvaluationException e) {
            throw e;
        } catch (final Exception e) {
            final String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            throw new EvaluationException("Evaluation failed: " + message, e);
        }
    }
    
    private Value evalExpr(final Expr expr) throws Exception {
        if (expr instanceof LiteralExpr lit) {
            return evalLiteral(lit);
        } else if (expr instanceof VariableExpr var) {
            return evalVariable(var);
        } else if (expr instanceof ThisExpr) {
            return evalThis();
        } else if (expr instanceof FieldAccessExpr field) {
            return evalFieldAccess(field);
        } else if (expr instanceof MethodCallExpr method) {
            return evalMethodCall(method);
        } else if (expr instanceof ArrayAccessExpr arr) {
            return evalArrayAccess(arr);
        } else if (expr instanceof BinaryExpr bin) {
            return evalBinary(bin);
        } else if (expr instanceof UnaryExpr un) {
            return evalUnary(un);
        } else if (expr instanceof CastExpr cast) {
            return evalCast(cast);
        } else if (expr instanceof InstanceOfExpr inst) {
            return evalInstanceOf(inst);
        } else if (expr instanceof NewObjectExpr newObj) {
            return evalNewObject(newObj);
        } else if (expr instanceof NewArrayExpr newArr) {
            return evalNewArray(newArr);
        } else {
            throw new EvaluationException("Unknown expression type: " + expr.getClass().getName());
        }
    }
    
    private Value evalLiteral(final LiteralExpr lit) {
        final Object value = lit.value();
        if (value == null) {
            return null;
        }
        
        final Class<?> type = lit.type();
        if (type == int.class) {
            return vm.mirrorOf((Integer) value);
        } else if (type == long.class) {
            return vm.mirrorOf((Long) value);
        } else if (type == float.class) {
            return vm.mirrorOf((Float) value);
        } else if (type == double.class) {
            return vm.mirrorOf((Double) value);
        } else if (type == boolean.class) {
            return vm.mirrorOf((Boolean) value);
        } else if (type == char.class) {
            return vm.mirrorOf((Character) value);
        } else if (type == String.class) {
            return vm.mirrorOf((String) value);
        }
        
        throw new IllegalArgumentException("Unsupported literal type: " + type);
    }
    
    private Value evalVariable(final VariableExpr var) throws EvaluationException {
        final Value value = localVariables.get(var.name());
        if (value == null && !localVariables.containsKey(var.name())) {
            throw new EvaluationException("Variable not found: " + var.name());
        }
        return value;
    }
    
    /**
     * Tries to resolve an expression as a class name (for static access).
     * Returns the ReferenceType if successful, null otherwise.
     */
    private ReferenceType tryResolveAsClassName(final Expr expr) {
        final String className = extractPotentialClassName(expr);
        if (className == null) {
            return null;
        }
        
        // Check if class is loaded - we only support already-loaded classes
        final List<ReferenceType> types = vm.classesByName(className);
        return types.isEmpty() ? null : types.get(0);
    }
    
    /**
     * Extracts a potential class name from an expression.
     * For example, java.lang.Math becomes "java.lang.Math"
     */
    private String extractPotentialClassName(final Expr expr) {
        if (expr instanceof VariableExpr var) {
            return var.name();
        }
        if (expr instanceof FieldAccessExpr field) {
            final String prefix = extractPotentialClassName(field.target());
            if (prefix == null) {
                return null;
            }
            return prefix + "." + field.fieldName();
        }
        return null;
    }
    
    private Value evalThis() throws EvaluationException {
        if (thisObject == null) {
            throw new EvaluationException("'this' is not available in static context");
        }
        return thisObject;
    }
    
    private Value evalFieldAccess(final FieldAccessExpr expr) throws Exception {
        // First, try to resolve the entire expression as a class name (for static access)
        final ReferenceType classRef = tryResolveAsClassName(expr);
        if (classRef != null) {
            // This is a class reference, not a field access - return marker for static access
            return new ClassReferenceMarker(classRef);
        }
        
        // Check if target is a class reference for static field access
        final ReferenceType targetClass = tryResolveAsClassName(expr.target());
        if (targetClass != null) {
            // Static field access
            final Field field = targetClass.fieldByName(expr.fieldName());
            if (field == null) {
                throw new EvaluationException("Static field not found: " + expr.fieldName() + " in " + targetClass.name());
            }
            if (!field.isStatic()) {
                throw new EvaluationException("Field " + expr.fieldName() + " is not static");
            }
            return targetClass.getValue(field);
        }
        
        // Instance field access
        final Value targetValue = evalExpr(expr.target());
        
        // Handle ClassReferenceMarker from nested field access
        if (targetValue instanceof ClassReferenceMarker marker) {
            final Field field = marker.type.fieldByName(expr.fieldName());
            if (field == null) {
                throw new EvaluationException("Static field not found: " + expr.fieldName() + " in " + marker.type.name());
            }
            return marker.type.getValue(field);
        }
        
        if (targetValue == null) {
            throw new EvaluationException("Cannot access field '" + expr.fieldName() + "' on null");
        }
        
        if (!(targetValue instanceof ObjectReference)) {
            throw new EvaluationException("Cannot access field on primitive value");
        }
        
        final ObjectReference obj = (ObjectReference) targetValue;
        final ReferenceType type = obj.referenceType();
        
        // Find the field
        final Field field = type.fieldByName(expr.fieldName());
        if (field == null) {
            throw new EvaluationException("Field not found: " + expr.fieldName() + " in " + type.name());
        }
        
        return obj.getValue(field);
    }
    
    /**
     * Marker class to represent a class reference (for static method/field access).
     * This is a workaround since JDI's Value hierarchy doesn't include class references.
     */
    private static final class ClassReferenceMarker extends ObjectReferenceImpl {
        final ReferenceType type;
        
        ClassReferenceMarker(final ReferenceType type) {
            this.type = type;
        }
    }
    
    /**
     * Minimal implementation to satisfy ObjectReference interface.
     * Only used as a marker, never actually used as a real ObjectReference.
     */
    private static abstract class ObjectReferenceImpl implements ObjectReference {
        @Override public ReferenceType referenceType() { throw new UnsupportedOperationException(); }
        @Override public Value getValue(Field sig) { throw new UnsupportedOperationException(); }
        @Override public Map<Field, Value> getValues(List<? extends Field> fields) { throw new UnsupportedOperationException(); }
        @Override public void setValue(Field field, Value value) { throw new UnsupportedOperationException(); }
        @Override public Value invokeMethod(ThreadReference thread, Method method, List<? extends Value> arguments, int options) { throw new UnsupportedOperationException(); }
        @Override public void disableCollection() { throw new UnsupportedOperationException(); }
        @Override public void enableCollection() { throw new UnsupportedOperationException(); }
        @Override public boolean isCollected() { throw new UnsupportedOperationException(); }
        @Override public long uniqueID() { throw new UnsupportedOperationException(); }
        @Override public List<ThreadReference> waitingThreads() { throw new UnsupportedOperationException(); }
        @Override public ThreadReference owningThread() { throw new UnsupportedOperationException(); }
        @Override public int entryCount() { throw new UnsupportedOperationException(); }
        @Override public List<ObjectReference> referringObjects(long maxReferrers) { throw new UnsupportedOperationException(); }
        @Override public Type type() { throw new UnsupportedOperationException(); }
        @Override public VirtualMachine virtualMachine() { throw new UnsupportedOperationException(); }
    }
    
    private Value evalMethodCall(final MethodCallExpr expr) throws Exception {
        // Evaluate arguments first
        final List<Value> args = new ArrayList<>();
        for (final Expr arg : expr.arguments()) {
            args.add(evalExpr(arg));
        }
        
        // Check if this is a static method call (target is a class name)
        if (expr.target() != null) {
            final String potentialClassName = extractPotentialClassName(expr.target());
            if (potentialClassName != null) {
                final ReferenceType targetClass = tryResolveAsClassName(expr.target());
                if (targetClass != null) {
                    // Static method call
                    return invokeStaticMethod(targetClass, expr.methodName(), args);
                }
                // Class name pattern but class not loaded - give helpful error
                throw new EvaluationException("Class not loaded: " + potentialClassName + 
                    ". The class must be used by the program before it can be accessed.");
            }
        }
        
        // Evaluate target (null means implicit this)
        Value targetValue;
        if (expr.target() == null) {
            targetValue = thisObject;
            if (targetValue == null) {
                throw new EvaluationException("Cannot call instance method '" + expr.methodName() + "' without target");
            }
        } else {
            targetValue = evalExpr(expr.target());
        }
        
        // Handle ClassReferenceMarker (from field access that resolved to a class)
        if (targetValue instanceof ClassReferenceMarker marker) {
            return invokeStaticMethod(marker.type, expr.methodName(), args);
        }
        
        if (targetValue == null) {
            throw new EvaluationException("Cannot call method '" + expr.methodName() + "' on null");
        }
        
        if (!(targetValue instanceof ObjectReference)) {
            throw new EvaluationException("Cannot call method on primitive value");
        }
        
        final ObjectReference obj = (ObjectReference) targetValue;
        final ReferenceType type = obj.referenceType();
        
        // Find matching method
        final Method method = findMethod(type, expr.methodName(), args);
        if (method == null) {
            throw new EvaluationException("Method not found: " + expr.methodName() + " with " + args.size() + " arguments in " + type.name());
        }
        
        // Invoke the method
        try {
            return obj.invokeMethod(thread, method, args, ObjectReference.INVOKE_SINGLE_THREADED);
        } catch (final InvocationException e) {
            final ObjectReference exception = e.exception();
            throw new EvaluationException("Method threw exception: " + exception.referenceType().name());
        }
    }
    
    private Value invokeStaticMethod(final ReferenceType type, final String methodName, final List<Value> args) throws Exception {
        // Find matching static method
        final Method method = findMethod(type, methodName, args);
        if (method == null) {
            throw new EvaluationException("Static method not found: " + methodName + " with " + args.size() + " arguments in " + type.name());
        }
        
        if (!method.isStatic()) {
            throw new EvaluationException("Method " + methodName + " is not static");
        }
        
        // Invoke static method
        if (type instanceof ClassType classType) {
            try {
                return classType.invokeMethod(thread, method, args, ClassType.INVOKE_SINGLE_THREADED);
            } catch (final InvocationException e) {
                final ObjectReference exception = e.exception();
                throw new EvaluationException("Method threw exception: " + exception.referenceType().name());
            }
        }
        
        throw new EvaluationException("Cannot invoke static method on non-class type: " + type.name());
    }
    
    private Method findMethod(final ReferenceType type, final String name, final List<Value> args) {
        // Find methods with matching name
        final List<Method> candidates = type.methodsByName(name);
        
        for (final Method method : candidates) {
            try {
                final List<Type> paramTypes = method.argumentTypes();
                if (paramTypes.size() != args.size()) {
                    continue;
                }
                
                // Check if arguments are compatible
                boolean compatible = true;
                for (int i = 0; i < args.size(); i++) {
                    if (!isAssignableFrom(paramTypes.get(i), args.get(i))) {
                        compatible = false;
                        break;
                    }
                }
                
                if (compatible) {
                    return method;
                }
            } catch (final ClassNotLoadedException e) {
                // Skip this method
            }
        }
        
        return null;
    }
    
    private boolean isAssignableFrom(final Type paramType, final Value argValue) {
        if (argValue == null) {
            // null is assignable to any reference type
            return !(paramType instanceof PrimitiveType);
        }
        
        final Type argType = argValue.type();
        
        // Same type
        if (paramType.equals(argType)) {
            return true;
        }
        
        // Primitive widening
        if (paramType instanceof PrimitiveType && argType instanceof PrimitiveType) {
            return canWiden(argType.name(), paramType.name());
        }
        
        // Reference type assignability
        if (paramType instanceof ReferenceType && argType instanceof ReferenceType) {
            // Check subtype relationship
            if (argType instanceof ClassType) {
                ClassType ct = (ClassType) argType;
                while (ct != null) {
                    if (ct.equals(paramType)) {
                        return true;
                    }
                    // Check interfaces
                    for (final InterfaceType iface : ct.interfaces()) {
                        if (iface.equals(paramType) || implementsInterface(iface, (ReferenceType) paramType)) {
                            return true;
                        }
                    }
                    ct = ct.superclass();
                }
            }
        }
        
        // Auto-boxing/unboxing
        if (paramType instanceof PrimitiveType && argType instanceof ReferenceType) {
            final String wrapper = getWrapperType(paramType.name());
            return wrapper != null && argType.name().equals(wrapper);
        }
        if (paramType instanceof ReferenceType && argType instanceof PrimitiveType) {
            final String wrapper = getWrapperType(argType.name());
            return wrapper != null && paramType.name().equals(wrapper);
        }
        
        return false;
    }
    
    private boolean implementsInterface(final InterfaceType iface, final ReferenceType target) {
        if (iface.equals(target)) {
            return true;
        }
        for (final InterfaceType superIface : iface.superinterfaces()) {
            if (implementsInterface(superIface, target)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean canWiden(final String from, final String to) {
        // Java primitive widening rules
        return switch (from) {
            case "byte" -> to.equals("short") || to.equals("int") || to.equals("long") || to.equals("float") || to.equals("double");
            case "short" -> to.equals("int") || to.equals("long") || to.equals("float") || to.equals("double");
            case "char" -> to.equals("int") || to.equals("long") || to.equals("float") || to.equals("double");
            case "int" -> to.equals("long") || to.equals("float") || to.equals("double");
            case "long" -> to.equals("float") || to.equals("double");
            case "float" -> to.equals("double");
            default -> false;
        };
    }
    
    private String getWrapperType(final String primitive) {
        return switch (primitive) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "boolean" -> "java.lang.Boolean";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "char" -> "java.lang.Character";
            default -> null;
        };
    }
    
    private Value evalArrayAccess(final ArrayAccessExpr expr) throws Exception {
        final Value targetValue = evalExpr(expr.target());
        final Value indexValue = evalExpr(expr.index());
        
        if (targetValue == null) {
            throw new EvaluationException("Cannot access array element on null");
        }
        
        if (!(targetValue instanceof ArrayReference)) {
            throw new EvaluationException("Cannot use array access on non-array type");
        }
        
        if (!(indexValue instanceof IntegerValue)) {
            throw new EvaluationException("Array index must be an integer");
        }
        
        final ArrayReference arr = (ArrayReference) targetValue;
        final int index = ((IntegerValue) indexValue).value();
        
        if (index < 0 || index >= arr.length()) {
            throw new EvaluationException("Array index out of bounds: " + index);
        }
        
        return arr.getValue(index);
    }
    
    private Value evalBinary(final BinaryExpr expr) throws Exception {
        // Short-circuit evaluation for && and ||
        if (expr.operator().equals("&&")) {
            final Value left = evalExpr(expr.left());
            if (!toBoolean(left)) {
                return vm.mirrorOf(false);
            }
            return vm.mirrorOf(toBoolean(evalExpr(expr.right())));
        }
        
        if (expr.operator().equals("||")) {
            final Value left = evalExpr(expr.left());
            if (toBoolean(left)) {
                return vm.mirrorOf(true);
            }
            return vm.mirrorOf(toBoolean(evalExpr(expr.right())));
        }
        
        final Value left = evalExpr(expr.left());
        final Value right = evalExpr(expr.right());
        
        // String concatenation
        if (expr.operator().equals("+") && (isString(left) || isString(right))) {
            return vm.mirrorOf(valueToString(left) + valueToString(right));
        }
        
        // Equality operators work on all types
        if (expr.operator().equals("==")) {
            return vm.mirrorOf(valuesEqual(left, right));
        }
        if (expr.operator().equals("!=")) {
            return vm.mirrorOf(!valuesEqual(left, right));
        }
        
        // Arithmetic and comparison operators require numeric values
        if (!(left instanceof PrimitiveValue) || !(right instanceof PrimitiveValue)) {
            throw new EvaluationException("Cannot apply operator '" + expr.operator() + "' to non-primitive types");
        }
        
        // Promote to common type and evaluate
        final double leftNum = toDouble(left);
        final double rightNum = toDouble(right);
        
        return switch (expr.operator()) {
            case "+" -> mirrorNumber(leftNum + rightNum, left, right);
            case "-" -> mirrorNumber(leftNum - rightNum, left, right);
            case "*" -> mirrorNumber(leftNum * rightNum, left, right);
            case "/" -> {
                if (rightNum == 0) {
                    throw new EvaluationException("Division by zero");
                }
                yield mirrorNumber(leftNum / rightNum, left, right);
            }
            case "%" -> mirrorNumber(leftNum % rightNum, left, right);
            case "<" -> vm.mirrorOf(leftNum < rightNum);
            case ">" -> vm.mirrorOf(leftNum > rightNum);
            case "<=" -> vm.mirrorOf(leftNum <= rightNum);
            case ">=" -> vm.mirrorOf(leftNum >= rightNum);
            default -> throw new EvaluationException("Unknown operator: " + expr.operator());
        };
    }
    
    private Value mirrorNumber(final double value, final Value left, final Value right) {
        // Determine result type based on operand types (numeric promotion rules)
        if (left instanceof DoubleValue || right instanceof DoubleValue) {
            return vm.mirrorOf(value);
        }
        if (left instanceof FloatValue || right instanceof FloatValue) {
            return vm.mirrorOf((float) value);
        }
        if (left instanceof LongValue || right instanceof LongValue) {
            return vm.mirrorOf((long) value);
        }
        return vm.mirrorOf((int) value);
    }
    
    private boolean isString(final Value value) {
        return value instanceof StringReference;
    }
    
    private String valueToString(final Value value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof StringReference) {
            return ((StringReference) value).value();
        }
        if (value instanceof PrimitiveValue) {
            return value.toString();
        }
        // For objects, try to invoke toString()
        if (value instanceof ObjectReference obj) {
            try {
                Method toString = null;
                for (final Method m : obj.referenceType().methodsByName("toString")) {
                    try {
                        if (m.argumentTypes().isEmpty()) {
                            toString = m;
                            break;
                        }
                    } catch (final ClassNotLoadedException e) {
                        // Skip this method
                    }
                }
                if (toString != null) {
                    final Value result = obj.invokeMethod(thread, toString, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                    if (result instanceof StringReference) {
                        return ((StringReference) result).value();
                    }
                }
            } catch (final Exception e) {
                // Fall through
            }
            return obj.referenceType().name() + "@" + Long.toHexString(obj.uniqueID());
        }
        return value.toString();
    }
    
    private boolean valuesEqual(final Value left, final Value right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        
        // Reference equality for objects
        if (left instanceof ObjectReference && right instanceof ObjectReference) {
            return ((ObjectReference) left).uniqueID() == ((ObjectReference) right).uniqueID();
        }
        
        // Value equality for primitives
        if (left instanceof PrimitiveValue && right instanceof PrimitiveValue) {
            try {
                return toDouble(left) == toDouble(right);
            } catch (final EvaluationException e) {
                return false;
            }
        }
        
        return false;
    }
    
    private boolean toBoolean(final Value value) throws EvaluationException {
        if (value instanceof BooleanValue) {
            return ((BooleanValue) value).value();
        }
        throw new EvaluationException("Expected boolean value");
    }
    
    private double toDouble(final Value value) throws EvaluationException {
        if (value instanceof IntegerValue) {
            return ((IntegerValue) value).value();
        }
        if (value instanceof LongValue) {
            return ((LongValue) value).value();
        }
        if (value instanceof FloatValue) {
            return ((FloatValue) value).value();
        }
        if (value instanceof DoubleValue) {
            return ((DoubleValue) value).value();
        }
        if (value instanceof ShortValue) {
            return ((ShortValue) value).value();
        }
        if (value instanceof ByteValue) {
            return ((ByteValue) value).value();
        }
        if (value instanceof CharValue) {
            return ((CharValue) value).value();
        }
        throw new EvaluationException("Expected numeric value");
    }
    
    private Value evalUnary(final UnaryExpr expr) throws Exception {
        final Value operand = evalExpr(expr.operand());
        
        return switch (expr.operator()) {
            case "!" -> {
                if (!(operand instanceof BooleanValue)) {
                    throw new EvaluationException("Cannot apply '!' to non-boolean");
                }
                yield vm.mirrorOf(!((BooleanValue) operand).value());
            }
            case "-" -> {
                if (operand instanceof IntegerValue) {
                    yield vm.mirrorOf(-((IntegerValue) operand).value());
                }
                if (operand instanceof LongValue) {
                    yield vm.mirrorOf(-((LongValue) operand).value());
                }
                if (operand instanceof FloatValue) {
                    yield vm.mirrorOf(-((FloatValue) operand).value());
                }
                if (operand instanceof DoubleValue) {
                    yield vm.mirrorOf(-((DoubleValue) operand).value());
                }
                throw new EvaluationException("Cannot apply '-' to non-numeric value");
            }
            default -> throw new EvaluationException("Unknown unary operator: " + expr.operator());
        };
    }
    
    private Value evalCast(final CastExpr expr) throws Exception {
        final Value operand = evalExpr(expr.operand());
        
        // Handle null
        if (operand == null) {
            return null;
        }
        
        // Handle primitive casts
        final String targetType = expr.typeName();
        if (operand instanceof PrimitiveValue) {
            final double value = toDouble(operand);
            return switch (targetType) {
                case "int" -> vm.mirrorOf((int) value);
                case "long" -> vm.mirrorOf((long) value);
                case "float" -> vm.mirrorOf((float) value);
                case "double" -> vm.mirrorOf(value);
                case "short" -> vm.mirrorOf((short) value);
                case "byte" -> vm.mirrorOf((byte) value);
                case "char" -> vm.mirrorOf((char) (int) value);
                default -> throw new EvaluationException("Cannot cast primitive to " + targetType);
            };
        }
        
        // For reference types, just check assignability
        if (operand instanceof ObjectReference obj) {
            final List<ReferenceType> types = vm.classesByName(targetType);
            if (types.isEmpty()) {
                throw new EvaluationException("Unknown type: " + targetType);
            }
            
            // Check if cast is valid (we'll let it proceed and JDI will throw if invalid)
            // In a full implementation, we'd check the type hierarchy
            return operand;
        }
        
        throw new EvaluationException("Cannot cast value of type " + operand.type().name());
    }
    
    private Value evalInstanceOf(final InstanceOfExpr expr) throws Exception {
        final Value operand = evalExpr(expr.operand());
        
        if (operand == null) {
            return vm.mirrorOf(false);
        }
        
        if (!(operand instanceof ObjectReference obj)) {
            throw new EvaluationException("Cannot use instanceof on primitive value");
        }
        
        final List<ReferenceType> types = vm.classesByName(expr.typeName());
        if (types.isEmpty()) {
            throw new EvaluationException("Unknown type: " + expr.typeName());
        }
        
        final ReferenceType targetType = types.get(0);
        final ReferenceType objType = obj.referenceType();
        
        // Check type hierarchy
        if (objType instanceof ClassType ct) {
            while (ct != null) {
                if (ct.equals(targetType)) {
                    return vm.mirrorOf(true);
                }
                for (final InterfaceType iface : ct.interfaces()) {
                    if (iface.equals(targetType) || implementsInterface(iface, targetType)) {
                        return vm.mirrorOf(true);
                    }
                }
                ct = ct.superclass();
            }
        }
        
        return vm.mirrorOf(false);
    }
    
    private Value evalNewObject(final NewObjectExpr expr) throws Exception {
        final List<ReferenceType> types = vm.classesByName(expr.typeName());
        if (types.isEmpty()) {
            throw new EvaluationException("Unknown type: " + expr.typeName());
        }
        
        if (!(types.get(0) instanceof ClassType classType)) {
            throw new EvaluationException("Cannot instantiate non-class type: " + expr.typeName());
        }
        
        // Evaluate arguments
        final List<Value> args = new ArrayList<>();
        for (final Expr arg : expr.arguments()) {
            args.add(evalExpr(arg));
        }
        
        // Find constructor
        final Method constructor = findMethod(classType, "<init>", args);
        if (constructor == null) {
            throw new EvaluationException("No matching constructor found for " + expr.typeName());
        }
        
        // Create new instance
        try {
            return classType.newInstance(thread, constructor, args, ObjectReference.INVOKE_SINGLE_THREADED);
        } catch (final InvocationException e) {
            throw new EvaluationException("Constructor threw exception: " + e.exception().referenceType().name());
        }
    }
    
    private Value evalNewArray(final NewArrayExpr expr) throws Exception {
        final Value sizeValue = evalExpr(expr.size());
        
        if (!(sizeValue instanceof IntegerValue)) {
            throw new EvaluationException("Array size must be an integer");
        }
        
        final int size = ((IntegerValue) sizeValue).value();
        if (size < 0) {
            throw new EvaluationException("Negative array size: " + size);
        }
        
        // Get the array type
        final String typeName = expr.typeName();
        final String arrayTypeName = typeName + "[]";
        
        // For primitive arrays
        final ArrayType arrayType = findArrayType(typeName);
        if (arrayType == null) {
            throw new EvaluationException("Cannot create array of type: " + typeName);
        }
        
        return arrayType.newInstance(size);
    }
    
    private ArrayType findArrayType(final String componentType) {
        // Try to find existing array type
        final String arrayTypeName = switch (componentType) {
            case "int" -> "[I";
            case "long" -> "[J";
            case "double" -> "[D";
            case "float" -> "[F";
            case "boolean" -> "[Z";
            case "byte" -> "[B";
            case "short" -> "[S";
            case "char" -> "[C";
            default -> "[L" + componentType.replace('.', '/') + ";";
        };
        
        final List<ReferenceType> types = vm.classesByName(arrayTypeName);
        if (!types.isEmpty() && types.get(0) instanceof ArrayType) {
            return (ArrayType) types.get(0);
        }
        
        return null;
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

