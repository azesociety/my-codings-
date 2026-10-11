package dev.lungetest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class JUnitRejoinScheduleVerification {
    @Test void waitsFiveSecondsAndCannotDispatchTwiceOnOneFailure() {
        var p = new RejoinSchedule(); p.schedule(100);
        assertFalse(p.poll(100 + RejoinSchedule.DELAY_NANOS - 1));
        assertTrue(p.poll(100 + RejoinSchedule.DELAY_NANOS));
        assertFalse(p.poll(100 + RejoinSchedule.DELAY_NANOS + 1));
        assertEquals(1, p.attempts());
    }
    @Test void slowClientFramesDoNotBurstRetriesAndAnExplicitFailureStartsANewDelay() {
        var p = new RejoinSchedule(); p.schedule(0);
        long late = RejoinSchedule.DELAY_NANOS * 10;
        assertTrue(p.poll(late)); assertFalse(p.poll(late));
        p.schedule(late);
        assertFalse(p.poll(late + RejoinSchedule.DELAY_NANOS - 1));
        assertTrue(p.poll(late + RejoinSchedule.DELAY_NANOS));
    }
    @Test void capsRetriesAndResetsAfterSuccessOrCancel() {
        var p = new RejoinSchedule(); long now = 0;
        for (int i = 0; i < RejoinSchedule.MAX_ATTEMPTS; i++) {
            p.schedule(now); now += RejoinSchedule.DELAY_NANOS; assertTrue(p.poll(now));
        }
        p.schedule(now); assertFalse(p.pending()); assertFalse(p.poll(Long.MAX_VALUE));
        p.reset(); assertEquals(0, p.attempts()); p.schedule(now);
        p.reset(); assertFalse(p.poll(now + RejoinSchedule.DELAY_NANOS));
        p.schedule(now); assertTrue(p.poll(now + RejoinSchedule.DELAY_NANOS));
    }
    @Test void countdownStaysStableOnRepeatedFrames() {
        var p = new RejoinSchedule(); p.schedule(0); p.schedule(4_000_000_000L);
        assertEquals(1, p.remainingSeconds(4_999_999_999L));
        assertTrue(p.poll(5_000_000_000L));
        assertEquals(0, p.remainingSeconds(5_000_000_001L));
    }
}
