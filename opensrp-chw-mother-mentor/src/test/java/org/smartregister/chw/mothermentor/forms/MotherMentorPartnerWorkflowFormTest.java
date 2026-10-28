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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerWorkflowFormTest {
    @Test
    public void tracingHasTwoStatusesAndConditionalAgreementOrMissingReasonInBothLanguages()
            throws Exception {
        for (String locale : locales()) {
            JSONObject form = form(locale, "mothermentor_partner_follow_up_status");
            assertOptions(field(form, "follow_up_status"), "found", "not_found");
            assertOptions(field(form, "partner_facility_agreement"), "agreed", "refused");
            assertOptions(field(form, "partner_not_found_reason"), "deceased", "moved", "travelled", "other");
            assertTrue(relevant(field(form, "partner_facility_agreement"), "found", "", "", ""));
            assertFalse(relevant(field(form, "partner_facility_agreement"), "not_found", "", "", ""));
            assertTrue(relevant(field(form, "partner_not_found_reason"), "not_found", "", "", ""));
            assertFalse(relevant(field(form, "partner_not_found_reason"), "found", "", "", ""));
            assertTrue(relevant(field(form, "partner_not_found_reason_other"), "not_found", "other", "", ""));
            assertFalse(relevant(field(form, "partner_not_found_reason_other"), "not_found", "travelled", "", ""));
            assertFalse(relevant(field(form, "partner_not_found_reason_other"), "found", "other", "", ""));
            assertTrue(field(form, "partner_not_found_reason_other").getJSONObject("v_required").getBoolean("value"));
        }
    }

    @Test
    public void eachTestCapturesAResultOrReasonAndOnlyHivAllowsAlreadyInfected() throws Exception {
        for (String locale : locales()) {
            for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
                JSONObject form = form(locale, "mothermentor_partner_" + disease + "_test");
                String prefix = "partner_" + disease;
                assertOptions(field(form, prefix + "_tested"), "yes", "no");
                JSONObject result = field(form, prefix + "_result");
                assertOptions(result, "positive", "negative");
                JSONObject reason = field(form, prefix + "_not_tested_reason");
                if ("hiv".equals(disease)) assertOptions(reason, "already_infected", "stock_out", "refused", "other");
                else assertOptions(reason, "stock_out", "refused", "other");
                assertTrue(relevant(result, "", "", "yes", ""));
                assertFalse(relevant(result, "", "", "no", ""));
                assertTrue(relevant(reason, "", "", "no", ""));
                assertFalse(relevant(reason, "", "", "yes", ""));
                JSONObject other = field(form, prefix + "_not_tested_reason_other");
                assertTrue(relevant(other, "", "", "no", "other"));
                assertFalse(relevant(other, "", "", "no", "stock_out"));
                assertFalse(relevant(other, "", "", "yes", "other"));
                assertTrue(other.getJSONObject("v_required").getBoolean("value"));
            }
        }
    }

    @Test
    public void educationRequiresSelectedTopicsOnlyWhenProvidedAndCommentsStayOptional()
            throws Exception {
        for (String locale : locales()) {
            JSONObject education = form(locale, "mothermentor_partner_education");
            assertOptions(field(education, "partner_education_provided"), "yes", "no");
            JSONObject topics = field(education, "partner_education_topics");
            assertOptions(topics, "hiv", "syphilis", "hepatitis_b");
            assertTrue(topics.getJSONObject("v_required").getBoolean("value"));
            assertTrue(relevant(topics, "", "", "yes", ""));
            assertFalse(relevant(topics, "", "", "no", ""));
            JSONObject comment = field(form(locale, "mothermentor_partner_follow_up_comment"), "comments");
            assertFalse(comment.has("v_required"));
            assertEquals("100", comment.getJSONObject("v_max_length").getString("value"));
        }
    }

    private boolean relevant(JSONObject field, String status, String missingReason,
            String tested, String reason) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_" + field.getString("key"));
        facts.put("step1_follow_up_status", status);
        facts.put("step1_partner_not_found_reason", missingReason);
        facts.put("step1_partner_education_provided", tested);
        for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            facts.put("step1_partner_" + disease + "_tested", tested);
            facts.put("step1_partner_" + disease + "_not_tested_reason", reason);
        }
        String rules = field.getJSONObject("relevance").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap())
                .getRelevance(facts, rules);
    }

    private void assertOptions(JSONObject field, String... keys) throws Exception {
        JSONArray options = field.getJSONArray("options");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < options.length(); i++) actual.add(options.getJSONObject(i).getString("key"));
        assertEquals(new HashSet<>(Arrays.asList(keys)), actual);
        assertEquals(keys.length, options.length());
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private JSONObject form(String locale, String name) throws Exception {
        String path = locale + "/" + name + ".json";
        try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets().open(path), "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private String[] locales() {
        return new String[] {"json.form", "json.form-sw"};
    }
}
