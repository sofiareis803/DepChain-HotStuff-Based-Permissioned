package com.depchain.consensus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class TimeoutTest {
    
    private Timeout timeoutTest;
    
    @BeforeEach
    public void setUp() {
        timeoutTest = new Timeout();
    }
    
    @AfterEach
    public void cancelTimout() {
        if (timeoutTest != null) {
            timeoutTest.cancel();
        }
    }
    
    @Test
    public void testConstructorTimeout() {
        timeoutTest = new Timeout();
        assertNotNull(timeoutTest);
    }
    
    @Test
    public void testStartTimeExecutesActionAfterTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        
        timeoutTest.startTime(() -> {
            executed.set(true);
            latch.countDown();
        });
        
        boolean completed = latch.await(3000, TimeUnit.MILLISECONDS);
        
        assertTrue( completed, "Timeout action should have executed");
        assertTrue( executed.get(), "Action should have been called");
    }
    
    
    @Test
    public void testTimeoutDoublingBehavior() throws InterruptedException {
        CountDownLatch firstLatch = new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);
        
        timeoutTest.startTime(() -> {
            firstLatch.countDown();
        });
        
        boolean firstExecuted = firstLatch.await(3000, TimeUnit.MILLISECONDS);
        assertTrue( firstExecuted, "First timeout should execute");
        
        long startTime = System.currentTimeMillis();
        timeoutTest.startTime(() -> {
            secondLatch.countDown();
        });
        
        boolean secondExecuted = secondLatch.await(5000, TimeUnit.MILLISECONDS);
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue( secondExecuted, "Second timeout should execute");
        
        assertTrue(executionTime >= 3500 && executionTime <= 5000,
           "Second timeout should be roughly doubled (got " + executionTime + "ms)");
    }
}