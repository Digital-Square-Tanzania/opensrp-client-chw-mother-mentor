package org.smartregister.chw.mothermentor.interactor;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorInterruptionReasonActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorReturnToFacilityActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;

/** Mother IIT-specific follow-up workflow. */
public class MotherMentorFollowUpVisitInteractor extends BaseMotherMentorFollowUpVisitInteractor {
    public MotherMentorFollowUpVisitInteractor(String eventType) {
        this(eventType, MotherMentorFollowUpType.MOTHER_IIT.getValue());
    }

    public MotherMentorFollowUpVisitInteractor(String eventType, String category) {
        super(
                new FollowUpFormDefinition(
                                eventType,
                                category,
                                R.string.mothermentor_follow_up_status_section,
                                R.string.mothermentor_return_to_facility_section,
                                R.string.mothermentor_comments_section,
                                "mothermentor_follow_up_status",
                                "mothermentor_return_to_facility",
                                "mothermentor_follow_up_comment")
                        .withReason(
                                R.string.mothermentor_interruption_reason_section,
                                "mothermentor_service_interruption_reason"));
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createStatusActionHelper(
            Runnable onStatusChanged) {
        return new MotherMentorFollowUpStatusActionHelper(onStatusChanged);
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createReasonActionHelper() {
        return new MotherMentorInterruptionReasonActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createArrivalActionHelper() {
        return new MotherMentorReturnToFacilityActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createCommentsActionHelper() {
        return new MotherMentorFollowUpCommentActionHelper();
    }
}
