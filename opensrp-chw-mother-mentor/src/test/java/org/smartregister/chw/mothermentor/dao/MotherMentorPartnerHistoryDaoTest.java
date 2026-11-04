package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentValues;
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
public class MotherMentorPartnerHistoryDaoTest {
    private SQLiteDatabase database;
    private Repository previous;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(AbstractDao.class, "repository");
        database = SQLiteDatabase.create(null);
        database.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, unique_id TEXT,"
                + " relational_id TEXT, dob TEXT, first_name TEXT, middle_name TEXT, last_name TEXT,"
                + " gender TEXT, marital_status TEXT, phone_number TEXT, other_phone_number TEXT,"
                + " is_closed INTEGER)");
        database.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, first_name TEXT,"
                + " primary_caregiver TEXT, family_head TEXT, village_town TEXT, is_closed INTEGER)");
        database.execSQL("CREATE TABLE ec_mothermentor_enroll_partner (base_entity_id TEXT,"
                + " is_closed INTEGER, last_interacted_with INTEGER)");
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> database.rawQuery(call.getArgument(0), call.getArgument(1)));
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", repository);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", previous);
        database.close();
    }

    @Test
    public void closedPartnerAndClosedHouseholdRetainTheirHistoricalIdentity() {
        database.execSQL("INSERT INTO ec_family VALUES ('family', 'Household', '', '', 'Village', 1)");
        partner("closed", "family", 1);
        MemberObject member = MotherMentorDao.getPartnerHistoryMember("closed");
        assertNotNull(member);
        assertEquals("closed", member.getBaseEntityId());
        assertEquals("Juma", member.getFirstName());
        assertEquals("family", member.getFamilyBaseEntityId());
    }

    @Test
    public void independentPartnerDoesNotRequireAHouseholdToOpenHistory() {
        partner("independent", null, 1);
        MemberObject member = MotherMentorDao.getPartnerHistoryMember("independent");
        assertNotNull(member);
        assertEquals("independent", member.getBaseEntityId());
        assertEquals("Juma", member.getFirstName());
    }

    @Test
    public void historyLookupSelectsOnlyTheRequestedPartnerAndEscapesItsIdentifier() {
        partner("ordinary", null, 1);
        partner("quoted'id", null, 1);
        assertEquals("quoted'id", MotherMentorDao.getPartnerHistoryMember("quoted'id").getBaseEntityId());
        assertNull(MotherMentorDao.getPartnerHistoryMember("missing"));
        assertNull(MotherMentorDao.getPartnerHistoryMember(" "));
    }

    private void partner(String id, String family, int closed) {
        ContentValues member = new ContentValues();
        member.put("base_entity_id", id);
        member.put("relational_id", family);
        member.put("first_name", "Juma");
        member.put("is_closed", closed);
        database.insertOrThrow("ec_family_member", null, member);
        ContentValues enrollment = new ContentValues();
        enrollment.put("base_entity_id", id);
        enrollment.put("is_closed", closed);
        enrollment.put("last_interacted_with", 1);
        database.insertOrThrow("ec_mothermentor_enroll_partner", null, enrollment);
    }
}
