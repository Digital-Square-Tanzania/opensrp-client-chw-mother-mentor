package org.smartregister.chw.mothermentor.util;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Interprets role counts without converting older sex-disaggregated counselling records. */
public final class MotherMentorHouseholdCounselling {
    private static final Set<String> ROLES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("mother", "partner", "child", "relative", "neighbour", "other")));
    public static final Map<String, String> COUNT_FIELDS;

    static {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("child", DBConstants.KEY.COUNSELLED_CHILD_COUNT);
        fields.put("relative", DBConstants.KEY.COUNSELLED_RELATIVE_COUNT);
        fields.put("neighbour", DBConstants.KEY.COUNSELLED_NEIGHBOUR_COUNT);
        fields.put("other", DBConstants.KEY.COUNSELLED_OTHER_COUNT);
        COUNT_FIELDS = Collections.unmodifiableMap(fields);
    }

    private MotherMentorHouseholdCounselling() {}

    /** Combined native checkboxes store one observation value per selected role. */
    public static Map<String, String> observationValues(JSONArray observations) {
        Map<String, String> values = MotherMentorVisitCompletion.observationValues(observations);
        if (observations == null) return values;
        for (int index = 0; index < observations.length(); index++) {
            JSONObject observation = observations.optJSONObject(index);
            if (observation != null
                    && DBConstants.KEY.PARTICIPANTS.equals(observation.optString("fieldCode"))) {
                JSONArray roles = observation.optJSONArray("values");
                if (roles != null && roles.length() > 1) {
                    values.put(DBConstants.KEY.PARTICIPANTS, roles.toString());
                }
            }
        }
        return values;
    }

    public static boolean isRoleBased(Map<String, String> values) {
        return values != null && "2".equals(values.get(DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA));
    }

    public static boolean isComplete(Map<String, String> values) {
        if (values == null) return false;
        String participants = values.get(DBConstants.KEY.PARTICIPANTS);
        if (!isRoleBased(values)) return StringUtils.isNotBlank(participants);
        Set<String> selected = selectedRoles(participants);
        if (selected.isEmpty() || !ROLES.containsAll(selected)) return false;
        for (Map.Entry<String, String> count : COUNT_FIELDS.entrySet()) {
            if (selected.contains(count.getKey()) && count(values.get(count.getValue())) < 1) {
                return false;
            }
        }
        return !selected.contains("other") || StringUtils.isNotBlank(values.get(DBConstants.KEY.OTHER));
    }

    /** A role-format total only; older aggregate totals remain in their original stored fields. */
    public static int total(Map<String, String> values) {
        if (!isRoleBased(values)) return 0;
        Set<String> selected = selectedRoles(values.get(DBConstants.KEY.PARTICIPANTS));
        int total = (selected.contains("mother") ? 1 : 0) + (selected.contains("partner") ? 1 : 0);
        for (Map.Entry<String, String> field : COUNT_FIELDS.entrySet()) {
            if (selected.contains(field.getKey())) total += count(values.get(field.getValue()));
        }
        return total;
    }

    public static Set<String> selectedRoles(String value) {
        Set<String> selected = new LinkedHashSet<>();
        if (StringUtils.isBlank(value)) return selected;
        for (String role : value.split("[\\[\\]\\\",\\s]+")) {
            if (!role.isEmpty()) selected.add(role);
        }
        return selected;
    }

    private static int count(String value) {
        return value != null && value.matches("[0-9]{1,5}") ? Integer.parseInt(value) : 0;
    }
}
