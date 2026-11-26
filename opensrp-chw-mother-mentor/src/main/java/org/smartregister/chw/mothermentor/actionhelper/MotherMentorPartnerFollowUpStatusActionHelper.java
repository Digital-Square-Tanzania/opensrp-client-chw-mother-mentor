package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;

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
            String referral = getValue(DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN);
            return completed("refused".equals(agreement) || ("agreed".equals(agreement)
                    && ("yes".equals(referral) || "no".equals(referral))));
        }
        return completed("not_found".equals(status)
                && MotherMentorPartnerFollowUpVisitUtils.isNotFoundComplete(
                        getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON),
                        getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER)));
    }

    @Override
    public String postProcess(String source) {
        if ("found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS))) {
            source = clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON);
            source = clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER);
            return "agreed".equals(getValue(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT))
                    ? source : clearField(source, DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN);
        }
        source = clearField(source, DBConstants.KEY.PARTNER_TESTING_REFERRAL_GIVEN);
        source = clearField(source, DBConstants.KEY.PARTNER_FACILITY_AGREEMENT);
        return "other".equals(getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON))
                ? source : clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER);
    }
}
