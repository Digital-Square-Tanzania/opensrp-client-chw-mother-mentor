package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Original mother enrollment context and canonical finalized visits for its current episode. */
public final class MotherMentorMotherVisitDao {
    private MotherMentorMotherVisitDao() {}

    public static Context getContext(String motherId) {
        if (StringUtils.isBlank(motherId)) return Context.empty();
        return getContext(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(), motherId);
    }

    /** Closed enrollments remain available to history and rebuildable projections. */
    public static Context getContext(SQLiteDatabase db, String motherId) {
        if (StringUtils.isBlank(motherId)
                || !hasTable(db, Constants.TABLES.MOTHERMENTOR_ENROLLMENT)) {
            return Context.empty();
        }
        Map<String, String> row = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + Constants.TABLES.MOTHERMENTOR_ENROLLMENT
                             + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ? ORDER BY CAST("
                             + DBConstants.KEY.LAST_INTERACTED_WITH + " AS INTEGER) DESC LIMIT 1",
                     new String[] {motherId})) {
            if (!cursor.moveToFirst()) return Context.empty();
            for (String column : cursor.getColumnNames()) row.put(column, text(cursor, column));
        }
        String rowDate = value(row, DBConstants.KEY.EVENT_DATE);
        if (rowDate.isEmpty()) rowDate = value(row, DBConstants.KEY.ENROLLMENT_DATE);
        long rowTime = timestamp(rowDate);
        String legacyId = "legacy:" + motherId + (rowDate.isEmpty() ? "" : ":" + rowDate);
        Enrollment latest = latestEnrollment(db, motherId);
        if (latest != null && rowTime <= latest.time) {
            Map<String, String> original =
                    MotherMentorVisitCompletion.observationValues(latest.event.optJSONArray("obs"));
            return new Context(latest.id, rowTime == 0 || rowTime == latest.time ? legacyId : "",
                    normalizePregnancy(value(original, DBConstants.KEY.PREGNANCY_STATUS)),
                    value(row, DBConstants.KEY.CHILD_DOB),
                    value(original, DBConstants.KEY.CHILD_DOB), familyId(db, motherId),
                    latest.time);
        }
        Context legacy = new Context(legacyId, legacyId,
                normalizePregnancy(value(row, DBConstants.KEY.PREGNANCY_STATUS)),
                value(row, DBConstants.KEY.CHILD_DOB), value(row, DBConstants.KEY.CHILD_DOB),
                familyId(db, motherId), rowTime);
        if (hasTable(db, Constants.TABLES.MOTHERMENTOR_MOTHER_CONTEXT)) {
            try (Cursor cursor = db.rawQuery("SELECT "
                                 + DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS + ", "
                                 + DBConstants.KEY.CHILD_DOB + " FROM "
                                 + Constants.TABLES.MOTHERMENTOR_MOTHER_CONTEXT + " WHERE "
                                 + DBConstants.KEY.MAMA_ENROLLMENT_ID + " = ? AND "
                                 + DBConstants.KEY.BASE_ENTITY_ID + " = ? LIMIT 1",
                         new String[] {legacyId, motherId})) {
                if (cursor.moveToFirst()) {
                    return new Context(legacyId, legacyId,
                            normalizePregnancy(
                                    text(cursor, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS)),
                            legacy.getChildDob(), text(cursor, DBConstants.KEY.CHILD_DOB),
                            legacy.getFamilyId(), rowTime);
                }
            }
        }
        for (Visit visit : getFinalizedVisits(db, motherId, null, legacy)) {
            try {
                Map<String, String> recorded = MotherMentorVisitCompletion.observationValues(
                        new JSONObject(visit.getJson()).optJSONArray("obs"));
                String originalPregnancy =
                        value(recorded, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS);
                if (!originalPregnancy.isEmpty()
                        && legacyId.equals(value(recorded, DBConstants.KEY.MAMA_ENROLLMENT_ID))) {
                    return new Context(legacyId, legacyId, normalizePregnancy(originalPregnancy),
                            legacy.getChildDob(), legacy.getEnrollmentChildDob(),
                            legacy.getFamilyId(), rowTime);
                }
            } catch (JSONException ignored) {
                // The enrollment row remains usable without an immutable visit snapshot.
            }
        }
        return legacy;
    }

    public static boolean belongsToCurrentEnrollment(
            SQLiteDatabase db, String motherId, String eventJson) {
        if (StringUtils.isBlank(eventJson)) return false;
        try {
            return belongs(new JSONObject(eventJson), getContext(db, motherId));
        } catch (JSONException invalid) {
            return false;
        }
    }

    /** Domain projections validate their own answers after this common evidence selection. */
    public static List<Visit> getFinalizedVisits(
            SQLiteDatabase db, String motherId, String excludedVisitId) {
        return getFinalizedVisits(db, motherId, excludedVisitId, getContext(db, motherId));
    }

    private static List<Visit> getFinalizedVisits(
            SQLiteDatabase db, String motherId, String excludedVisitId, Context context) {
        if (context.getEnrollmentId().isEmpty() || !hasTable(db, VisitRepository.VISIT_TABLE)) {
            return Collections.emptyList();
        }
        List<Evidence> candidates = new ArrayList<>();
        Set<String> excludedForms = new HashSet<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                             + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND "
                             + DBConstants.KEY.VISIT_TYPE + " = ? AND " + DBConstants.KEY.PROCESSED
                             + " = 1",
                     new String[] {motherId, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES})) {
            while (cursor.moveToNext()) {
                Visit visit = readVisit(cursor);
                try {
                    JSONObject event = new JSONObject(visit.getJson());
                    String formId = StringUtils.defaultIfBlank(
                            event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID),
                            visit.getFormSubmissionId());
                    LocalDate date = getServiceDate(visit);
                    boolean valid = !event.optBoolean("voided") && belongs(event, context)
                            && date != null && !date.isAfter(LocalDate.now())
                            && (context.getEnrollmentTime() == 0
                                    || !date.isBefore(new LocalDate(context.getEnrollmentTime())));
                    Evidence evidence = new Evidence(visit, StringUtils.defaultString(formId), date,
                            timestamp(event.opt("version")), valid);
                    candidates.add(evidence);
                    if (StringUtils.isNotBlank(excludedVisitId)
                            && (StringUtils.equals(excludedVisitId, visit.getVisitId())
                                    || StringUtils.equals(excludedVisitId, formId))) {
                        excludedForms.add(evidence.formId);
                    }
                } catch (JSONException ignored) {
                    // Malformed imported records are not finalized clinical evidence.
                }
            }
        }
        candidates.sort(Comparator.comparingLong((Evidence e) -> e.version)
                        .reversed()
                        .thenComparing(Comparator
                                        .comparingLong((Evidence e) -> time(e.visit.getUpdatedAt()))
                                        .reversed())
                        .thenComparing(Comparator
                                        .comparingLong((Evidence e) -> time(e.visit.getCreatedAt()))
                                        .reversed())
                        .thenComparing(e
                                -> StringUtils.defaultString(e.visit.getVisitId()),
                                Comparator.reverseOrder()));
        Set<String> forms = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<Evidence> selected = new ArrayList<>();
        for (Evidence evidence : candidates) {
            String id = evidence.visit.getVisitId();
            boolean duplicate = ids.contains(id)
                    || (!evidence.formId.isEmpty() && forms.contains(evidence.formId));
            ids.add(id);
            if (!evidence.formId.isEmpty()) forms.add(evidence.formId);
            if (!duplicate && evidence.valid
                    && !(StringUtils.isNotBlank(excludedVisitId)
                            && StringUtils.equals(excludedVisitId, id))
                    && !excludedForms.contains(evidence.formId))
                selected.add(evidence);
        }
        selected.sort(Comparator.comparing((Evidence e) -> e.date)
                        .thenComparingLong(e -> time(e.visit.getDate()))
                        .thenComparing(e -> StringUtils.defaultString(e.visit.getVisitId())));
        List<Visit> result = new ArrayList<>();
        for (Evidence evidence : selected) result.add(evidence.visit);
        return result;
    }

    /** An invalid explicit service date must not fall back to the later import timestamp. */
    public static LocalDate getServiceDate(Visit visit) {
        if (visit == null) return null;
        try {
            JSONObject event = new JSONObject(visit.getJson());
            Map<String, String> values =
                    MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            String recorded = values.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
            if (StringUtils.isNotBlank(recorded)) return calendarDate(recorded);
            long eventTime = timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
            if (eventTime > 0) {
                Object source = event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP);
                if (source instanceof String) {
                    try {
                        return DateTime.parse((String) source).toLocalDate();
                    } catch (IllegalArgumentException ignored) {
                        // Numeric legacy timestamps use the device calendar.
                    }
                }
                return new LocalDate(eventTime);
            }
        } catch (JSONException ignored) {
            return null;
        }
        return visit.getDate() == null ? null : new LocalDate(visit.getDate());
    }

    private static boolean belongs(JSONObject event, Context context) {
        if (context.getEnrollmentId().isEmpty()) return false;
        String id = value(MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs")),
                DBConstants.KEY.MAMA_ENROLLMENT_ID);
        return id.isEmpty() ? timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP))
                                >= context.enrollmentTime
                        && timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP)) > 0
                            : id.equals(context.id)
                        || (!context.legacyId.isEmpty() && id.equals(context.legacyId));
    }

    private static Enrollment latestEnrollment(SQLiteDatabase db, String motherId) {
        if (!hasTable(db, "event")) return null;
        Map<String, Enrollment> revisions = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM event WHERE "
                             + DBConstants.KEY.EVENT_BASE_ENTITY_ID + " = ? AND "
                             + DBConstants.KEY.EVENT_TYPE + " = ?",
                     new String[] {motherId, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject event = new JSONObject(text(cursor, DBConstants.KEY.EVENT_JSON));
                    String id = event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID,
                            text(cursor, DBConstants.KEY.EVENT_FORM_SUBMISSION_ID));
                    long time = timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP));
                    if (time == 0)
                        time = timestamp(text(cursor, DBConstants.KEY.EVENT_DATE_TIMESTAMP));
                    if (id.isEmpty() || time == 0) continue;
                    Enrollment next =
                            new Enrollment(id, time, timestamp(event.opt("version")), event);
                    Enrollment old = revisions.get(id);
                    if (old == null || next.version > old.version) revisions.put(id, next);
                } catch (JSONException ignored) {
                    // The existing enrollment row remains usable without a raw event.
                }
            }
        }
        Enrollment latest = null;
        for (Enrollment candidate : revisions.values()) {
            if (!candidate.event.optBoolean("voided")
                    && (latest == null || candidate.time > latest.time
                            || (candidate.time == latest.time
                                    && candidate.id.compareTo(latest.id) > 0)))
                latest = candidate;
        }
        return latest;
    }

    private static String familyId(SQLiteDatabase db, String motherId) {
        if (!hasTable(db, "ec_family_member")) return "";
        try (Cursor cursor = db.rawQuery("SELECT " + DBConstants.KEY.RELATIONAL_ID
                             + " FROM ec_family_member WHERE " + DBConstants.KEY.BASE_ENTITY_ID
                             + " = ? LIMIT 1",
                     new String[] {motherId})) {
            return cursor.moveToFirst() ? text(cursor, DBConstants.KEY.RELATIONAL_ID) : "";
        }
    }

    private static Visit readVisit(Cursor cursor) {
        Visit visit = new Visit();
        visit.setVisitId(text(cursor, DBConstants.KEY.VISIT_ID));
        visit.setVisitType(text(cursor, DBConstants.KEY.VISIT_TYPE));
        visit.setBaseEntityId(text(cursor, DBConstants.KEY.BASE_ENTITY_ID));
        visit.setFormSubmissionId(text(cursor, DBConstants.KEY.FORM_SUBMISSION_ID));
        visit.setJson(text(cursor, DBConstants.KEY.VISIT_JSON));
        visit.setPreProcessedJson(text(cursor, DBConstants.KEY.PRE_PROCESSED));
        visit.setDate(date(text(cursor, DBConstants.KEY.VISIT_DATE)));
        visit.setUpdatedAt(date(text(cursor, DBConstants.KEY.UPDATED_AT)));
        visit.setCreatedAt(date(text(cursor, DBConstants.KEY.CREATED_AT)));
        visit.setProcessed(true);
        return visit;
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", new String[0])) {
            return cursor.moveToFirst();
        }
    }

    private static LocalDate calendarDate(String value) {
        for (String format : new String[] {"dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy"}) {
            try {
                return DateTimeFormat.forPattern(format).withLocale(Locale.US).parseLocalDate(
                        value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static String normalizePregnancy(String value) {
        if ("bf".equals(value)) return "breast_feeding";
        if ("none".equals(value)) return "not_pregnant";
        return value;
    }

    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
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

    private static Date date(String value) {
        long time = timestamp(value);
        return time == 0 ? null : new Date(time);
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    public static final class Context {
        private final String id;
        private final String legacyId;
        private final String pregnancyStatus;
        private final String childDob;
        private final String enrollmentChildDob;
        private final String familyId;
        private final long enrollmentTime;

        public Context(String id, String legacyId, String pregnancyStatus, String childDob,
                String enrollmentChildDob, String familyId, long enrollmentTime) {
            this.id = id;
            this.legacyId = legacyId;
            this.pregnancyStatus = pregnancyStatus;
            this.childDob = childDob;
            this.enrollmentChildDob = enrollmentChildDob;
            this.familyId = familyId;
            this.enrollmentTime = enrollmentTime;
        }

        public static Context empty() {
            return new Context("", "", "", "", "", "", 0);
        }
        public String getEnrollmentId() {
            return id;
        }
        public String getEnrollmentPregnancyStatus() {
            return pregnancyStatus;
        }
        public String getChildDob() {
            return childDob;
        }
        public String getEnrollmentChildDob() {
            return enrollmentChildDob;
        }
        public String getFamilyId() {
            return familyId;
        }
        public long getEnrollmentTime() {
            return enrollmentTime;
        }
    }

    private static final class Enrollment {
        final String id;
        final long time;
        final long version;
        final JSONObject event;

        Enrollment(String id, long time, long version, JSONObject event) {
            this.id = id;
            this.time = time;
            this.version = version;
            this.event = event;
        }
    }

    private static final class Evidence {
        final Visit visit;
        final String formId;
        final LocalDate date;
        final long version;
        final boolean valid;

        Evidence(Visit visit, String formId, LocalDate date, long version, boolean valid) {
            this.visit = visit;
            this.formId = formId;
            this.date = date;
            this.version = version;
            this.valid = valid;
        }
    }
}
