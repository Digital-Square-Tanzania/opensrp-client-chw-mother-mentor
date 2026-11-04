package org.smartregister.chw.mothermentor.util;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Validation and inactive-answer clearing for the current Child EID tracing section. */
public final class MotherMentorChildEidStatusUtils {
    private static final String[] FIELDS = {
        CHILD_EID_FOLLOW_UP_SCHEMA, FOLLOW_UP_STATUS, MAMA_CHILD_STATUS,
        MAMA_TAREHE_MTOTO_ALIFARIKI, MTOTO_NOT_FOUND_REASON, MTOTO_NEW_FACILITY,
        MTOTO_NOT_FOUND_REASON_OTHER, MTOTO_MOVED_AREA_FACILITY, CHILD_DOB
    };

    private MotherMentorChildEidStatusUtils() {}

    public static boolean isSchema3(Map<String, String> values) {
        return "3".equals(value(values, CHILD_EID_FOLLOW_UP_SCHEMA));
    }

    public static boolean isFoundAlive(Map<String, String> values) {
        return isSchema3(values) && "found".equals(value(values, FOLLOW_UP_STATUS))
                && "alive".equals(value(values, MAMA_CHILD_STATUS));
    }

    public static boolean isStatusComplete(Map<String, String> values, LocalDate dob,
            LocalDate serviceDate) {
        if (!isSchema3(values) || serviceDate == null || serviceDate.isAfter(LocalDate.now())) {
            return false;
        }
        if ("found".equals(value(values, FOLLOW_UP_STATUS))) {
            String child = value(values, MAMA_CHILD_STATUS);
            if ("alive".equals(child)) return true;
            LocalDate died = parseDate(value(values, MAMA_TAREHE_MTOTO_ALIFARIKI));
            return "deceased".equals(child) && dob != null && died != null
                    && !died.isBefore(dob) && !died.isAfter(serviceDate);
        }
        if (!"not_found".equals(value(values, FOLLOW_UP_STATUS))) return false;
        String reason = value(values, MTOTO_NOT_FOUND_REASON);
        if ("moved_area".equals(reason)) return true;
        if ("moved_facility".equals(reason)) return !value(values, MTOTO_NEW_FACILITY).isEmpty();
        return "other".equals(reason) && !value(values, MTOTO_NOT_FOUND_REASON_OTHER).isEmpty();
    }

    public static Map<String, String> values(JSONObject form) {
        Map<String, String> values = new LinkedHashMap<>();
        if (form != null) {
            for (String field : FIELDS) values.put(field, JsonFormUtils.getValue(form, field));
        }
        return values;
    }

    /** A status change must never retain clinical answers from its inactive branch. */
    public static JSONObject clearInactiveAnswers(JSONObject form) throws JSONException {
        Map<String, String> values = values(form);
        if (!isSchema3(values)) return form;
        String status = value(values, FOLLOW_UP_STATUS);
        if (!"found".equals(status)) {
            clear(form, MAMA_CHILD_STATUS);
            clear(form, MAMA_TAREHE_MTOTO_ALIFARIKI);
        } else if (!"deceased".equals(value(values, MAMA_CHILD_STATUS))) {
            clear(form, MAMA_TAREHE_MTOTO_ALIFARIKI);
        }
        if (!"not_found".equals(status)) {
            clear(form, MTOTO_NOT_FOUND_REASON);
            clear(form, MTOTO_NEW_FACILITY);
            clear(form, MTOTO_MOVED_AREA_FACILITY);
            clear(form, MTOTO_NOT_FOUND_REASON_OTHER);
        } else {
            String reason = value(values, MTOTO_NOT_FOUND_REASON);
            if ("moved_area".equals(reason)) {
                JSONObject facility = FormUtils.getFieldFromForm(form, MTOTO_NEW_FACILITY);
                if (facility != null) facility.put(JsonFormConstants.VALUE,
                        value(values, MTOTO_MOVED_AREA_FACILITY));
            } else {
                clear(form, MTOTO_MOVED_AREA_FACILITY);
            }
            if (!"moved_area".equals(reason) && !"moved_facility".equals(reason)) {
                clear(form, MTOTO_NEW_FACILITY);
            }
            if (!"other".equals(reason)) clear(form, MTOTO_NOT_FOUND_REASON_OTHER);
        }
        return form;
    }

    public static LocalDate parseDate(String value) {
        if (value == null) return null;
        for (String pattern : new String[] {"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy"}) {
            String expression = "yyyy-MM-dd".equals(pattern) ? "\\d{4}-\\d{2}-\\d{2}"
                    : "dd-MM-yyyy".equals(pattern) ? "\\d{2}-\\d{2}-\\d{4}"
                    : "\\d{2}/\\d{2}/\\d{4}";
            if (!value.matches(expression)) continue;
            try {
                return LocalDate.parse(value, DateTimeFormat.forPattern(pattern));
            } catch (IllegalArgumentException ignored) {
                // Try the remaining supported form date formats.
            }
        }
        return null;
    }

    private static void clear(JSONObject form, String key) throws JSONException {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        if (field != null) field.put(JsonFormConstants.VALUE, "");
    }

    private static String value(Map<String, String> values, String key) {
        String value = values == null ? null : values.get(key);
        return value == null ? "" : value.trim();
    }
}
