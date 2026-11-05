package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
    public void nineMonthsAndWeaningCanBothBeDueWithoutAFifteenMonthAgeFloor() {
        LocalDate cessation = birth.plusMonths(4);
        assertEquals(java.util.Arrays.asList(Stage.NINE_MONTHS, Stage.AFTER_WEANING),
                MotherMentorHeiSchedule.eligibleForSampling(birth, cessation,
                        Collections.emptyMap(), birth.plusMonths(10)));
        assertEquals(java.util.Arrays.asList(Stage.SIX_WEEKS, Stage.AFTER_WEANING),
                MotherMentorHeiSchedule.eligibleForSampling(birth, cessation,
                        Collections.emptyMap(), birth.plusMonths(7)));
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
    public void nextTestChoosesEarlierWeaningDateInsteadOfStageDeclarationOrder() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", birth.plusDays(42), false,
                birth.plusDays(42)));
        LocalDate stop = new LocalDate(2025, 4, 30);
        assertEquals(Stage.AFTER_WEANING, MotherMentorHeiSchedule.next(birth, stop,
                tests, new LocalDate(2025, 6, 1)));
        assertEquals(new LocalDate(2025, 7, 30),
                MotherMentorHeiSchedule.dueDate(Stage.AFTER_WEANING, birth, stop));
    }

    @Test
    public void cessationMonthEndClampsAndBecomesEligibleOnTheExactCalendarDate() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", birth.plusDays(42), false,
                birth.plusDays(42)));
        assertEquals(new LocalDate(2025, 4, 30), MotherMentorHeiSchedule.dueDate(
                Stage.AFTER_WEANING, birth, birth));
        assertTrue(MotherMentorHeiSchedule.eligibleForSampling(birth, birth, tests,
                new LocalDate(2025, 4, 29)).isEmpty());
        assertEquals(Collections.singletonList(Stage.AFTER_WEANING),
                MotherMentorHeiSchedule.eligibleForSampling(birth, birth, tests,
                        new LocalDate(2025, 4, 30)));
    }

    @Test
    public void futureWeaningTestIsNotScheduledAfterItsEighteenMonthWindow() {
        Map<Stage, TestRecord> tests = new LinkedHashMap<>();
        tests.put(Stage.SIX_WEEKS, new TestRecord("negative", birth.plusDays(42), false,
                birth.plusDays(42)));
        tests.put(Stage.NINE_MONTHS, new TestRecord("negative", birth.plusMonths(9), false,
                birth.plusMonths(9)));
        assertEquals(Stage.EIGHTEEN_MONTHS, MotherMentorHeiSchedule.next(birth,
                birth.plusMonths(17), tests, birth.plusMonths(17)));
    }
}
