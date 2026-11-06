package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FACILITY_RETURN_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON_OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_AGREEMENT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.RETURNED_TO_FACILITY;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;

import java.util.Map;

/** Validation shared by the staged form and replay of a finalized IIT visit. */
public final class MotherMentorIitReturnUtils {
    public static final String NONE = "none";
    public static final String REFERRED = "referred";
    public static final String AGREED = "agreed";
    public static final String REFUSED = "refused";

    private MotherMentorIitReturnUtils() {}

    public static boolean isComplete(Map<String, String> values, String returnState,
            String missedDate, LocalDate serviceDate) {
        if (values == null || serviceDate == null) return false;
        if (NONE.equals(returnState)) {
            String agreement = values.get(IIT_RETURN_AGREEMENT);
            return (AGREED.equals(agreement) || REFUSED.equals(agreement))
                    && validReason(values, SERVICE_INTERRUPTION_REASON,
                            SERVICE_INTERRUPTION_REASON_OTHER);
        }
        if (!REFERRED.equals(returnState)) return false;
        String returned = values.get(RETURNED_TO_FACILITY);
        if ("no".equals(returned)) {
            return validReason(values, IIT_NOT_RETURNED_REASON, IIT_NOT_RETURNED_REASON_OTHER);
        }
        if (!"yes".equals(returned)) return false;
        LocalDate date = MotherMentorHeiSchedule.parseDate(values.get(FACILITY_RETURN_DATE));
        LocalDate missed = MotherMentorHeiSchedule.parseDate(missedDate);
        return date != null && !date.isAfter(serviceDate) && !date.isAfter(LocalDate.now())
                && (missed == null || !date.isBefore(missed));
    }

    private static boolean validReason(Map<String, String> values, String key, String otherKey) {
        switch (StringUtils.defaultString(values.get(key))) {
            case "medicine_side_effects":
            case "forgot":
            case "travel_or_moved":
            case "transport_cost":
            case "facility_congestion":
            case "stigma":
                return true;
            case OTHER:
                return StringUtils.isNotBlank(values.get(otherKey));
            default:
                return false;
        }
    }
}
