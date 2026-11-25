package org.smartregister.chw.mothermentor.util;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.vijay.jsonwizard.activities.JsonFormActivity;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.Collections;
import java.util.Locale;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidHeiUiTest {
    @Test
    public void directPositiveEnrollmentKeepsTheOriginalStageAndRefersForConfirmation() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            Fixture form = new Fixture(language, "confirmation_needed");
            form.put(MTOTO_ENTRY_SOURCE, "direct");
            form.put(MTOTO_RUFAA_TEST, "at_birth");
            form.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
            form.refresh();
            assertEquals("at_birth", form.value(MTOTO_RUFAA_TEST));
            assertEquals("agreed", form.value(MTOTO_REFERRAL_AGREEMENT));
            assertTrue(form.value(MAMA_HEI_NEXT_TEST_NOTE).startsWith("sw".equals(language)
                    ? "Kipimo cha uthibitisho (" : "Confirmatory test ("));
            assertTrue(MotherMentorChildEidHeiUtils.isComplete(
                    MotherMentorMotherChildUtils.values(form.form), new LocalDate(2026, 1, 1),
                    new LocalDate(2026, 10, 1), Collections.emptyMap(), "confirmation_needed"));
        }
    }

    @Test
    public void directConfirmatorySampleUsesItsDateAndResolvesTheAlreadyPositiveTest() throws Exception {
        LocalDate dob = new LocalDate(2026, 1, 1);
        LocalDate day = new LocalDate(2026, 10, 1);
        for (String language : new String[] {"en", "sw"}) {
            Fixture form = new Fixture(language, "confirmation_referred");
            form.put(MTOTO_ENTRY_SOURCE, "direct");
            form.put(MTOTO_RETURN_FLOW, "1");
            form.put(MTOTO_RUFAA_TEST, "at_birth");
            form.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            form.put(CHILD_EID_FACILITY_ARRIVAL_DATE, "2026-09-30");
            form.put(MAMA_HEI_TEST_DONE, "yes");
            form.put(MAMA_HEI_TEST_DATE, day.toString());
            form.put(MAMA_HEI_TEST_RESULT, "pending");
            form.refresh();
            assertEquals("sw".equals(language) ? "Fuatilia majibu kituoni."
                    : "Follow up the results at the facility.", form.value(MTOTO_RETURN_RESULT_NOTE));
            assertEquals("sw".equals(language) ? "Majibu ya kipimo cha uthibitisho" : "Confirmatory test result",
                    MotherMentorMotherChildUtils.field(form.form, MAMA_HEI_TEST_RESULT).getString("label"));
            MotherMentorChildEidReturnVisit.Result pending = MotherMentorChildEidReturnVisit.evaluate(
                    MotherMentorMotherChildUtils.values(form.form), dob, day, Collections.emptyMap(),
                    "confirmation_referred");
            assertNotNull(pending);
            assertEquals(Stage.AT_BIRTH, pending.stage);
            assertEquals(day, pending.testDate);
            assertEquals("awaiting_confirm", pending.nextState);

            form.put(MAMA_HEI_TEST_RESULT, "negative");
            form.refresh();
            assertEquals("", form.value(MTOTO_RETURN_RESULT_NOTE));
            assertEquals(MotherMentorChildEidReturnVisit.Outcome.CONFIRMATORY_NEGATIVE,
                    MotherMentorChildEidReturnVisit.evaluate(MotherMentorMotherChildUtils.values(form.form),
                            dob, day, Collections.emptyMap(), "confirmation_referred").outcome);
            form.put(MAMA_HEI_TEST_RESULT, "positive");
            form.refresh();
            assertEquals("", form.value(MTOTO_RETURN_RESULT_NOTE));
            assertEquals(MotherMentorChildEidReturnVisit.Outcome.CONFIRMED_POSITIVE,
                    MotherMentorChildEidReturnVisit.evaluate(MotherMentorMotherChildUtils.values(form.form),
                            dob, day, Collections.emptyMap(), "confirmation_referred").outcome);
        }
    }

    @Test
    public void missedAttendanceAndNegativeResultShowNoDeveloperMessageInEitherLanguage() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            Fixture missed = new Fixture(language, "referred");
            missed.put(MTOTO_RETURN_FLOW, "1");
            missed.put(CHILD_EID_ARRIVED_AT_FACILITY, "no");
            missed.put(MTOTO_SABABU_KUTOFIKA, "forgot");
            missed.refresh();
            assertEquals("", missed.value(MTOTO_RETURN_RESULT_NOTE));
            assertEquals("forgot", missed.value(MTOTO_SABABU_KUTOFIKA));

            Fixture tested = new Fixture(language, "referred");
            tested.put(MTOTO_RETURN_FLOW, "1");
            tested.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            tested.put(CHILD_EID_FACILITY_ARRIVAL_DATE, "2026-10-01");
            tested.put(MAMA_HEI_TEST_DONE, "yes");
            tested.put(CHILD_EID_TEST_TYPE, "nine_months");
            tested.put(MAMA_HEI_TEST_DATE, "2026-10-01");
            tested.put(MAMA_HEI_TEST_RESULT, "negative");
            tested.put(MTOTO_RESULT_RECEIVED_DATE, "2026-10-01");
            tested.refresh();
            assertEquals("sw".equals(language) ? "Hasi" : "Negative",
                    tested.value(MTOTO_RETURN_RESULT_NOTE));
            assertEquals("negative", tested.value(MAMA_HEI_TEST_RESULT));
        }
    }

    @Test
    public void receivedDateUsesSampleDateAndClearsWhenTheResultBecomesPending() throws Exception {
        Fixture f = new Fixture("sw", "referred");
        f.put(MTOTO_RETURN_FLOW, "1");
        f.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        f.put(CHILD_EID_FACILITY_ARRIVAL_DATE, "2026-10-01");
        f.put(MAMA_HEI_TEST_DONE, "yes");
        f.put(CHILD_EID_TEST_TYPE, "nine_months");
        f.put(MAMA_HEI_TEST_DATE, "2026-10-01");
        f.put(MAMA_HEI_TEST_RESULT, "negative");
        f.refresh();
        assertTrue(MotherMentorChildEidReturnVisit.requiresReceivedDate(
                MotherMentorMotherChildUtils.values(f.form)));
        for (String invalid : new String[] {"", "2026-09-30", "2026-10-02"}) {
            f.put(MTOTO_RESULT_RECEIVED_DATE, invalid);
            assertFalse(MotherMentorChildEidReturnVisit.validReceivedDate(
                    MotherMentorMotherChildUtils.values(f.form), new LocalDate(2026, 10, 1)));
        }
        f.put(MTOTO_RESULT_RECEIVED_DATE, "2026-10-01");
        assertTrue(MotherMentorChildEidReturnVisit.validReceivedDate(
                MotherMentorMotherChildUtils.values(f.form), new LocalDate(2026, 10, 1)));
        f.refresh();
        f.put(MAMA_HEI_TEST_RESULT, "pending");
        f.refresh();
        assertEquals("", f.value(MTOTO_RESULT_RECEIVED_DATE));
    }

    @Test
    public void aResultReceivedLaterUsesTheOriginalSampleDate() throws Exception {
        Fixture f = new Fixture("en", "awaiting_result");
        f.put(MTOTO_RETURN_FLOW, "1");
        f.put(MTOTO_PENDING_TEST_DATE, "2026-09-15");
        f.put(MTOTO_RESULT_RECEIVED, "yes");
        f.put(MTOTO_RECEIVED_RESULT, "positive");
        f.put(MTOTO_RESULT_RECEIVED_DATE, "2026-09-15");
        assertTrue(MotherMentorChildEidReturnVisit.requiresReceivedDate(
                MotherMentorMotherChildUtils.values(f.form)));
        assertTrue(MotherMentorChildEidReturnVisit.validReceivedDate(
                MotherMentorMotherChildUtils.values(f.form), new LocalDate(2026, 10, 1)));
    }

    @Test
    public void bilingualBannerUsesDueStagesAndCanonicalReferralSnapshot() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            Fixture f = new Fixture(language, "none");
            f.refresh();
            assertEquals("miezi9", f.value(MTOTO_RUFAA_TEST));
            assertEquals("2026-10-01", f.value(MTOTO_RUFAA_DUE));
            assertEquals("sw".equals(language) ? "Miezi 9" : "9 months", f.value(MAMA_HEI_NEXT_TEST_NOTE));
            assertEquals("sw".equals(language) ? "Miezi 9" : "9 months", f.value(MAMA_CHILD_AGE_NOTE));
            for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) assertEquals("", f.value(key));
        }
    }

    @Test
    public void stoppedBreastfeedingBannerWaitsForTheDateAndClearsWhenItIsRemoved() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            boolean swahili = "sw".equals(language);
            Fixture f = new Fixture(language, "none");
            f.put(CHILD_DOB, "2025-09-01");
            f.put(MAMA_HEI_ASSESSMENT_DATE, "2026-11-01");
            f.put(MAMA_HEI_TEST_HISTORY, "{\"nine_months\":{\"result\":\"negative\","
                    + "\"date\":\"2026-06-01\",\"confirmed\":false,\"service_date\":\"2026-06-01\"}}");
            f.refresh();
            assertEquals(swahili
                    ? "hakuna\nKipimo kinachofuata: Miezi 18 (Uthibitisho) — 01-03-2027"
                    : "none\nNext test: 18 months (confirmatory) — 01-03-2027", f.banner.getText().toString());

            f.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
            f.refresh();
            assertEquals(language, "", f.banner.getText().toString());
            assertEquals("", f.value(MAMA_HEI_NEXT_TEST_NOTE));

            f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "01-10-2026");
            f.refresh();
            assertEquals(swahili ? "hakuna\nKipimo kinachofuata: Miezi 3 baada ya kuacha kunyonya — 01-01-2027"
                    : "none\nNext test: 3 months after stopping breastfeeding — 01-01-2027", f.banner.getText().toString());
            assertEquals("", f.value(MTOTO_RUFAA_TEST));

            f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "01-08-2026");
            f.refresh();
            assertEquals(swahili ? "Miezi 3 baada ya kuacha kunyonya"
                    : "3 months after stopping breastfeeding", f.banner.getText().toString());
            assertEquals("miezi15", f.value(MTOTO_RUFAA_TEST));
            assertEquals("2026-11-01", f.value(MTOTO_RUFAA_DUE));

            f.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
            f.refresh();
            assertEquals("agreed", f.value(MTOTO_REFERRAL_AGREEMENT));
            f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "");
            f.refresh();
            assertEquals("", f.banner.getText().toString());
            assertEquals("", f.value(MAMA_HEI_NEXT_TEST_NOTE));
            assertEquals("", f.value(MTOTO_REFERRAL_AGREEMENT));
        }
    }

    @Test
    public void mtotoEidShowsPostWeaningOnlyBeforeTheEighteenMonthCutoffInBothLanguages() throws Exception {
        LocalDate cutoff = new LocalDate(2026, 7, 15);
        for (String language : new String[] {"en", "sw"}) {
            String postWeaning = "sw".equals(language) ? "Miezi 3 baada ya kuacha kunyonya"
                    : "3 months after stopping breastfeeding";
            for (int offset : new int[]{-1, 0, 1}) {
                LocalDate wouldBeDue = cutoff.plusDays(offset);
                Fixture f = new Fixture(language, "none");
                f.put(CHILD_DOB, "2025-01-15");
                f.put(MAMA_HEI_ASSESSMENT_DATE, cutoff.minusDays(2).toString());
                f.put(MAMA_HEI_TEST_HISTORY, "{\"nine_months\":{\"result\":\"negative\"}}");
                f.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
                f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, wouldBeDue.minusMonths(3).toString());
                f.refresh();
                String next = f.banner.getText().toString();
                assertEquals(next, offset < 0, next.contains(postWeaning));
                assertTrue(next, next.endsWith(offset < 0 ? "14-07-2026" : "15-07-2026"));

                f.put(MAMA_HEI_ASSESSMENT_DATE, wouldBeDue.toString());
                f.refresh();
                String due = f.banner.getText().toString();
                assertEquals(due, offset < 0, due.contains(postWeaning));
                assertEquals(offset < 0 ? "miezi15" : "miezi18", f.value(MTOTO_RUFAA_TEST));
            }
        }
    }

    @Test
    public void breastfeedingCorrectionsRecomputeTestsAndRequireReferralReconfirmation() throws Exception {
        Fixture f = new Fixture("en", "none");
        f.put(CHILD_DOB, "2025-09-01");
        f.refresh();
        f.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
        f.refresh();
        f.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        f.refresh();
        assertEquals("", f.value(MTOTO_REFERRAL_AGREEMENT));
        f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "2026-07-01");
        f.refresh();
        assertEquals("[\"nine_months\",\"three_months_after_weaning\"]", f.value(MAMA_HEI_DUE_TESTS));
        assertEquals("miezi9", f.value(MTOTO_RUFAA_TEST));
        assertEquals("9 months / 3 months after stopping breastfeeding", f.value(MAMA_HEI_NEXT_TEST_NOTE));
        f.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        f.refresh();
        assertEquals("", f.value(MAMA_TAREHE_ACHA_KUNYONYESHA));
        assertEquals("[\"nine_months\"]", f.value(MAMA_HEI_DUE_TESTS));
    }

    @Test
    public void returnOptionsExcludeSupersededAndNotYetEligibleStages() throws Exception {
        Fixture f = new Fixture("en", "referred");
        f.put(CHILD_DOB, "2025-09-01");
        f.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "2026-07-01");
        f.put(CHILD_EID_TEST_TYPE, "eighteen_months");
        f.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        f.put(CHILD_EID_FINAL_HIV_RESULT, "negative");
        f.refresh();
        assertEquals("", f.value(CHILD_EID_TEST_TYPE));
        assertEquals("", f.value(CHILD_EID_ARRIVED_AT_FACILITY));
        assertEquals("", f.value(CHILD_EID_FINAL_HIV_RESULT));
        assertEquals(View.GONE, f.option(Stage.SIX_WEEKS).getVisibility());
        assertEquals(View.VISIBLE, f.option(Stage.NINE_MONTHS).getVisibility());
        assertEquals(View.VISIBLE, f.option(Stage.AFTER_WEANING).getVisibility());
        assertEquals(View.GONE, f.option(Stage.EIGHTEEN_MONTHS).getVisibility());
    }

    @Test
    public void completedStageIsNotOfferedAndNextDateIsShownWithoutInventedReferral() throws Exception {
        Fixture f = new Fixture("en", "none");
        f.put(MAMA_HEI_TEST_HISTORY, "{\"nine_months\":{\"result\":\"negative\",\"date\":\"2026-10-01\",\"confirmed\":false,\"service_date\":\"2026-10-01\"}}");
        f.put(MTOTO_REFERRAL_AGREEMENT, "agreed");
        f.refresh();
        assertEquals("[]", f.value(MAMA_HEI_DUE_TESTS));
        assertEquals("", f.value(MTOTO_REFERRAL_AGREEMENT));
        assertEquals("", f.value(MTOTO_RUFAA_TEST));
        assertEquals("", f.value(MTOTO_RUFAA_DUE));
        assertEquals("none\nNext test: 18 months (confirmatory) — 01-07-2027", f.value(MAMA_HEI_NEXT_TEST_NOTE));
    }

    @Test
    public void pendingResultCanBeReviewedButIsNeverLabelledAsAnotherSampleDue() throws Exception {
        Fixture f = new Fixture("en", "awaiting_result");
        f.put(MAMA_HEI_TEST_HISTORY, "{\"nine_months\":{\"result\":\"pending\",\"date\":\"2026-10-01\",\"confirmed\":false,\"service_date\":\"2026-10-01\"}}");
        f.refresh();
        assertEquals("[]", f.value(MAMA_HEI_DUE_TESTS));
        assertEquals("[\"nine_months\"]", f.value(CHILD_EID_HEI_REVIEW_TESTS));
        assertEquals("nine_months", f.value(CHILD_EID_TEST_TYPE));
        assertEquals(View.VISIBLE, f.option(Stage.NINE_MONTHS).getVisibility());
        assertTrue(f.value(MAMA_HEI_NEXT_TEST_NOTE).startsWith("none\nNext test:"));
    }

    @Test
    public void invalidCessationDateIsClearedAndNativeBoundsUseBirthAndServiceDates() throws Exception {
        Fixture f = new Fixture("en", "none");
        f.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        f.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "2025-12-31");
        f.refresh();
        assertEquals("", f.value(MAMA_TAREHE_ACHA_KUNYONYESHA));
        assertEquals("", f.value(MAMA_HEI_NEXT_TEST_NOTE));
        JSONObject field = MotherMentorMotherChildUtils.field(f.form, MAMA_TAREHE_ACHA_KUNYONYESHA);
        assertEquals("01-01-2026", field.getString("min_date"));
        assertEquals("01-10-2026", field.getString("max_date"));
        assertNotNull(f.date.getError());
    }

    @Test
    public void pendingResultPromptsImmediatelyInBothLanguagesAndClearsForTheFinalTest() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            Fixture f = new Fixture(language, "referred");
            f.put(MTOTO_RETURN_FLOW, "1");
            f.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            f.put(MAMA_HEI_TEST_DONE, "yes");
            f.put(CHILD_EID_TEST_TYPE, "nine_months");
            f.put(MAMA_HEI_TEST_RESULT, "pending");
            f.refresh();
            assertEquals("sw".equals(language) ? "Fuatilia majibu kituoni."
                    : "Follow up the results at the facility.", f.value(MTOTO_RETURN_RESULT_NOTE));
            f.put(MAMA_HEI_ASSESSMENT_DATE, "2027-07-01");
            f.put(CHILD_EID_TEST_TYPE, "eighteen_months");
            f.put(MAMA_HEI_TEST_RESULT, "pending");
            MotherMentorChildEidHeiUi.prepare(f.form, f.activity);
            assertEquals("", f.value(MAMA_HEI_TEST_RESULT));
        }
    }

    @Test
    public void finalSampleAndFinalConfirmationBothHidePendingAndClearAStoredSelection() throws Exception {
        for (String language : new String[] {"en", "sw"}) {
            Fixture f = new Fixture(language, "referred");
            f.put(CHILD_DOB, "2025-01-01");
            f.put(MTOTO_RETURN_FLOW, "1");
            f.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
            f.put(CHILD_EID_TEST_TYPE, "eighteen_months");
            f.put(MAMA_HEI_TEST_DONE, "yes");
            f.put(MAMA_HEI_TEST_RESULT, "pending");
            f.refresh();
            assertEquals(View.GONE, f.initialPending.getVisibility());
            assertEquals(View.GONE, f.confirmationPending.getVisibility());
            assertEquals("", f.value(MAMA_HEI_TEST_RESULT));

            f.put(MAMA_HEI_TEST_RESULT, "positive");
            f.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
            f.put(MTOTO_CONFIRMATORY_RESULT, "pending");
            f.refresh();
            assertEquals("", f.value(MTOTO_CONFIRMATORY_RESULT));
            assertEquals("no", f.value(MTOTO_CONFIRMATION_RECEIVED));
        }
    }

    @Test
    public void earlierStageKeepsPendingAndOnlyAnExplicitResultSetsTheReportValue() throws Exception {
        Fixture f = new Fixture("en", "referred");
        f.put(MTOTO_RETURN_FLOW, "1");
        f.put(CHILD_EID_ARRIVED_AT_FACILITY, "yes");
        f.put(CHILD_EID_TEST_TYPE, "nine_months");
        f.put(MAMA_HEI_TEST_DONE, "yes");
        f.put(MAMA_HEI_TEST_RESULT, "positive");
        f.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        f.put(MTOTO_CONFIRMATORY_RESULT, "pending");
        f.refresh();
        assertEquals("pending", f.value(MTOTO_CONFIRMATORY_RESULT));
        assertEquals(View.VISIBLE, f.confirmationPending.getVisibility());
        assertEquals("no", f.value(MTOTO_CONFIRMATION_RECEIVED));
        f.put(MTOTO_CONFIRMATORY_RESULT, "negative");
        f.refresh();
        assertEquals("yes", f.value(MTOTO_CONFIRMATION_RECEIVED));
        f.put(MTOTO_CONFIRMATORY_TEST_DONE, "no");
        f.refresh();
        assertEquals("", f.value(MTOTO_CONFIRMATORY_RESULT));
        assertEquals("no", f.value(MTOTO_CONFIRMATION_RECEIVED));
        f.put(MAMA_HEI_TEST_RESULT, "negative");
        f.refresh();
        assertEquals("", f.value(MTOTO_CONFIRMATORY_TEST_DONE));
        assertEquals("", f.value(MTOTO_CONFIRMATION_RECEIVED));
    }

    @Test
    public void existingFinalSampleAppliesTheSameRestrictionWhenItsResultArrivesLater() throws Exception {
        Fixture f = new Fixture("sw", "awaiting_result");
        f.put(CHILD_DOB, "2025-01-01");
        f.put(MTOTO_RETURN_FLOW, "1");
        f.put(MTOTO_PENDING_TEST, "miezi18");
        f.put(MAMA_HEI_TEST_HISTORY, "{\"eighteen_months\":{\"result\":\"pending\","
                + "\"date\":\"2026-09-29\",\"confirmed\":false,\"service_date\":\"2026-09-29\"}}");
        f.put(MTOTO_RESULT_RECEIVED, "yes");
        f.put(MTOTO_RECEIVED_RESULT, "positive");
        f.put(MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        f.put(MTOTO_CONFIRMATORY_RESULT, "pending");
        f.refresh();
        assertEquals("miezi18", f.value(MTOTO_PENDING_TEST));
        assertEquals(View.GONE, f.confirmationPending.getVisibility());
        assertEquals("", f.value(MTOTO_CONFIRMATORY_RESULT));
    }

    @Test
    public void restoringOldAnswersOnlyPrefillsTestDoneWhenThereIsAnExplicitConfirmationOutcome() throws Exception {
        Fixture f = new Fixture("en", "awaiting_confirm");
        f.put(MTOTO_CONFIRMATION_RECEIVED, "no");
        MotherMentorChildEidReturnVisit.restoreLegacyConfirmation(f.form);
        assertEquals("", f.value(MTOTO_CONFIRMATORY_TEST_DONE));
        f.put(MTOTO_CONFIRMATION_RECEIVED, "yes");
        MotherMentorChildEidReturnVisit.restoreLegacyConfirmation(f.form);
        assertEquals("", f.value(MTOTO_CONFIRMATORY_TEST_DONE));
        f.put(MTOTO_CONFIRMATORY_RESULT, "negative");
        MotherMentorChildEidReturnVisit.restoreLegacyConfirmation(f.form);
        assertEquals("yes", f.value(MTOTO_CONFIRMATORY_TEST_DONE));
    }

    private static final class Fixture {
        final JSONObject form;
        final JsonFormActivity activity;
        final RadioGroup options;
        final RadioButton initialPending;
        final RadioButton confirmationPending;
        final EditText date;
        final EditText banner;
        final MotherMentorChildEidHeiUi ui = new MotherMentorChildEidHeiUi();

        Fixture(String language, String state) throws Exception {
            Context app = RuntimeEnvironment.getApplication();
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.setLocale(new Locale(language));
            Context context = app.createConfigurationContext(configuration);
            String directory = "sw".equals(language) ? "json.form-sw" : "json.form";
            try (Scanner scanner = new Scanner(app.getAssets().open(
                    directory + "/mothermentor_child_eid_hei.json"), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            put(CHILD_DOB, "2026-01-01");
            put(MAMA_HEI_ASSESSMENT_DATE, "2026-10-01");
            put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
            put(MTOTO_RUFAA_STATE_BEFORE, state);
            LinearLayout root = new LinearLayout(context);
            RadioGroup breastfeeding = new RadioGroup(context);
            breastfeeding.setTag(com.vijay.jsonwizard.R.id.key, MAMA_BREASTFEEDING_STATUS);
            root.addView(breastfeeding);
            options = new RadioGroup(context);
            options.setTag(com.vijay.jsonwizard.R.id.key, CHILD_EID_TEST_TYPE);
            for (Stage stage : Stage.values()) {
                RadioButton button = new RadioButton(context);
                button.setId(View.generateViewId());
                button.setTag(com.vijay.jsonwizard.R.id.key, CHILD_EID_TEST_TYPE);
                button.setTag(com.vijay.jsonwizard.R.id.childKey, stage.getCode());
                options.addView(button);
            }
            root.addView(options);
            initialPending = pending(root, context, MAMA_HEI_TEST_RESULT);
            confirmationPending = pending(root, context, MTOTO_CONFIRMATORY_RESULT);
            date = new EditText(context);
            date.setTag(com.vijay.jsonwizard.R.id.key, MAMA_TAREHE_ACHA_KUNYONYESHA);
            root.addView(date);
            banner = new EditText(context);
            banner.setTag(com.vijay.jsonwizard.R.id.key, MAMA_HEI_NEXT_TEST_NOTE);
            root.addView(banner);
            activity = mock(JsonFormActivity.class);
            Window window = mock(Window.class);
            when(activity.getmJSONObject()).thenReturn(form);
            when(activity.getWindow()).thenReturn(window);
            when(window.getDecorView()).thenReturn(root);
            when(activity.getResources()).thenReturn(context.getResources());
            when(activity.getPackageName()).thenReturn(context.getPackageName());
            doAnswer(call -> context.getString(call.getArgument(0),
                    new Object[] {call.getArgument(1)})).when(activity).getString(anyInt(), any());
            doAnswer(call -> context.getString(call.getArgument(0),
                    new Object[] {call.getArgument(1), call.getArgument(2)}))
                    .when(activity).getString(anyInt(), any(), any());
            doAnswer(call -> context.getString(call.getArgument(0))).when(activity).getString(anyInt());
        }

        void put(String key, String value) { MotherMentorMotherChildUtils.put(form, key, value); }
        String value(String key) { return MotherMentorMotherChildUtils.values(form).get(key); }
        void refresh() { ui.refresh(activity); }

        private static RadioButton pending(LinearLayout root, Context context, String key) {
            RadioGroup group = new RadioGroup(context);
            RadioButton option = new RadioButton(context);
            option.setTag(com.vijay.jsonwizard.R.id.key, key);
            option.setTag(com.vijay.jsonwizard.R.id.childKey, "pending");
            group.addView(option);
            root.addView(group);
            return option;
        }
        RadioButton option(Stage stage) {
            for (int i = 0; i < options.getChildCount(); i++) {
                RadioButton button = (RadioButton) options.getChildAt(i);
                if (stage.getCode().equals(button.getTag(com.vijay.jsonwizard.R.id.childKey))) return button;
            }
            throw new AssertionError(stage);
        }
    }
}
