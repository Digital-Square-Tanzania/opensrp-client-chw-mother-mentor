package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.repository.Repository;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdEligibilityDaoTest {
    private SQLiteDatabase data;
    private MotherMentorLibrary previous;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_family (base_entity_id TEXT, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT,"
                + " gender TEXT, dod TEXT, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status"
                + " TEXT, child_dob TEXT, status TEXT, is_closed INTEGER)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER)");
        data.execSQL("CREATE TABLE visit_details (visit_details_id TEXT, visit_id TEXT, visit_key"
                + " TEXT, details TEXT, updated_at INTEGER)");
        data.execSQL("CREATE TABLE ec_mothermentor_household_enrolment (base_entity_id TEXT,"
                + " consent TEXT, is_closed INTEGER)");
        data.execSQL("INSERT INTO ec_family VALUES ('family', 0), ('other', 0)");
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        when(library.getRepository()).thenReturn(repository);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        data.close();
    }

    @Test
    public void enrollmentRequiresAnActivelyEnrolledMotherInThisHousehold() {
        assertFalse(eligible());
        mother("mother", "other", "pregnant", null);
        assertFalse(eligible());
        data.execSQL("UPDATE ec_family_member SET relational_id = 'family'");
        assertTrue(eligible());
        data.execSQL("UPDATE ec_mothermentor_enrollment SET is_closed = 1");
        assertFalse(eligible());
        data.execSQL("UPDATE ec_mothermentor_enrollment SET is_closed = 0");
        data.execSQL("UPDATE ec_family_member SET is_closed = 1");
        assertFalse(eligible());
        data.execSQL("UPDATE ec_family_member SET is_closed = 0, dod = '2026-01-01'");
        assertFalse(eligible());
    }

    @Test
    public void pregnancyBreastfeedingAndTheEighteenMonthWindowShareTheMotherGate() {
        mother("mother", "family", "pregnant", null);
        assertTrue(eligible());
        update("breast_feeding", null);
        assertTrue(eligible());
        update("not_pregnant", null);
        assertFalse(eligible());
        LocalDate today = LocalDate.now();
        update("not_pregnant", today.minusMonths(17).toString());
        assertTrue(eligible());
        update("not_pregnant", today.minusMonths(18).minusDays(1).toString());
        assertFalse(eligible());
        update("not_pregnant", "invalid");
        assertFalse(eligible());
    }

    @Test
    public void latestFinalizedServiceStatusMatchesTheMotherRegisterAndRefreshesWithoutCache() {
        mother("mother", "family", "not_pregnant", LocalDate.now().minusYears(3).toString());
        assertFalse(eligible());
        visit("first", "breast_feeding", 1, 1);
        assertTrue(eligible());
        visit("draft", "not_pregnant", 0, 2);
        assertTrue(eligible());
        data.execSQL("UPDATE visits SET processed = 1 WHERE visit_id = 'draft'");
        assertFalse(eligible());
    }

    @Test
    public void oneEligibleMotherIsEnoughButScreeningAndClosedHouseholdsDoNotQualify() {
        mother("older", "family", "not_pregnant", LocalDate.now().minusYears(3).toString());
        mother("younger", "family", "pregnant", null);
        assertTrue(eligible());
        data.execSQL("UPDATE ec_mothermentor_enrollment SET status = 'screened' WHERE base_entity_id = 'younger'");
        assertFalse(eligible());
        data.execSQL("UPDATE ec_mothermentor_enrollment SET status = 'client'");
        data.execSQL("UPDATE ec_family SET is_closed = 1 WHERE base_entity_id = 'family'");
        assertFalse(eligible());
    }

    @Test
    public void activeConsentingEnrollmentIsReadWithoutChangingHistoricalRecords() {
        data.execSQL("INSERT INTO ec_mothermentor_household_enrolment VALUES ('family', 'ndiyo', 0)");
        assertTrue(MotherMentorHouseholdEligibilityDao.isEnrolled("family"));
        data.execSQL("UPDATE ec_mothermentor_household_enrolment SET is_closed = 1");
        assertFalse(MotherMentorHouseholdEligibilityDao.isEnrolled("family"));
        assertFalse(MotherMentorHouseholdEligibilityDao.isEnrolled("missing"));
        assertFalse(MotherMentorHouseholdEligibilityDao.hasEligibleMother(null));
    }

    private boolean eligible() { return MotherMentorHouseholdEligibilityDao.hasEligibleMother("family"); }

    private void mother(String id, String family, String status, String dob) {
        data.execSQL("INSERT INTO ec_family_member VALUES (?, ?, 'female', NULL, 0)", new Object[] {id, family});
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES (?, ?, ?, 'client', 0)",
                new Object[] {id, status, dob});
    }

    private void update(String status, String dob) {
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status = ?, child_dob = ?",
                new Object[] {status, dob});
    }

    private void visit(String id, String status, int processed, int order) {
        data.execSQL("INSERT INTO visits VALUES (?, 'mother', 'MotherMentor Services', ?, 1, ?, ?)",
                new Object[] {id, processed, order, order});
        data.execSQL("INSERT INTO visit_details VALUES (?, ?, 'visit_pregnancy_status', ?, ?)",
                new Object[] {id, id, status, order});
    }
}
