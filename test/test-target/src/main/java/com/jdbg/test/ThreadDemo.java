package com.jdbg.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded demo for testing thread management operations.
 * 
 * Tests:
 * - Thread listing with multiple threads
 * - Thread suspend/resume
 * - Thread selection
 * - Thread-specific breakpoints
 * - Thread status (running, waiting, etc.)
 */
public final class ThreadDemo {
    
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile boolean running = true;
    
    /**
     * Run multiple threads for thread listing and management tests.
     */
    public void runMultipleThreads() throws InterruptedException {
        System.out.println("Starting multi-threaded demo...");
        
        final List<Thread> threads = new ArrayList<>();
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(4);
        
        // Create worker threads with distinct names
        threads.add(createWorkerThread("Worker-Alpha", startLatch, doneLatch));
        threads.add(createWorkerThread("Worker-Beta", startLatch, doneLatch));
        threads.add(createWorkerThread("Worker-Gamma", startLatch, doneLatch));
        threads.add(createSleepingThread("Sleeper-Thread", startLatch, doneLatch));
        
        // Start all threads
        for (final Thread thread : threads) {
            thread.start();
        }
        
        // Give threads time to initialize
        Thread.sleep(100);
        
        // Release all threads to start working
        System.out.println("Releasing threads to work...");
        startLatch.countDown();
        
        // Let threads work for a bit
        Thread.sleep(2000);
        
        // Signal threads to stop
        running = false;
        
        // Wait for completion
        doneLatch.await();
        
        System.out.println("All threads completed. Counter: " + counter.get());
    }
    
    private Thread createWorkerThread(final String name, 
                                       final CountDownLatch startLatch,
                                       final CountDownLatch doneLatch) {
        return new Thread(() -> {
            try {
                System.out.println(name + " waiting to start...");
                startLatch.await();
                System.out.println(name + " started!");
                
                while (running) {
                    doWork(name);  // LINE 67 - good BP target in thread
                    Thread.sleep(100);
                }
                
                System.out.println(name + " finished.");
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(name + " interrupted.");
            } finally {
                doneLatch.countDown();
            }
        }, name);
    }
    
    private Thread createSleepingThread(final String name,
                                        final CountDownLatch startLatch,
                                        final CountDownLatch doneLatch) {
        return new Thread(() -> {
            try {
                startLatch.await();
                System.out.println(name + " going to sleep...");
                
                // This thread will be in SLEEPING state
                while (running) {
                    Thread.sleep(500);  // Thread status = SLEEPING
                    counter.incrementAndGet();
                }
                
                System.out.println(name + " woke up.");
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        }, name);
    }
    
    private void doWork(final String threadName) {
        // Local variables specific to this thread
        final int current = counter.incrementAndGet();
        final long threadId = Thread.currentThread().getId();
        
        // Some computation (LINE 105 - inspect thread-local variables)
        final int computed = current * 2;
        
        if (current % 5 == 0) {
            System.out.println(threadName + " [" + threadId + "]: " + current);
        }
    }
    
    /**
     * Creates a thread that monitors with Object.wait().
     * Useful for testing MONITOR/WAIT thread status.
     */
    public Thread createMonitorThread(final Object lock) {
        return new Thread(() -> {
            synchronized (lock) {
                try {
                    System.out.println("Monitor thread waiting...");
                    lock.wait();  // Thread status = WAIT
                    System.out.println("Monitor thread notified!");
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Monitor-Thread");
    }
    
    /**
     * Creates a thread that tries to acquire a lock.
     * Useful for testing MONITOR thread status (blocked on synchronized).
     */
    public Thread createBlockedThread(final Object lock) {
        return new Thread(() -> {
            System.out.println("Blocked thread trying to acquire lock...");
            synchronized (lock) {  // Will block if lock is held
                System.out.println("Blocked thread acquired lock!");
            }
        }, "Blocked-Thread");
    }
}

