package org.smartregister.chw.mothermentor.forms;

import android.app.Application;

import com.google.gson.Gson;
import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
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
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorIitStatusActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.domain.tag.FormTag;

import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FOLLOW_UP_STATUS;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_KITUO_KIPYA;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_FOUND_OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_FOUND_REASON;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorIitStatusNativeRulesTest {
    private static final String OPTIONAL_FACILITY = "iit_moved_area_facility";
    private static final String RULES = "mothermentor_iit_status_relevance.yml";
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};
    private static final String[] REASONS = {"deceased", "moved_area", "moved_facility",
            "travelled", "other"};

    @Test
    public void nativeRelevanceExposesOnlyQuestionsForTheSelectedReason() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_iit_follow_up_status");
            JsonFormActivity activity = nativeActivity(form);
            assertOptions(form, FOLLOW_UP_STATUS, "found", "not_found");
            assertOptions(form, IIT_NOT_FOUND_REASON, REASONS);
            assertFalse(FormUtils.getFieldFromForm(form, EXPECTED_FACILITY_VISIT_DATE).has("key"));

            answer(form, FOLLOW_UP_STATUS, "found");
            assertFalse(relevant(activity, form, IIT_NOT_FOUND_REASON));
            for (String reason : REASONS) {
                assertFalse(relevant(activity, form, "iit_note_" + reason));
            }
            answer(form, FOLLOW_UP_STATUS, "not_found");
            assertTrue(relevant(activity, form, IIT_NOT_FOUND_REASON));
            for (String reason : REASONS) {
                answer(form, IIT_NOT_FOUND_REASON, reason);
                assertEquals(reason.equals("moved_area"), relevant(activity, form, OPTIONAL_FACILITY));
                assertEquals(reason.equals("moved_facility"), relevant(activity, form, IIT_KITUO_KIPYA));
                assertEquals(reason.equals("other"), relevant(activity, form, IIT_NOT_FOUND_OTHER));
                for (String note : REASONS) {
                    assertEquals(reason.equals(note), relevant(activity, form, "iit_note_" + note));
                }
            }
        }
    }

    @Test
    public void requiredFacilityAndOtherTextAreValidatedAndMovedAreaUsesCanonicalObservation()
            throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_iit_follow_up_status");
            MotherMentorIitStatusActionHelper helper = new MotherMentorIitStatusActionHelper(
                    () -> {}, MotherMentorIitState.active(), values -> MotherMentorIitState.active());
            answer(form, FOLLOW_UP_STATUS, "not_found");
            answer(form, IIT_NOT_FOUND_REASON, "moved_facility");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            answer(form, IIT_KITUO_KIPYA, "Receiving clinic");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            answer(form, IIT_NOT_FOUND_REASON, "other");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            answer(form, IIT_NOT_FOUND_OTHER, "Reason in the visit");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            answer(form, IIT_NOT_FOUND_REASON, "moved_area");
            answer(form, OPTIONAL_FACILITY, "Optional clinic");
            helper.onPayloadReceived(form.toString());
            JSONObject processed = new JSONObject(helper.postProcess(form.toString()));
            FormTag tag = new FormTag();
            tag.providerId = "test-provider";
            tag.locationId = "test-location";
            tag.appVersion = 1;
            tag.databaseVersion = 66;
            Object event = org.smartregister.util.JsonFormUtils.createEvent(
                    processed.getJSONObject("step1").getJSONArray("fields"),
                    processed.getJSONObject("metadata"), tag, "test-mother",
                    "Mother Mentor Follow-up Visit", "ec_mothermentor_followup_visit");
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    new JSONObject(new Gson().toJson(event)).getJSONArray("obs"));
            assertEquals("Optional clinic", values.get(IIT_KITUO_KIPYA));
            assertFalse(values.containsKey(OPTIONAL_FACILITY));
            assertFalse(values.containsKey(IIT_NOT_FOUND_OTHER));
        }
    }

    @Test
    public void optionalCommentsRemainLimitedTo100CharactersInBothLocales() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_follow_up_comment");
            JSONObject comments = FormUtils.getFieldFromForm(form, "comments");
            assertEquals("100", comments.getJSONObject("v_max_length").getString("value"));
            assertNull(comments.optJSONObject("v_required"));
        }
    }

    private static JSONObject form(String language, String name) throws Exception {
        try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                .open(language + "/" + name + ".json"), "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }

    private static void assertOptions(JSONObject form, String key, String... expected) throws Exception {
        JSONArray options = FormUtils.getFieldFromForm(form, key).getJSONArray("options");
        assertEquals(expected.length, options.length());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], options.getJSONObject(i).getString("key"));
        }
    }

    private static JsonFormActivity nativeActivity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        ReflectionHelpers.setField(activity, "mJSONObject", form);
        activity.setRulesEngineFactory(new RulesEngineFactory(
                RuntimeEnvironment.getApplication(), Collections.emptyMap()));
        Map<String, JSONObject> fields = ReflectionHelpers.getField(activity, "formFields");
        JSONArray definitions = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject field = definitions.getJSONObject(i);
            fields.put("step1_" + field.getString("key"), field);
        }
        return activity;
    }

    private static void answer(JSONObject form, String key, String value) throws Exception {
        FormUtils.getFieldFromForm(form, key).put("value", value);
    }

    private static boolean relevant(JsonFormActivity activity, JSONObject form, String key) throws Exception {
        String[] address = {"rules-engine", RULES, "step1_" + key};
        JSONObject source = activity.getObjectUsingAddress(address, false);
        Facts facts = ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
        return activity.getRulesEngineFactory().getRelevance(facts, RULES);
    }
}
