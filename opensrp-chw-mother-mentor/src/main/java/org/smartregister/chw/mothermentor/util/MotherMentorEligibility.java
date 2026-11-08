package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

/** The PMTCT window shared by mother and household enrollment. */
public final class MotherMentorEligibility {
    private MotherMentorEligibility() {}

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
