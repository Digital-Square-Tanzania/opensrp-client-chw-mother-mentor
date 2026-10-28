package org.smartregister.chw.mothermentor.util;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** Reads the coded death outcomes shared by maternal and category-specific visits. */
public final class MotherMentorDeceasedVisitUtils {
    private MotherMentorDeceasedVisitUtils() {}

    public static boolean isDeceased(String json) {
        try {
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    new JSONObject(json).optJSONArray("obs"));
            return "deceased".equalsIgnoreCase(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                    || "deceased".equalsIgnoreCase(values.get(DBConstants.KEY.IIT_TRACING_OUTCOME))
                    || "client_deceased".equalsIgnoreCase(
                            values.get(DBConstants.KEY.FOLLOW_UP_OUTCOME))
                    || ("PARTNER".equals(values.get(DBConstants.KEY.FOLLOW_UP_CATEGORY))
                            && "2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                            && "tracing".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                            && "not_found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                            && "deceased".equals(values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON))
                            && "deceased".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END)));
        } catch (Exception e) {
            return false;
        }
    }

    public static Date getRecordedDate(String json, String visitTimestamp) {
        try {
            JSONObject event = new JSONObject(json);
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    event.optJSONArray("obs"));
            for (String key : new String[] {
                DBConstants.KEY.DOD, DBConstants.KEY.DATE_DIED,
                DBConstants.KEY.MOTHERMENTOR_VISIT_DATE
            }) {
                Date date = parseDate(values.get(key));
                if (date != null) return date;
            }
            Object eventDate = event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP);
            if (eventDate instanceof Number) return new Date(((Number) eventDate).longValue());
            Date date = parseDate(event.optString(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
            if (date != null) return date;
        } catch (Exception e) {
            // An older persisted visit can still supply its own service timestamp.
        }
        try {
            return visitTimestamp == null ? null : new Date(Long.parseLong(visitTimestamp));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length()) return date;
        }
        if (!value.matches("^\\d{4}-\\d{2}-\\d{2}T.+$")) return null;
        try {
            // The recorded calendar date must survive offsets near midnight. The host
            // formats this Date in its own timezone when creating the shared death event.
            return ISODateTimeFormat.dateOptionalTimeParser()
                    .withOffsetParsed()
                    .parseDateTime(value)
                    .toLocalDate()
                    .toDate();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
