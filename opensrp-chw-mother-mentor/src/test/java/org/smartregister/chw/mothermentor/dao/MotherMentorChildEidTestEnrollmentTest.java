package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.repository.Repository;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidTestEnrollmentTest {
    @Test
    public void recommendationUsesLatestActiveEnrollmentForTheExactClient() {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        try (SQLiteDatabase data = SQLiteDatabase.create(null)) {
            data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, dob TEXT)");
            data.execSQL(
                    "CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT,"
                            + " weaning_date TEXT, last_test TEXT, is_closed INTEGER,"
                            + " last_interacted_with INTEGER)");
            String childId = "child-with-'quote";
            data.execSQL(
                    "INSERT INTO ec_family_member VALUES (?, ?)",
                    new Object[] {childId, "2025-12-01"});
            data.execSQL(
                    "INSERT INTO ec_mothermentor_enroll_child_eid VALUES (?, ?, ?, ?, ?)",
                    new Object[] {childId, "2026-04-01", "dbs_6", 0, 10});
            data.execSQL(
                    "INSERT INTO ec_mothermentor_enroll_child_eid VALUES (?, ?, ?, ?, ?)",
                    new Object[] {childId, "2026-06-01", "dbs_9", 0, 20});
            data.execSQL(
                    "INSERT INTO ec_mothermentor_enroll_child_eid VALUES (?, ?, ?, ?, ?)",
                    new Object[] {childId, "2026-09-01", "closed", 1, 30});
            MotherMentorLibrary instance = mock(MotherMentorLibrary.class);
            Repository repository = mock(Repository.class);
            net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                    mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", instance);
            when(instance.getRepository()).thenReturn(repository);
            when(repository.getReadableDatabase()).thenReturn(cipher);
            when(cipher.rawQuery(anyString(), any(String[].class)))
                    .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
            Map<String, String> values = MotherMentorDao.getChildEidTestEnrollmentValues(childId);
            assertEquals("2025-12-01", values.get(DBConstants.KEY.DOB));
            assertEquals("2026-06-01", values.get(DBConstants.KEY.WEANING_DATE));
            assertEquals("dbs_9", values.get(DBConstants.KEY.LAST_TEST));
            verify(cipher).rawQuery(anyString(), eq(new String[] {childId}));
            assertTrue(MotherMentorDao.getChildEidTestEnrollmentValues("missing").isEmpty());
            assertTrue(MotherMentorDao.getChildEidTestEnrollmentValues(null).isEmpty());
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }
}
