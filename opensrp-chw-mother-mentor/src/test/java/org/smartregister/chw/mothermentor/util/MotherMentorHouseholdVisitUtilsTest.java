package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.Visit;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdVisitUtilsTest {
    private TimeZone previousZone;

    @Before
    public void useLocalTimeZone() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dar_es_Salaam"));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(previousZone);
    }

    @Test
    public void completedHouseholdVisitLocksOnlyItsLocalCalendarDay() throws Exception {
        Visit visit = completedVisit("25-10-2026");
        visit.setDate(date("2026-10-25 23:59:59"));
        assertTrue(locked(visit, "2026-10-25 23:59:59"));
        assertFalse(locked(visit, "2026-10-26 00:00:00"));
        assertFalse(locked(visit, "2026-10-24 23:59:59"));
    }

    @Test
    public void draftDoesNotCountAsCompletedVisit() throws Exception {
        Visit visit = completedVisit("25-10-2026");
        visit.setProcessed(false);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        visit.setProcessed(null);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void autoProcessedIncompleteHouseholdVisitDoesNotBlockServices() throws Exception {
        Visit visit = completedVisit("25-10-2026");
        JSONObject event = new JSONObject(visit.getJson());
        JSONArray observations = event.getJSONArray("obs");
        observations.remove(1);
        visit.setJson(event.toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void commentsDoNotDetermineWhetherProcessedServicesWereProvided() throws Exception {
        // A processed household event can omit optional feedback or come from an older form.
        Visit visit = completedVisit("25-10-2026");
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void oldVisitImportedTodayUsesRecordedServiceDate() throws Exception {
        Visit visit = completedVisit("24-10-2026");
        visit.setDate(date("2026-10-25 12:00:00"));
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void legacyImportedVisitUsesOriginalEventDateInLocalTimeZone() throws Exception {
        Visit visit = completedVisit(null);
        visit.setDate(date("2026-10-25 12:00:00"));
        JSONObject event = new JSONObject(visit.getJson());
        event.put("eventDate", "2026-10-24T20:00:00.000Z");
        visit.setJson(event.toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        event.put("eventDate", "2026-10-24T22:00:00.000Z");
        visit.setJson(event.toString());
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void legacyLocalVisitUsesItsPersistedTimestamp() throws Exception {
        Visit visit = completedVisit(null);
        visit.setDate(date("2026-10-25 00:01:00"));
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void unrelatedClientAndFollowUpVisitPayloadsDoNotLockHousehold() throws Exception {
        Visit visit = completedVisit("25-10-2026");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_FOLLOW_UP_VISIT);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        visit.setJson(new JSONObject().put("obs", new JSONArray()).toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void scansAllVisitsRatherThanLatestImportedRecord() throws Exception {
        assertTrue(
                MotherMentorHouseholdVisitUtils.hasCompletedVisitToday(
                        Arrays.asList(
                                completedVisit("24-10-2026"),
                                completedVisit("25-10-2026"),
                                completedVisit("26-10-2026")),
                        date("2026-10-25 12:00:00")));
    }

    @Test
    public void invalidRecordedDateOrJsonDoesNotLockFromImportTimestamp() throws Exception {
        Visit visit = completedVisit("32-10-2026");
        visit.setDate(date("2026-10-25 12:00:00"));
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        visit.setJson("invalid json");
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        assertFalse(MotherMentorHouseholdVisitUtils.hasCompletedVisitToday(null, new Date()));
    }

    @Test
    public void schemaTwoNotFoundCompletesWithoutServicesButRequiresItsReason() throws Exception {
        Visit visit = statusVisit("repeat", "not_found", "house_closed", "");
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
        assertFalse(locked(statusVisit("repeat", "not_found", "", ""), "2026-10-25 12:00:00"));
        assertFalse(locked(statusVisit("repeat", "not_found", "other", " "), "2026-10-25 12:00:00"));
        assertTrue(locked(statusVisit("repeat", "not_found", "other", "No one home"), "2026-10-25 12:00:00"));
        assertTrue(locked(statusVisit("repeat", "not_found", "moved", ""), "2026-10-25 12:00:00"));
        assertFalse(locked(statusVisit("repeat", "not_found", "unknown", ""), "2026-10-25 12:00:00"));
    }

    @Test
    public void schemaTwoNewAndFoundRequireAllApplicableServices() throws Exception {
        for (String type : new String[] {"new", "repeat"}) {
            Visit visit = statusVisit(type, "found", "", "");
            assertFalse(locked(visit, "2026-10-25 12:00:00"));
            JSONObject event = new JSONObject(visit.getJson());
            JSONArray obs = event.getJSONArray("obs");
            obs.put(observation(DBConstants.KEY.PURPOSE_OF_VISIT, "routine_follow_up"));
            obs.put(observation(DBConstants.KEY.PARTICIPANTS, "mother"));
            visit.setJson(event.toString());
            assertFalse(locked(visit, "2026-10-25 12:00:00"));
            obs.put(observation(DBConstants.KEY.TOPICS_TAUGHT, "nutrition"));
            visit.setJson(event.toString());
            assertTrue(locked(visit, "2026-10-25 12:00:00"));
        }
        assertFalse(locked(statusVisit("repeat", "", "house_closed", ""), "2026-10-25 12:00:00"));
        assertFalse(locked(statusVisit("invalid", "found", "", ""), "2026-10-25 12:00:00"));
    }

    private Visit statusVisit(String type, String status, String reason, String other) throws Exception {
        JSONArray obs = new JSONArray()
                .put(observation(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_SCHEMA, "2"))
                .put(observation(DBConstants.KEY.HOUSEHOLD_VISIT_TYPE, type))
                .put(observation(DBConstants.KEY.HOUSEHOLD_FOLLOW_UP_STATUS, status))
                .put(observation(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON, reason))
                .put(observation(DBConstants.KEY.HOUSEHOLD_NOT_FOUND_REASON_OTHER, other))
                .put(observation(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "25-10-2026"));
        Visit visit = new Visit();
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        visit.setProcessed(true);
        visit.setJson(new JSONObject().put("obs", obs).toString());
        return visit;
    }

    private JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    @Test
    public void newRoleCountsMustBeCompleteBeforeTheyLockTheHouseholdDay() throws Exception {
        Visit visit = completedVisit("25-10-2026");
        JSONObject event = new JSONObject(visit.getJson());
        JSONArray observations = event.getJSONArray("obs");
        observations.getJSONObject(1).put("values", new JSONArray().put("mother").put("child").put("other"));
        observations.put(new JSONObject().put("fieldCode", DBConstants.KEY.HOUSEHOLD_COUNSELLING_SCHEMA)
                .put("values", new JSONArray().put("2")));
        visit.setJson(event.toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        observations.put(new JSONObject().put("fieldCode", DBConstants.KEY.COUNSELLED_CHILD_COUNT)
                .put("values", new JSONArray().put("2")));
        observations.put(new JSONObject().put("fieldCode", DBConstants.KEY.COUNSELLED_OTHER_COUNT)
                .put("values", new JSONArray().put("1")));
        visit.setJson(event.toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        observations.put(new JSONObject().put("fieldCode", DBConstants.KEY.OTHER)
                .put("values", new JSONArray().put("Other person")));
        visit.setJson(event.toString());
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
    }

    private boolean locked(Visit visit, String now) throws Exception {
        return MotherMentorHouseholdVisitUtils.hasCompletedVisitToday(
                Collections.singletonList(visit), date(now));
    }

    private Date date(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value);
    }

    private Visit completedVisit(String recordedDate) throws Exception {
        JSONArray observations = new JSONArray();
        String[] fields = {
            DBConstants.KEY.PURPOSE_OF_VISIT, "education",
            DBConstants.KEY.PARTICIPANTS, "mother",
            DBConstants.KEY.TOPICS_TAUGHT, "hiv_prevention"
        };
        for (int i = 0; i < fields.length; i += 2) {
            observations.put(
                    new JSONObject()
                            .put("fieldCode", fields[i])
                            .put("values", new JSONArray().put(fields[i + 1])));
        }
        if (recordedDate != null) {
            observations.put(
                    new JSONObject()
                            .put("fieldCode", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE)
                            .put("values", new JSONArray().put(recordedDate)));
        }
        Visit visit = new Visit();
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        visit.setProcessed(true);
        visit.setJson(new JSONObject().put("obs", observations).toString());
        return visit;
    }
}
