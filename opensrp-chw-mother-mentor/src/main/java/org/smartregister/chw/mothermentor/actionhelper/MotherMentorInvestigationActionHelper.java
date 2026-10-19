package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorInvestigationActionHelper extends BaseMotherMentorActionHelper {
    public MotherMentorInvestigationActionHelper(Context context, MemberObject memberObject) {
        this.context = context;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(
                StringUtils.isNotBlank(getValue(DBConstants.KEY.MOTHER_MENTOR_INTERVIEW_FINDINGS))
                        || StringUtils.isNotBlank(
                                getValue(
                                        DBConstants.KEY
                                                .LEGACY_MOTHER_MENTOR_INVESTIGATION_FINDINGS)));
    }
}
