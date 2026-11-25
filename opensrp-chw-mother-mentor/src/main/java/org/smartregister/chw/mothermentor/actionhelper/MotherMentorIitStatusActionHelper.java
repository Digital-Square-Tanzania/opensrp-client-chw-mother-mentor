package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorIitRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class MotherMentorIitStatusActionHelper extends BaseMotherMentorActionHelper {
    private static final String OPTIONAL_FACILITY = "iit_moved_area_facility";
    private final Runnable onStatusChanged;

    public MotherMentorIitStatusActionHelper(Runnable onStatusChanged) {
        this.onStatusChanged = onStatusChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        onStatusChanged.run();
    }

    @Override
    public String getPreProcessed() {
        if (StringUtils.isBlank(sourceForm)) return null;
        try {
            JSONObject form = new JSONObject(sourceForm);
            if ("moved_area".equals(JsonFormUtils.getValue(form, DBConstants.KEY.IIT_NOT_FOUND_REASON))) {
                JSONObject field = FormUtils.getFieldFromForm(form, OPTIONAL_FACILITY);
                if (field != null) field.put(JsonFormConstants.VALUE,
                        JsonFormUtils.getValue(form, DBConstants.KEY.IIT_KITUO_KIPYA));
            }
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    @Override
    public String evaluateSubTitle() {
        String status = getValue(DBConstants.KEY.FOLLOW_UP_STATUS);
        String label = translate("mothermentor_status_", status);
        String reason = "not_found".equals(status)
                ? translate("mothermentor_iit_reason_", getValue(DBConstants.KEY.IIT_NOT_FOUND_REASON))
                : null;
        return StringUtils.isBlank(reason) ? label : label + " · " + reason;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        Map<String, String> values = new HashMap<>();
        for (String key : new String[] {DBConstants.KEY.FOLLOW_UP_STATUS,
                DBConstants.KEY.IIT_NOT_FOUND_REASON, DBConstants.KEY.IIT_NOT_FOUND_OTHER,
                DBConstants.KEY.IIT_KITUO_KIPYA}) {
            values.put(key, getValue(key));
        }
        return completed(MotherMentorIitRepository.isStatusComplete(values));
    }

    @Override
    public String postProcess(String source) {
        String reason = "not_found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS))
                ? getValue(DBConstants.KEY.IIT_NOT_FOUND_REASON) : null;
        if (reason == null) source = clearField(source, DBConstants.KEY.IIT_NOT_FOUND_REASON);
        if ("moved_area".equals(reason)) {
            source = canonicalizeOptionalFacility(source);
        } else {
            source = clearField(source, OPTIONAL_FACILITY);
            if (!"moved_facility".equals(reason)) {
                source = clearField(source, DBConstants.KEY.IIT_KITUO_KIPYA);
            }
        }
        if (!"other".equals(reason)) source = clearField(source, DBConstants.KEY.IIT_NOT_FOUND_OTHER);
        return source;
    }

    private String canonicalizeOptionalFacility(String source) {
        try {
            JSONObject form = new JSONObject(source);
            JSONObject canonical = FormUtils.getFieldFromForm(form, DBConstants.KEY.IIT_KITUO_KIPYA);
            if (canonical != null) canonical.put(JsonFormConstants.VALUE,
                    StringUtils.defaultString(getValue(OPTIONAL_FACILITY)));
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return source;
        }
    }
}
