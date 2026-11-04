package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;

import java.util.Collections;
import java.util.Map;

import timber.log.Timber;

/** One saved tracing action decides whether the current Child EID visit can offer HEI services. */
public class MotherMentorChildEidFollowUpStatusActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private final LocalDate serviceDate;
    private final Runnable onStatusChanged;

    public MotherMentorChildEidFollowUpStatusActionHelper(Runnable onStatusChanged) {
        this(null, null, onStatusChanged);
    }

    public MotherMentorChildEidFollowUpStatusActionHelper(MemberObject member,
            LocalDate serviceDate, Runnable onStatusChanged) {
        this.member = member;
        this.serviceDate = serviceDate;
        this.onStatusChanged = onStatusChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onStatusChanged != null) onStatusChanged.run();
    }

    protected LocalDate assessmentDate() {
        return serviceDate == null ? LocalDate.now() : serviceDate;
    }

    protected Map<String, String> loadEnrollmentValues() {
        return member == null || StringUtils.isBlank(member.getBaseEntityId())
                ? Collections.emptyMap()
                : MotherMentorDao.getChildEidTestEnrollmentValues(member.getBaseEntityId());
    }

    protected LocalDate childDob() {
        LocalDate dob = member == null ? null
                : MotherMentorChildEidStatusUtils.parseDate(member.getDob());
        if (dob != null) return dob;
        try {
            return MotherMentorChildEidStatusUtils.parseDate(
                    loadEnrollmentValues().get(DBConstants.KEY.DOB));
        } catch (Exception e) {
            Timber.e(e, "Unable to load the Child EID birth date");
            return null;
        }
    }

    @Override
    public String getPreProcessed() {
        if (StringUtils.isBlank(sourceForm)) return null;
        try {
            JSONObject form = new JSONObject(sourceForm);
            if (!MotherMentorChildEidStatusUtils.isSchema3(
                    MotherMentorChildEidStatusUtils.values(form))) return sourceForm;
            LocalDate dob = childDob();
            JSONObject context = FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_DOB);
            if (context != null) context.put(JsonFormConstants.VALUE, dob == null ? "" : dob.toString());
            JSONObject death = FormUtils.getFieldFromForm(form,
                    DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI);
            if (death != null) {
                if (dob != null) death.put("min_date", dob.toString("dd-MM-yyyy"));
                death.put("max_date", assessmentDate().toString("dd-MM-yyyy"));
            }
            if ("moved_area".equals(JsonFormUtils.getValue(form, DBConstants.KEY.MTOTO_NOT_FOUND_REASON))) {
                JSONObject optional = FormUtils.getFieldFromForm(form,
                        DBConstants.KEY.MTOTO_MOVED_AREA_FACILITY);
                if (optional != null) optional.put(JsonFormConstants.VALUE,
                        JsonFormUtils.getValue(
                                form, DBConstants.KEY.MTOTO_NEW_FACILITY));
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e, "Unable to prepare the Child EID status form");
            return sourceForm;
        }
    }

    @Override
    public String evaluateSubTitle() {
        String status = getValue(DBConstants.KEY.FOLLOW_UP_STATUS);
        String label = translate("mothermentor_status_", status);
        if (!MotherMentorChildEidStatusUtils.isSchema3(
                MotherMentorChildEidStatusUtils.values(payload))) return label;
        String detail = "found".equals(status)
                ? translate("mothermentor_child_eid_child_status_",
                        getValue(DBConstants.KEY.MAMA_CHILD_STATUS))
                : translate("mothermentor_child_eid_missing_",
                        getValue(DBConstants.KEY.MTOTO_NOT_FOUND_REASON));
        return StringUtils.isBlank(detail) ? label : label + " · " + detail;
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        Map<String, String> values = MotherMentorChildEidStatusUtils.values(payload);
        if (!MotherMentorChildEidStatusUtils.isSchema3(values)) {
            return completed(StringUtils.isNotBlank(getValue(DBConstants.KEY.FOLLOW_UP_STATUS)));
        }
        return completed(MotherMentorChildEidStatusUtils.isStatusComplete(
                values, childDob(), assessmentDate()));
    }

    @Override
    public String postProcess(String source) {
        if (StringUtils.isBlank(source)) return source;
        try {
            JSONObject form = MotherMentorChildEidStatusUtils.clearInactiveAnswers(
                    new JSONObject(source));
            String cleaned = form.toString();
            super.onPayloadReceived(cleaned);
            return cleaned;
        } catch (Exception e) {
            Timber.e(e, "Unable to clear inactive Child EID tracing answers");
            return source;
        }
    }
}
