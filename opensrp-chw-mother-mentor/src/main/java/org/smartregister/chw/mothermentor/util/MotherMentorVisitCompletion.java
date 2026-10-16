package org.smartregister.chw.mothermentor.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Completion follows the sections applicable to this visit, including legacy saved visits. */
public final class MotherMentorVisitCompletion {
    private MotherMentorVisitCompletion() { }

    public static boolean isFullVisit(String attendance, String status) {
        return matches(attendance, "new", "Mpya") || (matches(attendance, "repeat", "Marudio")
                && matches(status, "continuing_services", "Continuing Services", "Anaendelea na huduma"));
    }

    public static boolean isVisitTypeComplete(Map<String, String> values) {
        String attendance = values.get("attendance_type");
        if (!matches(attendance, "new", "Mpya", "repeat", "Marudio")) return false;
        if (matches(attendance, "repeat", "Marudio") && !answered(values, "follow_up_status")) return false;
        String version = values.get("mothermentor_reporting_version");
        if (!matches(version, "1", "2")) return true;
        if (isFullVisit(attendance, values.get("follow_up_status"))
                && "true".equals(values.get("primary_mothermentor_client"))) {
            if (!answered(values, "visit_pregnancy_status", "new_pmtct_art_initiation")) return false;
            if ("yes".equals(values.get("new_pmtct_art_initiation")) && !answered(values, "visit_art_start_date")) return false;
        }
        if ("true".equals(values.get("iit_enrolled"))) {
            if (!answered(values, "iit_tracing_visit")) return false;
            if ("yes".equals(values.get("iit_tracing_visit"))) {
                if (!answered(values, "iit_tracing_outcome", "iit_interruption_reason", "returned_to_art")) return false;
                if ("other".equals(values.get("iit_interruption_reason")) && !answered(values, "iit_interruption_other")) return false;
                if ("yes".equals(values.get("returned_to_art")) && !answered(values, "art_return_date")) return false;
            }
        }
        // Version 1 and older saved visits keep their original completion requirements.
        if ("2".equals(version)) {
            if ("true".equals(values.get("partner_enrolled"))
                    && !facilityAttendanceComplete(values, "partner_reached_facility", "partner_facility_date")) return false;
            if ("true".equals(values.get("child_eid_enrolled"))) {
                if (!matches(values.get("child_followup_test_stage"), "week_6", "month_9",
                        "three_months_after_breastfeeding", "month_18", "not_applicable")) return false;
                if (!facilityAttendanceComplete(values, "child_reached_facility", "child_facility_date")) return false;
            }
        }
        return true;
    }

    private static boolean facilityAttendanceComplete(Map<String, String> values, String answerKey, String dateKey) {
        String answer = values.get(answerKey);
        return matches(answer, "yes", "no", "not_applicable")
                && (!"yes".equals(answer) || answered(values, dateKey));
    }

    public static boolean isComplete(JSONArray observations) {
        Map<String, String> values = observationValues(observations);
        if (!isVisitTypeComplete(values) || !answered(values, "comments")) return false;
        return !isFullVisit(values.get("attendance_type"), values.get("follow_up_status"))
                || answered(values, "has_been_linked_to_psychosocial_support_group", "has_been_linked_to_iga_group",
                "education_provided", "referral_given", "next_appointment_date");
    }

    public static Map<String, String> observationValues(JSONArray observations) {
        Map<String, String> values = new HashMap<>();
        if (observations == null) return values;
        for (int i = 0; i < observations.length(); i++) {
            JSONObject obs = observations.optJSONObject(i);
            if (obs == null) continue;
            JSONArray answers = obs.optJSONArray("values");
            if (answers != null && answers.length() > 0) values.put(obs.optString("fieldCode"), answers.optString(0));
        }
        return values;
    }

    private static boolean answered(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) == null || values.get(key).trim().isEmpty()) return false;
        return true;
    }

    private static boolean matches(String value, String... choices) {
        if (value == null) return false;
        for (String choice : choices) if (choice.equalsIgnoreCase(value.trim())) return true;
        return false;
    }
}
