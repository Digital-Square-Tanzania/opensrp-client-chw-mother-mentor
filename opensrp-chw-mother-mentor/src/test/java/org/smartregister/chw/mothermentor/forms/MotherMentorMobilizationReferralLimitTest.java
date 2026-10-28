package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.app.Application;

import com.rengwuxian.materialedittext.MaterialEditText;
import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RuleConstant;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.AppExecutors;
import com.vijay.jsonwizard.utils.FormUtils;
import com.vijay.jsonwizard.validators.edittext.RequiredValidator;
import com.vijay.jsonwizard.views.JsonFormFragmentView;
import com.vijay.jsonwizard.widgets.EditTextFactory;

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
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMobilizationReferralLimitTest {
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};
    private static final String[] REFERRAL_FIELDS = {
        DBConstants.KEY.REFERRED_MALE, DBConstants.KEY.REFERRED_FEMALE
    };

    @Test
    public void bothEditableReferralCountsValidateTheCombinedTotalInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language);
            for (String key : REFERRAL_FIELDS) {
                JSONObject field = field(form, key);
                assertFalse(field.optBoolean("read_only"));
                assertTrue(field.getJSONObject("v_required").getBoolean("value"));
                JSONObject constraint = constraint(field);
                assertEquals("numeric", constraint.getString("type"));
                assertEquals("lessThanEqualTo(step1:referred_total, step1:elim_total)",
                        constraint.getString("ex"));
                assertEquals(error(language), constraint.getString("err"));
            }
        }
    }

    @Test
    public void nativeCalculationAndConstraintEnforceTheAggregateBoundaryInBothLanguages()
            throws Exception {
        for (String language : LANGUAGES) {
            // The issue screenshot: 2 men + 5 women educated, but 6 + 8 referred.
            assertLimit(language, 2, 5, 6, 8, false);
            assertLimit(language, 2, 5, 3, 5, false);
            assertLimit(language, 2, 5, 3, 4, true);
            assertLimit(language, 2, 5, 2, 4, true);
            assertLimit(language, 0, 0, 0, 0, true);
            assertLimit(language, 0, 0, 0, 1, false);
            // The requirement caps the total, not each sex's count separately.
            assertLimit(language, 2, 5, 7, 0, true);
            assertLimit(language, 2, 5, 0, 7, true);
            assertLimit(language, 99999, 99999, 99999, 99999, true);
            // Reducing the educated count must invalidate an earlier valid referral total.
            assertLimit(language, 2, 4, 3, 4, false);
        }
    }

    @Test
    public void nativeConstraintsClearInvalidEditableCountsAndRequiredValidationBlocksSaving()
            throws Exception {
        for (String language : LANGUAGES) {
            for (String key : REFERRAL_FIELDS) {
                JSONObject form = counts(form(language), 2, 5, 6, 8);
                JsonFormActivity activity = activity(form);
                JSONObject field = field(form, key);
                MaterialEditText editText = new MaterialEditText(activity);
                editText.setText(field.getString("value"));
                editText.setTag(com.vijay.jsonwizard.R.id.key, key);
                editText.setTag(com.vijay.jsonwizard.R.id.address, "step1:" + key);
                editText.setTag(com.vijay.jsonwizard.R.id.constraints,
                        field.getJSONArray("constraints").toString());
                editText.addValidator(new RequiredValidator(
                        field.getJSONObject("v_required").getString("err")));

                ReflectionHelpers.callInstanceMethod(activity, "checkViewConstraints",
                        ReflectionHelpers.ClassParameter.from(android.view.View.class, editText),
                        ReflectionHelpers.ClassParameter.from(boolean.class, false));

                assertEquals("", editText.getText().toString());
                assertEquals(error(language), editText.getError().toString());
                assertFalse(EditTextFactory.validate(mock(JsonFormFragmentView.class), editText)
                        .isValid());
            }
        }
    }

    @Test
    public void nativeRefreshRechecksReferralCountsWhenEitherReferralOrEducatedCountChanges()
            throws Exception {
        String[] changedFields = {DBConstants.KEY.REFERRED_MALE, DBConstants.KEY.REFERRED_FEMALE,
            DBConstants.KEY.ELIM_M, DBConstants.KEY.ELIM_F};
        int[][] changedCounts = {{2, 5, 4, 4}, {2, 5, 3, 5}, {1, 5, 3, 4}, {2, 4, 3, 4}};
        for (String language : LANGUAGES) {
            for (int change = 0; change < changedFields.length; change++) {
                JSONObject form = counts(form(language), 2, 5, 3, 4);
                JsonFormActivity activity = activity(form);
                MaterialEditText[] views = new MaterialEditText[REFERRAL_FIELDS.length];
                for (int i = 0; i < REFERRAL_FIELDS.length; i++) {
                    String key = REFERRAL_FIELDS[i];
                    JSONObject field = field(form, key);
                    MaterialEditText view = new MaterialEditText(activity);
                    view.setText(field.getString("value"));
                    view.setTag(com.vijay.jsonwizard.R.id.key, key);
                    view.setTag(com.vijay.jsonwizard.R.id.address, "step1:" + key);
                    view.setTag(com.vijay.jsonwizard.R.id.constraints,
                            field.getJSONArray("constraints").toString());
                    activity.addConstrainedView(view);
                    views[i] = view;
                }
                activity.refreshConstraints(changedFields[change], null, false);
                assertEquals("3", views[0].getText().toString());
                assertEquals("4", views[1].getText().toString());

                int[] values = changedCounts[change];
                counts(form, values[0], values[1], values[2], values[3]);
                for (int i = 0; i < REFERRAL_FIELDS.length; i++) {
                    views[i].setText(field(form, REFERRAL_FIELDS[i]).getString("value"));
                }
                activity.refreshConstraints(changedFields[change], null, false);
                for (MaterialEditText view : views) {
                    assertEquals(changedFields[change], "", view.getText().toString());
                    assertEquals(error(language), view.getError().toString());
                }
            }
        }
    }

    private void assertLimit(String language, int menEducated, int womenEducated,
            int menReferred, int womenReferred, boolean valid) throws Exception {
        JSONObject form = counts(form(language), menEducated, womenEducated,
                menReferred, womenReferred);
        JsonFormActivity activity = activity(form);
        for (String key : REFERRAL_FIELDS) {
            String result = ReflectionHelpers.callInstanceMethod(activity, "enforceConstraint",
                    ReflectionHelpers.ClassParameter.from(String.class,
                            field(form, key).getString("value")),
                    ReflectionHelpers.ClassParameter.from(android.view.View.class,
                            new MaterialEditText(activity)),
                    ReflectionHelpers.ClassParameter.from(JSONObject.class,
                            constraint(field(form, key))));
            if (valid) {
                assertNull(result);
            } else {
                assertEquals(error(language), result);
            }
        }
    }

    private JSONObject counts(JSONObject form, int menEducated, int womenEducated,
            int menReferred, int womenReferred) throws Exception {
        field(form, DBConstants.KEY.ELIM_M).put("value", String.valueOf(menEducated));
        field(form, DBConstants.KEY.ELIM_F).put("value", String.valueOf(womenEducated));
        field(form, DBConstants.KEY.REFERRED_MALE).put("value", String.valueOf(menReferred));
        field(form, DBConstants.KEY.REFERRED_FEMALE).put("value", String.valueOf(womenReferred));
        RulesEngineFactory rules = new RulesEngineFactory(RuntimeEnvironment.getApplication(),
                Collections.emptyMap());
        for (String total : new String[] {DBConstants.KEY.ELIM_TOTAL,
                DBConstants.KEY.REFERRED_TOTAL}) {
            Facts facts = new Facts();
            facts.put(RuleConstant.SELECTED_RULE, "step1_" + total);
            for (String key : new String[] {DBConstants.KEY.ELIM_M, DBConstants.KEY.ELIM_F,
                    DBConstants.KEY.REFERRED_MALE, DBConstants.KEY.REFERRED_FEMALE}) {
                facts.put("step1_" + key, field(form, key).getString("value"));
            }
            JSONObject field = field(form, total);
            String file = field.getJSONObject("calculation").getJSONObject("rules-engine")
                    .getJSONObject("ex-rules").getString("rules-file");
            field.put("value", rules.getCalculation(facts, file));
        }
        return form;
    }

    private JsonFormActivity activity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        activity.setTheme(com.vijay.jsonwizard.R.style.NativeFormsAppTheme);
        ReflectionHelpers.setField(activity, "appExecutors",
                new AppExecutors(Runnable::run, Runnable::run, Runnable::run));
        ReflectionHelpers.setField(activity, "constrainedViews", new HashMap<>());
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            activity.getFormFieldsMap().put("step1_" + field.getString("key"), field);
        }
        ReflectionHelpers.callInstanceMethod(activity, "initComparisons");
        return activity;
    }

    private JSONObject constraint(JSONObject field) throws Exception {
        JSONArray constraints = field.optJSONArray("constraints");
        assertNotNull(field.getString("key") + " must validate the aggregate referral count",
                constraints);
        return constraints.getJSONObject(0);
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private String error(String language) {
        return "json.form-sw".equals(language)
                ? "Idadi ya wateja waliopewa rufaa haipaswi kuzidi idadi ya watu waliofikiwa na kuelimishwa."
                : "The number of clients referred should not exceed the number of people reached and educated.";
    }

    private JSONObject form(String language) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                .open(language + "/mothermentor_mobilization_session.json")) {
            byte[] bytes = new byte[input.available()];
            assertEquals(bytes.length, input.read(bytes));
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
