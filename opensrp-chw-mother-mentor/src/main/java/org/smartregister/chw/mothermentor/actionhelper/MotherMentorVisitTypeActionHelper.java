package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import timber.log.Timber;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MotherMentorVisitTypeActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private String currentVisitId;

    public MotherMentorVisitTypeActionHelper(MemberObject member) {
        this.member = member;
    }

    @Override
    public void onJsonFormLoaded(
            String jsonPayload, Context context, Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        currentVisitId = null;
        if (details == null) return;
        for (List<VisitDetail> group : details.values()) {
            if (group == null) continue;
            for (VisitDetail detail : group) {
                if (detail != null && StringUtils.isNotBlank(detail.getVisitId())) {
                    currentVisitId = detail.getVisitId();
                    return;
                }
            }
        }
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
                if (DBConstants.KEY.ATTENDANCE_TYPE.equals(key)
                        && field.optString(JsonFormConstants.VALUE).isEmpty()
                        && member != null
                        && StringUtils.isNotBlank(member.getBaseEntityId())) {
                    value =
                            MotherMentorDao.hasMotherMentorServices(member.getBaseEntityId())
                                    ? "repeat"
                                    : "new";
                }
                if (DBConstants.KEY.ART_INITIATED_BEFORE_VISIT.equals(key)
                        && field.optString(JsonFormConstants.VALUE).isEmpty()
                        && member != null
                        && StringUtils.isNotBlank(member.getBaseEntityId())) {
                    // Keep this snapshot when editing the visit where ART was first initiated.
                    value =
                            Boolean.toString(
                                    MotherMentorDao.wasArtInitiatedBeforeVisit(
                                            member.getBaseEntityId(), currentVisitId));
                }
                // Preserve answers already saved in an unfinished visit.
                if (value != null
                        && !value.isEmpty()
                        && field.optString(JsonFormConstants.VALUE).isEmpty()) {
                    field.put(JsonFormConstants.VALUE, value);
                }
                if (DBConstants.KEY.ATTENDANCE_TYPE.equals(key)
                        && "repeat".equalsIgnoreCase(field.optString(JsonFormConstants.VALUE))) {
                    field.put(JsonFormConstants.READ_ONLY, true);
                }
                if (DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT.equals(key)
                        || DBConstants.KEY.IIT_ENROLLED.equals(key)
                        || DBConstants.KEY.PARTNER_ENROLLED.equals(key)
                        || DBConstants.KEY.CHILD_EID_ENROLLED.equals(key)) {
                    field.put(JsonFormConstants.VALUE, value);
                }
            }
            prepareChildAssessment(form);
            return form.toString();
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    protected void prepareChildAssessment(JSONObject form) {
        if (!"2".equals(MotherMentorMotherChildUtils.values(form).get(DBConstants.KEY.MAMA_VISIT_SCHEMA))
                || member == null || StringUtils.isBlank(member.getBaseEntityId())) return;
        try {
            MotherMentorMotherVisitDao.Context mother =
                    MotherMentorMotherVisitDao.getContext(member.getBaseEntityId());
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS,
                    mother.getEnrollmentPregnancyStatus());
            JSONObject delivery = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA);
            if (delivery != null && delivery.optString("value").isEmpty()) {
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA, mother.getChildDob());
            }
            MotherMentorMotherChildUtils.prepare(form, loadChildState(), childAssessmentDate(form));
        } catch (Exception unavailable) {
            Timber.w(unavailable, "Unable to prepare the routine mother child assessment");
        }
    }

    protected MotherMentorMotherChildRepository.State loadChildState() {
        return MotherMentorMotherChildRepository.getState(MotherMentorLibrary.getInstance()
                .getRepository().getReadableDatabase(), member.getBaseEntityId(), currentVisitId);
    }

    private LocalDate childAssessmentDate(JSONObject form) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        LocalDate date = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE));
        if (StringUtils.isNotBlank(currentVisitId)) {
            try {
                LocalDate clinical = MotherMentorMotherVisitDao.getServiceDate(MotherMentorLibrary.getInstance()
                        .visitRepository().getVisitByVisitId(currentVisitId));
                if (clinical != null) return clinical;
            } catch (Exception unavailable) { Timber.w(unavailable); }
        }
        return date == null ? LocalDate.now() : date;
    }

    protected Map<String, String> loadEnrollmentValues() {
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? Collections.emptyMap()
                : MotherMentorDao.getVisitEnrollmentValues(member.getBaseEntityId());
    }

    @Override
    public String postProcess(String source) {
        try {
            JSONObject form = new JSONObject(source);
            if (!"2".equals(MotherMentorMotherChildUtils.values(form).get(DBConstants.KEY.MAMA_VISIT_SCHEMA))) return null;
            MotherMentorMotherChildUtils.normalize(form, childAssessmentDate(form));
            String normalized = form.toString();
            super.onPayloadReceived(normalized);
            return normalized;
        } catch (Exception invalid) {
            Timber.w(invalid);
            return null;
        }
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
            return completed(MotherMentorVisitCompletion.isVisitTypeComplete(values)
                    && MotherMentorMotherChildUtils.isComplete(values, childAssessmentDate(payload)));
        } catch (Exception e) {
            Timber.e(e);
            return BaseMotherMentorVisitAction.Status.PENDING;
        }
    }
}
