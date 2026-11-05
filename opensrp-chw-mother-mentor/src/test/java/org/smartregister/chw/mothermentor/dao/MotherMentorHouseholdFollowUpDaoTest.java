package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
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
public class MotherMentorHouseholdFollowUpDaoTest {
    private SQLiteDatabase database;

    @Before public void setUp() {
        database = SQLiteDatabase.create(null);
        database.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, is_closed INTEGER)");
        database.execSQL("CREATE TABLE ec_mothermentor_household_enrolment (household_id TEXT,"
                + " is_closed INTEGER, consent TEXT, kaya_status TEXT, kaya_sababu_ya_kufungwa TEXT,"
                + " kaya_tarehe_ya_kufungwa TEXT, last_interacted_with TEXT)");
    }

    @After public void tearDown() { database.close(); }

    @Test public void activeAndClosedListsUseTheSamePartitionAsTheirCounts() {
        household("active", 0, 0, "ndiyo", "active");
        household("legacy", 0, 0, "ndiyo", "");
        household("declined", 0, 0, "hapana", "active");
        household("moved", 0, 1, "ndiyo", "moved");
        household("archived-family", 1, 1, "ndiyo", "house_closed_3");
        household("declined-later", 0, 1, "hapana", "other_3");
        household("unrelated-archive", 0, 1, "ndiyo", "active");
        assertEquals(Arrays.asList("active", "legacy"), rows(false));
        assertEquals(Arrays.asList("archived-family", "declined-later", "moved"), rows(true));
        assertEquals("2", scalar("SELECT count(*) FROM ec_family WHERE "
                + MotherMentorHouseholdFollowUpDao.enrollmentCondition("ec_family", false)));
        assertEquals("3", scalar("SELECT count(*) FROM ec_family WHERE "
                + MotherMentorHouseholdFollowUpDao.enrollmentCondition("ec_family", true)));
    }

    @Test public void summaryColumnsSelectTheLatestSourceWithoutDuplicatingHouseholdRows() {
        household("household", 0, 1, "ndiyo", "moved");
        database.execSQL("INSERT INTO ec_mothermentor_household_enrolment VALUES"
                + " ('household', 1, 'ndiyo', 'other_3', 'other_3', '2026-11-04', '10')");
        String reason = MotherMentorHouseholdFollowUpDao.summaryColumn("f", DBConstants.KEY.KAYA_SABABU_YA_KUFUNGWA);
        String date = MotherMentorHouseholdFollowUpDao.summaryColumn("f", DBConstants.KEY.KAYA_TAREHE_YA_KUFUNGWA);
        try (Cursor cursor = database.rawQuery("SELECT " + reason + ", " + date + " FROM ec_family f", null)) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("other_3", cursor.getString(0));
            assertEquals("2026-11-04", cursor.getString(1));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void tableNamesCannotInjectConditions() {
        MotherMentorHouseholdFollowUpDao.enrollmentCondition("ec_family WHERE 1=1", false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void summaryColumnsCannotSelectUnrelatedFields() {
        MotherMentorHouseholdFollowUpDao.summaryColumn("f", "consent");
    }

    private void household(String id, int familyClosed, int enrolledClosed, String consent, String status) {
        database.execSQL("INSERT INTO ec_family VALUES (?, ?)", new Object[] {id, familyClosed});
        database.execSQL("INSERT INTO ec_mothermentor_household_enrolment VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[] {id, enrolledClosed, consent, status,
                        enrolledClosed == 1 ? status : "", "2026-11-03", "1"});
    }

    private List<String> rows(boolean closed) {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT base_entity_id FROM ec_family WHERE "
                + MotherMentorHouseholdFollowUpDao.enrollmentCondition("ec_family", closed)
                + " ORDER BY base_entity_id", null)) {
            while (cursor.moveToNext()) rows.add(cursor.getString(0));
        }
        return rows;
    }

    private String scalar(String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }
}
