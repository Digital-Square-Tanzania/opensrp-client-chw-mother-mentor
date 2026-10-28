package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerSyphilisTestActionHelper extends BaseMotherMentorPartnerTestActionHelper {
    public MotherMentorPartnerSyphilisTestActionHelper(Runnable onTestChanged) {
        super(
                DBConstants.KEY.PARTNER_SYPHILIS_TESTED,
                DBConstants.KEY.PARTNER_SYPHILIS_RESULT,
                DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON,
                DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER,
                false,
                onTestChanged);
    }
}
