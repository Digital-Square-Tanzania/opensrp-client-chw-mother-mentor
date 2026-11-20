package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Activity;
import android.app.Application;
import android.app.DatePickerDialog;
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
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildUiTest {
    @Test
    public void dueStageOptionsAreFilteredAndSingleStageIsSelectedInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            Fixture fixture = new Fixture(directory, new LocalDate(2026, 1, 1), new LocalDate(2026, 10, 1));
            fixture.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
            fixture.put(MAMA_HEI_TEST_DONE, "yes");
            fixture.refresh();
            assertEquals("nine_months", fixture.value(MAMA_HEI_TEST_TYPE));
            assertEquals(View.GONE, fixture.option("six_weeks").getVisibility());
            assertEquals(View.VISIBLE, fixture.option("nine_months").getVisibility());
            assertEquals(View.GONE, fixture.option("three_months_after_weaning").getVisibility());
            assertEquals(View.GONE, fixture.option("eighteen_months").getVisibility());
        }
    }

    @Test
    public void nextTestShowsTheDueDateInPickerFormat() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            LocalDate delivery = new LocalDate(2026, 11, 10);
            Fixture fixture = new Fixture(directory, delivery, delivery);
            fixture.refresh();
            assertTrue(fixture.value(MAMA_HEI_NEXT_TEST_NOTE).endsWith("22-12-2026"));
        }
    }

    @Test
    public void weaningAndBranchCorrectionsClearInvalidPreviousAnswers() throws Exception {
        Fixture fixture = new Fixture("json.form", new LocalDate(2026, 1, 1), new LocalDate(2026, 10, 1));
        fixture.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding");
        fixture.put(MAMA_TAREHE_ACHA_KUNYONYESHA, "01-07-2026");
        fixture.put(MAMA_HEI_TEST_DONE, "yes");
        fixture.put(MAMA_HEI_TEST_TYPE_CHOICE, "three_months_after_weaning");
        fixture.put(MAMA_HEI_TEST_DATE, "01-10-2026");
        fixture.put(MAMA_HEI_TEST_RESULT, "negative");
        fixture.refresh();
        assertEquals("three_months_after_weaning", fixture.value(MAMA_HEI_TEST_TYPE));
        fixture.put(MAMA_BREASTFEEDING_STATUS, "never_breastfed");
        fixture.refresh();
        assertEquals("", fixture.value(MAMA_TAREHE_ACHA_KUNYONYESHA));
        assertEquals("", fixture.value(MAMA_HEI_TEST_DONE));
        assertEquals("", fixture.value(MAMA_HEI_TEST_RESULT));
        assertEquals(View.GONE, fixture.option("three_months_after_weaning").getVisibility());
        fixture.put(FOLLOW_UP_STATUS, "not_found");
        fixture.refresh();
        assertEquals("", fixture.value(MAMA_CHILD_STATUS));
        assertEquals("false", fixture.value(MAMA_CHILD_PATH));
    }

    @Test
    public void birthPickerUsesTheDeliveryWindowAndClearsDatesAfterACorrection() throws Exception {
        LocalDate dob = new LocalDate(2026, 10, 1);
        Fixture fixture = new Fixture("json.form-sw", dob, dob.plusDays(49));
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        EditText sample = new EditText(host);
        sample.setTag(com.vijay.jsonwizard.R.id.key, MAMA_BIRTH_TEST_DATE);
        ((LinearLayout) fixture.activity.getWindow().getDecorView()).addView(sample);
        fixture.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        fixture.put(MAMA_BIRTH_TEST_DONE, "yes");
        fixture.put(MAMA_BIRTH_TEST_DATE, dob.plusDays(2).toString("dd-MM-yyyy"));
        sample.setText(fixture.value(MAMA_BIRTH_TEST_DATE));
        fixture.refresh();
        assertEquals("true", fixture.value(MAMA_BIRTH_TEST_REQUIRED));
        assertTrue(sample.performClick());
        DatePickerDialog picker = (DatePickerDialog) ShadowDialog.getLatestDialog();
        assertEquals(dob, new LocalDate(picker.getDatePicker().getMinDate()));
        assertEquals(dob.plusDays(2), new LocalDate(picker.getDatePicker().getMaxDate()));
        picker.dismiss();

        LocalDate corrected = dob.plusDays(3);
        fixture.put(MAMA_TAREHE_KUJIFUNGUA, corrected.toString("dd-MM-yyyy"));
        fixture.refresh();
        assertEquals("", fixture.value(MAMA_BIRTH_TEST_DATE));
        assertEquals("", sample.getText().toString());
        assertTrue(sample.performClick());
        picker = (DatePickerDialog) ShadowDialog.getLatestDialog();
        assertEquals(corrected, new LocalDate(picker.getDatePicker().getMinDate()));
        assertEquals(corrected.plusDays(2), new LocalDate(picker.getDatePicker().getMaxDate()));
        picker.dismiss();

        fixture.put(MAMA_HEI_ASSESSMENT_DATE, corrected.plusDays(56).toString());
        fixture.refresh();
        assertEquals("false", fixture.value(MAMA_BIRTH_TEST_REQUIRED));
        host.finish();
    }

    @Test
    public void neverBreastfedShowsTheNextAgeBasedTestAfterNineMonths() throws Exception {
        LocalDate dob = new LocalDate(2025, 12, 1);
        Fixture fixture = new Fixture("json.form", dob, dob.plusMonths(10));
        fixture.put(MAMA_BREASTFEEDING_STATUS, "never_breastfed");
        fixture.put(MAMA_HEI_TEST_HISTORY, "{\"nine_months\":{\"result\":\"negative\"}}");
        fixture.refresh();
        assertTrue(fixture.value(MAMA_HEI_NEXT_TEST_NOTE).endsWith("01-06-2027"));
    }

    private static final class Fixture {
        private final JSONObject form;
        private final JsonFormActivity activity;
        private final RadioGroup options;
        private final MotherMentorMotherChildUi helper = new MotherMentorMotherChildUi();

        Fixture(String directory, LocalDate dob, LocalDate date) throws Exception {
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(directory + "/mothermentor_visit_type.json"), "UTF-8").useDelimiter("\\A")) {
                form = new JSONObject(scanner.next());
            }
            put(ATTENDANCE_TYPE, "repeat"); put(FOLLOW_UP_STATUS, "found");
            put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "breast_feeding");
            put(MAMA_TAREHE_KUJIFUNGUA, dob.toString("dd-MM-yyyy"));
            put(MAMA_HEI_ASSESSMENT_DATE, date.toString()); put(MAMA_CHILD_STATUS, "alive");
            LinearLayout root = new LinearLayout(RuntimeEnvironment.getApplication());
            options = new RadioGroup(RuntimeEnvironment.getApplication());
            options.setTag(com.vijay.jsonwizard.R.id.key, MAMA_HEI_TEST_TYPE_CHOICE);
            for (MotherMentorHeiSchedule.Stage stage : MotherMentorHeiSchedule.Stage.values()) {
                RadioButton button = new RadioButton(RuntimeEnvironment.getApplication());
                button.setId(View.generateViewId());
                button.setTag(com.vijay.jsonwizard.R.id.key, MAMA_HEI_TEST_TYPE_CHOICE);
                button.setTag(com.vijay.jsonwizard.R.id.childKey, stage.getCode());
                options.addView(button);
            }
            root.addView(options);
            activity = mock(JsonFormActivity.class);
            Window window = mock(Window.class);
            when(activity.getmJSONObject()).thenReturn(form);
            when(activity.getWindow()).thenReturn(window);
            when(window.getDecorView()).thenReturn(root);
            when(activity.getResources()).thenReturn(RuntimeEnvironment.getApplication().getResources());
            when(activity.getPackageName()).thenReturn(RuntimeEnvironment.getApplication().getPackageName());
            doAnswer(call -> RuntimeEnvironment.getApplication().getString(call.getArgument(0), new Object[] {call.getArgument(1)}))
                    .when(activity).getString(anyInt(), any());
            doAnswer(call -> RuntimeEnvironment.getApplication().getString(call.getArgument(0), new Object[] {call.getArgument(1), call.getArgument(2)}))
                    .when(activity).getString(anyInt(), any(), any());
            doAnswer(call -> RuntimeEnvironment.getApplication().getString(call.getArgument(0)))
                    .when(activity).getString(anyInt());
        }
        void put(String key, String value) { MotherMentorMotherChildUtils.put(form, key, value); }
        String value(String key) { return MotherMentorMotherChildUtils.values(form).get(key); }
        void refresh() { helper.refresh(activity); }
        RadioButton option(String code) {
            for (int i = 0; i < options.getChildCount(); i++) {
                RadioButton button = (RadioButton) options.getChildAt(i);
                if (code.equals(button.getTag(com.vijay.jsonwizard.R.id.childKey))) return button;
            }
            throw new AssertionError(code);
        }
    }
}
