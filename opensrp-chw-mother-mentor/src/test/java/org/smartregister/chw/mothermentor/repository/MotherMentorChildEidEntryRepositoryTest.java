package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

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
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.clientandeventmodel.Event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real SQLite assertions behind the SQLCipher facade used by the Android library. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidEntryRepositoryTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT, child_dob TEXT,"
                + " event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (id VARCHAR PRIMARY KEY, base_entity_id TEXT,"
                + " first_name TEXT, middle_name TEXT, last_name TEXT, dob TEXT, gender TEXT,"
                + " relational_id TEXT, dod TEXT, date_died TEXT, is_closed INTEGER, unique_id TEXT)");
        data.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, village_town TEXT, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_child (base_entity_id TEXT, mother_entity_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, form_submission_id TEXT,"
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT, mother_base_entity_id TEXT,"
                + " child_dob TEXT, is_closed INTEGER, relational_id TEXT, child_name TEXT, child_sex TEXT, mother_name TEXT,"
                + " hei_number TEXT, breastfeeding_status TEXT, weaning_date TEXT, last_test TEXT, last_test_result TEXT,"
                + " last_test_results_returned TEXT)");
        data.execSQL("INSERT INTO ec_family VALUES ('family','Kibiti',0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','mother','Neema','','John',"
                + "'1990-01-01','Female','family','','',0,'M1')");
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
        MotherMentorChildEidRepository.upgrade(db);
    }

    @After public void tearDown() { data.close(); }

    @Test public void graceBoundaryUsesTheOriginalServiceDayNotTodaysSyncDate() throws Exception {
        LocalDate due = dob.plusMonths(9);
        visit("within", "within-source", "mother", missed(), due.plusDays(14), 1, true, false, 9);
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
        visit("late", "late-source", "mother", missed(), due.plusDays(15), 1, true, false, 9);
        MotherMentorChildEidRepository.EnrollmentPlan plan = plan();
        assertEquals(Stage.NINE_MONTHS, plan.getMissedStage());
        assertEquals(due, plan.getMissedDue());
        assertEquals(due.plusDays(15), plan.getServiceDate());
        assertFalse(MotherMentorChildEidRepository.isPastGrace(due.plusDays(14), due));
    }

    @Test public void linkedRealChildCanEnterAfterEighteenMonthsButNoIdentityIsInvented() throws Exception {
        visit("late", "source", "mother", missed(), dob.plusMonths(18).plusDays(15), 1, true, false, 9);
        assertEquals(Stage.EIGHTEEN_MONTHS, plan().getMissedStage());
        data.delete("ec_child", null, null);
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
        assertEquals(2, count("ec_family_member"));
        assertEquals(0, count("ec_mothermentor_enroll_child_eid"));
    }

    @Test public void twinsAndMismatchedRegisteredBirthsNeverSelectAnArbitraryIdentity() throws Exception {
        visit("late", "source", "mother", missed(), dob.plusMonths(9).plusDays(15), 1, true, false, 9);
        person("twin", dob.toString(), "Furaha");
        data.execSQL("INSERT INTO ec_child VALUES ('twin','mother')");
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
        data.delete("ec_family_member", "base_entity_id='twin'", null);
        data.execSQL("UPDATE ec_family_member SET dob='2025-02-01' WHERE base_entity_id='child'");
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
    }

    @Test public void draftVoidedAndNonMissedAnswersCannotCreateARecord() throws Exception {
        LocalDate day = dob.plusMonths(9).plusDays(15);
        visit("draft", "draft-source", "mother", missed(), day, 1, false, false, 9);
        visit("void", "void-source", "mother", missed(), day, 1, true, true, 9);
        Map<String, String> noAnswer = missed(); noAnswer.remove(MAMA_HEI_TEST_DONE);
        visit("birth-only", "birth-only-source", "mother", noAnswer, day, 1, true, false, 9);
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
    }

    @Test public void aPendingResultIsNeverMistakenForANewMissedSampling() throws Exception {
        LocalDate day = dob.plusMonths(9).plusDays(15);
        Map<String, String> tested = missed();
        tested.put(MAMA_HEI_TEST_DONE, "yes"); tested.put(MAMA_HEI_TEST_TYPE, "nine_months");
        tested.put(MAMA_HEI_TEST_DATE, dob.plusMonths(9).plusDays(1).toString());
        tested.put(MAMA_HEI_TEST_RESULT, "pending");
        visit("test", "test-source", "mother", tested, day.minusDays(1), 1, true, false, 9);
        visit("missed", "missed-source", "mother", missed(), day, 1, true, false, 9);
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
    }

    @Test public void eventPlanPreservesActualIdentityContextAndCausalIdempotencyWithoutWritingRows() throws Exception {
        visit("late", "source", "mother", missed(), dob.plusMonths(9).plusDays(15), 1, true, false, 9);
        MotherMentorChildEidRepository.EnrollmentPlan first = plan();
        assertEquals(first.getFormSubmissionId(), plan().getFormSubmissionId());
        Event event = first.toEvent();
        Map<String, String> values = observations(event);
        assertEquals("child", event.getBaseEntityId());
        assertEquals(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, event.getEventType());
        assertEquals("source-provider", event.getProviderId());
        assertEquals("source-location", event.getLocationId());
        assertEquals("mother", values.get(MOTHER_BASE_ENTITY_ID));
        assertEquals("mother-epoch", values.get(MTOTO_MOTHER_ENROLLMENT_ID));
        assertEquals("miezi9", values.get(MTOTO_MISSED_TEST));
        assertEquals(dob.plusMonths(9).toString(), values.get(MTOTO_MISSED_DUE));
        assertEquals("none", values.get(MTOTO_RUFAA_STATE));
        assertEquals(0, count("ec_mothermentor_enroll_child_eid"));
        assertFalse(first.isPersisted(db));
        rawEvent("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID,
                first.getFormSubmissionId(), first.getServiceDate(), new JSONObject(JsonFormUtils.gson.toJson(event)));
        assertTrue(first.isPersisted(db));
        assertEquals(first.getFormSubmissionId(), plan().getFormSubmissionId());
    }

    @Test public void eventTimestampKeepsSameDayEnrollmentOrderWhileServiceDayIsExplicit() throws Exception {
        LocalDate day = dob.plusMonths(9).plusDays(15);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "earlier-episode",
                day, fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString()));
        visit("late", "source", "mother", missed(), day, 1, true, false, 15);
        MotherMentorChildEidRepository.EnrollmentPlan plan = plan();
        Event event = plan.toEvent();
        assertEquals(new DateTime(day + "T12:00:00.000+03:00").getMillis(), event.getEventDate().getTime());
        assertEquals(day.toString(), observations(event).get(MOTHERMENTOR_VISIT_DATE));
        rawEvent("child", event.getEventType(), plan.getFormSubmissionId(), day,
                new JSONObject(JsonFormUtils.gson.toJson(event)));
        assertEquals(plan.getFormSubmissionId(), MotherMentorChildEidDao.latestEnrollment(db, "child", day).id);
        assertEquals(day, MotherMentorChildEidDao.latestEnrollment(db, "child", day).day());
    }

    @Test public void persistedPayloadSurvivesChangedParentAnswersAndUsesItsLatestStoredRevision() throws Exception {
        LocalDate day = dob.plusMonths(9).plusDays(15);
        visit("late", "source", "mother", missed(), day, 1, true, false, 9);
        MotherMentorChildEidRepository.EnrollmentPlan first = plan();
        JSONObject original = new JSONObject(JsonFormUtils.gson.toJson(first.toEvent())).put(EVENT_VERSION, 1);
        rawEvent("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, first.getFormSubmissionId(), day, original);
        data.execSQL("UPDATE ec_family_member SET first_name='Corrected name' WHERE base_entity_id='child'");
        MotherMentorChildEidRepository.EnrollmentPlan retry = plan();
        assertEquals(first.getFormSubmissionId(), retry.getFormSubmissionId());
        assertEquals("Baraka Mwakyusa", MotherMentorVisitCompletion.observationValues(
                retry.getStoredEventJson(db).getJSONArray("obs")).get(CHILD_NAME));
        assertEquals("Corrected name Mwakyusa", observations(retry.toEvent()).get(CHILD_NAME));
        JSONObject revision = new JSONObject(original.toString()).put(EVENT_VERSION, 2).put("locationId", "stored-correction");
        rawEvent("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, first.getFormSubmissionId(), day, revision);
        assertEquals("stored-correction", retry.getStoredEventJson(db).getString("locationId"));
        assertEquals(0, count("ec_mothermentor_enroll_child_eid"));
    }

    @Test public void parentReEnrollmentRetainsSamplingPresenceWithoutInheritingTerminalProjection() throws Exception {
        eidRow("child", 0);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "old-eid", dob.plusMonths(1),
                fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString()));
        visit("old-final", "old-final-source", "child", childResult("negative", "eighteen_months"),
                dob.plusMonths(18).plusDays(2), 1, true, false, 9);
        LocalDate newEnrollment = dob.plusMonths(19);
        rawEnrollment("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, "new-mother", newEnrollment,
                fields(PREGNANCY_STATUS, "breast_feeding", CHILD_DOB, dob.toString()));
        data.execSQL("UPDATE ec_mothermentor_enrollment SET event_date='" + newEnrollment + "T08:00:00.000+03:00'");
        MotherMentorMotherChildRepository.State mother = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertTrue(mother.getTests().isEmpty());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, mother.getTerminalOutcome());
        assertTrue(mother.getSamplingHistory().containsKey(Stage.EIGHTEEN_MONTHS));
        assertTrue(MotherMentorChildEidRepository.getClinicalState(db, "child", null).getTests().containsKey(Stage.EIGHTEEN_MONTHS));
    }

    @Test public void existingOpenReferralContinuesWhenTheCurrentMissedTestChanges() throws Exception {
        eidRow("child", 0);
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET mtoto_missed_test='wiki6',"
                + " mtoto_rufaa_state='awaiting_result',mtoto_rufaa_test='wiki6',mtoto_counter_amekataa=2");
        visit("early", "early-source", "mother", missed(), dob.plusDays(57), 1, true, false, 9);
        visit("later", "later-source", "mother", missed(), dob.plusMonths(9).plusDays(15), 1, true, false, 9);
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertEquals("miezi9", scalar("SELECT mtoto_missed_test FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("awaiting_result", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("wiki6", scalar("SELECT mtoto_rufaa_test FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("2", scalar("SELECT mtoto_counter_amekataa FROM ec_mothermentor_enroll_child_eid"));
    }

    @Test public void unknownExistingStatesAndIndependentClosuresSurviveMigration() {
        eidRow("child", 1);
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET mtoto_rufaa_state='future_state',mtoto_status='external_close'");
        MotherMentorChildEidRepository.upgrade(db);
        MotherMentorChildEidRepository.upgrade(db);
        assertEquals(13, MotherMentorChildEidRepository.COLUMNS.size());
        assertEquals("future_state", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
        assertEquals("external_close", scalar("SELECT mtoto_status FROM ec_mothermentor_enroll_child_eid"));
        assertTrue(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertNull(MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother"));
    }

    @Test public void workflowEpisodesResetCountsWhileActualChildResultsRemainKnown() throws Exception {
        eidRow("child", 0);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "old-eid",
                dob.plusMonths(1), fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString()));
        Map<String, String> tested = childResult("negative", "nine_months");
        tested.put(MTOTO_ENROLLMENT_ID, "old-eid");
        visit("old", "old-source", "child", tested, dob.plusMonths(9).plusDays(2), 1, true, false, 9);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "new-eid",
                dob.plusMonths(10), fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString(), MTOTO_RUFAA_STATE, "none"));
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET mtoto_rufaa_state='referred',mtoto_counter_amekataa=3");
        MotherMentorChildEidRepository.State state = MotherMentorChildEidRepository.getState(db, "child", null);
        assertEquals("new-eid", state.getEnrollmentId());
        assertEquals("none", state.getReferralState());
        assertEquals(0, state.getRefusedCount());
        assertEquals(0, state.getVisitCount());
        assertEquals("negative", state.getClinicalState().getTests().get(Stage.NINE_MONTHS).getResult());
        assertFalse(MotherMentorHeiSchedule.eligibleForSampling(dob, null,
                state.getClinicalState().getSamplingHistory(), dob.plusMonths(10)).contains(Stage.NINE_MONTHS));
    }

    @Test public void editingFirstVisitCannotSeeALaterResultFromTheSameDay() throws Exception {
        eidRow("child", 0);
        rawEnrollment("child", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, "eid-epoch",
                dob.plusMonths(1), fields(MOTHER_BASE_ENTITY_ID, "mother", CHILD_DOB, dob.toString(), MTOTO_ENTRY_SOURCE, "cause"));
        LocalDate day = dob.plusMonths(19);
        Map<String, String> first = fields(FOLLOW_UP_STATUS, "not_found", MTOTO_ENROLLMENT_ID, "eid-epoch");
        visit("z-first", "first-source", "child", first, day, 1, true, false, 9);
        visit("a-later", "later-source", "child", childResult("negative", "eighteen_months"), day, 1, true, false, 12);
        assertTrue(MotherMentorChildEidRepository.getClinicalStateBeforeVisit(db, "child", "z-first").getTests().isEmpty());
        assertEquals(0, MotherMentorChildEidRepository.getStateBeforeVisit(db, "child", "z-first").getVisitCount());
        assertTrue(MotherMentorChildEidRepository.getClinicalState(db, "child", "z-first", day).getTests().containsKey(Stage.EIGHTEEN_MONTHS));
    }

    @Test public void undatedKnownSamplingBlocksRepeatsWithoutInventingMotherProjectionDates() {
        eidRow("child", 0);
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET last_test='dbs_9',last_test_result='positive'");
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertTrue(state.getTests().isEmpty());
        assertEquals("positive", state.getSamplingHistory().get(Stage.NINE_MONTHS).getResult());
        assertNull(state.getSamplingHistory().get(Stage.NINE_MONTHS).getDate());
        assertFalse(state.getSamplingHistory().get(Stage.NINE_MONTHS).isConfirmed());
        assertTrue(state.getSamplingHistory().get(Stage.NINE_MONTHS).requiresFollowUp());
        assertFalse(MotherMentorHeiSchedule.eligibleForSampling(dob, null, state.getSamplingHistory(), dob.plusMonths(10)).contains(Stage.NINE_MONTHS));
    }

    @Test public void registeredBirthTimestampUsesItsRecordedDateRatherThanTheDeviceOffset() throws Exception {
        data.execSQL("UPDATE ec_family_member SET dob='2025-01-31T23:30:00.000-11:00' WHERE base_entity_id='child'");
        visit("late", "source", "mother", missed(), dob.plusMonths(9).plusDays(15), 1, true, false, 9);
        assertEquals(dob.toString(), observations(plan().toEvent()).get(CHILD_DOB));
        assertEquals(dob, MotherMentorHeiSchedule.parseBirthDate("2025-01-31T00:15:00+14:00"));
        assertNull(MotherMentorHeiSchedule.parseBirthDate("2025-01"));
        assertNull(MotherMentorHeiSchedule.parseBirthDate("2025-02-30T00:00:00Z"));
        assertNull(MotherMentorHeiSchedule.parseDate("2025-01-31T00:00:00Z"));
    }

    @Test public void closedListAndBoundCardRetainActualIdentityWhenHouseholdIsClosed() {
        eidRow("child", 1);
        data.execSQL("UPDATE ec_family SET is_closed=1");
        data.execSQL("UPDATE ec_family_member SET is_closed=1,dod='2026-01-01' WHERE base_entity_id='child'");
        assertEquals("0", scalar(MotherMentorChildEidDao.countQuery("", false)));
        assertEquals("1", scalar(MotherMentorChildEidDao.countQuery("Neema John", true)));
        Map<String, String> card = MotherMentorChildEidDao.cardValues(db, "child");
        assertEquals("Neema John", card.get(MOTHER_NAME));
        assertEquals("Kibiti", card.get(VILLAGE_TOWN));
        assertEquals("child", card.get(BASE_ENTITY_ID));
        assertEquals("1", card.get(MTOTO_IS_CLOSED));
        assertTrue(MotherMentorChildEidDao.cardValues(db, "not-a-real-child").isEmpty());
    }

    @Test public void searchEscapesQuotesAndWildcardsInsteadOfChangingTheCohort() {
        eidRow("child", 0);
        assertEquals("0", scalar(MotherMentorChildEidDao.countQuery("%' OR 1=1 --", false)));
        assertEquals("0", scalar(MotherMentorChildEidDao.countQuery("_", false)));
        assertEquals("1", scalar(MotherMentorChildEidDao.countQuery("Baraka", false)));
        try (Cursor cursor = data.rawQuery(MotherMentorChildEidDao.pageQuery("Baraka", false, 10, 0), null)) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("child", cursor.getString(cursor.getColumnIndexOrThrow(BASE_ENTITY_ID)));
        }
    }

    @Test public void registerPageProvidesMemberIdForTheCursorAdapter() {
        try (Cursor cursor = data.rawQuery(MotherMentorChildEidDao.pageQuery("", false, 10, 0), null)) {
            assertEquals(0, cursor.getCount());
            assertTrue(cursor.getColumnIndex(BaseColumns._ID) >= 0);
        }

        eidRow("child", 0);
        try (Cursor cursor = data.rawQuery(MotherMentorChildEidDao.pageQuery("", false, 10, 0), null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("child", cursor.getString(cursor.getColumnIndexOrThrow(BaseColumns._ID)));
        }
    }

    private MotherMentorChildEidRepository.EnrollmentPlan plan() {
        MotherMentorChildEidRepository.EnrollmentPlan plan = MotherMentorChildEidRepository.getPendingEnrollmentPlan(db, "mother");
        assertNotNull("A valid overdue test on a real linked child must produce an enrollment plan", plan);
        return plan;
    }

    private Map<String, String> missed() {
        return fields(ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                MAMA_ENROLLMENT_ID, "mother-epoch", MAMA_ENROLLMENT_PREGNANCY_STATUS, "breast_feeding",
                MAMA_TAREHE_KUJIFUNGUA, dob.toString(), MAMA_CHILD_STATUS, "alive",
                MAMA_BREASTFEEDING_STATUS, "breastfeeding", MAMA_HEI_TEST_DONE, "no");
    }

    private Map<String, String> childResult(String result, String stage) {
        Map<String, String> values = fields(FOLLOW_UP_STATUS, "found", CHILD_EID_FOLLOW_UP_SCHEMA, "2",
                CHILD_EID_TEST_TYPE, stage, CHILD_EID_ARRIVED_AT_FACILITY, "yes",
                CHILD_EID_FACILITY_ARRIVAL_DATE, dob.plusMonths("nine_months".equals(stage) ? 9 : 18).plusDays(1).toString(),
                MAMA_HEI_TEST_DONE, "yes", MAMA_HEI_TEST_TYPE, stage, MAMA_HEI_TEST_RESULT, result,
                MAMA_HEI_TEST_DATE, dob.plusMonths("nine_months".equals(stage) ? 9 : 18).plusDays(1).toString());
        if ("eighteen_months".equals(stage)) values.putAll(fields(CHILD_EID_FINAL_HIV_TEST_DONE, "yes",
                CHILD_EID_FINAL_HIV_RESULT, result, CHILD_EID_FINAL_HIV_TEST_DATE, dob.plusMonths(18).plusDays(1).toString()));
        return values;
    }

    private void person(String id, String birth, String first) {
        data.execSQL("INSERT INTO ec_family_member VALUES (?,?,?,'','Mwakyusa',?,'Male','family','','',0,?)",
                new Object[] {id, id, first, birth, id});
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

    private Map<String, String> observations(Event event) throws Exception {
        return MotherMentorVisitCompletion.observationValues(new JSONObject(JsonFormUtils.gson.toJson(event)).optJSONArray("obs"));
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, String> value : values.entrySet()) result.put(new JSONObject()
                .put("fieldCode", value.getKey()).put("values", new JSONArray().put(value.getValue())));
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
