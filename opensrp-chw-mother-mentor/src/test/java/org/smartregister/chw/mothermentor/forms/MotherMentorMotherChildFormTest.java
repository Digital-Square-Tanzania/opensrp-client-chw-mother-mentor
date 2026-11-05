package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.*;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildFormTest {
    @Test
    public void childStatusAndSerialTestQuestionsExistInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(directory + "/mothermentor_visit_type.json"), "UTF-8")) {
                JSONObject form = new JSONObject(scanner.useDelimiter("\\A").next());
                for (String key : new String[] {"mama_child_status", "mama_breastfeeding_status",
                        "mama_hei_test_done", "mama_hei_test_type", "mama_hei_test_result",
                        "hei_chanya_imethibitishwa"}) {
                    assertEquals(directory + ":" + key, key, FormUtils.getFieldFromForm(form, key).optString("key"));
                }
            }
        }
    }
    @Test
    public void nativeEventsKeepCanonicalTestAndDueContextWithoutUiOnlyObservations() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form;
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(directory + "/mothermentor_visit_type.json"), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            String[] ui = {"mama_hei_test_type_choice", "mama_child_path",
                    "mama_previous_breastfeeding_status", "mama_previous_weaning_date",
                    "mama_child_age_note", "mama_hei_next_test_note"};
            for (String key : ui) {
                JSONObject field = FormUtils.getFieldFromForm(form, key);
                assertEquals("ui_only", field.getString("openmrs_entity"));
                field.put("value", "private-ui-state");
            }
            FormUtils.getFieldFromForm(form, "mama_hei_test_type").put("value", "nine_months");
            FormUtils.getFieldFromForm(form, "mama_hei_due_test").put("value", "nine_months");
            FormUtils.getFieldFromForm(form, "mama_hei_due_date").put("value", "2026-10-01");
            org.smartregister.domain.tag.FormTag tag = new org.smartregister.domain.tag.FormTag();
            tag.providerId = "test-provider"; tag.locationId = "test-location";
            tag.appVersion = 1; tag.databaseVersion = 64;
            Object event = org.smartregister.util.JsonFormUtils.createEvent(
                    form.getJSONObject("step1").getJSONArray("fields"), form.getJSONObject("metadata"),
                    tag, "test-mother", "Mother Mentor Services", "ec_mothermentor_enrollment");
            java.util.Map<String, String> values = org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion
                    .observationValues(new JSONObject(new com.google.gson.Gson().toJson(event)).getJSONArray("obs"));
            assertEquals("nine_months", values.get("mama_hei_test_type"));
            assertEquals("nine_months", values.get("mama_hei_due_test"));
            assertEquals("2026-10-01", values.get("mama_hei_due_date"));
            for (String key : ui) assertFalse(key, values.containsKey(key));
            FormUtils.getFieldFromForm(form, "mama_hei_test_type_choice").put("value", "");
            org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils.prepare(form,
                    new org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository.State(null),
                    new org.joda.time.LocalDate(2026, 10, 1));
            assertEquals("nine_months", FormUtils.getFieldFromForm(form, "mama_hei_test_type_choice").getString("value"));
        }
    }

}
