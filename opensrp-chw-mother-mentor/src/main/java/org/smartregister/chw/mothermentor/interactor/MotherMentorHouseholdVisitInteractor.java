package org.smartregister.chw.mothermentor.interactor;

import android.text.TextUtils;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdCounsellingActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdEducationActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdVisitPurposeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;

import timber.log.Timber;

import java.util.LinkedHashMap;
import java.util.Map;

public class MotherMentorHouseholdVisitInteractor extends BaseMotherMentorServiceVisitInteractor {

    private static final String FORM_VISIT_PURPOSE = "mothermentor_household_visit_purpose";
    private static final String FORM_COUNSELLING =
            "mother_mentor_household_members_who_received_counselling";
    private static final String FORM_EDUCATION = "mother_mentor_household_visit_topics_taught";
    private static final String FORM_COMMENT = "mothermentor_household_visit_comment";

    private final LinkedHashMap<String, BaseMotherMentorVisitAction> householdVisitActions =
            new LinkedHashMap<>();

    private final String householdTable;

    public MotherMentorHouseholdVisitInteractor(String visitType, String householdTable) {
        super(visitType);
        this.householdTable = householdTable;
    }

    @Override
    public MemberObject getMemberClient(String memberID, String profileType) {
        if (TextUtils.isEmpty(memberID)) {
            return null;
        }

        MemberObject householdMember = MotherMentorDao.getHouseholdMember(memberID, householdTable);
        if (householdMember != null) {
            return householdMember;
        }

        return super.getMemberClient(memberID, profileType);
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        final Runnable runnable =
                () -> {
                    householdVisitActions.clear();
                    try {
                        addVisitAction(
                                context.getString(
                                        R.string.mothermentor_household_visit_kusudi_la_ziara),
                                FORM_VISIT_PURPOSE,
                                new MotherMentorHouseholdVisitPurposeActionHelper());
                        addVisitAction(
                                context.getString(
                                        R.string
                                                .mothermentor_household_visit_wanakaya_waliopewa_ushauri),
                                FORM_COUNSELLING,
                                new MotherMentorHouseholdCounsellingActionHelper());
                        addVisitAction(
                                context.getString(
                                        R.string.mothermentor_household_visit_mada_zilizofundishwa),
                                FORM_EDUCATION,
                                new MotherMentorHouseholdEducationActionHelper());
                        addVisitAction(
                                context.getString(R.string.mothermentor_household_visit_maoni),
                                FORM_COMMENT,
                                new MotherMentorHouseholdCommentActionHelper());
                    } catch (BaseMotherMentorVisitAction.ValidationException e) {
                        Timber.e(e);
                    }

                    appExecutors
                            .mainThread()
                            .execute(() -> callBack.preloadActions(householdVisitActions));
                };

        appExecutors.diskIO().execute(runnable);
    }

    private void addVisitAction(
            String title,
            String formName,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper)
            throws BaseMotherMentorVisitAction.ValidationException {
        BaseMotherMentorVisitAction action =
                getBuilder(title)
                        .withOptional(FORM_COMMENT.equals(formName))
                        .withDetails(details)
                        .withHelper(helper)
                        .withFormName(formName)
                        .build();
        householdVisitActions.put(title, action);
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
            if (entry.getValue() != null && entry.getValue().isValid()) {
                visibleActions.put(entry.getKey(), entry.getValue());
            }
        }
        return visibleActions;
    }
}
