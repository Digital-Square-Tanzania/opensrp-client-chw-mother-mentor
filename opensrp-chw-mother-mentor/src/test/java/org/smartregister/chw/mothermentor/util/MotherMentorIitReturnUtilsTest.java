package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FACILITY_RETURN_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON_OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_AGREEMENT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.RETURNED_TO_FACILITY;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER;

import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MotherMentorIitReturnUtilsTest {
    private static final String MISSED = "2024-10-20";
    private static final LocalDate VISIT = new LocalDate(2024, 11, 5);

    @Test
    public void firstContactRequiresBothReasonAndAgreement() {
        Map<String, String> values = new HashMap<>();
        values.put(RETURNED_TO_FACILITY, "yes");
        values.put(FACILITY_RETURN_DATE, "20-10-2024");
        assertFalse(complete(values, "none"));
        values.put(SERVICE_INTERRUPTION_REASON, "travel_or_moved");
        assertFalse(complete(values, "none"));
        values.put(IIT_RETURN_AGREEMENT, "agreed");
        assertTrue(complete(values, "none"));
        values.put(IIT_RETURN_AGREEMENT, "refused");
        assertTrue(complete(values, "none"));
        values.put(SERVICE_INTERRUPTION_REASON, "unrecognized");
        assertFalse(complete(values, "none"));
        values.put(SERVICE_INTERRUPTION_REASON, "other");
        values.put(SERVICE_INTERRUPTION_REASON_OTHER, " ");
        assertFalse(complete(values, "none"));
        values.put(SERVICE_INTERRUPTION_REASON_OTHER, "Care duties");
        assertTrue(complete(values, "none"));
        assertFalse(complete(values, "unknown"));
    }

    @Test
    public void confirmedReturnMustFallOnOrAfterMissedDateAndOnOrBeforeVisit() {
        Map<String, String> values = new HashMap<>();
        values.put(RETURNED_TO_FACILITY, "yes");
        assertFalse(complete(values, "referred"));
        for (String invalid : new String[] {"19-10-2024", "2024-11-06", "31-10-2024x", "31-09-2024"}) {
            values.put(FACILITY_RETURN_DATE, invalid);
            assertFalse(invalid, complete(values, "referred"));
        }
        values.put(FACILITY_RETURN_DATE, "20-10-2024");
        assertTrue(complete(values, "referred"));
        values.put(FACILITY_RETURN_DATE, "05/11/2024");
        assertTrue(complete(values, "referred"));
        LocalDate future = LocalDate.now().plusDays(1);
        values.put(FACILITY_RETURN_DATE, future.toString());
        assertFalse(MotherMentorIitReturnUtils.isComplete(values, "referred", MISSED, future));
    }

    @Test
    public void didNotReturnRequiresItsOwnReasonAndOtherText() {
        Map<String, String> values = new HashMap<>();
        values.put(RETURNED_TO_FACILITY, "no");
        values.put(SERVICE_INTERRUPTION_REASON, "forgot");
        assertFalse(complete(values, "referred"));
        values.put(IIT_NOT_RETURNED_REASON, "other");
        assertFalse(complete(values, "referred"));
        values.put(IIT_NOT_RETURNED_REASON_OTHER, "Taking care of children");
        assertTrue(complete(values, "referred"));
        values.put(IIT_NOT_RETURNED_REASON, "transport_cost");
        values.remove(IIT_NOT_RETURNED_REASON_OTHER);
        assertTrue(complete(values, "referred"));
    }

    private static boolean complete(Map<String, String> values, String state) {
        return MotherMentorIitReturnUtils.isComplete(values, state, MISSED, VISIT);
    }
}
