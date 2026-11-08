package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.NCUtils;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorIitExitRepositoryTest {
    private static final String IIT = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
    private static final String MOTHERS = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String CLIENT = "test-client";
    private static final String IIT_EVENT = MotherMentorFollowUpType.MOTHER_IIT.getEventType();
    private static final String MOTHER_EVENT = Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES;
    private android.database.sqlite.SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = android.database.sqlite.SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE " + IIT + " (base_entity_id TEXT PRIMARY KEY, "
                + "expected_return_date TEXT, is_closed INTEGER, last_interacted_with INTEGER)");
        data.execSQL("CREATE TABLE " + MOTHERS + " (base_entity_id TEXT PRIMARY KEY, "
                + "pregnancy_status TEXT, child_dob TEXT, is_closed INTEGER, last_interacted_with INTEGER)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, "
                + "form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at INTEGER, "
                + "created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        doAnswer(call -> { data.execSQL(call.getArgument(0), call.getArgument(1)); return null; })
                .when(db).execSQL(anyString(), any(Object[].class));
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
        when(db.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> { data.beginTransaction(); return null; }).when(db).beginTransaction();
        doAnswer(call -> { data.setTransactionSuccessful(); return null; }).when(db).setTransactionSuccessful();
        doAnswer(call -> { data.endTransaction(); return null; }).when(db).endTransaction();
        data.execSQL("INSERT INTO " + IIT + " VALUES (?, '2020-04-08', 0, 1)", new Object[] {CLIENT});
        enrollment(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-one", "2020-04-10T08:00:00+03:00");
        MotherMentorMotherClosureRepository.upgrade(db);
        MotherMentorIitRepository.upgrade(db);
    }

    @After
    public void tearDown() { data.close(); }

    @Test
    public void mixedNotFoundPreviewDoesNotMutateAndFinalizedVisitsCloseOverall() throws Exception {
        iitVisit("travel-first", "travel-first", "2020-04-11", 1, true, notFound("travelled"));
        iitVisit("travel-copy", "travel-first", "2020-04-11", 1, true, notFound("travelled"));
        iitVisit("other", "other", "2020-04-12", 1, true, notFound("other"));
        iitVisit("pending", "pending", "2020-04-13", 1, false, notFound("travelled"));
        MotherMentorIitRepository.rebuild(db, CLIENT);
        MotherMentorIitState state = state();
        assertEquals(2, state.getVisitCount());
        assertEquals(2, state.getNotFoundCount());
        assertEquals(0, state.getTravelledCount());
        assertEquals(1, state.getOtherCount());
        assertFalse(state.isClosed());
        MotherMentorIitState preview = MotherMentorIitRepository.preview(db, CLIENT, null,
                notFound("travelled"), LocalDate.parse("2020-04-13"));
        assertEquals(MotherMentorIitState.LOST_TO_FOLLOW_UP, preview.getStatus());
        assertFalse(preview.requiresConfirmation());
        assertEquals("0", field(IIT, IS_CLOSED));

        data.execSQL("UPDATE visits SET processed = 1 WHERE visit_id = 'pending'");
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("I9", field(IIT, IIT_STATUS));
        assertEquals("3", field(IIT, IIT_COUNTER_HAJAPATIKANA));
        assertEquals("2020-04-13", field(IIT, IIT_TAREHE_YA_KUFUNGWA));
        data.execSQL("DELETE FROM visits WHERE visit_id = 'pending'");
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("active", field(IIT, IIT_STATUS));
        assertEquals("2", field(IIT, IIT_COUNTER_HAJAPATIKANA));

        iitVisit("death", "death", "2020-04-13", 1, true, notFound("deceased"));
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("I2", field(IIT, IIT_STATUS));
        assertEquals("2", field(IIT, IIT_COUNTER_HAJAPATIKANA));
    }

    @Test
    public void eachSpecificConsecutiveStrikeWinsAndUnrelatedOutcomesResetIt() throws Exception {
        String[] reasons = {"travelled", "other"};
        for (int index = 0; index < reasons.length; index++) {
            data.execSQL("DELETE FROM visits");
            for (int visit = 0; visit < 3; visit++) {
                String id = "visit-" + visit;
                iitVisit(id, id, "2020-04-" + (11 + visit), 1, true, notFound(reasons[index]));
            }
            assertEquals(index == 0 ? "I7" : "I8", state().getStatus());
        }
        data.execSQL("DELETE FROM visits");
        iitVisit("refuse", "refuse", "2020-04-11", 1, true, initial("refused"));
        iitVisit("agree", "agree", "2020-04-12", 1, true, initial("agreed"));
        iitVisit("miss-1", "miss-1", "2020-04-13", 1, true, notReturned());
        iitVisit("travel", "travel", "2020-04-14", 1, true, notFound("travelled"));
        iitVisit("miss-2", "miss-2", "2020-04-15", 1, true, notReturned());
        assertEquals(1, state().getNotReturnedCount());
        assertEquals(0, state().getRefusedCount());
        assertEquals(0, state().getNotFoundCount());
        iitVisit("miss-3", "miss-3", "2020-04-16", 1, true, notReturned());
        iitVisit("miss-4", "miss-4", "2020-04-17", 1, true, notReturned());
        assertEquals("I6", state().getStatus());

        data.execSQL("DELETE FROM visits");
        for (int visit = 0; visit < 3; visit++) {
            String id = "refuse-" + visit;
            iitVisit(id, id, "2020-04-" + (11 + visit), 1, true, initial("refused"));
        }
        assertEquals("I5", state().getStatus());
    }

    @Test
    public void iitImmediateExitsProjectToMamaAndCorrectedSourceReopensBothCards() throws Exception {
        linkMama();
        String[] reasons = {"deceased", "moved_area", "moved_facility"};
        for (int index = 0; index < reasons.length; index++) {
            data.execSQL("DELETE FROM visits");
            iitVisit("original", "transfer", "2020-04-11", 1, true, notFound(reasons[index]));
            MotherMentorIitRepository.rebuild(db, CLIENT);
            assertTrue(state().requiresConfirmation());
            assertEquals("I" + (index + 2), field(IIT, IIT_STATUS));
            assertEquals(reasons[index], field(MOTHERS, MAMA_STATUS));
            assertEquals("1", field(MOTHERS, IS_CLOSED));
            if (index > 0) assertEquals("Referral facility", field(MOTHERS, MAMA_KITUO_KIPYA));
            iitVisit("corrected", "transfer", "2020-04-11", 2, true, notFound("travelled"));
            MotherMentorIitRepository.rebuild(db, CLIENT);
            assertEquals("active", field(IIT, IIT_STATUS));
            assertEquals("active", field(MOTHERS, MAMA_STATUS));
            assertEquals("1", field(IIT, IIT_COUNTER_AMESAFIRI));
        }
    }

    @Test
    public void mamaExitClosesOpenIitAndCannotCloseAnIitReenrolledLaterThatDay() throws Exception {
        linkMama();
        mamaVisit("mother-move", "2020-04-11", "moved_facility");
        MotherMentorMotherClosureRepository.rebuild(db, CLIENT);
        assertEquals("I4", field(IIT, IIT_STATUS));
        assertEquals("Referral facility", field(IIT, IIT_KITUO_KIPYA));
        enrollment(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-two",
                "2020-04-11T17:00:00+03:00");
        data.execSQL("UPDATE " + IIT + " SET is_closed = 0");
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("active", field(IIT, IIT_STATUS));
        assertEquals("iit-two", state().getEnrollmentId());
        assertEquals("moved_facility", field(MOTHERS, MAMA_STATUS));
    }

    @Test
    public void sourceReenrollmentDoesNotReopenTheOtherAlreadyClosedEpisode() throws Exception {
        linkMama();
        iitVisit("transfer", "transfer", "2020-04-11", 1, true, notFound("moved_area"));
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("moved_area", field(MOTHERS, MAMA_STATUS));
        enrollment(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-two",
                "2020-04-11T17:00:00+03:00");
        data.execSQL("UPDATE " + IIT + " SET is_closed = 0");
        MotherMentorIitRepository.rebuild(db, CLIENT);
        assertEquals("moved_area", field(MOTHERS, MAMA_STATUS));
        assertEquals("active", field(IIT, IIT_STATUS));
        assertEquals(0, state().getVisitCount());
        assertFalse(MotherMentorIitRepository.belongsToCurrentEnrollment(db, CLIENT, field("visits", VISIT_JSON)));
    }

    @Test
    public void newMamaEpisodeDoesNotReopenItsPreviouslyClosedIitTarget() throws Exception {
        linkMama();
        mamaVisit("mother-move", "2020-04-11", "moved_area");
        MotherMentorMotherClosureRepository.rebuild(db, CLIENT);
        assertEquals("I3", field(IIT, IIT_STATUS));
        enrollment(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, "mama-two",
                "2020-04-11T17:00:00+03:00");
        data.execSQL("UPDATE " + MOTHERS + " SET is_closed = 0");
        MotherMentorMotherClosureRepository.rebuild(db, CLIENT);
        assertEquals("active", field(MOTHERS, MAMA_STATUS));
        assertEquals("I3", field(IIT, IIT_STATUS));
        assertEquals("mama-one", field(IIT, IIT_CASCADE_SOURCE_EPISODE));
    }

    @Test
    public void sameDayMidnightLegacyTimestampUsesTheExactEpisodeAndReturnUsesMissedDateSnapshot()
            throws Exception {
        Map<String, String> agreement = initial("agreed");
        agreement.put(IIT_FOLLOW_UP_SCHEMA, "2");
        agreement.put(IIT_ENROLLMENT_ID, "iit-one");
        visit("agreement", "agreement", IIT_EVENT, "2020-04-10", "00:00:00", 1, true, agreement);
        assertEquals("referred", state().getReturnState());

        Map<String, String> returned = new LinkedHashMap<>();
        returned.put(FOLLOW_UP_STATUS, "found");
        returned.put(RETURNED_TO_FACILITY, "yes");
        returned.put(IIT_MISSED_APPOINTMENT_DATE, "2020-04-08");
        returned.put(FACILITY_RETURN_DATE, "2020-04-07");
        iitVisit("return-invalid", "return", "2020-04-11", 1, true, returned);
        assertFalse(state().isClosed());

        data.execSQL("UPDATE " + IIT + " SET expected_return_date = '2020-04-09'");
        returned.put(FACILITY_RETURN_DATE, "2020-04-08");
        iitVisit("return-valid", "return", "2020-04-11", 2, true, returned);
        assertEquals("I1", state().getStatus());
        assertFalse(state().requiresConfirmation());
        assertFalse(state().isMamaKinaraLinked());
    }

    @Test
    public void voidedIitImportPreservesTheSourceTimestampButSuppliesNoReportDetails() throws Exception {
        String date = "2020-04-10T11:30:00+03:00";
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, "source")
                .put(EVENT_BASE_ENTITY_ID, CLIENT).put(EVENT_TYPE, IIT_EVENT)
                .put(EVENT_DATE_TIMESTAMP, date).put(EVENT_VOIDED, true)
                .put("obs", new JSONArray().put(new JSONObject().put("fieldCode", FOLLOW_UP_STATUS)
                        .put("formSubmissionField", FOLLOW_UP_STATUS)
                        .put("values", new JSONArray().put("not_found"))
                        .put("humanReadableValues", new JSONArray().put("Not found"))));
        org.smartregister.domain.Event source = JsonFormUtils.gson.fromJson(
                event.toString(), org.smartregister.domain.Event.class);
        org.smartregister.chw.mothermentor.domain.Visit visit = NCUtils.eventToVisit(source);
        assertEquals(DateTime.parse(date).getMillis(), visit.getDate().getTime());
        assertTrue(visit.getVisitDetails().isEmpty());
    }

    @Test
    public void aCorrectedLegacyAppointmentDateKeepsTheSavedTracingEpisode() throws Exception {
        data.execSQL("DELETE FROM event");
        Map<String, String> agreement = initial("agreed");
        agreement.put(IIT_FOLLOW_UP_SCHEMA, "2");
        agreement.put(IIT_ENROLLMENT_ID, "legacy:" + CLIENT + ":2020-04-08");
        visit("agreement", "agreement", IIT_EVENT, "2020-04-10", 1, true, agreement);
        data.execSQL("UPDATE " + IIT + " SET expected_return_date = '2020-04-09'");
        assertEquals("2020-04-09", state().getMissedAppointmentDate());
        assertEquals("referred", state().getReturnState());
    }

    private void linkMama() throws Exception {
        data.execSQL("INSERT INTO " + MOTHERS + " (base_entity_id, pregnancy_status, is_closed, "
                + "last_interacted_with) VALUES (?, 'pregnant', 0, 1)", new Object[] {CLIENT});
        enrollment(Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, "mama-one",
                "2020-04-10T09:00:00+03:00");
    }

    private void enrollment(String eventType, String id, String date) throws Exception {
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, id)
                .put(EVENT_DATE_TIMESTAMP, date).put(EVENT_VERSION, 1);
        data.execSQL("INSERT INTO event VALUES (?, ?, ?, ?)",
                new Object[] {CLIENT, eventType, id, event.toString()});
    }

    private void iitVisit(String id, String source, String day, int version, boolean processed,
            Map<String, String> values) throws Exception {
        values.put(IIT_FOLLOW_UP_SCHEMA, "2");
        values.put(IIT_ENROLLMENT_ID, "iit-one");
        visit(id, source, IIT_EVENT, day, version, processed, values);
    }

    private void mamaVisit(String id, String day, String reason) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "not_found");
        values.put(MAMA_NOT_FOUND_REASON, reason);
        values.put(MAMA_KITUO_KIPYA, "Referral facility");
        values.put(MAMA_ENROLLMENT_ID, "mama-one");
        visit(id, id, MOTHER_EVENT, day, 1, true, values);
    }

    private void visit(String id, String source, String type, String day, int version,
            boolean processed, Map<String, String> values) throws Exception {
        visit(id, source, type, day, "12:00:00", version, processed, values);
    }

    private void visit(String id, String source, String type, String day, String clock, int version,
            boolean processed, Map<String, String> values) throws Exception {
        values.put(MOTHERMENTOR_VISIT_DATE, day);
        JSONArray answers = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            answers.put(new JSONObject().put("fieldCode", entry.getKey())
                    .put("values", new JSONArray().put(entry.getValue())));
        }
        String date = day + "T" + clock + "+03:00";
        long time = DateTime.parse(date).getMillis();
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, source)
                .put(EVENT_DATE_TIMESTAMP, date).put(EVENT_VERSION, version).put("obs", answers);
        data.execSQL("INSERT INTO visits VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '')",
                new Object[] {id, CLIENT, type, source, processed ? 1 : 0, time, time, time, event.toString()});
    }

    private static Map<String, String> notFound(String reason) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(FOLLOW_UP_STATUS, "not_found");
        result.put(IIT_NOT_FOUND_REASON, reason);
        if ("other".equals(reason)) result.put(IIT_NOT_FOUND_OTHER, "Client was unavailable");
        if (reason.startsWith("moved")) result.put(IIT_KITUO_KIPYA, "Referral facility");
        return result;
    }

    private static Map<String, String> initial(String agreement) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(FOLLOW_UP_STATUS, "found");
        result.put(SERVICE_INTERRUPTION_REASON, "forgot");
        result.put(IIT_RETURN_AGREEMENT, agreement);
        return result;
    }

    private static Map<String, String> notReturned() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(FOLLOW_UP_STATUS, "found");
        result.put(RETURNED_TO_FACILITY, "no");
        result.put(IIT_NOT_RETURNED_REASON, "forgot");
        return result;
    }

    private MotherMentorIitState state() {
        return MotherMentorIitRepository.getState(db, CLIENT, null);
    }

    private String field(String table, String field) {
        try (Cursor rows = data.rawQuery("SELECT " + field + " FROM " + table
                + " WHERE base_entity_id = ?", new String[] {CLIENT})) {
            assertTrue(rows.moveToFirst());
            return rows.getString(0);
        }
    }
}
