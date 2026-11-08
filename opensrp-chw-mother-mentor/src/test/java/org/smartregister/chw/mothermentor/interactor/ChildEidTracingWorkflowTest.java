package org.smartregister.chw.mothermentor.interactor;

import android.app.Application;
import android.database.MatrixCursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorChildEidHeiActionHelper;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.repository.Repository;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class ChildEidTracingWorkflowTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousForms;
    private FormUtils forms;
    private LinkedHashMap<String, BaseMotherMentorVisitAction> visible;
    private TestInteractor interactor;
    private Visit edited;
    private MotherMentorChildEidRepository.State stateFixture;

    @Before
    public void setup() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.getApplication());
        Repository repository = mock(Repository.class);
        SQLiteDatabase database = mock(SQLiteDatabase.class);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(database);
        when(database.rawQuery(anyString(), any(String[].class))).thenAnswer(call -> {
            assertTrue(call.<String>getArgument(0).contains(Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID));
            assertArrayEquals(new String[] {"child"}, call.getArgument(1));
            MatrixCursor row = new MatrixCursor(new String[] {DOB, WEANING_DATE, LAST_TEST});
            row.addRow(new Object[] {LocalDate.now().minusMonths(10).toString(), null, null});
            return row;
        });
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        previousForms = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        forms = mock(FormUtils.class);
        when(forms.getFormJson(anyString())).thenAnswer(call -> {
            String folder = RuntimeEnvironment.getApplication().getResources().getConfiguration()
                    .locale.getLanguage().equals("sw") ? "json.form-sw" : "json.form";
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(folder + "/" + call.getArgument(0) + ".json"), "UTF-8")) {
                return new JSONObject(scanner.useDelimiter("\\A").next());
            }
        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", forms);
    }

    @After
    public void cleanup() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousForms);
    }

    @Test @Config(qualifiers = "en")
    public void englishChecklistRequiresSavedFoundAndAliveAndDiscardsHiddenHeiAnswers() throws Exception {
        checkChecklist();
    }

    @Test @Config(qualifiers = "sw")
    public void swahiliChecklistRequiresSavedFoundAndAliveAndDiscardsHiddenHeiAnswers() throws Exception {
        checkChecklist();
    }

    private void checkChecklist() throws Exception {
        calculate(false);
        assertEquals(2, visible.size());
        assertEquals("mothermentor_child_eid_follow_up_status", first().getFormName());
        assertTrue(action("mothermentor_child_eid_follow_up_comment").isOptional());
        answer("mothermentor_child_eid_follow_up_comment", COMMENTS, "Keep these comments");
        answer("mothermentor_child_eid_follow_up_status", FOLLOW_UP_STATUS, "found");
        assertEquals(2, visible.size());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, first().getActionStatus());
        answer("mothermentor_child_eid_follow_up_status", MAMA_CHILD_STATUS, "alive");
        assertEquals(3, visible.size());
        BaseMotherMentorVisitAction firstHei = action("mothermentor_child_eid_hei");
        assertFalse(firstHei.isOptional());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, firstHei.getActionStatus());
        List<String> order = new ArrayList<>();
        for (BaseMotherMentorVisitAction row : visible.values()) order.add(row.getFormName());
        assertEquals(java.util.Arrays.asList("mothermentor_child_eid_follow_up_status",
                "mothermentor_child_eid_hei", "mothermentor_child_eid_follow_up_comment"), order);
        answer("mothermentor_child_eid_hei", MAMA_BREASTFEEDING_STATUS, "breastfeeding", MTOTO_REFERRAL_AGREEMENT, "agreed");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, firstHei.getActionStatus());
        answer("mothermentor_child_eid_follow_up_status", FOLLOW_UP_STATUS, "not_found",
                MTOTO_NOT_FOUND_REASON, "other", MTOTO_NOT_FOUND_REASON_OTHER, "Away today");
        assertEquals(2, visible.size());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, first().getActionStatus());
        assertEquals("", value(first(), MAMA_CHILD_STATUS));
        assertEquals("Keep these comments", value(action("mothermentor_child_eid_follow_up_comment"), COMMENTS));
        answer("mothermentor_child_eid_follow_up_status", FOLLOW_UP_STATUS, "found", MAMA_CHILD_STATUS, "alive");
        assertEquals(3, visible.size());
        BaseMotherMentorVisitAction nextHei = action("mothermentor_child_eid_hei");
        assertNotSame(firstHei, nextHei);
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, nextHei.getActionStatus());
        assertEquals("", value(first(), MTOTO_NOT_FOUND_REASON_OTHER));
    }

    @Test
    public void childDeathNeverShowsHeiAndUsesTheActualBirthDate() throws Exception {
        calculate(false);
        answer("mothermentor_child_eid_follow_up_status", FOLLOW_UP_STATUS, "found",
                MAMA_CHILD_STATUS, "deceased", MAMA_TAREHE_MTOTO_ALIFARIKI,
                LocalDate.now().minusMonths(10).minusDays(1).toString());
        assertEquals(2, visible.size());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, first().getActionStatus());
        answer("mothermentor_child_eid_follow_up_status", MAMA_TAREHE_MTOTO_ALIFARIKI,
                LocalDate.now().toString());
        assertEquals(2, visible.size());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, first().getActionStatus());
    }

    @Test
    public void existingSchema2DraftUsesTheOriginalStatusAndFinalTestChecklist() throws Exception {
        edited = visit("2", "20-10-2026");
        calculate(true);
        assertEquals(5, visible.size());
        assertNotNull(action("mothermentor_child_eid_follow_up_status_legacy"));
        assertNotNull(action("mothermentor_child_eid_arrival"));
        assertNotNull(action("mothermentor_child_eid_final_test"));
        Event event = new Event(); interactor.enrich(event);
        assertEquals("2", values(event).get(CHILD_EID_FOLLOW_UP_SCHEMA));
        assertEquals("20-10-2026", values(event).get(MOTHERMENTOR_VISIT_DATE));
    }

    @Test
    public void originalSchema1DraftDoesNotGainSchema2FinalTestRequirements() throws Exception {
        edited = visit(null, "20-10-2026");
        calculate(true);
        assertEquals(4, visible.size());
        for (BaseMotherMentorVisitAction action : visible.values()) {
            assertNotEquals("mothermentor_child_eid_final_test", action.getFormName());
        }
        Event event = new Event(); interactor.enrich(event);
        assertNull(values(event).get(CHILD_EID_FOLLOW_UP_SCHEMA));
        assertEquals("20-10-2026", values(event).get(MOTHERMENTOR_VISIT_DATE));
    }

    @Test
    public void editingSchema3KeepsItsOriginalClinicalDayAndRejectsADeathAfterThatDay() throws Exception {
        edited = visit("3", "20-10-2026");
        calculate(true);
        assertEquals(2, visible.size());
        answer("mothermentor_child_eid_follow_up_status", FOLLOW_UP_STATUS, "found",
                MAMA_CHILD_STATUS, "deceased", MAMA_TAREHE_MTOTO_ALIFARIKI, "21-10-2026");
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, first().getActionStatus());
        Event event = new Event(); interactor.enrich(event);
        assertEquals("3", values(event).get(CHILD_EID_FOLLOW_UP_SCHEMA));
        assertEquals("20-10-2026", values(event).get(MOTHERMENTOR_VISIT_DATE));
    }

    @Test
    public void newVisitUsesTheEnrollmentIdentityCapturedBeforeAnswersWereEntered() throws Exception {
        stateFixture = episode("child-episode-one", "mother-episode-one");
        calculate(false);
        stateFixture = episode("child-episode-two", "mother-episode-two");
        Event event = new Event(); interactor.enrich(event);
        long beforeSave = System.currentTimeMillis();
        interactor.assignVisitSource(event, null);
        assertEquals("child-episode-one", values(event).get(MTOTO_ENROLLMENT_ID));
        assertEquals("mother-episode-one", values(event).get(MTOTO_MOTHER_ENROLLMENT_ID));
        assertTrue(event.getEventDate().getTime() >= beforeSave);
        assertEquals(LocalDate.now().toString("dd-MM-yyyy"), values(event).get(MOTHERMENTOR_VISIT_DATE));
        assertEquals(1, interactor.identityReads);
        assertNull(interactor.loadedBeforeVisit);
    }

    @Test
    public void editingRetainsItsOriginalEpisodeTagsInsteadOfCurrentEnrollmentTags() throws Exception {
        edited = visit("3", "20-10-2026");
        String originalTime = "2026-10-20T09:15:00.000+03:00";
        JSONObject source = new JSONObject(edited.getJson()).put(EVENT_DATE_TIMESTAMP, originalTime);
        source.getJSONArray("obs")
                .put(observation(MTOTO_ENROLLMENT_ID, "old-child-episode"))
                .put(observation(MTOTO_MOTHER_ENROLLMENT_ID, "old-mother-episode"));
        edited.setJson(source.toString());
        stateFixture = episode("current-child-episode", "current-mother-episode");
        calculate(true);
        Event event = new Event(); interactor.enrich(event);
        interactor.assignVisitSource(event, edited);
        assertEquals("old-child-episode", values(event).get(MTOTO_ENROLLMENT_ID));
        assertEquals("old-mother-episode", values(event).get(MTOTO_MOTHER_ENROLLMENT_ID));
        assertEquals(DateTime.parse(originalTime).toDate(), event.getEventDate());
        assertEquals("20-10-2026", values(event).get(MOTHERMENTOR_VISIT_DATE));
        assertEquals("editing", interactor.loadedBeforeVisit);
        assertEquals(1, interactor.identityReads);
    }

    @Test
    public void legacyVisitDoesNotAcquireTheCurrentEpisodeIdentityOnEdit() throws Exception {
        edited = visit("2", "20-10-2026");
        stateFixture = episode("current-child-episode", "current-mother-episode");
        calculate(true);
        Event event = new Event(); interactor.enrich(event);
        assertFalse(values(event).containsKey(MTOTO_ENROLLMENT_ID));
        assertFalse(values(event).containsKey(MTOTO_MOTHER_ENROLLMENT_ID));
        assertEquals(0, interactor.identityReads);
    }

    private MotherMentorChildEidRepository.State episode(String child, String mother) {
        MotherMentorChildEidRepository.State state = mock(MotherMentorChildEidRepository.State.class);
        when(state.getEnrollmentId()).thenReturn(child);
        when(state.getMotherEnrollmentId()).thenReturn(mother);
        return state;
    }

    private JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    private void calculate(boolean editing) {
        interactor = new TestInteractor();
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) interactor).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.getApplication());
        when(view.getEditMode()).thenReturn(editing);
        MemberObject member = new MemberObject(); member.setBaseEntityId("child");
        member.setDob(LocalDate.now().minusMonths(10).toString());
        BaseMotherMentorVisitContract.InteractorCallBack callback = mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        doAnswer(call -> { visible = new LinkedHashMap<>(call.getArgument(0)); return null; })
                .when(callback).preloadActions(any(LinkedHashMap.class));
        interactor.calculateActions(view, member, callback);
    }

    private BaseMotherMentorVisitAction first() { return visible.values().iterator().next(); }
    private BaseMotherMentorVisitAction action(String form) {
        for (BaseMotherMentorVisitAction action : visible.values()) if (form.equals(action.getFormName())) return action;
        throw new AssertionError("Missing action " + form);
    }
    private void answer(String name, String... pairs) throws Exception {
        BaseMotherMentorVisitAction action = action(name);
        JSONObject form = new JSONObject(action.getJsonPayload());
        for (int i = 0; i < pairs.length; i += 2) {
            com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, pairs[i]).put("value", pairs[i + 1]);
        }
        action.setJsonPayload(form.toString());
    }
    private String value(BaseMotherMentorVisitAction action, String key) throws Exception {
        return org.smartregister.chw.mothermentor.util.JsonFormUtils.getValue(new JSONObject(action.getJsonPayload()), key);
    }
    private Visit visit(String schema, String day) throws Exception {
        JSONArray obs = new JSONArray().put(new JSONObject().put("fieldCode", MOTHERMENTOR_VISIT_DATE)
                .put("values", new JSONArray().put(day)));
        if (schema != null) obs.put(new JSONObject().put("fieldCode", CHILD_EID_FOLLOW_UP_SCHEMA)
                .put("values", new JSONArray().put(schema)));
        Visit visit = new Visit(); visit.setVisitId("editing");
        visit.setJson(new JSONObject().put("obs", obs).toString()); return visit;
    }
    private Map<String, String> values(Event event) throws Exception {
        return MotherMentorVisitCompletion.observationValues(new JSONObject(
                new com.google.gson.Gson().toJson(event)).optJSONArray("obs"));
    }
    private class TestInteractor extends ChildEidFollowUpVisitInteractor {
        private int identityReads;
        private String loadedBeforeVisit;
        TestInteractor() { super("Mother Mentor Child EID Follow Up"); }
        @Override protected MotherMentorChildEidRepository.State loadEnrollmentState(
                String memberId, String editingVisitId) {
            identityReads++;
            loadedBeforeVisit = editingVisitId;
            return stateFixture;
        }
        @Override protected BaseMotherMentorVisitAction.MotherMentorVisitActionHelper createHeiActionHelper(Runnable changed) {
            return new MotherMentorChildEidHeiActionHelper(null, changed) {
                @Override protected String loadReferralState() { return "none"; }
                @Override protected MotherMentorMotherChildRepository.State loadClinicalState() {
                    return new MotherMentorMotherChildRepository.State(LocalDate.now().minusMonths(10));
                }
            };
        }
        @Override protected Visit loadEditingVisit(String id) { return edited; }
        @Override protected Map<String, List<VisitDetail>> loadVisitDetails(Visit visit) { return Collections.emptyMap(); }
        void enrich(Event event) { prepareEvent(event); }
    }
}
