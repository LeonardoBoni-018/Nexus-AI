package com.nexusai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexusai.actions.ProposedAction;
import com.nexusai.actions.ProposedAction.Status;
import com.nexusai.common.DateRange;
import com.nexusai.common.RateLimiter;
import com.nexusai.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainRulesTest {

    private static ProposedAction draft() {
        return new ProposedAction(UUID.randomUUID(), UUID.randomUUID(), "x", UUID.randomUUID(), false, null, Instant.now());
    }

    @Test
    void validTransitionsFollowTheStateMachine() {
        ProposedAction a = draft();
        a.transitionTo(Status.PENDING_APPROVAL, Instant.now());
        a.transitionTo(Status.APPROVED, Instant.now());
        a.transitionTo(Status.EXECUTED, Instant.now());
        assertEquals(Status.EXECUTED, a.getStatus());
        assertTrue(a.getExecutedAt() != null);
    }

    @Test
    void invalidTransitionsAreRejected() {
        ProposedAction a = draft();
        assertThrows(ApiException.class, () -> a.transitionTo(Status.APPROVED, Instant.now()));
        assertThrows(ApiException.class, () -> a.transitionTo(Status.EXECUTED, Instant.now()));
        a.transitionTo(Status.CANCELLED, Instant.now());
        for (Status s : Status.values()) {
            assertFalse(a.canTransitionTo(s), "estado terminal nao deve transitar para " + s);
        }
    }

    @Test
    void dateRangePreviousHasSameLengthAndEndsDayBefore() {
        DateRange r = DateRange.of(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 14), 400);
        assertEquals(7, r.days());
        DateRange p = r.previous();
        assertEquals(LocalDate.of(2026, 3, 1), p.from());
        assertEquals(LocalDate.of(2026, 3, 7), p.to());
        assertEquals(7, p.days());
    }

    @Test
    void dateRangeRejectsInvertedAndOversizedPeriods() {
        assertThrows(ApiException.class, () -> DateRange.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1), 400));
        assertThrows(ApiException.class, () -> DateRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 3, 1), 400));
        assertEquals(1, DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), 400).days());
    }

    @Test
    void rateLimiterBlocksAfterLimitAndRecoversAfterWindow() {
        long[] now = {0};
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now[0]); }
        };
        RateLimiter limiter = new RateLimiter(2, 1000, clock);
        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("other"));
        now[0] = 1500;
        assertTrue(limiter.tryAcquire("k"));
    }
}
