package org.smartregister.chw.mothermentor.actionhelper;

import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidFollowUpVisitUtils;

/** Captures the final 18-month HIV test independently of earlier DBS tests. */
public class MotherMentorChildEidFinalTestActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        return "yes".equals(getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE))
                ? translate("mothermentor_child_final_result_",
                        getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT))
                : translate("mothermentor_answer_",
                        getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(MotherMentorChildEidFollowUpVisitUtils.isFinalTestComplete(
                getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE),
                getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT),
                getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE)));
    }

    @Override
    public String postProcess(String source) {
        if ("yes".equals(getValue(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE))) return null;
        return clearField(clearField(source, DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT),
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE);
    }
}
