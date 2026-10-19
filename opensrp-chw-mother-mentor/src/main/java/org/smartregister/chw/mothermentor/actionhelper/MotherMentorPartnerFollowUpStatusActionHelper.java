package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerFollowUpStatusActionHelper extends BaseMotherMentorActionHelper {
    private final Runnable onStatusChanged;

    public MotherMentorPartnerFollowUpStatusActionHelper(Runnable onStatusChanged) {
        this.onStatusChanged = onStatusChanged;
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        if (onStatusChanged != null) onStatusChanged.run();
    }

    @Override
    public String evaluateSubTitle() {
        return translate("mothermentor_status_", getValue(DBConstants.KEY.FOLLOW_UP_STATUS));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(StringUtils.isNotBlank(getValue(DBConstants.KEY.FOLLOW_UP_STATUS)));
    }
}
