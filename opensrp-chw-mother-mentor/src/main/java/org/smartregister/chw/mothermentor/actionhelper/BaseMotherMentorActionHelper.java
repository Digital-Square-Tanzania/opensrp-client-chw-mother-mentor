package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import timber.log.Timber;

import java.util.List;
import java.util.Map;

/** Shared form lifecycle only; each action defines its own completion and processing rules. */
public abstract class BaseMotherMentorActionHelper
        implements BaseMotherMentorVisitAction.MotherMentorVisitActionHelper {
    protected Context context;
    protected String sourceForm;
    protected JSONObject payload;

    @Override
    public void onJsonFormLoaded(
            String jsonPayload, Context context, Map<String, List<VisitDetail>> details) {
        this.context = context;
        sourceForm = jsonPayload;
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        payload = null;
        if (StringUtils.isBlank(jsonPayload)) return;
        try {
            payload = new JSONObject(jsonPayload);
        } catch (JSONException e) {
            Timber.e(e);
        }
    }

    protected String getValue(String key) {
        return payload == null ? null : JsonFormUtils.getValue(payload, key);
    }

    protected String translate(String prefix, String value) {
        if (StringUtils.isBlank(value)) return null;
        if (context == null) return value;
        int id =
                context.getResources()
                        .getIdentifier(prefix + value, "string", context.getPackageName());
        return id == 0 ? value : context.getString(id);
    }

    protected BaseMotherMentorVisitAction.Status completed(boolean complete) {
        return complete
                ? BaseMotherMentorVisitAction.Status.COMPLETED
                : BaseMotherMentorVisitAction.Status.PENDING;
    }

    protected String clearField(String source, String key) {
        if (StringUtils.isBlank(source)) return source;
        try {
            JSONObject form = new JSONObject(source);
            JSONObject field = FormUtils.getFieldFromForm(form, key);
            if (field != null) field.put(JsonFormConstants.VALUE, "");
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return source;
        }
    }

    @Override
    public String getPreProcessed() {
        return null;
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
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        // Actions that affect other sections override this callback when needed.
    }
}
