package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherPregnancyFormTest {
    @Test
    public void englishFormOffersTheFivePregnancyOutcomesAndStrictDateInputs() throws Exception {
        verifyForm("json.form", "Pregnancy status", "Date of pregnancy loss");
    }

    @Test
    public void swahiliFormOffersTheSameCodesWithSwahiliLabels() throws Exception {
        verifyForm("json.form-sw", "Hali ya ujauzito", "Tarehe mimba ilipoharibika");
    }

    @Test
    public void nativeEventContainsTheCanonicalDateWithoutDuplicatingItsUiAliases() throws Exception {
        JSONObject form;
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                .open("json.form/mothermentor_visit_type.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            form = new JSONObject(scanner.next());
        }
        field(form, MAMA_TAREHE_KUJIFUNGUA).put("value", "19-10-2026");
        field(form, MAMA_DELIVERY_DATE_INPUT).put("value", "19-10-2026");
        org.smartregister.domain.tag.FormTag tag = new org.smartregister.domain.tag.FormTag();
        tag.providerId = "test-provider"; tag.locationId = "test-location";
        tag.appVersion = 1; tag.databaseVersion = 63;
        Object event = org.smartregister.util.JsonFormUtils.createEvent(
                form.getJSONObject("step1").getJSONArray("fields"), form.getJSONObject("metadata"),
                tag, "test-mother", "Mother Mentor Services", "ec_mothermentor_enrollment");
        JSONArray observations = new JSONObject(new com.google.gson.Gson().toJson(event)).getJSONArray("obs");
        java.util.Map<String, String> values = org.smartregister.chw.mothermentor.util
                .MotherMentorVisitCompletion.observationValues(observations);
        assertEquals("19-10-2026", values.get(MAMA_TAREHE_KUJIFUNGUA));
        for (String key : Arrays.asList(MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                MAMA_STILLBIRTH_DATE_INPUT, MAMA_ENROLLMENT_DELIVERY_DATE)) assertFalse(key + " unexpectedly appears in " + values, values.containsKey(key));
    }

    private void verifyForm(String directory, String pregnancyLabel, String lossLabel) throws Exception {
        JSONObject form;
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                .open(directory + "/mothermentor_visit_type.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            form = new JSONObject(scanner.next());
        }
        JSONObject pregnancy = field(form, VISIT_PREGNANCY_STATUS);
        assertEquals(pregnancyLabel, pregnancy.getString("label"));
        assertEquals(Arrays.asList("pregnant", "not_pregnant"), optionKeys(pregnancy));
        assertEquals(Arrays.asList("delivered", "preterm", "pregnancy_loss", "stillbirth", "other"),
                optionKeys(field(form, MAMA_PREGNANCY_OUTCOME)));
        for (String key : Arrays.asList(MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                MAMA_STILLBIRTH_DATE_INPUT, MAMA_PREGNANCY_LOSS_DATE)) {
            JSONObject date = field(form, key);
            assertEquals("date_picker", date.getString("type"));
            assertEquals("today", date.getString("max_date"));
            assertTrue(date.getJSONObject("v_required").getBoolean("value"));
            assertTrue(date.getJSONObject("relevance").has("rules-engine"));
        }
        assertEquals(lossLabel, field(form, MAMA_PREGNANCY_LOSS_DATE).getString("hint"));
        for (String key : Arrays.asList(MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                MAMA_STILLBIRTH_DATE_INPUT, MAMA_ENROLLMENT_DELIVERY_DATE)) {
            assertEquals("UI alias must not become a concept or fallback formsubmissionField",
                    "ui_only", field(form, key).optString("openmrs_entity"));
        }
        assertTrue(field(form, MAMA_ENROLLMENT_DELIVERY_DATE).getBoolean("read_only"));
        assertEquals("hidden", field(form, MAMA_TAREHE_KUJIFUNGUA).getString("type"));
        assertEquals("concept", field(form, MAMA_TAREHE_KUJIFUNGUA).getString("openmrs_entity"));
        assertTrue(field(form, MAMA_TAREHE_KUJIFUNGUA).has("calculation"));
    }

    private static JSONObject field(JSONObject form, String key) throws Exception {
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.optString("key"))) return field;
        }
        throw new AssertionError("Missing field " + key);
    }

    private static List<String> optionKeys(JSONObject field) throws Exception {
        List<String> result = new ArrayList<>();
        JSONArray options = field.getJSONArray("options");
        for (int i = 0; i < options.length(); i++) result.add(options.getJSONObject(i).getString("key"));
        return result;
    }
}
