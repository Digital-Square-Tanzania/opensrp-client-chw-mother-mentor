package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_CONFIRM;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_RESULT;

import timber.log.Timber;

/** Immutable links between a Child EID visit, a test sample and subsequent HEI result events. */
public final class MotherMentorChildEidResultEvent {
    public static final String EVENT_TYPE = "Mother Mentor Child EID HEI";
    public static final String PARENT_SUBMISSION_ID = CHILD_EID_PARENT_SUBMISSION_ID;
    public static final String SAMPLE_SUBMISSION_ID = CHILD_EID_SAMPLE_SUBMISSION_ID;
    public static final String ENTRY = CHILD_EID_RESULT_ENTRY;
    public static final String INLINE = "inline";
    public static final String STANDALONE = "standalone";
    private static final String DERIVED_INLINE = "child_eid_linked_hei";

    private MotherMentorChildEidResultEvent() {}

    public static final class PendingSample {
        private final String childId;
        private final String parentSubmissionId;
        private final String sampleSubmissionId;
        private final String enrollmentId;
        private final MotherMentorHeiSchedule.Stage stage;
        private final LocalDate sampleDate;
        private final String state;

        PendingSample(String childId, String parentSubmissionId, String sampleSubmissionId,
                String enrollmentId, MotherMentorHeiSchedule.Stage stage, LocalDate sampleDate,
                String state) {
            this.childId = childId;
            this.parentSubmissionId = parentSubmissionId;
            this.sampleSubmissionId = sampleSubmissionId;
            this.enrollmentId = enrollmentId;
            this.stage = stage;
            this.sampleDate = sampleDate;
            this.state = state;
        }

        PendingSample advance(String nextState) {
            return new PendingSample(childId, parentSubmissionId, sampleSubmissionId, enrollmentId,
                    stage, sampleDate, nextState);
        }

        boolean matches(Map<String, String> values) {
            return enrollmentId.equals(value(values, MTOTO_ENROLLMENT_ID))
                    && stage == MotherMentorHeiSchedule.Stage.fromCode(value(values, MTOTO_PENDING_TEST))
                    && sampleDate.equals(MotherMentorHeiSchedule.parseDate(value(values, MTOTO_PENDING_TEST_DATE)))
                    && state.equals(value(values, MTOTO_RUFAA_STATE_BEFORE))
                    && sampleSubmissionId.equals(value(values, SAMPLE_SUBMISSION_ID));
        }

        public String getChildId() { return childId; }
        public String getParentSubmissionId() { return parentSubmissionId; }
        public String getSampleSubmissionId() { return sampleSubmissionId; }
        public String getEnrollmentId() { return enrollmentId; }
        public MotherMentorHeiSchedule.Stage getStage() { return stage; }
        public LocalDate getSampleDate() { return sampleDate; }
        public String getState() { return state; }
    }

    public static PendingSample pending(SQLiteDatabase db, String childId) {
        return MotherMentorChildEidRepository.getState(db, childId, null).getPendingSample();
    }

    public static boolean validate(SQLiteDatabase db, PendingSample sample, Map<String, String> answers,
            LocalDate day) {
        return currentPayload(db, sample, answers, day) != null;
    }

    public static MotherMentorChildEidExit preview(SQLiteDatabase db, PendingSample sample,
            Map<String, String> answers, LocalDate day) {
        Map<String, String> values = currentPayload(db, sample, answers, day);
        if (values == null) return null;
        return MotherMentorChildEidExit.fromReturn(
                MotherMentorChildEidReturnVisit.evaluateStandalone(values, day).outcome);
    }

    public static boolean save(SQLiteDatabase db, PendingSample sample, Map<String, String> answers,
            LocalDate day) {
        if (db == null) return false;
        boolean transaction = !db.inTransaction();
        if (transaction) db.beginTransaction();
        try {
            Map<String, String> values = currentPayload(db, sample, answers, day);
            if (values == null) return false;
            Event event = new Event().withBaseEntityId(sample.childId).withEventType(EVENT_TYPE)
                    .withEntityType(Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID)
                    .withEventDate(new Date()).withFormSubmissionId(UUID.randomUUID().toString());
            for (Map.Entry<String, String> field : values.entrySet()) {
                observation(event, field.getKey(), field.getValue());
            }
            persist(db, event);
            rebuild(db, sample.childId);
            if (transaction) db.setTransactionSuccessful();
        } catch (Exception e) {
            Timber.e(e, "Unable to save the linked Child EID result");
            return false;
        } finally {
            if (transaction) db.endTransaction();
        }
        try {
            NCUtils.startClientProcessing();
        } catch (Exception e) {
            Timber.w(e, "The locally saved Child EID result will be processed on the next sync");
        }
        return true;
    }

