package dev.lungetest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class JUnitSessionTrackerVerification {
    @Test void countsTimeAndHorizontalTravel() {
        var tracker = new SessionTracker();
        tracker.sample(0, 0, 0); tracker.sample(1_000_000_000L, 3, 4);
        assertEquals(1, tracker.seconds()); assertEquals(5, tracker.distance());
    }
    @Test void dropsTeleportDistanceButKeepsActiveTime() {
        var tracker = new SessionTracker();
        tracker.sample(0, 0, 0); tracker.sample(1_000_000_000L, 1000, 0);
        tracker.sample(2_000_000_000L, 1003, 4);
        assertEquals(2, tracker.seconds()); assertEquals(5, tracker.distance());
    }
    @Test void pauseAndDimensionChangesClearTheAnchor() {
        var tracker = new SessionTracker();
        tracker.sample(0, 0, 0); tracker.sample(1_000_000_000L, 1, 0); tracker.clearAnchor();
        tracker.sample(30_000_000_000L, 100, 100); tracker.sample(31_000_000_000L, 101, 100);
        assertEquals(2, tracker.seconds()); assertEquals(2, tracker.distance());
    }
    @Test void missingFramesAndInvalidPositionsDoNotInflateTotals() {
        var tracker = new SessionTracker();
        tracker.sample(0, 0, 0); tracker.sample(10_000_000_000L, 5, 0);
        tracker.sample(11_000_000_000L, Double.NaN, 0); tracker.sample(12_000_000_000L, 10, 0);
        tracker.sample(13_000_000_000L, 11, 0);
        assertEquals(1, tracker.seconds()); assertEquals(1, tracker.distance());
    }
    @Test void clockReversalIsIgnoredAndRecoveryReanchors() {
        var tracker = new SessionTracker();
        tracker.sample(1_000_000_000L, 0, 0); tracker.sample(0, 1, 0); tracker.sample(1_000_000_000L, 2, 0);
        assertEquals(1, tracker.seconds()); assertEquals(1, tracker.distance());
    }
    @Test void resetClearsTimeDistanceAndPosition() {
        var tracker = new SessionTracker();
        tracker.sample(0, 0, 0); tracker.sample(1_000_000_000L, 5, 0); tracker.reset();
        tracker.sample(2_000_000_000L, 100, 0);
        assertEquals(0, tracker.seconds()); assertEquals(0, tracker.distance());
    }
}
