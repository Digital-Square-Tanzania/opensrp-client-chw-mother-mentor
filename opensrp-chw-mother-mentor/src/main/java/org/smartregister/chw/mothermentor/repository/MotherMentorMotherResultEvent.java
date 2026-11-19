package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_CONFIRM;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit.AWAITING_RESULT;

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
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidResultOverdue;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherBirthTest;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import timber.log.Timber;

/** Returned Mama Kadi results are linked to their original sample and synced outside the visit stream. */
public final class MotherMentorMotherResultEvent {
    public static final String EVENT_TYPE = "Mother Mentor HEI Test Result";
    private static final String SAMPLE_SOURCE = "mama_hei_sample_submission_id";
    private static final String CORRECTED = "mama_hei_result_corrected";

    private MotherMentorMotherResultEvent() {}

    public static final class PendingTest {
        private final String sampleSource;
        private final Stage stage;
        private final LocalDate testDate;
        private final boolean awaitingConfirmation;

        PendingTest(String sampleSource, Stage stage, LocalDate testDate, boolean awaitingConfirmation) {
            this.sampleSource = sampleSource;
            this.stage = stage;
            this.testDate = testDate;
            this.awaitingConfirmation = awaitingConfirmation;
        }

        public Stage getStage() { return stage; }
        public LocalDate getTestDate() { return testDate; }
        public boolean isAwaitingConfirmation() { return awaitingConfirmation; }
        public String getState() { return awaitingConfirmation ? AWAITING_CONFIRM : AWAITING_RESULT; }

        public String getKey() {
            return sampleSource + "/" + stage.getCode() + "/" + testDate + "/" + getState();
        }

        public boolean isOverdue(LocalDate today) {
            return !awaitingConfirmation && MotherMentorChildEidResultOverdue.isOverdue(stage, testDate, today);
        }

        boolean matches(Map<String, String> values) {
            return sampleSource.equals(values.get(SAMPLE_SOURCE))
                    && stage == Stage.fromCode(values.get(MTOTO_PENDING_TEST))
                    && testDate.equals(MotherMentorHeiSchedule.parseDate(values.get(MTOTO_PENDING_TEST_DATE)))
                    && getState().equals(values.get(MTOTO_RUFAA_STATE_BEFORE));
        }
    }

    public static final class Answers {
        private final String result;
        private final LocalDate receivedDate;
        private final String confirmationDone;
        private final String confirmationResult;

        public Answers(String result, LocalDate receivedDate, String confirmationDone,
                String confirmationResult) {
            this.result = result;
            this.receivedDate = receivedDate;
            this.confirmationDone = "positive".equals(result) ? StringUtils.defaultString(confirmationDone) : "";
            this.confirmationResult = "yes".equals(this.confirmationDone)
                    ? StringUtils.defaultString(confirmationResult) : "";
        }

        public String getResult() { return result; }
        public LocalDate getReceivedDate() { return receivedDate; }
        public String getConfirmationDone() { return confirmationDone; }
        public String getConfirmationResult() { return confirmationResult; }

        public boolean isValid(PendingTest sample, LocalDate day) {
            return MotherMentorHeiSchedule.validDate(receivedDate, sample.testDate, day)
                    && isComplete(sample, observations(sample), day);
        }

        public boolean closesFollowUp(PendingTest sample, LocalDate day) {
            return closesCard(sample, observations(sample), day);
        }

        private Map<String, String> observations(PendingTest sample) {
            Map<String, String> values = new LinkedHashMap<>();
            if (receivedDate != null) values.put(MTOTO_RESULT_RECEIVED_DATE, receivedDate.toString());
            if (sample.awaitingConfirmation) {
                values.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
                values.put(MTOTO_CONFIRMATORY_RESULT, result);
            } else {
                values.put(MTOTO_RECEIVED_RESULT, result);
                values.put(MTOTO_CONFIRMATORY_TEST_DONE, confirmationDone);
                values.put(MTOTO_CONFIRMATORY_RESULT, confirmationResult);
            }
            return values;
        }
    }

    public static final class Entry {
        private final PendingTest sample;
        private final String id;
        private final Answers answers;
        private final LocalDate recordedDate;
        private final long recordedAt;
        private final long revision;
        private final String eventId;
        private final boolean corrected;

