package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
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
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;

import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherVisitDaoTest {
    private static final String MOTHER = "mother";
    private static final String ENROLLED = "2026-10-01T08:00:00.000+03:00";
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, "
                + "pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with "
                + "INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, "
                + "formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                + " form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at "
                + "INTEGER,"
                + " created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','pregnant','',?, "
                        + "'2026-10-01',1,0)",
                new Object[] {ENROLLED});
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household')");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        enrollment("enrollment-one", ENROLLED, "pregnant", "", 1, false);
    }

    @After
    public void tearDown() {
        data.close();
    }

    @Test
    public void originalEnrollmentSurvivesMutableProjectionAndClosedState() {
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status='breast_feeding',"
                + " child_dob='20-10-2026', is_closed=1");
        MotherMentorMotherVisitDao.Context context =
                MotherMentorMotherVisitDao.getContext(db, MOTHER);
        assertEquals("enrollment-one", context.getEnrollmentId());
        assertEquals("pregnant", context.getEnrollmentPregnancyStatus());
        assertEquals("20-10-2026", context.getChildDob());
        assertEquals("", context.getEnrollmentChildDob());
        assertEquals("household", context.getFamilyId());
        assertEquals(new DateTime(ENROLLED).getMillis(), context.getEnrollmentTime());
    }

    @Test
    public void legacyOriginalPregnancySurvivesProjectionWithoutRawEnrollmentEvent()
            throws Exception {
        data.delete("event", null, null);
        String episode = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        visit("first", "first-form", episode, "02-10-2026", 1, true, false, 1);
        JSONObject event;
        try (android.database.Cursor cursor = data.rawQuery(
                     "SELECT visit_json FROM visits WHERE visit_id='first'", null)) {
            assertTrue(cursor.moveToFirst());
            event = new JSONObject(cursor.getString(0));
        }
        event.getJSONArray("obs").put(new JSONObject()
                        .put("fieldCode", MAMA_ENROLLMENT_PREGNANCY_STATUS)
                        .put("values", new JSONArray().put("pregnant")));
        ContentValues values = new ContentValues();
        values.put(VISIT_JSON, event.toString());
        data.update("visits", values, "visit_id='first'", null);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status='breast_feeding', "
                + "child_dob='02-10-2026'");
        assertEquals("pregnant",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
        assertEquals("02-10-2026", MotherMentorMotherVisitDao.getContext(db, MOTHER).getChildDob());
        data.execSQL(
                "UPDATE ec_mothermentor_enrollment SET event_date='2026-11-01T08:00:00.000+03:00'");
        assertEquals("breast_feeding",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
    }

    @Test
    public void frozenLegacyContextSurvivesDeletingOrVoidingTheOnlyVisit() throws Exception {
        data.delete("event", null, null);
        String episode = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        data.execSQL("CREATE TABLE mothermentor_mother_enrollment_context (mama_enrollment_id TEXT "
                     + "PRIMARY KEY,"
                + "base_entity_id TEXT, mama_enrollment_pregnancy_status TEXT, child_dob TEXT)");
        data.execSQL("INSERT INTO mothermentor_mother_enrollment_context VALUES (?, "
                     + "'mother','pregnant','')",
                new Object[] {episode});
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status='breast_feeding', "
                     + "child_dob='02-10-2026'");
        visit("only", "only-form", episode, "02-10-2026", 1, true, true, 1);
        assertEquals("pregnant",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
        assertEquals("", MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentChildDob());
        data.delete("visits", null, null);
        assertEquals("pregnant",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
        data.execSQL(
                "UPDATE ec_mothermentor_enrollment SET event_date='2026-11-01T08:00:00.000+03:00'");
        assertEquals("breast_feeding",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
        enrollment(
                "new-raw", "2026-11-01T08:00:00.000+03:00", "not_pregnant", "01-08-2026", 1, false);
        assertEquals("not_pregnant",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
    }

    @Test
    public void latestSourceRevisionDefinesOriginalEnrollmentValues() throws Exception {
        enrollment("enrollment-one", ENROLLED, "breast_feeding", "01-08-2026", 2, false);
        MotherMentorMotherVisitDao.Context context =
                MotherMentorMotherVisitDao.getContext(db, MOTHER);
        assertEquals("breast_feeding", context.getEnrollmentPregnancyStatus());
        assertEquals("01-08-2026", context.getEnrollmentChildDob());
    }

    @Test
    public void canonicalVisitsAreFinalizedClinicalDateOrderedAndCurrentEpisodeOnly()
            throws Exception {
        visit("later", "later-form", "enrollment-one", "04-10-2026", 1, true, false, 1);
        visit("earlier", "earlier-form", "enrollment-one", "02-10-2026", 1, true, false, 9);
        visit("draft", "draft-form", "enrollment-one", "03-10-2026", 1, false, false, 10);
        visit("old", "old-form", "earlier-enrollment", "03-10-2026", 1, true, false, 11);
        visit("future", "future-form", "enrollment-one", LocalDate.now().plusDays(1).toString(), 1,
                true, false, 12);
        List<Visit> visits = MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null);
        assertEquals(2, visits.size());
        assertEquals("earlier", visits.get(0).getVisitId());
        assertEquals("later", visits.get(1).getVisitId());
    }

    @Test
    public void newerVoidedOrInvalidRevisionSuppressesItsEarlierAlias() throws Exception {
        visit("old", "same-form", "enrollment-one", "02-10-2026", 1, true, false, 8);
        visit("new", "same-form", "enrollment-one", "02-10-2026", 2, true, true, 2);
        assertTrue(MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null).isEmpty());
        visit("invalid", "same-form", "enrollment-one", "31-02-2026", 3, true, false, 1);
        assertTrue(MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null).isEmpty());
    }

    @Test
    public void revisionsAreDeduplicatedAndExcludingAnyAliasExcludesTheSource() throws Exception {
        visit("old", "same-form", "enrollment-one", "02-10-2026", 1, true, false, 8);
        visit("new", "same-form", "enrollment-one", "03-10-2026", 2, true, false, 2);
        assertEquals("new",
                MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null)
                        .get(0)
                        .getVisitId());
        assertTrue(MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, "old").isEmpty());
        assertTrue(
                MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, "same-form").isEmpty());
        assertEquals(1, MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, "").size());
    }

    @Test
    public void newEnrollmentUuidOnTheSameDayDoesNotInheritOldEvidence() throws Exception {
        visit("old", "old-form", "enrollment-one", "02-10-2026", 1, true, false, 1);
        String newer = "2026-10-01T15:00:00.000+03:00";
        enrollment("enrollment-two", newer, "not_pregnant", "01-08-2026", 1, false);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET event_date=?", new Object[] {newer});
        visit("new", "new-form", "enrollment-two", "03-10-2026", 1, true, false, 2);
        assertEquals("enrollment-two",
                MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId());
        List<Visit> current = MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null);
        assertEquals(1, current.size());
        assertEquals("new", current.get(0).getVisitId());
    }

    @Test
    public void lateRawEnrollmentAcceptsItsStableLegacyDateAlias() throws Exception {
        data.delete("event", null, null);
        String legacy = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        assertTrue(legacy.startsWith("legacy:mother:"));
        visit("local", "local-form", legacy, "02-10-2026", 1, true, false, 1);
        enrollment("enrollment-one", ENROLLED, "pregnant", "", 1, false);
        assertEquals(1, MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null).size());
        assertTrue(MotherMentorMotherVisitDao.belongsToCurrentEnrollment(db, MOTHER,
                MotherMentorMotherVisitDao.getFinalizedVisits(db, MOTHER, null).get(0).getJson()));
    }

    @Test
    public void clinicalDateIsStrictAndPreservesEventCalendarOffset() throws Exception {
        Visit visit = new Visit();
        visit.setDate(new Date());
        visit.setJson(new JSONObject()
                        .put("eventDate", "2026-10-02T00:30:00.000+14:00")
                        .put("obs", new JSONArray())
                        .toString());
        assertEquals(new LocalDate(2026, 10, 2), MotherMentorMotherVisitDao.getServiceDate(visit));
        visit.setJson(new JSONObject()
                        .put("eventDate", "2026-10-02T08:00:00.000+03:00")
                        .put("obs", obs(MOTHERMENTOR_VISIT_DATE, "31-02-2026"))
                        .toString());
        assertNull(MotherMentorMotherVisitDao.getServiceDate(visit));
    }

    private void enrollment(String id, String date, String pregnancy, String childDob, int version,
            boolean voided) throws Exception {
        JSONObject event =
                new JSONObject()
                        .put("formSubmissionId", id)
                        .put("eventDate", date)
                        .put("version", version)
                        .put("voided", voided)
                        .put("obs", obs(PREGNANCY_STATUS, pregnancy, CHILD_DOB, childDob));
        ContentValues row = new ContentValues();
        row.put("baseEntityId", MOTHER);
        row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT);
        row.put("eventDate", date);
        row.put("formSubmissionId", id);
        row.put("json", event.toString());
        data.insertOrThrow("event", null, row);
    }

    private void visit(String id, String form, String episode, String day, int version,
            boolean finalized, boolean voided, int importOrder) throws Exception {
        JSONObject event = new JSONObject()
                                   .put("formSubmissionId", form)
                                   .put("eventDate", ENROLLED)
                                   .put("version", version)
                                   .put("voided", voided)
                                   .put("obs",
                                           obs(MAMA_ENROLLMENT_ID, episode,
                                                   ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS,
                                                   "found", MOTHERMENTOR_VISIT_DATE, day));
        ContentValues row = new ContentValues();
        row.put(VISIT_ID, id);
        row.put(BASE_ENTITY_ID, MOTHER);
        row.put(VISIT_TYPE, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        row.put(FORM_SUBMISSION_ID, form);
        row.put(PROCESSED, finalized ? 1 : 0);
        row.put(VISIT_DATE, importOrder);
        row.put(UPDATED_AT, importOrder);
        row.put(CREATED_AT, importOrder);
        row.put(VISIT_JSON, event.toString());
        data.insertOrThrow("visits", null, row);
    }

    private JSONArray obs(String... values) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; i < values.length; i += 2)
            result.put(new JSONObject()
                            .put("fieldCode", values[i])
                            .put("values", new JSONArray().put(values[i + 1])));
        return result;
    }
}
