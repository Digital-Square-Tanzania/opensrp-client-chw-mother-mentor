package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerHivTestActionHelper extends BaseMotherMentorPartnerTestActionHelper {
    public MotherMentorPartnerHivTestActionHelper(Runnable onTestChanged) {
        super(
                DBConstants.KEY.PARTNER_HIV_TESTED,
                DBConstants.KEY.PARTNER_HIV_RESULT,
                DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON,
                DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON_OTHER,
                true,
                onTestChanged);
    }
}
