package org.smartregister.chw.mothermentor.forms;

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

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Actual SDK dependency discovery, rather than manually supplied MVEL facts. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidHeiNativeRulesTest {
    private static final String RULES = "mothermentor_child_eid_hei_relevance.yml";

    @Test
    public void firstReferralShowsNoTestingOrArrivalQuestionsInEitherLanguage() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            NativeForm form = new NativeForm(directory);
            form.answer(MTOTO_RUFAA_STATE_BEFORE, "none");
            form.answer(MAMA_HEI_DUE_TESTS, "[\"nine_months\"]");
            assertTrue(form.relevant(MTOTO_REFERRAL_AGREEMENT));
            assertEquals("none", form.facts(MTOTO_REFERRAL_AGREEMENT)
                    .get("step1_" + MTOTO_RUFAA_STATE_BEFORE));
            assertEquals("[\"nine_months\"]", form.facts(MTOTO_REFERRAL_AGREEMENT)
                    .get("step1_" + MAMA_HEI_DUE_TESTS));
            form.answer(CHILD_EID_TEST_TYPE, "eighteen_months");
            form.answer(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            form.answer(CHILD_EID_FINAL_HIV_TEST_DONE, "yes");
            for (String key : new String[] {CHILD_EID_TEST_TYPE, CHILD_EID_ARRIVED_AT_FACILITY,
                    CHILD_EID_FACILITY_ARRIVAL_DATE, CHILD_EID_FINAL_HIV_TEST_DONE,
                    CHILD_EID_FINAL_HIV_TEST_DATE, CHILD_EID_FINAL_HIV_RESULT}) {
                assertFalse(directory + ":" + key, form.relevant(key));
            }
            form.answer(MTOTO_REFERRAL_AGREEMENT, "agreed");
            assertTrue(form.relevant("child_eid_referral_agreed_note"));
            assertFalse(form.relevant("child_eid_referral_refused_note"));
            form.answer(MTOTO_REFERRAL_AGREEMENT, "refused");
            assertFalse(form.relevant("child_eid_referral_agreed_note"));
            assertTrue(form.relevant("child_eid_referral_refused_note"));
            form.answer(MAMA_HEI_DUE_TESTS, "[]");
            assertFalse(form.relevant(MTOTO_REFERRAL_AGREEMENT));
            assertFalse(form.relevant("child_eid_referral_refused_note"));
        }
    }

    @Test
    public void existingReturnQuestionsAppearOnlyForTheEligibleStageAndArrivalBranch() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            NativeForm form = new NativeForm(directory);
            form.answer(MTOTO_RUFAA_STATE_BEFORE, "referred");
            form.answer(MAMA_HEI_DUE_TESTS, "[\"nine_months\"]");
            form.answer(CHILD_EID_HEI_REVIEW_TESTS, "[\"nine_months\"]");
            assertFalse(form.relevant(MTOTO_REFERRAL_AGREEMENT));
            assertTrue(form.relevant(CHILD_EID_TEST_TYPE));
            form.answer(CHILD_EID_TEST_TYPE, "nine_months");
            assertTrue(form.relevant(CHILD_EID_ARRIVED_AT_FACILITY));
            assertFalse(form.relevant(CHILD_EID_FACILITY_ARRIVAL_DATE));
            form.answer(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            assertTrue(form.relevant(CHILD_EID_FACILITY_ARRIVAL_DATE));
            assertFalse(form.relevant(CHILD_EID_FINAL_HIV_TEST_DONE));
            form.answer(CHILD_EID_TEST_TYPE, "eighteen_months");
            assertFalse(form.relevant(CHILD_EID_ARRIVED_AT_FACILITY));
            assertFalse(form.relevant(CHILD_EID_FINAL_HIV_TEST_DONE));
            form.answer(MAMA_HEI_DUE_TESTS, "[\"eighteen_months\"]");
            form.answer(CHILD_EID_HEI_REVIEW_TESTS, "[\"eighteen_months\"]");
            assertTrue(form.relevant(CHILD_EID_ARRIVED_AT_FACILITY));
            assertTrue(form.relevant(CHILD_EID_FINAL_HIV_TEST_DONE));
            form.answer(CHILD_EID_FINAL_HIV_TEST_DONE, "yes");
            assertTrue(form.relevant(CHILD_EID_FINAL_HIV_RESULT));
            assertTrue(form.relevant(CHILD_EID_FINAL_HIV_TEST_DATE));
            form.answer(MAMA_HEI_DUE_TESTS, "[]");
            form.answer(CHILD_EID_HEI_REVIEW_TESTS, "[]");
            assertFalse(form.relevant(CHILD_EID_FINAL_HIV_TEST_DONE));
            assertFalse(form.relevant(CHILD_EID_FINAL_HIV_RESULT));
        }
    }

    @Test
    public void cessationDateAndHintTrackBreastfeedingWithoutHiddenRuleFacts() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            NativeForm form = new NativeForm(directory);
            form.answer(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
            assertFalse(form.relevant(MAMA_TAREHE_ACHA_KUNYONYESHA));
            form.answer(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
            assertEquals("not_breastfeeding", form.facts(MAMA_TAREHE_ACHA_KUNYONYESHA)
                    .get("step1_" + MAMA_BREASTFEEDING_STATUS));
            assertTrue(form.relevant(MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertTrue(form.relevant("child_eid_weaning_hint"));
        }
    }

    private static final class NativeForm {
        private final JSONObject form;
        private final JsonFormActivity activity;

        NativeForm(String directory) throws Exception {
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(directory + "/mothermentor_child_eid_hei.json"), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            activity = Robolectric.buildActivity(JsonFormActivity.class).get();
            ReflectionHelpers.setField(activity, "mJSONObject", form);
            activity.setRulesEngineFactory(new RulesEngineFactory(
                    RuntimeEnvironment.getApplication(), Collections.emptyMap()));
            Map<String, JSONObject> fields = ReflectionHelpers.getField(activity, "formFields");
            JSONArray definitions = form.getJSONObject("step1").getJSONArray("fields");
            for (int i = 0; i < definitions.length(); i++) {
                JSONObject field = definitions.getJSONObject(i);
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
