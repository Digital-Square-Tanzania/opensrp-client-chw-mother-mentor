package org.smartregister.chw.mothermentor.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Completion follows the sections applicable to this visit, including legacy saved visits. */
public final class MotherMentorVisitCompletion {
    private MotherMentorVisitCompletion() {}

    public static boolean isFullVisit(String attendance, String status) {
        return matches(attendance, "new", "Mpya")
                || (matches(attendance, "repeat", "Marudio")
                        && matches(
                                status,
                                "continuing_services",
                                "Continuing Services",
                                "Anaendelea na huduma"));
    }

    public static boolean isVisitTypeComplete(Map<String, String> values) {
        String attendance = values.get(DBConstants.KEY.ATTENDANCE_TYPE);
        if (!matches(attendance, "new", "Mpya", "repeat", "Marudio")) return false;
        if (matches(attendance, "repeat", "Marudio")
                && !answered(values, DBConstants.KEY.FOLLOW_UP_STATUS)) return false;
        String version = values.get(DBConstants.KEY.MOTHERMENTOR_REPORTING_VERSION);
        if (!matches(version, "1", "2")) return true;
        if (isFullVisit(attendance, values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                && "true".equals(values.get(DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT))) {
            if (!answered(values, DBConstants.KEY.VISIT_PREGNANCY_STATUS)) return false;
            if (!"true".equals(values.get(DBConstants.KEY.ART_INITIATED_BEFORE_VISIT))) {
                if (!answered(values, DBConstants.KEY.NEW_PMTCT_ART_INITIATION)) return false;
                if ("yes".equals(values.get(DBConstants.KEY.NEW_PMTCT_ART_INITIATION))
                        && !answered(values, DBConstants.KEY.VISIT_ART_START_DATE)) return false;
            }
        }
        if ("true".equals(values.get(DBConstants.KEY.IIT_ENROLLED))) {
            if (!answered(values, DBConstants.KEY.IIT_TRACING_VISIT)) return false;
            if ("yes".equals(values.get(DBConstants.KEY.IIT_TRACING_VISIT))) {
                if (!answered(
                        values,
                        DBConstants.KEY.IIT_TRACING_OUTCOME,
                        DBConstants.KEY.IIT_INTERRUPTION_REASON,
                        DBConstants.KEY.RETURNED_TO_ART)) return false;
                if (DBConstants.KEY.OTHER.equals(
                                values.get(DBConstants.KEY.IIT_INTERRUPTION_REASON))
                        && !answered(values, DBConstants.KEY.IIT_INTERRUPTION_OTHER)) return false;
                if ("yes".equals(values.get(DBConstants.KEY.RETURNED_TO_ART))
                        && !answered(values, DBConstants.KEY.ART_RETURN_DATE)) return false;
            }
        }
        // Version 1 and older saved visits keep their original completion requirements.
        if ("2".equals(version)) {
            if ("true".equals(values.get(DBConstants.KEY.PARTNER_ENROLLED))
                    && !facilityAttendanceComplete(
                            values,
                            DBConstants.KEY.PARTNER_REACHED_FACILITY,
                            DBConstants.KEY.PARTNER_FACILITY_DATE)) return false;
            if ("true".equals(values.get(DBConstants.KEY.CHILD_EID_ENROLLED))) {
                if (!matches(
                        values.get(DBConstants.KEY.CHILD_FOLLOWUP_TEST_STAGE),
                        "week_6",
                        "month_9",
                        "three_months_after_breastfeeding",
                        "month_18",
                        "not_applicable")) return false;
                if (!facilityAttendanceComplete(
                        values,
                        DBConstants.KEY.CHILD_REACHED_FACILITY,
                        DBConstants.KEY.CHILD_FACILITY_DATE)) return false;
            }
        }
        return true;
    }

    private static boolean facilityAttendanceComplete(
            Map<String, String> values, String answerKey, String dateKey) {
        String answer = values.get(answerKey);
        return matches(answer, "yes", "no", "not_applicable")
                && (!"yes".equals(answer) || answered(values, dateKey));
    }

    public static boolean isComplete(JSONArray observations) {
        Map<String, String> values = observationValues(observations);
        if (!isVisitTypeComplete(values) || !answered(values, DBConstants.KEY.COMMENTS))
            return false;
        return !isFullVisit(
                        values.get(DBConstants.KEY.ATTENDANCE_TYPE),
                        values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                || answered(
                        values,
                        DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP,
                        DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP,
                        DBConstants.KEY.EDUCATION_PROVIDED,
                        DBConstants.KEY.REFERRAL_GIVEN,
                        DBConstants.KEY.NEXT_APPOINTMENT_DATE);
    }

    public static Map<String, String> observationValues(JSONArray observations) {
        Map<String, String> values = new HashMap<>();
        if (observations == null) return values;
        for (int i = 0; i < observations.length(); i++) {
            JSONObject obs = observations.optJSONObject(i);
            if (obs == null) continue;
            JSONArray answers = obs.optJSONArray("values");
            if (answers != null && answers.length() > 0)
                values.put(obs.optString("fieldCode"), answers.optString(0));
        }
        return values;
    }

    private static boolean answered(Map<String, String> values, String... keys) {
        for (String key : keys)
            if (values.get(key) == null || values.get(key).trim().isEmpty()) return false;
        return true;
    }

    private static boolean matches(String value, String... choices) {
        if (value == null) return false;
        for (String choice : choices) if (choice.equalsIgnoreCase(value.trim())) return true;
        return false;
    }
}
