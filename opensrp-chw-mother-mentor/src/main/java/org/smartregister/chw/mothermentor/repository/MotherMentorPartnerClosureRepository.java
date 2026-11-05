package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

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

/** Rebuildable closure projections; finalized visit answers remain the source of truth. */
public final class MotherMentorPartnerClosureRepository {
    public static final List<String> COUNTER_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MWENZI_COUNTER_AMEKATAA,
            DBConstants.KEY.MWENZI_COUNTER_HAJAHUDHURIA,
            DBConstants.KEY.MWENZI_COUNTER_AMESAFIRI,
            DBConstants.KEY.MWENZI_COUNTER_NYINGINE,
            DBConstants.KEY.MWENZI_COUNTER_HAJAPATIKANA));
    public static final List<String> CLOSURE_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MWENZI_STATUS,
            DBConstants.KEY.MWENZI_TAREHE_YA_KUFUNGWA,
            DBConstants.KEY.MWENZI_SABABU_YA_KUFUNGWA,
            DBConstants.KEY.MWENZI_COUNTER_AMEKATAA,
            DBConstants.KEY.MWENZI_COUNTER_HAJAHUDHURIA,
            DBConstants.KEY.MWENZI_COUNTER_AMESAFIRI,
            DBConstants.KEY.MWENZI_COUNTER_NYINGINE,
            DBConstants.KEY.MWENZI_COUNTER_HAJAPATIKANA));

    private MotherMentorPartnerClosureRepository() {}

    /** Called by the versioned app migration, using that migration's database instance. */
    public static void upgrade(SQLiteDatabase database) {
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            ensureColumns(database, Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER);
            ensureColumns(database, Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT);
            if (!columns(database, Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER).isEmpty()) {
                try (Cursor cursor = database.rawQuery("SELECT DISTINCT "
                        + DBConstants.KEY.BASE_ENTITY_ID + " FROM "
                        + Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, new String[0])) {
                    while (cursor.moveToNext()) {
                        String partner = cursor.getString(0);
                        if (StringUtils.isNotBlank(partner)) rebuild(database, partner);
                    }
                }
            }
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
    }

    public static MotherMentorPartnerClosure getState(
            SQLiteDatabase database, String partnerId, String excludedVisitId) {
        Map<String, String> enrollment = enrollment(database, partnerId);
        return project(database, partnerId, excludedVisitId, enrollment, false);
    }

    /** Replays evidence instead of incrementing stored counters, including after edits and sync. */
    public static void rebuild(SQLiteDatabase database, String partnerId) {
        Set<String> enrollmentColumns = columns(database, Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER);
        if (!enrollmentColumns.containsAll(CLOSURE_COLUMNS)) return;
        Map<String, String> enrollment = enrollment(database, partnerId);
        if (enrollment.isEmpty()) return;
        MotherMentorPartnerClosure state = project(database, partnerId, null, enrollment, true);
        database.update(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, contentValues(state),
                DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {partnerId});
    }

    public static Map<String, String> observationValues(MotherMentorPartnerClosure state) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(DBConstants.KEY.MWENZI_STATUS, state.getStatus());
        result.put(DBConstants.KEY.MWENZI_TAREHE_YA_KUFUNGWA, state.getClosedDate());
        result.put(DBConstants.KEY.MWENZI_SABABU_YA_KUFUNGWA, state.getClosureReason());
        result.put(DBConstants.KEY.MWENZI_COUNTER_AMEKATAA, Integer.toString(state.getRefusedCount()));
        result.put(DBConstants.KEY.MWENZI_COUNTER_HAJAHUDHURIA,
                Integer.toString(state.getNonAttendanceCount()));
        result.put(DBConstants.KEY.MWENZI_COUNTER_AMESAFIRI, Integer.toString(state.getTravelledCount()));
        result.put(DBConstants.KEY.MWENZI_COUNTER_NYINGINE, Integer.toString(state.getOtherCount()));
        result.put(DBConstants.KEY.MWENZI_COUNTER_HAJAPATIKANA, Integer.toString(state.getNotFoundCount()));
        return result;
    }

    static void rebuildForVisit(SQLiteDatabase database, Visit visit) {
        if (visit != null && Boolean.TRUE.equals(visit.getProcessed())
                && isPartnerVisit(visit)) {
            rebuild(database, visit.getBaseEntityId());
        }
    }

    public static boolean isPartnerVisit(Visit visit) {
        return visit != null && (MotherMentorFollowUpType.PARTNER.getEventType()
                .equals(visit.getVisitType())
                || (Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())
                        && "PARTNER".equals(observations(visit.getJson())
                                .get(DBConstants.KEY.FOLLOW_UP_CATEGORY))));
    }

    static Set<String> affectedPartners(SQLiteDatabase database, String visitId) {
        Set<String> partners = new LinkedHashSet<>();
        if (columns(database, VisitRepository.VISIT_TABLE).isEmpty()) return partners;
        try (Cursor cursor = database.rawQuery("SELECT " + DBConstants.KEY.BASE_ENTITY_ID + ", "
                + DBConstants.KEY.VISIT_TYPE + ", " + DBConstants.KEY.VISIT_JSON + " FROM "
                + VisitRepository.VISIT_TABLE + " WHERE " + DBConstants.KEY.VISIT_ID + " = ?",
                new String[] {visitId})) {
            while (cursor.moveToNext()) {
                if (partnerLike(cursor.getString(1), observations(cursor.getString(2)))) {
                    partners.add(cursor.getString(0));
                }
            }
        }
        return partners;
    }

    private static MotherMentorPartnerClosure project(SQLiteDatabase database, String partnerId,
            String excludedVisitId, Map<String, String> enrollment, boolean storeSnapshots) {
        MotherMentorPartnerClosure state = MotherMentorPartnerClosure.active();
        if (enrollment.isEmpty()) return state;
        String projectedStatus = value(enrollment, DBConstants.KEY.MWENZI_STATUS);
        String currentLinkage = linkage(enrollment, partnerId);
        List<Evidence> visits = evidence(database, partnerId, excludedVisitId, currentLinkage);
        boolean enrollmentCanInferLegacy = projectedStatus.isEmpty()
                || MotherMentorPartnerClosure.LEGACY_COMPLETED.equals(projectedStatus);
        Set<String> followUpColumns = columns(database, Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT);
        boolean canReadSnapshots = followUpColumns.contains(DBConstants.KEY.MWENZI_STATUS);
        boolean canStoreSnapshots = storeSnapshots
                && followUpColumns.containsAll(CLOSURE_COLUMNS);
        String today = iso(new Date());
        for (Evidence visit : visits) {
            String snapshot = canReadSnapshots ? snapshotStatus(database, partnerId, visit.formId) : null;
            boolean canInferLegacy = snapshot == null ? enrollmentCanInferLegacy
                    : snapshot.isEmpty() || MotherMentorPartnerClosure.LEGACY_COMPLETED.equals(snapshot);
            state = state.advance(visit.values, visit.serviceDate);
            if (!state.isClosed() && canInferLegacy && visit.serviceDate.compareTo(today) < 0
                    && !visit.values.containsKey(DBConstants.KEY.MWENZI_STATUS)
                    && "completed".equals(visit.values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END))
                    && "attendance".equals(visit.values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                    && "yes".equals(visit.values.get(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))) {
                state = MotherMentorPartnerClosure.legacyCompleted(visit.serviceDate);
            }
            if (canStoreSnapshots && StringUtils.isNotBlank(visit.formId)) {
                database.update(Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT, contentValues(state),
                        DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.ENTITY_ID
                                + " = ?", new String[] {visit.formId, partnerId});
            }
        }
        return state;
    }

    /** A blank imported row is new evidence; an active snapshot was already audited as ongoing. */
    private static String snapshotStatus(SQLiteDatabase database, String partnerId, String formId) {
        if (StringUtils.isBlank(formId)) return null;
        try (Cursor cursor = database.rawQuery("SELECT " + DBConstants.KEY.MWENZI_STATUS + " FROM "
                + Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.ENTITY_ID
                + " = ? LIMIT 1", new String[] {formId, partnerId})) {
            return cursor.moveToFirst() ? StringUtils.trimToEmpty(cursor.getString(0)) : null;
        }
    }

    private static List<Evidence> evidence(SQLiteDatabase database, String partnerId,
            String excludedVisitId, String currentLinkage) {
        List<Evidence> candidates = new ArrayList<>();
        if (columns(database, VisitRepository.VISIT_TABLE).isEmpty()) return candidates;
        String sql = "SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.PROCESSED
                + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " IN (?, ?)";
        String today = iso(new Date());
        try (Cursor cursor = database.rawQuery(sql, new String[] {partnerId,
                MotherMentorFollowUpType.PARTNER.getEventType(),
                Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES})) {
            while (cursor.moveToNext()) {
                String json = text(cursor, DBConstants.KEY.VISIT_JSON);
                JSONObject event;
                try {
                    event = new JSONObject(json);
                } catch (JSONException ignored) {
                    event = new JSONObject();
                }
                String id = text(cursor, DBConstants.KEY.VISIT_ID);
                String formId = text(cursor, DBConstants.KEY.FORM_SUBMISSION_ID);
                if (formId.isEmpty()) formId = event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID);
                if (id.isEmpty() && formId.isEmpty()) continue;
                Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                        event.optJSONArray("obs"));
                String recorded = value(values, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
                boolean valid = partnerLike(text(cursor, DBConstants.KEY.VISIT_TYPE), values)
                        && "2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                        && currentLinkage.equals(linkage(values, partnerId))
                        && MotherMentorPartnerFollowUpVisitUtils.isCompleteSchema2(values)
                        && (recorded.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")
                                || recorded.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
                Date date = valid ? MotherMentorPartnerFollowUpVisitUtils.getVisitDate(
                        new Visit(), event, values) : null;
                valid = valid && date != null && iso(date).compareTo(today) <= 0;
                // Select the source revision before validation, so a correction can remove a strike.
                candidates.add(new Evidence(id, formId, valid ? iso(date) : "",
                        timestamp(event.opt(DBConstants.KEY.EVENT_DATE_TIMESTAMP)),
                        timestamp(event.opt("version")), json, values, valid));
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
            boolean excluded = visit.id.equals(excludedVisitId)
                    || visit.formId.equals(excludedVisitId)
                    || (!visit.formId.isEmpty() && excludedForms.contains(visit.formId));
            boolean duplicate = (!visit.formId.isEmpty() && forms.contains(visit.formId))
                    || (!visit.id.isEmpty() && ids.contains(visit.id));
            if (!visit.formId.isEmpty()) forms.add(visit.formId);
            if (!visit.id.isEmpty()) ids.add(visit.id);
            if (!excluded && !duplicate && visit.valid) result.add(visit);
        }
        result.sort(Comparator.comparing((Evidence visit) -> visit.serviceDate)
                .thenComparingLong(visit -> visit.eventTime)
                .thenComparing(visit -> visit.id).thenComparing(visit -> visit.formId));
        return result;
    }

    private static Map<String, String> enrollment(SQLiteDatabase database, String partnerId) {
        Set<String> available = columns(database, Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER);
        if (available.isEmpty() || StringUtils.isBlank(partnerId)) return Collections.emptyMap();
        String order = available.contains(DBConstants.KEY.LAST_INTERACTED_WITH)
                ? " ORDER BY " + DBConstants.KEY.LAST_INTERACTED_WITH + " DESC" : "";
        try (Cursor cursor = database.rawQuery("SELECT * FROM "
                + Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ?" + order + " LIMIT 1",
                new String[] {partnerId})) {
            if (!cursor.moveToFirst()) return Collections.emptyMap();
            Map<String, String> result = new HashMap<>();
            for (String column : cursor.getColumnNames()) {
                result.put(column, text(cursor, column));
            }
            return result;
        }
    }

    private static void ensureColumns(SQLiteDatabase database, String table) {
        Set<String> existing = columns(database, table);
        if (existing.isEmpty()) return;
        for (String column : CLOSURE_COLUMNS) {
            if (!existing.contains(column)) {
                database.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column
                        + (COUNTER_COLUMNS.contains(column) ? " INTEGER" : " VARCHAR"));
            }
        }
    }

    private static Set<String> columns(SQLiteDatabase database, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", new String[0])) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return result;
    }

    private static ContentValues contentValues(MotherMentorPartnerClosure state) {
        ContentValues values = new ContentValues();
        for (Map.Entry<String, String> value : observationValues(state).entrySet()) {
            if (COUNTER_COLUMNS.contains(value.getKey())) {
                values.put(value.getKey(), Integer.parseInt(value.getValue()));
            } else {
                values.put(value.getKey(), value.getValue());
            }
        }
        return values;
    }

    private static boolean partnerLike(String type, Map<String, String> values) {
        String category = value(values, DBConstants.KEY.FOLLOW_UP_CATEGORY);
        return "PARTNER".equals(category)
                || (category.isEmpty() && MotherMentorFollowUpType.PARTNER.getEventType().equals(type));
    }

    private static Map<String, String> observations(String json) {
        if (StringUtils.isBlank(json)) return Collections.emptyMap();
        try {
            return MotherMentorVisitCompletion.observationValues(new JSONObject(json).optJSONArray("obs"));
        } catch (JSONException ignored) {
            return Collections.emptyMap();
        }
    }

    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
    }

    private static String linkage(Map<String, String> values, String partnerId) {
        String linkage = value(values, DBConstants.KEY.PARTNER_LINKAGE_ID);
        return linkage.isEmpty() ? "legacy:" + partnerId : linkage;
    }

    private static String iso(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static long timestamp(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null || value.toString().trim().isEmpty()) return 0;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            try {
                return new DateTime(value).getMillis();
            } catch (IllegalArgumentException invalid) {
                return 0;
            }
        }
    }

    private static final class Evidence {
        private final String id;
        private final String formId;
        private final String serviceDate;
        private final long eventTime;
        private final long version;
        private final String json;
        private final Map<String, String> values;
        private final boolean valid;

        private Evidence(String id, String formId, String serviceDate, long eventTime,
                long version, String json, Map<String, String> values, boolean valid) {
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
