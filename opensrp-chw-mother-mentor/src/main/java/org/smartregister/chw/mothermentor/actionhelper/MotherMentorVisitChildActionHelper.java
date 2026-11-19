package org.smartregister.chw.mothermentor.actionhelper;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherBirthTest;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gives Mpya its own child section while reusing the routine child controls from attendance. */
public final class MotherMentorVisitChildActionHelper extends BaseMotherMentorActionHelper {
    private static final List<String> MATERNAL_CONTEXT = Arrays.asList(ATTENDANCE_TYPE,
            MAMA_FIRST_VISIT_ASSESSMENT, MAMA_ENROLLMENT_PREGNANCY_STATUS,
            VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME, MAMA_TAREHE_KUJIFUNGUA);

    private final MotherMentorVisitTypeActionHelper entryHelper;
    private final Runnable onChildChanged;

    public MotherMentorVisitChildActionHelper(
            MotherMentorVisitTypeActionHelper entryHelper, Runnable onChildChanged) {
        this.entryHelper = entryHelper;
        this.onChildChanged = onChildChanged;
    }

    public String prepare(JSONObject entry, boolean restoreAnswers) throws JSONException {
        JSONObject form = new JSONObject(sourceForm);
        JSONArray birthFields = form.getJSONObject("step1").getJSONArray("fields");
        JSONArray childFields = new JSONArray();
        JSONArray entryFields = entry.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < entryFields.length(); i++) {
            JSONObject field = entryFields.getJSONObject(i);
            String key = field.getString("key");
            if (MATERNAL_CONTEXT.contains(key)) {
                childFields.put(new JSONObject().put("key", key).put("type", "hidden")
                        .put("openmrs_entity", "ui_only").put("openmrs_entity_id", key)
                        .put("openmrs_entity_parent", "").put("value", field.optString("value")));
            } else if (MotherMentorMotherChildUtils.isChildField(key)) {
                if (MAMA_HEI_TEST_DONE.equals(key)) {
                    for (int j = 0; j < birthFields.length(); j++) {
                        JSONObject birthField = birthFields.getJSONObject(j);
                        if (!restoreAnswers) birthField.put("value", "");
                        childFields.put(birthField);
                    }
                }
                if (!restoreAnswers && MotherMentorMotherChildUtils.ANSWER_FIELDS.contains(key)) {
                    field.put("value", "");
                }
                childFields.put(field);
            }
        }
        form.put("mothermentor_child_section", true);
        form.put("encounter_type", entry.getString("encounter_type"));
        form.put("metadata", entry.getJSONObject("metadata"));
        form.getJSONObject("step1").put("fields", childFields);
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        LocalDate assessment = entryHelper.childAssessmentDate(form);
        MotherMentorMotherChildUtils.put(form, MAMA_HEI_ASSESSMENT_DATE, assessment.toString());
        MotherMentorMotherChildUtils.put(form, MAMA_CHILD_PATH, "true");
        if (MotherMentorMotherBirthTest.isRequired(values, assessment)) {
            MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_REQUIRED, "true");
            LocalDate dob = MotherMentorMotherChildUtils.birth(values);
            setDateBounds(form, MAMA_BIRTH_TEST_DATE, dob,
                    MotherMentorMotherBirthTest.latestSampleDate(dob, assessment));
            setDateBounds(form, MAMA_BIRTH_TEST_RECEIVED_DATE, dob, assessment);
        } else {
            MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_REQUIRED, "false");
        }
        return form.toString();
    }

    public boolean matchesMother(JSONObject child, JSONObject entry) {
        Map<String, String> childValues = MotherMentorMotherChildUtils.values(child);
        Map<String, String> entryValues = MotherMentorMotherChildUtils.values(entry);
        for (String key : MATERNAL_CONTEXT) {
            if (!Objects.equals(childValues.get(key), entryValues.get(key))) return false;
        }
        return true;
    }

    private void setDateBounds(JSONObject form, String key, LocalDate min, LocalDate max)
            throws JSONException {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        field.put("min_date", min.toString("dd-MM-yyyy"));
        field.put("max_date", max.toString("dd-MM-yyyy"));
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        if (payload != null && payload.optBoolean("mothermentor_child_section")) {
            entryHelper.normalizeChildAssessment(payload);
        }
    }

    @Override
    public String postProcess(String jsonPayload) {
        return payload == null ? null : payload.toString();
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null) return BaseMotherMentorVisitAction.Status.PENDING;
        Map<String, String> values = MotherMentorMotherChildUtils.values(payload);
        return completed(MotherMentorMotherChildUtils.applies(values)
                && MotherMentorMotherChildUtils.isComplete(values, entryHelper.childAssessmentDate(payload)));
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (payload != null && payload.optBoolean("mothermentor_child_section")) onChildChanged.run();
    }
}
