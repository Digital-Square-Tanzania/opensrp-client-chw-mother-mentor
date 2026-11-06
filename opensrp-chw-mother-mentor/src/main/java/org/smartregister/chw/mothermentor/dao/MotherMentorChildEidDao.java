package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

/** Queries for the real child identity, current EID episode and register/card data. */
public final class MotherMentorChildEidDao {
    private static final String ENROLLMENTS = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
    private static final String MEMBERS = "ec_family_member";
    private static final String FAMILIES = "ec_family";

    private MotherMentorChildEidDao() {}

    public static Map<String, String> enrollment(SQLiteDatabase db, String childId) {
        return row(db, ENROLLMENTS, childId);
    }

    public static Map<String, String> person(SQLiteDatabase db, String childId) {
        return row(db, MEMBERS, childId);
    }

    public static Map<String, String> motherEnrollment(SQLiteDatabase db, String motherId) {
        return row(db, Constants.TABLES.MOTHERMENTOR_ENROLLMENT, motherId);
    }

    public static List<String> enrolledChildren(SQLiteDatabase db) {
        List<String> result = new ArrayList<>();
        if (!hasTable(db, ENROLLMENTS)) return result;
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT " + DBConstants.KEY.BASE_ENTITY_ID
                + " FROM " + ENROLLMENTS, null)) {
            while (cursor.moveToNext()) if (StringUtils.isNotBlank(cursor.getString(0))) result.add(cursor.getString(0));
        }
        return result;
    }

    private static Map<String, String> row(SQLiteDatabase db, String table, String id) {
        if (StringUtils.isBlank(id) || !hasTable(db, table)) return Collections.emptyMap();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + table + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {id})) {
            return cursor != null && cursor.moveToFirst() ? values(cursor) : Collections.emptyMap();
        }
    }

    /** Mother/DOB must agree with one explicit relationship and one registered person. */
    public static String resolveChild(SQLiteDatabase db, String motherId, LocalDate dob) {
        if (StringUtils.isBlank(motherId) || dob == null || !hasTable(db, MEMBERS)) return "";
        Set<String> linked = new HashSet<>();
        collectLinks(db, ENROLLMENTS, DBConstants.KEY.MOTHER_BASE_ENTITY_ID, motherId, linked);
        collectLinks(db, DBConstants.KEY.CHILD, DBConstants.KEY.MOTHER_ENTITY_ID, motherId, linked);
        Set<String> matches = new HashSet<>();
        for (String id : linked) {
            Map<String, String> person = person(db, id);
            if (!id.equals(motherId) && isActivePerson(person)
                    && dob.equals(MotherMentorHeiSchedule.parseBirthDate(person.get(DBConstants.KEY.DOB)))) matches.add(id);
        }
        return matches.size() == 1 ? matches.iterator().next() : "";
    }

    public static boolean isActivePerson(Map<String, String> person) {
        return !person.isEmpty() && !"1".equals(person.get(DBConstants.KEY.IS_CLOSED))
                && !recorded(person.get(DBConstants.KEY.DATE_DIED)) && !recorded(person.get(DBConstants.KEY.DOD));
    }

    private static boolean recorded(String value) {
        return StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value.trim());
    }

    private static void collectLinks(SQLiteDatabase db, String table, String column,
            String motherId, Set<String> result) {
        if (!columns(db, table).contains(column)) return;
        try (Cursor cursor = db.rawQuery("SELECT " + DBConstants.KEY.BASE_ENTITY_ID
                + " FROM " + table + " WHERE " + column + " = ?", new String[] {motherId})) {
            while (cursor.moveToNext()) if (StringUtils.isNotBlank(cursor.getString(0))) result.add(cursor.getString(0));
        }
    }

    /** Native PNC/child enrollment already stores the authoritative mother relationship. */
    public static String motherId(SQLiteDatabase db, String childId) {
        String mother = enrollment(db, childId).get(DBConstants.KEY.MOTHER_BASE_ENTITY_ID);
        if (StringUtils.isNotBlank(mother)) return mother;
        Map<String, String> child = row(db, DBConstants.KEY.CHILD, childId);
        return StringUtils.defaultString(child.get(DBConstants.KEY.MOTHER_ENTITY_ID));
    }

    public static Enrollment latestEnrollment(SQLiteDatabase db, String childId, LocalDate asOf) {
        return latestEnrollment(db, childId, asOf, "");
    }

    public static Enrollment latestEnrollment(SQLiteDatabase db, String childId, LocalDate asOf, String requiredId) {
        Enrollment latest = Enrollment.empty();
        for (Enrollment candidate : enrollmentHistory(db, childId, asOf)) {
            if (StringUtils.isNotBlank(requiredId) && !candidate.id.equals(requiredId)) continue;
            if (candidate.time > latest.time || candidate.time == latest.time
                    && candidate.id.compareTo(latest.id) > 0) latest = candidate;
        }
        return latest;
    }

    public static List<Enrollment> enrollmentHistory(SQLiteDatabase db, String childId, LocalDate asOf) {
        if (StringUtils.isBlank(childId) || !hasTable(db, "event")) return Collections.emptyList();
        Map<String, Enrollment> revisions = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT " + DBConstants.KEY.EVENT_JSON + " FROM event WHERE " + DBConstants.KEY.EVENT_BASE_ENTITY_ID
                + " = ? AND " + DBConstants.KEY.EVENT_TYPE + " = ?",
                new String[] {childId, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject json = new JSONObject(cursor.getString(0));
                    String id = json.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID);
                    long time = eventTime(json);
                    if (id.isEmpty() || time == 0) continue;
                    Enrollment candidate = new Enrollment(id, time, json.optLong(DBConstants.KEY.EVENT_VERSION, 0), json);
                    Enrollment previous = revisions.get(id);
                    if (previous == null || candidate.version > previous.version) revisions.put(id, candidate);
                } catch (Exception ignored) { }
            }
        }
        List<Enrollment> result = new ArrayList<>();
        for (Enrollment candidate : revisions.values()) {
            if (candidate.json.optBoolean(DBConstants.KEY.EVENT_VOIDED) || candidate.day().isAfter(asOf)) continue;
            result.add(candidate);
        }
        result.sort((first, second) -> first.time == second.time ? first.id.compareTo(second.id)
                : Long.compare(first.time, second.time));
        return result;
    }

    public static boolean hasEvent(SQLiteDatabase db, String childId, String formSubmissionId) {
        return storedEnrollmentEvent(db, childId, formSubmissionId) != null;
    }

    public static JSONObject storedEnrollmentEvent(SQLiteDatabase db, String childId, String formSubmissionId) {
        return storedEvent(db, childId, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, formSubmissionId);
    }

    public static JSONObject storedEvent(SQLiteDatabase db, String childId, String eventType, String formSubmissionId) {
        if (StringUtils.isBlank(formSubmissionId) || !hasTable(db, "event")) return null;
        JSONObject latest = null;
        try (Cursor cursor = db.rawQuery("SELECT " + DBConstants.KEY.EVENT_JSON + " FROM event WHERE " + DBConstants.KEY.EVENT_BASE_ENTITY_ID
                + " = ? AND " + DBConstants.KEY.EVENT_TYPE + " = ?",
                new String[] {childId, eventType})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject candidate = new JSONObject(cursor.getString(0));
                    if (!formSubmissionId.equals(candidate.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID))) continue;
                    if (latest == null || candidate.optLong(DBConstants.KEY.EVENT_VERSION, 0)
                            > latest.optLong(DBConstants.KEY.EVENT_VERSION, 0)) latest = candidate;
                } catch (Exception ignored) { }
            }
        }
        return latest;
    }

    /** Finalized, corrected-source-deduplicated evidence for the selected child episode. */
    public static List<Visit> finalizedVisits(SQLiteDatabase db, String childId,
            String excludedVisitId, Enrollment enrollment, LocalDate asOf) {
        if (!hasTable(db, VisitRepository.VISIT_TABLE)) return Collections.emptyList();
        Visit excluded = findVisit(db, excludedVisitId);
        String excludedSource = excluded == null ? "" : sourceIdentity(excluded);
        Map<String, Visit> latest = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.PROCESSED
                + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " = ?",
                new String[] {childId, MotherMentorFollowUpType.CHILD_EID.getEventType()})) {
            while (cursor.moveToNext()) {
                Visit visit = visit(cursor);
                if (StringUtils.equals(excludedVisitId, visit.getVisitId())) continue;
                JSONObject event = json(visit);
                String source = sourceIdentity(visit);
                if (!excludedSource.isEmpty() && excludedSource.equals(source)) continue;
                Visit previous = latest.get(source);
                if (previous == null || event.optLong(DBConstants.KEY.EVENT_VERSION, 0) > json(previous).optLong(DBConstants.KEY.EVENT_VERSION, 0)
                        || event.optLong(DBConstants.KEY.EVENT_VERSION, 0) == json(previous).optLong(DBConstants.KEY.EVENT_VERSION, 0)
                        && visit.getVisitId().compareTo(previous.getVisitId()) > 0) latest.put(source, visit);
            }
        }
        List<Visit> result = new ArrayList<>();
        for (Visit visit : latest.values()) {
            JSONObject event = json(visit);
            LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (day == null || day.isAfter(asOf) || event.optBoolean(DBConstants.KEY.EVENT_VOIDED)) continue;
            Map<String, String> answers = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            String episode = answers.get(DBConstants.KEY.MTOTO_ENROLLMENT_ID);
            if (StringUtils.isNotBlank(episode) && !enrollment.id.isEmpty() && !episode.equals(enrollment.id)) continue;
            if (enrollment.time > 0 && day.isBefore(enrollment.day())) continue;
            result.add(visit);
        }
        result.sort(MotherMentorChildEidDao::compareChronology);
        return result;
    }

    public static List<Visit> clinicalVisits(SQLiteDatabase db, String childId,
            String excludedVisitId, LocalDate asOf) {
        return finalizedVisits(db, childId, excludedVisitId, Enrollment.empty(), asOf);
    }

    public static Visit findVisit(SQLiteDatabase db, String visitId) {
        if (StringUtils.isBlank(visitId) || !hasTable(db, VisitRepository.VISIT_TABLE)) return null;
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.VISIT_ID + " = ?", new String[] {visitId})) {
            return cursor.moveToFirst() ? visit(cursor) : null;
        }
    }

    /** The same ordering is used for normal replay and the prefix before an edited visit. */
    public static int compareChronology(Visit first, Visit second) {
        int day = MotherMentorMotherVisitDao.getServiceDate(first).compareTo(
                MotherMentorMotherVisitDao.getServiceDate(second));
        if (day != 0) return day;
        int time = Long.compare(first.getDate() == null ? 0 : first.getDate().getTime(),
                second.getDate() == null ? 0 : second.getDate().getTime());
        return time != 0 ? time : first.getVisitId().compareTo(second.getVisitId());
    }

    public static String pageQuery(String search, boolean closed, int limit, int offset) {
        return selectColumns() + from() + condition(search, closed) + " ORDER BY m."
                + DBConstants.KEY.FIRST_NAME + " COLLATE NOCASE, m." + DBConstants.KEY.LAST_NAME
                + " COLLATE NOCASE, m." + DBConstants.KEY.BASE_ENTITY_ID + " LIMIT "
                + Math.max(1, limit) + " OFFSET " + Math.max(0, offset);
    }

    public static Map<String, String> cardValues(SQLiteDatabase db, String childId) {
        if (StringUtils.isBlank(childId) || !hasTable(db, ENROLLMENTS)) return Collections.emptyMap();
        try (Cursor cursor = db.rawQuery(selectColumns() + from() + " WHERE e."
                + DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {childId})) {
            return cursor.moveToFirst() ? values(cursor) : Collections.emptyMap();
        }
    }

    private static String selectColumns() {
        String[] metadata = {DBConstants.KEY.MTOTO_MISSED_TEST, DBConstants.KEY.MTOTO_MISSED_DUE,
                DBConstants.KEY.MTOTO_RUFAA_STATE, DBConstants.KEY.MTOTO_RUFAA_TEST,
                DBConstants.KEY.MTOTO_RUFAA_DUE, DBConstants.KEY.MTOTO_STATUS,
                DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA, DBConstants.KEY.MOTHER_BASE_ENTITY_ID,
                DBConstants.KEY.HEI_NUMBER, DBConstants.KEY.MTOTO_VISIT_COUNT};
        StringBuilder select = new StringBuilder("SELECT " + motherName() + " AS "
                + DBConstants.KEY.MOTHER_NAME + ", f." + DBConstants.KEY.VILLAGE_TOWN
                + " AS " + DBConstants.KEY.VILLAGE_TOWN + ", m.*");
        for (String column : metadata) select.append(", e.").append(column);
        select.append(", e.").append(DBConstants.KEY.IS_CLOSED).append(" AS ")
                .append(DBConstants.KEY.MTOTO_IS_CLOSED);
        return select + ", coalesce(nullif(m." + DBConstants.KEY.DOB + ", ''), e."
                + DBConstants.KEY.CHILD_DOB + ") AS " + DBConstants.KEY.CHILD_DOB;
    }

    public static String countQuery(String search, boolean closed) {
        return "SELECT COUNT(*)" + from() + condition(search, closed);
    }

    public static String activeCondition(String alias) {
        return "(" + alias + "." + DBConstants.KEY.IS_CLOSED + " IS NULL OR "
                + alias + "." + DBConstants.KEY.IS_CLOSED + " = 0)";
    }

    private static String from() {
        return " FROM " + ENROLLMENTS + " e INNER JOIN " + MEMBERS + " m ON m."
                + DBConstants.KEY.BASE_ENTITY_ID + " = e." + DBConstants.KEY.BASE_ENTITY_ID
                + " LEFT JOIN " + FAMILIES + " f ON f." + DBConstants.KEY.BASE_ENTITY_ID
                + " = m." + DBConstants.KEY.RELATIONAL_ID + " LEFT JOIN " + MEMBERS
                + " mother ON mother." + DBConstants.KEY.BASE_ENTITY_ID + " = e."
                + DBConstants.KEY.MOTHER_BASE_ENTITY_ID;
    }

    private static String condition(String search, boolean closed) {
        String where = " WHERE " + (closed ? "e." + DBConstants.KEY.IS_CLOSED + " = 1" : activeCondition("e"));
        if (!closed) where += " AND " + MotherMentorDao.getActiveClientCondition("m")
                + " AND (f." + DBConstants.KEY.BASE_ENTITY_ID + " IS NULL OR " + activeCondition("f") + ")";
        if (StringUtils.isNotBlank(search)) {
            String pattern = "'%" + search.trim().replace("'", "''").replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%'";
            where += " AND (" + name("m") + " LIKE " + pattern + " ESCAPE '\\' OR coalesce(e." + DBConstants.KEY.HEI_NUMBER
                    + ", '') LIKE " + pattern + " ESCAPE '\\' OR " + motherName() + " LIKE "
                    + pattern + " ESCAPE '\\' OR coalesce(m." + DBConstants.KEY.UNIQUE_ID
                    + ", '') LIKE " + pattern + " ESCAPE '\\')";
        }
        return where;
    }

    private static String motherName() {
        return "coalesce(nullif(" + name("mother") + ", ''), e." + DBConstants.KEY.MOTHER_NAME + ", '')";
    }

    private static String name(String alias) {
        return "trim(coalesce(" + alias + "." + DBConstants.KEY.FIRST_NAME + ", '') || ' ' || "
                + "CASE WHEN trim(coalesce(" + alias + "." + DBConstants.KEY.MIDDLE_NAME
                + ", '')) <> '' THEN trim(" + alias + "." + DBConstants.KEY.MIDDLE_NAME
                + ") || ' ' ELSE '' END || coalesce(" + alias + "." + DBConstants.KEY.LAST_NAME + ", ''))";
    }

    public static Set<String> columns(SQLiteDatabase db, String table) {
        Set<String> result = new HashSet<>();
        if (!hasTable(db, table)) return result;
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(1));
        }
        return result;
    }

    public static boolean hasTable(SQLiteDatabase db, String table) {
        if (db == null) return false;
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[] {table})) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    public static Map<String, String> values(Cursor cursor) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : cursor.getColumnNames()) {
            int index = cursor.getColumnIndex(column);
            values.put(column, cursor.isNull(index) ? "" : cursor.getString(index));
        }
        return values;
    }

    private static Visit visit(Cursor cursor) {
        Visit visit = new Visit();
        visit.setVisitId(text(cursor, DBConstants.KEY.VISIT_ID));
        visit.setBaseEntityId(text(cursor, DBConstants.KEY.BASE_ENTITY_ID));
        visit.setVisitType(text(cursor, DBConstants.KEY.VISIT_TYPE));
        visit.setFormSubmissionId(text(cursor, DBConstants.KEY.FORM_SUBMISSION_ID));
        String date = text(cursor, DBConstants.KEY.VISIT_DATE);
        if (!date.isEmpty()) try { visit.setDate(new Date(Long.parseLong(date))); }
        catch (NumberFormatException ignored) { }
        visit.setJson(text(cursor, DBConstants.KEY.VISIT_JSON));
        if (StringUtils.isBlank(visit.getJson())) visit.setJson(text(cursor, DBConstants.KEY.PRE_PROCESSED));
        visit.setProcessed(true);
        return visit;
    }

    private static String text(Cursor cursor, String key) {
        int index = cursor.getColumnIndex(key);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    public static JSONObject json(Visit visit) {
        try { return new JSONObject(visit.getJson()); }
        catch (Exception invalid) { return new JSONObject(); }
    }

    public static String sourceIdentity(Visit visit) {
        return StringUtils.defaultIfBlank(json(visit).optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID),
                StringUtils.defaultIfBlank(visit.getFormSubmissionId(), visit.getVisitId()));
    }

    private static long eventTime(JSONObject event) {
        try { return new DateTime(event.optString(DBConstants.KEY.EVENT_DATE_TIMESTAMP)).getMillis(); }
        catch (Exception invalid) { return 0; }
    }

    public static final class Enrollment {
        public final String id;
        public final long time;
        public final long version;
        public final JSONObject json;

        Enrollment(String id, long time, long version, JSONObject json) {
            this.id = id;
            this.time = time;
            this.version = version;
            this.json = json;
        }

        public LocalDate day() {
            LocalDate clinicalDay = MotherMentorHeiSchedule.parseDate(MotherMentorVisitCompletion.observationValues(
                    json.optJSONArray("obs")).get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE));
            return clinicalDay != null ? clinicalDay : time == 0 ? null
                    : MotherMentorHeiSchedule.parseBirthDate(json.optString(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
        }
        public static Enrollment empty() { return new Enrollment("", 0, 0, new JSONObject()); }
    }
}
