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
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
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
        MotherMentorChildEidRepository.upgrade(db);
    }

    @After public void tearDown() { data.close(); }

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
        assertEquals("negative", state().getClinicalState().getTests().get(Stage.NINE_MONTHS).getResult());
        assertEquals(sampleDate, state().getClinicalState().getTests().get(Stage.NINE_MONTHS).getDate());
        data.delete("visits", "visit_id = ?", new String[] {"confirm"});
        assertEquals("awaiting_confirm", state().getReferralState());
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
                        MTOTO_RUFAA_STATE, "none", MTOTO_ENROLLMENT_ID, id));
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
