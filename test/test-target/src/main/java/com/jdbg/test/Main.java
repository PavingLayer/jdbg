package com.jdbg.test;

/**
 * Main entry point for JDBG test target application.
 * 
 * This application is designed to test various debugging scenarios:
 * - Breakpoints (line and method)
 * - Variable inspection (primitives, objects, arrays)
 * - Step operations (into, over, out)
 * - Thread management
 * - Exception handling
 * 
 * Usage modes:
 *   java -jar test-target.jar              # Run all scenarios in loop
 *   java -jar test-target.jar simple       # Simple execution (good for basic BP tests)
 *   java -jar test-target.jar calc         # Calculator operations
 *   java -jar test-target.jar threads      # Multi-threaded scenarios
 *   java -jar test-target.jar exceptions   # Exception scenarios
 *   java -jar test-target.jar wait         # Wait mode (stays alive for attach)
 */
public final class Main {
    
    public static void main(final String[] args) throws InterruptedException {
        System.out.println("JDBG Test Target - Starting");
        System.out.println("PID: " + ProcessHandle.current().pid());
        
        final String mode = args.length > 0 ? args[0] : "all";
        
        switch (mode) {
            case "simple" -> runSimpleScenario();
            case "calc" -> runCalculatorScenario();
            case "threads" -> runThreadScenario();
            case "exceptions" -> runExceptionScenario();
            case "wait" -> runWaitMode();
            case "all" -> runAllInLoop();
            default -> {
                System.out.println("Unknown mode: " + mode);
                printUsage();
            }
        }
        
        System.out.println("JDBG Test Target - Finished");
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar test-target.jar [mode]");
        System.out.println("Modes: simple, calc, threads, exceptions, wait, all");
    }
    
    /**
     * Simple scenario for basic breakpoint and variable testing.
     */
    private static void runSimpleScenario() {
        System.out.println("=== Simple Scenario ===");
        
        // Local variables of different types
        final int intValue = 42;
        final long longValue = 9876543210L;
        final double doubleValue = 3.14159;
        final boolean boolValue = true;
        final String stringValue = "Hello, JDBG!";
        final int[] intArray = {1, 2, 3, 4, 5};
        final String[] stringArray = {"apple", "banana", "cherry"};
        Object nullValue = null;
        
        // Create an object for inspection
        final Person person = new Person("Alice", 30);
        
        // Line for setting breakpoint (BP-001)
        System.out.println("Int value: " + intValue);  // LINE 65 - good BP target
        System.out.println("Long value: " + longValue);
        System.out.println("Double value: " + doubleValue);
        System.out.println("Boolean value: " + boolValue);
        System.out.println("String value: " + stringValue);
        System.out.println("Array length: " + intArray.length);
        System.out.println("Person: " + person);
        System.out.println("Null value: " + nullValue);
        
        // Method call for step-into testing
        final int result = simpleCalculation(10, 5);
        System.out.println("Calculation result: " + result);
    }
    
    /**
     * Simple method for step-into testing.
     */
    private static int simpleCalculation(final int a, final int b) {
        final int sum = a + b;  // LINE 81 - step into lands here
        final int product = a * b;
        return sum + product;
    }
    
    /**
     * Calculator scenario for method breakpoints and stepping.
     */
    private static void runCalculatorScenario() {
        System.out.println("=== Calculator Scenario ===");
        
        final Calculator calc = new Calculator();
        
        // These method calls are good for method breakpoint testing
        final double result1 = calc.add(10, 20);
        final double result2 = calc.subtract(50, 25);
        final double result3 = calc.multiply(6, 7);
        final double result4 = calc.divide(100, 4);
        
        System.out.println("Add: " + result1);
        System.out.println("Subtract: " + result2);
        System.out.println("Multiply: " + result3);
        System.out.println("Divide: " + result4);
        
        // Nested calls for stack frame testing
        final double chainResult = calc.chainCalculation(10);
        System.out.println("Chain result: " + chainResult);
    }
    
    /**
     * Multi-threaded scenario for thread management testing.
     */
    private static void runThreadScenario() throws InterruptedException {
        System.out.println("=== Thread Scenario ===");
        
        final ThreadDemo demo = new ThreadDemo();
        demo.runMultipleThreads();
    }
    
    /**
     * Exception scenarios for exception breakpoint testing.
     */
    private static void runExceptionScenario() {
        System.out.println("=== Exception Scenario ===");
        
        final ExceptionDemo demo = new ExceptionDemo();
        
        // Caught exception
        demo.throwCaughtException();
        
        // Various exception types
        demo.throwArithmeticException();
        demo.throwArrayIndexException();
        demo.throwNullPointerException();
        demo.throwCustomException();
        
        // This one actually throws uncaught exception - run last or handle
        try {
            demo.throwUncaughtException();
        } catch (final RuntimeException e) {
            System.out.println("Caught in main: " + e.getMessage());
        }
    }
    
    /**
     * Wait mode - stays alive for debugger attachment.
     */
    private static void runWaitMode() throws InterruptedException {
        System.out.println("=== Wait Mode ===");
        System.out.println("Process waiting for debugger operations...");
        System.out.println("Use Ctrl+C to exit");
        
        int counter = 0;
        while (true) {
            counter++;
            if (counter % 10 == 0) {
                System.out.println("Heartbeat: " + counter);  // LINE 151 - dynamic BP target
            }
            Thread.sleep(1000);
            
            // Do some work that can be debugged
            performWork(counter);
        }
    }
    
    /**
     * Periodic work for wait mode debugging.
     */
    private static void performWork(final int iteration) {
        final String status = iteration % 2 == 0 ? "even" : "odd";
        final int computed = iteration * 2;  // LINE 164 - good for stepping
        // This line is good for setting breakpoints during wait mode
    }
    
    /**
     * Run all scenarios in a loop.
     */
    private static void runAllInLoop() throws InterruptedException {
        System.out.println("=== Running All Scenarios in Loop ===");
        
        for (int i = 0; i < 3; i++) {
            System.out.println("\n--- Iteration " + (i + 1) + " ---");
            runSimpleScenario();
            runCalculatorScenario();
            runExceptionScenario();
            Thread.sleep(1000);
        }
        
        // Finally run thread scenario
        runThreadScenario();
    }
}

