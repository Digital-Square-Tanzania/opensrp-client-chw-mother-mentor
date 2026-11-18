package org.smartregister.chw.mothermentor.util;

import android.app.Application;
import android.content.Context;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidDirectEnrollmentTest {
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
