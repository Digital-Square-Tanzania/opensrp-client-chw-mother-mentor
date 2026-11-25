package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads the coded death outcomes shared by maternal and category-specific visits. */
public final class MotherMentorDeceasedVisitUtils {
    private MotherMentorDeceasedVisitUtils() {}

    public static boolean isDeceased(String json) {
        try {
            JSONObject event = new JSONObject(json);
            if (event.optBoolean("voided")) return false;
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    event.optJSONArray("obs"));
            if (values.containsKey(DBConstants.KEY.ATTENDANCE_TYPE)) {
                return "repeat".equals(values.get(DBConstants.KEY.ATTENDANCE_TYPE))
                        && "not_found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                        && "deceased".equals(values.get(DBConstants.KEY.MAMA_NOT_FOUND_REASON));
            }
            return "deceased".equalsIgnoreCase(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                    || (MotherMentorFollowUpType.CHILD_EID.getEventType().equals(event.optString("eventType"))
                            && "found".equals(values.get(FOLLOW_UP_STATUS))
                            && "deceased".equals(values.get(MAMA_CHILD_STATUS))
                            && parseDate(values.get(MAMA_TAREHE_MTOTO_ALIFARIKI)) != null)
                    || (MotherMentorFollowUpType.MOTHER_IIT.getEventType().equals(event.optString("eventType"))
                            && "not_found".equals(values.get(FOLLOW_UP_STATUS))
                            && "deceased".equals(values.get(IIT_NOT_FOUND_REASON)))
                    || "deceased".equalsIgnoreCase(values.get(DBConstants.KEY.IIT_TRACING_OUTCOME))
                    || "client_deceased".equalsIgnoreCase(
                            values.get(DBConstants.KEY.FOLLOW_UP_OUTCOME))
                    || ("PARTNER".equals(values.get(DBConstants.KEY.FOLLOW_UP_CATEGORY))
                            && MotherMentorPartnerFollowUpVisitUtils.isWorkflowDeath(values));
        } catch (Exception e) {
            return false;
        }
    }

    /** Maternal assessments record the child's death on the mother's event. */
    public static Map<String, LocalDate> linkedChildDeaths(SQLiteDatabase db, String motherId,
            Map<String, String> values, LocalDate serviceDay) {
        Map<String, LocalDate> deaths = new LinkedHashMap<>();
        boolean sectioned = MotherMentorDeliveryChildren.hasSections(values);
        List<String> visited = sectioned
                ? Arrays.asList(values.getOrDefault(MotherMentorDeliveryChildren.VISITED, "").split(","))
                : Collections.singletonList("1");
        for (int number = 1; number <= (sectioned ? MotherMentorDeliveryChildren.MAX_COUNT : 1); number++) {
            if (!visited.contains(Integer.toString(number))) continue;
            Map<String, String> child = MotherMentorDeliveryChildren.childVisit(values, number);
            LocalDate date = maternalChildDeathDate(child, serviceDay);
            if (date == null) continue;

            LocalDate dob = MotherMentorMotherChildUtils.birth(child);
            Set<String> matches = MotherMentorChildEidDao.childrenBornOn(db, motherId, dob);
            String explicitLink = child.get(MotherMentorDeliveryChildren.LINK);
            if (StringUtils.isNotBlank(explicitLink)) {
                if (matches.contains(explicitLink)) deaths.putIfAbsent(explicitLink, date);
            } else if (matches.size() == 1
                    && (!sectioned || number == 1 && MotherMentorDeliveryChildren.count(values) <= 1)) {
                deaths.putIfAbsent(matches.iterator().next(), date);
            }
        }
        return deaths;
    }

    public static LocalDate maternalChildDeathDate(Map<String, String> child, LocalDate serviceDay) {
        if (!"deceased".equals(child.get(MAMA_CHILD_STATUS))
                || !MotherMentorMotherChildUtils.applies(child)
                || !MotherMentorMotherChildUtils.isRoutineAssessmentComplete(child, serviceDay, false)) {
            return null;
        }
        return MotherMentorHeiSchedule.parseDate(child.get(MAMA_TAREHE_MTOTO_ALIFARIKI));
    }

    public static Date getRecordedDate(String json, String visitTimestamp) {
        try {
            JSONObject event = new JSONObject(json);
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    event.optJSONArray("obs"));
            if (MotherMentorFollowUpType.CHILD_EID.getEventType().equals(event.optString("eventType"))
                    && "found".equals(values.get(FOLLOW_UP_STATUS))
                    && "deceased".equals(values.get(MAMA_CHILD_STATUS))) {
                Date childDeathDate = parseDate(values.get(MAMA_TAREHE_MTOTO_ALIFARIKI));
                if (childDeathDate != null) return childDeathDate;
            }
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
