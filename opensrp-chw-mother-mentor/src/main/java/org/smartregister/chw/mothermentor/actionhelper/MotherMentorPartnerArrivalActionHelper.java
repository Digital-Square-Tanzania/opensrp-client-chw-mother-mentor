package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;

import timber.log.Timber;

public class MotherMentorPartnerArrivalActionHelper extends BaseMotherMentorActionHelper {
    private final Runnable onArrivalChanged;

    public MotherMentorPartnerArrivalActionHelper() {
        this(null);
    }

    public MotherMentorPartnerArrivalActionHelper(Runnable onArrivalChanged) {
        this.onArrivalChanged = onArrivalChanged;
    }

    @Override
    public String getPreProcessed() {
        if (StringUtils.isBlank(sourceForm)) return null;
        try {
            JSONObject form = new JSONObject(sourceForm);
            JSONObject status = FormUtils.getFieldFromForm(form, DBConstants.KEY.FOLLOW_UP_STATUS);
            String attendance = JsonFormUtils.getValue(form, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY);
            // Restored attendance-only answers imply that the partner was found.
            if (status != null && StringUtils.isBlank(status.optString(JsonFormConstants.VALUE))
                    && ("yes".equals(attendance) || "no".equals(attendance))) {
                status.put(JsonFormConstants.VALUE, "found");
                return form.toString();
            }
        } catch (JSONException e) {
            Timber.e(e, "Unable to restore partner attendance");
        }
        return null;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onArrivalChanged != null) onArrivalChanged.run();
    }

    @Override
    public String evaluateSubTitle() {
        if ("not_found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS))) {
            String status = translate("mothermentor_status_", "not_found");
            String reason = translate("mothermentor_partner_missing_",
                    getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON));
            return StringUtils.isBlank(reason) ? status : status + " · " + reason;
        }
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
        String status = getValue(DBConstants.KEY.FOLLOW_UP_STATUS);
        if ("not_found".equals(status)) {
            return completed(MotherMentorPartnerFollowUpVisitUtils.isNotFoundComplete(
                    getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON),
                    getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER)));
        }
        String answer = getValue(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY);
        return completed(
                "found".equals(status) && ("yes".equals(answer) || "no".equals(answer))
                        && (!"yes".equals(answer)
                                || MotherMentorPartnerFollowUpVisitUtils.validRecordedDate(
                                        getValue(DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE))));
    }

    @Override
    public String postProcess(String source) {
        boolean found = "found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS));
        if (found) {
            source = clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON);
        } else {
            source = clearField(source, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY);
        }
        if (found || !"other".equals(getValue(DBConstants.KEY.PARTNER_NOT_FOUND_REASON))) {
            source = clearField(source, DBConstants.KEY.PARTNER_NOT_FOUND_REASON_OTHER);
        }
        return found && "yes".equals(getValue(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))
                ? source : clearField(source, DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE);
    }
}
