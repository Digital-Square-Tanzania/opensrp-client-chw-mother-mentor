package org.smartregister.chw.mothermentor.custom_views;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.fragment.BaseMotherMentorCallDialogFragment;

public class BaseMotherMentorFloatingMenu extends LinearLayout implements View.OnClickListener {
    private MemberObject memberObject;
    private FloatingActionButton fab;
    private FloatingActionButton callFab;
    private View activityMain;
    private View menuBar;
    private View callLayout;
    private View referralLayout;
    private View noPhoneHint;
    private View.OnClickListener referralClickListener;
    private boolean menuOpen;

    public BaseMotherMentorFloatingMenu(Context context, MemberObject memberObject) {
        super(context);
        this.memberObject = memberObject;
        initUi();
    }

    public void setMemberObject(MemberObject memberObject) {
        this.memberObject = memberObject;
        refreshCallAvailability();
    }

    public void setReferralClickListener(View.OnClickListener listener) {
        referralClickListener = listener;
        referralLayout.setVisibility(listener == null ? GONE : VISIBLE);
    }

    protected void initUi() {
        inflate(getContext(), R.layout.view_mothermentor_floating_menu, this);
        activityMain = findViewById(R.id.activity_main);
        menuBar = findViewById(R.id.menu_bar);
        fab = findViewById(R.id.mothermentor_fab);
        callFab = findViewById(R.id.callFab);
        callLayout = findViewById(R.id.call_layout);
        referralLayout = findViewById(R.id.refer_to_facility_layout);
        noPhoneHint = findViewById(R.id.CallTextViewHint);
        fab.setOnClickListener(this);
        activityMain.setOnClickListener(this);
        callLayout.setOnClickListener(this);
        callFab.setOnClickListener(this);
        referralLayout.setOnClickListener(this);
        findViewById(R.id.refer_to_facility_fab).setOnClickListener(this);
        setReferralClickListener(null);
        setMenuOpen(false);
    }

    private boolean hasPhoneNumber() {
        return memberObject != null && StringUtils.isNotBlank(memberObject.getPhoneNumber());
    }

    private void refreshCallAvailability() {
        boolean hasPhone = hasPhoneNumber();
        callLayout.setEnabled(hasPhone);
        callLayout.setClickable(menuOpen && hasPhone);
        callLayout.setAlpha(hasPhone ? 1f : 0.5f);
        callFab.setEnabled(hasPhone);
        callFab.setClickable(menuOpen && hasPhone);
        noPhoneHint.setVisibility(hasPhone ? GONE : VISIBLE);
    }

    private void setMenuOpen(boolean open) {
        menuOpen = open;
        menuBar.setVisibility(open ? VISIBLE : GONE);
        activityMain.setBackgroundResource(
                open ? R.color.mothermentor_profile_menu_scrim : android.R.color.transparent);
        activityMain.setClickable(open);
        fab.setImageResource(open ? R.drawable.ic_clear_black_24dp : R.drawable.ic_edit_white);
        fab.setContentDescription(
                getContext().getString(
                        open
                                ? R.string.mothermentor_close_profile_actions
                                : R.string.mothermentor_profile_actions));
        refreshCallAvailability();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.mothermentor_fab) {
            setMenuOpen(!menuOpen);
        } else if (id == R.id.activity_main) {
            setMenuOpen(false);
        } else if ((id == R.id.call_layout || id == R.id.callFab)
                && menuOpen && hasPhoneNumber()) {
            setMenuOpen(false);
            BaseMotherMentorCallDialogFragment.launchDialog((Activity) getContext(), memberObject);
        } else if ((id == R.id.refer_to_facility_layout || id == R.id.refer_to_facility_fab)
                && menuOpen && referralClickListener != null) {
            setMenuOpen(false);
            referralClickListener.onClick(view);
        }
    }
}
