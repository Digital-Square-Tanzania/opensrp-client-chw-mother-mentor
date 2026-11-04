package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.vijay.jsonwizard.rules.RuleConstant;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdFollowUpFormTest {
    @Test
    public void householdQuestionsAndNativeRelevanceMatchBothLanguages() throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(language);
            assertEquals("Mother Mentor Household Follow-up Status", form.getString("encounter_type"));
            assertEquals("2", field(form, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA).getString("value"));
            JSONObject type = field(form, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE);
            assertFalse(type.has("value"));
            assertOptions(type, "new", "repeat");
            JSONObject status = field(form, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS);
            JSONObject reason = field(form, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON);
            JSONObject other = field(form, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER);
            assertOptions(status, "found", "not_found");
            assertOptions(reason, "moved", "house_closed", "other");
            assertTrue(relevant(status, "repeat", "", ""));
            assertFalse(relevant(status, "new", "not_found", "other"));
            assertTrue(relevant(reason, "repeat", "not_found", ""));
            assertFalse(relevant(reason, "repeat", "found", ""));
            assertFalse(relevant(reason, "new", "not_found", ""));
            assertTrue(relevant(other, "repeat", "not_found", "other"));
            assertFalse(relevant(other, "repeat", "not_found", "house_closed"));
            assertFalse(relevant(other, "repeat", "found", "other"));
            assertFalse(relevant(other, "new", "not_found", "other"));
            for (JSONObject required : new JSONObject[] {type, status, reason, other}) {
                assertTrue(required.getJSONObject("v_required").getBoolean("value"));
            }
            assertEquals("json.form-sw".equals(language)
                    ? "Hali ya Ufuatiliaji wa Kaya" : "Household Follow-up Status",
                    form.getJSONObject("step1").getString("title"));
            if ("json.form-sw".equals(language)) {
                assertEquals("Imepatikana", status.getJSONArray("options").getJSONObject(0).getString("text"));
                assertEquals("Haijapatikana", status.getJSONArray("options").getJSONObject(1).getString("text"));
                assertEquals("Kaya nzima imehama eneo", reason.getJSONArray("options").getJSONObject(0).getString("text"));
            }
        }
    }

    private boolean relevant(JSONObject field, String type, String status, String reason) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_" + field.getString("key"));
        facts.put("step1_household_visit_type", type);
        facts.put("step1_household_follow_up_status", status);
        facts.put("step1_household_not_found_reason", reason);
        String rules = field.getJSONObject("relevance").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap())
                .getRelevance(facts, rules);
    }

    private JSONObject form(String directory) throws Exception {
        try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets().open(
                directory + "/mothermentor_household_follow_up_status.json"), "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private void assertOptions(JSONObject field, String... keys) throws Exception {
        JSONArray options = field.getJSONArray("options");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < options.length(); i++) actual.add(options.getJSONObject(i).getString("key"));
        assertEquals(new HashSet<>(Arrays.asList(keys)), actual);
        assertEquals(keys.length, options.length());
    }
}
