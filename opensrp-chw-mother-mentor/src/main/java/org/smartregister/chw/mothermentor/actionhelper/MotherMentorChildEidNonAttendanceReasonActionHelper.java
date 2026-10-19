package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

public class MotherMentorChildEidNonAttendanceReasonActionHelper
        extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        String reason =
                translate(
                        "mothermentor_child_eid_reason_",
                        getValue(DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON));
        String date = getValue(DBConstants.KEY.CHILD_EID_EXPECTED_FACILITY_VISIT_DATE);
        return StringUtils.isBlank(reason)
                ? null
                : reason + (StringUtils.isBlank(date) ? "" : " · " + date);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String reason = getValue(DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON);
        return completed(
                StringUtils.isNotBlank(reason)
                        && StringUtils.isNotBlank(
                                getValue(DBConstants.KEY.CHILD_EID_EXPECTED_FACILITY_VISIT_DATE))
                        && (!DBConstants.KEY.OTHER.equals(reason)
                                || StringUtils.isNotBlank(
                                        getValue(
                                                DBConstants.KEY
                                                        .CHILD_EID_NON_ATTENDANCE_REASON_OTHER))));
    }

    @Override
    public String postProcess(String source) {
        return DBConstants.KEY.OTHER.equals(
                        getValue(DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON))
                ? null
                : clearField(source, DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON_OTHER);
    }
}
