package org.smartregister.chw.mothermentor.presenter;

import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;
import org.smartregister.util.FormUtils;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;

import timber.log.Timber;

public class BaseMotherMentorVisitPresenter implements BaseMotherMentorVisitContract.Presenter, BaseMotherMentorVisitContract.InteractorCallBack {

    protected WeakReference<BaseMotherMentorVisitContract.View> view;
    protected BaseMotherMentorVisitContract.Interactor interactor;
    protected MemberObject memberObject;

    public BaseMotherMentorVisitPresenter(MemberObject memberObject, BaseMotherMentorVisitContract.View view, BaseMotherMentorVisitContract.Interactor interactor) {
        this.view = new WeakReference<>(view);
        this.interactor = interactor;
        this.memberObject = memberObject;
    }

    @Override
    public void startForm(String formName, String memberID, String currentLocationId) {
        try {
            if (view.get() != null) {
                JSONObject jsonObject = FormUtils.getInstance(view.get().getContext()).getFormJson(formName);
                MotherMentorJsonFormUtils.getRegistrationForm(jsonObject, memberID, currentLocationId);
                view.get().startFormActivity(jsonObject);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public boolean validateStatus() {
        return false;
    }

    @Override
    public void initialize() {
        view.get().displayProgressBar(true);
        view.get().redrawHeader(memberObject);
        interactor.calculateActions(view.get(), memberObject, this);
    }

    @Override
    public void submitVisit() {
        if (view.get() != null) {
            view.get().displayProgressBar(true);
            interactor.submitVisit(view.get().getEditMode(), memberObject.getBaseEntityId(), view.get().getMotherMentorVisitActions(), this);
        }
    }

    @Override
    public void reloadMemberDetails(String memberID, String profileType) {
        view.get().displayProgressBar(true);
        interactor.reloadMemberDetails(memberID, profileType, this);
    }

    @Override
    public void onMemberDetailsReloaded(MemberObject memberObject) {
        if (view.get() != null) {
            this.memberObject = memberObject;

            view.get().displayProgressBar(false);
            view.get().onMemberDetailsReloaded(memberObject);
        }
    }

    @Override
    public void onRegistrationSaved(boolean isEdit) {
        Timber.v("onRegistrationSaved");
    }

    @Override
    public void preloadActions(LinkedHashMap<String, BaseMotherMentorVisitAction> map) {
        if (view.get() != null)
            view.get().initializeActions(map);
    }

    @Override
    public void onSubmitted(String results) {
        if (view.get() != null) {
            view.get().displayProgressBar(false);
            if (results != null) {
                view.get().submittedAndClose(results);
            } else {
                view.get().displayToast(view.get().getContext().getString(R.string.error_unable_save_visit));
            }
        }
    }
}
