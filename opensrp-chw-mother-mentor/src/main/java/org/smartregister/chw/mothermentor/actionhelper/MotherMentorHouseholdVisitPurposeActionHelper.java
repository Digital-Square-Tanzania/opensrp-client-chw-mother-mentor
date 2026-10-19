package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorHouseholdVisitPurposeActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(
                MotherMentorActionAnswers.hasAnswer(
                        payload, DBConstants.KEY.PURPOSE_OF_VISIT, false));
    }
}
