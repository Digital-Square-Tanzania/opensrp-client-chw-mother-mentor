package org.smartregister.chw.mothermentor.dao;

import org.smartregister.chw.mothermentor.repository.VisitDetailsRepository;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.cursoradapter.SmartRegisterQueryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main Mother Mentor clients only; secondary enrollment lists keep their existing model. */
public final class MotherMentorClientRegisterDao {
    private static final String CURRENT_STATUS = DBConstants.KEY.MOTHERMENTOR_CURRENT_STATUS;
    private static final String COMPLETED_VISITS = DBConstants.KEY.MOTHERMENTOR_COMPLETED_VISITS;

    private static final String ENROLLMENT = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String SERVICE = Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES;
    private static final String PREGNANT = "pregnant";
    private static final String BREASTFEEDING = "breast_feeding";
    private static final String NOT_PREGNANT = "not_pregnant";
    private static final String FAMILY_TABLE = "ec_family";
    private static final String FAMILY_MEMBER_TABLE = "ec_family_member";
    private static final String TODAY = "date('now', 'localtime')";

    private MotherMentorClientRegisterDao() {}

    public static String selectClients(
            String mainCondition,
            String search,
            boolean filterOn,
            boolean pregnant,
            boolean breastfeeding,
            boolean underTwo) {
        List<String> conditions = new ArrayList<>();
        String term = search == null ? "" : search.trim();
        if (!term.isEmpty()) conditions.add(searchCondition(term));
        if (filterOn && (pregnant || breastfeeding || underTwo)) {
            List<String> categories = new ArrayList<>();
            if (pregnant) categories.add(CURRENT_STATUS + " = '" + PREGNANT + "'");
            if (breastfeeding) categories.add(CURRENT_STATUS + " = '" + BREASTFEEDING + "'");
            if (underTwo) categories.add(underTwoCondition());
            conditions.add("(" + join(" OR ", categories) + ")");
        }
        return "SELECT * FROM ("
                + clientProjection(mainCondition)
                + ") mothermentor_clients"
                + (conditions.isEmpty() ? "" : " WHERE " + join(" AND ", conditions));
    }

