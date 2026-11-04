package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidHeiActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/** Current Child EID tracing checklist, with an unchanged path for schema1/2 visits. */
public class ChildEidFollowUpVisitInteractor extends LegacyChildEidFollowUpVisitInteractor {
    private static final String STATUS_FORM = "mothermentor_child_eid_follow_up_status";
    private static final String COMMENT_FORM = "mothermentor_child_eid_follow_up_comment";
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> childActions =
            new LinkedHashMap<>();
    private BaseMotherMentorVisitContract.InteractorCallBack childCallback;
    private boolean actionsReady;
    private boolean statusSaved;
    private boolean legacyVisit;
    private String originalSchema;
    private LocalDate serviceDate = LocalDate.now();

    public ChildEidFollowUpVisitInteractor(String eventType) {
        super(eventType);
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        legacyVisit = false;
        originalSchema = null;
        statusSaved = false;
        serviceDate = LocalDate.now();
        if (!Boolean.TRUE.equals(view.getEditMode()) || member == null) return;
        Visit visit = loadEditingVisit(member.getBaseEntityId());
        if (visit == null) return;
        details = loadVisitDetails(visit);
        LocalDate recorded = MotherMentorMotherVisitDao.getServiceDate(visit);
        if (recorded != null) serviceDate = recorded;
        try {
            Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                    new JSONObject(visit.getJson()).optJSONArray("obs"));
            originalSchema = values.get(DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA);
            legacyVisit = !MotherMentorChildEidStatusUtils.isSchema3(values);
            statusSaved = !legacyVisit && MotherMentorChildEidStatusUtils.isStatusComplete(
                    values, childDob(), serviceDate);
        } catch (Exception e) {
            legacyVisit = true;
            Timber.e(e, "Unable to restore the Child EID visit schema");
        }
    }

    protected Visit loadEditingVisit(String memberId) {
        return visitRepository().getLatestVisit(memberId, getCurrentVisitType());
    }

    protected Map<String, List<VisitDetail>> loadVisitDetails(Visit visit) {
        return VisitUtils.getVisitGroups(MotherMentorLibrary.getInstance()
                .visitDetailsRepository().getVisits(visit.getVisitId()));
    }

    protected boolean useLegacyWorkflow() {
        return legacyVisit;
    }

    @Override
    protected boolean usesFinalTestSection() {
        return "2".equals(originalSchema);
    }

    protected LocalDate childDob() {
        LocalDate dob = memberObject == null ? null
                : MotherMentorHeiSchedule.parseBirthDate(memberObject.getDob());
        if (dob != null || memberObject == null) return dob;
        try {
            return MotherMentorHeiSchedule.parseBirthDate(MotherMentorDao
                    .getChildEidTestEnrollmentValues(memberObject.getBaseEntityId())
                    .get(DBConstants.KEY.DOB));
        } catch (Exception e) {
            Timber.e(e, "Unable to load the Child EID birth date");
            return null;
        }
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callback) {
        if (useLegacyWorkflow()) {
            super.populateActionList(callback);
            return;
        }
        childCallback = callback;
        appExecutors.diskIO().execute(() -> {
            childActions.clear();
            actionsReady = false;
            try {
                addChildAction(R.string.mothermentor_follow_up_status_section, STATUS_FORM,
                        createChildStatusActionHelper(this::onStatusChanged), false, details);
                addChildAction(R.string.mothermentor_child_eid_comments_optional, COMMENT_FORM,
                        new MotherMentorChildEidFollowUpCommentActionHelper(), true, details);
                syncHeiAction(true);
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            actionsReady = true;
            appExecutors.mainThread().execute(() -> callback.preloadActions(childActions));
        });
    }

    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createChildStatusActionHelper(Runnable changed) {
        return new MotherMentorChildEidFollowUpStatusActionHelper(
                memberObject, serviceDate, changed);
    }

    /** Child-specific HEI action; legacy visits retain their original section helpers. */
    protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper
            createHeiActionHelper(Runnable changed) {
        return new MotherMentorChildEidHeiActionHelper(memberObject, serviceDate, changed);
    }

    protected String getHeiFormName() {
        return "mothermentor_child_eid_hei";
    }

    private void addChildAction(int title, String form,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper, boolean optional,
            Map<String, List<VisitDetail>> restoredDetails)
            throws BaseMotherMentorVisitAction.ValidationException {
        String label = context.getString(title);
        childActions.put(label, getBuilder(label).withOptional(optional)
                .withDetails(restoredDetails).withHelper(helper).withFormName(form).build());
    }

    private Map<String, String> statusValues() {
        BaseMotherMentorVisitAction action = childActions.get(
                context.getString(R.string.mothermentor_follow_up_status_section));
        if (action == null || StringUtils.isBlank(action.getJsonPayload())) return Collections.emptyMap();
        try {
            return MotherMentorChildEidStatusUtils.values(new JSONObject(action.getJsonPayload()));
        } catch (Exception e) {
            Timber.e(e);
            return Collections.emptyMap();
        }
    }

    private void onStatusChanged() {
        if (!actionsReady) return;
        statusSaved = true;
        try {
            syncHeiAction(false);
            // Saved form callbacks run on the main thread. The view copies this map, so
            // replace its rows before the activity redraws the final action status.
            childCallback.preloadActions(childActions);
        } catch (BaseMotherMentorVisitAction.ValidationException e) {
            Timber.e(e);
        }
    }

    private void syncHeiAction(boolean restore)
            throws BaseMotherMentorVisitAction.ValidationException {
        String title = context.getString(R.string.mothermentor_child_eid_hei_section);
        Map<String, String> values = statusValues();
        if (!statusSaved || !MotherMentorChildEidStatusUtils.isFoundAlive(values)
                || !MotherMentorChildEidStatusUtils.isStatusComplete(values, childDob(), serviceDate)) {
            childActions.remove(title);
            return;
        }
        if (childActions.containsKey(title)) return;
        String commentsTitle = context.getString(R.string.mothermentor_child_eid_comments_optional);
        BaseMotherMentorVisitAction comments = childActions.remove(commentsTitle);
        addChildAction(R.string.mothermentor_child_eid_hei_section, getHeiFormName(),
                createHeiActionHelper(() -> {}), false, restore ? details : null);
        childActions.put(commentsTitle, comments);
    }

    @Override
    protected void prepareEvent(Event event) {
        super.prepareEvent(event);
        if (event == null) return;
        event.setEventDate(serviceDate.toDate());
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                serviceDate.toString("dd-MM-yyyy"));
        putObservation(event, DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA,
                useLegacyWorkflow() ? originalSchema : "3");
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
}
