package com.jdbg.test;

/**
 * Exception demo for testing exception breakpoints.
 * 
 * Tests:
 * - Caught exception breakpoints (EXC-003)
 * - Uncaught exception breakpoints (EXC-004)
 * - Specific exception type catching (EXC-002)
 * - Exception with message inspection
 * - Exception stack trace inspection
 */
public final class ExceptionDemo {
    
    /**
     * Throws and catches an exception internally.
     * Tests: EXC-003 (caught exceptions)
     */
    public void throwCaughtException() {
        System.out.println("Testing caught exception...");
        try {
            throw new RuntimeException("This is a caught exception");
        } catch (final RuntimeException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }
    
    /**
     * Throws an exception that propagates up.
     * Tests: EXC-004 (uncaught exceptions)
     */
    public void throwUncaughtException() {
        System.out.println("Testing uncaught exception...");
        throw new RuntimeException("This is an uncaught exception");
    }
    
    /**
     * Throws ArithmeticException.
     * Tests: EXC-002 (specific exception type)
     */
    public void throwArithmeticException() {
        System.out.println("Testing ArithmeticException...");
        try {
            @SuppressWarnings("unused")
            final int result = divideByZero();
        } catch (final ArithmeticException e) {
            System.out.println("Caught ArithmeticException: " + e.getMessage());
        }
    }
    
    private int divideByZero() {
        final int a = 10;
        final int b = 0;
        return a / b;  // LINE 50 - ArithmeticException thrown here
    }
    
    /**
     * Throws ArrayIndexOutOfBoundsException.
     * Tests: EXC-002 (specific exception type)
     */
    public void throwArrayIndexException() {
        System.out.println("Testing ArrayIndexOutOfBoundsException...");
        try {
            accessOutOfBounds();
        } catch (final ArrayIndexOutOfBoundsException e) {
            System.out.println("Caught ArrayIndexOutOfBoundsException: " + e.getMessage());
        }
    }
    
    private void accessOutOfBounds() {
        final int[] array = {1, 2, 3};
        @SuppressWarnings("unused")
        final int value = array[10];  // LINE 69 - ArrayIndexOutOfBoundsException
    }
    
    /**
     * Throws NullPointerException.
     * Tests: EXC-002 (specific exception type)
     */
    public void throwNullPointerException() {
        System.out.println("Testing NullPointerException...");
        try {
            accessNull();
        } catch (final NullPointerException e) {
            System.out.println("Caught NullPointerException: " + e.getMessage());
        }
    }
    
    private void accessNull() {
        final String str = null;
        @SuppressWarnings("unused")
        final int len = str.length();  // LINE 86 - NullPointerException
    }
    
    /**
     * Throws custom exception.
     * Tests: EXC-007 (subclass matching)
     */
    public void throwCustomException() {
        System.out.println("Testing custom exception...");
        try {
            throw new CustomException("Custom error", 42);
        } catch (final CustomException e) {
            System.out.println("Caught CustomException: " + e.getMessage() 
                               + ", code=" + e.getErrorCode());
        }
    }
    
    /**
     * Creates nested exception chain.
     * Tests: Exception chain inspection
     */
    public void throwChainedException() {
        System.out.println("Testing chained exception...");
        try {
            try {
                throw new IllegalArgumentException("Root cause");
            } catch (final IllegalArgumentException e) {
                throw new RuntimeException("Wrapper exception", e);
            }
        } catch (final RuntimeException e) {
            System.out.println("Caught chained exception: " + e.getMessage());
            System.out.println("  Caused by: " + e.getCause().getMessage());
        }
    }
    
    /**
     * Throws exception from nested method call.
     * Tests: Exception location in stack trace
     */
    public void throwFromNestedCall() {
        System.out.println("Testing nested call exception...");
        try {
            outerMethod();
        } catch (final IllegalStateException e) {
            System.out.println("Caught from nested call: " + e.getMessage());
        }
    }
    
    private void outerMethod() {
        innerMethod();
    }
    
    private void innerMethod() {
        throw new IllegalStateException("Exception from inner method");
    }
    
    /**
     * Custom exception class for testing.
     */
    public static final class CustomException extends RuntimeException {
        
        private final int errorCode;
        
        public CustomException(final String message, final int errorCode) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}

