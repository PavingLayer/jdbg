package com.jdbg.test;

/**
 * Class with inner classes for testing class name handling.
 * 
 * Tests:
 * - Inner class breakpoint naming (OuterClass$InnerClass)
 * - Anonymous class breakpoints
 * - Lambda expression debugging
 */
public final class InnerClassDemo {
    
    private final String outerField = "outer";
    
    /**
     * Non-static inner class.
     */
    public class InnerClass {
        private final String innerField = "inner";
        
        public void innerMethod() {
            // Class name: com.jdbg.test.InnerClassDemo$InnerClass
            System.out.println("Inner class method");
            System.out.println("Can access outer: " + outerField);
            System.out.println("Inner field: " + innerField);
            // LINE 25 - Breakpoint in inner class
        }
    }
    
    /**
     * Static nested class.
     */
    public static class StaticNestedClass {
        private final String nestedField = "nested";
        
        public void nestedMethod() {
            // Class name: com.jdbg.test.InnerClassDemo$StaticNestedClass
            System.out.println("Static nested class method");
            System.out.println("Nested field: " + nestedField);
            // LINE 38 - Breakpoint in static nested class
        }
    }
    
    /**
     * Demonstrates anonymous class.
     */
    public void demonstrateAnonymousClass() {
        // Anonymous class creates: com.jdbg.test.InnerClassDemo$1
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("Anonymous class run method");
                System.out.println("Can access outer: " + outerField);
                // LINE 52 - Breakpoint in anonymous class
            }
        };
        
        runnable.run();
    }
    
    /**
     * Demonstrates lambda expressions.
     */
    public void demonstrateLambda() {
        // Lambda - may be harder to set breakpoints
        final Runnable lambda = () -> {
            System.out.println("Lambda expression");
            // LINE 65 - Breakpoint in lambda
        };
        
        lambda.run();
        
        // Lambda with explicit types
        final java.util.function.Function<Integer, Integer> doubler = (Integer x) -> {
            final int result = x * 2;  // LINE 72 - Breakpoint in typed lambda
            return result;
        };
        
        System.out.println("Doubled: " + doubler.apply(21));
    }
    
    /**
     * Demonstrates local class (class defined in method).
     */
    public void demonstrateLocalClass() {
        // Local class creates: com.jdbg.test.InnerClassDemo$1LocalClass
        class LocalClass {
            private final String localField = "local";
            
            void localMethod() {
                System.out.println("Local class method");
                System.out.println("Local field: " + localField);
                System.out.println("Can access outer: " + outerField);
                // LINE 90 - Breakpoint in local class
            }
        }
        
        final LocalClass local = new LocalClass();
        local.localMethod();
    }
    
    /**
     * Run all inner class demonstrations.
     */
    public void runAll() {
        System.out.println("=== Inner Class Demo ===");
        
        // Inner class
        final InnerClass inner = new InnerClass();
        inner.innerMethod();
        
        // Static nested class
        final StaticNestedClass nested = new StaticNestedClass();
        nested.nestedMethod();
        
        // Anonymous class
        demonstrateAnonymousClass();
        
        // Lambda
        demonstrateLambda();
        
        // Local class
        demonstrateLocalClass();
    }
}

