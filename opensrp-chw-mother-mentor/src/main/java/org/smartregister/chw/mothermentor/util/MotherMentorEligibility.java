package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

/** Age rules for Mother Mentor enrollment and linking. */
public final class MotherMentorEligibility {
    private static final int CHILD_ENROLLMENT_AGE_MONTHS = 19;

    private MotherMentorEligibility() {}

    /** Inclusive through the child's 19-month calendar anniversary. */
    public static boolean isWithinChildEnrollmentAge(LocalDate birthDate, LocalDate today) {
        return birthDate != null && today != null && !birthDate.isAfter(today)
                && !today.isAfter(birthDate.plusMonths(CHILD_ENROLLMENT_AGE_MONTHS));
    }

    public static LocalDate earliestEligibleChildBirthDate(LocalDate today) {
        LocalDate birthDate = today.minusMonths(CHILD_ENROLLMENT_AGE_MONTHS);
        // Subtraction can land on a shorter month that cannot reach today's day on add-back.
        return isWithinChildEnrollmentAge(birthDate, today) ? birthDate : birthDate.plusDays(1);
    }

    /** Checks the current status of an already enrolled mother for household enrollment. */
    public static boolean isEligibleMother(String pregnancyStatus, String childDob, LocalDate today) {
        if ("pregnant".equals(pregnancyStatus)
                || "breast_feeding".equals(pregnancyStatus)
                || "bf".equals(pregnancyStatus)) return true;
        if (!("not_pregnant".equals(pregnancyStatus) || "none".equals(pregnancyStatus))
                || childDob == null || today == null) return false;
        for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd"}) {
            try {
                LocalDate birthDate = DateTimeFormat.forPattern(pattern).parseLocalDate(childDob);
                return !birthDate.isAfter(today)
                        && !birthDate.plusMonths(18).isBefore(today);
            } catch (IllegalArgumentException ignored) {
                // Enrollment and imported records use these two native date formats.
            }
        }
        return false;
    }
}
