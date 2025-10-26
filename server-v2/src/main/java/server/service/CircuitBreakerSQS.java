package server.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker for SQS failures
 */
public class CircuitBreakerSQS {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    private final int failureThreshold = 5;
    private final long resetTimeout = 60000;
    private final int successThreshold = 2;

    public boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeout) {
                state = State.HALF_OPEN;
                successCount.set(0);
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        failureCount.set(0);
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= successThreshold) {
                state = State.CLOSED;
                System.out.println("✓ Circuit breaker: CLOSED (recovered)");
            }
        }
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            System.out.println("✗ Circuit breaker: OPEN (still failing)");
            return;
        }
        if (failureCount.incrementAndGet() >= failureThreshold) {
            state = State.OPEN;
            System.out.println("✗ Circuit breaker: OPEN (too many failures)");
        }
    }

    public State getState() { return state; }
    public int getFailureCount() { return failureCount.get(); }
}