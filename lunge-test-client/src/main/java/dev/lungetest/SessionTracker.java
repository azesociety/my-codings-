package dev.lungetest;

/** Counts active play time and horizontal travel, dropping teleports, pauses and long gaps. */
public final class SessionTracker {
    private long activeNanos, anchorNanos;
    private double distance, x, z;
    private boolean anchored;
    public long seconds() { return activeNanos / 1_000_000_000L; }
    public double distance() { return distance; }
    public void reset() { activeNanos = 0; distance = 0; clearAnchor(); }
    public void clearAnchor() { anchored = false; }
    public void sample(long now, double nextX, double nextZ) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextZ)) { clearAnchor(); return; }
        if (anchored) {
            long gap = now - anchorNanos;
            if (gap > 0 && gap <= 2_000_000_000L) {
                activeNanos += gap;
                double segment = Math.hypot(nextX - x, nextZ - z);
                if (Double.isFinite(segment) && segment <= 32) distance += segment;
            }
        }
        x = nextX; z = nextZ; anchorNanos = now; anchored = true;
    }
}
