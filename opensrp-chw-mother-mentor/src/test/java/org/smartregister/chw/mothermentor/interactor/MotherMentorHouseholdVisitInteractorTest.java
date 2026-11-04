package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.widget.TextView;

import org.json.JSONObject;
import org.json.JSONArray;
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
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.repository.VisitDetailsRepository;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.util.FormUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdVisitInteractorTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousFormUtils;
    private VisitDetailsRepository detailsRepository;
    private TestInteractor interactor;
    private LinkedHashMap<String, BaseMotherMentorVisitAction> actions;
    private boolean includeImmutableServiceMetadata;
    private static final String SERVICE_SCHEMA = "fixture_service_schema";
    private static final String STATUS = "mothermentor_household_follow_up_status";
    private static final String PURPOSE = "mothermentor_household_visit_purpose";
    private static final String COUNSELLING = "mother_mentor_household_members_who_received_counselling";
    private static final String EDUCATION = "mother_mentor_household_visit_topics_taught";
    private static final String COMMENT = "mothermentor_household_visit_comment";

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        detailsRepository = mock(VisitDetailsRepository.class);
        when(library.visitDetailsRepository()).thenReturn(detailsRepository);
        when(detailsRepository.getVisits(anyString())).thenReturn(Collections.emptyList());
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        previousFormUtils = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        FormUtils formUtils = mock(FormUtils.class);
        when(formUtils.getFormJson(anyString()))
                .thenAnswer(
                        invocation -> {
                            Application application = RuntimeEnvironment.application;
                            String language =
                                    application.getResources().getConfiguration().locale.getLanguage();
                            String directory = "sw".equals(language) ? "json.form-sw" : "json.form";
                            String formName = invocation.getArgument(0);
                            try (Scanner scanner =
                                    new Scanner(
                                            application
                                                    .getAssets()
                                                    .open(directory + "/" + formName + ".json"),
                                            "UTF-8")) {
                                JSONObject form = new JSONObject(scanner.useDelimiter("\\A").next());
                                if (includeImmutableServiceMetadata && COUNSELLING.equals(formName)) {
                                    form.getJSONObject("step1").getJSONArray("fields").put(
                                            new JSONObject().put("key", SERVICE_SCHEMA)
                                                    .put("type", "hidden").put("value", "2"));
                                }
                                return form;
                            }
                        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", formUtils);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousFormUtils);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishChecklistBranchesAndClearsCorrectedServices() throws Exception {
        checkBranches();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliChecklistBranchesAndClearsCorrectedServices() throws Exception {
        checkBranches();
    }

    private void checkBranches() throws Exception {
        start(false, null);
        assertEquals(2, rowCount());
        assertEquals(STATUS, visibleForms().get(0));
        assertEquals(COMMENT, visibleForms().get(1));
        assertTrue(action(COMMENT).isOptional());
        JSONObject comment = field(action(COMMENT), DBConstants.KEY.COMMENTS);
        assertFalse(comment.has("v_required"));
        assertEquals("100", comment.getJSONObject("v_max_length").getString("value"));
        assertCanSubmit(false);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, "repeat");
        assertEquals(2, rowCount());
        assertCanSubmit(false);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "found");
        assertEquals(5, rowCount());
        assertCanSubmit(false);
        fillServices();
        assertCanSubmit(true);
        answer(COMMENT, DBConstants.KEY.COMMENTS, "Return next month");
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "not_found");
        assertEquals(2, rowCount());
        assertCanSubmit(false);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, "house_closed");
        assertCanSubmit(true);
        assertEquals("", field(action(PURPOSE), DBConstants.KEY.PURPOSE_OF_VISIT).optString("value"));
        assertEquals("", field(action(COUNSELLING), DBConstants.KEY.PARTICIPANTS).optString("value"));
        assertEquals("", field(action(EDUCATION), DBConstants.KEY.TOPICS_TAUGHT).optString("value"));
        for (String form : new String[] {PURPOSE, COUNSELLING, EDUCATION}) {
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, action(form).getActionStatus());
        }
        assertEquals("Return next month", field(action(COMMENT), DBConstants.KEY.COMMENTS).getString("value"));
        BaseMotherMentorVisitContract.InteractorCallBack callback =
                mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        interactor.submitVisit(false, "household", actions, callback);
        assertEquals(2, interactor.submitted.size());
        assertEquals(STATUS, interactor.submitted.values().iterator().next().getFormName());
        verify(callback).onSubmitted("saved");
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "found");
        assertEquals(5, rowCount());
        assertCanSubmit(false);
        fillServices();
        assertCanSubmit(true);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishServiceMetadataSurvivesInitialHidingAndBranchCorrections() throws Exception {
        checkServiceMetadataSurvivesBranchCorrections();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliServiceMetadataSurvivesInitialHidingAndBranchCorrections() throws Exception {
        checkServiceMetadataSurvivesBranchCorrections();
    }

    private void checkServiceMetadataSurvivesBranchCorrections() throws Exception {
        includeImmutableServiceMetadata = true;
        start(false, null);
        assertEquals("2", field(action(COUNSELLING), SERVICE_SCHEMA).getString("value"));
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, "repeat",
                DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "found");
        fillServices();
        assertCanSubmit(true);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "not_found",
                DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, "house_closed");
        assertEquals("2", field(action(COUNSELLING), SERVICE_SCHEMA).getString("value"));
        assertEquals("", field(action(COUNSELLING), DBConstants.KEY.PARTICIPANTS).optString("value"));
        assertEquals("", field(action(PURPOSE), DBConstants.KEY.PURPOSE_OF_VISIT).optString("value"));
        assertEquals("", field(action(EDUCATION), DBConstants.KEY.TOPICS_TAUGHT).optString("value"));
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "found");
        assertEquals("2", field(action(COUNSELLING), SERVICE_SCHEMA).getString("value"));
        assertCanSubmit(false);
    }

    @Test
    @Config(qualifiers = "en")
    public void newVisitHasServicesWithoutFurtherStatusAndClearsOldBranchAnswers() throws Exception {
        checkNewAndNotFoundBranches();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliNewVisitHasServicesWithoutFurtherStatusAndClearsOldBranchAnswers() throws Exception {
        checkNewAndNotFoundBranches();
    }

    private void checkNewAndNotFoundBranches() throws Exception {
        start(false, null);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, "repeat",
                DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "not_found",
                DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, "other");
        assertCanSubmit(false);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER, "Away for work");
        assertCanSubmit(true);
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, "moved");
        assertCanSubmit(true);
        assertEquals("", field(action(STATUS), DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER).optString("value"));
        answer(STATUS, DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, "new");
        assertEquals(5, rowCount());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action(STATUS).getActionStatus());
        assertEquals("", field(action(STATUS), DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS).optString("value"));
        assertEquals("", field(action(STATUS), DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON).optString("value"));
        fillServices();
        assertCanSubmit(true);
        Event event = new Event();
        interactor.prepare(event);
        assertEquals("2", observations(event).get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA));
    }

    @Test
    public void legacyVisitKeepsOriginalChecklistAndServiceDateWithoutInventedStatus() throws Exception {
        start(true, savedVisit(false));
        assertEquals(4, rowCount());
        assertFalse(visibleForms().contains(STATUS));
        Event event = new Event();
        interactor.prepare(event);
        assertEquals("25-10-2026", observations(event).get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE));
        assertFalse(observations(event).containsKey(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA));
    }

    @Test
    public void resumingSchemaTwoDraftRetainsItsStatusAndOriginalServiceDate() throws Exception {
        Visit visit = savedVisit(true);
        VisitDetail type = detail(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, "repeat");
        VisitDetail status = detail(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, "not_found");
        VisitDetail reason = detail(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, "house_closed");
        when(detailsRepository.getVisits("editing-household")).thenReturn(
                java.util.Arrays.asList(type, status, reason));
        start(true, visit);
        assertEquals(2, rowCount());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action(STATUS).getActionStatus());
        assertCanSubmit(true);
        Event event = new Event();
        interactor.prepare(event);
        assertEquals("25-10-2026", observations(event).get(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE));
        assertEquals("2", observations(event).get(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA));
    }

    private VisitDetail detail(String key, String value) {
        VisitDetail detail = new VisitDetail();
        detail.setVisitKey(key);
        detail.setDetails(value);
        return detail;
    }

    private Visit savedVisit(boolean schemaTwo) throws Exception {
        Visit visit = new Visit();
        visit.setVisitId("editing-household");
        visit.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-10-25"));
        JSONArray obs = new JSONArray().put(new JSONObject()
                .put("fieldCode", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE)
                .put("values", new JSONArray().put("25-10-2026")));
        if (schemaTwo) obs.put(new JSONObject().put("fieldCode", DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA)
                .put("values", new JSONArray().put("2")));
        visit.setJson(new JSONObject().put("obs", obs).toString());
        return visit;
    }

    private void start(boolean edit, Visit saved) {
        interactor = new TestInteractor();
        interactor.editing = saved;
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) interactor).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        when(view.getEditMode()).thenReturn(edit);
        MemberObject member = new MemberObject();
        member.setBaseEntityId("household");
        BaseMotherMentorVisitContract.InteractorCallBack callback =
                mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        interactor.calculateActions(view, member, callback);
        ArgumentCaptor<LinkedHashMap> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captor.capture());
        actions = captor.getValue();
    }

    private void fillServices() throws Exception {
        answer(PURPOSE, DBConstants.KEY.PURPOSE_OF_VISIT, "routine_follow_up");
        answer(COUNSELLING, DBConstants.KEY.PARTICIPANTS, new JSONArray().put("mother"));
        answer(EDUCATION, DBConstants.KEY.TOPICS_TAUGHT, new JSONArray().put("nutrition"),
                DBConstants.KEY.HOUSEHOLD_REFERRAL_GIVEN, "no");
    }

    private void answer(String name, Object... answers) throws Exception {
        BaseMotherMentorVisitAction action = action(name);
        JSONObject form = new JSONObject(action.getJsonPayload());
        for (int i = 0; i < answers.length; i += 2) {
            JSONObject field = com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, (String) answers[i]);
            assertNotNull(answers[i].toString(), field);
            field.put("value", answers[i + 1]);
            if ("check_box".equals(field.optString("type"))) {
                JSONArray options = field.getJSONArray("options");
                for (int j = 0; j < options.length(); j++) {
                    JSONObject option = options.getJSONObject(j);
                    option.put("value", answers[i + 1].toString().contains('"' + option.getString("key") + '"'));
                }
            }
        }
        action.setJsonPayload(form.toString());
    }

    private JSONObject field(BaseMotherMentorVisitAction action, String key) throws Exception {
        return com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(new JSONObject(action.getJsonPayload()), key);
    }

    private BaseMotherMentorVisitAction action(String form) {
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if (form.equals(action.getFormName())) return action;
        }
        throw new AssertionError("Missing household action: " + form);
    }

    private ArrayList<String> visibleForms() {
        ArrayList<String> forms = new ArrayList<>();
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if (action.isValid()) forms.add(action.getFormName());
        }
        return forms;
    }

    private int rowCount() {
        return new BaseMotherMentorVisitAdapter(RuntimeEnvironment.application,
                mock(BaseMotherMentorVisitContract.View.class), actions).getItemCount();
    }

    private void assertCanSubmit(boolean expected) {
        BaseMotherMentorVisitActivity activity =
                Robolectric.buildActivity(BaseMotherMentorVisitActivity.class).get();
        TextView submit = new TextView(activity);
        ReflectionHelpers.setField(activity, "tvSubmit", submit);
        ReflectionHelpers.setField(activity, "actionList", actions);
        ReflectionHelpers.setField(activity, "mAdapter",
                new BaseMotherMentorVisitAdapter(activity, activity, actions));
        activity.redrawVisitUI();
        assertEquals(expected, submit.hasOnClickListeners());
    }

    private Map<String, String> observations(Event event) throws Exception {
        return MotherMentorVisitCompletion.observationValues(new JSONObject(
                org.smartregister.chw.mothermentor.util.JsonFormUtils.gson.toJson(event)).optJSONArray("obs"));
    }

    private static class TestInteractor extends MotherMentorHouseholdVisitInteractor {
        @Override protected String loadHouseholdEnrollmentId(String memberId) { return "household-enrollment"; }
        @Override protected org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure
                loadClosureState(String memberId, String excludedId) {
            return org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure.active();
        }

        Visit editing;
        Map<String, BaseMotherMentorVisitAction> submitted;
        TestInteractor() {
            super(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                    Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT);
        }
        @Override protected Visit loadEditingVisit(String id) { return editing; }
        @Override protected String submitVisit(boolean edit, String id,
                Map<String, BaseMotherMentorVisitAction> map, String parentType) {
            submitted = new LinkedHashMap<>(map);
            return "saved";
        }
        void prepare(Event event) { prepareEvent(event); }
    }
}
