package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE, qualifiers = "en")
public class MotherMentorAgeUtilsTest {

    private final LocalDate today = new LocalDate(2026, 10, 26);

    @Test
    public void englishInfantsShowCompletedCalendarMonths() {
        assertEquals("6 months", formatAge("2026-04-26"));
        assertEquals("1 month", formatAge("2026-09-26"));
        assertEquals("11 months", formatAge("2025-10-27"));
    }

    @Test
    public void englishNewbornsShowDays() {
        assertEquals("5 days", formatAge("2026-10-21"));
        assertEquals("1 day", formatAge("2026-10-25"));
        assertEquals("0 days", formatAge("2026-10-26"));
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliInfantsShowLocalizedMonths() {
        assertEquals("Miezi 6", formatAge("2026-04-26"));
        assertEquals("Mwezi 1", formatAge("2026-09-26"));
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliNewbornsShowLocalizedDays() {
        assertEquals("Siku 5", formatAge("2026-10-21"));
        assertEquals("Siku 1", formatAge("2026-10-25"));
        assertEquals("Siku 0", formatAge("2026-10-26"));
    }

    @Test
    public void firstBirthdayAndAdultsKeepCompletedYearDisplay() {
        assertEquals("1", formatAge("2025-10-26"));
        assertEquals("32", formatAge("1994-10-26"));
        assertEquals("31", formatAge("1994-10-27"));
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliFirstBirthdayAndAdultsKeepCompletedYearDisplay() {
        assertEquals("1", formatAge("2025-10-26"));
        assertEquals("32", formatAge("1994-10-26"));
    }

    @Test
    public void leapYearDoesNotTurn365DaysIntoACompletedYear() {
        assertEquals("11 months", formatAge("2023-03-01", new LocalDate(2024, 2, 29)));
        assertEquals("1", formatAge("2023-03-01", new LocalDate(2024, 3, 1)));
    }

    @Test
    public void monthBoundaryUsesCalendarMonthsRatherThanThirtyDays() {
        assertEquals("30 days", formatAge("2026-05-01", new LocalDate(2026, 5, 31)));
        assertEquals("1 month", formatAge("2026-05-01", new LocalDate(2026, 6, 1)));
    }

    @Test
    public void storedIsoTimestampsKeepRecordedBirthCalendarDate() {
        assertEquals("6 months", formatAge("2026-04-26T00:00:00.000Z"));
        assertEquals("6 months", formatAge("2026-04-26T00:30:00+14:00"));
        assertEquals("6 months", formatAge("2026-04-26T23:30:00-12:00"));
    }

    @Test
    public void unavailableOrInvalidBirthDatesAreOmitted() {
        for (String dob : new String[] {
            null, "", "  ", "bad-date", "2026", "2026-04", "26/04/2026",
            "2026-02-30", "2026-13-01", "2026-04-26Tnonsense", "2026-04-26T25:00:00Z"
        }) {
            assertEquals("Invalid DOB should have no display age: " + dob, "", formatAge(dob));
        }
    }

    @Test
    public void futureBirthDatesAreOmitted() {
        assertEquals("", formatAge("2026-10-27"));
        assertEquals("", formatAge("2026-10-27T00:30:00+14:00"));
    }

    private String formatAge(String dob) {
        return formatAge(dob, today);
    }

    private String formatAge(String dob, LocalDate currentDate) {
        Context context = RuntimeEnvironment.getApplication();
        return MotherMentorAgeUtils.formatAge(context, dob, currentDate);
    }
}
