package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class VisitDetailsReportIndexTest {
    @Test
    public void freshVisitDetailsUsesAnIndexForCaseSensitiveReportLookups() {
        try (SQLiteDatabase database = SQLiteDatabase.create(null)) {
            net.zetetic.database.sqlcipher.SQLiteDatabase cipher = bridge(database);
            VisitDetailsRepository.createTable(cipher);
            database.execSQL("INSERT INTO visit_details (visit_id, visit_key, details) VALUES "
                    + "('visit', 'result', 'negative'), ('VISIT', 'result', 'positive')");

            assertReportIndexUsed(database);
            try (Cursor cursor = database.rawQuery(
                    "SELECT MAX(details) FROM visit_details WHERE visit_id = 'visit' AND visit_key = 'result'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("negative", cursor.getString(0));
            }
            try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE "
                    + "type = 'index' AND name = 'visit_details_visit_id_index'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(1, cursor.getInt(0));
            }
        }
    }

    @Test
    public void addingReportIndexToAnExistingTableIsRepeatableAndPreservesTheLegacyIndex() {
        try (SQLiteDatabase database = SQLiteDatabase.create(null)) {
            database.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
            database.execSQL("CREATE INDEX legacy_nocase ON visit_details "
                    + "(visit_id COLLATE NOCASE, visit_key COLLATE NOCASE)");
            database.execSQL("INSERT INTO visit_details VALUES ('visit', 'result', 'negative')");
            net.zetetic.database.sqlcipher.SQLiteDatabase cipher = bridge(database);

            VisitDetailsRepository.createReportIndex(cipher);
            VisitDetailsRepository.createReportIndex(cipher);

            assertReportIndexUsed(database);
            try (Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE "
                    + "type = 'index' AND tbl_name = 'visit_details'", null)) {
                assertEquals(2, cursor.getCount());
            }
            try (Cursor cursor = database.rawQuery("SELECT details FROM visit_details", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("negative", cursor.getString(0));
                assertEquals(1, cursor.getCount());
            }
        }
    }

    private static void assertReportIndexUsed(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery("EXPLAIN QUERY PLAN SELECT MAX(details) "
                + "FROM visit_details WHERE visit_id = 'visit' AND visit_key = 'result'", null)) {
            StringBuilder plan = new StringBuilder();
            while (cursor.moveToNext()) plan.append(cursor.getString(cursor.getColumnIndexOrThrow("detail")));
            assertTrue(plan.toString(), plan.toString().contains("mothermentor_report_visit_details"));
        }
    }

    private static net.zetetic.database.sqlcipher.SQLiteDatabase bridge(SQLiteDatabase database) {
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher = Mockito.mock(
                net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        Mockito.doAnswer(call -> {
            database.execSQL(call.getArgument(0));
            return null;
        }).when(cipher).execSQL(anyString());
        return cipher;
    }
}
