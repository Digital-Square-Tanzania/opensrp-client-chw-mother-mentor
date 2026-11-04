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
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.util.Constants;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherDeliveryRepositoryTest {
    private static final String MOTHER = "mother";
    private static final String ENROLLED = "2026-10-01T08:00:00.000+03:00";
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                + " form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at INTEGER,"
                + " created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','pregnant','',?, '2026-10-01',1,1)",
                new Object[] {ENROLLED});
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household')");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; })
                .when(db).execSQL(anyString());
        doAnswer(call -> { data.execSQL(call.getArgument(0), call.getArgument(1)); return null; })
                .when(db).execSQL(anyString(), any(Object[].class));
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
        enrollment("episode-one", ENROLLED, "pregnant", "");
    }

    @After
    public void tearDown() { data.close(); }

    @Test
    public void upgradeIsRepeatableAndProjectsLiveBirthWithoutRewritingEnrollmentOrClosedState() throws Exception {
        visit("birth", "birth-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        MotherMentorMotherDeliveryRepository.upgrade(db);
        MotherMentorMotherDeliveryRepository.upgrade(db);
        assertEquals("2026-10-19", column(MAMA_TAREHE_KUJIFUNGUA));
        assertEquals("2026-10-19", column(CHILD_DOB));
        assertEquals("breast_feeding", column(PREGNANCY_STATUS));
        assertEquals("1", column(IS_CLOSED));
        assertEquals("1", column(LAST_INTERACTED_WITH));
        assertEquals("pregnant", MotherMentorMotherVisitDao.getContext(db, MOTHER)
                .getEnrollmentPregnancyStatus());
        assertEquals("", MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentChildDob());
        verify(db, times(1)).execSQL(contains("ADD COLUMN"));
    }

    @Test
    public void sourceCorrectionAndEditingExclusionDoNotReuseEarlierVersion() throws Exception {
        visit("old", "birth-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        visit("corrected", "birth-source", "episode-one", "20-10-2026", 2, true,
                "not_pregnant", "preterm", "18-10-2026");
        assertEquals(new LocalDate(2026, 10, 18), MotherMentorMotherDeliveryRepository
                .getDeliveryDate(db, MOTHER, null));
        assertNull(MotherMentorMotherDeliveryRepository.getDeliveryDate(db, MOTHER, "old"));
        data.delete("visits", "form_submission_id=?", new String[] {"birth-source"});
        MotherMentorMotherDeliveryRepository.upgrade(db);
        assertEquals("", column(CHILD_DOB));
        assertEquals("pregnant", column(PREGNANCY_STATUS));
        assertEquals("", column(MAMA_TAREHE_KUJIFUNGUA));
    }

    @Test
    public void draftsFutureDatesAndInvalidOutcomesCannotProjectDelivery() throws Exception {
        visit("draft", "draft-source", "episode-one", "20-10-2026", 1, false,
                "not_pregnant", "delivered", "19-10-2026");
        visit("future", "future-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "preterm", "21-10-2026");
        visit("invalid", "invalid-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "delivered", "31-02-2026");
        assertNull(MotherMentorMotherDeliveryRepository.getDeliveryDate(db, MOTHER));
    }

    @Test
    public void inactiveStaleDeliveryDateCannotBecomePregnancyOrLossProjection() throws Exception {
        visit("pregnant", "pregnant-source", "episode-one", "20-10-2026", 1, true,
                "pregnant", "delivered", "19-10-2026");
        assertNull(MotherMentorMotherDeliveryRepository.getDeliveryDate(db, MOTHER));
        data.delete("visits", null, null);
        visit("other", "other-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "other", "19-10-2026");
        assertNull(MotherMentorMotherDeliveryRepository.getDeliveryDate(db, MOTHER));
    }

    @Test
    public void newEnrollmentResetsProjectionAndRetainsPriorVisitHistory() throws Exception {
        visit("birth", "birth-source", "episode-one", "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        MotherMentorMotherDeliveryRepository.upgrade(db);
        enrollment("episode-two", "2026-10-21T08:00:00.000+03:00", "pregnant", "");
        data.execSQL("UPDATE ec_mothermentor_enrollment SET event_date='2026-10-21T08:00:00.000+03:00'");
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        assertEquals("", column(CHILD_DOB));
        assertEquals("", column(MAMA_TAREHE_KUJIFUNGUA));
        assertEquals("pregnant", column(PREGNANCY_STATUS));
        try (Cursor rows = data.rawQuery("SELECT COUNT(*) FROM visits", null)) {
            assertTrue(rows.moveToFirst()); assertEquals(1, rows.getInt(0));
        }
    }

    @Test
    public void originalBreastfeedingUsesImmutableEnrollmentDobInsteadOfMutableProjection() throws Exception {
        data.delete("event", null, null);
        enrollment("episode-one", ENROLLED, "bf", "01-08-2026");
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status='pregnant', child_dob='02-08-2026'");
        MotherMentorMotherDeliveryRepository.upgrade(db);
        assertEquals("breast_feeding", column(PREGNANCY_STATUS));
        assertEquals("01-08-2026", column(CHILD_DOB));
        assertEquals("2026-08-01", column(MAMA_TAREHE_KUJIFUNGUA));
    }

    @Test
    public void legacyEnrollmentRetainsItsOriginalPregnancyAndDobWhenEditingTheProjectedBirth() throws Exception {
        data.delete("event", null, null);
        String episode = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        visit("birth", "birth-source", episode, "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        MotherMentorMotherDeliveryRepository.upgrade(db);
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        assertEquals("pregnant", MotherMentorMotherVisitDao.getContext(db, MOTHER)
                .getEnrollmentPregnancyStatus());
        assertEquals("2026-10-19", column(CHILD_DOB));
        MotherMentorMotherDeliveryRepository.State editing =
                MotherMentorMotherDeliveryRepository.getState(db, MOTHER, "birth");
        assertEquals("pregnant", editing.getPregnancyStatus());
        assertEquals("", editing.getChildDob());
        assertEquals("", editing.getAnswers().get(MAMA_ENROLLMENT_CHILD_DOB));
        assertNull(editing.getDeliveryDate());
    }

    @Test
    public void removingTheOnlyLegacyBirthRestoresTheFrozenEnrollmentBaseline() throws Exception {
        data.delete("event", null, null);
        String episode = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        MotherMentorMotherDeliveryRepository.upgrade(db);
        visit("birth", "birth-source", episode, "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        assertEquals("breast_feeding", column(PREGNANCY_STATUS));
        data.delete("visits", null, null);
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        assertEquals("pregnant", column(PREGNANCY_STATUS));
        assertEquals("", column(CHILD_DOB));
        assertEquals("", column(MAMA_TAREHE_KUJIFUNGUA));
        assertEquals("pregnant", MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentPregnancyStatus());
    }

    @Test
    public void voidingTheOnlyLegacyBirthDoesNotPromoteItsProjectionToOriginalEnrollment() throws Exception {
        data.delete("event", null, null);
        String episode = MotherMentorMotherVisitDao.getContext(db, MOTHER).getEnrollmentId();
        MotherMentorMotherDeliveryRepository.upgrade(db);
        visit("birth", "birth-source", episode, "20-10-2026", 1, true,
                "not_pregnant", "delivered", "19-10-2026");
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        try (Cursor rows = data.rawQuery("SELECT visit_json FROM visits WHERE visit_id='birth'", null)) {
            assertTrue(rows.moveToFirst());
            JSONObject voided = new JSONObject(rows.getString(0)).put("version", 2).put("voided", true);
            data.execSQL("UPDATE visits SET visit_json=?", new Object[] {voided.toString()});
        }
        MotherMentorMotherDeliveryRepository.rebuild(db, MOTHER);
        assertEquals("pregnant", column(PREGNANCY_STATUS));
        assertEquals("", column(CHILD_DOB));
        assertEquals("", column(MAMA_TAREHE_KUJIFUNGUA));
    }

    private String column(String key) {
        try (Cursor rows = data.rawQuery("SELECT " + key + " FROM ec_mothermentor_enrollment", null)) {
            assertTrue(rows.moveToFirst()); return rows.getString(0);
        }
    }

    private void enrollment(String id, String day, String pregnancy, String dob) throws Exception {
        JSONObject event = new JSONObject().put("formSubmissionId", id).put("eventDate", day)
                .put("obs", obs(PREGNANCY_STATUS, pregnancy, CHILD_DOB, dob));
        ContentValues row = new ContentValues();
        row.put("baseEntityId", MOTHER); row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT);
        row.put("eventDate", day); row.put("formSubmissionId", id); row.put("json", event.toString());
        data.insertOrThrow("event", null, row);
    }

    private void visit(String id, String source, String episode, String service, int version,
            boolean processed, String status, String outcome, String delivery) throws Exception {
        JSONObject event = new JSONObject().put("formSubmissionId", source).put("eventDate", ENROLLED)
                .put("version", version).put("obs", obs(MAMA_VISIT_SCHEMA, "2",
                        MAMA_ENROLLMENT_ID, episode, MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant",
                        MAMA_ENROLLMENT_CHILD_DOB, "", ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                        MOTHERMENTOR_VISIT_DATE, service, VISIT_PREGNANCY_STATUS, status,
                        MAMA_PREGNANCY_OUTCOME, outcome, MAMA_PREGNANCY_OUTCOME_OTHER, "Explanation",
                        MAMA_TAREHE_KUJIFUNGUA, delivery));
        ContentValues row = new ContentValues();
        row.put(VISIT_ID, id); row.put(BASE_ENTITY_ID, MOTHER);
        row.put(VISIT_TYPE, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        row.put(FORM_SUBMISSION_ID, source); row.put(PROCESSED, processed ? 1 : 0);
        row.put(VISIT_DATE, version); row.put(UPDATED_AT, version); row.put(CREATED_AT, version);
        row.put(VISIT_JSON, event.toString()); data.insertOrThrow("visits", null, row);
    }

    private JSONArray obs(String... values) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; i < values.length; i += 2) result.put(new JSONObject()
                .put("fieldCode", values[i]).put("values", new JSONArray().put(values[i + 1])));
        return result;
    }
}
