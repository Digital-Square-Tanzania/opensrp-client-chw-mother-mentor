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
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherClosureRepositoryTest {
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL(
                "CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT, date_died TEXT)");
        data.execSQL(
                "CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                + " form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at INTEGER,"
                + " created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','pregnant','',"
                + "'2026-10-01T08:00:00.000+03:00','2026-10-01',1,0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household','')");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> {
            data.execSQL(call.getArgument(0));
            return null;
        })
                .when(db)
                .execSQL(anyString());
        doAnswer(call -> {
            data.execSQL(call.getArgument(0), call.getArgument(1));
            return null;
        })
                .when(db)
                .execSQL(anyString(), any(Object[].class));
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                                call.getArgument(2), call.getArgument(3)));
        when(db.insert(anyString(), nullable(String.class), any(ContentValues.class)))
                .thenAnswer(call -> data.insert(
                                call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.delete(anyString(), anyString(), any(String[].class)))
                .thenAnswer(call -> data.delete(
                                call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> {
            data.beginTransaction();
            return null;
        })
                .when(db)
                .beginTransaction();
        doAnswer(call -> {
            data.setTransactionSuccessful();
            return null;
        })
                .when(db)
                .setTransactionSuccessful();
        doAnswer(call -> {
            data.endTransaction();
            return null;
        })
                .when(db)
                .endTransaction();
        enrollment("one", "2026-10-01T08:00:00.000+03:00");
    }

    @After
    public void tearDown() {
        data.close();
    }

    @Test
    public void otherCounterCountsUniqueFinalizedConsecutiveVisitsAndResetsOnFound()
            throws Exception {
        visit("one", "one-source", "one", "2026-10-02", 1, true, other());
        visit("one-replay", "one-source", "one", "2026-10-02", 2, true, other());
        visit("two", "two-source", "one", "2026-10-03", 1, true, other());
        visit("draft", "draft", "one", "2026-10-04", 1, false, other());
        assertEquals(2, state().getOtherCount());
        assertFalse(state().isClosed());
        visit("found", "found", "one", "2026-10-04", 1, true, found());
        assertEquals(0, state().getOtherCount());
        visit("three", "three", "one", "2026-10-05", 1, true, other());
        visit("four", "four", "one", "2026-10-06", 1, true, other());
        visit("five", "five", "one", "2026-10-07", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("not_found_other_3", column(MAMA_STATUS));
        assertEquals("2026-10-07", column(MAMA_TAREHE_YA_KUFUNGWA));
        assertEquals("3", column(MAMA_COUNTER_NYINGINE));
        assertEquals("1", column(IS_CLOSED));
        assertEquals("away", column(MAMA_SABABU_NYINGINE_TEXT));
        assertEquals(8, count("visits"));
    }

    @Test
    public void correctionVoidDeleteAndEditingExclusionReopenOnlyOwnedClosure() throws Exception {
        visit("a", "a", "one", "2026-10-02", 1, true, other());
        visit("b", "b", "one", "2026-10-03", 1, true, other());
        visit("c", "c", "one", "2026-10-04", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        assertTrue(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
        assertFalse(MotherMentorMotherClosureRepository.getState(db, "mother", "c").isClosed());
        visit("c-fixed", "c", "one", "2026-10-04", 2, true, found());
        MotherMentorMotherClosureRepository.rebuild(db, "mother");
        assertEquals("active", column(MAMA_STATUS));
        assertEquals("0", column(IS_CLOSED));
        data.delete("visits", "form_submission_id='c'", null);
        MotherMentorMotherClosureRepository.rebuild(db, "mother");
        assertEquals("2", column(MAMA_COUNTER_NYINGINE));
    }

    @Test
    public void previewDoesNotSaveOrLatchTheThirdStrikeAndUsesClinicalOrder() throws Exception {
        visit("a", "a", "one", "2026-10-02", 1, true, other());
        visit("b", "b", "one", "2026-10-03", 1, true, other());
        assertTrue(MotherMentorMotherClosureRepository
                        .preview(db, "mother", null, other(), new LocalDate(2026, 10, 4))
                        .isClosed());
        assertFalse(state().isClosed());
        assertEquals(2, count("visits"));
        visit("found", "found", "one", "2026-10-04", 1, true, found());
        assertFalse(MotherMentorMotherClosureRepository
                        .preview(db, "mother", null, other(), new LocalDate(2026, 10, 5))
                        .isClosed());
    }

    @Test
    public void newEnrollmentResetsCountersWithoutDeletingHistoryOrReusingSameDayOldUuid()
            throws Exception {
        visit("a", "a", "one", "2026-10-02", 1, true, other());
        visit("b", "b", "one", "2026-10-03", 1, true, other());
        visit("c", "c", "one", "2026-10-04", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        enrollment("two", "2026-10-04T12:00:00.000+03:00");
        data.execSQL(
                "UPDATE ec_mothermentor_enrollment SET event_date='2026-10-04T12:00:00.000+03:00',is_closed=0");
        MotherMentorMotherClosureRepository.rebuild(db, "mother");
        assertEquals("active", column(MAMA_STATUS));
        assertEquals("0", column(MAMA_COUNTER_NYINGINE));
        assertEquals(3, count("visits"));
    }

    @Test
    public void migrationIsIdempotentAndPreservesAnUnrelatedExistingClosure() {
        data.execSQL("UPDATE ec_mothermentor_enrollment SET is_closed=1");
        MotherMentorMotherClosureRepository.upgrade(db);
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("1", column(IS_CLOSED));
        assertEquals("closed", column(MAMA_STATUS));
        assertTrue(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void sharedDeathCannotBeUndoneByDeletingModuleVisitsOrReEnrollment() throws Exception {
        data.execSQL("UPDATE ec_family_member SET date_died='2026-10-03'");
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("deceased", column(MAMA_STATUS));
        enrollment("two", "2026-10-04T12:00:00.000+03:00");
        data.execSQL(
                "UPDATE ec_mothermentor_enrollment SET event_date='2026-10-04T12:00:00.000+03:00',is_closed=0");
        MotherMentorMotherClosureRepository.rebuild(db, "mother");
        assertEquals("1", column(IS_CLOSED));
        assertEquals("deceased", column(MAMA_STATUS));
    }

    @Test
    public void canonicalSharedDodRemainsClosedEvenWhenItsDateCannotBeParsed() {
        data.execSQL("ALTER TABLE ec_family_member ADD COLUMN dod TEXT");
        data.execSQL("UPDATE ec_family_member SET dod='recorded-deceased'");
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("deceased", column(MAMA_STATUS));
        assertEquals("", column(MAMA_TAREHE_YA_KUFUNGWA));
        assertTrue(MotherMentorMotherClosureRepository.isClosed(db, "mother"));
    }

    @Test
    public void newVisitIgnoresInactiveStaleClinicalAnswersAndContinues() {
        Map<String, String> values = found();
        values.put(ATTENDANCE_TYPE, "new");
        values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        values.put(VISIT_PREGNANCY_STATUS, "not_pregnant");
        values.put(MAMA_PREGNANCY_OUTCOME, "pregnancy_loss");
        values.put(MAMA_PREGNANCY_LOSS_DATE, "2026-10-04");
        assertEquals(VisitOutcome.CONTINUE,
                MotherMentorMotherClosureRepository.classify(values, new LocalDate(2026, 10, 5)));
    }

    @Test
    public void motherAndLinkedClosureRollBackTogetherOnWriteFailure() throws Exception {
        String iit = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
        data.execSQL(
                "CREATE TABLE " + iit + " (base_entity_id TEXT,event_date TEXT,is_closed INTEGER)");
        data.execSQL("INSERT INTO " + iit + " VALUES ('mother','2026-10-01',0)");
        MotherMentorMotherClosureRepository.upgrade(db);
        Map<String, String> moved = other();
        moved.put(MAMA_NOT_FOUND_REASON, "moved_area");
        visit("transfer", "transfer", "one", "2026-10-05", 1, true, moved);
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> {
                    if (iit.equals(call.getArgument(0)))
                        throw new IllegalStateException("simulated target write failure");
                    return data.update(call.getArgument(0), call.getArgument(1),
                            call.getArgument(2), call.getArgument(3));
                });
        try {
            MotherMentorMotherClosureRepository.rebuild(db, "mother");
            fail("A failed cascade must propagate without committing a partial mother closure");
        } catch (IllegalStateException expected) {
            assertEquals("simulated target write failure", expected.getMessage());
        }
        assertEquals("active", column(MAMA_STATUS));
        assertEquals("0", column(IS_CLOSED));
        assertEquals(1, count("visits"));
        assertFalse(data.inTransaction());
    }

    @Test
    public void invalidFutureOrDifferentEpisodeDoesNotAdvanceCounterOrClose() throws Exception {
        Map<String, String> missing = other();
        missing.put(MAMA_SABABU_NYINGINE_TEXT, "");
        visit("invalid", "invalid", "one", "2026-10-02", 1, true, missing);
        visit("future", "future", "one", "2099-01-01", 1, true, other());
        visit("old", "old", "old-episode", "2026-10-03", 1, true, other());
        assertEquals(0, state().getOtherCount());
        assertFalse(state().isClosed());
    }

    @Test
    public void immediateNotFoundTransfersKeepFacilityAndNeedRequiredAnswers() {
        for (String reason : new String[] {"deceased", "moved_area", "moved_facility"}) {
            Map<String, String> values = other();
            values.put(MAMA_NOT_FOUND_REASON, reason);
            values.put(MAMA_KITUO_KIPYA, "Kibiti");
            assertEquals(reason,
                    MotherMentorMotherClosure.active()
                            .advance(MotherMentorMotherClosureRepository.classify(
                                                values, new LocalDate(2026, 10, 5)),
                                    "2026-10-05")
                            .getStatus());
        }
        Map<String, String> missing = other();
        missing.put(MAMA_NOT_FOUND_REASON, "moved_facility");
        assertNull(
                MotherMentorMotherClosureRepository.classify(missing, new LocalDate(2026, 10, 5)));
    }

    @Test
    public void clinicalExitRulesRequireValidatedPregnancyAndChildEvidence() {
        LocalDate day = new LocalDate(2026, 10, 5);
        Map<String, String> values = found();
        values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        values.put(VISIT_PREGNANCY_STATUS, "not_pregnant");
        values.put(MAMA_PREGNANCY_OUTCOME, "pregnancy_loss");
        values.put(MAMA_PREGNANCY_LOSS_DATE, "2026-10-04");
        assertEquals(VisitOutcome.PREGNANCY_LOSS,
                MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_PREGNANCY_OUTCOME, "stillbirth");
        values.put(MAMA_TAREHE_KUJIFUNGUA, "2026-10-04");
        assertEquals(
                VisitOutcome.STILLBIRTH, MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_PREGNANCY_OUTCOME, "other");
        assertNull(MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_PREGNANCY_OUTCOME_OTHER, "clinical reason");
        assertEquals(VisitOutcome.PREGNANCY_ENDED_OTHER,
                MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_PREGNANCY_OUTCOME, "delivered");
        values.put(MAMA_CHILD_STATUS, "deceased");
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, "2026-10-05");
        assertEquals(VisitOutcome.CHILD_DECEASED,
                MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, "2026-10-03");
        assertNull(MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_CHILD_STATUS, "alive");
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        values.put(MAMA_TAREHE_KUJIFUNGUA, "2026-08-01");
        values.put(MAMA_HEI_TEST_DONE, "yes");
        values.put(MAMA_HEI_TEST_TYPE, "six_weeks");
        values.put(MAMA_HEI_TEST_DATE, "2026-10-05");
        values.put(MAMA_HEI_TEST_RESULT, "positive");
        values.put(HEI_CHANYA_IMETHIBITISHWA, "no");
        assertEquals(
                VisitOutcome.CONTINUE, MotherMentorMotherClosureRepository.classify(values, day));
        values.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        assertEquals(VisitOutcome.CHILD_CONFIRMED_POSITIVE,
                MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_HEI_TEST_RESULT, "negative");
        assertEquals(
                VisitOutcome.CONTINUE, MotherMentorMotherClosureRepository.classify(values, day));
        values.put(MAMA_TAREHE_KUJIFUNGUA, "2025-04-01");
        values.put(MAMA_HEI_TEST_TYPE, "eighteen_months");
        assertEquals(
                VisitOutcome.GRADUATED, MotherMentorMotherClosureRepository.classify(values, day));
    }

    @Test
    public void legacyFacilityTransferUsesTheRetainedOriginalFormCode() {
        Map<String, String> values = new HashMap<>();
        values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "transferred_to_another_facility");
        assertEquals(VisitOutcome.MOVED_FACILITY,
                MotherMentorMotherClosureRepository.classify(values, new LocalDate(2026, 10, 5)));
    }

    @Test
    public void sameClinicalDayPreservesCanonicalEventOrderBeforeVisitId() throws Exception {
        visit("a-other", "a-other", "one", "2026-10-05", 1, true, other());
        visit("z-found", "z-found", "one", "2026-10-05", 1, true, found());
        long base = new LocalDate(2026, 10, 5).toDate().getTime();
        data.execSQL("UPDATE visits SET visit_date=? WHERE visit_id='a-other'",
                new Object[] {base + 1000});
        data.execSQL(
                "UPDATE visits SET visit_date=? WHERE visit_id='z-found'", new Object[] {base});
        assertEquals(1, state().getOtherCount());
    }

    @Test
    public void staleFollowUpChecksAreLimitedToIitAndChildEnrollments() {
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enroll_it (base_entity_id TEXT,is_closed INTEGER)");
        data.execSQL("INSERT INTO ec_mothermentor_enroll_it VALUES ('mother',0)");
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enroll_child_eid (base_entity_id TEXT,is_closed INTEGER)");
        data.execSQL("INSERT INTO ec_mothermentor_enroll_child_eid VALUES ('child',1)");
        assertFalse(MotherMentorMotherClosureRepository.isFollowUpEnrollmentClosed(db, "mother",
                org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.MOTHER_IIT));
        assertTrue(MotherMentorMotherClosureRepository.isFollowUpEnrollmentClosed(db, "child",
                org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.CHILD_EID));
        assertFalse(MotherMentorMotherClosureRepository.isFollowUpEnrollmentClosed(db, "child",
                org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.PARTNER));
        data.execSQL("UPDATE ec_mothermentor_enroll_it SET is_closed=1");
        assertTrue(MotherMentorMotherClosureRepository.isFollowUpEnrollmentClosed(db, "mother",
                org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType.MOTHER_IIT));
    }

    private MotherMentorMotherClosure state() {
        return MotherMentorMotherClosureRepository.getState(db, "mother", null);
    }
    private static Map<String, String> other() {
        Map<String, String> v = new HashMap<>();
        v.put(MAMA_VISIT_SCHEMA, "2");
        v.put(ATTENDANCE_TYPE, "repeat");
        v.put(FOLLOW_UP_STATUS, "not_found");
        v.put(MAMA_NOT_FOUND_REASON, "other");
        v.put(MAMA_SABABU_NYINGINE_TEXT, "away");
        return v;
    }
    private static Map<String, String> found() {
        Map<String, String> v = other();
        v.put(FOLLOW_UP_STATUS, "found");
        v.put(VISIT_PREGNANCY_STATUS, "pregnant");
        return v;
    }
    private void enrollment(String source, String date) throws Exception {
        JSONObject event = new JSONObject()
                .put("formSubmissionId", source)
                .put("eventDate", date)
                .put("obs",
                                            obs(java.util.Collections.singletonMap(
                                                    PREGNANCY_STATUS, "pregnant")));
        data.execSQL("INSERT INTO event VALUES (?,?,?,?,?)",
                new Object[] {"mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, date, source,
                        event.toString()});
    }
    private void visit(String id, String source, String episode, String date, int version,
            boolean processed, Map<String, String> values) throws Exception {
        Map<String, String> all = new HashMap<>(values);
        all.put(MAMA_ENROLLMENT_ID, episode);
        all.put(MOTHERMENTOR_VISIT_DATE, date);
        JSONObject event = new JSONObject()
                .put("formSubmissionId", source)
                .put("eventDate", date + "T14:00:00.000+03:00")
                .put("version", version)
                .put("obs", obs(all));
        data.execSQL("INSERT INTO visits VALUES (?,?,?,?,?,?,?,?,?,?)",
                new Object[] {id, "mother", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, source,
                        processed ? 1 : 0, new LocalDate(date).toDate().getTime(), version, version,
                        event.toString(), ""});
    }
    private static JSONArray obs(Map<String, String> values) throws Exception {
        JSONArray obs = new JSONArray();
        for (Map.Entry<String, String> e : values.entrySet()) {
            obs.put(new JSONObject()
                            .put("fieldCode", e.getKey())
                            .put("values", new JSONArray().put(e.getValue())));
        }
        return obs;
    }
    private String column(String key) {
        try (Cursor c = data.rawQuery("SELECT " + key + " FROM ec_mothermentor_enrollment", null)) {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }
    private int count(String table) {
        try (Cursor c = data.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        }
    }
}
