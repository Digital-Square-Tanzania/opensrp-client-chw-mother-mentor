package org.smartregister.chw.mothermentor.listener;


import android.view.View;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.fragment.BaseMotherMentorCallDialogFragment;

public class BaseMotherMentorCallWidgetDialogListener implements View.OnClickListener {

    private BaseMotherMentorCallDialogFragment callDialogFragment;

    public BaseMotherMentorCallWidgetDialogListener(BaseMotherMentorCallDialogFragment dialogFragment) {
        callDialogFragment = dialogFragment;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.mothermentor_call_close) {
            callDialogFragment.dismiss();
        }
    }
}
