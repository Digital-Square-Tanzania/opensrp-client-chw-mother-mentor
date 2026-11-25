package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorAwaitedResults;
import org.smartregister.chw.mothermentor.util.MotherMentorDeliveryChildren;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtilsTest;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildRepositoryTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, form_submission_id TEXT,"
                + " processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT, mother_base_entity_id TEXT, child_dob TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','breast_feeding','2025-01-31',"
                + "'2025-03-01T08:00:00.000+03:00','2025-03-01',1,0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household')");
        enrollment("episode-one", "2025-03-01T08:00:00.000+03:00");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), nullable(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
    }

    @After public void tearDown() { data.close(); }

    @Test
    public void explicitMigrationAddsProjectionColumnsOnceAndRebuildsFromFinalizedEvidence() throws Exception {
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("six_weeks", "negative", dob.plusDays(43));
        visit("one", "source-one", "mother", values, dob.plusDays(45), 1, false);
        MotherMentorMotherChildRepository.upgrade(db);
        MotherMentorMotherChildRepository.upgrade(db);
        assertTrue(MotherMentorMotherChildRepository.COLUMNS.contains("mama_followed_child_count"));
        assertTrue(MotherMentorMotherChildRepository.COLUMNS.contains("mama_child_pending_tests"));
        assertEquals("negative", scalar("SELECT hei_test_wiki6_result FROM ec_mothermentor_enrollment"));
        assertEquals("2025-03-15", scalar("SELECT hei_test_wiki6_date FROM ec_mothermentor_enrollment"));
        assertNull(MotherMentorMotherChildRepository.getState(db, "mother", null)
                .getTests().get(Stage.SIX_WEEKS).getReceivedDate());
        assertEquals("breastfeeding", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        data.delete("visits", null, null);
        MotherMentorMotherChildRepository.rebuild(db, "mother");
        assertEquals("", scalar("SELECT hei_test_wiki6_result FROM ec_mothermentor_enrollment"));
    }

    @Test
    public void upgradeBackfillsOlderSingletonSamplesAndReturningOneKeepsTheOther() throws Exception {
        LocalDate serviceDay = dob.plusDays(49);
        LocalDate routineSample = dob.plusDays(44);
        Map<String, String> saved = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", routineSample);
        saved.put(MAMA_BIRTH_TEST_WORKFLOW, "true");
        saved.put(MAMA_BIRTH_TEST_DONE, "yes");
        saved.put(MAMA_BIRTH_TEST_DATE, dob.toString());
        saved.put(MAMA_BIRTH_TEST_RESULT, "pending");
        MotherMentorMotherChildRepository.upgrade(db);
        visit("child-visit", "child-source", "mother", saved, serviceDay, 1, false);
        // Alpha 46 left the already-existing pending-test column blank for singletons.
        assertEquals("[]", scalar("SELECT mama_child_pending_tests FROM ec_mothermentor_enrollment"));
        data.execSQL("UPDATE ec_mothermentor_enrollment SET mama_child_pending_tests = ''");
        MotherMentorMotherChildRepository.upgrade(db);
        assertEquals("", scalar("SELECT mama_followed_child_count FROM ec_mothermentor_enrollment"));
        assertEquals(new JSONArray().put(new JSONArray().put(1).put(Stage.AT_BIRTH.getCode()).put(dob.toString()))
                        .put(new JSONArray().put(1).put(Stage.SIX_WEEKS.getCode()).put(routineSample.toString()))
                        .toString(),
                scalar("SELECT mama_child_pending_tests FROM ec_mothermentor_enrollment"));
        resultEvent("routine", "child-source", Stage.SIX_WEEKS, routineSample, serviceDay.plusDays(1),
                "negative", "awaiting_result", null);
        MotherMentorMotherChildRepository.rebuild(db, "mother");
        assertEquals(new JSONArray().put(new JSONArray().put(1).put(Stage.AT_BIRTH.getCode()).put(dob.toString()))
                .toString(), scalar("SELECT mama_child_pending_tests FROM ec_mothermentor_enrollment"));
    }

    @Test
    public void followUpVisitRequiresEachAnswerAndReopensWithOnlyTheUnreceivedSample() throws Exception {
        LocalDate day = dob.plusDays(49);
        LocalDate routineSample = dob.plusDays(43);
        Map<String, String> first = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", routineSample);
        first.put(MAMA_BIRTH_TEST_WORKFLOW, "true");
        first.put(MAMA_BIRTH_TEST_DONE, "yes");
        first.put(MAMA_BIRTH_TEST_DATE, dob.toString());
        first.put(MAMA_BIRTH_TEST_RESULT, "pending");
        visit("first", "sample-source", "mother", first, day, 1, false);

        LocalDate followUp = day.plusDays(1);
        List<MotherMentorMotherResultEvent.PendingTest> samples =
                MotherMentorMotherResultEvent.pending(db, "mother", followUp);
        assertEquals(2, samples.size());
        assertTrue(samples.get(0).isOverdue(followUp));
        assertFalse(samples.get(1).isOverdue(followUp));
        Map<String, String> answers = awaitedFollowUp(first);
        for (MotherMentorMotherResultEvent.PendingTest sample : samples) {
            awaited(answers, sample.getStage(), MotherMentorAwaitedResults.SAMPLE, sample.getKey());
        }
        awaited(answers, Stage.AT_BIRTH, MotherMentorAwaitedResults.RECEIVED, "no");
        assertFalse(MotherMentorAwaitedResults.isComplete(samples, MotherMentorDeliveryChildren.childVisit(answers, 1), followUp));
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RECEIVED, "yes");
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RESULT, "negative");
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RECEIPT, routineSample.minusDays(1).toString());
        assertFalse(MotherMentorAwaitedResults.isComplete(samples, MotherMentorDeliveryChildren.childVisit(answers, 1), followUp));
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RECEIPT, followUp.toString());
        assertTrue(MotherMentorAwaitedResults.isComplete(samples, MotherMentorDeliveryChildren.childVisit(answers, 1), followUp));
        visit("follow-up", "follow-up-source", "mother", answers, followUp, 1, false);

        MotherMentorMotherChildRepository.State reopened =
                MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals(1, reopened.getPendingMotherTests().size());
        assertEquals(Stage.AT_BIRTH, reopened.getPendingMotherTests().get(0).getStage());
        assertEquals("negative", reopened.getTests().get(Stage.SIX_WEEKS).getResult());
        assertEquals(routineSample, reopened.getTests().get(Stage.SIX_WEEKS).getDate());
        assertEquals(followUp, reopened.getTests().get(Stage.SIX_WEEKS).getReceivedDate());

        resultEvent("profile-result", "sample-source", followUp.plusDays(1), "negative", "awaiting_result", null);
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", followUp.plusDays(1)).isEmpty());
    }

    @Test
    public void receivedConfirmationClosesTheChildAgainstTheOriginalSample() throws Exception {
        LocalDate day = dob.plusDays(44);
        Map<String, String> first = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", day);
        first.put(MAMA_HEI_CONFIRMATORY_TEST_DONE, "no");
        first.put(MAMA_HEI_TEST_RECEIVED_DATE, day.toString());
        visit("first", "sample-source", "mother", first, day, 1, false);

        LocalDate next = day.plusDays(2);
        MotherMentorMotherResultEvent.PendingTest pending =
                MotherMentorMotherResultEvent.pending(db, "mother", next).get(0);
        assertTrue(pending.isAwaitingConfirmation());
        Map<String, String> answers = awaitedFollowUp(first);
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.SAMPLE, pending.getKey());
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RECEIVED, "yes");
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.CONFIRMATION, "positive");
        awaited(answers, Stage.SIX_WEEKS, MotherMentorAwaitedResults.RECEIPT, next.toString());
        visit("follow-up", "follow-up-source", "mother", answers, next, 1, false);

        MotherMentorMotherChildRepository.State reopened =
                MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_CONFIRMED_POSITIVE,
                reopened.getTerminalOutcome());
        assertEquals(day, reopened.getTests().get(Stage.SIX_WEEKS).getReceivedDate());
        assertTrue(reopened.getPendingMotherTests().isEmpty());
    }

    private static Map<String, String> awaitedFollowUp(Map<String, String> first) {
        Map<String, String> answers = new LinkedHashMap<>(first);
        answers.keySet().removeIf(key -> key.startsWith("mama_birth_test_")
                || key.startsWith("mama_hei_test_") || key.startsWith("mama_hei_confirmatory_"));
        answers.put(MotherMentorDeliveryChildren.SECTIONS, "true");
        answers.put(MotherMentorDeliveryChildren.COUNT, "1");
        for (String field : Arrays.asList(MAMA_CHILD_STATUS, MAMA_BREASTFEEDING_STATUS)) {
            child(answers, 1, field, answers.remove(field));
        }
        child(answers, 1, MotherMentorDeliveryChildren.SEX, "female");
        child(answers, 1, MotherMentorAwaitedResults.WORKFLOW, "true");
        return answers;
    }

    private static void awaited(Map<String, String> answers, Stage stage, String field, String value) {
        child(answers, 1, MotherMentorAwaitedResults.key(stage, field), value);
    }

    @Test
    public void aSavedNoSuppressesBirthAssessmentWithoutDisplacingWikiSix() throws Exception {
        LocalDate day = dob.plusDays(42);
        Map<String, String> first = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", day);
        first.put(MAMA_BIRTH_TEST_WORKFLOW, "true");
        first.put(MAMA_BIRTH_TEST_DONE, "no");
        visit("first", "first-source", "mother", first, day, 1, false);
        // An offline duplicate answer cannot overwrite an earlier saved decision.
        Map<String, String> duplicate = new LinkedHashMap<>(first);
        duplicate.put(MAMA_BIRTH_TEST_DONE, "yes");
        duplicate.put(MAMA_BIRTH_TEST_DATE, dob.plusDays(2).toString());
        duplicate.put(MAMA_BIRTH_TEST_RESULT, "pending");
        visit("duplicate", "duplicate-source", "mother", duplicate, day.plusDays(1), 1, false);

        MotherMentorMotherChildRepository.State state =
                MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals(dob, state.getBirthTestAnsweredDob());
        assertEquals("pending", state.getTests().get(Stage.SIX_WEEKS).getResult());
        assertFalse(state.getTests().containsKey(Stage.AT_BIRTH));
    }

    @Test
    public void birthAndWikiSixStayIndependentWhenAnOverdueResultReturns() throws Exception {
        LocalDate day = dob.plusDays(49);
        Map<String, String> first = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", day);
        first.put(MAMA_BIRTH_TEST_WORKFLOW, "true");
        first.put(MAMA_BIRTH_TEST_DONE, "yes");
        first.put(MAMA_BIRTH_TEST_DATE, dob.toString());
        first.put(MAMA_BIRTH_TEST_RESULT, "pending");
        visit("first", "first-source", "mother", first, day, 1, false);

        List<MotherMentorMotherResultEvent.PendingTest> pending =
                MotherMentorMotherResultEvent.pending(db, "mother", day);
        assertEquals(2, pending.size());
        assertEquals(Stage.AT_BIRTH, pending.get(0).getStage());
        assertFalse(pending.get(0).isOverdue(dob.plusDays(7)));
        assertTrue(pending.get(0).isOverdue(dob.plusDays(8)));
        assertEquals(Stage.SIX_WEEKS, pending.get(1).getStage());

        resultEvent("unmatched", "unrelated-source", day.plusDays(1), "negative", "awaiting_result", null);
        assertEquals(2, MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(1)).size());
        resultEvent("result", "first-source", day.plusDays(2), "positive", "awaiting_result", null);
        pending = MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(2));
        assertEquals(2, pending.size());
        assertTrue(pending.get(0).isAwaitingConfirmation());
        assertFalse(pending.get(0).isOverdue(day.plusDays(2)));
        Map<String, String> confirmation = new LinkedHashMap<>();
        confirmation.put(MTOTO_CONFIRMATORY_RESULT, "negative");
        LocalDate confirmationDay = day.plusDays(3);
        assertFalse(MotherMentorMotherResultEvent.isComplete(pending.get(0), confirmation, confirmationDay));
        confirmation.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        confirmation.put(MTOTO_RESULT_RECEIVED_DATE, dob.minusDays(1).toString());
        assertFalse(MotherMentorMotherResultEvent.isComplete(pending.get(0), confirmation, confirmationDay));
        confirmation.put(MTOTO_RESULT_RECEIVED_DATE, confirmationDay.plusDays(1).toString());
        assertFalse(MotherMentorMotherResultEvent.isComplete(pending.get(0), confirmation, confirmationDay));
        confirmation.put(MTOTO_RESULT_RECEIVED_DATE, confirmationDay.toString());
        assertTrue(MotherMentorMotherResultEvent.isComplete(pending.get(0), confirmation, confirmationDay));
        resultEvent("confirm", "first-source", day.plusDays(3), null, "awaiting_confirm", "negative");
        pending = MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(3));
        assertEquals(1, pending.size());
        assertEquals(Stage.SIX_WEEKS, pending.get(0).getStage());
        assertEquals("1", scalar("SELECT COUNT(*) FROM visits"));
        assertFalse(MotherMentorMotherChildRepository.getState(db, "mother", null).hasConfirmedPositive());
    }

    @Test
    public void enrollmentDeliveryCountReplaysIntoChildSectionsForNewAndReturnFound() throws Exception {
        verifyEnrollmentDeliveryReplay("not_pregnant");
        verifyEnrollmentDeliveryReplay("breast_feeding");
    }

    private void verifyEnrollmentDeliveryReplay(String enrollmentStatus) throws Exception {
        LocalDate firstVisit = dob.plusDays(4);
        String enrolled = dob.plusDays(1) + "T08:00:00.000+03:00";
        data.delete("event", null, null);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status = ?, event_date = ?",
                new Object[] {enrollmentStatus, enrolled});
        enrollment("episode-one", enrolled, enrollmentStatus);
        for (String attendance : new String[] {"new", "repeat"}) {
            data.delete("visits", null, null);
            Map<String, String> delivery = twinDelivery(dob);
            delivery.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, enrollmentStatus);
            delivery.put(VISIT_PREGNANCY_STATUS, enrollmentStatus);
            delivery.put(MAMA_ENROLLMENT_CHILD_DOB, dob.toString());
            delivery.put(MAMA_FIRST_ENROLLMENT_DELIVERY, "true");
            delivery.put(ATTENDANCE_TYPE, attendance);
            delivery.put(MAMA_FIRST_VISIT_ASSESSMENT, "new".equals(attendance) ? "true" : "");
            if ("repeat".equals(attendance)) delivery.put(FOLLOW_UP_STATUS, "found");
            for (int number = 1; number <= 2; number++) {
                child(delivery, number, MAMA_CHILD_STATUS, "alive");
                child(delivery, number, MAMA_BREASTFEEDING_STATUS, "breastfeeding");
                child(delivery, number, MAMA_BIRTH_TEST_DONE, "no");
            }
            assertTrue(MotherMentorMotherVisitDao.canAssessEnrollmentDelivery(
                    db, "mother", null, MotherMentorMotherVisitDao.getContext(db, "mother")));
            visit("first", "first-form", "mother", delivery, firstVisit, 1, false);
            List<MotherMentorMotherChildRepository.State> children =
                    MotherMentorMotherChildRepository.getStates(db, "mother", null, firstVisit.plusDays(1));
            assertEquals(2, children.size());
            assertEquals("female", children.get(0).getSex());
            assertEquals("male", children.get(1).getSex());
            assertEquals(dob, children.get(1).getChildDob());
            assertEquals(enrollmentStatus, MotherMentorMotherDeliveryRepository.getState(db, "mother", null)
                    .getAnswers().get(MAMA_ENROLLMENT_PREGNANCY_STATUS));
            MotherMentorMotherVisitDao.Context enrollment = MotherMentorMotherVisitDao.getContext(db, "mother");
            assertTrue(MotherMentorMotherVisitDao.canAssessEnrollmentDelivery(db, "mother", "first", enrollment));
            assertFalse(MotherMentorMotherVisitDao.canAssessEnrollmentDelivery(db, "mother", null, enrollment));
        }
    }

    @Test
    public void finalizedLegacyBreastfeedingVisitKeepsItsSingleChildPath() throws Exception {
        LocalDate visitDay = new LocalDate(2025, 3, 2);
        Map<String, String> delivery = new LinkedHashMap<>();
        delivery.put(ATTENDANCE_TYPE, "repeat");
        delivery.put(FOLLOW_UP_STATUS, "found");
        delivery.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "breast_feeding");
        delivery.put(MAMA_ENROLLMENT_CHILD_DOB, dob.toString());
        delivery.put(MAMA_TAREHE_KUJIFUNGUA, dob.toString());
        visit("legacy", "legacy-form", "mother", delivery, visitDay, 1, false);
        MotherMentorMotherVisitDao.Context enrollment = MotherMentorMotherVisitDao.getContext(db, "mother");
        assertFalse(MotherMentorMotherVisitDao.canAssessEnrollmentDelivery(db, "mother", "legacy", enrollment));
        assertFalse(MotherMentorMotherVisitDao.canAssessEnrollmentDelivery(db, "mother", null, enrollment));
        assertEquals(1, MotherMentorMotherChildRepository.getStates(db, "mother", null, visitDay).size());
    }

    @Test
    public void keepsStillbornTwinOutOfFollowUpAndClosesOnTheLastChild() throws Exception {
        data.delete("event", null, null);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET pregnancy_status = 'pregnant', child_dob = ''");
        enrollment("episode-one", "2025-03-01T08:00:00.000+03:00", "pregnant");
        LocalDate birth = new LocalDate(2025, 3, 5);
        LocalDate firstVisit = birth.plusDays(1);
        Map<String, String> delivery = twinDelivery(birth);
        child(delivery, 1, MAMA_CHILD_STATUS, "stillbirth");
        child(delivery, 2, MAMA_CHILD_STATUS, "alive");
        child(delivery, 2, MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        child(delivery, 2, MAMA_BIRTH_TEST_DONE, "no");
        visit("delivery", "delivery", "mother", delivery, firstVisit, 1, false);
        List<MotherMentorMotherChildRepository.State> states =
                MotherMentorMotherChildRepository.getStates(db, "mother", null, firstVisit);
        assertEquals(2, states.size());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.STILLBIRTH, states.get(0).getTerminalOutcome());
        assertEquals("female", states.get(0).getSex());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, states.get(1).getTerminalOutcome());
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", null).isClosed());
        Visit closingVisit = MotherMentorChildEidDao.findVisit(db, "delivery");
        assertFalse(MotherMentorMotherChildRepository.isEarlierSiblingClosingVisit(db, closingVisit, firstVisit));
        assertTrue(MotherMentorMotherChildRepository.isEarlierSiblingClosingVisit(
                db, closingVisit, firstVisit.plusDays(1)));

        data.delete("visits", "visit_id = ?", new String[] {"delivery"});
        Map<String, String> corrected = new LinkedHashMap<>(delivery);
        child(corrected, 1, MAMA_CHILD_STATUS, "alive");
        child(corrected, 1, MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        child(corrected, 1, MAMA_BIRTH_TEST_DONE, "no");
        visit("delivery", "delivery", "mother", corrected, firstVisit, 2, false);
        assertFalse(MotherMentorMotherChildRepository.isEarlierSiblingClosingVisit(
                db, MotherMentorChildEidDao.findVisit(db, "delivery"), firstVisit.plusDays(1)));
        data.delete("visits", "visit_id = ?", new String[] {"delivery"});
        visit("delivery", "delivery", "mother", delivery, firstVisit, 3, false);

        LocalDate finalDay = birth.plusMonths(18);
        Map<String, String> later = twinDelivery(birth);
        later.put(MAMA_CARRIED_DELIVERY, "true");
        child(later, 2, MAMA_CHILD_STATUS, "alive");
        child(later, 2, MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        child(later, 2, MAMA_HEI_TEST_DONE, "yes");
        child(later, 2, MAMA_HEI_TEST_TYPE, "eighteen_months");
        child(later, 2, MAMA_HEI_TEST_DATE, finalDay.toString());
        child(later, 2, MAMA_HEI_TEST_RESULT, "negative");
        child(later, 2, MAMA_HEI_TEST_RECEIVED_DATE, finalDay.toString());
        visit("last", "last", "mother", later, finalDay, 1, false);
        MotherMentorMotherClosure closure = MotherMentorMotherClosureRepository.getState(db, "mother", null);
        assertEquals(MotherMentorMotherClosure.GRADUATED, closure.getStatus());
        assertEquals(finalDay.toString(), closure.getClosedDate());
    }

    private static Map<String, String> twinDelivery(LocalDate birth) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        values.put(VISIT_PREGNANCY_STATUS, "not_pregnant");
        values.put(MAMA_PREGNANCY_OUTCOME, "delivered");
        values.put(MAMA_TAREHE_KUJIFUNGUA, birth.toString());
        values.put(MotherMentorDeliveryChildren.SECTIONS, "true");
        values.put(MotherMentorDeliveryChildren.COUNT, "2");
        child(values, 1, MotherMentorDeliveryChildren.SEX, "female");
        child(values, 2, MotherMentorDeliveryChildren.SEX, "male");
        return values;
    }

    private static void child(Map<String, String> visit, int number, String key, String value) {
        visit.put(MotherMentorDeliveryChildren.observationKey(number, key), value);
    }

    private void resultEvent(String id, String source, LocalDate day, String result,
            String phase, String confirmation) throws Exception {
        resultEvent(id, source, Stage.AT_BIRTH, dob, day, result, phase, confirmation);
    }

    private void resultEvent(String id, String source, Stage stage, LocalDate sampleDay, LocalDate day,
            String result, String phase, String confirmation) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MAMA_ENROLLMENT_ID, "episode-one");
        values.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        values.put("mama_hei_sample_submission_id", source);
        values.put(MTOTO_PENDING_TEST, stage.getCode());
        values.put(MTOTO_PENDING_TEST_DATE, sampleDay.toString());
        values.put(MTOTO_RUFAA_STATE_BEFORE, phase);
        if (result != null) {
            values.put(MTOTO_RECEIVED_RESULT, result);
            values.put(MTOTO_RESULT_RECEIVED_DATE, day.toString());
            values.put(MTOTO_CONFIRMATORY_TEST_DONE, "no");
        }
        if (confirmation != null) {
            values.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
            values.put(MTOTO_CONFIRMATORY_RESULT, confirmation);
        }
        JSONObject event = new JSONObject().put("formSubmissionId", id).put("eventDate", day.toString())
                .put("version", 1).put("obs", observations(values));
        data.execSQL("INSERT INTO event VALUES ('mother', ?, ?, ?, ?)",
                new Object[] {MotherMentorMotherResultEvent.EVENT_TYPE, day.toString(), id, event.toString()});
    }

    @Test
    public void profileConfirmationFollowsTheOriginalSampleAndClosesOnlyForPositive() throws Exception {
        LocalDate day = dob.plusDays(43);
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", day);
        values.put(MAMA_HEI_CONFIRMATORY_TEST_DONE, "no");
        values.put(MAMA_HEI_TEST_RECEIVED_DATE, day.toString());
        visit("first", "sample", "mother", values, day, 1, false);
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day).get(0).isAwaitingConfirmation());

        resultEvent("waiting", "sample", Stage.SIX_WEEKS, day, day.plusDays(1), null, "awaiting_confirm", "pending");
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(1)).get(0).isAwaitingConfirmation());
        resultEvent("unlinked", "other", Stage.SIX_WEEKS, day, day.plusDays(2), null, "awaiting_confirm", "positive");
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
        resultEvent("final", "sample", Stage.SIX_WEEKS, day, day.plusDays(3), null, "awaiting_confirm", "positive");
        assertEquals(MotherMentorMotherClosure.CHILD_CONFIRMED_POSITIVE,
                MotherMentorMotherClosureRepository.getState(db, "mother", null).getStatus());
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(3)).isEmpty());
        assertEquals("1", scalar("SELECT COUNT(*) FROM visits"));
    }

    @Test
    public void eighteenMonthConfirmatoryNegativeClearsPendingWithoutGraduation() throws Exception {
        LocalDate day = dob.plusMonths(18);
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("eighteen_months", "positive", day);
        values.put(MAMA_HEI_CONFIRMATORY_TEST_DONE, "no");
        values.put(MAMA_HEI_TEST_RECEIVED_DATE, day.toString());
        visit("first", "sample", "mother", values, day, 1, false);
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day).get(0).isAwaitingConfirmation());

        resultEvent("invalid", "sample", Stage.EIGHTEEN_MONTHS, day, day.plusDays(1), null, "awaiting_confirm", "pending");
        assertEquals(1, MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(1)).size());
        resultEvent("final", "sample", Stage.EIGHTEEN_MONTHS, day, day.plusDays(2), null, "awaiting_confirm", "negative");
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(2)).isEmpty());
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertTrue(state.getTests().get(Stage.EIGHTEEN_MONTHS).isConfirmationNegative());
        assertFalse(state.hasFinalNegative());
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void twinsKeepTheirOwnOutcomeAndTheMotherCardClosesAfterTheLastChild() throws Exception {
        link("child", dob);
        link("twin", dob);
        Map<String, String> positive = childResult("positive");
        positive.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        visit("positive", "positive", "child", positive, dob.plusMonths(18), 1, false);
        assertTrue(MotherMentorMotherChildRepository.getStateForChild(db, "child", null).hasConfirmedPositive());
        assertFalse(MotherMentorMotherChildRepository.getStateForChild(db, "twin", null).hasConfirmedPositive());
        assertTrue(MotherMentorMotherClosureRepository.hasOpenSibling(db, "mother", "child"));
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));

        LocalDate last = dob.plusMonths(19);
        visit("negative", "negative", "twin", childResult("negative"), last, 1, false);
        MotherMentorMotherClosure closure = MotherMentorMotherClosureRepository.getState(db, "mother", null);
        assertEquals(MotherMentorMotherClosure.GRADUATED, closure.getStatus());
        assertEquals(last.toString(), closure.getClosedDate());
    }

    @Test
    public void manualSiblingLinkedAfterSampleKeepsMamaOpenUntilTheLastChildFinishes() throws Exception {
        enableChildClosureProjections();
        LocalDate sampleDay = dob.plusMonths(18);
        LocalDate resultDay = sampleDay.plusDays(2);
        LocalDate siblingDob = dob.plusDays(3);
        manualLink("child", dob, sampleDay.minusDays(1));
        mamaPositiveSample(sampleDay);
        manualLink("sibling", siblingDob, sampleDay.plusDays(1));

        assertTrue(MotherMentorMotherClosureRepository.hasOpenSibling(db, "mother", "child"));
        resultEvent("result", "mama-sample", Stage.EIGHTEEN_MONTHS, sampleDay, resultDay,
                null, "awaiting_confirm", "positive");
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertEquals("1", scalar("SELECT is_closed FROM ec_mothermentor_enroll_child_eid WHERE base_entity_id='child'"));
        assertEquals("0", scalar("SELECT is_closed FROM ec_mothermentor_enroll_child_eid WHERE base_entity_id='sibling'"));
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));

        LocalDate last = siblingDob.plusMonths(18);
        Map<String, String> siblingResult = childResult("negative");
        siblingResult.put(CHILD_EID_FACILITY_ARRIVAL_DATE, last.toString());
        siblingResult.put(CHILD_EID_FINAL_HIV_TEST_DATE, last.toString());
        visit("sibling-result", "sibling-result", "sibling", siblingResult, last, 1, false);
        assertEquals(last.toString(), MotherMentorMotherClosureRepository.getState(db, "mother", null).getClosedDate());
    }

    @Test
    public void birthConfirmationClosesTheOnlyLinkedChildAndCorrectionReopensIt() throws Exception {
        enableChildClosureProjections();
        LocalDate visitDay = dob.plusDays(43);
        manualLink("child", dob, visitDay.minusDays(1));
        Map<String, String> birth = MotherMentorMotherChildUtilsTest.child(dob);
        birth.put(MAMA_BIRTH_TEST_WORKFLOW, "true");
        birth.put(MAMA_BIRTH_TEST_DONE, "yes");
        birth.put(MAMA_BIRTH_TEST_DATE, dob.toString());
        birth.put(MAMA_BIRTH_TEST_RESULT, "positive");
        birth.put(MAMA_BIRTH_TEST_RECEIVED_DATE, visitDay.toString());
        birth.put(MAMA_BIRTH_TEST_CONFIRMATION_DONE, "no");
        birth.put(MAMA_HEI_TEST_DONE, "no");
        visit("visit", "birth-sample", "mother", birth, visitDay, 1, false);
        LocalDate resultDay = visitDay.plusDays(1);
        resultEvent("result", "birth-sample", resultDay, null, "awaiting_confirm", "positive");
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertTrue(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertEquals("E4", scalar("SELECT mtoto_eid_status FROM ec_mothermentor_enroll_child_eid"));
        assertTrue(MotherMentorMotherClosureRepository.isClosed(db, "mother"));

        data.delete("event", "formSubmissionId = ?", new String[] {"result"});
        resultEvent("result", "birth-sample", resultDay, null, "awaiting_confirm", "negative");
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertFalse(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertEquals("0", scalar("SELECT is_closed FROM ec_mothermentor_enroll_child_eid"));
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void laterSameBirthdayLinkDoesNotTakeOwnershipOfTheOriginalSample() throws Exception {
        enableChildClosureProjections();
        LocalDate sampleDay = dob.plusMonths(18);
        manualLink("child", dob, sampleDay.minusDays(1));
        mamaPositiveSample(sampleDay);
        manualLink("sibling", dob, sampleDay.plusDays(1));
        resultEvent("result", "mama-sample", Stage.EIGHTEEN_MONTHS, sampleDay, sampleDay.plusDays(2),
                null, "awaiting_confirm", "positive");
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertTrue(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertFalse(MotherMentorChildEidRepository.getState(db, "sibling", null).isClosed());
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void sameDayLinkAfterCorrectionKeepsTheLegacySampleWithItsOriginalChild() throws Exception {
        enableChildClosureProjections();
        LocalDate day = dob.plusMonths(18);
        manualLink("child", dob, day + "T09:00:00+03:00");
        mamaPositiveSample(day);
        JSONObject sample = new JSONObject(scalar("SELECT visit_json FROM visits WHERE visit_id='mama-visit'"));
        // The legacy JSON clock is ahead of the timestamp from the local visit save.
        sample.put(EVENT_DATE_TIMESTAMP, day + "T13:00:00+03:00");
        ContentValues visit = new ContentValues();
        visit.put(VISIT_JSON, sample.toString());
        visit.put(VISIT_DATE, org.joda.time.DateTime.parse(day + "T10:00:00+03:00").getMillis());
        data.update("visits", visit, "visit_id = ?", new String[] {"mama-visit"});

        sameDayConfirmation("waiting", day, "T10:30:00+03:00", 1, null);
        assertEquals(1, MotherMentorMotherResultEvent.pending(db, "mother", day).size());
        sameDayConfirmation("second", day, "T11:00:00+03:00", 1, "negative");
        assertTrue(MotherMentorMotherResultEvent.pending(db, "mother", day).isEmpty());
        sameDayConfirmation("second", day, "T11:00:00+03:00", 2, null);
        assertEquals(1, MotherMentorMotherResultEvent.pending(db, "mother", day).size());

        manualLink("sibling", dob, day + "T12:00:00+03:00");
        assertEquals("child", MotherMentorMotherResultEvent.pending(db, "mother", day).get(0).getChildId());
        sameDayConfirmation("positive", day, "T12:30:00+03:00", 1, "positive");
        MotherMentorChildEidRepository.rebuildForMother(db, "mother");
        assertTrue(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertFalse(MotherMentorChildEidRepository.getState(db, "sibling", null).isClosed());
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));

        visit("sibling-result", "sibling-result", "sibling", childResult("negative"), day, 1, false);
        assertTrue(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void capturedChildSurvivesSameBirthdayLinkAndReplay() throws Exception {
        LocalDate day = dob.plusMonths(18);
        manualLink("child", dob, day + "T09:00:00+03:00");
        Map<String, String> positive = MotherMentorMotherChildUtilsTest.test("eighteen_months", "positive", day);
        positive.put(MAMA_HEI_TEST_RECEIVED_DATE, day.toString());
        positive.put(MAMA_HEI_CONFIRMATORY_TEST_DONE, "no");
        positive.put(MAMA_HEI_SAMPLE_CHILD_ID, MotherMentorChildEidDao.captureMotherSampleChild(
                db, "mother", MotherMentorMotherVisitDao.getContext(db, "mother"), dob));
        visit("mama-visit", "mama-sample", "mother", positive, day, 1, false);

        manualLink("sibling", dob, day + "T11:00:00+03:00");
        assertEquals("child", MotherMentorMotherResultEvent.pending(db, "mother", day).get(0).getChildId());
        assertTrue(MotherMentorMotherClosureRepository.hasOpenSibling(db, "mother", "child"));
    }

    private void sameDayConfirmation(String id, LocalDate day, String time, int version, String result)
            throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MAMA_ENROLLMENT_ID, "episode-one");
        values.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        values.put("mama_hei_sample_submission_id", "mama-sample");
        values.put(MTOTO_PENDING_TEST, Stage.EIGHTEEN_MONTHS.getCode());
        values.put(MTOTO_PENDING_TEST_DATE, day.toString());
        values.put(MTOTO_RUFAA_STATE_BEFORE, "awaiting_confirm");
        values.put(MTOTO_CONFIRMATORY_TEST_DONE, result == null ? "no" : "yes");
        if (result != null) {
            values.put(MTOTO_CONFIRMATORY_RESULT, result);
            values.put(MTOTO_RESULT_RECEIVED_DATE, day.toString());
        }
        String date = day + time;
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, id)
                .put(EVENT_DATE_TIMESTAMP, date).put(EVENT_VERSION, version).put("obs", observations(values));
        data.execSQL("INSERT INTO event VALUES ('mother', ?, ?, ?, ?)",
                new Object[] {MotherMentorMotherResultEvent.EVENT_TYPE, date, id, event.toString()});
    }

    @Test
    public void simultaneousSameBirthdayLinksDoNotCloseAnUnidentifiedChild() throws Exception {
        LocalDate sampleDay = dob.plusMonths(18);
        manualLink("child", dob, sampleDay + "T08:00:00+03:00");
        manualLink("sibling", dob, sampleDay + "T09:00:00+03:00");
        mamaPositiveSample(sampleDay);
        JSONObject visitEvent = new JSONObject(scalar("SELECT visit_json FROM visits WHERE visit_id='mama-visit'"));
        visitEvent.put(EVENT_DATE_TIMESTAMP, sampleDay + "T10:00:00+03:00");
        ContentValues visit = new ContentValues();
        visit.put(VISIT_JSON, visitEvent.toString());
        visit.put(VISIT_DATE, org.joda.time.DateTime.parse(sampleDay + "T10:01:00+03:00").getMillis());
        data.update("visits", visit, "visit_id = ?", new String[] {"mama-visit"});
        MotherMentorMotherResultEvent.PendingTest sample =
                MotherMentorMotherResultEvent.pending(db, "mother", sampleDay).get(0);
        assertTrue(MotherMentorMotherResultEvent.needsChildIdentity(db, "mother", sample));
        resultEvent("old-unassigned", "mama-sample", Stage.EIGHTEEN_MONTHS, sampleDay, sampleDay.plusDays(1),
                null, "awaiting_confirm", "positive");
        assertFalse(MotherMentorChildEidRepository.getState(db, "child", null).isClosed());
        assertFalse(MotherMentorChildEidRepository.getState(db, "sibling", null).isClosed());
        assertFalse(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    private void enableChildClosureProjections() {
        data.execSQL("ALTER TABLE ec_mothermentor_enroll_child_eid ADD COLUMN is_closed INTEGER DEFAULT 0");
        MotherMentorChildEidRepository.upgrade(db);
        MotherMentorChildEidRepository.upgradeExitSchema(db);
    }

    private void mamaPositiveSample(LocalDate day) throws Exception {
        Map<String, String> positive = MotherMentorMotherChildUtilsTest.test("eighteen_months", "positive", day);
        positive.put(MAMA_HEI_CONFIRMATORY_TEST_DONE, "no");
        positive.put(MAMA_HEI_TEST_RECEIVED_DATE, day.toString());
        visit("mama-visit", "mama-sample", "mother", positive, day, 1, false);
    }

    private void manualLink(String child, LocalDate birth, LocalDate day) throws Exception {
        manualLink(child, birth, day + "T10:00:00.000+03:00");
    }

    private void manualLink(String child, LocalDate birth, String eventDate) throws Exception {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid (base_entity_id, mother_base_entity_id, child_dob)"
                + " VALUES (?, 'mother', ?)", new Object[] {child, birth.toString()});
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MOTHER_BASE_ENTITY_ID, "mother");
        values.put(CHILD_LINKING, "yes");
        values.put(CHILD_DOB, birth.toString());
        String id = child + "-manual";
        JSONObject event = new JSONObject().put(EVENT_FORM_SUBMISSION_ID, id)
                .put(EVENT_DATE_TIMESTAMP, eventDate).put("obs", observations(values));
        data.execSQL("INSERT INTO event VALUES (?, ?, ?, ?, ?)", new Object[] {child,
                Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID, eventDate, id, event.toString()});
    }

    @Test
    public void pendingAndUnconfirmedResultsRemainDistinctUntilExplicitConfirmation() {
        Map<String, String> pending = MotherMentorMotherChildUtilsTest.test("six_weeks", "pending", dob.plusDays(43));
        Map<String, String> positive = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", dob.plusDays(43));
        positive.put(HEI_CHANYA_IMETHIBITISHWA, "no");
        LocalDate received = dob.plusDays(49);
        positive.put(MAMA_HEI_TEST_RECEIVED_DATE, received.toString());
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.replay(dob,
                Arrays.asList(pending, positive), Arrays.asList(dob.plusDays(44), dob.plusDays(50)));
        assertEquals("positive", state.getTests().get(Stage.SIX_WEEKS).getResult());
        assertEquals(received, state.getTests().get(Stage.SIX_WEEKS).getReceivedDate());
        assertFalse(state.hasConfirmedPositive());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
        positive = new LinkedHashMap<>(positive); positive.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        state = MotherMentorMotherChildRepository.replay(dob, Arrays.asList(pending, positive),
                Arrays.asList(dob.plusDays(44), dob.plusDays(50)));
        assertTrue(state.hasConfirmedPositive());
        assertEquals(dob.plusDays(50), state.getTerminalServiceDate());
    }

    @Test
    public void mismatchedImmutableBirthDateCannotSupplyATerminalChildOutcome() throws Exception {
        Map<String, String> values = MotherMentorMotherChildUtilsTest.test("six_weeks", "positive", dob.plusDays(43));
        values.put(MAMA_TAREHE_KUJIFUNGUA, dob.minusDays(1).toString());
        values.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        visit("invalid-birth", "source", "mother", values, dob.plusDays(45), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertTrue(state.getTests().isEmpty());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
    }

    @Test
    public void linkedEidFinalNegativeIsSharedUsingServiceDateAndNotLabDate() throws Exception {
        link("child", dob);
        visit("child-result", "child-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("child", state.getChildBaseEntityId());
        assertTrue(state.hasFinalNegative());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.GRADUATED, state.getTerminalOutcome());
        assertEquals(dob.plusMonths(19), state.getTerminalServiceDate());
        assertEquals(dob.plusMonths(18), state.getTests().get(Stage.EIGHTEEN_MONTHS).getDate());
    }

    @Test
    public void returnedEidPositiveIsNotPositiveConfirmation() throws Exception {
        link("child", dob);
        Map<String, String> positive = childResult("positive");
        positive.put(LAST_TEST_RESULTS_RETURNED, "yes");
        visit("child-result", "child-source", "child", positive, dob.plusMonths(19), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertFalse(state.hasConfirmedPositive());
        assertTrue(state.getTests().get(Stage.EIGHTEEN_MONTHS).requiresFollowUp());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE, state.getTerminalOutcome());
    }

    @Test
    public void ambiguousTwinsOrWrongChildDobCannotSupplyResults() throws Exception {
        link("child", dob.minusDays(1));
        visit("result", "source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
        data.delete("ec_mothermentor_enroll_child_eid", null, null);
        link("child", dob); link("twin", dob);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("", state.getChildBaseEntityId());
        assertTrue(state.getTests().isEmpty());
    }

    @Test
    public void correctedOrVoidedChildSourceDoesNotResurrectAnOlderResult() throws Exception {
        link("child", dob);
        visit("one", "same-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        visit("two", "same-source", "child", childResult("positive"), dob.plusMonths(19), 2, true);
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
        visit("three", "same-source", "child", childResult("pending"), dob.plusMonths(19), 3, false);
        assertEquals("pending", MotherMentorMotherChildRepository.getState(db, "mother", null)
                .getTests().get(Stage.EIGHTEEN_MONTHS).getResult());
    }

    @Test
    public void reEnrollmentDoesNotInheritPriorEpisodeMotherOrChildResults() throws Exception {
        link("child", dob);
        visit("one", "mother-source", "mother", MotherMentorMotherChildUtilsTest.test(
                "six_weeks", "negative", dob.plusDays(43)), dob.plusDays(45), 1, false);
        visit("two", "child-source", "child", childResult("negative"), dob.plusMonths(19), 1, false);
        String time = "2026-10-01T08:00:00.000+03:00";
        enrollment("episode-two", time);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET event_date = ?", new Object[] {time});
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).getTests().isEmpty());
    }

    @Test
    public void schema3BreastfeedingIsSharedWithTheMotherAndChildWithoutAResult() throws Exception {
        link("child", dob);
        LocalDate day = dob.plusMonths(10);
        LocalDate cessation = dob.plusMonths(6);
        visit("new-child", "new-child", "child", childBreastfeeding(cessation), day, 1, false);
        MotherMentorMotherChildRepository.State mother = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals("not_breastfeeding", mother.getBreastfeedingStatus());
        assertEquals(cessation, mother.getWeaningDate());
        MotherMentorMotherChildRepository.State child = MotherMentorMotherChildRepository.getStateForChild(db, "child", null);
        assertEquals(mother.getBreastfeedingStatus(), child.getBreastfeedingStatus());
        assertEquals(mother.getWeaningDate(), child.getWeaningDate());
        assertTrue(child.getTests().isEmpty());
        MotherMentorMotherChildRepository.upgrade(db);
        assertEquals("not_breastfeeding", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        assertEquals(cessation.toString(), scalar("SELECT mama_tarehe_acha_kunyonyesha FROM ec_mothermentor_enrollment"));
    }

    @Test
    public void replayKeepsBreastfeedingHistoryWhenAnOfflineVisitClaimsNeverBreastfed() throws Exception {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid VALUES ('child', '', ?)",
                new Object[] {dob.toString()});
        LocalDate day = dob.plusMonths(10);
        Map<String, String> never = childBreastfeeding(null);
        never.put(MAMA_BREASTFEEDING_STATUS, "never_breastfed");
        visit("first", "first", "child", never, day, 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository
                .getStateForChild(db, "child", null);
        assertEquals("never_breastfed", state.getBreastfeedingStatus());
        assertFalse(state.hasBreastfeedingHistory());
        assertNull(state.getWeaningDate());

        visit("started", "started", "child", childBreastfeeding(null), day.plusDays(1), 1, false);
        visit("offline", "offline", "child", never, day.plusDays(2), 1, false);
        state = MotherMentorMotherChildRepository.getStateForChild(db, "child", null);
        assertEquals("breastfeeding", state.getBreastfeedingStatus());
        assertTrue(state.hasBreastfeedingHistory());
        assertNull(state.getWeaningDate());
        assertFalse(MotherMentorMotherChildRepository.getStateForChildBeforeVisit(db, "child", "started")
                .hasBreastfeedingHistory());
    }

    @Test
    public void mamaVisitReplaysNeverBreastfedThenKeepsNewBreastfeedingHistory() throws Exception {
        data.delete("event", "formSubmissionId = ?", new String[] {"episode-one"});
        enrollment("episode-one", "2025-03-01T08:00:00.000+03:00", "not_pregnant");
        link("child", dob);
        LocalDate sixWeeks = dob.plusDays(42);
        Map<String, String> never = MotherMentorMotherChildUtilsTest.child(dob);
        never.put(MAMA_BREASTFEEDING_STATUS, "never_breastfed");
        never.put(MAMA_HEI_TEST_DONE, "no");
        visit("never", "never", "mother", never, sixWeeks, 1, false);
        MotherMentorMotherChildRepository.upgrade(db);
        assertEquals("never_breastfed", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        assertEquals("", scalar("SELECT mama_tarehe_acha_kunyonyesha FROM ec_mothermentor_enrollment"));
        assertFalse(MotherMentorMotherChildRepository.getState(db, "mother", null).hasBreastfeedingHistory());
        assertEquals("never_breastfed", MotherMentorMotherChildRepository.getStateForChild(db, "child", null)
                .getBreastfeedingStatus());

        Map<String, String> started = new LinkedHashMap<>(never);
        started.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        visit("started", "started", "mother", started, sixWeeks.plusDays(1), 1, false);
        visit("stale", "stale", "mother", never, sixWeeks.plusDays(2), 1, false);
        MotherMentorMotherChildRepository.rebuild(db, "mother");
        assertEquals("breastfeeding", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        assertTrue(MotherMentorMotherChildRepository.getState(db, "mother", null).hasBreastfeedingHistory());
        assertFalse(MotherMentorMotherChildRepository.getStateBeforeMotherVisit(db, "mother", "never")
                .hasBreastfeedingHistory());
        assertTrue(MotherMentorMotherChildRepository.getStateBeforeMotherVisit(db, "mother", "stale")
                .hasBreastfeedingHistory());
    }

    @Test
    public void earlierEnrollmentRemainsBreastfeedingEvidenceAfterAnAmbiguousLegacyEnrollment() throws Exception {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid VALUES ('child', '', ?)",
                new Object[] {dob.toString()});
        for (String status : new String[] {"breast_feeding", "not_breast_feeding"}) {
            String day = dob.plusMonths("breast_feeding".equals(status) ? 2 : 4).toString();
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(BREASTFEEDING_STATUS, status);
            fields.put(CHILD_DOB, dob.toString());
            JSONObject event = new JSONObject().put("formSubmissionId", status).put("eventDate", day)
                    .put("version", 1).put("obs", observations(fields));
            ContentValues row = new ContentValues();
            row.put("baseEntityId", "child");
            row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID);
            row.put("eventDate", day);
            row.put("formSubmissionId", status);
            row.put("json", event.toString());
            data.insertOrThrow("event", null, row);
        }

        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository
                .getStateForChild(db, "child", null);
        assertTrue(state.hasBreastfeedingHistory());
        assertEquals("breastfeeding", state.getBreastfeedingStatus());
        assertNull(state.getWeaningDate());
    }

    @Test
    public void carriedWeaningDateFollowsTheCorrectedSource() throws Exception {
        link("child", dob);
        LocalDate day = dob.plusMonths(10);
        LocalDate original = dob.plusMonths(6);
        LocalDate corrected = original.plusDays(1);
        visit("child-first", "child-first", "child", childBreastfeeding(corrected), day, 1, false);
        Map<String, String> followUp = MotherMentorMotherChildUtilsTest.test(
                "nine_months", "negative", dob.plusMonths(9));
        followUp.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        followUp.put(MAMA_TAREHE_ACHA_KUNYONYESHA, original.toString());
        followUp.put(MAMA_CARRIED_WEANING_DATE, "true");
        visit("mother-next", "mother-next", "mother", followUp, day.plusDays(1), 1, false);

        assertEquals(corrected, MotherMentorMotherChildRepository.getState(db, "mother", null).getWeaningDate());
        data.delete("visits", "visit_id = ?", new String[] {"child-first"});
        assertNull(MotherMentorMotherChildRepository.getState(db, "mother", null).getWeaningDate());
    }

    @Test
    public void mtotoEidDoesNotAdoptAMamaKadiWeaningDate() throws Exception {
        link("child", dob);
        LocalDate weaning = dob.plusMonths(6);
        Map<String, String> motherAnswer = MotherMentorMotherChildUtilsTest.test(
                "nine_months", "negative", dob.plusMonths(9));
        motherAnswer.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        motherAnswer.put(MAMA_TAREHE_ACHA_KUNYONYESHA, weaning.toString());
        visit("mother", "mother", "mother", motherAnswer, dob.plusMonths(10), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository
                .getStateForChild(db, "child", null);
        assertEquals(weaning, state.getWeaningDate());
        assertNull(state.getChildEidWeaningDate());
        assertNull(state.getChildEidWeaningVisitId());
    }

    @Test
    public void mtotoEidCarriesTheFirstVisitDateAndUsesTheCorrectedOriginInLaterVisits() throws Exception {
        link("child", dob);
        LocalDate firstDay = dob.plusMonths(10);
        LocalDate original = dob.plusMonths(6);
        LocalDate corrected = dob.plusMonths(7);
        visit("first", "first", "child", childBreastfeeding(original), firstDay, 1, false);
        Map<String, String> copied = childBreastfeeding(original);
        copied.put(MTOTO_WEANING_CARRIED, "1");
        visit("second", "second", "child", copied, firstDay.plusDays(1), 1, false);
        visit("third", "third", "child", copied, firstDay.plusDays(2), 1, false);

        assertNull(MotherMentorMotherChildRepository.getStateForChildBeforeVisit(db, "child", "first")
                .getChildEidWeaningDate());
        data.delete("visits", "visit_id = ?", new String[] {"first"});
        visit("first", "first", "child", childBreastfeeding(corrected), firstDay, 2, false);
        for (String later : Arrays.asList("second", "third")) {
            MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository
                    .getStateForChildBeforeVisit(db, "child", later);
            assertEquals(corrected, state.getChildEidWeaningDate());
            assertEquals("first", state.getChildEidWeaningVisitId());
        }
        data.delete("visits", "visit_id = ?", new String[] {"first"});
        assertNull(MotherMentorMotherChildRepository.getStateForChild(db, "child", null).getChildEidWeaningDate());
    }

    @Test
    public void mtotoEidUsesTheLinkDateAndKeepsItWhenALaterOldClientRepeatsTheAnswer() throws Exception {
        link("child", dob);
        LocalDate weaning = dob.plusMonths(6);
        String linkedOn = dob.plusMonths(7).toString();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BREASTFEEDING_STATUS, "stop_breast_feeding");
        fields.put(WEANING_DATE, weaning.toString());
        fields.put(CHILD_DOB, dob.toString());
        JSONObject event = new JSONObject().put("formSubmissionId", "linked").put("eventDate", linkedOn)
                .put("version", 1).put("obs", observations(fields));
        ContentValues row = new ContentValues();
        row.put("baseEntityId", "child");
        row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID);
        row.put("eventDate", linkedOn);
        row.put("formSubmissionId", "linked");
        row.put("json", event.toString());
        data.insertOrThrow("event", null, row);
        visit("repeat", "repeat", "child", childBreastfeeding(dob.plusMonths(8)), dob.plusMonths(9), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository
                .getStateForChild(db, "child", null);
        assertEquals(weaning, state.getChildEidWeaningDate());
        assertNull(state.getChildEidWeaningVisitId());
    }

    @Test
    public void laterMotherAndChildAnswersShareChronologyAndBeforeEditContext() throws Exception {
        link("child", dob);
        LocalDate firstDay = dob.plusMonths(10);
        visit("child-first", "child-first", "child", childBreastfeeding(dob.plusMonths(6)), firstDay, 1, false);
        Map<String, String> motherAnswer = MotherMentorMotherChildUtilsTest.test("nine_months", "negative", dob.plusMonths(9));
        visit("mother-next", "mother-next", "mother", motherAnswer, firstDay.plusDays(1), 1, false);
        visit("child-last", "child-last", "child", childBreastfeeding(dob.plusMonths(8)), firstDay.plusDays(2), 1, false);
        assertEquals(dob.plusMonths(8), MotherMentorMotherChildRepository.getState(db, "mother", null).getWeaningDate());
        MotherMentorMotherChildRepository.State before = MotherMentorMotherChildRepository
                .getStateForChildBeforeVisit(db, "child", "child-last");
        assertEquals("breastfeeding", before.getBreastfeedingStatus());
        assertNull(before.getWeaningDate());
        data.delete("visits", "visit_id = ?", new String[] {"child-last"});
        assertEquals("breastfeeding", MotherMentorMotherChildRepository.getState(db, "mother", null).getBreastfeedingStatus());
        data.delete("visits", "visit_id = ?", new String[] {"mother-next"});
        assertEquals(dob.plusMonths(6), MotherMentorMotherChildRepository.getState(db, "mother", null).getWeaningDate());
    }

    @Test
    public void invalidOrInactiveChildBreastfeedingNeverErasesAnEarlierValidAnswer() throws Exception {
        link("child", dob);
        LocalDate day = dob.plusMonths(10);
        visit("valid", "valid", "child", childBreastfeeding(dob.plusMonths(6)), day, 1, false);
        visit("future-date", "future-date", "child", childBreastfeeding(day.plusDays(3)), day.plusDays(1), 1, false);
        Map<String, String> notFound = childBreastfeeding(null);
        notFound.put(FOLLOW_UP_STATUS, "not_found");
        notFound.put(MTOTO_NOT_FOUND_REASON, "other");
        notFound.put(MTOTO_NOT_FOUND_REASON_OTHER, "Not available");
        visit("not-found", "not-found", "child", notFound, day.plusDays(2), 1, false);
        assertEquals("not_breastfeeding", MotherMentorMotherChildRepository.getState(db, "mother", null).getBreastfeedingStatus());
        assertEquals(dob.plusMonths(6), MotherMentorMotherChildRepository.getState(db, "mother", null).getWeaningDate());
    }

    @Test
    public void aStaleReferralBranchDoesNotDiscardAValidClinicalBreastfeedingAnswer() throws Exception {
        link("child", dob);
        data.execSQL("ALTER TABLE ec_mothermentor_enroll_child_eid ADD COLUMN mtoto_rufaa_state TEXT");
        data.execSQL("UPDATE ec_mothermentor_enroll_child_eid SET mtoto_rufaa_state = 'referred'");
        LocalDate day = dob.plusMonths(10);
        Map<String, String> values = childBreastfeeding(dob.plusMonths(6));
        values.put(MTOTO_RUFAA_STATE_BEFORE, "none");
        values.put(MTOTO_REFERRAL_AGREEMENT, "refused");
        visit("offline-first", "offline-first", "child", values, day, 1, false);
        MotherMentorMotherChildRepository.upgrade(db);
        assertEquals("not_breastfeeding", scalar("SELECT mama_breastfeeding_status FROM ec_mothermentor_enrollment"));
        assertEquals("referred", scalar("SELECT mtoto_rufaa_state FROM ec_mothermentor_enroll_child_eid"));
    }

    @Test
    public void schema3ChildDeathWithAValidDateFeedsTheExistingMaternalTerminalState() throws Exception {
        link("child", dob);
        LocalDate died = dob.plusMonths(10);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "3");
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(MAMA_CHILD_STATUS, "deceased");
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, died.toString());
        visit("deceased", "deceased", "child", values, died.plusDays(2), 1, false);
        MotherMentorMotherChildRepository.State state = MotherMentorMotherChildRepository.getState(db, "mother", null);
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_DECEASED, state.getTerminalOutcome());
        assertEquals(died, state.getChildDeathDate());
        assertEquals(died.plusDays(2), state.getTerminalServiceDate());
    }

    @Test
    public void inactiveDeathAnswersOnANotFoundVisitDoNotDeclareTheChildDead() throws Exception {
        link("child", dob);
        Map<String, String> values = childBreastfeeding(null);
        values.put(FOLLOW_UP_STATUS, "not_found");
        values.put(MTOTO_NOT_FOUND_REASON, "other");
        values.put(MTOTO_NOT_FOUND_REASON_OTHER, "Away today");
        values.put(MAMA_CHILD_STATUS, "deceased");
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, dob.plusMonths(9).toString());
        visit("inactive-death", "inactive-death", "child", values, dob.plusMonths(10), 1, false);
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE,
                MotherMentorMotherChildRepository.getState(db, "mother", null).getTerminalOutcome());
    }

    private Map<String, String> childBreastfeeding(LocalDate cessation) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "3");
        values.put(FOLLOW_UP_STATUS, "found");
        values.put(MAMA_CHILD_STATUS, "alive");
        values.put(MAMA_BREASTFEEDING_STATUS, cessation == null ? "breastfeeding" : "not_breastfeeding");
        if (cessation != null) values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, cessation.toString());
        return values;
    }

    private Map<String, String> childResult(String result) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(FOLLOW_UP_STATUS, "found"); values.put(CHILD_EID_TEST_TYPE, "eighteen_months");
        values.put(CHILD_EID_FOLLOW_UP_SCHEMA, "2"); values.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        values.put(CHILD_EID_FACILITY_ARRIVAL_DATE, dob.plusMonths(18).toString());
        values.put(CHILD_EID_FINAL_HIV_TEST_DONE, "yes");
        values.put(CHILD_EID_FINAL_HIV_TEST_DATE, dob.plusMonths(18).toString());
        values.put(CHILD_EID_FINAL_HIV_RESULT, result);
        return values;
    }

    private void link(String child, LocalDate birth) {
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid VALUES (?, 'mother', ?)",
                new Object[] {child, birth.toString()});
    }

    private void enrollment(String id, String date) throws Exception {
        enrollment(id, date, "breast_feeding");
    }

    private void enrollment(String id, String date, String pregnancyStatus) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PREGNANCY_STATUS, pregnancyStatus);
        values.put(CHILD_DOB, dob.toString());
        JSONObject event = new JSONObject().put("formSubmissionId", id).put("eventDate", date)
                .put("version", 1).put("obs", observations(values));
        ContentValues row = new ContentValues(); row.put("baseEntityId", "mother");
        row.put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT); row.put("eventDate", date);
        row.put("formSubmissionId", id); row.put("json", event.toString()); data.insertOrThrow("event", null, row);
    }

    private void visit(String id, String source, String client, Map<String, String> values,
            LocalDate day, int version, boolean voided) throws Exception {
        values = new LinkedHashMap<>(values); values.put(MOTHERMENTOR_VISIT_DATE, day.toString());
        values.put(MAMA_ENROLLMENT_ID, "episode-one");
        JSONObject event = new JSONObject().put("formSubmissionId", source).put("eventDate", day.toString())
                .put("version", version).put("voided", voided).put("obs", observations(values));
        ContentValues row = new ContentValues(); row.put(VISIT_ID, id); row.put(BASE_ENTITY_ID, client);
        row.put(VISIT_TYPE, "mother".equals(client) ? Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES
                : MotherMentorFollowUpType.CHILD_EID.getEventType());
        row.put(FORM_SUBMISSION_ID, source); row.put(PROCESSED, 1); row.put(VISIT_DATE, 1);
        row.put(UPDATED_AT, 1); row.put(CREATED_AT, 1); row.put(VISIT_JSON, event.toString());
        data.insertOrThrow("visits", null, row);
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) result.put(new JSONObject()
                .put("fieldCode", entry.getKey()).put("values", new JSONArray().put(entry.getValue())));
        return result;
    }

    private String scalar(String sql) {
        try (Cursor cursor = data.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }
}
