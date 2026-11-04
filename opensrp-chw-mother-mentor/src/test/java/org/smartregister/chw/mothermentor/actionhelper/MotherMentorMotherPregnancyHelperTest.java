package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherPregnancyHelperTest {
    @Test
    public void nativeSubmissionCannotReviveTheOldDateAfterChangingTheOutcomeInEitherLanguage() throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            MotherMentorVisitTypeActionHelper helper = helper("pregnant", "");
            JSONObject form = form(locale);
            set(form, ATTENDANCE_TYPE, "repeat"); set(form, FOLLOW_UP_STATUS, "found");
            set(form, VISIT_PREGNANCY_STATUS, "not_pregnant");
            set(form, MAMA_PREGNANCY_OUTCOME, "preterm");
            set(form, MAMA_TAREHE_KUJIFUNGUA, "19-10-2026");
            set(form, MAMA_DELIVERY_DATE_INPUT, "19-10-2026");
            set(form, MAMA_PRETERM_DELIVERY_DATE_INPUT, "");
            helper.onPayloadReceived(form.toString());
            JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
            assertEquals("", MotherMentorMotherPregnancyUtils.values(saved).get(MAMA_TAREHE_KUJIFUNGUA));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            set(form, MAMA_PRETERM_DELIVERY_DATE_INPUT, "18-10-2026");
            helper.onPayloadReceived(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void readonlyContextIsRestoredAfterSavedDetailsAreReapplied() throws Exception {
        MotherMentorVisitTypeActionHelper helper = helper("bf", "01-08-2026");
        JSONObject form = form("json.form-sw");
        set(form, ATTENDANCE_TYPE, "repeat"); set(form, FOLLOW_UP_STATUS, "found");
        set(form, MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        set(form, MAMA_ENROLLMENT_CHILD_DOB, "02-08-2026");
        set(form, MAMA_TAREHE_KUJIFUNGUA, "02-08-2026");
        helper.onPayloadReceived(form.toString());
        Map<String, String> values = MotherMentorMotherPregnancyUtils.values(
                new JSONObject(helper.postProcess(form.toString())));
        assertEquals("breast_feeding", values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        assertEquals("01-08-2026", values.get(MAMA_TAREHE_KUJIFUNGUA));
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
    }

    @Test
    public void editingAnOlderVisitUsesItsOriginalServiceDateForValidation() throws Exception {
        MotherMentorVisitTypeActionHelper helper = helper("pregnant", "");
        JSONObject form = form("json.form");
        set(form, ATTENDANCE_TYPE, "repeat"); set(form, FOLLOW_UP_STATUS, "found");
        set(form, VISIT_PREGNANCY_STATUS, "not_pregnant"); set(form, MAMA_PREGNANCY_OUTCOME, "delivered");
        set(form, MAMA_DELIVERY_DATE_INPUT, "21-10-2026");
        helper.onPayloadReceived(form.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }

    private MotherMentorVisitTypeActionHelper helper(String pregnancy, String dob) {
        return new MotherMentorVisitTypeActionHelper(null) {
            @Override protected MotherMentorMotherVisitDao.Context loadMotherContext() {
                return new MotherMentorMotherVisitDao.Context("episode", "", pregnancy, dob, dob, "family", 1);
            }
            @Override protected Map<String, String> loadEnrollmentValues() { return Collections.emptyMap(); }
            @Override protected Map<String, String> loadDeliveryAnswers() { return Collections.emptyMap(); }
            @Override protected LocalDate loadServiceDate() { return new LocalDate(2026, 10, 20); }
        };
    }

    private JSONObject form(String locale) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(
                locale + "/mothermentor_visit_type.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.optString("key"))) { field.put("value", value); return; }
        }
        throw new AssertionError("Missing field: " + key);
    }
}
