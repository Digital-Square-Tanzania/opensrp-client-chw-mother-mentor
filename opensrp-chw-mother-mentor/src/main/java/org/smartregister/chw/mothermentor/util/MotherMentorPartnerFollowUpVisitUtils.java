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

/** Allows one completed partner follow-up per client and local calendar day. */
public final class MotherMentorPartnerFollowUpVisitUtils {
    public static final String REFERRAL_SCHEMA = "3";
    public static final String TRACING = "tracing";
    public static final String REFERRAL = "referral";
    public static final String ATTENDANCE = "attendance";

    private MotherMentorPartnerFollowUpVisitUtils() {}

    public static boolean hasWorkflowSchema(Map<String, String> values) {
        return matches(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA), "2", REFERRAL_SCHEMA);
    }

    public static boolean isWorkflowDeath(Map<String, String> values) {
        String phase = values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE);
        return hasWorkflowSchema(values)
                && (TRACING.equals(phase) || (ATTENDANCE.equals(phase)
                    && REFERRAL_SCHEMA.equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))))
                && "not_found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                && "deceased".equals(values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON))
                && "deceased".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END));
    }

    public static boolean hasReferralAnswer(Map<String, String> values) {
        return matches(values.get(DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN), "yes", "no");
    }

    /** The next phase for a completed visit on the same linkage. */
    public static String nextPhase(Map<String, String> values) {
        if (!hasWorkflowSchema(values)) return TRACING;
        String phase = values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE);
        boolean agreed = TRACING.equals(phase)
                && "found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                && "agreed".equals(values.get(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT));
        if (agreed || REFERRAL.equals(phase)) {
            return "yes".equals(values.get(DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN))
                    ? ATTENDANCE : REFERRAL;
        }
        if (ATTENDANCE.equals(phase)) {
            return REFERRAL_SCHEMA.equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                    ? ATTENDANCE : REFERRAL;
        }
        return TRACING;
    }

    public static boolean hasCompletedVisitToday(List<Visit> visits, Date now) {
        if (visits == null || now == null) return false;
        for (Visit visit : visits) {
            if (visit == null || !Boolean.TRUE.equals(visit.getProcessed())) continue;
            boolean partnerSchema =
                    MotherMentorFollowUpType.PARTNER.getEventType().equals(visit.getVisitType());
            if (!partnerSchema
                    && !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
                continue;
            }
            try {
                JSONObject event = new JSONObject(visit.getJson());
                Map<String, String> values =
                        MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
                String category = values.get(DBConstants.KEY.FOLLOW_UP_CATEGORY);
                if (hasWorkflowSchema(values)
                        && !MotherMentorFollowUpType.PARTNER.getValue().equals(category)) continue;
                if (answered(category)
                        && !MotherMentorFollowUpType.PARTNER.getValue().equals(category)) continue;
                if (!isComplete(values, partnerSchema)) continue;
                Date visitDate = getVisitDate(visit, event, values);
                if (visitDate != null && isSameDay(visitDate, now)) return true;
            } catch (Exception ignored) {
                // Malformed or incomplete imported records must not block a valid follow-up.
            }
        }
        return false;
    }

    public static boolean isComplete(Map<String, String> values, boolean partnerSchema) {
        if (values == null || (!partnerSchema && values.containsKey(DBConstants.KEY.ATTENDANCE_TYPE))) {
            return false;
        }
        if (hasWorkflowSchema(values)) return isWorkflowComplete(values);
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
        String arrival =
                values.get(
                        partnerSchema
                                ? DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY
                                : DBConstants.KEY.RETURNED_TO_FACILITY);
        if (!matches(arrival, "yes", "no")) return false;
        if (!partnerSchema) {
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
        return !"yes".equals(arrival)
                || answered(
                        values.get(
                                partnerSchema
                                        ? DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE
                                        : DBConstants.KEY.FACILITY_RETURN_DATE));
    }

    public static boolean isCompleteSchema2(Map<String, String> values) {
        return values != null && isWorkflowComplete(values, false);
    }

    public static boolean isWorkflowComplete(Map<String, String> values) {
        return isWorkflowComplete(values,
                REFERRAL_SCHEMA.equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA)));
    }

    private static boolean isWorkflowComplete(Map<String, String> values, boolean usesReferral) {
        String phase = values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE);
        if (REFERRAL.equals(phase)) return usesReferral && hasReferralAnswer(values);
        if (TRACING.equals(phase)) {
            String status = values.get(DBConstants.KEY.FOLLOW_UP_STATUS);
            if ("found".equals(status)) {
                String agreement = values.get(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT);
                return educationComplete(values) && ("refused".equals(agreement)
                        || ("agreed".equals(agreement) && (!usesReferral || hasReferralAnswer(values))));
            }
            return "not_found".equals(status) && isNotFoundComplete(
                    values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON),
                    values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER));
        }
        if (!ATTENDANCE.equals(phase)) return false;
        // Earlier attendance forms recorded attendance directly, without a tracing answer.
        if (usesReferral && values.containsKey(DBConstants.KEY.FOLLOW_UP_STATUS)) {
            String status = values.get(DBConstants.KEY.FOLLOW_UP_STATUS);
            if ("not_found".equals(status)) {
                return isNotFoundComplete(values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON),
                        values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER));
            }
            if (!"found".equals(status)) return false;
        }
        String attendance = values.get(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY);
        if ("no".equals(attendance)) return true;
        if (!"yes".equals(attendance)
                || !validRecordedDate(values.get(DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE))) {
            return false;
        }
        if (usesReferral) return true;
        return educationComplete(values) && testComplete(values, DBConstants.KEY.PARTNER_HIV_TESTED,
                        DBConstants.KEY.PARTNER_HIV_RESULT,
                        DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON_OTHER, true)
                && testComplete(values, DBConstants.KEY.PARTNER_SYPHILIS_TESTED,
                        DBConstants.KEY.PARTNER_SYPHILIS_RESULT,
                        DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER, false)
                && testComplete(values, DBConstants.KEY.PARTNER_HEPATITIS_B_TESTED,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_RESULT,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON_OTHER, false);
    }

    public static boolean isNotFoundComplete(String reason, String other) {
        return MotherMentorPartnerClosure.isImmediateNotFoundReason(reason)
                || "travelled".equals(reason)
                || ("other".equals(reason) && answered(other));
    }

    private static boolean educationComplete(Map<String, String> values) {
        String provided = values.get(DBConstants.KEY.PARTNER_EDUCATION_PROVIDED);
        if ("no".equals(provided)) return true;
        String topics = values.get(DBConstants.KEY.PARTNER_EDUCATION_TOPICS);
        if (!"yes".equals(provided) || !answered(topics)) return false;
        String[] selected = topics.replace("[", "").replace("]", "")
                .replace("\"", "").split(",", -1);
        for (String topic : selected) {
            if (!matches(topic.trim(), "hiv", "syphilis", "hepatitis_b")) return false;
        }
        return selected.length > 0;
    }

    private static boolean testComplete(Map<String, String> values, String testedKey,
            String resultKey, String reasonKey, String otherKey, boolean hiv) {
        String tested = values.get(testedKey);
        if ("yes".equals(tested)) {
            return matches(values.get(resultKey), "positive", "negative");
        }
        if (!"no".equals(tested)) return false;
        String reason = values.get(reasonKey);
        return (matches(reason, "stock_out", "refused", "other")
                        || (hiv && "already_infected".equals(reason)))
                && (!"other".equals(reason) || answered(values.get(otherKey)));
    }

    /** Validates the recorded formats accepted by partner attendance and completion. */
    public static boolean validRecordedDate(String value) {
        if (!answered(value) || !(value.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")
                || value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false;
        for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length()) return true;
        }
        return false;
    }

    public static Date getVisitDate(Visit visit, JSONObject event, Map<String, String> values) {
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
