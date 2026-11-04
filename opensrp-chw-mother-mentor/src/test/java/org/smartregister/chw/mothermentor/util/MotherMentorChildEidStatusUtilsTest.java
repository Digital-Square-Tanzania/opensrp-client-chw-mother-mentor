package org.smartregister.chw.mothermentor.util;

import android.app.Application;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidStatusUtilsTest {
    private static final LocalDate BIRTH = new LocalDate(2026, 1, 20);
    private static final LocalDate SERVICE = new LocalDate(2026, 10, 20);

    @Test
    public void foundAliveRequiresTheNestedAnswerButNoUnrelatedDeathDate() {
        Map<String, String> values = values(FOLLOW_UP_STATUS, "found");
        assertFalse(complete(values));
        assertFalse(MotherMentorChildEidStatusUtils.isFoundAlive(values));
        values.put(MAMA_CHILD_STATUS, "alive");
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, "invalid inactive answer");
        assertTrue(complete(values));
        assertTrue(MotherMentorChildEidStatusUtils.isFoundAlive(values));
        assertTrue(MotherMentorChildEidStatusUtils.isStatusComplete(values, null, SERVICE));
    }

    @Test
    public void deathDatesMustBeRealAndWithinBirthAndTheOriginalVisitDay() {
        Map<String, String> values = values(FOLLOW_UP_STATUS, "found", MAMA_CHILD_STATUS, "deceased");
        for (String invalid : new String[] {"", "31-02-2026", "2026-01-19", "2026-10-21",
                "2026-1-20", "2026-10-20T00:00:00Z"}) {
            values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, invalid);
            assertFalse(invalid, complete(values));
        }
        for (String valid : new String[] {"20-01-2026", "2026-10-20", "20/10/2026"}) {
            values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, valid);
            assertTrue(valid, complete(values));
            assertFalse(MotherMentorChildEidStatusUtils.isFoundAlive(values));
        }
        assertFalse(MotherMentorChildEidStatusUtils.isStatusComplete(values, null, SERVICE));
        assertFalse(MotherMentorChildEidStatusUtils.isStatusComplete(values, BIRTH, null));
        assertFalse(MotherMentorChildEidStatusUtils.isStatusComplete(values, BIRTH,
                LocalDate.now().plusDays(1)));
    }

    @Test
    public void eachNotFoundReasonHasItsOwnRequiredAnswer() {
        Map<String, String> values = values(FOLLOW_UP_STATUS, "not_found");
        assertFalse(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON, "moved_area");
        assertTrue(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON, "moved_facility");
        assertFalse(complete(values));
        values.put(MTOTO_NEW_FACILITY, "  ");
        assertFalse(complete(values));
        values.put(MTOTO_NEW_FACILITY, "New facility");
        assertTrue(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON, "other");
        assertFalse(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON_OTHER, "  ");
        assertFalse(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON_OTHER, "Not at home today");
        assertTrue(complete(values));
        values.put(MTOTO_NOT_FOUND_REASON, "unknown");
        assertFalse(complete(values));
    }

    @Test
    public void removedFlatOptionsCannotBecomeNewSchemaOutcomes() {
        for (String status : new String[] {"deceased", "transferred_facility", "refused_return", "under_follow_up", ""}) {
            assertFalse(status, complete(values(FOLLOW_UP_STATUS, status)));
        }
        Map<String, String> values = values(FOLLOW_UP_STATUS, "found", MAMA_CHILD_STATUS, "alive");
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "2");
        assertFalse(complete(values));
        assertFalse(MotherMentorChildEidStatusUtils.isFoundAlive(values));
    }

    @Test
    public void foundAndAliveClearsDeathAndEveryNotFoundAnswerWithoutLosingDob() throws Exception {
        JSONObject form = form(FOLLOW_UP_STATUS, "found", MAMA_CHILD_STATUS, "alive",
                MAMA_TAREHE_MTOTO_ALIFARIKI, "20-10-2026", MTOTO_NOT_FOUND_REASON, "other",
                MTOTO_NOT_FOUND_REASON_OTHER, "Old reason", MTOTO_NEW_FACILITY, "Old facility",
                MTOTO_MOVED_AREA_FACILITY, "Optional old facility", CHILD_DOB, BIRTH.toString());
        MotherMentorChildEidStatusUtils.clearInactiveAnswers(form);
        for (String key : new String[] {MAMA_TAREHE_MTOTO_ALIFARIKI, MTOTO_NOT_FOUND_REASON,
                MTOTO_NOT_FOUND_REASON_OTHER, MTOTO_NEW_FACILITY, MTOTO_MOVED_AREA_FACILITY}) {
            assertEquals(key, "", JsonFormUtils.getValue(form, key));
        }
        assertEquals("alive", JsonFormUtils.getValue(form, MAMA_CHILD_STATUS));
        assertEquals(BIRTH.toString(), JsonFormUtils.getValue(form, CHILD_DOB));
    }

    @Test
    public void notFoundOtherClearsTheFoundBranchAndBothFacilityInputs() throws Exception {
        JSONObject form = form(FOLLOW_UP_STATUS, "not_found", MTOTO_NOT_FOUND_REASON, "other",
                MTOTO_NOT_FOUND_REASON_OTHER, "Current explanation", MAMA_CHILD_STATUS, "deceased",
                MAMA_TAREHE_MTOTO_ALIFARIKI, "20-10-2026", MTOTO_NEW_FACILITY, "Old facility",
                MTOTO_MOVED_AREA_FACILITY, "Optional old facility");
        MotherMentorChildEidStatusUtils.clearInactiveAnswers(form);
        for (String key : new String[] {MAMA_CHILD_STATUS, MAMA_TAREHE_MTOTO_ALIFARIKI,
                MTOTO_NEW_FACILITY, MTOTO_MOVED_AREA_FACILITY}) {
            assertEquals(key, "", JsonFormUtils.getValue(form, key));
        }
        assertEquals("Current explanation", JsonFormUtils.getValue(form, MTOTO_NOT_FOUND_REASON_OTHER));
    }

    @Test
    public void optionalFacilityIsNormalizedButDoesNotLeakAcrossReasons() throws Exception {
        JSONObject form = form(FOLLOW_UP_STATUS, "not_found", MTOTO_NOT_FOUND_REASON, "moved_area",
                MTOTO_MOVED_AREA_FACILITY, "Optional facility", MTOTO_NEW_FACILITY, "Old required facility",
                MTOTO_NOT_FOUND_REASON_OTHER, "Old reason");
        MotherMentorChildEidStatusUtils.clearInactiveAnswers(form);
        assertEquals("Optional facility", JsonFormUtils.getValue(form, MTOTO_NEW_FACILITY));
        assertEquals("", JsonFormUtils.getValue(form, MTOTO_NOT_FOUND_REASON_OTHER));
        com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, MTOTO_NOT_FOUND_REASON)
                .put("value", "moved_facility");
        com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, MTOTO_NEW_FACILITY)
                .put("value", "Required facility");
        MotherMentorChildEidStatusUtils.clearInactiveAnswers(form);
        assertEquals("Required facility", JsonFormUtils.getValue(form, MTOTO_NEW_FACILITY));
        assertEquals("", JsonFormUtils.getValue(form, MTOTO_MOVED_AREA_FACILITY));
    }

    @Test
    public void legacyPayloadsAreNotRewrittenBySchema3Clearing() throws Exception {
        JSONObject form = form(FOLLOW_UP_STATUS, "deceased", MAMA_CHILD_STATUS, "alive");
        com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, CHILD_EID_FOLLOW_UP_SCHEMA)
                .put("value", "2");
        String before = form.toString();
        MotherMentorChildEidStatusUtils.clearInactiveAnswers(form);
        assertEquals(before, form.toString());
    }

    @Test
    public void fullCompletionCannotUseTheLegacyParserForNewClinicalAnswers() {
        Map<String, String> values = values(FOLLOW_UP_STATUS, "not_found",
                MTOTO_NOT_FOUND_REASON, "other", MTOTO_NOT_FOUND_REASON_OTHER, "Away today");
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true, BIRTH, SERVICE));
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(MAMA_CHILD_STATUS, "deceased");
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, "20-10-2026");
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true, null, SERVICE));
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true, BIRTH, SERVICE));
        values.put(MAMA_CHILD_STATUS, "alive");
        values.put(CHILD_EID_TEST_TYPE, "nine_months");
        values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, "20-10-2026");
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true, BIRTH, SERVICE));
    }

    private boolean complete(Map<String, String> values) {
        return MotherMentorChildEidStatusUtils.isStatusComplete(values, BIRTH, SERVICE);
    }

    private Map<String, String> values(String... pairs) {
        Map<String, String> values = new HashMap<>();
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "3");
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }

    private JSONObject form(String... pairs) throws Exception {
        JSONArray fields = new JSONArray();
        for (Map.Entry<String, String> entry : values(pairs).entrySet()) {
            fields.put(new JSONObject().put("key", entry.getKey()).put("value", entry.getValue()));
        }
        return new JSONObject().put("count", 1)
                .put("step1", new JSONObject().put("fields", fields));
    }
}
