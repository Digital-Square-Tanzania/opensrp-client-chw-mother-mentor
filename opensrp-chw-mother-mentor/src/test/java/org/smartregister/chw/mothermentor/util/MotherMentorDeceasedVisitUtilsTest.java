package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MotherMentorDeceasedVisitUtilsTest {
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
    public void deathRequiresACodedDeathOutcome() throws Exception {
        assertTrue(MotherMentorDeceasedVisitUtils.isDeceased(event("follow_up_status", "deceased")));
        assertTrue(MotherMentorDeceasedVisitUtils.isDeceased(event("iit_tracing_outcome", "deceased")));
        assertTrue(MotherMentorDeceasedVisitUtils.isDeceased(event("follow_up_outcome", "client_deceased")));
        for (String value : new String[] {"found", "not_found", "completed", "refused_return"}) {
            assertFalse(MotherMentorDeceasedVisitUtils.isDeceased(event("follow_up_status", value)));
        }
        assertFalse(MotherMentorDeceasedVisitUtils.isDeceased(event("comments", "deceased")));
        assertFalse(MotherMentorDeceasedVisitUtils.isDeceased(event("visit_pregnancy_status", "deceased")));
    }

    @Test
    public void explicitDeathDatePrecedesServiceAndImportDates() throws Exception {
        JSONObject json = new JSONObject(event(DBConstants.KEY.DOD, "2026-10-10"));
        json.getJSONArray("obs").put(observation(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "25-10-2026"));
        json.put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-26T12:00:00+03:00");
        assertEquals(date("2026-10-10"),
                MotherMentorDeceasedVisitUtils.getRecordedDate(json.toString(), "123456"));
    }

    @Test
    public void recordedServiceDatePrecedesEventAndVisitTimestamps() throws Exception {
        JSONObject json = new JSONObject(event(DBConstants.KEY.MOTHERMENTOR_VISIT_DATE, "25-10-2026"));
        json.put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-26T12:00:00+03:00");
        assertEquals(date("2026-10-25"),
                MotherMentorDeceasedVisitUtils.getRecordedDate(json.toString(), "123456"));
    }

    @Test
    public void supportsLegacyEventAndPersistedVisitDatesWithoutInventingToday() throws Exception {
        JSONObject json = new JSONObject().put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, "2026-10-25T12:00:00+03:00");
        assertEquals(date("2026-10-25T12:00:00+03:00"),
                MotherMentorDeceasedVisitUtils.getRecordedDate(json.toString(), null));
        assertEquals(new Date(123456),
                MotherMentorDeceasedVisitUtils.getRecordedDate("{}", "123456"));
        assertNull(MotherMentorDeceasedVisitUtils.getRecordedDate("{}", null));
        assertNull(MotherMentorDeceasedVisitUtils.getRecordedDate("{}", "not-a-date"));
    }

    @Test
    public void timestampOffsetsPreserveTheRecordedCalendarDate() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        for (String timestamp : new String[] {
            "2026-10-25T00:30:00+14:00", "2026-10-25T23:30:00-12:00"
        }) {
            assertEquals("2026-10-25", format.format(date(timestamp)));
            String json = new JSONObject()
                    .put(DBConstants.KEY.EVENT_DATE_TIMESTAMP, timestamp).toString();
            assertEquals("2026-10-25", format.format(
                    MotherMentorDeceasedVisitUtils.getRecordedDate(json, null)));
        }
    }

    @Test
    public void rejectsMalformedOutcomesAndImpossibleDates() {
        assertFalse(MotherMentorDeceasedVisitUtils.isDeceased("{"));
        assertFalse(MotherMentorDeceasedVisitUtils.isDeceased(null));
        for (String value : new String[] {null, "", "41", "2026-02-30", "30-02-2026", "2026-10-25oops"}) {
            assertNull(value, date(value));
        }
        assertEquals(date("2026-10-25"), date("25-10-2026"));
    }

    private static String event(String key, String value) throws Exception {
        return new JSONObject().put("obs", new JSONArray().put(observation(key, value))).toString();
    }

    private static JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    private static Date date(String value) {
        return MotherMentorDeceasedVisitUtils.parseDate(value);
    }
}