        private Entry(PendingTest sample, String id, Answers answers, LocalDate recordedDate,
                long recordedAt, long revision, String eventId, boolean corrected) {
            this.sample = sample;
            this.id = id;
            this.answers = answers;
            this.recordedDate = recordedDate;
            this.recordedAt = recordedAt;
            this.revision = revision;
            this.eventId = eventId;
            this.corrected = corrected;
        }

        public PendingTest getSample() { return sample; }
        public String getId() { return id; }
        public Answers getAnswers() { return answers; }
        public LocalDate getRecordedDate() { return recordedDate; }
        public long getRecordedAt() { return recordedAt; }
        public long getRevision() { return revision; }
        public boolean isRecorded() { return answers != null; }
        public boolean isCorrected() { return corrected; }
    }

    static Entry readEntry(Visit evidence, PendingTest sample, Map<String, String> values, LocalDate day) {
        JSONObject event = MotherMentorChildEidDao.json(evidence);
        long recordedAt = evidence.getDate().getTime();
        String result = values.get(sample.awaitingConfirmation ? MTOTO_CONFIRMATORY_RESULT : MTOTO_RECEIVED_RESULT);
        LocalDate receipt = MotherMentorHeiSchedule.parseDate(values.get(MTOTO_RESULT_RECEIVED_DATE));
        Answers answers = new Answers(result, receipt,
                sample.awaitingConfirmation ? "" : values.get(MTOTO_CONFIRMATORY_TEST_DONE),
                sample.awaitingConfirmation ? "" : values.get(MTOTO_CONFIRMATORY_RESULT));
        return new Entry(sample, evidence.getVisitId(), answers, day, recordedAt,
                event.optLong(EVENT_VERSION), event.optString("eventId"), "true".equals(values.get(CORRECTED)));
    }

    public static List<Entry> history(SQLiteDatabase db, String motherId, LocalDate day) {
        return MotherMentorMotherChildRepository.getState(db, motherId, null, day).testResultHistory();
    }

