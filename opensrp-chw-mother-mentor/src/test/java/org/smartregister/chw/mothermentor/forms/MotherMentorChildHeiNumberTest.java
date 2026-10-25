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
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildHeiNumberTest {
    private static final String FOLLOW_UP_NUMBER = "hei_follow_up_number";

    @Test
    public void bothLanguagesAllowOnlyTheTwoDigitSuffixToBeEdited() throws Exception {
        for (String language : languages()) {
            JSONObject form = form(language);
            JSONObject number = field(form, DBConstants.KEY.HEI_NUMBER);
            assertEquals("edit_text", number.getString("type"));
            assertTrue(number.getBoolean("read_only"));
            assertFalse(number.has("editable"));
            assertFalse(number.has("mask"));
            assertEquals("edit_text", field(form, DBConstants.KEY.MOTHER_CTC_NUMBER).getString("type"));
            assertTrue(field(form, DBConstants.KEY.MOTHER_CTC_NUMBER).getBoolean("read_only"));
            JSONObject suffix = field(form, FOLLOW_UP_NUMBER);
            assertTrue(suffix.getJSONObject("v_required").getBoolean("value"));
            assertFalse(suffix.has("edit_type"));
            String pattern = suffix.getJSONObject("v_regex").getString("value");
            assertTrue("01".matches(pattern));
            assertTrue("99".matches(pattern));
            assertFalse("1".matches(pattern));
            assertFalse("123".matches(pattern));
            assertFalse("aa".matches(pattern));
            assertFalse("".matches(pattern));
            assertEquals("equalTo(., \"yes\")", suffix.getJSONObject("relevance")
                    .getJSONObject("step1:hei_registered").getString("ex"));
        }
    }

    @Test
    public void nativeRulesGenerateTheHeiNumberAndKeepLeadingZerosInBothLanguages() throws Exception {
        RulesEngineFactory rules =
                new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap());
        for (String language : languages()) {
            JSONObject number = field(form(language), DBConstants.KEY.HEI_NUMBER);
            assertEquals("12-34-5678-123456-C-01", calculate(rules, number, "yes", "12-34-5678-123456", "01"));
            assertEquals("12-34-5678-123456-C-00", calculate(rules, number, "yes", "12-34-5678-123456", "00"));
            assertEquals("12-34-5678-123456-C-99", calculate(rules, number, "yes", "12-34-5678-123456", "99"));
            assertEquals("98-76-5432-654321-C-02", calculate(rules, number, "yes", "98-76-5432-654321", "02"));
            assertEquals("12-34-5678-123456-C-", calculate(rules, number, "yes", "12-34-5678-123456", ""));
            assertEquals("", calculate(rules, number, "no", "12-34-5678-123456", "01"));
            assertEquals("", calculate(rules, number, "yes", "", "01"));
            assertEquals("", calculate(rules, number, "yes", "   ", "01"));
        }
    }

    private String calculate(RulesEngineFactory rules, JSONObject field, String registered,
            String motherCtc, String suffix) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_hei_number");
        facts.put("step1_hei_registered", registered);
        facts.put("step1_mother_ctc_number", motherCtc);
        JSONObject suffixField = field(form("json.form"), FOLLOW_UP_NUMBER).put("value", suffix);
        Object nativeValue = new NativeValues().value(suffixField);
        assertEquals(suffix, nativeValue);
        facts.put("step1_" + FOLLOW_UP_NUMBER, nativeValue);
        String file = field.getJSONObject("calculation").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return rules.getCalculation(facts, file);
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private JSONObject form(String language) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                .open(language + "/mothermentor_enroll_child_eid.json")) {
            byte[] bytes = new byte[input.available()];
            assertEquals(bytes.length, input.read(bytes));
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private String[] languages() {
        return new String[] {"json.form", "json.form-sw"};
    }

    private static class NativeValues extends com.vijay.jsonwizard.utils.Utils {
        Object value(JSONObject field) throws Exception {
            return getValue(field);
        }
    }
}
