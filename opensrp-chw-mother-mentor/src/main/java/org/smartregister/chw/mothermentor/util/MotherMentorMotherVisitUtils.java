package org.smartregister.chw.mothermentor.util;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Schema-specific completion for the mother visit entry and its applicable service sections. */
public final class MotherMentorMotherVisitUtils {
    private MotherMentorMotherVisitUtils() {}

    public static boolean isSchema2(Map<String, String> values) {
        return values != null && "2".equals(values.get(DBConstants.KEY.MAMA_VISIT_SCHEMA));
    }

    public static boolean isEntryComplete(Map<String, String> values) {
        if (!isSchema2(values)) return MotherMentorVisitCompletion.isVisitTypeComplete(values);
        String type = values.get(DBConstants.KEY.ATTENDANCE_TYPE);
        if ("new".equals(type)) return true;
        if (!"repeat".equals(type)) return false;
        String status = values.get(DBConstants.KEY.FOLLOW_UP_STATUS);
        if ("found".equals(status)) return true;
        if (!"not_found".equals(status)) return false;
        String reason = values.get(DBConstants.KEY.MAMA_NOT_FOUND_REASON);
        if ("deceased".equals(reason) || "moved_area".equals(reason)) return true;
        if ("moved_facility".equals(reason)) {
            return answered(values, DBConstants.KEY.MAMA_KITUO_KIPYA);
        }
        return "other".equals(reason)
                && answered(values, DBConstants.KEY.MAMA_SABABU_NYINGINE_TEXT);
    }

    public static boolean isFullVisit(Map<String, String> values) {
        if (!isSchema2(values))
            return MotherMentorVisitCompletion.isFullVisit(
                    values.get(DBConstants.KEY.ATTENDANCE_TYPE),
                    values.get(DBConstants.KEY.FOLLOW_UP_STATUS));
        return "new".equals(values.get(DBConstants.KEY.ATTENDANCE_TYPE))
                || ("repeat".equals(values.get(DBConstants.KEY.ATTENDANCE_TYPE))
                        && "found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS)));
    }

    public static boolean isComplete(Map<String, String> values) {
        if (!isEntryComplete(values)) return false;
        return !isFullVisit(values)
                || (isArtComplete(values)
                        && answered(values,
                                DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP,
                                DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP,
                                DBConstants.KEY.EDUCATION_PROVIDED, DBConstants.KEY.REFERRAL_GIVEN,
                                DBConstants.KEY.NEXT_APPOINTMENT_DATE));
    }

    public static boolean isComplete(JSONArray observations) {
        return isComplete(MotherMentorVisitCompletion.observationValues(observations));
    }

    /** ART initiation remains New-only and is not asked again after previously starting ART. */
    public static boolean isArtComplete(Map<String, String> values) {
        if (!isSchema2(values) || !"new".equals(values.get(DBConstants.KEY.ATTENDANCE_TYPE))
                || "true".equals(values.get(DBConstants.KEY.ART_INITIATED_BEFORE_VISIT)))
            return true;
        String started = values.get(DBConstants.KEY.NEW_PMTCT_ART_INITIATION);
        return "no".equals(started)
                || ("yes".equals(started)
                        && answered(values, DBConstants.KEY.VISIT_ART_START_DATE));
    }

    public static Map<String, String> formValues(JSONObject form) {
        Map<String, String> values = new HashMap<>();
        if (form == null) return values;
        for (int step = 1; step <= form.optInt("count", 1); step++) {
            JSONObject page = form.optJSONObject("step" + step);
            JSONArray fields = page == null ? null : page.optJSONArray("fields");
            for (int i = 0; fields != null && i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field != null) values.put(field.optString("key"), field.optString("value"));
            }
        }
        return values;
    }

    private static boolean answered(Map<String, String> values, String... keys) {
        for (String key : keys)
            if (StringUtils.isBlank(values.get(key))) return false;
        return true;
    }
}
