package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorGroupLinkageActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(
                StringUtils.isNotBlank(
                                getValue(
                                        DBConstants.KEY
                                                .HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP))
                        && StringUtils.isNotBlank(
                                getValue(DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP)));
    }
}
