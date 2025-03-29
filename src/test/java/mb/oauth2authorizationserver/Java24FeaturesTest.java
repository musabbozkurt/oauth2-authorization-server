package mb.oauth2authorizationserver;

import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/***
 * Java 24 Features Overview
 * ------------------------
 * Performance Improvements
 * ------------------------
 * • Ahead-of-Time Class Loading and Linking
 *   - Ahead-of-time Class Loading and Linking shift some work loading before runtime that's why it makes java app at least %42 faster at startup.
 *   - Shifts class loading work before runtime
 *   - Up to 42% faster application startup
 * ----------------------------
 * Virtual Threads Enhancements
 * ----------------------------
 * • Improved Synchronization Handling
 *   - Fixed blocking issues with synchronized blocks in virtual threads
 *   - No more accidental main thread freezing
 * • Carrier Thread Management
 *   - Virtual thread issue if there is synchronized a block inside virtual thread, this can accidentally block the main thread and freeze the application. Java 24 removes that issue.
 *   - Java pinning carrier threads meaning other virtual threads couldn't run, now it does not pin the carrier thread. Or ReentrantLock can be used.
 *   - Eliminated carrier thread pinning
 *   - Better virtual thread scheduling
 *   - Alternative: ReentrantLock usage
 * ----------------
 * API Improvements
 * ----------------
 * • Gatherers API (Preview)
 *   - New stream processing capabilities
 *   - Supports folding, filtering, mapping, scanning, and windowing operations
 * • Pattern Matching for Switch
 *   - Enhanced switch expression capabilities
 * -----------------------------
 * Security and Platform Updates
 * -----------------------------
 * • Removed Security Manager
 *   - Replaced by modern alternatives:
 *     › Container security
 *     › OS permissions
 *     › Spring Security
 * • Platform Cleanup
 *   - Removed Windows 32-bit x86 port
 * -----------------------------
 */
class Java24FeaturesTest {

    // Gatherer that filters even numbers and maps them to their double value
    private static final Gatherer<Integer, List<Integer>, Integer> filterAndMap = Gatherer.of(
            ArrayList::new,
            (state, element, downstream) -> {
                if (element % 2 == 0) {
                    downstream.push(element * 2);
                }
                state.add(element);
                return true;
            },
            (state1, state2) -> {
                state1.addAll(state2);
                return state1;
            },
            (state, _) -> System.out.println("Total processed elements: " + state.size())
    );

    @Test
    void foldGatherer_ShouldStringBuilderConcatenateStrings_WhenInputContainsMultipleStrings() {
        // Arrange
        var strings = List.of("a", "b", "c", "d");

        // Act
        var result = strings.stream()
                .gather(Gatherers.fold(StringBuilder::new, StringBuilder::append))
                .findFirst()
                .map(StringBuilder::toString)
                .orElse("");

        // Assertions
        assertEquals("abcd", result);
    }

    @Test
    void foldGatherer_ShouldConcatenateStrings_WhenInputContainsMultipleStrings() {
        // Arrange
        var strings = List.of("a", "b", "c", "d");

        // Act
        var result = strings.stream()
                .gather(Gatherers.fold(() -> "", (a, b) -> a + b))
                .findFirst()
                .orElse("");

        // Assertions
        assertEquals("abcd", result);
    }

    @Test
    void foldGatherer_ShouldConcatenateCharacters_WhenInputContainsName() {
        // Arrange
        var expected = "abcd";

        // Act
        var result = Stream.of("a", "b", "c", "d")
                .gather(Gatherers.fold(() -> "", (a, b) -> a + b))
                .findFirst()
                .orElse("");

        // Assertions
        assertEquals(expected, result);
    }

    @Test
    void filterAndMapGatherer_ShouldReturnFilteredAndMappedList_WhenInputContainsEvenNumbers() {
        // Arrange
        var numbers = List.of(1, 2, 3, 4, 5);

        // Act
        var result = numbers.stream()
                .gather(filterAndMap)
                .toList();

        // Assertions
        assertEquals(List.of(4, 8), result);
    }

    @Test
    void scanGatherer_ShouldShowIntermediateResults_WhenConcatenatingName() {
        // Arrange
        var expected = List.of("a", "ab", "abc", "abcd", "abcde");

        // Act
        var result = Stream.of("a", "b", "c", "d", "e")
                .gather(Gatherers.scan(() -> "", (initial, element) -> initial + element))
                .toList();

        // Assertions
        assertEquals(expected, result);
    }

