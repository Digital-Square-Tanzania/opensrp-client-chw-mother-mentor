package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

public class MotherMentorPartnerEducationActionHelper extends BaseMotherMentorActionHelper {
    @Override
    public String evaluateSubTitle() {
        return translate("mothermentor_answer_", getValue(DBConstants.KEY.PARTNER_EDUCATION_PROVIDED));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String answer = getValue(DBConstants.KEY.PARTNER_EDUCATION_PROVIDED);
        if ("no".equals(answer)) return completed(true);
        Set<String> topics = selectedTopics();
        return completed("yes".equals(answer) && !topics.isEmpty()
                && new HashSet<>(Arrays.asList("hiv", "syphilis", "hepatitis_b")).containsAll(topics));
    }

    private Set<String> selectedTopics() {
        Set<String> topics = new HashSet<>();
        if (payload == null) return topics;
        try {
            JSONObject field = FormUtils.getFieldFromForm(payload, DBConstants.KEY.PARTNER_EDUCATION_TOPICS);
            if (field == null) return topics;
            String values = field.optString("value");
            for (String value : values.split("[\\[\\]\\\",\\s]+")) {
                if (!value.isEmpty()) topics.add(value);
            }
        } catch (JSONException e) {
            Timber.e(e);
        }
        return topics;
    }

    @Override
    public String postProcess(String source) {
        if (StringUtils.isBlank(source)) return null;
        if ("yes".equals(getValue(DBConstants.KEY.PARTNER_EDUCATION_PROVIDED))) return null;
        try {
            JSONObject form = new JSONObject(source);
            JSONObject field = FormUtils.getFieldFromForm(form, DBConstants.KEY.PARTNER_EDUCATION_TOPICS);
            if (field != null) {
                field.put("value", new JSONArray());
                JSONArray options = field.optJSONArray("options");
                for (int i = 0; options != null && i < options.length(); i++) {
                    options.getJSONObject(i).put("value", false);
                }
            }
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return source;
        }
    }
}
