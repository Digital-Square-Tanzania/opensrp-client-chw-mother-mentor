package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;

import org.joda.time.LocalDate;
import org.junit.Test;

public class MotherMentorEligibilityTest {
    private final LocalDate today = new LocalDate(2026, 11, 4);

    @Test
    public void newEnrollmentAndLinkingIncludeTheNineteenMonthAnniversary() {
        assertTrue(MotherMentorEligibility.isWithinChildEnrollmentAge(new LocalDate(2025, 4, 5), today));
        assertTrue(MotherMentorEligibility.isWithinChildEnrollmentAge(new LocalDate(2025, 4, 4), today));
        assertFalse(MotherMentorEligibility.isWithinChildEnrollmentAge(new LocalDate(2025, 4, 3), today));
        assertFalse(MotherMentorEligibility.isWithinChildEnrollmentAge(new LocalDate(2024, 11, 4), today));
        assertFalse(MotherMentorEligibility.isWithinChildEnrollmentAge(today.plusDays(1), today));
        assertFalse(MotherMentorEligibility.isWithinChildEnrollmentAge(null, today));
    }

    @Test
    public void nineteenMonthAnniversaryClampsAtTheEndOfShorterMonths() {
        LocalDate julyBirth = new LocalDate(2024, 7, 31);
        assertTrue(MotherMentorEligibility.isWithinChildEnrollmentAge(julyBirth, new LocalDate(2026, 2, 28)));
        assertFalse(MotherMentorEligibility.isWithinChildEnrollmentAge(julyBirth, new LocalDate(2026, 3, 1)));
        assertEquals(new LocalDate(2025, 9, 30),
                MotherMentorEligibility.earliestEligibleChildBirthDate(new LocalDate(2027, 4, 30)));
        assertEquals(new LocalDate(2025, 10, 1),
                MotherMentorEligibility.earliestEligibleChildBirthDate(new LocalDate(2027, 5, 1)));
        assertEquals(new LocalDate(2025, 3, 1),
                MotherMentorEligibility.earliestEligibleChildBirthDate(new LocalDate(2026, 9, 30)));
    }

    @Test
    public void pregnancyAndBreastfeedingAreEligibleWithoutAChildDob() {
        for (String status : new String[] {"pregnant", "breast_feeding", "bf"}) {
            assertTrue(MotherMentorEligibility.isEligibleMother(status, null, today));
            assertTrue(MotherMentorEligibility.isEligibleMother(status, "01-01-2020", today));
        }
    }

    @Test
    public void nonPregnantEligibilityEndsAfterTheEighteenMonthAnniversary() {
        assertTrue(MotherMentorEligibility.isEligibleMother("none", "2025-05-05", today));
        assertTrue(MotherMentorEligibility.isEligibleMother("not_pregnant", "04-05-2025", today));
        assertFalse(MotherMentorEligibility.isEligibleMother("none", "2025-05-03", today));
        assertFalse(MotherMentorEligibility.isEligibleMother("not_pregnant", "2025-04-04", today));
    }

    @Test
    public void monthEndBirthUsesTheLastDayOfTheShorterAnniversaryMonth() {
        assertTrue(MotherMentorEligibility.isEligibleMother("none", "2024-08-31",
                new LocalDate(2026, 2, 28)));
        assertFalse(MotherMentorEligibility.isEligibleMother("none", "2024-08-31",
                new LocalDate(2026, 3, 1)));
    }

    @Test
    public void missingMalformedAndFutureBirthDatesCannotEstablishEligibility() {
        for (String dob : new String[] {null, "", "31-02-2026", "2026-11-05", "invalid"}) {
            assertFalse(MotherMentorEligibility.isEligibleMother("not_pregnant", dob, today));
        }
        assertFalse(MotherMentorEligibility.isEligibleMother(null, "04-11-2026", today));
        assertFalse(MotherMentorEligibility.isEligibleMother("unknown", "04-11-2026", today));
    }
}
