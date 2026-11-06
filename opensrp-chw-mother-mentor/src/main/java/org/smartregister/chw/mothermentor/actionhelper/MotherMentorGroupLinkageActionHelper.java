package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;

import timber.log.Timber;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class MotherMentorGroupLinkageActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private String currentVisitId;
    private Set<String> priorGroupLinkages = Collections.emptySet();
    private final Supplier<Map<String, String>> entryValues;

    public MotherMentorGroupLinkageActionHelper() {
        this(null);
    }

    public MotherMentorGroupLinkageActionHelper(MemberObject member) {
        this(member, null);
    }

    public MotherMentorGroupLinkageActionHelper(
            MemberObject member, Supplier<Map<String, String>> entryValues) {
        this.member = member;
        this.entryValues = entryValues;
    }

    @Override
    public void onJsonFormLoaded(
            String jsonPayload, Context context, Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        currentVisitId = null;
        priorGroupLinkages = Collections.emptySet();
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
        if (sourceForm == null || (member == null && entryValues == null)) return null;
        try {
            if (member != null && StringUtils.isNotBlank(member.getBaseEntityId())) {
                priorGroupLinkages = MotherMentorDao.getPriorGroupLinkages(
                        member.getBaseEntityId(), currentVisitId);
            }
            return populateVisitContext(populatePriorGroupLinkages(sourceForm));
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (StringUtils.isBlank(action.getJsonPayload())) return;
        // Saved blank details are reapplied after preprocessing by the action's form lifecycle.
        String prepared = populateVisitContext(populatePriorGroupLinkages(action.getJsonPayload()));
        action.setProcessedJsonPayload(prepared);
        super.onPayloadReceived(prepared);
    }

    private String populateVisitContext(String source) {
        try {
            JSONObject form = new JSONObject(source);
            Map<String, String> values =
                    entryValues == null ? Collections.emptyMap() : entryValues.get();
            JSONObject attendance =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.ATTENDANCE_TYPE);
            if (attendance != null && values != null)
                attendance.put(JsonFormConstants.VALUE,
                        StringUtils.defaultString(values.get(DBConstants.KEY.ATTENDANCE_TYPE)));
            JSONObject priorArt =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT);
            if (priorArt != null && priorArt.optString(JsonFormConstants.VALUE).isEmpty()) {
                boolean initiated = member != null
                        && StringUtils.isNotBlank(member.getBaseEntityId())
                        && MotherMentorDao.wasArtInitiatedBeforeVisit(
                                member.getBaseEntityId(), currentVisitId);
                priorArt.put(JsonFormConstants.VALUE, Boolean.toString(initiated));
            }
            if (!"new".equals(
                        attendance == null ? "" : attendance.optString(JsonFormConstants.VALUE))
                    || (priorArt != null
                            && "true".equals(priorArt.optString(JsonFormConstants.VALUE)))) {
                for (String key : new String[] {DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                             DBConstants.KEY.VISIT_ART_START_DATE}) {
                    JSONObject field = FormUtils.getFieldFromForm(form, key);
                    if (field != null) field.put(JsonFormConstants.VALUE, "");
                }
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e, "Unable to prepare group linkage visit context");
            return source;
        }
    }

    private String populatePriorGroupLinkages(String jsonPayload) {
        Map<String, String> entry = entryValues == null ? null : entryValues.get();
        if (entry != null && !entry.isEmpty()
                && !MotherMentorMotherVisitUtils.isFullVisit(entry))
            return jsonPayload;
        try {
            JSONObject form = new JSONObject(jsonPayload);
            for (String key : priorGroupLinkages) {
                JSONObject field = FormUtils.getFieldFromForm(form, key);
                // Keep the answer already entered or saved for the current visit.
                if (field != null
                        && StringUtils.isBlank(field.optString(JsonFormConstants.VALUE))) {
                    field.put(JsonFormConstants.VALUE, Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
                }
            }
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return jsonPayload;
        }
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(MotherMentorMotherVisitUtils.isArtComplete(
                                 MotherMentorMotherVisitUtils.formValues(payload))
                && StringUtils.isNotBlank(
                        getValue(DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP))
                && StringUtils.isNotBlank(getValue(DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP)));
    }
}
