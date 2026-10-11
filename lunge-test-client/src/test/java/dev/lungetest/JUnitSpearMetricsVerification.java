package dev.lungetest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class JUnitSpearMetricsVerification {
    private static final long SECOND = 1_000_000_000L;

    @Test void rollingRatesExpireAtExactlyOneSecondAndKeepTotals() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.recordAttack(0L, true);
        metrics.recordAttack(SECOND / 2, false);
        var before = metrics.snapshot(SECOND - 1);
        assertEquals(2, before.clicksPerSecond());
        assertEquals(1, before.startsPerSecond());

        var atBoundary = metrics.snapshot(SECOND);
        assertEquals(1, atBoundary.clicksPerSecond());
        assertEquals(0, atBoundary.startsPerSecond());
        assertEquals(2, atBoundary.attempts());
        assertEquals(1, atBoundary.started());
        assertEquals(2, atBoundary.peakClicksPerSecond());

        var inactive = metrics.snapshot(SECOND * 2);
        assertEquals(0, inactive.clicksPerSecond());
        assertEquals(0, inactive.startsPerSecond());
        assertEquals(2, inactive.attempts());
        assertEquals(1, inactive.started());
    }

    @Test void falseStartsAreStillAttemptsAndRecordPrunesOldRates() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.recordAttack(0L, false);
        metrics.recordAttack(1L, false);
        metrics.recordAttack(2 * SECOND, true);
        var observed = metrics.snapshot(2 * SECOND);
        assertEquals(3, observed.attempts());
        assertEquals(1, observed.started());
        assertEquals(1, observed.clicksPerSecond());
        assertEquals(1, observed.startsPerSecond());
        assertEquals(2, observed.peakClicksPerSecond());
    }

    @Test void horizontalMovementTracksSpeedAndDistanceAndDecaysWhileInactive() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, -3.0, -4.0);
        assertEquals(0.0, metrics.snapshot(0L).speed());
        metrics.sampleMovement(SECOND, 0.0, 0.0);
        var moved = metrics.snapshot(SECOND);
        assertEquals(5.0, moved.speed(), 1e-9);
        assertEquals(5.0, moved.distance(), 1e-9);
        assertEquals(5.0, moved.peakSpeed(), 1e-9);
        assertEquals(5.0, metrics.snapshot(SECOND + SECOND / 2).speed(), 1e-9);
        var inactive = metrics.snapshot(SECOND + SECOND / 2 + 1);
        assertEquals(0.0, inactive.speed());
        assertEquals(5.0, inactive.distance(), 1e-9);
        assertEquals(5.0, inactive.peakSpeed(), 1e-9);
    }

    @Test void speedAndPeakWaitForAFullQuarterSecondOfMovementSamples() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(1_000_000L, 1.0, 0.0);
        var early = metrics.snapshot(1_000_000L);
        assertEquals(1.0, early.distance(), 1e-9);
        assertEquals(0.0, early.speed());
        assertEquals(0.0, early.peakSpeed());
        metrics.sampleMovement(249_999_999L, 1.0, 0.0);
        assertEquals(0.0, metrics.snapshot(249_999_999L).peakSpeed());
        metrics.sampleMovement(250_000_000L, 1.0, 0.0);
        var warmed = metrics.snapshot(250_000_000L);
        assertEquals(4.0, warmed.speed(), 1e-9);
        assertEquals(4.0, warmed.peakSpeed(), 1e-9);
    }

    @Test void millisecondCatchUpTicksDoNotCreatePersistentSpeedSpikes() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        double x = 0.0;
        // Five blocks each quarter-second, processed as five catch-up ticks 1 ms apart.
        for (int burst = 0; burst < 8; burst++) {
            long start = burst * 250_000_000L;
            for (int tick = 1; tick <= 5; tick++) {
                x += 1.0;
                metrics.sampleMovement(start + tick * 1_000_000L, x, 0.0);
                var during = metrics.snapshot(start + tick * 1_000_000L);
                assertTrue(during.speed() <= 20.0 + 1e-9);
                assertTrue(during.peakSpeed() <= 20.0 + 1e-9);
            }
            metrics.sampleMovement(start + 250_000_000L, x, 0.0);
        }
        var after = metrics.snapshot(2 * SECOND);
        assertEquals(40.0, after.distance(), 1e-9);
        assertEquals(20.0, after.speed(), 1e-9);
        assertEquals(20.0, after.peakSpeed(), 1e-9);
    }

    @Test void speedWindowInterpolatesItsBoundaryForUnevenSampleIntervals() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(100_000_000L, 1.0, 0.0);
        metrics.sampleMovement(300_000_000L, 5.0, 0.0);
        // Boundary at 50 ms has 0.5 blocks; 4.5 blocks in the last 250 ms = 18 b/s.
        var first = metrics.snapshot(300_000_000L);
        assertEquals(18.0, first.speed(), 1e-9);
        assertEquals(5.0, first.distance(), 1e-9);
        metrics.sampleMovement(500_000_000L, 7.0, 0.0);
        // Boundary at 250 ms has 4 blocks; 3 blocks in the last 250 ms = 12 b/s.
        var next = metrics.snapshot(500_000_000L);
        assertEquals(12.0, next.speed(), 1e-9);
        assertEquals(18.0, next.peakSpeed(), 1e-9);
    }

    @Test void correctionsAndInvalidSegmentsRestartSpeedWarmUpWithoutChangingTotals() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(250_000_000L, 1.0, 0.0);
        assertEquals(4.0, metrics.snapshot(250_000_000L).speed(), 1e-9);
        metrics.recordPositionSync();
        metrics.sampleMovement(300_000_000L, 100.0, 0.0);
        metrics.sampleMovement(301_000_000L, 101.0, 0.0);
        var synced = metrics.snapshot(301_000_000L);
        assertEquals(0.0, synced.speed());
        assertEquals(4.0, synced.peakSpeed(), 1e-9);
        assertEquals(2.0, synced.distance(), 1e-9);

        metrics.sampleMovement(350_000_000L, 200.0, 0.0);
        metrics.sampleMovement(351_000_000L, 201.0, 0.0);
        assertEquals(0.0, metrics.snapshot(351_000_000L).speed());
        assertEquals(3.0, metrics.snapshot(351_000_000L).distance(), 1e-9);
        metrics.sampleMovement(400_000_000L, Double.NaN, 0.0);
        metrics.sampleMovement(450_000_000L, 0.0, 0.0);
        metrics.sampleMovement(451_000_000L, 1.0, 0.0);
        assertEquals(0.0, metrics.snapshot(451_000_000L).speed());
        assertEquals(4.0, metrics.snapshot(451_000_000L).distance(), 1e-9);
        metrics.sampleMovement(3 * SECOND, 2.0, 0.0);
        metrics.sampleMovement(3 * SECOND + 1_000_000L, 3.0, 0.0);
        var gap = metrics.snapshot(3 * SECOND + 1_000_000L);
        assertEquals(0.0, gap.speed());
        assertEquals(4.0, gap.peakSpeed(), 1e-9);
        assertEquals(5.0, gap.distance(), 1e-9);
    }

    @Test void movementsOverTwoSecondsAreRebasedWithoutCountingTheGap() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(2 * SECOND, 2.0, 0.0);
        assertEquals(2.0, metrics.snapshot(2 * SECOND).distance(), 1e-9);
        metrics.sampleMovement(4 * SECOND + 1, 10.0, 0.0);
        var gap = metrics.snapshot(4 * SECOND + 1);
        assertEquals(0.0, gap.speed());
        assertEquals(2.0, gap.distance(), 1e-9);
        metrics.sampleMovement(5 * SECOND + 1, 11.0, 0.0);
        assertEquals(3.0, metrics.snapshot(5 * SECOND + 1).distance(), 1e-9);
    }

    @Test void warpsAndPositionSyncsDoNotInflateDistance() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(SECOND, 32.0, 0.0);
        assertEquals(32.0, metrics.snapshot(SECOND).distance(), 1e-9);
        metrics.sampleMovement(2 * SECOND, 65.0, 0.0);
        var warped = metrics.snapshot(2 * SECOND);
        assertEquals(0.0, warped.speed());
        assertEquals(32.0, warped.distance(), 1e-9);
        metrics.sampleMovement(3 * SECOND, 66.0, 0.0);
        assertEquals(33.0, metrics.snapshot(3 * SECOND).distance(), 1e-9);

        metrics.recordPositionSync();
        metrics.sampleMovement(4 * SECOND, 1000.0, 1000.0);
        var synced = metrics.snapshot(4 * SECOND);
        assertEquals(1, synced.positionSyncs());
        assertEquals(0.0, synced.speed());
        assertEquals(33.0, synced.distance(), 1e-9);
        metrics.sampleMovement(5 * SECOND, 1003.0, 1004.0);
        assertEquals(38.0, metrics.snapshot(5 * SECOND).distance(), 1e-9);
    }

    @Test void invalidCoordinatesAndZeroTimeRebaseWithoutSpikes() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(0L, 5.0, 0.0);
        assertEquals(0.0, metrics.snapshot(0L).distance());
        metrics.sampleMovement(SECOND, 6.0, 0.0);
        assertEquals(1.0, metrics.snapshot(SECOND).distance(), 1e-9);

        metrics.sampleMovement(2 * SECOND, Double.NaN, 0.0);
        assertEquals(0.0, metrics.snapshot(2 * SECOND).speed());
        metrics.sampleMovement(3 * SECOND, 100.0, 100.0);
        assertEquals(1.0, metrics.snapshot(3 * SECOND).distance(), 1e-9);
        metrics.sampleMovement(4 * SECOND, 0.0, Double.POSITIVE_INFINITY);
        metrics.sampleMovement(5 * SECOND, Double.NEGATIVE_INFINITY, 0.0);
        metrics.sampleMovement(6 * SECOND, 0.0, 0.0);
        var after = metrics.snapshot(6 * SECOND);
        assertEquals(1.0, after.distance(), 1e-9);
        assertTrue(Double.isFinite(after.speed()));
        assertTrue(Double.isFinite(after.peakSpeed()));
    }

    @Test void negativeClockOriginIsValidAndBackwardsObservationsClearTransientState() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.reset(-2 * SECOND);
        metrics.recordAttack(-2 * SECOND, true);
        metrics.sampleMovement(-2 * SECOND, 0.0, 0.0);
        metrics.sampleMovement(-SECOND - 1, 1.0, 0.0);
        assertEquals(1, metrics.snapshot(-SECOND - 1).clicksPerSecond());
        assertEquals(1.0, metrics.snapshot(-SECOND - 1).distance(), 1e-9);

        metrics.sampleMovement(-3 * SECOND, 30.0, 0.0);
        var backwards = metrics.snapshot(-3 * SECOND);
        assertEquals(0, backwards.clicksPerSecond());
        assertEquals(1, backwards.attempts());
        assertEquals(1.0, backwards.distance(), 1e-9);
        assertEquals(0.0, backwards.speed());
        metrics.sampleMovement(-2 * SECOND, 31.0, 0.0);
        assertEquals(2.0, metrics.snapshot(-2 * SECOND).distance(), 1e-9);
    }

    @Test void historyHasTwentyPointsAtQuarterSecondIntervalsWithoutCatchUpBursts() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.recordAttack(0L, true);
        var early = metrics.snapshot(249_999_999L);
        assertArrayEquals(new int[20], early.clickHistory());
        var boundary = metrics.snapshot(250_000_000L);
        assertEquals(1, boundary.clickHistory()[19]);
        assertEquals(0, boundary.clickHistory()[18]);
        assertEquals(1, metrics.snapshot(250_000_001L).clickHistory()[19]);
        var gap = metrics.snapshot(10 * SECOND);
        assertEquals(1, gap.clickHistory()[18]);
        assertEquals(0, gap.clickHistory()[19]);
        assertEquals(0, gap.clickHistory()[17]);
        assertEquals(20, gap.speedHistory().length);
    }

    @Test void historyKeepsNewestLastAndSnapshotsDoNotExposeArrays() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.sampleMovement(0L, 0.0, 0.0);
        double x = 0.0;
        for (int i = 1; i <= 21; i++) {
            x += i / 4.0;
            metrics.sampleMovement(i * 250_000_000L, x, 0.0);
        }
        var snapshot = metrics.snapshot(21 * 250_000_000L);
        assertEquals(2.0, snapshot.speedHistory()[0], 1e-9);
        assertEquals(21.0, snapshot.speedHistory()[19], 1e-9);
        snapshot.clickHistory()[19] = 999;
        snapshot.speedHistory()[19] = 999.0;
        assertEquals(0, snapshot.clickHistory()[19]);
        assertEquals(21.0, snapshot.speedHistory()[19], 1e-9);
        assertEquals(21.0, metrics.snapshot(21 * 250_000_000L).speedHistory()[19], 1e-9);

        int[] clicks = new int[20];
        double[] speeds = new double[20];
        var constructed = new SpearMetrics.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, clicks, speeds);
        clicks[0] = 1;
        speeds[0] = 1.0;
        assertEquals(0, constructed.clickHistory()[0]);
        assertEquals(0.0, constructed.speedHistory()[0]);
    }

    @Test void resetClearsAllCountersPeaksHistoryAndMovementAnchor() {
        SpearMetrics metrics = new SpearMetrics();
        metrics.recordAttack(0L, true);
        metrics.sampleMovement(0L, 0.0, 0.0);
        metrics.sampleMovement(SECOND, 3.0, 4.0);
        metrics.recordPositionSync();
        metrics.reset(2 * SECOND);
        var reset = metrics.snapshot(2 * SECOND);
        assertEquals(0, reset.attempts());
        assertEquals(0, reset.started());
        assertEquals(0, reset.clicksPerSecond());
        assertEquals(0, reset.startsPerSecond());
        assertEquals(0, reset.peakClicksPerSecond());
        assertEquals(0, reset.positionSyncs());
        assertEquals(0.0, reset.speed());
        assertEquals(0.0, reset.peakSpeed());
        assertEquals(0.0, reset.distance());
        assertArrayEquals(new int[20], reset.clickHistory());
        assertArrayEquals(new double[20], reset.speedHistory());
        metrics.sampleMovement(3 * SECOND, 1000.0, 1000.0);
        assertEquals(0.0, metrics.snapshot(3 * SECOND).distance());
    }
}
