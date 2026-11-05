package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.repository.Repository;
import org.smartregister.util.FormUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/** Real first-action helpers must finish an exit without opening unrelated service forms. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherExitActionFlowTest {
    private static final LocalDate DAY = LocalDate.now();
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousForms;
    private SQLiteDatabase data;
    private String language;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        previousForms = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, processed INTEGER, visit_date TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status TEXT, art_start_date TEXT)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), nullable(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.getApplication());
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        FormUtils forms = mock(FormUtils.class);
        when(forms.getFormJson(anyString())).thenAnswer(call -> {
            try (Scanner input =
                            new Scanner(RuntimeEnvironment.getApplication().getAssets().open(
                                                language + "/" + call.getArgument(0) + ".json"),
                                    "UTF-8")
                                    .useDelimiter("\\A")) {
                return new JSONObject(input.next());
            }
        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", forms);
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousForms);
    }

    @Test
    public void allTenExitPathsKeepOnlyEntryAndCommentsInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            language = directory;
            for (String reason :
                    new String[] {"deceased", "moved_area", "moved_facility", "other"}) {
                Map<String, BaseMotherMentorVisitAction> actions = start(false);
                for (int strike = 1; strike <= ("other".equals(reason) ? 3 : 1); strike++) {
                    answer(entry(actions), ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "not_found",
                            MAMA_NOT_FOUND_REASON, reason, MAMA_SABABU_NYINGINE_TEXT, "Away",
                            MAMA_KITUO_KIPYA, "Kibiti");
                    assertEquals(directory + ":" + reason + ":" + strike, 2, visible(actions));
                    assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED,
                            entry(actions).getActionStatus());
                    assertTrue(byForm(actions, "mothermentor_visit_comment").isOptional());
                }
            }
            for (String outcome : new String[] {"pregnancy_loss", "stillbirth", "other",
                            "child_deceased", "confirmed_positive", "final_negative"}) {
                Map<String, BaseMotherMentorVisitAction> actions = start(false);
                answer(entry(actions), clinical(outcome));
                assertEquals(directory + ":" + outcome,
                        BaseMotherMentorVisitAction.Status.COMPLETED,
                        entry(actions).getActionStatus());
                assertEquals(directory + ":" + outcome, 2, visible(actions));
            }
        }
    }

    @Test
    public void validContinuingPathsExposeAllFourServicesInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            language = directory;
            for (String path : new String[] {"new", "pregnant", "alive", "pending", "unconfirmed",
                            "not_done", "early_negative"}) {
                Map<String, BaseMotherMentorVisitAction> actions = start(false);
                String[] values;
                if ("new".equals(path))
                    values = new String[] {ATTENDANCE_TYPE, "new"};
                else if ("pregnant".equals(path))
                    values = new String[] {ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                            VISIT_PREGNANCY_STATUS, "pregnant"};
                else
                    values = clinical(path);
                answer(entry(actions), values);
                assertEquals(directory + ":" + path, BaseMotherMentorVisitAction.Status.COMPLETED,
                        entry(actions).getActionStatus());
                assertEquals(directory + ":" + path, 6, visible(actions));
            }
        }
    }

    @Test
    public void missingClinicalAnswersRemainPendingAndDoNotExposeServices() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            language = directory;
            for (String missing : new String[] {VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME,
                            MAMA_PREGNANCY_LOSS_DATE, MAMA_PREGNANCY_OUTCOME_OTHER,
                            MAMA_TAREHE_MTOTO_ALIFARIKI, HEI_CHANYA_IMETHIBITISHWA}) {
                Map<String, BaseMotherMentorVisitAction> actions = start(false);
                String outcome = MAMA_TAREHE_MTOTO_ALIFARIKI.equals(missing) ? "child_deceased"
                        : HEI_CHANYA_IMETHIBITISHWA.equals(missing)          ? "confirmed_positive"
                        : MAMA_PREGNANCY_OUTCOME_OTHER.equals(missing)       ? "other"
                                                                                : "pregnancy_loss";
                answer(entry(actions), clinical(outcome));
                answer(entry(actions), missing, "");
                assertEquals(directory + ":" + missing, BaseMotherMentorVisitAction.Status.PENDING,
                        entry(actions).getActionStatus());
                assertEquals(directory + ":" + missing, 2, visible(actions));
            }
        }
    }

    @Test
    public void automaticAttendanceStillNeedsAnActualSavedEntry() throws Exception {
        language = "json.form";
        Map<String, BaseMotherMentorVisitAction> first = start(false);
        assertEquals("new", value(entry(first), ATTENDANCE_TYPE));
        assertEquals(2, visible(first));
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, entry(first).getActionStatus());
        answer(entry(first), ATTENDANCE_TYPE, "new");
        assertEquals(6, visible(first));
        data.execSQL("INSERT INTO visits VALUES ('previous','mother',?,1,'1')",
                new Object[] {Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES});
        Map<String, BaseMotherMentorVisitAction> repeat = start(false);
        assertEquals("repeat", value(entry(repeat), ATTENDANCE_TYPE));
        assertTrue(field(entry(repeat), ATTENDANCE_TYPE).optBoolean("read_only"));
        assertEquals(2, visible(repeat));
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, entry(repeat).getActionStatus());
        answer(entry(repeat), FOLLOW_UP_STATUS, "found");
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, entry(repeat).getActionStatus());
        assertEquals(2, visible(repeat));
        answer(entry(repeat), VISIT_PREGNANCY_STATUS, "pregnant");
        assertEquals(6, visible(repeat));
    }

    @Test
    public void switchingToAnExitClearsServiceAnswersButRetainsCommentsAndMetadata()
            throws Exception {
        language = "json.form-sw";
        Map<String, BaseMotherMentorVisitAction> actions = start(false);
        answer(entry(actions), ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                VISIT_PREGNANCY_STATUS, "pregnant");
        BaseMotherMentorVisitAction group = byForm(actions, "mothermentor_visit_group_linkage");
        answer(group, HAS_BEEN_LINKED_TO_IGA_GROUP, "yes",
                HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP, "no");
        BaseMotherMentorVisitAction comments = byForm(actions, "mothermentor_visit_comment");
        answer(comments, COMMENTS, "Keep this comment");
        answer(entry(actions), clinical("pregnancy_loss"));
        assertEquals(2, visible(actions));
        assertEquals("", value(group, HAS_BEEN_LINKED_TO_IGA_GROUP));
        assertEquals("", value(group, HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP));
        assertEquals("Keep this comment", value(comments, COMMENTS));
        answer(entry(actions), VISIT_PREGNANCY_STATUS, "pregnant");
        assertEquals(6, visible(actions));
        assertEquals("", value(group, HAS_BEEN_LINKED_TO_IGA_GROUP));
    }

    private String[] clinical(String outcome) {
        String dob = DAY.minusMonths("final_negative".equals(outcome) ? 18 : 2).toString();
        if ("alive".equals(outcome))
            dob = DAY.minusDays(10).toString();
        return new String[] {ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                VISIT_PREGNANCY_STATUS, "not_pregnant", MAMA_PREGNANCY_OUTCOME,
                "pregnancy_loss".equals(outcome) || "stillbirth".equals(outcome)
                                || "other".equals(outcome)
                        ? outcome
                        : "delivered",
                MAMA_PREGNANCY_LOSS_DATE, DAY.minusDays(1).toString(), MAMA_PREGNANCY_OUTCOME_OTHER,
                "Clinical reason", MAMA_TAREHE_KUJIFUNGUA, dob, MAMA_DELIVERY_DATE_INPUT, dob,
                MAMA_PRETERM_DELIVERY_DATE_INPUT, dob, MAMA_STILLBIRTH_DATE_INPUT, dob,
                MAMA_CHILD_STATUS, "child_deceased".equals(outcome) ? "deceased" : "alive",
                MAMA_TAREHE_MTOTO_ALIFARIKI, DAY.toString(), MAMA_BREASTFEEDING_STATUS,
                "breastfeeding", MAMA_HEI_TEST_DONE, "not_done".equals(outcome) ? "no" : "yes",
                MAMA_HEI_TEST_TYPE,
                "final_negative".equals(outcome) ? "eighteen_months" : "six_weeks",
                MAMA_HEI_TEST_DATE, DAY.toString(), MAMA_HEI_TEST_RESULT,
                "confirmed_positive".equals(outcome) || "unconfirmed".equals(outcome) ? "positive"
                        : "pending".equals(outcome)                                   ? "pending"
                                                                                        : "negative",
                HEI_CHANYA_IMETHIBITISHWA, "confirmed_positive".equals(outcome) ? "yes" : "no"};
    }

    private LinkedHashMap<String, BaseMotherMentorVisitAction> start(boolean edit) {
        FixtureInteractor interactor = new FixtureInteractor();
        interactor.appExecutors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.getApplication());
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

    private BaseMotherMentorVisitAction entry(Map<String, BaseMotherMentorVisitAction> actions) {
        return byForm(actions, "mothermentor_visit_type");
    }

    private BaseMotherMentorVisitAction byForm(
            Map<String, BaseMotherMentorVisitAction> actions, String name) {
        return actions.values()
                .stream()
                .filter(a -> name.equals(a.getFormName()))
                .findFirst()
                .get();
    }

    private int visible(Map<String, BaseMotherMentorVisitAction> actions) {
        return (int) actions.values().stream().filter(BaseMotherMentorVisitAction::isValid).count();
    }

    private JSONObject field(BaseMotherMentorVisitAction action, String key) throws Exception {
        JSONObject field = com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(
                new JSONObject(action.getJsonPayload()), key);
        assertTrue(key, field.length() > 0);
        return field;
    }

    private String value(BaseMotherMentorVisitAction action, String key) throws Exception {
        return field(action, key).optString("value");
    }

    private void answer(BaseMotherMentorVisitAction action, String... answers) throws Exception {
        JSONObject form = new JSONObject(action.getJsonPayload());
        for (int i = 0; i < answers.length; i += 2) {
            JSONObject field =
                    com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, answers[i]);
            assertTrue(answers[i], field.length() > 0);
            field.put("value", answers[i + 1]);
        }
        action.setJsonPayload(form.toString());
    }

    private static final class FixtureInteractor extends MotherMentorServiceVisitInteractor {
        FixtureInteractor() {
            super(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        }
        @Override
        protected MotherMentorMotherVisitDao.Context loadMotherContext(String id) {
            return new MotherMentorMotherVisitDao.Context(
                    "enrollment-one", "", "pregnant", "", "", "family", 1);
        }
        @Override
        protected MotherMentorVisitTypeActionHelper createVisitTypeActionHelper() {
            return new MotherMentorVisitTypeActionHelper(
                    memberObject, this::onEntryChanged, enrollmentContext) {
                @Override
                protected Map<String, String> loadEnrollmentValues() {
                    return Collections.singletonMap(PRIMARY_MOTHERMENTOR_CLIENT, "true");
                }
                @Override
                protected Map<String, String> loadDeliveryAnswers() {
                    return Collections.emptyMap();
                }
                @Override
                protected MotherMentorMotherChildRepository.State loadChildState() {
                    return new MotherMentorMotherChildRepository.State(null);
                }
                @Override
                protected LocalDate loadServiceDate() {
                    return DAY;
                }
            };
        }
    }
}
