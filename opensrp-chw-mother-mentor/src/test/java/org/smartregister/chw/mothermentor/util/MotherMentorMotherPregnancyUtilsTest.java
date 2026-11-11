package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import com.vijay.jsonwizard.utils.FormUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherPregnancyUtilsTest {
    private static final LocalDate DAY = new LocalDate(2026, 11, 4);

    @Test
    public void ongoingPregnancyContinuesWithoutAskingOutcomeOrChildDates() {
        Map<String, String> values = answers("pregnant", "pregnant", "");
        assertTrue(complete(values));
        assertFalse(MotherMentorMotherPregnancyUtils.isChildPath(values, DAY));
        assertEquals("pregnant", MotherMentorMotherPregnancyUtils.currentStatus(values));
    }

    @Test
    public void liveBirthsRequireDatesAndShareAChildPathWithoutLosingTheirOutcomeCode() {
        for (String outcome : Arrays.asList("delivered", "preterm")) {
            Map<String, String> values = answers("pregnant", "not_pregnant", outcome);
            assertFalse(complete(values));
            values.put(MAMA_TAREHE_KUJIFUNGUA, "03-11-2026");
            assertTrue(complete(values));
            assertTrue(MotherMentorMotherPregnancyUtils.isChildPath(values, DAY));
            assertEquals(outcome, values.get(MAMA_PREGNANCY_OUTCOME));
            assertEquals("breast_feeding", MotherMentorMotherPregnancyUtils.currentStatus(values));
        }
    }

    @Test
    public void lossesStillbirthAndOtherValidateTheirOwnRequiredAnswers() {
        Map<String, String> loss = answers("pregnant", "not_pregnant", "pregnancy_loss");
        loss.put(MAMA_TAREHE_KUJIFUNGUA, "03-11-2026");
        assertFalse(complete(loss));
        loss.put(MAMA_PREGNANCY_LOSS_DATE, "03-11-2026");
        assertTrue(complete(loss));
        assertFalse(MotherMentorMotherPregnancyUtils.isChildPath(loss, DAY));
        Map<String, String> stillbirth = answers("pregnant", "not_pregnant", "stillbirth");
        assertFalse(complete(stillbirth));
        stillbirth.put(MAMA_TAREHE_KUJIFUNGUA, "04-11-2026");
        assertTrue(complete(stillbirth));
        assertFalse(MotherMentorMotherPregnancyUtils.isChildPath(stillbirth, DAY));
        Map<String, String> other = answers("pregnant", "not_pregnant", "other");
        other.put(MAMA_PREGNANCY_OUTCOME_OTHER, "   ");
        assertFalse(complete(other));
        other.put(MAMA_PREGNANCY_OUTCOME_OTHER, "Another recorded outcome");
        assertTrue(complete(other));
    }

    @Test
    public void everyDatedBranchRejectsImpossibleOrFutureClinicalDates() {
        for (String outcome : Arrays.asList("delivered", "preterm", "stillbirth", "pregnancy_loss")) {
            String key = "pregnancy_loss".equals(outcome) ? MAMA_PREGNANCY_LOSS_DATE : MAMA_TAREHE_KUJIFUNGUA;
            for (String date : Arrays.asList("", "2026-02-30", "31-04-2026", "05-11-2026",
                    "2026-11-05T00:30:00+14:00", "invalid")) {
                Map<String, String> values = answers("pregnant", "not_pregnant", outcome);
                values.put(key, date);
                assertFalse(outcome + ":" + date, complete(values));
            }
        }
        assertEquals(new LocalDate(2026, 11, 4),
                MotherMentorMotherPregnancyUtils.parseDate("2026-11-04T23:30:00-10:00"));
    }

    @Test
    public void breastfeedingAndNotPregnantEnrollmentUseAnImmutableDeliveryDateInBothLocales() throws Exception {
        for (String directory : Arrays.asList("json.form", "json.form-sw")) {
            for (String original : Arrays.asList("bf", "none")) {
                JSONObject form = MotherMentorMotherPregnancyUtils.prepare(form(directory), original,
                        "2026-05-01", new HashMap<>(), false);
                assertEquals("hidden", field(form, VISIT_PREGNANCY_STATUS).getString("type"));
                assertFalse(field(form, VISIT_PREGNANCY_STATUS).has("v_required"));
                assertTrue(field(form, MAMA_ENROLLMENT_DELIVERY_DATE).getBoolean("read_only"));
                assertEquals("01-05-2026", value(form, MAMA_TAREHE_KUJIFUNGUA));
                Map<String, String> values = MotherMentorMotherPregnancyUtils.values(form);
                values.put(ATTENDANCE_TYPE, "repeat");
                values.put(FOLLOW_UP_STATUS, "found");
                assertTrue(complete(values));
                values.put(MAMA_ENROLLMENT_DELIVERY_DATE, "02-05-2026");
                assertFalse("A readonly enrollment DOB cannot be changed by submitted values", complete(values));
            }
        }
    }

    @Test
    public void previouslyRecordedDeliveryIsReadOnlyDuringANewFollowUp() throws Exception {
        Map<String, String> previous = answers("pregnant", "not_pregnant", "preterm");
        previous.put(MAMA_TAREHE_KUJIFUNGUA, "2026-05-01");
        JSONObject source = form("json.form");
        field(source, VISIT_PREGNANCY_STATUS).put("value", "breast_feeding");
        JSONObject prepared = MotherMentorMotherPregnancyUtils.prepare(source, "pregnant", "", previous, false);
        assertEquals("pregnant", value(prepared, MAMA_ENROLLMENT_PREGNANCY_STATUS));
        assertEquals("hidden", field(prepared, VISIT_PREGNANCY_STATUS).getString("type"));
        assertEquals("hidden", field(prepared, MAMA_PREGNANCY_OUTCOME).getString("type"));
        assertEquals("not_pregnant", value(prepared, VISIT_PREGNANCY_STATUS));
        assertEquals("preterm", value(prepared, MAMA_PREGNANCY_OUTCOME));
        assertEquals("01-05-2026", value(prepared, MAMA_PRETERM_DELIVERY_DATE_INPUT));
        assertTrue(field(prepared, MAMA_PRETERM_DELIVERY_DATE_INPUT).getBoolean("read_only"));
    }

    @Test
    public void editingTheSavedDeliveryKeepsTheOriginalQuestionsAvailable() throws Exception {
        Map<String, String> previous = answers("pregnant", "not_pregnant", "delivered");
        previous.put(MAMA_TAREHE_KUJIFUNGUA, "2026-05-01");
        JSONObject prepared = MotherMentorMotherPregnancyUtils.prepare(
                form("json.form-sw"), "pregnant", "", previous, true);
        assertEquals("native_radio", field(prepared, VISIT_PREGNANCY_STATUS).getString("type"));
        assertFalse(field(prepared, MAMA_DELIVERY_DATE_INPUT).optBoolean("read_only"));
    }

    @Test
    public void changingTheOutcomeCannotReuseThePreviousBranchesCanonicalDate() throws Exception {
        JSONObject form = MotherMentorMotherPregnancyUtils.prepare(form("json.form"), "pregnant", "", null, true);
        field(form, ATTENDANCE_TYPE).put("value", "repeat");
        field(form, FOLLOW_UP_STATUS).put("value", "found");
        field(form, VISIT_PREGNANCY_STATUS).put("value", "not_pregnant");
        field(form, MAMA_PREGNANCY_OUTCOME).put("value", "preterm");
        field(form, MAMA_TAREHE_KUJIFUNGUA).put("value", "01-11-2026");
        field(form, MAMA_DELIVERY_DATE_INPUT).put("value", "01-11-2026");
        MotherMentorMotherPregnancyUtils.normalize(form);
        assertEquals("", value(form, MAMA_DELIVERY_DATE_INPUT));
        assertEquals("", value(form, MAMA_TAREHE_KUJIFUNGUA));
        assertFalse(complete(MotherMentorMotherPregnancyUtils.values(form)));
        field(form, MAMA_PRETERM_DELIVERY_DATE_INPUT).put("value", "02-11-2026");
        MotherMentorMotherPregnancyUtils.normalize(form);
        assertEquals("02-11-2026", value(form, MAMA_TAREHE_KUJIFUNGUA));
        assertTrue(complete(MotherMentorMotherPregnancyUtils.values(form)));
    }

    @Test
    public void newAttendanceContinuesWithoutPregnancyOrChildQuestions() throws Exception {
        JSONObject form = form("json.form");
        field(form, ATTENDANCE_TYPE).put("value", "new");
        field(form, MAMA_ENROLLMENT_PREGNANCY_STATUS).put("value", "pregnant");
        field(form, MAMA_PREGNANCY_OUTCOME).put("value", "delivered");
        field(form, MAMA_DELIVERY_DATE_INPUT).put("value", "01-11-2026");
        assertTrue(complete(MotherMentorMotherPregnancyUtils.values(form)));
        assertFalse(MotherMentorMotherPregnancyUtils.isChildPath(
                MotherMentorMotherPregnancyUtils.values(form), DAY));
        MotherMentorMotherPregnancyUtils.normalize(form);
        for (String key : MotherMentorMotherPregnancyUtils.ANSWER_FIELDS)
            assertEquals(key, "", value(form, key));
    }

    @Test
    public void notFoundClearsUserPregnancyAnswersAndRetainsOriginalContext() throws Exception {
        JSONObject form = MotherMentorMotherPregnancyUtils.prepare(form("json.form"), "pregnant", "", null, true);
        for (String key : MotherMentorMotherPregnancyUtils.ANSWER_FIELDS) field(form, key).put("value", "stale");
        field(form, ATTENDANCE_TYPE).put("value", "repeat");
        field(form, FOLLOW_UP_STATUS).put("value", "not_found");
        MotherMentorMotherPregnancyUtils.normalize(form);
        for (String key : MotherMentorMotherPregnancyUtils.ANSWER_FIELDS) assertEquals(key, "", value(form, key));
        assertEquals("pregnant", value(form, MAMA_ENROLLMENT_PREGNANCY_STATUS));
        assertTrue(complete(MotherMentorMotherPregnancyUtils.values(form)));
    }

    private static Map<String, String> answers(String original, String status, String outcome) {
        Map<String, String> values = new HashMap<>();
        values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
        values.put(VISIT_PREGNANCY_STATUS, status);
        values.put(MAMA_PREGNANCY_OUTCOME, outcome);
        return values;
    }

    private static boolean complete(Map<String, String> values) {
        return MotherMentorMotherPregnancyUtils.isComplete(values, DAY);
    }

    private static JSONObject form(String directory) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(directory + "/mothermentor_visit_type.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }

    private static JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private static String value(JSONObject form, String key) throws Exception {
        return field(form, key).optString("value");
    }
}
