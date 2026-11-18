package org.smartregister.chw.mothermentor.util;

import android.app.Application;
import android.content.Context;

import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RulesEngineFactory;

import org.jeasy.rules.api.Facts;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidDirectEnrollmentTest {
    private static final String WEANED_LAST_TEST = "direct_enrollment_last_test_weaned";
    private final Context context = RuntimeEnvironment.getApplication();
    private final LocalDate day = LocalDate.now();

    @Test
    @Config(qualifiers = "en")
    public void priorBirthTestDoesNotPreventEnrollmentForMissedSixWeekTest() throws Exception {
        Map<String, String> child = child(day.minusWeeks(10));
        JSONObject form = prepare(child, "");
        MotherMentorMotherChildUtils.put(form, LAST_TEST, "at_birth");
        MotherMentorMotherChildUtils.put(form, LAST_TEST_RESULT, "negative");
        MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Family");
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        assertEquals("direct", values.get(MTOTO_ENTRY_SOURCE));
        assertEquals("wiki6", values.get(MTOTO_MISSED_TEST));
        assertEquals(day.minusWeeks(4).toString(), values.get(MTOTO_MISSED_DUE));
        assertEquals("", values.get(HEI_NUMBER));
        assertEquals("", values.get(MOTHER_BASE_ENTITY_ID));
        assertEquals("never_breastfed", values.get(BREASTFEEDING_STATUS));
        assertEquals("", values.get(WEANING_DATE));
    }

    @Test
    @Config(qualifiers = "sw")
    public void twentyDayOldChildGetsNextTestRatherThanAnEnrollment() throws Exception {
        Map<String, String> child = child(day.minusDays(20));
        JSONObject form = prepare(child, "-sw");
        assertNull(MotherMentorMotherChildUtils.field(form, MOTHER_CTC_NUMBER));
        assertNull(MotherMentorMotherChildUtils.field(form, HEI_FOLLOW_UP_NUMBER));
        assertNull(MotherMentorMotherChildUtils.field(form, HEI_NUMBER).optJSONObject("v_required"));
        assertEquals("mask_edit_text", MotherMentorMotherChildUtils.field(form, HEI_NUMBER).optString("type"));
        MotherMentorMotherChildUtils.put(form, LAST_TEST, "no_test_has_been_done_yet");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Family"));
        assertTrue(error.getMessage().contains("Kipimo kinachofuata"));
        assertTrue(error.getMessage().contains(MotherMentorMotherPregnancyUtils.displayDate(day.plusDays(22))));
    }

    @Test
    public void pendingRequiresADateAndPositiveUsesConfirmationReferral() throws Exception {
        Map<String, String> child = child(day.minusWeeks(10));
        JSONObject form = prepare(child, "");
        MotherMentorMotherChildUtils.put(form, LAST_TEST, "dbs_6");
        MotherMentorMotherChildUtils.put(form, LAST_TEST_RESULT, "pending");
        assertThrows(IllegalArgumentException.class,
                () -> MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Family"));
        MotherMentorMotherChildUtils.put(form, MTOTO_ENTRY_TEST_DATE, day.minusWeeks(3).toString());
        MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Family");
        assertEquals("awaiting_result", MotherMentorMotherChildUtils.values(form).get(MTOTO_RUFAA_STATE));
        assertEquals(day.minusWeeks(3).toString(), MotherMentorMotherChildUtils.values(form).get(MTOTO_ENTRY_TEST_DATE));

        JSONObject positive = prepare(child, "");
        MotherMentorMotherChildUtils.put(positive, LAST_TEST, "at_birth");
        MotherMentorMotherChildUtils.put(positive, LAST_TEST_RESULT, "positive");
        MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, positive, child, "Family");
        assertEquals("confirmation_needed", MotherMentorMotherChildUtils.values(positive).get(MTOTO_RUFAA_STATE));
        assertEquals("at_birth", MotherMentorMotherChildUtils.values(positive).get(MTOTO_RUFAA_TEST));
    }

    @Test
    public void wizardUsesOneLastTestGroupAndChecksTheResultOfTheActiveGroup() throws Exception {
        for (String locale : new String[] {"", "-sw"}) {
            JSONObject form = prepare(child(day.minusMonths(15)), locale);
            JsonFormActivity activity = nativeActivity(form);
            assertTrue(relevant(activity, form, LAST_TEST));
            assertFalse(relevant(activity, form, WEANED_LAST_TEST));
            assertFalse(relevant(activity, form, LAST_TEST_RESULT));
            MotherMentorMotherChildUtils.put(form, LAST_TEST, "no_test_has_been_done_yet");
            assertFalse(relevant(activity, form, LAST_TEST_RESULT));
            MotherMentorMotherChildUtils.put(form, LAST_TEST, "at_birth");
            assertTrue(relevant(activity, form, LAST_TEST_RESULT));
            MotherMentorMotherChildUtils.put(form, LAST_TEST_RESULT, "pending");
            assertTrue(relevant(activity, form, MTOTO_ENTRY_TEST_DATE));

            MotherMentorMotherChildUtils.put(form, BREASTFEEDING_STATUS, "stop_breast_feeding");
            assertFalse(relevant(activity, form, LAST_TEST));
            assertTrue(relevant(activity, form, WEANED_LAST_TEST));
            assertFalse(relevant(activity, form, LAST_TEST_RESULT));
            MotherMentorMotherChildUtils.put(form, WEANED_LAST_TEST, "three_months_after_weaning");
            assertTrue(relevant(activity, form, LAST_TEST_RESULT));
        }
    }

    @Test
    public void nativeOptionalHeiPlaceholderRemainsBlankOnEnrollment() throws Exception {
        Map<String, String> child = child(day.minusWeeks(10));
        JSONObject form = prepare(child, "");
        MotherMentorMotherChildUtils.put(form, LAST_TEST, "no_test_has_been_done_yet");
        MotherMentorMotherChildUtils.put(form, HEI_NUMBER, "00-00-0000-000000-C00");
        MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Family");
        assertEquals("", MotherMentorMotherChildUtils.values(form).get(HEI_NUMBER));
        assertEquals("01-00-0000-000000-C00",
                MotherMentorChildEidDirectEnrollment.normalizeHeiNumber("01-00-0000-000000-C00"));
    }

    private JsonFormActivity nativeActivity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        ReflectionHelpers.setField(activity, "mJSONObject", form);
        activity.setRulesEngineFactory(new RulesEngineFactory(context, Collections.emptyMap()));
        ReflectionHelpers.callInstanceMethod(activity, "initComparisons");
        Map<String, JSONObject> fields = ReflectionHelpers.getField(activity, "formFields");
        for (int i = 1; i <= form.getInt("count"); i++) {
            String step = "step" + i;
            JSONArray definitions = form.getJSONObject(step).getJSONArray("fields");
            for (int j = 0; j < definitions.length(); j++) {
                JSONObject field = definitions.getJSONObject(j);
                fields.put(step + "_" + field.getString("key"), field);
            }
        }
        return activity;
    }

    private boolean relevant(JsonFormActivity activity, JSONObject form, String key) throws Exception {
        JSONObject relevance = MotherMentorMotherChildUtils.field(form, key).getJSONObject("relevance");
        String source = relevance.keys().next();
        JSONObject comparison = relevance.getJSONObject(source);
        String[] address = "rules-engine".equals(source)
                ? new String[]{source, comparison.getJSONObject("ex-rules").getString("rules-file"), "step4_" + key}
                : source.split(":");
        Facts facts = ReflectionHelpers.callInstanceMethod(activity, "getValueFromAddress",
                ClassParameter.from(String[].class, address), ClassParameter.from(boolean.class, false));
        return ReflectionHelpers.callInstanceMethod(activity, "isRelevant",
                ClassParameter.from(Facts.class, facts), ClassParameter.from(JSONObject.class, comparison));
    }

    private JSONObject prepare(Map<String, String> child, String locale) throws Exception {
        File file = new File("src/main/assets/json.form" + locale + "/mothermentor_enroll_child_eid.json");
        JSONObject form = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        MotherMentorChildEidDirectEnrollment.prepareForm(context, form, child, "Family");
        MotherMentorMotherChildUtils.put(form, HEI_REGISTERED, "yes");
        MotherMentorMotherChildUtils.put(form, MTOTO_CAREGIVER_NAME, "Test Caregiver");
        MotherMentorMotherChildUtils.put(form, MTOTO_CAREGIVER_RELATIONSHIP, "father");
        MotherMentorMotherChildUtils.put(form, BREASTFEEDING_STATUS, "never_breastfed");
        return form;
    }

    private Map<String, String> child(LocalDate birthday) {
        Map<String, String> identity = new LinkedHashMap<>();
        identity.put(BASE_ENTITY_ID, "child");
        identity.put(FIRST_NAME, "Test Child");
        identity.put(RELATIONAL_ID, "family");
        identity.put(DOB, birthday.toString());
        return identity;
    }
}
