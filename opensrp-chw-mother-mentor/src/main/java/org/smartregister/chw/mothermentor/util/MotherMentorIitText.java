package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.R;

/** Localized labels shared by the IIT register, profile, and tracing form. */
public final class MotherMentorIitText {
    private MotherMentorIitText() {}

    public static String missedAppointment(Context context, String date, LocalDate today) {
        return missedAppointment(context, date, today, R.string.mothermentor_iit_missed_appointment);
    }

    public static String missedAppointmentDetails(Context context, String date, LocalDate today) {
        return missedAppointment(context, date, today, R.string.mothermentor_iit_missed_appointment_details);
    }

    private static String missedAppointment(Context context, String date, LocalDate today, int label) {
        LocalDate missed = MotherMentorHeiSchedule.parseDate(date);
        return missed == null ? context.getString(R.string.mothermentor_iit_missing_appointment)
                : context.getString(label, MotherMentorMotherPregnancyUtils.displayDate(missed),
                        Math.max(0, Days.daysBetween(missed, today).getDays()));
    }

    public static String returnState(Context context, MotherMentorIitState state) {
        if (MotherMentorIitState.REFERRED.equals(state.getReturnState())) {
            return context.getString(R.string.mothermentor_iit_agreed_pending);
        }
        return context.getString(state.getRefusedCount() > 0
                ? R.string.mothermentor_iit_exit_refused : R.string.mothermentor_iit_not_agreed);
    }

    public static String reason(Context context, String status) {
        int label;
        switch (status == null ? "" : status) {
            case "I1": label = R.string.mothermentor_iit_exit_returned; break;
            case "I2": label = R.string.mothermentor_iit_exit_deceased; break;
            case "I3": label = R.string.mothermentor_iit_exit_moved; break;
            case "I4": label = R.string.mothermentor_iit_exit_transferred; break;
            case "I5": label = R.string.mothermentor_iit_exit_refused; break;
            case "I6": label = R.string.mothermentor_iit_exit_not_returned; break;
            case "I7": label = R.string.mothermentor_iit_exit_travelled; break;
            case "I8": label = R.string.mothermentor_iit_exit_other; break;
            case "I9": label = R.string.mothermentor_iit_exit_ltfu; break;
            default: label = R.string.mothermentor_iit_closed; break;
        }
        return context.getString(label);
    }
}
