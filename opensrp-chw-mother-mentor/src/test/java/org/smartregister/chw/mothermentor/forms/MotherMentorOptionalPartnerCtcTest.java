package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.appcompat.view.ContextThemeWrapper;

import com.vijay.jsonwizard.utils.FormUtils;
import com.vijay.jsonwizard.utils.ValidationStatus;
import com.vijay.jsonwizard.widgets.MaskEditTextFactory;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.InputStream;
import java.util.Scanner;

import io.github.softmedtanzania.MaskedEditText;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorOptionalPartnerCtcTest {
    @Test
    public void blankPartnerCtcPassesNativeMaskedValidationInBothLanguages() throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(locale);
            FormUtils.getFieldFromForm(form, "on_ctc").put("value", "yes");
            MaskedEditText input = inputFor(FormUtils.getFieldFromForm(form, "partner_ctc"));

            input.setText("");
            ValidationStatus result = MaskEditTextFactory.validate(null, input);

            assertEquals("", input.getRawText());
            assertTrue(locale, result.isValid());
            assertNull(result.getErrorMessage());
            assertNull(input.getError());

            input.setText("12-34");
            assertFalse(locale, MaskEditTextFactory.validate(null, input).isValid());
            input.setText("");
            ValidationStatus cleared = MaskEditTextFactory.validate(null, input);

            assertEquals("", input.getRawText());
            assertTrue(locale, cleared.isValid());
            assertNull(cleared.getErrorMessage());
            assertNull(input.getError());
        }
    }

    @Test
    public void enteredPartnerCtcStillRequiresACompleteFormattedIdentifier() throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            JSONObject field = FormUtils.getFieldFromForm(form(locale), "partner_ctc");
            MaskedEditText valid = inputFor(field);
            valid.setText("12-34-5678-123456");
            assertEquals("12345678123456", valid.getRawText());
            assertTrue(locale, MaskEditTextFactory.validate(null, valid).isValid());

            for (String partial : new String[] {"12-34", "12-34-5678-12345"}) {
                MaskedEditText invalid = inputFor(field);
                invalid.setText(partial);
                ValidationStatus result = MaskEditTextFactory.validate(null, invalid);
                assertFalse(locale + ": " + partial, result.isValid());
                assertEquals(
                        field.getJSONObject("v_regex").getString("err"), result.getErrorMessage());
            }
        }
    }

    @Test
    public void originalRequiredConfigurationRejectsAnEmptyMaskedValue() throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            JSONObject original = FormUtils.getFieldFromForm(form(locale), "partner_ctc");
            // Reproduce the previous asset's mandatory flag without changing the packaged form.
            original.put(
                    "v_required",
                    new JSONObject()
                            .put("value", true)
                            .put("err", original.getJSONObject("v_max_length").getString("err")));
            MaskedEditText input = inputFor(original);
            input.setText("");

            assertEquals("", input.getRawText());
            assertFalse(locale, MaskEditTextFactory.validate(null, input).isValid());
        }
    }

    private MaskedEditText inputFor(JSONObject field) throws Exception {
        MaskEditTextFactory factory = new MaskEditTextFactory();
        MaskedEditText input =
                factory.getRelativeLayout(
                                new ContextThemeWrapper(
                                        RuntimeEnvironment.getApplication(),
                                        androidx.appcompat.R.style.Theme_AppCompat))
                        .findViewById(com.vijay.jsonwizard.R.id.edit_text);
        input.setMask(field.getString("mask"));
        input.setAllowedChars(field.getString("allowed_chars"));
        input.setHint(field.getString("mask_hint"));
        // Run the factory's own validator wiring rather than duplicating its rules in the test.
        for (String method :
                new String[] {"addRequiredValidator", "addLengthValidator", "addRegexValidator"}) {
            ReflectionHelpers.callInstanceMethod(
                    factory,
                    method,
                    ReflectionHelpers.ClassParameter.from(JSONObject.class, field),
                    ReflectionHelpers.ClassParameter.from(MaskedEditText.class, input));
        }
        return input;
    }

    private JSONObject form(String locale) throws Exception {
        try (InputStream input =
                        RuntimeEnvironment.getApplication()
                                .getAssets()
                                .open(locale + "/mothermentor_enroll_partner.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }
}
