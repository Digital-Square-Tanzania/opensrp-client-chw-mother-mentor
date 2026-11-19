package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorEducationProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorGroupLinkageActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorNextVisitDateActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorReferralProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitChildActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherClosureRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import timber.log.Timber;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class MotherMentorServiceVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String FORM_VISIT_TYPE = "mothermentor_visit_type";
    private static final String FORM_CHILD = "mothermentor_visit_child";
    private static final String FORM_GROUP_LINKAGE = "mothermentor_visit_group_linkage";
    private static final String FORM_EDUCATION_PROVIDED = "mothermentor_visit_education_provided";
    private static final String FORM_REFERRAL_PROVIDED = "mothermentor_visit_referal";
    private static final String FORM_NEXT_VISIT_DATE = "mothermentor_next_visit_date";
    private static final String FORM_COMMENT = "mothermentor_visit_comment";
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> motherMentorVisitActions =
            new LinkedHashMap<>();
    private boolean actionsReady;
    private boolean entrySaved;
    private String attendanceContext;
    private Date serviceDate = new Date();
    protected MotherMentorMotherVisitDao.Context enrollmentContext =
            MotherMentorMotherVisitDao.Context.empty();
    private String visitEnrollmentId;
    private String originalPregnancyStatus;
    private BaseMotherMentorVisitAction entryAction;
    private BaseMotherMentorVisitAction childAction;
    private MotherMentorVisitChildActionHelper childHelper;

    public MotherMentorServiceVisitInteractor(String visitType) {
        super(visitType);
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        serviceDate = new Date();
        enrollmentContext = loadMotherContext(member == null ? null : member.getBaseEntityId());
        visitEnrollmentId = enrollmentContext.getEnrollmentId();
        originalPregnancyStatus = enrollmentContext.getEnrollmentPregnancyStatus();
        if (!Boolean.TRUE.equals(view.getEditMode()) || member == null) return;
        Visit visit = loadEditingVisit(member.getBaseEntityId());
        if (visit == null) return;
        details = VisitUtils.getVisitGroups(
                MotherMentorLibrary.getInstance().visitDetailsRepository().getVisits(
                        visit.getVisitId()));
        try {
            JSONObject event = new JSONObject(visit.getJson());
            Map<String, String> values =
                    org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion
                            .observationValues(event.optJSONArray("obs"));
            visitEnrollmentId = StringUtils.defaultIfBlank(
                    values.get(DBConstants.KEY.MAMA_ENROLLMENT_ID), visitEnrollmentId);
            originalPregnancyStatus = StringUtils.defaultIfBlank(
                    values.get(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS),
                    originalPregnancyStatus);
            org.joda.time.LocalDate recorded = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (recorded != null) serviceDate = recorded.toDate();
            else if (visit.getDate() != null) serviceDate = visit.getDate();
        } catch (Exception e) {
            if (visit.getDate() != null) serviceDate = visit.getDate();
            Timber.e(e, "Unable to restore the mother visit context");
        }
    }

    protected MotherMentorMotherVisitDao.Context loadMotherContext(String memberId) {
        return StringUtils.isBlank(memberId) ? MotherMentorMotherVisitDao.Context.empty()
                                             : MotherMentorMotherVisitDao.getContext(memberId);
    }

    protected Visit loadEditingVisit(String memberId) {
        return MotherMentorLibrary.getInstance().visitRepository().getLatestVisit(
                memberId, getCurrentVisitType());
    }

    protected MotherMentorVisitTypeActionHelper createVisitTypeActionHelper() {
        return new MotherMentorVisitTypeActionHelper(
                memberObject, this::onEntryChanged, enrollmentContext);
    }

    @Override
    protected boolean shouldSubmitOnSave() {
        if (super.shouldSubmitOnSave()) return true;
        if (memberObject == null || serviceDate == null) return false;
        // A confirmed terminal visit must close the card and its Kaya even while offline.
        return MotherMentorMotherClosureRepository.preview(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberObject.getBaseEntityId(), null, clinicalValues(),
                LocalDate.fromDateFields(serviceDate)).isClosed();
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        final Runnable runnable = () -> {
            motherMentorVisitActions.clear();
            actionsReady = false;
            entrySaved = details != null && details.containsKey(DBConstants.KEY.ATTENDANCE_TYPE);
            try {
                MotherMentorVisitTypeActionHelper entryHelper = createVisitTypeActionHelper();
                entryAction = addVisitAction(context.getString(R.string.mothermentor_visit_type_section),
                        FORM_VISIT_TYPE, entryHelper, null);
                childHelper = new MotherMentorVisitChildActionHelper(entryHelper, this::onChildChanged);
                childAction = addVisitAction(context.getString(R.string.mothermentor_visit_child_section),
                        FORM_CHILD, childHelper, new VisitFlowValidator(FORM_CHILD));
                refreshChildSection(entrySaved);
                addVisitAction(context.getString(R.string.mothermentor_group_linkage_section),
                        FORM_GROUP_LINKAGE,
                        new MotherMentorGroupLinkageActionHelper(memberObject, this::entryValues),
                        new VisitFlowValidator(FORM_GROUP_LINKAGE));
                addVisitAction(context.getString(R.string.mothermentor_education_provided_section),
                        FORM_EDUCATION_PROVIDED, new MotherMentorEducationProvidedActionHelper(),
                        new VisitFlowValidator(FORM_EDUCATION_PROVIDED));
                addVisitAction(context.getString(R.string.mothermentor_referral_provided_section),
                        FORM_REFERRAL_PROVIDED, new MotherMentorReferralProvidedActionHelper(),
                        new VisitFlowValidator(FORM_REFERRAL_PROVIDED));
                addVisitAction(context.getString(R.string.mothermentor_next_visit_date_section),
                        FORM_NEXT_VISIT_DATE, new MotherMentorNextVisitDateActionHelper(),
                        new VisitFlowValidator(FORM_NEXT_VISIT_DATE));
                addVisitAction(context.getString(R.string.mothermentor_comment_section),
                        FORM_COMMENT, new MotherMentorCommentActionHelper(),
                        new VisitFlowValidator(FORM_COMMENT));
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            attendanceContext = entryValues().get(DBConstants.KEY.ATTENDANCE_TYPE);
            actionsReady = true;
            clearHiddenServices();

            appExecutors.mainThread().execute(
                    () -> callBack.preloadActions(motherMentorVisitActions));
        };

        appExecutors.diskIO().execute(runnable);
    }

    private BaseMotherMentorVisitAction addVisitAction(String title, String formName,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper,
            BaseMotherMentorVisitAction.Validator validator)
            throws BaseMotherMentorVisitAction.ValidationException {
        BaseMotherMentorVisitAction action = getBuilder(title)
                                                     .withOptional(FORM_COMMENT.equals(formName))
                                                     .withDetails(details)
                                                     .withHelper(helper)
                                                     .withValidator(validator)
                                                     .withFormName(formName)
                                                     .build();
        motherMentorVisitActions.put(title, action);
        return action;
    }

    @Override
    public void submitVisit(boolean editMode, String memberID,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        Map<String, BaseMotherMentorVisitAction> visible = getVisibleVisitActions(map);
        if (MotherMentorMotherVisitUtils.hasFirstVisitAssessment(entryValues())) {
            for (BaseMotherMentorVisitAction action : visible.values()) {
                if (!action.isOptional()
                        && action.getActionStatus() != BaseMotherMentorVisitAction.Status.COMPLETED) {
                    callBack.onSubmitted(null);
                    return;
                }
            }
        }
        super.submitVisit(editMode, memberID, visible, callBack);
    }

    @Override
    protected Visit saveVisit(boolean editMode, String memberID, String encounterType,
            Map<String, String> forms, String parentEventType) throws Exception {
        if (MotherMentorMotherVisitUtils.hasFirstVisitAssessment(entryValues())) {
            // Mpya submits the child answers from Mtoto wa 1. Attendance retains those controls for Marudio.
            String title = entryAction.getTitle();
            JSONObject entry = new JSONObject(forms.get(title));
            JSONArray fields = entry.getJSONObject("step1").getJSONArray("fields");
            for (int i = fields.length() - 1; i >= 0; i--) {
                if (MotherMentorMotherChildUtils.isChildField(fields.getJSONObject(i).getString("key"))) {
                    fields.remove(i);
                }
            }
            forms = new LinkedHashMap<>(forms);
            forms.put(title, entry.toString());
        }
        return super.saveVisit(editMode, memberID, encounterType, forms, parentEventType);
    }

    @Override
    protected String getEncounterType() {
        return Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES;
    }

    private Map<String, BaseMotherMentorVisitAction> getVisibleVisitActions(
            Map<String, BaseMotherMentorVisitAction> map) {
        LinkedHashMap<String, BaseMotherMentorVisitAction> visibleActions = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : map.entrySet()) {
            if (entry.getValue().isValid()) {
                visibleActions.put(entry.getKey(), entry.getValue());
            }
        }
        return visibleActions;
    }

    private boolean shouldShowFullVisitActions() {
        return entrySaved
                && MotherMentorMotherClosureRepository.classify(
                        clinicalValues(), LocalDate.fromDateFields(serviceDate)) == VisitOutcome.CONTINUE;
    }

    private boolean shouldShowChildSection() {
        if (!entrySaved) return false;
        Map<String, String> values = entryValues();
        return MotherMentorMotherVisitUtils.hasFirstVisitAssessment(values)
                && MotherMentorMotherPregnancyUtils.isChildPath(values, LocalDate.fromDateFields(serviceDate))
                && MotherMentorMotherChildUtils.applies(values);
    }

    private void refreshChildSection(boolean restoring) {
        if (childAction == null) return;
        if (!shouldShowChildSection()) {
            childAction.setJsonPayload(null);
            return;
        }
        try {
            // Reopening and saving the same attendance section keeps the child's existing answers.
            JSONObject entry = new JSONObject(entryAction.getJsonPayload());
            String current = childAction.getJsonPayload();
            if (!restoring && StringUtils.isNotBlank(current)
                    && childHelper.matchesMother(new JSONObject(current), entry)) return;
            String prepared = childHelper.prepare(entry, restoring);
            childAction.setJsonPayload(restoring ? prepared : null);
            if (!restoring) childAction.setProcessedJsonPayload(prepared);
        } catch (Exception e) {
            childAction.setJsonPayload(null);
            Timber.e(e, "Unable to prepare the first-visit child section");
        }
    }

    private Map<String, String> entryValues() {
        return actionValues(entryAction);
    }

    private Map<String, String> clinicalValues() {
        Map<String, String> values = entryValues();
        if (shouldShowChildSection()) {
            // Ignore inline fields carried from an edited visit until the child section supplies them.
            values.keySet().removeIf(MotherMentorMotherChildUtils::isChildField);
            values.putAll(actionValues(childAction));
        }
        return values;
    }

    private Map<String, String> actionValues(BaseMotherMentorVisitAction action) {
        if (action != null && StringUtils.isNotBlank(action.getJsonPayload())) {
            try {
                return MotherMentorMotherVisitUtils.formValues(new JSONObject(action.getJsonPayload()));
            } catch (Exception e) {
                Timber.e(e, "Unable to read the mother visit action");
            }
        }
        return Collections.emptyMap();
    }

    private void onChildChanged() {
        if (actionsReady) clearHiddenServices();
    }

    protected void onEntryChanged() {
        if (!actionsReady) return;
        entrySaved = true;
        String currentAttendance = entryValues().get(DBConstants.KEY.ATTENDANCE_TYPE);
        if (!StringUtils.equals(attendanceContext, currentAttendance)) {
            for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
                if (FORM_GROUP_LINKAGE.equals(action.getFormName())) {
                    clearAnswers(action,
                            new String[] {DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                                    DBConstants.KEY.VISIT_ART_START_DATE});
                }
            }
        }
        attendanceContext = currentAttendance;
        refreshChildSection(false);
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (FORM_GROUP_LINKAGE.equals(action.getFormName())) {
                action.setJsonPayload(action.getJsonPayload());
            }
        }
        clearHiddenServices();
    }

    private void clearHiddenServices() {
        if (shouldShowFullVisitActions()) return;
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (!FORM_VISIT_TYPE.equals(action.getFormName())
                    && !FORM_CHILD.equals(action.getFormName())
                    && !FORM_COMMENT.equals(action.getFormName()))
                clearAnswers(action, null);
        }
    }

    private void clearAnswers(BaseMotherMentorVisitAction action, String[] selectedKeys) {
        try {
            JSONObject form = new JSONObject(action.getJsonPayload());
            for (int step = 1; step <= form.optInt("count", 1); step++) {
                JSONArray fields = form.getJSONObject("step" + step).getJSONArray("fields");
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    if ("hidden".equals(field.optString("type"))) continue;
                    if (selectedKeys != null
                            && !java.util.Arrays.asList(selectedKeys)
                                    .contains(field.optString("key")))
                        continue;
                    field.put("value", "");
                    JSONArray options = field.optJSONArray("options");
                    if ("check_box".equals(field.optString("type")) && options != null) {
                        for (int j = 0; j < options.length(); j++) {
                            options.getJSONObject(j).put("value", false);
                        }
                    }
                }
            }
            action.setJsonPayload(form.toString());
            action.setSubTitle("");
        } catch (Exception e) {
            Timber.e(e, "Unable to clear hidden mother services");
        }
    }

    @Override
    protected void prepareEvent(Event event) {
        if (event == null) return;
        event.setEventDate(serviceDate);
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(serviceDate));
        putObservation(event, DBConstants.KEY.MAMA_ENROLLMENT_ID, visitEnrollmentId);
        putObservation(
                event, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS, originalPregnancyStatus);
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA.equals(
                            observations.next().getFieldCode())) {
                    observations.remove();
                }
            }
        }
    }

    private void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext())
                if (key.equals(observations.next().getFieldCode())) observations.remove();
        }
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<>(Collections.singletonList(StringUtils.defaultString(value))),
                new ArrayList<>(), null, key));
    }

    private class VisitFlowValidator implements BaseMotherMentorVisitAction.Validator {
        private final String formName;

        private VisitFlowValidator(String formName) {
            this.formName = formName;
        }

        @Override
        public boolean isValid(String key) {
            if (FORM_CHILD.equals(formName)) return shouldShowChildSection();
            return FORM_COMMENT.equals(formName) || shouldShowFullVisitActions();
        }

        @Override
        public boolean isEnabled(String key) {
            return isValid(key);
        }

        @Override
        public void onChanged(String key) {
            // Visibility is recalculated by the adapter on redraw.
        }
    }
}
