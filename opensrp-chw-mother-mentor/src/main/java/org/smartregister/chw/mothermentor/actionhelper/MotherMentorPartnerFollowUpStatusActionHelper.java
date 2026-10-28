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
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onStatusChanged != null) onStatusChanged.run();
    }

    @Override
    public String evaluateSubTitle() {
        String status = getValue(DBConstants.KEY.FOLLOW_UP_STATUS);
        String outcome =
                "found".equals(status)
                        ? translate("mothermentor_partner_agreement_",
                                getValue(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT))
                        : translate("mothermentor_partner_missing_",
                                getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON));
        String label = translate("mothermentor_status_", status);
        return StringUtils.isBlank(outcome) ? label : label + " · " + outcome;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String status = getValue(DBConstants.KEY.FOLLOW_UP_STATUS);
        if ("found".equals(status)) {
            String agreement = getValue(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT);
            return completed("agreed".equals(agreement) || "refused".equals(agreement));
        }
        String reason = getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON);
        return completed("not_found".equals(status)
                && ("deceased".equals(reason) || "moved".equals(reason)
                    || "travelled".equals(reason) || "other".equals(reason))
                && (!"other".equals(reason)
                    || StringUtils.isNotBlank(getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER))));
    }

    @Override
    public String postProcess(String source) {
        if ("found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS))) {
            source = clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON);
            return clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER);
        }
        source = clearField(source, DBConstants.KEY.PARTNER_FACILITY_AGREEMENT);
        return "other".equals(getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON))
                ? source : clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER);
    }
}
