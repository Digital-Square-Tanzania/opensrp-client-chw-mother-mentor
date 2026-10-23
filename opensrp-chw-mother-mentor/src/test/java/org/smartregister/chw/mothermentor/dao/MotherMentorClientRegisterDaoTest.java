package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorClientRegisterDaoTest {
    private static final String TABLE = "ec_mothermentor_enrollment";
    private static final String SERVICE = "MotherMentor Services";
    private static final String CONDITION =
            TABLE
                    + ".is_closed = 0 AND ("
                    + TABLE
                    + ".status IS NULL OR "
                    + TABLE
                    + ".status = 'client') AND ("
                    + TABLE
                    + ".screening_status IS NULL OR "
                    + TABLE
                    + ".screening_status != '-')";

    private SQLiteDatabase database;

    @Before
    public void setUp() {
        database = SQLiteDatabase.create(null);
        database.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (id INTEGER PRIMARY KEY, base_entity_id"
                    + " TEXT, is_closed INTEGER DEFAULT 0, status TEXT, screening_status TEXT,"
                    + " ctc_number TEXT, pregnancy_status TEXT, child_dob TEXT,"
                    + " last_interacted_with INTEGER DEFAULT 0)");
        database.execSQL(
                "CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT, first_name"
                    + " TEXT, middle_name TEXT, last_name TEXT, dob TEXT, gender TEXT, unique_id"
                    + " TEXT, phone_number TEXT, other_phone_number TEXT)");
        database.execSQL(
                "CREATE TABLE ec_family (base_entity_id TEXT, primary_caregiver TEXT, family_head"
                    + " TEXT, first_name TEXT, village_town TEXT)");
        database.execSQL(
                "CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                    + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at"
                    + " INTEGER)");
        database.execSQL(
                "CREATE TABLE visit_details (visit_details_id TEXT, visit_id TEXT, visit_key TEXT,"
                    + " details TEXT, updated_at INTEGER)");
        database.execSQL(
                "INSERT INTO ec_family VALUES ('household', 'caregiver', 'head', 'Family',"
                    + " 'Village')");
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void projectionPreservesIdentityCtcAndIndependentClientsWithoutAncRows() {
        client("family-client", "Asha", "pregnant", "12-34-1234-123456", "household");
        client("independent", "Rehema", "breast_feeding", "12-34-1234-654321", null);

        try (Cursor rows =
                database.rawQuery(
                        select("", false, false, false, false) + " ORDER BY first_name, _id",
                        null)) {
            assertEquals(2, rows.getCount());
            rows.moveToFirst();
            assertEquals("family-client", string(rows, DBConstants.KEY.BASE_ENTITY_ID));
            assertEquals("12-34-1234-123456", string(rows, DBConstants.KEY.CTC_NUMBER));
            assertEquals("pregnant", string(rows, DBConstants.KEY.MOTHERMENTOR_CURRENT_STATUS));
            assertEquals("Village", string(rows, DBConstants.KEY.VILLAGE_TOWN));
            assertEquals(1, rows.getInt(rows.getColumnIndexOrThrow("_id")));
            rows.moveToNext();
            assertEquals("independent", string(rows, DBConstants.KEY.BASE_ENTITY_ID));
            assertNull(string(rows, DBConstants.KEY.RELATIONAL_ID));
            assertNull(string(rows, DBConstants.KEY.VILLAGE_TOWN));
            assertEquals("female", string(rows, DBConstants.KEY.GENDER));
        }
    }

    @Test
    public void completedCountExcludesDraftsOtherWorkflowsAndDuplicateVisitIds() {
        client("mother", "Asha", "pregnant", "123", "household");
        visit("service1", "mother", SERVICE, 1, 1);
        visit("service1", "mother", SERVICE, 1, 2);
        visit("service2", "mother", SERVICE, 1, 3);
        visit("draft", "mother", SERVICE, 0, 4);
        visit("iit", "mother", "MotherMentor Follow-up Visit", 1, 5);
        visit("partner", "mother", "MotherMentor Partner Follow-up Visit", 1, 6);
        visit("child", "mother", "MotherMentor Child EID Follow-up Visit", 1, 7);
        visit("household", "household", SERVICE, 1, 8);

        try (Cursor rows = database.rawQuery(select("", false, false, false, false), null)) {
            rows.moveToFirst();
            assertEquals(
                    2,
                    rows.getInt(
                            rows.getColumnIndexOrThrow(
                                    DBConstants.KEY.MOTHERMENTOR_COMPLETED_VISITS)));
        }
    }

    @Test
    public void latestRecordedServiceDateWinsOverImportTimeAndDrafts() {
        client("mother", "Asha", "pregnant", "123", "household");
        visit("older-imported-late", "mother", SERVICE, 1, 9000);
        status("older-imported-late", "pregnant", "2026-01-02");
        visit("newer-service", "mother", SERVICE, 1, 1000);
        status("newer-service", "breast_feeding", "03-02-2026");
        visit("draft", "mother", SERVICE, 0, 99999);
        status("draft", "not_pregnant", "2026-12-01");
        visit("iit", "mother", "MotherMentor Follow-up Visit", 1, 99999);
        status("iit", "not_pregnant", "2026-12-01");

        assertEquals("breast_feeding", currentStatus());
    }

    @Test
    public void unknownServiceAnswerDoesNotEraseLastKnownStatusOrInventAnEnrollmentStatus() {
        client("mother", "Asha", "pregnant", "123", "household");
        visit("known", "mother", SERVICE, 1, 1);
        status("known", "not_pregnant", "2026-01-01");
        visit("unknown", "mother", SERVICE, 1, 2);
        status("unknown", "unknown", "2026-02-01");
        assertEquals("not_pregnant", currentStatus());

        database.execSQL("DELETE FROM visits");
        database.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status = 'unknown'");
        assertNull(currentStatus());
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET pregnancy_status = ' breast_feeding '");
        assertEquals("breast_feeding", currentStatus());
    }

    @Test
    public void missingOrInvalidServiceDateFallsBackToTimestampWithDeterministicTieBreakers() {
        client("mother", "Asha", "pregnant", "123", "household");
        visit("first", "mother", SERVICE, 1, 1);
        status("first", "not_pregnant", "not-a-date");
        visit("second", "mother", SERVICE, 1, 2);
        status("second", "breast_feeding", "2026-02-30");
        assertEquals("breast_feeding", currentStatus());
    }

    @Test
    public void searchMatchesTrimmedNamesAndCtcAndTreatsWildcardCharactersLiterally() {
        client("one", " Asha ", "pregnant", "12-34-1234-123456", "household");
        client("percent", "Asha%", "pregnant", "other", "household");
        client("underscore", "Asha_", "pregnant", "other2", "household");
        client("quote", "O'Neil!", "pregnant", "other3", "household");

        assertIdsAndCount("  aSHA  ", false, false, false, false, "one", "percent", "underscore");
        assertIdsAndCount("asha client", false, false, false, false, "one");
        assertIdsAndCount("1234-123456", false, false, false, false, "one");
        assertIdsAndCount("%", false, false, false, false, "percent");
        assertIdsAndCount("_", false, false, false, false, "underscore");
        assertIdsAndCount("O'NEIL!", false, false, false, false, "quote");
        assertIdsAndCount("' OR 1=1 --", false, false, false, false);
    }

    @Test
    public void enabledCategoriesAreOrCombinedThenAndCombinedWithSearch() {
        client("pregnant", "Asha", "pregnant", "123", "household");
        client("breastfeeding", "Rehema", "breast_feeding", "456", "household");
        client("neither", "Neema", "not_pregnant", "789", "household");
        client("unknown", "Unknown", null, "000", "household");

        assertIdsAndCount("", true, true, true, false, "breastfeeding", "pregnant");
        assertIdsAndCount("asha", true, true, true, false, "pregnant");
        assertIdsAndCount(
                "", false, true, false, false, "breastfeeding", "neither", "pregnant", "unknown");
        assertIdsAndCount(
                "", true, false, false, false, "breastfeeding", "neither", "pregnant", "unknown");
    }

    @Test
    public void underTwoUsesRecordedDobAndStrictCalendarBoundaryButAlwaysIncludesBreastfeeding() {
        client("younger", "Asha", "not_pregnant", "1", "household");
        childDob("younger", "date('now', 'localtime', '-24 months', '+1 day')");
        client("boundary", "Boundary", "not_pregnant", "2", "household");
        childDob("boundary", "date('now', 'localtime', '-24 months')");
        client("older", "Older", "not_pregnant", "3", "household");
        childDob("older", "date('now', 'localtime', '-24 months', '-1 day')");
        client("native-date", "Native", "not_pregnant", "4", "household");
        childDob("native-date", "strftime('%d-%m-%Y', 'now', 'localtime', '-2 months')");
        client("future", "Future", "not_pregnant", "5", "household");
        childDob("future", "date('now', 'localtime', '+1 day')");
        client("missing", "Missing", "not_pregnant", "6", "household");
        client("invalid", "Invalid", "not_pregnant", "7", "household");
        childDob("invalid", "'2026-02-30'");
        client("unknown", "Unknown", null, "8", "household");
        childDob("unknown", "date('now', 'localtime', '-2 months')");
        client("bf-old-child", "Breastfeeding", "breast_feeding", "9", "household");
        childDob("bf-old-child", "date('now', 'localtime', '-3 years')");
        client("pregnant", "Pregnant", "pregnant", "10", "household");
        childDob("pregnant", "date('now', 'localtime', '-2 months')");

        assertIdsAndCount("", true, false, false, true, "bf-old-child", "native-date", "younger");
        assertIdsAndCount("asha", true, false, false, true, "younger");
    }

    @Test
    public void mainConditionAndUnfilteredOverridesKeepClosedAndNonClientsExcluded() {
        client("open", "Open", "pregnant", "1", "household");
        client("closed", "Closed", "pregnant", "2", "household");
        client("nonclient", "Nonclient", "pregnant", "3", "household");
        client("screened-out", "Screened", "pregnant", "4", "household");
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET is_closed = 1 WHERE base_entity_id ="
                    + " 'closed'");
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET status = 'contact' WHERE base_entity_id ="
                    + " 'nonclient'");
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET screening_status = '-' WHERE base_entity_id"
                    + " = 'screened-out'");

        assertIdsAndCount("", false, false, false, false, "open");
        assertFalse(select("", false, false, false, false).contains("ec_anc_register"));
    }

    @Test
    public void duplicateAncRecordsCannotMultiplyClientRowsOrCounts() {
        client("mother", "Asha", "pregnant", "1", "household");
        database.execSQL(
                "CREATE TABLE ec_anc_register (base_entity_id TEXT, is_closed INTEGER, edd TEXT)");
        database.execSQL(
                "INSERT INTO ec_anc_register VALUES ('mother', 0, '2026-12-01'), ('mother', 0,"
                    + " '2026-12-02')");

        assertIdsAndCount("", false, false, false, false, "mother");
    }

    @Test
    public void paginationPreservesInteractionSortAndUsesDeterministicClientIdsForTies() {
        client("a", "Zainab", "pregnant", "1", "household");
        client("b", "Asha", "pregnant", "2", "household");
        client("c", "Rehema", "pregnant", "3", "household");
        database.execSQL("UPDATE ec_mothermentor_enrollment SET last_interacted_with = 20");
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET last_interacted_with = 30 WHERE"
                    + " base_entity_id = 'b'");

        assertEquals(Arrays.asList("b", "a"), pageIds(2, 0));
        assertEquals(Arrays.asList("c"), pageIds(2, 2));
        assertIdsAndCount("", false, false, false, false, "a", "b", "c");
    }

    @Test(expected = IllegalArgumentException.class)
    public void paginationRejectsZeroLimit() {
        MotherMentorClientRegisterDao.selectClientsPage(
                CONDITION, "", false, false, false, false, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void paginationRejectsNegativeOffset() {
        MotherMentorClientRegisterDao.selectClientsPage(
                CONDITION, "", false, false, false, false, 20, -1);
    }

    private List<String> pageIds(int limit, int offset) {
        List<String> ids = new ArrayList<>();
        try (Cursor rows =
                database.rawQuery(
                        MotherMentorClientRegisterDao.selectClientsPage(
                                CONDITION, "", false, false, false, false, limit, offset),
                        null)) {
            while (rows.moveToNext()) ids.add(string(rows, DBConstants.KEY.BASE_ENTITY_ID));
        }
        return ids;
    }

    private void client(String id, String first, String status, String ctc, String household) {
        ContentValues enrollment = new ContentValues();
        enrollment.put("base_entity_id", id);
        enrollment.put("pregnancy_status", status);
        enrollment.put("ctc_number", ctc);
        database.insertOrThrow(TABLE, null, enrollment);
        ContentValues member = new ContentValues();
        member.put("base_entity_id", id);
        member.put("relational_id", household);
        member.put("first_name", first);
        member.put("middle_name", "");
        member.put("last_name", "Client");
        member.put("dob", "2000-01-01");
        member.put("gender", "female");
        member.put("unique_id", "qa-" + id);
        database.insertOrThrow("ec_family_member", null, member);
    }

    private void visit(String id, String member, String type, int processed, long timestamp) {
        database.execSQL(
                "INSERT INTO visits VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[] {id, member, type, processed, timestamp, timestamp, timestamp});
    }

    private void status(String visit, String status, String date) {
        database.execSQL(
                "INSERT INTO visit_details VALUES (?, ?, 'visit_pregnancy_status', ?, 1)",
                new Object[] {visit + "-status", visit, status});
        database.execSQL(
                "INSERT INTO visit_details VALUES (?, ?, 'mothermentor_visit_date', ?, 1)",
                new Object[] {visit + "-date", visit, date});
    }

    private void childDob(String id, String expression) {
        database.execSQL(
                "UPDATE ec_mothermentor_enrollment SET child_dob = "
                        + expression
                        + " WHERE base_entity_id = ?",
                new Object[] {id});
    }

    private String currentStatus() {
        try (Cursor rows = database.rawQuery(select("", false, false, false, false), null)) {
            rows.moveToFirst();
            return string(rows, DBConstants.KEY.MOTHERMENTOR_CURRENT_STATUS);
        }
    }

    private String select(
            String search, boolean on, boolean pregnant, boolean breastfeeding, boolean underTwo) {
        return MotherMentorClientRegisterDao.selectClients(
                CONDITION, search, on, pregnant, breastfeeding, underTwo);
    }

    private void assertIdsAndCount(
            String search,
            boolean on,
            boolean pregnant,
            boolean breastfeeding,
            boolean underTwo,
            String... expected) {
        List<String> ids = new ArrayList<>();
        try (Cursor rows =
                database.rawQuery(
                        select(search, on, pregnant, breastfeeding, underTwo)
                                + " ORDER BY base_entity_id",
                        null)) {
            while (rows.moveToNext()) ids.add(string(rows, DBConstants.KEY.BASE_ENTITY_ID));
        }
        assertEquals(Arrays.asList(expected), ids);
        try (Cursor count =
                database.rawQuery(
                        MotherMentorClientRegisterDao.countClients(
                                CONDITION, search, on, pregnant, breastfeeding, underTwo),
                        null)) {
            count.moveToFirst();
            assertEquals(expected.length, count.getInt(0));
        }
    }

    private String string(Cursor cursor, String key) {
        return cursor.getString(cursor.getColumnIndexOrThrow(key));
    }
}
