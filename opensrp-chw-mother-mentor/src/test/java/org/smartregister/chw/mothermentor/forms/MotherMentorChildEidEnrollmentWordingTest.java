package org.smartregister.chw.mothermentor.forms;

import android.app.Application;
import android.content.Context;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidDirectEnrollment;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.BASE_ENTITY_ID;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.BREASTFEEDING_STATUS;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.DOB;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FIRST_NAME;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.HEI_REGISTERED;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.LAST_TEST;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.LAST_TEST_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_CAREGIVER_NAME;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_CAREGIVER_RELATIONSHIP;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_ENTRY_SOURCE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_MISSED_DUE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_MISSED_TEST;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.WEANING_DATE;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidEnrollmentWordingTest {
    private static final String[] ENGLISH = {
            "Breastfeeding status", "Breastfeeding", "Never breastfed", "Stopped breastfeeding"
    };
    private static final String[] KISWAHILI = {
            "Hali ya unyonyeshaji", "Ananyonyeshwa", "Hajawahi kunyonyeshwa", "Ameacha kunyonyeshwa"
    };
    private static final String STOPPED_KEY = "stop_breast_feeding";

    @Test
    public void linkedEnglishUsesAgreedCaptionsAndExistingRouting() throws Exception {
        assertLinkedForm("", ENGLISH);
    }

    @Test
    public void linkedKiswahiliUsesAgreedCaptionsAndExistingRouting() throws Exception {
        assertLinkedForm("-sw", KISWAHILI);
    }

    @Test
    @Config(qualifiers = "en")
    public void directEnglishKeepsNeverBreastfedCodeAndAgeBasedDueDate() throws Exception {
        assertDirectForm("", ENGLISH);
    }

    @Test
    @Config(qualifiers = "sw")
    public void directKiswahiliKeepsNeverBreastfedCodeAndAgeBasedDueDate() throws Exception {
        assertDirectForm("-sw", KISWAHILI);
    }

    private void assertLinkedForm(String locale, String[] captions) throws Exception {
        JSONObject form = loadForm(locale);
        assertRoute(form);
        assertEquals(captions[0], form.getJSONObject("step2").getString("title"));
        assertCaptionsAndKeys(field(form, "step2", BREASTFEEDING_STATUS), captions, "not_breast_feeding");
        assertFeedingRelevance(field(form, "step2", WEANING_DATE), "step2", "equalTo");
        assertNotNull(field(form, "step3", LAST_TEST));
    }

    private void assertDirectForm(String locale, String[] captions) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LocalDate birthDate = LocalDate.now().minusWeeks(10);
        Map<String, String> child = new HashMap<>();
        child.put(BASE_ENTITY_ID, "synthetic-child-eid");
        child.put(FIRST_NAME, "Synthetic Child");
        child.put(DOB, birthDate.toString());

        JSONObject form = MotherMentorChildEidDirectEnrollment.prepareForm(
                context, loadForm(locale), child, "Synthetic Household");
        assertRoute(form);
        assertNotNull(field(form, "step2", MTOTO_CAREGIVER_NAME));
        assertEquals(captions[0], form.getJSONObject("step3").getString("title"));
        assertCaptionsAndKeys(field(form, "step3", BREASTFEEDING_STATUS), captions, "never_breastfed");
        assertFeedingRelevance(field(form, "step3", WEANING_DATE), "step3", "equalTo");
        assertFeedingRelevance(field(form, "step4", LAST_TEST), "step3", "notEqualTo");
        assertFeedingRelevance(field(form, "step4", "direct_enrollment_last_test_weaned"), "step3", "equalTo");

        MotherMentorMotherChildUtils.put(form, HEI_REGISTERED, "no");
        MotherMentorMotherChildUtils.put(form, MTOTO_CAREGIVER_NAME, "Synthetic Caregiver");
        MotherMentorMotherChildUtils.put(form, MTOTO_CAREGIVER_RELATIONSHIP, "father");
        MotherMentorMotherChildUtils.put(form, BREASTFEEDING_STATUS, "never_breastfed");
        MotherMentorMotherChildUtils.put(form, LAST_TEST, "at_birth");
        MotherMentorMotherChildUtils.put(form, LAST_TEST_RESULT, "negative");
        MotherMentorChildEidDirectEnrollment.prepareForEvent(context, null, form, child, "Synthetic Household");

        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        assertEquals("direct", values.get(MTOTO_ENTRY_SOURCE));
        assertEquals("never_breastfed", values.get(BREASTFEEDING_STATUS));
        assertEquals("", values.get(WEANING_DATE));
        assertEquals("wiki6", values.get(MTOTO_MISSED_TEST));
        assertEquals(birthDate.plusWeeks(6).toString(), values.get(MTOTO_MISSED_DUE));
    }

    private JSONObject loadForm(String locale) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Paths.get(
                "src/main/assets/json.form" + locale + "/mothermentor_enroll_child_eid.json")),
                StandardCharsets.UTF_8));
    }

    private void assertRoute(JSONObject form) throws Exception {
        assertEquals(4, form.getInt("count"));
        assertEquals("Mother Mentor Enroll Child Eid", form.getString("encounter_type"));
        assertEquals("step2", form.getJSONObject("step1").getString("next"));
        assertEquals("step3", form.getJSONObject("step2").getString("next"));
        assertEquals("step4", form.getJSONObject("step3").getString("next"));
        assertFalse(form.getJSONObject("step4").has("next"));
    }

    private void assertCaptionsAndKeys(JSONObject field, String[] captions, String neverKey) throws Exception {
        assertEquals(captions[0], field.getString("label"));
        assertEquals("native_radio", field.getString("type"));
        assertEquals(BREASTFEEDING_STATUS, field.getString("openmrs_entity_id"));
        JSONArray options = field.getJSONArray("options");
        String[] keys = {"breast_feeding", neverKey, STOPPED_KEY};
        assertEquals(keys.length, options.length());
        for (int i = 0; i < keys.length; i++) {
            JSONObject option = options.getJSONObject(i);
            assertEquals(keys[i], option.getString("key"));
            assertEquals(keys[i], option.getString("openmrs_entity_id"));
            assertEquals(captions[i + 1], option.getString("text"));
        }
    }

    private void assertFeedingRelevance(JSONObject field, String step, String comparison) throws Exception {
        JSONObject relevance = field.getJSONObject("relevance");
        assertEquals(1, relevance.length());
        JSONObject condition = relevance.getJSONObject(step + ":" + BREASTFEEDING_STATUS);
        assertEquals("string", condition.getString("type"));
        assertEquals(comparison + "(., \"" + STOPPED_KEY + "\")", condition.getString("ex"));
    }

    private JSONObject field(JSONObject form, String step, String key) throws Exception {
        JSONArray fields = form.getJSONObject(step).getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.getString("key"))) return field;
        }
        throw new AssertionError("Missing field " + step + ":" + key);
    }
}
