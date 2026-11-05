package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.utils.FormUtils;
import com.vijay.jsonwizard.utils.NativeFormLangUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdCounselling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public class MotherMentorHouseholdCounsellingActionHelper extends BaseMotherMentorActionHelper {
    private String legacyForm;

    @Override
    public void onJsonFormLoaded(String jsonPayload, Context context,
            Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        legacyForm = null;
        payload = null;
        if (details == null || details.containsKey(DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA)
                || !(details.containsKey(DBConstants.KEY.HOUSEHOLD_MEMBERS_FOUND)
                    || details.containsKey(DBConstants.KEY.COUNSELLED_MALE)
                    || details.containsKey(DBConstants.KEY.COUNSELLED_FEMALE)
                    || details.containsKey(DBConstants.KEY.COUNSELLED_TOTAL))) return;
        try {
            JSONObject form = org.smartregister.util.FormUtils.getInstance(context).getFormJson(
                    "mother_mentor_household_members_who_received_counselling_legacy");
            legacyForm = NativeFormLangUtils.getTranslatedString(form.toString(), context);
        } catch (Exception e) {
            Timber.e(e, "Unable to load the original household counselling form");
        }
    }

    @Override
    public String getPreProcessed() {
        return legacyForm;
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        Map<String, String> values = answers();
        if (!MotherMentorHouseholdCounselling.isRoleBased(values)) return;
        Set<String> selected = MotherMentorHouseholdCounselling.selectedRoles(
                values.get(DBConstants.KEY.PARTICIPANTS));
        try {
            for (Map.Entry<String, String> count : MotherMentorHouseholdCounselling.COUNT_FIELDS.entrySet()) {
                if (!selected.contains(count.getKey())) setValue(count.getValue(), "");
            }
            if (!selected.contains("other")) setValue(DBConstants.KEY.OTHER, "");
            setValue(DBConstants.KEY.COUNSELLED_TOTAL,
                    Integer.toString(MotherMentorHouseholdCounselling.total(answers())));
        } catch (JSONException e) {
            Timber.e(e);
        }
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        Map<String, String> values = answers();
        return completed(MotherMentorHouseholdCounselling.isRoleBased(values)
                ? MotherMentorHouseholdCounselling.isComplete(values)
                : MotherMentorActionAnswers.hasAnswer(payload, DBConstants.KEY.PARTICIPANTS, true));
    }

    @Override
    public String postProcess(String jsonPayload) {
        return MotherMentorHouseholdCounselling.isRoleBased(answers()) ? payload.toString() : null;
    }

    private Map<String, String> answers() {
        Map<String, String> values = new HashMap<>();
        if (payload == null) return values;
        for (String key : MotherMentorHouseholdCounselling.COUNT_FIELDS.values()) {
            values.put(key, getValue(key));
        }
        values.put(DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA,
                getValue(DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA));
        values.put(DBConstants.KEY.OTHER, getValue(DBConstants.KEY.OTHER));
        String participants = getValue(DBConstants.KEY.PARTICIPANTS);
        if (StringUtils.isBlank(participants)) {
            try {
                JSONObject field = FormUtils.getFieldFromForm(payload, DBConstants.KEY.PARTICIPANTS);
                JSONArray selected = new JSONArray();
                JSONArray options = field == null ? null : field.optJSONArray("options");
                for (int index = 0; options != null && index < options.length(); index++) {
                    JSONObject option = options.getJSONObject(index);
                    if (option.optBoolean("value")) selected.put(option.getString("key"));
                }
                participants = selected.toString();
            } catch (JSONException e) {
                Timber.e(e);
            }
        }
        values.put(DBConstants.KEY.PARTICIPANTS, participants);
        return values;
    }

    private void setValue(String key, String value) throws JSONException {
        JSONObject field = FormUtils.getFieldFromForm(payload, key);
        if (field != null) field.put("value", value);
    }
}
