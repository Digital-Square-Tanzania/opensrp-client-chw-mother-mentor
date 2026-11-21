package org.smartregister.chw.mothermentor.interactor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorEducationProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorGroupLinkageActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorNextVisitDateActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorReferralProvidedActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitChildActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherClosureRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository.State;
import org.smartregister.chw.mothermentor.util.MotherMentorDeliveryChildren;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import timber.log.Timber;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.text.SimpleDateFormat;

public class MotherMentorServiceVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String FORM_VISIT_TYPE = "mothermentor_visit_type";
    private static final String FORM_CHILD = "mothermentor_visit_child";
    private static final String FORM_GROUP_LINKAGE = "mothermentor_visit_group_linkage";
    private static final String FORM_EDUCATION_PROVIDED = "mothermentor_visit_education_provided";
    private static final String FORM_REFERRAL_PROVIDED = "mothermentor_visit_referal";
    private static final String FORM_NEXT_VISIT_DATE = "mothermentor_next_visit_date";
    private static final String FORM_COMMENT = "mothermentor_visit_comment";
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> motherMentorVisitActions =
            new LinkedHashMap<>();
    private boolean actionsReady;
    private boolean entrySaved;
    private String attendanceContext;
    private Date serviceDate = new Date();
    protected MotherMentorMotherVisitDao.Context enrollmentContext =
            MotherMentorMotherVisitDao.Context.empty();
    private String visitEnrollmentId;
    private String originalPregnancyStatus;
    private BaseMotherMentorVisitAction entryAction;
    private final Map<Integer, BaseMotherMentorVisitAction> childActions = new LinkedHashMap<>();
    private final Map<Integer, MotherMentorVisitChildActionHelper> childHelpers = new LinkedHashMap<>();
    private final Set<Integer> visibleChildren = new LinkedHashSet<>();
    private List<State> savedChildren = Collections.emptyList();
    private Map<String, String> editingValues = Collections.emptyMap();
    private final String historyVisitId;
    private Visit editing;

    public MotherMentorServiceVisitInteractor(String visitType) {
        this(visitType, null);
    }

    public MotherMentorServiceVisitInteractor(String visitType, String historyVisitId) {
        super(visitType);
        this.historyVisitId = historyVisitId;
    }

    @Override
    public MemberObject getMemberClient(String memberId, String profileType) {
        return historyVisitId == null ? super.getMemberClient(memberId, profileType)
                : MotherMentorDao.getPrimaryMemberForHistory(memberId);
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        editing = null;
        editingValues = Collections.emptyMap();
        serviceDate = new Date();
        enrollmentContext = loadMotherContext(member == null ? null : member.getBaseEntityId());
        visitEnrollmentId = enrollmentContext.getEnrollmentId();
        originalPregnancyStatus = enrollmentContext.getEnrollmentPregnancyStatus();
        if (!Boolean.TRUE.equals(view.getEditMode()) || member == null) return;
        Visit visit = loadEditingVisit(member.getBaseEntityId());
        if (visit == null || (historyVisitId != null && !historyVisitId.equals(visit.getVisitId()))) return;
        editing = visit;
        details = VisitUtils.getVisitGroups(
                MotherMentorLibrary.getInstance().visitDetailsRepository().getVisits(
                        visit.getVisitId()));
        try {
            JSONObject event = new JSONObject(visit.getJson());
            Map<String, String> values =
                    org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion
                            .observationValues(event.optJSONArray("obs"));
            editingValues = values;
            visitEnrollmentId = StringUtils.defaultIfBlank(
                    values.get(DBConstants.KEY.MAMA_ENROLLMENT_ID), visitEnrollmentId);
            originalPregnancyStatus = StringUtils.defaultIfBlank(
                    values.get(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS),
                    originalPregnancyStatus);
            org.joda.time.LocalDate recorded = MotherMentorMotherVisitDao.getServiceDate(visit);
            if (recorded != null) serviceDate = recorded.toDate();
            else if (visit.getDate() != null) serviceDate = visit.getDate();
        } catch (Exception e) {
            if (visit.getDate() != null) serviceDate = visit.getDate();
            Timber.e(e, "Unable to restore the mother visit context");
        }
    }

    protected MotherMentorMotherVisitDao.Context loadMotherContext(String memberId) {
        return StringUtils.isBlank(memberId) ? MotherMentorMotherVisitDao.Context.empty()
                                             : MotherMentorMotherVisitDao.getContext(memberId);
    }

    protected Visit loadEditingVisit(String memberId) {
        return MotherMentorLibrary.getInstance().visitRepository().getLatestVisit(
                memberId, getCurrentVisitType());
    }

    protected MotherMentorVisitTypeActionHelper createVisitTypeActionHelper() {
        return new MotherMentorVisitTypeActionHelper(
                memberObject, this::onEntryChanged, enrollmentContext);
    }

    @Override
    protected boolean shouldSubmitOnSave() {
        if (editingFinalizedVisit() || super.shouldSubmitOnSave()) return true;
        if (memberObject == null || serviceDate == null) return false;
        LocalDate day = LocalDate.fromDateFields(serviceDate);
        Map<String, String> values = clinicalValues();
        if (MotherMentorMotherClosureRepository.classify(values, day) == VisitOutcome.CHILD_CONFIRMED_POSITIVE) {
            return true;
        }
        // A confirmed terminal visit must close the card and its Kaya even while offline.
        return MotherMentorMotherClosureRepository.preview(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberObject.getBaseEntityId(), null, values, day).isClosed();
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        final Runnable runnable = () -> {
            motherMentorVisitActions.clear();
            childActions.clear();
            childHelpers.clear();
            visibleChildren.clear();
            actionsReady = false;
            entrySaved = details != null && details.containsKey(DBConstants.KEY.ATTENDANCE_TYPE);
            try {
                MotherMentorVisitTypeActionHelper entryHelper = createVisitTypeActionHelper();
                entryAction = addVisitAction(context.getString(R.string.mothermentor_visit_type_section),
                        FORM_VISIT_TYPE, entryHelper, null);
                savedChildren = loadSavedChildren();
                for (int i = 1; i <= MotherMentorDeliveryChildren.MAX_COUNT; i++) {
                    final int number = i;
                    MotherMentorVisitChildActionHelper helper =
                            new MotherMentorVisitChildActionHelper(entryHelper, this::onChildChanged, number);
                    childHelpers.put(number, helper);
                    childActions.put(number, addVisitAction(
                            MotherMentorDeliveryChildren.label(context, number, ""), FORM_CHILD, helper,
                            new BaseMotherMentorVisitAction.Validator() {
                                @Override public boolean isValid(String key) {
                                    return visibleChildren.contains(number);
                                }

                                @Override public boolean isEnabled(String key) {
                                    return isValid(key);
                                }

                                @Override public void onChanged(String key) { }
                            }));
                }
                refreshChildSections(entrySaved);
                addVisitAction(context.getString(R.string.mothermentor_group_linkage_section),
                        FORM_GROUP_LINKAGE,
                        new MotherMentorGroupLinkageActionHelper(memberObject, this::entryValues),
                        new VisitFlowValidator(FORM_GROUP_LINKAGE));
                addVisitAction(context.getString(R.string.mothermentor_education_provided_section),
                        FORM_EDUCATION_PROVIDED, new MotherMentorEducationProvidedActionHelper(),
                        new VisitFlowValidator(FORM_EDUCATION_PROVIDED));
                addVisitAction(context.getString(R.string.mothermentor_referral_provided_section),
                        FORM_REFERRAL_PROVIDED, new MotherMentorReferralProvidedActionHelper(),
                        new VisitFlowValidator(FORM_REFERRAL_PROVIDED));
                addVisitAction(context.getString(R.string.mothermentor_next_visit_date_section),
                        FORM_NEXT_VISIT_DATE, new MotherMentorNextVisitDateActionHelper(),
                        new VisitFlowValidator(FORM_NEXT_VISIT_DATE));
                addVisitAction(context.getString(R.string.mothermentor_comment_section),
                        FORM_COMMENT, new MotherMentorCommentActionHelper(),
                        new VisitFlowValidator(FORM_COMMENT));
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            attendanceContext = entryValues().get(DBConstants.KEY.ATTENDANCE_TYPE);
            actionsReady = true;
            clearHiddenServices();

            appExecutors.mainThread().execute(
                    () -> callBack.preloadActions(motherMentorVisitActions));
        };

        appExecutors.diskIO().execute(runnable);
    }

    private BaseMotherMentorVisitAction addVisitAction(String title, String formName,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper,
            BaseMotherMentorVisitAction.Validator validator)
            throws BaseMotherMentorVisitAction.ValidationException {
        BaseMotherMentorVisitAction action = getBuilder(title)
                                                     .withOptional(FORM_COMMENT.equals(formName))
                                                     .withDetails(FORM_CHILD.equals(formName) ? null : details)
                                                     .withHelper(helper)
                                                     .withValidator(validator)
                                                     .withFormName(formName)
                                                     .build();
        motherMentorVisitActions.put(title, action);
        return action;
    }

    @Override
    public void submitVisit(boolean editMode, String memberID,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        Map<String, BaseMotherMentorVisitAction> visible = getVisibleVisitActions(map);
        if (MotherMentorDeliveryChildren.hasSections(entryValues())
                || MotherMentorMotherVisitUtils.hasFirstVisitAssessment(entryValues())) {
            for (BaseMotherMentorVisitAction action : visible.values()) {
                if (!action.isOptional()
                        && action.getActionStatus() != BaseMotherMentorVisitAction.Status.COMPLETED) {
                    callBack.onSubmitted(null);
                    return;
                }
            }
        }
        super.submitVisit(editMode, memberID, visible, callBack);
    }

    @Override
    protected String submitVisit(boolean editMode, String memberId,
            Map<String, BaseMotherMentorVisitAction> actions, String parentEventType) throws Exception {
        if (!editMode) return super.submitVisit(false, memberId, actions, parentEventType);
        SQLiteDatabase db = MotherMentorLibrary.getInstance().getRepository().getWritableDatabase();
        String saved;
        db.beginTransaction();
        try {
            Visit latest = loadEditingVisit(memberId);
            if (!matchesEditingVisit(latest)) return null;
            if (MotherMentorMotherClosureRepository.isClosed(db, memberId)
                    && (historyVisitId == null || !MotherMentorMotherClosureRepository
                            .isSameDayClosingVisit(db, latest, LocalDate.now()))) return null;
            saved = super.submitVisit(true, memberId, actions, parentEventType);
            if (saved == null) return null;
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (editingFinalizedVisit()) {
            try {
                NCUtils.startClientProcessing();
            } catch (Exception error) {
                Timber.w(error, "The corrected Mama Kadi visit will be processed on the next sync");
            }
        }
        return saved;
    }

    private boolean matchesEditingVisit(Visit latest) {
        if (editing == null || latest == null || !editing.getVisitId().equals(latest.getVisitId())) return false;
        if (StringUtils.equals(editing.getJson(), latest.getJson())) return true;
        if (!editingFinalizedVisit() || !Boolean.TRUE.equals(latest.getProcessed())
                || !MotherMentorChildEidDao.sourceIdentity(editing)
                        .equals(MotherMentorChildEidDao.sourceIdentity(latest))
                || MotherMentorMotherVisitDao.getServiceDate(editing) == null
                || MotherMentorMotherVisitDao.getServiceDate(latest) == null
                || MotherMentorChildEidDao.compareChronology(editing, latest) != 0) return false;

        // A sync acknowledgement can replace IDs and version metadata while the editor is open.
        // Compare the full observations so a change to any saved answer still invalidates the edit.
        if (MotherMentorChildEidDao.json(latest).optBoolean("voided")) return false;
        try {
            JsonObject opened = JsonFormUtils.gson.fromJson(editing.getJson(), JsonObject.class);
            JsonObject current = JsonFormUtils.gson.fromJson(latest.getJson(), JsonObject.class);
            return opened != null && current != null
                    && opened.get("obs") != null && opened.get("obs").isJsonArray()
                    && opened.get("obs").equals(current.get("obs"));
        } catch (JsonParseException invalid) {
            return false;
        }
    }

    private boolean editingFinalizedVisit() {
        return editing != null && Boolean.TRUE.equals(editing.getProcessed());
    }

    @Override
    protected void processSavedVisit(Visit visit) throws Exception {
        if (!editingFinalizedVisit()) {
            super.processSavedVisit(visit);
            return;
        }
        VisitUtils.persistVisitEvents(Collections.singletonList(visit), visitRepository());
        SQLiteDatabase db = MotherMentorLibrary.getInstance().getRepository().getWritableDatabase();
        JSONObject stored = MotherMentorChildEidDao.storedEvent(db, visit.getBaseEntityId(),
                visit.getVisitType(), visit.getFormSubmissionId());
        Visit updated = visitRepository().getVisitByVisitId(visit.getVisitId());
        if (updated == null || !Boolean.TRUE.equals(updated.getProcessed()) || stored == null
                || stored.optLong("version") != MotherMentorChildEidDao.json(visit).optLong("version")) {
            throw new IllegalStateException("Unable to persist the corrected Mama Kadi visit");
        }
    }

    @Override
    protected void assignVisitSource(Event event, Visit editedVisit) {
        if (editedVisit == null || StringUtils.isBlank(editedVisit.getFormSubmissionId())) {
            super.assignVisitSource(event, editedVisit);
            if (editedVisit == null) captureSampleChild(event);
            return;
        }
        event.setFormSubmissionId(editedVisit.getFormSubmissionId());
        event.setEventId(editedVisit.getEventId());
        try {
            Event previous = JsonFormUtils.gson.fromJson(editedVisit.getJson(), Event.class);
            if (previous == null) return;
            if (previous.getEventDate() != null) event.setEventDate(previous.getEventDate());
            if (StringUtils.isBlank(event.getEventId())) event.setEventId(previous.getEventId());
            event.setVersion(Math.max(event.getVersion(), previous.getVersion() + 1));
            Map<String, String> originalAnswers = MotherMentorVisitCompletion.observationValues(
                    MotherMentorChildEidDao.json(editedVisit).optJSONArray("obs"));
            String originalChild = originalAnswers.get(DBConstants.KEY.MAMA_HEI_SAMPLE_CHILD_ID);
            LocalDate birth = sampleChildBirth(clinicalValues());
            if (StringUtils.isNotBlank(originalChild) && birth != null
                    && birth.equals(sampleChildBirth(originalAnswers))) {
                putObservation(event, DBConstants.KEY.MAMA_HEI_SAMPLE_CHILD_ID, originalChild);
            }
        } catch (JsonParseException invalid) {
            Timber.w(invalid, "Unable to restore the original Mama Kadi visit metadata");
        }
    }

    private void captureSampleChild(Event event) {
        Map<String, String> answers = clinicalValues();
        if (MotherMentorDeliveryChildren.count(answers) > 1) return;
        answers = MotherMentorDeliveryChildren.childVisit(answers, 1);
        if (StringUtils.isAllBlank(answers.get(DBConstants.KEY.MAMA_BIRTH_TEST_RESULT),
                answers.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT))) return;
        LocalDate birth = sampleChildBirth(answers);
        if (birth == null || StringUtils.isBlank(visitEnrollmentId)) return;
        SQLiteDatabase database = MotherMentorLibrary.getInstance().getRepository().getReadableDatabase();
        String childId = MotherMentorChildEidDao.captureMotherSampleChild(
                database, event.getBaseEntityId(), enrollmentContext, birth);
        if (StringUtils.isNotBlank(childId)) putObservation(event, DBConstants.KEY.MAMA_HEI_SAMPLE_CHILD_ID, childId);
    }

    private LocalDate sampleChildBirth(Map<String, String> answers) {
        return MotherMentorHeiSchedule.parseBirthDate(StringUtils.defaultIfBlank(
                answers.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA), enrollmentContext.getChildDob()));
    }

    @Override
    protected Visit saveVisit(boolean editMode, String memberID, String encounterType,
            Map<String, String> forms, String parentEventType) throws Exception {
        Map<String, String> maternalForms = new LinkedHashMap<>(forms);
        for (BaseMotherMentorVisitAction child : childActions.values()) {
            maternalForms.remove(child.getTitle());
        }
        return super.saveVisit(editMode, memberID, encounterType, maternalForms, parentEventType);
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
        if (!entrySaved) return false;
        Map<String, String> answers = clinicalValues();
        LocalDate day = LocalDate.fromDateFields(serviceDate);
        if (MotherMentorMotherClosureRepository.classify(answers, day) != VisitOutcome.CONTINUE) return false;
        if (visibleChildren.isEmpty()) return true;
        return !MotherMentorMotherChildRepository.allChildrenFinished(savedChildren, answers, day);
    }

    protected List<State> loadSavedChildren() {
        if (memberObject == null || StringUtils.isBlank(visitEnrollmentId)) return Collections.emptyList();
        return MotherMentorMotherChildRepository.getStatesBeforeMotherVisit(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberObject.getBaseEntityId(), editing == null ? null : editing.getVisitId());
    }

    private void refreshChildSections(boolean restoring) {
        visibleChildren.clear();
        Map<String, String> values = entryValues();
        LocalDate day = LocalDate.fromDateFields(serviceDate);
        boolean childPath = entrySaved && MotherMentorMotherPregnancyUtils.isChildPath(values, day);
        int count = MotherMentorDeliveryChildren.recordsDelivery(values)
                ? MotherMentorDeliveryChildren.count(values) : Math.max(1, savedChildren.size());
        if (restoring && count == 0 && !MotherMentorDeliveryChildren.hasSections(editingValues)) {
            count = 1;
        }
        for (Map.Entry<Integer, BaseMotherMentorVisitAction> item : childActions.entrySet()) {
            int number = item.getKey();
            BaseMotherMentorVisitAction action = item.getValue();
            State child = number <= savedChildren.size() ? savedChildren.get(number - 1) : new State(null);
            if (!childPath || number > count || child.getTerminalServiceDate() != null) {
                action.setJsonPayload(null);
                action.setDisplayTitle(null);
                continue;
            }
            visibleChildren.add(number);
            try {
                JSONObject entry = new JSONObject(entryAction.getJsonPayload());
                MotherMentorVisitChildActionHelper helper = childHelpers.get(number);
                Map<String, String> answers = restoring
                        ? MotherMentorDeliveryChildren.section(editingValues, number)
                        : Collections.emptyMap();
                String current = action.getJsonPayload();
                boolean matches = StringUtils.isNotBlank(current) && helper.matchesMother(new JSONObject(current), entry);
                if (!restoring && matches && helper.hasSubmittedAnswers()) continue;
                String prepared = helper.prepare(entry, answers, child, childCandidates(values, number, count));
                String sex = answers.getOrDefault(MotherMentorDeliveryChildren.SEX, child.getSex());
                action.setDisplayTitle(MotherMentorDeliveryChildren.label(context, number, sex));
                if (restoring && !answers.isEmpty()) action.setJsonPayload(prepared);
                else {
                    action.setJsonPayload(null);
                    action.setProcessedJsonPayload(prepared);
                }
            } catch (Exception e) {
                action.setJsonPayload(null);
                Timber.e(e, "Unable to prepare the mother visit child section");
            }
        }
    }

    private Map<String, String> childCandidates(Map<String, String> attendance, int number, int count) {
        Map<String, String> options = new LinkedHashMap<>();
        if (count < 2 || memberObject == null || StringUtils.isBlank(visitEnrollmentId)) return options;
        SQLiteDatabase db = MotherMentorLibrary.getInstance().getRepository().getReadableDatabase();
        LocalDate dob = MotherMentorHeiSchedule.parseDate(attendance.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA));
        Set<String> unavailable = new HashSet<>();
        for (int i = 0; i < savedChildren.size(); i++) {
            if (i + 1 != number) unavailable.add(savedChildren.get(i).getExplicitLink());
        }
        childActions.forEach((other, action) -> {
            if (other != number) {
                unavailable.add(actionValues(action).get(MotherMentorDeliveryChildren.LINK));
                unavailable.add(MotherMentorDeliveryChildren.section(editingValues, other)
                        .get(MotherMentorDeliveryChildren.LINK));
            }
        });
        Set<String> linked = new HashSet<>(MotherMentorChildEidDao.linkedChildren(db, memberObject.getBaseEntityId(), dob));
        linked.removeAll(unavailable);
        for (String id : linked) {
            Map<String, String> person = MotherMentorChildEidDao.person(db, id);
            String name = (person.getOrDefault(DBConstants.KEY.FIRST_NAME, "") + " "
                    + person.getOrDefault(DBConstants.KEY.LAST_NAME, "")).trim();
            String identifier = person.getOrDefault(DBConstants.KEY.UNIQUE_ID, "");
            options.put(id, identifier.isEmpty() ? name : name + " (" + identifier + ")");
        }
        return options;
    }

    private Map<String, String> entryValues() {
        return actionValues(entryAction);
    }

    private Map<String, String> clinicalValues() {
        Map<String, String> values = new LinkedHashMap<>(entryValues());
        for (Integer number : visibleChildren) {
            BaseMotherMentorVisitAction action = childActions.get(number);
            if (childHelpers.get(number).evaluateStatusOnPayload()
                    != BaseMotherMentorVisitAction.Status.COMPLETED) continue;
            try {
                values.putAll(MotherMentorDeliveryChildren.actionValues(new JSONObject(action.getJsonPayload())));
            } catch (Exception error) {
                Timber.e(error, "Unable to read the child section");
            }
        }
        return values;
    }

    private Map<String, String> actionValues(BaseMotherMentorVisitAction action) {
        if (action != null && StringUtils.isNotBlank(action.getJsonPayload())) {
            try {
                return MotherMentorMotherVisitUtils.formValues(new JSONObject(action.getJsonPayload()));
            } catch (Exception e) {
                Timber.e(e, "Unable to read the mother visit action");
            }
        }
        return Collections.emptyMap();
    }

    private void onChildChanged() {
        if (!actionsReady) return;
        refreshChildSections(false);
        clearHiddenServices();
    }

    protected void onEntryChanged() {
        if (!actionsReady) return;
        entrySaved = true;
        String currentAttendance = entryValues().get(DBConstants.KEY.ATTENDANCE_TYPE);
        if (!StringUtils.equals(attendanceContext, currentAttendance)) {
            for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
                if (FORM_GROUP_LINKAGE.equals(action.getFormName())) {
                    clearAnswers(action,
                            new String[] {DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                                    DBConstants.KEY.VISIT_ART_START_DATE});
                }
            }
        }
        attendanceContext = currentAttendance;
        refreshChildSections(false);
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (FORM_GROUP_LINKAGE.equals(action.getFormName())) {
                action.setJsonPayload(action.getJsonPayload());
            }
        }
        clearHiddenServices();
    }

    private void clearHiddenServices() {
        if (shouldShowFullVisitActions()) return;
        for (BaseMotherMentorVisitAction action : motherMentorVisitActions.values()) {
            if (!FORM_VISIT_TYPE.equals(action.getFormName())
                    && !FORM_CHILD.equals(action.getFormName())
                    && !FORM_COMMENT.equals(action.getFormName()))
                clearAnswers(action, null);
        }
    }

    private void clearAnswers(BaseMotherMentorVisitAction action, String[] selectedKeys) {
        try {
            JSONObject form = new JSONObject(action.getJsonPayload());
            for (int step = 1; step <= form.optInt("count", 1); step++) {
                JSONArray fields = form.getJSONObject("step" + step).getJSONArray("fields");
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    if ("hidden".equals(field.optString("type"))) continue;
                    if (selectedKeys != null
                            && !java.util.Arrays.asList(selectedKeys)
                                    .contains(field.optString("key")))
                        continue;
                    field.put("value", "");
                    JSONArray options = field.optJSONArray("options");
                    if ("check_box".equals(field.optString("type")) && options != null) {
                        for (int j = 0; j < options.length(); j++) {
                            options.getJSONObject(j).put("value", false);
                        }
                    }
                }
            }
            action.setJsonPayload(form.toString());
            action.setSubTitle("");
        } catch (Exception e) {
            Timber.e(e, "Unable to clear hidden mother services");
        }
    }

    @Override
    protected void prepareEvent(Event event) {
        if (event == null) return;
        event.setEventDate(serviceDate);
        Map<String, String> values = clinicalValues();
        if (MotherMentorDeliveryChildren.hasSections(values)) {
            List<String> visited = new ArrayList<>();
            for (Integer number : visibleChildren) visited.add(number.toString());
            putObservation(event, MotherMentorDeliveryChildren.SECTIONS, "true");
            putObservation(event, MotherMentorDeliveryChildren.COUNT, values.get(MotherMentorDeliveryChildren.COUNT));
            putObservation(event, MotherMentorDeliveryChildren.VISITED, StringUtils.join(visited, ","));
            boolean finished = !visibleChildren.isEmpty() && MotherMentorMotherChildRepository.allChildrenFinished(
                    savedChildren, values, LocalDate.fromDateFields(serviceDate));
            if (finished) putObservation(event, MotherMentorDeliveryChildren.ALL_FINISHED, "true");
        }
        for (String key : values.keySet()) {
            if (key.matches("mama_child_[1-4]_.*")) putObservation(event, key, values.get(key));
        }
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(serviceDate));
        putObservation(event, DBConstants.KEY.MAMA_ENROLLMENT_ID, visitEnrollmentId);
        putObservation(
                event, DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS, originalPregnancyStatus);
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA.equals(
                            observations.next().getFieldCode())) {
                    observations.remove();
                }
            }
        }
    }

    private void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext())
                if (key.equals(observations.next().getFieldCode())) observations.remove();
        }
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<>(Collections.singletonList(StringUtils.defaultString(value))),
                new ArrayList<>(), null, key));
    }

    private class VisitFlowValidator implements BaseMotherMentorVisitAction.Validator {
        private final String formName;

        private VisitFlowValidator(String formName) {
            this.formName = formName;
        }

        @Override
        public boolean isValid(String key) {
            return FORM_COMMENT.equals(formName) || shouldShowFullVisitActions();
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
}
