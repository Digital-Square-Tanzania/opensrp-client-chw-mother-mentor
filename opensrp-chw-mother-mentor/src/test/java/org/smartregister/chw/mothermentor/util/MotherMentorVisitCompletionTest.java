package org.smartregister.chw.mothermentor.util;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class MotherMentorVisitCompletionTest {
    @Test public void nonContinuingVisitsDoNotRequireHiddenActions() {
        assertTrue(MotherMentorVisitCompletion.isFullVisit("new", null));
        assertTrue(MotherMentorVisitCompletion.isFullVisit("repeat", "continuing_services"));
        for (String status : new String[]{"lost_to_follow_up", "deceased", "relocated", "transferred_to_another_facility", "stopped_services", "not_available"})
            assertFalse(MotherMentorVisitCompletion.isFullVisit("repeat", status));
    }
    @Test public void repeatVisitNeedsStatusEvenOnLegacyData() {
        Map<String,String> values = new HashMap<>();
        values.put("attendance_type", "repeat");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("follow_up_status", "not_available");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
    }
    @Test public void primaryAssessmentDoesNotLeakIntoSecondaryVisits() {
        Map<String,String> values = new HashMap<>();
        values.put("attendance_type", "new"); values.put("mothermentor_reporting_version", "1");
        values.put("primary_mothermentor_client", "true");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("primary_mothermentor_client", "false");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
    }
    @Test public void approvedPartnerAttendanceRequiresDateOnlyForYes() {
        Map<String,String> values = new HashMap<>();
        values.put("attendance_type", "repeat"); values.put("follow_up_status", "not_available");
        values.put("mothermentor_reporting_version", "2"); values.put("partner_enrolled", "true");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("partner_reached_facility", "no");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("partner_reached_facility", "not_applicable");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("partner_reached_facility", "yes");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("partner_facility_date", "15-10-2026");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
    }
    @Test public void approvedChildStageAndAttendanceRespectCohortAndLegacyVersion() {
        Map<String,String> values = new HashMap<>();
        values.put("attendance_type", "new"); values.put("mothermentor_reporting_version", "2");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("child_eid_enrolled", "true");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("mothermentor_reporting_version", "1");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("mothermentor_reporting_version", "2");
        values.put("child_reached_facility", "yes"); values.put("child_followup_test_stage", "month_9");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put("child_facility_date", "15-10-2026");
        for (String stage : new String[]{"week_6", "month_9", "three_months_after_breastfeeding", "month_18", "not_applicable"}) {
            values.put("child_followup_test_stage", stage);
            assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        }
    }
}
