package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerHepatitisBTestActionHelper extends BaseMotherMentorPartnerTestActionHelper {
    public MotherMentorPartnerHepatitisBTestActionHelper(Runnable onTestChanged) {
        super(
                DBConstants.KEY.PARTNER_HEPATITIS_B_TESTED,
                DBConstants.KEY.PARTNER_HEPATITIS_B_RESULT,
                DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON,
                DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON_OTHER,
                false,
                onTestChanged);
    }
}
