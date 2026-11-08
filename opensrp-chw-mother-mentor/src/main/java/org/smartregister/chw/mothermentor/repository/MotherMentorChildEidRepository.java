package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;

/** Child EID enrollment metadata; entry events remain durable, synchronized source evidence. */
public final class MotherMentorChildEidRepository {
    public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MTOTO_MISSED_TEST, DBConstants.KEY.MTOTO_MISSED_DUE,
            DBConstants.KEY.MTOTO_RUFAA_STATE, DBConstants.KEY.MTOTO_RUFAA_TEST,
            DBConstants.KEY.MTOTO_RUFAA_DUE, DBConstants.KEY.MTOTO_COUNTER_AMEKATAA,
            DBConstants.KEY.MTOTO_COUNTER_NYINGINE, DBConstants.KEY.MTOTO_STATUS,
            DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA, DBConstants.KEY.MTOTO_ENROLLMENT_ID,
            DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID, DBConstants.KEY.MTOTO_ENTRY_SOURCE,
            DBConstants.KEY.MTOTO_VISIT_COUNT));

    public static final List<String> EXIT_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA, DBConstants.KEY.MTOTO_EID_STATUS,
            DBConstants.KEY.MTOTO_EID_TAREHE_YA_KUFUNGWA, DBConstants.KEY.MTOTO_EID_SABABU_YA_KUFUNGWA,
            DBConstants.KEY.MTOTO_EXIT_SOURCE, DBConstants.KEY.MTOTO_PENDING_TEST,
            DBConstants.KEY.MTOTO_PENDING_TEST_DATE, DBConstants.KEY.MTOTO_TAREHE_KUFIKA,
            DBConstants.KEY.MTOTO_SABABU_KUTOFIKA, DBConstants.KEY.MTOTO_SABABU_KUTOFIKA_OTHER,
            DBConstants.KEY.MTOTO_SABABU_KUTOPIMWA, DBConstants.KEY.MTOTO_SABABU_KUTOPIMWA_OTHER,
            DBConstants.KEY.MTOTO_KITUO_KIPYA, DBConstants.KEY.MTOTO_TAREHE_ALIFARIKI,
            DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
    private static final List<String> RETURN_PROJECTIONS = Arrays.asList(
            DBConstants.KEY.MTOTO_PENDING_TEST, DBConstants.KEY.MTOTO_PENDING_TEST_DATE,
            DBConstants.KEY.MTOTO_TAREHE_KUFIKA, DBConstants.KEY.MTOTO_SABABU_KUTOFIKA,
            DBConstants.KEY.MTOTO_SABABU_KUTOFIKA_OTHER, DBConstants.KEY.MTOTO_SABABU_KUTOPIMWA,
            DBConstants.KEY.MTOTO_SABABU_KUTOPIMWA_OTHER);
    public static final List<String> CLOSURE_PROJECTIONS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MTOTO_STATUS, DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA,
            DBConstants.KEY.MTOTO_EID_STATUS, DBConstants.KEY.MTOTO_EID_TAREHE_YA_KUFUNGWA,
            DBConstants.KEY.MTOTO_EID_SABABU_YA_KUFUNGWA, DBConstants.KEY.MTOTO_EXIT_SOURCE,
            DBConstants.KEY.MTOTO_KITUO_KIPYA, DBConstants.KEY.MTOTO_TAREHE_ALIFARIKI));
    private MotherMentorChildEidRepository() {}

    /** Explicit database-67/fresh-schema migration, after the original entry metadata exists. */
    public static void upgradeExitSchema(SQLiteDatabase db) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        Set<String> columns = MotherMentorChildEidDao.columns(db, table);
        if (!columns.containsAll(COLUMNS)) return;
        boolean ownTransaction = !db.inTransaction();
        if (ownTransaction) db.beginTransaction();
        try {
            for (String column : EXIT_COLUMNS) if (!columns.contains(column)) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column
                        + (counter(column) ? " INTEGER DEFAULT 0" : " TEXT DEFAULT ''"));
            }
            for (String childId : MotherMentorChildEidDao.enrolledChildren(db)) {
                rebuild(db, childId);
                MotherMentorMotherClosureRepository.rebuildForChild(db, childId);
            }
            if (ownTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownTransaction) db.endTransaction();
        }
    }

    /** Explicit database-66/fresh-schema migration; never invoked from database open. */
    public static void upgrade(SQLiteDatabase db) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        Set<String> columns = MotherMentorChildEidDao.columns(db, table);
        if (columns.isEmpty()) return;
        boolean transaction = !db.inTransaction();
        if (transaction) db.beginTransaction();
        try {
            for (String column : COLUMNS) if (!columns.contains(column)) {
                boolean counter = counter(column);
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column
                        + (counter ? " INTEGER DEFAULT 0" : " TEXT DEFAULT ''"));
            }
            for (String child : MotherMentorChildEidDao.enrolledChildren(db)) rebuild(db, child);
            if (transaction) db.setTransactionSuccessful();
        } finally {
            if (transaction) db.endTransaction();
        }
    }

    private static boolean counter(String key) {
        return DBConstants.KEY.MTOTO_COUNTER_AMEKATAA.equals(key)
                || DBConstants.KEY.MTOTO_COUNTER_NYINGINE.equals(key)
                || DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA.equals(key)
                || DBConstants.KEY.MTOTO_VISIT_COUNT.equals(key);
    }

    public static State getState(String childId, String excludedVisitId) {
        return getState(MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(), childId, excludedVisitId);
    }

    public static State getState(SQLiteDatabase db, String childId, String excludedVisitId) {
        return getState(db, childId, excludedVisitId, LocalDate.now());
    }

    public static State getState(SQLiteDatabase db, String childId, String excludedVisitId, LocalDate assessmentDate) {
        return state(db, childId, excludedVisitId, assessmentDate, null);
    }

    public static State getStateBeforeVisit(SQLiteDatabase db, String childId, String visitId) {
        Visit before = MotherMentorChildEidDao.findVisit(db, visitId);
        if (before == null || !childId.equals(before.getBaseEntityId())
                || MotherMentorMotherVisitDao.getServiceDate(before) == null) {
            throw new IllegalArgumentException("Unable to locate the Child EID visit being edited");
        }
        return state(db, childId, visitId, MotherMentorMotherVisitDao.getServiceDate(before), before);
    }

    private static State state(SQLiteDatabase db, String childId, String excludedVisitId,
            LocalDate assessmentDate, Visit before) {
        LocalDate day = assessmentDate == null ? LocalDate.now() : assessmentDate;
        Map<String, String> enrollment = MotherMentorChildEidDao.enrollment(db, childId);
        String taggedEpisode = before == null ? "" : value(MotherMentorVisitCompletion.observationValues(
                MotherMentorChildEidDao.json(before).optJSONArray("obs")), DBConstants.KEY.MTOTO_ENROLLMENT_ID);
        MotherMentorChildEidDao.Enrollment episode = MotherMentorChildEidDao.latestEnrollment(db, childId, day, taggedEpisode);
        if (!episode.id.isEmpty()) {
            Map<String, String> projected = enrollment;
            enrollment = new LinkedHashMap<>(projected);
            Map<String, String> original = MotherMentorVisitCompletion.observationValues(episode.json.optJSONArray("obs"));
            for (String key : COLUMNS) {
                if (DBConstants.KEY.MTOTO_STATUS.equals(key)
                        || DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA.equals(key)) continue;
                enrollment.put(key, counter(key) ? "0" : "");
                if (original.containsKey(key) && !counter(key)) enrollment.put(key, original.get(key));
            }
            enrollment.put(DBConstants.KEY.MTOTO_ENROLLMENT_ID, episode.id);
            enrollment.put(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS, value(original, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
            for (String key : Arrays.asList(DBConstants.KEY.MOTHER_BASE_ENTITY_ID, DBConstants.KEY.CHILD_DOB)) {
                if (original.containsKey(key)) enrollment.put(key, original.get(key));
            }
            if (before == null && day.equals(LocalDate.now())
                    && episode.id.equals(projected.get(DBConstants.KEY.MTOTO_ENROLLMENT_ID))) {
                for (String key : Arrays.asList(DBConstants.KEY.MTOTO_MISSED_TEST, DBConstants.KEY.MTOTO_MISSED_DUE)) {
                    if (StringUtils.isNotBlank(projected.get(key))) enrollment.put(key, projected.get(key));
                }
            }
        }
        if (!episode.id.isEmpty()) {
            enrollment.put(DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA, "0");
            for (String field : RETURN_PROJECTIONS) enrollment.put(field, "");
        }
        State state = new State(childId, enrollment, episode,
                before == null ? getClinicalState(db, childId, excludedVisitId, day)
                        : getClinicalStateBeforeVisit(db, childId, before.getVisitId()));
        List<Visit> history = new ArrayList<>(MotherMentorChildEidDao.finalizedVisits(db, childId, excludedVisitId, episode, day));
        if (before != null) history.removeIf(item -> MotherMentorChildEidDao.compareChronology(item, before) >= 0);
        state.visits = Collections.unmodifiableList(history);
        state.visitCount = history.size();
        replayReferrals(db, state);
        return state;
    }

    private static void replayReferrals(SQLiteDatabase db, State state) {
        // A legacy projection without its enrollment event is not an immutable replay baseline.
        if (state.episode.id.isEmpty()) return;
        for (Visit visit : state.visits) {
            if (state.exit != null) break;
            Map<String, String> answers = MotherMentorVisitCompletion.observationValues(
                    MotherMentorChildEidDao.json(visit).optJSONArray("obs"));
            if (!MotherMentorChildEidStatusUtils.isSchema3(answers)) continue;
            LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
            MotherMentorMotherChildRepository.State clinical = MotherMentorMotherChildRepository
                    .getStateForChildBeforeEvidence(db, state.childId, visit);
            Map<String, String> validated = new LinkedHashMap<>(answers);
            validated.put(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS, value(state.enrollment, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
            applyVisit(state, validated, clinical, day);
        }
    }

    /** Uses the same state machine for the unsaved confirmation and finalized visits. */
    public static MotherMentorChildEidExit previewExit(SQLiteDatabase db, String childId,
            String editingVisitId, Map<String, String> answers, LocalDate day) {
        if (day == null || day.isAfter(LocalDate.now())) return null;
        State state = StringUtils.isBlank(editingVisitId) ? getState(db, childId, null, day)
                : getStateBeforeVisit(db, childId, editingVisitId);
        if (state.isClosed()) return null;
        applyVisit(state, answers, state.clinical, day);
        return state.exit;
    }

    private static void applyVisit(State state, Map<String, String> answers,
            MotherMentorMotherChildRepository.State clinical, LocalDate day) {
        if (!MotherMentorChildEidStatusUtils.isStatusComplete(answers, clinical.getChildDob(), day)) return;
        String status = value(answers, DBConstants.KEY.FOLLOW_UP_STATUS);
        if ("not_found".equals(status)) {
            switch (value(answers, DBConstants.KEY.MTOTO_NOT_FOUND_REASON)) {
                case "moved_area": state.close(MotherMentorChildEidExit.E6, day, answers); break;
                case "moved_facility": state.close(MotherMentorChildEidExit.E7, day, answers); break;
                case "other":
                    if (state.increment(DBConstants.KEY.MTOTO_COUNTER_NYINGINE) >= 3) {
                        state.close(MotherMentorChildEidExit.E10, day, answers);
                    }
                    break;
                default: break;
            }
            return;
        }
        if ("deceased".equals(value(answers, DBConstants.KEY.MAMA_CHILD_STATUS))) {
            state.close(MotherMentorChildEidExit.E5, day, answers);
            return;
        }
        String before = state.getReferralState();
        if (!before.equals(value(answers, DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))) return;
        if (MotherMentorChildEidReturnVisit.applies(answers)) {
            applyReturn(state, answers, clinical, day, before);
            return;
        }
        if (!MotherMentorChildEidHeiUtils.isComplete(answers,
                clinical.getChildDob(), day, clinical.getSamplingHistory(), before)) return;
        state.enrollment.put(DBConstants.KEY.MTOTO_COUNTER_NYINGINE, "0");
        if (!"none".equals(before)) return;
        String agreement = value(answers, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT);
        if ("refused".equals(agreement)) {
            if (state.increment(DBConstants.KEY.MTOTO_COUNTER_AMEKATAA) >= 3) {
                state.close(MotherMentorChildEidExit.E8, day, answers);
            }
        } else if ("agreed".equals(agreement)) {
            state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_STATE, MotherMentorChildEidReturnVisit.REFERRED);
            state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_TEST,
                    Stage.fromCode(value(answers, DBConstants.KEY.MTOTO_RUFAA_TEST)).getMetadataCode());
            state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_DUE, value(answers, DBConstants.KEY.MTOTO_RUFAA_DUE));
            state.enrollment.put(DBConstants.KEY.MTOTO_COUNTER_AMEKATAA, "0");
        }
    }

    private static void applyReturn(State state, Map<String, String> answers,
            MotherMentorMotherChildRepository.State clinical, LocalDate day, String before) {
        if (!MotherMentorChildEidReturnVisit.REFERRED.equals(before)
                && !state.getReferralTest().isEmpty()
                && Stage.fromCode(state.getReferralTest())
                        != Stage.fromCode(value(answers, DBConstants.KEY.MTOTO_PENDING_TEST))) return;
        MotherMentorChildEidReturnVisit.Result result = MotherMentorChildEidReturnVisit.evaluate(
                answers, clinical.getChildDob(), day, clinical.getSamplingHistory(), before);
        if (result == null) return;
        state.enrollment.put(DBConstants.KEY.MTOTO_COUNTER_NYINGINE, "0");
        if (MotherMentorChildEidReturnVisit.REFERRED.equals(before)) {
            String attendance = value(answers, DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY);
            if ("yes".equals(attendance)) state.enrollment.put(DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA, "0");
            for (String key : RETURN_PROJECTIONS) state.enrollment.put(key, value(answers, key));
            state.enrollment.put(DBConstants.KEY.MTOTO_TAREHE_KUFIKA,
                    value(answers, DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE));
            if ("no".equals(attendance) && state.increment(DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA) >= 3) {
                state.close(MotherMentorChildEidExit.E9, day, answers);
            }
        }
        state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_STATE, result.nextState);
        if (result.stage != null) {
            boolean awaiting = !"none".equals(result.nextState);
            state.enrollment.put(DBConstants.KEY.MTOTO_PENDING_TEST,
                    awaiting ? result.stage.getMetadataCode() : "");
            state.enrollment.put(DBConstants.KEY.MTOTO_PENDING_TEST_DATE,
                    awaiting ? result.testDate.toString() : "");
        }
        MotherMentorChildEidExit exit = MotherMentorChildEidExit.fromReturn(result.outcome);
        if (exit != null) state.close(exit, day, answers);
        if (result.stage != null) {
            state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_TEST, result.stage.getMetadataCode());
            LocalDate due = MotherMentorHeiSchedule.dueDate(result.stage, clinical.getChildDob(),
                    MotherMentorChildEidHeiUtils.cessation(answers));
            state.enrollment.put(DBConstants.KEY.MTOTO_RUFAA_DUE, due == null ? "" : due.toString());
        }
    }

    public static MotherMentorMotherChildRepository.State getClinicalState(
            SQLiteDatabase db, String childId, String excludedVisitId) {
        return getClinicalState(db, childId, excludedVisitId, LocalDate.now());
    }

    public static MotherMentorMotherChildRepository.State getClinicalStateBeforeVisit(
            SQLiteDatabase db, String childId, String visitId) {
        return MotherMentorMotherChildRepository.getStateForChildBeforeVisit(db, childId, visitId);
    }

    public static MotherMentorMotherChildRepository.State getClinicalState(
            SQLiteDatabase db, String childId, String excludedVisitId, LocalDate assessmentDate) {
        return MotherMentorMotherChildRepository.getStateForChild(db, childId, excludedVisitId,
                assessmentDate == null ? LocalDate.now() : assessmentDate);
    }

    /** Rebuild projections only; callers enqueue/process an enrollment plan after the parent event. */
    public static void rebuild(SQLiteDatabase db, String childId) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
        if (!MotherMentorChildEidDao.columns(db, table).containsAll(COLUMNS)) return;
        if (MotherMentorChildEidDao.enrollment(db, childId).isEmpty()) return;
        State state = getState(db, childId, null);
        ContentValues values = new ContentValues();
        values.put(DBConstants.KEY.MTOTO_ENROLLMENT_ID, state.getEnrollmentId());
        values.put(DBConstants.KEY.MTOTO_VISIT_COUNT, state.getVisitCount());
        // Known episodes replay from their original enrollment, including after a correction.
        // Unknown future/legacy projections have no safe baseline and remain untouched.
        Map<String, String> existing = MotherMentorChildEidDao.enrollment(db, childId);
        if (!state.episode.id.isEmpty()) {
            values.put(DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID, state.getMotherEnrollmentId());
            for (String key : Arrays.asList(DBConstants.KEY.MTOTO_RUFAA_STATE,
                    DBConstants.KEY.MTOTO_RUFAA_TEST, DBConstants.KEY.MTOTO_RUFAA_DUE,
                    DBConstants.KEY.MTOTO_COUNTER_AMEKATAA)) values.put(key, value(state.enrollment, key));
            values.put(DBConstants.KEY.MTOTO_RUFAA_STATE, state.getReferralState());
        } else if (StringUtils.isBlank(existing.get(DBConstants.KEY.MTOTO_RUFAA_STATE))) {
            values.put(DBConstants.KEY.MTOTO_RUFAA_STATE, state.getReferralState());
        }
        if (MotherMentorChildEidDao.columns(db, table).containsAll(EXIT_COLUMNS)) {
            if (!state.episode.id.isEmpty()) {
                values.put(DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA, state.getNonAttendanceCount());
                values.put(DBConstants.KEY.MTOTO_COUNTER_NYINGINE, state.getOtherCount());
                values.put(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS, value(state.enrollment, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
                for (String key : RETURN_PROJECTIONS) values.put(key, value(state.enrollment, key));
            }
            boolean owned = MotherMentorChildEidExit.CHILD_VISIT.equals(value(existing, DBConstants.KEY.MTOTO_EXIT_SOURCE));
            boolean external = "1".equals(value(existing, DBConstants.KEY.IS_CLOSED)) && !owned;
            if (!external && state.exit == null && StringUtils.isBlank(existing.get(DBConstants.KEY.MTOTO_EID_STATUS))) {
                values.put(DBConstants.KEY.MTOTO_EID_STATUS, "active");
            }
            if (!external && (state.exit != null || owned)) {
                values.put(DBConstants.KEY.IS_CLOSED, state.exit == null ? 0 : 1);
                for (Map.Entry<String, String> field : closureProjection(state.exit, state.exitDate,
                        state.facility, state.deathDate, MotherMentorChildEidExit.CHILD_VISIT).entrySet()) {
                    values.put(field.getKey(), field.getValue());
                }
            }
        }
        if (StringUtils.isBlank(existing.get(DBConstants.KEY.MTOTO_STATUS)) && !values.containsKey(DBConstants.KEY.MTOTO_STATUS)) {
            values.put(DBConstants.KEY.MTOTO_STATUS, state.getClosedStatus());
        }
        db.update(table, values, DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {childId});
    }

    static Map<String, String> closureProjection(MotherMentorChildEidExit exit, LocalDate day,
            String facility, String deathDate, String source) {
        Map<String, String> values = new LinkedHashMap<>();
        String date = day == null ? "" : day.toString();
        String code = exit == null ? "" : exit.name();
        values.put(DBConstants.KEY.MTOTO_STATUS, exit == null ? "active" : exit.getStatus());
        values.put(DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA, date);
        values.put(DBConstants.KEY.MTOTO_EID_STATUS, exit == null ? "active" : code);
        values.put(DBConstants.KEY.MTOTO_EID_TAREHE_YA_KUFUNGWA, date);
        values.put(DBConstants.KEY.MTOTO_EID_SABABU_YA_KUFUNGWA, code);
        values.put(DBConstants.KEY.MTOTO_EXIT_SOURCE, exit == null ? "" : source);
        values.put(DBConstants.KEY.MTOTO_KITUO_KIPYA, StringUtils.defaultString(facility));
        values.put(DBConstants.KEY.MTOTO_TAREHE_ALIFARIKI, StringUtils.defaultString(deathDate));
        return values;
    }

    public static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (visit != null && MotherMentorFollowUpType.CHILD_EID.getEventType().equals(visit.getVisitType())) {
            rebuild(db, visit.getBaseEntityId());
        } else if (visit != null && Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType())) {
            rebuildForMother(db, visit.getBaseEntityId());
        }
    }

    /** A later missed test updates metadata, never the open episode's referral progress. */
    public static void rebuildForMother(SQLiteDatabase db, String motherId) {
        EnrollmentPlan latest = enrollmentPlan(db, motherId, true);
        if (latest == null || !MotherMentorChildEidDao.columns(db,
                Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID).containsAll(COLUMNS)) return;
        ContentValues values = new ContentValues();
        values.put(DBConstants.KEY.MTOTO_MISSED_TEST, latest.getMissedStage().getMetadataCode());
        values.put(DBConstants.KEY.MTOTO_MISSED_DUE, latest.getMissedDue().toString());
        db.update(Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID, values,
                DBConstants.KEY.BASE_ENTITY_ID + " = ?", new String[] {latest.getChildId()});
    }

    /** A single real identity and a qualifying saved Mama answer are required, including on sync. */
    public static EnrollmentPlan getPendingEnrollmentPlan(SQLiteDatabase db, String motherId) {
        return enrollmentPlan(db, motherId, false);
    }

    private static EnrollmentPlan enrollmentPlan(SQLiteDatabase db, String motherId, boolean updateOpen) {
        Map<String, String> mother = MotherMentorChildEidDao.motherEnrollment(db, motherId);
        if (mother.isEmpty() || "1".equals(mother.get(DBConstants.KEY.IS_CLOSED))) return null;
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        LocalDate dob = MotherMentorHeiSchedule.parseBirthDate(context.getChildDob());
        if (context.getEnrollmentId().isEmpty() || dob == null) return null;
        String childId = MotherMentorChildEidDao.resolveChild(db, motherId, dob);
        if (childId.isEmpty()) return null;
        Map<String, String> person = MotherMentorChildEidDao.person(db, childId);
        if (!MotherMentorChildEidDao.isActivePerson(person)) return null;
        Map<String, String> existing = MotherMentorChildEidDao.enrollment(db, childId);
        boolean open = !existing.isEmpty() && !"1".equals(existing.get(DBConstants.KEY.IS_CLOSED));
        if (updateOpen != open) return null;
        String linkedMother = value(existing, DBConstants.KEY.MOTHER_BASE_ENTITY_ID);
        if (!linkedMother.isEmpty() && !motherId.equals(linkedMother)) return null;
        String episode = value(existing, DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID);
        if (open && !episode.isEmpty() && !context.getEnrollmentId().equals(episode)) return null;
        Set<Stage> suppressed = context.getEnrollmentId().equals(episode)
                ? suppressedTests(value(existing, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS)) : Collections.emptySet();
        LocalDate closed = MotherMentorHeiSchedule.parseDate(existing.get(DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA));
        // Without a dated closure we cannot distinguish a new missed visit from the old episode.
        if (!open && !existing.isEmpty() && closed == null) return null;
        EnrollmentPlan latest = null;
        for (Visit visit : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, null)) {
            LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (day == null || day.isAfter(LocalDate.now()) || closed != null && !day.isAfter(closed)) continue;
            Map<String, String> answers = new LinkedHashMap<>(MotherMentorVisitCompletion.observationValues(
                    MotherMentorChildEidDao.json(visit).optJSONArray("obs")));
            if (!"no".equals(answers.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) continue;
            MotherMentorMotherChildRepository.State clinical = MotherMentorMotherChildRepository.getStateForChildBeforeEvidence(
                    db, childId, visit);
            answers.put(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS, context.getEnrollmentPregnancyStatus());
            answers.put(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB, context.getEnrollmentChildDob());
            answers.put(DBConstants.KEY.MAMA_HEI_TEST_HISTORY, MotherMentorMotherChildUtils.serializeTests(clinical.getSamplingHistory()));
            if (!MotherMentorMotherPregnancyUtils.isComplete(answers, day)
                    || !MotherMentorMotherChildUtils.applies(answers)
                    || !MotherMentorMotherChildUtils.isComplete(answers, day)
                    || !"alive".equals(answers.get(DBConstants.KEY.MAMA_CHILD_STATUS))
                    || !dob.equals(MotherMentorMotherChildUtils.birth(answers))) continue;
            LocalDate cessation = "not_breastfeeding".equals(answers.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))
                    ? MotherMentorHeiSchedule.parseDate(answers.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) : null;
            for (Stage stage : MotherMentorHeiSchedule.eligibleForSampling(dob, cessation, clinical.getSamplingHistory(), day)) {
                if (suppressed.contains(stage)) continue;
                LocalDate due = MotherMentorHeiSchedule.dueDate(stage, dob, cessation);
                if (!isPastGrace(day, due)) continue;
                String source = StringUtils.defaultIfBlank(MotherMentorChildEidDao.json(visit)
                        .optString(DBConstants.KEY.EVENT_FORM_SUBMISSION_ID), visit.getFormSubmissionId());
                if (StringUtils.isBlank(source)) continue;
                latest = new EnrollmentPlan(childId, motherId, context.getEnrollmentId(), source, day, stage,
                        due, person, MotherMentorChildEidDao.person(db, motherId), answers,
                        MotherMentorChildEidDao.json(visit), existing, visit.getDate());
                if (!updateOpen) return latest;
                break;
            }
        }
        return latest;
    }

    public static boolean isPastGrace(LocalDate serviceDate, LocalDate dueDate) {
        return serviceDate != null && dueDate != null && serviceDate.isAfter(dueDate.plusDays(14));
    }

    public static Map<String, String> observationValues(State state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DBConstants.KEY.MTOTO_ENROLLMENT_ID, state.getEnrollmentId());
        values.put(DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID, state.getMotherEnrollmentId());
        return values;
    }

    /** Tag new events; an edited historical event retains its original episode identity. */
    public static void tagEvent(Event event, State state) {
        if (event == null || state == null) return;
        for (Map.Entry<String, String> field : observationValues(state).entrySet()) {
            if (field.getValue().isEmpty()) continue;
            boolean present = false;
            if (event.getObs() != null) for (Iterator<Obs> iterator = event.getObs().iterator(); iterator.hasNext();) {
                Obs obs = iterator.next();
                if (!field.getKey().equals(obs.getFieldCode())) continue;
                if (obs.getValues() != null && !obs.getValues().isEmpty()
                        && StringUtils.isNotBlank(String.valueOf(obs.getValues().get(0)))) present = true;
                else iterator.remove();
            }
            if (!present) {
                List<Object> values = new ArrayList<>();
                values.add(field.getValue());
                event.addObs(new Obs("concept", "text", field.getKey(), "", values,
                        new ArrayList<>(), null, field.getKey()));
            }
        }
    }

    /** The host persists this specific event once and processes it after its parent has completed. */
    public static final class EnrollmentPlan {
        private final String childId;
        private final String motherId;
        private final String motherEnrollmentId;
        private final String sourceId;
        private final LocalDate serviceDate;
        private final Stage stage;
        private final LocalDate due;
        private final Map<String, String> child;
        private final Map<String, String> mother;
        private final Map<String, String> answers;
        private final JSONObject parent;
        private final Map<String, String> previous;
        private final java.util.Date recordedAt;

        EnrollmentPlan(String childId, String motherId, String motherEnrollmentId, String sourceId,
                LocalDate serviceDate, Stage stage, LocalDate due, Map<String, String> child,
                Map<String, String> mother, Map<String, String> answers, JSONObject parent,
                Map<String, String> previous, java.util.Date recordedAt) {
            this.childId = childId;
            this.motherId = motherId;
            this.motherEnrollmentId = motherEnrollmentId;
            this.sourceId = sourceId;
            this.serviceDate = serviceDate;
            this.stage = stage;
            this.due = due;
            this.child = child;
            this.mother = mother;
            this.answers = answers;
            this.parent = parent;
            this.previous = previous;
            this.recordedAt = recordedAt == null ? serviceDate.toDate() : recordedAt;
        }

        public String getChildId() { return childId; }
        public String getMotherId() { return motherId; }
        public String getMotherEnrollmentId() { return motherEnrollmentId; }
        public String getSourceId() { return sourceId; }
        public LocalDate getServiceDate() { return serviceDate; }
        public Stage getMissedStage() { return stage; }
        public LocalDate getMissedDue() { return due; }
        public String getFormSubmissionId() {
            return UUID.nameUUIDFromBytes(("mother-mentor-child-eid:" + motherEnrollmentId + ":"
                    + childId + ":" + sourceId).getBytes(StandardCharsets.UTF_8)).toString();
        }

        public boolean isPersisted(SQLiteDatabase db) {
            return getStoredEventJson(db) != null;
        }

        /** Retry the stored enrollment itself, rather than reconstructing mutable parent answers. */
        public JSONObject getStoredEventJson(SQLiteDatabase db) {
            return MotherMentorChildEidDao.storedEnrollmentEvent(db, childId, getFormSubmissionId());
        }

        public Event toEvent() {
            JSONObject metadata = new JSONObject();
            for (String key : new String[] {"providerId", "locationId", "childLocationId", "team", "teamId",
                    "clientApplicationVersion", "clientDatabaseVersion"}) {
                try { if (parent.has(key)) metadata.put(key, parent.get(key)); }
                catch (Exception ignored) { }
            }
            Event event = JsonFormUtils.gson.fromJson(metadata.toString(), Event.class);
            event.setBaseEntityId(childId);
            event.setEntityType(Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID);
            event.setEventType(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID);
            // eventDate orders actual enrollment episodes; the observation retains clinical day.
            try {
                event.setEventDate(new DateTime(parent.optString(DBConstants.KEY.EVENT_DATE_TIMESTAMP)).toDate());
            } catch (IllegalArgumentException invalidTimestamp) {
                event.setEventDate(recordedAt);
            }
            event.setFormSubmissionId(getFormSubmissionId());
            Map<String, String> fields = new LinkedHashMap<>();
            for (String column : COLUMNS) fields.put(column, counter(column) ? "0" : "");
            for (String column : EXIT_COLUMNS) fields.put(column, counter(column) ? "0" : "");
            fields.put(DBConstants.KEY.MTOTO_EID_STATUS, "active");
            if (motherEnrollmentId.equals(value(previous, DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID))) {
                fields.put(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS, value(previous, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
            }
            fields.put(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, serviceDate.toString());
            fields.put(DBConstants.KEY.MOTHER_BASE_ENTITY_ID, motherId);
            fields.put(DBConstants.KEY.RELATIONAL_ID, value(child, DBConstants.KEY.RELATIONAL_ID));
            fields.put(DBConstants.KEY.CHILD_DOB, MotherMentorHeiSchedule.parseBirthDate(value(child, DBConstants.KEY.DOB)).toString());
            fields.put(DBConstants.KEY.CHILD_NAME, name(child));
            fields.put(DBConstants.KEY.CHILD_SEX, value(child, DBConstants.KEY.GENDER));
            fields.put(DBConstants.KEY.MOTHER_NAME, name(mother));
            fields.put(DBConstants.KEY.HEI_NUMBER, value(previous, DBConstants.KEY.HEI_NUMBER));
            fields.put(DBConstants.KEY.MTOTO_ENROLLMENT_ID, getFormSubmissionId());
            fields.put(DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID, motherEnrollmentId);
            fields.put(DBConstants.KEY.MTOTO_ENTRY_SOURCE, sourceId);
            fields.put(DBConstants.KEY.MTOTO_MISSED_TEST, stage.getMetadataCode());
            fields.put(DBConstants.KEY.MTOTO_MISSED_DUE, due.toString());
            fields.put(DBConstants.KEY.MTOTO_RUFAA_STATE, "none");
            fields.put(DBConstants.KEY.MTOTO_STATUS, "active");
            fields.put(DBConstants.KEY.BREASTFEEDING_STATUS,
                    "breastfeeding".equals(answers.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))
                            ? "breast_feeding" : "stop_breast_feeding");
            fields.put(DBConstants.KEY.WEANING_DATE, value(answers, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            for (Map.Entry<String, String> field : fields.entrySet()) {
                List<Object> values = new ArrayList<>();
                values.add(field.getValue());
                event.addObs(new Obs("concept", "text", field.getKey(), "", values,
                        new ArrayList<>(), null, field.getKey()));
            }
            return event;
        }
    }

    public static Set<Stage> suppressedTests(String encoded) {
        Set<Stage> stages = new LinkedHashSet<>();
        for (String value : StringUtils.defaultString(encoded).split(",")) {
            Stage stage = Stage.fromCode(value);
            if (stage != null) stages.add(stage);
        }
        return stages;
    }

    public static String encodeStages(Set<Stage> stages) {
        List<String> codes = new ArrayList<>();
        for (Stage stage : stages) codes.add(stage.getMetadataCode());
        return StringUtils.join(codes, ",");
    }

    private static String name(Map<String, String> person) {
        return StringUtils.normalizeSpace(value(person, DBConstants.KEY.FIRST_NAME) + " "
                + value(person, DBConstants.KEY.MIDDLE_NAME) + " " + value(person, DBConstants.KEY.LAST_NAME));
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.defaultString(values.get(key));
    }

    public static final class State {
        private final String childId;
        private final Map<String, String> enrollment;
        private final MotherMentorChildEidDao.Enrollment episode;
        private final MotherMentorMotherChildRepository.State clinical;
        private int visitCount;
        private MotherMentorChildEidExit exit;
        private LocalDate exitDate;
        private String facility;
        private String deathDate;
        private List<Visit> visits = Collections.emptyList();

        State(String childId, Map<String, String> enrollment, MotherMentorChildEidDao.Enrollment episode,
                MotherMentorMotherChildRepository.State clinical) {
            this.childId = childId;
            this.enrollment = new LinkedHashMap<>(enrollment);
            this.episode = episode;
            this.clinical = clinical;
        }

        public String getChildId() { return childId; }
        public LocalDate getChildDob() { return clinical.getChildDob(); }
        public MotherMentorMotherChildRepository.State getClinicalState() { return clinical; }
        public String getMotherId() { return value(enrollment, DBConstants.KEY.MOTHER_BASE_ENTITY_ID); }
        public String getMotherEnrollmentId() { return value(enrollment, DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID); }
        public String getEnrollmentId() { return episode.id.isEmpty() ? value(enrollment, DBConstants.KEY.MTOTO_ENROLLMENT_ID) : episode.id; }
        public String getMissedTest() { return value(enrollment, DBConstants.KEY.MTOTO_MISSED_TEST); }
        public String getMissedDue() { return value(enrollment, DBConstants.KEY.MTOTO_MISSED_DUE); }
        public String getEntrySource() { return value(enrollment, DBConstants.KEY.MTOTO_ENTRY_SOURCE); }
        public String getReferralState() { return StringUtils.defaultIfBlank(value(enrollment, DBConstants.KEY.MTOTO_RUFAA_STATE), "none"); }
        public String getReferralTest() { return value(enrollment, DBConstants.KEY.MTOTO_RUFAA_TEST); }
        public String getReferralDue() { return value(enrollment, DBConstants.KEY.MTOTO_RUFAA_DUE); }
        public int getRefusedCount() { return number(DBConstants.KEY.MTOTO_COUNTER_AMEKATAA); }
        public int getOtherCount() { return number(DBConstants.KEY.MTOTO_COUNTER_NYINGINE); }
        public int getNonAttendanceCount() { return number(DBConstants.KEY.MTOTO_COUNTER_HAJAFIKA); }
        public MotherMentorChildEidExit getVisitExit() { return exit; }
        public LocalDate getVisitExitDate() { return exitDate; }
        public String getExitFacility() {
            return exit == null ? value(enrollment, DBConstants.KEY.MTOTO_KITUO_KIPYA) : StringUtils.defaultString(facility);
        }
        public boolean isClosed() {
            return exit != null || ("1".equals(value(enrollment, DBConstants.KEY.IS_CLOSED))
                    && !MotherMentorChildEidExit.CHILD_VISIT.equals(value(enrollment, DBConstants.KEY.MTOTO_EXIT_SOURCE)));
        }
        public String getClosedStatus() {
            if (exit != null) return exit.getStatus();
            String status = value(enrollment, DBConstants.KEY.MTOTO_STATUS);
            return isClosed() && (status.isEmpty() || "active".equals(status)) ? "closed"
                    : StringUtils.defaultIfBlank(status, "active");
        }
        public String getClosedDate() {
            return exitDate == null ? value(enrollment, DBConstants.KEY.MTOTO_TAREHE_YA_KUFUNGWA) : exitDate.toString();
        }

        private void close(MotherMentorChildEidExit outcome, LocalDate date, Map<String, String> values) {
            exit = outcome;
            exitDate = date;
            facility = outcome == MotherMentorChildEidExit.E6 || outcome == MotherMentorChildEidExit.E7
                    ? value(values, DBConstants.KEY.MTOTO_NEW_FACILITY) : "";
            deathDate = outcome == MotherMentorChildEidExit.E5
                    ? value(values, DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI) : "";
            if (outcome.isStrikeExit()) {
                Stage missed = Stage.fromCode(StringUtils.defaultIfBlank(
                        getSourceValues().get(DBConstants.KEY.MTOTO_MISSED_TEST),
                        StringUtils.defaultIfBlank(getMissedTest(), getReferralTest())));
                if (missed != null) {
                    Set<Stage> stages = suppressedTests(value(enrollment, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
                    stages.add(missed);
                    enrollment.put(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS, encodeStages(stages));
                }
            }
        }

        private int increment(String key) {
            int count = number(key) + 1;
            enrollment.put(key, String.valueOf(count));
            return count;
        }
        public int getVisitCount() { return visitCount; }
        public List<Visit> getVisits() { return visits; }
        public Map<String, String> getSourceValues() {
            return Collections.unmodifiableMap(MotherMentorVisitCompletion.observationValues(episode.json.optJSONArray("obs")));
        }
        public Map<String, String> getEnrollmentValues() { return Collections.unmodifiableMap(enrollment); }

        private int number(String key) {
            try { return Math.max(0, Integer.parseInt(value(enrollment, key))); }
            catch (NumberFormatException invalid) { return 0; }
        }
    }
}
