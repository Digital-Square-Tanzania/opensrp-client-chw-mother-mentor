package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.repository.AllSharedPreferences;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorEnrollmentPregnancyTest {
    private static final String PREGNANCY_SELECTION = "pregnancy_status_selection";

    @Test
    public void enrollmentSeparatesPregnancyFromRequiredConditionalBreastfeedingInBothLanguages()
            throws Exception {
        RulesEngineFactory rules =
                new RulesEngineFactory(RuntimeEnvironment.application, Collections.emptyMap());
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(directory);
            JSONObject pregnancy = FormUtils.getFieldFromForm(form, PREGNANCY_SELECTION);
            JSONObject breastfeeding =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.BREASTFEEDING_STATUS);
            assertOptions(pregnancy, "pregnant", "not_pregnant");
            assertOptions(breastfeeding, "breast_feeding", "not_breast_feeding");
            assertTrue(breastfeeding.getJSONObject("v_required").getBoolean("value"));
            assertEquals(
                    directory.equals("json.form") ? "Breastfeeding" : "Ananyonyesha",
                    breastfeeding.getJSONArray("options").getJSONObject(0).getString("text"));
            assertEquals(
                    directory.equals("json.form") ? "Not breastfeeding" : "Hanyonyeshi",
                    breastfeeding.getJSONArray("options").getJSONObject(1).getString("text"));

            assertFalse(relevant(rules, breastfeeding, "", ""));
            assertFalse(relevant(rules, breastfeeding, "pregnant", ""));
            assertTrue(relevant(rules, breastfeeding, "not_pregnant", ""));

            // Retain the existing nonpregnant-mother child DOB and age eligibility flow.
            JSONObject childDob = FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_DOB);
            assertFalse(relevant(rules, childDob, "pregnant", ""));
            assertTrue(relevant(rules, childDob, "not_pregnant", "breast_feeding"));
            assertTrue(relevant(rules, childDob, "not_pregnant", "not_breast_feeding"));
        }
    }

    @Test
    public void nativeCalculationAndEventSerializationKeepExistingStatusCodesInBothLanguages()
            throws Exception {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        try {
            RulesEngineFactory rules =
                    new RulesEngineFactory(RuntimeEnvironment.application, Collections.emptyMap());
            for (String directory : new String[] {"json.form", "json.form-sw"}) {
                assertSavedStatus(rules, directory, "pregnant", "", "pregnant");
                assertSavedStatus(rules, directory, "not_pregnant", "breast_feeding", "breast_feeding");
                assertSavedStatus(rules, directory, "not_pregnant", "not_breast_feeding", "not_pregnant");
                // Switching pregnancy must override a previous breastfeeding selection immediately.
                assertEquals(
                        "pregnant", calculate(rules, form(directory), "pregnant", "breast_feeding"));
                assertEquals("", calculate(rules, form(directory), "not_pregnant", ""));
                assertEquals("", calculate(rules, form(directory), "", "breast_feeding"));
            }
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private void assertSavedStatus(
            RulesEngineFactory rules,
            String directory,
            String pregnancy,
            String breastfeeding,
            String expected)
            throws Exception {
        JSONObject form = form(directory);
        form.put("entity_id", "mother-1049");
        FormUtils.getFieldFromForm(form, PREGNANCY_SELECTION).put("value", pregnancy);
        FormUtils.getFieldFromForm(form, DBConstants.KEY.BREASTFEEDING_STATUS)
                .put("value", breastfeeding);
        JSONObject storedStatus = FormUtils.getFieldFromForm(form, DBConstants.KEY.PREGNANCY_STATUS);
        assertEquals("hidden", storedStatus.getString("type"));
        storedStatus.put("value", calculate(rules, form, pregnancy, breastfeeding));

        Event event =
                MotherMentorJsonFormUtils.processJsonForm(
                        mock(AllSharedPreferences.class), form.toString());
        assertNotNull(event);
        assertEquals("mother-1049", event.getBaseEntityId());
        assertEquals(expected, observation(event, DBConstants.KEY.PREGNANCY_STATUS));
        assertEquals(pregnancy, observation(event, PREGNANCY_SELECTION));
        if (!breastfeeding.isEmpty()) {
            assertEquals(breastfeeding, observation(event, DBConstants.KEY.BREASTFEEDING_STATUS));
        }
    }

    private String calculate(
            RulesEngineFactory rules, JSONObject form, String pregnancy, String breastfeeding)
            throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, DBConstants.KEY.PREGNANCY_STATUS);
        Facts facts = facts(field, pregnancy, breastfeeding);
        return rules.getCalculation(facts, ruleFile(field, "calculation"));
    }

    private boolean relevant(
            RulesEngineFactory rules, JSONObject field, String pregnancy, String breastfeeding)
            throws Exception {
        return rules.getRelevance(facts(field, pregnancy, breastfeeding), ruleFile(field, "relevance"));
    }

    private Facts facts(JSONObject field, String pregnancy, String breastfeeding) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step3_" + field.getString("key"));
        facts.put("step3_" + PREGNANCY_SELECTION, pregnancy);
        facts.put("step3_" + DBConstants.KEY.BREASTFEEDING_STATUS, breastfeeding);
        return facts;
    }

    private String ruleFile(JSONObject field, String property) throws Exception {
        return field.getJSONObject(property)
                .getJSONObject("rules-engine")
                .getJSONObject("ex-rules")
                .getString("rules-file");
    }

    private void assertOptions(JSONObject field, String... expected) throws Exception {
        JSONArray options = field.getJSONArray("options");
        assertEquals(expected.length, options.length());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], options.getJSONObject(i).getString("key"));
        }
    }

    private String observation(Event event, String fieldCode) {
        for (Obs observation : event.getObs()) {
            if (fieldCode.equals(observation.getFieldCode())) {
                return observation.getValues().get(0).toString();
            }
        }
        throw new AssertionError("Missing enrollment observation: " + fieldCode);
    }

    private JSONObject form(String directory) throws Exception {
        try (InputStream input =
                RuntimeEnvironment.application
                        .getAssets()
                        .open(directory + "/mothermentor_enrollment.json")) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            assertEquals(bytes.length, count);
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
