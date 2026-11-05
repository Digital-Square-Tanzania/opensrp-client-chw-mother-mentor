package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtilsTest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildRepositoryTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, form_submission_id TEXT,"
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT, mother_base_entity_id TEXT, child_dob TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','breast_feeding','2025-01-31',"
                + "'2025-03-01T08:00:00.000+03:00','2025-03-01',1,0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household')");
        enrollment("episode-one", "2025-03-01T08:00:00.000+03:00");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), nullable(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
    }

    @After public void tearDown() { data.close(); }

    @Test
    public void explicitMigrationAddsThirteenColumnsOnceAndRebuildsFromFinalizedEvidence() throws Exception {
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("six_weeks", "negative", dob.plusDays(43));
        visit("one", "source-one", "mother", values, dob.plusDays(45), 1, false);
        MotherMentorMotherChildRepository.upgrade(db);
        MotherMentorMotherChildRepository.upgrade(db);
        assertEquals(13, MotherMentorMotherChildRepository.COLUMNS.size());
        assertEquals("negative", scalar("SELECT hei_test_wiki6_result FROM ec_mothermentor_enrollment"));
        assertEquals("2025-03-15", scalar("SELECT hei_test_wiki6_date FROM ec_mothermentor_enrollment"));
        assertEquals("breastfeeding", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        data.delete("visits", null, null);
        MotherMentorMotherChildRepository.rebuild(db, "mother");
        assertEquals("", scalar("SELECT hei_test_wiki6_result FROM ec_mothermentor_enrollment"));
    }

    @Test
    public void pendingAndUnconfirmedResultsRemainDistinctUntilExplicitConfirmation() {
        Map<String, String> pending = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", dob.plusDays(43));
        Map<String, String> positive = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", dob.plusDays(43));
        positive.put(HEI_CHANYA_IMETHIBITISHWA, "no");
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.replay(dob,
                Arrays.asList(pending, positive), Arrays.asList(dob.plusDays(44), dob.plusDays(50)));
        assertEquals("positive", state.getTests().get(Stage.SIX_WEEKS).getResult());
        assertFalse(state.hasConfirmedPositive());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
        positive = new LinkedHashMap<>(positive); positive.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        state = MotherMentorMotherChildRepository.replay(dob, Arrays.asList(pending, positive),
                Arrays.asList(dob.plusDays(44), dob.plusDays(50)));
        assertTrue(state.hasConfirmedPositive());
        assertEquals(dob.plusDays(50), state.getTerminalServiceDate());
    }

    @Test
    public void mismatchedImmutableBirthDateCannotSupplyATerminalChildOutcome() throws Exception {
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", dob.plusDays(43));
        values.put(MAMA_TAREHE_KUJIFUNGUA, dob.minusDays(1).toString());
        values.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        visit("invalid-birth", "source", "mother", values, dob.plusDays(45), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertTrue(state.getTests().isEmpty());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
    }

    @Test
    public void linkedEidFinalNegativeIsSharedUsingServiceDateAndNotLabDate() throws Exception {
        link("child", dob);
        visit("child-result", "child-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("child", state.getChildBaseEntityId());
        assertTrue(state.hasFinalNegative());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.GRADUATED, state.getTerminalOutcome());
        assertEquals(dob.plusMonths(19), state.getTerminalServiceDate());
        assertEquals(dob.plusMonths(18), state.getTests().get(Stage.EIGHTEEN_MONTHS).getDate());
    }

    @Test
    public void returnedEidPositiveIsNotPositiveConfirmation() throws Exception {
        link("child", dob);
        Map<String, String> positive = childResult("positive");
        positive.put(LAST_TEST_RESULTS_RETURNED, "yes");
        visit("child-result", "child-source", "child", positive, dob.plusMonths(19), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertFalse(state.hasConfirmedPositive());
        assertTrue(state.getTests().get(Stage.EIGHTEEN_MONTHS).requiresFollowUp());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
    }

    @Test
    public void ambiguousTwinsOrWrongChildDobCannotSupplyResults() throws Exception {
        link("child", dob.minusDays(1));
        visit("result", "source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
        data.delete("ec_mothermentor_enroll_child_eid", null, null);
        link("child", dob); link("twin", dob);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("", state.getChildBaseEntityId());
        assertTrue(state.getTests().isEmpty());
    }

    @Test
    public void correctedOrVoidedChildSourceDoesNotResurrectAnOlderResult() throws Exception {
        link("child", dob);
        visit("one", "same-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        visit("two", "same-source", "child", childResult("positive"), dob.plusMonths(19), 2, true);
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
        visit("three", "same-source", "child", childResult("pending"), dob.plusMonths(19), 3, false);
        assertEquals("pending", MotherMentorMotherChildRepository.getState(db, "mother", null)
                .getTests().get(Stage.EIGHTEEN_MONTHS).getResult());
    }

    @Test
    public void reEnrollmentDoesNotInheritPriorEpisodeMotherOrChildResults() throws Exception {
        link("child", dob);
        visit("one", "mother-source", "mother", MotherMentorMotherChildUtilsTest.test(
                "six_weeks", "negative", dob.plusDays(43)), dob.plusDays(45), 1, false);
        visit("two", "child-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        String time = "2026-10-01T08:00:00.000+03:00";
        enrollment("episode-two", time);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET event_date = ?", new Object[] {time});
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
    }

    private Map<String, String> childResult(String result) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(FOLLOW_UP_STATUS, "found"); values.put(CHILD_EID_TEST_TYPE, "eighteen_months");
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "2"); values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, dob.plusMonths(18).toString());
        values.put(CHILD_EID_FINAL_HIV_TEST_DONE, "yes");
        values.put(CHILD_EID_FINAL_HIV_TEST_DATE, dob.plusMonths(18).toString());
        values.put(CHILD_EID_FINAL_HIV_RESULT, result);
        return values;
    }

    private void link(String child, LocalDate birth) {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid VALUES (?, 'mother', ?)",
                new Object[] {child, birth.toString()});
    }

    private void enrollment(String id, String date) throws Exception {
        Map<String, String> values = new LinkedHashMap<>(); values.put(PREGNANCY_STATUS, "breast_feeding");
        values.put(CHILD_DOB, dob.toString());
        JSONObject event = new JSONObject().put("formSubmissionId", id).put("eventDate", date)
                .put("version", 1).put("obs", observations(values));
        ContentValues row = new ContentValues(); row.put("baseEntityId", "mother");
        row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT); row.put("eventDate", date);
        row.put("formSubmissionId", id); row.put("json", event.toString()); data.insertOrThrow("event", null, row);
    }

    private void visit(String id, String source, String client, Map<String, String> values,
            LocalDate day, int version, boolean voided) throws Exception {
        values = new LinkedHashMap<>(values); values.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        values.put(MAMA_ENROLLMENT_ID, "episode-one");
        JSONObject event = new JSONObject().put("formSubmissionId", source).put("eventDate", day.toString())
                .put("version", version).put("voided", voided).put("obs", observations(values));
        ContentValues row = new ContentValues(); row.put(VISIT_ID, id); row.put(BASE_ENTITY_ID, client);
        row.put(VISIT_TYPE, "mother".equals(client) ? Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES
                : MotherMentorFollowUpType.CHILD_EID.getEventType());
        row.put(FORM_SUBMISSION_ID, source); row.put(PROCESSED, 1); row.put(VISIT_DATE, 1);
        row.put(UPDATED_AT, 1); row.put(CREATED_AT, 1); row.put(VISIT_JSON, event.toString());
        data.insertOrThrow("visits", null, row);
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) result.put(new JSONObject()
                .put("fieldCode", entry.getKey()).put("values", new JSONArray().put(entry.getValue())));
        return result;
    }

    private String scalar(String sql) {
        try (Cursor cursor = data.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }
}
