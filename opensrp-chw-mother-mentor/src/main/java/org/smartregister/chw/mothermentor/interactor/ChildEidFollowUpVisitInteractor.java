package org.smartregister.chw.mothermentor.interactor;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidArrivalActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidTestTypeActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFinalTestActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.util.ArrayList;
import java.util.Collections;

/** Child EID-specific follow-up action assembly. */
public class ChildEidFollowUpVisitInteractor extends BaseMotherMentorFollowUpVisitInteractor {
    private static final String FINAL_TEST = "final_test";
    public ChildEidFollowUpVisitInteractor(String eventType) {
        super(
                new FollowUpFormDefinition(
                                eventType,
                                MotherMentorFollowUpType.CHILD_EID.getValue(),
                                R.string.mothermentor_follow_up_status_section,
                                R.string.mothermentor_child_eid_arrival_section,
                                R.string.mothermentor_comments_section,
                                "mothermentor_child_eid_follow_up_status",
                                "mothermentor_child_eid_arrival",
                                "mothermentor_child_eid_follow_up_comment")
                        .withTestType(
                                R.string.mothermentor_child_eid_test_type_section,
                                "mothermentor_child_eid_test_type"));
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createStatusActionHelper(
            Runnable onStatusChanged) {
        return new MotherMentorChildEidFollowUpStatusActionHelper(onStatusChanged);
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createArrivalActionHelper() {
        return new MotherMentorChildEidArrivalActionHelper(this::clearAdditionalActions);
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createCommentsActionHelper() {
        return new MotherMentorChildEidFollowUpCommentActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createTestTypeActionHelper() {
        return new MotherMentorChildEidTestTypeActionHelper(
                memberObject, this::clearAdditionalActions);
    }

    @Override
    protected void addAdditionalActions() throws BaseMotherMentorVisitAction.ValidationException {
        add(FINAL_TEST, R.string.mothermentor_child_final_test_section,
                "mothermentor_child_eid_final_test", new MotherMentorChildEidFinalTestActionHelper(),
                new BaseMotherMentorVisitAction.Validator() {
                    @Override
                    public boolean isValid(String key) { return finalTestRequired(); }

                    @Override
                    public boolean isEnabled(String key) { return finalTestRequired(); }

                    @Override
                    public void onChanged(String key) {}
                }, false);
    }

    private boolean finalTestRequired() {
        return clientFound()
                && "eighteen_months".equals(value(TEST_TYPE, DBConstants.KEY.CHILD_EID_TEST_TYPE))
                && "yes".equals(value(ARRIVAL, DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY));
    }

    @Override
    protected void clearAdditionalActions() {
        if (!finalTestRequired()) clearAction(FINAL_TEST);
    }

    @Override
    protected void prepareEvent(Event event) {
        super.prepareEvent(event);
        if (event != null) event.addObs(new Obs("concept", "text",
                DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA, "",
                new ArrayList<>(Collections.singletonList("2")), new ArrayList<>(), null,
                DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA));
    }
}
