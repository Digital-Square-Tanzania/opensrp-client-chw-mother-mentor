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
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildHeiNumberTest {
    private static final String FOLLOW_UP_NUMBER = DBConstants.KEY.HEI_FOLLOW_UP_NUMBER;
    private static final String[] VALID_BIRTH_ORDERS = {"1", "01", "2", "02", "9", "09", "10", "20"};
    private static final String[] INVALID_BIRTH_ORDERS = {"0", "00", "21", "99", "-1", "001", "aa", ""};

    @Test
    public void bothLanguagesAllowOnlyBirthOrdersOneToTwentyToBeEdited() throws Exception {
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
            assertEquals(
                    "json.form-sw".equals(language) ? "Mtoto wa ngapi kuzaliwa" : "Child's birth order",
                    suffix.getString("hint"));
            String pattern = suffix.getJSONObject("v_regex").getString("value");
            String heiPattern = number.getJSONObject("v_regex").getString("value");
            for (String order : VALID_BIRTH_ORDERS) {
                assertTrue(order, order.matches(pattern));
                assertTrue(
                        heiNumber(order),
                        heiNumber(order).matches(heiPattern));
            }
            for (String order : INVALID_BIRTH_ORDERS) {
                assertFalse(order, order.matches(pattern));
            }
            for (String invalidHei : new String[] {
                    "12-34-5678-123456-C00", "12-34-5678-123456-C21",
                    "12-34-5678-123456-C-01", "12-34-5678-123456-C1", "invalid-C01"}) {
                assertFalse(invalidHei, invalidHei.matches(heiPattern));
            }
            assertEquals("equalTo(., \"yes\")", suffix.getJSONObject("relevance")
                    .getJSONObject("step1:hei_registered").getString("ex"));
        }
    }

    @Test
    public void nativeRulesGenerateTheHeiNumberFromValidBirthOrdersInBothLanguages() throws Exception {
        RulesEngineFactory rules =
                new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap());
        for (String language : languages()) {
            JSONObject number = field(form(language), DBConstants.KEY.HEI_NUMBER);
            for (String order : VALID_BIRTH_ORDERS) {
                assertEquals(
                        order,
                        heiNumber(order),
                        calculate(rules, number, "yes", "12-34-5678-123456", order));
            }
            for (String order : INVALID_BIRTH_ORDERS) {
                assertEquals(order, "", calculate(rules, number, "yes", "12-34-5678-123456", order));
            }
            assertEquals("98-76-5432-654321-C02", calculate(rules, number, "yes", "98-76-5432-654321", "02"));
            assertEquals("12-34-5678-123456-C01", calculate(rules, number, "yes", " 12-34-5678-123456 ", "1"));
            assertEquals("", calculate(rules, number, "no", "12-34-5678-123456", "01"));
            for (String invalidCtc : new String[] {"", "   ", "12345678123456", "12-34-5678-12345", "bad"}) {
                assertEquals("", calculate(rules, number, "yes", invalidCtc, "01"));
            }
            assertEquals("", calculate(rules, number, "yes", null, "01"));
            assertEquals("", calculate(rules, number, "yes", "12-34-5678-123456", null));
        }
    }

    @Test
    public void boundMotherCtcRemainsAvailableBeforeAndAfterHeiRegistrationIsAnswered()
            throws Exception {
        RulesEngineFactory rules =
                new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap());
        for (String language : languages()) {
            JSONObject form = form(language);
            JSONObject motherCtc = field(form, DBConstants.KEY.MOTHER_CTC_NUMBER);
            motherCtc.put("value", "12-34-5678-123456");
            // Native forms erase values when conditional relevance hides an edit-text field.
            // Maternal identity must remain available even before the HEI question is answered.
            assertFalse(motherCtc.has("relevance"));
            assertTrue(motherCtc.getBoolean("read_only"));
            JSONObject registered = field(form, DBConstants.KEY.HEI_REGISTERED);
            JSONObject suffix = field(form, FOLLOW_UP_NUMBER);

            registered.put("value", "");
            suffix.put("value", "");
            assertEquals("", calculateBoundForm(rules, form));

            registered.put("value", "yes");
            suffix.put("value", "07");
            assertEquals("12-34-5678-123456-C07", calculateBoundForm(rules, form));

            registered.put("value", "no");
            assertEquals("", calculateBoundForm(rules, form));
            assertEquals("12-34-5678-123456", new NativeValues().value(motherCtc));

            registered.put("value", "yes");
            suffix.put("value", "08");
            assertEquals("12-34-5678-123456-C08", calculateBoundForm(rules, form));
        }
    }

    private String heiNumber(String order) {
        return "12-34-5678-123456-C" + String.format(Locale.ROOT, "%02d", Integer.parseInt(order));
    }

    private String calculateBoundForm(RulesEngineFactory rules, JSONObject form) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_hei_number");
        NativeValues values = new NativeValues();
        for (String key :
                new String[] {
                    DBConstants.KEY.HEI_REGISTERED,
                    DBConstants.KEY.MOTHER_CTC_NUMBER,
                    FOLLOW_UP_NUMBER
                }) {
            facts.put("step1_" + key, values.value(field(form, key)));
        }
        String file = field(form, DBConstants.KEY.HEI_NUMBER)
                .getJSONObject("calculation").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return rules.getCalculation(facts, file);
    }

    private String calculate(RulesEngineFactory rules, JSONObject field, String registered,
            String motherCtc, String suffix) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_hei_number");
        facts.put("step1_hei_registered", registered);
        facts.put("step1_mother_ctc_number", motherCtc);
        Object nativeValue = null;
        if (suffix != null) {
            JSONObject suffixField = field(form("json.form"), FOLLOW_UP_NUMBER).put("value", suffix);
            nativeValue = new NativeValues().value(suffixField);
            assertEquals(suffix, nativeValue);
        }
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
