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
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
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
        assertEquals(15, MotherMentorMotherChildRepository.COLUMNS.size());
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
        assertTrue(MotherMentorMotherResultEvent.isComplete(pending.get(0), confirmation, confirmationDay));
        resultEvent("confirm", "first-source", day.plusDays(3), null, "awaiting_confirm", "negative");
        pending = MotherMentorMotherResultEvent.pending(db, "mother", day.plusDays(3));
        assertEquals(1, pending.size());
        assertEquals(Stage.SIX_WEEKS, pending.get(0).getStage());
        assertEquals("1", scalar("SELECT COUNT(*) FROM visits"));
        assertFalse(MotherMentorMotherChildRepository.getState(db, "mother", null).hasConfirmedPositive());
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
