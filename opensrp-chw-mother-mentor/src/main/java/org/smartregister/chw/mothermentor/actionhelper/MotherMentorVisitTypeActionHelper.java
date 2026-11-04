package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;
import com.vijay.jsonwizard.utils.NativeFormLangUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;

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
    private String legacyForm;

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
        currentVisitId = null;
        legacyForm = null;
        payload = null;
        if (details == null) return;
        if (!details.containsKey(DBConstants.KEY.MAMA_VISIT_SCHEMA)
                && (details.containsKey(DBConstants.KEY.ATTENDANCE_TYPE)
                        || details.containsKey(DBConstants.KEY.FOLLOW_UP_STATUS))) {
            try {
                JSONObject form = org.smartregister.util.FormUtils.getInstance(context).getFormJson(
                        "mothermentor_visit_type_legacy");
                legacyForm = NativeFormLangUtils.getTranslatedString(form.toString(), context);
            } catch (Exception e) {
                Timber.e(e, "Unable to restore the original mother visit form");
            }
        }
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
            JSONObject form = new JSONObject(legacyForm == null ? sourceForm : legacyForm);
            Map<String, String> defaults = loadEnrollmentValues();
            if (MotherMentorMotherVisitUtils.isSchema2(
                        MotherMentorMotherVisitUtils.formValues(form))) {
                try {
                    MotherMentorMotherVisitDao.Context enrollment = loadMotherContext();
                    defaults = new HashMap<>(defaults);
                    defaults.put(DBConstants.KEY.MAMA_ENROLLMENT_ID, enrollment.getEnrollmentId());
                    defaults.put(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS,
                            enrollment.getEnrollmentPregnancyStatus());
                } catch (Exception e) {
                    Timber.e(e, "Unable to prepare the original mother enrollment context");
                }
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
                if (DBConstants.KEY.ART_INITIATED_BEFORE_VISIT.equals(key)
                        && field.optString(JsonFormConstants.VALUE).isEmpty() && member != null
                        && StringUtils.isNotBlank(member.getBaseEntityId())) {
                    // Keep this snapshot when editing the visit where ART was first initiated.
                    value = Boolean.toString(MotherMentorDao.wasArtInitiatedBeforeVisit(
                            member.getBaseEntityId(), currentVisitId));
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
                if (DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT.equals(key)
                        || DBConstants.KEY.IIT_ENROLLED.equals(key)
                        || DBConstants.KEY.PARTNER_ENROLLED.equals(key)
                        || DBConstants.KEY.CHILD_EID_ENROLLED.equals(key)) {
                    field.put(JsonFormConstants.VALUE, value);
                }
            }
            if (legacyForm == null
                    && "moved_area".equals(
                            org.smartregister.chw.mothermentor.util.JsonFormUtils.getValue(
                                    form, DBConstants.KEY.MAMA_NOT_FOUND_REASON))) {
                JSONObject optional =
                        FormUtils.getFieldFromForm(form, DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA);
                if (optional != null && optional.optString(JsonFormConstants.VALUE).isEmpty()) {
                    optional.put(JsonFormConstants.VALUE,
                            org.smartregister.chw.mothermentor.util.JsonFormUtils.getValue(
                                    form, DBConstants.KEY.MAMA_KITUO_KIPYA));
                }
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
        if (enrollmentContext != null) return enrollmentContext;
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? MotherMentorMotherVisitDao.Context.empty()
                : MotherMentorMotherVisitDao.getContext(member.getBaseEntityId());
    }

    @Override
    public void onPayloadReceived(String jsonPayload) {
        super.onPayloadReceived(jsonPayload);
        if (!MotherMentorMotherVisitUtils.isSchema2(
                    MotherMentorMotherVisitUtils.formValues(payload)))
            return;
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
        } catch (Exception e) {
            Timber.e(e, "Unable to clear dependent mother entry answers");
        }
    }

    @Override
    public String postProcess(String jsonPayload) {
        return MotherMentorMotherVisitUtils.isSchema2(
                       MotherMentorMotherVisitUtils.formValues(payload))
                ? payload.toString()
                : null;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onEntryChanged != null) onEntryChanged.run();
    }

    private void setValue(String key, String value) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(payload, key);
        if (field != null) field.put(JsonFormConstants.VALUE, value);
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null) return BaseMotherMentorVisitAction.Status.PENDING;
        try {
            JSONArray fields = payload.getJSONObject(JsonFormConstants.STEP1)
                                       .getJSONArray(JsonFormConstants.FIELDS);
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                values.put(field.optString(JsonFormConstants.KEY),
                        field.optString(JsonFormConstants.VALUE));
            }
            return completed(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        } catch (Exception e) {
            Timber.e(e);
            return BaseMotherMentorVisitAction.Status.PENDING;
        }
    }
}