    public static String selectClientsPage(
            String mainCondition,
            String search,
            boolean filterOn,
            boolean pregnant,
            boolean breastfeeding,
            boolean underTwo,
            int limit,
            int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException(
                    "A page requires a positive limit and non-negative offset");
        }
        return selectClients(mainCondition, search, filterOn, pregnant, breastfeeding, underTwo)
                + " ORDER BY CAST("
                + DBConstants.KEY.LAST_INTERACTED_WITH
                + " AS INTEGER) DESC, "
                + DBConstants.KEY.BASE_ENTITY_ID
                + " COLLATE NOCASE ASC LIMIT "
                + limit
                + " OFFSET "
                + offset;
    }

    public static String countClients(
            String mainCondition,
            String search,
            boolean filterOn,
            boolean pregnant,
            boolean breastfeeding,
            boolean underTwo) {
        return "SELECT COUNT(*) FROM ("
                + selectClients(mainCondition, search, filterOn, pregnant, breastfeeding, underTwo)
                + ") mothermentor_client_count";
    }

    private static String clientProjection(String mainCondition) {
        SmartRegisterQueryBuilder query = new SmartRegisterQueryBuilder();
        List<String> columns = new ArrayList<>();
        columns.add(ENROLLMENT + "." + DBConstants.KEY.BASE_ENTITY_ID);
        columns.add(ENROLLMENT + "." + DBConstants.KEY.LAST_INTERACTED_WITH);
        columns.add(
                FAMILY_MEMBER_TABLE
                        + "."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " AS "
                        + DBConstants.KEY.RELATIONALID);
        for (String field :
                new String[] {
                    DBConstants.KEY.FIRST_NAME,
                    DBConstants.KEY.MIDDLE_NAME,
                    DBConstants.KEY.LAST_NAME,
                    DBConstants.KEY.DOB,
                    DBConstants.KEY.GENDER,
                    DBConstants.KEY.UNIQUE_ID,
                    DBConstants.KEY.RELATIONAL_ID,
                    DBConstants.KEY.PHONE_NUMBER,
                    DBConstants.KEY.OTHER_PHONE_NUMBER
                }) {
            columns.add(FAMILY_MEMBER_TABLE + "." + field);
        }
        columns.add(FAMILY_TABLE + "." + DBConstants.KEY.VILLAGE_TOWN);
        columns.add(
                FAMILY_TABLE
                        + "."
                        + DBConstants.KEY.FIRST_NAME
                        + " AS "
                        + DBConstants.KEY.FAMILY_NAME);
        columns.add(
                "T2."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " AS "
                        + DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER);
        columns.add(memberName("T1") + " AS " + DBConstants.KEY.PRIMARY_CARE_GIVER);
        columns.add(memberName("T2") + " AS " + DBConstants.KEY.FAMILY_HEAD);
        columns.add(ENROLLMENT + "." + DBConstants.KEY.CTC_NUMBER);
        columns.add(ENROLLMENT + "." + DBConstants.KEY.CHILD_DOB);
        columns.add(currentStatus() + " AS " + CURRENT_STATUS);
        columns.add(completedVisits() + " AS " + COMPLETED_VISITS);
        query.selectInitiateMainTable(ENROLLMENT, columns.toArray(new String[0]));
        query.customJoin(
                " INNER JOIN "
                        + FAMILY_MEMBER_TABLE
                        + " ON "
                        + ENROLLMENT
                        + "."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = "
                        + FAMILY_MEMBER_TABLE
                        + "."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " COLLATE NOCASE");
        // An independent registered client need not have a household record.
        query.customJoin(
                " LEFT JOIN "
                        + FAMILY_TABLE
                        + " ON "
                        + FAMILY_MEMBER_TABLE
                        + "."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = "
                        + FAMILY_TABLE
                        + "."
                        + DBConstants.KEY.BASE_ENTITY_ID);
        addFamilyMemberJoin(query, "T1", DBConstants.KEY.PRIMARY_CARE_GIVER);
        addFamilyMemberJoin(query, "T2", DBConstants.KEY.FAMILY_HEAD);
        return query.mainCondition(mainCondition == null ? "" : mainCondition);
    }

    private static String memberName(String alias) {
        return alias
                + "."
                + DBConstants.KEY.FIRST_NAME
                + " || ' ' || "
                + alias
                + "."
                + DBConstants.KEY.MIDDLE_NAME
                + " || ' ' || "
                + alias
                + "."
                + DBConstants.KEY.LAST_NAME;
    }

    private static void addFamilyMemberJoin(
            SmartRegisterQueryBuilder query, String alias, String familyField) {
        query.customJoin(
                " LEFT JOIN "
                        + FAMILY_MEMBER_TABLE
                        + " AS "
                        + alias
                        + " ON "
                        + FAMILY_TABLE
                        + "."
                        + familyField
                        + " = "
                        + alias
                        + "."
                        + DBConstants.KEY.BASE_ENTITY_ID);
    }

    private static String currentStatus() {
        String answer = "lower(trim(d." + DBConstants.KEY.DETAILS + "))";
        return "coalesce((SELECT "
                + answer
                + " FROM "
                + VisitRepository.VISIT_TABLE
                + " v INNER JOIN "
                + VisitDetailsRepository.VISIT_DETAILS_TABLE
                + " d ON d."
                + DBConstants.KEY.VISIT_ID
                + " = v."
                + DBConstants.KEY.VISIT_ID
                + " WHERE "
                + serviceCondition()
                + " AND d."
                + DBConstants.KEY.VISIT_KEY
                + " = '"
                + DBConstants.KEY.VISIT_PREGNANCY_STATUS
                + "' AND "
                + validStatus(answer)
                + " ORDER BY coalesce("
                + serviceDate()
                + ", date(CAST(v."
                + DBConstants.KEY.VISIT_DATE
                + " AS INTEGER) / 1000, 'unixepoch')) DESC, CAST(v."
                + DBConstants.KEY.UPDATED_AT
                + " AS INTEGER) DESC, CAST(v."
                + DBConstants.KEY.CREATED_AT
                + " AS INTEGER) DESC, v."
                + DBConstants.KEY.VISIT_ID
                + " DESC, CAST(d."
                + DBConstants.KEY.UPDATED_AT
                + " AS INTEGER) DESC, d."
                + DBConstants.KEY.VISIT_DETAILS_ID
                + " DESC LIMIT 1), CASE WHEN "
                + validStatus(
                        "lower(trim(" + ENROLLMENT + "." + DBConstants.KEY.PREGNANCY_STATUS + "))")
                + " THEN lower(trim("
                + ENROLLMENT
                + "."
                + DBConstants.KEY.PREGNANCY_STATUS
                + ")) END)";
    }

    private static String serviceDate() {
        return "(SELECT "
                + normalizedDate("sd." + DBConstants.KEY.DETAILS)
                + " FROM "
                + VisitDetailsRepository.VISIT_DETAILS_TABLE
                + " sd WHERE sd."
                + DBConstants.KEY.VISIT_ID
                + " = v."
                + DBConstants.KEY.VISIT_ID
                + " AND sd."
                + DBConstants.KEY.VISIT_KEY
                + " = '"
                + DBConstants.KEY.MOTHERMENTOR_VISIT_DATE
                + "' ORDER BY CAST(sd."
                + DBConstants.KEY.UPDATED_AT
                + " AS INTEGER) DESC, sd."
                + DBConstants.KEY.VISIT_DETAILS_ID
                + " DESC LIMIT 1)";
    }

    private static String completedVisits() {
        return "(SELECT COUNT(DISTINCT v."
                + DBConstants.KEY.VISIT_ID
                + ") FROM "
                + VisitRepository.VISIT_TABLE
                + " v WHERE "
                + serviceCondition()
                + ")";
    }

    private static String serviceCondition() {
        return "v."
                + DBConstants.KEY.BASE_ENTITY_ID
                + " = "
                + ENROLLMENT
                + "."
                + DBConstants.KEY.BASE_ENTITY_ID
                + " COLLATE NOCASE AND v."
                + DBConstants.KEY.VISIT_TYPE
                + " = '"
                + SERVICE
                + "' AND v."
                + DBConstants.KEY.PROCESSED
                + " = 1";
    }

    private static String validStatus(String expression) {
        return expression
                + " IN ('"
                + PREGNANT
                + "', '"
                + BREASTFEEDING
                + "', '"
                + NOT_PREGNANT
                + "')";
    }

    private static String searchCondition(String search) {
        String first = "trim(coalesce(" + DBConstants.KEY.FIRST_NAME + ", ''))";
        String middle = "trim(coalesce(" + DBConstants.KEY.MIDDLE_NAME + ", ''))";
        String last = "trim(coalesce(" + DBConstants.KEY.LAST_NAME + ", ''))";
        String name =
                first
                        + " || CASE WHEN "
                        + middle
                        + " <> '' THEN ' ' || "
                        + middle
                        + " ELSE '' END || ' ' || "
                        + last;
        String pattern = "'%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%' ESCAPE '!'";
        return "(lower(trim("
                + name
                + ")) LIKE "
                + pattern
                + " OR lower(coalesce("
                + DBConstants.KEY.CTC_NUMBER
                + ", '')) LIKE "
                + pattern
                + ")";
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("'", "''");
    }

    private static String underTwoCondition() {
        String dob = normalizedDate(DBConstants.KEY.CHILD_DOB);
        String months =
                "(CAST(strftime('%Y', "
                        + TODAY
                        + ") AS INTEGER) - CAST(strftime('%Y', "
                        + dob
                        + ") AS INTEGER)) * 12 + CAST(strftime('%m', "
                        + TODAY
                        + ") AS INTEGER) - CAST(strftime('%m', "
                        + dob
                        + ") AS INTEGER) - CASE WHEN strftime('%d', "
                        + TODAY
                        + ") < strftime('%d', "
                        + dob
                        + ") THEN 1 ELSE 0 END";
        return "("
                + CURRENT_STATUS
                + " = '"
                + BREASTFEEDING
                + "' OR ("
                + CURRENT_STATUS
                + " = '"
                + NOT_PREGNANT
                + "' AND "
                + dob
                + " <= "
                + TODAY
                + " AND ("
                + months
                + ") < 24))";
    }

    /** Enrollment stores dd-MM-yyyy, while processed visit details also use ISO dates. */
    private static String normalizedDate(String expression) {
        String raw = "trim(" + expression + ")";
        String candidate =
                "CASE WHEN "
                        + raw
                        + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*' THEN substr("
                        + raw
                        + ", 1, 10) WHEN "
                        + raw
                        + " GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]' THEN substr("
                        + raw
                        + ", 7, 4) || '-' || substr("
                        + raw
                        + ", 4, 2) || '-' || substr("
                        + raw
                        + ", 1, 2) END";
        return "CASE WHEN date("
                + candidate
                + ", '+0 days') = ("
                + candidate
                + ") THEN ("
                + candidate
                + ") END";
    }

    private static String join(String separator, List<String> values) {
        return android.text.TextUtils.join(separator, values);
    }
}
