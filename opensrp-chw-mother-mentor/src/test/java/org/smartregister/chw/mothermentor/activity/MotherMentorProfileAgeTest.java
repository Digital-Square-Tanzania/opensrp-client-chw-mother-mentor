package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.domain.AlertStatus;
import org.smartregister.util.LangUtils;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorProfileAgeTest {
    private DateTimeZone previousTimeZone;

    @Before
    public void freezeToday() {
        LangUtils.saveLanguage(
                RuntimeEnvironment.application,
                RuntimeEnvironment.application.getResources().getConfiguration().locale.getLanguage());
        previousTimeZone = DateTimeZone.getDefault();
        DateTimeZone.setDefault(DateTimeZone.forID("Africa/Dar_es_Salaam"));
        DateTimeUtils.setCurrentMillisFixed(
                new DateTime(2026, 10, 26, 12, 0, DateTimeZone.forID("Africa/Dar_es_Salaam"))
                        .getMillis());
    }

    @After
    public void restoreClock() {
        DateTimeUtils.setCurrentMillisSystem();
        DateTimeZone.setDefault(previousTimeZone);
    }

    @Test
    @Config(qualifiers = "en")
    public void profileShowsInfantMonthsAndNewbornDaysInsteadOfZeroYears() {
        assertEquals("Amina Test Kinara, 6 months", profile("2026-04-26").textViewName.getText().toString());
        assertEquals("Amina Test Kinara, 5 days", profile("2026-10-21").textViewName.getText().toString());
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliProfileShowsLocalizedInfantAge() {
        assertEquals("Amina Test Kinara, Miezi 6", profile("2026-04-26").textViewName.getText().toString());
        assertEquals("Amina Test Kinara, Siku 5", profile("2026-10-21").textViewName.getText().toString());
    }

    @Test
    public void profileKeepsAdultYearDisplay() {
        assertEquals("Amina Test Kinara, 30", profile("1996-10-26").textViewName.getText().toString());
    }

    @Test
    public void profileOmitsUnavailableAgeWithoutAComma() {
        for (String dob : new String[] {null, "", "bad-date", "2026-10-27"}) {
            assertEquals("Amina Test Kinara", profile(dob).textViewName.getText().toString());
        }
    }

    private TestProfile profile(String dob) {
        TestProfile activity = Robolectric.buildActivity(TestProfile.class).get();
        MemberObject member = new MemberObject();
        member.setFirstName("Amina");
        member.setMiddleName("Test");
        member.setLastName("Kinara");
        member.setGender("Female");
        member.setDob(dob);
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
