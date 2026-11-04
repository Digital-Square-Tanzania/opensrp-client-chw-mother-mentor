package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
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
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerClosure;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.domain.db.EventClient;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerClosureRepositoryTest {
    private static final String PARTNERS = Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER;
    private static final String FOLLOWUPS = Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT;
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase cipher;
    private TimeZone previousZone;
    private VisitRepository visits;
    private MotherMentorLibrary previousLibrary;

    @Before
    public void setUp() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dar_es_Salaam"));
        data = SQLiteDatabase.create(null);
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        cipher = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; })
                .when(cipher).execSQL(anyString());
        when(cipher.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> { data.beginTransaction(); return null; }).when(cipher).beginTransaction();
        doAnswer(call -> { data.setTransactionSuccessful(); return null; })
                .when(cipher).setTransactionSuccessful();
        doAnswer(call -> { data.endTransaction(); return null; }).when(cipher).endTransaction();
        when(cipher.insert(anyString(), nullable(String.class), any(ContentValues.class)))
                .thenAnswer(call -> data.insertOrThrow(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2)));
        when(cipher.update(anyString(), any(ContentValues.class), nullable(String.class),
                nullable(String[].class))).thenAnswer(call -> data.update(call.getArgument(0),
                        call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(cipher.delete(anyString(), nullable(String.class), nullable(String[].class)))
                .thenAnswer(call -> data.delete(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2)));
        when(cipher.query(anyString(), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class)))
                .thenAnswer(call -> data.query(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3), call.getArgument(4),
                        call.getArgument(5), call.getArgument(6), call.getArgument(7)));
        create(PARTNERS, BASE_ENTITY_ID, PARTNER_LINKAGE_ID, LAST_INTERACTED_WITH);
        create(FOLLOWUPS, BASE_ENTITY_ID, ENTITY_ID);
        VisitRepository.createTable(cipher);
        data.execSQL(VisitRepository.ADD_VISIT_GROUP_COLUMN);
        VisitDetailsRepository.createTable(cipher);
        visits = new VisitRepository() {
            @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getWritableDatabase() {
                return cipher;
            }
            @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getReadableDatabase() {
                return cipher;
            }
        };
        insert(PARTNERS, map(BASE_ENTITY_ID, "partner", PARTNER_LINKAGE_ID, "link-one",
                LAST_INTERACTED_WITH, "1"));
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        data.close();
        TimeZone.setDefault(previousZone);
    }

    @Test
    public void upgradeAddsEightColumnsAndBackfillsSummariesAndVisitSnapshotsIdempotently()
            throws Exception {
        rawVisit("one", "form-one", "01-10-2026", 1, refused(), true);
        rawVisit("two", "form-two", "02-10-2026", 2, refused(), true);
        insert(FOLLOWUPS, map(BASE_ENTITY_ID, "form-one", ENTITY_ID, "partner"));
        insert(FOLLOWUPS, map(BASE_ENTITY_ID, "form-two", ENTITY_ID, "partner"));
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
        assertEquals("2", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        assertEquals("1", stored(FOLLOWUPS, "form-one", MWENZI_COUNTER_AMEKATAA));
        assertEquals("2", stored(FOLLOWUPS, "form-two", MWENZI_COUNTER_AMEKATAA));
        for (String table : new String[] {PARTNERS, FOLLOWUPS}) {
            int columns = 0;
            try (Cursor cursor = data.rawQuery("PRAGMA table_info(" + table + ")", null)) {
                while (cursor.moveToNext()) {
                    if (MotherMentorPartnerClosureRepository.CLOSURE_COLUMNS.contains(
                            cursor.getString(cursor.getColumnIndexOrThrow("name")))) columns++;
                }
            }
            assertEquals(8, columns);
        }
        assertEquals("integer", scalar("SELECT typeof(" + MWENZI_COUNTER_AMEKATAA
                + ") FROM " + PARTNERS));
    }

    @Test
    public void missingModuleTablesAreHarmlessOnOtherFlavors() {
        data.execSQL("DROP TABLE " + PARTNERS);
        data.execSQL("DROP TABLE " + FOLLOWUPS);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertFalse(MotherMentorPartnerClosureRepository.getState(cipher, "partner", null).isClosed());
    }

    @Test(expected = android.database.sqlite.SQLiteException.class)
    public void malformedExistingSchemaDoesNotSilentlySkipAnActualMigrationFailure() {
        data.execSQL("DROP TABLE " + PARTNERS);
        create(PARTNERS, "unexpected_column");
        MotherMentorPartnerClosureRepository.upgrade(cipher);
    }

    @Test
    public void chronologyAndLatestSourceRevisionDetermineCountersNotImportOrder() throws Exception {
        rawVisit("third", "third", "03-10-2026", 1, refused(), true);
        rawVisit("first", "first", "01-10-2026", 1, refused(), true);
        rawVisit("old-second", "second", "02-10-2026", 1, refused(), true);
        rawVisit("new-second", "second", "02-10-2026", 2, agreed(), true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals(1, state().getRefusedCount());
        assertFalse(state().isClosed());
        // Excluding an edited row also excludes aliases of the same form submission.
        assertEquals(2, MotherMentorPartnerClosureRepository.getState(
                cipher, "partner", "new-second").getRefusedCount());
        assertFalse(MotherMentorPartnerClosureRepository.getState(
                cipher, "partner", "new-second").isClosed());
    }

    @Test
    public void correctedInvalidRevisionSupersedesOlderValidAliasAndDoesNotCount() throws Exception {
        rawVisit("one", "one", "01-10-2026", 1, refused(), true);
        rawVisit("old-two", "two", "02-10-2026", 1, refused(), true);
        rawVisit("new-two", "two", "02-10-2026", 2,
                map(PARTNER_FOLLOW_UP_PHASE, "tracing", FOLLOW_UP_STATUS, "found"), true);
        rawVisit("three", "three", "03-10-2026", 1, refused(), true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals(2, state().getRefusedCount());
        assertFalse(state().isClosed());
        assertEquals(2, MotherMentorPartnerClosureRepository.getState(
                cipher, "partner", "new-two").getRefusedCount());
    }

    @Test
    public void duplicateIdentityCannotCountTwiceAndMixedReasonsCloseOnlyOverallCounter()
            throws Exception {
        rawVisit("one", "duplicate", "01-10-2026", 1, notFound("travelled"), true);
        rawVisit("copy", "duplicate", "01-10-2026", 1, notFound("travelled"), true);
        rawVisit("two", "two", "02-10-2026", 1, notFound("other"), true);
        rawVisit("three", "three", "03-10-2026", 1, notFound("travelled"), true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("lost_to_follow_up", state().getStatus());
        assertEquals(3, state().getNotFoundCount());
        assertEquals(1, state().getTravelledCount());
        assertEquals(0, state().getOtherCount());
    }

    @Test
    public void rejectsDraftOtherLinkageWrongCategoryFutureAndImpossibleServiceDate() throws Exception {
        rawVisit("one", "one", "01-10-2026", 1, refused(), true);
        rawVisit("draft", "draft", "02-10-2026", 1, refused(), false);
        Map<String, String> otherLink = refused();
        otherLink.put(PARTNER_LINKAGE_ID, "old-link");
        rawVisit("old", "old", "02-10-2026", 1, otherLink, true);
        Map<String, String> otherCategory = refused();
        otherCategory.put(FOLLOW_UP_CATEGORY, "CHILD_EID");
        rawVisit("child", "child", "02-10-2026", 1, otherCategory, true);
        rawVisit("future", "future", "01-01-2999", 1, refused(), true);
        rawVisit("invalid", "invalid", "31-02-2026", 1, refused(), true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals(1, state().getRefusedCount());
        assertFalse(state().isClosed());
    }

    @Test
    public void preservesLegacyCompletionOnlyForItsCurrentLinkageEvidence() throws Exception {
        rawVisit("legacy", "legacy", "01-10-2026", 1, attendedAllNo(), true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("completed", state().getStatus());
        assertEquals("2026-10-01", state().getClosedDate());
        assertEquals("completed", stored(PARTNERS, "partner", MWENZI_STATUS));
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("completed", state().getStatus());
        // Older apps can import a new linkage without the new reset observations.
        ContentValues enrollment = new ContentValues();
        enrollment.put(PARTNER_LINKAGE_ID, "link-two");
        data.update(PARTNERS, enrollment, null, null);
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertFalse(state().isClosed());
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
        assertEquals("", stored(PARTNERS, "partner", MWENZI_TAREHE_YA_KUFUNGWA));
    }

    @Test
    public void currentSchemaAllNoDoesNotCloseAndExplicitActiveProjectionCannotBecomeLegacy()
            throws Exception {
        Map<String, String> current = attendedAllNo();
        current.put(MWENZI_STATUS, "active");
        rawVisit("current", "current", "01-10-2026", 1, current, true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertFalse(state().isClosed());
        Map<String, String> legacy = attendedAllNo();
        rawVisit("old-app", "old-app", "02-10-2026", 1, legacy, true);
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertFalse(state().isClosed());
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
    }

    @Test
    public void lateImportedHistoricalLegacyCompletionIsPreservedAfterAnEmptyUpgrade()
            throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
        rawVisit("late", "late-form", "01-10-2026", 1, attendedAllNo(), true);
        insert(FOLLOWUPS, map(BASE_ENTITY_ID, "late-form", ENTITY_ID, "partner"));
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertEquals("completed", state().getStatus());
        assertEquals("completed", stored(PARTNERS, "partner", MWENZI_STATUS));
        assertEquals("completed", stored(FOLLOWUPS, "late-form", MWENZI_STATUS));
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertEquals("completed", state().getStatus());
        ContentValues reset = new ContentValues();
        reset.put(PARTNER_LINKAGE_ID, "link-two");
        reset.put(MWENZI_STATUS, "active");
        data.update(PARTNERS, reset, null, null);
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertFalse(state().isClosed());
    }

    @Test
    public void todaysLegacyAllNoRemainsActiveAfterProjectionEvenWhenItBecomesHistorical()
            throws Exception {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        String yesterday = new SimpleDateFormat("dd-MM-yyyy", Locale.US)
                .format(new Date(System.currentTimeMillis() - 86400000));
        rawVisit("today", "today", today, 1, attendedAllNo(), true);
        insert(FOLLOWUPS, map(BASE_ENTITY_ID, "today", ENTITY_ID, "partner"));
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
        assertEquals("active", stored(FOLLOWUPS, "today", MWENZI_STATUS));
        ContentValues values = new ContentValues();
        values.put(VISIT_JSON, visit("today", "today", yesterday, 1,
                attendedAllNo(), true).getJson());
        data.update(VisitRepository.VISIT_TABLE, values, VISIT_ID + " = ?",
                new String[] {"today"});
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertEquals("active", stored(PARTNERS, "partner", MWENZI_STATUS));
        assertFalse(state().isClosed());
    }

    @Test
    public void processedAddFinalizationAndDeleteRebuildWithoutMutableCounterIncrements()
            throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        Visit one = visit("one", "one", "01-10-2026", 1, refused(), false);
        visits.addVisit(one, cipher);
        assertEquals("0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        visits.completeProcessing("one");
        visits.completeProcessing("one");
        assertEquals("1", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        visits.addVisit(visit("two", "two", "02-10-2026", 1, refused(), true), cipher);
        assertEquals("2", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        visits.deleteVisit("one");
        assertEquals("1", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
    }

    @Test
    public void identicalReplayDoesNotDuplicateVisitsDetailsOrCounters() throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        Visit existing = visit("one", "same-form", "01-10-2026", 10, refused(), true);
        visits.addVisit(existing, cipher);
        VisitDetailsRepository details = new VisitDetailsRepository();
        details.addVisitDetails(detail("old-detail", "one", "refused"), cipher);
        Visit replay = visit("new-random-id", "same-form", "01-10-2026", 10, refused(), true);
        visits.refreshPartnerVisit(existing, replay, cipher, details);
        visits.refreshPartnerVisit(existing, replay, cipher, details);
        assertEquals("1", scalar("SELECT COUNT(*) FROM visits"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM visit_details"));
        assertEquals("old-detail", scalar("SELECT " + VISIT_DETAILS_ID + " FROM visit_details"));
        assertEquals("1", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
    }

    @Test
    public void newerAndEqualVersionCorrectionsReplaceEvidenceAndDetailsPreservingIdentity()
            throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        Visit existing = visit("original-id", "same-form", "01-10-2026", 10, refused(), true);
        existing.setParentVisitID("parent");
        visits.addVisit(existing, cipher);
        VisitDetailsRepository details = new VisitDetailsRepository();
        details.addVisitDetails(detail("old-detail", "original-id", "refused"), cipher);
        Visit correction = visit("incoming-id", "same-form", "01-10-2026", 11, agreed(), true);
        correction.setVisitDetails(Collections.singletonMap(PARTNER_FACILITY_AGREEMENT,
                Arrays.asList(detail("new-detail", "incoming-id", "agreed"))));
        visits.refreshPartnerVisit(existing, correction, cipher, details);
        assertEquals("original-id", scalar("SELECT " + VISIT_ID + " FROM visits"));
        assertEquals("parent", scalar("SELECT " + PARENT_VISIT_ID + " FROM visits"));
        assertEquals(Long.toString(existing.getCreatedAt().getTime()),
                scalar("SELECT " + CREATED_AT + " FROM visits"));
        assertEquals("0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        assertEquals("1", scalar("SELECT COUNT(*) FROM visit_details"));
        assertEquals("original-id", scalar("SELECT " + VISIT_ID + " FROM visit_details"));
        assertEquals("agreed", scalar("SELECT " + DETAILS + " FROM visit_details"));
        Visit equalVersion = visit("another-id", "same-form", "01-10-2026", 11, refused(), true);
        visits.refreshPartnerVisit(correction, equalVersion, cipher, details);
        assertEquals("1", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
    }

    @Test
    public void olderAndUnversionedDifferentPayloadsCannotOverwriteNewerSourceEvidence()
            throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        Visit existing = visit("one", "same-form", "01-10-2026", 10, agreed(), true);
        visits.addVisit(existing, cipher);
        VisitDetailsRepository details = new VisitDetailsRepository();
        for (long version : new long[] {9, 0}) {
            Visit incoming = visit("incoming", "same-form", "01-10-2026", version, refused(), true);
            visits.refreshPartnerVisit(existing, incoming, cipher, details);
        }
        Visit missingVersion = visit("incoming", "same-form", "01-10-2026", 0, refused(), true);
        JSONObject incomingJson = new JSONObject(missingVersion.getJson());
        incomingJson.remove("version");
        missingVersion.setJson(incomingJson.toString());
        visits.refreshPartnerVisit(existing, missingVersion, cipher, details);
        assertEquals(existing.getJson(), scalar("SELECT " + VISIT_JSON + " FROM visits"));
        assertEquals("0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        Visit legacy = visit("legacy", "legacy-form", "02-10-2026", 0, agreed(), true);
        visits.addVisit(legacy, cipher);
        visits.refreshPartnerVisit(legacy,
                visit("new", "legacy-form", "02-10-2026", 0, refused(), true), cipher, details);
        assertEquals("0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
        visits.refreshPartnerVisit(legacy,
                visit("versioned", "legacy-form", "02-10-2026", 1, refused(), true), cipher, details);
        assertEquals("1", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
    }

    @Test
    public void processHomeVisitReplayReplacesPartnerEvidenceBeforeUiRefresh() throws Exception {
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.visitRepository()).thenReturn(visits);
        when(library.visitDetailsRepository()).thenReturn(new VisitDetailsRepository());
        when(library.getSourceDateFormat()).thenReturn("dd-MM-yyyy");
        when(library.getSaveDateFormat()).thenReturn("yyyy-MM-dd");
        Visit original = visit("local-id", "same-form", "01-10-2026", 10, refused(), true);
        visits.addVisit(original, cipher);
        Visit correction = visit("server-id", "same-form", "01-10-2026", 11, agreed(), true);
        EventClient source = mock(EventClient.class);
        when(source.getEvent()).thenReturn(JsonFormUtils.gson.fromJson(
                correction.getJson(), org.smartregister.domain.Event.class));
        ProjectionObserver observer = new ProjectionObserver(() -> assertEquals(
                "0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA)));
        EventBus.getDefault().register(observer);
        try {
            NCUtils.processHomeVisit(source, cipher);
            NCUtils.processHomeVisit(source, cipher);
            assertEquals(2, observer.observed);
            assertEquals("1", scalar("SELECT COUNT(*) FROM visits"));
            assertEquals("local-id", scalar("SELECT " + VISIT_ID + " FROM visits"));
            assertEquals("0", stored(PARTNERS, "partner", MWENZI_COUNTER_AMEKATAA));
            assertTrue(Integer.parseInt(scalar("SELECT COUNT(*) FROM visit_details")) > 0);
        } finally {
            EventBus.getDefault().unregister(observer);
        }
    }

    @Test
    public void legacyLinkageFallbackAndRelinkStartFromZero() throws Exception {
        data.execSQL("UPDATE " + PARTNERS + " SET " + PARTNER_LINKAGE_ID + " = ''");
        Map<String, String> legacy = refused();
        legacy.put(PARTNER_LINKAGE_ID, "");
        rawVisit("one", "one", "01-10-2026", 1, legacy, true);
        MotherMentorPartnerClosureRepository.upgrade(cipher);
        assertEquals(1, state().getRefusedCount());
        ContentValues reset = new ContentValues();
        reset.put(PARTNER_LINKAGE_ID, "link-two");
        for (Map.Entry<String, String> entry : MotherMentorPartnerClosureRepository
                .observationValues(MotherMentorPartnerClosure.active()).entrySet()) {
            reset.put(entry.getKey(), entry.getValue());
        }
        data.update(PARTNERS, reset, null, null);
        MotherMentorPartnerClosureRepository.rebuild(cipher, "partner");
        assertEquals(0, state().getRefusedCount());
    }

    private MotherMentorPartnerClosure state() {
        return MotherMentorPartnerClosureRepository.getState(cipher, "partner", null);
    }

    private void create(String table, String... columns) {
        data.execSQL("CREATE TABLE " + table + " (" + String.join(" TEXT, ", columns) + " TEXT)");
    }

    private void insert(String table, Map<String, String> fields) {
        ContentValues values = new ContentValues();
        for (Map.Entry<String, String> field : fields.entrySet()) values.put(field.getKey(), field.getValue());
        data.insertOrThrow(table, null, values);
    }

    private void rawVisit(String id, String form, String day, long version,
            Map<String, String> values, boolean processed) throws Exception {
        Visit visit = visit(id, form, day, version, values, processed);
        insert(VisitRepository.VISIT_TABLE, map(VISIT_ID, id, BASE_ENTITY_ID, "partner",
                FORM_SUBMISSION_ID, form, PROCESSED, processed ? "1" : "0",
                VISIT_TYPE, visit.getVisitType(), VISIT_DATE, "1", UPDATED_AT, "1",
                CREATED_AT, "1", VISIT_JSON, visit.getJson()));
    }

    private Visit visit(String id, String form, String day, long version,
            Map<String, String> answers, boolean processed) throws Exception {
        Map<String, String> values = map(FOLLOW_UP_CATEGORY, "PARTNER",
                PARTNER_FOLLOW_UP_SCHEMA, "2", PARTNER_LINKAGE_ID, "link-one",
                MOTHERMENTOR_VISIT_DATE, day);
        values.putAll(answers);
        JSONArray observations = new JSONArray();
        for (Map.Entry<String, String> value : values.entrySet()) {
            observations.put(new JSONObject().put("fieldCode", value.getKey())
                    .put("formSubmissionField", value.getKey())
                    .put("values", new JSONArray().put(value.getValue()))
                    .put("humanReadableValues", new JSONArray().put(value.getValue())));
        }
        String json = new JSONObject().put("baseEntityId", "partner")
                .put("formSubmissionId", form).put("eventId", form)
                .put("eventType", MotherMentorFollowUpType.PARTNER.getEventType())
                .put("eventDate", "2026-10-01T12:00:00.000+03:00")
                .put("version", version).put("obs", observations).toString();
        Visit visit = new Visit();
        visit.setVisitId(id);
        visit.setVisitType(MotherMentorFollowUpType.PARTNER.getEventType());
        visit.setBaseEntityId("partner");
        visit.setFormSubmissionId(form);
        visit.setDate(new Date(1));
        visit.setCreatedAt(new Date(2));
        visit.setUpdatedAt(new Date(3));
        visit.setProcessed(processed);
        visit.setJson(json);
        return visit;
    }

    private VisitDetail detail(String id, String visitId, String answer) {
        VisitDetail detail = new VisitDetail();
        detail.setVisitDetailsId(id);
        detail.setVisitId(visitId);
        detail.setVisitKey(PARTNER_FACILITY_AGREEMENT);
        detail.setDetails(answer);
        detail.setHumanReadable(answer);
        detail.setProcessed(true);
        detail.setCreatedAt(new Date(2));
        detail.setUpdatedAt(new Date(3));
        return detail;
    }

    private Map<String, String> refused() {
        return map(PARTNER_FOLLOW_UP_PHASE, "tracing", FOLLOW_UP_STATUS, "found",
                PARTNER_FACILITY_AGREEMENT, "refused", PARTNER_EDUCATION_PROVIDED, "no");
    }

    private Map<String, String> agreed() {
        Map<String, String> result = refused();
        result.put(PARTNER_FACILITY_AGREEMENT, "agreed");
        return result;
    }

    private Map<String, String> notFound(String reason) {
        return map(PARTNER_FOLLOW_UP_PHASE, "tracing", FOLLOW_UP_STATUS, "not_found",
                PARTNER_NOT_FOUND_REASON, reason, PARTNER_NOT_FOUND_REASON_OTHER, "Other reason");
    }

    private Map<String, String> attendedAllNo() {
        return map(PARTNER_FOLLOW_UP_PHASE, "attendance", PARTNER_ARRIVED_AT_FACILITY, "yes",
                PARTNER_FACILITY_ARRIVAL_DATE, "01-10-2026", PARTNER_EDUCATION_PROVIDED, "no",
                PARTNER_FOLLOW_UP_END, "completed", PARTNER_HIV_TESTED, "no",
                PARTNER_HIV_NOT_TESTED_REASON, "refused", PARTNER_SYPHILIS_TESTED, "no",
                PARTNER_SYPHILIS_NOT_TESTED_REASON, "stock_out", PARTNER_HEPATITIS_B_TESTED, "no",
                PARTNER_HEPATITIS_B_NOT_TESTED_REASON, "stock_out");
    }

    private String stored(String table, String id, String field) {
        try (Cursor cursor = data.rawQuery("SELECT " + field + " FROM " + table + " WHERE "
                + BASE_ENTITY_ID + " = ?", new String[] {id})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private String scalar(String sql) {
        try (Cursor cursor = data.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private Map<String, String> map(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    public static final class ProjectionObserver {
        private final Runnable check;
        private int observed;

        ProjectionObserver(Runnable check) { this.check = check; }

        @Subscribe public void onVisit(Visit visit) {
            check.run();
            observed++;
        }
    }
}
