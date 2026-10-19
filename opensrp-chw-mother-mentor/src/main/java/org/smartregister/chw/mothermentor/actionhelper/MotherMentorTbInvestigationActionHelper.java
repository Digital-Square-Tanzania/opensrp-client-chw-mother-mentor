package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import timber.log.Timber;

import java.util.List;
import java.util.Map;

public class MotherMentorTbInvestigationActionHelper extends BaseMotherMentorActionHelper {
    private String screeningStatus;

    public MotherMentorTbInvestigationActionHelper(Context context, MemberObject memberObject) {
        this.context = context;
    }

    @Override
    public void onJsonFormLoaded(
            String jsonPayload, Context context, Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        screeningStatus = null;
        try {
            screeningStatus =
                    JsonFormUtils.getValue(
                            new JSONObject(jsonPayload), DBConstants.KEY.SCREENING_STATUS);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(
                StringUtils.isNotBlank(getValue(DBConstants.KEY.TB_SCREENING_FINDINGS))
                        || StringUtils.isNotBlank(
                                getValue(DBConstants.KEY.LEGACY_TB_INVESTIGATION_FINDINGS)));
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        screeningStatus = getValue(DBConstants.KEY.SCREENING_STATUS);
    }

    public String getScreeningStatus() {
        return screeningStatus;
    }

    public boolean isTbPresumptive() {
        return StringUtils.equalsIgnoreCase(getScreeningStatus(), "tb_presumptive");
    }
}
