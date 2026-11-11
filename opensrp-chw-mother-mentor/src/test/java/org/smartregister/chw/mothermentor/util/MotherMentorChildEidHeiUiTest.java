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

import java.util.Locale;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidHeiUiTest {
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
    public void breastfeedingCorrectionsRecomputeTestsAndRequireReferralReconfirmation() throws Exception {
        Fixture f = new Fixture("en", "none");
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
        assertEquals("9 months / 15 months", f.value(MAMA_HEI_NEXT_TEST_NOTE));
        f.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        f.refresh();
        assertEquals("", f.value(MAMA_TAREHE_ACHA_KUNYONYESHA));
        assertEquals("[\"nine_months\"]", f.value(MAMA_HEI_DUE_TESTS));
    }

    @Test
    public void returnOptionsExcludeSupersededAndNotYetEligibleStages() throws Exception {
        Fixture f = new Fixture("en", "referred");
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
        JSONObject field = MotherMentorMotherChildUtils.field(f.form, MAMA_TAREHE_ACHA_KUNYONYESHA);
        assertEquals("01-01-2026", field.getString("min_date"));
        assertEquals("01-10-2026", field.getString("max_date"));
        assertNotNull(f.date.getError());
    }

    private static final class Fixture {
        final JSONObject form;
        final JsonFormActivity activity;
        final RadioGroup options;
        final EditText date;
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
            date = new EditText(context);
            date.setTag(com.vijay.jsonwizard.R.id.key, MAMA_TAREHE_ACHA_KUNYONYESHA);
            root.addView(date);
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
        RadioButton option(Stage stage) {
            for (int i = 0; i < options.getChildCount(); i++) {
                RadioButton button = (RadioButton) options.getChildAt(i);
                if (stage.getCode().equals(button.getTag(com.vijay.jsonwizard.R.id.childKey))) return button;
            }
            throw new AssertionError(stage);
        }
    }
}
