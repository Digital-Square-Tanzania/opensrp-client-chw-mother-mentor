package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import org.smartregister.chw.mothermentor.R;

/** A single confirmation for the child exit and its linked mother outcome. */
public final class MotherMentorChildEidClosureDialog {
    private MotherMentorChildEidClosureDialog() {}

    public static void show(Context context, MotherMentorChildEidExit exit, Runnable confirm, Runnable cancel) {
        String message = MotherMentorChildEidLabels.closedReason(context, exit.getStatus());
        if (exit == MotherMentorChildEidExit.E4) {
            message += "\n" + context.getString(R.string.mothermentor_eid_return_confirm_positive);
        }
        if (exit.getMotherOutcome() != null) {
            message += "\n\n" + context.getString(R.string.mothermentor_eid_exit_mother_note);
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.mothermentor_eid_exit_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.mothermentor_eid_exit_confirm_button, (dialog, which) -> confirm.run())
                .setNegativeButton(R.string.mothermentor_eid_exit_cancel_button, (dialog, which) -> cancel.run())
                .setOnCancelListener(dialog -> cancel.run())
                .show();
    }
}
