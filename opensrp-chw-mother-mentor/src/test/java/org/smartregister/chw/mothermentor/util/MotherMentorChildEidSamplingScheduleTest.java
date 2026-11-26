package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MotherMentorChildEidSamplingScheduleTest {
    private final LocalDate birth = new LocalDate(2025, 1, 31);

    @Test
    public void pendingSampleIsReviewedButNeverOfferedForAnotherSample() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        LocalDate day = birth.plusMonths(10);
        tests.put(Stage.NINE_MONTHS, new TestRecord("pending", birth.plusMonths(9), false, day));
        assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, null, tests, day).isEmpty());
        assertEquals(Collections.singletonList(Stage.NINE_MONTHS),
                MotherMentorHeiSchedule.due(birth, null, tests, day));
    }

    @Test
    public void unconfirmedPositiveIsNotASecondSamplingOpportunity() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        LocalDate day = birth.plusMonths(10);
        tests.put(Stage.NINE_MONTHS, new TestRecord("positive", birth.plusMonths(9), false, day));
        assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, null, tests, day).isEmpty());
        assertEquals(Collections.singletonList(Stage.NINE_MONTHS),
                MotherMentorHeiSchedule.due(birth, null, tests, day));
    }

    @Test
    public void sixWeeksStartsAt42DaysAndEndsAtNineCalendarMonths() {
        assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, null,
                Collections.emptyMap(), birth.plusDays(41)).isEmpty());
        assertEquals(Collections.singletonList(Stage.SIX_WEEKS),
                MotherMentorHeiSchedule.eligibleForSampling(birth, null,
                        Collections.emptyMap(), birth.plusDays(42)));
        assertEquals(Collections.singletonList(Stage.NINE_MONTHS),
                MotherMentorHeiSchedule.eligibleForSampling(birth, null,
                        Collections.emptyMap(), birth.plusMonths(9)));
    }

    @Test
    public void fiveMonthWeaningKeepsTheNineMonthTestAndTenThroughTwelveWeaningHaveSeparateDueDates() {
        assertEquals(Stage.NINE_MONTHS, MotherMentorHeiSchedule.next(birth, birth.plusMonths(5),
                Collections.emptyMap(), birth.plusMonths(8)));
        LocalDate dob = new LocalDate(2025, 1, 15);
        for (int age : new int[]{10, 11, 12}) {
            LocalDate stop = dob.plusMonths(age);
            LocalDate due = dob.plusMonths(age + 3);
            assertEquals(due, MotherMentorHeiSchedule.dueDate(Stage.AFTER_WEANING, dob, stop));
            assertEquals(Collections.singletonList(Stage.NINE_MONTHS),
                    MotherMentorHeiSchedule.eligibleForSampling(dob, stop, Collections.emptyMap(), due.minusDays(1)));
            assertEquals(java.util.Arrays.asList(Stage.NINE_MONTHS, Stage.AFTER_WEANING),
                    MotherMentorHeiSchedule.eligibleForSampling(dob, stop, Collections.emptyMap(), due));
        }
    }

    @Test
    public void eighteenMonthsSupersedesMissingEarlierTestsButNotOutstandingReview() {
        LocalDate day = birth.plusMonths(18);
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.NINE_MONTHS, new TestRecord("pending", birth.plusMonths(9), false, day));
        assertEquals(Collections.singletonList(Stage.EIGHTEEN_MONTHS),
                MotherMentorHeiSchedule.eligibleForSampling(birth, birth.plusMonths(10), tests, day));
        assertEquals(java.util.Arrays.asList(Stage.NINE_MONTHS, Stage.EIGHTEEN_MONTHS),
                MotherMentorHeiSchedule.due(birth, birth.plusMonths(10), tests, day));
    }

    @Test
    public void recordedNegativeAndConfirmedPositiveAreNeverOfferedAgain() {
        for (String result : new String[] {"negative", "positive"}) {
            Map<Stage, TestRecord> tests = new LinkedHashMap<>();
            tests.put(Stage.NINE_MONTHS, new TestRecord(result, birth.plusMonths(9), true,
                    birth.plusMonths(9)));
            assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, null, tests,
                    birth.plusMonths(10)).isEmpty());
        }
    }

    @Test
    public void nextTestUsesThePostWeaningDateAfterPriorTestsAreComplete() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", birth.plusDays(42), false,
                birth.plusDays(42)));
        tests.put(Stage.NINE_MONTHS, new TestRecord("negative", birth.plusMonths(9), false, birth.plusMonths(9)));
        LocalDate stop = birth.plusMonths(10);
        assertEquals(Stage.AFTER_WEANING, MotherMentorHeiSchedule.next(birth, stop,
                tests, stop.plusDays(1)));
        assertEquals(stop.plusMonths(3),
                MotherMentorHeiSchedule.dueDate(Stage.AFTER_WEANING, birth, stop));
    }

    @Test
    public void cessationMonthEndClampsAndBecomesEligibleOnTheExactCalendarDate() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", birth.plusDays(42), false,
                birth.plusDays(42)));
        tests.put(Stage.NINE_MONTHS, new TestRecord("negative", birth.plusMonths(9), false, birth.plusMonths(9)));
        LocalDate stop = new LocalDate(2026, 1, 31);
        assertEquals(new LocalDate(2026, 4, 30), MotherMentorHeiSchedule.dueDate(
                Stage.AFTER_WEANING, birth, stop));
        assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, stop, tests,
                new LocalDate(2026, 4, 29)).isEmpty());
        assertEquals(Collections.singletonList(Stage.AFTER_WEANING),
                MotherMentorHeiSchedule.eligibleForSampling(birth, stop, tests,
                        new LocalDate(2026, 4, 30)));
    }

    @Test
    public void postWeaningDueDateMustBeBeforeTheEighteenMonthBirthday() {
        LocalDate dob = new LocalDate(2025, 1, 15);
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", dob.plusDays(42), false,
                dob.plusDays(42)));
        tests.put(Stage.NINE_MONTHS, new TestRecord("negative", dob.plusMonths(9), false,
                dob.plusMonths(9)));
        LocalDate cutoff = dob.plusMonths(18);
        LocalDate stopBefore = new LocalDate(2026, 4, 14);
        assertEquals(cutoff.minusDays(1), MotherMentorHeiSchedule.dueDate(Stage.AFTER_WEANING, dob, stopBefore));
        assertEquals(Stage.AFTER_WEANING, MotherMentorHeiSchedule.next(dob, stopBefore, tests, stopBefore));
        assertEquals(Collections.singletonList(Stage.AFTER_WEANING),
                MotherMentorHeiSchedule.eligibleForSampling(dob, stopBefore, tests, cutoff.minusDays(1)));
        assertEquals(Collections.singletonList(Stage.EIGHTEEN_MONTHS),
                MotherMentorHeiSchedule.eligibleForSampling(dob, stopBefore, tests, cutoff));

        for (int daysAfterCutoff : new int[]{0, 1}) {
            LocalDate stop = new LocalDate(2026, 4, 15).plusDays(daysAfterCutoff);
            LocalDate lateDue = cutoff.plusDays(daysAfterCutoff);
            assertEquals(lateDue, stop.plusMonths(3));
            assertNull(MotherMentorHeiSchedule.dueDate(Stage.AFTER_WEANING, dob, stop));
            assertEquals(Stage.EIGHTEEN_MONTHS, MotherMentorHeiSchedule.next(dob, stop, tests, stop));
            assertEquals(Collections.singletonList(Stage.EIGHTEEN_MONTHS),
                    MotherMentorHeiSchedule.eligibleForSampling(dob, stop, tests, lateDue));
        }
    }
}