    private static Map<String, String> currentPayload(SQLiteDatabase db, PendingSample sample,
            Map<String, String> answers, LocalDate day) {
        if (sample == null || answers == null || day == null || day.isAfter(LocalDate.now())) return null;
        PendingSample current = pending(db, sample.childId);
        if (current == null || !current.parentSubmissionId.equals(sample.parentSubmissionId)
                || !current.sampleSubmissionId.equals(sample.sampleSubmissionId)
                || !current.enrollmentId.equals(sample.enrollmentId) || !current.state.equals(sample.state)
                || current.stage != sample.stage || !current.sampleDate.equals(sample.sampleDate)) return null;
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ENTRY, STANDALONE);
        values.put(PARENT_SUBMISSION_ID, sample.parentSubmissionId);
        values.put(SAMPLE_SUBMISSION_ID, sample.sampleSubmissionId);
        values.put(MTOTO_ENROLLMENT_ID, sample.enrollmentId);
        values.put(MTOTO_PENDING_TEST, sample.stage.getMetadataCode());
        values.put(MTOTO_PENDING_TEST_DATE, sample.sampleDate.toString());
        values.put(MTOTO_RUFAA_STATE_BEFORE, sample.state);
        values.put(MTOTO_RETURN_FLOW, "1");
        values.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        if (AWAITING_RESULT.equals(sample.state)) {
            values.put(MTOTO_RESULT_RECEIVED_DATE, value(answers, MTOTO_RESULT_RECEIVED_DATE));
            values.put(MTOTO_RESULT_RECEIVED, "yes");
            values.put(MTOTO_RECEIVED_RESULT, value(answers, MTOTO_RECEIVED_RESULT));
        }
        if (AWAITING_CONFIRM.equals(sample.state) || "positive".equals(value(values, MTOTO_RECEIVED_RESULT))) {
            values.put(MTOTO_CONFIRMATORY_TEST_DONE, value(answers, MTOTO_CONFIRMATORY_TEST_DONE));
            if ("yes".equals(value(answers, MTOTO_CONFIRMATORY_TEST_DONE))) {
                values.put(MTOTO_CONFIRMATORY_RESULT, value(answers, MTOTO_CONFIRMATORY_RESULT));
            }
            String received = MotherMentorChildEidReturnVisit.confirmationReceived(values);
            values.put(MTOTO_CONFIRMATION_RECEIVED, received);
            if (AWAITING_CONFIRM.equals(sample.state) && "yes".equals(received)) {
                String date = value(answers, MTOTO_RESULT_RECEIVED_DATE);
                if (!MotherMentorHeiSchedule.validDate(MotherMentorHeiSchedule.parseDate(date), sample.sampleDate, day)) {
                    return null;
                }
                values.put(MTOTO_RESULT_RECEIVED_DATE, date);
            }
        }
        return MotherMentorChildEidReturnVisit.evaluateStandalone(values, day) == null ? null : values;
    }

    /** Stage the separate action and identify the exact revision it belongs to on the wire. */
    public static void linkInline(Event parent, Event hei, PendingSample previous, String existingSample) {
        String parentId = parent.getFormSubmissionId();
        String submissionId = UUID.nameUUIDFromBytes(
                (EVENT_TYPE + ":" + parentId).getBytes(StandardCharsets.UTF_8)).toString();
        String revision = UUID.randomUUID().toString();
        hei.setFormSubmissionId(submissionId);
        hei.setEventDate(parent.getEventDate());
        hei.setVersion(parent.getVersion());
        observation(parent, CHILD_EID_INLINE_SUBMISSION_ID, submissionId);
        observation(parent, CHILD_EID_INLINE_REVISION, revision);
        observation(hei, ENTRY, INLINE);
        observation(hei, CHILD_EID_INLINE_REVISION, revision);
        observation(hei, PARENT_SUBMISSION_ID, parentId);
        observation(hei, SAMPLE_SUBMISSION_ID, previous == null
                ? StringUtils.defaultIfBlank(existingSample, submissionId) : previous.sampleSubmissionId);
    }

    public static void persist(SQLiteDatabase db, Event event) throws Exception {
        NCUtils.addEvent(MotherMentorLibrary.getInstance().context().allSharedPreferences(), event);
        JSONObject persisted = MotherMentorChildEidDao.storedEvent(db, event.getBaseEntityId(),
                EVENT_TYPE, event.getFormSubmissionId());
        if (persisted == null || !value(answers(persisted), CHILD_EID_INLINE_REVISION)
                .equals(value(answers(new JSONObject(JsonFormUtils.gson.toJson(event))),
                        CHILD_EID_INLINE_REVISION))) {
            throw new IllegalStateException("Unable to persist the linked HEI event");
        }
    }

    public static Map<String, String> visitAnswers(SQLiteDatabase db, Visit visit) {
        Map<String, String> values = answers(MotherMentorChildEidDao.json(visit));
        JSONObject hei = inline(db, visit, values);
        if (hei != null) {
            Map<String, String> heiValues = answers(hei);
            heiValues.remove(MOTHERMENTOR_VISIT_DATE);
            values.putAll(heiValues);
        }
        return values;
    }

    public static boolean isSplitVisit(Visit visit) {
        return StringUtils.isNotBlank(answers(MotherMentorChildEidDao.json(visit))
                .get(CHILD_EID_INLINE_SUBMISSION_ID));
    }

    public static Map<String, String> historyAnswers(SQLiteDatabase db, Visit visit) {
        Map<String, String> values = visitAnswers(db, visit);
        if (!MotherMentorChildEidDao.hasTable(db, VisitRepository.VISIT_TABLE)) return values;
        try (Cursor rows = db.query(VisitRepository.VISIT_TABLE, new String[] {VISIT_JSON},
                PARENT_VISIT_ID + " = ? AND " + VISIT_TYPE + " = ? AND " + PROCESSED + " = 1",
                new String[] {visit.getVisitId(), EVENT_TYPE}, null, null, VISIT_DATE)) {
            while (rows.moveToNext()) {
                Map<String, String> result = answers(new JSONObject(rows.getString(0)));
                for (String key : Arrays.asList(MTOTO_RECEIVED_RESULT, MTOTO_RESULT_RECEIVED_DATE,
                        MTOTO_CONFIRMATORY_TEST_DONE, MTOTO_CONFIRMATORY_RESULT)) {
                    if (StringUtils.isNotBlank(result.get(key))) values.put(key, result.get(key));
                }
            }
        } catch (JSONException e) {
            Timber.w(e, "Unable to read linked Child EID history");
        }
        return values;
    }

    private static JSONObject inline(SQLiteDatabase db, Visit visit, Map<String, String> parent) {
        String linked = value(parent, CHILD_EID_INLINE_SUBMISSION_ID);
        if (linked.isEmpty() || MotherMentorChildEidDao.deletedEventSources(db, visit.getBaseEntityId()).contains(linked)) {
            return null;
        }
        JSONObject event = MotherMentorChildEidDao.storedEvent(db, visit.getBaseEntityId(), EVENT_TYPE,
                linked);
        if (event == null || event.optBoolean(EVENT_VOIDED)) return null;
        Map<String, String> child = answers(event);
        return INLINE.equals(value(child, ENTRY))
                && MotherMentorChildEidDao.sourceIdentity(visit).equals(value(child, PARENT_SUBMISSION_ID))
                && value(parent, CHILD_EID_INLINE_REVISION).equals(value(child, CHILD_EID_INLINE_REVISION))
                ? event : null;
    }

    public static void processEvent(SQLiteDatabase db, org.smartregister.domain.Event event) {
        if (db == null || event == null || !EVENT_TYPE.equals(event.getEventType())) return;
        rebuild(db, event.getBaseEntityId());
    }

    public static boolean processDeletion(SQLiteDatabase db, String submissionId) {
        if (StringUtils.isBlank(submissionId) || !MotherMentorChildEidDao.hasTable(db, "event")) return false;
        String child;
        try (Cursor rows = db.rawQuery("SELECT " + EVENT_BASE_ENTITY_ID + ", " + EVENT_JSON
                + " FROM event WHERE " + DBConstants.KEY.EVENT_TYPE + " = ? AND "
                + EVENT_FORM_SUBMISSION_ID + " = ?", new String[] {EVENT_TYPE, submissionId})) {
            if (rows.moveToFirst()) {
                child = rows.getString(0);
                JSONObject event = new JSONObject(rows.getString(1));
                if (event.optBoolean(EVENT_VOIDED)) return true;
                event.put(EVENT_VOIDED, true);
                ContentValues values = new ContentValues();
                values.put(EVENT_JSON, event.toString());
                db.update("event", values, DBConstants.KEY.EVENT_TYPE + " = ? AND " + EVENT_FORM_SUBMISSION_ID + " = ?",
                        new String[] {EVENT_TYPE, submissionId});
            } else {
                // A legacy void message can replace the original raw event before it is processed.
                child = projectedChild(db, submissionId);
            }
        } catch (JSONException e) {
            Timber.w(e, "Unable to delete the linked Child EID event");
            return false;
        }
        if (StringUtils.isBlank(child)) return false;
        rebuild(db, child);
        return true;
    }

    private static String projectedChild(SQLiteDatabase db, String submissionId) {
        if (!MotherMentorChildEidDao.hasTable(db, VisitRepository.VISIT_TABLE)) return "";
        try (Cursor rows = db.rawQuery("SELECT " + BASE_ENTITY_ID + " FROM " + VisitRepository.VISIT_TABLE
                + " WHERE " + VISIT_TYPE + " = ? AND " + FORM_SUBMISSION_ID + " = ?",
                new String[] {EVENT_TYPE, submissionId})) {
            return rows.moveToFirst() ? rows.getString(0) : "";
        }
    }

    static void rebuild(SQLiteDatabase db, String childId) {
        for (Visit visit : MotherMentorChildEidDao.clinicalVisits(db, childId, null, LocalDate.now())) {
            if (isSplitVisit(visit)) refreshInlineDetails(db, visit);
        }
        MotherMentorChildEidRepository.rebuild(db, childId);
        MotherMentorMotherChildRepository.rebuildForChild(db, childId);
        MotherMentorMotherClosureRepository.rebuildForChild(db, childId);
    }

    public static void refreshInlineDetails(SQLiteDatabase db, Visit visit) {
        if (visit == null || !MotherMentorFollowUpType.CHILD_EID.getEventType().equals(visit.getVisitType())
                || !MotherMentorChildEidDao.hasTable(db, VisitDetailsRepository.VISIT_DETAILS_TABLE)) return;
        db.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ? AND "
                + PREPROCESSED_TYPE + " = ?", new String[] {visit.getVisitId(), DERIVED_INLINE});
        JSONObject hei = inline(db, visit, answers(MotherMentorChildEidDao.json(visit)));
        if (hei == null) return;
        try {
            Event event = JsonFormUtils.gson.fromJson(hei.toString(), Event.class);
            VisitDetailsRepository details = new VisitDetailsRepository();
            for (List<VisitDetail> group : NCUtils.eventsObsToDetails(
                    event.getObs(), visit.getVisitId(), visit.getBaseEntityId()).values()) {
                for (VisitDetail detail : group) {
                    detail.setPreProcessedType(DERIVED_INLINE);
                    details.addVisitDetails(detail, db);
                }
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to project the linked HEI action", e);
        }
    }

    static List<Visit> standaloneEvents(SQLiteDatabase db, String childId, LocalDate asOf) {
        if (!MotherMentorChildEidDao.hasTable(db, "event")) return Collections.emptyList();
        Map<String, Visit> rowsById = new LinkedHashMap<>();
        try (Cursor rows = db.rawQuery("SELECT " + EVENT_JSON + " FROM event WHERE "
                + EVENT_BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.EVENT_TYPE + " = ?",
                new String[] {childId, EVENT_TYPE})) {
            while (rows.moveToNext()) {
                try {
                    JSONObject event = new JSONObject(rows.getString(0));
                    String source = event.optString(EVENT_FORM_SUBMISSION_ID);
                    if (source.isEmpty() || !STANDALONE.equals(value(answers(event), ENTRY))) continue;
                    Visit existing = rowsById.get(source);
                    if (existing != null && MotherMentorChildEidDao.json(existing).optLong(EVENT_VERSION)
                            > event.optLong(EVENT_VERSION)) continue;
                    Visit visit = new Visit();
                    visit.setVisitId(source);
                    visit.setFormSubmissionId(source);
                    visit.setVisitType(EVENT_TYPE);
                    visit.setBaseEntityId(childId);
                    visit.setDate(new Date(MotherMentorIitRepository.timestamp(event.opt(EVENT_DATE_TIMESTAMP))));
                    visit.setJson(event.toString());
                    rowsById.put(source, visit);
                } catch (JSONException invalid) {
                    Timber.w(invalid, "Unable to read linked Child EID evidence");
                }
            }
        }
        List<Visit> visits = new ArrayList<>();
        Set<String> deleted = MotherMentorChildEidDao.deletedEventSources(db, childId);
        for (Visit visit : rowsById.values()) {
            LocalDate recorded = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (recorded != null && !recorded.isAfter(asOf) && !deleted.contains(visit.getFormSubmissionId())
                    && !MotherMentorChildEidDao.json(visit).optBoolean(EVENT_VOIDED)) visits.add(visit);
        }
        return visits;
    }

    static List<Visit> projectedStandalone(SQLiteDatabase db, String childId, String excludedVisitId,
            LocalDate asOf) {
        if (!MotherMentorChildEidDao.hasTable(db, VisitRepository.VISIT_TABLE)) return Collections.emptyList();
        List<Visit> visits = new ArrayList<>();
        if (!MotherMentorChildEidDao.columns(db, VisitRepository.VISIT_TABLE).contains(PARENT_VISIT_ID)) {
            return Collections.emptyList();
        }
        try (Cursor rows = db.rawQuery("SELECT " + VISIT_ID + ", " + VISIT_JSON + ", "
                + PARENT_VISIT_ID + ", " + VISIT_DATE + " FROM " + VisitRepository.VISIT_TABLE
                + " WHERE " + BASE_ENTITY_ID + " = ? AND " + VISIT_TYPE + " = ? AND " + PROCESSED + " = 1",
                new String[] {childId, EVENT_TYPE})) {
            while (rows.moveToNext()) {
                if (StringUtils.equals(excludedVisitId, rows.getString(2))) continue;
                Visit visit = new Visit();
                visit.setVisitId(rows.getString(0));
                visit.setVisitType(EVENT_TYPE);
                visit.setBaseEntityId(childId);
                visit.setJson(rows.getString(1));
                visit.setDate(new Date(rows.getLong(3)));
                LocalDate day = MotherMentorMotherVisitDao.getServiceDate(visit);
                if (day != null && !day.isAfter(asOf)) visits.add(visit);
            }
        }
        return visits;
    }

    static void projectStandalone(SQLiteDatabase db, String childId, List<Visit> accepted) {
        if (!MotherMentorChildEidDao.hasTable(db, VisitRepository.VISIT_TABLE)
                || !MotherMentorChildEidDao.hasTable(db, VisitDetailsRepository.VISIT_DETAILS_TABLE)) return;
        String where = BASE_ENTITY_ID + " = ? AND " + VISIT_TYPE + " = ?";
        String[] args = {childId, EVENT_TYPE};
        db.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " IN (SELECT "
                + VISIT_ID + " FROM " + VisitRepository.VISIT_TABLE + " WHERE " + where + ")", args);
        db.delete(VisitRepository.VISIT_TABLE, where, args);
        for (Visit visit : accepted) {
            ContentValues row = new ContentValues();
            row.put(VISIT_ID, visit.getVisitId());
            row.put(FORM_SUBMISSION_ID, visit.getFormSubmissionId());
            row.put(BASE_ENTITY_ID, visit.getBaseEntityId());
            row.put(VISIT_TYPE, EVENT_TYPE);
            row.put(VISIT_DATE, visit.getDate().getTime());
            row.put(VISIT_JSON, visit.getJson());
            row.put(PARENT_VISIT_ID, visit.getParentVisitID());
            row.put(PROCESSED, 1);
            db.insertOrThrow(VisitRepository.VISIT_TABLE, null, row);
            for (Map.Entry<String, String> field : answers(MotherMentorChildEidDao.json(visit)).entrySet()) {
                ContentValues detail = new ContentValues();
                detail.put(VISIT_DETAILS_ID, UUID.randomUUID().toString());
                detail.put(VISIT_ID, visit.getVisitId());
                detail.put(VISIT_KEY, field.getKey());
                detail.put(DETAILS, field.getValue());
                detail.put(PROCESSED, 1);
                db.insertOrThrow(VisitDetailsRepository.VISIT_DETAILS_TABLE, null, detail);
            }
        }
    }

    static Map<String, String> answers(JSONObject event) {
        return MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
    }

    static String value(Map<String, String> values, String key) {
        return StringUtils.defaultString(values.get(key));
    }

    private static void observation(Event event, String key, String value) {
        if (StringUtils.isBlank(value)) return;
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<>(Collections.singletonList(value)), new ArrayList<>(), null, key));
    }
}
