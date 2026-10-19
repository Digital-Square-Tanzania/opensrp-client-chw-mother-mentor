package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.Collections;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidTestTypeActionHelperTest {
    private final DateTime now = new DateTime(2026, 10, 16, 12, 0);

    @Test
    public void doesNotGuessForNewbornWithoutAnApplicableChoice() {
        assertNull(
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2026-10-01", null, null, now));
    }

    @Test
    public void followsVerifiedHeiAgeThresholds() {
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_6_WEEKS,
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2026-08-01", null, null, now));
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_9_MONTHS,
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2025-12-01", null, null, now));
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_18_MONTHS,
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2025-03-01", "2025-12-01", null, now));
    }

    @Test
    public void postWeaningChoiceRequiresRecordedDateAndThreeElapsedMonths() {
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_POST_WEANING,
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2025-08-01", "2026-06-01", null, now));
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_9_MONTHS,
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2025-08-01", "2026-09-01", null, now));
    }

    @Test
    public void invalidOrFutureBirthDateNeverProducesARecommendation() {
        assertNull(
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "not-a-date", null, null, now));
        assertNull(
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2027-01-01", null, null, now));
    }

    @Test
    public void doesNotRecommendARecordedCompletedAgeTestAgain() {
        assertNull(
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2026-08-01", null, "dbs_6", now));
        assertNull(
                MotherMentorChildEidTestTypeActionHelper.recommendTest(
                        "2025-12-01", null, "dbs_9", now));
    }

    @Test
    public void recommendationPrefillsBlankTestButPreservesSavedSelection() throws Exception {
        MotherMentorChildEidTestTypeActionHelper helper =
                new MotherMentorChildEidTestTypeActionHelper(new MemberObject()) {
                    @Override
                    protected Map<String, String> loadEnrollmentValues() {
                        return Collections.singletonMap(DBConstants.KEY.DOB, "2025-12-01");
                    }

                    @Override
                    protected DateTime currentTime() {
                        return now;
                    }
                };
        String testKey = DBConstants.KEY.CHILD_EID_TEST_TYPE;
        String noteKey = DBConstants.KEY.CHILD_EID_TEST_RECOMMENDATION;
        JSONObject form = MotherMentorActionHelpersTest.form(testKey, "", noteKey, "");
        helper.onJsonFormLoaded(
                form.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        JSONObject prepared = new JSONObject(helper.getPreProcessed());
        assertEquals(
                MotherMentorChildEidTestTypeActionHelper.TEST_9_MONTHS,
                JsonFormUtils.getValue(prepared, testKey));
        assertTrue(FormUtils.getFieldFromForm(prepared, noteKey).getString("text").contains("9"));
        FormUtils.getFieldFromForm(form, testKey).put("value", "six_weeks");
        helper.onJsonFormLoaded(
                form.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        prepared = new JSONObject(helper.getPreProcessed());
        assertEquals("six_weeks", JsonFormUtils.getValue(prepared, testKey));
        helper.onPayloadReceived(prepared.toString());
        assertEquals(
                BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        helper.onPayloadReceived((String) null);
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }
}
