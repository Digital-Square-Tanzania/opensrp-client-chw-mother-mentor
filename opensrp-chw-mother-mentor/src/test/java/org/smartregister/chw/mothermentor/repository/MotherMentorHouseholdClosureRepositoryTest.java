package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes the real migration and replay SQL through a SQLCipher-shaped database adapter. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdClosureRepositoryTest {
    private static final String HOUSEHOLDS = Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT;
    private static final String HOUSEHOLD = "household";
    private static final String ENROLLMENT = "enrollment-one";
    private static final String ENROLLED_AT = "2026-10-01T08:00:00.000+03:00";
    private MotherMentorLibrary previousLibrary;
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase database;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        when(library.getSourceDateFormat()).thenReturn("dd-MM-yyyy");
        when(library.getSaveDateFormat()).thenReturn("dd-MM-yyyy");
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        data = SQLiteDatabase.create(null);
        database = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(database.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        when(database.insert(anyString(), any(), any(ContentValues.class)))
                .thenAnswer(call -> data.insert(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(database.delete(anyString(), anyString(), any(String[].class)))
                .thenAnswer(call -> data.delete(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(database.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; })
                .when(database).execSQL(anyString());
        when(database.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> { data.beginTransaction(); return null; }).when(database).beginTransaction();
        doAnswer(call -> { data.setTransactionSuccessful(); return null; })
                .when(database).setTransactionSuccessful();
        doAnswer(call -> { data.endTransaction(); return null; }).when(database).endTransaction();
        create(HOUSEHOLDS, BASE_ENTITY_ID, HOUSEHOLD_ID, CONSENT, ENROLLMENT_DATE,
                EVENT_DATE, LAST_INTERACTED_WITH, IS_CLOSED);
        create("event", EVENT_BASE_ENTITY_ID, EVENT_TYPE, EVENT_DATE_TIMESTAMP,
                EVENT_FORM_SUBMISSION_ID, EVENT_JSON);
        VisitRepository.createTable(database);
        database.execSQL(VisitRepository.ADD_VISIT_GROUP_COLUMN);
        VisitDetailsRepository.createTable(database);
        insert(HOUSEHOLDS, map(BASE_ENTITY_ID, HOUSEHOLD, HOUSEHOLD_ID, HOUSEHOLD,
                CONSENT, "ndiyo", ENROLLMENT_DATE, "2026-10-01", EVENT_DATE, ENROLLED_AT,
                LAST_INTERACTED_WITH, "1", IS_CLOSED, "0"));
        enrollment(ENROLLMENT, ENROLLED_AT, 1);
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
    }

    @Test
    public void upgradeAddsOnlySixSummaryColumnsAndBackfillsIdempotently() throws Exception {
        assertEquals(7, columns(HOUSEHOLDS));
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals(13, columns(HOUSEHOLDS));
        assertEquals("INTEGER", columnType(KAYA_COUNTER_NYUMBA_IMEFUNGWA));
        assertEquals("active", stored(KAYA_STATUS));
        assertEquals("1", stored(KAYA_COUNTER_NYUMBA_IMEFUNGWA));
        assertEquals("1", stored(KAYA_COUNTER_HAIJAPATIKANA));
        assertEquals("0", stored(IS_CLOSED));
        assertEquals(6, MotherMentorHouseholdClosureRepository.CLOSURE_COLUMNS.size());
    }

    @Test
    public void movedClosesImmediatelyWithoutRewritingTheSourceOrDeletingHistory() throws Exception {
        visit("moved", "form-moved", "02-10-2026", 1, missing("moved"));
        String original = scalar("SELECT visit_json FROM visits");
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals("moved", state().getStatus());
        assertEquals("2026-10-02", stored(KAYA_TAREHE_YA_KUFUNGWA));
        assertEquals("moved", stored(KAYA_SABABU_YA_KUFUNGWA));
        assertEquals("1", stored(IS_CLOSED));
        assertEquals(original, scalar("SELECT visit_json FROM visits"));
        assertEquals("1", scalar("SELECT count(*) FROM visits"));
    }

    @Test
    public void matchingReasonsCloseSpecificallyWhileMixedReasonsCloseAsLtfu() throws Exception {
        for (int day = 2; day <= 4; day++) {
            visit("visit-" + day, "form-" + day, "0" + day + "-10-2026", 1, missing("house_closed"));
        }
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals("house_closed_3", state().getStatus());
        assertEquals(3, state().getHouseClosedCount());
        updateVisit("visit-3", "form-3", "03-10-2026", 2, missing("other"));
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertEquals("lost_to_follow_up", state().getStatus());
        assertEquals(1, state().getHouseClosedCount());
        assertEquals(0, state().getOtherCount());
        assertEquals(3, state().getNotFoundCount());
    }

    @Test
    public void sourceRevisionsDeduplicateBeforeValidationAndCorrectedFoundReopens() throws Exception {
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        visit("two", "form-two", "03-10-2026", 1, missing("house_closed"));
        visit("two-copy", "form-two", "03-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals(2, state().getHouseClosedCount());
        visit("three", "form-three", "04-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertTrue(state().isClosed());
        updateVisit("two", "form-two", "03-10-2026", 2, found());
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertFalse(state().isClosed());
        assertEquals(1, state().getHouseClosedCount());
        assertEquals("0", stored(IS_CLOSED));
        assertEquals("", stored(KAYA_TAREHE_YA_KUFUNGWA));
        assertEquals("4", scalar("SELECT count(*) FROM visits"));
        Map<String, String> invalid = missing("other");
        invalid.remove(HOUSEHOLD_NOT_FOUND_REASON_OTHER);
        updateVisit("two", "form-two", "03-10-2026", 3, invalid);
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertEquals(2, state().getHouseClosedCount());
        assertFalse(state().isClosed());
    }

    @Test
    public void clinicalChronologyControlsTheWorkedExampleRegardlessOfImportOrder() throws Exception {
        visit("six", "form-six", "06-10-2026", 1, missing("house_closed"));
        visit("two", "form-two", "02-10-2026", 1, missing("house_closed"));
        visit("four", "form-four", "04-10-2026", 1, missing("house_closed"));
        visit("one", "form-one", "01-10-2026", 1, missing("house_closed"));
        visit("three", "form-three", "03-10-2026", 1, found());
        visit("five", "form-five", "05-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals("house_closed_3", state().getStatus());
        assertEquals("2026-10-06", state().getClosedDate());
        assertEquals(3, state().getNotFoundCount());
    }

    @Test
    public void editingExcludesEveryAliasOfTheCurrentForm() throws Exception {
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        visit("two", "form-two", "03-10-2026", 1, missing("house_closed"));
        visit("three", "form-three", "04-10-2026", 1, missing("house_closed"));
        visit("three-copy", "form-three", "04-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertTrue(state().isClosed());
        MotherMentorHouseholdClosure edited = MotherMentorHouseholdClosureRepository
                .getState(database, HOUSEHOLD, "three-copy");
        assertFalse(edited.isClosed());
        assertEquals(2, edited.getHouseClosedCount());
        assertEquals(2, MotherMentorHouseholdClosureRepository
                .getState(database, HOUSEHOLD, "form-three").getHouseClosedCount());
    }

    @Test
    public void reEnrolmentStartsAnotherEpisodeEvenOnTheSameCalendarDate() throws Exception {
        visit("old-moved", "old-form", "01-10-2026", 1, missing("moved"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertTrue(state().isClosed());
        String later = "2026-10-01T15:00:00.000+03:00";
        enrollment("enrollment-two", later, 2);
        changeEnrollment(map(EVENT_DATE, later, LAST_INTERACTED_WITH, "2"));
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertEquals("enrollment-two", MotherMentorHouseholdClosureRepository
                .currentEnrollmentId(database, HOUSEHOLD));
        assertFalse(state().isClosed());
        assertEquals("0", stored(IS_CLOSED));
        Map<String, String> current = missing("house_closed");
        current.put(HOUSEHOLD_ENROLLMENT_ID, "enrollment-two");
        visit("new-one", "new-form", "02-10-2026", 1, current);
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertFalse(state().isClosed());
        assertEquals(1, state().getHouseClosedCount());
        assertEquals("2", scalar("SELECT count(*) FROM visits"));
    }

    @Test
    public void legacyEpisodeAliasSurvivesTheLaterArrivalOfItsSourceEnrollmentEvent() throws Exception {
        data.delete("event", null, null);
        String fallback = MotherMentorHouseholdClosureRepository.currentEnrollmentId(database, HOUSEHOLD);
        assertEquals("legacy:household:" + ENROLLED_AT, fallback);
        Map<String, String> values = missing("house_closed");
        values.put(HOUSEHOLD_ENROLLMENT_ID, fallback);
        visit("one", "form-one", "02-10-2026", 1, values);
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals(1, state().getHouseClosedCount());
        enrollment(ENROLLMENT, ENROLLED_AT, 1);
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertEquals(ENROLLMENT, MotherMentorHouseholdClosureRepository
                .currentEnrollmentId(database, HOUSEHOLD));
        assertEquals(1, state().getHouseClosedCount());
    }

    @Test
    public void unscopedPartTwoVisitsUseSourceTimeRatherThanImportTime() throws Exception {
        Map<String, String> values = missing("house_closed");
        values.remove(HOUSEHOLD_ENROLLMENT_ID);
        visit("old", "form-old", "30-09-2026", 1, values);
        visit("current", "form-current", "02-10-2026", 1, values);
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals(1, state().getHouseClosedCount());
    }

    @Test
    public void malformedFutureDraftAndIncompleteVisitsCannotCountOrReset() throws Exception {
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        visit("draft", "draft", "03-10-2026", 1, missing("moved"));
        data.execSQL("UPDATE visits SET processed = 0 WHERE visit_id = 'draft'");
        visit("invalid-date", "invalid-date", "31-02-2026", 1, missing("moved"));
        visit("future", "future", "01-01-2099", 1, missing("moved"));
        Map<String, String> incompleteFound = found();
        incompleteFound.remove(TOPICS_TAUGHT);
        visit("incomplete", "incomplete", "03-10-2026", 1, incompleteFound);
        visit("two", "form-two", "04-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertFalse(state().isClosed());
        assertEquals(2, state().getHouseClosedCount());
    }

    @Test
    public void newRegistrationWithServicesResetsEveryNotFoundCounter() throws Exception {
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        visit("two", "form-two", "03-10-2026", 1, missing("other"));
        Map<String, String> registration = found();
        registration.put(HOUSEHOLD_VISIT_TYPE, "new");
        visit("new", "form-new", "04-10-2026", 1, registration);
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals(0, state().getNotFoundCount());
        assertFalse(state().isClosed());
    }

    @Test
    public void unrelatedArchivedRowsAreNotReopenedByAnActiveProjection() throws Exception {
        changeEnrollment(map(IS_CLOSED, "1"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertEquals("active", stored(KAYA_STATUS));
        assertEquals("1", stored(IS_CLOSED));
    }

    @Test
    public void missingEnrollmentDoesNotInventAHouseholdAndSourceClassifierIsNarrow() throws Exception {
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertFalse(MotherMentorHouseholdClosureRepository.getState(database, "missing", null).isClosed());
        assertEquals("", MotherMentorHouseholdClosureRepository.currentEnrollmentId(database, "missing"));
        MotherMentorHouseholdClosureRepository.rebuild(database, "missing");
        assertEquals("1", scalar("SELECT count(*) FROM " + HOUSEHOLDS));
        Visit visit = new Visit();
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        visit.setJson(event("form", "02-10-2026", 1, missing("house_closed")));
        assertTrue(MotherMentorHouseholdClosureRepository.isHouseholdVisit(visit));
        visit.setVisitType("Other services");
        assertFalse(MotherMentorHouseholdClosureRepository.isHouseholdVisit(visit));
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        visit.setJson(null);
        assertFalse(MotherMentorHouseholdClosureRepository.isHouseholdVisit(visit));
        assertEquals(6, MotherMentorHouseholdClosureRepository
                .observationValues(MotherMentorHouseholdClosure.active()).size());
    }

    @Test
    public void addCompleteAndDeleteHooksRebuildBeforeTheRefreshEvent() throws Exception {
        MotherMentorHouseholdClosureRepository.upgrade(database);
        FixtureVisits repository = new FixtureVisits();
        ClosureObserver observer = new ClosureObserver();
        EventBus.getDefault().register(observer);
        try {
            Visit incoming = sourceVisit("form-hook", "02-10-2026", 1, missing("moved"));
            incoming.setProcessed(false);
            repository.addVisit(incoming, database);
            assertFalse(state().isClosed());
            assertEquals("active", observer.statuses.get(0));
            repository.completeProcessing(incoming.getVisitId());
            assertTrue(state().isClosed());
            assertEquals("1", stored(IS_CLOSED));
            repository.deleteVisit(incoming.getVisitId());
            assertFalse(state().isClosed());
            assertEquals("0", stored(IS_CLOSED));
            assertEquals("0", scalar("SELECT count(*) FROM visits"));
            incoming.setVisitId("finalized-hook");
            incoming.setProcessed(true);
            repository.addVisit(incoming, database);
            assertEquals("moved", observer.statuses.get(observer.statuses.size() - 1));
        } finally {
            EventBus.getDefault().unregister(observer);
        }
    }

    @Test
    public void replayPreservesIdentityAndRejectsOlderOrUnversionedCorrections() throws Exception {
        MotherMentorHouseholdClosureRepository.upgrade(database);
        FixtureVisits repository = new FixtureVisits();
        Visit original = sourceVisit("form-replay", "02-10-2026", 2, missing("moved"));
        repository.addVisit(original, database);
        String identity = original.getVisitId();
        long created = original.getCreatedAt().getTime();
        ClosureObserver observer = new ClosureObserver();
        EventBus.getDefault().register(observer);
        try {
            Visit correction = sourceVisit("form-replay", "02-10-2026", 1, found());
            repository.refreshHouseholdVisit(original, correction, database, new VisitDetailsRepository());
            assertTrue(state().isClosed());
            JSONObject noVersion = new JSONObject(correction.getJson());
            noVersion.remove("version");
            correction.setJson(noVersion.toString());
            repository.refreshHouseholdVisit(original, correction, database, new VisitDetailsRepository());
            assertTrue(state().isClosed());
            correction = sourceVisit("form-replay", "02-10-2026", 3, found());
            repository.refreshHouseholdVisit(original, correction, database, new VisitDetailsRepository());
            assertFalse(state().isClosed());
            assertEquals("active", observer.statuses.get(observer.statuses.size() - 1));
            assertEquals(identity, correction.getVisitId());
            assertEquals(created, correction.getCreatedAt().getTime());
            assertEquals("1", scalar("SELECT count(*) FROM visits"));
            assertTrue(Integer.parseInt(scalar("SELECT count(*) FROM visit_details")) > 0);
            repository.refreshHouseholdVisit(correction, correction, database, new VisitDetailsRepository());
            assertEquals("1", scalar("SELECT count(*) FROM visits"));
            assertFalse(state().isClosed());
        } finally {
            EventBus.getDefault().unregister(observer);
        }
    }

    @Test
    public void voidedSourceRevisionRemovesItsStrikeWithoutRevivingAnOlderAlias() throws Exception {
        visit("one", "form-one", "02-10-2026", 1, missing("house_closed"));
        visit("two", "form-two", "03-10-2026", 1, missing("house_closed"));
        visit("three", "form-three", "04-10-2026", 1, missing("house_closed"));
        MotherMentorHouseholdClosureRepository.upgrade(database);
        assertTrue(state().isClosed());
        String correction = new JSONObject(event("form-two", "03-10-2026", 2,
                missing("house_closed"))).put("voided", true).toString();
        insert(VisitRepository.VISIT_TABLE, map(VISIT_ID, "two-voided", FORM_SUBMISSION_ID, "form-two",
                BASE_ENTITY_ID, HOUSEHOLD, PROCESSED, "1",
                VISIT_TYPE, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, VISIT_JSON, correction));
        MotherMentorHouseholdClosureRepository.rebuild(database, HOUSEHOLD);
        assertFalse(state().isClosed());
        assertEquals(2, state().getHouseClosedCount());
        assertEquals("0", stored(IS_CLOSED));
        assertEquals("4", scalar("SELECT count(*) FROM visits"));
    }

    @Test
    public void currentEnrollmentGuardAcceptsAliasesButRejectsAnOlderCycle() throws Exception {
        data.delete("event", null, null);
        Map<String, String> values = missing("house_closed");
        values.put(HOUSEHOLD_ENROLLMENT_ID,
                MotherMentorHouseholdClosureRepository.currentEnrollmentId(database, HOUSEHOLD));
        String original = event("form", "02-10-2026", 1, values);
        enrollment(ENROLLMENT, ENROLLED_AT, 1);
        assertTrue(MotherMentorHouseholdClosureRepository
                .belongsToCurrentEnrollment(database, HOUSEHOLD, original));
        enrollment("next-cycle", "2026-10-05T08:00:00.000+03:00", 2);
        changeEnrollment(map(EVENT_DATE, "2026-10-05T08:00:00.000+03:00"));
        assertFalse(MotherMentorHouseholdClosureRepository
                .belongsToCurrentEnrollment(database, HOUSEHOLD, original));
    }

    private Visit sourceVisit(String form, String day, long version, Map<String, String> values)
            throws Exception {
        org.smartregister.domain.Event event = JsonFormUtils.gson.fromJson(
                event(form, day, version, values), org.smartregister.domain.Event.class);
        event.setEventType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        return org.smartregister.chw.mothermentor.util.NCUtils.eventToVisit(event);
    }

    private class FixtureVisits extends VisitRepository {
        @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getWritableDatabase() { return database; }
        @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getReadableDatabase() { return database; }
    }

    public class ClosureObserver {
        final List<String> statuses = new ArrayList<>();
        @Subscribe public void onVisit(Visit visit) { statuses.add(state().getStatus()); }
    }

    private void enrollment(String id, String date, long version) throws Exception {
        JSONObject event = new JSONObject().put(EVENT_BASE_ENTITY_ID, HOUSEHOLD)
                .put(EVENT_TYPE, Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT)
                .put(EVENT_FORM_SUBMISSION_ID, id).put(EVENT_DATE_TIMESTAMP, date)
                .put("version", version).put("obs", observations(map(CONSENT, "ndiyo")));
        insert("event", map(EVENT_BASE_ENTITY_ID, HOUSEHOLD,
                EVENT_TYPE, Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT,
                EVENT_FORM_SUBMISSION_ID, id, EVENT_DATE_TIMESTAMP, date, EVENT_JSON, event.toString()));
    }

    private void visit(String id, String form, String date, long version, Map<String, String> values)
            throws Exception {
        insert(VisitRepository.VISIT_TABLE, map(VISIT_ID, id, FORM_SUBMISSION_ID, form,
                BASE_ENTITY_ID, HOUSEHOLD, PROCESSED, "1",
                VISIT_TYPE, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, VISIT_DATE, "1",
                CREATED_AT, "9999999999999", UPDATED_AT, "9999999999999",
                VISIT_JSON, event(form, date, version, values)));
    }

    private void updateVisit(String id, String form, String date, long version,
            Map<String, String> values) throws Exception {
        ContentValues update = new ContentValues();
        update.put(VISIT_JSON, event(form, date, version, values));
        data.update(VisitRepository.VISIT_TABLE, update, VISIT_ID + " = ?", new String[] {id});
    }

    private String event(String form, String date, long version, Map<String, String> original)
            throws Exception {
        Map<String, String> values = new HashMap<>(original);
        values.put(MOTHERMENTOR_VISIT_DATE, date);
        String sourceTime = date.substring(6) + "-" + date.substring(3, 5) + "-" + date.substring(0, 2)
                + "T10:00:00.000+03:00";
        return new JSONObject().put(EVENT_BASE_ENTITY_ID, HOUSEHOLD)
                .put(EVENT_FORM_SUBMISSION_ID, form).put(EVENT_DATE_TIMESTAMP, sourceTime)
                .put("version", version).put("obs", observations(values)).toString();
    }

    private JSONArray observations(Map<String, String> values) throws Exception {
        JSONArray obs = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            obs.put(new JSONObject().put("fieldCode", entry.getKey()).put("formSubmissionField", entry.getKey())
                    .put("values", new JSONArray().put(entry.getValue()))
                    .put("humanReadableValues", new JSONArray().put(entry.getValue())));
        }
        return obs;
    }

    private Map<String, String> missing(String reason) {
        return map(HOUSEHOLD_FOLLOW_UP_SCHEMA, "2", HOUSEHOLD_VISIT_TYPE, "repeat",
                HOUSEHOLD_FOLLOW_UP_STATUS, "not_found", HOUSEHOLD_NOT_FOUND_REASON, reason,
                HOUSEHOLD_NOT_FOUND_REASON_OTHER, "Recorded reason", HOUSEHOLD_ENROLLMENT_ID, ENROLLMENT);
    }

    private Map<String, String> found() {
        return map(HOUSEHOLD_FOLLOW_UP_SCHEMA, "2", HOUSEHOLD_VISIT_TYPE, "repeat",
                HOUSEHOLD_FOLLOW_UP_STATUS, "found", HOUSEHOLD_ENROLLMENT_ID, ENROLLMENT,
                PURPOSE_OF_VISIT, "ufuatiliaji", PARTICIPANTS, "mama", TOPICS_TAUGHT, "hiv",
                HOUSEHOLD_MEMBERS_FOUND, "1", COUNSELLED_MALE, "0", COUNSELLED_FEMALE, "1",
                COUNSELLED_TOTAL, "1");
    }

    private MotherMentorHouseholdClosure state() {
        return MotherMentorHouseholdClosureRepository.getState(database, HOUSEHOLD, null);
    }

    private void changeEnrollment(Map<String, String> values) {
        ContentValues update = new ContentValues();
        for (Map.Entry<String, String> entry : values.entrySet()) update.put(entry.getKey(), entry.getValue());
        data.update(HOUSEHOLDS, update, BASE_ENTITY_ID + " = ?", new String[] {HOUSEHOLD});
    }

    private String stored(String field) {
        return scalar("SELECT " + field + " FROM " + HOUSEHOLDS);
    }

    private String scalar(String query) {
        try (Cursor cursor = data.rawQuery(query, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private int columns(String table) {
        try (Cursor cursor = data.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            return cursor.getCount();
        }
    }

    private String columnType(String key) {
        try (Cursor cursor = data.rawQuery("PRAGMA table_info(" + HOUSEHOLDS + ")", null)) {
            while (cursor.moveToNext()) {
                if (key.equals(cursor.getString(1))) return cursor.getString(2);
            }
        }
        throw new AssertionError("Missing column " + key);
    }

    private void create(String table, String... columns) {
        data.execSQL("CREATE TABLE " + table + " (" + String.join(" TEXT, ", columns) + " TEXT)");
    }

    private void insert(String table, Map<String, String> values) {
        ContentValues row = new ContentValues();
        for (Map.Entry<String, String> entry : values.entrySet()) row.put(entry.getKey(), entry.getValue());
        data.insertOrThrow(table, null, row);
    }

    private Map<String, String> map(String... entries) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) values.put(entries[i], entries[i + 1]);
        return values;
    }
}
