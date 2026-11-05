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
import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;

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
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.repository.Repository;
import org.smartregister.chw.mothermentor.activity.BaseMotherMentorVisitActivity;
import org.smartregister.chw.mothermentor.adapter.BaseMotherMentorVisitAdapter;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.repository.VisitDetailsRepository;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorServiceVisitInteractorTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousFormUtils;
    private SQLiteDatabase data;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, "
                     + "processed INTEGER, visit_date TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status "
                     + "TEXT, art_start_date TEXT)");
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), org.mockito.ArgumentMatchers.any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        previousFormUtils = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        FormUtils formUtils = mock(FormUtils.class);
        when(formUtils.getFormJson(anyString())).thenAnswer(invocation -> {
            Application application = RuntimeEnvironment.application;
            String language = application.getResources().getConfiguration().locale.getLanguage();
            String directory = "sw".equals(language) ? "json.form-sw" : "json.form";
            String formName = invocation.getArgument(0);
            try (Scanner scanner = new Scanner(
                         application.getAssets().open(directory + "/" + formName + ".json"),
                         "UTF-8")) {
                return new JSONObject(scanner.useDelimiter("\\A").next());
            }
        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", formUtils);
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousFormUtils);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishActionsPreserveVisitFlow() throws Exception {
        assertLocalizedVisitFlow("Visit Type", "Group Linkage", "Education Provided",
                "Referral Provided", "Next Visit Date", "Comment");
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliActionsPreserveVisitFlow() throws Exception {
        assertLocalizedVisitFlow("Aina ya hudhurio", "Uunganishaji wa vikundi", "Elimu iliyotolewa",
                "Rufaa iliyotolewa", "Tarehe ya tembeleo lijalo", "Maoni");
    }

    @Test
    @Config(qualifiers = "en")
    public void realNewClientPrefillStillStartsWithExactlyTwoRows() throws Exception {
        checkRealClient(false);
    }

    @Test
    @Config(qualifiers = "sw")
    public void realReturningClientPrefillIsReadOnlyAndStillNeedsStatus() throws Exception {
        checkRealClient(true);
    }

    private void checkRealClient(boolean returning) throws Exception {
        if (returning) {
            data.execSQL("INSERT INTO visits VALUES ('previous','mother',?,1,'1')",
                    new Object[] {Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES});
            data.execSQL("INSERT INTO visit_details VALUES ('previous',?,'yes')",
                    new Object[] {DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP});
        }
        {
            TestInteractor interactor = new TestInteractor();
            LinkedHashMap<String, BaseMotherMentorVisitAction> actions = start(interactor, false);
            BaseMotherMentorVisitAction entry = byForm(actions, "mothermentor_visit_type");
            JSONObject attendance = field(entry, DBConstants.KEY.ATTENDANCE_TYPE);
            assertEquals(returning ? "repeat" : "new", attendance.optString("value"));
            assertEquals(returning, attendance.optBoolean("read_only"));
            assertEquals(2, visible(actions));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, entry.getActionStatus());
            assertTrue(byForm(actions, "mothermentor_visit_comment").isOptional());
            if (returning) {
                answer(entry, DBConstants.KEY.FOLLOW_UP_STATUS, "found");
                assertEquals(2, visible(actions));
                answer(entry, DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
            } else {
                answer(entry, DBConstants.KEY.FOLLOW_UP_STATUS, "");
            }
            assertEquals(6, visible(actions));
            BaseMotherMentorVisitAction group = byForm(actions, "mothermentor_visit_group_linkage");
            answer(group, DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP, "yes",
                    DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP, "yes",
                    DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "no");
            answer(entry, DBConstants.KEY.ATTENDANCE_TYPE, "repeat",
                    DBConstants.KEY.FOLLOW_UP_STATUS, "not_found",
                    DBConstants.KEY.MAMA_NOT_FOUND_REASON, "other",
                    DBConstants.KEY.MAMA_SABABU_NYINGINE_TEXT, "Return tomorrow");
            assertEquals(2, visible(actions));
            assertEquals("",
                    field(group, DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP).optString("value"));
            assertEquals(
                    "", field(group, DBConstants.KEY.NEW_PMTCT_ART_INITIATION).optString("value"));
            assertEquals(
                    "repeat", field(group, DBConstants.KEY.ATTENDANCE_TYPE).optString("value"));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, entry.getActionStatus());
        }
    }

    @Test
    public void resumedVisitUsesSavedEntryAndRetainsOriginalServiceDayAndEnrollment() throws Exception {
        TestInteractor interactor = new TestInteractor();
        JSONArray observations = new JSONArray()
                .put(observation(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "15-10-2026"))
                .put(observation(DBConstants.KEY.MAMA_ENROLLMENT_ID, "enrollment-one"));
        List<VisitDetail> saved = new ArrayList<>();
        addDetail(saved, DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        addDetail(saved, DBConstants.KEY.FOLLOW_UP_STATUS, "found");
        addDetail(saved, DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
        Visit visit = new Visit();
        visit.setVisitId("saved-visit");
        visit.setDate(new Date());
        visit.setJson(new JSONObject()
                .put("eventDate", "2026-11-04T08:00:00.000+03:00")
                .put("obs", observations)
                .toString());
        interactor.editing = visit;
        VisitDetailsRepository repository = mock(VisitDetailsRepository.class);
        when(repository.getVisits("saved-visit")).thenReturn(saved);
        when(MotherMentorLibrary.getInstance().visitDetailsRepository()).thenReturn(repository);

        LinkedHashMap<String, BaseMotherMentorVisitAction> actions = start(interactor, true);
        assertEquals(6, visible(actions));
        BaseMotherMentorVisitAction entry = byForm(actions, "mothermentor_visit_type");
        assertEquals("found", field(entry, DBConstants.KEY.FOLLOW_UP_STATUS).optString("value"));
        Event event = new Event();
        interactor.prepareEvent(event);
        assertEquals("15-10-2026",
                new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                        .format(event.getEventDate()));
        assertTrue(event.getObs().stream().anyMatch(obs ->
                DBConstants.KEY.MAMA_ENROLLMENT_ID.equals(obs.getFieldCode())
                        && obs.getValues().contains("enrollment-one")));
    }

    private LinkedHashMap<String, BaseMotherMentorVisitAction> start(
            TestInteractor interactor, boolean edit) {
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        when(view.getEditMode()).thenReturn(edit);
        BaseMotherMentorVisitContract.InteractorCallBack callback =
                mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        MemberObject mother = new MemberObject();
        mother.setBaseEntityId("mother");
        interactor.calculateActions(view, mother, callback);
        ArgumentCaptor<LinkedHashMap> captured = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captured.capture());
        return captured.getValue();
    }

    private BaseMotherMentorVisitAction byForm(
            Map<String, BaseMotherMentorVisitAction> actions, String name) {
        return actions.values()
                .stream()
                .filter(a -> name.equals(a.getFormName()))
                .findFirst()
                .get();
    }

    private JSONObject field(BaseMotherMentorVisitAction action, String key) throws Exception {
        return com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(
                new JSONObject(action.getJsonPayload()), key);
    }

    private void answer(BaseMotherMentorVisitAction action, String... values) throws Exception {
        JSONObject form = new JSONObject(action.getJsonPayload());
        for (int i = 0; i < values.length; i += 2)
            com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, values[i])
                    .put("value", values[i + 1]);
        action.setJsonPayload(form.toString());
    }

    private int visible(Map<String, BaseMotherMentorVisitAction> actions) {
        return (int) actions.values().stream().filter(BaseMotherMentorVisitAction::isValid).count();
    }

    private JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    private void addDetail(List<VisitDetail> details, String key, String value) {
        VisitDetail detail = new VisitDetail();
        detail.setVisitId("saved-visit");
        detail.setVisitKey(key);
        detail.setDetails(value);
        details.add(detail);
    }

    private static final class TestInteractor extends MotherMentorServiceVisitInteractor {
        Visit editing;
        TestInteractor() {
            super(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        }
        @Override
        protected MotherMentorMotherVisitDao.Context loadMotherContext(String memberId) {
            return new MotherMentorMotherVisitDao.Context(
                    "enrollment-one", "", "pregnant", "", "", "household", 1);
        }
        @Override
        protected Visit loadEditingVisit(String memberId) {
            return editing;
        }
        @Override
        protected MotherMentorVisitTypeActionHelper createVisitTypeActionHelper() {
            return new MotherMentorVisitTypeActionHelper(
                    memberObject, this::onEntryChanged, enrollmentContext) {
                @Override
                protected Map<String, String> loadEnrollmentValues() {
                    return Collections.singletonMap(
                            DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT, "true");
                }
            };
        }
    }

    private void assertLocalizedVisitFlow(String... titles) throws Exception {
        MotherMentorServiceVisitInteractor interactor = new TestInteractor();
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) interactor).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        when(view.getEditMode()).thenReturn(false);
        BaseMotherMentorVisitContract.InteractorCallBack callback =
                mock(BaseMotherMentorVisitContract.InteractorCallBack.class);

        interactor.calculateActions(view, new MemberObject(), callback);

        ArgumentCaptor<LinkedHashMap> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captor.capture());
        LinkedHashMap<String, BaseMotherMentorVisitAction> actions = captor.getValue();
        assertEquals(Arrays.asList(titles), new ArrayList<>(actions.keySet()));
        List<BaseMotherMentorVisitAction> orderedActions = new ArrayList<>(actions.values());
        for (int i = 0; i < titles.length; i++) {
            assertEquals(titles[i], orderedActions.get(i).getTitle());
        }
        BaseMotherMentorVisitAction visitType = orderedActions.get(0);
        assertEquals("mothermentor_visit_type", visitType.getFormName());
        assertVisibility(orderedActions, false, true);

        setVisitType(visitType, "new", "");
        assertVisibility(orderedActions, true, true);
        assertBlankCommentDoesNotBlockSubmission(actions);

        setVisitType(visitType, "repeat", "not_found");
        assertVisibility(orderedActions, false, true);

        setVisitType(visitType, "repeat", "found");
        assertVisibility(orderedActions, true, true);

        setVisitType(visitType, "", "");
        assertVisibility(orderedActions, false, true);
    }

    private void setVisitType(BaseMotherMentorVisitAction action, String attendance, String status)
            throws Exception {
        JSONObject form = new JSONObject(action.getJsonPayload());
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (DBConstants.KEY.ATTENDANCE_TYPE.equals(field.optString("key"))) {
                field.put("value", attendance);
            } else if (DBConstants.KEY.FOLLOW_UP_STATUS.equals(field.optString("key"))) {
                field.put("value", status);
            } else if (DBConstants.KEY.VISIT_PREGNANCY_STATUS.equals(field.optString("key"))) {
                field.put("value", "found".equals(status) ? "pregnant" : "");
            }
        }
        action.setJsonPayload(form.toString());
    }

    private void assertBlankCommentDoesNotBlockSubmission(
            LinkedHashMap<String, BaseMotherMentorVisitAction> actions) throws Exception {
        BaseMotherMentorVisitAction comment = null;
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if ("mothermentor_visit_comment".equals(action.getFormName())) {
                comment = action;
                assertTrue(action.isOptional());
                JSONObject field = com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(
                        new JSONObject(action.getJsonPayload()), DBConstants.KEY.COMMENTS);
                assertFalse(field.optJSONObject("v_required") != null
                        && field.getJSONObject("v_required").optBoolean("value"));
                assertEquals("100", field.getJSONObject("v_max_length").getString("value"));
                action.setActionStatus(BaseMotherMentorVisitAction.Status.PENDING);
            } else {
                assertFalse(action.isOptional());
                action.setActionStatus(BaseMotherMentorVisitAction.Status.COMPLETED);
            }
        }
        assertNotNull(comment);
        BaseMotherMentorVisitActivity activity =
                Robolectric.buildActivity(BaseMotherMentorVisitActivity.class).get();
        TextView submit = new TextView(activity);
        ReflectionHelpers.setField(activity, "tvSubmit", submit);
        ReflectionHelpers.setField(activity, "actionList", actions);
        ReflectionHelpers.setField(activity, "mAdapter",
                new BaseMotherMentorVisitAdapter(activity, activity, actions));

        activity.redrawVisitUI();
        assertTrue(submit.hasOnClickListeners());
        for (BaseMotherMentorVisitAction required : actions.values()) {
            if (required.isOptional()) continue;
            required.setActionStatus(BaseMotherMentorVisitAction.Status.PENDING);
            activity.redrawVisitUI();
            assertFalse(required.getTitle(), submit.hasOnClickListeners());
            required.setActionStatus(BaseMotherMentorVisitAction.Status.COMPLETED);
        }
    }

    private void assertVisibility(
            List<BaseMotherMentorVisitAction> actions, boolean fullVisit, boolean comment) {
        assertTrue(actions.get(0).isValid());
        assertTrue(actions.get(0).isEnabled());
        for (int i = 1; i < actions.size(); i++) {
            BaseMotherMentorVisitAction action = actions.get(i);
            boolean expected = i == actions.size() - 1 ? comment : fullVisit;
            assertEquals(action.getTitle(), expected, action.isValid());
            assertEquals(action.getTitle(), expected, action.isEnabled());
        }
    }
}
