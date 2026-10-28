package org.smartregister.chw.mothermentor.interactor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerArrivalActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerEducationActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpCommentActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerFollowUpStatusActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerHepatitisBTestActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerHivTestActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorPartnerSyphilisTestActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorPartnerFollowUpDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

/** Partner tracing and attendance are distinct visits; saved visits retain their original context. */
public class PartnerFollowUpVisitInteractor extends BaseMotherMentorServiceVisitInteractor {
    private static final String STATUS = "status";
    private static final String ARRIVAL = "arrival";
    private static final String HIV = "hiv";
    private static final String SYPHILIS = "syphilis";
    private static final String HEPATITIS_B = "hepatitis_b";
    private static final String EDUCATION = "education";
    private static final String COMMENTS = DBConstants.KEY.COMMENTS;
    private static final String TRACING = "tracing";
    private static final String ATTENDANCE = "attendance";

    private final LinkedHashMap<String, BaseMotherMentorVisitAction> actions = new LinkedHashMap<>();
    private final Map<String, BaseMotherMentorVisitAction> workflow = new LinkedHashMap<>();
    private final Map<String, String> snapshot = new LinkedHashMap<>();
    private String editedVisitId = "";
    private Date serviceDate = new Date();
    private boolean frozenContext;
    private boolean active;
    private boolean clearingHiddenActions;

    public PartnerFollowUpVisitInteractor(String eventType) {
        super(eventType);
    }

    @Override
    public MemberObject getMemberClient(String memberId, String profileType) {
        MemberObject member = super.getMemberClient(memberId, profileType);
        return member == null ? MotherMentorDao.getSecondaryEnrollmentMember(memberId) : member;
    }

