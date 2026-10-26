package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorOptionalCommentCompletionTest {
    @Test
    public void completedNewAndRepeatServicesDoNotNeedComments() throws Exception {
        Map<String, String> values = completeService();
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));

        values.put(DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        values.put(DBConstants.KEY.FOLLOW_UP_STATUS, "continuing_services");
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));

        values.put(DBConstants.KEY.COMMENTS, "");
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));
        values.put(DBConstants.KEY.COMMENTS, "Additional follow-up information");
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));
    }

    @Test
    public void nonContinuingVisitsDoNotNeedCommentsOrHiddenSections() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        values.put(DBConstants.KEY.FOLLOW_UP_STATUS, "lost_to_follow_up");
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));
        values.remove(DBConstants.KEY.FOLLOW_UP_STATUS);
        assertFalse(MotherMentorVisitCompletion.isComplete(observations(values)));
    }

    @Test
    public void allOtherRequiredServiceSectionsStillBlockCompletion() throws Exception {
        for (String required : completeService().keySet()) {
            Map<String, String> missing = completeService();
            missing.remove(required);
            assertFalse(required, MotherMentorVisitCompletion.isComplete(observations(missing)));
        }
    }

    @Test
    public void primaryClinicalAssessmentStillRequiredWithoutComments() throws Exception {
        Map<String, String> values = completeService();
        values.put(DBConstants.KEY.MOTHERMENTOR_REPORTING_VERSION, "2");
        values.put(DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT, "true");
        assertFalse(MotherMentorVisitCompletion.isComplete(observations(values)));
        values.put(DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
        values.put(DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "no");
        assertTrue(MotherMentorVisitCompletion.isComplete(observations(values)));
    }

    private Map<String, String> completeService() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DBConstants.KEY.ATTENDANCE_TYPE, "new");
        values.put(DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP, "no");
        values.put(DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP, "no");
        values.put(DBConstants.KEY.EDUCATION_PROVIDED, "no");
        values.put(DBConstants.KEY.REFERRAL_GIVEN, "no");
        values.put(DBConstants.KEY.NEXT_APPOINTMENT_DATE, "2026-11-01");
        return values;
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray observations = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            observations.put(
                    new JSONObject()
                            .put("fieldCode", entry.getKey())
                            .put("values", new JSONArray().put(entry.getValue())));
        }
        return observations;
    }
}
