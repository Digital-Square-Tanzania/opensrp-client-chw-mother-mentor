package org.smartregister.chw.mothermentor_sample.presenter;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.smartregister.chw.mothermentor.contract.MotherMentorProfileContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.presenter.BaseMotherMentorProfilePresenter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BaseMotherMentorProfilePresenterTest {

    @Mock
    private MotherMentorProfileContract.View view = Mockito.mock(MotherMentorProfileContract.View.class);

    @Mock
    private MotherMentorProfileContract.Interactor interactor = Mockito.mock(MotherMentorProfileContract.Interactor.class);

    @Mock
    private MemberObject mothermentorMemberObject = new MemberObject();

    private BaseMotherMentorProfilePresenter profilePresenter = new BaseMotherMentorProfilePresenter(view, interactor, mothermentorMemberObject);


    @Test
    public void fillProfileDataCallsSetProfileViewWithDataWhenPassedMemberObject() {
        profilePresenter.fillProfileData(mothermentorMemberObject);
        verify(view).setProfileViewWithData();
    }

    @Test
    public void fillProfileDataDoesntCallsSetProfileViewWithDataIfMemberObjectEmpty() {
        profilePresenter.fillProfileData(null);
        verify(view, never()).setProfileViewWithData();
    }

    @Test
    public void malariaTestDatePeriodIsLessThanSeven() {
        profilePresenter.recordMotherMentorButton("");
        verify(view).hideView();
    }

    @Test
    public void malariaTestDatePeriodIsMoreThanFourteen() {
        profilePresenter.recordMotherMentorButton("EXPIRED");
        verify(view).hideView();
    }

    @Test
    public void refreshProfileBottom() {
        profilePresenter.refreshProfileBottom();
        verify(interactor).refreshProfileInfo(mothermentorMemberObject, profilePresenter.getView());
    }

    @Test
    public void saveForm() {
        profilePresenter.saveForm(null);
        verify(interactor).saveRegistration(null, view);
    }
}
