package org.smartregister.chw.mothermentor.interactor;

import androidx.annotation.VisibleForTesting;

import org.smartregister.chw.mothermentor.contract.MotherMentorRegisterContract;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.MotherMentorUtil;

public class BaseMotherMentorRegisterInteractor implements MotherMentorRegisterContract.Interactor {

    private AppExecutors appExecutors;

    @VisibleForTesting
    BaseMotherMentorRegisterInteractor(AppExecutors appExecutors) {
        this.appExecutors = appExecutors;
    }

    public BaseMotherMentorRegisterInteractor() {
        this(new AppExecutors());
    }

    @Override
    public void saveRegistration(final String jsonString, final MotherMentorRegisterContract.InteractorCallBack callBack) {

        Runnable runnable = () -> {
            try {
                MotherMentorUtil.saveFormEvent(jsonString);
            } catch (Exception e) {
                e.printStackTrace();
            }

            appExecutors.mainThread().execute(() -> callBack.onRegistrationSaved());
        };
        appExecutors.diskIO().execute(runnable);
    }
}
