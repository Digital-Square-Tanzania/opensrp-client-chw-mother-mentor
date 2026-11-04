package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdVisitUtils;

public class MotherMentorHouseholdFollowUpStatusActionHelper extends BaseMotherMentorActionHelper {
    private final Runnable onStatusChanged;

    public MotherMentorHouseholdFollowUpStatusActionHelper(Runnable onStatusChanged) {
        this.onStatusChanged = onStatusChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onStatusChanged != null) onStatusChanged.run();
    }

    @Override
    public String evaluateSubTitle() {
        String type = getValue(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE);
        if (!"repeat".equals(type)) return translate("mothermentor_household_visit_type_", type);
        String status = getValue(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS);
        String label = translate("mothermentor_household_status_", status);
        if (!"not_found".equals(status)) return label;
        String reason = translate("mothermentor_household_missing_",
                getValue(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON));
        return StringUtils.isBlank(reason) ? label : label + " · " + reason;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(MotherMentorHouseholdVisitUtils.isStatusComplete(
                getValue(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE),
                getValue(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS),
                getValue(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON),
                getValue(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER)));
    }

    @Override
    public String postProcess(String source) {
        if (!"repeat".equals(getValue(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE))) {
            source = clearField(source, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS);
        }
        if (!"repeat".equals(getValue(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE))
                || !"not_found".equals(getValue(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS))) {
            source = clearField(source, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON);
            return clearField(source, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER);
        }
        return "other".equals(getValue(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON))
                ? source : clearField(source, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER);
    }
}
