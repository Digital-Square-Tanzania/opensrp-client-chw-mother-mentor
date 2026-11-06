package org.smartregister.chw.mothermentor.interactor;

import com.google.gson.JsonParseException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorIitReturnActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorIitStatusActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorIitRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import timber.log.Timber;

public class MotherMentorIitVisitInteractor extends BaseMotherMentorFollowUpVisitInteractor {
    private MotherMentorIitState state = MotherMentorIitState.active();
    private LocalDate serviceDate = LocalDate.now();
    private String editedVisitId;

    public MotherMentorIitVisitInteractor() {
        super(new FollowUpFormDefinition(
                MotherMentorFollowUpType.MOTHER_IIT.getEventType(),
                MotherMentorFollowUpType.MOTHER_IIT.getValue(),
                R.string.mothermentor_follow_up_status_section,
                R.string.mothermentor_return_to_facility_section,
                R.string.mothermentor_comments_section,
                "mothermentor_iit_follow_up_status",
                "mothermentor_iit_return_to_facility",
                "mothermentor_follow_up_comment"));
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        details = null;
        super.getDetailsOnEdit(view, member);
        memberObject = member;
        Visit edited = Boolean.TRUE.equals(view.getEditMode())
                ? visitRepository().getLatestVisit(member.getBaseEntityId(), getCurrentVisitType())
                : null;
        editedVisitId = edited == null ? null : edited.getVisitId();
        LocalDate recorded = edited == null ? null : MotherMentorMotherVisitDao.getServiceDate(edited);
        serviceDate = recorded == null ? LocalDate.now() : recorded;
        state = MotherMentorIitRepository.getState(database(), member.getBaseEntityId(), editedVisitId);
    }

    protected MotherMentorIitState getIitState() {
        return state;
    }

    protected LocalDate getIitServiceDate() {
        return serviceDate;
    }

    @Override
    protected boolean commentsRequireStatus() {
        return false;
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createStatusActionHelper(
            Runnable onStatusChanged) {
        return new MotherMentorIitStatusActionHelper(onStatusChanged, state, this::preview);
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createArrivalActionHelper() {
        MotherMentorIitState current = getIitState();
        return new MotherMentorIitReturnActionHelper(current.getReturnState(),
                current.getMissedAppointmentDate(), current.getRefusedCount(),
                current.getNotReturnedCount(), getIitServiceDate());
    }

    @Override
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createCommentsActionHelper() {
        return new MotherMentorFollowUpCommentActionHelper();
    }

    @Override
    protected boolean shouldSubmitOnSave() {
        return true;
    }

    @Override
    protected void prepareEvent(Event event) {
        super.prepareEvent(event);
        if (event == null) return;
        // Replay compares the actual event time with enrollment, even on the same clinical day.
        event.setEventDate(new Date());
        putObservation(event, DBConstants.KEY.IIT_FOLLOW_UP_SCHEMA, "2");
        putObservation(event, DBConstants.KEY.IIT_ENROLLMENT_ID, state.getEnrollmentId());
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, serviceDate.toString("dd-MM-yyyy"));
    }

    @Override
    protected void assignVisitSource(Event event, Visit editedVisit) {
        if (editedVisit == null || StringUtils.isBlank(editedVisit.getFormSubmissionId())) {
            super.assignVisitSource(event, editedVisit);
            return;
        }
        event.setFormSubmissionId(editedVisit.getFormSubmissionId());
        event.setEventId(editedVisit.getEventId());
        try {
            Event previous = JsonFormUtils.gson.fromJson(StringUtils.defaultIfBlank(
                    editedVisit.getJson(), editedVisit.getPreProcessedJson()), Event.class);
            if (previous == null) return;
            if (previous.getEventDate() != null) event.setEventDate(previous.getEventDate());
            if (StringUtils.isBlank(event.getEventId())) event.setEventId(previous.getEventId());
            if (event.getVersion() <= previous.getVersion()) {
                event.setVersion(previous.getVersion() + 1);
            }
        } catch (JsonParseException invalid) {
            Timber.w(invalid, "Unable to restore the original Mother IIT event metadata");
        }
    }

    private MotherMentorIitState preview(Map<String, String> answers) {
        return MotherMentorIitRepository.preview(database(), memberObject.getBaseEntityId(),
                editedVisitId, answers, serviceDate);
    }

    private void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (key.equals(observations.next().getFieldCode())) observations.remove();
            }
        }
        if (StringUtils.isBlank(value)) return;
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<>(Collections.singletonList(value)), new ArrayList<>(), null, key));
    }

    private SQLiteDatabase database() {
        return MotherMentorLibrary.getInstance().getRepository().getReadableDatabase();
    }
}
