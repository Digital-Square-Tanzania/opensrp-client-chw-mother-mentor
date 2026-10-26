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
public class MotherMentorPartnerFollowUpDailyVisitTest {
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
    public void completedPartnerFollowUpLocksOnlyItsLocalCalendarDay() throws Exception {
        Visit visit = partnerVisit("found", DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY, "no");
        assertTrue(locked(visit, "2026-10-25 23:59:59"));
        assertFalse(locked(visit, "2026-10-26 00:00:00"));
        assertFalse(locked(visit, "2026-10-24 23:59:59"));
    }

    @Test
    public void followUpWithoutFindingPartnerStillCountsAsCompletedVisit() throws Exception {
        for (String status :
                new String[] {
                    "not_found", "deceased", "transferred_facility", "refused_return", "under_follow_up"
                }) {
            assertTrue(locked(partnerVisit(status), "2026-10-25 12:00:00"));
        }
    }

    @Test
    public void foundPartnerNeedsAttendanceAndDateOnlyWhenAttended() throws Exception {
        assertFalse(locked(partnerVisit("found"), "2026-10-25 12:00:00"));
        assertFalse(
                locked(
                        partnerVisit("found", DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY, "yes"),
                        "2026-10-25 12:00:00"));
        assertTrue(
                locked(
                        partnerVisit(
                                "found",
                                DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY,
                                "yes",
                                DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE,
                                "25-10-2026"),
                        "2026-10-25 12:00:00"));
    }

    @Test
    public void draftsAndAutomaticallyProcessedIncompleteVisitsDoNotBlockFollowUp()
            throws Exception {
        Visit draft = partnerVisit("not_found");
        draft.setProcessed(false);
        assertFalse(locked(draft, "2026-10-25 12:00:00"));
        draft.setProcessed(null);
        assertFalse(locked(draft, "2026-10-25 12:00:00"));
        assertFalse(locked(partnerVisit("found"), "2026-10-25 12:00:00"));
        assertFalse(locked(partnerVisit("unknown"), "2026-10-25 12:00:00"));
    }

    @Test
    public void importedPartnerVisitUsesOriginalEventDateInsteadOfImportTimestamp()
            throws Exception {
        Visit visit = partnerVisit("not_found");
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
                partnerVisit("not_found", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "24-10-2026");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        assertTrue(locked(visit, "2026-10-24 12:00:00"));
    }

    @Test
    public void legacyPartnerVisitUsesItsOriginalFoundSchema() throws Exception {
        Visit visit =
                partnerVisit(
                        "found",
                        DBConstants.KEY.RETURNED_TO_FACILITY,
                        "no",
                        DBConstants.KEY.SERVICE_INTERRUPTION_REASON,
                        "transport_cost",
                        DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE,
                        "26-10-2026");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertTrue(locked(visit, "2026-10-25 12:00:00"));
        Visit incomplete = partnerVisit("found", DBConstants.KEY.RETURNED_TO_FACILITY, "no");
        incomplete.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(incomplete, "2026-10-25 12:00:00"));
    }

    @Test
    public void primaryAndOtherFollowUpCategoriesDoNotLockPartnerVisit() throws Exception {
        Visit visit = partnerVisit("not_found");
        visit.setVisitType(MotherMentorFollowUpType.MOTHER_IIT.getEventType());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        visit.setVisitType(MotherMentorFollowUpType.CHILD_EID.getEventType());
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
        assertFalse(
                locked(
                        partnerVisit(
                                "not_found",
                                DBConstants.KEY.FOLLOW_UP_CATEGORY,
                                MotherMentorFollowUpType.CHILD_EID.getValue()),
                        "2026-10-25 12:00:00"));
        visit = partnerVisit("continuing_services", DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        visit.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        assertFalse(locked(visit, "2026-10-25 12:00:00"));
    }

    @Test
    public void malformedRecordsAndInvalidRecordedDateDoNotCreateAnImportDayLock()
            throws Exception {
        Visit invalidDate =
                partnerVisit("not_found", DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "32-10-2026");
        assertFalse(locked(invalidDate, "2026-10-25 12:00:00"));
        invalidDate.setJson("invalid json");
        assertFalse(locked(invalidDate, "2026-10-25 12:00:00"));
    }

    @Test
    public void allSavedVisitsAreCheckedAndDaoLimitsReadsToTheRequestedPartner() throws Exception {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        VisitRepository repository = mock(VisitRepository.class);
        try {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
            when(library.visitRepository()).thenReturn(repository);
            Visit older = partnerVisit("not_found");
            older.setDate(date("2026-10-24 12:00:00"));
            when(repository.getVisits("partner-id", MotherMentorFollowUpType.PARTNER.getEventType()))
                    .thenReturn(Arrays.asList(older, partnerVisit("not_found")));
            when(repository.getVisits("partner-id", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES))
                    .thenReturn(Collections.emptyList());

            assertTrue(
                    MotherMentorDao.hasCompletedPartnerFollowUpVisitToday(
                            "partner-id", date("2026-10-25 12:00:00")));
            verify(repository).getVisits("partner-id", MotherMentorFollowUpType.PARTNER.getEventType());
            verify(repository).getVisits("partner-id", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
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
            assertFalse(MotherMentorDao.hasCompletedPartnerFollowUpVisitToday(null, new Date()));
            assertFalse(MotherMentorDao.hasCompletedPartnerFollowUpVisitToday(" ", new Date()));
            assertFalse(MotherMentorDao.hasCompletedPartnerFollowUpVisitToday("partner-id", null));
            verifyNoInteractions(library);
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private boolean locked(Visit visit, String now) throws Exception {
        return MotherMentorPartnerFollowUpVisitUtils.hasCompletedVisitToday(
                Collections.singletonList(visit), date(now));
    }

    private Date date(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value);
    }

    private Visit partnerVisit(String status, String... answers) throws Exception {
        JSONArray observations = new JSONArray();
        observations.put(
                new JSONObject()
                        .put("fieldCode", DBConstants.KEY.FOLLOW_UP_STATUS)
                        .put("values", new JSONArray().put(status)));
        for (int i = 0; i < answers.length; i += 2) {
            observations.put(
                    new JSONObject()
                            .put("fieldCode", answers[i])
                            .put("values", new JSONArray().put(answers[i + 1])));
        }
        Visit visit = new Visit();
        visit.setVisitType(MotherMentorFollowUpType.PARTNER.getEventType());
        visit.setProcessed(true);
        visit.setDate(date("2026-10-25 23:59:59"));
        visit.setJson(new JSONObject().put("obs", observations).toString());
        return visit;
    }
}
