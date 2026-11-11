package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorIitRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorIitText;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import timber.log.Timber;

public class MotherMentorIitStatusActionHelper extends BaseMotherMentorActionHelper {
    private static final String OPTIONAL_FACILITY = "iit_moved_area_facility";
    private final Runnable onStatusChanged;
    private final MotherMentorIitState state;
    private final Function<Map<String, String>, MotherMentorIitState> preview;

    public MotherMentorIitStatusActionHelper(Runnable onStatusChanged, MotherMentorIitState state,
            Function<Map<String, String>, MotherMentorIitState> preview) {
        this.onStatusChanged = onStatusChanged;
        this.state = state;
        this.preview = preview;
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
            setNote(form, "deceased", "I2");
            setNote(form, "moved_area", "I3");
            setNote(form, "travelled", "");
            setNote(form, "other", "");
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    private void setNote(JSONObject form, String reason, String immediateStatus) throws JSONException {
        JSONObject field = FormUtils.getFieldFromForm(form, "iit_note_" + reason);
        if (field == null) return;
        String notice;
        if (!immediateStatus.isEmpty()) {
            notice = context.getString(R.string.mothermentor_iit_close_now,
                    MotherMentorIitText.reason(context, immediateStatus));
            if (state.isMamaKinaraLinked()) {
                notice += " " + context.getString(R.string.mothermentor_iit_linked_close_note);
            }
        } else {
            Map<String, String> outcome = new HashMap<>();
            outcome.put(DBConstants.KEY.FOLLOW_UP_STATUS, "not_found");
            outcome.put(DBConstants.KEY.IIT_NOT_FOUND_REASON, reason);
            // The outcome does not depend on the wording of an Other reason.
            outcome.put(DBConstants.KEY.IIT_NOT_FOUND_OTHER, "other");
            MotherMentorIitState projected = preview.apply(outcome);
            notice = projected.isClosed()
                    ? context.getString(R.string.mothermentor_iit_close_third,
                            MotherMentorIitText.reason(context, projected.getStatus()))
                    : context.getString(R.string.mothermentor_iit_remains_open,
                            Math.max(projected.getNotFoundCount(), state.getNotFoundCount() + 1));
        }
        field.put("text", notice);
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
