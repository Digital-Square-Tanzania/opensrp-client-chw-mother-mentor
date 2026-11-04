package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Current-episode closure projection. Raw visit and enrollment history remains unchanged. */
public final class MotherMentorMotherClosureRepository {
    public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
            MAMA_STATUS, MAMA_TAREHE_YA_KUFUNGWA, MAMA_SABABU_YA_KUFUNGWA,
            MAMA_SABABU_NYINGINE_TEXT, MAMA_KITUO_KIPYA, MAMA_COUNTER_NYINGINE));
    private static final String TABLE = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String EXTERNALLY_CLOSED = "closed";

    private MotherMentorMotherClosureRepository() {}

    /** Called only by the explicit database-65 migration and fresh database initialization. */
    public static void upgrade(SQLiteDatabase db) {
        MotherMentorMotherCascadeRepository.upgrade(db);
        List<String> columns = columns(db, TABLE);
        if (columns.isEmpty()) return;
        for (String column : COLUMNS) {
            if (!columns.contains(column)) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN "
                    + column + (MAMA_COUNTER_NYINGINE.equals(column) ? " INTEGER" : " VARCHAR"));
        }
        List<String> mothers = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT " + BASE_ENTITY_ID + " FROM " + TABLE,
                new String[0])) {
            while (cursor.moveToNext()) mothers.add(cursor.getString(0));
        }
        for (String motherId : mothers) rebuild(db, motherId);
    }

    public static MotherMentorMotherClosure getState(SQLiteDatabase db, String motherId,
            String excludedVisitId) {
        return replay(db, motherId, excludedVisitId, null, null);
    }

    /** Unsaved answers are inserted in service-date order; this method never writes anything. */
    public static MotherMentorMotherClosure preview(SQLiteDatabase db, String motherId,
            String excludedVisitId, Map<String, String> answers, LocalDate serviceDate) {
        return replay(db, motherId, excludedVisitId, answers, serviceDate);
    }

    private static MotherMentorMotherClosure replay(SQLiteDatabase db, String motherId,
            String excludedVisitId, Map<String, String> answers, LocalDate date) {
        MotherMentorMotherClosure state = MotherMentorMotherClosure.active();
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        if (context.getEnrollmentId().isEmpty()) return state;
        List<Evidence> evidence = new ArrayList<>();
        for (Visit visit : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, excludedVisitId)) {
            try {
                Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                        new JSONObject(visit.getJson()).optJSONArray("obs"));
                evidence.add(new Evidence(visit.getVisitId(), values,
                        MotherMentorMotherVisitDao.getServiceDate(visit),
                        visit.getDate() == null ? 0 : visit.getDate().getTime()));
            } catch (JSONException ignored) {
                // Malformed source events cannot close a card or advance a counter.
            }
        }
        if (answers != null && date != null && !date.isAfter(LocalDate.now())
                && (context.getEnrollmentTime() == 0
                        || !date.isBefore(new LocalDate(context.getEnrollmentTime())))) {
            Evidence preview = new Evidence("~preview", new HashMap<>(answers), date,
                    System.currentTimeMillis());
            for (Visit previous : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, null)) {
                if (StringUtils.isNotBlank(excludedVisitId)
                        && (excludedVisitId.equals(previous.getVisitId())
                                || excludedVisitId.equals(previous.getFormSubmissionId()))) {
                    preview = new Evidence(previous.getVisitId(), new HashMap<>(answers), date,
                            previous.getDate() == null ? 0 : previous.getDate().getTime());
                    break;
                }
            }
            evidence.add(preview);
        }
        MotherMentorMotherChildRepository.State child =
                MotherMentorMotherChildRepository.getState(db, motherId, excludedVisitId);
        VisitOutcome childExit = childOutcome(child.getTerminalOutcome());
        if (childExit != null && child.getTerminalServiceDate() != null) {
            evidence.add(new Evidence("~linked-child", childExit, child.getTerminalServiceDate()));
        }
        evidence.sort(Comparator.comparing((Evidence item) -> item.date)
                .thenComparingLong(item -> item.order).thenComparing(item -> item.id));
        for (Evidence item : evidence) {
            Map<String, String> values = new HashMap<>(item.values);
            if (MotherMentorMotherVisitUtils.isSchema2(values)) {
                values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, context.getEnrollmentPregnancyStatus());
                values.put(MAMA_ENROLLMENT_CHILD_DOB, context.getEnrollmentChildDob());
            }
            VisitOutcome outcome = item.outcome == null ? classify(values, item.date) : item.outcome;
            state = state.advance(outcome, item.date.toString(),
                    value(values, outcome == VisitOutcome.PREGNANCY_ENDED_OTHER
                            ? MAMA_PREGNANCY_OUTCOME_OTHER : MAMA_SABABU_NYINGINE_TEXT),
                    value(values, MAMA_KITUO_KIPYA));
        }
        // A corrected module visit can never resurrect a client whose shared record is deceased.
        String death = sharedDeath(db, motherId);
        LocalDate died = MotherMentorMotherPregnancyUtils.parseDate(death);
        return !state.isClosed() && !death.isEmpty()
                ? state.advance(VisitOutcome.DECEASED, died == null ? "" : died.toString()) : state;
    }

    /** Null means invalid/incomplete evidence, so it must not reset a valid consecutive counter. */
    public static VisitOutcome classify(Map<String, String> values, LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now())
                || !MotherMentorMotherVisitUtils.isEntryComplete(values)) return null;
        if (!MotherMentorMotherVisitUtils.isSchema2(values)) {
            String status = value(values, FOLLOW_UP_STATUS);
            if ("deceased".equals(status)) return VisitOutcome.DECEASED;
            if ("relocated".equals(status)) return VisitOutcome.MOVED_AREA;
            if ("transferred".equals(status) || "transferred_to_another_facility".equals(status)) {
                return VisitOutcome.MOVED_FACILITY;
            }
            return VisitOutcome.CONTINUE;
        }
        if ("new".equals(value(values, ATTENDANCE_TYPE))) return VisitOutcome.CONTINUE;
        if ("not_found".equals(value(values, FOLLOW_UP_STATUS))) {
            switch (value(values, MAMA_NOT_FOUND_REASON)) {
                case "deceased": return VisitOutcome.DECEASED;
                case "moved_area": return VisitOutcome.MOVED_AREA;
                case "moved_facility": return VisitOutcome.MOVED_FACILITY;
                case "other": return VisitOutcome.NOT_FOUND_OTHER;
                default: return null;
            }
        }
        if (!MotherMentorMotherPregnancyUtils.isComplete(values, date)) return null;
        switch (MotherMentorMotherPregnancyUtils.terminalOutcome(values, date)) {
            case "pregnancy_loss": return VisitOutcome.PREGNANCY_LOSS;
            case "stillbirth": return VisitOutcome.STILLBIRTH;
            case "other": return VisitOutcome.PREGNANCY_ENDED_OTHER;
            default: break;
        }
        if (!MotherMentorMotherPregnancyUtils.isChildPath(values, date)) return VisitOutcome.CONTINUE;
        if (!MotherMentorMotherChildUtils.isComplete(values, date)) return null;
        VisitOutcome child = childOutcome(MotherMentorMotherChildUtils.terminalOutcome(values, date));
        return child == null ? VisitOutcome.CONTINUE : child;
    }

    private static VisitOutcome childOutcome(MotherMentorMotherChildUtils.TerminalOutcome value) {
        switch (value) {
            case CHILD_DECEASED: return VisitOutcome.CHILD_DECEASED;
            case CHILD_CONFIRMED_POSITIVE: return VisitOutcome.CHILD_CONFIRMED_POSITIVE;
            case GRADUATED: return VisitOutcome.GRADUATED;
            default: return null;
        }
    }

    public static void rebuild(SQLiteDatabase db, String motherId) {
        if (!columns(db, TABLE).containsAll(COLUMNS)) return;
        boolean ownTransaction = !db.inTransaction();
        if (ownTransaction) db.beginTransaction();
        try {
            Map<String, String> row = enrollment(db, motherId);
            if (row.isEmpty()) return;
            MotherMentorMotherClosure state = getState(db, motherId, null);
            boolean external = "1".equals(value(row, IS_CLOSED))
                    && !isManagedStatus(value(row, MAMA_STATUS));
            ContentValues values = new ContentValues();
            for (Map.Entry<String, String> entry : observationValues(state).entrySet()) {
                values.put(entry.getKey(), entry.getValue());
            }
            if (external && !state.isClosed()) values.put(MAMA_STATUS, EXTERNALLY_CLOSED);
            values.put(IS_CLOSED, state.isClosed() || external ? 1 : 0);
            db.update(TABLE, values, BASE_ENTITY_ID + " = ?", new String[] {motherId});
            MotherMentorMotherCascadeRepository.rebuild(db, motherId, state);
            if (ownTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownTransaction) db.endTransaction();
        }
    }

    public static Map<String, String> observationValues(MotherMentorMotherClosure state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MAMA_STATUS, state.getStatus());
        values.put(MAMA_TAREHE_YA_KUFUNGWA, state.getClosedDate());
        values.put(MAMA_SABABU_YA_KUFUNGWA, state.getClosureReason());
        values.put(MAMA_SABABU_NYINGINE_TEXT, state.getOtherReason());
        values.put(MAMA_KITUO_KIPYA, state.getNewFacility());
        values.put(MAMA_COUNTER_NYINGINE, Integer.toString(state.getOtherCount()));
        return values;
    }

    public static boolean isClosed(SQLiteDatabase db, String motherId) {
        return "1".equals(value(enrollment(db, motherId), IS_CLOSED))
                || !sharedDeath(db, motherId).isEmpty() || getState(db, motherId, null).isClosed();
    }

    /** An already-open linked follow-up must also respect a finalized mother cascade. */
    public static boolean isFollowUpEnrollmentClosed(SQLiteDatabase db, String clientId,
            MotherMentorFollowUpType type) {
        if (type != MotherMentorFollowUpType.MOTHER_IIT && type != MotherMentorFollowUpType.CHILD_EID) {
            return false;
        }
        if (!sharedDeath(db, clientId).isEmpty()) return true;
        String table = type.getEnrollmentTable();
        if (!columns(db, table).contains(IS_CLOSED)) return false;
        try (Cursor cursor = db.rawQuery("SELECT " + IS_CLOSED + " FROM " + table
                + " WHERE " + BASE_ENTITY_ID + " = ?", new String[] {clientId})) {
            return cursor.moveToFirst() && "1".equals(cursor.getString(0));
        }
    }

    public static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (visit == null || !Boolean.TRUE.equals(visit.getProcessed())) return;
        if (Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
            rebuild(db, visit.getBaseEntityId());
        } else if (MotherMentorFollowUpType.CHILD_EID.getEventType().equals(visit.getVisitType())) {
            rebuildForChild(db, visit.getBaseEntityId());
        }
    }

    /** Capture before replacing/deleting a source alias, including a changed client identity. */
    static Set<String> affectedMothers(SQLiteDatabase db, String visitId) {
        Set<String> mothers = new LinkedHashSet<>();
        if (columns(db, VisitRepository.VISIT_TABLE).isEmpty()) return mothers;
        try (Cursor cursor = db.rawQuery("SELECT " + BASE_ENTITY_ID + ", " + VISIT_TYPE
                + " FROM " + VisitRepository.VISIT_TABLE + " WHERE " + VISIT_ID + " = ?",
                new String[] {visitId})) {
            while (cursor.moveToNext()) {
                if (Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(cursor.getString(1))) {
                    mothers.add(cursor.getString(0));
                } else if (MotherMentorFollowUpType.CHILD_EID.getEventType().equals(cursor.getString(1))) {
                    mothers.addAll(linkedMothers(db, cursor.getString(0)));
                }
            }
        }
        return mothers;
    }

    public static void rebuildForChild(SQLiteDatabase db, String childId) {
        for (String motherId : linkedMothers(db, childId)) rebuild(db, motherId);
    }

    /** A replayed household enrollment must retain closure from its current enrolled mother. */
    public static void rebuildForHousehold(SQLiteDatabase db, String householdId) {
        String members = "ec_family_member";
        if (StringUtils.isBlank(householdId)
                || !columns(db, members).containsAll(Arrays.asList(BASE_ENTITY_ID, RELATIONAL_ID))
                || !columns(db, TABLE).contains(BASE_ENTITY_ID)) return;
        Set<String> mothers = new LinkedHashSet<>();
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT mother." + BASE_ENTITY_ID
                + " FROM " + TABLE + " mother INNER JOIN " + members + " member ON mother."
                + BASE_ENTITY_ID + " = member." + BASE_ENTITY_ID + " WHERE member."
                + RELATIONAL_ID + " = ?", new String[] {householdId})) {
            while (cursor.moveToNext()) mothers.add(cursor.getString(0));
        }
        for (String motherId : mothers) rebuild(db, motherId);
    }

    private static Set<String> linkedMothers(SQLiteDatabase db, String childId) {
        Set<String> mothers = new LinkedHashSet<>();
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        if (!columns(db, table).contains(MOTHER_BASE_ENTITY_ID)) return mothers;
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT " + MOTHER_BASE_ENTITY_ID + " FROM "
                + table + " WHERE " + BASE_ENTITY_ID + " = ?", new String[] {childId})) {
            while (cursor.moveToNext()) {
                if (StringUtils.isNotBlank(cursor.getString(0))) mothers.add(cursor.getString(0));
            }
        }
        return mothers;
    }

    private static Map<String, String> enrollment(SQLiteDatabase db, String motherId) {
        Map<String, String> values = new HashMap<>();
        if (columns(db, TABLE).isEmpty()) return values;
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + BASE_ENTITY_ID
                + " = ? LIMIT 1", new String[] {motherId})) {
            if (cursor.moveToFirst()) for (String key : cursor.getColumnNames()) {
                values.put(key, cursor.getString(cursor.getColumnIndexOrThrow(key)));
            }
        }
        return values;
    }

    private static String sharedDeath(SQLiteDatabase db, String motherId) {
        List<String> columns = columns(db, "ec_family_member");
        if (!columns.contains(DOD) && !columns.contains(DATE_DIED)) return "";
        try (Cursor cursor = db.rawQuery("SELECT * FROM ec_family_member WHERE "
                + BASE_ENTITY_ID + " = ?", new String[] {motherId})) {
            if (!cursor.moveToFirst()) return "";
            for (String key : new String[] {DOD, DATE_DIED}) {
                int index = cursor.getColumnIndex(key);
                String value = index < 0 ? "" : StringUtils.trimToEmpty(cursor.getString(index));
                if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
            }
        }
        return "";
    }

    private static boolean isManagedStatus(String status) {
        return MotherMentorMotherClosure.ACTIVE.equals(status)
                || Arrays.asList(MotherMentorMotherClosure.DECEASED, MotherMentorMotherClosure.MOVED_AREA,
                        MotherMentorMotherClosure.MOVED_FACILITY, MotherMentorMotherClosure.NOT_FOUND_OTHER_3,
                        MotherMentorMotherClosure.PREGNANCY_LOSS, MotherMentorMotherClosure.STILLBIRTH,
                        MotherMentorMotherClosure.PREGNANCY_ENDED_OTHER, MotherMentorMotherClosure.CHILD_DECEASED,
                        MotherMentorMotherClosure.CHILD_CONFIRMED_POSITIVE, MotherMentorMotherClosure.GRADUATED)
                        .contains(status);
    }

    private static List<String> columns(SQLiteDatabase db, String table) {
        List<String> columns = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", new String[0])) {
            while (cursor.moveToNext()) columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
        return columns;
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
    }

    private static final class Evidence {
        final String id;
        final Map<String, String> values;
        final VisitOutcome outcome;
        final LocalDate date;
        final long order;

        Evidence(String id, Map<String, String> values, LocalDate date, long order) {
            this.id = id;
            this.values = values;
            this.date = date;
            this.outcome = null;
            this.order = order;
        }

        Evidence(String id, VisitOutcome outcome, LocalDate date) {
            this.id = id;
            this.values = Collections.emptyMap();
            this.date = date;
            this.outcome = outcome;
            this.order = Long.MAX_VALUE;
        }
    }
}
