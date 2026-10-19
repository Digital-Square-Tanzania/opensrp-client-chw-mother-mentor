package org.smartregister.chw.mothermentor.interactor;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidArrivalActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidTestTypeActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;

/** Child EID-specific follow-up action assembly. */
public class ChildEidFollowUpVisitInteractor extends BaseMotherMentorFollowUpVisitInteractor {
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
        return new MotherMentorChildEidArrivalActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createCommentsActionHelper() {
        return new MotherMentorChildEidFollowUpCommentActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createTestTypeActionHelper() {
        return new MotherMentorChildEidTestTypeActionHelper(memberObject);
    }
}
