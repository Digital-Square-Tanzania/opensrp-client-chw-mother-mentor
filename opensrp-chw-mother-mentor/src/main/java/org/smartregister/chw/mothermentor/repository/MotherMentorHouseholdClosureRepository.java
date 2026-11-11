package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.processor.HouseholdEnrolmentEventProcessor;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorDeceasedVisitUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Rebuildable household closure summary; finalized visit answers remain the source of truth. */
public final class MotherMentorHouseholdClosureRepository {
    public static final List<String> COUNTER_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.KAYA_COUNTER_NYUMBA_IMEFUNGWA,
            DBConstants.KEY.KAYA_COUNTER_NYINGINE,
            DBConstants.KEY.KAYA_COUNTER_HAIJAPATIKANA));
    public static final List<String> CLOSURE_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.KAYA_STATUS,
            DBConstants.KEY.KAYA_TAREHE_YA_KUFUNGWA,
            DBConstants.KEY.KAYA_SABABU_YA_KUFUNGWA,
            DBConstants.KEY.KAYA_COUNTER_NYUMBA_IMEFUNGWA,
            DBConstants.KEY.KAYA_COUNTER_NYINGINE,
            DBConstants.KEY.KAYA_COUNTER_HAIJAPATIKANA));
    private static final String ENROLLMENTS = Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT;

    private MotherMentorHouseholdClosureRepository() {}

    /** Called by the versioned host migration, without opening another database connection. */
    public static void upgrade(SQLiteDatabase database) {
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            Set<String> existing = columns(database, ENROLLMENTS);
            if (!existing.isEmpty()) {
                for (String column : CLOSURE_COLUMNS) {
                    if (!existing.contains(column)) {
                        database.execSQL("ALTER TABLE " + ENROLLMENTS + " ADD COLUMN " + column
                                + (COUNTER_COLUMNS.contains(column) ? " INTEGER" : " VARCHAR"));
                    }
                }
                Set<String> households = new LinkedHashSet<>();
                try (Cursor cursor = database.rawQuery("SELECT DISTINCT "
                        + DBConstants.KEY.BASE_ENTITY_ID + " FROM " + ENROLLMENTS, new String[0])) {
                    while (cursor.moveToNext()) {
                        if (StringUtils.isNotBlank(cursor.getString(0))) households.add(cursor.getString(0));
                    }
                }
                for (String household : households) rebuild(database, household);
            }
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
    }

    public static MotherMentorHouseholdClosure getState(
            SQLiteDatabase database, String householdId, String excludedVisitId) {
        MotherMentorHouseholdClosure state = getVisitState(database, householdId, excludedVisitId);
        Map<String, String> enrollment = enrollment(database, householdId);
        String memberReason = value(enrollment, DBConstants.KEY.KAYA_STATUS);
        String memberDate = value(enrollment, DBConstants.KEY.KAYA_TAREHE_YA_KUFUNGWA);
        boolean memberClosed = MotherMentorHouseholdClosure.isMemberClosure(memberReason);
        if (memberClosed && memberDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                && MotherMentorDeceasedVisitUtils.parseDate(memberDate) != null
                && memberDate.compareTo(iso(new Date())) <= 0
                && memberDate.compareTo(iso(new Date(episode(database, householdId, enrollment).started))) >= 0
                && (!state.isClosed() || memberDate.compareTo(state.getClosedDate()) < 0)) {
            return state.withMemberClosure(memberReason, memberDate);
        }
        return state;
    }

    /** Replay existing household enrollments when member-exit closure is enabled. */
    public static void rebuildMemberExits(SQLiteDatabase database) {
        if (!columns(database, ENROLLMENTS).containsAll(CLOSURE_COLUMNS)) return;
        Set<String> households = new LinkedHashSet<>();
        try (Cursor rows = database.rawQuery("SELECT DISTINCT " + DBConstants.KEY.BASE_ENTITY_ID
                + " FROM " + ENROLLMENTS + " WHERE " + DBConstants.KEY.CONSENT + " = ?",
                new String[] {HouseholdEnrolmentEventProcessor.CONSENT_YES})) {
            while (rows.moveToNext()) households.add(rows.getString(0));
        }
        for (String household : households) rebuildMemberExit(database, household);
    }

    public static void rebuildMemberExit(SQLiteDatabase database, String householdId) {
        MotherMentorMotherCascadeRepository.rebuildHousehold(database, householdId);
    }

    public static void rebuildForMemberDeath(SQLiteDatabase database, String memberId) {
        String householdId = MotherMentorMotherCascadeRepository.row(database, "ec_family_member", memberId)
                .get(DBConstants.KEY.RELATIONAL_ID);
        rebuildMemberExit(database, householdId);
    }

    /** Own visit evidence only, used when a member projection is corrected or removed. */
    public static MotherMentorHouseholdClosure getVisitState(
            SQLiteDatabase database, String householdId, String excludedVisitId) {
        Map<String, String> enrollment = enrollment(database, householdId);
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        if (enrollment.isEmpty()) return state;
        Episode episode = episode(database, householdId, enrollment);
        for (Evidence visit : evidence(database, householdId, excludedVisitId, episode)) {
            state = state.advance(visit.values.get(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE),
                    visit.values.get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS),
                    visit.values.get(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON), visit.serviceDate);
        }
        return state;
    }

    /** A source enrolment UUID is metadata on visits, not a new household table column. */
    public static String currentEnrollmentId(SQLiteDatabase database, String householdId) {
        Map<String, String> enrollment = enrollment(database, householdId);
        return enrollment.isEmpty() ? "" : episode(database, householdId, enrollment).id;
    }

    public static boolean belongsToCurrentEnrollment(SQLiteDatabase database, String householdId,
            String eventJson) {
        Map<String, String> enrollment = enrollment(database, householdId);
        if (enrollment.isEmpty() || StringUtils.isBlank(eventJson)) return false;
        try {
            JSONObject event = new JSONObject(eventJson);
            Map<String, String> values = MotherMentorHouseholdVisitUtils.observationValues(
                    event.optJSONArray("obs"));
            return sameEnrollment(values, timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP)),
                    episode(database, householdId, enrollment));
        } catch (JSONException invalid) {
            return false;
        }
    }

    static void rebuildForVisit(SQLiteDatabase database, Visit visit) {
        if (visit != null && Boolean.TRUE.equals(visit.getProcessed()) && isHouseholdVisit(visit)) {
            rebuild(database, visit.getBaseEntityId());
        }
    }

    static Set<String> affectedHouseholds(SQLiteDatabase database, String visitId) {
        Set<String> households = new LinkedHashSet<>();
        try (Cursor cursor = database.rawQuery("SELECT " + DBConstants.KEY.BASE_ENTITY_ID + ", "
                + DBConstants.KEY.VISIT_TYPE + ", " + DBConstants.KEY.VISIT_JSON + " FROM "
                + VisitRepository.VISIT_TABLE + " WHERE " + DBConstants.KEY.VISIT_ID + " = ?",
                new String[] {visitId})) {
            while (cursor.moveToNext()) {
                Visit visit = new Visit();
                visit.setVisitType(cursor.getString(1));
                visit.setJson(cursor.getString(2));
                if (isHouseholdVisit(visit)) households.add(cursor.getString(0));
            }
        }
        return households;
    }

    public static void rebuild(SQLiteDatabase database, String householdId) {
        if (!columns(database, ENROLLMENTS).containsAll(CLOSURE_COLUMNS)) return;
        Map<String, String> enrollment = enrollment(database, householdId);
        if (enrollment.isEmpty()) return;
        MotherMentorHouseholdClosure state = getState(database, householdId, null);
        ContentValues values = new ContentValues();
        for (Map.Entry<String, String> entry : observationValues(state).entrySet()) {
            if (COUNTER_COLUMNS.contains(entry.getKey())) {
                values.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            } else {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        String previous = value(enrollment, DBConstants.KEY.KAYA_STATUS);
        if (state.isClosed() || knownClosedStatus(previous)) {
            values.put(DBConstants.KEY.IS_CLOSED, state.isClosed() ? 1 : 0);
        }
        database.update(ENROLLMENTS, values, DBConstants.KEY.BASE_ENTITY_ID + " = ?",
                new String[] {householdId});
    }

    public static Map<String, String> observationValues(MotherMentorHouseholdClosure state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DBConstants.KEY.KAYA_STATUS, state.getStatus());
        values.put(DBConstants.KEY.KAYA_TAREHE_YA_KUFUNGWA, state.getClosedDate());
        values.put(DBConstants.KEY.KAYA_SABABU_YA_KUFUNGWA, state.getClosureReason());
        values.put(DBConstants.KEY.KAYA_COUNTER_NYUMBA_IMEFUNGWA,
                Integer.toString(state.getHouseClosedCount()));
        values.put(DBConstants.KEY.KAYA_COUNTER_NYINGINE, Integer.toString(state.getOtherCount()));
        values.put(DBConstants.KEY.KAYA_COUNTER_HAIJAPATIKANA, Integer.toString(state.getNotFoundCount()));
        return values;
    }

    public static boolean isHouseholdVisit(Visit visit) {
        if (visit == null || StringUtils.isBlank(visit.getJson())
                || !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
            return false;
        }
        try {
            Map<String, String> values = MotherMentorHouseholdVisitUtils.observationValues(
                    new JSONObject(visit.getJson()).optJSONArray("obs"));
            return values.containsKey(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA)
                    || values.containsKey(DBConstants.KEY.HOUSEHOLD_ENROLLMENT_ID);
        } catch (JSONException invalid) {
            return false;
        }
    }

    private static boolean knownClosedStatus(String status) {
        return MotherMentorHouseholdClosure.MOVED.equals(status)
                || MotherMentorHouseholdClosure.HOUSE_CLOSED_3.equals(status)
                || MotherMentorHouseholdClosure.OTHER_3.equals(status)
                || MotherMentorHouseholdClosure.LOST_TO_FOLLOW_UP.equals(status)
                || MotherMentorHouseholdClosure.isMemberClosure(status);
    }

    private static List<Evidence> evidence(SQLiteDatabase database, String householdId,
            String excludedVisitId, Episode episode) {
        List<Evidence> candidates = new ArrayList<>();
        if (columns(database, VisitRepository.VISIT_TABLE).isEmpty()) return candidates;
        String today = iso(new Date());
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE
                + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND "
                + DBConstants.KEY.PROCESSED + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " = ?",
                new String[] {householdId, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES})) {
            while (cursor.moveToNext()) {
                String id = text(cursor, DBConstants.KEY.VISIT_ID);
                String formId = text(cursor, DBConstants.KEY.FORM_SUBMISSION_ID);
                if (id.isEmpty() && formId.isEmpty()) continue;
                String json = text(cursor, DBConstants.KEY.VISIT_JSON);
                try {
                    JSONObject event = new JSONObject(json);
                    Map<String, String> values = MotherMentorHouseholdVisitUtils.observationValues(
                            event.optJSONArray("obs"));
                    if (formId.isEmpty()) formId = event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID);
                    String recorded = value(values, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
                    long eventTime = timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
                    boolean valid = !event.optBoolean("voided")
                            && sameEnrollment(values, eventTime, episode)
                            && "2".equals(values.get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA))
                            && MotherMentorHouseholdVisitUtils.isComplete(values)
                            && (recorded.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")
                                    || recorded.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
                    Date date = valid ? MotherMentorHouseholdVisitUtils.getVisitDate(
                            new Visit(), event, values) : null;
                    valid = valid && date != null && iso(date).compareTo(today) <= 0;
                    candidates.add(new Evidence(id, formId, valid ? iso(date) : "", eventTime,
                            timestamp(event.opt("version")), json, values, valid));
                } catch (JSONException ignored) {
                    // Invalid imported events cannot contribute a strike or a reset.
                }
            }
        }
        Set<String> excludedForms = new HashSet<>();
        for (Evidence visit : candidates) {
            if (visit.id.equals(excludedVisitId) || visit.formId.equals(excludedVisitId)) {
                excludedForms.add(visit.formId);
            }
        }
        candidates.sort(Comparator.comparingLong((Evidence visit) -> visit.version).reversed()
                .thenComparing(visit -> visit.id).thenComparing(visit -> visit.json));
        Set<String> forms = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<Evidence> result = new ArrayList<>();
        for (Evidence visit : candidates) {
            boolean excluded = visit.id.equals(excludedVisitId) || visit.formId.equals(excludedVisitId)
                    || (!visit.formId.isEmpty() && excludedForms.contains(visit.formId));
            boolean duplicate = (!visit.formId.isEmpty() && forms.contains(visit.formId))
                    || (!visit.id.isEmpty() && ids.contains(visit.id));
            if (!visit.formId.isEmpty()) forms.add(visit.formId);
            if (!visit.id.isEmpty()) ids.add(visit.id);
            if (!excluded && !duplicate && visit.valid) result.add(visit);
        }
        result.sort(Comparator.comparing((Evidence visit) -> visit.serviceDate)
                .thenComparingLong(visit -> visit.eventTime).thenComparing(visit -> visit.id));
        return result;
    }

    private static boolean sameEnrollment(Map<String, String> values, long eventTime, Episode episode) {
        String visitEnrollment = value(values, DBConstants.KEY.HOUSEHOLD_ENROLLMENT_ID);
        return visitEnrollment.isEmpty() ? eventTime > 0 && eventTime >= episode.started
                : visitEnrollment.equals(episode.id) || visitEnrollment.equals(episode.legacyId);
    }

    private static Map<String, String> enrollment(SQLiteDatabase database, String householdId) {
        Set<String> available = columns(database, ENROLLMENTS);
        if (available.isEmpty() || StringUtils.isBlank(householdId)) return Collections.emptyMap();
        String order = available.contains(DBConstants.KEY.LAST_INTERACTED_WITH)
                ? " ORDER BY CAST(" + DBConstants.KEY.LAST_INTERACTED_WITH + " AS INTEGER) DESC" : "";
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + ENROLLMENTS + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ?" + order + " LIMIT 1",
                new String[] {householdId})) {
            if (!cursor.moveToFirst()) return Collections.emptyMap();
            Map<String, String> values = new HashMap<>();
            for (String column : cursor.getColumnNames()) values.put(column, text(cursor, column));
            return values;
        }
    }

    private static Episode episode(SQLiteDatabase database, String householdId,
            Map<String, String> enrollment) {
        String date = value(enrollment, DBConstants.KEY.EVENT_DATE);
        if (date.isEmpty()) date = value(enrollment, DBConstants.KEY.ENROLLMENT_DATE);
        String legacyId = "legacy:" + householdId + (date.isEmpty() ? "" : ":" + date);
        long rowTime = timestamp(date);
        Episode latest = null;
        if (!columns(database, "event").isEmpty()) {
            Map<String, Episode> revisions = new HashMap<>();
            try (Cursor cursor = database.rawQuery("SELECT * FROM event WHERE "
                    + DBConstants.KEY.EVENT_BASE_ENTITY_ID + " = ? AND "
                    + DBConstants.KEY.EVENT_TYPE + " = ?", new String[] {householdId,
                        Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT})) {
                while (cursor.moveToNext()) {
                    try {
                        JSONObject event = new JSONObject(text(cursor, DBConstants.KEY.EVENT_JSON));
                        String id = event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID,
                                text(cursor, DBConstants.KEY.EVENT_FORM_SUBMISSION_ID));
                        long started = timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
                        if (started == 0) {
                            started = timestamp(text(cursor, DBConstants.KEY.EVENT_DATE_TIMESTAMP));
                        }
                        if (id.isEmpty() || started <= 0) continue;
                        String consent = MotherMentorHouseholdVisitUtils.observationValues(
                                event.optJSONArray("obs")).get(DBConstants.KEY.CONSENT);
                        Episode candidate = new Episode(id, legacyId, started,
                                timestamp(event.opt("version")),
                                !event.optBoolean("voided")
                                        && HouseholdEnrolmentEventProcessor.CONSENT_YES.equals(consent));
                        Episode previous = revisions.get(id);
                        if (previous == null || candidate.version > previous.version) {
                            revisions.put(id, candidate);
                        }
                    } catch (JSONException ignored) {
                        // Legacy enrolments can still use the existing row's stable date metadata.
                    }
                }
            }
            for (Episode candidate : revisions.values()) {
                if (candidate.consented && (latest == null || candidate.started > latest.started
                        || (candidate.started == latest.started && candidate.id.compareTo(latest.id) > 0))) {
                    latest = candidate;
                }
            }
        }
        if (latest == null || rowTime > latest.started) {
            return new Episode(legacyId, legacyId, rowTime, 0, true);
        }
        if (rowTime > 0 && rowTime != latest.started) {
            return new Episode(latest.id, "", latest.started, latest.version, true);
        }
        return latest;
    }

    private static Set<String> columns(SQLiteDatabase database, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", new String[0])) {
            while (cursor.moveToNext()) result.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
        return result;
    }

    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
    }

    private static String iso(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static long timestamp(Object value) {
        if (value instanceof Number) return Math.max(0, ((Number) value).longValue());
        if (value == null || value.toString().trim().isEmpty()) return 0;
        try {
            return Math.max(0, Long.parseLong(value.toString()));
        } catch (NumberFormatException ignored) {
            try {
                return new DateTime(value).getMillis();
            } catch (IllegalArgumentException invalid) {
                return 0;
            }
        }
    }

    private static final class Episode {
        final String id;
        final String legacyId;
        final long started;
        final long version;
        final boolean consented;

        Episode(String id, String legacyId, long started, long version, boolean consented) {
            this.id = id;
            this.legacyId = legacyId;
            this.started = started;
            this.version = version;
            this.consented = consented;
        }
    }

    private static final class Evidence {
        final String id;
        final String formId;
        final String serviceDate;
        final long eventTime;
        final long version;
        final String json;
        final Map<String, String> values;
        final boolean valid;

        Evidence(String id, String formId, String serviceDate, long eventTime, long version,
                String json, Map<String, String> values, boolean valid) {
            this.id = id;
            this.formId = formId;
            this.serviceDate = serviceDate;
            this.eventTime = eventTime;
            this.version = version;
            this.json = json;
            this.values = values;
            this.valid = valid;
        }
    }
}
