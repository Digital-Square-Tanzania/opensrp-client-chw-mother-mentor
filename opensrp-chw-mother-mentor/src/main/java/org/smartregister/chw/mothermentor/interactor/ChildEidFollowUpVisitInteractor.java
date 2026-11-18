package org.smartregister.chw.mothermentor.interactor;

import com.google.gson.JsonParseException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

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
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidResultEvent;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidStatusUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    private MotherMentorChildEidRepository.State enrollmentState;
    private Map<String, String> originalEpisode = Collections.emptyMap();
    private Event linkedHei;

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
        enrollmentState = null;
        originalEpisode = new LinkedHashMap<>();
        if (member == null) return;
        Visit visit = Boolean.TRUE.equals(view.getEditMode())
                ? loadEditingVisit(member.getBaseEntityId()) : null;
        if (visit != null) {
            details = loadVisitDetails(visit);
            LocalDate recorded = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (recorded != null) serviceDate = recorded;
            try {
                Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                        new JSONObject(visit.getJson()).optJSONArray("obs"));
                originalSchema = values.get(DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA);
                legacyVisit = !MotherMentorChildEidStatusUtils.isSchema3(values);
                for (String key : new String[] {DBConstants.KEY.MTOTO_ENROLLMENT_ID,
                        DBConstants.KEY.MTOTO_MOTHER_ENROLLMENT_ID}) {
                    if (StringUtils.isNotBlank(values.get(key))) originalEpisode.put(key, values.get(key));
                }
                statusSaved = !legacyVisit && MotherMentorChildEidStatusUtils.isStatusComplete(
                        values, childDob(), serviceDate);
            } catch (Exception e) {
                legacyVisit = true;
                Timber.e(e, "Unable to restore the Child EID visit schema");
            }
        }
        if (!useLegacyWorkflow()) {
            try {
                enrollmentState = loadEnrollmentState(member.getBaseEntityId(),
                        visit == null ? null : visit.getVisitId());
            } catch (Exception e) {
                Timber.e(e, "Unable to capture the Child EID enrollment episode");
            }
        }
    }

    protected MotherMentorChildEidRepository.State loadEnrollmentState(String memberId,
            String editingVisitId) {
        SQLiteDatabase db =
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase();
        return StringUtils.isBlank(editingVisitId)
                ? MotherMentorChildEidRepository.getState(db, memberId, null, serviceDate)
                : MotherMentorChildEidRepository.getStateBeforeVisit(db, memberId, editingVisitId);
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
    protected String submitVisit(boolean editMode, String memberId,
            Map<String, BaseMotherMentorVisitAction> actions, String parentEventType) throws Exception {
        if (useLegacyWorkflow()) return super.submitVisit(editMode, memberId, actions, parentEventType);
        Map<String, String> parentSections = new LinkedHashMap<>();
        linkedHei = null;
        String payloadType = null;
        String payloadDetails = null;
        for (Map.Entry<String, BaseMotherMentorVisitAction> section : actions.entrySet()) {
            BaseMotherMentorVisitAction action = section.getValue();
            String json = action.getJsonPayload();
            if (StringUtils.isBlank(json)) continue;
            if (getHeiFormName().equals(action.getFormName())) {
                linkedHei = MotherMentorJsonFormUtils.processVisitJsonForm(
                        MotherMentorLibrary.getInstance().context().allSharedPreferences(), memberId,
                        MotherMentorChildEidResultEvent.EVENT_TYPE,
                        Collections.singletonMap(section.getKey(), json), getTableName());
                if (linkedHei == null) return null;
            } else {
                parentSections.put(section.getKey(), json);
            }
            payloadType = action.getPayloadType().name();
            payloadDetails = action.getPayloadDetails();
        }

        SQLiteDatabase db = MotherMentorLibrary.getInstance().getRepository().getWritableDatabase();
        Visit visit;
        db.beginTransaction();
        try {
            visit = saveVisit(editMode, memberId, getEncounterType(), parentSections, parentEventType);
            if (visit == null) return null;
            saveVisitDetails(visit, payloadType, payloadDetails);
            if (linkedHei != null) {
                MotherMentorChildEidResultEvent.persist(db, linkedHei);
                MotherMentorChildEidResultEvent.refreshInlineDetails(db, visit);
            }
            VisitUtils.persistVisitEvents(Collections.singletonList(visit), visitRepository());
            Visit saved = visitRepository().getVisitByVisitId(visit.getVisitId());
            if (saved == null || !Boolean.TRUE.equals(saved.getProcessed())) {
                throw new IllegalStateException("The Child EID visit was not finalized");
            }
            db.setTransactionSuccessful();
        } finally {
            linkedHei = null;
            db.endTransaction();
        }
        try {
            NCUtils.startClientProcessing();
        } catch (Exception e) {
            Timber.w(e, "The locally saved Child EID visit will be processed on the next sync");
        }
        return visit.getJson();
    }

    @Override
    protected boolean shouldSubmitOnSave() {
        return !useLegacyWorkflow() || super.shouldSubmitOnSave();
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
        for (Map.Entry<String, String> episode : originalEpisode.entrySet()) {
            putObservation(event, episode.getKey(), episode.getValue());
        }
        if (!useLegacyWorkflow()) MotherMentorChildEidRepository.tagEvent(event, enrollmentState);
    }

    @Override
    protected void assignVisitSource(Event event, Visit editedVisit) {
        super.assignVisitSource(event, editedVisit);
        if (useLegacyWorkflow()) return;
        String originalSample = "";
        if (editedVisit == null) {
            event.setEventDate(new Date());
        } else {
            try {
                Event previous = JsonFormUtils.gson.fromJson(StringUtils.defaultIfBlank(
                        editedVisit.getJson(), editedVisit.getPreProcessedJson()), Event.class);
                if (previous != null) {
                    if (previous.getEventDate() != null) event.setEventDate(previous.getEventDate());
                    if (StringUtils.isNotBlank(editedVisit.getFormSubmissionId())) {
                        event.setFormSubmissionId(editedVisit.getFormSubmissionId());
                        event.setEventId(previous.getEventId());
                        event.setVersion(Math.max(event.getVersion(), previous.getVersion() + 1));
                        Map<String, String> original =
                                MotherMentorChildEidResultEvent.visitAnswers(
                                        MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                                        editedVisit);
                        if (StringUtils.isNotBlank(original.get(DBConstants.KEY.MAMA_HEI_TEST_DATE))) {
                            originalSample = StringUtils.defaultIfBlank(
                                    original.get(DBConstants.KEY.CHILD_EID_SAMPLE_SUBMISSION_ID),
                                    editedVisit.getFormSubmissionId());
                        }
                    }
                }
            } catch (JsonParseException invalid) {
                Timber.w(invalid, "Unable to restore the original Child EID event metadata");
            }
        }
        if (linkedHei != null) {
            MotherMentorChildEidResultEvent.linkInline(event, linkedHei,
                    enrollmentState == null ? null : enrollmentState.getPendingSample(), originalSample);
        }
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
