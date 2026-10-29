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

import java.util.Collections;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidResultsStatusTest {
    private static final String RESULTS_RETURNED = DBConstants.KEY.LAST_TEST_RESULTS_RETURNED;
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};
    private static final String[] TESTS_PERFORMED = {"at_birth", "dbs_6", "dbs_9"};

    @Test
    public void bothLanguagesAskForResultsStatusBeforeTheResultAndKeepTheNextStep()
            throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language);
            assertEquals("4", form.getString("count"));
            assertEquals("Mother Mentor Enroll Child Eid", form.getString("encounter_type"));
            JSONObject step = form.getJSONObject("step3");
            assertEquals("step4", step.getString("next"));
            assertNotNull(form.getJSONObject("step4"));
            JSONArray fields = step.getJSONArray("fields");
            assertEquals(3, fields.length());
            assertEquals(DBConstants.KEY.LAST_TEST, fields.getJSONObject(0).getString("key"));
            assertEquals(RESULTS_RETURNED, fields.getJSONObject(1).getString("key"));
            assertEquals(DBConstants.KEY.LAST_TEST_RESULT, fields.getJSONObject(2).getString("key"));

            JSONObject returned = field(form, RESULTS_RETURNED);
            assertEquals("native_radio", returned.getString("type"));
            assertEquals("concept", returned.getString("openmrs_entity"));
            assertEquals(RESULTS_RETURNED, returned.getString("openmrs_entity_id"));
            assertTrue(returned.getJSONObject("v_required").getBoolean("value"));
            assertOptions(returned, "yes", "no");
            boolean swahili = "json.form-sw".equals(language);
            assertEquals(swahili ? "Majibu yamerudi?" : "Have the results returned?",
                    returned.getString("label"));
            assertEquals(swahili ? "Ndiyo" : "Yes",
                    returned.getJSONArray("options").getJSONObject(0).getString("text"));
            assertEquals(swahili ? "Hapana" : "No",
                    returned.getJSONArray("options").getJSONObject(1).getString("text"));
        }
    }

    @Test
    public void resultsStatusIsAskedOnlyWhenATestHasBeenPerformedInBothLanguages()
            throws Exception {
        for (String language : LANGUAGES) {
            JSONObject returned = field(form(language), RESULTS_RETURNED);
            for (String test : TESTS_PERFORMED) {
                for (String status : new String[] {"", "yes", "no"}) {
                    assertTrue(relevant(returned, test, status));
                }
            }
            for (String test : new String[] {"", "no_test_has_been_done_yet"}) {
                for (String status : new String[] {"", "yes", "no"}) {
                    assertFalse(relevant(returned, test, status));
                }
            }
        }
    }

    @Test
    public void resultIsRequiredOnlyAfterResultsHaveReturnedInBothLanguages()
            throws Exception {
        for (String language : LANGUAGES) {
            JSONObject result = field(form(language), DBConstants.KEY.LAST_TEST_RESULT);
            assertTrue(result.getJSONObject("v_required").getBoolean("value"));
            for (String test : TESTS_PERFORMED) {
                assertTrue(relevant(result, test, "yes"));
                assertFalse(relevant(result, test, "no"));
                assertFalse(relevant(result, test, ""));
            }
            for (String test : new String[] {"", "no_test_has_been_done_yet"}) {
                for (String status : new String[] {"", "yes", "no"}) {
                    assertFalse(relevant(result, test, status));
                }
            }
        }
    }

    @Test
    public void returnedResultsKeepPositiveAndNegativeCodesWithoutAPendingChoice()
            throws Exception {
        for (String language : LANGUAGES) {
            JSONObject result = field(form(language), DBConstants.KEY.LAST_TEST_RESULT);
            assertOptions(result, "negative", "positive");
            boolean swahili = "json.form-sw".equals(language);
            assertEquals(swahili ? "Hasi" : "Negative",
                    result.getJSONArray("options").getJSONObject(0).getString("text"));
            assertEquals(swahili ? "Chanya" : "Positive",
                    result.getJSONArray("options").getJSONObject(1).getString("text"));
        }
    }

    private boolean relevant(JSONObject field, String test, String returned) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step3_" + field.getString("key"));
        facts.put("step3_" + DBConstants.KEY.LAST_TEST, test);
        facts.put("step3_" + RESULTS_RETURNED, returned);
        String rules = field.getJSONObject("relevance").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap())
                .getRelevance(facts, rules);
    }

    private void assertOptions(JSONObject field, String... expected) throws Exception {
        JSONArray options = field.getJSONArray("options");
        assertEquals(expected.length, options.length());
        for (int i = 0; i < expected.length; i++) {
            JSONObject option = options.getJSONObject(i);
            assertEquals(expected[i], option.getString("key"));
            assertEquals(expected[i], option.getString("openmrs_entity_id"));
        }
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private JSONObject form(String language) throws Exception {
        try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                .open(language + "/mothermentor_enroll_child_eid.json"), "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }
}
