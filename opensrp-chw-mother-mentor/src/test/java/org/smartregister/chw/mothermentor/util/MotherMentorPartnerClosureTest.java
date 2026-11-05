package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MotherMentorPartnerClosureTest {
    private static final String FIRST_DAY = "2026-11-01";
    private static final String SECOND_DAY = "2026-11-02";
    private static final String THIRD_DAY = "2026-11-03";

    @Test
    public void activeStateHasNoClosureAndAdvancingDoesNotMutateItsPredecessor() {
        MotherMentorPartnerClosure initial = MotherMentorPartnerClosure.active();
        MotherMentorPartnerClosure next = initial.advance(found("refused"), FIRST_DAY);
        assertFalse(initial.isClosed());
        assertEquals(MotherMentorPartnerClosure.ACTIVE, initial.getStatus());
        assertEquals("", initial.getClosedDate());
        assertEquals("", initial.getClosureReason());
        assertCounters(initial, 0, 0, 0, 0, 0);
        assertCounters(next, 1, 0, 0, 0, 0);
    }

    @Test
    public void attendanceClosesForAnyOnePerformedTestWithEitherValidResult() {
        String[][] tests = {{PARTNER_HIV_TESTED, PARTNER_HIV_RESULT},
                {PARTNER_SYPHILIS_TESTED, PARTNER_SYPHILIS_RESULT},
                {PARTNER_HEPATITIS_B_TESTED, PARTNER_HEPATITIS_B_RESULT}};
        for (String[] test : tests) {
            for (String result : new String[] {"positive", "negative"}) {
                Map<String, String> values = attendance("yes");
                values.put(test[0], "yes");
                values.put(test[1], result);
                MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                        .advance(values, FIRST_DAY);
                assertClosed(state, MotherMentorPartnerClosure.ATTENDED_TESTED, FIRST_DAY);
            }
        }
    }

    @Test
    public void attendanceWithoutAPerformedTestAndValidResultStaysActive() {
        Map<String, String> values = attendance("yes");
        values.put(PARTNER_HIV_TESTED, "no");
        values.put(PARTNER_HIV_RESULT, "positive");
        values.put(PARTNER_SYPHILIS_TESTED, "no");
        values.put(PARTNER_HEPATITIS_B_TESTED, "no");
        assertFalse(MotherMentorPartnerClosure.active().advance(values, FIRST_DAY).isClosed());
        values.put(PARTNER_HIV_TESTED, "yes");
        values.put(PARTNER_HIV_RESULT, "unknown");
        assertFalse(MotherMentorPartnerClosure.active().advance(values, FIRST_DAY).isClosed());
        values.remove(PARTNER_HIV_RESULT);
        assertFalse(MotherMentorPartnerClosure.active().advance(values, FIRST_DAY).isClosed());
    }

    @Test
    public void deathAndMovingCloseImmediatelyAheadOfAnyCounter() {
        for (String reason : new String[] {"deceased", "moved"}) {
            MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                    .advance(notFound("travelled"), FIRST_DAY)
                    .advance(notFound("travelled"), SECOND_DAY)
                    .advance(notFound(reason), THIRD_DAY);
            assertClosed(state, reason, THIRD_DAY);
            assertCounters(state, 0, 0, 0, 0, 3);
        }
    }

    @Test
    public void thirdConsecutiveRefusalClosesButAgreementResetsTheWorkedExample() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active();
        state = state.advance(found("refused"), FIRST_DAY);
        assertFalse(state.isClosed());
        assertEquals(1, state.getRefusedCount());
        state = state.advance(found("refused"), SECOND_DAY);
        assertFalse(state.isClosed());
        assertEquals(2, state.getRefusedCount());
        state = state.advance(found("agreed"), THIRD_DAY);
        assertCounters(state, 0, 0, 0, 0, 0);
        state = state.advance(found("refused"), "2026-11-04");
        assertEquals(1, state.getRefusedCount());
        state = state.advance(found("refused"), "2026-11-05");
        assertFalse(state.isClosed());
        assertEquals(2, state.getRefusedCount());
        state = state.advance(found("refused"), "2026-11-06");
        assertClosed(state, MotherMentorPartnerClosure.REFUSED_3, "2026-11-06");
        assertCounters(state, 3, 0, 0, 0, 0);
    }

    @Test
    public void thirdConsecutiveNonAttendanceClosesAfterAgreement() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(found("agreed"), "2026-10-31")
                .advance(attendance("no"), FIRST_DAY)
                .advance(attendance("no"), SECOND_DAY);
        assertFalse(state.isClosed());
        assertCounters(state, 0, 2, 0, 0, 0);
        state = state.advance(attendance("no"), THIRD_DAY);
        assertClosed(state, MotherMentorPartnerClosure.NOT_ATTENDED_3, THIRD_DAY);
        assertCounters(state, 0, 3, 0, 0, 0);
    }

    @Test
    public void attendanceYesWithoutTestingResetsTheNonAttendanceStreak() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(attendance("no"), FIRST_DAY)
                .advance(attendance("no"), SECOND_DAY)
                .advance(attendance("yes"), THIRD_DAY);
        assertFalse(state.isClosed());
        assertCounters(state, 0, 0, 0, 0, 0);
        state = state.advance(attendance("no"), "2026-11-04");
        assertFalse(state.isClosed());
        assertEquals(1, state.getNonAttendanceCount());
    }

    @Test
    public void threeTravelledVisitsUseTheSpecificReasonInsteadOfOverallLtfu() {
        MotherMentorPartnerClosure state = repeat(notFound("travelled"));
        assertClosed(state, MotherMentorPartnerClosure.TRAVELLED_3, THIRD_DAY);
        assertCounters(state, 0, 0, 3, 0, 3);
    }

    @Test
    public void threeOtherVisitsUseTheSpecificReasonInsteadOfOverallLtfu() {
        MotherMentorPartnerClosure state = repeat(notFound("other"));
        assertClosed(state, MotherMentorPartnerClosure.OTHER_3, THIRD_DAY);
        assertCounters(state, 0, 0, 0, 3, 3);
    }

    @Test
    public void threeMixedNotFoundVisitsCloseAsOverallLtfu() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(notFound("travelled"), FIRST_DAY)
                .advance(notFound("other"), SECOND_DAY);
        assertFalse(state.isClosed());
        assertCounters(state, 0, 0, 0, 1, 2);
        state = state.advance(notFound("travelled"), THIRD_DAY);
        assertClosed(state, MotherMentorPartnerClosure.LOST_TO_FOLLOW_UP, THIRD_DAY);
        assertCounters(state, 0, 0, 1, 0, 3);
    }

    @Test
    public void foundOrAttendanceResetsOverallNotFoundAndDifferentIndividualCounters() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(notFound("travelled"), FIRST_DAY)
                .advance(notFound("other"), SECOND_DAY)
                .advance(found("refused"), THIRD_DAY);
        assertCounters(state, 1, 0, 0, 0, 0);
        state = state.advance(notFound("travelled"), "2026-11-04");
        assertCounters(state, 0, 0, 1, 0, 1);
        state = state.advance(attendance("no"), "2026-11-05");
        assertCounters(state, 0, 1, 0, 0, 0);
        state = state.advance(found("agreed"), "2026-11-06");
        assertFalse(state.isClosed());
        assertCounters(state, 0, 0, 0, 0, 0);
    }

    @Test
    public void commentsDoNotGateCountersOrClosure() {
        Map<String, String> values = found("refused");
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(values, FIRST_DAY);
        values.put(COMMENTS, "");
        state = state.advance(values, SECOND_DAY);
        values.put(COMMENTS, "Follow-up notes");
        state = state.advance(values, THIRD_DAY);
        assertClosed(state, MotherMentorPartnerClosure.REFUSED_3, THIRD_DAY);
    }

    @Test
    public void closedStateCannotBeReopenedByLaterEvidenceFromTheSameLinkage() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(notFound("deceased"), FIRST_DAY);
        assertSame(state, state.advance(found("agreed"), SECOND_DAY));
        assertClosed(state, MotherMentorPartnerClosure.DECEASED, FIRST_DAY);
    }

    @Test
    public void unrecognizedEvidenceDoesNotIncrementOrResetAStreak() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active()
                .advance(found("refused"), FIRST_DAY);
        assertSame(state, state.advance(new HashMap<>(), SECOND_DAY));
        assertSame(state, state.advance(found("already_attended"), SECOND_DAY));
        assertSame(state, state.advance(notFound("unknown"), SECOND_DAY));
        assertSame(state, state.advance(attendance("unknown"), SECOND_DAY));
        assertCounters(state, 1, 0, 0, 0, 0);
    }

    @Test
    public void legacyCompletionRemainsGenericAndTerminalWithoutClaimingTestResults() {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.legacyCompleted(FIRST_DAY);
        assertClosed(state, "completed", FIRST_DAY);
        assertCounters(state, 0, 0, 0, 0, 0);
        assertSame(state, state.advance(found("refused"), SECOND_DAY));
    }

    private MotherMentorPartnerClosure repeat(Map<String, String> values) {
        return MotherMentorPartnerClosure.active().advance(values, FIRST_DAY)
                .advance(values, SECOND_DAY).advance(values, THIRD_DAY);
    }

    private Map<String, String> found(String agreement) {
        Map<String, String> values = new HashMap<>();
        values.put(PARTNER_FOLLOW_UP_PHASE, "tracing");
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(PARTNER_FACILITY_AGREEMENT, agreement);
        return values;
    }

    private Map<String, String> notFound(String reason) {
        Map<String, String> values = new HashMap<>();
        values.put(PARTNER_FOLLOW_UP_PHASE, "tracing");
        values.put(FOLLOW_UP_STATUS, "not_found");
        values.put(PARTNER_NOT_FOUND_REASON, reason);
        return values;
    }

    private Map<String, String> attendance(String answer) {
        Map<String, String> values = new HashMap<>();
        values.put(PARTNER_FOLLOW_UP_PHASE, "attendance");
        values.put(PARTNER_ARRIVED_AT_FACILITY, answer);
        return values;
    }

    private void assertClosed(MotherMentorPartnerClosure state, String status, String date) {
        assertTrue(state.isClosed());
        assertEquals(status, state.getStatus());
        assertEquals(status, state.getClosureReason());
        assertEquals(date, state.getClosedDate());
    }

    private void assertCounters(MotherMentorPartnerClosure state, int refused, int nonAttendance,
            int travelled, int other, int notFound) {
        assertEquals(refused, state.getRefusedCount());
        assertEquals(nonAttendance, state.getNonAttendanceCount());
        assertEquals(travelled, state.getTravelledCount());
        assertEquals(other, state.getOtherCount());
        assertEquals(notFound, state.getNotFoundCount());
    }
}
