package org.smartregister.chw.mothermentor.actionhelper;

import android.content.Context;
import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUi;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidReturnVisit;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;

import java.util.List;
import java.util.Map;

import timber.log.Timber;

/** One HEI action: shared breastfeeding, first referral, and the existing return questions. */
public class MotherMentorChildEidHeiActionHelper extends BaseMotherMentorActionHelper {
    private final MemberObject member;
    private final Runnable onHeiChanged;
    private final LocalDate suppliedServiceDate;
    private String currentVisitId;
    private LocalDate serviceDate;
    private String referralState;
    private MotherMentorMotherChildRepository.State clinicalState;
    private MotherMentorChildEidRepository.State episode;
    private boolean savedReturnFlow;

    public MotherMentorChildEidHeiActionHelper(MemberObject member, Runnable onHeiChanged) {
        this(member, null, onHeiChanged);
    }

    public MotherMentorChildEidHeiActionHelper(MemberObject member, LocalDate serviceDate,
            Runnable onHeiChanged) {
        this.member = member;
        this.suppliedServiceDate = serviceDate;
        this.onHeiChanged = onHeiChanged;
    }

    @Override
    public void onJsonFormLoaded(String jsonPayload, Context context,
            Map<String, List<VisitDetail>> details) {
        super.onJsonFormLoaded(jsonPayload, context, details);
        payload = null;
        currentVisitId = null;
        serviceDate = suppliedServiceDate == null ? LocalDate.now() : suppliedServiceDate;
        referralState = null;
        clinicalState = null;
        episode = null;
        savedReturnFlow = false;
        if (details == null) return;
        List<VisitDetail> flow = details.get(DBConstants.KEY.MTOTO_RETURN_FLOW);
        savedReturnFlow = flow != null && !flow.isEmpty() && "1".equals(flow.get(0).getDetails());
        List<VisitDetail> previousStates = details.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE);
        if (previousStates != null && !previousStates.isEmpty()
                && MotherMentorChildEidHeiUtils.isReferralState(previousStates.get(0).getDetails())) {
            // Keep the original form branch even if an earlier visit was later corrected/deleted.
            referralState = previousStates.get(0).getDetails();
        }
        List<VisitDetail> dates = details.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
        if (dates != null && !dates.isEmpty()) {
            LocalDate recorded = MotherMentorHeiSchedule.parseDate(dates.get(0).getDetails());
            if (recorded != null && suppliedServiceDate == null) serviceDate = recorded;
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

    protected String loadReferralState() {
        MotherMentorChildEidRepository.State state = episode();
        return state == null ? "none" : state.getReferralState();
    }

    private MotherMentorChildEidRepository.State episode() {
        if (member == null || StringUtils.isBlank(member.getBaseEntityId())) return null;
        if (episode == null) {
            net.zetetic.database.sqlcipher.SQLiteDatabase database =
                    MotherMentorLibrary.getInstance().getRepository().getReadableDatabase();
            episode = StringUtils.isBlank(currentVisitId)
                    ? MotherMentorChildEidRepository.getState(database, member.getBaseEntityId(),
                            null, getServiceDate())
                    : MotherMentorChildEidRepository.getStateBeforeVisit(database,
                            member.getBaseEntityId(), currentVisitId);
        }
        return episode;
    }

    protected MotherMentorMotherChildRepository.State loadClinicalState() {
        if (member == null || StringUtils.isBlank(member.getBaseEntityId())) {
            return new MotherMentorMotherChildRepository.State(null);
        }
        if (StringUtils.isNotBlank(currentVisitId)) {
            return MotherMentorChildEidRepository.getClinicalStateBeforeVisit(MotherMentorLibrary.getInstance()
                    .getRepository().getReadableDatabase(), member.getBaseEntityId(), currentVisitId);
        }
        return MotherMentorMotherChildRepository.getStateForChild(MotherMentorLibrary.getInstance()
                .getRepository().getReadableDatabase(), member.getBaseEntityId(),
                currentVisitId, getServiceDate());
    }

    protected LocalDate getServiceDate() {
        return serviceDate == null ? LocalDate.now() : serviceDate;
    }

    private MotherMentorMotherChildRepository.State clinical() {
        if (clinicalState == null) clinicalState = loadClinicalState();
        return clinicalState;
    }

    private String previousReferralState() {
        if (referralState == null) referralState = loadReferralState();
        return referralState;
    }

    @Override
    public String getPreProcessed() {
        return prepare(sourceForm, true);
    }

    @Override
    public String postProcess(String source) {
        String prepared = prepare(source, false);
        super.onPayloadReceived(prepared);
        return prepared;
    }

    private String prepare(String source, boolean prefill) {
        if (StringUtils.isBlank(source)) return source;
        try {
            JSONObject form = new JSONObject(source);
            MotherMentorMotherChildRepository.State clinical = clinical();
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_DOB,
                    clinical.getChildDob() == null ? "" : clinical.getChildDob().toString());
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE,
                    getServiceDate().toString());
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_HEI_TEST_HISTORY,
                    MotherMentorMotherChildUtils.serializeTests(clinical.getSamplingHistory()));
            String referral = previousReferralState();
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE, referral);
            boolean returnFlow = !"none".equals(referral) && (currentVisitId == null || savedReturnFlow);
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RETURN_FLOW, returnFlow ? "1" : "");
            MotherMentorChildEidRepository.State state = episode();
            if (state != null) {
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS,
                        state.getEnrollmentValues().get(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS));
                if (returnFlow && !savedReturnFlow) {
                    MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RUFAA_TEST, state.getReferralTest());
                }
            }
            if (prefill) {
                putIfEmpty(form, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS,
                        clinical.getBreastfeedingStatus());
                putIfEmpty(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                        clinical.getWeaningDate() == null ? "" : clinical.getWeaningDate().toString());
            }
            MotherMentorChildEidHeiUtils.clearInactiveAnswers(form);
            MotherMentorChildEidHeiUi.prepare(form, context);
            return form.toString();
        } catch (Exception e) {
            Timber.e(e, "Unable to prepare the Child EID HEI assessment");
            return source;
        }
    }

    private void putIfEmpty(JSONObject form, String key, String value) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field != null && field.optString("value").isEmpty()) {
            MotherMentorMotherChildUtils.put(form, key, value == null ? "" : value);
        }
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if (payload == null) return BaseMotherMentorVisitAction.Status.PENDING;
        try {
            return completed(MotherMentorChildEidHeiUtils.isComplete(
                    MotherMentorMotherChildUtils.values(payload), clinical().getChildDob(),
                    getServiceDate(), clinical().getSamplingHistory(), previousReferralState()));
        } catch (Exception e) {
            Timber.e(e, "Unable to validate the Child EID HEI assessment");
            return BaseMotherMentorVisitAction.Status.PENDING;
        }
    }

    @Override
    public String evaluateSubTitle() {
        if (evaluateStatusOnPayload() != BaseMotherMentorVisitAction.Status.COMPLETED) return "";
        String agreement = getValue(DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT);
        if ("agreed".equals(agreement)) return context.getString(
                R.string.mothermentor_child_eid_hei_referral_agreed);
        if ("refused".equals(agreement)) return context.getString(
                R.string.mothermentor_child_eid_hei_referral_refused);
        Map<String, String> values = MotherMentorMotherChildUtils.values(payload);
        if (MotherMentorChildEidReturnVisit.applies(values)) {
            return MotherMentorChildEidHeiUi.returnNote(context, values, clinical().getChildDob(),
                    getServiceDate(), clinical().getSamplingHistory());
        }
        List<Stage> due = MotherMentorChildEidHeiUtils.reviewable(values,
                clinical().getChildDob(), clinical().getSamplingHistory(), getServiceDate());
        return due.isEmpty() ? context.getString(R.string.mothermentor_child_eid_hei_none)
                : TextUtils.join(" / ", MotherMentorChildEidHeiUi.labels(context, due));
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onHeiChanged != null) onHeiChanged.run();
    }
}
