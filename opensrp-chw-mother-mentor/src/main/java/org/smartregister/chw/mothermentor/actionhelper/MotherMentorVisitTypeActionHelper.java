package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherDeliveryRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
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
            if (MotherMentorMotherPregnancyUtils.values(form)
                    .containsKey(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB)) {
                MotherMentorMotherVisitDao.Context original = loadMotherContext();
                Map<String, String> previous = loadDeliveryAnswers();
                MotherMentorMotherPregnancyUtils.prepare(form,
                        original.getEnrollmentPregnancyStatus(), previous.getOrDefault(
                                DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB, original.getEnrollmentChildDob()),
                        previous, StringUtils.isNotBlank(currentVisitId));
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    protected Map<String, String> loadEnrollmentValues() {
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? Collections.emptyMap()
                : MotherMentorDao.getVisitEnrollmentValues(member.getBaseEntityId());
    }

    protected MotherMentorMotherVisitDao.Context loadMotherContext() {
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? MotherMentorMotherVisitDao.Context.empty()
                : MotherMentorMotherVisitDao.getContext(member.getBaseEntityId());
    }

    protected Map<String, String> loadDeliveryAnswers() {
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? Collections.emptyMap()
                : MotherMentorMotherDeliveryRepository.getState(
                        MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                        member.getBaseEntityId(), currentVisitId).getAnswers();
    }

    protected LocalDate loadServiceDate() {
        return StringUtils.isBlank(currentVisitId) ? LocalDate.now()
                : MotherMentorMotherVisitDao.getServiceDate(MotherMentorLibrary.getInstance()
                        .visitRepository().getVisitByVisitId(currentVisitId));
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        if (payload == null) return;
        try {
            if (!MotherMentorMotherPregnancyUtils.values(payload)
                    .containsKey(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB)) return;
            MotherMentorMotherVisitDao.Context original = loadMotherContext();
            MotherMentorMotherPregnancyUtils.restoreContext(payload,
                    original.getEnrollmentPregnancyStatus(), loadDeliveryAnswers().getOrDefault(
                            DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB, original.getEnrollmentChildDob()));
            MotherMentorMotherPregnancyUtils.normalize(payload);
        } catch (Exception e) {
            Timber.e(e, "Unable to restore the original mother pregnancy context");
        }
    }

    @Override
    public String postProcess(String jsonPayload) {
        if (payload == null) return null;
        try {
            return MotherMentorMotherPregnancyUtils.values(payload)
                    .containsKey(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB) ? payload.toString() : null;
        } catch (Exception e) {
            Timber.e(e);
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
                    && (!values.containsKey(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS)
                            || MotherMentorMotherPregnancyUtils.isComplete(values, loadServiceDate())));
        } catch (Exception e) {
            Timber.e(e);
            return BaseMotherMentorVisitAction.Status.PENDING;
        }
    }
}
