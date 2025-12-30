package com.jdbg.test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Class demonstrating various data types for variable inspection testing.
 * 
 * Tests:
 * - Primitive type inspection (VAR-004)
 * - String inspection (VAR-005)
 * - Object reference inspection (VAR-006)
 * - Array inspection (VAR-007)
 * - Collection inspection
 * - Null value handling (VAR-008)
 */
public final class DataTypes {
    
    // Primitive fields
    private final byte byteValue = 127;
    private final short shortValue = 32767;
    private final int intValue = 2147483647;
    private final long longValue = 9223372036854775807L;
    private final float floatValue = 3.14159f;
    private final double doubleValue = 2.718281828459045;
    private final boolean booleanValue = true;
    private final char charValue = 'A';
    
    // Reference fields
    private final String stringValue = "Hello, World!";
    private final String emptyString = "";
    private final String nullString = null;
    
    // Wrapper types
    private final Integer wrappedInt = 42;
    private final Double wrappedDouble = 3.14;
    private final Boolean wrappedBoolean = Boolean.TRUE;
    
    // Arrays
    private final int[] intArray = {1, 2, 3, 4, 5};
    private final String[] stringArray = {"apple", "banana", "cherry"};
    private final int[][] multiDimArray = {{1, 2}, {3, 4}, {5, 6}};
    private final Object[] mixedArray = {1, "two", 3.0, null};
    
    // Collections
    private final List<String> stringList;
    private final Set<Integer> intSet;
    private final Map<String, Integer> stringIntMap;
    private final Queue<String> stringQueue;
    
    // Complex objects
    private final LocalDate date = LocalDate.of(2024, 1, 15);
    private final LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
    private final BigDecimal bigDecimal = new BigDecimal("123456789.123456789");
    
    // Enum
    private final Status status = Status.ACTIVE;
    
    public DataTypes() {
        // Initialize collections
        stringList = new ArrayList<>();
        stringList.add("first");
        stringList.add("second");
        stringList.add("third");
        
        intSet = new HashSet<>();
        intSet.add(10);
        intSet.add(20);
        intSet.add(30);
        
        stringIntMap = new HashMap<>();
        stringIntMap.put("one", 1);
        stringIntMap.put("two", 2);
        stringIntMap.put("three", 3);
        
        stringQueue = new LinkedList<>();
        stringQueue.offer("first-in");
        stringQueue.offer("second-in");
    }
    
    /**
     * Method with various local variables for inspection.
     * Set breakpoint here to inspect all variable types.
     */
    public void demonstrateAllTypes() {
        // Local primitive variables
        final int localInt = 100;
        final double localDouble = 99.99;
        final boolean localBool = false;
        
        // Local string
        final String localString = "Local string value";
        
        // Local array
        final int[] localArray = {10, 20, 30};
        
        // Local collection
        final List<String> localList = List.of("a", "b", "c");
        
        // Local complex object
        final Person localPerson = new Person("Bob", 25);
        
        // Null reference
        final Object localNull = null;
        
        // LINE 102 - Set breakpoint here to inspect all local variables
        System.out.println("Demonstrating data types...");
        System.out.println("Local int: " + localInt);
        System.out.println("Local double: " + localDouble);
        System.out.println("Local bool: " + localBool);
        System.out.println("Local string: " + localString);
        System.out.println("Local array length: " + localArray.length);
        System.out.println("Local list size: " + localList.size());
        System.out.println("Local person: " + localPerson);
        System.out.println("Local null: " + localNull);
        
        // Also demonstrate field access
        System.out.println("Field byteValue: " + byteValue);
        System.out.println("Field stringValue: " + stringValue);
        System.out.println("Field status: " + status);
    }
    
    /**
     * Method with arguments for testing VARIABLE_KIND_ARGUMENT.
     */
    public void methodWithArguments(final String name, 
                                    final int count, 
                                    final List<String> items,
                                    final Object nullable) {
        // LINE 124 - Set breakpoint here to see arguments
        System.out.println("Name: " + name);
        System.out.println("Count: " + count);
        System.out.println("Items: " + items);
        System.out.println("Nullable: " + nullable);
    }
    
    /**
     * Method with 'this' and static context.
     */
    public void demonstrateThisReference() {
        // 'this' should be available here
        final DataTypes self = this;
        System.out.println("This: " + self);
        System.out.println("This.intValue: " + this.intValue);
        // LINE 138 - Breakpoint to test 'this' inspection
    }
    
    /**
     * Static method - no 'this' reference available.
     */
    public static void staticMethod(final String param) {
        // 'this' should NOT be available here
        final String local = param.toUpperCase();
        System.out.println("Static method, param: " + param);
        System.out.println("Static method, local: " + local);
        // LINE 148 - Breakpoint to verify no 'this'
    }
    
    // Getters for testing field access
    public int getIntValue() { return intValue; }
    public String getStringValue() { return stringValue; }
    public List<String> getStringList() { return stringList; }
    public Status getStatus() { return status; }
    
    /**
     * Enum for testing enum inspection.
     */
    public enum Status {
        PENDING,
        ACTIVE,
        COMPLETED,
        FAILED
    }
}

