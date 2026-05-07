package com.premiumauth.hybridlogin.utils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class RateLimiter {

    private final long minIntervalNanos;
    private final AtomicLong lastRequestNanos = new AtomicLong(0);

    public RateLimiter(double permitsPerSecond) {
        this.minIntervalNanos = (long) (1_000_000_000L / permitsPerSecond);
    }

    public void acquire() throws InterruptedException {
        long now = System.nanoTime();
        long last = lastRequestNanos.get();
        long waitNanos = (last + minIntervalNanos) - now;

        if (waitNanos > 0) {
            long deadline = System.nanoTime() + waitNanos;
            while (waitNanos > 0) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                LockSupport.parkNanos(this, waitNanos);
                waitNanos = deadline - System.nanoTime();
            }
        }

        lastRequestNanos.set(System.nanoTime());
    }
}
