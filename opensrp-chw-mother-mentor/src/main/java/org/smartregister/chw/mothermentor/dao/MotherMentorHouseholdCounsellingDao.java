package org.smartregister.chw.mothermentor.dao;

import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdCounselling;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Report projection for original aggregate visits and the new participant-role format. */
public final class MotherMentorHouseholdCounsellingDao {
    public static final List<String> REPORT_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "total", "missing", "male_total", "female_total", "role_visits", "legacy_visits"));

    private MotherMentorHouseholdCounsellingDao() {}

    /** The host supplies its existing finalized, consented, in-month household-visit selection. */
    public static String reportQuery(String householdVisits) {
        String[] fields = {
            DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA, DBConstants.KEY.PARTICIPANTS,
            DBConstants.KEY.COUNSELLED_CHILD_COUNT, DBConstants.KEY.COUNSELLED_RELATIVE_COUNT,
            DBConstants.KEY.COUNSELLED_NEIGHBOUR_COUNT, DBConstants.KEY.COUNSELLED_OTHER_COUNT,
            DBConstants.KEY.OTHER, DBConstants.KEY.COUNSELLED_MALE, DBConstants.KEY.COUNSELLED_FEMALE
        };
        StringBuilder source = new StringBuilder("SELECT ");
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) source.append(", ");
            source.append(detail(fields[index])).append(" AS ").append(fields[index]);
        }
        source.append(" FROM (").append(householdVisits).append(") v");
        String roleFormat = "coalesce(" + DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA + " = '2', 0)";
        String legacyFormat = "trim(ifnull(" + DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA + ", '')) = ''";
        String legacyKnown = legacyFormat + " AND " + validCount(DBConstants.KEY.COUNSELLED_MALE)
                + " AND " + validCount(DBConstants.KEY.COUNSELLED_FEMALE);
        String roleKnown = roleFormat + " AND " + validRoles();
        String known = "(" + roleKnown + ") OR (" + legacyKnown + ")";
        String roleTotal = "(CASE WHEN " + hasRole("mother") + " THEN 1 ELSE 0 END)"
                + " + (CASE WHEN " + hasRole("partner") + " THEN 1 ELSE 0 END)";
        for (Map.Entry<String, String> field : MotherMentorHouseholdCounselling.COUNT_FIELDS.entrySet()) {
            roleTotal += " + (CASE WHEN " + hasRole(field.getKey()) + " THEN CAST("
                    + field.getValue() + " AS INTEGER) ELSE 0 END)";
        }
        return "SELECT coalesce(SUM(CASE WHEN " + roleKnown + " THEN " + roleTotal
                + " WHEN " + legacyKnown + " THEN CAST(" + DBConstants.KEY.COUNSELLED_MALE
                + " AS INTEGER) + CAST(" + DBConstants.KEY.COUNSELLED_FEMALE
                + " AS INTEGER) ELSE 0 END), 0) AS total, COUNT(CASE WHEN NOT ("
                + known + ") THEN 1 END) AS missing, "
                + sumLegacy(legacyKnown, DBConstants.KEY.COUNSELLED_MALE) + " AS male_total, "
                + sumLegacy(legacyKnown, DBConstants.KEY.COUNSELLED_FEMALE) + " AS female_total, "
                + "COUNT(CASE WHEN " + roleFormat + " THEN 1 END) AS role_visits, "
                + "COUNT(CASE WHEN " + legacyFormat + " THEN 1 END) AS legacy_visits FROM ("
                + source + ")";
    }

    private static String validRoles() {
        String participants = "replace(replace(replace(ifnull(" + DBConstants.KEY.PARTICIPANTS
                + ", ''), '[', ''), ']', ''), '\"', '')";
        String remainder = participants;
        // Separate token boundaries also allow adjacent duplicates without partial-name matches.
        String tokens = "',' || replace(replace(" + participants + ", ' ', ''), ',', ',,') || ','";
        for (String role : new String[] {"mother", "partner", "child", "relative", "neighbour", "other"}) {
            remainder = "replace(" + remainder + ", '" + role + "', '')";
            tokens = "replace(" + tokens + ", '," + role + ",', '')";
        }
        String condition = "trim(replace(" + remainder + ", ',', '')) = '' AND trim("
                + participants + ") <> '' AND replace(" + tokens + ", ',', '') = ''";
        // A list containing only separators is not a selection.
        condition += " AND (" + hasRole("mother") + " OR " + hasRole("partner")
                + " OR " + hasRole("child") + " OR " + hasRole("relative")
                + " OR " + hasRole("neighbour") + " OR " + hasRole("other") + ")";
        for (Map.Entry<String, String> count : MotherMentorHouseholdCounselling.COUNT_FIELDS.entrySet()) {
            condition += " AND (NOT (" + hasRole(count.getKey()) + ") OR ("
                    + validCount(count.getValue()) + " AND length(trim(" + count.getValue()
                    + ")) <= 5 AND CAST(" + count.getValue() + " AS INTEGER) BETWEEN 1 AND 99999))";
        }
        return condition + " AND (NOT (" + hasRole("other") + ") OR trim(ifnull("
                + DBConstants.KEY.OTHER + ", '')) <> '')";
    }

    private static String hasRole(String role) {
        return "instr(',' || replace(replace(replace(replace(ifnull("
                + DBConstants.KEY.PARTICIPANTS
                + ", ''), '[', ''), ']', ''), '\"', ''), ' ', '') || ',', ',"
                + role + ",') > 0";
    }

    private static String validCount(String column) {
        return "coalesce((trim(" + column + ") <> '' AND trim(" + column
                + ") NOT GLOB '*[^0-9]*'), 0)";
    }

    private static String sumLegacy(String known, String column) {
        return "coalesce(SUM(CASE WHEN " + known + " THEN CAST(" + column
                + " AS INTEGER) ELSE 0 END), 0)";
    }

    private static String detail(String key) {
        return "(SELECT MAX(d." + DBConstants.KEY.DETAILS + ") FROM visit_details d WHERE d."
                + DBConstants.KEY.VISIT_ID + " = v." + DBConstants.KEY.VISIT_ID + " AND d."
                + DBConstants.KEY.VISIT_KEY + " = '" + key + "')";
    }
}
