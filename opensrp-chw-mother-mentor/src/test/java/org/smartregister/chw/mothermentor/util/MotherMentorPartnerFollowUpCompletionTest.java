package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MotherMentorPartnerFollowUpCompletionTest {
    @Test
    public void tracingFoundRequiresAgreementAndEducationButNoPrematureAttendanceQuestions() {
        Map<String, String> values = tracing("found");
        assertFalse(complete(values));
        values.put(PARTNER_FACILITY_AGREEMENT, "agreed");
        assertFalse(complete(values));
        values.put(PARTNER_EDUCATION_PROVIDED, "no");
        assertTrue(complete(values));
        values.put(PARTNER_FACILITY_AGREEMENT, "refused");
        assertTrue(complete(values));
    }

    @Test
    public void notFoundRequiresOnlyARecognizedReasonAndOtherExplanationWhenApplicable() {
        Map<String, String> values = tracing("not_found");
        assertFalse(complete(values));
        for (String reason : new String[] {"deceased", "moved", "travelled"}) {
            values.put(PARTNER_NOT_FOUND_REASON, reason);
            assertTrue(complete(values));
        }
        values.put(PARTNER_NOT_FOUND_REASON, "other");
        assertFalse(complete(values));
        values.put(PARTNER_NOT_FOUND_REASON_OTHER, "Away temporarily");
        assertTrue(complete(values));
    }

    @Test
    public void attendanceNoCanCompleteWithoutTestsButYesRequiresAllThreeTestBranches() {
        Map<String, String> values = attendance();
        assertTrue(complete(values));
        values.put(PARTNER_ARRIVED_AT_FACILITY, "yes");
        assertFalse(complete(values));
        values.put(PARTNER_FACILITY_ARRIVAL_DATE, "28-10-2026");
        tested(values, PARTNER_HIV_TESTED, PARTNER_HIV_RESULT);
        tested(values, PARTNER_SYPHILIS_TESTED, PARTNER_SYPHILIS_RESULT);
        assertFalse(complete(values));
        tested(values, PARTNER_HEPATITIS_B_TESTED, PARTNER_HEPATITIS_B_RESULT);
        assertTrue(complete(values));
        values.put(PARTNER_HEPATITIS_B_RESULT, "unknown");
        assertFalse(complete(values));
    }

    @Test
    public void notTestedAcceptsHivAlreadyInfectedButRequiresOtherDiseaseReasonsAndOtherText() {
        Map<String, String> values = attendance();
        values.put(PARTNER_ARRIVED_AT_FACILITY, "yes");
        values.put(PARTNER_FACILITY_ARRIVAL_DATE, "2026-10-28");
        values.put(PARTNER_HIV_TESTED, "no");
        values.put(PARTNER_HIV_NOT_TESTED_REASON, "already_infected");
        values.put(PARTNER_SYPHILIS_TESTED, "no");
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON, "stock_out");
        values.put(PARTNER_HEPATITIS_B_TESTED, "no");
        values.put(PARTNER_HEPATITIS_B_NOT_TESTED_REASON, "refused");
        assertTrue(complete(values));
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON, "already_infected");
        assertFalse(complete(values));
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON, "other");
        assertFalse(complete(values));
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER, "Test unavailable");
        assertTrue(complete(values));
        values.put(PARTNER_FACILITY_ARRIVAL_DATE, "31-02-2026");
        assertFalse(complete(values));
    }

    @Test
    public void educationYesRequiresAllowedTopicsAndCommentsRemainOptional() {
        Map<String, String> values = attendance();
        values.put(PARTNER_EDUCATION_PROVIDED, "yes");
        assertFalse(complete(values));
        values.put(PARTNER_EDUCATION_TOPICS, "[\"hiv\",\"hepatitis_b\"]");
        assertTrue(complete(values));
        values.put(PARTNER_EDUCATION_TOPICS, "hiv,unknown");
        assertFalse(complete(values));
        values.put(PARTNER_EDUCATION_TOPICS, "hiv,");
        assertFalse(complete(values));
    }

    private Map<String, String> tracing(String status) {
        Map<String, String> values = new HashMap<>();
        values.put(PARTNER_FOLLOW_UP_SCHEMA, "2");
        values.put(PARTNER_FOLLOW_UP_PHASE, "tracing");
        values.put(FOLLOW_UP_STATUS, status);
        return values;
    }

    private Map<String, String> attendance() {
        Map<String, String> values = new HashMap<>();
        values.put(PARTNER_FOLLOW_UP_SCHEMA, "2");
        values.put(PARTNER_FOLLOW_UP_PHASE, "attendance");
        values.put(PARTNER_ARRIVED_AT_FACILITY, "no");
        values.put(PARTNER_EDUCATION_PROVIDED, "no");
        return values;
    }

    private void tested(Map<String, String> values, String tested, String result) {
        values.put(tested, "yes");
        values.put(result, "negative");
    }

    private boolean complete(Map<String, String> values) {
        return MotherMentorPartnerFollowUpVisitUtils.isComplete(values, true);
    }
}
