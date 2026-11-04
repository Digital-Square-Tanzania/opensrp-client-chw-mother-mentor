package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;

public class MotherMentorMotherClosureTest {
    private static final String FIRST_DAY = "2026-11-01";
    private static final String SECOND_DAY = "2026-11-02";
    private static final String THIRD_DAY = "2026-11-03";

    @Test
    public void aNewEnrollmentHasNoClosureAndAdvancingDoesNotMutateIt() {
        MotherMentorMotherClosure initial = MotherMentorMotherClosure.active();
        MotherMentorMotherClosure next = initial.advance(VisitOutcome.NOT_FOUND_OTHER, FIRST_DAY);
        assertActive(initial, 0);
        assertActive(next, 1);
    }

    @Test
    public void eachImmediateExitClosesOnItsFirstVisitWithItsOwnReason() {
        VisitOutcome[] outcomes = {
            VisitOutcome.DECEASED,
            VisitOutcome.MOVED_AREA,
            VisitOutcome.MOVED_FACILITY,
            VisitOutcome.PREGNANCY_LOSS,
            VisitOutcome.STILLBIRTH,
            VisitOutcome.PREGNANCY_ENDED_OTHER,
            VisitOutcome.CHILD_DECEASED,
            VisitOutcome.CHILD_CONFIRMED_POSITIVE,
            VisitOutcome.GRADUATED
        };
        String[] statuses = {
            "deceased", "moved_area", "moved_facility", "pregnancy_loss", "stillbirth",
            "pregnancy_ended_other", "child_deceased", "child_confirmed_positive", "graduated"
        };
        for (int index = 0; index < outcomes.length; index++) {
            MotherMentorMotherClosure state = MotherMentorMotherClosure.active()
                    .advance(outcomes[index], FIRST_DAY);
            assertClosed(state, statuses[index], FIRST_DAY);
            assertEquals(0, state.getOtherCount());
        }
    }

    @Test
    public void onlyTheThirdConsecutiveNotFoundOtherVisitClosesAsA4() {
        MotherMentorMotherClosure state = MotherMentorMotherClosure.active()
                .advance(VisitOutcome.NOT_FOUND_OTHER, FIRST_DAY);
        assertActive(state, 1);
        state = state.advance(VisitOutcome.NOT_FOUND_OTHER, SECOND_DAY);
        assertActive(state, 2);
        state = state.advance(VisitOutcome.NOT_FOUND_OTHER, THIRD_DAY);
        assertClosed(state, MotherMentorMotherClosure.NOT_FOUND_OTHER_3, THIRD_DAY);
        assertEquals(3, state.getOtherCount());
    }

    @Test
    public void foundVisitResetsTheDocumentedSixVisitSequence() {
        MotherMentorMotherClosure state = MotherMentorMotherClosure.active()
                .advance(VisitOutcome.NOT_FOUND_OTHER, FIRST_DAY)
                .advance(VisitOutcome.NOT_FOUND_OTHER, SECOND_DAY);
        assertActive(state, 2);
        state = state.advance(VisitOutcome.CONTINUE, THIRD_DAY);
        assertActive(state, 0);
        state = state.advance(VisitOutcome.NOT_FOUND_OTHER, "2026-11-04");
        assertActive(state, 1);
        state = state.advance(VisitOutcome.NOT_FOUND_OTHER, "2026-11-05");
        assertActive(state, 2);
        state = state.advance(VisitOutcome.NOT_FOUND_OTHER, "2026-11-06");
        assertClosed(state, MotherMentorMotherClosure.NOT_FOUND_OTHER_3, "2026-11-06");
        assertEquals(3, state.getOtherCount());
    }

    @Test
    public void everyOtherValidOutcomeResetsTheSingleCounter() {
        for (VisitOutcome outcome : VisitOutcome.values()) {
            if (outcome == VisitOutcome.NOT_FOUND_OTHER) continue;
            MotherMentorMotherClosure state = twoMisses().advance(outcome, THIRD_DAY);
            assertEquals(outcome.name(), 0, state.getOtherCount());
            if (outcome == VisitOutcome.CONTINUE) assertActive(state, 0);
            else assertTrue(state.isClosed());
        }
    }

    @Test
    public void everyClosedStateKeepsItsReasonDateAndCounterForTheRestOfItsEnrollment() {
        for (VisitOutcome exit : VisitOutcome.values()) {
            if (exit == VisitOutcome.CONTINUE) continue;
            MotherMentorMotherClosure state = exit == VisitOutcome.NOT_FOUND_OTHER
                    ? twoMisses().advance(exit, THIRD_DAY)
                    : MotherMentorMotherClosure.active().advance(exit, THIRD_DAY);
            assertTrue(state.isClosed());
            String status = state.getStatus();
            int count = state.getOtherCount();
            for (VisitOutcome later : VisitOutcome.values()) {
                assertSame(state, state.advance(later, "2026-12-01"));
                assertClosed(state, status, THIRD_DAY);
                assertEquals(count, state.getOtherCount());
            }
        }
    }

