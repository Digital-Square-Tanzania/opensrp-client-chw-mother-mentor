package org.smartregister.chw.mothermentor.util;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Allows one completed Child EID follow-up per client and local calendar day. */
public final class MotherMentorChildEidFollowUpVisitUtils {
    private MotherMentorChildEidFollowUpVisitUtils() {}

    public static boolean hasCompletedVisitToday(List<Visit> visits, Date now) {
        if (visits == null || now == null) return false;
        for (Visit visit : visits) {
            if (visit == null || !Boolean.TRUE.equals(visit.getProcessed())) continue;
            boolean childSchema =
                    MotherMentorFollowUpType.CHILD_EID.getEventType().equals(visit.getVisitType());
            if (!childSchema
                    && !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
                continue;
            }
            try {
                JSONObject event = new JSONObject(visit.getJson());
                Map<String, String> values =
                        MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
                String category = values.get(DBConstants.KEY.FOLLOW_UP_CATEGORY);
                if (answered(category)
                        && !MotherMentorFollowUpType.CHILD_EID.getValue().equals(category)) {
                    continue;
                }
                if (!isComplete(values, childSchema)) continue;
                Date visitDate = getVisitDate(visit, event, values);
                if (visitDate != null && isSameDay(visitDate, now)) return true;
            } catch (Exception ignored) {
                // Malformed or incomplete imported records must not block a valid follow-up.
            }
        }
        return false;
    }

    public static boolean isComplete(Map<String, String> values, boolean childSchema) {
        String status = values.get(DBConstants.KEY.FOLLOW_UP_STATUS);
        if (!matches(
                status,
                "found",
                "not_found",
                "deceased",
                "transferred_facility",
                "refused_return",
                "under_follow_up")) return false;
        if (!"found".equals(status)) return true;
        if (childSchema && !answered(values.get(DBConstants.KEY.CHILD_EID_TEST_TYPE))) return false;
        String arrival =
                values.get(
                        childSchema
                                ? DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY
                                : DBConstants.KEY.RETURNED_TO_FACILITY);
        if (!matches(arrival, "yes", "no")) return false;
        if (!childSchema) {
            String reason = values.get(DBConstants.KEY.SERVICE_INTERRUPTION_REASON);
            if (!answered(reason)
                    || !answered(values.get(DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE))) {
                return false;
            }
            if (DBConstants.KEY.OTHER.equals(reason)
                    && !answered(values.get(DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER))) {
                return false;
            }
        }
        boolean complete = !"yes".equals(arrival)
                || answered(
                        values.get(
                                childSchema
                                        ? DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE
                                        : DBConstants.KEY.FACILITY_RETURN_DATE));
        if (complete && "2".equals(values.get(DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA))
                && "eighteen_months".equals(values.get(DBConstants.KEY.CHILD_EID_TEST_TYPE))
                && "yes".equals(arrival)) {
            return isFinalTestComplete(values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE),
                    values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT),
                    values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE));
        }
        return complete;
    }

    public static boolean isFinalTestComplete(String done, String result, String date) {
        if ("no".equals(done)) return true;
        if (!"yes".equals(done) || !matches(result, "positive", "negative", "pending")) return false;
        if (!answered(date)) return false;
        for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(date, position);
            if (parsed != null && position.getIndex() == date.length()) return true;
        }
        return false;
    }

    private static Date getVisitDate(Visit visit, JSONObject event, Map<String, String> values) {
        String recordedDate = values.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
        if (answered(recordedDate)) {
            for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd"}) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date date = format.parse(recordedDate, position);
                if (date != null && position.getIndex() == recordedDate.length()) return date;
            }
            return null;
        }
        // Import timestamps are not the date on which the follow-up was provided.
        Object eventDate = event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP);
        if (eventDate instanceof Number) return new Date(((Number) eventDate).longValue());
        if (eventDate instanceof String && answered((String) eventDate)) {
            try {
                return new DateTime(eventDate).toDate();
            } catch (IllegalArgumentException ignored) {
                // Older local visits can have only the persisted visit timestamp.
            }
        }
        return visit.getDate();
    }

    private static boolean isSameDay(Date visitDate, Date now) {
        Calendar visitDay = Calendar.getInstance();
        visitDay.setTime(visitDate);
        Calendar today = Calendar.getInstance();
        today.setTime(now);
        return visitDay.get(Calendar.ERA) == today.get(Calendar.ERA)
                && visitDay.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && visitDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean answered(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean matches(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }
}
