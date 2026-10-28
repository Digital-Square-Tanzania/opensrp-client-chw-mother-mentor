package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

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
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorDeceasedVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.repository.Repository;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorDeceasedClientTest {
    private static final String CLIENT = "client-with-'quote";
    private static final String[] ENROLLMENT_TABLES = {
        Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
        Constants.TABLES.MOTHERMENTOR_ENROLL_IIT,
        Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER,
        Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID
    };
    private MotherMentorLibrary previous;
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase cipher;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, dod TEXT)");
        data.execSQL("CREATE TABLE visits (base_entity_id TEXT, visit_type TEXT, processed INTEGER,"
                + " visit_json TEXT, visit_date TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_followup_visit (base_entity_id TEXT,"
                + " entity_id TEXT, follow_up_outcome TEXT, is_closed INTEGER, follow_up_status TEXT)");
        data.execSQL("CREATE TABLE event (formSubmissionId TEXT, json TEXT)");
        for (String table : ENROLLMENT_TABLES) {
            data.execSQL("CREATE TABLE " + table + " (base_entity_id TEXT, is_closed INTEGER)");
        }
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        cipher = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(repository.getWritableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        when(cipher.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
    }

    @Test
    public void sharedDeathRecordTakesPrecedenceAndPreventsAnotherRemoval() {
        data.execSQL("INSERT INTO ec_family_member VALUES (?, ?)",
                new Object[] {CLIENT, "2026-10-20"});
        assertTrue(MotherMentorDao.hasRecordedDeath(CLIENT));
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertEquals(date("2026-10-20"), MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void emptyAndLiteralNullDeathDatesAreNotRecordedDeaths() {
        for (String missing : new String[] {null, "", " ", "null"}) {
            data.delete("ec_family_member", null, null);
            data.execSQL("INSERT INTO ec_family_member VALUES (?, ?)",
                    new Object[] {CLIENT, missing});
            assertFalse(MotherMentorDao.hasRecordedDeath(CLIENT));
            assertFalse(MotherMentorDao.isClientDeceased(CLIENT));
        }
    }

    @Test
    public void everyCurrentFollowUpCategoryRecognizesFinalizedDeath() throws Exception {
        for (MotherMentorFollowUpType type : MotherMentorFollowUpType.values()) {
            data.delete("visits", null, null);
            insertVisit(CLIENT, type.getEventType(), 1,
                    event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
            assertTrue(type.name(), MotherMentorDao.isClientDeceased(CLIENT));
            assertFalse(MotherMentorDao.hasRecordedDeath(CLIENT));
            assertEquals(date("2026-10-25"), MotherMentorDao.getClientDeathDate(CLIENT));
        }
    }

    @Test
    public void maternalServiceAndIitTracingOutcomesRecognizeDeath() throws Exception {
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.IIT_TRACING_OUTCOME, "deceased"), "9");
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        data.delete("visits", null, null);
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
    }

    @Test
    public void draftsAndOtherModulesCannotMarkTheClientDeceased() throws Exception {
        insertVisit(CLIENT, MotherMentorFollowUpType.PARTNER.getEventType(), 0,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        insertVisit(CLIENT, "ANC Home Visit", 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        assertFalse(MotherMentorDao.isClientDeceased(CLIENT));
        assertNull(MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void laterOrdinaryVisitCannotResurrectADeceasedClient() throws Exception {
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "continuing_services"), "20");
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertEquals(date("2026-10-25"), MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void deathLookupBindsOnlyTheRequestedClient() throws Exception {
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertFalse(MotherMentorDao.isClientDeceased("' OR 1 = 1 --"));
    }

    @Test
    public void legacyDirectFormRetainsDeathAndUsesCanonicalEventDate() throws Exception {
        String json = new JSONObject(event(DBConstants.KEY.FOLLOW_UP_OUTCOME, "client_deceased"))
                .put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-20T10:00:00+03:00")
                .put("obs", new JSONArray()).toString();
        data.execSQL("INSERT INTO ec_mothermentor_followup_visit "
                        + "(base_entity_id, entity_id, follow_up_outcome, is_closed) VALUES (?, ?, ?, ?)",
                new Object[] {"legacy-death", CLIENT, "client_deceased", 1});
        data.execSQL("INSERT INTO event VALUES (?, ?)", new Object[] {"legacy-death", json});
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertEquals(date("2026-10-20T10:00:00+03:00"),
                MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void synchronizedCurrentFollowUpRecognizesDeathWithoutLocalVisit() throws Exception {
        String json = event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased");
        data.execSQL("INSERT INTO ec_mothermentor_followup_visit "
                        + "(base_entity_id, entity_id, follow_up_status, is_closed) VALUES (?, ?, ?, ?)",
                new Object[] {"synced-death", CLIENT, "deceased", 1});
        data.execSQL("INSERT INTO event VALUES (?, ?)", new Object[] {"synced-death", json});
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertEquals(date("2026-10-25"), MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void missingLegacyDateDoesNotInventTodayOrLoseDeathStatus() {
        data.execSQL("INSERT INTO ec_mothermentor_followup_visit "
                        + "(base_entity_id, entity_id, follow_up_outcome, is_closed) VALUES (?, ?, ?, ?)",
                new Object[] {"legacy-death", CLIENT, "client_deceased", 0});
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        assertNull(MotherMentorDao.getClientDeathDate(CLIENT));
    }

    @Test
    public void malformedSavedJsonDoesNotMarkClientDeceased() {
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1, "{", "9");
        assertFalse(MotherMentorDao.isClientDeceased(CLIENT));
    }

    @Test
    public void closureIsIdempotentAndRetainsOwnAndRelatedVisitHistory() throws Exception {
        for (String table : ENROLLMENT_TABLES) {
            data.execSQL("INSERT INTO " + table + " VALUES (?, 0)", new Object[] {CLIENT});
            data.execSQL("INSERT INTO " + table + " VALUES ('related-client', 0)");
        }
        insertVisit(CLIENT, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1,
                event(DBConstants.KEY.FOLLOW_UP_STATUS, "deceased"), "9");
        MotherMentorDao.closeDeceasedClientEnrollments(CLIENT);
        MotherMentorDao.closeDeceasedClientEnrollments(CLIENT);
        for (String table : ENROLLMENT_TABLES) {
            try (android.database.Cursor cursor = data.rawQuery(
                    "SELECT is_closed FROM " + table + " WHERE base_entity_id = ?",
                    new String[] {CLIENT})) {
                assertTrue(cursor.moveToFirst());
                assertEquals(1, cursor.getInt(0));
            }
            try (android.database.Cursor cursor = data.rawQuery(
                    "SELECT is_closed FROM " + table + " WHERE base_entity_id = 'related-client'",
                    null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
            }
        }
        assertTrue(MotherMentorDao.isClientDeceased(CLIENT));
        try (android.database.Cursor cursor = data.rawQuery("SELECT COUNT(*) FROM visits", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }

    @Test
    public void activeConditionHidesSharedDeathEvenWhenEnrollmentWasReopenedBySync() {
        for (String table : ENROLLMENT_TABLES) {
            data.execSQL("INSERT INTO " + table + " VALUES (?, 0)", new Object[] {CLIENT});
        }
        data.execSQL("INSERT INTO ec_family_member VALUES (?, ?)",
                new Object[] {CLIENT, "2026-10-20"});
        for (String table : ENROLLMENT_TABLES) {
            try (android.database.Cursor cursor = data.rawQuery(
                    "SELECT COUNT(*) FROM " + table + " WHERE "
                            + MotherMentorDao.getActiveClientCondition(table), null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
            }
        }
    }

    @Test
    public void missingIdentityDoesNotQueryOrCloseAnything() {
        for (String client : new String[] {null, "", " "}) {
            assertFalse(MotherMentorDao.isClientDeceased(client));
            assertFalse(MotherMentorDao.hasRecordedDeath(client));
            assertNull(MotherMentorDao.getClientDeathDate(client));
            MotherMentorDao.closeDeceasedClientEnrollments(client);
        }
        verifyNoInteractions(cipher);
    }

    private void insertVisit(String client, String type, int processed, String json, String stamp) {
        data.execSQL("INSERT INTO visits VALUES (?, ?, ?, ?, ?)",
                new Object[] {client, type, processed, json, stamp});
    }

    private static String event(String key, String value) throws Exception {
        JSONArray obs = new JSONArray()
                .put(new JSONObject().put("fieldCode", key)
                        .put("values", new JSONArray().put(value)))
                .put(new JSONObject().put("fieldCode", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE)
                        .put("values", new JSONArray().put("25-10-2026")));
        return new JSONObject().put("obs", obs).toString();
    }

    private static Date date(String value) {
        return MotherMentorDeceasedVisitUtils.parseDate(value);
    }
}
