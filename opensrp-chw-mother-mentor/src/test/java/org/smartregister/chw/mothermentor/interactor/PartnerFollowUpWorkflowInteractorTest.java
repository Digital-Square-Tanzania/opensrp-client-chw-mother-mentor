package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.widget.TextView;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.activity.BaseMotherMentorVisitActivity;
import org.smartregister.chw.mothermentor.adapter.BaseMotherMentorVisitAdapter;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorPartnerFollowUpDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.VisitDetailsRepository;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.util.FormUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class PartnerFollowUpWorkflowInteractorTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousForms;
    private FormUtils forms;
    private VisitDetailsRepository detailsRepository;
    private TestInteractor interactor;
    private LinkedHashMap<String, BaseMotherMentorVisitAction> actions;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(library.getSourceDateFormat()).thenReturn("dd-MM-yyyy");
        when(library.getSaveDateFormat()).thenReturn("yyyy-MM-dd");
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        detailsRepository = mock(VisitDetailsRepository.class);
        when(detailsRepository.getVisits(anyString())).thenReturn(Collections.emptyList());
        when(library.visitDetailsRepository()).thenReturn(detailsRepository);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        previousForms = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        forms = mock(FormUtils.class);
        when(forms.getFormJson(anyString())).thenAnswer(invocation -> {
            String language = RuntimeEnvironment.application.getResources().getConfiguration().locale.getLanguage();
            String folder = "sw".equals(language) ? "json.form-sw" : "json.form";
            try (Scanner scanner = new Scanner(RuntimeEnvironment.application.getAssets()
                    .open(folder + "/" + invocation.getArgument(0) + ".json"), "UTF-8")) {
                return new JSONObject(scanner.useDelimiter("\\A").next());
            }
        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", forms);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousForms);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishTracingRequiresAgreementOrMissingReasonAndKeepsCommentsOptional() throws Exception {
        tracingBranches();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliTracingRequiresAgreementOrMissingReasonAndKeepsCommentsOptional() throws Exception {
        tracingBranches();
    }

    private void tracingBranches() throws Exception {
        start(false);
        assertVisible("mothermentor_partner_follow_up_status", true);
        assertVisible("mothermentor_partner_arrival", false);
        assertVisible("mothermentor_partner_education", false);
        assertTrue(new ArrayList<>(actions.values()).get(actions.size() - 1).isOptional());
        answer("mothermentor_partner_follow_up_status", "follow_up_status", "found");
        assertStatus("mothermentor_partner_follow_up_status", false);
        answer("mothermentor_partner_follow_up_status", "partner_facility_agreement", "agreed");
        assertStatus("mothermentor_partner_follow_up_status", true);
        assertVisible("mothermentor_partner_education", true);
        assertVisible("mothermentor_partner_arrival", false);
        answer("mothermentor_partner_education", "partner_education_provided", "yes",
                "partner_education_topics", new JSONArray().put("hiv"));
        assertStatus("mothermentor_partner_education", true);
        assertEquals("", observation(interactor.event(), DBConstants.KEY.PARTNER_FOLLOW_UP_END));
        answer("mothermentor_partner_follow_up_status", "partner_facility_agreement", "refused");
        assertStatus("mothermentor_partner_follow_up_status", true);
        answer("mothermentor_partner_follow_up_status", "follow_up_status", "not_found",
                "partner_not_found_reason", "other");
        assertStatus("mothermentor_partner_follow_up_status", false);
        assertVisible("mothermentor_partner_education", false);
        assertNull(action("mothermentor_partner_education").getJsonPayload());
        answer("mothermentor_partner_follow_up_status", "partner_not_found_reason_other", "Away for work");
        assertStatus("mothermentor_partner_follow_up_status", true);
        for (String reason : new String[] {"moved", "travelled", "deceased"}) {
            answer("mothermentor_partner_follow_up_status", "partner_not_found_reason", reason);
            assertStatus("mothermentor_partner_follow_up_status", true);
            assertEquals("", value("mothermentor_partner_follow_up_status", "partner_not_found_reason_other"));
        }
        assertEquals("deceased", observation(interactor.event(), DBConstants.KEY.PARTNER_FOLLOW_UP_END));
        assertBlankCommentDoesNotBlockSave();
    }

    @Test
    @Config(qualifiers = "en")
    public void englishAttendanceAsksTestsInOrderAndCompletesEvenWhenAllTestsWereNotDone() throws Exception {
        attendanceBranches();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliAttendanceAsksTestsInOrderAndCompletesEvenWhenAllTestsWereNotDone() throws Exception {
        attendanceBranches();
    }

    private void attendanceBranches() throws Exception {
        start(true);
        assertVisible("mothermentor_partner_follow_up_status", false);
        assertVisible("mothermentor_partner_arrival", true);
        assertVisible("mothermentor_partner_hiv_test", false);
        answer("mothermentor_partner_arrival", "partner_arrived_at_facility", "yes");
        assertStatus("mothermentor_partner_arrival", false);
        assertVisible("mothermentor_partner_hiv_test", false);
        answer("mothermentor_partner_arrival", "partner_facility_arrival_date", "01-02-2025");
        assertVisible("mothermentor_partner_hiv_test", true);
        assertVisible("mothermentor_partner_syphilis_test", false);
        answer("mothermentor_partner_hiv_test", "partner_hiv_tested", "no");
        assertStatus("mothermentor_partner_hiv_test", false);
        answer("mothermentor_partner_hiv_test", "partner_hiv_not_tested_reason", "already_infected");
        assertStatus("mothermentor_partner_hiv_test", true);
        assertVisible("mothermentor_partner_syphilis_test", true);
        answer("mothermentor_partner_syphilis_test", "partner_syphilis_tested", "no",
                "partner_syphilis_not_tested_reason", "already_infected");
        assertStatus("mothermentor_partner_syphilis_test", false);
        assertVisible("mothermentor_partner_hepatitis_b_test", false);
        answer("mothermentor_partner_syphilis_test", "partner_syphilis_not_tested_reason", "other");
        assertStatus("mothermentor_partner_syphilis_test", false);
        answer("mothermentor_partner_syphilis_test", "partner_syphilis_not_tested_reason_other", "Testing postponed");
        assertVisible("mothermentor_partner_hepatitis_b_test", true);
        answer("mothermentor_partner_hepatitis_b_test", "partner_hepatitis_b_tested", "no",
                "partner_hepatitis_b_not_tested_reason", "stock_out");
        assertEquals("", observation(interactor.event(), DBConstants.KEY.PARTNER_FOLLOW_UP_END));
        answer("mothermentor_partner_education", "partner_education_provided", "no");
        assertEquals("completed", observation(interactor.event(), DBConstants.KEY.PARTNER_FOLLOW_UP_END));
        assertBlankCommentDoesNotBlockSave();
        answer("mothermentor_partner_arrival", "partner_arrived_at_facility", "no");
        assertEquals("", value("mothermentor_partner_arrival", "partner_facility_arrival_date"));
        for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            assertVisible("mothermentor_partner_" + disease + "_test", false);
            assertNull(action("mothermentor_partner_" + disease + "_test").getJsonPayload());
        }
        assertEquals("", observation(interactor.event(), DBConstants.KEY.PARTNER_FOLLOW_UP_END));
    }

    @Test
    public void changingTestAnswersClearsOnlyHiddenResultsAndRequiresOtherText() throws Exception {
        start(true);
        answer("mothermentor_partner_arrival", "partner_arrived_at_facility", "yes",
                "partner_facility_arrival_date", "01-02-2025");
        for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            String form = "mothermentor_partner_" + disease + "_test";
            String key = "partner_" + disease;
            answer(form, key + "_tested", "yes");
            assertStatus(form, false);
            answer(form, key + "_result", "positive");
            assertStatus(form, true);
            answer(form, key + "_tested", "no", key + "_not_tested_reason", "other");
            assertEquals("", value(form, key + "_result"));
            assertStatus(form, false);
            answer(form, key + "_not_tested_reason_other", "Declined this time");
            assertStatus(form, true);
            answer(form, key + "_not_tested_reason", "refused");
            assertEquals("", value(form, key + "_not_tested_reason_other"));
        }
    }

    @Test
    public void educationRequiresAllowedTopicsAndClearsThemWhenNotProvided() throws Exception {
        start(false);
        answer("mothermentor_partner_follow_up_status", "follow_up_status", "found",
                "partner_facility_agreement", "agreed");
        answer("mothermentor_partner_education", "partner_education_provided", "yes");
        assertStatus("mothermentor_partner_education", false);
        answer("mothermentor_partner_education", "partner_education_topics", new JSONArray().put("tb"));
        assertStatus("mothermentor_partner_education", false);
        answer("mothermentor_partner_education", "partner_education_topics",
                new JSONArray().put("hiv").put("syphilis").put("hepatitis_b"));
        assertStatus("mothermentor_partner_education", true);
        answer("mothermentor_partner_education", "partner_education_provided", "no");
        assertStatus("mothermentor_partner_education", true);
        JSONObject topic = field(new JSONObject(action("mothermentor_partner_education").getJsonPayload()),
                "partner_education_topics");
        assertEquals(0, topic.getJSONArray("value").length());
    }

    @Test
    public void nativeEventAggregationRetainsAllEducationTopicsAndQualifyingTestResults()
            throws Exception {
        Event event = nativeCompletedEvent();
        Map<String, String> values = MotherMentorVisitCompletion.observationValues(
                new JSONArray(new Gson().toJson(event.getObs())));
        assertTrue(MotherMentorPartnerFollowUpVisitUtils.isCompleteSchema2(values));
        assertEquals("completed", values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END));
        Obs education = null;
        for (Obs obs : event.getObs()) {
            if (DBConstants.KEY.PARTNER_EDUCATION_TOPICS.equals(obs.getFieldCode())) education = obs;
        }
        assertNotNull(education);
        String topics = new Gson().toJson(education.getValues());
        for (String topic : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            assertTrue(topics, topics.contains(topic));
        }
        assertEquals("negative", values.get(DBConstants.KEY.PARTNER_HIV_RESULT));
        assertEquals("negative", values.get(DBConstants.KEY.PARTNER_SYPHILIS_RESULT));
        assertEquals("negative", values.get(DBConstants.KEY.PARTNER_HEPATITIS_B_RESULT));
    }

    private Event nativeCompletedEvent() throws Exception {
        start(true);
        answer("mothermentor_partner_arrival", "partner_arrived_at_facility", "yes",
                "partner_facility_arrival_date", "01-02-2025");
        for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            answer("mothermentor_partner_" + disease + "_test",
                    "partner_" + disease + "_tested", "yes",
                    "partner_" + disease + "_result", "negative");
        }
        answer("mothermentor_partner_education", "partner_education_provided", "yes",
                "partner_education_topics", new JSONArray().put("hiv").put("syphilis").put("hepatitis_b"));
        Map<String, String> forms = new LinkedHashMap<>();
        for (Map.Entry<String, BaseMotherMentorVisitAction> entry : actions.entrySet()) {
            if (entry.getValue().isValid()) forms.put(entry.getKey(), entry.getValue().getJsonPayload());
        }
        Event event = MotherMentorJsonFormUtils.processVisitJsonForm(
                mock(AllSharedPreferences.class), "partner",
                MotherMentorFollowUpType.PARTNER.getEventType(), forms,
                MotherMentorFollowUpType.FOLLOW_UP_TABLE);
        assertNotNull(event);
        interactor.prepareEvent(event);
        return event;
    }

    @Test
    @Config(qualifiers = "en")
    public void englishNativeEventVisitDetailsResumePreservesRawAnswersAndFrozenContext()
            throws Exception {
        resumeNativeEvent();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliNativeEventVisitDetailsResumePreservesRawAnswersAndFrozenContext()
            throws Exception {
        resumeNativeEvent();
    }

    private void resumeNativeEvent() throws Exception {
        Event event = nativeCompletedEvent();
        String originalDate = observation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
        Visit visit = NCUtils.eventToVisit(event, "saved-visit");
        visit.setDate(new Date(visit.getDate().getTime() + 30L * 24 * 60 * 60 * 1000));
        ArrayList<VisitDetail> restored = new ArrayList<>();
        for (java.util.List<VisitDetail> section : visit.getVisitDetails().values()) restored.addAll(section);
        when(detailsRepository.getVisits("saved-visit")).thenReturn(restored);
        interactor = new TestInteractor();
        interactor.editing = visit;
        interactor.forbidContextRead = true;
        calculate(true);
        assertVisible("mothermentor_partner_follow_up_status", false);
        assertEquals("yes", value("mothermentor_partner_arrival", "partner_arrived_at_facility"));
        for (String disease : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            String form = "mothermentor_partner_" + disease + "_test";
            assertVisible(form, true);
            assertStatus(form, true);
            assertEquals("yes", value(form, "partner_" + disease + "_tested"));
            assertEquals("negative", value(form, "partner_" + disease + "_result"));
        }
        assertStatus("mothermentor_partner_education", true);
        JSONArray topics = field(new JSONObject(action("mothermentor_partner_education").getJsonPayload()),
                "partner_education_topics").getJSONArray("value");
        assertEquals(3, topics.length());
        for (String topic : new String[] {"hiv", "syphilis", "hepatitis_b"}) {
            assertTrue(topics.toString().contains('"' + topic + '"'));
        }
        Event resumed = interactor.event();
        assertEquals(originalDate, observation(resumed, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE));
        assertEquals("link", observation(resumed, DBConstants.KEY.PARTNER_LINKAGE_ID));
        assertEquals("mother", observation(resumed, DBConstants.KEY.MOTHER_BASE_ENTITY_ID));
        assertEquals("episode", observation(resumed, DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID));
        assertEquals("yes", observation(resumed, DBConstants.KEY.PARTNER_MOTHER_PREGNANT));
        assertEquals("attendance", observation(resumed, DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE));
        assertEquals("completed", observation(resumed, DBConstants.KEY.PARTNER_FOLLOW_UP_END));
    }

    @Test
    public void schemaTwoDraftRetainsOriginalPhaseLinkagePregnancyAndServiceDate() throws Exception {
        interactor = new TestInteractor();
        interactor.editing = savedVisit(true);
        interactor.forbidContextRead = true;
        calculate(true);
        Event event = interactor.event();
        assertEquals("attendance", observation(event, DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE));
        assertEquals("original-link", observation(event, DBConstants.KEY.PARTNER_LINKAGE_ID));
        assertEquals("original-mother", observation(event, DBConstants.KEY.MOTHER_BASE_ENTITY_ID));
        assertEquals("original-episode", observation(event, DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID));
        assertEquals("yes", observation(event, DBConstants.KEY.PARTNER_MOTHER_PREGNANT));
        assertEquals("01-02-2025", observation(event, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE));
        assertEquals("01-02-2025", new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(event.getEventDate()));
        assertVisible("mothermentor_partner_arrival", true);
        assertVisible("mothermentor_partner_follow_up_status", false);
    }

    @Test
    public void legacyDraftContextLookupExcludesItselfAndUsesOriginalServiceDay() throws Exception {
        for (String recorded : new String[] {"01-02-2025", "2025-02-01", "01/02/2025"}) {
            interactor = new TestInteractor();
            interactor.editing = savedVisit(false);
            JSONObject json = new JSONObject(interactor.editing.getJson());
            json.getJSONArray("obs").getJSONObject(0).put("values", new JSONArray().put(recorded));
            interactor.editing.setJson(json.toString());
            interactor.editing.setDate(new SimpleDateFormat("dd-MM-yyyy", Locale.US).parse("20-03-2026"));
            calculate(true);
            assertEquals("editing-visit", interactor.excludedId);
            assertEquals(recorded, "01-02-2025",
                    new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(interactor.contextDate));
        }
    }

    private Visit savedVisit(boolean schemaTwo) throws Exception {
        Visit visit = new Visit();
        visit.setVisitId("editing-visit");
        visit.setDate(new SimpleDateFormat("dd-MM-yyyy", Locale.US).parse("01-02-2025"));
        JSONArray obs = new JSONArray();
        for (String[] pair : new String[][] {{"mothermentor_visit_date", "01-02-2025"},
                {"partner_follow_up_schema", schemaTwo ? "2" : ""},
                {"partner_follow_up_phase", "attendance"}, {"partner_linkage_id", "original-link"},
                {"mother_base_entity_id", "original-mother"}, {"mother_pregnancy_episode_id", "original-episode"},
                {"partner_mother_pregnant", "yes"}}) {
            obs.put(new JSONObject().put("fieldCode", pair[0]).put("values", new JSONArray().put(pair[1])));
        }
        visit.setJson(new JSONObject().put("obs", obs).toString());
        return visit;
    }

    private void start(boolean pending) throws Exception {
        interactor = new TestInteractor();
        interactor.state = new MotherMentorPartnerFollowUpDao.Context("link", "mother", "episode", "yes", pending, true);
        calculate(false);
    }

    private void calculate(boolean edit) throws Exception {
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) interactor).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        when(view.getEditMode()).thenReturn(edit);
        MemberObject member = new MemberObject();
        member.setBaseEntityId("partner");
        BaseMotherMentorVisitContract.InteractorCallBack callback = mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        interactor.calculateActions(view, member, callback);
        ArgumentCaptor<LinkedHashMap> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captor.capture());
        actions = captor.getValue();
    }

    private BaseMotherMentorVisitAction action(String form) {
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if (form.equals(action.getFormName())) return action;
        }
        throw new AssertionError("Missing action " + form);
    }

    private void answer(String formName, Object... values) throws Exception {
        BaseMotherMentorVisitAction action = action(formName);
        JSONObject form = action.getJsonPayload() == null ? forms.getFormJson(formName)
                : new JSONObject(action.getJsonPayload());
        for (int i = 0; i < values.length; i += 2) {
            JSONObject field = field(form, (String) values[i]);
            field.put("value", values[i + 1]);
            if (values[i + 1] instanceof JSONArray && "check_box".equals(field.optString("type"))) {
                JSONArray options = field.getJSONArray("options");
                for (int j = 0; j < options.length(); j++) {
                    JSONObject option = options.getJSONObject(j);
                    option.put("value", values[i + 1].toString().contains('"' + option.getString("key") + '"'));
                }
            }
        }
        action.setJsonPayload(form.toString());
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private String value(String form, String key) throws Exception {
        return field(new JSONObject(action(form).getJsonPayload()), key).optString("value");
    }

    private void assertVisible(String form, boolean expected) {
        assertEquals(form, expected, action(form).isValid());
        assertEquals(form, expected, action(form).isEnabled());
    }

    private void assertStatus(String form, boolean complete) {
        assertEquals(form, complete ? BaseMotherMentorVisitAction.Status.COMPLETED
                : BaseMotherMentorVisitAction.Status.PENDING, action(form).getActionStatus());
    }

    private String observation(Event event, String key) {
        for (Obs obs : event.getObs()) {
            if (key.equals(obs.getFieldCode())) return obs.getValues().get(0).toString();
        }
        throw new AssertionError("Missing observation " + key);
    }

    private void assertBlankCommentDoesNotBlockSave() {
        BaseMotherMentorVisitAction comment = action("mothermentor_partner_follow_up_comment");
        assertTrue(comment.isOptional());
        comment.setActionStatus(BaseMotherMentorVisitAction.Status.PENDING);
        BaseMotherMentorVisitActivity activity = Robolectric.buildActivity(BaseMotherMentorVisitActivity.class).get();
        ReflectionHelpers.setField(activity, "tvSubmit", new TextView(activity));
        ReflectionHelpers.setField(activity, "actionList", actions);
        ReflectionHelpers.setField(activity, "mAdapter", new BaseMotherMentorVisitAdapter(activity, activity, actions));
        activity.redrawVisitUI();
        assertTrue(((TextView) ReflectionHelpers.getField(activity, "tvSubmit")).hasOnClickListeners());
    }

    private static class TestInteractor extends PartnerFollowUpVisitInteractor {
        MotherMentorPartnerFollowUpDao.Context state = new MotherMentorPartnerFollowUpDao.Context(
                "new-link", "new-mother", "new-episode", "no", false, true);
        Visit editing;
        boolean forbidContextRead;
        String excludedId;
        Date contextDate;

        TestInteractor() { super(MotherMentorFollowUpType.PARTNER.getEventType()); }

        @Override protected Visit loadEditingVisit(String memberId) { return editing; }

        @Override protected MotherMentorPartnerFollowUpDao.Context loadContext(String memberId, String exclude, Date date) {
            if (forbidContextRead) throw new AssertionError("Saved draft context was re-evaluated");
            excludedId = exclude;
            contextDate = date;
            return state;
        }

        Event event() {
            Event event = new Event();
            prepareEvent(event);
            return event;
        }
    }
}
