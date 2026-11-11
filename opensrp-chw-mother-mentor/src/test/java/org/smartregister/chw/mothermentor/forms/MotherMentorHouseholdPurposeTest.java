package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.*;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdVisitPurposeActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;

import java.io.InputStream;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdPurposeTest {
    @Test
    public void onlyTheThreePurposesAreSelectableInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(directory);
            JSONObject purpose = field(form, "purpose_of_visit");
            if (directory.endsWith("-sw")) {
                assertEquals("Dhumuni la Hudhurio", form.getJSONObject("step1").getString("title"));
                assertEquals("Dhumuni la Hudhurio", purpose.getString("label"));
            }
            JSONArray options = purpose.getJSONArray("options");
            assertEquals(3, options.length());
            assertEquals("routine_follow_up", options.getJSONObject(0).getString("key"));
            assertEquals(directory.endsWith("-sw") ? "Kufuatilia maendeleo ya kaya"
                    : "Follow up household progress", options.getJSONObject(0).getString("text"));
            assertEquals("psychological_support", options.getJSONObject(1).getString("key"));
            assertEquals("other", options.getJSONObject(2).getString("key"));
            assertTrue(field(form, "other_purpose").getJSONObject("v_required").getBoolean("value"));
        }
    }

    @Test
    public void otherPurposeRequiresItsExplanationWithoutChangingLegacyCodedAnswers() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(directory);
            MotherMentorHouseholdVisitPurposeActionHelper helper = new MotherMentorHouseholdVisitPurposeActionHelper();
            field(form, "purpose_of_visit").put("value", "other");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            field(form, "other_purpose").put("value", "Support requested by household");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            field(form, "purpose_of_visit").put("value", "routine_follow_up");
            field(form, "other_purpose").put("value", "");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    private JSONObject form(String directory) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(
                directory + "/mothermentor_household_visit_purpose.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }
}
