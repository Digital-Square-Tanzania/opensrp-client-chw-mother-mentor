package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

public class MotherMentorChildEidHeiUtilsTest {
    private final LocalDate day = new LocalDate(2026, 10, 31);
    private final LocalDate birth = day.minusMonths(10);

    @Test
    public void firstAgreementAndRefusalRequireOnlyBreastfeedingAndValidReferralSnapshot() {
        for (String agreement : new String[] {"agreed", "refused"}) {
            Map<String, String> values = first(agreement);
            assertTrue(complete(values, "none"));
            for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) {
                values.put(key, "stale");
                assertFalse(key, complete(values, "none"));
                values.remove(key);
            }
        }
    }

    @Test
    public void missingOrInventedReferralCannotCompleteTheFirstAssessment() {
        Map<String, String> values = first("agreed");
        values.remove(MTOTO_REFERRAL_AGREEMENT);
        assertFalse(complete(values, "none"));
        values = first("agreed"); values.put(MTOTO_RUFAA_TEST, "wiki6");
        assertFalse(complete(values, "none"));
        values = first("agreed"); values.put(MTOTO_RUFAA_DUE, birth.plusMonths(9).plusDays(1).toString());
        assertFalse(complete(values, "none"));
    }

    @Test
    public void missingUnknownOrMismatchedBranchSnapshotCannotBeAssumedToMeanFirstVisit() {
        for (String state : new String[] {"", "closed", "referred"}) {
            Map<String, String> values = first("agreed");
            values.put(MTOTO_RUFAA_STATE_BEFORE, state);
            assertFalse(state, complete(values, "none"));
        }
    }

    @Test
    public void simultaneousNineMonthAndWeaningReferralUsesStableStageOrder() {
        Map<String, String> values = first("agreed");
        values.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, birth.plusMonths(4).toString());
        assertTrue(complete(values, "none"));
        values.put(MTOTO_RUFAA_TEST, "miezi15");
        values.put(MTOTO_RUFAA_DUE, birth.plusMonths(7).toString());
        assertFalse(complete(values, "none"));
    }

    @Test
    public void cessationMustBeWithinTheChildsLifeAndNotAfterTheClinicalVisit() {
        Map<String, String> values = first("agreed");
        values.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        for (String date : new String[] {"", "2026-02-30", birth.minusDays(1).toString(), day.plusDays(1).toString()}) {
            values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, date);
            assertFalse(date, complete(values, "none"));
        }
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, birth.plusMonths(4).toString());
        assertTrue(complete(values, "none"));
    }

    @Test
    public void breastfeedingCannotCarryAnInactiveCessationDate() {
        Map<String, String> values = first("agreed");
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, birth.plusMonths(4).toString());
        assertFalse(complete(values, "none"));
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "");
        assertTrue(complete(values, "none"));
    }

    @Test
    public void missingDobOrFutureClinicalDayCannotBeNormalizedIntoEligibility() {
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(first("agreed"), null, day,
                Collections.emptyMap(), "none"));
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(first("agreed"), birth,
                LocalDate.now().plusDays(1), Collections.emptyMap(), "none"));
    }

    @Test
    public void noEligibleTestAllowsReconfirmationWithoutFabricatingAReferral() {
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "none");
        Map<Stage, TestRecord> tests = new HashMap<>();
        tests.put(Stage.NINE_MONTHS, new TestRecord("negative", day.minusDays(1), false, day));
        assertTrue(MotherMentorChildEidHeiUtils.isComplete(values, birth, day, tests, "none"));
        values.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(values, birth, day, tests, "none"));
    }

    @Test
    public void existingReturnVisitKeepsAttendanceQuestionsWithoutRepeatingReferral() {
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "referred");
        values.put(CHILD_EID_TEST_TYPE, "nine_months");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "no");
        assertTrue(complete(values, "referred"));
        values.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
        assertFalse(complete(values, "referred"));
        values.remove(MTOTO_REFERRAL_AGREEMENT);
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        assertFalse(complete(values, "referred"));
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, day.toString());
        assertTrue(complete(values, "referred"));
    }

    @Test
    public void returnCannotSelectAnEarlierSupersededTest() {
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "referred");
        values.put(CHILD_EID_TEST_TYPE, "six_weeks");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "no");
        assertFalse(complete(values, "referred"));
    }

    @Test
    public void pendingResultsCanBeReviewedWithoutOfferingAnotherSample() {
        Map<Stage, TestRecord> tests = new HashMap<>();
        tests.put(Stage.NINE_MONTHS, new TestRecord("pending", day.minusDays(1), false, day));
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "referred");
        values.put(CHILD_EID_TEST_TYPE, "nine_months");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, day.toString());
        values.put(MTOTO_RUFAA_STATE_BEFORE, "awaiting_result");
        assertTrue(MotherMentorChildEidHeiUtils.isComplete(values, birth, day, tests, "awaiting_result"));
        assertTrue(MotherMentorChildEidHeiUtils.eligible(values, birth, tests, day).isEmpty());
    }

    @Test
    public void existingFinalTestReturnRequiresAValidEighteenMonthResultWhenDone() {
        LocalDate olderBirth = day.minusMonths(20);
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "referred");
        values.put(CHILD_EID_TEST_TYPE, "eighteen_months");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, day.toString());
        values.put(CHILD_EID_FINAL_HIV_TEST_DONE, "no");
        assertTrue(MotherMentorChildEidHeiUtils.isComplete(values, olderBirth, day,
                Collections.emptyMap(), "referred"));
        values.put(CHILD_EID_FINAL_HIV_TEST_DONE, "yes");
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(values, olderBirth, day,
                Collections.emptyMap(), "referred"));
        values.put(CHILD_EID_FINAL_HIV_RESULT, "negative");
        values.put(CHILD_EID_FINAL_HIV_TEST_DATE, olderBirth.plusMonths(18).minusDays(1).toString());
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(values, olderBirth, day,
                Collections.emptyMap(), "referred"));
        values.put(CHILD_EID_FINAL_HIV_TEST_DATE, olderBirth.plusMonths(18).toString());
        assertTrue(MotherMentorChildEidHeiUtils.isComplete(values, olderBirth, day,
                Collections.emptyMap(), "referred"));
    }

    @Test
    public void inactiveAnswersAreClearedBeforeTheCanonicalPayloadIsValidated() throws Exception {
        Map<String, String> values = first("agreed");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "none");
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, birth.plusMonths(4).toString());
        values.put(CHILD_EID_TEST_TYPE, "nine_months");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        JSONObject form = form(values);
        MotherMentorChildEidHeiUtils.clearInactiveAnswers(form);
        assertTrue(complete(MotherMentorMotherChildUtils.values(form), "none"));
        for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) {
            assertEquals("", MotherMentorMotherChildUtils.values(form).get(key));
        }
    }

    private Map<String, String> first(String agreement) {
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MTOTO_RUFAA_STATE_BEFORE, "none");
        values.put(MTOTO_REFERRAL_AGREEMENT, agreement);
        values.put(MTOTO_RUFAA_TEST, "miezi9");
        values.put(MTOTO_RUFAA_DUE, birth.plusMonths(9).toString());
        return values;
    }

    private boolean complete(Map<String, String> values, String state) {
        return MotherMentorChildEidHeiUtils.isComplete(values, birth, day,
                Collections.emptyMap(), state);
    }

    private JSONObject form(Map<String, String> values) throws Exception {
        JSONArray fields = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            fields.put(new JSONObject().put("key", entry.getKey()).put("value", entry.getValue()));
        }
        for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) {
            if (!values.containsKey(key)) fields.put(new JSONObject().put("key", key).put("value", ""));
        }
        return new JSONObject().put("step1", new JSONObject().put("fields", fields));
    }
}
