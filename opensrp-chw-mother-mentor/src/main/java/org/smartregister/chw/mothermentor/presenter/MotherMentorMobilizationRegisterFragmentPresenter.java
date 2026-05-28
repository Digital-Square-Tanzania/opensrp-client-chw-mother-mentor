package org.smartregister.chw.mothermentor.presenter;

import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.contract.MotherMentorRegisterFragmentContract;

public class MotherMentorMobilizationRegisterFragmentPresenter extends BaseMotherMentorRegisterFragmentPresenter {

    public MotherMentorMobilizationRegisterFragmentPresenter(MotherMentorRegisterFragmentContract.View view, MotherMentorRegisterFragmentContract.Model model, String viewConfigurationIdentifier) {
        super(view, model, viewConfigurationIdentifier);
    }

    @Override
    public String getMainTable() {
        return Constants.TABLES.MOTHERMENTOR_MOBILIZATION;
    }

    @Override
    public String getMainCondition() {
        return " is_closed is 0 ";
    }

}
