package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.dao.AbstractDao;
import org.smartregister.repository.Repository;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorClosedMotherIdentityTest {
    private Repository previous;
    private SQLiteDatabase data;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(AbstractDao.class, "repository");
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, unique_id TEXT, relational_id TEXT,"
                + " dob TEXT, first_name TEXT, middle_name TEXT, last_name TEXT, gender TEXT, marital_status TEXT,"
                + " phone_number TEXT, other_phone_number TEXT, is_closed INTEGER, dod TEXT)");
        data.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, first_name TEXT, primary_caregiver TEXT,"
                + " family_head TEXT, village_town TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, is_closed INTEGER, ctc_number TEXT)");
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase db =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(repository.getReadableDatabase()).thenReturn(db);
        when(db.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", repository);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", previous);
        data.close();
    }

    @Test
    public void closedIndependentMotherKeepsHerIdentityAndDoesNotReenterTheActiveLookup() {
        insert("mother-'one", null, 1);
        MemberObject mother = MotherMentorDao.getPrimaryMemberForHistory("mother-'one");
        assertNotNull(mother);
        assertEquals("mother-'one", mother.getBaseEntityId());
        assertEquals("Asha", mother.getFirstName());
        assertEquals("CTC-test", mother.getCtcNumber());
        assertNull(MotherMentorDao.getMember("mother-'one"));
        assertNull(MotherMentorDao.getPrimaryMemberForHistory("' OR 1 = 1 --"));
    }

    @Test
    public void aSharedDeceasedMemberAndClosedFamilyRemainAvailableForReadOnlyHistory() {
        data.execSQL("INSERT INTO ec_family VALUES ('family', 'Household', NULL, NULL, 'Village')");
        insert("mother", "family", 1);
        data.execSQL("UPDATE ec_family_member SET is_closed = 1, dod = '2026-10-30'");
        MemberObject mother = MotherMentorDao.getPrimaryMemberForHistory("mother");
        assertNotNull(mother);
        assertEquals("family", mother.getFamilyBaseEntityId());
        assertEquals("Village", mother.getAddress());
        assertNull(MotherMentorDao.getMember("mother"));
    }

    private void insert(String id, String family, int closed) {
        data.execSQL("INSERT INTO ec_family_member VALUES (?, 'test-id', ?, '2000-01-01', 'Asha', '',"
                + " 'Jaribio', 'Female', '', '', '', 0, NULL)", new Object[] {id, family});
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES (?, ?, 'CTC-test')",
                new Object[] {id, closed});
    }
}
