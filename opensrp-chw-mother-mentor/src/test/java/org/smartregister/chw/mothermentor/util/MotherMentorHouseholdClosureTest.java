package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MotherMentorHouseholdClosureTest {
    private static final String DATE = "2026-11-04";

    @Test
    public void activeHouseholdHasNoClosureOrStrikes() {
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        assertFalse(state.isClosed());
        assertEquals("active", state.getStatus());
        assertEquals("", state.getClosureReason());
        assertEquals("", state.getClosedDate());
        assertCounts(state, 0, 0, 0);
    }

    @Test
    public void movedClosesOnTheFirstFinalizedVisit() {
        MotherMentorHouseholdClosure state = missing(MotherMentorHouseholdClosure.active(), "moved");
        assertTrue(state.isClosed());
        assertEquals("moved", state.getStatus());
        assertEquals("moved", state.getClosureReason());
        assertEquals(DATE, state.getClosedDate());
        assertCounts(state, 0, 0, 1);
    }

    @Test
    public void threeHouseClosedVisitsUseTheSpecificReasonBeforeOverallLtfu() {
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        state = missing(state, "house_closed");
        assertFalse(state.isClosed());
        assertCounts(state, 1, 0, 1);
        state = missing(state, "house_closed");
        assertFalse(state.isClosed());
        assertCounts(state, 2, 0, 2);
        state = state.advance("repeat", "not_found", "house_closed", "2026-11-06");
        assertEquals("house_closed_3", state.getStatus());
        assertEquals("2026-11-06", state.getClosedDate());
        assertCounts(state, 3, 0, 3);
    }

    @Test
    public void threeOtherVisitsUseTheSpecificReasonBeforeOverallLtfu() {
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        for (int visit = 1; visit <= 3; visit++) {
            state = missing(state, "other");
            assertCounts(state, 0, visit, visit);
            assertEquals(visit == 3, state.isClosed());
        }
        assertEquals("other_3", state.getStatus());
        assertEquals("other_3", state.getClosureReason());
    }

    @Test
    public void differentReasonsResetOnlyTheirOwnCountersWhileOverallContinues() {
        MotherMentorHouseholdClosure state = missing(MotherMentorHouseholdClosure.active(), "house_closed");
        state = missing(state, "other");
        assertFalse(state.isClosed());
        assertCounts(state, 0, 1, 2);
        state = missing(state, "house_closed");
        assertEquals("lost_to_follow_up", state.getStatus());
        assertCounts(state, 1, 0, 3);
    }

    @Test
    public void foundResetsAllCountersAndIgnoresAnyStaleReason() {
        MotherMentorHouseholdClosure state = missing(MotherMentorHouseholdClosure.active(), "house_closed");
        state = missing(state, "other");
        state = state.advance("repeat", "found", "moved", DATE);
        assertFalse(state.isClosed());
        assertCounts(state, 0, 0, 0);
        assertEquals("", state.getClosedDate());
    }

    @Test
    public void newRegistrationResetsAllCountersAndIgnoresAnyStaleStatusOrReason() {
        MotherMentorHouseholdClosure state = missing(MotherMentorHouseholdClosure.active(), "house_closed");
        state = missing(state, "other");
        state = state.advance("new", "not_found", "moved", DATE);
        assertFalse(state.isClosed());
        assertCounts(state, 0, 0, 0);
    }

    @Test
    public void workedExampleStartsAFreshSequenceAfterFound() {
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        state = missing(state, "house_closed");
        state = missing(state, "house_closed");
        state = state.advance("repeat", "found", "", "2026-11-03");
        assertCounts(state, 0, 0, 0);
        state = state.advance("repeat", "not_found", "house_closed", "2026-11-04");
        assertCounts(state, 1, 0, 1);
        state = state.advance("repeat", "not_found", "house_closed", "2026-11-05");
        assertFalse(state.isClosed());
        assertCounts(state, 2, 0, 2);
        state = state.advance("repeat", "not_found", "house_closed", "2026-11-06");
        assertEquals("house_closed_3", state.getStatus());
        assertEquals("2026-11-06", state.getClosedDate());
    }

    @Test
    public void incompleteOrUnknownAnswersCannotIncrementOrResetValidCounters() {
        MotherMentorHouseholdClosure state = missing(MotherMentorHouseholdClosure.active(), "house_closed");
        for (String[] answers : new String[][] {
                {null, "found", ""}, {"", "found", ""}, {"other", "found", ""},
                {"repeat", "", ""}, {"repeat", null, "house_closed"},
                {"repeat", "not_found", ""}, {"repeat", "not_found", "unknown"},
                {"repeat", "unknown", "house_closed"}}) {
            assertSame(state, state.advance(answers[0], answers[1], answers[2], DATE));
        }
        assertCounts(state, 1, 0, 1);
    }

    @Test
    public void closedStateCannotBeChangedByALaterVisitOrNewRegistrationAnswer() {
        MotherMentorHouseholdClosure closed = missing(MotherMentorHouseholdClosure.active(), "moved");
        assertSame(closed, missing(closed, "other"));
        assertSame(closed, closed.advance("repeat", "found", "", "2026-11-05"));
        assertSame(closed, closed.advance("new", "", "", "2026-11-05"));
        assertEquals(DATE, closed.getClosedDate());
    }

    @Test
    public void newEnrolmentStartsWithItsOwnActiveState() {
        MotherMentorHouseholdClosure old = missing(MotherMentorHouseholdClosure.active(), "moved");
        MotherMentorHouseholdClosure current = missing(MotherMentorHouseholdClosure.active(), "house_closed");
        assertTrue(old.isClosed());
        assertFalse(current.isClosed());
        assertCounts(current, 1, 0, 1);
    }

    private MotherMentorHouseholdClosure missing(MotherMentorHouseholdClosure state, String reason) {
        return state.advance("repeat", "not_found", reason, DATE);
    }

    private void assertCounts(MotherMentorHouseholdClosure state, int houseClosed, int other, int missing) {
        assertEquals(houseClosed, state.getHouseClosedCount());
        assertEquals(other, state.getOtherCount());
        assertEquals(missing, state.getNotFoundCount());
    }
}
