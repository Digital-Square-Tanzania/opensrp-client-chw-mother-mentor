package org.smartregister.chw.mothermentor.contract;

import android.content.Context;

public interface BaseMotherMentorCallDialogContract {

    interface View {
        void setPendingCallRequest(Dialer dialer);
        Context getCurrentContext();
    }

    interface Dialer {
        void callMe();
    }
}
