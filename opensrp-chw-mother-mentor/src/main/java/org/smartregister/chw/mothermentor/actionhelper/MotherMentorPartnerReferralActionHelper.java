package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerReferralActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        return translate("mothermentor_answer_", getValue(DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String referral = getValue(DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN);
        return completed("yes".equals(referral) || "no".equals(referral));
    }
}
