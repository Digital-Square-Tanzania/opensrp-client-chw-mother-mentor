package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import timber.log.Timber;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MotherMentorVisitTypeActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;

    public MotherMentorVisitTypeActionHelper(MemberObject member) {
        this.member = member;
    }

    @Override
    public String getPreProcessed() {
        if (sourceForm == null) return null;
        try {
            JSONObject form = new JSONObject(sourceForm);
            Map<String, String> defaults = loadEnrollmentValues();
            JSONArray fields =
                    form.getJSONObject(JsonFormConstants.STEP1)
                            .getJSONArray(JsonFormConstants.FIELDS);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String key = field.optString(JsonFormConstants.KEY);
                String value = defaults.get(key);
                // Preserve answers already saved in an unfinished visit.
                if (value != null
                        && !value.isEmpty()
                        && field.optString(JsonFormConstants.VALUE).isEmpty()) {
                    field.put(JsonFormConstants.VALUE, value);
                }
                if (DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT.equals(key)
                        || DBConstants.KEY.IIT_ENROLLED.equals(key)
                        || DBConstants.KEY.PARTNER_ENROLLED.equals(key)
                        || DBConstants.KEY.CHILD_EID_ENROLLED.equals(key)) {
                    field.put(JsonFormConstants.VALUE, value);
                }
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    protected Map<String, String> loadEnrollmentValues() {
        return member == null
                ? Collections.emptyMap()
                : MotherMentorDao.getVisitEnrollmentValues(member.getBaseEntityId());
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null) return BaseMotherMentorVisitAction.Status.PENDING;
        try {
            JSONArray fields =
                    payload.getJSONObject(JsonFormConstants.STEP1)
                            .getJSONArray(JsonFormConstants.FIELDS);
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                values.put(
                        field.optString(JsonFormConstants.KEY),
                        field.optString(JsonFormConstants.VALUE));
            }
            return completed(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        } catch (Exception e) {
            Timber.e(e);
            return BaseMotherMentorVisitAction.Status.PENDING;
        }
    }
}
