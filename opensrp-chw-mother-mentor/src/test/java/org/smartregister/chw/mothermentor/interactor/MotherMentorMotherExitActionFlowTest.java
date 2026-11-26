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
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherClosureRepository;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorDeliveryChildren;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.repository.AllSharedPreferences;
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
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status TEXT, art_start_date TEXT, last_interacted_with INTEGER)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        when(library.getSourceDateFormat()).thenReturn("dd-MM-yyyy");
        when(library.getSaveDateFormat()).thenReturn("yyyy-MM-dd");
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
                answerClinical(actions, clinical(outcome));
                assertEquals(directory + ":" + outcome,
                        BaseMotherMentorVisitAction.Status.COMPLETED,
                        entry(actions).getActionStatus());
                assertEquals(directory + ":" + outcome,
                        outcome.equals("pregnancy_loss") || outcome.equals("other") ? 2 : 3, visible(actions));
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
                    values = new String[] {ATTENDANCE_TYPE, "new", VISIT_PREGNANCY_STATUS, "pregnant"};
                else if ("pregnant".equals(path))
                    values = new String[] {ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "found",
                            VISIT_PREGNANCY_STATUS, "pregnant"};
                else
                    values = clinical(path);
                answerClinical(actions, values);
                assertEquals(directory + ":" + path, BaseMotherMentorVisitAction.Status.COMPLETED,
                        entry(actions).getActionStatus());
                assertEquals(directory + ":" + path, "new".equals(path) || "pregnant".equals(path)
                        ? 6 : 7, visible(actions));
            }
        }
    }

    @Test
    public void missingClinicalAnswersRemainPendingAndDoNotExposeServices() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            language = directory;
            for (String missing : new String[] {VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME,
                            MAMA_PREGNANCY_LOSS_DATE, MAMA_PREGNANCY_OUTCOME_OTHER,
                            MAMA_TAREHE_MTOTO_ALIFARIKI, MAMA_HEI_CONFIRMATORY_TEST_DONE,
                            MAMA_HEI_CONFIRMATORY_RESULT}) {
                Map<String, BaseMotherMentorVisitAction> actions = start(false);
                String outcome = MAMA_TAREHE_MTOTO_ALIFARIKI.equals(missing) ? "child_deceased"
                        : missing.startsWith("mama_hei_confirmatory_")          ? "confirmed_positive"
                        : MAMA_PREGNANCY_OUTCOME_OTHER.equals(missing)       ? "other"
                                                                                : "pregnancy_loss";
                answerClinical(actions, clinical(outcome));
                BaseMotherMentorVisitAction section = MotherMentorMotherChildUtils.isChildField(missing)
                        ? byForm(actions, "mothermentor_visit_child") : entry(actions);
                answer(section, missing, "");
                assertEquals(directory + ":" + missing, BaseMotherMentorVisitAction.Status.PENDING,
                        section.getActionStatus());
                assertEquals(directory + ":" + missing, section == entry(actions) ? 2 : 7, visible(actions));
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
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, entry(first).getActionStatus());
        assertEquals(2, visible(first));
        answer(entry(first), VISIT_PREGNANCY_STATUS, "pregnant");
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
    public void firstPostDeliveryVisitRequiresSeparateBirthAndDueTestAnswersInBothLanguages()
            throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            language = directory;
            LocalDate birth = DAY.minusDays(49);
            FixtureInteractor interactor = new FixtureInteractor("breast_feeding", birth);
            Map<String, BaseMotherMentorVisitAction> actions = start(interactor, false);
            BaseMotherMentorVisitAction child = byForm(actions, "mothermentor_visit_child");
            assertFalse(child.isValid());
            answer(entry(actions), ATTENDANCE_TYPE, "new", FOLLOW_UP_STATUS, "not_found");
            assertEquals("", value(entry(actions), FOLLOW_UP_STATUS));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, entry(actions).getActionStatus());
            assertTrue(child.isValid());
            assertFalse(child.isOptional());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            assertEquals("true", value(child, MAMA_BIRTH_TEST_REQUIRED));
            assertEquals(birth.plusDays(2).toString("dd-MM-yyyy"),
                    field(child, MAMA_BIRTH_TEST_DATE).getString("max_date"));

            answer(child, MotherMentorDeliveryChildren.SEX, "female",
                    MAMA_CHILD_STATUS, "alive", MAMA_BREASTFEEDING_STATUS, "breastfeeding");
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_BIRTH_TEST_DONE, "yes", MAMA_BIRTH_TEST_RESULT, "pending",
                    MAMA_BIRTH_TEST_DATE, birth.plusDays(3).toString(),
                    MAMA_HEI_TEST_DONE, "no");
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_BIRTH_TEST_DATE, birth.plusDays(1).toString(), MAMA_HEI_TEST_DONE, "");
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_DONE, "yes", MAMA_HEI_TEST_TYPE, "six_weeks",
                    MAMA_HEI_TEST_DATE, DAY.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_RESULT, "negative");
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_RECEIVED_DATE, DAY.minusDays(1).toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_RECEIVED_DATE, DAY.plusDays(1).toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_RECEIVED_DATE, DAY.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, child.getActionStatus());
            answer(child, MAMA_HEI_TEST_RESULT, "pending");
            assertEquals("", value(child, MAMA_HEI_TEST_RECEIVED_DATE));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, child.getActionStatus());
            assertEquals(7, visible(actions));
            answer(entry(actions), ATTENDANCE_TYPE, "new");
            assertEquals("pending", value(child, MAMA_BIRTH_TEST_RESULT));
            assertEquals("pending", value(child, MAMA_HEI_TEST_RESULT));
        }
    }

    @Test
    public void firstVisitAppliesChildExitsAndPersistsEachAnswerOnce() throws Exception {
        language = "json.form";
        LocalDate birth = DAY.minusDays(1);
        FixtureInteractor interactor = new FixtureInteractor("pregnant", null);
        Map<String, BaseMotherMentorVisitAction> actions = start(interactor, false);
        BaseMotherMentorVisitAction entry = entry(actions);
        BaseMotherMentorVisitAction child = byForm(actions, "mothermentor_visit_child");
        answer(entry, ATTENDANCE_TYPE, "new", VISIT_PREGNANCY_STATUS, "not_pregnant",
                MAMA_PREGNANCY_OUTCOME, "delivered", MAMA_DELIVERY_DATE_INPUT, birth.toString(),
                MotherMentorDeliveryChildren.COUNT, "1");
        assertTrue(child.isValid());
        answer(child, MotherMentorDeliveryChildren.SEX, "male", MAMA_CHILD_STATUS, "deceased");
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
        answer(child, MAMA_TAREHE_MTOTO_ALIFARIKI, DAY.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, child.getActionStatus());
        assertEquals(VisitOutcome.CHILD_DECEASED, outcome(entry, child));
        assertEquals(3, visible(actions));

        answer(child, MAMA_CHILD_STATUS, "alive", MAMA_BREASTFEEDING_STATUS, "breastfeeding",
                MAMA_BIRTH_TEST_DONE, "yes", MAMA_BIRTH_TEST_DATE, birth.toString(),
                MAMA_BIRTH_TEST_RESULT, "positive", MAMA_BIRTH_TEST_RECEIVED_DATE, DAY.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
        answer(child, MAMA_BIRTH_TEST_CONFIRMATION_DONE, "yes");
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, child.getActionStatus());
        answer(child, MAMA_BIRTH_TEST_CONFIRMATION_RESULT, "positive");
        assertEquals(VisitOutcome.CHILD_CONFIRMED_POSITIVE, outcome(entry, child));
        assertEquals(3, visible(actions));

        MotherMentorLibrary library = MotherMentorLibrary.getInstance();
        when(library.context().allSharedPreferences()).thenReturn(mock(AllSharedPreferences.class));
        when(library.visitRepository()).thenReturn(mock(VisitRepository.class));
        Map<String, String> forms = new LinkedHashMap<>();
        forms.put(entry.getTitle(), entry.getJsonPayload());
        forms.put(child.getTitle(), child.getJsonPayload());
        Visit saved = interactor.saveVisit(false, "mother", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, forms, "");
        org.json.JSONArray observations = new JSONObject(saved.getJson()).getJSONArray("obs");
        Map<String, String> recorded = MotherMentorVisitCompletion.observationValues(observations);
        for (String key : new String[] {ATTENDANCE_TYPE, VISIT_PREGNANCY_STATUS,
                MAMA_TAREHE_KUJIFUNGUA, childKey(MAMA_CHILD_STATUS), childKey(MAMA_BIRTH_TEST_RESULT),
                childKey(MAMA_BIRTH_TEST_RECEIVED_DATE)}) {
            int count = 0;
            for (int i = 0; i < observations.length(); i++) {
                if (key.equals(observations.getJSONObject(i).optString("fieldCode"))) count++;
            }
            assertEquals(key, 1, count);
        }
        assertEquals(DAY.toString(), recorded.get(childKey(MAMA_BIRTH_TEST_RECEIVED_DATE)));
        assertTrue(MotherMentorVisitCompletion.isComplete(observations));
        assertEquals(VisitOutcome.CHILD_CONFIRMED_POSITIVE,
                MotherMentorMotherClosureRepository.outcome(recorded));
        assertEquals("positive", MotherMentorMotherChildRepository.replay(birth,
                Collections.singletonList(recorded), Collections.singletonList(DAY))
                        .getSamplingHistory().get(Stage.AT_BIRTH).getResult());

        answer(entry, VISIT_PREGNANCY_STATUS, "pregnant");
        assertFalse(child.isValid());
        assertNull(child.getJsonPayload());
    }

    @Test
    @Config(qualifiers = "sw")
    public void twinsRequireBothSectionsAndCanBeEditedBeforeSavingTheVisit() throws Exception {
        language = "json.form-sw";
        Map<String, BaseMotherMentorVisitAction> actions = start(false);
        BaseMotherMentorVisitAction attendance = entry(actions);
        answer(attendance, ATTENDANCE_TYPE, "new", VISIT_PREGNANCY_STATUS, "not_pregnant",
                MAMA_PREGNANCY_OUTCOME, "delivered", MAMA_DELIVERY_DATE_INPUT, DAY.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, attendance.getActionStatus());
        assertEquals(2, visible(actions));

        answer(attendance, MotherMentorDeliveryChildren.COUNT, "2");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, attendance.getActionStatus());
        BaseMotherMentorVisitAction first = actions.get("Mtoto wa 1");
        BaseMotherMentorVisitAction second = actions.get("Mtoto wa 2");
        assertTrue(first.isValid());
        assertTrue(second.isValid());
        assertFalse(first.isOptional());
        assertFalse(second.isOptional());
        answer(first, MotherMentorDeliveryChildren.SEX, "female", MAMA_CHILD_STATUS, "stillbirth");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, first.getActionStatus());
        assertEquals("Mtoto wa 1 (Wa kike)", first.getDisplayTitle());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, second.getActionStatus());
        answer(first, MAMA_CHILD_STATUS, "alive", MAMA_BREASTFEEDING_STATUS, "breastfeeding",
                MAMA_BIRTH_TEST_DONE, "no");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, first.getActionStatus());
        assertEquals("alive", value(first, MAMA_CHILD_STATUS));
        answer(second, MotherMentorDeliveryChildren.SEX, "male", MAMA_CHILD_STATUS, "stillbirth");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, second.getActionStatus());
        assertEquals("Mtoto wa 2 (Wa kiume)", second.getDisplayTitle());
        assertEquals(8, visible(actions));
    }

    private VisitOutcome outcome(BaseMotherMentorVisitAction entry, BaseMotherMentorVisitAction child)
            throws Exception {
        Map<String, String> values = MotherMentorMotherVisitUtils.formValues(new JSONObject(entry.getJsonPayload()));
        values.putAll(MotherMentorDeliveryChildren.actionValues(new JSONObject(child.getJsonPayload())));
        return MotherMentorMotherClosureRepository.classify(values, DAY);
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
        answerClinical(actions, clinical("pregnancy_loss"));
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
                MotherMentorDeliveryChildren.COUNT, "1", MotherMentorDeliveryChildren.SEX, "female",
                VISIT_PREGNANCY_STATUS, "not_pregnant", MAMA_PREGNANCY_OUTCOME,
                "pregnancy_loss".equals(outcome) || "other".equals(outcome)
                        ? outcome
                        : "delivered",
                MAMA_PREGNANCY_LOSS_DATE, DAY.minusDays(1).toString(), MAMA_PREGNANCY_OUTCOME_OTHER,
                "Clinical reason", MAMA_TAREHE_KUJIFUNGUA, dob, MAMA_DELIVERY_DATE_INPUT, dob,
                MAMA_PRETERM_DELIVERY_DATE_INPUT, dob, MAMA_STILLBIRTH_DATE_INPUT, dob,
                MAMA_CHILD_STATUS, "child_deceased".equals(outcome) ? "deceased"
                        : "stillbirth".equals(outcome) ? "stillbirth" : "alive",
                MAMA_TAREHE_MTOTO_ALIFARIKI, DAY.toString(), MAMA_BREASTFEEDING_STATUS,
                "breastfeeding", MAMA_BIRTH_TEST_DONE, "no",
                MAMA_HEI_TEST_DONE, "not_done".equals(outcome) ? "no" : "yes",
                MAMA_HEI_TEST_TYPE,
                "final_negative".equals(outcome) ? "eighteen_months" : "six_weeks",
                MAMA_HEI_TEST_DATE, DAY.toString(), MAMA_HEI_TEST_RESULT,
                "confirmed_positive".equals(outcome) || "unconfirmed".equals(outcome) ? "positive"
                        : "pending".equals(outcome)                                   ? "pending"
                                                                                        : "negative",
                MAMA_HEI_TEST_RECEIVED_DATE, DAY.toString(),
                MAMA_HEI_CONFIRMATORY_TEST_DONE, "confirmed_positive".equals(outcome) ? "yes" : "no",
                MAMA_HEI_CONFIRMATORY_RESULT, "confirmed_positive".equals(outcome) ? "positive" : ""};
    }

    private String childKey(String key) {
        return MotherMentorDeliveryChildren.observationKey(1, key);
    }

    private void answerClinical(Map<String, BaseMotherMentorVisitAction> actions, String... answers) throws Exception {
        java.util.List<String> maternal = new java.util.ArrayList<>();
        java.util.List<String> child = new java.util.ArrayList<>();
        for (int i = 0; i < answers.length; i += 2) {
            java.util.List<String> target = MotherMentorMotherChildUtils.isChildField(answers[i]) ? child : maternal;
            target.add(answers[i]);
            target.add(answers[i + 1]);
        }
        answer(entry(actions), maternal.toArray(new String[0]));
        BaseMotherMentorVisitAction action = byForm(actions, "mothermentor_visit_child");
        if (action.isValid() && !child.isEmpty()) answer(action, child.toArray(new String[0]));
    }

    private LinkedHashMap<String, BaseMotherMentorVisitAction> start(boolean edit) {
        return start(new FixtureInteractor("pregnant", null), edit);
    }

    private LinkedHashMap<String, BaseMotherMentorVisitAction> start(FixtureInteractor interactor,
            boolean edit) {
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
        private final String status;
        private final LocalDate birth;

        FixtureInteractor(String status, LocalDate birth) {
            super(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
            this.status = status;
            this.birth = birth;
        }
        @Override
        protected MotherMentorMotherVisitDao.Context loadMotherContext(String id) {
            String dob = birth == null ? "" : birth.toString();
            return new MotherMentorMotherVisitDao.Context(
                    "enrollment-one", "", status, dob, dob, "family", 1);
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
