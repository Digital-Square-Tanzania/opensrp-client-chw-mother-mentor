package org.smartregister.chw.mothermentor.actionhelper;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository.State;
import org.smartregister.chw.mothermentor.util.MotherMentorDeliveryChildren;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherBirthTest;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MotherMentorVisitChildActionHelper extends BaseMotherMentorActionHelper {
    private static final List<String> MATERNAL_CONTEXT = Arrays.asList(ATTENDANCE_TYPE,
            FOLLOW_UP_STATUS, MAMA_FIRST_VISIT_ASSESSMENT, MAMA_ENROLLMENT_PREGNANCY_STATUS,
            VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME, MAMA_TAREHE_KUJIFUNGUA,
            MAMA_CARRIED_DELIVERY, MotherMentorDeliveryChildren.COUNT);

    private final MotherMentorVisitTypeActionHelper entryHelper;
    private final Runnable onChildChanged;
    private final int number;
    private State child;
    private Map<String, String> candidates;

    public MotherMentorVisitChildActionHelper(MotherMentorVisitTypeActionHelper entryHelper,
            Runnable onChildChanged, int number) {
        this.entryHelper = entryHelper;
        this.onChildChanged = onChildChanged;
        this.number = number;
    }

    public String prepare(JSONObject entry, Map<String, String> answers, State child,
            Map<String, String> candidates) throws JSONException {
        this.child = child;
        this.candidates = candidates;
        JSONObject form = new JSONObject(sourceForm);
        JSONObject step = form.getJSONObject("step1");
        JSONArray fields = step.getJSONArray("fields");
        Map<String, String> maternal = MotherMentorMotherChildUtils.values(entry);
        for (String key : MATERNAL_CONTEXT) {
            fields.put(hidden(key, maternal.getOrDefault(key, "")));
        }
        for (Map.Entry<String, String> answer : answers.entrySet()) {
            if (MotherMentorMotherChildUtils.isChildField(answer.getKey())) {
                MotherMentorMotherChildUtils.put(form, answer.getKey(), answer.getValue());
            }
        }
        if (MotherMentorDeliveryChildren.validSex(child.getSex())) {
            JSONObject sex = MotherMentorMotherChildUtils.field(form, MotherMentorDeliveryChildren.SEX);
            sex.put("type", "hidden").put("value", child.getSex());
            sex.remove("v_required");
        }
        if (!MotherMentorDeliveryChildren.recordsDelivery(maternal)) {
            JSONObject status = MotherMentorMotherChildUtils.field(form, MAMA_CHILD_STATUS);
            JSONArray options = status.getJSONArray("options");
            for (int i = options.length() - 1; i >= 0; i--) {
                if ("stillbirth".equals(options.getJSONObject(i).optString("key"))) options.remove(i);
            }
        }
        String linked = child.getExplicitLink();
        if (linked.isEmpty() && !candidates.isEmpty()) {
            JSONArray options = new JSONArray();
            for (Map.Entry<String, String> item : candidates.entrySet()) {
                options.put(new JSONObject().put("key", item.getKey()).put("text", item.getValue()));
            }
            fields.put(hidden(MotherMentorDeliveryChildren.LINK,
                    answers.getOrDefault(MotherMentorDeliveryChildren.LINK, ""))
                    .put("type", "native_radio").put("options", options)
                    .put("label", context.getString(R.string.mothermentor_delivery_child_link)));
        } else {
            fields.put(hidden(MotherMentorDeliveryChildren.LINK, linked));
        }
        form.put("mothermentor_child_section", true);
        form.put(MotherMentorDeliveryChildren.NUMBER, number);
        form.put("encounter_type", entry.getString("encounter_type"));
        form.put("metadata", entry.getJSONObject("metadata"));
        step.put("title", MotherMentorDeliveryChildren.label(context, number, child.getSex()));
        LocalDate assessment = entryHelper.childAssessmentDate(form);
        MotherMentorMotherChildUtils.put(form, MAMA_CHILD_PATH, "true");
        MotherMentorMotherChildUtils.prepare(form, child, assessment);
        MotherMentorMotherBirthTest.prepare(form, MotherMentorMotherChildUtils.values(form), assessment);
        JSONObject cessation = MotherMentorMotherChildUtils.field(form, MAMA_TAREHE_ACHA_KUNYONYESHA);
        if (entryHelper.canCarryWeaningDate(form) && cessation != null && child.getWeaningDate() != null) {
            MotherMentorMotherPregnancyUtils.showRecordedDate(
                    cessation, MotherMentorMotherPregnancyUtils.displayDate(child.getWeaningDate()));
        }
        String closed = child.getTracingClosedStatus();
        MotherMentorMotherChildUtils.put(form, MTOTO_CLOSED_FOLLOWUP_NOTE,
                closed.isEmpty() ? "" : context.getString(R.string.mothermentor_eid_mama_followup_closed,
                        org.smartregister.chw.mothermentor.util.MotherMentorChildEidLabels.closedReason(context, closed)));
        return form.toString();
    }

    private static JSONObject hidden(String key, String value) throws JSONException {
        return new JSONObject().put("key", key).put("type", "hidden")
                .put("openmrs_entity", "ui_only").put("openmrs_entity_id", key)
                .put("openmrs_entity_parent", "").put("value", value);
    }

    public boolean hasSubmittedAnswers() {
        return payload != null && payload.optBoolean("mothermentor_child_section");
    }

    public boolean matchesMother(JSONObject form, JSONObject entry) {
        Map<String, String> childValues = MotherMentorMotherChildUtils.values(form);
        Map<String, String> entryValues = MotherMentorMotherChildUtils.values(entry);
        for (String key : MATERNAL_CONTEXT) {
            if (!Objects.equals(childValues.getOrDefault(key, ""), entryValues.getOrDefault(key, ""))) return false;
        }
        return true;
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        if (payload == null || !payload.optBoolean("mothermentor_child_section") || child == null) return;
        if (MotherMentorDeliveryChildren.validSex(child.getSex())) {
            MotherMentorMotherChildUtils.put(payload, MotherMentorDeliveryChildren.SEX, child.getSex());
        }
        String link = getValue(MotherMentorDeliveryChildren.LINK);
        if (!child.getExplicitLink().isEmpty() || !candidates.containsKey(link)) {
            MotherMentorMotherChildUtils.put(payload, MotherMentorDeliveryChildren.LINK, child.getExplicitLink());
        }
        entryHelper.normalizeChildAssessment(payload, child);
        try {
            payload.getJSONObject("step1").put("title", MotherMentorDeliveryChildren.label(context, number,
                    getValue(MotherMentorDeliveryChildren.SEX)));
        } catch (JSONException error) {
            timber.log.Timber.w(error, "Unable to label the child assessment");
        }
    }

    @Override
    public String postProcess(String jsonPayload) {
        return payload == null ? null : payload.toString();
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null || child == null) return BaseMotherMentorVisitAction.Status.PENDING;
        Map<String, String> values = MotherMentorMotherChildUtils.values(payload);
        return completed(MotherMentorDeliveryChildren.validSex(values.get(MotherMentorDeliveryChildren.SEX))
                && MotherMentorMotherChildUtils.applies(values)
                && entryHelper.isChildAssessmentComplete(payload, values, child));
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (payload != null && payload.optBoolean("mothermentor_child_section")) {
            action.setDisplayTitle(MotherMentorDeliveryChildren.label(context, number,
                    getValue(MotherMentorDeliveryChildren.SEX)));
            onChildChanged.run();
        }
    }
}
