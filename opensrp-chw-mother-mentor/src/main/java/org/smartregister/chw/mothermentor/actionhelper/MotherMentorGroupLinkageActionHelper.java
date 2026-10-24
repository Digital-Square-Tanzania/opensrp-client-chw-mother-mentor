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

import timber.log.Timber;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MotherMentorGroupLinkageActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private String currentVisitId;
    private Set<String> priorGroupLinkages = Collections.emptySet();

    public MotherMentorGroupLinkageActionHelper() {
        this(null);
    }

    public MotherMentorGroupLinkageActionHelper(MemberObject member) {
        this.member = member;
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
        if (sourceForm == null || member == null) return null;
        try {
            priorGroupLinkages =
                    MotherMentorDao.getPriorGroupLinkages(
                            member.getBaseEntityId(), currentVisitId);
            return populatePriorGroupLinkages(sourceForm);
        } catch (Exception e) {
            Timber.e(e);
            return sourceForm;
        }
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (priorGroupLinkages.isEmpty() || StringUtils.isBlank(action.getJsonPayload())) return;
        // Saved blank details are reapplied after preprocessing by the action's form lifecycle.
        String prepared = populatePriorGroupLinkages(action.getJsonPayload());
        action.setProcessedJsonPayload(prepared);
        super.onPayloadReceived(prepared);
    }

    private String populatePriorGroupLinkages(String jsonPayload) {
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
        return completed(
                StringUtils.isNotBlank(
                                getValue(
                                        DBConstants.KEY
                                                .HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP))
                        && StringUtils.isNotBlank(
                                getValue(DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP)));
    }
}
