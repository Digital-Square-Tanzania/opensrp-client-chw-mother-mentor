package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import timber.log.Timber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared section UI and validation plumbing for category-specific follow-up forms. */
public abstract class BaseMotherMentorFollowUpVisitInteractor
        extends BaseMotherMentorServiceVisitInteractor {
    private static final String STATUS = DBConstants.KEY.STATUS;
    private static final String TEST_TYPE = "test_type";
    private static final String REASON = "reason";
    private static final String ARRIVAL = "arrival";
    private static final String COMMENTS = DBConstants.KEY.COMMENTS;
    protected static final String FOLLOW_UP_STATUS = DBConstants.KEY.FOLLOW_UP_STATUS;

    private final FollowUpFormDefinition definition;
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> actions =
            new LinkedHashMap<>();
    private final Map<String, BaseMotherMentorVisitAction> workflowActions = new LinkedHashMap<>();

    protected BaseMotherMentorFollowUpVisitInteractor(FollowUpFormDefinition definition) {
        super(definition.eventType);
        this.definition = definition;
    }

    @Override
    public MemberObject getMemberClient(String memberId, String profileType) {
        MemberObject memberObject = super.getMemberClient(memberId, profileType);
        return memberObject != null
                ? memberObject
                : MotherMentorDao.getSecondaryEnrollmentMember(memberId);
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        appExecutors
                .diskIO()
                .execute(
                        () -> {
                            actions.clear();
                            workflowActions.clear();
                            try {
                                add(
                                        STATUS,
                                        definition.statusTitle,
                                        definition.statusForm,
                                        createStatusActionHelper(this::clearHiddenActions),
                                        null,
                                        false);
                                if (StringUtils.isNotBlank(definition.testTypeForm)) {
                                    add(
                                            TEST_TYPE,
                                            definition.testTypeTitle,
                                            definition.testTypeForm,
                                            createTestTypeActionHelper(),
                                            new FoundValidator(),
                                            false);
                                }
                                if (StringUtils.isNotBlank(definition.reasonForm)) {
                                    add(
                                            REASON,
                                            definition.reasonTitle,
                                            definition.reasonForm,
                                            createReasonActionHelper(),
                                            new FoundValidator(),
                                            false);
                                }
                                add(
                                        ARRIVAL,
                                        definition.arrivalTitle,
                                        definition.arrivalForm,
                                        createArrivalActionHelper(),
                                        new FoundValidator(),
                                        false);
                                add(
                                        COMMENTS,
                                        definition.commentsTitle,
                                        definition.commentsForm,
                                        createCommentsActionHelper(),
                                        new AnsweredValidator(),
                                        true);
                            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                                Timber.e(e);
                            }
                            appExecutors
                                    .mainThread()
                                    .execute(() -> callBack.preloadActions(actions));
                        });
    }

    private void add(
            String key,
            int title,
            String form,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper,
            BaseMotherMentorVisitAction.Validator validator,
            boolean optional)
            throws BaseMotherMentorVisitAction.ValidationException {
        String localizedTitle = context.getString(title);
        BaseMotherMentorVisitAction action =
                getBuilder(localizedTitle)
                        .withOptional(optional)
                        .withDetails(details)
                        .withHelper(helper)
                        .withValidator(validator)
                        .withFormName(form)
                        .build();
        actions.put(localizedTitle, action);
        workflowActions.put(key, action);
    }

    @Override
    public void submitVisit(
            boolean edit,
            String memberId,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callback) {
        LinkedHashMap<String, BaseMotherMentorVisitAction> visible = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : map.entrySet()) {
            if (entry.getValue().isValid()) visible.put(entry.getKey(), entry.getValue());
        }
        super.submitVisit(edit, memberId, visible, callback);
    }

    @Override
    protected String getEncounterType() {
        return definition.eventType;
    }

    @Override
    protected String getTableName() {
        return MotherMentorFollowUpType.FOLLOW_UP_TABLE;
    }

    @Override
    protected void prepareEvent(Event event) {
        super.prepareEvent(event);
        if (event != null) {
            List<Object> values = new ArrayList<>();
            values.add(definition.category);
            event.addObs(
                    new Obs(
                            "concept",
                            "text",
                            DBConstants.KEY.FOLLOW_UP_CATEGORY,
                            "",
                            values,
                            new ArrayList<>(),
                            null,
                            DBConstants.KEY.FOLLOW_UP_CATEGORY));
        }
    }

    private String value(String actionKey, String field) {
        BaseMotherMentorVisitAction action = workflowActions.get(actionKey);
        if (action == null || StringUtils.isBlank(action.getJsonPayload())) return null;
        try {
            return JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), field);
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }

    private boolean statusAnswered() {
        return StringUtils.isNotBlank(value(STATUS, FOLLOW_UP_STATUS));
    }

    private boolean clientFound() {
        return "found".equalsIgnoreCase(value(STATUS, FOLLOW_UP_STATUS));
    }

    private void clearHiddenActions() {
        if (clientFound()) return;
        clearAction(REASON);
        clearAction(ARRIVAL);
        clearAction(TEST_TYPE);
    }

    private void clearAction(String key) {
        BaseMotherMentorVisitAction action = workflowActions.get(key);
        if (action != null) {
            action.setJsonPayload(null);
            action.setSubTitle(null);
        }
    }

    private class FoundValidator implements BaseMotherMentorVisitAction.Validator {
        @Override
        public boolean isValid(String key) {
            return clientFound();
        }

        @Override
        public boolean isEnabled(String key) {
            return clientFound();
        }

        @Override
        public void onChanged(String key) {}
    }

    private class AnsweredValidator implements BaseMotherMentorVisitAction.Validator {
        @Override
        public boolean isValid(String key) {
            return statusAnswered();
        }

        @Override
        public boolean isEnabled(String key) {
            return statusAnswered();
        }

        @Override
        public void onChanged(String key) {}
    }

    protected abstract BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createStatusActionHelper(Runnable onStatusChanged);

    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createReasonActionHelper() {
        return null;
    }

    protected abstract BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createArrivalActionHelper();

    protected abstract BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createCommentsActionHelper();

    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createTestTypeActionHelper() {
        return null;
    }

    protected static class FollowUpFormDefinition {
        final String eventType;
        final String category;
        final int statusTitle, arrivalTitle, commentsTitle;
        int reasonTitle, testTypeTitle;
        final String statusForm, arrivalForm, commentsForm;
        String reasonForm, testTypeForm;

        protected FollowUpFormDefinition(
                String eventType,
                String category,
                int statusTitle,
                int arrivalTitle,
                int commentsTitle,
                String statusForm,
                String arrivalForm,
                String commentsForm) {
            this.eventType = eventType;
            this.category = category;
            this.statusTitle = statusTitle;
            this.arrivalTitle = arrivalTitle;
            this.commentsTitle = commentsTitle;
            this.statusForm = statusForm;
            this.arrivalForm = arrivalForm;
            this.commentsForm = commentsForm;
        }

        protected FollowUpFormDefinition withReason(int title, String form) {
            reasonTitle = title;
            reasonForm = form;
            return this;
        }

        protected FollowUpFormDefinition withTestType(int title, String form) {
            testTypeTitle = title;
            testTypeForm = form;
            return this;
        }
    }
}
