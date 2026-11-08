package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;

import org.joda.time.LocalDate;
import org.junit.Test;

public class MotherMentorEligibilityTest {
    private final LocalDate today = new LocalDate(2026, 11, 4);

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