    @Override
    protected void getDetailsOnEdit(
            BaseMotherMentorVisitContract.View view, MemberObject member) {
        memberObject = member;
        details = null;
        editedVisitId = "";
        serviceDate = new Date();
        snapshot.clear();
        frozenContext = false;
        if (!Boolean.TRUE.equals(view.getEditMode())) return;
        Visit visit = loadEditingVisit(member.getBaseEntityId());
        if (visit == null) return;
        editedVisitId = visit.getVisitId();
        details = VisitUtils.getVisitGroups(
                MotherMentorLibrary.getInstance().visitDetailsRepository().getVisits(editedVisitId));
        try {
            String json = StringUtils.defaultIfBlank(visit.getJson(), visit.getPreProcessedJson());
            Map<String, String> saved = MotherMentorVisitCompletion.observationValues(
                    new JSONObject(json).optJSONArray("obs"));
            serviceDate = originalServiceDate(
                    saved.get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE), visit.getDate());
            String phase = saved.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE);
            if ("2".equals(saved.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                    && (TRACING.equals(phase) || ATTENDANCE.equals(phase))) {
                for (String key : new String[] {
                        DBConstants.KEY.PARTNER_LINKAGE_ID,
                        DBConstants.KEY.MOTHER_BASE_ENTITY_ID,
                        DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID,
                        DBConstants.KEY.PARTNER_MOTHER_PREGNANT,
                        DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE}) {
                    snapshot.put(key, StringUtils.defaultString(saved.get(key)));
                }
                frozenContext = true;
                active = true;
            }
        } catch (Exception e) {
            serviceDate = visit.getDate() == null ? serviceDate : visit.getDate();
            Timber.e(e, "Unable to restore partner visit context");
        }
    }

    protected Visit loadEditingVisit(String memberId) {
        return MotherMentorLibrary.getInstance().visitRepository()
                .getLatestVisit(memberId, getCurrentVisitType());
    }

    protected MotherMentorPartnerFollowUpDao.Context loadContext(
            String memberId, String excludedVisitId, Date date) {
        return MotherMentorPartnerFollowUpDao.getContext(memberId, excludedVisitId, date);
    }

    private static Date originalServiceDate(String recordedDate, Date fallback) {
        if (StringUtils.isNotBlank(recordedDate)) {
            for (String pattern : new String[] {"dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy"}) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date parsed = format.parse(recordedDate, position);
                if (parsed != null && position.getIndex() == recordedDate.length()) return parsed;
            }
        }
        return fallback == null ? new Date() : fallback;
    }

    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callback) {
        appExecutors.diskIO().execute(() -> {
            actions.clear();
            workflow.clear();
            if (!frozenContext) {
                MotherMentorPartnerFollowUpDao.Context linkage =
                        loadContext(memberObject.getBaseEntityId(), editedVisitId, serviceDate);
                snapshot.put(DBConstants.KEY.PARTNER_LINKAGE_ID, linkage.getLinkageId());
                snapshot.put(DBConstants.KEY.MOTHER_BASE_ENTITY_ID, linkage.getMotherId());
                snapshot.put(DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID, linkage.getPregnancyEpisodeId());
                snapshot.put(DBConstants.KEY.PARTNER_MOTHER_PREGNANT, linkage.getMotherPregnant());
                snapshot.put(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE,
                        linkage.isAttendancePending() ? ATTENDANCE : TRACING);
                active = linkage.isActive();
            }
            try {
                add(STATUS, R.string.mothermentor_follow_up_status_section,
                        "mothermentor_partner_follow_up_status",
                        new MotherMentorPartnerFollowUpStatusActionHelper(this::clearHiddenActions), false);
                add(ARRIVAL, R.string.mothermentor_partner_attendance_section,
                        "mothermentor_partner_arrival",
                        new MotherMentorPartnerArrivalActionHelper(this::clearHiddenActions), false);
                add(HIV, R.string.mothermentor_partner_hiv_test_section,
                        "mothermentor_partner_hiv_test",
                        new MotherMentorPartnerHivTestActionHelper(this::clearHiddenActions), false);
                add(SYPHILIS, R.string.mothermentor_partner_syphilis_test_section,
                        "mothermentor_partner_syphilis_test",
                        new MotherMentorPartnerSyphilisTestActionHelper(this::clearHiddenActions), false);
                add(HEPATITIS_B, R.string.mothermentor_partner_hepatitis_b_test_section,
                        "mothermentor_partner_hepatitis_b_test",
                        new MotherMentorPartnerHepatitisBTestActionHelper(this::clearHiddenActions), false);
                add(EDUCATION, R.string.mothermentor_partner_education_section,
                        "mothermentor_partner_education",
                        new MotherMentorPartnerEducationActionHelper(), false);
                add(COMMENTS, R.string.mothermentor_comments_section,
                        "mothermentor_partner_follow_up_comment",
                        new MotherMentorPartnerFollowUpCommentActionHelper(), true);
                clearHiddenActions();
            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }
            appExecutors.mainThread().execute(() -> callback.preloadActions(actions));
        });
    }

    private void add(
            String key, int title, String form,
            BaseMotherMentorVisitAction.MotherMentorVisitActionHelper helper, boolean optional)
            throws BaseMotherMentorVisitAction.ValidationException {
        String localizedTitle = context.getString(title);
        BaseMotherMentorVisitAction action = getBuilder(localizedTitle)
                .withOptional(optional).withDetails(details).withHelper(helper)
                .withValidator(new BranchValidator(key)).withFormName(form).build();
        actions.put(localizedTitle, action);
        workflow.put(key, action);
    }

    private boolean attendancePhase() {
        return ATTENDANCE.equals(snapshot.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE));
    }

    private boolean found() {
        return "found".equals(value(STATUS, DBConstants.KEY.FOLLOW_UP_STATUS));
    }

    private boolean attended() {
        return "yes".equals(value(ARRIVAL, DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY));
    }

    private boolean complete(String key) {
        BaseMotherMentorVisitAction action = workflow.get(key);
        return action != null && action.getMotherMentorVisitActionHelper()
                .evaluateStatusOnPayload() == BaseMotherMentorVisitAction.Status.COMPLETED;
    }

    private boolean visible(String key) {
        if (!active) return false;
        switch (key) {
            case STATUS: return !attendancePhase();
            case ARRIVAL: return attendancePhase();
            case HIV: return attendancePhase() && attended() && complete(ARRIVAL);
            case SYPHILIS: return visible(HIV) && complete(HIV);
            case HEPATITIS_B: return visible(SYPHILIS) && complete(SYPHILIS);
            case EDUCATION: return attendancePhase() || found();
            default: return true;
        }
    }

    private String value(String key, String field) {
        BaseMotherMentorVisitAction action = workflow.get(key);
        if (action == null || StringUtils.isBlank(action.getJsonPayload())) return "";
        try {
            return JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), field);
        } catch (Exception e) {
            Timber.e(e);
            return "";
        }
    }

    private void clearHiddenActions() {
        if (clearingHiddenActions) return;
        clearingHiddenActions = true;
        try {
            for (Map.Entry<String, BaseMotherMentorVisitAction> entry : workflow.entrySet()) {
                if (!visible(entry.getKey())) {
                    entry.getValue().setJsonPayload(null);
                    entry.getValue().setSubTitle(null);
                }
            }
        } finally {
            clearingHiddenActions = false;
        }
    }

    @Override
    public void submitVisit(boolean edit, String memberId,
            Map<String, BaseMotherMentorVisitAction> map,
            BaseMotherMentorVisitContract.InteractorCallBack callback) {
        if (!active) {
            callback.onSubmitted(null);
            return;
        }
        LinkedHashMap<String, BaseMotherMentorVisitAction> visible = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : map.entrySet()) {
            if (entry.getValue().isValid()) visible.put(entry.getKey(), entry.getValue());
        }
        super.submitVisit(edit, memberId, visible, callback);
    }

    @Override
    protected String getEncounterType() {
        return getCurrentVisitType();
    }

    @Override
    protected String getTableName() {
        return MotherMentorFollowUpType.FOLLOW_UP_TABLE;
    }

    @Override
    protected void prepareEvent(Event event) {
        if (event == null) return;
        event.setEventDate(serviceDate);
        putObservation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(serviceDate));
        putObservation(event, DBConstants.KEY.FOLLOW_UP_CATEGORY,
                MotherMentorFollowUpType.PARTNER.getValue());
        putObservation(event, DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA, "2");
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            putObservation(event, entry.getKey(), entry.getValue());
        }
        String end = "";
        if (!attendancePhase() && "not_found".equals(value(STATUS, DBConstants.KEY.FOLLOW_UP_STATUS))
                && "deceased".equals(value(STATUS, DBConstants.KEY.PARTNER_NOT_FOUND_REASON))
                && complete(STATUS)) {
            end = "deceased";
        } else if (attendancePhase() && attended() && complete(ARRIVAL) && complete(HIV)
                && complete(SYPHILIS) && complete(HEPATITIS_B) && complete(EDUCATION)) {
            end = "completed";
        }
        putObservation(event, DBConstants.KEY.PARTNER_FOLLOW_UP_END, end);
    }

    private static void putObservation(Event event, String key, String value) {
        if (event.getObs() != null) {
            Iterator<Obs> observations = event.getObs().iterator();
            while (observations.hasNext()) {
                if (key.equals(observations.next().getFieldCode())) observations.remove();
            }
        }
        event.addObs(new Obs("concept", "text", key, "",
                new ArrayList<Object>(Collections.singletonList(StringUtils.defaultString(value))),
                new ArrayList<>(), null, key));
    }

    private class BranchValidator implements BaseMotherMentorVisitAction.Validator {
        private final String actionKey;

        private BranchValidator(String actionKey) {
            this.actionKey = actionKey;
        }

        @Override
        public boolean isValid(String key) {
            return visible(actionKey);
        }

        @Override
        public boolean isEnabled(String key) {
            return visible(actionKey);
        }

        @Override
        public void onChanged(String key) {}
    }
}
