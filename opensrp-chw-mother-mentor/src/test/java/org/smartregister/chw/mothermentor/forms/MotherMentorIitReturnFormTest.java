package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FACILITY_RETURN_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON_OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_AGREEMENT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_STATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.RETURNED_TO_FACILITY;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.vijay.jsonwizard.rules.RuleConstant;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorIitReturnActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;

import java.util.Collections;
import java.util.Locale;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorIitReturnFormTest {
    private static final LocalDate VISIT = new LocalDate(2024, 11, 5);

    @Test
    public void wizardShowsOnlyTheApplicableQuestionsInBothLanguages() throws Exception {
        for (boolean swahili : new boolean[] {false, true}) {
            JSONObject form = form(swahili);
            assertEquals("hidden", field(form, IIT_RETURN_STATE).getString("type"));
            assertEquals("toaster_notes", field(form, "iit_missed_appointment_note").getString("type"));
            assertFalse(field(form, "iit_missed_appointment_note").has("openmrs_entity_id"));
            for (String key : new String[] {SERVICE_INTERRUPTION_REASON, IIT_NOT_RETURNED_REASON}) {
                JSONArray options = field(form, key).getJSONArray("options");
                assertEquals(7, options.length());
                JSONObject travel = options.getJSONObject(2);
                assertEquals("travel_or_moved", travel.getString("key"));
                assertEquals(swahili ? "Safari" : "Travelled", travel.getString("text"));
            }
            assertTrue(relevant(form, SERVICE_INTERRUPTION_REASON, "none", "", "", ""));
            assertTrue(relevant(form, IIT_RETURN_AGREEMENT, "none", "", "", ""));
            assertTrue(relevant(form, SERVICE_INTERRUPTION_REASON_OTHER, "none", "other", "", ""));
            assertFalse(relevant(form, SERVICE_INTERRUPTION_REASON_OTHER, "none", "stigma", "", ""));
            assertFalse(relevant(form, RETURNED_TO_FACILITY, "none", "", "", ""));
            assertTrue(relevant(form, RETURNED_TO_FACILITY, "referred", "", "", ""));
            assertFalse(relevant(form, SERVICE_INTERRUPTION_REASON, "referred", "", "", ""));
            assertTrue(relevant(form, FACILITY_RETURN_DATE, "referred", "", "yes", ""));
            assertFalse(relevant(form, IIT_NOT_RETURNED_REASON, "referred", "", "yes", ""));
            assertFalse(relevant(form, FACILITY_RETURN_DATE, "referred", "", "no", ""));
            assertTrue(relevant(form, IIT_NOT_RETURNED_REASON, "referred", "", "no", ""));
            assertTrue(relevant(form, IIT_NOT_RETURNED_REASON_OTHER, "referred", "", "no", "other"));
            assertFalse(relevant(form, IIT_NOT_RETURNED_REASON_OTHER, "referred", "", "no", "forgot"));
        }
    }

    @Test
    public void preparationShowsMissedDateAndLocalizedCounterAndSetsPickerLimits() throws Exception {
        for (boolean swahili : new boolean[] {false, true}) {
            MotherMentorIitReturnActionHelper helper =
                    new MotherMentorIitReturnActionHelper("referred", "2024-10-20", 1, 2, VISIT);
            helper.onJsonFormLoaded(form(swahili).toString(), context(swahili), Collections.emptyMap());
            JSONObject prepared = new JSONObject(helper.getPreProcessed());
            assertEquals("referred", field(prepared, IIT_RETURN_STATE).getString("value"));
            assertEquals(swahili ? "Miadi aliyokosa: 20/10/2024 · siku 16 zilizopita"
                            : "Missed appointment: 20/10/2024 · 16 days ago",
                    field(prepared, "iit_missed_appointment_note").getString("text"));
            assertEquals("20-10-2024", field(prepared, FACILITY_RETURN_DATE).getString("min_date"));
            assertEquals("05-11-2024", field(prepared, FACILITY_RETURN_DATE).getString("max_date"));
            assertTrue(field(prepared, "iit_refused_note").getString("text").contains("1/3"));
            JSONObject warning = field(prepared, "iit_not_returned_note");
            assertTrue(warning.getString("text").startsWith(swahili ? "Mara ya 3" : "3rd time"));
            assertEquals("problem", warning.getString("toaster_type"));
        }
    }

    @Test
    public void stagingAnAnswerClearsInactiveValuesAndPreservesTheCurrentPhase() throws Exception {
        MotherMentorIitReturnActionHelper helper =
                new MotherMentorIitReturnActionHelper("referred", "2024-10-20", 0, 0, VISIT);
        JSONObject response = form(false);
        helper.onJsonFormLoaded(response.toString(), context(false), Collections.emptyMap());
        field(response, RETURNED_TO_FACILITY).put("value", "no");
        field(response, FACILITY_RETURN_DATE).put("value", "20-10-2024");
        field(response, IIT_RETURN_AGREEMENT).put("value", "agreed");
        field(response, SERVICE_INTERRUPTION_REASON).put("value", "forgot");
        field(response, IIT_NOT_RETURNED_REASON).put("value", "travel_or_moved");
        field(response, IIT_NOT_RETURNED_REASON_OTHER).put("value", "Old response");
        helper.onPayloadReceived(response.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        JSONObject staged = new JSONObject(helper.postProcess(response.toString()));
        assertEquals("referred", field(staged, IIT_RETURN_STATE).getString("value"));
        assertEquals("travel_or_moved", field(staged, IIT_NOT_RETURNED_REASON).getString("value"));
        for (String key : new String[] {IIT_RETURN_AGREEMENT, SERVICE_INTERRUPTION_REASON,
                FACILITY_RETURN_DATE, IIT_NOT_RETURNED_REASON_OTHER}) {
            assertEquals(key, "", field(staged, key).getString("value"));
        }
    }

    private static boolean relevant(JSONObject form, String key, String phase, String reason,
            String returned, String nonReturnReason) throws Exception {
        JSONObject field = field(form, key);
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_" + key);
        facts.put("step1_" + IIT_RETURN_STATE, phase);
        facts.put("step1_" + SERVICE_INTERRUPTION_REASON, reason);
        facts.put("step1_" + IIT_RETURN_AGREEMENT, "");
        facts.put("step1_" + RETURNED_TO_FACILITY, returned);
        facts.put("step1_" + IIT_NOT_RETURNED_REASON, nonReturnReason);
        String rules = field.getJSONObject("relevance").getJSONObject("rules-engine")
                .getJSONObject("ex-rules").getString("rules-file");
        return new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap())
                .getRelevance(facts, rules);
    }

    private static Context context(boolean swahili) {
        Context application = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(application.getResources().getConfiguration());
        config.setLocale(swahili ? new Locale("sw") : Locale.ENGLISH);
        return application.createConfigurationContext(config);
    }

    private static JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private static JSONObject form(boolean swahili) throws Exception {
        String folder = swahili ? "json.form-sw" : "json.form";
        String path = folder + "/mothermentor_iit_return_to_facility.json";
        try (Scanner input = new Scanner(RuntimeEnvironment.getApplication().getAssets().open(path), "UTF-8")) {
            return new JSONObject(input.useDelimiter("\\A").next());
        }
    }
}
