package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.Years;
import org.joda.time.format.ISODateTimeFormat;
import org.smartregister.chw.mothermentor.R;

/** Calendar age for display; completed years remain available through MemberObject.getAge(). */
public final class MotherMentorAgeUtils {

    private MotherMentorAgeUtils() {}

    public static String formatAge(Context context, String dob) {
        return formatAge(context, dob, LocalDate.now());
    }

    public static String formatAge(Context context, String dob, LocalDate today) {
        if (dob == null || !dob.trim().matches("\\d{4}-\\d{2}-\\d{2}(T.+)?")) {
            return "";
        }

        LocalDate birthDate;
        try {
            // Keep the recorded birth date, even when a stored timestamp has a time-zone offset.
            birthDate = ISODateTimeFormat.dateOptionalTimeParser().parseLocalDate(dob.trim());
        } catch (IllegalArgumentException e) {
            return "";
        }
        if (birthDate.isAfter(today)) {
            return "";
        }

        int years = Years.yearsBetween(birthDate, today).getYears();
        if (years > 0) {
            return Integer.toString(years);
        }
        int months = Months.monthsBetween(birthDate, today).getMonths();
        if (months > 0) {
            return context.getResources()
                    .getQuantityString(R.plurals.mothermentor_age_months, months, months);
        }
        int days = Days.daysBetween(birthDate, today).getDays();
        return context.getResources().getQuantityString(R.plurals.mothermentor_age_days, days, days);
    }
}
