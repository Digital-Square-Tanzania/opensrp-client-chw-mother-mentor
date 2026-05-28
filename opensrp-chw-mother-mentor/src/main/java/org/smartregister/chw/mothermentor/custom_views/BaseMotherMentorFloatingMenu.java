package org.smartregister.chw.mothermentor.custom_views;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.fragment.BaseMotherMentorCallDialogFragment;

public class BaseMotherMentorFloatingMenu extends LinearLayout implements View.OnClickListener {
    private MemberObject MEMBER_OBJECT;

    public BaseMotherMentorFloatingMenu(Context context, MemberObject MEMBER_OBJECT) {
        super(context);
        initUi();
        this.MEMBER_OBJECT = MEMBER_OBJECT;
    }

    protected void initUi() {
        inflate(getContext(), R.layout.view_mothermentor_floating_menu, this);
        FloatingActionButton fab = findViewById(R.id.mothermentor_fab);
        if (fab != null)
            fab.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.mothermentor_fab) {
            Activity activity = (Activity) getContext();
            BaseMotherMentorCallDialogFragment.launchDialog(activity, MEMBER_OBJECT);
        }  else if (view.getId() == R.id.refer_to_facility_layout) {
            Activity activity = (Activity) getContext();
            BaseMotherMentorCallDialogFragment.launchDialog(activity, MEMBER_OBJECT);
        }
    }
}