package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorReturnToFacilityActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        String answer =
                translate("mothermentor_answer_", getValue(DBConstants.KEY.RETURNED_TO_FACILITY));
        String date = getValue(DBConstants.KEY.FACILITY_RETURN_DATE);
        return StringUtils.isBlank(answer)
                ? null
                : answer + (StringUtils.isBlank(date) ? "" : " · " + date);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String answer = getValue(DBConstants.KEY.RETURNED_TO_FACILITY);
        return completed(
                StringUtils.isNotBlank(answer)
                        && (!"yes".equals(answer)
                                || StringUtils.isNotBlank(
                                        getValue(DBConstants.KEY.FACILITY_RETURN_DATE))));
    }

    @Override
    public String postProcess(String source) {
        return "yes".equals(getValue(DBConstants.KEY.RETURNED_TO_FACILITY))
                ? null
                : clearField(source, DBConstants.KEY.FACILITY_RETURN_DATE);
    }
}
