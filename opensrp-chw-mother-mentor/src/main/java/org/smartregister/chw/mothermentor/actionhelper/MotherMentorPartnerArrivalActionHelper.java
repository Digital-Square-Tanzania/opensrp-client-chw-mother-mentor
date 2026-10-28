package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorPartnerArrivalActionHelper extends BaseMotherMentorActionHelper {
    private final Runnable onArrivalChanged;

    public MotherMentorPartnerArrivalActionHelper() {
        this(null);
    }

    public MotherMentorPartnerArrivalActionHelper(Runnable onArrivalChanged) {
        this.onArrivalChanged = onArrivalChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onArrivalChanged != null) onArrivalChanged.run();
    }
    @Override
    public String evaluateSubTitle() {
        String answer =
                translate(
                        "mothermentor_answer_",
                        getValue(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY));
        String date = getValue(DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE);
        return StringUtils.isBlank(answer)
                ? null
                : answer + (StringUtils.isBlank(date) ? "" : " · " + date);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String answer = getValue(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY);
        return completed(
                ("yes".equals(answer) || "no".equals(answer))
                        && (!"yes".equals(answer)
                                || StringUtils.isNotBlank(
                                        getValue(DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE))));
    }

    @Override
    public String postProcess(String source) {
        return "yes".equals(getValue(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))
                ? null
                : clearField(source, DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE);
    }
}
