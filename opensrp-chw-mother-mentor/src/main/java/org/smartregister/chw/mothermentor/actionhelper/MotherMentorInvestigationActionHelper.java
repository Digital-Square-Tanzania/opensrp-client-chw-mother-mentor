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

public class MotherMentorInvestigationActionHelper implements BaseMotherMentorVisitAction.MotherMentorVisitActionHelper {

    protected String jsonPayload;

    protected String mothermentorObservation;
    protected String screeningStatus;

    protected String baseEntityId;

    protected Context context;

    protected MemberObject memberObject;


    public MotherMentorInvestigationActionHelper(Context context, MemberObject memberObject) {
        this.context = context;
        this.memberObject = memberObject;
    }

    @Override
    public void onJsonFormLoaded(String jsonPayload, Context context, Map<String, List<VisitDetail>> map) {
        this.jsonPayload = jsonPayload;
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
            screeningStatus = JsonFormUtils.getValue(jsonObject, "screening_status");
        } catch (JSONException e) {
            Timber.e(e);
        }
    }


    @Override
    public String getPreProcessed() {
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
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

            mothermentorObservation = JsonFormUtils.getValue(jsonObject, "tb_screening_findings");
            if (StringUtils.isBlank(mothermentorObservation)) {
                mothermentorObservation = JsonFormUtils.getValue(jsonObject, "majibu_ya_uchunguzi_tb");
            }

            if (StringUtils.isBlank(mothermentorObservation)){
                mothermentorObservation = JsonFormUtils.getValue(jsonObject, "mother_mentor_interview_findings");
            }

            if (StringUtils.isBlank(mothermentorObservation)){
                mothermentorObservation = JsonFormUtils.getValue(jsonObject, "majibu_ya_uchunguzi_ukoma");
            }

            screeningStatus = JsonFormUtils.getValue(jsonObject, "screening_status");

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
        if(StringUtils.isNotBlank(mothermentorObservation)){
            return BaseMotherMentorVisitAction.Status.COMPLETED;
        }
        return BaseMotherMentorVisitAction.Status.PENDING;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction baseMotherMentorVisitAction) {
        Timber.v("onPayloadReceived");
    }

    public String getScreeningStatus() {
        return screeningStatus;
    }

    public boolean isTbPresumptive() {
        return StringUtils.equalsIgnoreCase(screeningStatus, "tb_presumptive");
    }
}
