package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.smartregister.chw.mothermentor.R;

/** Shared localized closure labels for household profiles, registers and history. */
public final class MotherMentorHouseholdClosureLabels {
    private MotherMentorHouseholdClosureLabels() {}

    public static String reason(Context context, String status) {
        if (MotherMentorHouseholdClosure.MOVED.equals(status)) {
            return context.getString(R.string.mothermentor_household_closure_moved);
        }
        if (MotherMentorHouseholdClosure.HOUSE_CLOSED_3.equals(status)) {
            return context.getString(R.string.mothermentor_household_closure_house_closed_3);
        }
        if (MotherMentorHouseholdClosure.OTHER_3.equals(status)) {
            return context.getString(R.string.mothermentor_household_closure_other_3);
        }
        if (MotherMentorHouseholdClosure.LOST_TO_FOLLOW_UP.equals(status)) {
            return context.getString(R.string.mothermentor_household_closure_lost_to_follow_up);
        }
        return context.getString(R.string.mothermentor_household_closure_active);
    }
}
