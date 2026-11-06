package org.smartregister.chw.mothermentor.util;

import android.content.Context;
import android.text.TextUtils;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.R;

import java.util.ArrayList;
import java.util.List;

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
                : context.getString(label, missed.toString(),
                        Math.max(0, Days.daysBetween(missed, today).getDays()));
    }

    public static String returnState(Context context, MotherMentorIitState state) {
        return context.getString(MotherMentorIitState.REFERRED.equals(state.getReturnState())
                ? R.string.mothermentor_iit_agreed_pending : R.string.mothermentor_iit_not_agreed);
    }

    public static String counters(Context context, MotherMentorIitState state) {
        List<String> labels = new ArrayList<>();
        addCounter(labels, context, R.string.mothermentor_iit_refused_count, state.getRefusedCount());
        addCounter(labels, context, R.string.mothermentor_iit_not_returned_count, state.getNotReturnedCount());
        addCounter(labels, context, R.string.mothermentor_iit_travelled_count, state.getTravelledCount());
        addCounter(labels, context, R.string.mothermentor_iit_other_count, state.getOtherCount());
        addCounter(labels, context, R.string.mothermentor_iit_not_found_count, state.getNotFoundCount());
        return TextUtils.join(" · ", labels);
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

    private static void addCounter(List<String> labels, Context context, int label, int value) {
        if (value > 0) labels.add(context.getString(label, value));
    }
}
