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
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherPregnancyRulesTest {
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};
    private static final String[] CONDITIONAL_FIELDS = {VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME,
            MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT, MAMA_STILLBIRTH_DATE_INPUT,
            MAMA_PREGNANCY_LOSS_DATE, MAMA_PREGNANCY_OUTCOME_OTHER, MAMA_ENROLLMENT_DELIVERY_DATE};

    @Test
    public void sdkExtractsNamedDependenciesForEveryPregnancyRuleInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = foundPregnant(language);
            assertEquals(0, FormUtils.getFieldFromForm(form, "mama_delivery_edit_note").length());
            assertEquals(0, FormUtils.getFieldFromForm(form, "mama_delivery_schedule_note").length());
            JsonFormActivity activity = nativeActivity(form);
            for (String key : CONDITIONAL_FIELDS) {
                Facts facts = nativeFacts(activity, form, key, "relevance");
                assertTrue(language + ":" + key, facts.asMap().containsKey("step1_" + ATTENDANCE_TYPE));
                assertTrue(key, facts.asMap().containsKey("step1_" + MAMA_ENROLLMENT_PREGNANCY_STATUS));
                assertFalse(key, facts.asMap().containsKey("value"));
            }
            Facts dates = nativeFacts(activity, form, MAMA_TAREHE_KUJIFUNGUA, "calculation");
            assertTrue(dates.asMap().containsKey("step1_" + ATTENDANCE_TYPE));
            assertTrue(dates.asMap().containsKey("step1_" + MAMA_ENROLLMENT_CHILD_DOB));
            assertTrue(dates.asMap().containsKey("step1_" + MAMA_PRETERM_DELIVERY_DATE_INPUT));
        }
    }

    @Test
    public void nativeFactsSelectOnlyTheOriginalPregnancyAndCurrentOutcomeBranches() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = foundPregnant(language);
            JsonFormActivity activity = nativeActivity(form);
            set(form, VISIT_PREGNANCY_STATUS, "pregnant");
            assertTrue(relevant(activity, form, VISIT_PREGNANCY_STATUS));
            assertFalse(relevant(activity, form, MAMA_PREGNANCY_OUTCOME));
            set(form, VISIT_PREGNANCY_STATUS, "not_pregnant");
            assertTrue(relevant(activity, form, MAMA_PREGNANCY_OUTCOME));
            String[] outcomes = {"delivered", "preterm", "stillbirth", "pregnancy_loss", "other"};
            String[] fields = {MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                    MAMA_STILLBIRTH_DATE_INPUT, MAMA_PREGNANCY_LOSS_DATE, MAMA_PREGNANCY_OUTCOME_OTHER};
            for (int branch = 0; branch < outcomes.length; branch++) {
                set(form, MAMA_PREGNANCY_OUTCOME, outcomes[branch]);
                for (int candidate = 0; candidate < fields.length; candidate++) {
                    assertEquals(language + ":" + outcomes[branch] + ":" + fields[candidate],
                            branch == candidate, relevant(activity, form, fields[candidate]));
                }
            }
        }
    }

    @Test
    public void switchingDeliveredAndPretermClearsThePreviousNativeDateWithoutReusingIt() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = foundPregnant(language);
            JsonFormActivity activity = nativeActivity(form);
            set(form, VISIT_PREGNANCY_STATUS, "not_pregnant");
            set(form, MAMA_PREGNANCY_OUTCOME, "delivered");
            set(form, MAMA_DELIVERY_DATE_INPUT, "19-10-2026");
            assertTrue(relevant(activity, form, MAMA_DELIVERY_DATE_INPUT));
            assertEquals("19-10-2026", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
            set(form, MAMA_PREGNANCY_OUTCOME, "preterm");
            assertFalse(relevant(activity, form, MAMA_DELIVERY_DATE_INPUT));
            assertTrue(relevant(activity, form, MAMA_PRETERM_DELIVERY_DATE_INPUT));
            clearHidden(activity, form, MAMA_DELIVERY_DATE_INPUT);
            assertFalse(field(form, MAMA_DELIVERY_DATE_INPUT).has("value"));
            assertEquals("", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
            set(form, MAMA_PRETERM_DELIVERY_DATE_INPUT, "18-10-2026");
            assertEquals("18-10-2026", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
            set(form, MAMA_PREGNANCY_OUTCOME, "delivered");
            assertFalse(relevant(activity, form, MAMA_PRETERM_DELIVERY_DATE_INPUT));
            clearHidden(activity, form, MAMA_PRETERM_DELIVERY_DATE_INPUT);
            assertEquals("", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
        }
    }

    @Test
    public void originalChildDateRemainsReadonlyAndRestoresAfterNotFoundInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            for (String original : new String[] {"breast_feeding", "not_pregnant"}) {
                JSONObject form = foundPregnant(language);
                JsonFormActivity activity = nativeActivity(form);
                set(form, MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
                set(form, MAMA_ENROLLMENT_CHILD_DOB, "01-08-2026");
                set(form, MAMA_ENROLLMENT_DELIVERY_DATE, "02-08-2026");
                assertFalse(relevant(activity, form, VISIT_PREGNANCY_STATUS));
                assertTrue(relevant(activity, form, MAMA_ENROLLMENT_DELIVERY_DATE));
                assertTrue(field(form, MAMA_ENROLLMENT_DELIVERY_DATE).getBoolean("read_only"));
                assertEquals("01-08-2026", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
                set(form, FOLLOW_UP_STATUS, "not_found");
                assertFalse(relevant(activity, form, MAMA_ENROLLMENT_DELIVERY_DATE));
                clearHidden(activity, form, MAMA_ENROLLMENT_DELIVERY_DATE);
                assertEquals("", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
                set(form, FOLLOW_UP_STATUS, "found");
                assertEquals("01-08-2026", calculated(activity, form, MAMA_ENROLLMENT_DELIVERY_DATE));
                assertEquals("01-08-2026", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
                assertEquals("01-08-2026", field(form, MAMA_ENROLLMENT_CHILD_DOB).getString("value"));
            }
        }
    }

    @Test
    public void newAttendanceHidesAllPregnancyFieldsAndClearsDatesInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = foundPregnant(language);
            JsonFormActivity activity = nativeActivity(form);
            set(form, ATTENDANCE_TYPE, "new");
            set(form, VISIT_PREGNANCY_STATUS, "not_pregnant");
            set(form, MAMA_PREGNANCY_OUTCOME, "delivered");
            set(form, MAMA_DELIVERY_DATE_INPUT, "01-11-2026");
            for (String key : CONDITIONAL_FIELDS) assertFalse(language + ":" + key, relevant(activity, form, key));
            assertEquals("", calculated(activity, form, MAMA_TAREHE_KUJIFUNGUA));
            assertEquals("", calculated(activity, form, MAMA_ENROLLMENT_DELIVERY_DATE));
        }
    }

    private JsonFormActivity nativeActivity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        ReflectionHelpers.setField(activity, "mJSONObject", form);
        activity.setRulesEngineFactory(new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap()));
        Map<String, JSONObject> nativeFields = ReflectionHelpers.getField(activity, "formFields");
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int index = 0; index < fields.length(); index++) {
            JSONObject field = fields.getJSONObject(index);
            nativeFields.put("step1_" + field.getString("key"), field);
        }
        return activity;
    }

    private Facts nativeFacts(JsonFormActivity activity, JSONObject form, String key, String logic) throws Exception {
        JSONObject configuration = field(form, key).getJSONObject(logic);
        String file = configuration.getJSONObject("rules-engine").getJSONObject("ex-rules").getString("rules-file");
        String[] address = {"rules-engine", file, "step1_" + key};
        JSONObject source = activity.getObjectUsingAddress(address, false);
        return ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
    }

    private boolean relevant(JsonFormActivity activity, JSONObject form, String key) throws Exception {
        return activity.getRulesEngineFactory().getRelevance(nativeFacts(activity, form, key, "relevance"),
                "mothermentor_mother_pregnancy_relevance.yml");
    }

    private String calculated(JsonFormActivity activity, JSONObject form, String key) throws Exception {
        return activity.getRulesEngineFactory().getCalculation(nativeFacts(activity, form, key, "calculation"),
                "mothermentor_mother_pregnancy_calculation.yml");
    }

    private void clearHidden(JsonFormActivity activity, JSONObject form, String key) throws Exception {
        ReflectionHelpers.callInstanceMethod(activity, "clearHiddenViewsValues",
                ClassParameter.from(JSONObject.class, field(form, key)),
                ClassParameter.from(String.class, "step1:" + key));
    }

    private JSONObject foundPregnant(String language) throws Exception {
        JSONObject form;
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(language + "/mothermentor_visit_type.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            form = new JSONObject(scanner.next());
        }
        set(form, ATTENDANCE_TYPE, "repeat"); set(form, FOLLOW_UP_STATUS, "found");
        set(form, MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        return form;
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key); assertNotNull(key, field); return field;
    }

    private void set(JSONObject form, String key, String value) throws Exception { field(form, key).put("value", value); }
}
