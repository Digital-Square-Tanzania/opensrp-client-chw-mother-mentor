package org.smartregister.chw.mothermentor.forms;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
import org.joda.time.LocalDate;
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
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidResultForm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_CONFIRMATORY_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_CONFIRMATORY_TEST_DONE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_PENDING_TEST_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_RECEIVED_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_RESULT_RECEIVED_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_CONFIRM;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_RESULT;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidRecordResultFormTest {
    private static final String RULES = "mothermentor_child_eid_record_result_relevance.yml";
    private static final String SAMPLE_LABEL = "child_eid_result_sample_label";
    private static final String DATE_LABEL = "child_eid_result_sample_date_label";
    private static final LocalDate SCREENING_DATE = new LocalDate(2020, 10, 20);
    private static final LocalDate CONFIRMATION_DATE = new LocalDate(2020, 10, 25);
    private static final Locale[] LOCALES = {Locale.ENGLISH, Locale.forLanguageTag("sw")};

    @Test
    public void documentedConfirmationOpensOnRequiredResultAndReceiptDateInBothLanguages() throws Exception {
        for (Locale locale : LOCALES) {
            ResultForm form = new ResultForm(locale, "six_weeks", AWAITING_CONFIRM, CONFIRMATION_DATE);
            assertTrue(form.field(SAMPLE_LABEL).getBoolean("read_only"));
            assertEquals(form.context.getString(R.string.mothermentor_eid_test_six_weeks),
                    form.field(SAMPLE_LABEL).getString("value"));
            assertTrue(form.field(DATE_LABEL).getBoolean("read_only"));
            assertEquals("25-10-2020", form.field(DATE_LABEL).getString("value"));
            assertEquals(SCREENING_DATE.toString(), form.field(MTOTO_PENDING_TEST_DATE).getString("value"));
            assertEquals("25-10-2020", form.field(MTOTO_RESULT_RECEIVED_DATE).getString("min_date"));
            assertFalse(form.relevant(MTOTO_RECEIVED_RESULT));
            assertFalse(form.relevant(MTOTO_CONFIRMATORY_TEST_DONE));
            assertTrue(form.relevant(MTOTO_CONFIRMATORY_RESULT));
            assertTrue(form.relevant(MTOTO_RESULT_RECEIVED_DATE));
            assertTrue(form.field(MTOTO_CONFIRMATORY_RESULT).getJSONObject("v_required").getBoolean("value"));
            assertTrue(form.field(MTOTO_RESULT_RECEIVED_DATE).getJSONObject("v_required").getBoolean("value"));
            assertEquals(MTOTO_RESULT_RECEIVED_DATE, form.lastFieldKey());
            JSONArray options = form.field(MTOTO_CONFIRMATORY_RESULT).getJSONArray("options");
            assertEquals(2, options.length());
            assertEquals("negative", options.getJSONObject(0).getString("key"));
            assertEquals("positive", options.getJSONObject(1).getString("key"));
        }
    }

    @Test
    public void undatedConfirmationKeepsTheQuestionAndAcceptsNotDoneInBothLanguages() throws Exception {
        for (Locale locale : LOCALES) {
            ResultForm form = new ResultForm(locale, "six_weeks", AWAITING_CONFIRM, null);
            assertEquals("20-10-2020", form.field(DATE_LABEL).getString("value"));
            assertTrue(form.relevant(MTOTO_CONFIRMATORY_TEST_DONE));
            form.field(MTOTO_CONFIRMATORY_TEST_DONE).put("value", "no");
            assertFalse(form.relevant(MTOTO_CONFIRMATORY_RESULT));
            assertFalse(form.relevant(MTOTO_RESULT_RECEIVED_DATE));
            form.field(MTOTO_CONFIRMATORY_TEST_DONE).put("value", "yes");
            assertTrue(form.relevant(MTOTO_CONFIRMATORY_RESULT));
            assertEquals(3, form.field(MTOTO_CONFIRMATORY_RESULT).getJSONArray("options").length());
            form.field(MTOTO_CONFIRMATORY_RESULT).put("value", "pending");
            assertFalse(form.relevant(MTOTO_RESULT_RECEIVED_DATE));
            form.field(MTOTO_CONFIRMATORY_RESULT).put("value", "negative");
            assertTrue(form.relevant(MTOTO_RESULT_RECEIVED_DATE));
        }
    }

    @Test
    public void initialPositiveAsksAboutConfirmationAndEighteenMonthsNeverOffersPending() throws Exception {
        for (Locale locale : LOCALES) {
            ResultForm initial = new ResultForm(locale, "eighteen_months", AWAITING_RESULT, CONFIRMATION_DATE);
            assertEquals("20-10-2020", initial.field(DATE_LABEL).getString("value"));
            assertFalse(initial.relevant(MTOTO_CONFIRMATORY_TEST_DONE));
            initial.field(MTOTO_RECEIVED_RESULT).put("value", "positive");
            assertTrue(initial.relevant(MTOTO_CONFIRMATORY_TEST_DONE));
            assertFalse(initial.relevant(MTOTO_CONFIRMATORY_RESULT));
            initial.field(MTOTO_CONFIRMATORY_TEST_DONE).put("value", "yes");
            assertTrue(initial.relevant(MTOTO_CONFIRMATORY_RESULT));
            assertEquals(2, initial.field(MTOTO_CONFIRMATORY_RESULT).getJSONArray("options").length());

            ResultForm undated = new ResultForm(locale, "eighteen_months", AWAITING_CONFIRM, null);
            assertTrue(undated.relevant(MTOTO_CONFIRMATORY_TEST_DONE));
            assertEquals(2, undated.field(MTOTO_CONFIRMATORY_RESULT).getJSONArray("options").length());
        }
    }

    @Test
    public void receiptDateUsesTheRecordedConfirmationDateEvenWithoutARepeatAnswer() {
        Map<String, String> answers = new HashMap<>();
        answers.put(MTOTO_RUFAA_STATE_BEFORE, AWAITING_CONFIRM);
        assertEquals(R.string.mothermentor_eid_received_invalid_date,
                MotherMentorChildEidResultForm.receiptDateError(answers, SCREENING_DATE, CONFIRMATION_DATE));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, CONFIRMATION_DATE.minusDays(1).toString());
        assertEquals(R.string.mothermentor_eid_received_before_test,
                MotherMentorChildEidResultForm.receiptDateError(answers, SCREENING_DATE, CONFIRMATION_DATE));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, CONFIRMATION_DATE.toString());
        assertEquals(0, MotherMentorChildEidResultForm.receiptDateError(answers, SCREENING_DATE, CONFIRMATION_DATE));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, LocalDate.now().plusDays(1).toString());
        assertEquals(R.string.mothermentor_eid_received_invalid_date,
                MotherMentorChildEidResultForm.receiptDateError(answers, SCREENING_DATE, CONFIRMATION_DATE));
        answers.put(MTOTO_CONFIRMATORY_TEST_DONE, "no");
        assertEquals(0, MotherMentorChildEidResultForm.receiptDateError(answers, SCREENING_DATE));
    }

    private static final class ResultForm {
        private final JSONObject form;
        private final Context context;
        private final JsonFormActivity activity;

        ResultForm(Locale locale, String stage, String state, LocalDate confirmationDate) throws Exception {
            Application application = RuntimeEnvironment.getApplication();
            Configuration configuration = new Configuration(application.getResources().getConfiguration());
            configuration.setLocale(locale);
            context = application.createConfigurationContext(configuration);
            String directory = "sw".equals(locale.getLanguage()) ? "json.form-sw" : "json.form";
            String path = directory + "/" + MotherMentorChildEidResultForm.FORM_NAME + ".json";
            try (Scanner scanner = new Scanner(application.getAssets().open(path), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            MotherMentorChildEidResultForm.prepare(form, context, stage, SCREENING_DATE, state, confirmationDate);
            activity = Robolectric.buildActivity(JsonFormActivity.class).get();
            ReflectionHelpers.setField(activity, "mJSONObject", form);
            activity.setRulesEngineFactory(new RulesEngineFactory(context, Collections.emptyMap()));
            Map<String, JSONObject> fields = ReflectionHelpers.getField(activity, "formFields");
            JSONArray definitions = definitions();
            for (int i = 0; i < definitions.length(); i++) {
                JSONObject field = definitions.getJSONObject(i);
                fields.put("step1_" + field.getString("key"), field);
            }
        }

        JSONObject field(String key) throws Exception {
            return FormUtils.getFieldFromForm(form, key);
        }

        String lastFieldKey() throws Exception {
            JSONArray fields = definitions();
            return fields.getJSONObject(fields.length() - 1).getString("key");
        }

        private JSONArray definitions() throws Exception {
            return form.getJSONObject("step1").getJSONArray("fields");
        }

        boolean relevant(String key) throws Exception {
            String[] address = {"rules-engine", RULES, "step1_" + key};
            JSONObject source = activity.getObjectUsingAddress(address, false);
            Facts facts = ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                    ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
            return activity.getRulesEngineFactory().getRelevance(facts, RULES);
        }
    }
}
