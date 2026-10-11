package dev.lungetest;

import java.util.ArrayDeque;

/** Local observations only: a successful client invocation is not server acceptance. */
public final class SpearMetrics {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long HISTORY_INTERVAL_NANOS = 250_000_000L;
    private static final long SPEED_WINDOW_NANOS = 250_000_000L;
    private static final long SPEED_STALE_NANOS = 500_000_000L;
    private static final long MAX_MOVEMENT_GAP_NANOS = 2_000_000_000L;
    private static final double MAX_MOVEMENT_SEGMENT = 32.0;
    private static final int HISTORY_SIZE = 20;

    private final ArrayDeque<Long> clicks = new ArrayDeque<>();
    private final ArrayDeque<Long> starts = new ArrayDeque<>();
    private final ArrayDeque<MovementPoint> speedWindow = new ArrayDeque<>();
    private final int[] clickHistory = new int[HISTORY_SIZE];
    private final double[] speedHistory = new double[HISTORY_SIZE];
    private long attempts;
    private long started;
    private long positionSyncs;
    private int peakClicksPerSecond;
    private double speed;
    private double peakSpeed;
    private double distance;
    private boolean movementAnchored;
    private double anchorX;
    private double anchorZ;
    private long anchorNanos;
    private long lastMovementNanos;
    private long lastObservationNanos;
    private long lastHistoryNanos;

    public SpearMetrics() {
        reset(0L);
    }

    public void reset(long nowNanos) {
        clicks.clear();
        starts.clear();
        speedWindow.clear();
        java.util.Arrays.fill(clickHistory, 0);
        java.util.Arrays.fill(speedHistory, 0.0);
        attempts = started = positionSyncs = 0L;
        peakClicksPerSecond = 0;
        speed = peakSpeed = distance = 0.0;
        movementAnchored = false;
        lastObservationNanos = lastHistoryNanos = lastMovementNanos = nowNanos;
    }

    public void recordAttack(long nowNanos, boolean attackStarted) {
        observeTime(nowNanos);
        attempts++;
        clicks.addLast(nowNanos);
        if (attackStarted) {
            started++;
            starts.addLast(nowNanos);
        }
        peakClicksPerSecond = Math.max(peakClicksPerSecond, clicks.size());
    }

    public void sampleMovement(long nowNanos, double x, double z) {
        observeTime(nowNanos);
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            clearMovementAnchor();
            recordHistory(nowNanos);
            return;
        }

        if (movementAnchored) {
            long elapsed = nowNanos - anchorNanos;
            double segment = Math.hypot(x - anchorX, z - anchorZ);
            if (elapsed > 0L && elapsed <= MAX_MOVEMENT_GAP_NANOS
                    && Double.isFinite(segment) && segment <= MAX_MOVEMENT_SEGMENT) {
                distance += segment;
                speedWindow.addLast(new MovementPoint(nowNanos, distance));
                updateSmoothedSpeed(nowNanos);
            } else {
                resetSpeedWindow(nowNanos);
            }
        } else {
            resetSpeedWindow(nowNanos);
        }

        anchorX = x;
        anchorZ = z;
        anchorNanos = lastMovementNanos = nowNanos;
        movementAnchored = true;
        recordHistory(nowNanos);
    }

    public void clearMovementAnchor() {
        movementAnchored = false;
        speed = 0.0;
        speedWindow.clear();
    }

    public void recordPositionSync() {
        positionSyncs++;
        clearMovementAnchor();
    }

    public Snapshot snapshot(long nowNanos) {
        observeTime(nowNanos);
        recordHistory(nowNanos);
        return new Snapshot(attempts, started, clicks.size(), starts.size(),
                peakClicksPerSecond, speed, peakSpeed, distance, positionSyncs,
                clickHistory, speedHistory);
    }

    private void observeTime(long nowNanos) {
        // nanoTime's origin may be negative. Only a backwards observation is anomalous.
        if (nowNanos < lastObservationNanos) {
            clicks.clear();
            starts.clear();
            clearMovementAnchor();
            lastHistoryNanos = nowNanos;
        }
        lastObservationNanos = nowNanos;
        prune(clicks, nowNanos);
        prune(starts, nowNanos);
        if (!movementAnchored || nowNanos - lastMovementNanos > SPEED_STALE_NANOS) {
            speed = 0.0;
        }
    }

    private static void prune(ArrayDeque<Long> times, long nowNanos) {
        while (!times.isEmpty() && nowNanos - times.getFirst() >= WINDOW_NANOS) {
            times.removeFirst();
        }
    }

    private void resetSpeedWindow(long nowNanos) {
        speedWindow.clear();
        speedWindow.addLast(new MovementPoint(nowNanos, distance));
        speed = 0.0;
    }

    private void updateSmoothedSpeed(long nowNanos) {
        // Client catch-up ticks can be only a millisecond apart. Always measure a
        // full wall-clock window instead of retaining a spike from one such tick.
        if (nowNanos - speedWindow.getFirst().nanos() < SPEED_WINDOW_NANOS) {
            speed = 0.0;
            return;
        }
        long boundaryNanos = nowNanos - SPEED_WINDOW_NANOS;
        while (speedWindow.size() > 1) {
            var points = speedWindow.iterator();
            points.next();
            if (points.next().nanos() > boundaryNanos) break;
            speedWindow.removeFirst();
        }

        var points = speedWindow.iterator();
        MovementPoint before = points.next();
        double boundaryDistance = before.distance();
        if (before.nanos() < boundaryNanos && points.hasNext()) {
            MovementPoint after = points.next();
            double portion = (double) (boundaryNanos - before.nanos())
                    / (after.nanos() - before.nanos());
            boundaryDistance += portion * (after.distance() - before.distance());
        }
        speed = Math.max(0.0, distance - boundaryDistance)
                / (SPEED_WINDOW_NANOS / 1_000_000_000.0);
        peakSpeed = Math.max(peakSpeed, speed);
    }

    private record MovementPoint(long nanos, double distance) {}

    private void recordHistory(long nowNanos) {
        if (nowNanos - lastHistoryNanos < HISTORY_INTERVAL_NANOS) return;
        // A long pause adds one observation, not fabricated catch-up samples.
        lastHistoryNanos = nowNanos;
        System.arraycopy(clickHistory, 1, clickHistory, 0, HISTORY_SIZE - 1);
        System.arraycopy(speedHistory, 1, speedHistory, 0, HISTORY_SIZE - 1);
        clickHistory[HISTORY_SIZE - 1] = clicks.size();
        speedHistory[HISTORY_SIZE - 1] = speed;
    }

    public record Snapshot(long attempts, long started, int clicksPerSecond,
                           int startsPerSecond, int peakClicksPerSecond, double speed,
                           double peakSpeed, double distance, long positionSyncs,
                           int[] clickHistory, double[] speedHistory) {
        public Snapshot {
            clickHistory = clickHistory.clone();
            speedHistory = speedHistory.clone();
        }

        @Override public int[] clickHistory() { return clickHistory.clone(); }
        @Override public double[] speedHistory() { return speedHistory.clone(); }
    }
}
