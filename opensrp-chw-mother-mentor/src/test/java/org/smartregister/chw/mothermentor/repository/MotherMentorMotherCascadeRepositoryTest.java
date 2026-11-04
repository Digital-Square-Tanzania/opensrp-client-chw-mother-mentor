package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherCascadeRepositoryTest {
    private static final String IIT = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
    private static final String CHILD = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
    private static final String HOUSEHOLD = Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT;
    private static final String MOTHERS = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String LEDGER = Constants.TABLES.MOTHERMENTOR_MOTHER_CASCADE;
    private static final String DAY = "2026-10-20";
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;
    @Before
    public void setUp() {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE " + MOTHERS + " (base_entity_id TEXT, pregnancy_status TEXT, child_dob TEXT, mama_child_status TEXT, is_closed INTEGER, event_date TEXT, last_interacted_with INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT, dob TEXT, is_closed INTEGER, date_died TEXT, dod TEXT)");
        data.execSQL("CREATE TABLE " + IIT + " (base_entity_id TEXT, is_closed INTEGER, event_date TEXT, last_interacted_with INTEGER)");
        data.execSQL("CREATE TABLE " + CHILD + " (base_entity_id TEXT, mother_base_entity_id TEXT, child_dob TEXT, is_closed INTEGER, event_date TEXT, last_interacted_with INTEGER)");
        data.execSQL("CREATE TABLE " + HOUSEHOLD + " (base_entity_id TEXT, consent TEXT, is_closed INTEGER, event_date TEXT, last_interacted_with INTEGER,"
                + " kaya_status TEXT, kaya_tarehe_ya_kufungwa TEXT, kaya_sababu_ya_kufungwa TEXT, kaya_counter_nyumba_imefungwa INTEGER, kaya_counter_nyingine INTEGER, kaya_counter_haijapatikana INTEGER)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at INTEGER, created_at INTEGER, visit_json TEXT, pre_processed TEXT)");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), any(String[].class))).thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        when(db.rawQuery(anyString(), org.mockito.ArgumentMatchers.<String[]>isNull()))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), null));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        doAnswer(call -> { data.execSQL(call.getArgument(0), call.getArgument(1)); return null; }).when(db).execSQL(anyString(), any(Object[].class));
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(db.delete(anyString(), anyString(), any(String[].class)))
                .thenAnswer(call -> data.delete(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> { data.beginTransaction(); return null; }).when(db).beginTransaction();
        doAnswer(call -> { data.setTransactionSuccessful(); return null; }).when(db).setTransactionSuccessful();
        doAnswer(call -> { data.endTransaction(); return null; }).when(db).endTransaction();
        mother("mother", "pregnant", "", 1);
        MotherMentorMotherCascadeRepository.upgrade(db);
        MotherMentorMotherCascadeRepository.upgrade(db);
    }

    @After
    public void tearDown() { data.close(); }

    @Test
    public void deathAndBothTransfersCloseIitAndKeepFacilityMetadataWithoutChangingRawHistory() throws Exception {
        for (VisitOutcome outcome : new VisitOutcome[] {VisitOutcome.DECEASED, VisitOutcome.MOVED_AREA, VisitOutcome.MOVED_FACILITY}) {
            data.delete(IIT, null, null); data.delete(LEDGER, null, null);
            iit("2026-10-01", 0);
            MotherMentorMotherClosure exit = MotherMentorMotherClosure.active().advance(outcome, DAY, "", "New facility");
            MotherMentorMotherCascadeRepository.rebuild(db, "mother", exit);
            assertEquals("1", value(IIT, "mother", IS_CLOSED));
            JSONArray claims = new JSONArray(ledger(MAMA_CASCADE_CLAIMS));
            assertEquals(exit.getStatus(), claims.getJSONObject(0).getString(MAMA_STATUS));
            assertEquals(exit.getNewFacility(), claims.getJSONObject(0).getString(MAMA_KITUO_KIPYA));
            assertEquals(1, count("event"));
        }
    }

    @Test
    public void sameEpisodeCorrectionRestoresOnlyAnOwnedOpenIit() {
        iit("2026-10-01", 0);
        close(VisitOutcome.MOVED_AREA);
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("0", value(IIT, "mother", IS_CLOSED)); assertEquals(0, count(LEDGER));
        data.execSQL("UPDATE " + IIT + " SET is_closed=1");
        close(VisitOutcome.MOVED_AREA);
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(IIT, "mother", IS_CLOSED)); assertEquals(0, count(LEDGER));
    }

    @Test
    public void laterExternalRevisionCannotBeReopenedByCorrection() {
        iit("2026-10-01", 0); close(VisitOutcome.MOVED_AREA);
        data.execSQL("UPDATE " + IIT + " SET last_interacted_with=2");
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(IIT, "mother", IS_CLOSED));
        assertEquals("Historical ownership survives an external revision", 1, count(LEDGER));
    }

    @Test
    public void sharedDeathCannotBeReopenedByCorrection() {
        iit("2026-10-01", 0); close(VisitOutcome.MOVED_AREA);
        data.execSQL("UPDATE ec_family_member SET date_died='2026-10-20' WHERE base_entity_id='mother'");
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(IIT, "mother", IS_CLOSED));
    }

    @Test
    public void unknownTargetEpisodeStillClosesButCannotBeAutomaticallyReopened() {
        iit(null, 0); close(VisitOutcome.MOVED_FACILITY);
        assertEquals("1", value(IIT, "mother", IS_CLOSED));
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(IIT, "mother", IS_CLOSED)); assertEquals(0, count(LEDGER));
    }

    @Test
    public void aNewTargetUuidIsIndependentFromTheOldClosureEvenOnTheSameCalendarDay() throws Exception {
        iit("2026-10-01", 0);
        event("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-one", "2026-10-01T08:00:00+03:00");
        close(VisitOutcome.MOVED_AREA);
        event("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-two", "2026-10-20T17:00:00+03:00");
        data.execSQL("UPDATE " + IIT + " SET is_closed=0, event_date='2026-10-20'");
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("0", value(IIT, "mother", IS_CLOSED));
        assertEquals("Retain the served old target episode", 1, count(LEDGER));
    }

    @Test
    public void replayingTheOldClosedMotherCannotCloseHerNewIitOnRepeatedRebuilds() throws Exception {
        iit("2026-10-01", 0);
        event("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-one", "2026-10-01T08:00:00+03:00");
        close(VisitOutcome.MOVED_AREA);
        event("mother", Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT, "iit-two", "2026-10-20T17:00:00+03:00");
        data.execSQL("UPDATE " + IIT + " SET is_closed=0, event_date='2026-10-20'");
        for (int replay = 0; replay < 4; replay++) {
            close(VisitOutcome.MOVED_AREA);
            assertEquals("Old closure replay " + replay, "0", value(IIT, "mother", IS_CLOSED));
        }
        enrollment("mother", "new-mother-episode", "pregnant", "");
        close(VisitOutcome.MOVED_AREA);
        assertEquals("A new mother enrollment has independent closure ownership", "1",
                value(IIT, "mother", IS_CLOSED));
    }

    @Test
    public void oldMemberClaimsCannotCloseANewHouseholdEnrollmentOnLaterReplays() throws Exception {
        household("ndiyo"); mother("other", "none", "", 1); deceasedVisit("other");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
        event("family", Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT,
                "new-household", "2026-10-20T17:00:00+03:00");
        data.execSQL("UPDATE " + HOUSEHOLD + " SET is_closed=0, event_date='2026-10-20',"
                + " kaya_status='active', kaya_tarehe_ya_kufungwa='', kaya_sababu_ya_kufungwa=''");
        for (int replay = 0; replay < 4; replay++) {
            close(VisitOutcome.CHILD_DECEASED);
            assertEquals("Old member claims replay " + replay, "0", value(HOUSEHOLD, "family", IS_CLOSED));
        }
    }

    @Test
    public void newMotherEpisodeDoesNotReopenACompletedOldIitEnrollment() {
        iit("2026-10-01", 0); close(VisitOutcome.MOVED_AREA);
        enrollment("mother", "new-mother-episode", "pregnant", "");
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(IIT, "mother", IS_CLOSED));
    }

    @Test
    public void eachChildTerminalOutcomeClosesOnlyOneRealMatchingLinkedChild() throws Exception {
        data.execSQL("UPDATE " + MOTHERS + " SET child_dob='2026-05-01'");
        enrollment("mother", "mother-episode", "breast_feeding", "2026-05-01");
        child("child", "2026-05-01", true);
        for (VisitOutcome outcome : new VisitOutcome[] {VisitOutcome.CHILD_DECEASED, VisitOutcome.CHILD_CONFIRMED_POSITIVE, VisitOutcome.GRADUATED}) {
            data.execSQL("UPDATE " + CHILD + " SET is_closed=0"); data.delete(LEDGER, null, null);
            close(outcome);
            assertEquals("1", value(CHILD, "child", IS_CLOSED));
            assertEquals(MotherMentorMotherClosure.active().advance(outcome, DAY).getStatus(),
                    new JSONArray(ledger(MAMA_CASCADE_CLAIMS)).getJSONObject(0).getString(MAMA_STATUS));
            MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
            assertEquals("0", value(CHILD, "child", IS_CLOSED));
        }
    }

    @Test
    public void missingRealIdentityAndAmbiguousTwinsNeverCreateOrCloseAnAssumedChild() {
        enrollment("mother", "mother-episode", "breast_feeding", "2026-05-01");
        child("missing-person", "2026-05-01", false); close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(CHILD, "missing-person", IS_CLOSED));
        child("real-twin", "2026-05-01", true); close(VisitOutcome.CHILD_CONFIRMED_POSITIVE);
        assertEquals("0", value(CHILD, "real-twin", IS_CLOSED));
        assertEquals(0, count(LEDGER));
    }

    @Test
    public void householdDeathClosureRequiresConsentAndNoOtherEligibleMotherOrLivingChild() {
        household("ndiyo");
        mother("other", "bf", "", 0);
        close(VisitOutcome.DECEASED); assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + MOTHERS + " SET is_closed=1 WHERE base_entity_id='other'");
        data.execSQL("UPDATE " + MOTHERS + " SET child_dob='2026-05-01' WHERE base_entity_id='mother'");
        close(VisitOutcome.DECEASED); assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + MOTHERS + " SET child_dob='' WHERE base_entity_id='mother'");
        data.execSQL("UPDATE " + HOUSEHOLD + " SET consent='hapana'");
        close(VisitOutcome.DECEASED); assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + HOUSEHOLD + " SET consent='ndiyo'");
        close(VisitOutcome.DECEASED); assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
        assertEquals(MotherMentorHouseholdClosure.MOTHER_DECEASED, value(HOUSEHOLD, "family", KAYA_STATUS));
    }

    @Test
    public void stoppedBreastfeedingPastEighteenMonthsDoesNotBlockEitherDeathCascade() {
        mother("other", "breast_feeding", org.joda.time.LocalDate.now()
                .minusMonths(18).minusDays(1).toString(), 0);
        data.execSQL("ALTER TABLE " + MOTHERS + " ADD COLUMN " + MAMA_BREASTFEEDING_STATUS + " TEXT");
        data.execSQL("UPDATE " + MOTHERS + " SET " + MAMA_BREASTFEEDING_STATUS
                + "='not_breastfeeding' WHERE base_entity_id='other'");
        for (VisitOutcome outcome : new VisitOutcome[] {VisitOutcome.DECEASED, VisitOutcome.CHILD_DECEASED}) {
            data.delete(HOUSEHOLD, null, null);
            data.delete(LEDGER, null, null);
            household("ndiyo");
            close(outcome);
            assertEquals(outcome.name(), "1", value(HOUSEHOLD, "family", IS_CLOSED));
        }
    }

    @Test
    public void currentBreastfeedingProtectsHouseholdDespiteLegacyNotPregnantStatus() {
        household("ndiyo");
        mother("other", "none", org.joda.time.LocalDate.now().minusMonths(19).toString(), 0);
        data.execSQL("ALTER TABLE " + MOTHERS + " ADD COLUMN " + MAMA_BREASTFEEDING_STATUS + " TEXT");
        data.execSQL("UPDATE " + MOTHERS + " SET " + MAMA_BREASTFEEDING_STATUS
                + "='breastfeeding' WHERE base_entity_id='other'");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void stoppedBreastfeedingStillProtectsAChildThroughEighteenCalendarMonths() {
        household("ndiyo");
        mother("other", "none", org.joda.time.LocalDate.now().minusMonths(18).toString(), 0);
        data.execSQL("ALTER TABLE " + MOTHERS + " ADD COLUMN " + MAMA_BREASTFEEDING_STATUS + " TEXT");
        data.execSQL("UPDATE " + MOTHERS + " SET " + MAMA_BREASTFEEDING_STATUS
                + "='not_breastfeeding' WHERE base_entity_id='other'");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + MOTHERS + " SET child_dob=? WHERE base_entity_id='other'",
                new Object[] {org.joda.time.LocalDate.now().minusMonths(18).minusDays(1).toString()});
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void pregnancyProtectsHouseholdEvenAfterBreastfeedingStopped() {
        household("ndiyo");
        mother("other", "pregnant", org.joda.time.LocalDate.now().minusMonths(19).toString(), 0);
        data.execSQL("ALTER TABLE " + MOTHERS + " ADD COLUMN " + MAMA_BREASTFEEDING_STATUS + " TEXT");
        data.execSQL("UPDATE " + MOTHERS + " SET " + MAMA_BREASTFEEDING_STATUS
                + "='not_breastfeeding' WHERE base_entity_id='other'");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void absentOrEmptyBreastfeedingProjectionRetainsLegacyEligibility() {
        household("ndiyo");
        mother("other", "breast_feeding", org.joda.time.LocalDate.now().minusMonths(19).toString(), 0);
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("ALTER TABLE " + MOTHERS + " ADD COLUMN " + MAMA_BREASTFEEDING_STATUS + " TEXT");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + MOTHERS + " SET " + MAMA_BREASTFEEDING_STATUS
                + "='' WHERE base_entity_id='other'");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void householdEligibilityUsesCalendarAgeAndCanonicalPregnancyAliases() {
        household("ndiyo"); mother("other", "none", org.joda.time.LocalDate.now().minusMonths(18).toString(), 0);
        close(VisitOutcome.CHILD_DECEASED); assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE " + MOTHERS + " SET child_dob=? WHERE base_entity_id='other'",
                new Object[] {org.joda.time.LocalDate.now().minusMonths(18).minusDays(1).toString()});
        close(VisitOutcome.CHILD_DECEASED); assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void multipleMotherClaimsKeepHouseholdClosedUntilTheFinalSameEpisodeCorrection() {
        household("ndiyo"); mother("other", "none", "", 1);
        deceasedVisit("other");
        close(VisitOutcome.CHILD_DECEASED);
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
        MotherMentorMotherCascadeRepository.rebuild(db, "other", MotherMentorMotherClosure.active());
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        assertEquals(MotherMentorHouseholdClosure.ACTIVE, value(HOUSEHOLD, "family", KAYA_STATUS));
    }

    @Test
    public void ownHouseholdVisitClosureSurvivesRemovalOfTheMemberCause() {
        household("ndiyo"); close(VisitOutcome.CHILD_DECEASED);
        service("family", "household-moved", HOUSEHOLD_FOLLOW_UP_SCHEMA, "2",
                HOUSEHOLD_ENROLLMENT_ID, MotherMentorHouseholdClosureRepository.currentEnrollmentId(db, "family"),
                HOUSEHOLD_VISIT_TYPE, "repeat", HOUSEHOLD_FOLLOW_UP_STATUS, "not_found",
                HOUSEHOLD_NOT_FOUND_REASON, "moved", MOTHERMENTOR_VISIT_DATE, "19-10-2026");
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", MotherMentorMotherClosure.active());
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
        assertEquals(MotherMentorHouseholdClosure.MOVED, value(HOUSEHOLD, "family", KAYA_STATUS));
    }

    @Test
    public void anExistingLinkedLivingChildKeepsHouseholdOpenWhenMotherDobIsMissing() {
        household("ndiyo"); child("living-child", "2026-05-01", true);
        close(VisitOutcome.DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
        data.execSQL("UPDATE ec_family_member SET date_died='2026-10-19' WHERE base_entity_id='living-child'");
        close(VisitOutcome.DECEASED);
        assertEquals("1", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void householdEligibilityMatchesCaseInsensitiveRegisterStatusAliases() {
        household("ndiyo"); mother("other", " BF ", "", 0);
        close(VisitOutcome.DECEASED);
        assertEquals("0", value(HOUSEHOLD, "family", IS_CLOSED));
    }

    @Test
    public void ownershipAndTargetClosureRollBackTogetherIfTheLedgerWriteFails() {
        iit("2026-10-01", 0);
        doAnswer(call -> { throw new IllegalStateException("Simulated ledger write failure"); })
                .when(db).execSQL(startsWith("INSERT OR REPLACE INTO " + LEDGER), any(Object[].class));
        try {
            close(VisitOutcome.MOVED_AREA);
            fail("The failed ownership write must abort the closure projection");
        } catch (IllegalStateException expected) {
            assertEquals("Simulated ledger write failure", expected.getMessage());
        }
        assertEquals("0", value(IIT, "mother", IS_CLOSED));
        assertEquals(0, count(LEDGER));
    }

    private void close(VisitOutcome outcome) {
        MotherMentorMotherClosure exit = exit(outcome);
        MotherMentorMotherCascadeRepository.rebuild(db, "mother", exit);
    }
    private MotherMentorMotherClosure exit(VisitOutcome outcome) { return MotherMentorMotherClosure.active().advance(outcome, DAY); }
    private void mother(String id, String pregnancy, String dob, int closed) {
        data.execSQL("INSERT INTO " + MOTHERS + " VALUES (?,?,?,'',?,'2026-10-01T08:00:00+03:00',1)", new Object[] {id, pregnancy, dob, closed});
        data.execSQL("INSERT INTO ec_family_member VALUES (?, 'family', '1990-01-01',0,'','')", new Object[] {id});
        enrollment(id, id + "-episode", pregnancy, dob);
    }
    private void enrollment(String id, String episode, String pregnancy, String dob) {
        try {
            data.delete("event", "baseEntityId=? AND eventType=?", new String[] {id, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT});
            JSONObject event = new JSONObject().put("formSubmissionId", episode)
                    .put("eventDate", "2026-10-01T08:00:00+03:00")
                    .put("obs", observations(PREGNANCY_STATUS, pregnancy, CHILD_DOB, dob));
            data.execSQL("INSERT INTO event VALUES (?,?,?,?)", new Object[] {id,
                    Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, episode, event.toString()});
            data.execSQL("UPDATE " + MOTHERS + " SET child_dob=?, pregnancy_status=? WHERE base_entity_id=?", new Object[] {dob, pregnancy, id});
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private void deceasedVisit(String id) {
        service(id, id + "-death", MAMA_VISIT_SCHEMA, "2", MAMA_ENROLLMENT_ID, id + "-episode",
                ATTENDANCE_TYPE, "repeat", FOLLOW_UP_STATUS, "not_found", MAMA_NOT_FOUND_REASON,
                "deceased", MOTHERMENTOR_VISIT_DATE, "20-10-2026");
    }
    private void service(String id, String source, String... values) {
        try {
            JSONObject event = new JSONObject().put("formSubmissionId", source)
                    .put("eventDate", "2026-10-20T08:00:00+03:00").put("version", 1)
                    .put("obs", observations(values));
            data.execSQL("INSERT INTO visits VALUES (?,?,?,?,1,1,1,1,?,NULL)", new Object[] {source, id,
                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, source, event.toString()});
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private JSONArray observations(String... values) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; i < values.length; i += 2) result.put(new JSONObject()
                .put("fieldCode", values[i]).put("values", new JSONArray().put(values[i + 1])));
        return result;
    }
    private void iit(String day, int closed) { data.execSQL("INSERT INTO " + IIT + " VALUES ('mother',?,?,1)", new Object[] {closed, day}); }
    private void child(String id, String dob, boolean real) {
        data.execSQL("INSERT INTO " + CHILD + " VALUES (?, 'mother',?,0,'2026-10-01',1)", new Object[] {id, dob});
        if (real) data.execSQL("INSERT INTO ec_family_member VALUES (?, 'family',?,0,'','')", new Object[] {id, dob});
    }
    private void household(String consent) { data.execSQL("INSERT INTO " + HOUSEHOLD + " VALUES ('family',?,0,'2026-10-01',1,'active','','',0,0,0)", new Object[] {consent}); }
    private void event(String id, String type, String source, String date) throws Exception {
        JSONObject event = new JSONObject().put("formSubmissionId", source).put("eventDate", date).put("version",1);
        data.execSQL("INSERT INTO event VALUES (?,?,?,?)", new Object[] {id, type, source, event.toString()});
    }
    private String value(String table, String id, String column) {
        try (Cursor rows = data.rawQuery("SELECT " + column + " FROM " + table + " WHERE base_entity_id=?", new String[] {id})) {
            assertTrue(rows.moveToFirst()); return rows.getString(0);
        }
    }
    private String ledger(String column) {
        try (Cursor rows = data.rawQuery("SELECT " + column + " FROM " + LEDGER, null)) { assertTrue(rows.moveToFirst()); return rows.getString(0); }
    }
    private int count(String table) {
        try (Cursor rows = data.rawQuery("SELECT COUNT(*) FROM " + table, null)) { assertTrue(rows.moveToFirst()); return rows.getInt(0); }
    }
}
