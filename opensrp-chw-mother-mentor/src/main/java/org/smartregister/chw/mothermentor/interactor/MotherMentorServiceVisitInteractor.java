package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorEducationProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorGroupLinkageActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorNextVisitDateActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorReferralProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import timber.log.Timber;

import java.util.LinkedHashMap;
import java.util.Map;

public class MotherMentorServiceVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String ACTION_VISIT_TYPE = "Visit Type";
    private static final String ACTION_GROUP_LINKAGE = "Group Linkage";
    private static final String ACTION_EDUCATION_PROVIDED = "Education Provided";
    private static final String ACTION_REFERRAL_PROVIDED = "Referral Provided";
    private static final String ACTION_NEXT_VISIT_DATE = "Next Visit Date";
    private static final String ACTION_COMMENT = "Comment";
    private static final String FORM_VISIT_TYPE = "mothermentor_visit_type";
    private static final String FORM_GROUP_LINKAGE = "mothermentor_visit_group_linkage";
    private static final String FORM_EDUCATION_PROVIDED = "mothermentor_visit_education_provided";
    private static final String FORM_REFERRAL_PROVIDED = "mothermentor_visit_referal";
    private static final String FORM_NEXT_VISIT_DATE = "mothermentor_next_visit_date";
    private static final String FORM_COMMENT = "mothermentor_visit_comment";
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> motherMentorVisitActions =
            new LinkedHashMap<>();

    public MotherMentorServiceVisitInteractor(String visitType) {
        super(visitType);
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        final Runnable runnable =
                () -> {
                    motherMentorVisitActions.clear();
                    try {
                        addVisitAction(
                                ACTION_VISIT_TYPE,
                                FORM_VISIT_TYPE,
                                new MotherMentorVisitTypeActionHelper(memberObject),
                                null);
                        addVisitAction(
                                ACTION_GROUP_LINKAGE,
                                FORM_GROUP_LINKAGE,
                                new MotherMentorGroupLinkageActionHelper(),
                                new VisitFlowValidator(false));
                        addVisitAction(
                                ACTION_EDUCATION_PROVIDED,
                                FORM_EDUCATION_PROVIDED,
                                new MotherMentorEducationProvidedActionHelper(),
                                new VisitFlowValidator(false));
                        addVisitAction(
                                ACTION_REFERRAL_PROVIDED,
                                FORM_REFERRAL_PROVIDED,
                                new MotherMentorReferralProvidedActionHelper(),
                                new VisitFlowValidator(false));
                        addVisitAction(
                                ACTION_NEXT_VISIT_DATE,
                                FORM_NEXT_VISIT_DATE,
                                new MotherMentorNextVisitDateActionHelper(),
                                new VisitFlowValidator(false));
                        addVisitAction(
                                ACTION_COMMENT,
                                FORM_COMMENT,
                                new MotherMentorCommentActionHelper(),
                                new VisitFlowValidator(true));
                    } catch (BaseMotherMentorVisitAction.ValidationException e) {
                        Timber.e(e);
                    }

                    appExecutors
                            .mainThread()
                            .execute(() -> callBack.preloadActions(motherMentorVisitActions));
                };

        appExecutors.diskIO().execute(runnable);
    }

    private void addVisitAction(
            String title,
            String formName,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper,
            BaseMotherMentorVisitAction.Validator validator)
            throws BaseMotherMentorVisitAction.ValidationException {
        BaseMotherMentorVisitAction action =
                getBuilder(title)
                        .withOptional(false)
                        .withDetails(details)
                        .withHelper(helper)
                        .withValidator(validator)
                        .withFormName(formName)
                        .build();
        motherMentorVisitActions.put(title, action);
    }

    @Override
    public void submitVisit(
            boolean editMode,
            String memberID,
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
        VisitTypeSelection selection = getVisitTypeSelection();
        return org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion.isFullVisit(
                selection.attendanceType, selection.followUpStatus);
    }

    private boolean isVisitTypeAnswered() {
        return StringUtils.isNotBlank(getVisitTypeSelection().attendanceType);
    }

    private VisitTypeSelection getVisitTypeSelection() {
        BaseMotherMentorVisitAction visitTypeAction =
                motherMentorVisitActions.get(ACTION_VISIT_TYPE);
        if (visitTypeAction == null || StringUtils.isBlank(visitTypeAction.getJsonPayload())) {
            return new VisitTypeSelection(null, null);
        }

        try {
            JSONObject jsonObject = new JSONObject(visitTypeAction.getJsonPayload());
            return new VisitTypeSelection(
                    JsonFormUtils.getValue(jsonObject, DBConstants.KEY.ATTENDANCE_TYPE),
                    JsonFormUtils.getValue(jsonObject, DBConstants.KEY.FOLLOW_UP_STATUS));
        } catch (Exception e) {
            Timber.e(e);
            return new VisitTypeSelection(null, null);
        }
    }

    private class VisitFlowValidator implements BaseMotherMentorVisitAction.Validator {
        private final boolean commentAction;

        private VisitFlowValidator(boolean commentAction) {
            this.commentAction = commentAction;
        }

        @Override
        public boolean isValid(String key) {
            return commentAction ? isVisitTypeAnswered() : shouldShowFullVisitActions();
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

    private static class VisitTypeSelection {
        private final String attendanceType;
        private final String followUpStatus;

        private VisitTypeSelection(String attendanceType, String followUpStatus) {
            this.attendanceType = attendanceType;
            this.followUpStatus = followUpStatus;
        }
    }
}
