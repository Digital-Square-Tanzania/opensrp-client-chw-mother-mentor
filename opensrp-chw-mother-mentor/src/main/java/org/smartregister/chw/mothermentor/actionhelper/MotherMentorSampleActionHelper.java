package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class MotherMentorSampleActionHelper implements BaseMotherMentorVisitAction.MotherMentorVisitActionHelper {
    protected String jsonPayload;

    protected String sampleCollection;
    protected String observation;

    protected String baseEntityId;

    protected Context context;

    protected MemberObject memberObject;
    private MotherMentorInvestigationActionHelper tbInvestigationActionHelper;


    public MotherMentorSampleActionHelper(Context context, MemberObject memberObject, MotherMentorInvestigationActionHelper tbInvestigationActionHelper) {
        this.context = context;
        this.memberObject = memberObject;
        this.tbInvestigationActionHelper = tbInvestigationActionHelper;
        this.observation = tbInvestigationActionHelper != null ? tbInvestigationActionHelper.getScreeningStatus() : null;
    }

    @Override
    public void onJsonFormLoaded(String jsonPayload, Context context, Map<String, List<VisitDetail>> map) {
        this.jsonPayload = jsonPayload;
    }

    @Override
    public String getPreProcessed() {
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
            JSONObject global = jsonObject.getJSONObject("global");
            global.put("observation", getCurrentScreeningStatus());
            return jsonObject.toString();
        } catch (JSONException e) {
            Timber.e(e);
        }

        return null;
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
            sampleCollection = JsonFormUtils.getValue(jsonObject, "has_sample_been_collected");


        } catch (JSONException e) {
            Timber.e(e);
        }
    }

    @Override
    public BaseMotherMentorVisitAction.ScheduleStatus getPreProcessedStatus() {
        return null;
    }

    @Override
    public String getPreProcessedSubTitle() {
        return null;
    }

    @Override
    public String postProcess(String jsonPayload) {
        return null;
    }

    @Override
    public String evaluateSubTitle() {
        return null;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {


        if(StringUtils.isNotBlank(sampleCollection)){
            return BaseMotherMentorVisitAction.Status.COMPLETED;
        }
        return BaseMotherMentorVisitAction.Status.PENDING;

    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction baseMotherMentorVisitAction) {
        Timber.v("onPayloadReceived");
    }

    public boolean isEligibleForSampleCollection() {
        return tbInvestigationActionHelper != null && tbInvestigationActionHelper.isTbPresumptive();
    }

    private String getCurrentScreeningStatus() {
        if (tbInvestigationActionHelper != null) {
            String status = tbInvestigationActionHelper.getScreeningStatus();
            if (StringUtils.isNotBlank(status)) {
                return status;
            }
        }
        return StringUtils.defaultString(observation);
    }

    public void setTbInvestigationActionHelper(MotherMentorInvestigationActionHelper tbInvestigationActionHelper) {
        this.tbInvestigationActionHelper = tbInvestigationActionHelper;
        this.observation = tbInvestigationActionHelper != null ? tbInvestigationActionHelper.getScreeningStatus() : null;
    }
}
