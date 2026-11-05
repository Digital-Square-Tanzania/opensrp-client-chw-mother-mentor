package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Checks saved observations against the required sections of a mother visit. */
public final class MotherMentorVisitCompletion {
    private MotherMentorVisitCompletion() {}

    public static boolean isFullVisit(String attendance, String status) {
        return "new".equals(attendance) || ("repeat".equals(attendance) && "found".equals(status));
    }

    public static boolean isVisitTypeComplete(Map<String, String> values) {
        return MotherMentorMotherVisitUtils.isEntryComplete(values)
                && MotherMentorMotherPregnancyUtils.isComplete(values, serviceDate(values));
    }

    public static boolean isComplete(JSONArray observations) {
        Map<String, String> values = observationValues(observations);
        return isVisitTypeComplete(values) && MotherMentorMotherVisitUtils.isComplete(values);
    }

    private static LocalDate serviceDate(Map<String, String> values) {
        return values.containsKey(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE)
                ? MotherMentorMotherPregnancyUtils.parseDate(values.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE))
                : LocalDate.now();
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

}
