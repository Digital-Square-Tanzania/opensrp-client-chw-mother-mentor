package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorFollowUpCommentActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        return getValue(DBConstants.KEY.COMMENTS);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(StringUtils.isNotBlank(getValue(DBConstants.KEY.COMMENTS)));
    }
}
