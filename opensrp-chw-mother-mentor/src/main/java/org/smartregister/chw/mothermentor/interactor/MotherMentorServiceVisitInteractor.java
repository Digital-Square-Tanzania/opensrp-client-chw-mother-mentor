package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorEducationProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorGroupLinkageActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorNextVisitDateActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorReferralProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
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
    private static final String FORM_GROUP_LINKAGE = "mothermentor_visit_group_linkage";
    private static final String FORM_EDUCATION_PROVIDED = "mothermentor_visit_education_provided";
    private static final String FORM_REFERRAL_PROVIDED = "mothermentor_visit_referal";
    private static final String FORM_NEXT_VISIT_DATE = "mothermentor_next_visit_date";
    private static final String FORM_COMMENT = "mothermentor_visit_comment";
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> motherMentorVisitActions =
            new LinkedHashMap<>();
    private boolean actionsReady;
    private boolean entrySaved;
    private boolean legacyVisit;
    private String attendanceContext;
    private Date serviceDate = new Date();
    protected MotherMentorMotherVisitDao.Context enrollmentContext =
            MotherMentorMotherVisitDao.Context.empty();
    private String visitEnrollmentId;
    private String originalPregnancyStatus;

    public MotherMentorServiceVisitInteractor(String visitType) {
        super(visitType);
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        legacyVisit = false;
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
        legacyVisit = true;
        try {
            JSONObject event = new JSONObject(visit.getJson());
            Map<String, String> values =
                    org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion
                            .observationValues(event.optJSONArray("obs"));
            legacyVisit = !MotherMentorMotherVisitUtils.isSchema2(values);
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
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        final Runnable runnable = () -> {
            motherMentorVisitActions.clear();
            actionsReady = false;
            entrySaved = legacyVisit
                    || (details != null && details.containsKey(DBConstants.KEY.MAMA_VISIT_SCHEMA));
            try {
                addVisitAction(context.getString(R.string.mothermentor_visit_type_section),
                        FORM_VISIT_TYPE, createVisitTypeActionHelper(), null);
                addVisitAction(context.getString(R.string.mothermentor_group_linkage_section),
                        FORM_GROUP_LINKAGE,
                        new MotherMentorGroupLinkageActionHelper(memberObject, this::entryValues),
                        new VisitFlowValidator(false));
                addVisitAction(context.getString(R.string.mothermentor_education_provided_section),
                        FORM_EDUCATION_PROVIDED, new MotherMentorEducationProvidedActionHelper(),
                        new VisitFlowValidator(false));
                addVisitAction(context.getString(R.string.mothermentor_referral_provided_section),
                        FORM_REFERRAL_PROVIDED, new MotherMentorReferralProvidedActionHelper(),
                        new VisitFlowValidator(false));
                addVisitAction(context.getString(R.string.mothermentor_next_visit_date_section),
                        FORM_NEXT_VISIT_DATE, new MotherMentorNextVisitDateActionHelper(),
                        new VisitFlowValidator(false));
                addVisitAction(context.getString(R.string.mothermentor_comment_section),
                        FORM_COMMENT, new MotherMentorCommentActionHelper(),
                        new VisitFlowValidator(true));
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

    private void addVisitAction(String title, String formName,
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
    }

    @Override
    public void submitVisit(boolean editMode, String memberID,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        super.submitVisit(editMode, memberID, getVisibleVisitActions(map), callBack);
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
        return entrySaved && MotherMentorMotherVisitUtils.isFullVisit(entryValues());
    }

    private Map<String, String> entryValues() {
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (FORM_VISIT_TYPE.equals(action.getFormName())) {
                try {
                    return MotherMentorMotherVisitUtils.formValues(
                            new JSONObject(action.getJsonPayload()));
                } catch (Exception ignored) {
                    return Collections.emptyMap();
                }
            }
        }
        return Collections.emptyMap();
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
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (FORM_GROUP_LINKAGE.equals(action.getFormName())) {
                action.setJsonPayload(action.getJsonPayload());
            }
        }
        clearHiddenServices();
    }

    private void clearHiddenServices() {
        if (legacyVisit || shouldShowFullVisitActions()) return;
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (!FORM_VISIT_TYPE.equals(action.getFormName())
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
        if (legacyVisit) return;
        putObservation(event, DBConstants.KEY.MAMA_VISIT_SCHEMA, "2");
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
        private final boolean commentAction;

        private VisitFlowValidator(boolean commentAction) {
            this.commentAction = commentAction;
        }

        @Override
        public boolean isValid(String key) {
            return commentAction || shouldShowFullVisitActions();
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
