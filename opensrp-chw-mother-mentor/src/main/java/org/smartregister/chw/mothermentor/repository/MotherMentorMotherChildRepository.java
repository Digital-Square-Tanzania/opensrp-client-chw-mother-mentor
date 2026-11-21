package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.json.JSONArray;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidDirectEnrollment;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiConfirmation;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherBirthTest;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils.TerminalOutcome;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorDeliveryChildren;
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
                DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA,
                MotherMentorDeliveryChildren.FOLLOWED_COUNT, MotherMentorDeliveryChildren.PENDING_TESTS));
        for (Stage stage : Stage.values()) {
            columns.add(stage.getResultKey());
            columns.add(stage.getDateKey());
        }
        COLUMNS = Collections.unmodifiableList(columns);
    }

    private MotherMentorMotherChildRepository() {}

    /** Add missing projection columns and rebuild them from saved events. */
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
        List<State> children = getStates(db, motherId, null, LocalDate.now());
        State state = children.get(0);
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
        int following = 0;
        JSONArray pending = new JSONArray();
        for (State child : children) {
            if (child.getTerminalServiceDate() == null) following++;
            for (MotherMentorMotherResultEvent.PendingTest test : child.getPendingMotherTests()) {
                if (!test.isAwaitingConfirmation()) {
                    pending.put(new JSONArray().put(child.getNumber()).put(test.getStage().getCode())
                            .put(test.getTestDate().toString()));
                }
            }
        }
        if (children.size() > 1) projection.put(MotherMentorDeliveryChildren.FOLLOWED_COUNT, following);
        projection.put(MotherMentorDeliveryChildren.PENDING_TESTS, pending.toString());
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
        return getStates(db, motherId, excludedVisitId, assessmentDate).get(0);
    }

    public static State getStateBeforeMotherVisit(SQLiteDatabase db, String motherId, String visitId) {
        return getStatesBeforeMotherVisit(db, motherId, visitId).get(0);
    }

    public static List<State> getStatesBeforeMotherVisit(SQLiteDatabase db, String motherId, String visitId) {
        Visit before = MotherMentorChildEidDao.findVisit(db, visitId);
        LocalDate day = before == null ? null : MotherMentorMotherVisitDao.getServiceDate(before);
        return day != null && motherId.equals(before.getBaseEntityId())
                ? getStates(db, motherId, visitId, day, before)
                : getStates(db, motherId, visitId, LocalDate.now());
    }

    public static List<State> getStates(SQLiteDatabase db, String motherId, String excludedVisitId, LocalDate day) {
        return getStates(db, motherId, excludedVisitId, day, null);
    }

    private static List<State> getStates(SQLiteDatabase db, String motherId, String excludedVisitId,
            LocalDate assessmentDate, Visit before) {
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        List<Evidence> visits = new ArrayList<>();
        int count = 1;
        boolean recordedDelivery = false;
        LocalDate dob = MotherMentorHeiSchedule.parseBirthDate(context.getChildDob());
        for (Visit visit : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, excludedVisitId)) {
            Evidence item = new Evidence(visit, false);
            if (item.date == null || item.date.isAfter(assessmentDate)
                    || before != null && MotherMentorChildEidDao.compareChronology(visit, before) >= 0) continue;
            int recorded = MotherMentorDeliveryChildren.count(item.values);
            if (recorded > 0 && MotherMentorDeliveryChildren.recordsDelivery(item.values)
                    && MotherMentorMotherPregnancyUtils.isComplete(item.values, item.date)) {
                if (recordedDelivery) continue;
                recordedDelivery = true;
                count = recorded;
                dob = MotherMentorMotherChildUtils.birth(item.values);
            }
            visits.add(item);
        }
        Map<Integer, String> links = new LinkedHashMap<>();
        Set<String> reserved = new HashSet<>();
        for (Evidence visit : visits) {
            if (!MotherMentorMotherPregnancyUtils.isFound(visit.values)
                    || !MotherMentorMotherPregnancyUtils.isComplete(visit.values, visit.date)) continue;
            for (int number = 1; number <= count; number++) {
                String link = MotherMentorDeliveryChildren.section(visit.values, number)
                        .get(MotherMentorDeliveryChildren.LINK);
                if (StringUtils.isNotBlank(link) && !links.containsKey(number) && reserved.add(link)) {
                    links.put(number, link);
                }
            }
        }
        List<State> children = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            State state = new State(dob);
            state.number = number;
            state.siblings = count;
            state.explicitLink = links.getOrDefault(number, "");
            children.add(replay(db, motherId, excludedVisitId, assessmentDate, before, context, visits, state));
        }
        return children;
    }

    private static State replay(SQLiteDatabase db, String motherId, String excludedVisitId,
            LocalDate assessmentDate, Visit before, MotherMentorMotherVisitDao.Context context,
            List<Evidence> visits, State state) {
        state.originalStatus = context.getEnrollmentPregnancyStatus();
        state.enrollmentChildDob = MotherMentorHeiSchedule.parseDate(context.getEnrollmentChildDob());
        if (state.siblings > 1) {
            if (motherId.equals(MotherMentorChildEidDao.motherId(db, state.explicitLink))) {
                state.childBaseEntityId = state.explicitLink;
            }
        } else {
            state.childBaseEntityId = resolveChild(db, motherId, state.childDob);
            if (state.childBaseEntityId.isEmpty()) {
                state.childBaseEntityId = MotherMentorChildEidDao.resolveChild(db, motherId, state.childDob);
            }
        }
        Map<String, String> registered = MotherMentorChildEidDao.person(db, state.childBaseEntityId);
        LocalDate registeredDob = MotherMentorHeiSchedule.parseBirthDate(registered.get(DBConstants.KEY.DOB));
        if (registeredDob != null && !registeredDob.equals(state.childDob)) state.childBaseEntityId = "";
        if ("pregnant".equals(state.originalStatus)) {
            LocalDate delivery = MotherMentorMotherDeliveryRepository.getDeliveryDate(db, motherId, excludedVisitId);
            if (delivery == null || delivery.isAfter(assessmentDate)) state.childBaseEntityId = "";
        }
        if ("breast_feeding".equals(state.originalStatus)) state.recordBreastfeeding("breastfeeding", null);
        else if ("not_pregnant".equals(state.originalStatus)) state.breastfeedingStatus = "not_breastfeeding";
        if (context.getEnrollmentId().isEmpty()) return state;
        if (before == null && !state.childBaseEntityId.isEmpty()) {
            loadTracingContext(db, state, context, assessmentDate);
        }
        List<Evidence> evidence = new ArrayList<>(visits);
        evidence.addAll(childEvidence(db, state.childBaseEntityId, context, excludedVisitId, assessmentDate, before));
        evidence.addAll(enrollmentEvidence(db, state.childBaseEntityId, state.childDob, assessmentDate, before));
        for (Visit visit : MotherMentorMotherResultEvent.evidence(
                db, motherId, context.getEnrollmentId(), assessmentDate)) {
            if (before == null || MotherMentorChildEidDao.compareChronology(visit, before) < 0) {
                evidence.add(new Evidence(visit, false));
            }
        }
        if (before == null && assessmentDate.equals(LocalDate.now())
                && MotherMentorChildEidDao.enrollmentHistory(db, state.childBaseEntityId, assessmentDate).isEmpty()) {
            Map<String, String> enrollment = MotherMentorChildEidDao.enrollment(db, state.childBaseEntityId);
            seedBreastfeeding(state, enrollment, assessmentDate);
            seedLegacyTest(state, enrollment, null);
        }
        evidence.sort((first, second) -> MotherMentorChildEidDao.compareChronology(first.visit, second.visit));
        Set<String> replayedMotherVisits = new HashSet<>();
        Map<String, List<Evidence>> deferredResults = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            if (item.enrollmentSample) {
                if (context.getEnrollmentTime() == 0
                        || !item.date.isBefore(new LocalDate(context.getEnrollmentTime()))) {
                    seedBreastfeeding(state, item.values, item.date);
                } else {
                    State past = new State(state.childDob);
                    seedBreastfeeding(past, item.values, item.date);
                    state.hasBreastfeedingHistory |= past.hasBreastfeedingHistory;
                }
                seedLegacyTest(state, item.values, item.date);
            }
            else if (item.child && context.getEnrollmentTime() > 0
                    && item.date.isBefore(new LocalDate(context.getEnrollmentTime()))) {
                State past = new State(state.childDob);
                applyChild(past, item.values, item.date);
                state.hasBreastfeedingHistory |= past.hasBreastfeedingHistory;
                state.knownPerformed.putAll(past.tests);
            } else if (item.child) applyChild(state, item.values, item.date);
            else if (MotherMentorMotherResultEvent.EVENT_TYPE.equals(item.visit.getVisitType())) {
                String source = MotherMentorMotherResultEvent.sampleSource(item.values);
                if (replayedMotherVisits.contains(source)) {
                    applyMotherResult(state, item);
                } else if (StringUtils.isNotBlank(source)) {
                    // A legacy visit timestamp can sort after a result recorded from that visit.
                    deferredResults.computeIfAbsent(source, ignored -> new ArrayList<>()).add(item);
                }
            } else {
                String source = MotherMentorChildEidDao.sourceIdentity(item.visit);
                applyMother(state, MotherMentorDeliveryChildren.childVisit(item.values, state.number),
                        item.date, source);
                if (state.motherTestSources.containsValue(source)) {
                    String owner = state.siblings > 1 ? state.childBaseEntityId
                            : MotherMentorChildEidDao.childForMotherSample(db, motherId, context, state.childDob, item.visit);
                    state.motherTestChildren.put(source, owner);
                }
                replayedMotherVisits.add(source);
                List<Evidence> waiting = deferredResults.remove(source);
                if (waiting != null) {
                    for (Evidence result : waiting) {
                        if (item.date.equals(result.date)) applyMotherResult(state, result);
                    }
                }
            }
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
        if (motherId.isEmpty() && !MotherMentorChildEidDirectEnrollment.SOURCE.equals(
                enrollment.get(DBConstants.KEY.MTOTO_ENTRY_SOURCE))) {
            motherId = MotherMentorChildEidDao.motherId(db, childId);
        }
        State shared = null;
        if (!motherId.isEmpty()) {
            String motherEpisode = value(enrollment, DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID);
            if (motherEpisode.isEmpty() || motherEpisode.equals(
                    MotherMentorMotherVisitDao.getContext(db, motherId).getEnrollmentId())) {
                List<State> children = getStates(db, motherId, excludedVisitId, assessmentDate, before);
                shared = children.size() == 1 ? children.get(0) : children.stream()
                        .filter(candidate -> childId.equals(candidate.getChildBaseEntityId())).findFirst().orElse(null);
            }
            if (shared != null && childId.equals(shared.getChildBaseEntityId())
                    && dob != null && dob.equals(shared.getChildDob())
                    && (shared.siblings > 1 || shared.terminalResultTime == 0
                        || childId.equals(shared.terminalResultChildId) && episode.time <= shared.terminalResultTime)) {
                State lifetime = new State(dob);
                for (Evidence item : childEvidence(db, childId, MotherMentorMotherVisitDao.Context.empty(),
                        excludedVisitId, assessmentDate, before)) applyChild(lifetime, item.values, item.date);
                shared.hasBreastfeedingHistory |= lifetime.hasBreastfeedingHistory;
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
        List<Evidence> evidence = new ArrayList<>(childEvidence(db, childId,
                MotherMentorMotherVisitDao.Context.empty(), excludedVisitId, assessmentDate, before));
        List<Evidence> enrollments = enrollmentEvidence(db, childId, dob, assessmentDate, before);
        if (enrollments.isEmpty()) seedBreastfeeding(state, enrollment, assessmentDate);
        evidence.addAll(enrollments);
        if (before == null && assessmentDate.equals(LocalDate.now())
                && MotherMentorChildEidDao.enrollmentHistory(db, childId, assessmentDate).isEmpty()) {
            seedLegacyTest(state, enrollment, null);
        }
        evidence.sort((first, second) -> MotherMentorChildEidDao.compareChronology(first.visit, second.visit));
        for (Evidence item : evidence) {
            if (item.enrollmentSample) {
                seedBreastfeeding(state, item.values, item.date);
                seedLegacyTest(state, item.values, item.date);
            } else {
                applyChild(state, item.values, item.date);
            }
        }
        if (shared != null && childId.equals(shared.terminalResultChildId)
                && episode.time <= shared.terminalResultTime && state.terminalDate == null) {
            state.terminal(shared.terminal, shared.terminalDate);
            state.terminalResultTime = shared.terminalResultTime;
            state.terminalResultChildId = childId;
        }
        return state;
    }

    private static void seedBreastfeeding(State state, Map<String, String> enrollment, LocalDate day) {
        String status = enrollment.get(DBConstants.KEY.BREASTFEEDING_STATUS);
        if ("breast_feeding".equals(status)) {
            state.recordBreastfeeding("breastfeeding", null);
        } else if (MotherMentorChildEidHeiUtils.NEVER_BREASTFED.equals(status)) {
            if (!state.hasBreastfeedingHistory) state.recordBreastfeeding(status, null);
        } else if ("not_breast_feeding".equals(status) || "stop_breast_feeding".equals(status)) {
            LocalDate cessation = MotherMentorHeiSchedule.parseDate(enrollment.get(DBConstants.KEY.WEANING_DATE));
            if (MotherMentorHeiSchedule.validDate(cessation, state.childDob, day)) {
                state.recordBreastfeeding("not_breastfeeding", cessation);
            } else if ("stop_breast_feeding".equals(status) && !state.hasBreastfeedingHistory) {
                state.recordBreastfeeding("not_breastfeeding", null);
            } else if (!state.hasBreastfeedingHistory && state.breastfeedingStatus.isEmpty()) {
                state.breastfeedingStatus = "not_breastfeeding";
            }
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
        if (MotherMentorChildEidDirectEnrollment.SOURCE.equals(value(enrollment, DBConstants.KEY.MTOTO_ENTRY_SOURCE))) {
            for (Stage completed : MotherMentorChildEidRepository.suppressedTests(
                    value(enrollment, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS))) {
                if (completed != stage) state.knownPerformed.putIfAbsent(completed,
                        new TestRecord("negative", null, false, enrollmentDay));
            }
        }
        LocalDate sample = MotherMentorHeiSchedule.parseDate(value(enrollment, DBConstants.KEY.MTOTO_ENTRY_TEST_DATE));
        state.knownPerformed.put(stage, new TestRecord(result, sample, false, enrollmentDay));
    }

    /** Pure replay entry point used by migration/correction tests. */
    public static State replay(LocalDate dob, List<Map<String, String>> values,
            List<LocalDate> serviceDates) {
        State state = new State(dob);
        for (int i = 0; i < values.size(); i++) {
            applyMother(state, MotherMentorDeliveryChildren.childVisit(values.get(i), 1), serviceDates.get(i), "");
        }
        return state;
    }

    /** A visit that ended one sibling's follow-up expires even if Mama's card stayed open. */
    public static boolean isEarlierSiblingClosingVisit(SQLiteDatabase db, Visit visit, LocalDate today) {
        if (visit == null || !Boolean.TRUE.equals(visit.getProcessed())
                || !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) return false;
        LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
        if (day == null || !day.isBefore(today)) return false;
        Map<String, String> answers = MotherMentorVisitCompletion.observationValues(
                MotherMentorChildEidDao.json(visit).optJSONArray("obs"));
        int count = MotherMentorDeliveryChildren.count(answers);
        if (count < 2 || !MotherMentorDeliveryChildren.hasSections(answers)
                || !MotherMentorMotherVisitDao.belongsToCurrentEnrollment(db, visit.getBaseEntityId(),
                        visit.getJson())) return false;
        List<State> before = getStatesBeforeMotherVisit(db, visit.getBaseEntityId(), visit.getVisitId());
        for (int i = 0; i < count; i++) {
            State child = i < before.size() ? before.get(i) : new State(null);
            if (child.getTerminalOutcome() != TerminalOutcome.NONE) continue;
            applyMother(child, MotherMentorDeliveryChildren.childVisit(answers, i + 1), day,
                    MotherMentorChildEidDao.sourceIdentity(visit));
            if (child.getTerminalOutcome() != TerminalOutcome.NONE) return true;
        }
        return false;
    }

    public static boolean allChildrenFinished(List<State> children, Map<String, String> answers, LocalDate day) {
        return closingOutcome(children, answers, day) != TerminalOutcome.NONE;
    }

    static TerminalOutcome closingOutcome(List<State> children, Map<String, String> answers, LocalDate day) {
        int count = Math.max(children.size(), MotherMentorDeliveryChildren.count(answers));
        if (count == 0) return TerminalOutcome.NONE;
        TerminalOutcome reason = TerminalOutcome.NONE;
        for (int i = 1; i <= count; i++) {
            State state = i <= children.size() ? children.get(i - 1) : new State(null);
            TerminalOutcome outcome = state.getTerminalOutcome();
            if (outcome == TerminalOutcome.NONE) {
                outcome = MotherMentorMotherChildUtils.terminalOutcome(
                        MotherMentorDeliveryChildren.childVisit(answers, i), day);
            }
            if (outcome == TerminalOutcome.NONE) return outcome;
            if (outcome.ordinal() > reason.ordinal()) reason = outcome;
        }
        return reason;
    }

    private static void applyMother(State state, Map<String, String> values, LocalDate date, String sourceId) {
        values = new LinkedHashMap<>(values);
        if (!state.originalStatus.isEmpty()) values.put(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS, state.originalStatus);
        values.put(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB, iso(state.enrollmentChildDob));
        if (!org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.isComplete(values, date)
                || !MotherMentorMotherChildUtils.applies(values)) return;
        LocalDate dob = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA));
        if ("true".equals(values.get(DBConstants.KEY.MAMA_CARRIED_DELIVERY))) {
            if (!Objects.equals(state.recordedDelivery, dob)) return;
        } else if (org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.isLiveBirth(values)) {
            state.recordedDelivery = dob;
        }
        if (state.terminalDate != null || MotherMentorDeliveryChildren.hasSections(values)
                && !MotherMentorDeliveryChildren.validSex(values.get(MotherMentorDeliveryChildren.SEX))) return;
        if ("true".equals(values.get(DBConstants.KEY.MAMA_CARRIED_WEANING_DATE))) {
            values.put(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, iso(state.weaningDate));
        }
        values.put(DBConstants.KEY.MAMA_HEI_TEST_HISTORY, MotherMentorMotherChildUtils.serializeTests(state.getSamplingHistory()));
        values.put(DBConstants.KEY.MAMA_BIRTH_TEST_ANSWERED_DOB, iso(state.birthTestAnsweredDob));
        if (!MotherMentorMotherChildUtils.isRoutineAssessmentComplete(values, date,
                state.hasBreastfeedingHistory())) return;
        if ("alive".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))) {
            applyBirthAssessment(state, values, dob, date, sourceId);
        }
        if (dob != null) state.childDob = dob;
        state.childStatus = value(values, DBConstants.KEY.MAMA_CHILD_STATUS);
        String sex = values.get(MotherMentorDeliveryChildren.SEX);
        if (state.sex.isEmpty() && MotherMentorDeliveryChildren.validSex(sex)) state.sex = sex;
        if ("stillbirth".equals(state.childStatus)) {
            state.terminal(TerminalOutcome.STILLBIRTH, date);
            return;
        }
        if ("deceased".equals(state.childStatus)) {
            state.childDeathDate = MotherMentorHeiSchedule.parseDate(
                    values.get(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI));
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_DECEASED, date);
            return;
        }
        state.childDeathDate = null;
        state.recordBreastfeeding(value(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS),
                MotherMentorChildEidHeiUtils.cessation(values));
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return;
        Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE));
        TestRecord before = state.tests.get(stage);
        MotherMentorHeiConfirmation.Outcome confirmation = MotherMentorHeiConfirmation.fromRoutineVisit(stage, values);
        record(state, stage,
                values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT),
                MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_TEST_DATE)),
                confirmation == MotherMentorHeiConfirmation.Outcome.POSITIVE, date,
                confirmation == MotherMentorHeiConfirmation.Outcome.NEGATIVE,
                MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_TEST_RECEIVED_DATE)));
        state.trackMotherTest(stage, before, sourceId);
    }

    private static void applyBirthAssessment(State state, Map<String, String> values,
            LocalDate dob, LocalDate day, String sourceId) {
        if (!MotherMentorMotherBirthTest.isRequired(values, day)
                || !MotherMentorMotherBirthTest.isComplete(values, day)) return;
        state.birthTestAnsweredDob = dob;
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_BIRTH_TEST_DONE))) return;
        String confirmation = MotherMentorMotherBirthTest.confirmationResult(values);
        String result = values.get(DBConstants.KEY.MAMA_BIRTH_TEST_RESULT);
        LocalDate sample = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_BIRTH_TEST_DATE));
        record(state, Stage.AT_BIRTH, result, sample, "positive".equals(confirmation), day,
                "negative".equals(confirmation),
                MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_BIRTH_TEST_RECEIVED_DATE)));
        state.trackMotherTest(Stage.AT_BIRTH, null, sourceId);
    }

    private static void applyMotherResult(State state, Evidence item) {
        Map<String, String> values = item.values;
        LocalDate day = item.date;
        Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MTOTO_PENDING_TEST));
        MotherMentorMotherResultEvent.PendingTest pending = state.pendingMotherTest(stage);
        if (pending == null || !pending.matches(values)) return;
        MotherMentorMotherResultEvent.Outcome result = MotherMentorMotherResultEvent.evaluate(pending, values, day);
        if (result == null) return;
        MotherMentorMotherResultEvent.Entry entry = MotherMentorMotherResultEvent.readEntry(item.visit, pending, values, day);
        state.resultHistory.add(entry);
        if (state.terminalDate != null) return;
        boolean confirmed = "positive".equals(result.confirmation);
        LocalDate received = pending.isAwaitingConfirmation() ? state.tests.get(stage).getReceivedDate()
                : entry.getAnswers().getReceivedDate();
        record(state, stage, result.result, pending.getTestDate(), confirmed, day,
                "negative".equals(result.confirmation), received);
        if (state.terminalDate != null) {
            state.terminalResultTime = entry.getRecordedAt();
            state.terminalResultChildId = entry.getSample().getChildId();
        }
        state.motherTestChildren.put(MotherMentorMotherResultEvent.sampleSource(values), entry.getSample().getChildId());
    }

    private static void applyChild(State state, Map<String, String> values, LocalDate date) {
        if (MotherMentorChildEidResultEvent.STANDALONE.equals(values.get(DBConstants.KEY.CHILD_EID_RESULT_ENTRY))) {
            MotherMentorChildEidReturnVisit.Result result =
                    MotherMentorChildEidReturnVisit.evaluateStandalone(values, date);
            if (result != null && MotherMentorChildEidReturnVisit.matchesPending(state.tests.get(result.stage),
                    values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))) {
                recordChildResult(state, result, values, date);
            }
            return;
        }
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
                    || !MotherMentorChildEidHeiUtils.isBreastfeedingComplete(values, state.childDob, date,
                            state.hasBreastfeedingHistory)) return;
            // Breastfeeding is shared clinical evidence even when an offline referral snapshot is stale.
            state.childStatus = "alive";
            state.childDeathDate = null;
            state.recordBreastfeeding(values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS),
                    MotherMentorChildEidHeiUtils.cessation(values));
            String before = values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE);
            if (MotherMentorChildEidReturnVisit.applies(values)) {
                MotherMentorChildEidReturnVisit.Result result = MotherMentorChildEidReturnVisit.evaluate(
                        values, state.childDob, date, state.getSamplingHistory(), before);
                if (result != null && result.stage != null) {
                    recordChildResult(state, result, values, date);
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
        record(state, stage, result, date, confirmed, serviceDate, false, null);
    }

    private static void recordChildResult(State state, MotherMentorChildEidReturnVisit.Result result,
            Map<String, String> values, LocalDate date) {
        boolean confirmationNegative = result.outcome == MotherMentorChildEidReturnVisit.Outcome.CONFIRMATORY_NEGATIVE;
        boolean confirmed = result.outcome == MotherMentorChildEidReturnVisit.Outcome.CONFIRMED_POSITIVE;
        LocalDate received = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MTOTO_RESULT_RECEIVED_DATE));
        TestRecord prior = state.tests.get(result.stage);
        if (received == null && prior != null && result.testDate.equals(prior.getDate())) {
            received = prior.getReceivedDate();
        }
        record(state, result.stage, result.result, result.testDate, confirmed, date, confirmationNegative, received);
    }

    private static void record(State state, Stage stage, String result, LocalDate date,
            boolean confirmed, LocalDate serviceDate, boolean confirmationNegative, LocalDate receivedDate) {
        if (stage == null || !Arrays.asList("negative", "positive", "pending").contains(result)) return;
        state.tests.put(stage, new TestRecord(result, date, "positive".equals(result) && confirmed, serviceDate,
                confirmationNegative, "pending".equals(result) ? null : receivedDate));
        if ("positive".equals(result) && confirmed) {
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_CONFIRMED_POSITIVE, serviceDate);
        } else if (stage == Stage.EIGHTEEN_MONTHS && "negative".equals(result) && !confirmationNegative) {
            state.terminal(MotherMentorMotherChildUtils.TerminalOutcome.GRADUATED, serviceDate);
        }
    }

    /** A mother-only form cannot disambiguate twins or duplicate child links. */
    public static String resolveChild(SQLiteDatabase db, String motherId, LocalDate dob) {
        Set<String> matches = MotherMentorChildEidDao.childrenBornOn(db, motherId, dob);
        return matches.size() == 1 ? matches.iterator().next() : "";
    }

    private static List<Evidence> childEvidence(SQLiteDatabase db, String childId,
            MotherMentorMotherVisitDao.Context context, String excludedVisitId, LocalDate assessmentDate, Visit before) {
        if (childId.isEmpty()) return Collections.emptyList();
        List<Evidence> result = new ArrayList<>();
        for (Visit visit : MotherMentorChildEidDao.clinicalVisits(db, childId, excludedVisitId, assessmentDate)) {
            if (before == null || MotherMentorChildEidDao.compareChronology(visit, before) < 0) {
                result.add(new Evidence(visit, true,
                        MotherMentorChildEidResultEvent.visitAnswers(db, visit)));
            }
        }
        for (Visit visit : MotherMentorChildEidResultEvent.projectedStandalone(db, childId,
                excludedVisitId, assessmentDate)) {
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
        private int number = 1;
        private int siblings = 1;
        private String sex = "";
        private String explicitLink = "";
        private final Set<Stage> tracingExcludedTests = new HashSet<>();
        private String tracingClosedStatus = "";
        private LocalDate enrollmentChildDob;
        private String originalStatus = "";
        private String childBaseEntityId = "";
        private String childStatus = "";
        private String breastfeedingStatus = "";
        private boolean hasBreastfeedingHistory;
        private LocalDate recordedDelivery;
        private LocalDate weaningDate;
        private LocalDate childDeathDate;
        private LocalDate birthTestAnsweredDob;
        private final Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        private final Map<Stage, String> motherTestSources = new LinkedHashMap<>();
        private final Map<String, String> motherTestChildren = new LinkedHashMap<>();
        private final Map<Stage, TestRecord> knownPerformed = new LinkedHashMap<>();
        private MotherMentorMotherChildUtils.TerminalOutcome terminal = MotherMentorMotherChildUtils.TerminalOutcome.NONE;
        private LocalDate terminalDate;
        private long terminalResultTime;
        private String terminalResultChildId = "";
        private final List<MotherMentorMotherResultEvent.Entry> resultHistory = new ArrayList<>();

        public State(LocalDate childDob) { this.childDob = childDob; this.enrollmentChildDob = childDob; }
        public int getNumber() { return number; }
        boolean isMultipleDelivery() { return siblings > 1; }
        public String getSex() { return sex; }
        public String getExplicitLink() { return explicitLink; }
        public LocalDate getChildDob() { return childDob; }
        public Set<Stage> getTracingExcludedTests() { return Collections.unmodifiableSet(tracingExcludedTests); }
        public String getTracingClosedStatus() { return tracingClosedStatus; }
        public String getChildBaseEntityId() { return childBaseEntityId; }
        public String getChildStatus() { return childStatus; }
        public String getBreastfeedingStatus() { return breastfeedingStatus; }
        /** Whether this child has a recorded breastfeeding or cessation answer. */
        public boolean hasBreastfeedingHistory() { return hasBreastfeedingHistory; }
        public LocalDate getWeaningDate() { return weaningDate; }
        public LocalDate getChildDeathDate() { return childDeathDate; }
        public LocalDate getBirthTestAnsweredDob() { return birthTestAnsweredDob; }
        public Map<Stage, TestRecord> getTests() { return MotherMentorHeiSchedule.immutableTests(tests); }
        public Map<Stage, TestRecord> getSamplingHistory() {
            Map<Stage, TestRecord> result = new LinkedHashMap<>(knownPerformed);
            result.putAll(tests);
            return MotherMentorHeiSchedule.immutableTests(result);
        }
        public MotherMentorMotherChildUtils.TerminalOutcome getTerminalOutcome() { return terminal; }
        public LocalDate getTerminalServiceDate() { return terminalDate; }
        long getTerminalResultTime() { return terminalResultTime; }
        String getTerminalResultChildId() { return terminalResultChildId; }
        List<MotherMentorMotherResultEvent.Entry> testResultHistory() {
            return Collections.unmodifiableList(resultHistory);
        }
        public boolean hasConfirmedPositive() {
            for (TestRecord test : tests.values()) if ("positive".equals(test.getResult()) && test.isConfirmed()) return true;
            return false;
        }
        public boolean hasFinalNegative() {
            TestRecord test = tests.get(Stage.EIGHTEEN_MONTHS);
            return test != null && "negative".equals(test.getResult()) && !test.isConfirmationNegative();
        }

        public List<MotherMentorMotherResultEvent.PendingTest> getPendingMotherTests() {
            if (terminalDate != null) return Collections.emptyList();
            List<MotherMentorMotherResultEvent.PendingTest> result = new ArrayList<>();
            for (Stage stage : Stage.values()) {
                if (tracingExcludedTests.contains(stage)) continue;
                MotherMentorMotherResultEvent.PendingTest pending = pendingMotherTest(stage);
                if (pending != null) result.add(pending);
            }
            return result;
        }

        private MotherMentorMotherResultEvent.PendingTest pendingMotherTest(Stage stage) {
            if (terminal != MotherMentorMotherChildUtils.TerminalOutcome.NONE) return null;
            String source = motherTestSources.get(stage);
            TestRecord test = tests.get(stage);
            if (source == null || test == null || !test.requiresFollowUp() || test.getDate() == null) return null;
            if (stage == Stage.EIGHTEEN_MONTHS && !"positive".equals(test.getResult())) return null;
            return new MotherMentorMotherResultEvent.PendingTest(source, stage, test.getDate(),
                    "positive".equals(test.getResult()), number, siblings > 1,
                    motherTestChildren.getOrDefault(source, ""));
        }

        private void trackMotherTest(Stage stage, TestRecord previous, String source) {
            TestRecord recorded = tests.get(stage);
            if (stage == null || recorded == null || recorded == previous
                    || recorded.getDate() == null || StringUtils.isBlank(source)) return;
            if (previous == null || !Objects.equals(previous.getDate(), recorded.getDate())
                    || !previous.requiresFollowUp()) motherTestSources.put(stage, source);
            else motherTestSources.putIfAbsent(stage, source);
        }

        private void recordBreastfeeding(String status, LocalDate cessation) {
            breastfeedingStatus = status;
            weaningDate = cessation;
            hasBreastfeedingHistory |= "breastfeeding".equals(status) || "not_breastfeeding".equals(status);
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
            this(visit, child, null);
        }

        Evidence(Visit visit, boolean child, Map<String, String> merged) {
            this.visit = visit;
            this.child = child;
            this.date = MotherMentorMotherVisitDao.getServiceDate(visit);
            JSONObject event;
            try { event = new JSONObject(visit.getJson()); } catch (Exception e) { event = new JSONObject(); }
            this.values = merged == null
                    ? MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs")) : merged;
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
