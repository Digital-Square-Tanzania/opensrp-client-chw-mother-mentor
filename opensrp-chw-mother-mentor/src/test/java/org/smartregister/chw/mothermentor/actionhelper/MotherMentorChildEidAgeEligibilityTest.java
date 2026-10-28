package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.vijay.jsonwizard.utils.FormUtils;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidAgeEligibilityTest {
    private static final String TEST_KEY = DBConstants.KEY.CHILD_EID_TEST_TYPE;
    private static final String SIX_WEEKS = "six_weeks";
    private static final String NINE_MONTHS = "nine_months";
    private static final String POST_WEANING = "three_months_after_weaning";
    private static final String EIGHTEEN_MONTHS = "eighteen_months";
    private static final String NOT_APPLICABLE = "not_applicable";
    private final DateTime now = new DateTime(2026, 10, 28, 12, 0);

    @Test
    public void twoMonthInfantCannotChooseFutureTestsInEitherLanguage() throws Exception {
        for (String language : languages()) {
            assertChoices(language, "2026-08-28", null, null, SIX_WEEKS);
            // A previously completed test suppresses recommendation, not age validation.
            assertChoices(language, "2026-08-28", null, "dbs_6", SIX_WEEKS);
        }
    }

    @Test
    public void calendarAgeBoundariesRetainEligibleCatchUpChoices() throws Exception {
        for (String language : languages()) {
            assertChoices(language, "2026-10-01", null, null, NOT_APPLICABLE);
            assertChoices(language, "2026-09-30", null, null, SIX_WEEKS);
            assertChoices(language, "2026-01-29", null, null, SIX_WEEKS);
            assertChoices(language, "2026-01-28", null, null, SIX_WEEKS, NINE_MONTHS);
            assertChoices(language, "2025-04-29", null, null, SIX_WEEKS, NINE_MONTHS);
            assertChoices(language, "2025-04-28", null, null,
                    SIX_WEEKS, NINE_MONTHS, EIGHTEEN_MONTHS);
        }
    }

    @Test
    public void timestampOffsetsDoNotMoveTheRecordedBirthDateAcrossAnAgeBoundary()
            throws Exception {
        for (String language : languages()) {
            assertChoices(language, "2026-10-01T00:00:00.000+14:00", null, null,
                    NOT_APPLICABLE);
            assertChoices(language, "2026-09-30T23:00:00.000-12:00", null, null,
                    SIX_WEEKS);
            assertChoices(language, "2026-01-29T00:00:00.000+14:00", null, null,
                    SIX_WEEKS);
            assertChoices(language, "2026-01-28T23:00:00.000-12:00", null, null,
                    SIX_WEEKS, NINE_MONTHS);
        }
    }

    @Test
    public void postWeaningRequiresARealDateAndThreeCalendarMonths() throws Exception {
        for (String language : languages()) {
            assertChoices(language, "2025-08-28", "2026-07-29", null, SIX_WEEKS, NINE_MONTHS);
            assertChoices(language, "2025-08-28", "2026-07-28", null,
                    SIX_WEEKS, NINE_MONTHS, POST_WEANING);
            assertChoices(language, "2026-08-28", "2026-01-01", null, SIX_WEEKS);
            assertChoices(language, "2026-08-28", "2027-01-01", null, SIX_WEEKS);
        }
    }

    @Test
    public void missingInvalidOrFutureDobNeverEnablesAChoice() throws Exception {
        for (String language : languages()) {
            for (String dob : new String[] {null, "", "not-a-date", "2027-01-01"}) {
                MotherMentorChildEidTestTypeActionHelper helper = helper(dob, null, null);
                JSONObject form = prepare(helper, language);
                assertEquals(Collections.emptyList(), choices(form));
                field(form).put("value", NOT_APPLICABLE);
                helper.onPayloadReceived(form.toString());
                assertEquals(BaseMotherMentorVisitAction.Status.PENDING,
                        helper.evaluateStatusOnPayload());
            }
        }
    }

    @Test
    public void submittedFutureOrFabricatedValuesCannotCompleteTheAction() throws Exception {
        for (String language : languages()) {
            MotherMentorChildEidTestTypeActionHelper helper = helper("2026-08-28", null, null);
            BaseMotherMentorVisitAction action = action(helper, language, Collections.emptyMap());
            for (String invalid : new String[] {
                    NINE_MONTHS, EIGHTEEN_MONTHS, POST_WEANING, NOT_APPLICABLE, "other"}) {
                JSONObject form = form(language);
                field(form).put("value", invalid);
                action.setJsonPayload(form.toString());
                assertEquals(BaseMotherMentorVisitAction.Status.PENDING, action.getActionStatus());
                assertEquals("", JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), TEST_KEY));
                assertEquals("", action.getSubTitle());
            }
        }
    }

    @Test
    public void restoringAnOldFutureStageCannotUndoAgeValidation() throws Exception {
        for (String language : languages()) {
            VisitDetail saved = new VisitDetail();
            saved.setVisitKey(TEST_KEY);
            saved.setDetails(NINE_MONTHS);
            Map<String, List<VisitDetail>> details = Collections.singletonMap(TEST_KEY,
                    Collections.singletonList(saved));
            BaseMotherMentorVisitAction action = action(helper("2026-08-28", null, null), language, details);
            JSONObject restored = new JSONObject(action.getJsonPayload());
            assertEquals(Collections.singletonList(SIX_WEEKS), choices(restored));
            assertEquals("", JsonFormUtils.getValue(restored, TEST_KEY));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, action.getActionStatus());
            // Correcting the old draft to an eligible stage makes the action usable again.
            field(restored).put("value", SIX_WEEKS);
            action.setJsonPayload(restored.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action.getActionStatus());
        }
    }

    @Test
    public void validSavedCatchUpSelectionIsPreserved() throws Exception {
        for (String language : languages()) {
            VisitDetail saved = new VisitDetail();
            saved.setVisitKey(TEST_KEY);
            saved.setDetails(SIX_WEEKS);
            BaseMotherMentorVisitAction action = action(helper("2025-12-01", null, null),
                    language, Collections.singletonMap(TEST_KEY, Collections.singletonList(saved)));
            assertEquals(SIX_WEEKS, JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), TEST_KEY));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action.getActionStatus());
        }
    }

    @Test
    public void notApplicableIsAvailableOnlyBeforeFirstEligibleTest() throws Exception {
        for (String language : languages()) {
            MotherMentorChildEidTestTypeActionHelper helper = helper("2026-10-15", null, null);
            JSONObject prepared = prepare(helper, language);
            assertEquals(Collections.singletonList(NOT_APPLICABLE), choices(prepared));
            field(prepared).put("value", NOT_APPLICABLE);
            helper.onPayloadReceived(prepared.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            assertFalse(helper.evaluateSubTitle().contains("not_applicable"));
        }
    }

    private void assertChoices(String language, String dob, String weaning, String last,
            String... expected) throws Exception {
        assertEquals(language + ": " + dob + "/" + weaning,
                Arrays.asList(expected), choices(prepare(helper(dob, weaning, last), language)));
    }

    private MotherMentorChildEidTestTypeActionHelper helper(String dob, String weaning, String last) {
        Map<String, String> values = new HashMap<>();
        values.put(DBConstants.KEY.DOB, dob);
        values.put(DBConstants.KEY.WEANING_DATE, weaning);
        values.put(DBConstants.KEY.LAST_TEST, last);
        return new MotherMentorChildEidTestTypeActionHelper(new MemberObject()) {
            @Override protected Map<String, String> loadEnrollmentValues() { return values; }
            @Override protected DateTime currentTime() { return now; }
        };
    }

    private BaseMotherMentorVisitAction action(MotherMentorChildEidTestTypeActionHelper helper,
            String language, Map<String, List<VisitDetail>> details) throws Exception {
        return new BaseMotherMentorVisitAction.Builder(context(language), "Test type")
                .withFormName("mothermentor_child_eid_test_type")
                .withJsonPayload(form(language).toString())
                .withDetails(details).withHelper(helper).build();
    }

    private JSONObject prepare(MotherMentorChildEidTestTypeActionHelper helper, String language)
            throws Exception {
        helper.onJsonFormLoaded(form(language).toString(), context(language), Collections.emptyMap());
        return new JSONObject(helper.getPreProcessed());
    }

    private Context context(String language) {
        Context context = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale("json.form-sw".equals(language) ? "sw" : "en"));
        return context.createConfigurationContext(configuration);
    }

    private JSONObject form(String language) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                .open(language + "/mothermentor_child_eid_test_type.json")) {
            byte[] bytes = new byte[input.available()];
            assertEquals(bytes.length, input.read(bytes));
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private JSONObject field(JSONObject form) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, TEST_KEY);
        assertNotNull(field);
        return field;
    }

    private List<String> choices(JSONObject form) throws Exception {
        JSONArray options = field(form).getJSONArray("options");
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < options.length(); i++) choices.add(options.getJSONObject(i).getString("key"));
        return choices;
    }

    private String[] languages() { return new String[] {"json.form", "json.form-sw"}; }
}
