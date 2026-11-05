package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MotherMentorVisitCompletionTest {
    @Test
    public void onlyNewAndFoundRepeatVisitsShowServiceActions() {
        assertTrue(MotherMentorVisitCompletion.isFullVisit("new", null));
        assertTrue(MotherMentorVisitCompletion.isFullVisit("repeat", "found"));
        assertFalse(MotherMentorVisitCompletion.isFullVisit("repeat", "not_found"));
        assertFalse(MotherMentorVisitCompletion.isFullVisit("repeat", null));
    }

    @Test
    public void notFoundRepeatRequiresAReason() {
        Map<String, String> values = new HashMap<>();
        values.put(DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put(DBConstants.KEY.FOLLOW_UP_STATUS, "not_found");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put(DBConstants.KEY.MAMA_NOT_FOUND_REASON, "moved_area");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
    }
}
