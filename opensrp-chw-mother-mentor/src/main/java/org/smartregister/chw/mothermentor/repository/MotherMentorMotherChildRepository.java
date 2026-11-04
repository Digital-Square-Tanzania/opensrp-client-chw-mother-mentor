package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rebuildable child assessment projection for the mother's current enrollment. */
public final class MotherMentorMotherChildRepository {
    public static final List<String> COLUMNS;
    static {
        List<String> columns = new ArrayList<>(Arrays.asList(DBConstants.KEY.MAMA_CHILD_STATUS,
                DBConstants.KEY.MAMA_BREASTFEEDING_STATUS,
                DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
                DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));
        for (Stage stage : Stage.values()) {
            columns.add(stage.getResultKey());
            columns.add(stage.getDateKey());
        }
        COLUMNS = Collections.unmodifiableList(columns);
    }

    private MotherMentorMotherChildRepository() {}

    /** Called only by the host's explicit database-64/fresh-schema migration. */
    public static void upgrade(SQLiteDatabase db) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
        if (!hasTable(db, table)) return;
        Set<String> existing = columns(db, table);
        for (String column : COLUMNS) {
            if (!existing.contains(column)) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT DEFAULT ''");
            }
        }
        try (Cursor mothers = db.rawQuery("SELECT DISTINCT " + DBConstants.KEY.BASE_ENTITY_ID
                + " FROM " + table, null)) {
            while (mothers.moveToNext()) rebuild(db, mothers.getString(0));
        }
    }

    public static void rebuild(SQLiteDatabase db, String motherId) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
        if (!hasTable(db, table) || !columns(db, table).containsAll(COLUMNS)) return;
        State state = getState(db, motherId, null);
        ContentValues projection = new ContentValues();
        for (String column : COLUMNS) projection.put(column, "");
        projection.put(DBConstants.KEY.MAMA_CHILD_STATUS, state.getChildStatus());
        projection.put(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS, state.getBreastfeedingStatus());
        projection.put(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, iso(state.getWeaningDate()));
        projection.put(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI, iso(state.getChildDeathDate()));
        projection.put(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA,
                state.hasConfirmedPositive() ? "yes" : "no");
        for (Map.Entry<Stage, TestRecord> entry : state.tests.entrySet()) {
            projection.put(entry.getKey().getResultKey(), entry.getValue().getResult());
            projection.put(entry.getKey().getDateKey(), iso(entry.getValue().getDate()));
        }
        db.update(table, projection, DBConstants.KEY.BASE_ENTITY_ID + " = ?",
                new String[] {motherId});
    }

    /** Repository and sync hooks call this after storing the canonical visit. */
    public static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (visit == null || StringUtils.isBlank(visit.getBaseEntityId())) return;
        if (org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.CHILD_EID
                .getEventType().equals(visit.getVisitType())) {
            rebuildForChild(db, visit.getBaseEntityId());
        } else if (Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
            try {
                Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                        new JSONObject(visit.getJson()).optJSONArray("obs"));
                if ("2".equals(values.get(DBConstants.KEY.MAMA_VISIT_SCHEMA))) rebuild(db, visit.getBaseEntityId());
            } catch (Exception ignored) { }
        }
    }

    /** Enrollment replay and linked child result changes can affect the mother's serial state. */
    public static void rebuildForChild(SQLiteDatabase db, String childId) {
        String children = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        if (!hasTable(db, children) || !columns(db, children).contains(DBConstants.KEY.MOTHER_BASE_ENTITY_ID)) return;
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT " + DBConstants.KEY.MOTHER_BASE_ENTITY_ID
                + " FROM " + children + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ?",
                new String[] {childId})) {
            while (cursor.moveToNext()) if (StringUtils.isNotBlank(cursor.getString(0))) rebuild(db, cursor.getString(0));
        }
    }

    public static State getState(SQLiteDatabase db, String motherId, String excludedVisitId) {
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        State state = new State(MotherMentorHeiSchedule.parseDate(context.getChildDob()));
        state.originalStatus = context.getEnrollmentPregnancyStatus();
        state.enrollmentChildDob = MotherMentorHeiSchedule.parseDate(context.getEnrollmentChildDob());
        state.childBaseEntityId = resolveChild(db, motherId, state.childDob);
        if ("breast_feeding".equals(state.originalStatus)) state.breastfeedingStatus = "breastfeeding";
        else if ("not_pregnant".equals(state.originalStatus)) state.breastfeedingStatus = "not_breastfeeding";
        if (context.getEnrollmentId().isEmpty()) return state;
        List<Evidence> evidence = new ArrayList<>();
        for (Visit visit : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, excludedVisitId)) {
            evidence.add(new Evidence(visit, false));
        }
        evidence.addAll(childEvidence(db, state.childBaseEntityId, context));
        evidence.sort(Comparator.comparing((Evidence item) -> item.date)
                .thenComparing(item -> item.visit.getVisitId()));
        for (Evidence item : evidence) {
            if (item.child) applyChild(state, item.values, item.date);
            else applyMother(state, item.values, item.date);
        }
        return state;
    }

    /** Pure replay entry point used by migration/correction tests. */
    public static State replay(LocalDate dob, List<Map<String, String>> values,
            List<LocalDate> serviceDates) {
        State state = new State(dob);
        for (int i = 0; i < values.size(); i++) applyMother(state, values.get(i), serviceDates.get(i));
        return state;
    }

    private static void applyMother(State state, Map<String, String> values, LocalDate date) {
        values = new LinkedHashMap<>(values);
        if (!state.originalStatus.isEmpty()) values.put(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS, state.originalStatus);
        values.put(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB, iso(state.enrollmentChildDob));
        values.put(DBConstants.KEY.MAMA_HEI_TEST_HISTORY, MotherMentorMotherChildUtils.serializeTests(state.tests));
        if (!org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.isComplete(values, date)
                || !MotherMentorMotherChildUtils.applies(values)
                || !MotherMentorMotherChildUtils.isComplete(values, date)) return;
        LocalDate dob = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA));
        if (dob != null) state.childDob = dob;
        state.childStatus = value(values, DBConstants.KEY.MAMA_CHILD_STATUS);
        if ("deceased".equals(state.childStatus)) {
            state.childDeathDate = MotherMentorHeiSchedule.parseDate(
                    values.get(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI));
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_DECEASED, date);
            return;
        }
        state.childDeathDate = null;
        state.breastfeedingStatus = value(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS);
        state.weaningDate = "not_breastfeeding".equals(state.breastfeedingStatus)
                ? MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) : null;
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return;
        record(state, Stage.fromCode(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE)),
                values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT),
                MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_TEST_DATE)),
                "yes".equals(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA)), date);
    }

    private static void applyChild(State state, Map<String, String> values, LocalDate date) {
        // Existing EID results-returned means availability, never positive confirmation.
        if (!org.smartregister.chw.mothermentor.util.MotherMentorChildEidFollowUpVisitUtils.isComplete(values, true)) return;
        if ("eighteen_months".equals(values.get(DBConstants.KEY.CHILD_EID_TEST_TYPE))
                && "yes".equals(values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE))) {
            LocalDate testDate = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE));
            if (MotherMentorHeiSchedule.validDate(testDate, state.childDob, date)
                    && !date.isBefore(state.childDob.plusMonths(18))) {
                record(state, Stage.EIGHTEEN_MONTHS,
                        values.get(DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT), testDate,
                        "yes".equals(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA)), date);
            }
        }
        // Future linked EID forms share the same explicit test observations.
        Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE));
        LocalDate testDate = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_TEST_DATE));
        if ("yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))
                && MotherMentorHeiSchedule.validDate(testDate, state.childDob, date)) {
            record(state, stage, values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT), testDate,
                    "yes".equals(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA)), date);
        }
    }

    private static void record(State state, Stage stage, String result, LocalDate date,
            boolean confirmed, LocalDate serviceDate) {
        if (stage == null || !Arrays.asList("negative", "positive", "pending").contains(result)) return;
        state.tests.put(stage, new TestRecord(result, date, "positive".equals(result) && confirmed, serviceDate));
        if ("positive".equals(result) && confirmed) {
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_CONFIRMED_POSITIVE, serviceDate);
        } else if (stage == Stage.EIGHTEEN_MONTHS && "negative".equals(result)) {
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.GRADUATED, serviceDate);
        }
    }

    /** A mother-only form cannot disambiguate twins or duplicate child links. */
    public static String resolveChild(SQLiteDatabase db, String motherId, LocalDate dob) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        if (dob == null || !hasTable(db, table)
                || !columns(db, table).containsAll(Arrays.asList(DBConstants.KEY.MOTHER_BASE_ENTITY_ID,
                        DBConstants.KEY.CHILD_DOB, DBConstants.KEY.BASE_ENTITY_ID))) return "";
        Set<String> matches = new HashSet<>();
        try (Cursor cursor = db.rawQuery("SELECT " + DBConstants.KEY.BASE_ENTITY_ID + ", "
                + DBConstants.KEY.CHILD_DOB + " FROM " + table + " WHERE "
                + DBConstants.KEY.MOTHER_BASE_ENTITY_ID + " = ?", new String[] {motherId})) {
            while (cursor.moveToNext()) if (dob.equals(MotherMentorHeiSchedule.parseDate(cursor.getString(1)))) {
                matches.add(cursor.getString(0));
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : "";
    }

    private static List<Evidence> childEvidence(SQLiteDatabase db, String childId,
            MotherMentorMotherVisitDao.Context context) {
        if (childId.isEmpty() || !hasTable(db, VisitRepository.VISIT_TABLE)) return Collections.emptyList();
        Map<String, Evidence> latest = new HashMap<>();
        String query = "SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.PROCESSED
                + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " = ?";
        try (Cursor cursor = db.rawQuery(query, new String[] {childId,
                org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.CHILD_EID.getEventType()})) {
            while (cursor.moveToNext()) {
                Visit visit = new Visit();
                visit.setVisitId(text(cursor, DBConstants.KEY.VISIT_ID));
                visit.setJson(text(cursor, DBConstants.KEY.VISIT_JSON));
                if (StringUtils.isBlank(visit.getJson())) visit.setJson(text(cursor, DBConstants.KEY.PRE_PROCESSED));
                visit.setFormSubmissionId(text(cursor, DBConstants.KEY.FORM_SUBMISSION_ID));
                Evidence item = new Evidence(visit, true);
                JSONObject event;
                try { event = new JSONObject(visit.getJson()); } catch (Exception invalid) { event = new JSONObject(); }
                String identity = StringUtils.defaultIfBlank(event.optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID), visit.getFormSubmissionId());
                if (identity.isEmpty()) identity = visit.getVisitId();
                Evidence previous = latest.get(identity);
                if (previous == null || item.version > previous.version
                        || (item.version == previous.version && visit.getVisitId().compareTo(previous.visit.getVisitId()) > 0)) {
                    latest.put(identity, item);
                }
            }
        }
        List<Evidence> result = new ArrayList<>();
        for (Evidence item : latest.values()) {
            try {
                JSONObject event = new JSONObject(item.visit.getJson());
                if (!event.optBoolean("voided") && item.date != null && !item.date.isAfter(LocalDate.now())
                        && (context.getEnrollmentTime() == 0
                        || !item.date.isBefore(new LocalDate(context.getEnrollmentTime())))) result.add(item);
            } catch (Exception ignored) { }
        }
        return result;
    }

    public static final class State {
        private LocalDate childDob;
        private LocalDate enrollmentChildDob;
        private String originalStatus = "";
        private String childBaseEntityId = "";
        private String childStatus = "";
        private String breastfeedingStatus = "";
        private LocalDate weaningDate;
        private LocalDate childDeathDate;
        private final Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        private MotherMentorMotherChildUtils.TerminalOutcome terminal = MotherMentorMotherChildUtils.TerminalOutcome.NONE;
        private LocalDate terminalDate;

        public State(LocalDate childDob) { this.childDob = childDob; this.enrollmentChildDob = childDob; }
        public LocalDate getChildDob() { return childDob; }
        public String getChildBaseEntityId() { return childBaseEntityId; }
        public String getChildStatus() { return childStatus; }
        public String getBreastfeedingStatus() { return breastfeedingStatus; }
        public LocalDate getWeaningDate() { return weaningDate; }
        public LocalDate getChildDeathDate() { return childDeathDate; }
        public Map<Stage, TestRecord> getTests() { return MotherMentorHeiSchedule.immutableTests(tests); }
        public MotherMentorMotherChildUtils.TerminalOutcome getTerminalOutcome() { return terminal; }
        public LocalDate getTerminalServiceDate() { return terminalDate; }
        public boolean hasConfirmedPositive() {
            for (TestRecord test : tests.values()) if ("positive".equals(test.getResult()) && test.isConfirmed()) return true;
            return false;
        }
        public boolean hasFinalNegative() {
            TestRecord test = tests.get(Stage.EIGHTEEN_MONTHS);
            return test != null && "negative".equals(test.getResult());
        }
        private void terminal(MotherMentorMotherChildUtils.TerminalOutcome outcome, LocalDate date) {
            if (date != null && (terminalDate == null || date.isBefore(terminalDate))) {
                terminal = outcome;
                terminalDate = date;
            }
        }
    }

    private static final class Evidence {
        final Visit visit;
        final boolean child;
        final LocalDate date;
        final Map<String, String> values;
        final long version;
        Evidence(Visit visit, boolean child) {
            this.visit = visit;
            this.child = child;
            this.date = MotherMentorMotherVisitDao.getServiceDate(visit);
            JSONObject event;
            try { event = new JSONObject(visit.getJson()); } catch (Exception e) { event = new JSONObject(); }
            this.values = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            this.version = event.optLong("version", 0);
        }
    }

    private static String value(Map<String, String> values, String key) { return StringUtils.defaultString(values.get(key)); }
    private static String iso(LocalDate value) { return value == null ? "" : value.toString(); }
    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }
    private static boolean hasTable(SQLiteDatabase db, String table) {
        if (db == null) return false;
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[] {table})) { return cursor != null && cursor.moveToFirst(); }
    }
    private static Set<String> columns(SQLiteDatabase db, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) { while (cursor.moveToNext()) result.add(cursor.getString(1)); }
        return result;
    }
}
