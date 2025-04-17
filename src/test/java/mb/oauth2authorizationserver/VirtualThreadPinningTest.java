package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class VirtualThreadPinningTest {

    private static final int NUM_THREADS = 5000;
    private static final long BLOCKING_TIME_MS = 5;
    private static final int CPU_WORK_ITERATIONS = 20000;

    @Test
    @DisplayName("Virtual Thread Pinning Test (Java 21 vs 24)")
    void testVirtualThreadPinning() throws InterruptedException {
        // Force single carrier thread to make pinning effects more visible
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        var completedTasks = new AtomicInteger(0);
        var busyWorkSink = new double[]{0.0};

        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            Instant start = Instant.now();

            // Launch multiple virtual threads that will compete for the single carrier thread
            for (int i = 0; i < NUM_THREADS; i++) {
                final Object lock = new Object(); // Separate lock per thread to demonstrate pinning
                executor.submit(() -> {
                    // CPU-bound work outside lock
                    double result = 0;
                    for (int j = 0; j < CPU_WORK_ITERATIONS; j++) {
                        result += Math.sin(j) * Math.cos(j);
                    }
                    busyWorkSink[0] += result;

                    // Lock acquisition and sleep
                    synchronized (lock) {
                        // In Java 21: Virtual thread remains pinned to carrier thread during sleep
                        // In Java 24: Virtual thread unpins from carrier thread during sleep
                        await().atMost(Duration.ofMillis(BLOCKING_TIME_MS));
                    }
                    completedTasks.incrementAndGet();
                    return null;
                });
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);
            Duration duration = Duration.between(start, Instant.now());

            // Verify test results
            assertTrue(finished, "Test should complete within timeout");
            assertEquals(NUM_THREADS, completedTasks.get(), "All tasks should complete");

            // Log performance metrics
            log.info("Java Version: {}", System.getProperty("java.version"));
            log.info("Total tasks completed: {}", String.format("%,d", completedTasks.get()));
            log.info("Total execution time: {} seconds", duration.toMillis() / 1000.0);
            log.info("Average time per task: {} ms", duration.toMillis() / (double) NUM_THREADS);

            // Additional assertions for reasonable performance
            assertTrue(duration.toSeconds() < 300, "Test should complete in reasonable time");
            assertNotEquals(0.0, busyWorkSink[0], "CPU work should be performed");
        }
    }
}
