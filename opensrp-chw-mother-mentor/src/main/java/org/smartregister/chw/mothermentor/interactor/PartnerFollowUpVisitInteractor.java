package org.smartregister.chw.mothermentor.interactor;

import static org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils.ATTENDANCE;
import static org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils.REFERRAL;
import static org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils.REFERRAL_SCHEMA;
import static org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils.TRACING;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerArrivalActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerEducationActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerReferralActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorPartnerFollowUpDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.chw.mothermentor.repository.MotherMentorPartnerClosureRepository;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

/** Partner tracing, referral and attendance are distinct phases; saved visits retain their original context. */
public class PartnerFollowUpVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String STATUS = "status";
    private static final String ARRIVAL = "arrival";
    private static final String EDUCATION = "education";
    private static final String COMMENTS = DBConstants.KEY.COMMENTS;

    private final LinkedHashMap<String, BaseMotherMentorVisitAction> actions = new LinkedHashMap<>();
    private final Map<String, BaseMotherMentorVisitAction> workflow = new LinkedHashMap<>();
    private final Map<String, String> snapshot = new LinkedHashMap<>();
    private final ArrayList<Obs> historicalTests = new ArrayList<>();
    private final ArrayList<Obs> originalNonAttendanceEducation = new ArrayList<>();
    private String editedVisitId = "";
    private Date serviceDate = new Date();
    private boolean frozenContext;
    private boolean active;
    private boolean clearingHiddenActions;

    public PartnerFollowUpVisitInteractor(String eventType) {
        super(eventType);
    }

    @Override
    public MemberObject getMemberClient(String memberId, String profileType) {
        MemberObject member = super.getMemberClient(memberId, profileType);
        return member == null ? MotherMentorDao.getSecondaryEnrollmentMember(memberId) : member;
    }

    @Override
    protected void getDetailsOnEdit(
            BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        editedVisitId = "";
        serviceDate = new Date();
        snapshot.clear();
        originalNonAttendanceEducation.clear();
        historicalTests.clear();
        frozenContext = false;
        if (!Boolean.TRUE.equals(view.getEditMode())) return;
        Visit visit = loadEditingVisit(member.getBaseEntityId());
        if (visit == null) return;
        editedVisitId = visit.getVisitId();
        details = VisitUtils.getVisitGroups(
                MotherMentorLibrary.getInstance().visitDetailsRepository().getVisits(editedVisitId));
        try {
            String json = StringUtils.defaultIfBlank(visit.getJson(), visit.getPreProcessedJson());
            JSONArray observations = new JSONObject(json).optJSONArray("obs");
            Map<String, String> saved = MotherMentorVisitCompletion.observationValues(observations);
            serviceDate = originalServiceDate(
                    saved.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE), visit.getDate());
            String phase = saved.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE);
            boolean referralWorkflow = REFERRAL_SCHEMA.equals(saved.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA));
            // A pre-referral attendance draft must resume through the linkage's current phase.
            if (MotherMentorPartnerFollowUpVisitUtils.hasWorkflowSchema(saved)
                    && (TRACING.equals(phase) || (referralWorkflow
                            && (REFERRAL.equals(phase) || ATTENDANCE.equals(phase))))) {
                for (String key : new String[] {
                        DBConstants.KEY.PARTNER_LINKAGE_ID,
                        DBConstants.KEY.MOTHER_BASE_ENTITY_ID,
                        DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID,
                        DBConstants.KEY.PARTNER_MOTHER_PREGNANT,
                        DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE}) {
                    snapshot.put(key, StringUtils.defaultString(saved.get(key)));
                }
                frozenContext = true;
                active = true;
            }
            if (observations != null) {
                boolean nonAttendance = ATTENDANCE.equals(phase)
                        && "no".equals(saved.get(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY));
                for (Obs obs : new Gson().fromJson(observations.toString(), Obs[].class)) {
                    if (isTestObservation(obs)) historicalTests.add(obs);
                    if (nonAttendance && isEducationObservation(obs)) {
                        originalNonAttendanceEducation.add(obs);
                    }
                }
            }
        } catch (Exception e) {
            serviceDate = visit.getDate() == null ? serviceDate : visit.getDate();
            Timber.e(e, "Unable to restore partner visit context");
        }
    }

    protected Visit loadEditingVisit(String memberId) {
        return MotherMentorLibrary.getInstance().visitRepository()
                .getLatestVisit(memberId, getCurrentVisitType());
    }

    protected MotherMentorPartnerFollowUpDao.Context loadContext(
            String memberId, String excludedVisitId, Date date) {
        return MotherMentorPartnerFollowUpDao.getContext(memberId, excludedVisitId, date);
    }

    protected MotherMentorPartnerClosure loadClosureState(String memberId, String excludedVisitId) {
        return MotherMentorPartnerClosureRepository.getState(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberId, excludedVisitId);
    }

    private static Date originalServiceDate(String recordedDate, Date fallback) {
        if (StringUtils.isNotBlank(recordedDate)) {
            for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy"}) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date parsed = format.parse(recordedDate, position);
                if (parsed != null && position.getIndex() == recordedDate.length()) return parsed;
            }
        }
        return fallback == null ? new Date() : fallback;
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callback) {
        appExecutors.diskIO().execute(() -> {
            actions.clear();
            workflow.clear();
            if (!frozenContext) {
                MotherMentorPartnerFollowUpDao.Context linkage =
                        loadContext(memberObject.getBaseEntityId(), editedVisitId, serviceDate);
                snapshot.put(DBConstants.KEY.PARTNER_LINKAGE_ID, linkage.getLinkageId());
                snapshot.put(DBConstants.KEY.MOTHER_BASE_ENTITY_ID, linkage.getMotherId());
                snapshot.put(DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID, linkage.getPregnancyEpisodeId());
                snapshot.put(DBConstants.KEY.PARTNER_MOTHER_PREGNANT, linkage.getMotherPregnant());
                snapshot.put(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE,
                        linkage.getNextPhase());
                active = linkage.isActive();
            }
            try {
                add(STATUS, R.string.mothermentor_follow_up_status_section,
                        "mothermentor_partner_follow_up_status",
                        new MotherMentorPartnerFollowUpStatusActionHelper(this::clearHiddenActions), false);
                add(ARRIVAL, R.string.mothermentor_partner_attendance_section,
                        "mothermentor_partner_arrival",
                        new MotherMentorPartnerArrivalActionHelper(this::clearHiddenActions), false);
                add(REFERRAL, R.string.mothermentor_partner_referral_section,
                        "mothermentor_partner_testing_referral",
                        new MotherMentorPartnerReferralActionHelper(), false);
                add(EDUCATION, R.string.mothermentor_partner_education_section,
                        "mothermentor_partner_education",
                        new MotherMentorPartnerEducationActionHelper(), !isPhase(TRACING));
                add(COMMENTS, R.string.mothermentor_comments_section,
                        "mothermentor_partner_follow_up_comment",
                        new MotherMentorPartnerFollowUpCommentActionHelper(), true);
                clearHiddenActions();
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            appExecutors.mainThread().execute(() -> callback.preloadActions(actions));
        });
    }

    private void add(
            String key, int title, String form,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper, boolean optional)
            throws BaseMotherMentorVisitAction.ValidationException {
        String localizedTitle = context.getString(title);
        BaseMotherMentorVisitAction action = getBuilder(localizedTitle)
                .withOptional(optional).withDetails(details).withHelper(helper)
                .withValidator(new BranchValidator(key)).withFormName(form).build();
        actions.put(localizedTitle, action);
        workflow.put(key, action);
    }

    private boolean isPhase(String phase) {
        return phase.equals(snapshot.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE));
    }

    private boolean found() {
        return "found".equals(value(STATUS, DBConstants.KEY.FOLLOW_UP_STATUS));
    }

    private boolean attended() {
        return "yes".equals(value(ARRIVAL, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY));
    }

    private boolean complete(String key) {
        BaseMotherMentorVisitAction action = workflow.get(key);
        return action != null && action.getMotherMentorVisitActionHelper()
                .evaluateStatusOnPayload() == BaseMotherMentorVisitAction.Status.COMPLETED;
    }

    private boolean visible(String key) {
        if (!active) return false;
        switch (key) {
            case STATUS: return isPhase(TRACING);
            case REFERRAL: return isPhase(REFERRAL);
            case ARRIVAL: return isPhase(ATTENDANCE);
            case EDUCATION: return isPhase(ATTENDANCE) ? attended() && complete(ARRIVAL)
                    : isPhase(REFERRAL) || found();
            case COMMENTS: return isPhase(REFERRAL) || (isPhase(ATTENDANCE)
                    && (attended() || "no".equals(value(ARRIVAL, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))));
            default: return true;
        }
    }

    private String value(String key, String field) {
        BaseMotherMentorVisitAction action = workflow.get(key);
        if (action == null || StringUtils.isBlank(action.getJsonPayload())) return "";
        try {
            return JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), field);
        } catch (Exception e) {
            Timber.e(e);
            return "";
        }
    }

    private void clearHiddenActions() {
        if (clearingHiddenActions) return;
        clearingHiddenActions = true;
        try {
            for (Map.Entry<String, BaseMotherMentorVisitAction> entry : workflow.entrySet()) {
                if (!visible(entry.getKey()) && !COMMENTS.equals(entry.getKey())) {
                    entry.getValue().setJsonPayload(null);
                    entry.getValue().setSubTitle(null);
                }
            }
        } finally {
            clearingHiddenActions = false;
        }
    }

    @Override
    public void submitVisit(boolean edit, String memberId,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callback) {
        if (!active) {
            callback.onSubmitted(null);
            return;
        }
        LinkedHashMap<String, BaseMotherMentorVisitAction> visible = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : map.entrySet()) {
            if (entry.getValue().isValid()) visible.put(entry.getKey(), entry.getValue());
        }
        super.submitVisit(edit, memberId, visible, callback);
    }

    @Override
    protected String submitVisit(boolean edit, String memberId,
            Map<String, BaseMotherMentorVisitAction> map, String parentEventType) throws Exception {
        if (!MotherMentorPartnerFollowUpVisitUtils.isWorkflowComplete(currentValues())
                || loadClosureState(memberId, editedVisitId).isClosed()) return null;
        return super.submitVisit(edit, memberId, map, parentEventType);
    }

    @Override
    protected boolean shouldSubmitOnSave() {
        return true;
    }

    @Override
    protected void processSavedVisit(Visit visit) throws Exception {
        VisitUtils.persistVisitEvents(Collections.singletonList(visit), visitRepository());
        NCUtils.getClientProcessorForJava().processClient(NCUtils.getSyncHelper()
                .getEvents(Collections.singletonList(visit.getFormSubmissionId())));
    }

    @Override
    protected String getEncounterType() {
        return getCurrentVisitType();
    }

    @Override
    protected String getTableName() {
        return MotherMentorFollowUpType.FOLLOW_UP_TABLE;
    }

    @Override
    protected void prepareEvent(Event event) {
        if (event == null) return;
        restoreHistoricalEducation(event);
        if (!isPhase(ATTENDANCE) || !"not_found".equals(value(ARRIVAL, DBConstants.KEY.FOLLOW_UP_STATUS))) {
            for (Obs observation : historicalTests) event.addObs(observation);
        }
        event.setEventDate(serviceDate);
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(serviceDate));
        putObservation(event, DBConstants.KEY.FOLLOW_UP_CATEGORY,
                MotherMentorFollowUpType.PARTNER.getValue());
        putObservation(event, DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA, REFERRAL_SCHEMA);
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            putObservation(event, entry.getKey(), entry.getValue());
        }
        MotherMentorPartnerClosure closure = previewClosure();
        for (Map.Entry<String, String> entry :
                MotherMentorPartnerClosureRepository.observationValues(closure).entrySet()) {
            putObservation(event, entry.getKey(), entry.getValue());
        }
        String end = MotherMentorPartnerClosure.ATTENDED.equals(closure.getStatus())
                ? "completed" : (closure.isClosed() ? closure.getStatus() : "");
        putObservation(event, DBConstants.KEY.PARTNER_FOLLOW_UP_END, end);
    }

    /** Project the current form using the same linkage and answers as the saved event. */
    public MotherMentorPartnerClosure previewClosure() {
        if (memberObject == null) return MotherMentorPartnerClosure.active();
        Map<String, String> values = currentValues();
        MotherMentorPartnerClosure closure = loadClosureState(
                memberObject.getBaseEntityId(), editedVisitId);
        if (MotherMentorPartnerFollowUpVisitUtils.isWorkflowComplete(values)) {
            return closure.advance(values,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(serviceDate));
        }
        return closure;
    }

    private Map<String, String> currentValues() {
        Map<String, String> values = new LinkedHashMap<>(snapshot);
        values.put(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA, REFERRAL_SCHEMA);
        for (BaseMotherMentorVisitAction action : workflow.values()) {
            if (!action.isValid() || StringUtils.isBlank(action.getJsonPayload())) continue;
            try {
                JSONObject form = new JSONObject(action.getJsonPayload());
                for (int step = 1; step <= form.optInt("count", 1); step++) {
                    JSONArray fields = form.getJSONObject("step" + step)
                            .getJSONArray("fields");
                    for (int index = 0; index < fields.length(); index++) {
                        String key = fields.getJSONObject(index).optString("key");
                        values.put(key, JsonFormUtils.getValue(form, key));
                    }
                }
            } catch (Exception e) {
                Timber.e(e, "Unable to read partner visit answers");
            }
        }
        return values;
    }

    private void restoreHistoricalEducation(Event event) {
        if (!isPhase(ATTENDANCE)
                || !"no".equals(value(ARRIVAL, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))
                || originalNonAttendanceEducation.isEmpty()) return;
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (isEducationObservation(observations.next())) observations.remove();
            }
        }
        for (Obs obs : originalNonAttendanceEducation) event.addObs(obs);
    }

    private static boolean isTestObservation(Obs obs) {
        String key = StringUtils.defaultString(obs.getFieldCode());
        return key.startsWith("partner_hiv_") || key.startsWith("partner_syphilis_")
                || key.startsWith("partner_hepatitis_b_");
    }

    private static boolean isEducationObservation(Obs obs) {
        return DBConstants.KEY.PARTNER_EDUCATION_PROVIDED.equals(obs.getFieldCode())
                || DBConstants.KEY.PARTNER_EDUCATION_TOPICS.equals(obs.getFieldCode());
    }

    private static void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (key.equals(observations.next().getFieldCode())) observations.remove();
            }
        }
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<Object>(Collections.singletonList(StringUtils.defaultString(value))),
                new ArrayList<>(), null, key));
    }

    private class BranchValidator implements BaseMotherMentorVisitAction.Validator {
        private final String actionKey;

        private BranchValidator(String actionKey) {
            this.actionKey = actionKey;
        }

        @Override
        public boolean isValid(String key) {
            return visible(actionKey);
        }

        @Override
        public boolean isEnabled(String key) {
            return visible(actionKey);
        }

        @Override
        public void onChanged(String key) {}
    }
}
