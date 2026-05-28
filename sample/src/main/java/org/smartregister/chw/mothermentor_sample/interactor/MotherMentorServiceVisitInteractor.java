package org.smartregister.chw.mothermentor_sample.interactor;

import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.interactor.BaseMotherMentorServiceVisitInteractor;
import org.smartregister.chw.mothermentor_sample.activity.EntryActivity;


public class MotherMentorServiceVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    public MotherMentorServiceVisitInteractor(String visitType) {
        super(visitType);
    }

    @Override
    public MemberObject getMemberClient(String memberID, String profileType) {
        return EntryActivity.getSampleMember();
    }
}
