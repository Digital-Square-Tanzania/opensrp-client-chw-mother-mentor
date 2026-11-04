package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pregnancy questions follow original enrollment context, never its mutable current status. */
public final class MotherMentorMotherPregnancyUtils {
    public static final List<String> ANSWER_FIELDS = Collections.unmodifiableList(Arrays.asList(
            VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME, MAMA_PREGNANCY_OUTCOME_OTHER,
            MAMA_TAREHE_KUJIFUNGUA, MAMA_PREGNANCY_LOSS_DATE, MAMA_DELIVERY_DATE_INPUT,
            MAMA_PRETERM_DELIVERY_DATE_INPUT, MAMA_STILLBIRTH_DATE_INPUT,
            MAMA_ENROLLMENT_DELIVERY_DATE));

    private MotherMentorMotherPregnancyUtils() {}

    public static boolean isComplete(Map<String, String> values, LocalDate serviceDate) {
        if (!values.containsKey(MAMA_ENROLLMENT_PREGNANCY_STATUS) || !isFound(values)) return true;
        String original = normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        if ("breast_feeding".equals(original) || "not_pregnant".equals(original)) {
            LocalDate enrollmentDob = parseDate(values.get(MAMA_ENROLLMENT_CHILD_DOB));
            return valid(enrollmentDob, serviceDate) && enrollmentDob.equals(deliveryDate(values));
        }
        if (!"pregnant".equals(original)) return false;
        String status = values.get(VISIT_PREGNANCY_STATUS);
        if ("pregnant".equals(status)) return true;
        if (!"not_pregnant".equals(status)) return false;
        switch (StringUtils.defaultString(values.get(MAMA_PREGNANCY_OUTCOME))) {
            case "delivered":
            case "preterm":
            case "stillbirth":
                return valid(deliveryDate(values), serviceDate);
            case "pregnancy_loss":
                return valid(parseDate(values.get(MAMA_PREGNANCY_LOSS_DATE)), serviceDate);
            case "other":
                return StringUtils.isNotBlank(values.get(MAMA_PREGNANCY_OUTCOME_OTHER));
            default:
                return false;
        }
    }

    public static boolean isFound(Map<String, String> values) {
        return "repeat".equals(values.get(ATTENDANCE_TYPE))
                && ("found".equals(values.get(FOLLOW_UP_STATUS))
                        || "continuing_services".equals(values.get(FOLLOW_UP_STATUS)));
    }

    public static boolean isChildPath(Map<String, String> values, LocalDate serviceDate) {
        if (!isFound(values) || !isComplete(values, serviceDate)) return false;
        String original = normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        return "breast_feeding".equals(original) || "not_pregnant".equals(original)
                || isLiveBirth(values);
    }

