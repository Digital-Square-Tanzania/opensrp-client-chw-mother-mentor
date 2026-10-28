package org.smartregister.chw.mothermentor.actionhelper;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidFollowUpVisitUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidFinalTestActionHelperTest {
    @Test
    public void finalTestRequiresDateAndKnownResultButAcceptsPendingOrNoTest() throws Exception {
        MotherMentorChildEidFinalTestActionHelper helper = new MotherMentorChildEidFinalTestActionHelper();
        JSONObject form = MotherMentorActionHelpersTest.form(
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE, "yes",
                DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT, "positive",
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE, "");
        helper.onPayloadReceived(form.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
        for (String result : new String[] {"positive", "negative", "pending"}) {
            assertTrue(MotherMentorChildEidFollowUpVisitUtils.isFinalTestComplete("yes", result, "28-10-2026"));
        }
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isFinalTestComplete("yes", "unknown", "28-10-2026"));
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isFinalTestComplete("yes", "negative", "31-02-2026"));
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isFinalTestComplete("no", null, null));
    }

    @Test
    public void changingToNoTestClearsPreviouslyRecordedDateAndResult() throws Exception {
        MotherMentorChildEidFinalTestActionHelper helper = new MotherMentorChildEidFinalTestActionHelper();
        JSONObject form = MotherMentorActionHelpersTest.form(
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE, "no",
                DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT, "positive",
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE, "28-10-2026");
        helper.onPayloadReceived(form.toString());
        JSONObject cleaned = new JSONObject(helper.postProcess(form.toString()));
        assertEquals("", JsonFormUtils.getValue(cleaned, DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT));
        assertEquals("", JsonFormUtils.getValue(cleaned, DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE));
    }

    @Test
    public void onlyNewEighteenMonthAttendeesRequireFinalTestWithoutInvalidatingHistory() {
        Map<String, String> values = new HashMap<>();
        values.put(DBConstants.KEY.FOLLOW_UP_STATUS, "found");
        values.put(DBConstants.KEY.CHILD_EID_TEST_TYPE, "eighteen_months");
        values.put(DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE, "28-10-2026");
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
        values.put(DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA, "2");
        assertFalse(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
        values.put(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE, "no");
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
        values.remove(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE);
        values.put(DBConstants.KEY.CHILD_EID_TEST_TYPE, "nine_months");
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
        values.put(DBConstants.KEY.CHILD_EID_TEST_TYPE, "eighteen_months");
        values.put(DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY, "no");
        assertTrue(MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true));
    }
}
