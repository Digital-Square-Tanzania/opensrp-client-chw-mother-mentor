package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.dao.AbstractDao;
import org.smartregister.domain.AlertStatus;
import org.smartregister.repository.Repository;
import org.smartregister.util.LangUtils;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorProfileCtcNumberTest {
    private SQLiteDatabase data;
    private Repository previousRepository;

    @Before
    public void setUp() {
        LangUtils.saveLanguage(
                RuntimeEnvironment.application,
                RuntimeEnvironment.application.getResources().getConfiguration().locale.getLanguage());
        previousRepository = ReflectionHelpers.getStaticField(AbstractDao.class, "repository");
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE ec_family_member (base_entity_id TEXT, unique_id TEXT,"
                        + " relational_id TEXT, dob TEXT, first_name TEXT, middle_name TEXT,"
                        + " last_name TEXT, gender TEXT, marital_status TEXT, phone_number TEXT,"
                        + " other_phone_number TEXT)");
        data.execSQL(
                "CREATE TABLE ec_family (base_entity_id TEXT, first_name TEXT,"
                        + " primary_caregiver TEXT, family_head TEXT, village_town TEXT)");
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT,"
                        + " ctc_number TEXT, is_closed INTEGER)");
        data.execSQL("INSERT INTO ec_family VALUES ('family', 'Test', '', '', 'Test village')");
        insertMember("mother", "unique-id", "12-34-5678-123456");
        insertMember("other-mother", "other-id", "98-76-5432-654321");

        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", repository);
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", previousRepository);
    }

    @Test
    @Config(qualifiers = "en")
    public void reopeningProfileDisplaysPersistedCtcIdWithoutReplacingRegistrationId() {
        TestProfile activity = profile("mother");
        assertEquals("CTC ID: 12-34-5678-123456", activity.textViewCtcNumber.getText().toString());
        assertEquals(View.VISIBLE, activity.textViewCtcNumber.getVisibility());
        assertEquals("unique-id", activity.textViewUniqueID.getText().toString());

        data.execSQL(
                "UPDATE ec_mothermentor_enrollment SET ctc_number = ? WHERE base_entity_id = ?",
                new Object[] {"11-22-3333-444444", "mother"});
        TestProfile reopened = profile("mother");
        assertEquals("CTC ID: 11-22-3333-444444", reopened.textViewCtcNumber.getText().toString());
        assertEquals("98-76-5432-654321", MotherMentorDao.getMember("other-mother").getCtcNumber());
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliProfileUsesTranslatedCtcLabel() {
        TestProfile activity = profile("mother");
        assertEquals("Namba ya CTC: 12-34-5678-123456", activity.textViewCtcNumber.getText().toString());
        assertEquals(View.VISIBLE, activity.textViewCtcNumber.getVisibility());
    }

    @Test
    public void absentCtcNumberHidesAndClearsTheRow() {
        TestProfile activity = profile("mother");
        for (String missing : new String[] {null, "", "   "}) {
            data.execSQL(
                    "UPDATE ec_mothermentor_enrollment SET ctc_number = ? WHERE base_entity_id = ?",
                    new Object[] {missing, "mother"});
            activity.memberObject = MotherMentorDao.getMember("mother");
            activity.setProfileViewWithData();
            assertEquals(View.GONE, activity.textViewCtcNumber.getVisibility());
            assertEquals("", activity.textViewCtcNumber.getText().toString());
            assertEquals("unique-id", activity.textViewUniqueID.getText().toString());
        }
    }

    private void insertMember(String clientId, String uniqueId, String ctcNumber) {
        data.execSQL(
                "INSERT INTO ec_family_member VALUES (?, ?, 'family', '1990-01-01',"
                        + " 'Test', '', 'Mother', 'Female', '', '', '')",
                new Object[] {clientId, uniqueId});
        data.execSQL(
                "INSERT INTO ec_mothermentor_enrollment VALUES (?, ?, 0)",
                new Object[] {clientId, ctcNumber});
    }

    private TestProfile profile(String clientId) {
        MemberObject member = MotherMentorDao.getMember(clientId);
        assertNotNull(member);
        TestProfile activity = Robolectric.buildActivity(TestProfile.class).get();
        activity.memberObject = member;
        activity.textViewName = new TextView(activity);
        activity.textViewGender = new TextView(activity);
        activity.textViewLocation = new TextView(activity);
        activity.textViewUniqueID = new TextView(activity);
        activity.textViewCtcNumber = new TextView(activity);
        activity.setProfileViewWithData();
        return activity;
    }

    public static class TestProfile extends BaseMotherMentorProfileActivity {
        @Override
        public void openObservationResults() {}

        @Override
        public void refreshMedicalHistory(boolean hasHistory) {}

        @Override
        public void refreshUpComingServicesStatus(String service, AlertStatus status, Date date) {}

        @Override
        public void refreshFamilyStatus(AlertStatus status) {}

        @Override
        public void startServiceForm() {}

        @Override
        public void continueService() {}

        @Override
        public void continueContactVisit() {}

        @Override
        public void openMedicalHistory() {}

        @Override
        public void openUpcomingService() {}

        @Override
        public void openFamilyDueServices() {}

        @Override
        public void openRecordTbContactVisit() {}

        @Override
        public void openClientObservationResults() {}

        @Override
        public void observationResults() {}

        @Override
        public void openTbContactFollowUpVisit() {}

        @Override
        public void openRecordClientVisit() {}
    }
}
