package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherDeliveryRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import timber.log.Timber;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MotherMentorVisitTypeActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private String currentVisitId;
    private final Runnable onEntryChanged;
    private final MotherMentorMotherVisitDao.Context enrollmentContext;

    public MotherMentorVisitTypeActionHelper(MemberObject member) {
        this(member, null);
    }

    public MotherMentorVisitTypeActionHelper(MemberObject member, Runnable onEntryChanged) {
        this(member, onEntryChanged, null);
    }

    public MotherMentorVisitTypeActionHelper(MemberObject member, Runnable onEntryChanged,
            MotherMentorMotherVisitDao.Context enrollmentContext) {
        this.member = member;
        this.onEntryChanged = onEntryChanged;
        this.enrollmentContext = enrollmentContext;
    }

    @Override
    public void onJsonFormLoaded(
            String jsonPayload, Context context, Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        payload = null;
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
            Map<String, String> defaults = new HashMap<>(loadEnrollmentValues());
            MotherMentorMotherVisitDao.Context enrollment = MotherMentorMotherVisitDao.Context.empty();
            try {
                enrollment = loadMotherContext();
                defaults.put(DBConstants.KEY.MAMA_ENROLLMENT_ID, enrollment.getEnrollmentId());
                defaults.put(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS,
                        enrollment.getEnrollmentPregnancyStatus());
            } catch (Exception unavailable) {
                Timber.w(unavailable, "Unable to load the mother enrollment context");
            }
            JSONArray fields = form.getJSONObject(JsonFormConstants.STEP1)
                                       .getJSONArray(JsonFormConstants.FIELDS);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String key = field.optString(JsonFormConstants.KEY);
                String value = defaults.get(key);
                if (DBConstants.KEY.ATTENDANCE_TYPE.equals(key)
                        && field.optString(JsonFormConstants.VALUE).isEmpty() && member != null
                        && StringUtils.isNotBlank(member.getBaseEntityId())) {
                    value = MotherMentorDao.hasMotherMentorServices(member.getBaseEntityId())
                            ? "repeat"
                            : "new";
                }
                // Preserve answers already saved in an unfinished visit.
                if (value != null && !value.isEmpty()
                        && field.optString(JsonFormConstants.VALUE).isEmpty()) {
                    field.put(JsonFormConstants.VALUE, value);
                }
                if (DBConstants.KEY.ATTENDANCE_TYPE.equals(key)
                        && "repeat".equalsIgnoreCase(field.optString(JsonFormConstants.VALUE))) {
                    field.put(JsonFormConstants.READ_ONLY, true);
                }
            }
            if ("moved_area".equals(org.smartregister.chw.mothermentor.util.JsonFormUtils.getValue(
                    form, DBConstants.KEY.MAMA_NOT_FOUND_REASON))) {
                JSONObject optional =
                        FormUtils.getFieldFromForm(form, DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA);
                if (optional != null && optional.optString(JsonFormConstants.VALUE).isEmpty()) {
                    optional.put(JsonFormConstants.VALUE,
                            org.smartregister.chw.mothermentor.util.JsonFormUtils.getValue(
                                    form, DBConstants.KEY.MAMA_KITUO_KIPYA));
                }
            }
            preparePregnancyAssessment(form, enrollment);
            prepareChildAssessment(form, enrollment);
            return form.toString();
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    private void preparePregnancyAssessment(JSONObject form, MotherMentorMotherVisitDao.Context enrollment) {
        if (StringUtils.isBlank(enrollment.getEnrollmentId())
                || !MotherMentorMotherVisitUtils.formValues(form)
                        .containsKey(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB)) {
            return;
        }
        try {
            Map<String, String> previous = loadDeliveryAnswers();
            MotherMentorMotherPregnancyUtils.prepare(form, enrollment.getEnrollmentPregnancyStatus(),
                    previous.getOrDefault(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB,
                            enrollment.getEnrollmentChildDob()), previous, StringUtils.isNotBlank(currentVisitId));
        } catch (Exception unavailable) {
            Timber.w(unavailable, "Unable to prepare the mother pregnancy assessment");
        }
    }

    private void prepareChildAssessment(JSONObject form, MotherMentorMotherVisitDao.Context mother) {
        if (MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MAMA_CHILD_STATUS) == null
                || StringUtils.isBlank(mother.getEnrollmentId())
                || member == null || StringUtils.isBlank(member.getBaseEntityId())) return;
        try {
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS,
                    mother.getEnrollmentPregnancyStatus());
            JSONObject delivery = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA);
            if (delivery != null && delivery.optString("value").isEmpty()) {
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA, mother.getChildDob());
            }
            MotherMentorMotherChildRepository.State child = loadChildState();
            MotherMentorMotherChildUtils.prepare(form, child, childAssessmentDate(form));
            JSONObject cessation = MotherMentorMotherChildUtils.field(form,
                    DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA);
            if (canCarryWeaningDate(form) && cessation != null && child.getWeaningDate() != null) {
                MotherMentorMotherPregnancyUtils.showRecordedDate(
                        cessation, MotherMentorMotherPregnancyUtils.displayDate(child.getWeaningDate()));
            }
            String closed = child.getTracingClosedStatus();
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_CLOSED_FOLLOWUP_NOTE,
                    closed == null || closed.isEmpty() ? "" : context.getString(
                            R.string.mothermentor_eid_mama_followup_closed,
                            org.smartregister.chw.mothermentor.util.MotherMentorChildEidLabels.closedReason(context, closed)));
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

    protected MotherMentorMotherVisitDao.Context loadMotherContext() {
        if (enrollmentContext != null) return enrollmentContext;
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
            String attendance = getValue(DBConstants.KEY.ATTENDANCE_TYPE);
            if (!"repeat".equals(attendance)) {
                setValue(DBConstants.KEY.FOLLOW_UP_STATUS, "");
            }
            if (!"repeat".equals(attendance)
                    || !"not_found".equals(getValue(DBConstants.KEY.FOLLOW_UP_STATUS))) {
                setValue(DBConstants.KEY.MAMA_NOT_FOUND_REASON, "");
            }
            String reason = getValue(DBConstants.KEY.MAMA_NOT_FOUND_REASON);
            if (!"other".equals(reason)) setValue(DBConstants.KEY.MAMA_SABABU_NYINGINE_TEXT, "");
            if ("moved_area".equals(reason)) {
                setValue(DBConstants.KEY.MAMA_KITUO_KIPYA,
                        StringUtils.defaultString(
                                getValue(DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA)));
            } else {
                setValue(DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA, "");
                if (!"moved_facility".equals(reason))
                    setValue(DBConstants.KEY.MAMA_KITUO_KIPYA, "");
            }
            if (FormUtils.getFieldFromForm(payload, DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB).length() != 0) {
                MotherMentorMotherVisitDao.Context original = loadMotherContext();
                Map<String, String> previous = loadDeliveryAnswers();
                MotherMentorMotherPregnancyUtils.restoreContext(payload, original.getEnrollmentPregnancyStatus(),
                        previous.getOrDefault(DBConstants.KEY.MAMA_ENROLLMENT_CHILD_DOB,
                                original.getEnrollmentChildDob()));
                MotherMentorMotherPregnancyUtils.restoreRecordedDelivery(payload, previous,
                        StringUtils.isNotBlank(currentVisitId));
                MotherMentorMotherPregnancyUtils.normalize(payload);
            }
            if (MotherMentorMotherChildUtils.field(payload, DBConstants.KEY.MAMA_CHILD_STATUS) != null) {
                String carried = "";
                if (member != null && canCarryWeaningDate(payload)
                        && "not_breastfeeding".equals(getValue(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))) {
                    LocalDate weaningDate = loadChildState().getWeaningDate();
                    if (weaningDate != null) {
                        setValue(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                                MotherMentorMotherPregnancyUtils.displayDate(weaningDate));
                        carried = "true";
                    }
                }
                setValue(DBConstants.KEY.MAMA_CARRIED_WEANING_DATE, carried);
                MotherMentorMotherChildUtils.normalize(payload, childAssessmentDate(payload));
            }
        } catch (Exception e) {
            Timber.e(e, "Unable to clear dependent mother entry answers");
        }
    }

    @Override
    public String postProcess(String jsonPayload) {
        return payload == null ? null : payload.toString();
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onEntryChanged != null) onEntryChanged.run();
    }

    private boolean canCarryWeaningDate(JSONObject form) {
        return StringUtils.isBlank(currentVisitId)
                || "true".equals(MotherMentorMotherChildUtils.values(form)
                        .get(DBConstants.KEY.MAMA_CARRIED_WEANING_DATE));
    }

    private void setValue(String key, String value) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(payload, key);
        if (field != null) field.put(JsonFormConstants.VALUE, value);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null) return BaseMotherMentorVisitAction.Status.PENDING;
        Map<String, String> values = MotherMentorMotherVisitUtils.formValues(payload);
        return completed(MotherMentorMotherVisitUtils.isEntryComplete(values)
                && MotherMentorMotherPregnancyUtils.isComplete(values, loadServiceDate())
                && MotherMentorMotherChildUtils.isComplete(values, childAssessmentDate(payload)));
    }
}
