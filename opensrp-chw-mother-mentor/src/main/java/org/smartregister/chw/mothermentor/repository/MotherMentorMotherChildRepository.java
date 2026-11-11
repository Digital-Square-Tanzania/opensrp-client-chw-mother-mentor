package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public static boolean isChildEidVisit(Visit visit) {
        return visit != null && org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType
                .CHILD_EID.getEventType().equals(visit.getVisitType());
    }

    /** Repository and sync hooks call this after storing the canonical visit. */
    public static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (visit == null || StringUtils.isBlank(visit.getBaseEntityId())) return;
        if (org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.CHILD_EID
                .getEventType().equals(visit.getVisitType())) {
            rebuildForChild(db, visit.getBaseEntityId());
        } else if (MotherMentorMotherDeliveryRepository.isMotherVisit(visit)) {
            rebuild(db, visit.getBaseEntityId());
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
        return getState(db, motherId, excludedVisitId, LocalDate.now());
    }

    public static State getState(SQLiteDatabase db, String motherId, String excludedVisitId,
            LocalDate assessmentDate) {
        return getState(db, motherId, excludedVisitId, assessmentDate, null);
    }

    private static State getState(SQLiteDatabase db, String motherId, String excludedVisitId,
            LocalDate assessmentDate, Visit before) {
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        State state = new State(MotherMentorHeiSchedule.parseBirthDate(context.getChildDob()));
        state.originalStatus = context.getEnrollmentPregnancyStatus();
        state.enrollmentChildDob = MotherMentorHeiSchedule.parseDate(context.getEnrollmentChildDob());
        state.childBaseEntityId = resolveChild(db, motherId, state.childDob);
        if (state.childBaseEntityId.isEmpty()) {
            state.childBaseEntityId = MotherMentorChildEidDao.resolveChild(db, motherId, state.childDob);
        }
        Map<String, String> registered = MotherMentorChildEidDao.person(db, state.childBaseEntityId);
        LocalDate registeredDob = MotherMentorHeiSchedule.parseBirthDate(registered.get(DBConstants.KEY.DOB));
        if (registeredDob != null && !registeredDob.equals(state.childDob)) state.childBaseEntityId = "";
        if ("pregnant".equals(state.originalStatus)) {
            LocalDate delivery = MotherMentorMotherDeliveryRepository.getDeliveryDate(db, motherId, excludedVisitId);
            if (delivery == null || delivery.isAfter(assessmentDate)) state.childBaseEntityId = "";
        }
        if ("breast_feeding".equals(state.originalStatus)) state.breastfeedingStatus = "breastfeeding";
        else if ("not_pregnant".equals(state.originalStatus)) state.breastfeedingStatus = "not_breastfeeding";
        if (context.getEnrollmentId().isEmpty()) return state;
        if (before == null && !state.childBaseEntityId.isEmpty()) {
            loadTracingContext(db, state, context, assessmentDate);
        }
        List<Evidence> evidence = new ArrayList<>();
        for (Visit visit : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, excludedVisitId)) {
            Evidence item = new Evidence(visit, false);
            if (item.date != null && !item.date.isAfter(assessmentDate)
                    && (before == null || MotherMentorChildEidDao.compareChronology(visit, before) < 0)) evidence.add(item);
        }
        evidence.addAll(childEvidence(db, state.childBaseEntityId, context, excludedVisitId, assessmentDate, before));
        evidence.addAll(enrollmentEvidence(db, state.childBaseEntityId, state.childDob, assessmentDate, before));
        if (before == null && assessmentDate.equals(LocalDate.now())
                && MotherMentorChildEidDao.enrollmentHistory(db, state.childBaseEntityId, assessmentDate).isEmpty()) {
            Map<String, String> enrollment = MotherMentorChildEidDao.enrollment(db, state.childBaseEntityId);
            seedBreastfeeding(state, enrollment, assessmentDate);
            seedLegacyTest(state, enrollment, null);
        }
        evidence.sort((first, second) -> MotherMentorChildEidDao.compareChronology(first.visit, second.visit));
        for (Evidence item : evidence) {
            if (item.enrollmentSample) {
                if (context.getEnrollmentTime() == 0
                        || !item.date.isBefore(new LocalDate(context.getEnrollmentTime()))) {
                    seedBreastfeeding(state, item.values, item.date);
                }
                seedLegacyTest(state, item.values, item.date);
            }
            else if (item.child && context.getEnrollmentTime() > 0
                    && item.date.isBefore(new LocalDate(context.getEnrollmentTime()))) {
                State past = new State(state.childDob);
                applyChild(past, item.values, item.date);
                state.knownPerformed.putAll(past.tests);
            } else if (item.child) applyChild(state, item.values, item.date);
            else applyMother(state, item.values, item.date);
        }
        return state;
    }

    private static void loadTracingContext(SQLiteDatabase db, State state,
            MotherMentorMotherVisitDao.Context mother, LocalDate day) {
        Map<String, String> child = MotherMentorChildEidDao.enrollment(db, state.childBaseEntityId);
        if (child.isEmpty() || !mother.getEnrollmentId().equals(child.get(DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID))) return;
        LocalDate closedOn = MotherMentorHeiSchedule.parseDate(child.get(DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA));
        if (closedOn == null || !closedOn.isAfter(day)) {
            state.tracingExcludedTests.addAll(MotherMentorChildEidRepository.suppressedTests(
                    child.get(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS)));
            MotherMentorChildEidExit exit = MotherMentorChildEidExit.fromStatus(child.get(DBConstants.KEY.MTOTO_STATUS));
            if ("1".equals(child.get(DBConstants.KEY.IS_CLOSED)) && exit != null && exit.isStrikeExit()) {
                state.tracingClosedStatus = exit.getStatus();
            }
        }
        String referral = child.get(DBConstants.KEY.MTOTO_RUFAA_STATE);
        if (!"1".equals(child.get(DBConstants.KEY.IS_CLOSED))
                && (MotherMentorChildEidReturnVisit.AWAITING_RESULT.equals(referral)
                    || MotherMentorChildEidReturnVisit.AWAITING_CONFIRM.equals(referral))) {
            Stage pending = Stage.fromCode(child.get(DBConstants.KEY.MTOTO_RUFAA_TEST));
            if (pending != null) state.tracingExcludedTests.add(pending);
        }
    }

    /** Register/card and form readers share one clinical history without creating a child. */
    public static State getStateForChild(SQLiteDatabase db, String childId, String excludedVisitId) {
        return getStateForChild(db, childId, excludedVisitId, LocalDate.now());
    }

    public static State getStateForChild(SQLiteDatabase db, String childId, String excludedVisitId,
            LocalDate assessmentDate) {
        return getStateForChild(db, childId, excludedVisitId, assessmentDate, null);
    }

    public static State getStateForChildBeforeVisit(SQLiteDatabase db, String childId, String visitId) {
        Visit visit = MotherMentorChildEidDao.findVisit(db, visitId);
        if (visit == null || !childId.equals(visit.getBaseEntityId())
                || MotherMentorMotherVisitDao.getServiceDate(visit) == null) {
            throw new IllegalArgumentException("Unable to locate the Child EID visit being edited");
        }
        return getStateForChildBeforeEvidence(db, childId, visit);
    }

    public static State getStateForChildBeforeEvidence(SQLiteDatabase db, String childId, Visit before) {
        if (before == null || MotherMentorMotherVisitDao.getServiceDate(before) == null
                || !(childId.equals(before.getBaseEntityId())
                        || MotherMentorChildEidDao.motherId(db, childId).equals(before.getBaseEntityId()))) {
            throw new IllegalArgumentException("The clinical evidence does not belong to this child");
        }
        return getStateForChild(db, childId, before.getVisitId(), MotherMentorMotherVisitDao.getServiceDate(before), before);
    }

    private static State getStateForChild(SQLiteDatabase db, String childId, String excludedVisitId,
            LocalDate assessmentDate, Visit before) {
        Map<String, String> person = MotherMentorChildEidDao.person(db, childId);
        Map<String, String> enrollment = MotherMentorChildEidDao.enrollment(db, childId);
        Map<String, String> beforeValues = before == null ? Collections.emptyMap()
                : MotherMentorVisitCompletion.observationValues(MotherMentorChildEidDao.json(before).optJSONArray("obs"));
        MotherMentorChildEidDao.Enrollment episode = MotherMentorChildEidDao.latestEnrollment(
                db, childId, assessmentDate, beforeValues.get(DBConstants.KEY.MTOTO_ENROLLMENT_ID));
        if (before != null && !episode.id.isEmpty()) {
            Map<String, String> original = MotherMentorVisitCompletion.observationValues(episode.json.optJSONArray("obs"));
            enrollment = new LinkedHashMap<>(enrollment);
            enrollment.putAll(original);
        }
        LocalDate dob = MotherMentorHeiSchedule.parseBirthDate(person.get(DBConstants.KEY.DOB));
        if (dob == null) dob = MotherMentorHeiSchedule.parseBirthDate(enrollment.get(DBConstants.KEY.CHILD_DOB));
        String motherId = value(enrollment, DBConstants.KEY.MOTHER_BASE_ENTITY_ID);
        if (motherId.isEmpty()) motherId = MotherMentorChildEidDao.motherId(db, childId);
        if (!motherId.isEmpty()) {
            State shared = getState(db, motherId, excludedVisitId, assessmentDate, before);
            String motherEpisode = value(enrollment, DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID);
            boolean sameEpisode = motherEpisode.isEmpty() || motherEpisode.equals(
                    MotherMentorMotherVisitDao.getContext(db, motherId).getEnrollmentId());
            if (sameEpisode && childId.equals(shared.getChildBaseEntityId()) && dob != null && dob.equals(shared.getChildDob())) {
                State lifetime = new State(dob);
                for (Evidence item : childEvidence(db, childId, MotherMentorMotherVisitDao.Context.empty(),
                        excludedVisitId, assessmentDate, before)) applyChild(lifetime, item.values, item.date);
                for (Map.Entry<Stage, TestRecord> item : lifetime.tests.entrySet()) {
                    TestRecord current = shared.tests.get(item.getKey());
                    if (current == null || current.getServiceDate() != null && item.getValue().getServiceDate() != null
                            && item.getValue().getServiceDate().isAfter(current.getServiceDate())) {
                        shared.tests.put(item.getKey(), item.getValue());
                    }
                }
                if (lifetime.terminalDate != null) shared.terminal(lifetime.terminal, lifetime.terminalDate);
                return shared;
            }
        }
        State state = new State(dob);
        state.childBaseEntityId = childId;
        seedBreastfeeding(state, enrollment, assessmentDate);
        List<Evidence> evidence = new ArrayList<>(childEvidence(db, childId,
                MotherMentorMotherVisitDao.Context.empty(), excludedVisitId, assessmentDate, before));
        evidence.addAll(enrollmentEvidence(db, childId, dob, assessmentDate, before));
        if (before == null && assessmentDate.equals(LocalDate.now())
                && MotherMentorChildEidDao.enrollmentHistory(db, childId, assessmentDate).isEmpty()) {
            seedLegacyTest(state, enrollment, null);
        }
        evidence.sort((first, second) -> MotherMentorChildEidDao.compareChronology(first.visit, second.visit));
        for (Evidence item : evidence) {
            if (item.enrollmentSample) seedLegacyTest(state, item.values, item.date);
            else applyChild(state, item.values, item.date);
        }
        return state;
    }

    private static void seedBreastfeeding(State state, Map<String, String> enrollment, LocalDate day) {
        String status = enrollment.get(DBConstants.KEY.BREASTFEEDING_STATUS);
        if ("breast_feeding".equals(status)) {
            state.breastfeedingStatus = "breastfeeding";
            state.weaningDate = null;
        } else if ("not_breast_feeding".equals(status) || "stop_breast_feeding".equals(status)) {
            state.breastfeedingStatus = "not_breastfeeding";
            LocalDate cessation = MotherMentorHeiSchedule.parseDate(enrollment.get(DBConstants.KEY.WEANING_DATE));
            if (MotherMentorHeiSchedule.validDate(cessation, state.childDob, day)) state.weaningDate = cessation;
        }
    }

    /** A known sample without a lab date blocks re-sampling but is never a dated Mama result. */
    private static void seedLegacyTest(State state, Map<String, String> enrollment, LocalDate enrollmentDay) {
        String last = value(enrollment, DBConstants.KEY.LAST_TEST);
        Stage stage = "dbs_6".equals(last) ? Stage.SIX_WEEKS
                : "dbs_9".equals(last) ? Stage.NINE_MONTHS : Stage.fromCode(last);
        if (stage == null || state.tests.containsKey(stage)) return;
        String result = value(enrollment, DBConstants.KEY.LAST_TEST_RESULT);
        if ("waiting".equals(result) || "no".equals(value(enrollment, DBConstants.KEY.LAST_TEST_RESULTS_RETURNED))) result = "pending";
        if (!Arrays.asList("negative", "positive", "pending").contains(result)) result = "unknown";
        state.knownPerformed.put(stage, new TestRecord(result, null, false, enrollmentDay));
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
        values.put(DBConstants.KEY.MAMA_HEI_TEST_HISTORY, MotherMentorMotherChildUtils.serializeTests(state.getSamplingHistory()));
        if (!org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.isComplete(values, date)
                || !MotherMentorMotherChildUtils.applies(values)) return;
        LocalDate dob = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA));
        if ("true".equals(values.get(DBConstants.KEY.MAMA_CARRIED_DELIVERY))) {
            if (!Objects.equals(state.recordedDelivery, dob)) return;
        } else if (org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.isLiveBirth(values)) {
            state.recordedDelivery = dob;
        }
        if ("true".equals(values.get(DBConstants.KEY.MAMA_CARRIED_WEANING_DATE))) {
            values.put(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, iso(state.weaningDate));
        }
        if (!MotherMentorMotherChildUtils.isComplete(values, date)) return;
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
        LocalDate recordedDob = MotherMentorHeiSchedule.parseBirthDate(values.get(DBConstants.KEY.CHILD_DOB));
        if (recordedDob != null && !recordedDob.equals(state.childDob)) return;
        if (MotherMentorChildEidStatusUtils.isSchema3(values)) {
            if (!MotherMentorChildEidStatusUtils.isStatusComplete(values, state.childDob, date)) return;
            if ("found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                    && "deceased".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))) {
                state.childStatus = "deceased";
                state.childDeathDate = MotherMentorChildEidStatusUtils.parseDate(
                        values.get(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI));
                state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_DECEASED, date);
                return;
            }
            if (!MotherMentorChildEidStatusUtils.isFoundAlive(values)
                    || !MotherMentorChildEidHeiUtils.isBreastfeedingComplete(values, state.childDob, date)) return;
            // Breastfeeding is shared clinical evidence even when an offline referral snapshot is stale.
            state.childStatus = "alive";
            state.childDeathDate = null;
            state.breastfeedingStatus = values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS);
            state.weaningDate = MotherMentorChildEidHeiUtils.cessation(values);
            String before = values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE);
            if (MotherMentorChildEidReturnVisit.applies(values)) {
                MotherMentorChildEidReturnVisit.Result result = MotherMentorChildEidReturnVisit.evaluate(
                        values, state.childDob, date, state.getSamplingHistory(), before);
                if (result != null && result.stage != null) {
                    state.tests.put(result.stage, new TestRecord(result.result, result.testDate,
                            result.outcome == MotherMentorChildEidReturnVisit.Outcome.CONFIRMED_POSITIVE, date));
                }
                return;
            }
            if (!MotherMentorChildEidHeiUtils.isComplete(values, state.childDob, date,
                    state.getSamplingHistory(), before)) return;
        } else if (!org.smartregister.chw.mothermentor.util.MotherMentorChildEidFollowUpVisitUtils
                .isComplete(values, true)) return;
        // Existing EID results-returned means availability, never positive confirmation.
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
            MotherMentorMotherVisitDao.Context context, String excludedVisitId, LocalDate assessmentDate, Visit before) {
        if (childId.isEmpty()) return Collections.emptyList();
        List<Evidence> result = new ArrayList<>();
        for (Visit visit : MotherMentorChildEidDao.clinicalVisits(db, childId, excludedVisitId, assessmentDate)) {
            if (before == null || MotherMentorChildEidDao.compareChronology(visit, before) < 0) {
                result.add(new Evidence(visit, true));
            }
        }
        return result;
    }

    private static List<Evidence> enrollmentEvidence(SQLiteDatabase db, String childId,
            LocalDate dob, LocalDate assessmentDate, Visit before) {
        List<Evidence> result = new ArrayList<>();
        if (childId.isEmpty() || dob == null) return result;
        for (MotherMentorChildEidDao.Enrollment enrollment : MotherMentorChildEidDao.enrollmentHistory(db, childId, assessmentDate)) {
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(enrollment.json.optJSONArray("obs"));
            LocalDate recordedDob = MotherMentorHeiSchedule.parseBirthDate(values.get(DBConstants.KEY.CHILD_DOB));
            if (recordedDob != null && !dob.equals(recordedDob) || enrollment.day().isBefore(dob)) continue;
            Visit visit = new Visit();
            visit.setVisitId(enrollment.id);
            visit.setBaseEntityId(childId);
            visit.setDate(new java.util.Date(enrollment.time));
            visit.setJson(enrollment.json.toString());
            if (before != null && MotherMentorChildEidDao.compareChronology(visit, before) >= 0) continue;
            Evidence item = new Evidence(visit, true);
            item.enrollmentSample = true;
            result.add(item);
        }
        return result;
    }

    public static final class State {
        private LocalDate childDob;
        private final Set<Stage> tracingExcludedTests = new HashSet<>();
        private String tracingClosedStatus = "";
        private LocalDate enrollmentChildDob;
        private String originalStatus = "";
        private String childBaseEntityId = "";
        private String childStatus = "";
        private String breastfeedingStatus = "";
        private LocalDate recordedDelivery;
        private LocalDate weaningDate;
        private LocalDate childDeathDate;
        private final Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        private final Map<Stage, TestRecord> knownPerformed = new LinkedHashMap<>();
        private MotherMentorMotherChildUtils.TerminalOutcome terminal = MotherMentorMotherChildUtils.TerminalOutcome.NONE;
        private LocalDate terminalDate;

        public State(LocalDate childDob) { this.childDob = childDob; this.enrollmentChildDob = childDob; }
        public LocalDate getChildDob() { return childDob; }
        public Set<Stage> getTracingExcludedTests() { return Collections.unmodifiableSet(tracingExcludedTests); }
        public String getTracingClosedStatus() { return tracingClosedStatus; }
        public String getChildBaseEntityId() { return childBaseEntityId; }
        public String getChildStatus() { return childStatus; }
        public String getBreastfeedingStatus() { return breastfeedingStatus; }
        public LocalDate getWeaningDate() { return weaningDate; }
        public LocalDate getChildDeathDate() { return childDeathDate; }
        public Map<Stage, TestRecord> getTests() { return MotherMentorHeiSchedule.immutableTests(tests); }
        public Map<Stage, TestRecord> getSamplingHistory() {
            Map<Stage, TestRecord> result = new LinkedHashMap<>(knownPerformed);
            result.putAll(tests);
            return MotherMentorHeiSchedule.immutableTests(result);
        }
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
        boolean enrollmentSample;
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
