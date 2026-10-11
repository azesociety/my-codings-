package dev.lungetest;

/** Monotonic retry timing; a failed attempt must explicitly schedule its successor. */
public final class RejoinSchedule {
    public static final long DELAY_NANOS = 5_000_000_000L;
    public static final int MAX_ATTEMPTS = 6;
    private long due;
    private int attempts;
    private boolean pending;
    public void schedule(long now) {
        if (!pending && attempts < MAX_ATTEMPTS) { due = now + DELAY_NANOS; pending = true; }
    }
    public boolean poll(long now) {
        if (!pending || now - due < 0) return false;
        pending = false;
        attempts++;
        return true;
    }
    public void reset() { attempts = 0; pending = false; }
    public int attempts() { return attempts; }
    public boolean pending() { return pending; }
    public long remainingSeconds(long now) { return pending ? Math.max(0, (due - now + 999_999_999L) / 1_000_000_000L) : 0; }
}
