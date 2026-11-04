package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.smartregister.chw.mothermentor.R;

/** Display labels stay independent of stable closure codes and stored clinical evidence. */
public final class MotherMentorMotherClosureLabels {
    private MotherMentorMotherClosureLabels() {}

    public static String label(Context context, String status) {
        return context.getString(resource(status, false));
    }

    public static String note(Context context, String status) {
        return context.getString(resource(status, true));
    }

    private static int resource(String status, boolean note) {
        if (status == null) return R.string.mothermentor_mother_closed;
        switch (status) {
            case MotherMentorMotherClosure.DECEASED:
                return note ? R.string.mothermentor_mother_close_note_deceased
                        : R.string.mothermentor_mother_status_deceased;
            case MotherMentorMotherClosure.MOVED_AREA:
                return note ? R.string.mothermentor_mother_close_note_moved_area
                        : R.string.mothermentor_mother_status_moved_area;
            case MotherMentorMotherClosure.MOVED_FACILITY:
                return note ? R.string.mothermentor_mother_close_note_moved_facility
                        : R.string.mothermentor_mother_status_moved_facility;
            case MotherMentorMotherClosure.NOT_FOUND_OTHER_3:
                return note ? R.string.mothermentor_mother_close_note_not_found_other_3
                        : R.string.mothermentor_mother_status_not_found_other_3;
            case MotherMentorMotherClosure.PREGNANCY_LOSS:
                return note ? R.string.mothermentor_mother_close_note_pregnancy_loss
                        : R.string.mothermentor_mother_status_pregnancy_loss;
            case MotherMentorMotherClosure.STILLBIRTH:
                return note ? R.string.mothermentor_mother_close_note_stillbirth
                        : R.string.mothermentor_mother_status_stillbirth;
            case MotherMentorMotherClosure.PREGNANCY_ENDED_OTHER:
                return note ? R.string.mothermentor_mother_close_note_pregnancy_ended_other
                        : R.string.mothermentor_mother_status_pregnancy_ended_other;
            case MotherMentorMotherClosure.CHILD_DECEASED:
                return note ? R.string.mothermentor_mother_close_note_child_deceased
                        : R.string.mothermentor_mother_status_child_deceased;
            case MotherMentorMotherClosure.CHILD_CONFIRMED_POSITIVE:
                return note ? R.string.mothermentor_mother_close_note_child_confirmed_positive
                        : R.string.mothermentor_mother_status_child_confirmed_positive;
            case MotherMentorMotherClosure.GRADUATED:
                return note ? R.string.mothermentor_mother_close_note_graduated
                        : R.string.mothermentor_mother_status_graduated;
            case MotherMentorMotherClosure.ACTIVE:
                return R.string.mothermentor_mother_status_active;
            default:
                return R.string.mothermentor_mother_closed;
        }
    }
}
