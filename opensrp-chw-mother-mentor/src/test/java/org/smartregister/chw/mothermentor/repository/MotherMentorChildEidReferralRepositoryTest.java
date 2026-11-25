package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/** Real SQLite assertions behind the SQLCipher facade used by the Android library. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidReferralRepositoryTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT, child_dob TEXT,"
                + " event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, first_name TEXT, middle_name TEXT, last_name TEXT,"
                + " dob TEXT, gender TEXT, relational_id TEXT, dod TEXT, date_died TEXT, is_closed INTEGER, unique_id TEXT)");
        data.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, village_town TEXT, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_child (base_entity_id TEXT, mother_entity_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, form_submission_id TEXT,"
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT,"
                + " pre_processed TEXT, parent_visit_id TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT, mother_base_entity_id TEXT,"
                + " child_dob TEXT, is_closed INTEGER, relational_id TEXT, child_name TEXT, child_sex TEXT, mother_name TEXT,"
                + " hei_number TEXT, breastfeeding_status TEXT, weaning_date TEXT, last_test TEXT, last_test_result TEXT,"
                + " last_test_results_returned TEXT)");
        data.execSQL("INSERT INTO ec_family VALUES ('family','Kibiti',0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','Neema','','John','1990-01-01','Female','family','','',0,'M1')");
        person("child", dob.toString(), "Baraka");
        data.execSQL("INSERT INTO ec_child VALUES ('child','mother')");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','breast_feeding','2025-01-31',"
                + "'2025-02-01T08:00:00.000+03:00','2025-02-01',1,0)");
        rawEnrollment("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, "mother-epoch",
                new LocalDate(2025, 2, 1), fields(PREGNANCY_STATUS, "breast_feeding", CHILD_DOB, dob.toString()));
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), nullable(String[].class))).thenAnswer(
                call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class))).thenAnswer(
                call -> data.update(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(db.delete(anyString(), anyString(), any(String[].class))).thenAnswer(
                call -> data.delete(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.insert(anyString(), isNull(), any(ContentValues.class))).thenAnswer(
                call -> data.insert(call.getArgument(0), null, call.getArgument(2)));
        when(db.insertOrThrow(anyString(), isNull(), any(ContentValues.class))).thenAnswer(
                call -> data.insertOrThrow(call.getArgument(0), null, call.getArgument(2)));
        VisitDetailsRepository.createTable(db);
        MotherMentorChildEidRepository.upgrade(db);
        MotherMentorChildEidRepository.upgradeExitSchema(db);
    }

    @After public void tearDown() { data.close(); }

    @Test public void resultDeliveredBeforeItsParentWaitsAndNeverIncrementsVisitCount() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        LocalDate sampleDate = day.plusDays(1);
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "result", sampleDate.plusDays(1),
                linkedResult("sample-source", "sample-source", sampleDate, "negative"));
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("referred", state().getReferralState());
        assertEquals(1, count("visits"));

        visit("sample-local", "sample-source", "child", sampled(sampleDate, "nine_months", "pending", ""),
                sampleDate, 1, true, false, 9);
        MotherMentorChildEidRepository.rebuild(db, "child");
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals(2, state().getVisitCount());
        assertEquals(MotherMentorChildEidExit.E1, state().getVisitExit());
        assertEquals("sample-local", scalar("SELECT parent_visit_id FROM visits WHERE visit_id='result'"));
        assertEquals("negative", state().getClinicalState().getTests().get(Stage.NINE_MONTHS).getResult());
        assertNull(MotherMentorChildEidResultEvent.pending(db, "child"));

        assertTrue(MotherMentorChildEidResultEvent.processDeletion(db, "result"));
        assertEquals(2, state().getVisitCount());
        assertEquals(2, count("visits"));
        assertEquals("sample-source", MotherMentorChildEidResultEvent.pending(db, "child").getSampleSubmissionId());
        assertFalse(state().isClosed());
    }

    @Test public void splitInlineRevisionDeterminesTheExactPendingSample() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        LocalDate sampleDate = day.plusDays(1);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        Map<String, String> parent = fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "found",
                MAMA_CHILD_STATUS, "alive", MTOTO_ENROLLMENT_ID, "eid-one",
                CHILD_EID_INLINE_SUBMISSION_ID, "hei", CHILD_EID_INLINE_REVISION, "current");
        visit("sample-local", "sample-source", "child", parent, sampleDate, 1, true, false, 9);
        Map<String, String> hei = sampled(sampleDate, "nine_months", "pending", "");
        hei.putAll(fields(CHILD_EID_RESULT_ENTRY, MotherMentorChildEidResultEvent.INLINE,
                CHILD_EID_PARENT_SUBMISSION_ID, "sample-source", CHILD_EID_SAMPLE_SUBMISSION_ID, "hei",
                CHILD_EID_INLINE_REVISION, "stale"));
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "hei", sampleDate, hei);
        assertNull(MotherMentorChildEidResultEvent.pending(db, "child"));
        data.delete("event", "formSubmissionId = ?", new String[] {"hei"});
        hei.put(CHILD_EID_INLINE_REVISION, "current");
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "hei", sampleDate, hei);
        MotherMentorChildEidResultEvent.PendingSample pending = MotherMentorChildEidResultEvent.pending(db, "child");
        assertEquals("hei", pending.getSampleSubmissionId());
        assertEquals("sample-source", pending.getParentSubmissionId());
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending,
                fields(MTOTO_RECEIVED_RESULT, "negative"), sampleDate.plusDays(1)));
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending,
                fields(MTOTO_RECEIVED_RESULT, "negative", MTOTO_RESULT_RECEIVED_DATE, day.toString()),
                sampleDate.plusDays(1)));
        assertTrue(MotherMentorChildEidResultEvent.validate(db, pending,
                fields(MTOTO_RECEIVED_RESULT, "negative", MTOTO_RESULT_RECEIVED_DATE, sampleDate.toString()),
                sampleDate.plusDays(1)));
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "wrong-result", sampleDate.plusDays(1),
                linkedResult("sample-source", "other-hei", sampleDate, "negative"));
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("hei", MotherMentorChildEidResultEvent.pending(db, "child").getSampleSubmissionId());
        assertEquals(2, count("visits"));
    }

    private Map<String, String> linkedResult(String parent, String sample, LocalDate sampleDate, String result) {
        return fields(CHILD_EID_RESULT_ENTRY, MotherMentorChildEidResultEvent.STANDALONE,
                CHILD_EID_PARENT_SUBMISSION_ID, parent, CHILD_EID_SAMPLE_SUBMISSION_ID, sample,
                MTOTO_ENROLLMENT_ID, "eid-one", MTOTO_PENDING_TEST, "miezi9",
                MTOTO_PENDING_TEST_DATE, sampleDate.toString(), MTOTO_RUFAA_STATE_BEFORE, "awaiting_result",
                MTOTO_RECEIVED_RESULT, result, MTOTO_RESULT_RECEIVED_DATE, sampleDate.plusDays(1).toString(),
                MOTHERMENTOR_VISIT_DATE, sampleDate.plusDays(1).toString());
    }

    @Test public void positiveResultAndConfirmationRetainTheSameSampleWithoutIncreasingVisitCount() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        LocalDate sampleDate = day.plusDays(1);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        visit("sample-local", "sample-source", "child", sampled(sampleDate, "nine_months", "pending", ""),
                sampleDate, 1, true, false, 9);
        Map<String, String> positive = linkedResult("sample-source", "sample-source", sampleDate, "positive");
        positive.put(MTOTO_CONFIRMATORY_TEST_DONE, "no");
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "first-result", sampleDate.plusDays(1), positive);
        MotherMentorChildEidRepository.rebuild(db, "child");
        MotherMentorChildEidResultEvent.PendingSample pending = MotherMentorChildEidResultEvent.pending(db, "child");
        assertEquals("awaiting_confirm", pending.getState());
        assertNull(pending.getConfirmationSampleDate());
        assertEquals("sample-source", pending.getSampleSubmissionId());
        assertFalse(state().isClosed());
        assertEquals(2, state().getVisitCount());

        LocalDate confirmDate = sampleDate.plusDays(2);
        Map<String, String> confirmation = linkedResult("sample-source", "sample-source", sampleDate, "");
        confirmation.putAll(fields(MTOTO_RUFAA_STATE_BEFORE, "awaiting_confirm", MOTHERMENTOR_VISIT_DATE,
                confirmDate.toString(), MTOTO_CONFIRMATORY_TEST_DONE, "yes", MTOTO_CONFIRMATORY_RESULT, "positive"));
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "confirmation", confirmDate, confirmation);
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals(2, state().getVisitCount());
        assertEquals(MotherMentorChildEidExit.E4, state().getVisitExit());
        assertTrue(state().getClinicalState().getTests().get(Stage.NINE_MONTHS).isConfirmed());
    }

    @Test public void documentedPositiveConfirmationRequiresReceiptAndReplaysAsE4() throws Exception {
        assertDocumentedConfirmation("positive", MotherMentorChildEidExit.E4, true);
    }

    @Test public void documentedNegativeConfirmationReplaysAsE2WithoutClosurePrompt() throws Exception {
        assertDocumentedConfirmation("negative", MotherMentorChildEidExit.E2, false);
    }

    @Test public void editedOrUnmatchedAttendanceInvalidatesThePreviouslyOpenedResult() throws Exception {
        LocalDate screeningDate = dob.plusMonths(10);
        LocalDate confirmationDate = screeningDate.plusDays(7);
        LocalDate day = confirmationDate.plusDays(1);
        saveConfirmationAttendance(screeningDate, confirmationDate, day);
        MotherMentorChildEidResultEvent.PendingSample opened = state().getPendingSample();
        assertNotNull(opened);
        Map<String, String> result = fields(MTOTO_CONFIRMATORY_RESULT, "positive",
                MTOTO_RESULT_RECEIVED_DATE, day.toString());
        assertTrue(MotherMentorChildEidResultEvent.validate(db, opened, result, day));

        data.delete("event", "formSubmissionId = ?", new String[] {"attendance-hei"});
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "attendance-hei", day,
                confirmationAttendance(confirmationDate, day, "old-revision"));
        assertNull(state().getPendingSample());
        assertFalse(MotherMentorChildEidResultEvent.save(db, opened, result, day));

        data.delete("event", "formSubmissionId = ?", new String[] {"attendance-hei"});
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "attendance-hei", day,
                confirmationAttendance(day, day, "attendance-revision"));
        assertEquals(day, state().getPendingSample().getConfirmationSampleDate());
        assertEquals(opened.getSampleSubmissionId(), state().getPendingSample().getSampleSubmissionId());
        assertFalse(MotherMentorChildEidResultEvent.save(db, opened, result, day));
        assertEquals(2, count("visits"));
    }

    private void assertDocumentedConfirmation(String outcome, MotherMentorChildEidExit exit, boolean prompt)
            throws Exception {
        LocalDate screeningDate = dob.plusMonths(10);
        LocalDate confirmationDate = screeningDate.plusDays(7);
        LocalDate day = confirmationDate.plusDays(1);
        saveConfirmationAttendance(screeningDate, confirmationDate, confirmationDate);
        MotherMentorChildEidResultEvent.PendingSample pending = state().getPendingSample();
        assertNotNull(pending);
        assertEquals("awaiting_confirm", pending.getState());
        assertEquals(Stage.NINE_MONTHS, pending.getStage());
        assertEquals(confirmationDate, pending.getConfirmationSampleDate());
        assertEquals(confirmationDate, pending.getSampleDate());
        assertEquals("attendance-parent", pending.getParentSubmissionId());
        assertEquals("attendance-hei", pending.getSampleSubmissionId());
        assertEquals(2, state().getVisitCount());

        Map<String, String> answers = fields(MTOTO_CONFIRMATORY_RESULT, outcome);
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending, answers, day));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, confirmationDate.minusDays(1).toString());
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending, answers, day));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, day.plusDays(1).toString());
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending, answers, day));
        answers.put(MTOTO_RESULT_RECEIVED_DATE, confirmationDate.toString());
        answers.put(MTOTO_CONFIRMATORY_RESULT, "pending");
        assertFalse(MotherMentorChildEidResultEvent.validate(db, pending, answers, day));
        answers.put(MTOTO_CONFIRMATORY_RESULT, outcome);
        assertEquals(exit, MotherMentorChildEidResultEvent.preview(db, pending, answers, day));
        assertEquals(prompt, exit.needsConfirmation());
        assertEquals(2, count("visits"));

        MotherMentorLibrary previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        ECSyncHelper sync = mock(ECSyncHelper.class);
        when(library.context()).thenReturn(context);
        when(context.allSharedPreferences()).thenReturn(mock(AllSharedPreferences.class));
        when(library.getEcSyncHelper()).thenReturn(sync);
        when(library.getSourceDateFormat()).thenReturn("dd-MM-yyyy");
        when(library.getSaveDateFormat()).thenReturn("yyyy-MM-dd");
        doAnswer(call -> {
            JSONObject event = call.getArgument(1);
            rawEvent("child", MotherMentorChildEidResultEvent.EVENT_TYPE,
                    event.getString(EVENT_FORM_SUBMISSION_ID), day, event);
            return null;
        }).when(sync).addEvent(eq("child"), any(JSONObject.class));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Timber.Tree logger = new Timber.Tree() {
            @Override protected void log(int priority, String tag, String message, Throwable cause) {
                if (priority == android.util.Log.ERROR) error.set(cause);
            }
        };
        Timber.plant(logger);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        try {
            if (!MotherMentorChildEidResultEvent.save(db, pending, answers, day)) {
                throw new AssertionError("Confirmation result was not saved", error.get());
            }
            assertFalse(MotherMentorChildEidResultEvent.save(db, pending, answers, day));
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
            Timber.uproot(logger);
        }
        assertEquals(2, state().getVisitCount());
        assertEquals(exit, state().getVisitExit());
        assertNull(state().getPendingSample());
        assertEquals("attendance-local", scalar(
                "SELECT parent_visit_id FROM visits WHERE visit_type = 'Mother Mentor Child EID HEI'"));
        JSONObject saved = new JSONObject(scalar("SELECT json FROM event WHERE eventType = 'Mother Mentor Child EID HEI'"
                + " AND formSubmissionId != 'attendance-hei'"));
        Map<String, String> results = MotherMentorVisitCompletion.observationValues(saved.getJSONArray("obs"));
        assertEquals("attendance-hei", results.get(CHILD_EID_SAMPLE_SUBMISSION_ID));
        assertEquals("attendance-parent", results.get(CHILD_EID_PARENT_SUBMISSION_ID));
        assertEquals("yes", results.get(MTOTO_CONFIRMATORY_TEST_DONE));
        assertEquals(outcome, results.get(MTOTO_CONFIRMATORY_RESULT));
        assertEquals(confirmationDate.toString(), results.get(MTOTO_RESULT_RECEIVED_DATE));
    }

    private void saveConfirmationAttendance(LocalDate screeningDate, LocalDate sampleDate, LocalDate visitDate)
            throws Exception {
        eidRow("child", 0);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "eid-one", screeningDate,
                fields(MOTHER_BASE_ENTITY_ID, "", CHILD_DOB, dob.toString(), MTOTO_ENTRY_SOURCE, "direct",
                        MTOTO_ENTRY_TEST, "miezi9", MTOTO_ENTRY_RESULT, "positive", MTOTO_ENROLLMENT_ID, "eid-one",
                        MTOTO_RUFAA_STATE, "confirmation_needed", MTOTO_RUFAA_TEST, "miezi9"));
        Map<String, String> referral = first("agreed");
        referral.put(MTOTO_RUFAA_STATE_BEFORE, "confirmation_needed");
        visit("referral", "referral", "child", referral, screeningDate.plusDays(1), 1, true, false, 9);
        assertEquals("confirmation_referred", state().getReferralState());
        Map<String, String> parent = fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "found",
                MAMA_CHILD_STATUS, "alive", MTOTO_ENROLLMENT_ID, "eid-one",
                CHILD_EID_INLINE_SUBMISSION_ID, "attendance-hei", CHILD_EID_INLINE_REVISION, "attendance-revision");
        visit("attendance-local", "attendance-parent", "child", parent, visitDate, 1, true, false, 9);
        rawEnrollment("child", MotherMentorChildEidResultEvent.EVENT_TYPE, "attendance-hei", visitDate,
                confirmationAttendance(sampleDate, visitDate, "attendance-revision"));
    }

    private Map<String, String> confirmationAttendance(LocalDate sampleDate, LocalDate visitDate, String revision) {
        Map<String, String> answers = returning("confirmation_referred");
        answers.putAll(fields(MTOTO_RUFAA_TEST, "miezi9", CHILD_EID_ARRIVED_AT_FACILITY, "yes",
                CHILD_EID_FACILITY_ARRIVAL_DATE, sampleDate.toString(), MAMA_HEI_TEST_DONE, "yes",
                MAMA_HEI_TEST_DATE, sampleDate.toString(), MAMA_HEI_TEST_RESULT, "pending",
                CHILD_EID_RESULT_ENTRY, MotherMentorChildEidResultEvent.INLINE,
                CHILD_EID_PARENT_SUBMISSION_ID, "attendance-parent", CHILD_EID_SAMPLE_SUBMISSION_ID, "attendance-hei",
                CHILD_EID_INLINE_REVISION, revision, MOTHERMENTOR_VISIT_DATE, visitDate.toString()));
        return answers;
    }

    @Test public void inlinePositiveNeedsAnExplicitConfirmationAnswerAndOnlyPositiveConfirmationPromptsClosure()
            throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        LocalDate sampleDate = day.plusDays(1);
        Map<String, String> sample = sampled(sampleDate, "nine_months", "positive", "");
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(sample, dob, sampleDate,
                state().getClinicalState().getSamplingHistory(), "referred"));
        sample.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(sample, dob, sampleDate,
                state().getClinicalState().getSamplingHistory(), "referred"));

        sample.put(MTOTO_CONFIRMATORY_RESULT, "positive");
        MotherMentorChildEidExit positive =
                MotherMentorChildEidRepository.previewExit(db, "child", null, sample, sampleDate);
        assertEquals(MotherMentorChildEidExit.E4, positive);
        assertTrue(positive.needsConfirmation());
        sample.put(MTOTO_CONFIRMATORY_RESULT, "negative");
        MotherMentorChildEidExit negative =
                MotherMentorChildEidRepository.previewExit(db, "child", null, sample, sampleDate);
        assertEquals(MotherMentorChildEidExit.E2, negative);
        assertFalse(negative.needsConfirmation());

        sample.put(MTOTO_CONFIRMATORY_RESULT, "pending");
        assertNull(MotherMentorChildEidRepository.previewExit(db, "child", null, sample, sampleDate));
        visit("sample", "sample", "child", sample, sampleDate, 1, true, false, 9);
        assertEquals("awaiting_confirm", state().getReferralState());
        assertNull(state().getPendingSample().getConfirmationSampleDate());
        assertFalse(state().isClosed());
        assertFalse(state().getClinicalState().getTests().get(Stage.NINE_MONTHS).isConfirmed());
    }

    @Test public void positivePendingSampleAndFinalConfirmationUseTheOriginalSampleStage() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(18).plusDays(1);
        Map<String, String> referral = first("agreed");
        referral.put(MTOTO_RUFAA_TEST, "miezi18");
        referral.put(MTOTO_RUFAA_DUE, dob.plusMonths(18).toString());
        visit("agree", "agree", "child", referral, day, 1, true, false, 9);
        LocalDate sampleDate = day.plusDays(1);
        Map<String, String> sample = sampled(sampleDate, "eighteen_months", "positive", "");
        sample.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        sample.put(MTOTO_CONFIRMATORY_RESULT, "pending");
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(sample, dob, sampleDate,
                state().getClinicalState().getSamplingHistory(), "referred"));
        sample.put(MTOTO_CONFIRMATORY_TEST_DONE, "no");
        sample.remove(MTOTO_CONFIRMATORY_RESULT);
        visit("sample", "sample", "child", sample, sampleDate, 1, true, false, 9);
        assertEquals("awaiting_confirm", state().getReferralState());
        assertFalse(state().isClosed());

        Map<String, String> confirmation = returning("awaiting_confirm");
        confirmation.putAll(fields(MTOTO_PENDING_TEST, "miezi18", MTOTO_PENDING_TEST_DATE,
                sampleDate.toString(), MTOTO_CONFIRMATORY_TEST_DONE, "yes", MTOTO_CONFIRMATORY_RESULT, "pending"));
        assertFalse(MotherMentorChildEidHeiUtils.isComplete(confirmation, dob, sampleDate.plusDays(1),
                state().getClinicalState().getSamplingHistory(), "awaiting_confirm"));
        confirmation.put(MTOTO_CONFIRMATORY_RESULT, "negative");
        assertEquals(MotherMentorChildEidExit.E2,
                MotherMentorChildEidRepository.previewExit(db, "child", null, confirmation, sampleDate.plusDays(1)));
    }

    @Test public void firstAgreementBecomesReferredWithTheActualStageAndDueSnapshot() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        visit("agree", "agree-source", "child", first("agreed"), dob.plusMonths(10), 1, true, false, 9);
        MotherMentorChildEidRepository.State state = state();
        assertEquals("referred", state.getReferralState());
        assertEquals("miezi9", state.getReferralTest());
        assertEquals(dob.plusMonths(9).toString(), state.getReferralDue());
        assertEquals(0, state.getRefusedCount());
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("referred", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("miezi9", scalar("SELECT mtoto_rufaa_test FROM ec_mothermentor_enroll_child_eid"));
    }

    @Test public void refusalsReplayOncePerCanonicalSourceAndCorrectionOrDeletionRebuildsThem() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("one", "same-source", "child", first("refused"), day, 1, true, false, 9);
        visit("copy", "same-source", "child", first("refused"), day, 2, true, false, 9);
        visit("two", "second-source", "child", first("refused"), day.plusDays(1), 1, true, false, 9);
        assertEquals(2, state().getRefusedCount());
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("2", scalar("SELECT mtoto_counter_amekataa FROM ec_mothermentor_enroll_child_eid"));
        visit("correction", "same-source", "child", first("agreed"), day, 3, true, false, 9);
        assertEquals("referred", state().getReferralState());
        assertEquals(0, state().getRefusedCount());
        data.delete("visits", "form_submission_id = ?", new String[] {"same-source"});
        assertEquals("none", state().getReferralState());
        assertEquals(1, state().getRefusedCount());
        data.delete("visits", null, null);
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("none", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("0", scalar("SELECT mtoto_counter_amekataa FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("", scalar("SELECT mtoto_rufaa_test FROM ec_mothermentor_enroll_child_eid"));
    }

    @Test public void draftsVoidsInvalidSnapshotsAndInactiveStatusCannotAdvanceReferral() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("draft", "draft", "child", first("agreed"), day, 1, false, false, 9);
        visit("void", "void", "child", first("agreed"), day, 1, true, true, 9);
        Map<String,String> invalid = first("refused"); invalid.put(MTOTO_RUFAA_TEST, "wiki6");
        visit("invalid", "invalid", "child", invalid, day, 1, true, false, 9);
        Map<String,String> inactive = first("agreed"); inactive.put(FOLLOW_UP_STATUS, "not_found");
        inactive.put(MTOTO_NOT_FOUND_REASON, "other"); inactive.put(MTOTO_NOT_FOUND_REASON_OTHER, "Absent");
        visit("inactive", "inactive", "child", inactive, day, 1, true, false, 9);
        assertEquals("none", state().getReferralState());
        assertEquals(0, state().getRefusedCount());
    }

    @Test public void staleOfflineFirstReferralCannotResetAnExistingReferralOrCountAgain() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("first", "first", "child", first("agreed"), day, 1, true, false, 9);
        Map<String,String> stale = first("refused");
        stale.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        stale.put(MAMA_TAREHE_ACHA_KUNYONYESHA, dob.plusMonths(6).toString());
        visit("offline", "offline", "child", stale, day.plusDays(1), 1, true, false, 9);
        assertEquals("referred", state().getReferralState());
        assertEquals(0, state().getRefusedCount());
        assertEquals(dob.plusMonths(6), state().getClinicalState().getWeaningDate());
    }

    @Test public void reEnrollmentStartsFreshButAnEditedHistoricalVisitUsesItsOriginalPrefix() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("one", "one", "child", first("refused"), day, 1, true, false, 9);
        visit("two", "two", "child", first("agreed"), day.plusDays(1), 1, true, false, 9);
        assertEquals(1, MotherMentorChildEidRepository.getStateBeforeVisit(db, "child", "two").getRefusedCount());
        enroll("eid-two", day.plusDays(2));
        assertEquals("none", state().getReferralState());
        assertEquals(0, state().getRefusedCount());
        assertEquals(0, state().getVisitCount());
        assertEquals("eid-one", MotherMentorChildEidRepository.getStateBeforeVisit(db, "child", "two").getEnrollmentId());
    }

    @Test public void absenceOfRawEnrollmentPreservesProjectionWithoutInventingOrRecountingAnEpisode() throws Exception {
        eidRow("child", 0);
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET mtoto_rufaa_state='none',"
                + "mtoto_counter_amekataa=4,mtoto_rufaa_test='wiki6',mtoto_enrollment_id='missing-original'");
        visit("one", "one", "child", first("refused"), dob.plusMonths(10), 1, true, false, 9);
        assertEquals("missing-original", state().getEnrollmentId());
        assertEquals(4, state().getRefusedCount());
        MotherMentorChildEidRepository.rebuild(db, "child");
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("4", scalar("SELECT mtoto_counter_amekataa FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("wiki6", scalar("SELECT mtoto_rufaa_test FROM ec_mothermentor_enroll_child_eid"));
    }

    @Test public void noEligibleSamplingDoesNotInventReferralOrReuseAPendingSample() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET last_test='dbs_9',last_test_result='pending'");
        Map<String,String> values = first("");
        values.remove(MTOTO_RUFAA_TEST); values.remove(MTOTO_RUFAA_DUE);
        visit("none", "none", "child", values, dob.plusMonths(10), 1, true, false, 9);
        assertEquals("none", state().getReferralState());
        assertEquals("", state().getReferralTest());
        assertEquals(0, state().getRefusedCount());
        assertTrue(state().getClinicalState().getTests().isEmpty());
    }

    @Test public void savedReturnResultsReplayAcrossVisitsAndKeepTheOriginalSampleDate() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        LocalDate sampleDate = day.plusDays(1);
        Map<String, String> sample = returning("referred");
        sample.putAll(fields(CHILD_EID_ARRIVED_AT_FACILITY, "yes", CHILD_EID_FACILITY_ARRIVAL_DATE,
                sampleDate.toString(), MAMA_HEI_TEST_DONE, "yes", CHILD_EID_TEST_TYPE, "nine_months",
                MAMA_HEI_TEST_DATE, sampleDate.toString(), MAMA_HEI_TEST_RESULT, "pending"));
        visit("draft", "draft", "child", sample, sampleDate, 1, false, false, 9);
        assertEquals("referred", state().getReferralState());
        visit("sample", "sample", "child", sample, sampleDate, 1, true, false, 9);
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("awaiting_result", state().getReferralState());
        assertEquals("awaiting_result", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
        Map<String, String> received = returning("awaiting_result");
        received.putAll(fields(MTOTO_PENDING_TEST, "miezi9", MTOTO_PENDING_TEST_DATE,
                sampleDate.toString(), MTOTO_RESULT_RECEIVED, "yes", MTOTO_RECEIVED_RESULT,
                "positive", MTOTO_CONFIRMATION_RECEIVED, "no"));
        visit("result", "result", "child", received, sampleDate.plusDays(1), 1, true, false, 9);
        assertEquals("awaiting_confirm", state().getReferralState());
        assertFalse(state().getClinicalState().getTests().get(Stage.NINE_MONTHS).isConfirmed());
        Map<String, String> confirm = returning("awaiting_confirm");
        confirm.putAll(fields(MTOTO_PENDING_TEST, "miezi9", MTOTO_PENDING_TEST_DATE,
                sampleDate.toString(), MTOTO_CONFIRMATION_RECEIVED, "yes", MTOTO_CONFIRMATORY_RESULT, "negative"));
        visit("confirm", "confirm", "child", confirm, sampleDate.plusDays(2), 1, true, false, 9);
        assertEquals("none", state().getReferralState());
        assertEquals(MotherMentorChildEidExit.E2, state().getVisitExit());
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", null).isClosed());
        assertEquals("negative", state().getClinicalState().getTests().get(Stage.NINE_MONTHS).getResult());
        assertEquals(sampleDate, state().getClinicalState().getTests().get(Stage.NINE_MONTHS).getDate());
        data.delete("visits", "visit_id = ?", new String[] {"confirm"});
        assertEquals("awaiting_confirm", state().getReferralState());
    }

    @Test public void attendanceResetsStrikesAndThirdUniqueNonattendanceClosesOnlyTheChild() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        Map<String, String> noAttendance = returning("referred");
        noAttendance.putAll(fields(CHILD_EID_ARRIVED_AT_FACILITY, "no", MTOTO_SABABU_KUTOFIKA, "no_fare"));
        visit("one", "one", "child", noAttendance, day.plusDays(1), 1, true, false, 9);
        visit("duplicate", "one", "child", noAttendance, day.plusDays(1), 2, true, false, 9);
        assertEquals(1, state().getNonAttendanceCount());
        Map<String, String> notTested = returning("referred");
        notTested.putAll(fields(CHILD_EID_ARRIVED_AT_FACILITY, "yes", CHILD_EID_FACILITY_ARRIVAL_DATE,
                day.plusDays(2).toString(), MAMA_HEI_TEST_DONE, "no", MTOTO_SABABU_KUTOPIMWA, "stockout"));
        visit("attended", "attended", "child", notTested, day.plusDays(2), 1, true, false, 9);
        assertEquals(0, state().getNonAttendanceCount());
        for (int index = 3; index <= 5; index++) {
            String id = "strike-" + index;
            visit(id, id, "child", noAttendance, day.plusDays(index), 1, true, false, 9);
        }
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals(3, state().getNonAttendanceCount());
        assertEquals(MotherMentorChildEidExit.E9, state().getVisitExit());
        assertEquals("E9", scalar("SELECT mtoto_eid_sababu_ya_kufungwa FROM ec_mothermentor_enroll_child_eid"));
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", null).isClosed());
        data.delete("visits", "visit_id = ?", new String[] {"strike-5"});
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertFalse(state().isClosed());
        assertEquals("0", scalar("SELECT is_closed FROM ec_mothermentor_enroll_child_eid"));
        assertEquals(2, state().getNonAttendanceCount());
    }

    @Test public void refusalAndNotFoundOtherCloseAtThreeAndSuppressTheOriginalMissedTest() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        for (int index = 0; index < 3; index++) {
            String id = "refusal-" + index;
            visit(id, id, "child", first("refused"), day.plusDays(index), 1, true, false, 9);
        }
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals(MotherMentorChildEidExit.E8, state().getVisitExit());
        assertEquals("miezi9", scalar("SELECT mtoto_suppressed_tests FROM ec_mothermentor_enroll_child_eid"));
        MotherMentorMotherChildRepository.State mother = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("refused_3", mother.getTracingClosedStatus());
        assertTrue(mother.getTracingExcludedTests().contains(Stage.NINE_MONTHS));
        Map<String, String> nextAssessment = fields(MAMA_TAREHE_KUJIFUNGUA, dob.toString(), MAMA_BREASTFEEDING_STATUS,
                "not_breastfeeding", MAMA_TAREHE_ACHA_KUNYONYESHA, dob.plusMonths(10).toString(),
                MTOTO_SUPPRESSED_TESTS, "miezi9");
        assertEquals(Collections.singletonList(Stage.AFTER_WEANING),
                MotherMentorMotherChildUtils.due(nextAssessment, dob.plusMonths(14)));
        data.delete("visits", null, null);
        MotherMentorChildEidRepository.rebuild(db, "child");
        Map<String, String> absent = fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "not_found",
                MTOTO_NOT_FOUND_REASON, "other", MTOTO_NOT_FOUND_REASON_OTHER, "Not home");
        visit("other-one", "other-one", "child", absent, day, 1, true, false, 9);
        visit("found", "found", "child", first("refused"), day.plusDays(1), 1, true, false, 9);
        assertEquals(0, state().getOtherCount());
        for (int index = 2; index <= 4; index++) {
            String id = "other-" + index;
            visit(id, id, "child", absent, day.plusDays(index), 1, true, false, 9);
        }
        assertEquals(MotherMentorChildEidExit.E10, state().getVisitExit());
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", null).isClosed());
    }

    @Test public void closurePreviewDoesNotSaveAndTransferUsesTheSameFacilityForBothCards() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        Map<String, String> transfer = fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "not_found",
                MTOTO_NOT_FOUND_REASON, "moved_facility", MTOTO_NEW_FACILITY, "Kibiti CTC");
        assertEquals(MotherMentorChildEidExit.E7,
                MotherMentorChildEidRepository.previewExit(db, "child", null, transfer, day));
        assertFalse(state().isClosed());
        assertEquals(0, count("visits"));
        visit("transfer", "transfer", "child", transfer, day, 1, true, false, 9);
        MotherMentorChildEidRepository.rebuild(db, "child");
        assertEquals("E7", scalar("SELECT mtoto_eid_status FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("Kibiti CTC", state().getExitFacility());
        assertEquals("moved_facility", MotherMentorMotherClosureRepository.getState(db, "mother", null).getStatus());
        assertEquals("Kibiti CTC", MotherMentorMotherClosureRepository.getState(db, "mother", null).getNewFacility());
    }

    @Test public void childTransferDoesNotCloseAnIitEpisodeEnrolledLaterThatDay() throws Exception {
        String iit = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
        data.execSQL("CREATE TABLE " + iit + " (base_entity_id TEXT, expected_return_date TEXT,"
                + " is_closed INTEGER, last_interacted_with INTEGER)");
        data.execSQL("INSERT INTO " + iit + " VALUES ('mother', '2025-11-01', 0, 1)");
        MotherMentorMotherClosureRepository.upgrade(db);
        MotherMentorIitRepository.upgrade(db);
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        rawEnrollment("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-one",
                day, Collections.emptyMap());

        Map<String, String> transfer = fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "not_found",
                MTOTO_NOT_FOUND_REASON, "moved_facility", MTOTO_NEW_FACILITY, "Kibiti CTC");
        visit("transfer", "transfer", "child", transfer, day, 1, true, false, 19);
        MotherMentorIitRepository.rebuild(db, "mother");
        assertEquals("I4", scalar("SELECT iit_status FROM " + iit));
        assertEquals("Kibiti CTC", scalar("SELECT iit_kituo_kipya FROM " + iit));

        JSONObject reenrollment = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, "iit-two")
                .put(EVENT_DATE_TIMESTAMP, day + "T17:00:00.000+03:00");
        rawEvent("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-two", day, reenrollment);
        data.execSQL("UPDATE " + iit + " SET is_closed = 0");
        MotherMentorIitRepository.rebuild(db, "mother");
        assertEquals("0", scalar("SELECT is_closed FROM " + iit));
    }

    @Test public void earlyNegativeClosesOnlyChildButFinalNegativeAndConfirmedPositiveCloseBoth() throws Exception {
        enroll("eid-one", dob.plusMonths(1));
        LocalDate day = dob.plusMonths(10);
        visit("agree", "agree", "child", first("agreed"), day, 1, true, false, 9);
        LocalDate later = dob.plusMonths(18).plusDays(1);
        Map<String, String> negative = sampled(day.plusDays(1), "nine_months", "negative", "");
        visit("sample", "sample", "child", negative, day.plusDays(1), 1, true, false, 9);
        assertEquals(MotherMentorChildEidExit.E1, state().getVisitExit());
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", null).isClosed());
        data.delete("visits", "form_submission_id = ?", new String[] {"sample"});
        visit("final", "final", "child", sampled(later, "eighteen_months", "negative", ""), later, 1, true, false, 9);
        assertEquals(MotherMentorChildEidExit.E3, state().getVisitExit());
        assertEquals("graduated", MotherMentorMotherClosureRepository.getState(db, "mother", null).getStatus());
        data.delete("visits", "form_submission_id = ?", new String[] {"final"});
        visit("positive", "positive", "child", sampled(day.plusDays(1), "nine_months", "positive", "yes"),
                day.plusDays(1), 1, true, false, 9);
        assertEquals(MotherMentorChildEidExit.E4, state().getVisitExit());
        assertEquals("child_confirmed_positive", MotherMentorMotherClosureRepository.getState(db, "mother", null).getStatus());
    }

    private Map<String, String> sampled(LocalDate date, String stage, String result, String confirmation) {
        Map<String, String> values = returning("referred");
        values.putAll(fields(CHILD_EID_ARRIVED_AT_FACILITY, "yes", CHILD_EID_FACILITY_ARRIVAL_DATE,
                date.toString(), MAMA_HEI_TEST_DONE, "yes", CHILD_EID_TEST_TYPE, stage,
                MAMA_HEI_TEST_DATE, date.toString(), MAMA_HEI_TEST_RESULT, result,
                MTOTO_CONFIRMATION_RECEIVED, confirmation));
        return values;
    }

    private Map<String, String> returning(String before) {
        Map<String, String> values = first("");
        values.remove(MTOTO_RUFAA_TEST);
        values.remove(MTOTO_RUFAA_DUE);
        values.put(MTOTO_RETURN_FLOW, "1");
        values.put(MTOTO_RUFAA_STATE_BEFORE, before);
        return values;
    }

    private MotherMentorChildEidRepository.State state() {
        return MotherMentorChildEidRepository.getState(db, "child", null);
    }

    private void enroll(String id, LocalDate day) throws Exception {
        if (count("ec_mothermentor_enroll_child_eid") == 0) eidRow("child", 0);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, id, day,
                fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString(),
                        MTOTO_RUFAA_STATE, "none", MTOTO_ENROLLMENT_ID, id, MTOTO_MISSED_TEST, "miezi9",
                        MTOTO_MOTHER_ENROLLMENT_ID, "mother-epoch"));
    }

    private Map<String,String> first(String agreement) {
        return fields(CHILD_EID_FOLLOW_UP_SCHEMA, "3", FOLLOW_UP_STATUS, "found", MAMA_CHILD_STATUS, "alive",
                MAMA_BREASTFEEDING_STATUS, "breastfeeding", MTOTO_RUFAA_STATE_BEFORE, "none",
                MTOTO_RUFAA_TEST, "miezi9", MTOTO_RUFAA_DUE, dob.plusMonths(9).toString(),
                MTOTO_REFERRAL_AGREEMENT, agreement, MTOTO_ENROLLMENT_ID, "eid-one");
    }

    private void person(String id, String birth, String first) {
        data.execSQL("INSERT INTO ec_family_member VALUES (?,?,'','Mwakyusa',?,'Male','family','','',0,?)",
                new Object[] {id, first, birth, id});
    }

    private void eidRow(String id, int closed) {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid (base_entity_id,mother_base_entity_id,child_dob,"
                + "is_closed,relational_id,mother_name,hei_number,breastfeeding_status) VALUES (?,'mother',?,?,'family','Old name','HEI-0412','breast_feeding')",
                new Object[] {id, dob.toString(), closed});
    }

    private void rawEnrollment(String client, String type, String id, LocalDate day, Map<String, String> values) throws Exception {
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, id).put(EVENT_DATE_TIMESTAMP,
                day.toString() + "T08:00:00.000+03:00").put("version", 1).put("obs", observations(values));
        rawEvent(client, type, id, day, event);
    }

    private void rawEvent(String client, String type, String id, LocalDate day, JSONObject event) {
        ContentValues row = new ContentValues(); row.put("baseEntityId", client); row.put("eventType", type);
        row.put("eventDate", day.toString()); row.put("formSubmissionId", id); row.put("json", event.toString());
        data.insertOrThrow("event", null, row);
    }

    private void visit(String id, String source, String client, Map<String, String> answers, LocalDate day,
            int version, boolean processed, boolean voided, int hour) throws Exception {
        answers = new LinkedHashMap<>(answers); answers.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, source).put(EVENT_DATE_TIMESTAMP,
                day.toString() + "T12:00:00.000+03:00").put("version", version).put("voided", voided)
                .put("providerId", "source-provider").put("locationId", "source-location").put("obs", observations(answers));
        ContentValues row = new ContentValues(); row.put(VISIT_ID, id); row.put(BASE_ENTITY_ID, client);
        row.put(VISIT_TYPE, "mother".equals(client) ? Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES
                : MotherMentorFollowUpType.CHILD_EID.getEventType());
        row.put(FORM_SUBMISSION_ID, source); row.put(PROCESSED, processed ? 1 : 0);
        row.put(VISIT_DATE, day.toDate().getTime() + hour * 3600000L); row.put(UPDATED_AT, version); row.put(CREATED_AT, 1);
        row.put(VISIT_JSON, event.toString()); row.put(PRE_PROCESSED, ""); data.insertOrThrow("visits", null, row);
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, String> value : values.entrySet()) result.put(new JSONObject()
                .put("fieldCode", value.getKey()).put("formSubmissionField", value.getKey())
                .put("values", new JSONArray().put(value.getValue())).put("humanReadableValues", new JSONArray()));
        return result;
    }

    private Map<String, String> fields(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }

    private String scalar(String sql) {
        try (Cursor cursor = data.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }

    private int count(String table) { return Integer.parseInt(scalar("SELECT COUNT(*) FROM " + table)); }
}
