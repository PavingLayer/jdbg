package com.jdbg.test;

/**
 * Calculator class for testing method breakpoints and stepping.
 * 
 * Tests:
 * - Method entry breakpoints
 * - Step into/over/out operations
 * - Stack frame inspection with multiple levels
 * - Variable inspection in different frames
 */
public final class Calculator {
    
    private double lastResult;
    private int operationCount;
    
    public Calculator() {
        this.lastResult = 0;
        this.operationCount = 0;
    }
    
    /**
     * Addition operation.
     * Good target for method breakpoint (BP-003).
     */
    public double add(final double a, final double b) {
        operationCount++;
        final double result = a + b;  // LINE 27 - step target
        lastResult = result;
        return result;
    }
    
    /**
     * Subtraction operation.
     */
    public double subtract(final double a, final double b) {
        operationCount++;
        final double result = a - b;
        lastResult = result;
        return result;
    }
    
    /**
     * Multiplication operation.
     */
    public double multiply(final double a, final double b) {
        operationCount++;
        final double result = a * b;
        lastResult = result;
        return result;
    }
    
    /**
     * Division operation.
     * Can be used to test ArithmeticException scenarios.
     */
    public double divide(final double a, final double b) {
        operationCount++;
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        final double result = a / b;
        lastResult = result;
        return result;
    }
    
    /**
     * Chain calculation for testing deep stack frames.
     * Creates a call stack: chainCalculation -> level1 -> level2 -> level3
     */
    public double chainCalculation(final double input) {
        System.out.println("Chain calculation starting with: " + input);
        return level1(input);
    }
    
    private double level1(final double value) {
        final double adjusted = value * 2;  // Frame index 1
        return level2(adjusted);
    }
    
    private double level2(final double value) {
        final double adjusted = value + 10;  // Frame index 2
        return level3(adjusted);
    }
    
    private double level3(final double value) {
        final double result = value / 3;  // Frame index 3 - deepest
        // Good place to set breakpoint and inspect all frames
        return result;  // LINE 86 - breakpoint here shows deep stack
    }
    
    /**
     * Recursive calculation for testing recursive stack frames.
     */
    public int factorial(final int n) {
        if (n <= 1) {
            return 1;  // Base case - good for BP to see full recursion stack
        }
        return n * factorial(n - 1);
    }
    
    /**
     * Loop-based calculation for testing step-over in loops.
     */
    public int sumRange(final int start, final int end) {
        int sum = 0;
        for (int i = start; i <= end; i++) {
            sum += i;  // LINE 104 - step over should stay in loop
        }
        return sum;
    }
    
    /**
     * Conditional calculation for testing step behavior with conditions.
     */
    public String classify(final double value) {
        String result;
        if (value < 0) {
            result = "negative";
        } else if (value == 0) {
            result = "zero";
        } else if (value < 100) {
            result = "small positive";
        } else {
            result = "large positive";
        }
        return result;
    }
    
    public double getLastResult() {
        return lastResult;
    }
    
    public int getOperationCount() {
        return operationCount;
    }
}

