package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;

import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MotherMentorHeiScheduleTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);

    @Test
    public void newbornHasNoDueTestAndSixWeeksStartsExactlyOnDayFortyTwo() {
        assertTrue(due(null, dob.plusDays(41)).isEmpty());
        assertEquals(Collections.singletonList(MotherMentorHeiSchedule.Stage.SIX_WEEKS),
                due(null, dob.plusDays(42)));
    }

    @Test
    public void nineAndEighteenMonthBoundariesSupersedeUntestedEarlierStages() {
        assertEquals(Collections.singletonList(MotherMentorHeiSchedule.Stage.NINE_MONTHS),
                due(null, dob.plusMonths(9)));
        assertEquals(Collections.singletonList(MotherMentorHeiSchedule.Stage.EIGHTEEN_MONTHS),
                due(null, dob.plusMonths(18)));
    }

    @Test
    public void cessationPlusThreeMonthsHasNoFixedFifteenMonthAgeFloor() {
        LocalDate cessation = dob.plusMonths(1);
        assertEquals(Arrays.asList(MotherMentorHeiSchedule.Stage.SIX_WEEKS,
                MotherMentorHeiSchedule.Stage.AFTER_WEANING),
                due(cessation, cessation.plusMonths(3)));
        assertEquals(Arrays.asList(MotherMentorHeiSchedule.Stage.NINE_MONTHS,
                MotherMentorHeiSchedule.Stage.AFTER_WEANING),
                due(cessation, dob.plusMonths(10)));
    }

    @Test
    public void calendarMonthAdditionClampsShorterMonths() {
        assertEquals(new LocalDate(2025, 4, 30), MotherMentorHeiSchedule.dueDate(
                MotherMentorHeiSchedule.Stage.AFTER_WEANING, dob, dob));
        LocalDate end = new LocalDate(2024, 8, 31);
        assertEquals(new LocalDate(2026, 2, 28), MotherMentorHeiSchedule.dueDate(
                MotherMentorHeiSchedule.Stage.EIGHTEEN_MONTHS, end, null));
    }

    @Test
    public void completedTestsDisappearButPendingAndUnconfirmedResultsRemainActionable() {
        Map<MotherMentorHeiSchedule.Stage, MotherMentorHeiSchedule.TestRecord> tests = new LinkedHashMap<>();
        tests.put(MotherMentorHeiSchedule.Stage.SIX_WEEKS,
                result("pending", false));
        tests.put(MotherMentorHeiSchedule.Stage.NINE_MONTHS,
                result("positive", false));
        assertEquals(Arrays.asList(MotherMentorHeiSchedule.Stage.SIX_WEEKS,
                MotherMentorHeiSchedule.Stage.NINE_MONTHS,
                MotherMentorHeiSchedule.Stage.EIGHTEEN_MONTHS),
                MotherMentorHeiSchedule.due(dob, null, tests, dob.plusMonths(20)));
        tests.put(MotherMentorHeiSchedule.Stage.SIX_WEEKS, result("negative", false));
        tests.put(MotherMentorHeiSchedule.Stage.NINE_MONTHS, result("positive", true));
        assertEquals(Collections.singletonList(MotherMentorHeiSchedule.Stage.EIGHTEEN_MONTHS),
                MotherMentorHeiSchedule.due(dob, null, tests, dob.plusMonths(20)));
    }

    @Test
    public void futureOrInvalidDatesCannotProduceDueTests() {
        assertTrue(MotherMentorHeiSchedule.due(dob.plusDays(1), null,
                Collections.emptyMap(), dob).isEmpty());
        assertNull(MotherMentorHeiSchedule.parseDate("2025-02-30"));
        assertNull(MotherMentorHeiSchedule.parseDate("31-04-2025"));
        assertNull(MotherMentorHeiSchedule.parseDate("2025-1-31"));
        assertEquals(dob, MotherMentorHeiSchedule.parseDate("31-01-2025"));
        assertEquals(dob, MotherMentorHeiSchedule.parseDate("2025-01-31"));
    }

    private MotherMentorHeiSchedule.TestRecord result(String value, boolean confirmed) {
        return new MotherMentorHeiSchedule.TestRecord(value, dob.plusDays(42), confirmed,
                dob.plusDays(45));
    }

    private java.util.List<MotherMentorHeiSchedule.Stage> due(LocalDate cessation,
            LocalDate assessment) {
        return MotherMentorHeiSchedule.due(dob, cessation, Collections.emptyMap(), assessment);
    }
}