    public static List<Entry> visible(SQLiteDatabase db, String motherId, LocalDate day) {
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, motherId, null, day);
        Map<Stage, Entry> entries = new LinkedHashMap<>();
        List<PendingTest> pending = state.getPendingMotherTests();
        if (!pending.isEmpty() && !MotherMentorMotherClosureRepository.isClosed(db, motherId)) {
            for (PendingTest sample : pending) {
                entries.put(sample.stage, new Entry(sample, sample.getKey(), null, null, 0, 0, "", false));
            }
        }
        for (Entry entry : state.testResultHistory()) {
            if (day.equals(entry.recordedDate)) entries.put(entry.sample.stage, entry);
        }
        return new ArrayList<>(entries.values());
    }

    public static Entry editable(SQLiteDatabase db, String motherId, String id, LocalDate day) {
        for (Entry entry : visible(db, motherId, day)) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    static final class Outcome {
        final String result;
        final String confirmation;

        Outcome(String result, String confirmation) {
            this.result = result;
            this.confirmation = confirmation;
        }
    }

    public static List<PendingTest> pending(SQLiteDatabase db, String motherId, LocalDate day) {
        return MotherMentorMotherChildRepository.getState(db, motherId, null, day).getPendingMotherTests();
    }

    public static PendingTest pending(SQLiteDatabase db, String motherId, String key, LocalDate day) {
        for (PendingTest test : pending(db, motherId, day)) {
            if (test.getKey().equals(key)) return test;
        }
        return null;
    }

    public static boolean isComplete(PendingTest pending, Map<String, String> answers, LocalDate day) {
        return answers.containsKey(MTOTO_RESULT_RECEIVED_DATE) && evaluate(pending, answers, day) != null;
    }

    public static boolean closesCard(PendingTest pending, Map<String, String> answers, LocalDate day) {
        Outcome result = evaluate(pending, answers, day);
        return answers.containsKey(MTOTO_RESULT_RECEIVED_DATE) && result != null
                && "positive".equals(result.confirmation);
    }

    static Outcome evaluate(PendingTest pending, Map<String, String> values, LocalDate day) {
        if (day == null || day.isBefore(pending.testDate)) return null;
        LocalDate receipt = MotherMentorHeiSchedule.parseDate(values.get(MTOTO_RESULT_RECEIVED_DATE));
        // Confirmation events from the first birth-test workflow may omit the receipt date.
        if ((!pending.awaitingConfirmation || values.containsKey(MTOTO_RESULT_RECEIVED_DATE))
                && !MotherMentorHeiSchedule.validDate(receipt, pending.testDate, day)) return null;
        String confirmation = values.get(MTOTO_CONFIRMATORY_RESULT);
        if (pending.awaitingConfirmation) {
            return "negative".equals(confirmation) || "positive".equals(confirmation)
                    ? new Outcome("positive", confirmation) : null;
        }
        String result = values.get(MTOTO_RECEIVED_RESULT);
        if ("negative".equals(result)) return new Outcome(result, null);
        String done = values.get(MTOTO_CONFIRMATORY_TEST_DONE);
        return "positive".equals(result) && MotherMentorMotherBirthTest.validConfirmation(done, confirmation)
                ? new Outcome(result, "yes".equals(done) ? confirmation : null) : null;
    }

    public static boolean save(SQLiteDatabase db, String motherId, PendingTest displayed,
            Map<String, String> answers, LocalDate day) {
        if (db == null || day == null || day.isAfter(LocalDate.now())) return false;
        try {
            return save(db, motherId, displayed.getKey(), 0, sample -> answers, day, false);
        } catch (Exception error) {
            Timber.e(error, "Unable to save the Mama Kadi test result");
            return false;
        }
    }

    public static boolean save(SQLiteDatabase db, String motherId, String id, long expectedRevision,
            Answers answers) throws Exception {
        return save(db, motherId, id, expectedRevision, answers::observations, LocalDate.now(), true);
    }

    private static boolean save(SQLiteDatabase db, String motherId, String id, long expectedRevision,
            Function<PendingTest, Map<String, String>> form, LocalDate day, boolean requireReceipt) throws Exception {
        db.beginTransaction();
        try {
            Entry previous = editable(db, motherId, id, day);
            if (previous == null || previous.revision != expectedRevision) return false;
            PendingTest sample = previous.sample;
            Map<String, String> answers = form.apply(sample);
            LocalDate receipt = MotherMentorHeiSchedule.parseDate(answers.get(MTOTO_RESULT_RECEIVED_DATE));
            if (!isComplete(sample, answers, day) || (requireReceipt
                    && !MotherMentorHeiSchedule.validDate(receipt, sample.testDate, day))) return false;
            persist(db, motherId, previous, answers, day);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static void persist(SQLiteDatabase db, String motherId, Entry previous,
            Map<String, String> answers, LocalDate day) throws Exception {
        PendingTest sample = previous.sample;
        String enrollment = MotherMentorMotherVisitDao.getContext(db, motherId).getEnrollmentId();
        String key = EVENT_TYPE + ":" + motherId + ":" + enrollment + ":" + sample.getKey();
        String id = previous.isRecorded() ? previous.id
                : UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        Date time = previous.isRecorded() ? new Date(previous.recordedAt) : new Date();
        Event event = new Event().withBaseEntityId(motherId).withEventType(EVENT_TYPE)
                .withEntityType(Constants.TABLES.MOTHERMENTOR_ENROLLMENT)
                .withEventDate(time).withFormSubmissionId(id);
        if (!previous.eventId.isEmpty()) event.setEventId(previous.eventId);
        event.setVersion(Math.max(System.currentTimeMillis(), previous.revision + 1));
        observe(event, MAMA_ENROLLMENT_ID, enrollment);
        observe(event, SAMPLE_SOURCE, sample.sampleSource);
        observe(event, MOTHERMENTOR_VISIT_DATE, day.toString());
        observe(event, MTOTO_PENDING_TEST, sample.stage.getCode());
        observe(event, MTOTO_PENDING_TEST_DATE, sample.testDate.toString());
        observe(event, MTOTO_RUFAA_STATE_BEFORE, sample.getState());
        observe(event, MTOTO_RESULT_RECEIVED_DATE, answers.get(MTOTO_RESULT_RECEIVED_DATE));
        observe(event, CORRECTED, previous.isRecorded() ? "true" : "");
        if (!sample.awaitingConfirmation) observe(event, MTOTO_RECEIVED_RESULT, answers.get(MTOTO_RECEIVED_RESULT));
        if (sample.awaitingConfirmation || "positive".equals(answers.get(MTOTO_RECEIVED_RESULT))) {
            String done = sample.awaitingConfirmation ? "yes" : answers.get(MTOTO_CONFIRMATORY_TEST_DONE);
            observe(event, MTOTO_CONFIRMATORY_TEST_DONE, done);
            if ("yes".equals(done)) observe(event, MTOTO_CONFIRMATORY_RESULT, answers.get(MTOTO_CONFIRMATORY_RESULT));
        }

        NCUtils.addEvent(MotherMentorLibrary.getInstance().context().allSharedPreferences(), event);
        if (history(db, motherId, day).stream().noneMatch(entry ->
                id.equals(entry.id) && entry.revision == event.getVersion())) {
            throw new IllegalStateException("Unable to persist the linked Mama Kadi result");
        }
        rebuild(db, motherId);
        MotherMentorLibrary.getInstance().context().getEventClientRepository().markEventAsProcessed(id);
    }

    public static void rebuild(SQLiteDatabase db, String motherId) {
        if (db == null || StringUtils.isBlank(motherId)) return;
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        try {
            MotherMentorMotherChildRepository.rebuild(db, motherId);
            MotherMentorMotherClosureRepository.rebuild(db, motherId);
            if (ownsTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    public static boolean processDeletion(SQLiteDatabase db, String submissionId) {
        if (StringUtils.isBlank(submissionId) || !MotherMentorChildEidDao.hasTable(db, "event")) return false;
        String motherId;
        JSONObject event;
        try (Cursor cursor = db.rawQuery("SELECT " + EVENT_BASE_ENTITY_ID + ", " + EVENT_JSON
                + " FROM event WHERE " + DBConstants.KEY.EVENT_TYPE + " = ? AND " + EVENT_FORM_SUBMISSION_ID + " = ?",
                new String[] {EVENT_TYPE, submissionId})) {
            if (!cursor.moveToFirst()) return false;
            motherId = cursor.getString(0);
            event = new JSONObject(cursor.getString(1));
            event.put(EVENT_VOIDED, true);
        } catch (JSONException invalid) {
            Timber.w(invalid, "Unable to read the deleted Mama Kadi result");
            return false;
        }
        ContentValues data = new ContentValues();
        data.put(EVENT_JSON, event.toString());
        db.update("event", data, DBConstants.KEY.EVENT_TYPE + " = ? AND " + EVENT_FORM_SUBMISSION_ID + " = ?",
                new String[] {EVENT_TYPE, submissionId});
        rebuild(db, motherId);
        return true;
    }

    static List<Visit> evidence(SQLiteDatabase db, String motherId, String enrollment, LocalDate through) {
        if (!MotherMentorChildEidDao.hasTable(db, "event")) return Collections.emptyList();
        Map<String, JSONObject> latest = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT " + EVENT_JSON + " FROM event WHERE "
                + EVENT_BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.EVENT_TYPE + " = ?",
                new String[] {motherId, EVENT_TYPE})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject event = new JSONObject(cursor.getString(0));
                    String id = event.optString(EVENT_FORM_SUBMISSION_ID);
                    if (id.isEmpty()) continue;
                    JSONObject previous = latest.get(id);
                    if (previous == null || event.optLong(EVENT_VERSION) >= previous.optLong(EVENT_VERSION)) {
                        latest.put(id, event);
                    }
                } catch (JSONException invalid) {
                    // Invalid events supply no result evidence.
                }
            }
        }
        Set<String> deleted = MotherMentorChildEidDao.deletedEventSources(db, motherId);
        List<Visit> evidence = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : latest.entrySet()) {
            JSONObject event = entry.getValue();
            if (deleted.contains(entry.getKey()) || event.optBoolean(EVENT_VOIDED)) continue;
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            if (!enrollment.equals(values.get(MAMA_ENROLLMENT_ID))) continue;
            LocalDate day = MotherMentorHeiSchedule.parseDate(values.get(MOTHERMENTOR_VISIT_DATE));
            if (day == null || day.isAfter(through)) continue;
            Visit visit = new Visit();
            visit.setVisitId(entry.getKey());
            visit.setBaseEntityId(motherId);
            visit.setVisitType(EVENT_TYPE);
            visit.setJson(event.toString());
            visit.setDate(new Date(MotherMentorIitRepository.timestamp(event.opt(EVENT_DATE_TIMESTAMP))));
            evidence.add(visit);
        }
        return evidence;
    }

    private static void observe(Event event, String key, String value) {
        if (StringUtils.isBlank(value)) return;
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<>(Collections.singletonList(value)), new ArrayList<>(), null, key));
    }
}
