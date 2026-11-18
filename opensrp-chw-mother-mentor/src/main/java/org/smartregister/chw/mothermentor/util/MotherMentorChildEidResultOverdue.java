package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.Map;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Uses the recorded sample date and the device's calendar day. */
public final class MotherMentorChildEidResultOverdue {
    public static final String DISPLAY_STATE = "results_overdue";
    private static final String BIRTH_TEST = "at_birth";
    private static final int BIRTH_LIMIT_DAYS = 7;
    private static final int DBS_LIMIT_DAYS = 14;

    private MotherMentorChildEidResultOverdue() {}

    static LocalDate overdueDate(Map<String, String> values, LocalDate day) {
        if (!MotherMentorChildEidReturnVisit.AWAITING_RESULT.equals(values.get(MTOTO_RUFAA_STATE))) return null;
        String code = values.get(MTOTO_PENDING_TEST);
        int limit = BIRTH_TEST.equals(code) ? BIRTH_LIMIT_DAYS : limit(Stage.fromCode(code));
        LocalDate sample = MotherMentorHeiSchedule.parseDate(values.get(MTOTO_PENDING_TEST_DATE));
        return limit > 0 && sample != null && day.isAfter(sample.plusDays(limit)) ? sample : null;
    }

    /** List queries use the same thresholds as the card and keep the stored state intact. */
    public static String registerState(String alias) {
        String state = alias + "." + MTOTO_RUFAA_STATE;
        String test = alias + "." + MTOTO_PENDING_TEST;
        String sample = alias + "." + MTOTO_PENDING_TEST_DATE;
        StringBuilder limitDays = new StringBuilder("CASE ").append(test)
                .append(" WHEN '").append(BIRTH_TEST).append("' THEN ").append(BIRTH_LIMIT_DAYS);
        for (Stage stage : Stage.values()) {
            if (limit(stage) == 0) continue;
            limitDays.append(" WHEN '").append(stage.getCode()).append("' THEN ").append(limit(stage))
                    .append(" WHEN '").append(stage.getMetadataCode()).append("' THEN ").append(limit(stage));
        }
        limitDays.append(" END");
        return "CASE WHEN " + state + " = '" + MotherMentorChildEidReturnVisit.AWAITING_RESULT
                + "' AND " + alias + "." + IS_CLOSED + " IS NOT 1"
                + " AND julianday(date('now', 'localtime')) - julianday(" + sample + ") > (" + limitDays
                + ") THEN '" + DISPLAY_STATE + "' ELSE " + state + " END AS " + MTOTO_RUFAA_STATE;
    }

    private static int limit(Stage stage) {
        return stage == null || stage == Stage.EIGHTEEN_MONTHS ? 0 : DBS_LIMIT_DAYS;
    }
}
