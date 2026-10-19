package org.smartregister.chw.mothermentor.interactor;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerArrivalActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;

/** Partner-specific follow-up workflow with an independent form and event contract. */
public class PartnerFollowUpVisitInteractor extends BaseMotherMentorFollowUpVisitInteractor {
    public PartnerFollowUpVisitInteractor(String eventType) {
        super(
                new FollowUpFormDefinition(
                        eventType,
                        MotherMentorFollowUpType.PARTNER.getValue(),
                        R.string.mothermentor_follow_up_status_section,
                        R.string.mothermentor_partner_arrival_section,
                        R.string.mothermentor_comments_section,
                        "mothermentor_partner_follow_up_status",
                        "mothermentor_partner_arrival",
                        "mothermentor_partner_follow_up_comment"));
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createStatusActionHelper(
            Runnable onStatusChanged) {
        return new MotherMentorPartnerFollowUpStatusActionHelper(onStatusChanged);
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createArrivalActionHelper() {
        return new MotherMentorPartnerArrivalActionHelper();
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createCommentsActionHelper() {
        return new MotherMentorPartnerFollowUpCommentActionHelper();
    }
}