    public static boolean isLiveBirth(Map<String, String> values) {
        return "pregnant".equals(normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS)))
                && "not_pregnant".equals(values.get(VISIT_PREGNANCY_STATUS))
                && ("delivered".equals(values.get(MAMA_PREGNANCY_OUTCOME))
                        || "preterm".equals(values.get(MAMA_PREGNANCY_OUTCOME)));
    }

    public static String terminalOutcome(Map<String, String> values, LocalDate serviceDate) {
        if (!isFound(values) || !isComplete(values, serviceDate)
                || !"pregnant".equals(normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS)))
                || !"not_pregnant".equals(values.get(VISIT_PREGNANCY_STATUS))) return "";
        String outcome = values.get(MAMA_PREGNANCY_OUTCOME);
        return "pregnancy_loss".equals(outcome) || "stillbirth".equals(outcome)
                || "other".equals(outcome) ? outcome : "";
    }

    public static boolean continues(Map<String, String> values, LocalDate serviceDate) {
        return isComplete(values, serviceDate) && terminalOutcome(values, serviceDate).isEmpty();
    }

    public static LocalDate deliveryDate(Map<String, String> values) {
        String alias = dateAlias(values);
        return parseDate(values.containsKey(alias) ? values.get(alias)
                : values.get(MAMA_TAREHE_KUJIFUNGUA));
    }

    public static String currentStatus(Map<String, String> values) {
        if (!isFound(values)) return "";
        String original = normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        if (!"pregnant".equals(original)) return original;
        if ("pregnant".equals(values.get(VISIT_PREGNANCY_STATUS))) return "pregnant";
        return isLiveBirth(values) ? "breast_feeding" : "not_pregnant";
    }

    public static JSONObject prepare(JSONObject form, String originalStatus, String enrollmentDob,
            Map<String, String> previous, boolean savedVisit) throws JSONException {
        restoreContext(form, originalStatus, enrollmentDob);
        String original = normalizeStatus(originalStatus);
        if ("breast_feeding".equals(original) || "not_pregnant".equals(original)) return form;
        if (!savedVisit && previous != null) {
            for (String key : Arrays.asList(MAMA_PREGNANCY_OUTCOME, MAMA_PREGNANCY_OUTCOME_OTHER,
                    MAMA_TAREHE_KUJIFUNGUA, MAMA_PREGNANCY_LOSS_DATE)) {
                if (StringUtils.isBlank(value(form, key))) put(form, key, previous.get(key));
            }
            String status = value(form, VISIT_PREGNANCY_STATUS);
            if (StringUtils.isBlank(status) || "breast_feeding".equals(status)) {
                put(form, VISIT_PREGNANCY_STATUS, previous.get(VISIT_PREGNANCY_STATUS));
            }
        }
        if ("breast_feeding".equals(value(form, VISIT_PREGNANCY_STATUS))) {
            put(form, VISIT_PREGNANCY_STATUS, "not_pregnant");
        }
        Map<String, String> values = values(form);
        String alias = dateAlias(values);
        if (StringUtils.isNotBlank(alias) && StringUtils.isBlank(value(form, alias))) {
            put(form, alias, displayDate(parseDate(values.get(MAMA_TAREHE_KUJIFUNGUA))));
        }
        return form;
    }

    public static JSONObject restoreContext(JSONObject form, String originalStatus,
            String enrollmentDob) throws JSONException {
        String original = normalizeStatus(originalStatus);
        put(form, MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
        put(form, MAMA_ENROLLMENT_CHILD_DOB, displayDate(parseDate(enrollmentDob)));
        JSONObject pregnancy = field(form, VISIT_PREGNANCY_STATUS);
        if ("breast_feeding".equals(original) || "not_pregnant".equals(original)) {
            pregnancy.put("type", "hidden");
            pregnancy.remove("v_required");
            pregnancy.remove("relevance");
            put(form, VISIT_PREGNANCY_STATUS, original);
            put(form, MAMA_ENROLLMENT_DELIVERY_DATE, displayDate(parseDate(enrollmentDob)));
            put(form, MAMA_TAREHE_KUJIFUNGUA, displayDate(parseDate(enrollmentDob)));
            for (String key : Arrays.asList(MAMA_PREGNANCY_OUTCOME, MAMA_PREGNANCY_OUTCOME_OTHER,
                    MAMA_PREGNANCY_LOSS_DATE, MAMA_DELIVERY_DATE_INPUT,
                    MAMA_PRETERM_DELIVERY_DATE_INPUT, MAMA_STILLBIRTH_DATE_INPUT)) put(form, key, "");
            return form;
        }
        return form;
    }

    /** UI aliases are not observations; publish just the canonical branch-specific answers. */
    public static JSONObject normalize(JSONObject form) throws JSONException {
        Map<String, String> values = values(form);
        if (!values.containsKey(MAMA_ENROLLMENT_PREGNANCY_STATUS)) return form;
        if (!isFound(values)) {
            for (String key : ANSWER_FIELDS) put(form, key, "");
            return form;
        }
        String original = normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        if (!"pregnant".equals(original)) {
            put(form, MAMA_TAREHE_KUJIFUNGUA, value(form, MAMA_ENROLLMENT_CHILD_DOB));
            put(form, MAMA_ENROLLMENT_DELIVERY_DATE, value(form, MAMA_ENROLLMENT_CHILD_DOB));
            put(form, VISIT_PREGNANCY_STATUS, original);
            for (String key : Arrays.asList(MAMA_PREGNANCY_OUTCOME, MAMA_PREGNANCY_OUTCOME_OTHER,
                    MAMA_PREGNANCY_LOSS_DATE, MAMA_DELIVERY_DATE_INPUT,
                    MAMA_PRETERM_DELIVERY_DATE_INPUT, MAMA_STILLBIRTH_DATE_INPUT)) put(form, key, "");
            return form;
        }
        String outcome = "not_pregnant".equals(values.get(VISIT_PREGNANCY_STATUS))
                ? values.get(MAMA_PREGNANCY_OUTCOME) : "";
        if (!StringUtils.equals(outcome, values.get(MAMA_PREGNANCY_OUTCOME))) {
            put(form, MAMA_PREGNANCY_OUTCOME, "");
        }
        String alias = dateAlias(values);
        boolean delivery = "delivered".equals(outcome) || "preterm".equals(outcome)
                || "stillbirth".equals(outcome);
        put(form, MAMA_TAREHE_KUJIFUNGUA, delivery
                ? displayDate(parseDate(values.containsKey(alias) ? values.get(alias)
                        : values.get(MAMA_TAREHE_KUJIFUNGUA))) : "");
        if (!"pregnancy_loss".equals(outcome)) put(form, MAMA_PREGNANCY_LOSS_DATE, "");
        if (!"other".equals(outcome)) put(form, MAMA_PREGNANCY_OUTCOME_OTHER, "");
        for (String key : Arrays.asList(MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                MAMA_STILLBIRTH_DATE_INPUT)) {
            if (!delivery || !key.equals(alias)) put(form, key, "");
        }
        return form;
    }

    public static Map<String, String> values(JSONObject form) throws JSONException {
        Map<String, String> result = new HashMap<>();
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            result.put(field.optString("key"), field.optString("value"));
        }
        return result;
    }

    public static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;
        String raw = value.trim();
        try {
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}(T.+)?")) {
                return ISODateTimeFormat.dateOptionalTimeParser().parseLocalDate(raw);
            }
            if (raw.matches("\\d{2}-\\d{2}-\\d{4}")) {
                return DateTimeFormat.forPattern("dd-MM-yyyy").parseLocalDate(raw);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    public static String displayDate(LocalDate date) {
        return date == null ? "" : date.toString("dd-MM-yyyy");
    }

    public static String normalizeStatus(String status) {
        String value = StringUtils.trimToEmpty(status);
        if ("bf".equals(value)) return "breast_feeding";
        if ("none".equals(value)) return "not_pregnant";
        return value;
    }

    private static String dateAlias(Map<String, String> values) {
        String original = normalizeStatus(values.get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
        if ("breast_feeding".equals(original) || "not_pregnant".equals(original)) {
            return MAMA_ENROLLMENT_DELIVERY_DATE;
        }
        switch (StringUtils.defaultString(values.get(MAMA_PREGNANCY_OUTCOME))) {
            case "delivered": return MAMA_DELIVERY_DATE_INPUT;
            case "preterm": return MAMA_PRETERM_DELIVERY_DATE_INPUT;
            case "stillbirth": return MAMA_STILLBIRTH_DATE_INPUT;
            default: return "";
        }
    }

    private static boolean valid(LocalDate date, LocalDate serviceDate) {
        return date != null && serviceDate != null && !date.isAfter(serviceDate);
    }

    private static String value(JSONObject form, String key) throws JSONException {
        JSONObject field = field(form, key);
        return field == null ? "" : field.optString("value");
    }

    private static void put(JSONObject form, String key, String value) throws JSONException {
        JSONObject field = field(form, key);
        if (field != null) field.put("value", StringUtils.defaultString(value));
    }

    private static JSONObject field(JSONObject form, String key) throws JSONException {
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.optString("key"))) return field;
        }
        return null;
    }
}
