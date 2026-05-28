package org.smartregister.chw.mothermentor.listener;

import android.app.Activity;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.listener.BottomNavigationListener;
import org.smartregister.view.activity.BaseRegisterActivity;

public class MotherMentorBottomNavigationListener extends BottomNavigationListener {
    private Activity context;

    public MotherMentorBottomNavigationListener(Activity context) {
        super(context);
        this.context = context;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        super.onNavigationItemSelected(item);

        BaseRegisterActivity baseRegisterActivity = (BaseRegisterActivity) context;

        if (item.getItemId() == R.id.action_home) {
            baseRegisterActivity.switchToBaseFragment();
        } else if (item.getItemId() == R.id.action_contact) {
            baseRegisterActivity.switchToFragment(1);
        } else if (item.getItemId() == R.id.action_mobilization) {
            baseRegisterActivity.switchToFragment(2);
        }

        return true;
    }
}