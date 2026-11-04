package org.smartregister.chw.mothermentor.dao;

import org.smartregister.chw.mothermentor.repository.MotherMentorHouseholdClosureRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;

/** Household register conditions and closure projection columns, shared by list and count queries. */
public final class MotherMentorHouseholdFollowUpDao {
    private MotherMentorHouseholdFollowUpDao() {}

    public static String enrollmentCondition(String householdTable, boolean closed) {
        validateTable(householdTable);
        String source = "household_enrollment";
        String condition = closed
                ? source + "." + DBConstants.KEY.IS_CLOSED + " = 1 AND " + source + "."
                        + DBConstants.KEY.KAYA_STATUS
                        + " IN ('moved', 'house_closed_3', 'other_3', 'lost_to_follow_up')"
                : source + "." + DBConstants.KEY.IS_CLOSED + " = 0 AND " + source + "."
                        + DBConstants.KEY.CONSENT + " = 'ndiyo' AND coalesce(nullif(" + source + "."
                        + DBConstants.KEY.KAYA_STATUS + ", ''), 'active') = 'active'";
        return (closed ? "" : householdTable + "." + DBConstants.KEY.IS_CLOSED + " = 0 AND ")
                + "EXISTS (SELECT 1 FROM " + Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT
                + " " + source + " WHERE " + source + "." + DBConstants.KEY.HOUSEHOLD_ID
                + " = " + householdTable + "." + DBConstants.KEY.BASE_ENTITY_ID + " AND " + condition + ")";
    }

    public static String summaryColumn(String householdTable, String column) {
        validateTable(householdTable);
        if (!MotherMentorHouseholdClosureRepository.CLOSURE_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("Unsupported household summary column");
        }
        return "(SELECT household_enrollment." + column + " FROM "
                + Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT
                + " household_enrollment WHERE household_enrollment." + DBConstants.KEY.HOUSEHOLD_ID
                + " = " + householdTable + "." + DBConstants.KEY.BASE_ENTITY_ID
                + " ORDER BY CAST(household_enrollment." + DBConstants.KEY.LAST_INTERACTED_WITH
                + " AS INTEGER) DESC LIMIT 1) AS " + column;
    }

    private static void validateTable(String table) {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid household table alias");
        }
    }
}