    @Test
    public void replayingTheSameOrderedEpisodeFromActiveProducesTheSameSummary() {
        VisitOutcome[] outcomes = {
            VisitOutcome.NOT_FOUND_OTHER, VisitOutcome.CONTINUE, VisitOutcome.NOT_FOUND_OTHER,
            VisitOutcome.NOT_FOUND_OTHER, VisitOutcome.NOT_FOUND_OTHER, VisitOutcome.GRADUATED
        };
        MotherMentorMotherClosure first = replay(outcomes);
        MotherMentorMotherClosure again = replay(outcomes);
        assertClosed(first, MotherMentorMotherClosure.NOT_FOUND_OTHER_3, "2026-11-05");
        assertEquals(first.getStatus(), again.getStatus());
        assertEquals(first.getClosureReason(), again.getClosureReason());
        assertEquals(first.getClosedDate(), again.getClosedDate());
        assertEquals(first.getOtherCount(), again.getOtherCount());
    }

    @Test
    public void aFreshEnrollmentDoesNotInheritAnEarlierClosureOrCounter() {
        MotherMentorMotherClosure previous = twoMisses()
                .advance(VisitOutcome.NOT_FOUND_OTHER, THIRD_DAY);
        MotherMentorMotherClosure next = MotherMentorMotherClosure.active()
                .advance(VisitOutcome.NOT_FOUND_OTHER, "2026-12-01");
        assertActive(next, 1);
        assertClosed(previous, MotherMentorMotherClosure.NOT_FOUND_OTHER_3, THIRD_DAY);
        assertEquals(3, previous.getOtherCount());
    }

    @Test
    public void invalidEvidenceDoesNotIncrementOrResetTheCounter() {
        MotherMentorMotherClosure state = twoMisses();
        assertSame(state, state.advance(null, THIRD_DAY));
        assertActive(state, 2);
    }

    @Test
    public void theClosingOtherExplanationAndTransferFacilityRemainAttachedToTheirOutcome() {
        MotherMentorMotherClosure other = twoMisses().advance(
                VisitOutcome.NOT_FOUND_OTHER, THIRD_DAY, "Household could not identify her", "");
        assertEquals("Household could not identify her", other.getOtherReason());
        assertSame(other, other.advance(VisitOutcome.CONTINUE, "2026-12-01", "changed", "changed"));
        MotherMentorMotherClosure transfer = MotherMentorMotherClosure.active().advance(
                VisitOutcome.MOVED_FACILITY, THIRD_DAY, "", "Receiving facility");
        assertEquals("Receiving facility", transfer.getNewFacility());
        assertEquals("", transfer.getOtherReason());
    }

    @Test
    public void anOrdinaryVisitClearsAnEarlierOpenOtherExplanation() {
        MotherMentorMotherClosure first = MotherMentorMotherClosure.active().advance(
                VisitOutcome.NOT_FOUND_OTHER, FIRST_DAY, "Not at home", "");
        assertEquals("Not at home", first.getOtherReason());
        MotherMentorMotherClosure found = first.advance(VisitOutcome.CONTINUE, SECOND_DAY);
        assertActive(found, 0);
        assertEquals("", found.getOtherReason());
        assertEquals("", found.getNewFacility());
    }

    private MotherMentorMotherClosure twoMisses() {
        return MotherMentorMotherClosure.active()
                .advance(VisitOutcome.NOT_FOUND_OTHER, FIRST_DAY)
                .advance(VisitOutcome.NOT_FOUND_OTHER, SECOND_DAY);
    }

    private MotherMentorMotherClosure replay(VisitOutcome[] outcomes) {
        MotherMentorMotherClosure state = MotherMentorMotherClosure.active();
        for (int index = 0; index < outcomes.length; index++) {
            state = state.advance(outcomes[index], "2026-11-0" + (index + 1));
        }
        return state;
    }

    private void assertActive(MotherMentorMotherClosure state, int count) {
        assertFalse(state.isClosed());
        assertEquals(MotherMentorMotherClosure.ACTIVE, state.getStatus());
        assertEquals("", state.getClosureReason());
        assertEquals("", state.getClosedDate());
        assertEquals(count, state.getOtherCount());
    }

    private void assertClosed(MotherMentorMotherClosure state, String reason, String date) {
        assertTrue(state.isClosed());
        assertEquals(reason, state.getStatus());
        assertEquals(reason, state.getClosureReason());
        assertEquals(date, state.getClosedDate());
    }
}
