package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;

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
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.repository.VisitRepository;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidFollowUpDailyVisitTest {
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
    public void completedChildEidFollowUpLocksOnlyItsLocalCalendarDay() throws Exception {
        Visit visit =
                childVisit(
                        "found",
                        DBConstants.KEY.CHILD_EID_TEST_TYPE,
                        "six_weeks",
                        DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
                        "no");
        assertTrue(locked(visit, "2026-10-25 23:59:59"));
        assertFalse(locked(visit, "2026-10-26 00:00:00"));
        assertFalse(locked(visit, "2026-10-24 23:59:59"));
    }

    @Test
    public void followUpWithoutFindingChildEidStillCountsAsCompletedVisit() throws Exception {
        for (String status :
                new String[] {
                    "not_found",
                    "deceased",
                    "transferred_facility",
                    "refused_return",
                    "under_follow_up"
                }) {
            assertTrue(locked(childVisit(status), "2026-10-25 12:00:00"));
        }
    }

    @Test
    public void foundChildEidNeedsAttendanceAndDateOnlyWhenAttended() throws Exception {
        assertFalse(locked(childVisit("found"), "2026-10-25 12:00:00"));
        assertFalse(
                locked(
                        childVisit(
                                "found",
                                DBConstants.KEY.CHILD_EID_TEST_TYPE,
                                "six_weeks",
                                DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
                                "yes"),
                        "2026-10-25 12:00:00"));
        assertTrue(
                locked(
                        childVisit(
                                "found",
                                DBConstants.KEY.CHILD_EID_TEST_TYPE,
                                "six_weeks",
                                DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
                                "yes",
                                DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE,
                                "25-10-2026"),
                        "2026-10-25 12:00:00"));
    }

    @Test
    public void foundChildNeedsTestTypeEvenWhenFacilityAttendanceIsRecorded() throws Exception {
        assertFalse(
                locked(
                        childVisit("found", DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY, "no"),
                        "2026-10-25 12:00:00"));
        for (String testType :
                new String[] {
                    "six_weeks", "nine_months", "three_months_after_weaning", "eighteen_months"
                }) {
            assertTrue(
                    locked(
                            childVisit(
                                    "found",
                                    DBConstants.KEY.CHILD_EID_TEST_TYPE,
                                    testType,
                                    DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
                                    "no"),
                            "2026-10-25 12:00:00"));
        }
    }

    @Test
    public void draftsAndAutomaticallyProcessedIncompleteVisitsDoNotBlockFollowUp()
            throws Exception {
        Visit draft = childVisit("not_found");
        draft.setProcessed(false);
        assertFalse(locked(draft, "2026-10-25 12:00:00"));
        draft.setProcessed(null);
        assertFalse(locked(draft, "2026-10-25 12:00:00"));
        assertFalse(locked(childVisit("found"), "2026-10-25 12:00:00"));
        assertFalse(locked(childVisit("unknown"), "2026-10-25 12:00:00"));
    }

    @Test
    public void importedChildEidVisitUsesOriginalEventDateInsteadOfImportTimestamp()
            throws Exception {
        Visit visit = childVisit("not_found");
        visit.setDate(date("2026-10-25 12:00:00"));
        JSONObject event = new JSONObject(visit.getJson());
        event.put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-24T20:59:59.000Z");
        visit.setJson(event.toString());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        event.put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-24T21:00:00.000Z");
        visit.setJson(event.toString());
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void explicitLegacyVisitDateTakesPrecedenceOverImportTime() throws Exception {
        Visit visit =
                childVisit("not_found", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "24-10-2026");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        assertTrue(locked(visit, "2026-10-24 12:00:00"));
    }

    @Test
    public void legacyChildEidVisitUsesItsOriginalFoundSchema() throws Exception {
        Visit visit =
                childVisit(
                        "found",
                        DBConstants.KEY.RETURNED_TO_FACILITY,
                        "no",
                        DBConstants.KEY.SERVICE_INTERRUPTION_REASON,
                        "transport_cost",
                        DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE,
                        "26-10-2026");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
        Visit incomplete = childVisit("found", DBConstants.KEY.RETURNED_TO_FACILITY, "no");
        incomplete.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(incomplete, "2026-10-25 12:00:00"));
        Visit otherReason =
                childVisit(
                        "found",
                        DBConstants.KEY.RETURNED_TO_FACILITY,
                        "yes",
                        DBConstants.KEY.SERVICE_INTERRUPTION_REASON,
                        DBConstants.KEY.OTHER,
                        DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE,
                        "26-10-2026");
        otherReason.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(otherReason, "2026-10-25 12:00:00"));
        JSONObject event = new JSONObject(otherReason.getJson());
        event.getJSONArray("obs")
                .put(
                        observation(
                                DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER, "Other reason"));
        otherReason.setJson(event.toString());
        assertFalse(locked(otherReason, "2026-10-25 12:00:00"));
        event.getJSONArray("obs")
                .put(observation(DBConstants.KEY.FACILITY_RETURN_DATE, "25-10-2026"));
        otherReason.setJson(event.toString());
        assertTrue(locked(otherReason, "2026-10-25 12:00:00"));
    }

    @Test
    public void primaryAndOtherFollowUpCategoriesDoNotLockChildEidVisit() throws Exception {
        Visit visit = childVisit("not_found");
        visit.setVisitType(MotherMentorFollowUpType.MOTHER_IIT.getEventType());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        visit.setVisitType(MotherMentorFollowUpType.PARTNER.getEventType());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        assertFalse(
                locked(
                        childVisit(
                                "not_found",
                                DBConstants.KEY.FOLLOW_UP_CATEGORY,
                                MotherMentorFollowUpType.PARTNER.getValue()),
                        "2026-10-25 12:00:00"));
        visit = childVisit("continuing_services", DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void malformedRecordsAndInvalidRecordedDateDoNotCreateAnImportDayLock()
            throws Exception {
        Visit invalidDate =
                childVisit("not_found", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "32-10-2026");
        assertFalse(locked(invalidDate, "2026-10-25 12:00:00"));
        invalidDate.setJson("invalid json");
        assertFalse(locked(invalidDate, "2026-10-25 12:00:00"));
    }

    @Test
    public void allSavedVisitsAreCheckedAndDaoLimitsReadsToTheRequestedChildEid() throws Exception {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        VisitRepository repository = mock(VisitRepository.class);
        try {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
            when(library.visitRepository()).thenReturn(repository);
            Visit older = childVisit("not_found");
            older.setDate(date("2026-10-24 12:00:00"));
            when(
                            repository.getVisits(
                                    "child-id", MotherMentorFollowUpType.CHILD_EID.getEventType()))
                    .thenReturn(Arrays.asList(older, childVisit("not_found")));
            when(repository.getVisits("child-id", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES))
                    .thenReturn(Collections.emptyList());

            assertTrue(
                    MotherMentorDao.hasCompletedChildEidFollowUpVisitToday(
                            "child-id", date("2026-10-25 12:00:00")));
            verify(repository)
                    .getVisits("child-id", MotherMentorFollowUpType.CHILD_EID.getEventType());
            verify(repository).getVisits("child-id", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    @Test
    public void missingIdentityDoesNotAccessTheDatabase() {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        try {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
            assertFalse(MotherMentorDao.hasCompletedChildEidFollowUpVisitToday(null, new Date()));
            assertFalse(MotherMentorDao.hasCompletedChildEidFollowUpVisitToday(" ", new Date()));
            assertFalse(MotherMentorDao.hasCompletedChildEidFollowUpVisitToday("child-id", null));
            verifyNoInteractions(library);
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private boolean locked(Visit visit, String now) throws Exception {
        return MotherMentorChildEidFollowUpVisitUtils.hasCompletedVisitToday(
                Collections.singletonList(visit), date(now));
    }

    private Date date(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value);
    }

    private JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    private Visit childVisit(String status, String... answers) throws Exception {
        JSONArray observations = new JSONArray();
        observations.put(
                observation(DBConstants.KEY.FOLLOW_UP_STATUS, status));
        for (int i = 0; i < answers.length; i += 2) {
            observations.put(
                    observation(answers[i], answers[i + 1]));
        }
        Visit visit = new Visit();
        visit.setVisitType(MotherMentorFollowUpType.CHILD_EID.getEventType());
        visit.setProcessed(true);
        visit.setDate(date("2026-10-25 23:59:59"));
        visit.setJson(new JSONObject().put("obs", observations).toString());
        return visit;
    }
}
