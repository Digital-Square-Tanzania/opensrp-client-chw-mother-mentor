package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;

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

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

/** Exercise the SDK's dependency discovery before evaluating relevance; never supply facts manually. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidStatusNativeRulesTest {
    private static final String RULES = "mothermentor_child_eid_status_relevance.yml";
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};

    @Test
    public void childStatusAndDeathQuestionsReceiveActualNativeStatusFacts() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_child_eid_follow_up_status");
            JsonFormActivity activity = nativeActivity(form);
            for (String status : new String[] {"", "found", "not_found"}) {
                set(form, FOLLOW_UP_STATUS, status);
                Facts childFacts = nativeFacts(activity, field(form, MAMA_CHILD_STATUS));
                assertFact(childFacts, FOLLOW_UP_STATUS, status);
                assertEquals("found".equals(status), relevant(activity, childFacts));
                for (String child : new String[] {"", "alive", "deceased"}) {
                    set(form, MAMA_CHILD_STATUS, child);
                    Facts deathFacts = nativeFacts(activity, field(form, MAMA_TAREHE_MTOTO_ALIFARIKI));
                    assertFact(deathFacts, FOLLOW_UP_STATUS, status);
                    assertFact(deathFacts, MAMA_CHILD_STATUS, child);
                    assertEquals(language + ":" + status + ":" + child,
                            "found".equals(status) && "deceased".equals(child), relevant(activity, deathFacts));
                    Facts noteFacts = nativeFacts(activity, field(form, MTOTO_ALIVE_NOTE));
                    assertEquals("found".equals(status) && "alive".equals(child), relevant(activity, noteFacts));
                }
            }
        }
    }

    @Test
    public void missingReasonsAndFacilitiesAreExclusiveAcrossAllBranches() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_child_eid_follow_up_status");
            JsonFormActivity activity = nativeActivity(form);
            for (String status : new String[] {"found", "not_found"}) {
                set(form, FOLLOW_UP_STATUS, status);
                Facts reasonFacts = nativeFacts(activity, field(form, MTOTO_NOT_FOUND_REASON));
                assertFact(reasonFacts, FOLLOW_UP_STATUS, status);
                assertEquals("not_found".equals(status), relevant(activity, reasonFacts));
                for (String reason : new String[] {"", "moved_area", "moved_facility", "other"}) {
                    set(form, MTOTO_NOT_FOUND_REASON, reason);
                    for (String[] branch : new String[][] {
                            {MTOTO_MOVED_AREA_FACILITY, "moved_area"},
                            {MTOTO_NEW_FACILITY, "moved_facility"},
                            {MTOTO_NOT_FOUND_REASON_OTHER, "other"}}) {
                        Facts facts = nativeFacts(activity, field(form, branch[0]));
                        assertFact(facts, FOLLOW_UP_STATUS, status);
                        assertFact(facts, MTOTO_NOT_FOUND_REASON, reason);
                        assertEquals(language + ":" + status + ":" + reason + ":" + branch[0],
                                "not_found".equals(status) && branch[1].equals(reason), relevant(activity, facts));
                    }
                }
            }
        }
    }

    @Test
    public void bilingualFormsHaveTheExactChoicesAndDistinctRequiredFacilityControl() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_child_eid_follow_up_status");
            assertOptions(form, FOLLOW_UP_STATUS, "found", "not_found");
            assertOptions(form, MAMA_CHILD_STATUS, "alive", "deceased");
            assertOptions(form, MTOTO_NOT_FOUND_REASON, "moved_area", "moved_facility", "other");
            assertEquals("3", field(form, CHILD_EID_FOLLOW_UP_SCHEMA).getString("value"));
            assertEquals("ui_only", field(form, CHILD_DOB).getString("openmrs_entity"));
            assertEquals("ui_only", field(form, MTOTO_MOVED_AREA_FACILITY).getString("openmrs_entity"));
            assertFalse(field(form, MTOTO_MOVED_AREA_FACILITY).has("v_required"));
            assertTrue(field(form, MTOTO_NEW_FACILITY).getJSONObject("v_required").getBoolean("value"));
            assertTrue(field(form, MAMA_TAREHE_MTOTO_ALIFARIKI).getJSONObject("v_required").getBoolean("value"));
            assertTrue(field(form, MTOTO_NOT_FOUND_REASON_OTHER).getJSONObject("v_required").getBoolean("value"));
            String label = field(form, MAMA_CHILD_STATUS).getString("label");
            assertEquals("json.form-sw".equals(language) ? "Hali ya mtoto" : "Child status", label);
        }
    }

    @Test
    public void nativeEventSerializationKeepsTheCanonicalFacilityAndNoUiOnlyCopies() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_child_eid_follow_up_status");
            set(form, FOLLOW_UP_STATUS, "not_found");
            set(form, MTOTO_NOT_FOUND_REASON, "moved_area");
            set(form, MTOTO_MOVED_AREA_FACILITY, "Test facility");
            set(form, CHILD_DOB, "2026-01-20");
            set(form, MTOTO_ALIVE_NOTE, "private-ui-state");
            org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils
                    .clearInactiveAnswers(form);
            org.smartregister.domain.tag.FormTag tag = new org.smartregister.domain.tag.FormTag();
            tag.providerId = "test-provider"; tag.locationId = "test-location";
            tag.appVersion = 1; tag.databaseVersion = 65;
            Object event = org.smartregister.util.JsonFormUtils.createEvent(
                    form.getJSONObject("step1").getJSONArray("fields"), form.getJSONObject("metadata"),
                    tag, "test-child", "Mother Mentor Child EID Follow Up", "ec_mothermentor_enroll_child_eid");
            Map<String, String> values = org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion
                    .observationValues(new JSONObject(new com.google.gson.Gson().toJson(event)).getJSONArray("obs"));
            assertEquals("3", values.get(CHILD_EID_FOLLOW_UP_SCHEMA));
            assertEquals("not_found", values.get(FOLLOW_UP_STATUS));
            assertEquals("Test facility", values.get(MTOTO_NEW_FACILITY));
            for (String key : new String[] {CHILD_DOB, MTOTO_MOVED_AREA_FACILITY, MTOTO_ALIVE_NOTE}) {
                assertFalse(key, values.containsKey(key));
            }
        }
    }

    private void assertOptions(JSONObject form, String key, String... expected) throws Exception {
        JSONArray options = field(form, key).getJSONArray("options");
        assertEquals(expected.length, options.length());
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], options.getJSONObject(i).getString("key"));
    }

    private JsonFormActivity nativeActivity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        ReflectionHelpers.setField(activity, "mJSONObject", form);
        activity.setRulesEngineFactory(new RulesEngineFactory(
                RuntimeEnvironment.getApplication(), Collections.emptyMap()));
        Map<String, JSONObject> nativeFields = ReflectionHelpers.getField(activity, "formFields");
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            nativeFields.put("step1_" + field.getString("key"), field);
        }
        return activity;
    }

    private Facts nativeFacts(JsonFormActivity activity, JSONObject field) throws Exception {
        JSONObject configuration = field.getJSONObject("relevance")
                .getJSONObject("rules-engine").getJSONObject("ex-rules");
        assertEquals(RULES, configuration.getString("rules-file"));
        String[] address = {"rules-engine", RULES, "step1_" + field.getString("key")};
        JSONObject source = activity.getObjectUsingAddress(address, false);
        Facts facts = ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
        assertFalse(facts.asMap().containsKey("value"));
        return facts;
    }

    private void assertFact(Facts facts, String key, String expected) {
        String name = "step1_" + key;
        assertTrue(name, facts.asMap().containsKey(name));
        Object actual = facts.get(name);
        assertEquals(name, expected, String.valueOf(actual));
    }

    private boolean relevant(JsonFormActivity activity, Facts facts) {
        return activity.getRulesEngineFactory().getRelevance(facts, RULES);
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertTrue(key, field != null && field.length() > 0);
        return field;
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        field(form, key).put("value", value);
    }

    private JSONObject form(String language, String name) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                        .open(language + "/" + name + ".json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }
}
