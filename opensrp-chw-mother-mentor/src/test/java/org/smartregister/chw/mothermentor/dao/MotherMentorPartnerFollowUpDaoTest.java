package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.repository.Repository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerFollowUpDaoTest {
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase cipher;
    private MotherMentorLibrary previous;
    private TimeZone previousZone;

    @Before
    public void setUp() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dar_es_Salaam"));
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        data = SQLiteDatabase.create(null);
        create("ec_family_member", BASE_ENTITY_ID, DOD);
        create("event", EVENT_FORM_SUBMISSION_ID, EVENT_JSON);
        create(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER,
                BASE_ENTITY_ID, MOTHER_BASE_ENTITY_ID, PARTNER_LINKAGE_ID, IS_CLOSED,
                LAST_INTERACTED_WITH);
        create(Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                BASE_ENTITY_ID, PREGNANCY_STATUS, MOTHER_PREGNANCY_EPISODE_ID,
                IS_CLOSED, LAST_INTERACTED_WITH);
        create(VisitRepository.VISIT_TABLE, VISIT_ID, BASE_ENTITY_ID, FORM_SUBMISSION_ID,
                PROCESSED, VISIT_TYPE, VISIT_DATE, VISIT_JSON);
        create(Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT,
                BASE_ENTITY_ID, ENTITY_ID, FOLLOW_UP_CATEGORY, FOLLOW_UP_OUTCOME, MOTHERMENTOR_VISIT_DATE,
                PARTNER_FOLLOW_UP_SCHEMA, PARTNER_LINKAGE_ID, PARTNER_FOLLOW_UP_PHASE,
                FOLLOW_UP_STATUS, PARTNER_FACILITY_AGREEMENT, PARTNER_NOT_FOUND_REASON,
                PARTNER_NOT_FOUND_REASON_OTHER, PARTNER_FOLLOW_UP_END,
                PARTNER_ARRIVED_AT_FACILITY, PARTNER_FACILITY_ARRIVAL_DATE,
                PARTNER_EDUCATION_PROVIDED, PARTNER_EDUCATION_TOPICS,
                PARTNER_HIV_TESTED, PARTNER_HIV_RESULT, PARTNER_HIV_NOT_TESTED_REASON,
                PARTNER_HIV_NOT_TESTED_REASON_OTHER, PARTNER_SYPHILIS_TESTED,
                PARTNER_SYPHILIS_RESULT, PARTNER_SYPHILIS_NOT_TESTED_REASON,
                PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER, PARTNER_HEPATITIS_B_TESTED,
                PARTNER_HEPATITIS_B_RESULT, PARTNER_HEPATITIS_B_NOT_TESTED_REASON,
                PARTNER_HEPATITIS_B_NOT_TESTED_REASON_OTHER);
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        cipher = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        enroll("link-one");
        insert(Constants.TABLES.MOTHERMENTOR_ENROLLMENT, map(BASE_ENTITY_ID, "mother",
                PREGNANCY_STATUS, "pregnant", MOTHER_PREGNANCY_EPISODE_ID, "maternal-enrollment",
                IS_CLOSED, "0", LAST_INTERACTED_WITH, "1"));
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        data.close();
        TimeZone.setDefault(previousZone);
    }

    @Test
    public void completionExpiresAtLocalMidnightAndCannotCloseAFutureLinkage() throws Exception {
        addVisit("complete", "28-10-2026", true, completed());
        assertTrue(active("2026-10-28 23:59:59"));
        assertTrue(context("2026-10-28 23:59:59").isActive());
        assertFalse(MotherMentorPartnerFollowUpDao.canLinkPartner("partner", date("2026-10-28 23:59:59")));
        assertFalse(active("2026-10-29 00:00:00"));
        assertFalse(context("2026-10-29 00:00:00").isActive());
        assertTrue(MotherMentorPartnerFollowUpDao.canLinkPartner("partner", date("2026-10-29 00:00:00")));
        data.delete(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, null, null);
        enroll("link-two");
        assertTrue(active("2026-10-29 00:00:00"));
        MotherMentorPartnerFollowUpDao.Context state = context("2026-10-29 00:00:00");
        assertTrue(state.isActive());
        assertEquals("link-two", state.getLinkageId());
        assertEquals("maternal-enrollment", state.getPregnancyEpisodeId());
    }

    @Test
    public void deceasedIsImmediateAndPermanentlyBlocksRelinkingAcrossLinkages() throws Exception {
        addVisit("death", "28-10-2026", true, death());
        assertFalse(active("2026-10-28 12:00:00"));
        assertFalse(context("2026-10-28 12:00:00").isActive());
        assertTrue(deceased());
        data.delete(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, null, null);
        enroll("future-link");
        assertFalse(active("2026-10-29 12:00:00"));
        assertFalse(MotherMentorPartnerFollowUpDao.canLinkPartner("partner", date("2026-10-29 12:00:00")));
    }

    @Test
    public void sharedDeathWithoutPartnerHistoryCannotReopenUnderANewLinkageUuid() throws Exception {
        insert("ec_family_member", map(BASE_ENTITY_ID, "partner", DOD, "2026-10-28"));
        assertFalse(active("2026-10-28 12:00:00"));
        assertFalse(context("2026-10-28 12:00:00").isActive());
        assertTrue(MotherMentorPartnerFollowUpDao.isPermanentlyClosed("partner", cipher));
        assertFalse(MotherMentorPartnerFollowUpDao.canLinkPartner("partner", new Date()));
        data.delete(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, null, null);
        enroll("new-linkage-uuid-after-sync");
        assertFalse(active("2026-10-29 12:00:00"));
        assertFalse(context("2026-10-29 12:00:00").isActive());
        assertTrue(MotherMentorPartnerFollowUpDao.isPermanentlyClosed("partner", cipher));
    }

    @Test
    public void blankOrLiteralNullSharedDeathDoesNotHideTheActivePartner() throws Exception {
        for (String missing : new String[] {null, "", " ", "null", " NULL "}) {
            data.delete("ec_family_member", null, null);
            insert("ec_family_member", map(BASE_ENTITY_ID, "partner", DOD, missing));
            assertTrue(active("2026-10-29 12:00:00"));
            assertTrue(context("2026-10-29 12:00:00").isActive());
            assertFalse(MotherMentorPartnerFollowUpDao.isPermanentlyClosed("partner", cipher));
        }
    }

    @Test
    public void finalizedSchemaTwoDeathUsesTheSharedDeathContractButAttendanceCompletionDoesNot() throws Exception {
        addVisit("routine", "27-10-2026", true, completed());
        assertFalse(MotherMentorDao.isClientDeceased("partner"));
        addVisit("death", "28-10-2026", true, death());
        assertTrue(MotherMentorDao.isClientDeceased("partner"));
        assertTrue(MotherMentorPartnerFollowUpDao.isPermanentlyClosed("partner", cipher));
    }

    @Test
    public void onlyExplicitPartnerLegacyDeathIsPermanent() throws Exception {
        Map<String, String> values = map(FOLLOW_UP_STATUS, "deceased", FOLLOW_UP_CATEGORY, "IIT");
        addVisit("iit", "28-10-2026", true, values);
        assertFalse(deceased());
        assertTrue(context("2026-10-29 12:00:00").isActive());
        values.put(FOLLOW_UP_CATEGORY, "PARTNER");
        addVisit("partner-death", "28-10-2026", true, values);
        assertTrue(deceased());
        assertFalse(MotherMentorPartnerFollowUpDao.canLinkPartner("partner", date("2026-10-29 12:00:00")));
    }

    @Test
    public void draftsIncompleteTestsWrongCategoriesAndMalformedDatesCannotEndLinkage() throws Exception {
        addVisit("draft", "27-10-2026", false, completed());
        Map<String, String> incomplete = completed();
        incomplete.remove(PARTNER_HEPATITIS_B_RESULT);
        addVisit("incomplete", "27-10-2026", true, incomplete);
        Map<String, String> child = death();
        child.put(FOLLOW_UP_CATEGORY, "CHILD_EID");
        addVisit("child", "27-10-2026", true, child);
        addVisit("invalid-date", "31-02-2026", true, completed());
        assertTrue(active("2026-10-29 12:00:00"));
        assertTrue(context("2026-10-29 12:00:00").isActive());
        assertFalse(deceased());
    }

    @Test
    public void stateUsesEarlierFinalizedVisitsExcludingCurrentEditAndFutureDays() throws Exception {
        Map<String, String> found = tracingAgreement("agreed");
        addVisit("agreed", "27-10-2026", true, found);
        addVisit("draft-refused", "28-10-2026", false, tracingAgreement("refused"));
        addVisit("future-refused", "29-10-2026", true, tracingAgreement("refused"));
        addVisit("editing-refused", "28-10-2026", true, tracingAgreement("refused"));
        MotherMentorPartnerFollowUpDao.Context state = MotherMentorPartnerFollowUpDao.getContext(
                "partner", "editing-refused", date("2026-10-29 12:00:00"));
        assertTrue(state.isAttendancePending());
        assertEquals("mother", state.getMotherId());
        assertEquals("yes", state.getMotherPregnant());
        assertFalse(context("2026-10-29 12:00:00").isAttendancePending());
    }

    @Test
    public void attendanceNoStaysPendingAndOtherLinkageAgreementCannotAdvanceCurrentState() throws Exception {
        Map<String, String> attendance = completed();
        attendance.put(PARTNER_ARRIVED_AT_FACILITY, "no");
        attendance.remove(PARTNER_FOLLOW_UP_END);
        addVisit("attendance-no", "27-10-2026", true, attendance);
        Map<String, String> old = tracingAgreement("refused");
        old.put(PARTNER_LINKAGE_ID, "older-link");
        addVisit("other-link-refused", "28-10-2026", true, old);
        assertTrue(context("2026-10-29 12:00:00").isAttendancePending());
        assertTrue(active("2026-10-29 12:00:00"));
    }

    @Test
    public void missingClientHasUnknownContextAndCanBeLinkedWithoutInventingPregnancy() throws Exception {
        MotherMentorPartnerFollowUpDao.Context missing = MotherMentorPartnerFollowUpDao.getContext(
                "missing", null, date("2026-10-29 12:00:00"));
        assertFalse(missing.isActive());
        assertEquals("", missing.getLinkageId());
        assertEquals("unknown", missing.getMotherPregnant());
        assertTrue(MotherMentorPartnerFollowUpDao.canLinkPartner("missing", new Date()));
        data.delete(Constants.TABLES.MOTHERMENTOR_ENROLLMENT, null, null);
        assertEquals("", context("2026-10-29 12:00:00").getPregnancyEpisodeId());
        assertEquals("unknown", context("2026-10-29 12:00:00").getMotherPregnant());
    }

    @Test
    public void legacyLinkageFallbackMatchesHistoricalProjectionWithoutAStoredUuid() throws Exception {
        data.delete(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, null, null);
        enroll("");
        Map<String, String> values = completed();
        values.remove(PARTNER_LINKAGE_ID);
        addVisit("legacy-uuid", "28-10-2026", true, values);
        assertEquals("legacy:partner", context("2026-10-28 12:00:00").getLinkageId());
        assertFalse(active("2026-10-29 12:00:00"));
        assertFalse(context("2026-10-29 12:00:00").isActive());
    }

    @Test
    public void completedUntestedBranchesWithEducationTopicsExpireLikeTestedBranches() throws Exception {
        Map<String, String> values = completed();
        values.put(PARTNER_HIV_TESTED, "no");
        values.put(PARTNER_HIV_NOT_TESTED_REASON, "already_infected");
        values.put(PARTNER_SYPHILIS_TESTED, "no");
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON, "other");
        values.put(PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER, "No reagent");
        values.put(PARTNER_HEPATITIS_B_TESTED, "no");
        values.put(PARTNER_HEPATITIS_B_NOT_TESTED_REASON, "refused");
        values.put(PARTNER_EDUCATION_PROVIDED, "yes");
        values.put(PARTNER_EDUCATION_TOPICS, "[\"syphilis\",\"hepatitis_b\",\"hiv\"]");
        addVisit("not-tested", "28-10-2026", true, values);
        assertFalse(active("2026-10-29 00:00:00"));
        assertFalse(context("2026-10-29 00:00:00").isActive());
    }

    private MotherMentorPartnerFollowUpDao.Context context(String now) throws Exception {
        return MotherMentorPartnerFollowUpDao.getContext("partner", null, date(now));
    }

    private boolean active(String now) throws Exception {
        return exists("SELECT 1 FROM " + Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER
                + " p WHERE " + MotherMentorPartnerFollowUpDao.activeEnrollmentCondition("p", date(now)));
    }

    private boolean deceased() {
        return exists("SELECT 1 FROM " + Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER
                + " p WHERE " + MotherMentorPartnerFollowUpDao.deceasedPartnerCondition("p." + BASE_ENTITY_ID));
    }

    private boolean exists(String sql) {
        try (Cursor cursor = data.rawQuery(sql, null)) {
            return cursor.moveToFirst();
        }
    }

    private void enroll(String linkage) {
        insert(Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER, map(BASE_ENTITY_ID, "partner",
                MOTHER_BASE_ENTITY_ID, "mother", PARTNER_LINKAGE_ID, linkage, IS_CLOSED, "0",
                LAST_INTERACTED_WITH, "1"));
    }

    private Map<String, String> completed() {
        return map(FOLLOW_UP_CATEGORY, "PARTNER", PARTNER_FOLLOW_UP_SCHEMA, "2",
                PARTNER_LINKAGE_ID, "link-one", PARTNER_FOLLOW_UP_PHASE, "attendance",
                PARTNER_ARRIVED_AT_FACILITY, "yes", PARTNER_FACILITY_ARRIVAL_DATE, "28-10-2026",
                PARTNER_FOLLOW_UP_END, "completed", PARTNER_EDUCATION_PROVIDED, "no",
                PARTNER_HIV_TESTED, "yes", PARTNER_HIV_RESULT, "negative",
                PARTNER_SYPHILIS_TESTED, "yes", PARTNER_SYPHILIS_RESULT, "positive",
                PARTNER_HEPATITIS_B_TESTED, "yes", PARTNER_HEPATITIS_B_RESULT, "negative");
    }

    private Map<String, String> tracingAgreement(String agreement) {
        return map(FOLLOW_UP_CATEGORY, "PARTNER", PARTNER_FOLLOW_UP_SCHEMA, "2",
                PARTNER_LINKAGE_ID, "link-one", PARTNER_FOLLOW_UP_PHASE, "tracing",
                FOLLOW_UP_STATUS, "found", PARTNER_FACILITY_AGREEMENT, agreement,
                PARTNER_EDUCATION_PROVIDED, "no");
    }

    private Map<String, String> death() {
        return map(FOLLOW_UP_CATEGORY, "PARTNER", PARTNER_FOLLOW_UP_SCHEMA, "2",
                PARTNER_LINKAGE_ID, "link-one", PARTNER_FOLLOW_UP_PHASE, "tracing",
                FOLLOW_UP_STATUS, "not_found", PARTNER_NOT_FOUND_REASON, "deceased",
                PARTNER_FOLLOW_UP_END, "deceased");
    }

    private void addVisit(String id, String serviceDate, boolean processed, Map<String, String> values)
            throws Exception {
        values.put(MOTHERMENTOR_VISIT_DATE, serviceDate);
        JSONArray obs = new JSONArray();
        for (Map.Entry<String, String> value : values.entrySet()) {
            obs.put(new JSONObject().put("fieldCode", value.getKey())
                    .put("values", new JSONArray().put(value.getValue())));
        }
        insert(VisitRepository.VISIT_TABLE, map(VISIT_ID, id, BASE_ENTITY_ID, "partner",
                FORM_SUBMISSION_ID, id + "-form", PROCESSED, processed ? "1" : "0",
                VISIT_TYPE, MotherMentorFollowUpType.PARTNER.getEventType(), VISIT_DATE,
                Long.toString(date("2026-10-28 12:00:00").getTime()), VISIT_JSON,
                new JSONObject().put("obs", obs).toString()));
        Map<String, String> projection = new HashMap<>(values);
        projection.put(BASE_ENTITY_ID, id + "-form");
        projection.put(ENTITY_ID, "partner");
        insert(Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT, projection);
    }

    private void create(String table, String... columns) {
        data.execSQL("CREATE TABLE " + table + " (" + String.join(" TEXT, ", columns) + " TEXT)");
    }

    private void insert(String table, Map<String, String> values) {
        ContentValues row = new ContentValues();
        for (Map.Entry<String, String> value : values.entrySet()) row.put(value.getKey(), value.getValue());
        data.insertOrThrow(table, null, row);
    }

    private Map<String, String> map(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }

    private Date date(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value);
    }
}
