package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.content.ContentValues;
import android.database.Cursor;
import org.apache.commons.lang3.StringUtils;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao.Context;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rebuildable mother delivery projection. Raw enrollment and visit evidence is never rewritten. */
public final class MotherMentorMotherDeliveryRepository {
    private static final String TABLE = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String CONTEXT_TABLE = Constants.TABLES.MOTHERMENTOR_MOTHER_CONTEXT;

    private MotherMentorMotherDeliveryRepository() {}

    /** Called explicitly by the host's database 63 upgrade and fresh-schema initialization. */
    public static void upgrade(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CONTEXT_TABLE + " (" + MAMA_ENROLLMENT_ID
                + " TEXT PRIMARY KEY, " + BASE_ENTITY_ID + " TEXT NOT NULL, "
                + MAMA_ENROLLMENT_PREGNANCY_STATUS + " TEXT, " + CHILD_DOB + " TEXT)");
        List<String> columns = columns(db);
        if (columns.isEmpty()) return;
        if (!columns.contains(MAMA_TAREHE_KUJIFUNGUA)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE + " ADD COLUMN " + MAMA_TAREHE_KUJIFUNGUA + " VARCHAR");
        }
        List<String> mothers = new ArrayList<>();
        try (Cursor rows =
                        db.rawQuery("SELECT " + BASE_ENTITY_ID + " FROM " + TABLE, new String[0])) {
            while (rows.moveToNext()) mothers.add(rows.getString(0));
        }
        for (String mother : mothers) rebuild(db, mother);
    }

    public static void rebuild(SQLiteDatabase db, String motherId) {
        if (!columns(db).contains(MAMA_TAREHE_KUJIFUNGUA)) return;
        Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        if (context.getEnrollmentId().isEmpty()) return;
        // Freeze the baseline before the first mutable projection, including legacy enrollments
        // without retained raw enrollment events. Deleting all later visits remains reversible.
        db.execSQL("INSERT OR IGNORE INTO " + CONTEXT_TABLE + " (" + MAMA_ENROLLMENT_ID + ", "
                        + BASE_ENTITY_ID + ", " + MAMA_ENROLLMENT_PREGNANCY_STATUS + ", "
                        + CHILD_DOB + ") VALUES (?, ?, ?, ?)",
                new Object[] {context.getEnrollmentId(), motherId,
                        context.getEnrollmentPregnancyStatus(), context.getEnrollmentChildDob()});
        State state = getState(db, motherId, null);
        ContentValues values = new ContentValues();
        values.put(MAMA_TAREHE_KUJIFUNGUA,
                state.getDeliveryDate() == null ? "" : state.getDeliveryDate().toString());
        values.put(CHILD_DOB, state.getChildDob());
        if (!state.getPregnancyStatus().isEmpty())
            values.put(PREGNANCY_STATUS, state.getPregnancyStatus());
        db.update(TABLE, values, BASE_ENTITY_ID + " = ?", new String[] {motherId});
    }

    /** Routine visits carry attendance, while category follow-ups share only status fields. */
    public static boolean isMotherVisit(Visit visit) {
        if (visit == null
                || !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(visit.getVisitType()))
            return false;
        try {
            return hasRoutineAttendance(MotherMentorVisitCompletion
                    .observationValues(new JSONObject(visit.getJson()).optJSONArray("obs"))
                    .get(ATTENDANCE_TYPE));
        } catch (JSONException invalid) {
            return false;
        }
    }

    public static boolean isMotherEvent(org.smartregister.domain.Event event) {
        if (event == null || !Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES.equals(event.getEventType())
                || event.getObs() == null) return false;
        for (org.smartregister.domain.Obs observation : event.getObs()) {
            if ((ATTENDANCE_TYPE.equals(observation.getFieldCode())
                    || ATTENDANCE_TYPE.equals(observation.getFormSubmissionField()))
                    && observation.getValues() != null && !observation.getValues().isEmpty()
                    && hasRoutineAttendance(String.valueOf(observation.getValues().get(0)))) return true;
        }
        return false;
    }

    private static boolean hasRoutineAttendance(String value) {
        return "new".equals(value) || "repeat".equals(value);
    }

    public static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (isMotherVisit(visit) && Boolean.TRUE.equals(visit.getProcessed())) {
            rebuild(db, visit.getBaseEntityId());
        }
    }

    /** Capture affected identities before deleting a visit ID or one of its source aliases. */
    public static Set<String> affectedMothers(SQLiteDatabase db, String visitIdOrFormId) {
        Set<String> mothers = new HashSet<>();
        if (StringUtils.isBlank(visitIdOrFormId)) return mothers;
        try (Cursor rows = db.rawQuery("SELECT " + BASE_ENTITY_ID + ", " + VISIT_JSON + " FROM "
                             + VisitRepository.VISIT_TABLE + " WHERE " + VISIT_TYPE + " = ? AND ("
                             + VISIT_ID + " = ? OR " + FORM_SUBMISSION_ID + " = ? OR "
                             + FORM_SUBMISSION_ID + " IN (SELECT " + FORM_SUBMISSION_ID + " FROM "
                             + VisitRepository.VISIT_TABLE + " WHERE " + VISIT_ID + " = ?))",
                     new String[] {Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, visitIdOrFormId,
                             visitIdOrFormId, visitIdOrFormId})) {
            while (rows.moveToNext()) {
                Visit visit = new Visit();
                visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
                visit.setJson(rows.getString(1));
                if (isMotherVisit(visit) && !rows.isNull(0)) mothers.add(rows.getString(0));
            }
        }
        return mothers;
    }

    public static LocalDate getDeliveryDate(SQLiteDatabase db, String motherId) {
        return getDeliveryDate(db, motherId, null);
    }

    public static LocalDate getDeliveryDate(
            SQLiteDatabase db, String motherId, String excludedVisitId) {
        return getState(db, motherId, excludedVisitId).getDeliveryDate();
    }

    public static State getState(SQLiteDatabase db, String motherId, String excludedVisitId) {
        Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        String original = MotherMentorMotherPregnancyUtils.normalizeStatus(
                context.getEnrollmentPregnancyStatus());
        List<Visit> allVisits = MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, null);
        String enrollmentDob = originalChildDob(context, allVisits);
        boolean enrolledWithChild =
                "breast_feeding".equals(original) || "not_pregnant".equals(original);
        LocalDate initialDelivery = enrolledWithChild
                ? MotherMentorMotherPregnancyUtils.parseDate(enrollmentDob)
                : null;
        if (initialDelivery != null && initialDelivery.isAfter(LocalDate.now()))
            initialDelivery = null;
        Map<String, String> baseline = new HashMap<>();
        baseline.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
        baseline.put(MAMA_ENROLLMENT_CHILD_DOB, enrollmentDob);
        baseline.put(VISIT_PREGNANCY_STATUS, original);
        if (initialDelivery != null)
            baseline.put(MAMA_TAREHE_KUJIFUNGUA, initialDelivery.toString());
        State state = new State(original, enrollmentDob, initialDelivery, baseline);
        if (context.getEnrollmentId().isEmpty()) return state;
        List<Visit> applicable = excludedVisitId == null || excludedVisitId.isEmpty()
                ? allVisits
                : MotherMentorMotherVisitDao.getFinalizedVisits(db, motherId, excludedVisitId);
        for (Visit visit : applicable) {
            try {
                Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                        new JSONObject(visit.getJson()).optJSONArray("obs"));

                // A confirmation in a later visit must not replace the recorded delivery.
                if ("true".equals(values.get(MAMA_CARRIED_DELIVERY))) continue;

                values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
                values.put(MAMA_ENROLLMENT_CHILD_DOB, enrollmentDob);
                LocalDate serviceDate = MotherMentorMotherVisitDao.getServiceDate(visit);
                if (!MotherMentorMotherPregnancyUtils.isFound(values)
                        || !MotherMentorMotherPregnancyUtils.isComplete(values, serviceDate))
                    continue;
                String status = MotherMentorMotherPregnancyUtils.currentStatus(values);
                String outcome = values.get(MAMA_PREGNANCY_OUTCOME);
                boolean deliveryRecorded = enrolledWithChild
                        || ("not_pregnant".equals(values.get(VISIT_PREGNANCY_STATUS))
                                && ("delivered".equals(outcome) || "preterm".equals(outcome)
                                        || "stillbirth".equals(outcome)));
                LocalDate delivery = deliveryRecorded
                        ? MotherMentorMotherPregnancyUtils.deliveryDate(values)
                        : null;
                String childDob = MotherMentorMotherPregnancyUtils.isLiveBirth(values)
                        ? delivery.toString()
                        : enrollmentDob;
                state = new State(status, childDob, delivery, values);
            } catch (JSONException ignored) {
                // Malformed source events do not change the last valid clinical state.
            }
        }
        return state;
    }

    private static String originalChildDob(Context context, List<Visit> visits) {
        if (context.getEnrollmentId().startsWith("legacy:")) {
            for (Visit visit : visits) {
                try {
                    Map<String, String> recorded = MotherMentorVisitCompletion.observationValues(
                            new JSONObject(visit.getJson()).optJSONArray("obs"));
                    if (recorded.containsKey(MAMA_ENROLLMENT_CHILD_DOB)
                            && context.getEnrollmentPregnancyStatus().equals(
                                    MotherMentorMotherPregnancyUtils.normalizeStatus(
                                            recorded.get(MAMA_ENROLLMENT_PREGNANCY_STATUS)))) {
                        return recorded.get(MAMA_ENROLLMENT_CHILD_DOB);
                    }
                } catch (JSONException ignored) {
                    // Invalid raw evidence cannot replace an original context snapshot.
                }
            }
        }
        return context.getEnrollmentChildDob();
    }

    private static List<String> columns(SQLiteDatabase db) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE + ")", new String[0])) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return result;
    }

    public static final class State {
        private final String pregnancyStatus;
        private final String childDob;
        private final LocalDate deliveryDate;
        private final Map<String, String> answers;

        private State(String pregnancyStatus, String childDob, LocalDate deliveryDate,
                Map<String, String> values) {
            this.pregnancyStatus = pregnancyStatus;
            this.childDob = childDob;
            this.deliveryDate = deliveryDate;
            Map<String, String> recorded = new HashMap<>();
            for (String key : new String[] {VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME,
                         MAMA_PREGNANCY_OUTCOME_OTHER, MAMA_TAREHE_KUJIFUNGUA,
                         MAMA_PREGNANCY_LOSS_DATE, MAMA_ENROLLMENT_PREGNANCY_STATUS,
                         MAMA_ENROLLMENT_CHILD_DOB}) {
                if (values.containsKey(key)) recorded.put(key, values.get(key));
            }
            this.answers = Collections.unmodifiableMap(recorded);
        }

        public String getPregnancyStatus() {
            return pregnancyStatus;
        }
        public String getChildDob() {
            return childDob;
        }
        public LocalDate getDeliveryDate() {
            return deliveryDate;
        }
        public Map<String, String> getAnswers() {
            return answers;
        }
    }
}
