package org.smartregister.chw.mothermentor.interactor;

import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdCounsellingActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdEducationActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdVisitPurposeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.repository.MotherMentorHouseholdClosureRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdVisitUtils;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import timber.log.Timber;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MotherMentorHouseholdVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String FORM_STATUS = "mothermentor_household_follow_up_status";
    private static final String FORM_VISIT_PURPOSE = "mothermentor_household_visit_purpose";
    private static final String FORM_COUNSELLING =
            "mother_mentor_household_members_who_received_counselling";
    private static final String FORM_EDUCATION = "mother_mentor_household_visit_topics_taught";
    private static final String FORM_COMMENT = "mothermentor_household_visit_comment";

    private final LinkedHashMap<String, BaseMotherMentorVisitAction> householdVisitActions =
            new LinkedHashMap<>();
    private final String householdTable;
    private boolean legacyVisit;
    private Date serviceDate = new Date();
    private String closureEditedVisitId;
    private String closureEnrollmentId;
    private String closureCurrentEnrollmentId;
    private String closureEditingEventJson;

    public MotherMentorHouseholdVisitInteractor(String visitType, String householdTable) {
        super(visitType);
        this.householdTable = householdTable;
    }

    @Override
    public MemberObject getMemberClient(String memberID, String profileType) {
        if (TextUtils.isEmpty(memberID)) return null;
        MemberObject householdMember = MotherMentorDao.getHouseholdMember(memberID, householdTable);
        return householdMember != null ? householdMember : super.getMemberClient(memberID, profileType);
    }

    @Override
    protected void getDetailsOnEdit(BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        legacyVisit = false;
        serviceDate = new Date();
        closureEditedVisitId = null;
        closureCurrentEnrollmentId = loadHouseholdEnrollmentId(member.getBaseEntityId());
        closureEnrollmentId = closureCurrentEnrollmentId;
        closureEditingEventJson = null;
        if (!Boolean.TRUE.equals(view.getEditMode())) return;
        Visit visit = loadClosureEditingVisit(member.getBaseEntityId());
        if (visit == null) return;
        details = VisitUtils.getVisitGroups(MotherMentorLibrary.getInstance()
                .visitDetailsRepository().getVisits(visit.getVisitId()));
        closureEditedVisitId = visit.getVisitId();
        legacyVisit = true;
        try {
            closureEditingEventJson = StringUtils.defaultIfBlank(
                    visit.getJson(), visit.getPreProcessedJson());
            JSONObject event = new JSONObject(closureEditingEventJson);
            Map<String, String> saved = MotherMentorHouseholdVisitUtils.observationValues(
                    event.optJSONArray("obs"));
            legacyVisit = !"2".equals(saved.get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA));
            closureEnrollmentId = StringUtils.defaultIfBlank(
                    saved.get(DBConstants.KEY.HOUSEHOLD_ENROLLMENT_ID), closureEnrollmentId);
            Date recorded = MotherMentorHouseholdVisitUtils.getVisitDate(visit, event, saved);
            if (recorded != null) serviceDate = recorded;
        } catch (Exception e) {
            if (visit.getDate() != null) serviceDate = visit.getDate();
            Timber.e(e, "Unable to restore household visit context");
        }
    }

    protected Visit loadEditingVisit(String memberId) {
        return MotherMentorLibrary.getInstance().visitRepository()
                .getLatestVisit(memberId, getCurrentVisitType());
    }

    protected Visit loadClosureEditingVisit(String memberId) {
        return loadEditingVisit(memberId);
    }

    protected MotherMentorHouseholdClosure loadClosureState(String memberId, String excludedVisitId) {
        return MotherMentorHouseholdClosureRepository.getState(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberId, excludedVisitId);
    }

    protected String loadHouseholdEnrollmentId(String memberId) {
        return MotherMentorHouseholdClosureRepository.currentEnrollmentId(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(), memberId);
    }

    protected boolean isClosureVisitCurrentEnrollment(String memberId, String rawEvent) {
        return MotherMentorHouseholdClosureRepository.belongsToCurrentEnrollment(
                MotherMentorLibrary.getInstance().getRepository().getReadableDatabase(),
                memberId, rawEvent);
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callback) {
        appExecutors.diskIO().execute(() -> {
            householdVisitActions.clear();
            try {
                if (!legacyVisit) {
                    addVisitAction(context.getString(R.string.mothermentor_household_follow_up_status),
                            FORM_STATUS, new MotherMentorHouseholdFollowUpStatusActionHelper(
                                    this::clearHiddenServices));
                }
                addVisitAction(context.getString(R.string.mothermentor_household_visit_kusudi_la_ziara),
                        FORM_VISIT_PURPOSE, new MotherMentorHouseholdVisitPurposeActionHelper());
                addVisitAction(context.getString(
                                R.string.mothermentor_household_visit_wanakaya_waliopewa_ushauri),
                        FORM_COUNSELLING, new MotherMentorHouseholdCounsellingActionHelper());
                addVisitAction(context.getString(R.string.mothermentor_household_visit_mada_zilizofundishwa),
                        FORM_EDUCATION, new MotherMentorHouseholdEducationActionHelper());
                addVisitAction(context.getString(R.string.mothermentor_household_visit_maoni),
                        FORM_COMMENT, new MotherMentorHouseholdCommentActionHelper());
                clearHiddenServices();
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            appExecutors.mainThread().execute(() -> callback.preloadActions(householdVisitActions));
        });
    }

    private void addVisitAction(String title, String formName,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper)
            throws BaseMotherMentorVisitAction.ValidationException {
        BaseMotherMentorVisitAction.Builder builder = getBuilder(title)
                .withOptional(FORM_COMMENT.equals(formName)).withDetails(details)
                .withHelper(helper).withFormName(formName);
        if (!FORM_STATUS.equals(formName) && !FORM_COMMENT.equals(formName)) {
            builder.withValidator(new BaseMotherMentorVisitAction.Validator() {
                @Override public boolean isValid(String key) { return servicesVisible(); }
                @Override public boolean isEnabled(String key) { return servicesVisible(); }
                @Override public void onChanged(String key) { }
            });
        }
        householdVisitActions.put(title, builder.build());
    }

    private boolean servicesVisible() {
        if (legacyVisit) return true;
        for (BaseMotherMentorVisitAction action : householdVisitActions.values()) {
            if (!FORM_STATUS.equals(action.getFormName())) continue;
            try {
                JSONObject form = new JSONObject(action.getJsonPayload());
                String type = JsonFormUtils.getValue(form, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE);
                return "new".equals(type) || ("repeat".equals(type)
                        && "found".equals(JsonFormUtils.getValue(
                                form, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS)));
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return false;
    }

    private void clearHiddenServices() {
        if (servicesVisible()) return;
        for (BaseMotherMentorVisitAction action : householdVisitActions.values()) {
            if (FORM_STATUS.equals(action.getFormName()) || FORM_COMMENT.equals(action.getFormName())) {
                continue;
            }
            try {
                JSONObject form = new JSONObject(action.getJsonPayload());
                for (int step = 1; step <= form.optInt("count", 1); step++) {
                    JSONArray fields = form.getJSONObject("step" + step).getJSONArray("fields");
                    for (int i = 0; i < fields.length(); i++) {
                        JSONObject field = fields.getJSONObject(i);
                        if ("hidden".equals(field.optString("type"))) continue;
                        field.put("value", "");
                        if ("check_box".equals(field.optString("type"))) {
                            JSONArray options = field.optJSONArray("options");
                            if (options != null) {
                                for (int j = 0; j < options.length(); j++) {
                                    options.getJSONObject(j).put("value", false);
                                }
                            }
                        }
                    }
                }
                action.setJsonPayload(form.toString());
                action.setSubTitle("");
            } catch (Exception e) {
                Timber.e(e, "Unable to clear hidden household services");
            }
        }
    }

    @Override
    public void submitVisit(boolean editMode, String memberID,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callback) {
        LinkedHashMap<String, BaseMotherMentorVisitAction> visible = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : map.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isValid()) {
                visible.put(entry.getKey(), entry.getValue());
            }
        }
        super.submitVisit(editMode, memberID, visible, callback);
    }

    @Override
    protected String submitVisit(boolean editMode, String memberId,
            Map<String, BaseMotherMentorVisitAction> map, String parentEventType) throws Exception {
        if (loadClosureState(memberId, null).isClosed()
                || !StringUtils.equals(closureCurrentEnrollmentId,
                        loadHouseholdEnrollmentId(memberId))
                || (editMode && !isClosureVisitCurrentEnrollment(
                        memberId, closureEditingEventJson))) return null;
        return super.submitVisit(editMode, memberId, map, parentEventType);
    }

    @Override
    protected void prepareEvent(Event event) {
        if (event == null) return;
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(serviceDate));
        if (!legacyVisit) putObservation(event, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA, "2");
        if (legacyVisit) return;
        putObservation(event, DBConstants.KEY.HOUSEHOLD_ENROLLMENT_ID, closureEnrollmentId);
        Map<String, String> answers = currentClosureAnswers();
        answers.put(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA, "2");
        MotherMentorHouseholdClosure state = loadClosureState(
                memberObject.getBaseEntityId(), closureEditedVisitId);
        if (MotherMentorHouseholdVisitUtils.isComplete(answers)) {
            state = state.advance(answers.get(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE),
                    answers.get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS),
                    answers.get(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON),
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(serviceDate));
        }
        for (Map.Entry<String, String> value :
                MotherMentorHouseholdClosureRepository.observationValues(state).entrySet()) {
            putObservation(event, value.getKey(), value.getValue());
        }
    }

    private Map<String, String> currentClosureAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        for (BaseMotherMentorVisitAction action : householdVisitActions.values()) {
            if (!action.isValid() || StringUtils.isBlank(action.getJsonPayload())) continue;
            try {
                JSONObject form = new JSONObject(action.getJsonPayload());
                for (int step = 1; step <= form.optInt("count", 1); step++) {
                    JSONArray fields = form.getJSONObject("step" + step).getJSONArray("fields");
                    for (int index = 0; index < fields.length(); index++) {
                        String key = fields.getJSONObject(index).optString("key");
                        answers.put(key, JsonFormUtils.getValue(form, key));
                    }
                }
            } catch (Exception e) {
                Timber.e(e, "Unable to read household visit answers");
            }
        }
        return answers;
    }

    private void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (key.equals(observations.next().getFieldCode())) observations.remove();
            }
        }
        ArrayList<Object> answers = new ArrayList<>();
        answers.add(value);
        event.addObs(new Obs("concept", "text", key, "", answers, new ArrayList<>(), null, key));
    }

    @Override
    protected String getEncounterType() {
        return Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES;
    }
}
