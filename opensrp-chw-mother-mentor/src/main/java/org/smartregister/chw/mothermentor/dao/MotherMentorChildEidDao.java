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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static Map<String, String> row(SQLiteDatabase db, String table, String id) {
        if (StringUtils.isBlank(id) || !hasTable(db, table)) return Collections.emptyMap();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + table + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {id})) {
            return cursor.moveToFirst() ? values(cursor) : Collections.emptyMap();
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
            if (!id.equals(motherId) && !person.isEmpty()
                    && dob.equals(MotherMentorHeiSchedule.parseDate(person.get(DBConstants.KEY.DOB)))
                    && StringUtils.isBlank(person.get(DBConstants.KEY.DATE_DIED))
                    && StringUtils.isBlank(person.get(DBConstants.KEY.DOD))) matches.add(id);
        }
        return matches.size() == 1 ? matches.iterator().next() : "";
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
        if (StringUtils.isBlank(childId) || !hasTable(db, "event")) return Enrollment.empty();
        Map<String, Enrollment> revisions = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT json FROM event WHERE baseEntityId = ? AND eventType = ?",
                new String[] {childId, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject json = new JSONObject(cursor.getString(0));
                    String id = json.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID);
                    long time = eventTime(json);
                    if (id.isEmpty() || time == 0) continue;
                    Enrollment candidate = new Enrollment(id, time, json.optLong("version", 0), json);
                    Enrollment previous = revisions.get(id);
                    if (previous == null || candidate.version > previous.version) revisions.put(id, candidate);
                } catch (Exception ignored) { }
            }
        }
        Enrollment latest = Enrollment.empty();
        for (Enrollment candidate : revisions.values()) {
            if (candidate.json.optBoolean("voided") || candidate.day().isAfter(asOf)) continue;
            if (candidate.time > latest.time || candidate.time == latest.time
                    && candidate.id.compareTo(latest.id) > 0) latest = candidate;
        }
        return latest;
    }

    public static boolean hasEvent(SQLiteDatabase db, String formSubmissionId) {
        if (StringUtils.isBlank(formSubmissionId) || !hasTable(db, "event")) return false;
        try (Cursor cursor = db.rawQuery("SELECT json FROM event WHERE baseEntityId IN ("
                + "SELECT " + DBConstants.KEY.BASE_ENTITY_ID + " FROM " + MEMBERS + ")"
                + " AND eventType = ?", new String[] {Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID})) {
            while (cursor.moveToNext()) {
                try {
                    if (formSubmissionId.equals(new JSONObject(cursor.getString(0))
                            .optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID))) return true;
                } catch (Exception ignored) { }
            }
        }
        return false;
    }

    /** Finalized, corrected-source-deduplicated evidence for the selected child episode. */
    public static List<Visit> finalizedVisits(SQLiteDatabase db, String childId,
            String excludedVisitId, Enrollment enrollment, LocalDate asOf) {
        if (!hasTable(db, VisitRepository.VISIT_TABLE)) return Collections.emptyList();
        Map<String, Visit> latest = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.PROCESSED
                + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " = ?",
                new String[] {childId, MotherMentorFollowUpType.CHILD_EID.getEventType()})) {
            while (cursor.moveToNext()) {
                Visit visit = visit(cursor);
                if (StringUtils.equals(excludedVisitId, visit.getVisitId())) continue;
                JSONObject event = json(visit);
                String source = StringUtils.defaultIfBlank(event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID),
                        StringUtils.defaultIfBlank(visit.getFormSubmissionId(), visit.getVisitId()));
                Visit previous = latest.get(source);
                if (previous == null || event.optLong("version", 0) > json(previous).optLong("version", 0)
                        || event.optLong("version", 0) == json(previous).optLong("version", 0)
                        && visit.getVisitId().compareTo(previous.getVisitId()) > 0) latest.put(source, visit);
            }
        }
        List<Visit> result = new ArrayList<>();
        for (Visit visit : latest.values()) {
            JSONObject event = json(visit);
            LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (day == null || day.isAfter(asOf) || event.optBoolean("voided")) continue;
            Map<String, String> answers = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            String episode = answers.get(DBConstants.KEY.MTOTO_ENROLLMENT_ID);
            if (StringUtils.isNotBlank(episode) && !enrollment.id.isEmpty() && !episode.equals(enrollment.id)) continue;
            if (enrollment.time > 0 && day.isBefore(enrollment.day())) continue;
            result.add(visit);
        }
        result.sort(Comparator.comparing((Visit item) -> MotherMentorMotherVisitDao.getServiceDate(item))
                .thenComparing(Visit::getVisitId));
        return result;
    }

    public static String pageQuery(String search, boolean closed, int limit, int offset) {
        return "SELECT m.*, e.*, " + motherName() + " AS " + DBConstants.KEY.MOTHER_NAME
                + ", coalesce(nullif(m." + DBConstants.KEY.DOB + ", ''), e."
                + DBConstants.KEY.CHILD_DOB + ") AS " + DBConstants.KEY.CHILD_DOB
                + from() + condition(search, closed) + " ORDER BY m." + DBConstants.KEY.FIRST_NAME
                + " COLLATE NOCASE, m." + DBConstants.KEY.LAST_NAME + " COLLATE NOCASE, m."
                + DBConstants.KEY.BASE_ENTITY_ID + " LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset);
    }

    public static String countQuery(String search, boolean closed) {
        return "SELECT COUNT(*)" + from() + condition(search, closed);
    }

    public static String activeCondition(String alias) {
        return "coalesce(" + alias + "." + DBConstants.KEY.IS_CLOSED + ", 0) = 0";
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
        String where = " WHERE coalesce(e." + DBConstants.KEY.IS_CLOSED + ", 0) = " + (closed ? "1" : "0");
        if (!closed) where += " AND " + MotherMentorDao.getActiveClientCondition("m")
                + " AND (f." + DBConstants.KEY.BASE_ENTITY_ID + " IS NULL OR coalesce(f."
                + DBConstants.KEY.IS_CLOSED + ", 0) = 0)";
        if (StringUtils.isNotBlank(search)) {
            String pattern = "'%" + search.trim().replace("'", "''").replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%'";
            where += " AND (trim(coalesce(m." + DBConstants.KEY.FIRST_NAME + ", '') || ' ' || coalesce(m."
                    + DBConstants.KEY.MIDDLE_NAME + ", '') || ' ' || coalesce(m." + DBConstants.KEY.LAST_NAME
                    + ", '')) LIKE " + pattern + " ESCAPE '\\' OR coalesce(e." + DBConstants.KEY.HEI_NUMBER
                    + ", '') LIKE " + pattern + " ESCAPE '\\' OR " + motherName() + " LIKE "
                    + pattern + " ESCAPE '\\' OR coalesce(m." + DBConstants.KEY.UNIQUE_ID
                    + ", '') LIKE " + pattern + " ESCAPE '\\')";
        }
        return where;
    }

    private static String motherName() {
        return "coalesce(nullif(trim(coalesce(mother." + DBConstants.KEY.FIRST_NAME
                + ", '') || ' ' || coalesce(mother." + DBConstants.KEY.MIDDLE_NAME
                + ", '') || ' ' || coalesce(mother." + DBConstants.KEY.LAST_NAME
                + ", '')), ''), e." + DBConstants.KEY.MOTHER_NAME + ", '')";
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
            return cursor.moveToFirst();
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

        public LocalDate day() { return time == 0 ? null : new LocalDate(time); }
        public static Enrollment empty() { return new Enrollment("", 0, 0, new JSONObject()); }
    }
}
