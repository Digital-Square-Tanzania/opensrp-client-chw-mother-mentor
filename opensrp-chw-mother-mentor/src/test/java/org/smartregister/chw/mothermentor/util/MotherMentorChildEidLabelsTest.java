package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidLabelsTest {
    private final LocalDate day = new LocalDate(2026, 11, 4);

    @Test
    @Config(qualifiers = "en")
    public void childAgeKeepsMonthsAfterTheFirstBirthdayAndUsesEarlyWeeks() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("Newborn", MotherMentorChildEidLabels.age(context, day.toString(), day));
        assertEquals("6 days", age(context, 6));
        assertEquals("1 week", age(context, 7));
        assertEquals("8 weeks", age(context, 62));
        assertEquals("2 months", age(context, 63));
        assertEquals("19 months", MotherMentorChildEidLabels.age(context, "2025-04-04", day));
        assertEquals("19 months", MotherMentorChildEidLabels.age(
                context, "2025-04-04T23:00:00.000-11:00", day));
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliAgesUseDaysWeeksAndMonthsWithoutAFalseYearValue() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("Siku 6", age(context, 6));
        assertEquals("wiki 1", age(context, 7));
        assertEquals("wiki 8", age(context, 62));
        assertEquals("Miezi 19", MotherMentorChildEidLabels.age(context, "2025-04-04", day));
    }

    @Test
    public void invalidAndFutureBirthDatesDoNotDisplayInventedAges() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("", MotherMentorChildEidLabels.age(context, "not a date", day));
        assertEquals("", MotherMentorChildEidLabels.age(context, "2026-11-05", day));
        assertEquals("", MotherMentorChildEidLabels.age(context, null, day));
        assertEquals("", MotherMentorChildEidLabels.age(context, "2026", day));
        assertEquals("", MotherMentorChildEidLabels.age(context, "2026-04", day));
    }

    @Test
    @Config(qualifiers = "en")
    public void englishMissedTestAndReferralStatesUseClinicalStageAliases() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("Missed 9 months — due 2026-10-04",
                MotherMentorChildEidLabels.missed(context, "miezi9", "2026-10-04"));
        assertEquals("Referred: 9 months",
                MotherMentorChildEidLabels.referral(context, "referred", "nine_months"));
        assertEquals("Result pending: 6 weeks",
                MotherMentorChildEidLabels.referral(context, "awaiting_result", "wiki6"));
        assertEquals("Awaiting confirmation",
                MotherMentorChildEidLabels.referral(context, "awaiting_confirm", "miezi18"));
        assertEquals("Mother: Neema John · HEI-0412-C01 · Visit 0",
                MotherMentorChildEidLabels.summary(context, "Neema John", "HEI-0412-C01", 0));
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliCardTextDoesNotExposeEnglishCodesOrFabricateMissingDetails() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("Amepewa rufaa: Miezi 9",
                MotherMentorChildEidLabels.referral(context, "referred", "miezi9"));
        assertEquals("Amekosa Miezi 9 — kilitakiwa 2026-10-04",
                MotherMentorChildEidLabels.missed(context, "nine_months", "2026-10-04"));
        assertEquals("Mama: Neema · HEI-0412-C01 · Ziara 2",
                MotherMentorChildEidLabels.summary(context, "Neema", "HEI-0412-C01", 2));
        assertEquals("Amepewa rufaa",
                MotherMentorChildEidLabels.referral(context, "referred", ""));
        assertEquals("", MotherMentorChildEidLabels.missed(context, "miezi9", "unknown"));
        assertEquals("", MotherMentorChildEidLabels.entryReason(context, "unknown", "2026-10-04"));
        assertFalse(MotherMentorChildEidLabels.isSuccessfulClose("confirmed_positive"));
        assertFalse(MotherMentorChildEidLabels.isSuccessfulClose("graduated"));
        assertTrue(MotherMentorChildEidLabels.isSuccessfulClose("tested_negative"));
        assertTrue(MotherMentorChildEidLabels.isSuccessfulClose("confirmatory_negative"));
    }

    private String age(Context context, int days) {
        return MotherMentorChildEidLabels.age(context, day.minusDays(days).toString(), day);
    }
}