    @Test
    void patternMatching_ShouldMatchShapes_WhenUsingTypePatterns() {
        // Arrange
        record Circle(double radius) {
        }
        record Rectangle(double length, double width) {
        }
        record Triangle(double base, double height) {
        }

        var shapes = List.of(
                new Circle(5),
                new Rectangle(4, 3),
                new Triangle(6, 8)
        );

        // Act
        // Assertions
        for (var shape : shapes) {
            var area = switch (shape) {
                case Circle c -> Math.PI * c.radius() * c.radius();
                case Rectangle r -> r.length() * r.width();
                case Triangle t -> 0.5 * t.base() * t.height();
                default -> throw new IllegalArgumentException("Unknown shape");
            };

            switch (shape) {
                case Circle _ -> assertEquals(Math.PI * 25, area, 0.001);
                case Rectangle _ -> assertEquals(12.0, area, 0.001);
                case Triangle _ -> assertEquals(24.0, area, 0.001);
                default -> throw new IllegalStateException("Unexpected value: " + shape);
            }
        }
    }

    @Test
    void patternMatching_ShouldMatchGuardedPatterns_WhenUsingTypeAndCondition() {
        // Arrange
        record Person(String name, int age) {
        }

        var people = List.of(
                new Person("Alice", 25),
                new Person("Bob", 17),
                new Person("Charlie", 65)
        );

        // Act
        // Assertions
        for (var person : people) {
            var category = switch (person) {
                case Person p when p.age() < 18 -> "Minor";
                case Person p when p.age() >= 65 -> "Senior";
                case Person _ -> "Adult";
            };

            switch (person.name()) {
                case "Alice" -> assertEquals("Adult", category);
                case "Bob" -> assertEquals("Minor", category);
                case "Charlie" -> assertEquals("Senior", category);
                default -> throw new IllegalArgumentException("Unknown person");
            }
        }
    }

    @Test
    void systemArchitecture_ShouldNotBe32BitX86_WhenRunningOnWindows() {
        // Arrange
        var osName = System.getProperty("os.name").toLowerCase();
        var osArch = System.getProperty("os.arch").toLowerCase();

        // Act
        // Assertions
        assertFalse(osName.contains("windows") && (osArch.equals("x86") || osArch.equals("i386")), "32-bit x86 architecture is no longer supported on Windows");
    }

    @Test
    void virtualThread_ShouldNotBlock_WhenUsingSynchronizedBlock() throws InterruptedException {
        // Arrange
        var counter = new Counter();
        var threadCount = 1000;
        var latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            Thread.startVirtualThread(() -> {
                counter.incrementSync();
                latch.countDown();
            });
        }

        // Assertions
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All virtual threads should complete in time");
        assertEquals(threadCount, counter.getValue());
    }

    @Test
    void virtualThread_ShouldNotBlock_WhenUsingReentrantLock() throws InterruptedException {
        // Arrange
        var counter = new LockCounter();
        var threadCount = 1000;
        var latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            Thread.startVirtualThread(() -> {
                counter.increment();
                latch.countDown();
            });
        }

        // Assertions
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All virtual threads should complete in time");
        assertEquals(threadCount, counter.getValue());
    }

    @Test
    void windowSlidingGatherer_ShouldCreateSlidingWindows_WhenInputContainsSequentialNumbers() {
        // Arrange
        var numbers = List.of(1, 2, 3, 4, 5);

        // Act
        var result = numbers.stream()
                .gather(Gatherers.windowSliding(3))
                .toList();

        // Assertions
        assertEquals(List.of(
                List.of(1, 2, 3),
                List.of(2, 3, 4),
                List.of(3, 4, 5)
        ), result);
    }

    @Test
    void windowFixedGatherer_ShouldCreateFixedWindows_WhenInputContainsSequentialNumbers() {
        // Arrange
        var numbers = List.of(1, 2, 3, 4, 5);

        // Act
        var result = numbers.stream()
                .gather(Gatherers.windowFixed(2))
                .toList();

        // Assertions
        assertEquals(List.of(
                List.of(1, 2),
                List.of(3, 4),
                List.of(5)
        ), result);
    }
}

@Getter
class Counter {
    private int value = 0;

    synchronized void incrementSync() {
        value++;
    }
}

@Getter
class LockCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int value = 0;

    void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }
}
