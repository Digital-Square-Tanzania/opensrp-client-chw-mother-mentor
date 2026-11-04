package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildUtilsTest {
    private final LocalDate dob = new LocalDate(2025, 1, 31);

    @Test
    public void newbornContinuesWithoutAResultQuestionButChildAndBreastfeedingAreRequired() {
        Map<String, String> values = child(dob);
        assertTrue(MotherMentorMotherChildUtils.isComplete(values, dob));
        values.remove(MAMA_CHILD_STATUS);
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob));
        values.put(MAMA_CHILD_STATUS, "alive"); values.remove(MAMA_BREASTFEEDING_STATUS);
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob));
    }

    @Test
    public void noTestIsAValidContinueAnswerWithoutInventingAnEidReferral() {
        Map<String, String> values = child(dob);
        values.put(MAMA_HEI_TEST_DONE, "no");
        assertTrue(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusDays(60)));
    }

    @Test
    public void onlyDueStagesAndPlausibleDatesCanCompleteATest() {
        Map<String, String> values = test("six_weeks", "negative", dob.plusDays(45));
        assertTrue(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        values.put(MAMA_HEI_TEST_TYPE, "eighteen_months");
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        values.put(MAMA_HEI_TEST_TYPE, "six_weeks");
        for (String invalid : new String[] {"2025-02-30", "2025-01-30", "2025-04-02", ""}) {
            values.put(MAMA_HEI_TEST_DATE, invalid);
            assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        }
    }

    @Test
    public void unconfirmedPositiveAndPendingDoNotCloseAMother() {
        Map<String, String> values = test("six_weeks", "positive", dob.plusDays(45));
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        values.put(HEI_CHANYA_IMETHIBITISHWA, "no");
        assertTrue(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(60)));
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusDays(60)));
        values.put(HEI_CHANYA_IMETHIBITISHWA, "yes");
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_CONFIRMED_POSITIVE,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusDays(60)));
        values.put(MAMA_HEI_TEST_RESULT, "pending");
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusDays(60)));
    }

    @Test
    public void onlyFinalEighteenMonthNegativeGraduates() {
        Map<String, String> values = test("nine_months", "negative", dob.plusMonths(10));
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.NONE,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusMonths(10)));
        values = test("eighteen_months", "negative", dob.plusMonths(18));
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.GRADUATED,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusMonths(19)));
    }

    @Test
    public void childDeathNeedsAValidDateAndDoesNotRequireTheHiddenTestBranch() {
        Map<String, String> values = child(dob);
        values.put(MAMA_CHILD_STATUS, "deceased"); values.remove(MAMA_BREASTFEEDING_STATUS);
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(10)));
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, dob.plusDays(2).toString());
        assertEquals(MotherMentorMotherChildUtils.TerminalOutcome.CHILD_DECEASED,
                MotherMentorMotherChildUtils.terminalOutcome(values, dob.plusDays(10)));
        values.put(MAMA_TAREHE_MTOTO_ALIFARIKI, dob.minusDays(1).toString());
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(10)));
    }

    @Test
    public void cessationMustBeRecordedAndPlausible() {
        Map<String, String> values = child(dob);
        values.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(20)));
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, dob.plusDays(3).toString());
        assertTrue(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(20)));
        values.put(MAMA_TAREHE_ACHA_KUNYONYESHA, dob.minusDays(1).toString());
        assertFalse(MotherMentorMotherChildUtils.isComplete(values, dob.plusDays(20)));
    }

    public static Map<String, String> child(LocalDate dob) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MAMA_VISIT_SCHEMA, "2"); values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "found"); values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "breast_feeding");
        values.put(MAMA_TAREHE_KUJIFUNGUA, dob.toString()); values.put(MAMA_CHILD_STATUS, "alive");
        values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        return values;
    }

    public static Map<String, String> test(String stage, String result, LocalDate date) {
        Map<String, String> values = child(new LocalDate(2025, 1, 31));
        values.put(MAMA_HEI_TEST_DONE, "yes"); values.put(MAMA_HEI_TEST_TYPE, stage);
        values.put(MAMA_HEI_TEST_RESULT, result); values.put(MAMA_HEI_TEST_DATE, date.toString());
        return values;
    }
}
