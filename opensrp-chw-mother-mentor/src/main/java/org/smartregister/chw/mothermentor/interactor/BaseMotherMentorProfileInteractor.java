package org.smartregister.chw.mothermentor.interactor;

import androidx.annotation.VisibleForTesting;

import org.smartregister.chw.mothermentor.contract.MotherMentorProfileContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.MotherMentorUtil;
import org.smartregister.domain.AlertStatus;

import java.util.Date;

public class BaseMotherMentorProfileInteractor implements MotherMentorProfileContract.Interactor {
    protected AppExecutors appExecutors;

    @VisibleForTesting
    BaseMotherMentorProfileInteractor(AppExecutors appExecutors) {
        this.appExecutors = appExecutors;
    }

    public BaseMotherMentorProfileInteractor() {
        this(new AppExecutors());
    }

    @Override
    public void refreshProfileInfo(MemberObject memberObject, MotherMentorProfileContract.InteractorCallBack callback) {
        Runnable runnable = () -> appExecutors.mainThread().execute(() -> {
            callback.refreshFamilyStatus(AlertStatus.normal);
            callback.refreshMedicalHistory(true);
            callback.refreshUpComingServicesStatus("MotherMentor Visit", AlertStatus.normal, new Date());
        });
        appExecutors.diskIO().execute(runnable);
    }

    @Override
    public void saveRegistration(final String jsonString, final MotherMentorProfileContract.InteractorCallBack callback) {

        Runnable runnable = () -> {
            try {
                MotherMentorUtil.saveFormEvent(jsonString);
            } catch (Exception e) {
                e.printStackTrace();
            }

        };
        appExecutors.diskIO().execute(runnable);
    }
}
