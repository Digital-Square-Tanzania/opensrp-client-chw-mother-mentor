package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.*;
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
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

/** Exercises dependency discovery and fact extraction from the actual native SDK. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildNativeRulesTest {
    private static final String RULES = "mothermentor_mother_child_relevance.yml";

    @Test
    public void nativeFactsIncludeTheHiddenPathAndEveryActiveBranchDependencyInBothLanguages() throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            NativeForm nativeForm = new NativeForm(language);
            nativeForm.answer(MAMA_CHILD_PATH, "true");
            nativeForm.answer(MAMA_CHILD_STATUS, "alive");
            nativeForm.answer(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
            nativeForm.answer(MAMA_HEI_DUE_TESTS, "[\"nine_months\",\"three_months_after_weaning\"]");
            nativeForm.answer(MAMA_HEI_TEST_DONE, "yes");
            nativeForm.answer(MAMA_HEI_TEST_RESULT, "positive");
            for (String key : new String[] {MAMA_CHILD_STATUS, MAMA_CHILD_AGE_NOTE,
                    MAMA_HEI_NEXT_TEST_NOTE, MAMA_BREASTFEEDING_STATUS,
                    MAMA_TAREHE_ACHA_KUNYONYESHA, MAMA_HEI_TEST_DONE,
                    MAMA_HEI_TEST_TYPE_CHOICE, MAMA_HEI_TEST_DATE,
                    MAMA_HEI_TEST_RESULT, HEI_CHANYA_IMETHIBITISHWA}) {
                Facts facts = nativeForm.facts(key);
                assertTrue(language + ":" + key + " missing path fact: " + facts.asMap().keySet(),
                        facts.asMap().containsKey("step1_" + MAMA_CHILD_PATH));
                assertEquals("true", facts.get("step1_" + MAMA_CHILD_PATH));
                assertTrue(language + ":" + key, nativeForm.relevant(key));
            }
            Facts confirmation = nativeForm.facts(HEI_CHANYA_IMETHIBITISHWA);
            assertEquals("alive", confirmation.get("step1_" + MAMA_CHILD_STATUS));
            assertEquals("yes", confirmation.get("step1_" + MAMA_HEI_TEST_DONE));
            assertEquals("positive", confirmation.get("step1_" + MAMA_HEI_TEST_RESULT));
            assertEquals("[\"nine_months\",\"three_months_after_weaning\"]",
                    confirmation.get("step1_" + MAMA_HEI_DUE_TESTS));
            assertFalse(nativeForm.relevant(MAMA_TAREHE_MTOTO_ALIFARIKI));
        }
    }

    @Test
    public void nativeVisibilityTracksNoDueTestSingleStageResultsAndChildDeath() throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            NativeForm nativeForm = new NativeForm(language);
            nativeForm.answer(MAMA_CHILD_PATH, "true");
            assertTrue(nativeForm.relevant(MAMA_CHILD_STATUS));
            assertFalse(nativeForm.relevant(MAMA_BREASTFEEDING_STATUS));
            nativeForm.answer(MAMA_CHILD_STATUS, "alive");
            nativeForm.answer(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
            assertTrue(nativeForm.relevant(MAMA_BREASTFEEDING_STATUS));
            assertFalse(nativeForm.relevant(MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertFalse(nativeForm.relevant(MAMA_HEI_TEST_DONE));
            nativeForm.answer(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
            assertTrue(nativeForm.relevant(MAMA_TAREHE_ACHA_KUNYONYESHA));
            nativeForm.answer(MAMA_HEI_DUE_TESTS, "[\"eighteen_months\"]");
            assertTrue(nativeForm.relevant(MAMA_HEI_TEST_DONE));
            nativeForm.answer(MAMA_HEI_TEST_DONE, "yes");
            assertFalse(nativeForm.relevant(MAMA_HEI_TEST_TYPE_CHOICE));
            assertTrue(nativeForm.relevant(MAMA_HEI_TEST_DATE));
            assertTrue(nativeForm.relevant(MAMA_HEI_TEST_RESULT));
            nativeForm.answer(MAMA_HEI_TEST_RESULT, "positive");
            assertTrue(nativeForm.relevant(HEI_CHANYA_IMETHIBITISHWA));
            nativeForm.answer(MAMA_HEI_TEST_RESULT, "negative");
            assertFalse(nativeForm.relevant(HEI_CHANYA_IMETHIBITISHWA));
            nativeForm.answer(MAMA_HEI_TEST_DONE, "no");
            assertFalse(nativeForm.relevant(MAMA_HEI_TEST_DATE));
            assertFalse(nativeForm.relevant(MAMA_HEI_TEST_RESULT));
            nativeForm.answer(MAMA_CHILD_STATUS, "deceased");
            assertTrue(nativeForm.relevant(MAMA_TAREHE_MTOTO_ALIFARIKI));
            assertFalse(nativeForm.relevant(MAMA_BREASTFEEDING_STATUS));
            assertFalse(nativeForm.relevant(MAMA_HEI_TEST_DONE));
            nativeForm.answer(MAMA_CHILD_PATH, "false");
            assertFalse(nativeForm.relevant(MAMA_CHILD_STATUS));
            assertFalse(nativeForm.relevant(MAMA_TAREHE_MTOTO_ALIFARIKI));
        }
    }

    private static final class NativeForm {
        private final JSONObject form;
        private final JsonFormActivity activity;

        NativeForm(String language) throws Exception {
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(language + "/mothermentor_visit_type.json"), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            activity = Robolectric.buildActivity(JsonFormActivity.class).get();
            ReflectionHelpers.setField(activity, "mJSONObject", form);
            activity.setRulesEngineFactory(new RulesEngineFactory(
                    RuntimeEnvironment.getApplication(), Collections.emptyMap()));
            Map<String, JSONObject> fields = ReflectionHelpers.getField(activity, "formFields");
            JSONArray definitions = form.getJSONObject("step1").getJSONArray("fields");
            for (int index = 0; index < definitions.length(); index++) {
                JSONObject field = definitions.getJSONObject(index);
                fields.put("step1_" + field.getString("key"), field);
            }
        }

        void answer(String key, String value) throws Exception {
            FormUtils.getFieldFromForm(form, key).put("value", value);
        }

        Facts facts(String key) throws Exception {
            JSONObject field = FormUtils.getFieldFromForm(form, key);
            String file = field.getJSONObject("relevance").getJSONObject("rules-engine")
                    .getJSONObject("ex-rules").getString("rules-file");
            assertEquals(RULES, file);
            String[] address = {"rules-engine", file, "step1_" + key};
            JSONObject source = activity.getObjectUsingAddress(address, false);
            return ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                    ClassParameter.from(String[].class, address),
                    ClassParameter.from(JSONObject.class, source));
        }

        boolean relevant(String key) throws Exception {
            return activity.getRulesEngineFactory().getRelevance(facts(key), RULES);
        }
    }
}
