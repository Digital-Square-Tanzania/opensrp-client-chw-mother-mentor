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

/** Allows one completed household service visit per local calendar day. */
public final class MotherMentorHouseholdVisitUtils {
    private MotherMentorHouseholdVisitUtils() {}

    public static boolean hasCompletedVisitToday(List<Visit> visits, Date now) {
        if (visits == null || now == null) return false;
        for (Visit visit : visits) {
            if (visit == null
                    || !Boolean.TRUE.equals(visit.getProcessed())
                    || !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
                continue;
            }
            try {
                JSONObject event = new JSONObject(visit.getJson());
                Map<String, String> values =
                        MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
                if (!hasCompletedHouseholdServices(values)) continue;
                Date visitDate = getVisitDate(visit, event, values);
                if (visitDate != null && isSameDay(visitDate, now)) return true;
            } catch (Exception ignored) {
                // Malformed or incomplete records must not block valid household services.
            }
        }
        return false;
    }

    private static boolean hasCompletedHouseholdServices(Map<String, String> values) {
        for (String key :
                new String[] {
                    DBConstants.KEY.PURPOSE_OF_VISIT,
                    DBConstants.KEY.PARTICIPANTS,
                    DBConstants.KEY.TOPICS_TAUGHT
                }) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) return false;
        }
        return true;
    }

    private static Date getVisitDate(Visit visit, JSONObject event, Map<String, String> values) {
        String recordedDate = values.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
        if (recordedDate != null && !recordedDate.trim().isEmpty()) {
            for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd"}) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date date = format.parse(recordedDate, position);
                if (date != null && position.getIndex() == recordedDate.length()) return date;
            }
            return null;
        }
        // Imported visits can have a recent persistence timestamp but an older event date.
        Object eventDate = event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP);
        if (eventDate instanceof Number) return new Date(((Number) eventDate).longValue());
        if (eventDate instanceof String && !((String) eventDate).isEmpty()) {
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
}
